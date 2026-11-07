#!/usr/bin/env bash
# P2 screen of a meet-back candidate against the control v11 (the local champion and the selected version) on PV3 screen
# block 5: seeds 1005000000 + 397 k, k < 2000; the process opponent boss plays the first 500 maps (3 threads, 200 ms).
# Strategy gates: G0 (no candidate failure), G1 (direct, the lower 95 % bound of S above 0.50), G2 (boss delta >= -2 pp),
# G4 and G5 (field, rival, delta >= -2 pp); G3 skipped (no `old`). Run from the repository root with nothing else running:
#   C="current?..." OUT=<raw report directory> bash docs/data/exp-2026-11-07-meet-back/screen.sh
# OUT is gitignored (default replays/bench/exp-2026-11-07-meet-back/screen); SUM is the summary directory (tracked).
set -u
C=${C:?set C to the candidate spec}
OUT=${OUT:-replays/bench/exp-2026-11-07-meet-back/screen}
SUM=${SUM:-docs/data/exp-2026-11-07-meet-back}
mkdir -p "$OUT" "$SUM"
B="-PseedBase=1005000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v11 2000 "$IN" direct
bench v11 v11 2000 "$IN" mirror
bench "$C" field 2000 "$IN" field-cand
bench v11 field 2000 "$IN" field-ctrl
bench "$C" rival 2000 "$IN" rival-cand
bench v11 rival 2000 "$IN" rival-ctrl
bench "$C" boss 500 "$PR" boss-cand
bench v11 boss 500 "$PR" boss-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1.json"; echo "G1 exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/g2.json"; echo "G2 exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4.json"; echo "G4 exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/g5.json"; echo "G5 exit $?"
