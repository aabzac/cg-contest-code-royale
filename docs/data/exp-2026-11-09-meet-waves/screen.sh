#!/usr/bin/env bash
# P2 screen of a meet-waves candidate against the control v12 (the local champion and the selected version) on PV3 screen
# block 6: seeds 1006000000 + 397 k, k < 2000; the process opponent boss plays the first 500 maps (3 threads, 200 ms).
# Strategy gates: G0 (no candidate failure), G1 (direct, the lower 95 % bound of S above 0.50), G2 (boss delta >= -2 pp),
# G4 and G5 (field, rival, delta >= -2 pp); G3 skipped (no `old`). Run from the repository root with nothing else running:
#   C="current?..." OUT=<raw report directory> bash docs/data/exp-2026-11-09-meet-waves/screen.sh
# OUT is gitignored (default replays/bench/exp-2026-11-09-meet-waves/screen); SUM is the summary directory (tracked).
set -u
C=${C:?set C to the candidate spec}
OUT=${OUT:-replays/bench/exp-2026-11-09-meet-waves/screen}
SUM=${SUM:-docs/data/exp-2026-11-09-meet-waves}
mkdir -p "$OUT" "$SUM"
B="-PseedBase=1006000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v12 2000 "$IN" direct
bench v12 v12 2000 "$IN" mirror
bench "$C" field 2000 "$IN" field-cand
bench v12 field 2000 "$IN" field-ctrl
bench "$C" rival 2000 "$IN" rival-cand
bench v12 rival 2000 "$IN" rival-ctrl
bench "$C" boss 500 "$PR" boss-cand
bench v12 boss 500 "$PR" boss-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1.json"; echo "G1 exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/g2.json"; echo "G2 exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4.json"; echo "G4 exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/g5.json"; echo "G5 exit $?"
