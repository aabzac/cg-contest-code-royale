#!/bin/bash
# G0 recheck of the final evaluation (4 slow candidate turns on maps 412270, 412271, 412275, 412280 in the first direct run;
# v06 had 1 there and 4 in the mirror with the same code): the region 412260-412289 on one thread, then a quiet repeat of
# the whole direct series.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
C="current?KITE=1"
OUT=replays/bench/exp-2026-11-03-kite-eval
run() { local name=$1; shift; echo "=== $name $(date +%H:%M:%S)"; ./gradlew.bat bench "$@" "-Pout=$OUT/$name.json" > "$OUT/$name.log" 2>&1 || echo "FAILED $name"; grep -E "score=|slow" "$OUT/$name.log" | tail -2 | cut -c1-220; }
run recheck-1thread "-Pbot1=$C" -Pbot2=v06 -Pmaps=30 -Pthreads=1 -PseedBase=412260 -PturnMs=50
run direct-rerun "-Pbot1=$C" -Pbot2=v06 -Pmaps=2000 -Pthreads=8 -PseedBase=412000 -PturnMs=50
python -I tools/bench_pair.py direct $OUT/direct-rerun.json --mirror $OUT/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-kite/eval-g1-rerun.json
rm -rf tools/__pycache__
echo "=== done $(date +%H:%M:%S)"
