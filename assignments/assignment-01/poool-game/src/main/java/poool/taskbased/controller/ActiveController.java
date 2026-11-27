package poool.taskbased.controller;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import poool.common.model.PooolModel;
import poool.common.controller.Cmd;
import poool.common.controller.AController;

/**
 * Task-based active controller: the asynchronous human input coming from the
 * view is queued in a {@link BlockingQueue} and the commands are executed in
 * order by a single-thread {@link ExecutorService}, so the Swing
 * event-dispatch thread is never blocked. This replaces both the dedicated
 * consumer thread and the hand-written bounded-buffer monitor of version 1
 * with standard Executor Framework constructs.
 */
public class ActiveController implements AController {

    private final BlockingQueue<Cmd> queue = new LinkedBlockingQueue<>(100);
    private final ExecutorService executor;

    public ActiveController(PooolModel model) {
        executor = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "poool-controller"));
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    queue.take().execute(model);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    public void notifyNewCmd(Cmd cmd) {
        // never blocks the EDT: drops only if 100 commands are already pending
        queue.offer(cmd);
    }

    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
