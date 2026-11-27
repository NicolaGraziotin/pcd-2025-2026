package poool.sequential.controller;

import java.util.List;

import poool.common.controller.AController;
import poool.common.controller.Cmd;
import poool.common.model.PooolModel;
import poool.sequential.util.CommandQueue;

/**
 * Passive controller of the sequential version.
 */
public class Controller implements AController {

    private final CommandQueue<Cmd> pending = new CommandQueue<>();
    private final PooolModel model;

    public Controller(PooolModel model) {
        this.model = model;
    }

    @Override
    public void notifyNewCmd(Cmd cmd) {
        pending.put(cmd);
    }

    /**
     * Executes all the commands received since the previous call. Must be
     * invoked by the game loop, so that the model is only modified by the
     * simulation thread.
     */
    public void processPending() {
        List<Cmd> commands = pending.drain();
        for (Cmd cmd : commands) {
            try {
                cmd.execute(model);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }
    }
}
