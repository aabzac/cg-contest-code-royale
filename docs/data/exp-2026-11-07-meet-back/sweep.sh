#!/usr/bin/env bash
# Development sweep of the "meet the first wave back" flags over v11 (2026-11-07, Gold): each arm of `current` (v11's defaults plus the arm) against v11 (direct),
# the rival R0 and field, on the development seeds 1 + 397 k, k < 500 (all 16 starting HPs), with v11's own series
# against v11 (the mirror), rival and field as the controls. Run from the repository root:
#   OUT=<raw report directory> bash docs/data/exp-2026-11-07-meet-back/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as MEET_BACK=400. OUT is gitignored, by default
# replays/bench/exp-2026-11-07-meet-back/dev; a report is named after its arm (',' -> '_', '=' -> '-') and opponent.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-07-meet-back/dev}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -1; }
[ -f "$OUT/ctrl-v11.json" ] || bench v11 v11 ctrl-v11
[ -f "$OUT/ctrl-rival.json" ] || bench v11 rival ctrl-rival
[ -f "$OUT/ctrl-field.json" ] || bench v11 field ctrl-field
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  bench "current?$arm" v11 "$name-v11"
  bench "current?$arm" rival "$name-rival"
  bench "current?$arm" field "$name-field"
done
