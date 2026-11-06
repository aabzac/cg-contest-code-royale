#!/usr/bin/env bash
# Quiet repeats for G0 of the final evaluation (2026-11-06), as for the screen (rerun.sh, maprepeat.sh). v10's three
# in-process series are repeated with 2 threads (the same seeds and turnMs) and must hold the same games. The maps
# where v10 still has a slow turn are then replayed one at a time in 1 thread and checked by map_check.py (the same
# two games, no failure of the candidate). Run from the repository root with nothing else running.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-06-waves-eval}
SUM=${SUM:-docs/data/exp-2026-11-06-waves}
C=v10
B="-PseedBase=2000000000 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
bench "$C" v08 2000 "-Pthreads=2 -PturnMs=50" direct-rerun
bench "$C" field 2000 "-Pthreads=2 -PturnMs=50" field-cand-rerun
bench "$C" rival 2000 "-Pthreads=2 -PturnMs=50" rival-cand-rerun
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$OUT/direct.json" "$OUT/direct-rerun.json" "$OUT/field-cand.json" "$OUT/field-cand-rerun.json" "$OUT/rival-cand.json" "$OUT/rival-cand-rerun.json"
for pair in "direct-rerun:v08" "field-cand-rerun:field" "rival-cand-rerun:rival"; do
  rep=${pair%%:*}; opp=${pair##*:}
  for seed in $(python -B -I docs/data/exp-2026-11-06-waves/slow_seeds.py "$OUT/$rep.json" | tr -d '\r'); do
    ./gradlew -q bench "-Pbot1=$C" "-Pbot2=$opp" -Pmaps=1 -Pthreads=1 -PturnMs=50 "-PseedBase=$seed" -PseedStride=397 "-Pout=$OUT/map-$rep-$seed.json" | head -1
  done
done
python -B -I docs/data/exp-2026-11-06-waves/map_check.py "$OUT"; echo "map check exit $?"
