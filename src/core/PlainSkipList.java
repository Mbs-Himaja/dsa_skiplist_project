package core;

public class PlainSkipList extends SkipListBase {
    public PlainSkipList(int capacity, long seed) { super(capacity, seed); }

    @Override
    public int access(int k) {
        search(k);
        return lastHops;
    }
}
