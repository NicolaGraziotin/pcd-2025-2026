package pcd.ttt.client;

import pcd.ttt.common.BoardState;
import pcd.ttt.common.PlayerListener;
import pcd.ttt.common.Symbol;

/**
 * Client-side implementation of the remote callback.
 *
 * <p>The server invokes these methods on RMI threads. The listener keeps the
 * last board snapshot so the main thread can print it without an extra remote
 * call, and it uses {@link Console} to serialize the output.</p>
 */
public final class PlayerListenerImpl implements PlayerListener {

    private final String playerName;
    private final Console console;

    private volatile BoardState board;
    private volatile Symbol symbol;
    private volatile String opponentName;
    private volatile boolean gameOver;
    private volatile String resultMessage;

    public PlayerListenerImpl(String playerName, Console console) {
        this.playerName = playerName;
        this.console = console;
    }

    @Override
    public void onGameStarted(BoardState board, Symbol mySymbol, String opponentName) {
        this.board = board;
        this.symbol = mySymbol;
        this.opponentName = opponentName;
        this.gameOver = false;
        this.resultMessage = null;

        console.print("");
        console.print("=== Game started ===");
        console.print("You are " + mySymbol + "; your opponent is " + opponentName
                + " (" + mySymbol.other() + ").");
        console.print(board.render());
        if (board.turn() == mySymbol) {
            console.print("Your turn: type 'move <row> <col>'.");
        } else {
            console.print("Waiting for " + opponentName + " to move...");
        }
    }

    @Override
    public void onMoveMade(BoardState board, String playerName, int row, int col) {
        this.board = board;

        String who = playerName.equals(this.playerName) ? "You" : playerName;
        console.print("");
        console.print(who + " played (" + row + ", " + col + ").");
        console.print(board.render());

        if (!board.isTerminal()) {
            if (board.turn() == symbol) {
                console.print("Your turn: type 'move <row> <col>'.");
            } else {
                console.print("Waiting for " + (opponentName == null ? "the opponent" : opponentName) + "...");
            }
        }
    }

    @Override
    public void onGameOver(BoardState board, String message) {
        this.board = board;
        this.gameOver = true;
        this.resultMessage = message;

        console.print("");
        console.print("=== Game over ===");
        console.print(board.render());
        console.print(message);
        console.print("Type 'create <name>' or 'join <name>' to play again, or 'quit'.");
    }

    @Override
    public void onOpponentLeft(String message) {
        console.print("");
        console.print(message);
    }

    public String playerName() {
        return playerName;
    }

    public BoardState board() {
        return board;
    }

    public Symbol symbol() {
        return symbol;
    }

    public String opponentName() {
        return opponentName;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String resultMessage() {
        return resultMessage;
    }
}
