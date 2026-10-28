#!/bin/bash
# usage: run.sh <copy> <label> <replays-abs-path> [extra gradle -P args...]
WF=/c/Users/garus/AppData/Local/Temp/claude/C--Disk-D-Programming-CG-cg-contest-code-royale/e5f4d102-26bd-4f74-986e-7960068e5e9a/scratchpad/wf
copy=$1; label=$2; rp=$3; shift 3
out=$WF/verify-turnorder-out/$label.txt
cd $WF/$copy || exit 2
echo "# copy=$copy replays=$rp args=$*" > "$out"
start=$(date +%s)
./gradlew -q --console=plain cgValidate -Preplays="$rp" -Pquiet=true "$@" >> "$out" 2>&1
code=$?
end=$(date +%s)
echo "# exit=$code seconds=$((end-start))" >> "$out"
grep -E "^games=|unit match|resynced turns|^events|first mismatch kind|^# exit" "$out" | cut -c1-400 | sed "s/^/[$label] /"
