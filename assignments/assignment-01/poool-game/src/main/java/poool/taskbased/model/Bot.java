package poool.taskbased.model;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import poool.common.model.Ball;
import poool.common.model.P2d;
import poool.common.model.V2d;
import poool.common.model.PooolModel;

/**
 * Autonomous bot player: same behaviour as version 1 (whenever the bot ball
 * is fully stopped, aim at the nearest small ball and throw), but scheduled
 * as a recurring task on a {@link ScheduledExecutorService} instead of a
 * hand-written thread with {@code sleep}.
 */
public class Bot {

    private static final double THROW_SPEED = 1.8;

    private final PooolModel model;
    private final long periodMs;
    private final Random rand = new Random();
    private final ScheduledExecutorService scheduler;

    public Bot(PooolModel model, long periodMs) {
        this.model = model;
        this.periodMs = periodMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "poool-bot"));
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::takeShotGuarded,
                periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void takeShotGuarded() {
        try {
            takeShotIfNeeded();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        }
    }

    private void takeShotIfNeeded() {
        if (model.isGameOver()) {
            return;
        }
        Ball botBall = model.getBotBall();
        if (botBall.isHoled() || botBall.getVel().abs() > PooolModel.STOPPED_THRESHOLD) {
            return;
        }
        P2d from = botBall.getPos();
        P2d target = nearestSmallBall(from);
        if (target == null) {
            return;
        }
        V2d dir = target.sub(from);
        if (dir.abs() < 1e-6) {
            return;
        }
        // Aim at the target with a small random perturbation.
        double angle = (rand.nextDouble() - 0.5) * 0.2;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        V2d aimed = new V2d(dir.x() * cos - dir.y() * sin,
                dir.x() * sin + dir.y() * cos).getNormalized();
        model.setBotVelocity(aimed.mul(THROW_SPEED));
    }

    private P2d nearestSmallBall(P2d from) {
        P2d best = null;
        double bestDist = Double.MAX_VALUE;
        for (Ball b : model.getSmallBalls()) {
            if (b.isHoled()) {
                continue;
            }
            P2d p = b.getPos();
            double d = Math.hypot(p.x() - from.x(), p.y() - from.y());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
