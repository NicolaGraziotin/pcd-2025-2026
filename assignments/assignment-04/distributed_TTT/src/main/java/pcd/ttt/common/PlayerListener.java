package pcd.ttt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote callback implemented by the client and invoked by the server.
 *
 * <p>This is the classic RMI callback pattern: the client exports an object
 * implementing this interface and passes its stub to the server. The server
 * then calls the client asynchronously whenever something happens in the game.</p>
 */
public interface PlayerListener extends Remote {

    /**
     * Called on both players when the second player joins and the game starts.
     *
     * @param board        initial board snapshot
     * @param mySymbol     symbol assigned to the receiving player
     * @param opponentName name of the other player
     */
    void onGameStarted(BoardState board, Symbol mySymbol, String opponentName)
            throws RemoteException;

    /**
     * Called on both players after every accepted move.
     *
     * @param board      board snapshot after the move
     * @param playerName name of the player who made the move
     * @param row        row of the move
     * @param col        column of the move
     */
    void onMoveMade(BoardState board, String playerName, int row, int col)
            throws RemoteException;

    /**
     * Called on both players when the game reaches a terminal state.
     *
     * @param board   final board snapshot
     * @param message human-readable result
     */
    void onGameOver(BoardState board, String message) throws RemoteException;

    /**
     * Called on the remaining player when the opponent leaves the game.
     */
    void onOpponentLeft(String message) throws RemoteException;
}
