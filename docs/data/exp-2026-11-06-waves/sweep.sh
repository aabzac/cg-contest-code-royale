#!/usr/bin/env bash
# Development sweep of the knight release rules (2026-11-06): each arm of `current` (v09's defaults plus the arm) against
# v09 (direct), the rival R0 and field, on the development seeds 1 + 397 k, k < 500 (all 16 starting HPs), with
# v09's own series against rival and field as the controls. Run from the repository root:
#   OUT=<raw report directory> bash docs/data/exp-2026-11-06-waves/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as RELEASE_NEAR=800,RELEASE_HOLD=30 or BARRACKS_FORWARD=35. OUT is gitignored, by default
# replays/bench/dev-waves-2026-11-06; a report is named after its arm (',' -> '_', '=' -> '-') and opponent.
set -u
OUT=${OUT:-replays/bench/dev-waves-2026-11-06}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -1; }
[ -f "$OUT/ctrl-rival.json" ] || bench v09 rival ctrl-rival
[ -f "$OUT/ctrl-field.json" ] || bench v09 field ctrl-field
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  bench "current?$arm" v09 "$name-v09"
  bench "current?$arm" rival "$name-rival"
  bench "current?$arm" field "$name-field"
done
