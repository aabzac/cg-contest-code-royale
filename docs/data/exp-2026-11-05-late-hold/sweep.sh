#!/usr/bin/env bash
# Development sweep of LATE_HOLD (2026-11-05): each arm against v08 and against field, on the development
# seeds 1 + 397 k, k < 500 (all 16 starting HPs). Run from the repository root; OUT is the raw report directory
# (gitignored), by default replays/bench/dev-late-hold-2026-11-05. The control v08-vs-field series is the
# first-wave sweep's (the same seeds and settings), copied in as ctrl-field.json when present.
set -u
OUT=${OUT:-replays/bench/dev-late-hold-2026-11-05}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -2; }
[ -f "$OUT/ctrl-field.json" ] || bench v08 field ctrl-field
for t in 100 150 200 225; do
  bench "current?LATE_HOLD=$t" v08 "late_hold-$t-v08"
  bench "current?LATE_HOLD=$t" field "late_hold-$t-field"
done
