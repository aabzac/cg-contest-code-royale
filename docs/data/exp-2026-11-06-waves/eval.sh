#!/usr/bin/env bash
# P2 final evaluation of v10 (the local champion, the second knight barracks at the centre) against v08 (the selected
# version on the arena) on PV3 evaluation block 0: seeds 2000000000 + 397 k, k < 2000; the process opponent boss plays
# the first 500 maps (3 threads, 200 ms). Gates G0-G5 (G3 skipped: no `old`); the upload recommendation is S >= 0.60 in
# the direct match. Run from the repository root with nothing else running:
#   OUT=<raw report directory> bash docs/data/exp-2026-11-06-waves/eval.sh
# OUT is gitignored (default replays/bench/exp-2026-11-06-waves-eval); SUM is the summary directory (tracked).
set -u
C=v10
K=v08
OUT=${OUT:-replays/bench/exp-2026-11-06-waves-eval}
SUM=${SUM:-docs/data/exp-2026-11-06-waves}
mkdir -p "$OUT" "$SUM"
B="-PseedBase=2000000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" "$K" 2000 "$IN" direct
bench "$K" "$K" 2000 "$IN" mirror
bench "$C" field 2000 "$IN" field-cand
bench "$K" field 2000 "$IN" field-ctrl
bench "$C" rival 2000 "$IN" rival-cand
bench "$K" rival 2000 "$IN" rival-ctrl
bench "$C" boss 500 "$PR" boss-cand
bench "$K" boss 500 "$PR" boss-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --min-score 0.6 --out "$SUM/eval-g1.json"; echo "G1 and the 0.60 recommendation exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g2.json"; echo "G2 exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g4.json"; echo "G4 exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g5.json"; echo "G5 exit $?"
