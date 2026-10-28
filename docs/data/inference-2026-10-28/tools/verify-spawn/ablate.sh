#!/bin/sh
# Usage: ablate.sh <corpus-name cg|cg2>  -> runs the patched copy under ablation flag sets
WF=/c/Users/garus/AppData/Local/Temp/claude/C--Disk-D-Programming-CG-cg-contest-code-royale/e5f4d102-26bd-4f74-986e-7960068e5e9a/scratchpad/wf
OUT=$WF/verify-spawn-out
C=$1
R=C:/Disk_D/Programming/CG/cg-contest-code-royale/replays/$C
cd $WF/verify-spawn || exit 1
run() {
  name=$1; shift
  f=$OUT/abl_${C}_$name
  ./gradlew -q cgValidate -Preplays=$R -Presync=true -Pquiet=true "$@" > $f.resync.txt 2>&1
  ./gradlew -q cgValidate -Preplays=$R "$@" > $f.strict.txt 2>&1
  echo "### $C $name ($*)"
  grep -E "^(games|resynced|unit match|new creeps|spawn turns by|ERROR)" $f.resync.txt | sed 's/^/  R: /'
  grep -E "^(games|unit match)" $f.strict.txt | sed 's/^/  S: /'
}
run base_flags -PspawnOffset=0 -PspawnTiming=0 -PlegacyOrder=false
run corners_only -PspawnTiming=0 -PlegacyOrder=false
run corners_timing -PlegacyOrder=false
run legacy_only -PspawnOffset=0 -PspawnTiming=0
run corners_legacy -PspawnTiming=0
run legacy_timing -PspawnOffset=0
run timing2_full -PspawnTiming=2
run final_defaults
