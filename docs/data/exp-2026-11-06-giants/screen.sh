#!/usr/bin/env bash
# P2 screen of the answer to giants against the control v09 (the local champion) on PV3 screen block 2: seeds
# 1002000000 + 397 k, k < 2000; the process opponent boss plays the first 500 maps (3 threads, 200 ms). The full panel:
# the direct match and v09's mirror (G1, and the changed maps that make a targeted fix), field (G4), the rival R0 (G5)
# and boss (G2); field with giants as a second giant opponent. G3 skipped (no `old`). Run from the repository root with
# nothing else running:
#   C="current?..." OUT=<raw report directory> bash docs/data/exp-2026-11-06-giants/screen.sh
# OUT is gitignored (default replays/bench/exp-2026-11-06-giants); SUM is the summary directory (tracked).
set -u
C=${C:?set C to the candidate spec}
OUT=${OUT:-replays/bench/exp-2026-11-06-giants}
SUM=${SUM:-docs/data/exp-2026-11-06-giants}
mkdir -p "$OUT" "$SUM"
B="-PseedBase=1002000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v09 2000 "$IN" direct
bench v09 v09 2000 "$IN" mirror
bench "$C" field 2000 "$IN" field-cand
bench v09 field 2000 "$IN" field-ctrl
bench "$C" rival 2000 "$IN" rival-cand
bench v09 rival 2000 "$IN" rival-ctrl
bench "$C" "field?GIANTS=1" 2000 "$IN" fieldg-cand
bench v09 "field?GIANTS=1" 2000 "$IN" fieldg-ctrl
bench "$C" boss 500 "$PR" boss-cand
bench v09 boss 500 "$PR" boss-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-score 0.5 --max-changed 0.10 --out "$SUM/fix.json"; echo "targeted fix exit $?"
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1.json"; echo "G1 exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.01 --out "$SUM/g2.json"; echo "G2 (targeted fix: -1 pp) exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4.json"; echo "G4 exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/g5.json"; echo "G5 exit $?"
python -B -I tools/bench_pair.py common "$OUT/fieldg-cand.json" "$OUT/fieldg-ctrl.json" --out "$SUM/fieldg.json"; echo "field?GIANTS=1 exit $?"
