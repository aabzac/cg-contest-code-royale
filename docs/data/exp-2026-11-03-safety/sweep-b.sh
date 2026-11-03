#!/bin/bash
# Stage B (after stage A: THREAT_TOWERS_NEAR=2 0.62-0.64 at radius 300-500, N=3 0.58, a larger THREAT_RANGE loses):
# the N=2 arms replicated on seeds 11000-11499 (r-*), and on 10000-10499: N=1, radius 600, THREAT_RANGE 200, the tower
# cap 6, and the army flags on top (b-*).
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-safety-2026-11-03
run() {
  local name=$1 spec=$2 base=${3:-10000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" v05 500 8 "$base" 50 "$OUT/$name.json"
}
for r in 300 400 500; do run "r-near2-r${r}" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=$r" 11000; done
run "r-near3-r500" "THREAT_TOWERS_NEAR=3,THREAT_RADIUS=500" 11000
run "b-near1-r500" "THREAT_TOWERS_NEAR=1,THREAT_RADIUS=500"
run "b-near2-r600" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=600"
run "b-near2-r500-tr200" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,THREAT_RANGE=200"
run "b-near2-r500-mt6" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,MAX_TOWERS=6"
run "b-near2-r500-army" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6"
run "r-near2-r500-army" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6" 11000
echo "=== done $(date +%H:%M:%S)"
