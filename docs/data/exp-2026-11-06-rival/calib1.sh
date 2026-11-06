#!/usr/bin/env bash
# The rival opponent, first round (2026-11-06): field with v08's kiting (KITE=1), with and without giants, against
# v09 on the development seeds 1 + 397 k. Run from the repository root; OUT is the raw directory (gitignored).
# First a regression check: the default field must play the games of the first-wave sweep's control.
set -u
OUT=${OUT:-replays/bench/dev-rival-2026-11-06}
REF=${REF:-replays/bench/dev-first-wave-2026-11-05/ctrl-field.json}
mkdir -p "$OUT"
S="-Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397"
./gradlew -q bench -Pbot1=v08 -Pbot2=field $S "-Pout=$OUT/v08-field-regression.json" | head -1
python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py "$REF" "$OUT/v08-field-regression.json"
for opp in "field?KITE=1" "field?KITE=1,GIANTS=1"; do
  echo "### v09 vs $opp"; ./gradlew -q localProfile -PbotA=v09 "-PbotB=$opp" -Pmaps=500 -PseedBase=1 -PseedStride=397
  echo "### $opp vs v09"; ./gradlew -q localProfile "-PbotA=$opp" -PbotB=v09 -Pmaps=500 -PseedBase=1 -PseedStride=397
done
