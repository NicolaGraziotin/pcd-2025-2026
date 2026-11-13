package pcd.ttt.server;

import pcd.ttt.common.BoardState;
import pcd.ttt.common.GameException;
import pcd.ttt.common.GameFullException;
import pcd.ttt.common.GameStatus;
import pcd.ttt.common.InvalidMoveException;
import pcd.ttt.common.NotYourTurnException;
import pcd.ttt.common.PlayerListener;
import pcd.ttt.common.Symbol;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Server-side state of a single game.
 *
 * <p><b>Concurrency design.</b> All mutable state (board, turn, players,
 * status) is guarded by the {@link #lock} monitor. Every operation:
 * <ol>
 *   <li>validates and updates the state while holding the lock;</li>
 *   <li>creates an immutable {@link BoardState} snapshot;</li>
 *   <li>enqueues the callbacks to the players;</li>
 *   <li>releases the lock before doing any remote call or cleanup.</li>
 * </ol>
 * Remote calls are never performed while holding the monitor, so a slow or
 * crashed client cannot block the game or create lock-ordering deadlocks.</p>
 *
 * <p>Each player has a dedicated single-thread notifier. This preserves the
 * order of the events observed by that player while isolating the two players:
 * a slow client does not delay the notifications sent to the opponent.</p>
 */
public final class GameImpl {

    private static final int[][] WINNING_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},   // rows
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},   // columns
            {0, 4, 8}, {2, 4, 6}               // diagonals
    };

    private final String name;
    private final Object lock = new Object();
    private final Symbol[] board = new Symbol[BoardState.SIZE * BoardState.SIZE];
    private final List<Player> players = new ArrayList<>(2);
    private final List<GameSessionImpl> sessions = new ArrayList<>(2);
    private final Consumer<GameImpl> onFinished;

    private Symbol turn = Symbol.X;
    private GameStatus status = GameStatus.WAITING_FOR_OPPONENT;
    private boolean closed;

    public GameImpl(String name, Consumer<GameImpl> onFinished) {
        this.name = Objects.requireNonNull(name);
        this.onFinished = Objects.requireNonNull(onFinished);
    }

    public String name() {
        return name;
    }

    /**
     * Adds a player to the game. The first player is X, the second one is O.
     *
     * @return the symbol assigned to the new player
     */
    public Symbol join(String playerName, PlayerListener listener) throws GameException {
        synchronized (lock) {
            if (closed || status.isTerminal()) {
                throw new GameFullException("Game '" + name + "' is no longer available");
            }
            if (players.size() >= 2) {
                throw new GameFullException("Game '" + name + "' already has two players");
            }

            Symbol symbol = players.isEmpty() ? Symbol.X : Symbol.O;
            Player player = new Player(playerName, symbol, listener);
            players.add(player);

            if (players.size() == 2) {
                status = GameStatus.IN_PROGRESS;
                turn = Symbol.X;
                BoardState snapshot = snapshotLocked();
                Player first = players.get(0);
                Player second = players.get(1);
                first.send(l -> l.onGameStarted(snapshot, first.symbol, second.name));
                second.send(l -> l.onGameStarted(snapshot, second.symbol, first.name));
                log("Game '" + name + "' started: " + first.name + " (X) vs " + second.name + " (O)");
            }
            return symbol;
        }
    }

    /**
     * Validates and applies a move. The move is accepted only if the game is in
     * progress, it is the caller's turn, and the target cell is empty.
     */
    public void makeMove(Symbol symbol, int row, int col) throws GameException {
        BoardState snapshot;
        String moverName;
        GameStatus result;
        boolean gameFinished;

        synchronized (lock) {
            if (closed || status.isTerminal()) {
                throw new GameException("The game is over");
            }
            if (status == GameStatus.WAITING_FOR_OPPONENT) {
                throw new GameException("The game is still waiting for an opponent");
            }

            Player mover = playerFor(symbol);
            if (mover == null) {
                throw new GameException("Unknown player");
            }
            if (turn != symbol) {
                throw new NotYourTurnException("It is " + turn + "'s turn");
            }
            if (row < 0 || row >= BoardState.SIZE || col < 0 || col >= BoardState.SIZE) {
                throw new InvalidMoveException("Coordinates must be in [0, " + (BoardState.SIZE - 1) + "]");
            }

            int index = row * BoardState.SIZE + col;
            if (board[index] != null) {
                throw new InvalidMoveException("Cell (" + row + ", " + col + ") is already occupied");
            }

            board[index] = symbol;
            moverName = mover.name;
            result = evaluate(symbol);

            if (result != null) {
                status = result;
                closed = true;
                gameFinished = true;
            } else {
                turn = turn.other();
                gameFinished = false;
            }

            snapshot = snapshotLocked();

            // Enqueueing inside the lock guarantees that every player observes
            // events in exactly the same order in which the state changed.
            for (Player p : players) {
                p.send(l -> l.onMoveMade(snapshot, moverName, row, col));
            }
            if (gameFinished) {
                String message = resultMessage(result);
                for (Player p : players) {
                    p.send(l -> l.onGameOver(snapshot, message));
                }
                log("Game '" + name + "' finished: " + result);
            }
        }

        if (gameFinished) {
            cleanup();
        }
    }

    /**
     * Removes a player. If the game was in progress, the remaining player is
     * notified that the game has been aborted.
     */
    public void leave(Symbol symbol) {
        boolean doCleanup = false;

        synchronized (lock) {
            if (closed) {
                return;
            }
            Player leaver = playerFor(symbol);
            if (leaver == null) {
                return;
            }

            closed = true;
            doCleanup = true;
            players.remove(leaver);

            if (!players.isEmpty() && !status.isTerminal()) {
                status = GameStatus.ABORTED;
                Player remaining = players.get(0);
                BoardState snapshot = snapshotLocked();
                String message = leaver.name + " left the game.";
                remaining.send(l -> l.onOpponentLeft(message));
                remaining.send(l -> l.onGameOver(snapshot, "Game aborted: " + message));
                log("Game '" + name + "' aborted because " + leaver.name + " left");
            } else {
                status = GameStatus.ABORTED;
                log("Game '" + name + "' closed because " + leaver.name + " left");
            }
        }

        if (doCleanup) {
            cleanup();
        }
    }

    /**
     * Forces the game to a terminal state, used when creation or joining fails.
     */
    public void abort(String reason) {
        boolean doCleanup = false;

        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            doCleanup = true;
            status = GameStatus.ABORTED;
            BoardState snapshot = snapshotLocked();
            for (Player p : players) {
                p.send(l -> l.onGameOver(snapshot, reason));
            }
        }

        if (doCleanup) {
            cleanup();
        }
    }

    public BoardState currentState() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    public String opponentName(Symbol symbol) {
        synchronized (lock) {
            for (Player p : players) {
                if (p.symbol != symbol) {
                    return p.name;
                }
            }
            return null;
        }
    }

    public boolean isJoinable() {
        synchronized (lock) {
            return !closed && !status.isTerminal() && players.size() < 2;
        }
    }

    /**
     * Registers the exported session so that it can be unexported at the end of
     * the game. Returns {@code false} if the game has already been closed.
     */
    public boolean registerSession(GameSessionImpl session) {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            sessions.add(session);
            return true;
        }
    }

    private Player playerFor(Symbol symbol) {
        for (Player p : players) {
            if (p.symbol == symbol) {
                return p;
            }
        }
        return null;
    }

    private BoardState snapshotLocked() {
        return new BoardState(board, turn, status);
    }

    private GameStatus evaluate(Symbol symbol) {
        for (int[] line : WINNING_LINES) {
            if (board[line[0]] == symbol && board[line[1]] == symbol && board[line[2]] == symbol) {
                return symbol == Symbol.X ? GameStatus.X_WON : GameStatus.O_WON;
            }
        }
        for (Symbol cell : board) {
            if (cell == null) {
                return null;
            }
        }
        return GameStatus.DRAW;
    }

    private static String resultMessage(GameStatus result) {
        return switch (result) {
            case X_WON -> "Player X wins!";
            case O_WON -> "Player O wins!";
            case DRAW -> "It's a draw.";
            case ABORTED -> "Game aborted.";
            default -> "Game finished.";
        };
    }

    /**
     * Removes the game from the lobby and releases the RMI resources.
     * Called exactly once, outside the game lock.
     */
    private void cleanup() {
        List<Player> playersToStop;
        List<GameSessionImpl> sessionsToUnexport;

        synchronized (lock) {
            playersToStop = List.copyOf(players);
            sessionsToUnexport = List.copyOf(sessions);
            sessions.clear();
        }

        onFinished.accept(this);

        // shutdown() lets already enqueued notifications complete.
        for (Player p : playersToStop) {
            p.shutdown();
        }

        for (GameSessionImpl session : sessionsToUnexport) {
            try {
                UnicastRemoteObject.unexportObject(session, true);
            } catch (NoSuchObjectException e) {
                // Already unexported: nothing to do.
            }
        }

        log("Game '" + name + "' cleaned up");
    }

    private static void log(String message) {
        System.out.println("[ " + System.currentTimeMillis() + " ][ Game ] " + message);
    }

    @FunctionalInterface
    private interface RemoteCall {
        void call(PlayerListener listener) throws RemoteException;
    }

    /**
     * A connected player: identity, remote callback stub, and a private
     * single-thread executor that delivers notifications in order.
     */
    private static final class Player {

        private final String name;
        private final Symbol symbol;
        private final PlayerListener listener;
        private final ExecutorService notifier;

        private Player(String name, Symbol symbol, PlayerListener listener) {
            this.name = name;
            this.symbol = symbol;
            this.listener = listener;
            this.notifier = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ttt-notifier-" + name + "-" + symbol);
                t.setDaemon(true);
                return t;
            });
        }

        private void send(RemoteCall call) {
            try {
                notifier.execute(() -> {
                    try {
                        call.call(listener);
                    } catch (RemoteException e) {
                        log("Cannot notify player '" + name + "': " + e.getMessage());
                    } catch (RuntimeException e) {
                        log("Unexpected error while notifying player '" + name + "': " + e);
                    }
                });
            } catch (RejectedExecutionException e) {
                log("Notifier for player '" + name + "' is already shut down");
            }
        }

        private void shutdown() {
            notifier.shutdown();
        }
    }
}
