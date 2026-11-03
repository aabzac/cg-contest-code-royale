#!/bin/bash
# G0 recheck of the final evaluation (one slow candidate turn of 50.1 ms on map 414083 against v07, one of 50.6 ms on map
# 414137 against field; v07 had 4 in the direct run): the two regions on one thread, then quiet repeats of both series.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
C="current?KITE=2,KITE_PULL=100"
OUT=replays/bench/exp-2026-11-03-kite2-eval
run() { local name=$1; shift; echo "=== $name $(date +%H:%M:%S)"; ./gradlew.bat bench "$@" "-Pout=$OUT/$name.json" > "$OUT/$name.log" 2>&1 || echo "FAILED $name"; grep -E "score=|slow" "$OUT/$name.log" | tail -2 | cut -c1-220; }
run recheck-1thread-v07 "-Pbot1=$C" -Pbot2=v07 -Pmaps=20 -Pthreads=1 -PseedBase=414075 -PturnMs=50
run recheck-1thread-field "-Pbot1=$C" -Pbot2=field -Pmaps=20 -Pthreads=1 -PseedBase=414130 -PturnMs=50
run direct-rerun "-Pbot1=$C" -Pbot2=v07 -Pmaps=2000 -Pthreads=8 -PseedBase=414000 -PturnMs=50
run field-cand-rerun "-Pbot1=$C" -Pbot2=field -Pmaps=2000 -Pthreads=8 -PseedBase=414000 -PturnMs=50
python -I tools/bench_pair.py direct $OUT/direct-rerun.json --mirror $OUT/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-kite2/eval-g1-rerun.json
python -I tools/bench_pair.py common $OUT/field-cand-rerun.json $OUT/field-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-kite2/eval-g4-rerun.json
rm -rf tools/__pycache__
echo "=== done $(date +%H:%M:%S)"
