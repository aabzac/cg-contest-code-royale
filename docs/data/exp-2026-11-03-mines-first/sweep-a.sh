#!/bin/bash
# Development sweep 2026-11-03: strategy parameters of current against v04 on seeds 10000-10499 (500 maps, both seats).
# Stage A: 2 x 2 x 2 x 3 x 2 = 48 arms. In-process, 8 threads, 50 ms turns. One JVM per arm (BenchmarkRunner directly).
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
echo "=== mirror v04 vs v04 $(date +%H:%M:%S)"
java -cp "$CP" cr.runner.BenchmarkRunner v04 v04 500 8 10000 50 "$OUT/mirror.json"
for mf in 0 1; do
  for it in 3 5; do
    for up in 0 1; do
      for mt in 4 6 8; do
        for hp in 400 700; do
          run "mf${mf}-it${it}-up${up}-mt${mt}-hp${hp}" "MINES_FIRST=$mf,INCOME_TARGET=$it,UPGRADE=$up,MAX_TOWERS=$mt,TOWER_HP=$hp"
        done
      done
    done
  done
done
echo "=== done $(date +%H:%M:%S)"
