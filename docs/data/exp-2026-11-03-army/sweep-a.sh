#!/bin/bash
# Development sweep 2026-11-03 (army): the second barracks and synchronous training of current against v05
# on seeds 10000-10499 (500 maps, both seats). In-process, 8 threads, 50 ms turns. One JVM per arm.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-army-2026-11-03
run() {
  local name=$1 spec=$2
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" v05 500 8 10000 50 "$OUT/$name.json"
}
echo "=== mirror v05 vs v05 $(date +%H:%M:%S)"
[ -f "$OUT/mirror.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v05 v05 500 8 10000 50 "$OUT/mirror.json"
run "a-b1-sync0" "BARRACKS=1,TRAIN_SYNC=0"
run "a-b1-sync1" "BARRACKS=1,TRAIN_SYNC=1"
for at in 0 2 4 6 8; do
  for sync in 0 1; do
    run "a-b2-at${at}-sync${sync}" "BARRACKS=2,BARRACKS_AFTER_TOWERS=$at,TRAIN_SYNC=$sync"
  done
done
echo "=== done $(date +%H:%M:%S)"
