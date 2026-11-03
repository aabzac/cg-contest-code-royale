#!/bin/bash
# P2 final evaluation (PV2: G0-G4) of the bound-kiting candidate against v07 on evaluation block k=7 (seeds 414000-415999), 2026-11-03. Sequential.
cd "$(dirname "$0")/../../.." || exit 1
export MSYS_NO_PATHCONV=1
C="current?KITE=2,KITE_PULL=100"
OUT=replays/bench/exp-2026-11-03-kite2-eval
run() { local name=$1; shift; echo "=== $name $(date +%H:%M:%S)"; ./gradlew.bat bench "$@" "-Pout=$OUT/$name.json" > "$OUT/$name.log" 2>&1 || echo "FAILED $name"; }
run direct "-Pbot1=$C" -Pbot2=v07 -Pmaps=2000 -Pthreads=8 -PseedBase=414000 -PturnMs=50
run mirror -Pbot1=v07 -Pbot2=v07 -Pmaps=2000 -Pthreads=8 -PseedBase=414000 -PturnMs=50
run field-cand "-Pbot1=$C" -Pbot2=field -Pmaps=2000 -Pthreads=8 -PseedBase=414000 -PturnMs=50
run field-ctrl -Pbot1=v07 -Pbot2=field -Pmaps=2000 -Pthreads=8 -PseedBase=414000 -PturnMs=50
run boss-cand "-Pbot1=$C" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=414000 -PturnMs=200
run boss-ctrl -Pbot1=v07 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=414000 -PturnMs=200
python -I tools/bench_pair.py direct $OUT/direct.json --mirror $OUT/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-kite2/eval-g1.json
python -I tools/bench_pair.py common $OUT/boss-cand.json $OUT/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-kite2/eval-g2.json
python -I tools/bench_pair.py common $OUT/field-cand.json $OUT/field-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-kite2/eval-g4.json
rm -rf tools/__pycache__
echo "=== done $(date +%H:%M:%S)"
