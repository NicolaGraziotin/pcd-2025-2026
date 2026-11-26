package pcd.ttt.common;

/**
 * Lifecycle of a game as seen by the clients.
 */
public enum GameStatus {
    WAITING_FOR_OPPONENT,
    IN_PROGRESS,
    X_WON,
    O_WON,
    DRAW,
    ABORTED;

    public boolean isTerminal() {
        return this == X_WON || this == O_WON || this == DRAW || this == ABORTED;
    }
}
