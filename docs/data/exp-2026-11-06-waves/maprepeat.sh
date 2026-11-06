#!/usr/bin/env bash
# Per-map quiet repeats (2026-11-06): the maps where the candidate still had a slow turn in the 2-thread repeats of
# rerun.sh are played again one at a time (1 thread, the same turnMs). map_check.py then requires each to hold the same
# two games as the screen's first run with no failure of the candidate. Games are deterministic, so every game of the
# three series then has a run without a candidate failure. Run from the repository root with nothing else running.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-06-waves}
C="current?BARRACKS_FORWARD=50"
for pair in "direct-rerun:v09" "field-cand-rerun:field" "rival-cand-rerun:rival"; do
  rep=${pair%%:*}; opp=${pair##*:}
  for seed in $(python -B -I docs/data/exp-2026-11-06-waves/slow_seeds.py "$OUT/$rep.json" | tr -d '\r'); do
    ./gradlew -q bench "-Pbot1=$C" "-Pbot2=$opp" -Pmaps=1 -Pthreads=1 -PturnMs=50 "-PseedBase=$seed" -PseedStride=397 "-Pout=$OUT/map-$rep-$seed.json" | head -1
  done
done
python -B -I docs/data/exp-2026-11-06-waves/map_check.py "$OUT"; echo "map check exit $?"
