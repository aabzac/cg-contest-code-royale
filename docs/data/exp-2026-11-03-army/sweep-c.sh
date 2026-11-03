#!/bin/bash
# Stage C: the stage B arms above 0.53 replicated on the second development block (seeds 11000-11499), where the
# stage A winner fell from 0.548 to 0.508; the candidate for the screen is chosen on the two blocks together.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-army-2026-11-03
run() {
  local name=$1 spec=$2 base=${3:-11000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" v05 500 8 "$base" 50 "$OUT/$name.json"
}
run "r-b2-at4-sync1-mt6" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6"
run "r-b2-at4-sync1-fw50" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,BARRACKS_FORWARD=50"
run "r-b2-at4-sync1-it4" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,INCOME_TARGET=4"
run "r-b2-at3-sync1" "BARRACKS=2,BARRACKS_AFTER_TOWERS=3,TRAIN_SYNC=1"
[ -f "$OUT/r-mirror.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v05 v05 500 8 11000 50 "$OUT/r-mirror.json"
echo "=== done $(date +%H:%M:%S)"
