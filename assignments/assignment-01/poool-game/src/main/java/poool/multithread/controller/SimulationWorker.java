package poool.multithread.controller;

import java.util.List;

import poool.common.model.Ball;
import poool.common.model.Boundary;
import poool.common.model.Hole;
import poool.common.model.PooolModel;
import poool.multithread.util.Barrier;

/**
 * Persistent simulation worker (plain platform thread): it owns a stride-based
 * slice of the small balls and cooperates with the {@link SimulationEngine}
 * (master) and the sibling workers through a reusable {@link Barrier}.
 */
class SimulationWorker extends Thread {

    private final PooolModel model;
    private final Barrier barrier;
    private final SimulationEngine engine;
    private final int index;
    private final int nWorkers;

    SimulationWorker(PooolModel model, Barrier barrier, SimulationEngine engine,
            int index, int nWorkers) {
        super("poool-worker-" + index);
        this.model = model;
        this.barrier = barrier;
        this.engine = engine;
        this.index = index;
        this.nWorkers = nWorkers;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                // --- move phase ---
                barrier.hitAndWaitAll(); // wait for the master to start the step
                if (!model.isGameOver()) {
                    moveSlice(engine.currentDt());
                }
                barrier.hitAndWaitAll(); // signal end of move phase

                // --- collision phase ---
                barrier.hitAndWaitAll(); // wait for the master to start collisions
                if (!model.isGameOver()) {
                    collideSlice();
                }
                barrier.hitAndWaitAll(); // signal end of collision phase
            }
        } catch (InterruptedException ex) {
            // termination request: just exit
        }
    }

    private void moveSlice(long dt) {
        Boundary bounds = model.getBounds();
        List<Hole> holes = model.getHoles();
        List<Ball> smallBalls = model.getSmallBalls();
        for (int i = index; i < smallBalls.size(); i += nWorkers) {
            Ball b = smallBalls.get(i);
            if (b.isHoled()) {
                continue;
            }
            b.updateState(dt, bounds);
            if (b.isInHole(holes)) {
                model.handleHole(b);
            }
        }
    }

    private void collideSlice() {
        List<Ball> smallBalls = model.getSmallBalls();

        // small vs small: worker 'index' owns the outer-loop rows
        // index, index + nWorkers, ...
        for (int i = index; i < smallBalls.size(); i += nWorkers) {
            Ball a = smallBalls.get(i);
            if (a.isHoled()) {
                continue;
            }
            for (int j = i + 1; j < smallBalls.size(); j++) {
                Ball b = smallBalls.get(j);
                if (!b.isHoled()) {
                    Ball.resolveCollision(a, b);
                }
            }
        }

        // player balls vs small balls, strided as well
        Ball human = model.getHumanBall();
        Ball bot = model.getBotBall();
        for (int i = index; i < smallBalls.size(); i += nWorkers) {
            Ball s = smallBalls.get(i);
            if (!s.isHoled()) {
                Ball.resolveCollision(human, s);
                Ball.resolveCollision(bot, s);
            }
        }
    }
}
