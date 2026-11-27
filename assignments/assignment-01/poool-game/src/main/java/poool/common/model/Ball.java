package poool.common.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ball on the board.
 */
public class Ball {

    private static final AtomicInteger ID_GEN = new AtomicInteger();

    private static final double FRICTION_FACTOR = 0.25; /* 0 minimum */
    private static final double RESTITUTION_FACTOR = 1;

    private final int id;
    private final BallType type;
    private final double radius;
    private final double mass;

    private P2d pos;
    private V2d vel;
    private boolean holed;

    /* Who touched this small ball last: used to attribute scores on holes. */
    private volatile BallType lastTouchedBy;
    private volatile long lastTouchTime;

    public Ball(P2d pos, double radius, double mass, V2d vel, BallType type) {
        this.id = ID_GEN.getAndIncrement();
        this.pos = pos;
        this.radius = radius;
        this.mass = mass;
        this.vel = vel;
        this.type = type;
        this.holed = false;
        this.lastTouchedBy = null;
        this.lastTouchTime = 0;
    }

    public int getId() {
        return id;
    }

    public BallType getType() {
        return type;
    }

    public synchronized P2d getPos() {
        return pos;
    }

    public synchronized V2d getVel() {
        return vel;
    }

    public double getRadius() {
        return radius;
    }

    public double getMass() {
        return mass;
    }

    public synchronized boolean isHoled() {
        return holed;
    }

    public synchronized void markHoled() {
        holed = true;
    }

    /**
     * Instantaneously updates the velocity, simulating an impulse
     * (used for human kicks: the impulse is <i>added</i> to the current
     * velocity, as in the assignment specification).
     */
    public synchronized void addImpulse(V2d impulse) {
        vel = vel.sum(impulse);
    }

    /** Sets the velocity outright (used by the bot for its throws). */
    public synchronized void setVelocity(V2d v) {
        vel = v;
    }

    public void touchBy(BallType player, long now) {
        lastTouchedBy = player;
        lastTouchTime = now;
    }

    public BallType getLastTouchedBy() {
        return lastTouchedBy;
    }

    public long getLastTouchTime() {
        return lastTouchTime;
    }

    /**
     * Integrates motion over {@code dt} milliseconds: applies friction,
     * advances the position and bounces against the board boundaries.
     */
    public void updateState(long dt, Boundary bounds) {
        synchronized (this) {
            if (holed) {
                return;
            }
            double speed = vel.abs();
            double dtScaled = dt * 0.001;
            if (speed > 0.001) {
                double dec = FRICTION_FACTOR * dtScaled;
                double factor = Math.max(0, speed - dec) / speed;
                vel = vel.mul(factor);
            } else {
                vel = new V2d(0, 0);
            }
            pos = pos.sum(vel.mul(dtScaled));
            applyBoundaryConstraints(bounds);
        }
    }

    private void applyBoundaryConstraints(Boundary bounds) {
        if (pos.x() + radius > bounds.x1()) {
            pos = new P2d(bounds.x1() - radius, pos.y());
            vel = vel.getSwappedX();
        } else if (pos.x() - radius < bounds.x0()) {
            pos = new P2d(bounds.x0() + radius, pos.y());
            vel = vel.getSwappedX();
        } else if (pos.y() + radius > bounds.y1()) {
            pos = new P2d(pos.x(), bounds.y1() - radius);
            vel = vel.getSwappedY();
        } else if (pos.y() - radius < bounds.y0()) {
            pos = new P2d(pos.x(), bounds.y0() + radius);
            vel = vel.getSwappedY();
        }
    }

    /** Checks whether the ball centre is currently inside any hole. */
    public boolean isInHole(List<Hole> holes) {
        P2d current;
        synchronized (this) {
            if (holed) {
                return false;
            }
            current = pos;
        }
        for (Hole h : holes) {
            if (h.captures(current)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPlayerBall() {
        return type == BallType.HUMAN || type == BallType.BOT;
    }

    /**
     * Resolves the elastic collision between two balls, updating positions
     * (overlap removal) and velocities (impulse).
     *
     * <p>In addition, when a player ball hits a small ball, the small ball
     * records which player touched it last, so that a subsequent hole can be
     * attributed for scoring.
     *
     * <p>Locks are always acquired in increasing {@code id} order to avoid
     * deadlock when several workers resolve overlapping pairs concurrently.
     */
    public static void resolveCollision(Ball a, Ball b) {
        if (a == b) {
            return;
        }
        Ball first = a.id < b.id ? a : b;
        Ball second = a.id < b.id ? b : a;
        synchronized (first) {
            synchronized (second) {
                if (a.holed || b.holed) {
                    return;
                }

                double dx = b.pos.x() - a.pos.x();
                double dy = b.pos.y() - a.pos.y();
                double dist = Math.hypot(dx, dy);
                double minD = a.radius + b.radius;

                if (dist < minD && dist > 1e-6) {
                    double nx = dx / dist;
                    double ny = dy / dist;

                    // 1) solve overlaps, moving balls proportionally to mass
                    double overlap = minD - dist;
                    double totalM = a.mass + b.mass;

                    double aFactor = overlap * (b.mass / totalM);
                    a.pos = new P2d(a.pos.x() - nx * aFactor, a.pos.y() - ny * aFactor);

                    double bFactor = overlap * (a.mass / totalM);
                    b.pos = new P2d(b.pos.x() + nx * bFactor, b.pos.y() + ny * bFactor);

                    // 2) update velocities
                    double dvx = b.vel.x() - a.vel.x();
                    double dvy = b.vel.y() - a.vel.y();
                    double dvn = dvx * nx + dvy * ny;

                    if (dvn <= 0) {
                        double imp = -(1 + RESTITUTION_FACTOR) * dvn
                                / (1.0 / a.mass + 1.0 / b.mass);
                        a.vel = new V2d(a.vel.x() - (imp / a.mass) * nx,
                                a.vel.y() - (imp / a.mass) * ny);
                        b.vel = new V2d(b.vel.x() + (imp / b.mass) * nx,
                                b.vel.y() + (imp / b.mass) * ny);
                    }

                    // 3) record touches for scoring purposes
                    long now = System.currentTimeMillis();
                    if (a.isPlayerBall() && b.type == BallType.SMALL) {
                        b.lastTouchedBy = a.type;
                        b.lastTouchTime = now;
                    } else if (b.isPlayerBall() && a.type == BallType.SMALL) {
                        a.lastTouchedBy = b.type;
                        a.lastTouchTime = now;
                    }
                }
            }
        }
    }
}
