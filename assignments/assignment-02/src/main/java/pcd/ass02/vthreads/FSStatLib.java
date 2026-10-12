package pcd.ass02.vthreads;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

import pcd.ass02.FSReport;

/**
 * Virtual-threads FS stat library.
 *
 * <p>Each directory spawns one virtual thread per subdirectory on a
 * {@code newVirtualThreadPerTaskExecutor}. Blocking calls
 * ({@code newDirectoryStream}, {@code size}) simply park the virtual thread
 * (cheap), so unlike platform threads there is no pool exhaustion and no
 * pool-induced deadlock when a task joins its children. Shared counters use
 * {@code LongAdder} (lock-free, contention-friendly for thousands of
 * writers); no {@code synchronized} (which would pin the virtual thread to
 * its carrier on older JDKs) and no explicit locks at all. At the end the
 * adders are copied into an immutable {@link FSReport}.
 */
public class FSStatLib implements AutoCloseable {

    private final ExecutorService executor;

    public FSStatLib() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /** One-shot async report. */
    public CompletableFuture<FSReport> getFSReport(String rootDir, long maxFS, int nb) {
        if (rootDir == null) throw new IllegalArgumentException("rootDir is null");
        if (nb <= 0) throw new IllegalArgumentException("NB must be > 0");
        if (maxFS <= 0) throw new IllegalArgumentException("MaxFS must be > 0");
        Scan scan = new Scan(Paths.get(rootDir), rootDir, maxFS, nb);
        return CompletableFuture.supplyAsync(scan::run, executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private final class Scan {
        final Path root;
        final String rootDir;
        final long maxFS;
        final int nb;
        final LongAdder total = new LongAdder();
        final LongAdder[] bands;

        Scan(Path root, String rootDir, long maxFS, int nb) {
            this.root = root;
            this.rootDir = rootDir;
            this.maxFS = maxFS;
            this.nb = nb;
            this.bands = new LongAdder[nb + 1];
            for (int i = 0; i < bands.length; i++) bands[i] = new LongAdder();
        }

        FSReport run() {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("not a directory: " + rootDir);
            }
            scanDir(root);
            return snapshot();
        }

        void scanDir(Path dir) {
            if (Thread.currentThread().isInterrupted()) return;
            List<Future<?>> children = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    if (Thread.currentThread().isInterrupted()) break;
                    try {
                        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                            children.add(executor.submit(() -> { scanDir(p); return null; }));
                        } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                            try {
                                long size = Files.size(p);
                                bands[FSReport.bandIndex(size, maxFS, nb)].increment();
                                total.increment();
                            } catch (IOException ignored) { }
                        }
                    } catch (SecurityException ignored) { }
                }
            } catch (IOException | SecurityException ignored) { }
            for (Future<?> f : children) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) { }
            }
        }

        FSReport snapshot() {
            long[] copy = new long[nb + 1];
            for (int i = 0; i < copy.length; i++) copy[i] = bands[i].sum();
            return new FSReport(rootDir, maxFS, nb, total.sum(), copy);
        }
    }
}
