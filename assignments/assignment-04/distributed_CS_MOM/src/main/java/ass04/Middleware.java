package ass04;

import java.io.IOException;

/**
 * High-level middleware that lets a process execute code inside a distributed
 * critical section.
 *
 * <p>A process only needs its own identifier and the two operations below: all
 * the coordination with the other processes is hidden by the implementation.</p>
 */
public interface Middleware extends AutoCloseable {

    /**
     * Blocks until this process is allowed to enter the critical section.
     *
     * <p>Every successful call must be paired with a call to {@link #exitCS()}.</p>
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws IOException          if the middleware cannot communicate with the broker
     */
    void enterCS() throws InterruptedException, IOException;

    /**
     * Leaves the critical section and lets other waiting processes proceed.
     *
     * @throws InterruptedException if the calling thread is interrupted
     * @throws IOException          if the middleware cannot communicate with the broker
     */
    void exitCS() throws InterruptedException, IOException;

    /**
     * Leaves the distributed system and releases the resources used by the
     * middleware. Calling this method more than once has no effect.
     */
    @Override
    void close();
}
