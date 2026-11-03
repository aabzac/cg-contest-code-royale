#!/bin/bash
# Stage B (after stage A: the second barracks after 4 towers with TRAIN_SYNC=1 is the only arm above 0.50, 0.548):
# the gate 3/5, the income target, the tower cap, a third barracks, the forward placement, and a replication of the
# stage A winner on another development block (seeds 11000-11499).
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-army-2026-11-03
run() {
  local name=$1 spec=$2 base=${3:-10000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" v05 500 8 "$base" 50 "$OUT/$name.json"
}
run "b-b2-at3-sync1" "BARRACKS=2,BARRACKS_AFTER_TOWERS=3,TRAIN_SYNC=1"
run "b-b2-at5-sync1" "BARRACKS=2,BARRACKS_AFTER_TOWERS=5,TRAIN_SYNC=1"
run "b-b2-at4-sync1-it4" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,INCOME_TARGET=4"
run "b-b2-at4-sync1-mt6" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6"
run "b-b2-at4-sync1-mt10" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=10"
run "b-b3-at4-sync1" "BARRACKS=3,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1"
for fw in 30 50 70; do
  run "b-b2-at4-sync1-fw${fw}" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,BARRACKS_FORWARD=$fw"
done
run "b-b2-at2-sync1-fw50" "BARRACKS=2,BARRACKS_AFTER_TOWERS=2,TRAIN_SYNC=1,BARRACKS_FORWARD=50"
run "b-b2-at6-sync1-fw50" "BARRACKS=2,BARRACKS_AFTER_TOWERS=6,TRAIN_SYNC=1,BARRACKS_FORWARD=50"
run "r-b2-at4-sync1" "BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1" 11000
echo "=== done $(date +%H:%M:%S)"
