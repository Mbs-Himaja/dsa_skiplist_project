package core;

/**
 * V5 (proposed method): counters as in V1, plus shift-triggered aging.
 * After every 'window' searches the window's mean hops is compared with the best mean among the last
 * 'history' windows; if it is more than 'trigger' times worse, all counters are halved.
 */
public class OursSkipList extends CounterSkipList {
    private final int window;
    private final double trigger;
    private final double[] hist;
    private int histCount = 0, histHead = 0;
    private long winSum = 0;
    private int winCnt = 0;

    public OursSkipList(int capacity, long seed, int delta, int minCount, int window, int history, double trigger) {
        super(capacity, seed, false, 0, delta, minCount);
        this.window = window;
        this.trigger = trigger;
        this.hist = new double[history];
    }

    @Override
    public int access(int k) {
        int hops = super.access(k);
        if (wasFound()) monitor(hops);
        return hops;
    }

    private void monitor(int hops) {
        winSum += hops;
        if (++winCnt < window) return;
        double mean = (double) winSum / window;
        winSum = 0;
        winCnt = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < histCount; i++) best = Math.min(best, hist[i]);
        if (histCount > 0 && mean > trigger * best) age();
        hist[histHead] = mean;
        histHead = (histHead + 1) % hist.length;
        if (histCount < hist.length) histCount++;
    }
}
