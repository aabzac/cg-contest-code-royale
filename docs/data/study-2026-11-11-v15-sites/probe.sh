#!/usr/bin/env bash
# The measurement of v15's Gold games (2026-11-11): the v13 study's probe, unchanged
# (docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java), on v15's two placements and, as the control, on v14's
# two; then v14's answers on v15's recorded positions (cgAttribute -Pbot=v14; its exit code 1 is expected, the games are
# not v14's). Run from the repository root of a checkout that can reach the arena replays, after ./gradlew compileJava,
# with nothing else running:
#   R=<the replays directory> bash docs/data/study-2026-11-11-v15-sites/probe.sh
# R defaults to ../../../replays (a worktree under .claude/worktrees of the main checkout; relative, because MSYS
# rewrites absolute Windows paths in Gradle's -P arguments). OUT is gitignored, by default
# replays/study/gold-v15-2026-11-11. MSYS also rewrites a class path that holds a drive letter, hence MSYS_NO_PATHCONV.
set -u
R=${R:-../../../replays}
OUT=${OUT:-replays/study/gold-v15-2026-11-11}
mkdir -p "$OUT" build/probe-classes
GSON=$(cygpath -m "$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/*/*/gson-*.jar | grep -v sources | head -1)")
date
MSYS_NO_PATHCONV=1 javac -encoding UTF-8 -cp "build/classes/java/main;$GSON" -d build/probe-classes docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java || exit 1
CP="build/probe-classes;build/classes/java/main;build/resources/main;$GSON"
MSYS_NO_PATHCONV=1 java -cp "$CP" ArenaProbe 6871160,6871202 "$OUT/probe" "$R/arena/v15/gold/upload-01/games" "$R/arena/v15/gold/upload-02/games"
MSYS_NO_PATHCONV=1 java -cp "$CP" ArenaProbe 6868933,6869279 "$OUT/probe-v14" "$R/arena/v14/gold/upload-01/games" "$R/arena/v14/gold/upload-02/games"
date
./gradlew -q cgAttribute "-Preplays=$R/arena/v15/gold/upload-01/games" -Pagent=6871160 -Pbot=v14 > "$OUT/as-v14-upload-01.txt" 2>&1; echo "cgAttribute upload 1 exit $?"
./gradlew -q cgAttribute "-Preplays=$R/arena/v15/gold/upload-02/games" -Pagent=6871202 -Pbot=v14 > "$OUT/as-v14-upload-02.txt" 2>&1; echo "cgAttribute upload 2 exit $?"
date
sha256sum "$OUT"/probe/*.csv "$OUT"/probe-v14/*.csv "$OUT"/as-v14-upload-0*.txt
