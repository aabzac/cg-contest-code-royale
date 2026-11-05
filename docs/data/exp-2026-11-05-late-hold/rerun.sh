#!/usr/bin/env bash
# Quiet repeats of the screen's candidate series (2026-11-05): the direct and field series had slow turns of the
# candidate on runs of neighbouring maps (host noise: v08's own mirror had them too), and the boss series lost
# 6 maps to boss timeouts (more than 1 %), so it reruns with 2 threads instead of 3 (same turnMs, as P2 asks).
# Run from the repository root with nothing else running; then the gates on the reruns.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-05-late-hold}
SUM=${SUM:-docs/data/exp-2026-11-05-late-hold}
C="current?LATE_HOLD=200"
B="-PseedBase=1000000000 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v08 2000 "-Pthreads=8 -PturnMs=50" direct-rerun
bench "$C" field 2000 "-Pthreads=8 -PturnMs=50" field-cand-rerun
bench "$C" boss 500 "-Pthreads=2 -PturnMs=200" boss-cand-rerun
python -B -I tools/bench_pair.py direct "$OUT/direct-rerun.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1-rerun.json"; echo "G1 rerun exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand-rerun.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/g2-rerun.json"; echo "G2 rerun exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand-rerun.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4-rerun.json"; echo "G4 rerun exit $?"
