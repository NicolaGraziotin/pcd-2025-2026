package pcd.ttt.client;

/**
 * Small synchronized console wrapper.
 *
 * <p>RMI callback methods run on RMI threads, while the main thread reads
 * commands from standard input. Printing through the same monitor avoids
 * interleaved/garbled output.</p>
 */
public final class Console {

    public synchronized void print(String message) {
        System.out.println(message);
        System.out.flush();
    }

    public synchronized void prompt(String prompt) {
        System.out.print(prompt);
        System.out.flush();
    }
}
