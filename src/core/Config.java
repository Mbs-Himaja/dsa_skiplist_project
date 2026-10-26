package core;


public final class Config {
    private Config() {}

    public static final int DELTA = 0;            
    public static final int MIN_COUNT = 4;        
    public static final int EPOCH_FACTOR = 2;     
    public static final int WINDOW = 50_000;      
    public static final int HISTORY = 20;         
    public static final double TRIGGER = 1.05;    

    public static final int N = 100_000;
    public static final int STEADY_M = 2_000_000;
    public static final int SHIFT_M = 5_000_000;
    public static final int SHIFT_EVERY = 1_000_000;

    // Seeds: tune on 1..3, report on 21..30
    public static final long TUNE_SEED_FIRST = 1;
    public static final long TEST_SEED_FIRST = 21;
    public static final int TEST_SEED_COUNT = 10;
}
