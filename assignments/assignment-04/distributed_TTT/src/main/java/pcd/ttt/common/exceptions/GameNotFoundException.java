package pcd.ttt.common.exceptions;

import java.io.Serial;

/**
 * Thrown when the requested game does not exist (or is no longer available).
 */
public class GameNotFoundException extends GameException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GameNotFoundException(String message) {
        super(message);
    }
}
