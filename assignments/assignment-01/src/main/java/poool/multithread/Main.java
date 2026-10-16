package poool.multithread;

import java.awt.Component;

import poool.multithread.view.ConfigDialog;

/**
 * Entry point of the multithreaded version of the Poool game (assignment-01,
 * version 1: plain multithreaded programming with default/platform threads).
 *
 * <p>Flow: a configuration popup ({@link ConfigDialog}) is shown first (and
 * again after every finished game); each confirmed selection starts a new
 * {@link GameSession}. Closing the popup quits the application.
 */
public class Main {

    public static void main(String[] argv) {
        Component parent = null;
        GameSession session = null;
        try {
            while (true) {
                var selection = ConfigDialog.showDialog(parent);
                if (selection == null) {
                    break;
                }
                if (session != null) {
                    session.shutdown();
                }
                session = new GameSession(selection);
                parent = session.frame();
                session.awaitGameOver();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (session != null) {
                session.shutdown();
            }
        }
        System.exit(0);
    }
}
