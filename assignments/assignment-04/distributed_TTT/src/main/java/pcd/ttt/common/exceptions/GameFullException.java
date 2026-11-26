package pcd.ttt.common.exceptions;

import java.io.Serial;

/**
 * Thrown when a game already has two players (or is no longer joinable).
 */
public class GameFullException extends GameException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GameFullException(String message) {
        super(message);
    }
}
