#!/usr/bin/env bash
# Development sweep of the "tower sites chosen for overlap" flags over v14 (2026-11-10, Gold). First the identity series:
# `current` at its defaults against v14 on the first development set (1 + 397 k, k < 500), compared with v14's mirror by
# tools/bench_pair.py (0 maps may change). Then each arm of `current` (v14's defaults plus the arm) against v14 (direct),
# the rival R0 and field on the four development sets BASE + 397 k, BASE 1, 3, 5 and 7, with v14's own series as the
# controls: the open-ground experiment's sweep.sh, one call a set. Run from the repository root with nothing else running
# and src/ untouched (every bench recompiles):
#   bash docs/data/exp-2026-11-10-tower-sites/sweep.sh ARM [ARM ...]
# where an ARM is a parameter list such as SITE_COVER=200. The raw reports go to
# replays/bench/exp-2026-11-10-tower-sites/dev-<BASE> (gitignored), a report named after its arm (',' -> '_', '=' -> '-')
# and opponent; a report that exists is not played again. SETS (default "1 3 5 7") limits the sets.
set -u
R=replays/bench/exp-2026-11-10-tower-sites
SETS=${SETS:-1 3 5 7}
mkdir -p "$R"
date
if [ ! -f "$R/identity-current-v14.json" ]; then
  OUT=$R/dev-1 BASE=1 bash docs/data/exp-2026-11-10-open-ground/sweep.sh
  ./gradlew -q bench -Pbot1=current -Pbot2=v14 -Pmaps=500 -Pthreads=8 -PturnMs=50 -PseedBase=1 -PseedStride=397 "-Pout=$R/identity-current-v14.json" | head -1
fi
python -B -I tools/bench_pair.py direct "$R/identity-current-v14.json" --mirror "$R/dev-1/ctrl-v14.json" --out "$R/identity.json" | head -12
for base in $SETS; do
  echo "set $base"
  OUT=$R/dev-$base BASE=$base bash docs/data/exp-2026-11-10-open-ground/sweep.sh "$@"
done
date
