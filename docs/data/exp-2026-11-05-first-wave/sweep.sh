#!/usr/bin/env bash
# Development sweep of the first-wave flags (2026-11-05): each arm against v08 and against field, on the
# development seeds 1 + 397 k, k < 500 (all 16 starting HPs). Run from the repository root; OUT is the raw
# report directory (gitignored), by default replays/bench/dev-first-wave-2026-11-05.
set -u
OUT=${OUT:-replays/bench/dev-first-wave-2026-11-05}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" $S "-Pout=$OUT/$3.json" | head -2; }
bench v08 field ctrl-field
for arm in "WAVE_TOWERS=2" "WAVE_TOWERS=3" "TOPUP_FROM=2" "WAVE_TOWERS=2,TOPUP_FROM=2"; do
  name=$(echo "$arm" | tr 'A-Z,=' 'a-z_-')
  bench "current?$arm" v08 "$name-v08"
  bench "current?$arm" field "$name-field"
done
