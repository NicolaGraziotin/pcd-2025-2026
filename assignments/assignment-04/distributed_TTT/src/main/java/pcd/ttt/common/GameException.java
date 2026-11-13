package pcd.ttt.common;

import java.io.Serial;

/**
 * Base class for all the checked game errors that can cross an RMI boundary.
 *
 * <p>It is serializable so that the server can send it back to the client.</p>
 */
public class GameException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public GameException(String message) {
        super(message);
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
    }
}
