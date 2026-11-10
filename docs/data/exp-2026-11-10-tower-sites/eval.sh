#!/usr/bin/env bash
# The freeze's identity series and the P2 final evaluation of the local champion v15 (2026-11-11). Run from the repository
# root with nothing else running and src/ untouched (every bench recompiles); refs/referee-csj/config/level4/Boss.java
# must be present.
#   bash docs/data/exp-2026-11-10-tower-sites/eval.sh identity   the frozen copy against `current` and against the screened arm
#   bash docs/data/exp-2026-11-10-tower-sites/eval.sh eval       the final evaluation
# identity (development seeds 1 + 397 k, k < 500; raw reports in <R>/freeze): `current` at its defaults against v15 must
#   repeat v15's mirror game for game (tools/bench_pair.py direct --mirror: 0 maps changed), and v15 against v14 must
#   repeat the 1000 games of the screened arm current?SITE_NEAR=50 on these maps (same_games.py: 0 differ).
# eval: v15 against the selected version v14 on PV3 evaluation block 4, seeds 2004000000 + 397 k, k < 2000; the process
#   opponent boss plays the first 500 maps (3 threads, 200 ms). Gates G0-G5 as in the screen (G3 skipped, no `old`); the
#   upload recommendation needs S >= 0.60. Raw reports in <R>/eval, the summaries eval-g*.json beside this file.
set -u
R=replays/bench/exp-2026-11-10-tower-sites
SUM=docs/data/exp-2026-11-10-tower-sites
IN="-Pthreads=8 -PturnMs=50"
PR="-Pthreads=3 -PturnMs=200"
date
if [ "${1:-}" = identity ]; then
  OUT=$R/freeze
  mkdir -p "$OUT"
  B="-PseedBase=1 -PseedStride=397"
  bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" -Pmaps=500 $IN $B "-Pout=$OUT/$3.json" | head -2; }
  bench v15 v15 mirror-v15
  bench current v15 identity-current-v15
  bench v15 v14 v15-v14
  python -B -I tools/bench_pair.py direct "$OUT/identity-current-v15.json" --mirror "$OUT/mirror-v15.json" --out "$OUT/identity.json" | grep -v "plays \|^input"; echo "identity exit $?"
  python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$R/dev-1/SITE_NEAR-50-v14.json" "$OUT/v15-v14.json"
elif [ "${1:-}" = eval ]; then
  OUT=$R/eval
  mkdir -p "$OUT"
  B="-PseedBase=2004000000 -PseedStride=397"
  bench() { ./gradlew -q bench "-Pbot1=$1" "-Pbot2=$2" "-Pmaps=$3" $4 $B "-Pout=$OUT/$5.json" | head -2; }
  bench v15 v14 2000 "$IN" direct
  bench v14 v14 2000 "$IN" mirror
  bench v15 field 2000 "$IN" field-cand
  bench v14 field 2000 "$IN" field-ctrl
  bench v15 rival 2000 "$IN" rival-cand
  bench v14 rival 2000 "$IN" rival-ctrl
  date
  bench v15 boss 500 "$PR" boss-cand
  bench v14 boss 500 "$PR" boss-ctrl
  date
  python -B -I tools/bench_pair.py direct "$OUT/direct.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/eval-g1.json" | grep -v "plays \|^input"; echo "G1 exit ${PIPESTATUS[0]}"
  python -B -I tools/bench_pair.py common "$OUT/boss-cand.json" "$OUT/boss-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g2.json" | grep -v "plays \|^input"; echo "G2 exit ${PIPESTATUS[0]}"
  python -B -I tools/bench_pair.py common "$OUT/field-cand.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g4.json" | grep -v "plays \|^input"; echo "G4 exit ${PIPESTATUS[0]}"
  python -B -I tools/bench_pair.py common "$OUT/rival-cand.json" "$OUT/rival-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g5.json" | grep -v "plays \|^input"; echo "G5 exit ${PIPESTATUS[0]}"
else
  echo "usage: eval.sh identity|eval"; exit 2
fi
date
