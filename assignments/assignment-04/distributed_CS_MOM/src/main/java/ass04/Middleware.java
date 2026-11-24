package ass04;

import java.io.IOException;

/**
 * High-level middleware that lets a process execute code inside a distributed
 * critical section.
 */
public interface Middleware extends AutoCloseable {

    /**
     * Blocks until this process is allowed to enter the critical section.
     */
    void enterCS() throws InterruptedException, IOException;

    /**
     * Leaves the critical section and lets other waiting processes proceed.
     */
    void exitCS() throws InterruptedException, IOException;

    /**
     * Leaves the distributed system and releases the resources used by the
     * middleware. Calling this method more than once has no effect.
     */
    @Override
    void close();
}
