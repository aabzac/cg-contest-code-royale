#!/usr/bin/env bash
# The rival, grid 3 (2026-11-06): around grid 2's best, v09 against field variants on the development seeds
# 1 + 397 k, k < 300. Run from the repository root; OUT is the raw directory (gitignored).
set -u
OUT=${OUT:-replays/bench/dev-rival-2026-11-06}
mkdir -p "$OUT"
B="KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1"
i=0
for opp in "$B,GIANT_TURN=60,GIANTS_MAX=2" "$B,GIANT_TURN=100,GIANTS_MAX=2" "$B,GIANT_TURN=80,GIANTS_MAX=1" "$B,GIANT_TURN=80,GIANTS_MAX=3" \
           "$B,GIANT_TURN=80,GIANTS_MAX=2,TOWERS=3" "$B,GIANT_TURN=80,GIANTS_MAX=2,INCOME=10" "$B,GIANT_TURN=80,GIANTS_MAX=2,KITE_RANGE=350"; do
  i=$((i+1))
  ./gradlew -q bench -Pbot1=v09 "-Pbot2=field?$opp" -Pmaps=300 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397 "-Pout=$OUT/grid3-$i.json" | head -1
done
