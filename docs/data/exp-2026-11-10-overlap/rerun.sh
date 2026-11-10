#!/usr/bin/env bash
# Quiet repeat of the screen's candidate series against field (2026-11-10): the first run at 8 threads had one slow turn of
# the candidate (51.7 ms on map 1007611777; v13's own field series had two). The repeat runs with 2 threads, the same seeds
# and turnMs (P2 takes G0 from a clean repeat with identical games; the practice of docs/data/exp-2026-11-06-waves/rerun.sh).
# Run from the repository root with nothing else running; same_games.py of the late-hold experiment checks the games are
# identical, and gate G4 runs on the repeat.
set -u
C=${C:?set C to the candidate spec}
OUT=${OUT:-replays/bench/exp-2026-11-10-overlap/screen}
SUM=${SUM:-docs/data/exp-2026-11-10-overlap}
B="-PseedBase=1007000000 -PseedStride=397"
./gradlew -q bench "-Pbot1=$C" -Pbot2=field -Pmaps=2000 -Pthreads=2 -PturnMs=50 $B "-Pout=$OUT/field-cand-rerun.json" | head -2
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$OUT/field-cand.json" "$OUT/field-cand-rerun.json"
python -B -I tools/bench_pair.py common "$OUT/field-cand-rerun.json" "$OUT/field-ctrl.json" --min-delta -0.02 --out "$SUM/g4-rerun.json"; echo "G4 rerun exit $?"
