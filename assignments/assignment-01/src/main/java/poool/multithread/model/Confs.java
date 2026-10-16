package poool.multithread.model;

import java.util.List;

/**
 * Shared helper to place the two holes in the top corners of the board.
 */
final class Confs {

    private Confs() {
    }

    static List<Hole> topCornerHoles(Boundary bounds) {
        double marginX = 0.2;
        double y = bounds.y1() - 0.15;
        return List.of(
                new Hole(new P2d(bounds.x0() + marginX, y), 0.09),
                new Hole(new P2d(bounds.x1() - marginX, y), 0.09));
    }
}
