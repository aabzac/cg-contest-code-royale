#!/usr/bin/env bash
# The rival R0 (2026-11-06): its profile against v09, and the version ladder v05-v09 against it (does it order
# the versions as the arena did?), on the development seeds 1 + 397 k, k < 500. Run from the repository root.
set -u
OUT=${OUT:-replays/bench/dev-rival-2026-11-06}
mkdir -p "$OUT"
R="field?KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2"
for v in v05 v06 v07 v08 v09; do
  ./gradlew -q bench "-Pbot1=$v" "-Pbot2=$R" -Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397 "-Pout=$OUT/ladder-$v.json" | head -1
done
echo "### rival vs v09"; ./gradlew -q localProfile "-PbotA=$R" -PbotB=v09 -Pmaps=500 -PseedBase=1 -PseedStride=397
echo "### v09 vs rival"; ./gradlew -q localProfile -PbotA=v09 "-PbotB=$R" -Pmaps=500 -PseedBase=1 -PseedStride=397
