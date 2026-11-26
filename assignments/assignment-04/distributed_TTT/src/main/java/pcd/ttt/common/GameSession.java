package pcd.ttt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

import pcd.ttt.common.exceptions.GameException;

/**
 * Per-player remote view of a game.
 */
public interface GameSession extends Remote {

    String gameName() throws RemoteException;

    Symbol mySymbol() throws RemoteException;

    String opponentName() throws RemoteException;

    BoardState currentState() throws RemoteException;

    void makeMove(int row, int col) throws RemoteException, GameException;

    void leave() throws RemoteException;
}
