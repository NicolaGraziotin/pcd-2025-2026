package ass04;

import java.io.IOException;

/**
 * High-level middleware that lets a process execute code inside a distributed
 * critical section.
 */
public interface Middleware extends AutoCloseable {

    void enterCS() throws InterruptedException, IOException;

    void exitCS() throws InterruptedException, IOException;

    @Override
    void close();
}
