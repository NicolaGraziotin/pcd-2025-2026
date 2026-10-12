package pcd.ass02.eventloop;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;

import pcd.ass02.FSReport;

/**
 * Event-loop based FS stat library (Vert.x).
 *
 * <p>All coordination state
 * lives on the Vert.x event-loop thread (confinement), so no locks are
 * needed. Parallelism comes from many outstanding async FS operations
 * ({@code readDir}/{@code props}), whose completions are serialized as
 * events on the loop. Blocking syscalls are never executed on the loop:
 * {@code readDir} and {@code props} are the async Vert.x FileSystem APIs.
 */
public class FSStatLib {

    private final Vertx vertx;
    private final FileSystem fs;

    public FSStatLib(Vertx vertx) {
        this.vertx = vertx;
        this.fs = vertx.fileSystem();
    }

    public Future<FSReport> getFSReport(String rootDir, long maxFS, int nb) {
        if (nb <= 0) throw new IllegalArgumentException("NB must be > 0");
        if (maxFS <= 0) throw new IllegalArgumentException("MaxFS must be > 0");
        if (rootDir == null) throw new IllegalArgumentException("rootDir is null");
        Promise<FSReport> promise = Promise.promise();
        Scan scan = new Scan(rootDir, maxFS, nb, promise);
        vertx.runOnContext(v -> scan.start());
        return scan.promise.future();
    }

    private final class Scan {
        final String rootDir;
        final long maxFS;
        final int nb;
        final Promise<FSReport> promise;
        final AtomicInteger pendingDirs = new AtomicInteger(0);
        final long[] bands;
        long totalFiles = 0;
        boolean settled = false;

        Scan(String rootDir, long maxFS, int nb, Promise<FSReport> promise) {
            this.rootDir = rootDir;
            this.maxFS = maxFS;
            this.nb = nb;
            this.promise = promise;
            this.bands = new long[nb + 1];
        }

        void start() {
            fs.props(rootDir).onComplete(ar -> {
                if (ar.failed()) { tryFail(ar.cause()); return; }
                FileProps p = ar.result();
                if (!p.isDirectory()) { tryFail(new IllegalArgumentException("not a directory: " + rootDir)); return; }
                visitDir(rootDir);
            });
        }

        void visitDir(String dir) {
            pendingDirs.incrementAndGet();
            fs.readDir(dir).onComplete(ar -> {
                if (ar.failed()) {
                    dirDone();
                    return;
                }
                List<String> entries = ar.result();
                if (entries == null || entries.isEmpty()) { dirDone(); return; }
                AtomicInteger remaining = new AtomicInteger(entries.size());
                for (String e : entries) {
                    fs.props(e).onComplete(par -> {
                        try {
                            if (par.succeeded()) {
                                FileProps fp = par.result();
                                if (fp.isDirectory()) {
                                    visitDir(e);
                                } else if (fp.isRegularFile()) {
                                    countFile(fp.size());
                                }
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) {
                                dirDone();
                            }
                        }
                    });
                }
            });
        }

        void countFile(long size) {
            int idx = FSReport.bandIndex(size, maxFS, nb);
            bands[idx]++;
            totalFiles++;
        }

        void dirDone() {
            if (pendingDirs.decrementAndGet() == 0) {
                checkDone();
            }
        }

        void checkDone() {
            if (pendingDirs.get() == 0) {
                tryComplete(snapshot());
            }
        }

        FSReport snapshot() {
            return new FSReport(rootDir, maxFS, nb, totalFiles, bands.clone());
        }

        void tryComplete(FSReport r) {
            if (!settled) { settled = true; promise.tryComplete(r); }
        }

        void tryFail(Throwable t) {
            if (!settled) { settled = true; promise.tryFail(t); }
        }
    }
}
