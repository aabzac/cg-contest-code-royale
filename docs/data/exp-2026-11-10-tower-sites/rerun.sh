#!/usr/bin/env bash
# Quiet repeats of the final evaluation's two candidate series with slow turns (2026-11-11): the first run at 8 threads
# had 2 slow turns of v15 in the direct series (maps 2004073445 and 2004075033, the longest 173.8 ms; v14 had 9 as its
# opponent) and 9 in its field series (the longest 222.1 ms); v14 had 13 in its own rival series of the same minutes.
# The repeats run with 2 threads, the same seeds and turnMs (P2 takes G0 from a clean repeat with identical games; the
# practice of docs/data/exp-2026-11-10-overlap/rerun.sh). Run from the repository root with nothing else running;
# same_games.py of the late-hold experiment checks the games are identical, and the gates G1 and G4 run on the repeats.
set -u
R=replays/bench/exp-2026-11-10-tower-sites
OUT=$R/eval
SUM=docs/data/exp-2026-11-10-tower-sites
B="-PseedBase=2004000000 -PseedStride=397"
date
./gradlew -q bench -Pbot1=v15 -Pbot2=v14 -Pmaps=2000 -Pthreads=2 -PturnMs=50 $B "-Pout=$OUT/direct-rerun.json" | head -2
./gradlew -q bench -Pbot1=v15 -Pbot2=field -Pmaps=2000 -Pthreads=2 -PturnMs=50 $B "-Pout=$OUT/field-cand-rerun.json" | head -2
date
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$OUT/direct.json" "$OUT/direct-rerun.json" "$OUT/field-cand.json" "$OUT/field-cand-rerun.json"
python -B -I tools/bench_pair.py direct "$OUT/direct-rerun.json" --mirror "$OUT/mirror.json" --min-lower 0.5 --out "$SUM/eval-g1-rerun.json" | grep -v "plays \|^input"; echo "G1 rerun exit ${PIPESTATUS[0]}"
python -B -I tools/bench_pair.py common "$OUT/field-cand-rerun.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/eval-g4-rerun.json" | grep -v "plays \|^input"; echo "G4 rerun exit ${PIPESTATUS[0]}"
