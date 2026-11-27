package poool.common.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Large configuration: 400 small balls.
 */
public class LargeBoardConf implements BoardConf {

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
        return new Ball(new P2d(0, -0.75), 0.05, 1.5, new V2d(0, 0), BallType.HUMAN);
    }

    @Override
    public Ball getBotBall() {
        return new Ball(new P2d(0.8, -0.75), 0.05, 1.5, new V2d(0, 0), BallType.BOT);
    }

    @Override
    public List<Ball> getSmallBalls() {
        var ballRadius = 0.01;
        var balls = new ArrayList<Ball>();
        for (int row = 0; row < 20; row++) {
            for (int col = 0; col < 20; col++) {
                var px = -0.25 + col * 0.025;
                var py = row * 0.025;
                balls.add(new Ball(new P2d(px, py), ballRadius, 0.25,
                        new V2d(0, 0), BallType.SMALL));
            }
        }
        return balls;
    }
}
