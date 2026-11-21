package poool.common.controller;

import poool.common.model.PooolModel;
import poool.common.model.V2d;

/**
 * Human kick command: throws the human ball in the given direction.
 *
 * <p>The model applies the throw only if the ball is fully stopped;
 * kicks issued while the ball is still moving are discarded.
 */
public class KickCmd implements Cmd {

    private final V2d velocity;

    public KickCmd(V2d velocity) {
        this.velocity = velocity;
    }

    @Override
    public void execute(PooolModel model) {
        model.kickHuman(velocity);
    }

    @Override
    public String toString() {
        return "KickCmd" + velocity;
    }
}
