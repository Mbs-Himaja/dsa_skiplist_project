package core;

/** All experiment parameters in one place, so both team members use the same values. */
public final class Config {
    private Config() {}

    // Skip list / method parameters (chosen on tuning seeds 1-3)
    public static final int DELTA = 0;            // margin subtracted in the height formula
    public static final int MIN_COUNT = 4;        // hits needed before a key can be promoted
    public static final int EPOCH_FACTOR = 2;     // scheduled aging (V2): halve every EPOCH_FACTOR * n recorded searches
    public static final int WINDOW = 50_000;      // searches per monitoring window (V5) and per plotted point
    public static final int HISTORY = 20;         // windows remembered by the trigger (V5)
    public static final double TRIGGER = 1.05;    // V5 ages when window mean > TRIGGER * best recent mean

    // Experiment sizes
    public static final int N = 100_000;
    public static final int STEADY_M = 2_000_000;
    public static final int SHIFT_M = 5_000_000;
    public static final int SHIFT_EVERY = 1_000_000;

    // Seeds: tune on 1..3, report on 21..30
    public static final long TUNE_SEED_FIRST = 1;
    public static final long TEST_SEED_FIRST = 21;
    public static final int TEST_SEED_COUNT = 10;
}
