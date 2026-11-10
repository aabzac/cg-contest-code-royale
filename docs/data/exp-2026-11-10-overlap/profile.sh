#!/usr/bin/env bash
# Mechanism profiles of v13 and of the "stand where the towers overlap" arms of `current` (2026-11-10, Gold): the study's
# probe in its local mode (docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java), each bot against v13 (the mirror),
# the rival R0 and field on the development seeds 1 + 397 k, k < 200, both seats. Run from the repository root after
# ./gradlew compileJava, with nothing else running:
#   OUT=<directory> bash docs/data/exp-2026-11-10-overlap/profile.sh ARM [ARM ...]
# where an ARM is a parameter list such as HOLD_OVERLAP=180. OUT is gitignored, by default
# replays/bench/exp-2026-11-10-overlap/profiles; a profile is a directory named after its arm (',' -> '_', '=' -> '-') and
# opponent, with the probe's turns.csv, events.csv, waves.csv and games.csv. v13's own profiles are written once
# (v13-<opponent>). profile_table.py reads them.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-10-overlap/profiles}
MAPS=${MAPS:-200}
mkdir -p "$OUT" build/probe-classes
GSON=$(cygpath -m "$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/*/*/gson-*.jar | grep -v sources | head -1)")
CP="build/probe-classes;build/classes/java/main;build/resources/main;$GSON"
javac -encoding UTF-8 -cp "build/classes/java/main;$GSON" -d build/probe-classes docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java || exit 1
prof() { [ -f "$OUT/$3/games.csv" ] || java -cp "$CP" ArenaProbe local "$1" "$2" "$MAPS" 1 397 "$OUT/$3"; }
for opp in v13 rival field; do prof v13 "$opp" "v13-$opp"; done
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  for opp in v13 rival field; do prof "current?$arm" "$opp" "$name-$opp"; done
done
