#!/usr/bin/env bash
# P2 screen of current?LATE_HOLD=200 against the control v08 on PV3 screen block 0 (seeds 1000000000 + 397 k,
# k < 2000; process opponents the first 500 maps). Run from the repository root with nothing else running;
# OUT is the raw report directory (gitignored), SUM the summary directory (tracked).
set -u
OUT=${OUT:-replays/bench/exp-2026-11-05-late-hold}
SUM=${SUM:-docs/data/exp-2026-11-05-late-hold}
mkdir -p "$OUT" "$SUM"
C="current?LATE_HOLD=200"
B="-PseedBase=1000000000 -PseedStride=397"
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v08 2000 "$IN" direct
bench v08 v08 2000 "$IN" mirror
bench "$C" boss 500 "$PR" boss-cand
bench v08 boss 500 "$PR" boss-ctrl
bench "$C" field 2000 "$IN" field-cand
bench v08 field 2000 "$IN" field-ctrl
python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1.json"; echo "G1 exit $?"
python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/g2.json"; echo "G2 exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4.json"; echo "G4 exit $?"
