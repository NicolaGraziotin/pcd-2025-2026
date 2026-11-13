package pcd.ttt.common;

import java.io.Serial;

/**
 * Thrown when a move refers to an invalid or already occupied cell.
 */
public class InvalidMoveException extends GameException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMoveException(String message) {
        super(message);
    }
}
