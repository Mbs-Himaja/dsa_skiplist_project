"""Reads results/summary_*.csv and results/windows_*.csv and writes figures and tables into results/.
Usage (from the project folder):  python3 plots/make_plots.py [shiftEvery] [windowSize]
"""
import csv, glob, os, sys
from collections import defaultdict
from statistics import mean, stdev
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

SHIFT_EVERY = int(sys.argv[1]) if len(sys.argv) > 1 else 1_000_000
WINDOW = int(sys.argv[2]) if len(sys.argv) > 2 else 50_000
ORDER = ["uniform", "hot90_10", "hot95_5", "hot99_1", "zipf", "shift99_1", "shift95_5"]
LABEL = {"V0": "Plain skip list", "V1": "Counters, no aging", "V2": "Counters + scheduled aging",
         "V4": "SASL-style", "V5": "Ours (shift-triggered aging)"}
COLOR = {"V0": "#9e9e9e", "V1": "#f2a900", "V2": "#6aa84f", "V4": "#cc4125", "V5": "#1c4587"}
PRETTY = {"uniform": "Uniform", "hot90_10": "90-10", "hot95_5": "95-5", "hot99_1": "99-1", "zipf": "Zipf",
          "shift99_1": "Shifting 99-1", "shift95_5": "Shifting 95-5"}

# ---------- summary files ----------
rows = defaultdict(lambda: defaultdict(dict))   # workload -> variant -> seed -> row
for path in glob.glob("results/summary_*.csv"):
    with open(path) as f:
        for r in csv.DictReader(f):
            rows[r["workload"]][r["variant"]][int(r["seed"])] = r
workloads = [w for w in ORDER if w in rows] + [w for w in rows if w not in ORDER]
variants = ["V0", "V1", "V2", "V4", "V5"]

def vals(w, v, col):
    return [float(rows[w][v][s][col]) for s in sorted(rows[w][v])]

# ---------- figure 1: bars ----------
if workloads:
    fig, ax = plt.subplots(figsize=(11, 5))
    width = 0.8 / len(variants)
    for i, v in enumerate(variants):
        xs = [j + i * width for j in range(len(workloads))]
        hs = [mean(vals(w, v, "hops_all")) for w in workloads]
        es = [stdev(vals(w, v, "hops_all")) if len(rows[w][v]) > 1 else 0 for w in workloads]
        ax.bar(xs, hs, width, yerr=es, capsize=2, label=LABEL[v], color=COLOR[v])
    ax.set_xticks([j + 0.4 - width / 2 for j in range(len(workloads))])
    ax.set_xticklabels([PRETTY.get(w, w) for w in workloads])
    ax.set_ylabel("Average hops per search (lower is better)")
    ax.set_title("Average search path by method and workload")
    ax.legend(fontsize=8, ncol=2)
    ax.grid(axis="y", alpha=0.3)
    fig.tight_layout()
    fig.savefig("results/fig1_hops_by_workload.png", dpi=160)
    plt.close(fig)

# ---------- tables ----------
with open("results/paired_table.csv", "w", newline="") as f:
    w_ = csv.writer(f)
    w_.writerow(["workload", "V5 vs V0 plain %", "V5 vs V1 no aging %", "V5 vs V2 scheduled %", "V5 vs V4 SASL-style %"])
    for w in workloads:
        out = [PRETTY.get(w, w)]
        for ref in ["V0", "V1", "V2", "V4"]:
            seeds = sorted(set(rows[w]["V5"]) & set(rows[w][ref]))
            d = [100 * (float(rows[w]["V5"][s]["hops_all"]) / float(rows[w][ref][s]["hops_all"]) - 1) for s in seeds]
            out.append(f"{mean(d):+.1f}")
        w_.writerow(out)
with open("results/time_and_restructuring_table.csv", "w", newline="") as f:
    w_ = csv.writer(f)
    w_.writerow(["workload", "variant", "hops", "ns_per_search", "promotions_per_1k", "demotions_per_1k"])
    for w in workloads:
        for v in variants:
            w_.writerow([PRETTY.get(w, w), LABEL[v], f"{mean(vals(w, v, 'hops_all')):.2f}",
                         f"{mean(vals(w, v, 'ns_per_search')):.0f}",
                         f"{mean(vals(w, v, 'promotions_per_1k')):.1f}", f"{mean(vals(w, v, 'demotions_per_1k')):.1f}"])

# ---------- figure 2: shifting workload over time ----------
for path in sorted(glob.glob("results/windows_*.csv")):
    name = os.path.basename(path)[len("windows_"):-len(".csv")]
    series = defaultdict(lambda: defaultdict(list))   # variant -> window -> values over seeds
    with open(path) as f:
        for r in csv.DictReader(f):
            series[r["variant"]][int(r["window"])].append(float(r["mean_hops"]))
    fig, ax = plt.subplots(figsize=(11, 4.8))
    nwin = 0
    for v in variants:
        if v not in series:
            continue
        ws = sorted(series[v])
        nwin = max(nwin, len(ws))
        ax.plot([(k + 1) * WINDOW / 1e6 for k in ws], [mean(series[v][k]) for k in ws],
                label=LABEL[v], color=COLOR[v], linewidth=1.8)
    shifts = int(nwin * WINDOW // SHIFT_EVERY)
    for s in range(1, shifts + 1):
        ax.axvline(s * SHIFT_EVERY / 1e6, color="black", linestyle="--", alpha=0.35)
    ax.set_xlabel("Searches (millions); dashed lines = the popular keys change")
    ax.set_ylabel("Average hops per window of %d searches" % WINDOW)
    ax.set_title("Following a moving hot set: " + PRETTY.get(name, name))
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig("results/fig2_shifting_%s.png" % name, dpi=160)
    plt.close(fig)
print("wrote figures and tables to results/")
