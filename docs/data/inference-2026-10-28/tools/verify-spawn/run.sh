#!/bin/sh
# Usage: run.sh <copy-name> <label> <replays-abs-path> [extra -P args...]
# Writes <label>.resync.txt (resync, verbose=1 for per-game first resync) and <label>.strict.txt (strict, per-game MISMATCH lines).
WF=/c/Users/garus/AppData/Local/Temp/claude/C--Disk-D-Programming-CG-cg-contest-code-royale/e5f4d102-26bd-4f74-986e-7960068e5e9a/scratchpad/wf
OUT=$WF/verify-spawn-out
COPY=$1; LABEL=$2; R=$3; shift 3
cd $WF/$COPY || exit 1
echo "== $COPY $LABEL $R $*" > $OUT/$LABEL.resync.txt
./gradlew -q cgValidate -Preplays=$R -Presync=true -Pverbose=1 "$@" >> $OUT/$LABEL.resync.txt 2>&1
echo "== $COPY $LABEL $R $*" > $OUT/$LABEL.strict.txt
./gradlew -q cgValidate -Preplays=$R "$@" >> $OUT/$LABEL.strict.txt 2>&1
echo "--- $LABEL resync"; grep -E "^(games|resynced|unit match|new creeps|spawn turns by|ERROR)" $OUT/$LABEL.resync.txt
echo "--- $LABEL strict"; grep -E "^(games|  first mismatch kind|unit match|new creeps|spawn turns by|ERROR)" $OUT/$LABEL.strict.txt
