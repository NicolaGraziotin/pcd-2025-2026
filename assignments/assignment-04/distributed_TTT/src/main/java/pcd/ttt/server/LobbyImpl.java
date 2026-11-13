package pcd.ttt.server;

import pcd.ttt.common.GameAlreadyExistsException;
import pcd.ttt.common.GameException;
import pcd.ttt.common.GameFullException;
import pcd.ttt.common.GameLobby;
import pcd.ttt.common.GameNotFoundException;
import pcd.ttt.common.GameSession;
import pcd.ttt.common.PlayerListener;
import pcd.ttt.common.Symbol;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lobby: the only globally known remote object of the system.
 *
 * <p>The {@link ConcurrentHashMap} makes game creation atomic: two clients
 * trying to create a game with the same name at the same time cannot both
 * succeed, because {@code putIfAbsent} is atomic. Each {@link GameImpl}
 * protects its own state independently, so games do not contend with each
 * other.</p>
 */
public final class LobbyImpl implements GameLobby {

    private final Map<String, GameImpl> games = new ConcurrentHashMap<>();

    @Override
    public GameSession createGame(String gameName, String playerName, PlayerListener listener)
            throws RemoteException, GameException {

        String name = requireName(gameName, "game");
        String player = requireName(playerName, "player");
        Objects.requireNonNull(listener, "listener");

        GameImpl game = new GameImpl(name, this::removeGame);
        GameImpl existing = games.putIfAbsent(name, game);
        if (existing != null) {
            throw new GameAlreadyExistsException("A game named '" + name + "' already exists");
        }

        try {
            Symbol symbol = game.join(player, listener);
            GameSession session = exportSession(game, symbol, player);
            log("Game '" + name + "' created by " + player + " (" + symbol + ")");
            return session;
        } catch (GameException | RemoteException e) {
            games.remove(name, game);
            game.abort("Game creation failed: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public GameSession joinGame(String gameName, String playerName, PlayerListener listener)
            throws RemoteException, GameException {

        String name = requireName(gameName, "game");
        String player = requireName(playerName, "player");
        Objects.requireNonNull(listener, "listener");

        GameImpl game = games.get(name);
        if (game == null) {
            throw new GameNotFoundException("No game named '" + name + "'");
        }

        Symbol symbol = game.join(player, listener);
        try {
            GameSession session = exportSession(game, symbol, player);
            log("Player " + player + " (" + symbol + ") joined game '" + name + "'");
            return session;
        } catch (GameException | RemoteException e) {
            // The player was already added to the game: remove it again.
            game.leave(symbol);
            throw e;
        }
    }

    @Override
    public List<String> listGames() {
        List<String> names = new ArrayList<>();
        for (GameImpl game : games.values()) {
            if (game.isJoinable()) {
                names.add(game.name());
            }
        }
        Collections.sort(names);
        return names;
    }

    private GameSession exportSession(GameImpl game, Symbol symbol, String playerName)
            throws RemoteException, GameException {

        GameSessionImpl session = new GameSessionImpl(game, symbol, playerName);
        GameSession stub = (GameSession) UnicastRemoteObject.exportObject(session, 0);

        if (!game.registerSession(session)) {
            try {
                UnicastRemoteObject.unexportObject(session, true);
            } catch (NoSuchObjectException e) {
                // Already unexported: nothing to do.
            }
            throw new GameFullException("Game '" + game.name() + "' is no longer available");
        }
        return stub;
    }

    private void removeGame(GameImpl game) {
        games.remove(game.name(), game);
        log("Game '" + game.name() + "' removed from the lobby");
    }

    private static String requireName(String value, String what) throws GameException {
        if (value == null || value.isBlank()) {
            throw new GameException("The " + what + " name must not be empty");
        }
        return value.trim();
    }

    private static void log(String message) {
        System.out.println("[ " + System.currentTimeMillis() + " ][ Lobby ] " + message);
    }
}
