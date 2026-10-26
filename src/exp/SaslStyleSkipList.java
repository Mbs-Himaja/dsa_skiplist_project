package exp;
import core.SkipListBase;

public class SaslStyleSkipList extends SkipListBase {
    private static final int BANDS = 3;
    private final int[] bandHeight = new int[BANDS];
    private final long[] bandCap = new long[BANDS];
    private final long[] bandSize = new long[BANDS];
    private final LongQueue[] queues = new LongQueue[BANDS];
    private final byte[] bandOf;
    private final int[] stamp;
    private int clock = 0;

    public SaslStyleSkipList(int capacity, long seed) {
        super(capacity, seed);
        bandOf = new byte[capacity + 1];
        java.util.Arrays.fill(bandOf, (byte) -1);
        stamp = new int[capacity + 1];
        double s = Math.sqrt(capacity);
        bandHeight[0] = 1 + (int) Math.floor(Math.log(s) / Math.log(2));
        bandHeight[1] = bandHeight[0] - 3;
        bandHeight[2] = bandHeight[0] - 5;
        if (bandHeight[2] < 2) { bandHeight[1] = 3; bandHeight[2] = 2; }
        bandCap[0] = (long) s;
        bandCap[1] = (long) (capacity / Math.pow(2, bandHeight[1] - 1));
        bandCap[2] = (long) (capacity / Math.pow(2, bandHeight[2] - 1));
        for (int i = 0; i < BANDS; i++) queues[i] = new LongQueue();
    }

    private static long pack(int node, int st) { return ((long) node << 32) | (st & 0xffffffffL); }
    private static int nodeOf(long e) { return (int) (e >>> 32); }
    private static int stampOf(long e) { return (int) e; }

    private void dropStale(int band) {
        LongQueue q = queues[band];
        while (!q.isEmpty()) {
            long e = q.peek();
            int v = nodeOf(e);
            if (bandOf[v] == band && stamp[v] == stampOf(e)) break;
            q.pop();
        }
    }

    @Override
    public int access(int k) {
        int u = search(k);
        int hops = lastHops;
        if (u < 0) return hops;
        clock++;
        stamp[u] = clock;
        int b = bandOf[u];
        int t0 = Math.max(base[u], bandHeight[0]);
        if (height[u] < t0) promote(u, t0);
        if (b >= 0) bandSize[b]--;
        bandOf[u] = 0;
        bandSize[0]++;
        queues[0].push(pack(u, clock));
        for (int j = 0; j < BANDS; j++) {
            while (bandSize[j] > bandCap[j]) {
                dropStale(j);
                if (queues[j].isEmpty()) break;
                long e = queues[j].pop();
                int v = nodeOf(e);
                bandSize[j]--;
                if (j + 1 < BANDS) {
                    bandOf[v] = (byte) (j + 1);
                    bandSize[j + 1]++;
                    int t = Math.max(base[v], bandHeight[j + 1]);
                    if (height[v] > t) demote(v, t);
                    queues[j + 1].push(e);
                } else {
                    bandOf[v] = -1;
                    if (height[v] > base[v]) demote(v, base[v]);
                }
            }
        }
        return hops;
    }

    @Override
    protected void onRemoving(int u) {
        if (bandOf[u] >= 0) { bandSize[bandOf[u]]--; bandOf[u] = -1; }
    }

    /** Minimal growable ring buffer of longs (avoids boxing). */
    private static final class LongQueue {
        private long[] buf = new long[1024];
        private int head = 0, size = 0;
        boolean isEmpty() { return size == 0; }
        long peek() { return buf[head]; }
        long pop() { long v = buf[head]; head = (head + 1) % buf.length; size--; return v; }
        void push(long v) {
            if (size == buf.length) {
                long[] nb = new long[buf.length * 2];
                for (int i = 0; i < size; i++) nb[i] = buf[(head + i) % buf.length];
                buf = nb;
                head = 0;
            }
            buf[(head + size) % buf.length] = v;
            size++;
        }
    }
}
