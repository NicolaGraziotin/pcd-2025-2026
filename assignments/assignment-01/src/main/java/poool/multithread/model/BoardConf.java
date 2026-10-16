package poool.multithread.model;

import java.util.List;

/**
 * Initial configuration of the board, in the spirit of
 * {@code pcd.sketch01.BoardConf}, extended with the bot ball and the holes
 * required by the Poool game.
 */
public interface BoardConf {

    Boundary getBounds();

    List<Hole> getHoles();

    Ball getHumanBall();

    Ball getBotBall();

    List<Ball> getSmallBalls();
}
