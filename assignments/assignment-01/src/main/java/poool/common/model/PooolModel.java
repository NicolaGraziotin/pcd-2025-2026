package poool.common.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The game model: it is the shared state observed by the view and updated
 * by the active components (simulation workers, controller, bot).
 *
 * <p>It is designed as a <i>monitor</i>: every public method is
 * {@code synchronized}, so all compound state transitions (hole capture,
 * scoring, game-over detection) are atomic. Individual balls additionally
 * guard their own kinematic state with per-ball locks;
 *
 * <p>Game rules implemented:
 * <ul>
 *   <li>players (human and bot) can shoot only when their own ball is fully
 *   stopped; any kick/throw requested while the ball is still moving is
 *   discarded;</li>
 *   <li>a small ball falling into a hole is removed; if it was recently
 *   touched by a player ball, that player scores one point, otherwise the
 *   score is unchanged;</li>
 *   <li>the game ends when no small balls are left (the highest score wins)
 *   or when a player ball falls into a hole (the other player wins).</li>
 * </ul>
 */
public class PooolModel {

    /** Time window in which a touch still attributes the hole to a player. */
    private static final long SCORE_WINDOW_MS = 4000;

    /**
     * A player ball is considered stopped (and hence allowed to shoot again)
     * when its speed is below this threshold.
     */
    public static final double STOPPED_THRESHOLD = 0.05;

    /** Immutable snapshot of a single ball, handed over to the view. */
    public record BallSnapshot(P2d pos, double radius, BallType type) {
    }

    /** Immutable snapshot of the whole game state, handed over to the view. */
    public record GameSnapshot(
            List<BallSnapshot> balls,
            int humanScore,
            int botScore,
            int ballsRemaining,
            boolean gameOver,
            String winner,
            int fps) {
    }

    private Boundary bounds;
    private List<Hole> holes;
    private Ball humanBall;
    private Ball botBall;
    private List<Ball> smallBalls;

    private int humanScore;
    private int botScore;
    private boolean gameOver;
    private String winner;
    private int fps;

    private final List<PooolObserver> observers = new ArrayList<>();

    public void init(BoardConf conf) {
        this.bounds = conf.getBounds();
        this.holes = List.copyOf(conf.getHoles());
        this.humanBall = conf.getHumanBall();
        this.botBall = conf.getBotBall();
        this.smallBalls = new ArrayList<>(conf.getSmallBalls());
        this.humanScore = 0;
        this.botScore = 0;
        this.gameOver = false;
        this.winner = "";
        this.fps = 0;
    }

    public void addObserver(PooolObserver o) {
        synchronized (this) {
            observers.add(o);
        }
    }

    /* ---------------- actions from the controller / bot ---------------- */

    /**
     * Human kick: the ball is thrown in the given direction <i>only if it is
     * fully stopped</i>, otherwise the command is silently discarded.
     */
    public synchronized boolean kickHuman(V2d velocity) {
        if (gameOver || humanBall.getVel().abs() > STOPPED_THRESHOLD) {
            return false;
        }
        humanBall.setVelocity(velocity);
        return true;
    }

    /**
     * Bot throw: the velocity is set outright, <i>only if the bot ball is
     * fully stopped</i>, otherwise the command is silently discarded.
     */
    public synchronized boolean setBotVelocity(V2d v) {
        if (gameOver || botBall.getVel().abs() > STOPPED_THRESHOLD) {
            return false;
        }
        botBall.setVelocity(v);
        return true;
    }

    /**
     * Records that {@code ball} fell into a hole. Thread-safe: several
     * simulation workers may detect holes concurrently.
     */
    public synchronized void handleHole(Ball ball) {
        if (ball.isHoled() || gameOver) {
            return;
        }
        ball.markHoled();

        if (ball.getType() == BallType.SMALL) {
            BallType touch = ball.getLastTouchedBy();
            long age = System.currentTimeMillis() - ball.getLastTouchTime();
            if (touch == BallType.HUMAN && age <= SCORE_WINDOW_MS) {
                humanScore++;
            } else if (touch == BallType.BOT && age <= SCORE_WINDOW_MS) {
                botScore++;
            }
            // otherwise: small ball kicked by another small ball -> no score
            if (remainingSmallBalls() == 0) {
                gameOver = true;
                if (humanScore > botScore) {
                    winner = "Human wins " + humanScore + " - " + botScore + "!";
                } else if (botScore > humanScore) {
                    winner = "Bot wins " + botScore + " - " + humanScore + "!";
                } else {
                    winner = "Draw " + humanScore + " - " + botScore + "!";
                }
            }
        } else if (ball.getType() == BallType.HUMAN) {
            gameOver = true;
            winner = "Bot wins - the human ball fell into a hole!";
        } else {
            gameOver = true;
            winner = "Human wins - the bot ball fell into a hole!";
        }
    }

    /* ---------------- read-only access (also synchronized) ---------------- */

    public synchronized Boundary getBounds() {
        return bounds;
    }

    public synchronized List<Hole> getHoles() {
        return holes;
    }

    public synchronized Ball getHumanBall() {
        return humanBall;
    }

    public synchronized Ball getBotBall() {
        return botBall;
    }

    public synchronized List<Ball> getSmallBalls() {
        return smallBalls;
    }

    public synchronized int getHumanScore() {
        return humanScore;
    }

    public synchronized int getBotScore() {
        return botScore;
    }

    public synchronized boolean isGameOver() {
        return gameOver;
    }

    public synchronized String getWinner() {
        return winner;
    }

    public synchronized void setFps(int fps) {
        this.fps = fps;
    }

    public synchronized int remainingSmallBalls() {
        int n = 0;
        for (Ball b : smallBalls) {
            if (!b.isHoled()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Builds an immutable snapshot for the view. The ball list is never
     * structurally modified after {@link #init} (holed balls are flagged, not
     * removed).
     */
    public synchronized GameSnapshot getSnapshot() {
        var balls = new ArrayList<BallSnapshot>(smallBalls.size() + 2);
        for (Ball b : smallBalls) {
            if (!b.isHoled()) {
                balls.add(new BallSnapshot(b.getPos(), b.getRadius(), b.getType()));
            }
        }
        if (!humanBall.isHoled()) {
            balls.add(new BallSnapshot(humanBall.getPos(), humanBall.getRadius(),
                    humanBall.getType()));
        }
        if (!botBall.isHoled()) {
            balls.add(new BallSnapshot(botBall.getPos(), botBall.getRadius(),
                    botBall.getType()));
        }
        return new GameSnapshot(List.copyOf(balls), humanScore, botScore,
                remainingSmallBalls(), gameOver, winner, fps);
    }

    /** Notifies the observers that a step completed. */
    public void fireUpdate() {
        List<PooolObserver> copy;
        synchronized (this) {
            copy = List.copyOf(observers);
        }
        for (PooolObserver o : copy) {
            o.modelUpdated(this);
        }
    }
}
