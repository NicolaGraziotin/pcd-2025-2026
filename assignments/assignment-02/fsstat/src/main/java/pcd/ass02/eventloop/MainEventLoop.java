package pcd.ass02.eventloop;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;

/**
 * Usage: MainEventLoop &lt;dir&gt; &lt;maxFS&gt; &lt;NB&gt;
 */
public class MainEventLoop {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: MainEventLoop <dir> <maxFS> <NB>");
            System.exit(1);
        }
        String dir = args[0];
        long maxFS = Long.parseLong(args[1]);
        int nb = Integer.parseInt(args[2]);

        Vertx vertx = Vertx.vertx();
        FSStatLib lib = new FSStatLib(vertx);
        long t0 = System.currentTimeMillis();
        CountDownLatch done = new CountDownLatch(1);

        System.out.println("[event-loop] scanning " + dir + " ...");
        lib.getFSReport(dir, maxFS, nb)
            .onSuccess(r -> {
                System.out.println(r);
                System.out.println("Elapsed: " + (System.currentTimeMillis() - t0) + " ms");
                done.countDown();
            })
            .onFailure(err -> {
                System.err.println("FAILED: " + err);
                done.countDown();
            });

        if (!done.await(5, TimeUnit.MINUTES)) {
            System.err.println("Timeout waiting for report");
        }
        vertx.close();
    }
}
