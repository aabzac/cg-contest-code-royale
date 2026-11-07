#!/usr/bin/env bash
# P2 screen of SAFE_LADDER=1 as a targeted fix against the control v10 (the local champion) on PV3 screen block 4: seeds
# 1004000000 + 397 k, k < 2000; the process opponent boss plays the first 500 maps (3 threads, 200 ms). The targeted
# fix's gates: G0, S >= 0.500 with fewer than 10 % of the maps changed against v10's mirror, boss delta >= -1 pp, and
# the regression test; G4 and G5 (field, rival, delta >= -2 pp) are run as for a strategy candidate. G3 skipped (no
# `old`). Run from the repository root with nothing else running:
#   C="current?SAFE_LADDER=1" OUT=<raw report directory> bash docs/data/exp-2026-11-07-safe-ladder/screen.sh
# OUT is gitignored (default replays/bench/exp-2026-11-07-safe-ladder/screen); SUM is the summary directory (tracked).
set -u
C=${C:?set C to the candidate spec}
OUT=${OUT:-replays/bench/exp-2026-11-07-safe-ladder/screen}
SUM=${SUM:-docs/data/exp-2026-11-07-safe-ladder}
mkdir -p "$OUT" "$SUM"
B="-PseedBase=1004000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v10 2000 "$IN" direct
bench v10 v10 2000 "$IN" mirror
bench "$C" field 2000 "$IN" field-cand
bench v10 field 2000 "$IN" field-ctrl
bench "$C" rival 2000 "$IN" rival-cand
bench v10 rival 2000 "$IN" rival-ctrl
bench "$C" boss 500 "$PR" boss-cand
bench v10 boss 500 "$PR" boss-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-score 0.5 --max-changed 0.1 --out "$SUM/g1.json"; echo "G1 exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.01 --out "$SUM/g2.json"; echo "G2 exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4.json"; echo "G4 exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/g5.json"; echo "G5 exit $?"
