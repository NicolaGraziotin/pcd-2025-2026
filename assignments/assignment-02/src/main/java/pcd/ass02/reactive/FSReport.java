package pcd.ass02.reactive;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable report.
 */
public final class FSReport {
    private final String rootDir;
    private final long maxFS;
    private final int nb;
    private final long totalFiles;
    private final long[] bands;

    public FSReport(String rootDir, long maxFS, int nb, long totalFiles, long[] bands) {
        if (nb <= 0) throw new IllegalArgumentException("NB must be > 0");
        if (maxFS <= 0) throw new IllegalArgumentException("MaxFS must be > 0");
        Objects.requireNonNull(rootDir);
        Objects.requireNonNull(bands);
        if (bands.length != nb + 1) throw new IllegalArgumentException("bands.length must be NB+1");
        this.rootDir = rootDir;
        this.maxFS = maxFS;
        this.nb = nb;
        this.totalFiles = totalFiles;
        this.bands = bands.clone();
    }

    public static int bandIndex(long size, long maxFS, int nb) {
        if (size < 0) size = 0;
        if (size > maxFS) return nb;
        int idx = (int) Math.min((size * (double) nb) / (double) maxFS, nb - 1);
        return Math.max(idx, 0);
    }

    public long maxFS() { return maxFS; }
    public int nb() { return nb; }
    public long totalFiles() { return totalFiles; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        double w = (double) maxFS / nb;
        for (int i = 0; i < nb; i++) {
            long lo = (long) (i * w);
            String range = (i < nb - 1)
                ? "[" + lo + ", " + ((long) ((i + 1) * w)) + ")"
                : "[" + lo + ", " + maxFS + "]";
            sb.append(String.format("  band %2d %-18s : %d%n", i, range, bands[i]));
        }
        sb.append(String.format("  band %2d %-18s : %d%n", nb, "(" + maxFS + ", +inf)", bands[nb]));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FSReport other)) return false;
        return totalFiles == other.totalFiles && maxFS == other.maxFS && nb == other.nb
            && rootDir.equals(other.rootDir) && Arrays.equals(bands, other.bands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootDir, maxFS, nb, totalFiles, Arrays.hashCode(bands));
    }
}
