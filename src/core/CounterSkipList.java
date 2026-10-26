package core;

public class CounterSkipList extends SkipListBase {
    protected final int delta;
    protected final int minCount;
    private final boolean scheduled;
    private final long epoch;
    protected long total = 0;                    
    private final boolean[] inPromoted;
    private final int[] promoted;
    private int promotedCount = 0;

    public CounterSkipList(int capacity, long seed, boolean scheduledAging, long epoch, int delta, int minCount) {
        super(capacity, seed);
        this.scheduled = scheduledAging;
        this.epoch = epoch;
        this.delta = delta;
        this.minCount = minCount;
        this.inPromoted = new boolean[capacity + 1];
        this.promoted = new int[capacity + 1];
    }

    protected int targetHeight(int u) {
        if (total <= 0 || count[u] < minCount) return base[u];
        long x = (long) n * count[u] / total;
        if (x < 1) return base[u];
        int t = 1 + (63 - Long.numberOfLeadingZeros(x)) - delta;
        t = Math.min(t, MAXL);
        return Math.max(base[u], t);
    }

    @Override
    public int access(int k) {
        int u = search(k);
        int hops = lastHops;
        if (u < 0) return hops;
        count[u]++;
        total++;
        promote(u, targetHeight(u));
        if (scheduled && total >= epoch) age();
        return hops;
    }

    protected void age() {
        for (int i = 1; i < used; i++) count[i] >>= 1;
        total >>= 1;
        int keep = 0;
        for (int i = 0; i < promotedCount; i++) {
            int v = promoted[i];
            int t = targetHeight(v);
            if (height[v] > t) demote(v, t);
            if (height[v] == base[v]) inPromoted[v] = false;
            else promoted[keep++] = v;
        }
        promotedCount = keep;
    }

    @Override
    protected void onPromoted(int u) {
        if (!inPromoted[u]) {
            inPromoted[u] = true;
            promoted[promotedCount++] = u;
        }
    }

    @Override
    protected void onRemoving(int u) {
        if (!inPromoted[u]) return;
        inPromoted[u] = false;
        for (int i = 0; i < promotedCount; i++) {
            if (promoted[i] == u) { promoted[i] = promoted[--promotedCount]; break; }
        }
    }
}
