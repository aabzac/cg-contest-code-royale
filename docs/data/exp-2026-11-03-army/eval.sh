#!/bin/bash
# P2 final evaluation of the army candidate against v05 on evaluation block k=4 (seeds 408000-409999), 2026-11-03. Sequential.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
C="current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6"
OUT=replays/bench/exp-2026-11-03-army-eval
run() { local name=$1; shift; echo "=== $name $(date +%H:%M:%S)"; ./gradlew.bat bench "$@" "-Pout=$OUT/$name.json" > "$OUT/$name.log" 2>&1 || echo "FAILED $name"; }
run direct "-Pbot1=$C" -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=408000 -PturnMs=50
run mirror -Pbot1=v05 -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=408000 -PturnMs=50
run boss-cand "-Pbot1=$C" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=408000 -PturnMs=200
run boss-ctrl -Pbot1=v05 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=408000 -PturnMs=200
python -I tools/bench_pair.py direct $OUT/direct.json --mirror $OUT/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-army/eval-g1.json
python -I tools/bench_pair.py common $OUT/boss-cand.json $OUT/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-army/eval-g2.json
echo "=== done $(date +%H:%M:%S)"
