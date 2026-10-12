package pcd.ass02;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable report shared by the three FSStat versions (event-loop, reactive,
 * virtual threads): total number of regular files + size distribution.
 *
 * <p>Sharing this class is deliberate and does not violate the "one
 * discipline per version" rule: it is a pure immutable value object with no
 * concurrency logic inside. What stays strictly separate is the traversal /
 * coordination logic in each {@code FSStatLib}.
 *
 * <p>Bands: {@code NB} bands partitioning {@code [0, MaxFS]} plus one
 * overflow band for sizes {@code > MaxFS} (total {@code NB+1} counters).
 * Band {@code i} ({@code 0 <= i < NB}) is lower-inclusive / upper-exclusive,
 * except the last regular band which is inclusive on both ends:
 * <pre>
 *   i = min((int)(size * NB / MaxFS), NB - 1)  for size in [0, MaxFS]
 *   i = NB                                     for size > MaxFS
 * </pre>
 * Example MaxFS=1000, NB=4: [0,250), [250,500), [500,750), [750,1000], overflow.
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

    public String rootDir() { return rootDir; }
    public long maxFS() { return maxFS; }
    public int nb() { return nb; }
    public long totalFiles() { return totalFiles; }
    public long[] bands() { return bands.clone(); }
    public long bandCount(int i) { return bands[i]; }

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
