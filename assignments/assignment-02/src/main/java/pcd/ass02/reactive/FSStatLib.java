package pcd.ass02.reactive;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import pcd.ass02.FSReport;

/**
 * Reactive FS stat library (RxJava 3).
 *
 * <p>The directory tree becomes a
 * {@code Flowable<Path>}, file sizes a {@code Flowable<Long>}, the report a
 * {@code Single<FSReport>} reduction. No explicit threads, locks or counters:
 * concurrency is declared with {@code subscribeOn}/{@code flatMap} and
 * parallelism is delegated to {@code Schedulers.io()} (unbounded pool tuned
 * for blocking I/O). Aggregation via {@code collect}/{@code scan} is safe
 * without locks because Rx serializes downstream {@code onNext} calls.
 * Backpressure ({@code BUFFER}) absorbs bursts when traversal outruns stat.
 * Cancellation = dispose; progress = throttled snapshots of the scan.
 */
public class FSStatLib {

    public Single<FSReport> getFSReport(String rootDir, long maxFS, int nb) {
        try {
            checkArgs(rootDir, maxFS, nb);
        } catch (IllegalArgumentException e) {
            return Single.error(e);
        }
        return listFilesRecursive(rootDir)
            .flatMapSingle(path -> statSize(path).onErrorReturnItem(-1L))
            .filter(size -> size >= 0)
            .collect(() -> new Accumulator(rootDir, maxFS, nb),
                    Accumulator::add)
            .map(Accumulator::snapshot);
    }

    Flowable<Path> listFilesRecursive(String rootDir) {
        return Flowable.<Path>create(emitter -> {
            Path root = Paths.get(rootDir);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                emitter.onError(new IllegalArgumentException("not a directory: " + rootDir));
                return;
            }
            Deque<Path> stack = new ArrayDeque<>();
            stack.push(root);
            try {
                while (!stack.isEmpty()) {
                    if (emitter.isCancelled()) return;
                    Path dir = stack.pop();
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                        for (Path p : ds) {
                            if (emitter.isCancelled()) return;
                            try {
                                if (Files.isSymbolicLink(p)) continue;
                                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                                    stack.push(p);
                                } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                                    emitter.onNext(p);
                                }
                            } catch (SecurityException ignored) { }
                        }
                    } catch (IOException | SecurityException ignored) { }
                }
                emitter.onComplete();
            } catch (Throwable t) {
                if (!emitter.isCancelled()) emitter.onError(t);
            }
        }, BackpressureStrategy.BUFFER).subscribeOn(Schedulers.io());
    }

    Single<Long> statSize(Path path) {
        return Single.fromCallable(() -> Files.size(path))
                     .subscribeOn(Schedulers.io());
    }

    private static void checkArgs(String rootDir, long maxFS, int nb) {
        if (rootDir == null) throw new IllegalArgumentException("rootDir is null");
        if (nb <= 0) throw new IllegalArgumentException("NB must be > 0");
        if (maxFS <= 0) throw new IllegalArgumentException("MaxFS must be > 0");
    }

    static final class Accumulator {
        final String rootDir;
        final long maxFS;
        final int nb;
        long total = 0;
        final long[] bands;

        Accumulator(String rootDir, long maxFS, int nb) {
            this.rootDir = rootDir;
            this.maxFS = maxFS;
            this.nb = nb;
            this.bands = new long[nb + 1];
        }

        void add(long size) {
            bands[FSReport.bandIndex(size, maxFS, nb)]++;
            total++;
        }

        FSReport snapshot() {
            return new FSReport(rootDir, maxFS, nb, total, bands.clone());
        }
    }
}
