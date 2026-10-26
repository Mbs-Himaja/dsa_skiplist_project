package core;

import java.util.Arrays;
import java.util.SplittableRandom;

public abstract class SkipListBase {
    public static final int MAXL = 24;

    protected final int[] key, base, height, count;
    protected final int[] nxt;                 
    protected int used = 1;                    
    private final int[] freeSlots;
    private int freeTop = 0;
    protected int n = 0;                       
    protected int top = 1;                     
    protected final SplittableRandom rng;
    protected final int[] pred = new int[MAXL];       
    protected final int[] predFull = new int[MAXL];   
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
        if (c != -1 && key[c] == k) return;                  
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
        while (top > 1 && nxt[top - 1] == -1) top--;        
        return true;
    }

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

    protected final void fullDescent(int k, int[] out) {
        int x = 0;
        for (int lane = top - 1; lane >= 0; lane--) {
            int nx;
            while ((nx = nxt[x * MAXL + lane]) != -1 && key[nx] < k) x = nx;
            out[lane] = x;
        }
    }

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
            if (height[i] == 0) continue;                    
            if (seen[i] != height[i]) return false;
            if (height[i] < base[i]) return false;
        }
        return true;
    }
}
