#!/usr/bin/env bash
# The rival, grid 2 (2026-11-06): around grid 1's best (TOWER_HP=700), v09 against field variants on the
# development seeds 1 + 397 k, k < 300. Run from the repository root; OUT is the raw directory (gitignored).
set -u
OUT=${OUT:-replays/bench/dev-rival-2026-11-06}
mkdir -p "$OUT"
B="KITE=1,MINES_FIRST=2,TOWER_HP=700"
G="GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2"
i=0
for opp in "$B,$G,TOWERS=5" "$B,$G,TOWERS=6" "KITE=1,MINES_FIRST=2,TOWER_HP=800,$G" "$B,$G,INCOME=6" "$B" \
           "MINES_FIRST=2,TOWER_HP=700,$G" "$B,$G,KITE_PULL=200" "$B,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=4"; do
  i=$((i+1))
  ./gradlew -q bench -Pbot1=v09 "-Pbot2=field?$opp" -Pmaps=300 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397 "-Pout=$OUT/grid2-$i.json" | head -1
done
