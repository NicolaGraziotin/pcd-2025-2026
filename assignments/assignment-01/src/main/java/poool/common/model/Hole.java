package poool.common.model;

/**
 * A hole on the board: any ball whose centre falls inside the hole circle is
 * captured and removed from the game.
 */
public record Hole(P2d center, double radius) {

    public boolean captures(P2d p) {
        double dx = p.x() - center.x();
        double dy = p.y() - center.y();
        return Math.hypot(dx, dy) < radius;
    }
}
