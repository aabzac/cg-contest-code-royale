#!/bin/sh
# usage: run.sh <copy-dir-name> <out-name> <replays-abs-path> [java -D options...]
WF=/c/Users/garus/AppData/Local/Temp/claude/C--Disk-D-Programming-CG-cg-contest-code-royale/e5f4d102-26bd-4f74-986e-7960068e5e9a/scratchpad/wf
COPY=$1; NAME=$2; REP=$3; shift 3
GSON=$(cygpath -w /c/Users/garus/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0/527175ca6d81050b53bdd4c457a6d6e017626b0e/gson-2.11.0.jar)
CP="$(cygpath -w $WF/$COPY/build/classes/java/main);$GSON"
java -cp "$CP" "$@" cr.tools.CgValidate "$REP" 0 > $WF/verify-movement-out/$NAME.txt 2>&1
echo "=== $NAME ($COPY $*)"
grep -E "games=|resynced|unit match|warnings|^ERROR" $WF/verify-movement-out/$NAME.txt
