#!/usr/bin/env bash
# First-wave profiles (cr.tools.LocalProfile) of v11 and of the "meet the first wave back" arms of `current` (2026-11-07, Gold):
# each against v11 (the mirror), the rival R0 and field on the development seeds 1 + 397 k, k < 200, both seats. Run from the
# repository root:
#   OUT=<directory> bash docs/data/exp-2026-11-07-meet-back/profile.sh ARM [ARM ...]
# where an ARM is a parameter list such as MEET_BACK=400. OUT is gitignored, by default
# replays/bench/exp-2026-11-07-meet-back/profiles; a profile is named after its arm (',' -> '_', '=' -> '-') and opponent.
# v11's own profiles are written once (v11-<opponent>.txt). profile_table.py reads them.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-07-meet-back/profiles}
mkdir -p "$OUT"
S="-Pmaps=200 -PseedBase=1 -PseedStride=397"
prof() { ./gradlew -q localProfile "-PbotA=$1" "-PbotB=$2" $S > "$OUT/$3.txt"; }
for opp in v11 rival field; do
  [ -f "$OUT/v11-$opp.txt" ] || prof v11 "$opp" "v11-$opp"
done
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  for opp in v11 rival field; do prof "current?$arm" "$opp" "$name-$opp"; done
done
