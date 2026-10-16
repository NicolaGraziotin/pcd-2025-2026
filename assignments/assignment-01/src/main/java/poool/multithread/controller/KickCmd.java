package poool.multithread.controller;

import poool.multithread.model.PooolModel;
import poool.multithread.model.V2d;

/**
 * Human kick command: adds the given impulse to the human ball velocity
 * (cf. assignment spec: pressing UP adds e.g. the vector (0,1)).
 */
public class KickCmd implements Cmd {

    private final V2d impulse;

    public KickCmd(V2d impulse) {
        this.impulse = impulse;
    }

    @Override
    public void execute(PooolModel model) {
        model.kickHuman(impulse);
    }

    @Override
    public String toString() {
        return "KickCmd" + impulse;
    }
}
