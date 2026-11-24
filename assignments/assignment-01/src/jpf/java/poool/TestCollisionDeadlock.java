package poool;

/**
 * Deadlock-freedom check for pairwise collision resolution.
 *
 * <p>The project model classes ({@code P2d}, {@code V2d}, {@code Ball}, ...)
 * use records, which need Java 16+, while JPF runs on Java 11. To avoid
 * touching the project code, this test uses small local Java 11 replacements
 * that mirror the same id-ordered locking protocol as
 * {@code Ball.resolveCollision}.
 */
public class TestCollisionDeadlock {

    static final class P2d {
        final double x;
        final double y;

        P2d(double x, double y) {
            this.x = x;
            this.y = y;
        }

        double x() {
            return x;
        }

        double y() {
            return y;
        }
    }

    static final class V2d {
        final double x;
        final double y;

        V2d(double x, double y) {
            this.x = x;
            this.y = y;
        }

        double x() {
            return x;
        }

        double y() {
            return y;
        }
    }

    static final class Ball {
        private static int idGen = 0;

        private final int id = idGen++;
        private P2d pos;
        private V2d vel;
        private final double radius;
        private final double mass;

        Ball(P2d pos, double radius, double mass, V2d vel) {
            this.pos = pos;
            this.radius = radius;
            this.mass = mass;
            this.vel = vel;
        }

        /**
         * Same lock ordering as the production code: always lock the ball with
         * the lower id first, so a circular wait cannot happen.
         */
        static void resolveCollision(Ball a, Ball b) {
            if (a == b) {
                return;
            }
            Ball first = a.id < b.id ? a : b;
            Ball second = a.id < b.id ? b : a;
            synchronized (first) {
                synchronized (second) {
                    double dx = b.pos.x() - a.pos.x();
                    double dy = b.pos.y() - a.pos.y();
                    double dist = Math.hypot(dx, dy);
                    double minD = a.radius + b.radius;
                    if (dist < minD && dist > 1e-6) {
                        double overlap = minD - dist;
                        double totalM = a.mass + b.mass;
                        double nx = dx / dist;
                        double ny = dy / dist;
                        a.pos = new P2d(a.pos.x() - nx * overlap * (b.mass / totalM),
                                a.pos.y() - ny * overlap * (b.mass / totalM));
                        b.pos = new P2d(b.pos.x() + nx * overlap * (a.mass / totalM),
                                b.pos.y() + ny * overlap * (a.mass / totalM));
                    }
                }
            }
        }
    }

    public static void main(String[] args) {

        // Three overlapping balls in the same point.
        P2d center = new P2d(0, 0);
        V2d vel = new V2d(1, 1);
        double radius = 10.0;
        double mass = 1.0;

        Ball b1 = new Ball(center, radius, mass, vel);
        Ball b2 = new Ball(center, radius, mass, vel);
        Ball b3 = new Ball(center, radius, mass, vel);

        // Three workers resolving cross collisions: a circular lock pattern
        // (A->B, B->C, C->A) if the lock ordering were not enforced.
        Thread t1 = new Thread(() -> Ball.resolveCollision(b1, b2), "Worker-1");
        Thread t2 = new Thread(() -> Ball.resolveCollision(b2, b3), "Worker-2");
        Thread t3 = new Thread(() -> Ball.resolveCollision(b3, b1), "Worker-3");

        t1.start();
        t2.start();
        t3.start();

        // If the lock ordering fails, JPF reports a deadlock here.
        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
