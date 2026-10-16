package poool.multithread.controller;

import poool.multithread.model.PooolModel;
import poool.multithread.util.BoundedBuffer;
import poool.multithread.util.BoundedBufferImpl;

/**
 * Active controller, mirroring
 * {@code pcd.sketch02.controller.ActiveController}: it serialises the
 * asynchronous human input coming from the view. Key presses are enqueued as
 * {@link Cmd}s into a bounded-buffer monitor and executed one at a time on
 * the model by this dedicated thread, so the Swing event-dispatch thread is
 * never blocked and commands are applied in order.
 */
public class ActiveController extends Thread {

    private final BoundedBuffer<Cmd> cmdBuffer;
    private final PooolModel model;

    public ActiveController(PooolModel model) {
        super("poool-controller");
        this.cmdBuffer = new BoundedBufferImpl<>(100);
        this.model = model;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                Cmd cmd = cmdBuffer.get();
                cmd.execute(model);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void notifyNewCmd(Cmd cmd) {
        try {
            cmdBuffer.put(cmd);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
