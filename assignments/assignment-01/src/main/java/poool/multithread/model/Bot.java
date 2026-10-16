package poool.multithread.model;

import java.util.Random;

/**
 * Autonomous bot player, playing the same role as
 * {@code pcd.sketch02.model.AutonomousUpdater}: an independent thread that
 * periodically updates the model without any user intervention.
 *
 * <p>Behaviour (deliberately simple, as allowed by the assignment): whenever
 * the bot ball is fully stopped, the bot aims at the nearest small ball and
 * throws its own ball in that direction. The stop-to-move rule is enforced
 * atomically by the model, so throws requested while the ball is still moving
 * are discarded.
 */
public class Bot extends Thread {

    private static final double THROW_SPEED = 1.8;

    private final PooolModel model;
    private final long periodMs;
    private final Random rand = new Random();

    public Bot(PooolModel model, long periodMs) {
        super("poool-bot");
        this.model = model;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Thread.sleep(periodMs);
                takeShotIfNeeded();
            }
        } catch (InterruptedException ex) {
            // termination request: just exit
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
