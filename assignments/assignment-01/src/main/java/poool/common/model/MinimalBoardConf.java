package poool.common.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal configuration: 2 small balls (mirrors
 * {@code pcd.sketch01.MinimalBoardConf}).
 */
public class MinimalBoardConf implements BoardConf {

    private final Boundary bounds = new Boundary(-1.5, -1.0, 1.5, 1.0);

    @Override
    public Boundary getBounds() {
        return bounds;
    }

    @Override
    public List<Hole> getHoles() {
        return Confs.topCornerHoles(bounds);
    }

    @Override
    public Ball getHumanBall() {
        return new Ball(new P2d(0, -0.5), 0.06, 1.0, new V2d(0, 0), BallType.HUMAN);
    }

    @Override
    public Ball getBotBall() {
        return new Ball(new P2d(0.5, -0.5), 0.06, 1.0, new V2d(0, 0), BallType.BOT);
    }

    @Override
    public List<Ball> getSmallBalls() {
        var balls = new ArrayList<Ball>();
        balls.add(new Ball(new P2d(0, 0.5), 0.05, 0.75, new V2d(0, 0), BallType.SMALL));
        balls.add(new Ball(new P2d(0.05, 0.55), 0.025, 0.25, new V2d(0, 0), BallType.SMALL));
        return balls;
    }
}
