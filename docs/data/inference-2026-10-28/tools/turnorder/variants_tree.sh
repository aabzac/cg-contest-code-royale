#!/bin/sh
# Usage: variants.sh <outdir> <extra gradle args...>; runs each hypothesis variant through cgValidate.
WF=/c/Users/garus/AppData/Local/Temp/claude/C--Disk-D-Programming-CG-cg-contest-code-royale/e5f4d102-26bd-4f74-986e-7960068e5e9a/scratchpad/wf
OUT=$1; shift
mkdir -p "$OUT"
cd "$WF/$TREE" || exit 1
./gradlew compileJava -q || exit 1
run() {
  name=$1; shift
  ./gradlew cgValidate -Preplays=C:/Disk_D/Programming/CG/cg-contest-code-royale/replays/cg -Pquiet=true "$@" > "$OUT/$name.txt" 2>&1
  echo "== $name: $*"
  grep -E "^(games|resynced|unit match|events)" "$OUT/$name.txt"
}
while read -r name args; do
  [ -z "$name" ] && continue
  case "$name" in \#*) continue;; esac
  # shellcheck disable=SC2086
  run "$name" "$@" $args
done
