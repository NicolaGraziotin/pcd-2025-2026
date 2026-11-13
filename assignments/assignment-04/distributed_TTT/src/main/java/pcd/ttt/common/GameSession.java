package pcd.ttt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Per-player remote view of a game.
 *
 * <p>Each player receives a different session object, already bound to the
 * player's symbol. In this way a client cannot accidentally (or maliciously)
 * play a move on behalf of the opponent: the server knows who is calling.</p>
 */
public interface GameSession extends Remote {

    String gameName() throws RemoteException;

    Symbol mySymbol() throws RemoteException;

    /**
     * @return the opponent's name, or {@code null} while the game is still waiting.
     */
    String opponentName() throws RemoteException;

    BoardState currentState() throws RemoteException;

    /**
     * Plays a move on the calling player's behalf.
     *
     * @param row row index in {@code [0, 2]}
     * @param col column index in {@code [0, 2]}
     * @throws GameException if the move is illegal or the game is not in progress
     */
    void makeMove(int row, int col) throws RemoteException, GameException;

    /**
     * Leaves the game. If the game is in progress, the opponent wins by
     * abandonment and is notified through the listener.
     */
    void leave() throws RemoteException;
}
