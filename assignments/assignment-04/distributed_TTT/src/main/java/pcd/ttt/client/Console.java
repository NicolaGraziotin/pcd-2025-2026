package pcd.ttt.client;

/**
 * Small synchronized console wrapper.
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
