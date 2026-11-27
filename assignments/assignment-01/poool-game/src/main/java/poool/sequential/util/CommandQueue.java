package poool.sequential.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking FIFO queue used to hand human commands from the view (Swing
 * event-dispatch thread) to the sequential game loop.
 */
public class CommandQueue<Item> {

    private final ArrayDeque<Item> items = new ArrayDeque<>();

    public synchronized void put(Item item) {
        items.addLast(item);
    }

    /** Removes and returns all the pending items, in insertion order. */
    public synchronized List<Item> drain() {
        List<Item> copy = new ArrayList<>(items);
        items.clear();
        return copy;
    }
}
