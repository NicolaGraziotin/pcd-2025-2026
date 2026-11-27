package poool.multithread.controller;

import java.util.ArrayList;
import java.util.List;

import poool.common.model.Ball;
import poool.common.model.PooolModel;
import poool.multithread.util.Barrier;
import poool.multithread.util.BarrierImpl;

/**
 * Master of the parallel simulation: a dedicated thread running the main
 * game loop. At every step it rendezvouses with the persistent
 * {@link SimulationWorker}s on a shared reusable {@link Barrier}, moves the two
 * player balls itself while the workers move the small ones, then finalises
 * the step (human-vs-bot collision, FPS accounting, view notification).
 */
public class SimulationEngine extends Thread {

    private static final long TARGET_STEP_MS = 16;
    private static final long MAX_DT_MS = 50;

    private final PooolModel model;
    private final Barrier barrier;
    private final List<SimulationWorker> workers;

    /** Time step published by the master and read by the workers. */
    private volatile long currentDt;

    private long lastUpdateTime;
    private int frames;
    private long fpsTime;

    public SimulationEngine(PooolModel model, int nWorkers) {
        super("poool-engine");
        if (nWorkers <= 0) {
            throw new IllegalArgumentException("nWorkers must be > 0");
        }
        this.model = model;
        this.barrier = new BarrierImpl(nWorkers + 1);
        this.workers = new ArrayList<>(nWorkers);
        for (int i = 0; i < nWorkers; i++) {
            workers.add(new SimulationWorker(model, barrier, this, i, nWorkers));
        }
        this.lastUpdateTime = System.currentTimeMillis();
        this.fpsTime = lastUpdateTime;
    }

    long currentDt() {
        return currentDt;
    }

    public int getWorkerCount() {
        return workers.size();
    }

    @Override
    public void run() {
        for (SimulationWorker w : workers) {
            w.start();
        }
        try {
            while (!isInterrupted()) {
                long stepStart = System.currentTimeMillis();

                long now = stepStart;
                long elapsed = now - lastUpdateTime;
                lastUpdateTime = now;
                currentDt = Math.min(Math.max(elapsed, 0), MAX_DT_MS);

                // --- move phase (workers move small balls...) ---
                barrier.hitAndWaitAll();
                // ...while the master moves the two player balls.
                if (!model.isGameOver()) {
                    movePlayerBalls(currentDt);
                }
                barrier.hitAndWaitAll();

                // --- collision phase (entirely computed by the workers) ---
                barrier.hitAndWaitAll();
                barrier.hitAndWaitAll();

                // --- step finalisation (single-threaded) ---
                if (!model.isGameOver()) {
                    Ball.resolveCollision(model.getHumanBall(), model.getBotBall());
                }
                updateFps(stepStart);
                model.fireUpdate();

                long workTime = System.currentTimeMillis() - stepStart;
                long sleepTime = TARGET_STEP_MS - workTime;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
        } catch (InterruptedException ex) {
        } finally {
            for (SimulationWorker w : workers) {
                w.interrupt();
            }
        }
    }

    private void movePlayerBalls(long dt) {
        Ball human = model.getHumanBall();
        Ball bot = model.getBotBall();
        if (!human.isHoled()) {
            human.updateState(dt, model.getBounds());
            if (human.isInHole(model.getHoles())) {
                model.handleHole(human);
            }
        }
        if (!bot.isHoled()) {
            bot.updateState(dt, model.getBounds());
            if (bot.isInHole(model.getHoles())) {
                model.handleHole(bot);
            }
        }
    }

    private void updateFps(long stepStart) {
        frames++;
        if (stepStart - fpsTime >= 1000) {
            model.setFps((int) (frames * 1000L / (stepStart - fpsTime)));
            frames = 0;
            fpsTime = stepStart;
        }
    }
}
