#!/usr/bin/env bash
# Quiet repeat of the screen's direct series (2026-11-07). In the first run the candidate had one slow turn (map
# 1004717379) and v10 one on a neighbouring map (1004719761), and v10 had slow turns against field on the series' first
# maps: host noise on a hybrid-core laptop. The repeat runs with 2 threads, the same seeds and turnMs (P2 asks for G0
# from a clean repeat with identical games). Run from the repository root with nothing else running; same_games.py of the
# late-hold experiment checks the games are identical, and the gates run on the repeat.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-07-safe-ladder/screen}
SUM=${SUM:-docs/data/exp-2026-11-07-safe-ladder}
C="current?SAFE_LADDER=1"
./gradlew -q bench "-Pbot1=$C" -Pbot2=v10 -Pmaps=2000 -Pthreads=2 -PturnMs=50 -PseedBase=1004000000 -PseedStride=397 "-Pout=$OUT/direct-rerun.json" | head -2
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$OUT/direct.json" "$OUT/direct-rerun.json"
python -B -I tools/bench_pair.py direct "$OUT/direct-rerun.json" --mirror "$OUT/mirror.json" --min-score 0.5 --max-changed 0.1 --out "$SUM/g1-rerun.json"; echo "G1 rerun exit $?"
