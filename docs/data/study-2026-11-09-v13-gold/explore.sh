#!/usr/bin/env bash
# Exploratory development series over v13 (study 2026-11-09, Gold): each arm of `current` (v13's defaults plus the arm)
# against v13 (direct), the rival R0 and field, on the development seeds 1 + 397 k, k < 500 (all 16 starting HPs), with
# v13's own series against v13 (the mirror), rival and field as the controls. Not a P2 screen: no fresh block is used and
# no arm is promoted from here. Run from the repository root:
#   OUT=<raw report directory> bash docs/data/study-2026-11-09-v13-gold/explore.sh ARM [ARM ...]
# where an ARM is a parameter list such as INCOME_LATE=6,LATE_COVER=1. OUT is gitignored, by default
# replays/bench/study-2026-11-09-v13-gold/dev; a report is named after its arm (',' -> '_', '=' -> '-') and opponent.
set -u
OUT=${OUT:-replays/bench/study-2026-11-09-v13-gold/dev}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
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
