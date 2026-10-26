package exp;
import core.Config;
import core.CounterSkipList;
import core.OursSkipList;
import core.PlainSkipList;
import core.SkipListBase;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public final class Runner {
    static final String[] VARIANTS = {"V0", "V1", "V2", "V4", "V5"};
    static final String[] LABELS = {"Plain skip list", "Counters, no aging", "Counters + scheduled aging",
            "SASL-style", "Ours (shift-triggered aging)"};

    static final class Result {
        String variant; long seed;
        double hopsAll, hopsLast, nsPer, promoPer1k, demoPer1k;
        double[] windows;
    }

    static SkipListBase make(String variant, int n, long seed) {
        long s = seed * 7919 + 1;
        switch (variant) {
            case "V0": return new PlainSkipList(n, s);
            case "V1": return new CounterSkipList(n, s, false, 0, Config.DELTA, Config.MIN_COUNT);
            case "V2": return new CounterSkipList(n, s, true, (long) Config.EPOCH_FACTOR * n, Config.DELTA, Config.MIN_COUNT);
            case "V4": return new SaslStyleSkipList(n, s);
            case "V5": return new OursSkipList(n, s, Config.DELTA, Config.MIN_COUNT, Config.WINDOW, Config.HISTORY, Config.TRIGGER);
            default: throw new IllegalArgumentException(variant);
        }
    }

    static Result runOne(String variant, int n, int[] seq, long seed, boolean wantWindows) {
        SkipListBase list = make(variant, n, seed);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Workloads.shuffle(order, new SplittableRandom(seed * 31 + 5));
        for (int k : order) list.insert(k);
        if (!list.validate()) throw new IllegalStateException("invalid after build: " + variant);

        int m = seq.length;
        int win = Config.WINDOW;
        double[] windows = wantWindows ? new double[m / win] : null;
        long total = 0, last = 0, winSum = 0;
        int lastStart = m - m / 4;
        long t0 = System.nanoTime();
        for (int i = 0; i < m; i++) {
            int h = list.access(seq[i]);
            total += h;
            if (i >= lastStart) last += h;
            if (wantWindows) {
                winSum += h;
                if ((i + 1) % win == 0) { windows[(i + 1) / win - 1] = (double) winSum / win; winSum = 0; }
            }
        }
        long t1 = System.nanoTime();
        if (!list.validate()) throw new IllegalStateException("invalid after run: " + variant + " seed " + seed);

        Result r = new Result();
        r.variant = variant; r.seed = seed;
        r.hopsAll = (double) total / m;
        r.hopsLast = (double) last / (m / 4);
        r.nsPer = (double) (t1 - t0) / m;
        r.promoPer1k = 1000.0 * list.promotions / m;
        r.demoPer1k = 1000.0 * list.demotions / m;
        r.windows = windows;
        return r;
    }

    static void warmUp() {
        int n = 20_000;
        int[] seq = Workloads.generate("hot95_5", n, 300_000, 999, 100_000);
        for (String v : VARIANTS) runOne(v, n, seq, 999, false);
    }

    public static void main(String[] args) throws Exception {
        String workload = args[0];
        int n = Integer.parseInt(args[1]);
        int m = Integer.parseInt(args[2]);
        int seedCount = Integer.parseInt(args[3]);
        long firstSeed = Long.parseLong(args[4]);
        int shiftEvery = args.length > 5 ? Integer.parseInt(args[5]) : Config.SHIFT_EVERY;
        int threads = args.length > 6 ? Integer.parseInt(args[6]) : 1;
        boolean shifting = workload.startsWith("shift");

        warmUp();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Result>> jobs = new ArrayList<>();
        for (int s = 0; s < seedCount; s++) {
            final long seed = firstSeed + s;
            final int[] seq = Workloads.generate(workload, n, m, seed, shiftEvery);
            for (String v : VARIANTS) jobs.add(pool.submit(() -> runOne(v, n, seq, seed, shifting)));
        }
        List<Result> results = new ArrayList<>();
        for (Future<Result> f : jobs) results.add(f.get());
        pool.shutdown();

        Files.createDirectories(Path.of("results"));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("results/summary_" + workload + ".csv")))) {
            w.println("workload,variant,seed,hops_all,hops_last25,ns_per_search,promotions_per_1k,demotions_per_1k");
            for (Result r : results)
                w.printf(Locale.ROOT, "%s,%s,%d,%.4f,%.4f,%.1f,%.3f,%.3f%n", workload, r.variant, r.seed,
                        r.hopsAll, r.hopsLast, r.nsPer, r.promoPer1k, r.demoPer1k);
        }
        if (shifting) {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("results/windows_" + workload + ".csv")))) {
                w.println("seed,variant,window,mean_hops");
                for (Result r : results)
                    for (int i = 0; i < r.windows.length; i++)
                        w.printf(Locale.ROOT, "%d,%s,%d,%.4f%n", r.seed, r.variant, i, r.windows[i]);
            }
        }
        printSummary(workload, n, m, seedCount, results);
    }

    static void printSummary(String workload, int n, int m, int seedCount, List<Result> results) {
        System.out.printf("== %s  n=%d  m=%d  seeds=%d%n", workload, n, m, seedCount);
        System.out.printf("%-30s %10s %12s %10s %10s %10s%n", "variant", "hops", "hops(last25%)", "ns/search", "promo/1k", "demo/1k");
        Map<String, Map<Long, Double>> hopsBy = new HashMap<>();
        for (int vi = 0; vi < VARIANTS.length; vi++) {
            String v = VARIANTS[vi];
            double a = 0, b = 0, c = 0, d = 0, e = 0;
            int cnt = 0;
            for (Result r : results) if (r.variant.equals(v)) {
                a += r.hopsAll; b += r.hopsLast; c += r.nsPer; d += r.promoPer1k; e += r.demoPer1k; cnt++;
                hopsBy.computeIfAbsent(v, k -> new HashMap<>()).put(r.seed, r.hopsAll);
            }
            System.out.printf(Locale.ROOT, "%-30s %10.2f %12.2f %10.0f %10.2f %10.2f%n", LABELS[vi], a / cnt, b / cnt, c / cnt, d / cnt, e / cnt);
        }
        System.out.println("paired change in average hops (negative = fewer hops):");
        String[][] pairs = {{"V5", "V0"}, {"V5", "V1"}, {"V5", "V2"}, {"V5", "V4"}, {"V2", "V1"}};
        for (String[] p : pairs) {
            List<Double> d = new ArrayList<>();
            for (long s : hopsBy.get(p[0]).keySet()) d.add(100.0 * (hopsBy.get(p[0]).get(s) / hopsBy.get(p[1]).get(s) - 1));
            double mean = d.stream().mapToDouble(x -> x).average().orElse(0);
            double var = d.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / Math.max(1, d.size() - 1);
            System.out.printf(Locale.ROOT, "  %s vs %s: %+.2f%% (sd %.2f)%n", p[0], p[1], mean, Math.sqrt(var));
        }
    }
}
