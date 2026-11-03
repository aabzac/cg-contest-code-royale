#!/bin/bash
# Development sweep 2026-11-03 (late income): INCOME_LATE of current against v06 on seeds 10000-10499 (500 maps, both
# seats), the v06 mirror on the same maps and on 11000-11499. In-process, 8 threads, 50 ms turns. One JVM per arm.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-late-income-2026-11-03
run() {
  local name=$1 spec=$2 base=${3:-10000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" v06 500 8 "$base" 50 "$OUT/$name.json"
}
[ -f "$OUT/mirror.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v06 v06 500 8 10000 50 "$OUT/mirror.json"
[ -f "$OUT/r-mirror.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v06 v06 500 8 11000 50 "$OUT/r-mirror.json"
run "a-defaults" "INCOME_LATE=0"
for il in 4 5 6 8; do run "a-late${il}" "INCOME_LATE=$il"; done
run "a-late6-hp500" "INCOME_LATE=6,TOWER_HP=500"
run "a-late6-hp600" "INCOME_LATE=6,TOWER_HP=600"
run "a-late8-hp500" "INCOME_LATE=8,TOWER_HP=500"
echo "=== done $(date +%H:%M:%S)"
