package core;
import exp.SaslStyleSkipList;
import java.util.SplittableRandom;
import java.util.TreeSet;

public final class ValidateTest {
    public static void main(String[] args) {
        int range = 4000;
        int ops = 600_000;
        String[] names = {"V0 plain", "V1 counters", "V2 scheduled aging", "V4 SASL-style", "V5 ours"};
        for (int v = 0; v < names.length; v++) {
            SkipListBase list;
            switch (v) {
                case 0: list = new PlainSkipList(range, 5); break;
                case 1: list = new CounterSkipList(range, 5, false, 0, Config.DELTA, Config.MIN_COUNT); break;
                case 2: list = new CounterSkipList(range, 5, true, 8000, Config.DELTA, Config.MIN_COUNT); break;
                case 3: list = new SaslStyleSkipList(range, 5); break;
                default: list = new OursSkipList(range, 5, Config.DELTA, Config.MIN_COUNT, 2000, 20, 1.02); break;
            }
            TreeSet<Integer> ref = new TreeSet<>();
            SplittableRandom r = new SplittableRandom(123);
            for (int i = 0; i < ops; i++) {
                int k = (r.nextInt(100) < 90) ? r.nextInt(40) : r.nextInt(range);
                if (i % 150_000 == 0 && i > 0) k = range - 1 - k;     
                int op = r.nextInt(100);
                if (op < 4) {
                    list.insert(k);
                    ref.add(k);
                } else if (op < 8) {
                    boolean got = list.delete(k);
                    boolean want = ref.remove(k);
                    if (got != want) fail(names[v], i, "delete result");
                } else {
                    list.access(k);
                    if (list.wasFound() != ref.contains(k)) fail(names[v], i, "search result");
                }
                if (list.size() != ref.size()) fail(names[v], i, "size");
                if (i % 20_000 == 0 && !list.validate()) fail(names[v], i, "structure");
            }
            if (!list.validate()) fail(names[v], ops, "structure at end");
            System.out.printf("%-20s OK  (keys=%d, promotions=%d, demotions=%d)%n", names[v], ref.size(), list.promotions, list.demotions);
        }
    }

    private static void fail(String name, int op, String what) {
        throw new IllegalStateException(name + " failed at operation " + op + ": " + what);
    }
}
