#!/bin/bash
# Development sweep 2026-11-03 (kiting bound to the towers): KITE=2 with KITE_PULL / KITE_MAX of current against v06 and
# against field on seeds 10000-10499 (500 maps, both seats); then LocalProfile (50 maps) of the arms' behaviour under knights.
# The v06 mirror and the v06-vs-field control on these maps are the kiting sweep's (copied).
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
CP="build/classes/java/main;build/resources/main;build/gson.jar"
OUT=replays/bench/dev-kite2-2026-11-03
run() {
  local name=$1 spec=$2 opp=${3:-v06} base=${4:-10000}
  if [ -f "$OUT/$name.json" ]; then echo "skip $name"; return; fi
  echo "=== $name  $spec  vs $opp  base $base  $(date +%H:%M:%S)"
  java -cp "$CP" cr.runner.BenchmarkRunner "current?$spec" "$opp" 500 8 "$base" 50 "$OUT/$name.json"
}
for pull in 30 50 100; do
  for max in 0 8; do
    run "a-k2-p${pull}-m${max}" "KITE=2,KITE_PULL=$pull,KITE_MAX=$max"
    run "f-k2-p${pull}-m${max}" "KITE=2,KITE_PULL=$pull,KITE_MAX=$max" field
  done
done
run "a-k2-p200-m0" "KITE=2,KITE_PULL=200"
run "f-k2-p200-m0" "KITE=2,KITE_PULL=200" field
echo "=== profiles $(date +%H:%M:%S)"
for spec in "KITE=0" "KITE=1" "KITE=2,KITE_PULL=50" "KITE=2,KITE_PULL=100" "KITE=2,KITE_PULL=50,KITE_MAX=8" "KITE=2,KITE_PULL=200"; do
  echo "--- current?$spec vs v06"
  java -cp "$CP" cr.tools.LocalProfile "current?$spec" v06 50 10000
done
echo "=== done $(date +%H:%M:%S)"
