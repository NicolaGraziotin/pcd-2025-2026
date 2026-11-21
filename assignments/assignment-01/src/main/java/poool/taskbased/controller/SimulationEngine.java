package poool.taskbased.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import poool.common.model.Ball;
import poool.common.model.PooolModel;

/**
 * Task-based simulation engine (assignment-01, version 2): instead of
 * persistent worker threads synchronized by a barrier, every simulation step
 * is decomposed into short-lived tasks submitted to a fixed
 * {@link ExecutorService}.
 *
 * <p>Each step has two barrier-separated phases, where the barrier is simply
 * {@link ExecutorService#invokeAll} (it returns only when every task is
 * done):
 * <ol>
 *   <li><b>move phase</b> - one task per contiguous chunk of small balls:
 *   motion integration, bounces and hole capture (the engine thread itself
 *   moves the two player balls concurrently);</li>
 *   <li><b>collision phase</b> - small-vs-small tasks over row ranges sized
 *   so that every task owns roughly the same number of ball pairs, plus one
 *   task per chunk for the player-vs-small collisions.</li>
 * </ol>
 *
 * <p>Thread-safety relies on the same discipline as version 1: per-ball locks
 * acquired in id order inside {@link Ball}, plus the model monitor for
 * scoring and game-over transitions.
 */
public class SimulationEngine extends Thread {

    private static final long TARGET_STEP_MS = 16;
    private static final long MAX_DT_MS = 50;

    private final PooolModel model;
    private final ExecutorService pool;
    private final int nWorkers;

    private long lastUpdateTime;
    private int frames;
    private long fpsTime;

    public SimulationEngine(PooolModel model, int nWorkers) {
        super("poool-engine");
        if (nWorkers <= 0) {
            throw new IllegalArgumentException("nWorkers must be > 0");
        }
        this.model = model;
        this.nWorkers = nWorkers;
        var threadCount = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(nWorkers,
                r -> new Thread(r, "poool-pool-" + threadCount.getAndIncrement()));
        this.lastUpdateTime = System.currentTimeMillis();
        this.fpsTime = lastUpdateTime;
    }

    public int getWorkerCount() {
        return nWorkers;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                long stepStart = System.currentTimeMillis();
                long elapsed = stepStart - lastUpdateTime;
                lastUpdateTime = stepStart;
                long dt = Math.min(Math.max(elapsed, 0), MAX_DT_MS);

                if (!model.isGameOver()) {
                    // --- move phase ---
                    pool.invokeAll(moveTasks(dt));
                    movePlayerBalls(dt);

                    // --- collision phase ---
                    pool.invokeAll(collideTasks());
                    Ball.resolveCollision(model.getHumanBall(), model.getBotBall());
                }
                updateFps(stepStart);
                model.fireUpdate();

                long sleepTime = TARGET_STEP_MS - (System.currentTimeMillis() - stepStart);
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }

    /* ---------------- task decomposition ---------------- */

    private List<Callable<Void>> moveTasks(long dt) {
        var smallBalls = model.getSmallBalls();
        int n = smallBalls.size();
        var tasks = new ArrayList<Callable<Void>>();
        int chunk = (n + nWorkers - 1) / nWorkers;
        for (int t = 0; t < nWorkers; t++) {
            int from = t * chunk;
            int to = Math.min(from + chunk, n);
            if (from < to) {
                tasks.add(() -> {
                    moveRange(from, to, dt);
                    return null;
                });
            }
        }
        return tasks;
    }

    private List<Callable<Void>> collideTasks() {
        int n = model.getSmallBalls().size();
        var tasks = new ArrayList<Callable<Void>>();

        // small vs small: row ranges owning ~equal numbers of pairs
        int[] bounds = balancedRowBounds(n, nWorkers);
        for (int t = 0; t < nWorkers; t++) {
            int from = bounds[t];
            int to = bounds[t + 1];
            if (from < to) {
                tasks.add(() -> {
                    collideRows(from, to);
                    return null;
                });
            }
        }

        // player balls vs small balls: plain contiguous chunks
        int chunk = (n + nWorkers - 1) / nWorkers;
        for (int t = 0; t < nWorkers; t++) {
            int from = t * chunk;
            int to = Math.min(from + chunk, n);
            if (from < to) {
                tasks.add(() -> {
                    collidePlayers(from, to);
                    return null;
                });
            }
        }
        return tasks;
    }

    /**
     * Splits the outer loop of the pairwise collision check into ranges
     * owning roughly the same number of pairs, since row {@code i} owns
     * {@code n-1-i} pairs.
     */
    private static int[] balancedRowBounds(int n, int parts) {
        int[] bounds = new int[parts + 1];
        long total = (long) n * (n - 1) / 2;
        bounds[0] = 0;
        int row = 0;
        for (int p = 1; p < parts; p++) {
            long target = total * p / parts;
            while (row < n && pairsBeforeRow(n, row) < target) {
                row++;
            }
            bounds[p] = row;
        }
        bounds[parts] = n;
        return bounds;
    }

    /** Number of pairs with outer index strictly smaller than {@code row}. */
    private static long pairsBeforeRow(int n, int row) {
        return (long) row * (n - 1) - (long) row * (row - 1) / 2;
    }

    /* ---------------- task bodies ---------------- */

    private void moveRange(int from, int to, long dt) {
        var bounds = model.getBounds();
        var holes = model.getHoles();
        var smallBalls = model.getSmallBalls();
        for (int i = from; i < to; i++) {
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

    private void collideRows(int fromRow, int toRow) {
        var smallBalls = model.getSmallBalls();
        for (int i = fromRow; i < toRow; i++) {
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
    }

    private void collidePlayers(int from, int to) {
        var smallBalls = model.getSmallBalls();
        Ball human = model.getHumanBall();
        Ball bot = model.getBotBall();
        for (int i = from; i < to; i++) {
            Ball s = smallBalls.get(i);
            if (!s.isHoled()) {
                Ball.resolveCollision(human, s);
                Ball.resolveCollision(bot, s);
            }
        }
    }

    /* ---------------- single-threaded parts (engine thread) ---------------- */

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
