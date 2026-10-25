package core;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Array-based skip list shared by every variant.
 * Slot 0 is the header. Lanes are numbered 0..MAXL-1 in the code; a node's "height" is the number of lanes it occupies.
 * A "hop" is one pointer followed, to the right or down. A search stops as soon as the key is met in any lane.
 */
public abstract class SkipListBase {
    public static final int MAXL = 24;

    protected final int[] key, base, height, count;
    protected final int[] nxt;                 // nxt[node * MAXL + lane]; -1 means no next node
    protected int used = 1;                    // slots handed out so far (slot 0 is the header)
    private final int[] freeSlots;
    private int freeTop = 0;
    protected int n = 0;                       // number of keys stored
    protected int top = 1;                     // number of lanes currently in use
    protected final SplittableRandom rng;
    protected final int[] pred = new int[MAXL];       // predecessors saved by search()
    protected final int[] predFull = new int[MAXL];   // predecessors saved by a full descent
    protected int lastHops;
    protected boolean lastFound;
    public long promotions, demotions;

    protected SkipListBase(int capacity, long seed) {
        key = new int[capacity + 1];
        base = new int[capacity + 1];
        height = new int[capacity + 1];
        count = new int[capacity + 1];
        nxt = new int[(capacity + 1) * MAXL];
        freeSlots = new int[capacity + 1];
        Arrays.fill(nxt, -1);
        key[0] = Integer.MIN_VALUE;
        base[0] = MAXL;
        height[0] = MAXL;
        rng = new SplittableRandom(seed);
    }

    /** One search (and, in adaptive variants, the adaptation that follows). Returns the hops used. */
    public abstract int access(int k);

    public boolean wasFound() { return lastFound; }
    public int size() { return n; }

    protected int randomHeight() {
        int h = 1;
        while (h < MAXL && rng.nextBoolean()) h++;
        return h;
    }

    public void insert(int k) {
        int x = 0;
        for (int lane = MAXL - 1; lane >= 0; lane--) {
            int nx;
            while ((nx = nxt[x * MAXL + lane]) != -1 && key[nx] < k) x = nx;
            predFull[lane] = x;
        }
        int c = nxt[predFull[0] * MAXL];
        if (c != -1 && key[c] == k) return;                  // already present
        int id = (freeTop > 0) ? freeSlots[--freeTop] : used++;
        key[id] = k;
        base[id] = height[id] = randomHeight();
        count[id] = 0;
        for (int lane = 0; lane < height[id]; lane++) {
            nxt[id * MAXL + lane] = nxt[predFull[lane] * MAXL + lane];
            nxt[predFull[lane] * MAXL + lane] = id;
        }
        if (height[id] > top) top = height[id];
        n++;
    }

    public boolean delete(int k) {
        fullDescent(k, predFull);
        int c = nxt[predFull[0] * MAXL];
        if (c == -1 || key[c] != k) return false;
        onRemoving(c);
        for (int lane = 0; lane < height[c]; lane++) {
            nxt[predFull[lane] * MAXL + lane] = nxt[c * MAXL + lane];
        }
        Arrays.fill(nxt, c * MAXL, (c + 1) * MAXL, -1);
        height[c] = 0;
        count[c] = 0;
        freeSlots[freeTop++] = c;
        n--;
        while (top > 1 && nxt[top - 1] == -1) top--;         // header is slot 0
        return true;
    }

    /** Search with early exit. Fills pred[] for the lanes visited and sets lastHops / lastFound. */
    protected final int search(int k) {
        int hops = 0;
        int x = 0;
        for (int lane = top - 1; lane >= 0; lane--) {
            int nx;
            while ((nx = nxt[x * MAXL + lane]) != -1 && key[nx] < k) { x = nx; hops++; }
            pred[lane] = x;
            nx = nxt[x * MAXL + lane];
            hops++;
            if (nx != -1 && key[nx] == k) { lastHops = hops; lastFound = true; return nx; }
        }
        lastHops = hops;
        lastFound = false;
        return -1;
    }

    /** Descent through every lane without early exit; used when a node must be unlinked. */
    protected final void fullDescent(int k, int[] out) {
        int x = 0;
        for (int lane = top - 1; lane >= 0; lane--) {
            int nx;
            while ((nx = nxt[x * MAXL + lane]) != -1 && key[nx] < k) x = nx;
            out[lane] = x;
        }
    }

    /** Raise node u to the given height, one lane at a time, using the predecessors saved by the last search(). */
    protected final void promote(int u, int target) {
        while (height[u] < target) {
            int lane = height[u];
            if (lane >= top) { pred[lane] = 0; top = lane + 1; }
            nxt[u * MAXL + lane] = nxt[pred[lane] * MAXL + lane];
            nxt[pred[lane] * MAXL + lane] = u;
            height[u]++;
            promotions++;
            onPromoted(u);
        }
    }

    /** Lower node v to the given height by removing its top lanes. */
    protected final void demote(int v, int target) {
        fullDescent(key[v], predFull);
        for (int lane = height[v] - 1; lane >= target; lane--) {
            nxt[predFull[lane] * MAXL + lane] = nxt[v * MAXL + lane];
            nxt[v * MAXL + lane] = -1;
            demotions++;
        }
        height[v] = target;
        while (top > 1 && nxt[top - 1] == -1) top--;
    }

    protected void onPromoted(int u) {}
    protected void onRemoving(int u) {}

    /** Structural check: sorted lanes, contiguous heights, base <= height, every key present. */
    public boolean validate() {
        int[] seen = new int[used];
        for (int lane = 0; lane < MAXL; lane++) {
            int x = nxt[lane];
            int prev = Integer.MIN_VALUE;
            int c = 0;
            while (x != -1) {
                if (key[x] <= prev) return false;
                prev = key[x];
                if (height[x] <= lane) return false;
                seen[x]++;
                c++;
                x = nxt[x * MAXL + lane];
            }
            if (lane == 0 && c != n) return false;
        }
        for (int i = 1; i < used; i++) {
            if (height[i] == 0) continue;                    // freed slot
            if (seen[i] != height[i]) return false;
            if (height[i] < base[i]) return false;
        }
        return true;
    }
}
