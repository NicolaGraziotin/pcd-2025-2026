package pcd.ttt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote entry point of the server: the lobby where games are created and joined.
 *
 * <p>The lobby is a distributed object exported by the server. Clients obtain its
 * stub from the RMI registry and invoke its methods remotely.</p>
 */
public interface GameLobby extends Remote {

    /**
     * Creates a new game and registers the calling player as player X.
     *
     * @param gameName  unique name of the game
     * @param playerName name chosen by the player
     * @param listener  remote callback used by the server to notify this player
     * @return a remote session bound to this player and this game
     * @throws GameException if the name is empty or already in use
     */
    GameSession createGame(String gameName, String playerName, PlayerListener listener)
            throws RemoteException, GameException;

    /**
     * Joins an existing game as player O.
     *
     * @param gameName  name of the game to join
     * @param playerName name chosen by the player
     * @param listener  remote callback used by the server to notify this player
     * @return a remote session bound to this player and this game
     * @throws GameException if the game does not exist, is full, or is already over
     */
    GameSession joinGame(String gameName, String playerName, PlayerListener listener)
            throws RemoteException, GameException;

    /**
     * Returns the names of the games that currently accept a second player.
     */
    List<String> listGames() throws RemoteException;
}
