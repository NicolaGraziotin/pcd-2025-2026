package pcd.ttt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import pcd.ttt.common.exceptions.GameException;

/**
 * Remote entry point of the server: the lobby where games are created and joined.
 */
public interface GameLobby extends Remote {

    GameSession createGame(String gameName, String playerName, PlayerListener listener)
            throws RemoteException, GameException;

    GameSession joinGame(String gameName, String playerName, PlayerListener listener)
            throws RemoteException, GameException;

    List<String> listGames() throws RemoteException;
}
