#!/usr/bin/env bash
# Development sweep of the "hold at the towers only" flag over v14 (2026-11-10, Gold): each arm of `current` (v14's
# defaults plus the arm) against v14 (direct), the rival R0 and field, on one development set BASE + 397 k, k < 500 (all
# 16 starting HPs, every seed below 200000), with v14's own series against v14 (the mirror), rival and field as the
# controls. Run from the repository root with nothing else running:
#   BASE=<first seed> bash docs/data/exp-2026-11-10-open-ground/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as OVERLAP_TOUCH=9. The four sets of the experiment are BASE 1, 3, 5 and 7. The raw
# reports go to OUT, gitignored, by default replays/bench/exp-2026-11-10-open-ground/dev-<BASE>; a report is named after
# its arm (',' -> '_', '=' -> '-') and opponent. With no ARM only the controls are written.
set -u
BASE=${BASE:-1}
OUT=${OUT:-replays/bench/exp-2026-11-10-open-ground/dev-$BASE}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=$BASE -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -1; }
[ -f "$OUT/ctrl-v14.json" ] || bench v14 v14 ctrl-v14
[ -f "$OUT/ctrl-rival.json" ] || bench v14 rival ctrl-rival
[ -f "$OUT/ctrl-field.json" ] || bench v14 field ctrl-field
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  [ -f "$OUT/$name-v14.json" ] || bench "current?$arm" v14 "$name-v14"
  [ -f "$OUT/$name-rival.json" ] || bench "current?$arm" rival "$name-rival"
  [ -f "$OUT/$name-field.json" ] || bench "current?$arm" field "$name-field"
done
