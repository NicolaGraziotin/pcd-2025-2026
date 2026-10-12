package pcd.ass02.vthreads;

/**
 * Usage: MainVThreads &lt;dir&gt; &lt;maxFS&gt; &lt;NB&gt;
 */
public class MainVThreads {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: MainVThreads <dir> <maxFS> <NB>");
            System.exit(1);
        }
        String dir = args[0];
        long maxFS = Long.parseLong(args[1]);
        int nb = Integer.parseInt(args[2]);

        try (FSStatLib lib = new FSStatLib()) {
            long t0 = System.currentTimeMillis();
            System.out.println("[vthreads] scanning " + dir + " ...");
            try {
                FSReport r = lib.getFSReport(dir, maxFS, nb).join();
                System.out.println(r);
            } catch (Exception e) {
                System.err.println("FAILED: " + e);
            }
            System.out.println("Elapsed: " + (System.currentTimeMillis() - t0) + " ms");
        }
    }
}
