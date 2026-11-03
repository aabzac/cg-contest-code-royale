#!/bin/bash
# G0 recheck after the first direct run (4 slow candidate turns on maps 213203, 213270, 213280, 213282, 1 of v05):
# the region 213200-213289 on one thread, then a quiet repeat of the whole direct series.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
C="current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6"
OUT=replays/bench/exp-2026-11-03-army
run() { local name=$1; shift; echo "=== $name $(date +%H:%M:%S)"; ./gradlew.bat bench "$@" "-Pout=$OUT/$name.json" > "$OUT/$name.log" 2>&1 || echo "FAILED $name"; grep -E "score=|slow" "$OUT/$name.log" | tail -2; }
run recheck-1thread "-Pbot1=$C" -Pbot2=v05 -Pmaps=90 -Pthreads=1 -PseedBase=213200 -PturnMs=50
run direct-rerun "-Pbot1=$C" -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=212000 -PturnMs=50
python -I tools/bench_pair.py direct $OUT/direct-rerun.json --mirror $OUT/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-army/g1-rerun.json
rm -rf tools/__pycache__
echo "=== done $(date +%H:%M:%S)"
