package poool.multithread.util;

/**
 * Monitor-based reusable barrier implemented with raw wait/notify mechanisms,
 * following the style of the barrier exercise in the course lab on monitors
 * ({@code pcd.lab05.monitors.ex_barrier}).
 *
 * <p>A generation counter distinguishes successive barrier rounds, so that
 * threads waking up spuriously or late cannot confuse the current round with
 * the next one.
 */
public class BarrierImpl implements Barrier {

    private final int parties;
    private int arrived;
    private int generation;

    public BarrierImpl(int parties) {
        if (parties <= 0) {
            throw new IllegalArgumentException("parties must be > 0");
        }
        this.parties = parties;
        this.arrived = 0;
        this.generation = 0;
    }

    @Override
    public synchronized void hitAndWaitAll() throws InterruptedException {
        int myGeneration = generation;
        arrived++;
        if (arrived == parties) {
            // Last arrival: reset the count, move to the next generation
            // and release everybody.
            arrived = 0;
            generation++;
            notifyAll();
        } else {
            while (myGeneration == generation) {
                wait();
            }
        }
    }
}
