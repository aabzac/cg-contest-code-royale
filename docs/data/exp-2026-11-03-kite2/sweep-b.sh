#!/bin/bash
# Stage B: KITE_PULL 100 and 200 replicated on seeds 11000-11499 against v06 and field (the kiting sweep's r-mirror and
# rf-control there), and PULL 100 against v07 (the selected version, the control of the screen) on both blocks.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-kite2-2026-11-03
run() {
  local name=$1 spec=$2 opp=${3:-v06} base=${4:-11000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  vs $opp  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" "$opp" 500 8 "$base" 50 "$OUT/$name.json"
}
for pull in 100 200; do
  run "r-k2-p${pull}-m0" "KITE=2,KITE_PULL=$pull"
  run "rf-k2-p${pull}-m0" "KITE=2,KITE_PULL=$pull" field
done
run "v7-k2-p100-m0" "KITE=2,KITE_PULL=100" v07 10000
run "r7-k2-p100-m0" "KITE=2,KITE_PULL=100" v07 11000
run "v7-k2-p200-m0" "KITE=2,KITE_PULL=200" v07 10000
[ -f "$OUT/v7-mirror.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v07 v07 500 8 10000 50 "$OUT/v7-mirror.json"
echo "=== done $(date +%H:%M:%S)"
