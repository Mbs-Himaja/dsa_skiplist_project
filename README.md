# Frequency-based adaptive skip list with aging: code

Java, no external libraries. Plots use Python 3 with matplotlib.

## Layout

| File | Purpose |
|---|---|
| `src/core/Config.java` | All parameters and seed ranges in one place |
| `src/core/SkipListBase.java` | Array-based skip list: insert, delete, search with early exit, promote, demote, validate |
| `src/core/PlainSkipList.java` | V0: ordinary skip list |
| `src/core/CounterSkipList.java` | V1: counters, no aging. V2: counters with scheduled aging |
| `src/core/OursSkipList.java` | V5: the proposed method (shift-triggered aging) |
| `src/core/ValidateTest.java` | Random insert/delete/search test against `java.util.TreeSet`, plus structure checks |
| `src/exp/Workloads.java` | Search-sequence generators (uniform, hotX_Y, zipf, shiftX_Y) |
| `src/exp/SaslStyleSkipList.java` | V4: simplified recency-based rival (our own simplification, not the original SASL) |
| `src/exp/Runner.java` | Runs every variant on the same searches, writes CSVs, prints a summary |
| `plots/make_plots.py` | Draws the figures and tables from the CSVs |
| `run_all.sh` | Build, test, run all workloads, draw figures |

The numbering skips V3 (an optional sketch-based variant that is not built).

## Build and run

    ./run_all.sh 1 3 1        # tuning run on seeds 1-3
    ./run_all.sh 21 10 1      # final run on seeds 21-30 (freeze parameters first)

Single workload:

    javac -d out $(find src -name "*.java")
    java -Xmx2g -XX:+UseSerialGC -cp out exp.Runner shift99_1 100000 5000000 10 21 1000000 1

Arguments: workload, n, searches, seed count, first seed, shift period, threads.
Run the correctness test alone with `java -cp out core.ValidateTest`.

## Outputs (in `results/`)

* `summary_<workload>.csv`: hops, ns per search, promotions and demotions per 1,000 searches, per seed and variant
* `windows_<workload>.csv`: windowed hops for the shifting workloads
* `fig1_hops_by_workload.png`, `fig2_shifting_<workload>.png`
* `paired_table.csv`, `time_and_restructuring_table.csv`

## Rules for honest results

1. Choose parameters on seeds 1-3 only; freeze them in `Config.java`; report seeds 21-30.
2. Hop counts do not depend on the number of threads. Timings do: use `threads = 1` for the numbers you quote.
3. A hop is one pointer followed (right or down). Every variant uses the same search with early exit.
4. V1 is a stand-in for counter-based promotion, and V4 is a simplified recency rule. Say so in the report.
5. The real Splay-List is not included in this code base.
