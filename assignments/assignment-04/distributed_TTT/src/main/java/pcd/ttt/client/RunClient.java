package pcd.ttt.client;

import pcd.ttt.common.BoardState;
import pcd.ttt.common.GameLobby;
import pcd.ttt.common.GameSession;
import pcd.ttt.common.PlayerListener;
import pcd.ttt.common.RmiConfig;
import pcd.ttt.common.exceptions.GameException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Locale;

/**
 * Interactive command-line client.
 */
public final class RunClient {

    private RunClient() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = RmiConfig.DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: RunClient [host] [port] [playerName]");
                return;
            }
        }
        String playerName = args.length > 2 ? args[2] : null;

        Console console = new Console();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        PlayerListenerImpl listener = null;
        GameSession session = null;

        try {
            if (playerName == null || playerName.isBlank()) {
                console.prompt("Your name: ");
                playerName = input.readLine();
                if (playerName == null || playerName.isBlank()) {
                    console.print("A player name is required.");
                    return;
                }
            }
            playerName = playerName.trim();

            Registry registry = LocateRegistry.getRegistry(host, port);
            GameLobby lobby = (GameLobby) registry.lookup(RmiConfig.LOBBY_NAME);

            console.print("Connected to the TTT lobby at " + host + ":" + port
                    + " as '" + playerName + "'.");
            printHelp(console);

            boolean running = true;
            while (running) {
                console.prompt("> ");
                String line = input.readLine();
                if (line == null) {
                    line = "quit";
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                String command = parts[0].toLowerCase(Locale.ROOT);

                try {
                    switch (command) {
                        case "help" -> printHelp(console);

                        case "list" -> printGames(lobby, console);

                        case "create" -> {
                            if (parts.length < 2) {
                                console.print("Usage: create <gameName>");
                                break;
                            }
                            if (session != null && listener != null && !listener.isGameOver()) {
                                console.print("You are already playing. Type 'leave' first.");
                                break;
                            }

                            cleanupListener(listener);
                            listener = new PlayerListenerImpl(playerName, console);
                            PlayerListener stub = (PlayerListener) UnicastRemoteObject.exportObject(listener, 0);
                            session = lobby.createGame(parts[1], playerName, stub);
                            printSession(session, console);
                        }

                        case "join" -> {
                            if (parts.length < 2) {
                                console.print("Usage: join <gameName>");
                                break;
                            }
                            if (session != null && listener != null && !listener.isGameOver()) {
                                console.print("You are already playing. Type 'leave' first.");
                                break;
                            }

                            cleanupListener(listener);
                            listener = new PlayerListenerImpl(playerName, console);
                            PlayerListener stub = (PlayerListener) UnicastRemoteObject.exportObject(listener, 0);
                            session = lobby.joinGame(parts[1], playerName, stub);
                            printSession(session, console);
                        }

                        case "move" -> {
                            if (session == null || listener == null || listener.isGameOver()) {
                                console.print("You are not in an active game.");
                                break;
                            }
                            if (parts.length < 3) {
                                console.print("Usage: move <row> <col>   (0 <= row,col <= 2)");
                                break;
                            }
                            int row = Integer.parseInt(parts[1]);
                            int col = Integer.parseInt(parts[2]);
                            session.makeMove(row, col);
                            console.print("Move sent.");
                        }

                        case "board", "status" -> {
                            if (session == null) {
                                console.print("You are not in a game.");
                                break;
                            }
                            BoardState state = listener == null ? null : listener.board();
                            if (state == null) {
                                state = session.currentState();
                            }
                            console.print(state.render());
                        }

                        case "leave" -> {
                            if (session == null) {
                                console.print("You are not in a game.");
                                break;
                            }
                            try {
                                session.leave();
                            } catch (RemoteException e) {
                                // The game may already be over and its session unexported.
                            }
                            cleanupListener(listener);
                            listener = null;
                            session = null;
                            console.print("You left the game.");
                        }

                        case "quit", "exit" -> {
                            if (session != null) {
                                try {
                                    session.leave();
                                } catch (RemoteException e) {
                                    // The game may already be over.
                                }
                            }
                            cleanupListener(listener);
                            listener = null;
                            running = false;
                        }

                        default -> console.print("Unknown command. Type 'help'.");
                    }
                } catch (GameException e) {
                    console.print("Game error: " + e.getMessage());
                } catch (RemoteException e) {
                    console.print("Remote error: " + e.getMessage());
                } catch (NumberFormatException e) {
                    console.print("Row and column must be integers.");
                }
            }

            console.print("Bye.");
        } catch (Exception e) {
            console.print("Client exception: " + e);
            e.printStackTrace();
        } finally {
            cleanupListener(listener);
        }
    }

    private static void printSession(GameSession session, Console console) throws RemoteException {
        console.print("");
        console.print("Game '" + session.gameName() + "' - you are " + session.mySymbol() + ".");
        String opponent = session.opponentName();
        if (opponent == null) {
            console.print("Waiting for an opponent to join...");
        } else {
            console.print("Opponent: " + opponent);
        }
        console.print(session.currentState().render());
        if (opponent == null) {
            console.print("Another player can join with: join " + session.gameName());
        }
    }

    private static void printGames(GameLobby lobby, Console console) throws RemoteException {
        List<String> games = lobby.listGames();
        if (games.isEmpty()) {
            console.print("No joinable games. Create one with: create <gameName>");
            return;
        }
        console.print("Joinable games:");
        for (String game : games) {
            console.print("  - " + game);
        }
    }

    private static void cleanupListener(PlayerListenerImpl listener) {
        if (listener == null) {
            return;
        }
        try {
            UnicastRemoteObject.unexportObject(listener, true);
        } catch (NoSuchObjectException e) {
            // Already unexported: nothing to do.
        }
    }

    private static void printHelp(Console console) {
        console.print("""
                Commands:
                  list                 list the games waiting for an opponent
                  create <gameName>    create a new game and wait for an opponent
                  join <gameName>      join an existing game
                  move <row> <col>     play a move (rows and columns are 0..2)
                  board                show the current board
                  leave                leave the current game
                  help                 show this help
                  quit                 leave and exit
                """);
    }
}
