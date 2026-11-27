package poool.sequential.controller;

import java.util.List;

import poool.common.model.Ball;
import poool.common.model.Boundary;
import poool.common.model.Hole;
import poool.common.model.PooolModel;
import poool.sequential.model.Bot;

/**
 * Sequential simulation engine: it runs the whole game as a single-threaded
 * main loop, on the thread that calls {@link #run()}.
 */
public class SimulationEngine {

    private static final long TARGET_STEP_MS = 16;
    private static final long MAX_DT_MS = 50;

    private final PooolModel model;
    private final Controller controller;
    private final Bot bot;

    private long lastUpdateTime;
    private int frames;
    private long fpsTime;

    public SimulationEngine(PooolModel model, Controller controller, Bot bot) {
        this.model = model;
        this.controller = controller;
        this.bot = bot;
        this.lastUpdateTime = System.currentTimeMillis();
        this.fpsTime = lastUpdateTime;
    }

    /** Runs the game loop until the game is over. */
    public void run() throws InterruptedException {
        while (!model.isGameOver()) {
            long stepStart = System.currentTimeMillis();

            long elapsed = stepStart - lastUpdateTime;
            lastUpdateTime = stepStart;
            long dt = Math.min(Math.max(elapsed, 0), MAX_DT_MS);

            // --- human input and bot decision ---
            controller.processPending();
            bot.update(stepStart);

            // --- simulation step ---
            if (!model.isGameOver()) {
                step(dt);
            }

            // --- view notification ---
            updateFps(stepStart);
            model.fireUpdate();

            long sleepTime = TARGET_STEP_MS - (System.currentTimeMillis() - stepStart);
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
        }
    }

    /** One complete simulation step, executed sequentially. */
    private void step(long dt) {
        moveSmallBalls(dt);
        movePlayerBalls(dt);
        resolveSmallBallCollisions();
        resolvePlayerBallCollisions();
        Ball.resolveCollision(model.getHumanBall(), model.getBotBall());
    }

    private void moveSmallBalls(long dt) {
        Boundary bounds = model.getBounds();
        List<Hole> holes = model.getHoles();
        for (Ball b : model.getSmallBalls()) {
            if (b.isHoled()) {
                continue;
            }
            b.updateState(dt, bounds);
            if (b.isInHole(holes)) {
                model.handleHole(b);
            }
        }
    }

    private void movePlayerBalls(long dt) {
        Ball human = model.getHumanBall();
        Ball botBall = model.getBotBall();
        if (!human.isHoled()) {
            human.updateState(dt, model.getBounds());
            if (human.isInHole(model.getHoles())) {
                model.handleHole(human);
            }
        }
        if (!botBall.isHoled()) {
            botBall.updateState(dt, model.getBounds());
            if (botBall.isInHole(model.getHoles())) {
                model.handleHole(botBall);
            }
        }
    }

    /** Small-vs-small collisions, checked once per pair. */
    private void resolveSmallBallCollisions() {
        List<Ball> smallBalls = model.getSmallBalls();
        for (int i = 0; i < smallBalls.size(); i++) {
            Ball a = smallBalls.get(i);
            if (a.isHoled()) {
                continue;
            }
            for (int j = i + 1; j < smallBalls.size(); j++) {
                Ball b = smallBalls.get(j);
                if (!b.isHoled()) {
                    Ball.resolveCollision(a, b);
                }
            }
        }
    }

    /** Player balls vs small balls. */
    private void resolvePlayerBallCollisions() {
        Ball human = model.getHumanBall();
        Ball botBall = model.getBotBall();
        for (Ball s : model.getSmallBalls()) {
            if (!s.isHoled()) {
                Ball.resolveCollision(human, s);
                Ball.resolveCollision(botBall, s);
            }
        }
    }

    private void updateFps(long stepStart) {
        frames++;
        if (stepStart - fpsTime >= 1000) {
            model.setFps((int) (frames * 1000L / (stepStart - fpsTime)));
            frames = 0;
            fpsTime = stepStart;
        }
    }
}
