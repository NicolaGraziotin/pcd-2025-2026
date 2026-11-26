package pcd.ttt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote callback implemented by the client and invoked by the server.
 */
public interface PlayerListener extends Remote {

    void onGameStarted(BoardState board, Symbol mySymbol, String opponentName)
            throws RemoteException;

    void onMoveMade(BoardState board, String playerName, int row, int col)
            throws RemoteException;

    void onGameOver(BoardState board, String message) throws RemoteException;

    void onOpponentLeft(String message) throws RemoteException;
}
