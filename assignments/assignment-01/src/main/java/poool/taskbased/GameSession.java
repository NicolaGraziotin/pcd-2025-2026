package poool.taskbased;

import java.awt.Component;

import poool.taskbased.controller.ActiveController;
import poool.taskbased.controller.SimulationEngine;
import poool.common.model.BoardConf;
import poool.taskbased.model.Bot;
import poool.common.model.LargeBoardConf;
import poool.common.model.MassiveBoardConf;
import poool.common.model.MinimalBoardConf;
import poool.common.model.PooolModel;
import poool.common.view.ConfigDialog;
import poool.common.view.ViewModel;
import poool.common.view.View;

/**
 * A single game run: wires model, view, controller, simulation engine and bot
 * together, waits for the game to finish and can then be shut down so that a
 * new game (with a new configuration) can start.
 */
public class GameSession {

    private final PooolModel model;
    private final ActiveController controller;
    private final SimulationEngine engine;
    private final Bot bot;
    private final View view;

    public GameSession(ConfigDialog.ConfigSelection sel) {
        BoardConf conf = switch (sel.board().toLowerCase()) {
            case "minimal" -> new MinimalBoardConf();
            case "massive" -> new MassiveBoardConf();
            default -> new LargeBoardConf();
        };
        int workers = sel.workers() != null
                ? sel.workers()
                : Runtime.getRuntime().availableProcessors();

        model = new PooolModel();
        model.init(conf);

        controller = new ActiveController(model);

        var viewModel = new ViewModel();
        view = new View(viewModel, controller, model.getBounds(), model.getHoles());
        model.addObserver(view);

        engine = new SimulationEngine(model, workers);
        engine.start();

        bot = new Bot(model, 1500);
        bot.start();

        view.display();

        System.out.println("[ Poool ] board=" + conf.getClass().getSimpleName()
                + ", smallBalls=" + model.getSmallBalls().size()
                + ", workers=" + engine.getWorkerCount());
        System.out.println("[ Poool ] Use the ARROW keys to throw the blue (human) ball.");
    }

    /** The game window, used as parent for the configuration popup. */
    public Component frame() {
        return view.getFrame();
    }

    /**
     * Blocks until the game is over, then keeps the final board visible for a
     * few seconds so the result can be read before going back to the
     * configuration popup.
     */
    public void awaitGameOver() throws InterruptedException {
        while (!model.isGameOver()) {
            Thread.sleep(200);
        }
        Thread.sleep(4000);
    }

    /** Stops all the executors/threads of this session and disposes the game window. */
    public void shutdown() {
        bot.shutdown();
        engine.interrupt();
        controller.shutdown();
        try {
            engine.join(5000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        view.dispose();
    }
}

