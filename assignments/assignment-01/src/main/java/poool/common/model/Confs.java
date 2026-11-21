package poool.common.model;

import java.util.List;

/**
 * Shared helper to place the two holes, centred exactly on the top corners
 * of the board.
 */
final class Confs {

    private Confs() {
    }

    private static final double HOLE_RADIUS = 0.30;

    static List<Hole> topCornerHoles(Boundary bounds) {
        return List.of(
                new Hole(new P2d(bounds.x0(), bounds.y1()), HOLE_RADIUS),
                new Hole(new P2d(bounds.x1(), bounds.y1()), HOLE_RADIUS));
    }
}
