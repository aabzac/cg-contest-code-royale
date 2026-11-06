#!/usr/bin/env bash
# Quiet repeats of the screen's candidate series with slow turns (2026-11-06). In the first run the slow turns came
# on runs of neighbouring maps and on both sides, and v09's own mirror had 22 and 21 (host noise on a hybrid-core
# laptop). The repeats run with 2 threads, the same seeds and turnMs (P2 asks for G0 from a clean repeat with identical
# games). Run from the repository root with nothing else running; then same_games.py of the late-hold experiment
# checks the games are identical, and the gates run on the repeats.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-06-waves}
SUM=${SUM:-docs/data/exp-2026-11-06-waves}
C="current?BARRACKS_FORWARD=50"
B="-PseedBase=1003000000 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v09 2000 "-Pthreads=2 -PturnMs=50" direct-rerun
bench "$C" field 2000 "-Pthreads=2 -PturnMs=50" field-cand-rerun
bench "$C" rival 2000 "-Pthreads=2 -PturnMs=50" rival-cand-rerun
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$OUT/direct.json" "$OUT/direct-rerun.json" "$OUT/field-cand.json" "$OUT/field-cand-rerun.json" "$OUT/rival-cand.json" "$OUT/rival-cand-rerun.json"
python -B -I tools/bench_pair.py direct "$OUT/direct-rerun.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/g1-rerun.json"; echo "G1 rerun exit $?"
python -B -I tools/bench_pair.py common "$OUT/field-cand-rerun.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4-rerun.json"; echo "G4 rerun exit $?"
python -B -I tools/bench_pair.py common "$OUT/rival-cand-rerun.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/g5-rerun.json"; echo "G5 rerun exit $?"
