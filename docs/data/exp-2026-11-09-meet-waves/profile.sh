#!/usr/bin/env bash
# First-wave profiles (cr.tools.LocalProfile) of v12 and of the "meet every wave at a tower" arms of `current` (2026-11-09, Gold):
# each against v12 (the mirror), the rival R0 and field on the development seeds 1 + 397 k, k < 200, both seats. Run from the
# repository root:
#   OUT=<directory> bash docs/data/exp-2026-11-09-meet-waves/profile.sh ARM [ARM ...]
# where an ARM is a parameter list such as WAVE_TOWER_HP=500. OUT is gitignored, by default
# replays/bench/exp-2026-11-09-meet-waves/profiles; a profile is named after its arm (',' -> '_', '=' -> '-') and opponent.
# v12's own profiles are written once (v12-<opponent>.txt). profile_table.py reads them.
set -u
OUT=${OUT:-replays/bench/exp-2026-11-09-meet-waves/profiles}
mkdir -p "$OUT"
S="-Pmaps=200 -PseedBase=1 -PseedStride=397"
prof() { ./gradlew -q localProfile "-PbotA=$1" "-PbotB=$2" $S > "$OUT/$3.txt"; }
for opp in v12 rival field; do
  [ -f "$OUT/v12-$opp.txt" ] || prof v12 "$opp" "v12-$opp"
done
for arm in "$@"; do
  name=$(echo "$arm" | tr ',=' '_-')
  for opp in v12 rival field; do prof "current?$arm" "$opp" "$name-$opp"; done
done
