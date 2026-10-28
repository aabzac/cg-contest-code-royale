#!/bin/sh
# Usage: metrics.sh [extra gradle -P args...]  -> resync + strict summary lines
cd /c/Users/garus/AppData/Local/Temp/claude/C--Disk-D-Programming-CG-cg-contest-code-royale/e5f4d102-26bd-4f74-986e-7960068e5e9a/scratchpad/wf/spawn || exit 1
R=C:/Disk_D/Programming/CG/cg-contest-code-royale/replays/cg
echo "== args: $*"
./gradlew -q cgValidate -Preplays=$R -Presync=true -Pquiet=true "$@" 2>&1 | grep -E "^(games|resynced|unit match|new creeps|spawn turns by|ERROR)"
./gradlew -q cgValidate -Preplays=$R -Pquiet=true "$@" 2>&1 | grep -E "^(games|unit match|new creeps|spawn turns by|ERROR)"
