package poool.multithread.util;

/**
 * Reusable barrier: {@code N} participant threads block in
 * {@link #hitAndWaitAll()} until they have <i>all</i> reached the common
 * synchronisation point, then they are all released and the barrier can be
 * reused for the next step.
 */
public interface Barrier {

    void hitAndWaitAll() throws InterruptedException;
}
