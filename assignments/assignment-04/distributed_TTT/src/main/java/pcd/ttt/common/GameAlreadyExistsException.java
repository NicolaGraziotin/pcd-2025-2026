package pcd.ttt.common;

import java.io.Serial;

/**
 * Thrown when a player tries to create a game with a name that is already in use.
 */
public class GameAlreadyExistsException extends GameException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GameAlreadyExistsException(String message) {
        super(message);
    }
}
