#!/usr/bin/env bash
# Development sweep of the "stand where the towers overlap" flags over v13 (2026-11-10, Gold): each arm of `current`
# (v13's defaults plus the arm) against v13 (direct), the rival R0 and field, on the development seeds 1 + 397 k, k < 500
# (all 16 starting HPs), with v13's own series against v13 (the mirror), rival and field as the controls. Run from the
# repository root with nothing else running:
#   OUT=<raw report directory> bash docs/data/exp-2026-11-10-overlap/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as HOLD_OVERLAP=180. OUT is gitignored, by default
# replays/bench/exp-2026-11-10-overlap/dev; a report is named after its arm (',' -> '_', '=' -> '-') and opponent.
# BASE (default 1) is the first seed: BASE=3 with OUT=.../dev-b is the second development set, 3 + 397 k (other maps, all below 200000).
set -u
OUT=${OUT:-replays/bench/exp-2026-11-10-overlap/dev}
BASE=${BASE:-1}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=$BASE -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -1; }
[ -f "$OUT/ctrl-v13.json" ] || bench v13 v13 ctrl-v13
[ -f "$OUT/ctrl-rival.json" ] || bench v13 rival ctrl-rival
[ -f "$OUT/ctrl-field.json" ] || bench v13 field ctrl-field
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  [ -f "$OUT/$name-v13.json" ] || bench "current?$arm" v13 "$name-v13"
  [ -f "$OUT/$name-rival.json" ] || bench "current?$arm" rival "$name-rival"
  [ -f "$OUT/$name-field.json" ] || bench "current?$arm" field "$name-field"
done
