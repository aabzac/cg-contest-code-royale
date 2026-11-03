#!/bin/bash
# Development sweep 2026-11-03 (queen safety): THREAT_TOWERS_NEAR / THREAT_RADIUS / THREAT_RANGE of current against v05
# on seeds 10000-10499 (500 maps, both seats). In-process, 8 threads, 50 ms turns. One JVM per arm. The v05 mirror
# on these maps is the army sweep's (copied: mirror.json; r-mirror.json for seeds 11000-11499).
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
run "a-defaults" "THREAT_TOWERS_NEAR=0"
for n in 2 3; do
  for r in 300 400 500; do
    run "a-near${n}-r${r}" "THREAT_TOWERS_NEAR=$n,THREAT_RADIUS=$r"
  done
done
run "a-tr350" "THREAT_RANGE=350"
run "a-tr450" "THREAT_RANGE=450"
run "a-near2-r400-tr350" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=400,THREAT_RANGE=350"
run "a-near2-r400-tr450" "THREAT_TOWERS_NEAR=2,THREAT_RADIUS=400,THREAT_RANGE=450"
run "a-near3-r400-tr350" "THREAT_TOWERS_NEAR=3,THREAT_RADIUS=400,THREAT_RANGE=350"
echo "=== done $(date +%H:%M:%S)"
