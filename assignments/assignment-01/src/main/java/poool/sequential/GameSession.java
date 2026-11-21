package poool.sequential;

import java.awt.Component;

import poool.common.model.BoardConf;
import poool.common.model.LargeBoardConf;
import poool.common.model.MassiveBoardConf;
import poool.common.model.MinimalBoardConf;
import poool.common.model.PooolModel;
import poool.common.view.ConfigDialog;
import poool.common.view.View;
import poool.common.view.ViewModel;
import poool.sequential.controller.Controller;
import poool.sequential.controller.SimulationEngine;
import poool.sequential.model.Bot;

/**
 * A single sequential game run: wires model, view, controller, simulation
 * engine and bot together, then plays the whole game on the calling thread.
 *
 * <p>Differently from the multithreaded version, the session does not start
 * any active component: {@link #play()} simply runs the game loop until the
 * model reports the end of the game.
 */
public class GameSession {

    private final PooolModel model;
    private final Controller controller;
    private final SimulationEngine engine;
    private final Bot bot;
    private final View view;

    public GameSession(ConfigDialog.ConfigSelection sel) {
        BoardConf conf = switch (sel.board().toLowerCase()) {
            case "minimal" -> new MinimalBoardConf();
            case "massive" -> new MassiveBoardConf();
            default -> new LargeBoardConf();
        };

        model = new PooolModel();
        model.init(conf);

        controller = new Controller(model);

        var viewModel = new ViewModel();
        view = new View(viewModel, controller, model.getBounds(), model.getHoles(), "sequential");
        view.getFrame().setTitle("Poool - sequential version");
        model.addObserver(view);

        bot = new Bot(model, 1500);
        engine = new SimulationEngine(model, controller, bot);

        view.display();

        System.out.println("[ Poool ] board=" + conf.getClass().getSimpleName()
                + ", smallBalls=" + model.getSmallBalls().size()
                + ", mode=sequential");
        System.out.println("[ Poool ] Use the ARROW keys to throw the blue (human) ball.");
    }

    /** The game window, used as parent for the configuration popup. */
    public Component frame() {
        return view.getFrame();
    }

    /**
     * Plays the game synchronously: the calling thread runs the whole
     * simulation loop and returns when the game is over. The final board is
     * then kept visible for a few seconds so the result can be read before
     * going back to the configuration popup.
     */
    public void play() throws InterruptedException {
        engine.run();
        Thread.sleep(4000);
    }

    /** Disposes the game window; there are no threads to stop. */
    public void shutdown() {
        view.dispose();
    }
}
