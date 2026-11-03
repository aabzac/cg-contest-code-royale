#!/bin/bash
# Stage B: the kiting arms replicated on seeds 11000-11499 against v06 (r-*) and field (rf-*), with the v06-vs-field control there.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-kite-2026-11-03
run() {
  local name=$1 spec=$2 opp=${3:-v06} base=${4:-11000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  vs $opp  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" "$opp" 500 8 "$base" 50 "$OUT/$name.json"
}
for r in 250 400 500; do run "r-kite-r${r}" "KITE=1,KITE_RANGE=$r"; done
run "r-kite-r250-cover0" "KITE=1,KITE_RANGE=250,KITE_COVER=0"
[ -f "$OUT/rf-control.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v06 field 500 8 11000 50 "$OUT/rf-control.json"
for r in 250 400; do run "rf-kite-r${r}" "KITE=1,KITE_RANGE=$r" field; done
run "rf-kite-r250-cover0" "KITE=1,KITE_RANGE=250,KITE_COVER=0" field
echo "=== done $(date +%H:%M:%S)"
