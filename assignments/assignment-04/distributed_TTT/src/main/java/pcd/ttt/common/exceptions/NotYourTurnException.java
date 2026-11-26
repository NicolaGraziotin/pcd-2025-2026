package pcd.ttt.common.exceptions;

import java.io.Serial;

/**
 * Thrown when a player tries to move while it is the opponent's turn.
 */
public class NotYourTurnException extends GameException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotYourTurnException(String message) {
        super(message);
    }
}
