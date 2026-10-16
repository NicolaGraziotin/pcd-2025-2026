package poool.multithread;

import poool.multithread.controller.ActiveController;
import poool.multithread.controller.SimulationEngine;
import poool.multithread.model.BoardConf;
import poool.multithread.model.Bot;
import poool.multithread.model.LargeBoardConf;
import poool.multithread.model.MassiveBoardConf;
import poool.multithread.model.MinimalBoardConf;
import poool.multithread.model.PooolModel;
import poool.multithread.view.View;
import poool.multithread.view.ViewModel;

/**
 * Entry point of the multithreaded version of the Poool game (assignment-01,
 * version 1: plain multithreaded programming with default/platform threads).
 *
 * <p>Architecture (MVC, mirroring {@code pcd.sketch02.Sketch02}):
 * <ul>
 *   <li><b>model</b> - {@link PooolModel}, a monitor holding balls, holes,
 *   scores and game-over state;</li>
 *   <li><b>view</b> - {@link View} + {@link ViewModel}, observing the model
 *   and rendering it with Swing;</li>
 *   <li><b>controller</b> - {@link ActiveController}, serialising the
 *   asynchronous human input (arrow keys) through a bounded-buffer monitor,
 *   plus the {@link SimulationEngine} with its pool of persistent worker
 *   threads synchronised by a reusable barrier monitor;</li>
 *   <li><b>bot</b> - {@link Bot}, an autonomous thread throwing the bot ball
 *   towards the nearest small ball.</li>
 * </ul>
 *
 * <p>Usage: {@code Main [minimal|large|massive] [nWorkers]}
 */
public class Main {

    public static void main(String[] argv) {
        BoardConf conf = parseConf(argv.length > 0 ? argv[0] : "large");
        int nWorkers = parseWorkers(argv.length > 1 ? argv[1] : null);

        var model = new PooolModel();
        model.init(conf);

        var controller = new ActiveController(model);

        var viewModel = new ViewModel();
        var view = new View(viewModel, controller, model.getBounds(), model.getHoles());
        model.addObserver(view);

        controller.start();

        var engine = new SimulationEngine(model, nWorkers);
        engine.start();

        var bot = new Bot(model, 1500);
        bot.start();

        view.display();

        System.out.println("[ Poool ] board=" + conf.getClass().getSimpleName()
                + ", smallBalls=" + model.getSmallBalls().size()
                + ", workers=" + engine.getWorkerCount());
        System.out.println("[ Poool ] Use the ARROW keys to kick the blue (human) ball.");
    }

    private static BoardConf parseConf(String name) {
        return switch (name.toLowerCase()) {
            case "minimal" -> new MinimalBoardConf();
            case "massive" -> new MassiveBoardConf();
            default -> new LargeBoardConf();
        };
    }

    private static int parseWorkers(String arg) {
        if (arg != null) {
            try {
                int n = Integer.parseInt(arg);
                if (n > 0) {
                    return n;
                }
            } catch (NumberFormatException ex) {
                // fall through to default
            }
        }
        return Runtime.getRuntime().availableProcessors();
    }
}
