#!/usr/bin/env bash
# Local profiles (cr.tools.LocalProfile) of v09 and of each arm against an opponent, on the development seeds
# 1 + 397 k, k < 500, both seats: the economy over the game, the waves, the HP. Run from the repository root:
#   OPP=rival bash docs/data/exp-2026-11-06-income/profile.sh ARM [ARM ...]
# where an ARM is a parameter list for `current` such as INCOME_LATE=6; v09 itself is profiled first.
set -u
OPP=${OPP:-rival}
P="-Pmaps=500 -PseedBase=1 -PseedStride=397"
echo "### v09 vs $OPP"; ./gradlew -q localProfile -PbotA=v09 "-PbotB=$OPP" $P
for arm in "$@"; do
  echo "### current?$arm vs $OPP"; ./gradlew -q localProfile "-PbotA=current?$arm" "-PbotB=$OPP" $P
done
