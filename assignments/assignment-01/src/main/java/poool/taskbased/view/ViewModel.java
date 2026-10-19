package poool.taskbased.view;

import java.util.List;

import poool.taskbased.model.PooolModel;

/**
 * View-side copy of the game state, mirroring
 * {@code pcd.sketch02.view.ViewModel}: it is updated from the immutable
 * {@link PooolModel.GameSnapshot} and read by the Swing panel. All methods
 * are synchronised because updates come from the simulation thread while
 * reads happen on the event-dispatch thread.
 */
public class ViewModel {

    private List<PooolModel.BallSnapshot> balls = List.of();
    private int humanScore;
    private int botScore;
    private int ballsRemaining;
    private boolean gameOver;
    private String winner = "";
    private int fps;

    public synchronized void update(PooolModel.GameSnapshot snap) {
        this.balls = snap.balls();
        this.humanScore = snap.humanScore();
        this.botScore = snap.botScore();
        this.ballsRemaining = snap.ballsRemaining();
        this.gameOver = snap.gameOver();
        this.winner = snap.winner();
        this.fps = snap.fps();
    }

    public synchronized List<PooolModel.BallSnapshot> getBalls() {
        return balls;
    }

    public synchronized int getHumanScore() {
        return humanScore;
    }

    public synchronized int getBotScore() {
        return botScore;
    }

    public synchronized int getBallsRemaining() {
        return ballsRemaining;
    }

    public synchronized boolean isGameOver() {
        return gameOver;
    }

    public synchronized String getWinner() {
        return winner;
    }

    public synchronized int getFps() {
        return fps;
    }
}

