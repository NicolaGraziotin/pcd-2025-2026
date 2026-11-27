package pcd.ass02.reactive;

import pcd.ass02.FSReport;

/**
 * Usage: MainRx &lt;dir&gt; &lt;maxFS&gt; &lt;NB&gt;
 */
public class MainRx {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: MainRx <dir> <maxFS> <NB>");
            System.exit(1);
        }
        String dir = args[0];
        long maxFS = Long.parseLong(args[1]);
        int nb = Integer.parseInt(args[2]);

        FSStatLib lib = new FSStatLib();
        long t0 = System.currentTimeMillis();
        System.out.println("[rx] scanning " + dir + " ...");
        try {
            FSReport r = lib.getFSReport(dir, maxFS, nb).blockingGet();
            System.out.println(r);
            System.out.println("Elapsed: " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Exception e) {
            System.err.println("FAILED: " + e);
        }
    }
}
