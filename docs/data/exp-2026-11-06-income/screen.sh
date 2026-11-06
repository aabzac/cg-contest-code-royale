#!/usr/bin/env bash
# P2 screen of the income candidate against the control v09 (the local champion) on PV3 screen block 1: seeds
# 1001000000 + 397 k, k < 2000; the process opponent boss plays the first 500 maps (3 threads, 200 ms). Gates G0-G5
# (G3 skipped: no `old`). Run from the repository root with nothing else running:
#   C="current?..." OUT=<raw report directory> bash docs/data/exp-2026-11-06-income/screen.sh
# OUT is gitignored (default replays/bench/exp-2026-11-06-income); SUM is the summary directory (tracked).
set -u
C=${C:?set C to the candidate spec}
OUT=${OUT:-replays/bench/exp-2026-11-06-income}
SUM=${SUM:-docs/data/exp-2026-11-06-income}
mkdir -p "$OUT" "$SUM"
B="-PseedBase=1001000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v09 2000 "$IN" direct
bench v09 v09 2000 "$IN" mirror
bench "$C" field 2000 "$IN" field-cand
bench v09 field 2000 "$IN" field-ctrl
bench "$C" rival 2000 "$IN" rival-cand
bench v09 rival 2000 "$IN" rival-ctrl
bench "$C" boss 500 "$PR" boss-cand
bench v09 boss 500 "$PR" boss-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1.json"; echo "G1 exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/g2.json"; echo "G2 exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4.json"; echo "G4 exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/g5.json"; echo "G5 exit $?"
