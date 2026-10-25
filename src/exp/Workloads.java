package exp;

import java.util.SplittableRandom;

/**
 * Search sequences. Names: uniform, zipf, hotX_Y (X% of searches go to Y% of the keys, e.g. hot99_1),
 * shiftX_Y (as hotX_Y but a new random hot set is chosen every 'shiftEvery' searches).
 * Keys are 0..n-1; popularity ranks are mapped to a random permutation of the keys.
 */
public final class Workloads {
    private Workloads() {}

    public static int[] generate(String name, int n, int m, long seed, int shiftEvery) {
        SplittableRandom r = new SplittableRandom(seed);
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        shuffle(perm, r);
        int[] out = new int[m];

        if (name.equals("uniform")) {
            for (int i = 0; i < m; i++) out[i] = perm[r.nextInt(n)];
        } else if (name.equals("zipf")) {
            double[] cdf = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) { sum += 1.0 / (i + 1); cdf[i] = sum; }
            for (int i = 0; i < m; i++) {
                double u = r.nextDouble() * sum;
                int lo = 0, hi = n - 1;
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (cdf[mid] < u) lo = mid + 1; else hi = mid;
                }
                out[i] = perm[lo];
            }
        } else if (name.startsWith("hot") || name.startsWith("shift")) {
            boolean shifting = name.startsWith("shift");
            String[] parts = name.substring(shifting ? 5 : 3).split("_");
            int hotShare = Integer.parseInt(parts[0]);
            int hotKeys = Math.max(1, (int) ((long) n * Integer.parseInt(parts[1]) / 100));
            for (int i = 0; i < m; i++) {
                if (shifting && i > 0 && i % shiftEvery == 0) shuffle(perm, r);
                int idx = (r.nextInt(100) < hotShare) ? r.nextInt(hotKeys) : hotKeys + r.nextInt(n - hotKeys);
                out[i] = perm[idx];
            }
        } else {
            throw new IllegalArgumentException("unknown workload: " + name);
        }
        return out;
    }

    public static void shuffle(int[] a, SplittableRandom r) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
