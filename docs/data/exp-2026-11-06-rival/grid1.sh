#!/usr/bin/env bash
# The rival, grid 1 (2026-11-06): v09 against field variants on the development seeds 1 + 397 k, k < 300.
# Run from the repository root; OUT is the raw directory (gitignored). First the regression check of the
# default field after the new knobs (MINES_FIRST, GIANT_TURN, GIANTS_MAX).
set -u
OUT=${OUT:-replays/bench/dev-rival-2026-11-06}
REF=${REF:-replays/bench/dev-first-wave-2026-11-05/ctrl-field.json}
mkdir -p "$OUT"
./gradlew -q bench -Pbot1=v08 -Pbot2=field -Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397 "-Pout=$OUT/v08-field-regression2.json" | head -1
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$REF" "$OUT/v08-field-regression2.json" | head -1
G="GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2"
i=0
for opp in "KITE=1,MINES_FIRST=2" "KITE=1,MINES_FIRST=2,INCOME=6" "KITE=1,MINES_FIRST=2,$G" "KITE=1,MINES_FIRST=2,$G,TOWERS=5" \
           "MINES_FIRST=2,$G" "KITE=1,MINES_FIRST=2,$G,TOWER_HP=700" "KITE=1,MINES_FIRST=2,$G,INCOME=6,TOWERS=5"; do
  i=$((i+1))
  ./gradlew -q bench -Pbot1=v09 "-Pbot2=field?$opp" -Pmaps=300 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397 "-Pout=$OUT/grid1-$i.json" | head -1
done
