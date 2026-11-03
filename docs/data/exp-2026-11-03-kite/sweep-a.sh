#!/bin/bash
# Development sweep 2026-11-03 (kiting): KITE / KITE_RANGE / KITE_COVER of current against v06 and against field on
# seeds 10000-10499 (500 maps, both seats). In-process, 8 threads, 50 ms turns. One JVM per arm. The v06 mirrors are
# the late-income sweep's (copied).
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-kite-2026-11-03
run() {
  local name=$1 spec=$2 opp=${3:-v06} base=${4:-10000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  vs $opp  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" "$opp" 500 8 "$base" 50 "$OUT/$name.json"
}
run "a-defaults" "KITE=0"
for r in 250 400 500; do
  run "a-kite-r${r}" "KITE=1,KITE_RANGE=$r"
  run "a-kite-r${r}-cover0" "KITE=1,KITE_RANGE=$r,KITE_COVER=0"
done
[ -f "$OUT/f-control.json" ] || java -cp "$CP" cr.runner.BenchmarkRunner v06 field 500 8 10000 50 "$OUT/f-control.json"
for r in 250 400 500; do run "f-kite-r${r}" "KITE=1,KITE_RANGE=$r" field; done
echo "=== done $(date +%H:%M:%S)"
