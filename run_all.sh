#!/usr/bin/env bash
# Builds everything, runs the correctness test, runs every workload, and draws the figures.
# Usage: ./run_all.sh [firstSeed] [seedCount] [threads]
#   tuning run : ./run_all.sh 1 3 1
#   final run  : ./run_all.sh 21 10 1      (use threads = 1 when you want timings you can quote)
set -e
cd "$(dirname "$0")"
FIRST=${1:-21}; COUNT=${2:-10}; THREADS=${3:-1}
JAVA="java -Xmx2g -XX:+UseSerialGC -cp out"

rm -rf out && mkdir out
javac -d out $(find src -name "*.java")

$JAVA core.ValidateTest

for w in uniform hot90_10 hot95_5 hot99_1 zipf; do
  $JAVA exp.Runner $w 100000 2000000 $COUNT $FIRST 1000000 $THREADS
done
for w in shift99_1 shift95_5; do
  $JAVA exp.Runner $w 100000 5000000 $COUNT $FIRST 1000000 $THREADS
done

python3 plots/make_plots.py 1000000 50000
echo "Done. See the results/ folder."
