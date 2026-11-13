package pcd.ttt.common;

/**
 * Lifecycle of a game as seen by the clients.
 */
public enum GameStatus {
    /** The game has been created and is waiting for the second player. */
    WAITING_FOR_OPPONENT,
    /** Both players are connected and the game is being played. */
    IN_PROGRESS,
    /** Player X completed a line. */
    X_WON,
    /** Player O completed a line. */
    O_WON,
    /** The board is full without a winner. */
    DRAW,
    /** A player left before the game was completed. */
    ABORTED;

    public boolean isTerminal() {
        return this == X_WON || this == O_WON || this == DRAW || this == ABORTED;
    }
}
