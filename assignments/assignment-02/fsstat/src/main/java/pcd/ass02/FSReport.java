package pcd.ass02;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable report shared by the three FSStat versions (event-loop, reactive,
 * virtual threads): total number of regular files + size distribution.
 */
public final class FSReport {
    private final String rootDir;
    private final long maxFS;
    private final int nb;
    private final long totalFiles;
    private final long[] bands;

    public FSReport(String rootDir, long maxFS, int nb, long totalFiles, long[] bands) {
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

    public long getTotalFiles() { return totalFiles; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FSReport(root=").append(rootDir)
          .append(", totalFiles=").append(totalFiles)
          .append(", MaxFS=").append(maxFS).append(", NB=").append(nb).append(")\n");
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
