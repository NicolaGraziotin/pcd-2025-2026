package poool.multithread.util;

/**
 * Bounded buffer used to exchange commands between the view (producer, running
 * on the Swing EDT) and the active controller (consumer).
 */
public interface BoundedBuffer<Item> {

    void put(Item item) throws InterruptedException;

    Item get() throws InterruptedException;
}
