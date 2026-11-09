#!/usr/bin/env bash
# Development sweep of the "meet every wave at a tower" flags over v12 (2026-11-09, Gold): each arm of `current` (v12's defaults plus the arm) against v12 (direct),
# the rival R0 and field, on the development seeds 1 + 397 k, k < 500 (all 16 starting HPs), with v12's own series
# against v12 (the mirror), rival and field as the controls. Run from the repository root:
#   OUT=<raw report directory> bash docs/data/exp-2026-11-09-meet-waves/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as WAVE_TOWER_HP=500. OUT is gitignored, by default
# replays/bench/exp-2026-11-09-meet-waves/dev; a report is named after its arm (',' -> '_', '=' -> '-') and opponent.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-09-meet-waves/dev}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -1; }
[ -f "$OUT/ctrl-v12.json" ] || bench v12 v12 ctrl-v12
[ -f "$OUT/ctrl-rival.json" ] || bench v12 rival ctrl-rival
[ -f "$OUT/ctrl-field.json" ] || bench v12 field ctrl-field
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  bench "current?$arm" v12 "$name-v12"
  bench "current?$arm" rival "$name-rival"
  bench "current?$arm" field "$name-field"
done
