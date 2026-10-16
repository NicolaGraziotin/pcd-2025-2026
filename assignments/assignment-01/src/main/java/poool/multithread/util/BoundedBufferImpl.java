package poool.multithread.util;

import java.util.LinkedList;

/**
 * Monitor-based implementation of {@link BoundedBuffer} using raw
 * wait/notify mechanisms.
 *
 * <p>It follows the same pattern shown in the course lab on monitors
 * ({@code pcd.lab05.monitors.BoundedBufferImplRaw}): condition
 * synchronisation is expressed with guarded {@code wait()} loops and a
 * {@code notifyAll()} on every state change.
 *
 * @param <Item> type of the buffered items
 */
public class BoundedBufferImpl<Item> implements BoundedBuffer<Item> {

    private final LinkedList<Item> buffer;
    private final int maxSize;

    public BoundedBufferImpl(int size) {
        this.buffer = new LinkedList<>();
        this.maxSize = size;
    }

    @Override
    public synchronized void put(Item item) throws InterruptedException {
        while (isFull()) {
            wait();
        }
        buffer.addLast(item);
        notifyAll();
    }

    @Override
    public synchronized Item get() throws InterruptedException {
        while (isEmpty()) {
            wait();
        }
        Item item = buffer.removeFirst();
        notifyAll();
        return item;
    }

    private boolean isFull() {
        return buffer.size() == maxSize;
    }

    private boolean isEmpty() {
        return buffer.isEmpty();
    }
}
