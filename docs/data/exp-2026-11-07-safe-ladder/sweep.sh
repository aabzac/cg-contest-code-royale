#!/usr/bin/env bash
# Development sweep of SAFE_LADDER (2026-11-07): each arm of `current` (v10's defaults plus the arm) against v10 (direct),
# the rival R0 and field, on the development seeds 1 + 397 k, k < 500 (all 16 starting HPs), with v10's own series
# against v10 (the mirror), rival and field as the controls. Run from the repository root:
#   OUT=<raw report directory> bash docs/data/exp-2026-11-07-safe-ladder/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as SAFE_LADDER=1. OUT is gitignored, by default
# replays/bench/exp-2026-11-07-safe-ladder/dev; a report is named after its arm (',' -> '_', '=' -> '-') and opponent.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-07-safe-ladder/dev}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -1; }
[ -f "$OUT/ctrl-v10.json" ] || bench v10 v10 ctrl-v10
[ -f "$OUT/ctrl-rival.json" ] || bench v10 rival ctrl-rival
[ -f "$OUT/ctrl-field.json" ] || bench v10 field ctrl-field
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  bench "current?$arm" v10 "$name-v10"
  bench "current?$arm" rival "$name-rival"
  bench "current?$arm" field "$name-field"
done
