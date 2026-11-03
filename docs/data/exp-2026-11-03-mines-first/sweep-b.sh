#!/bin/bash
# Stage B (after stage A: MINES_FIRST=1 wins, lower TOWER_HP / fewer towers / income 5 lose): refine around MINES_FIRST.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-strategy-2026-11-03
run() {
  local name=$1 spec=$2
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" v04 500 8 10000 50 "$OUT/$name.json"
}
for mf in 1 2 3; do
  for it in 2 3 4; do
    for mt in 8 12; do
      run "b-mf${mf}-it${it}-mt${mt}-hp700" "MINES_FIRST=$mf,INCOME_TARGET=$it,MAX_TOWERS=$mt,TOWER_HP=700"
    done
  done
done
for hp in 600 800; do
  run "b-mf1-it3-mt8-hp${hp}" "MINES_FIRST=1,INCOME_TARGET=3,MAX_TOWERS=8,TOWER_HP=$hp"
done
echo "=== done $(date +%H:%M:%S)"
