"""The LATE_HOLD sweep's table: each arm's S against v08 and its delta against field over v08's (2026-11-05).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-05-late-hold/sweep_table.py [<report directory>]
The reports are those of sweep.sh (default replays/bench/dev-late-hold-2026-11-05); the output goes to stdout.
Besides S and delta: the games decided at the 250-turn limit and our wins among them, against v08.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "tools"))
import bench_pair  # noqa: E402

ARMS = (100, 150, 200, 225)


def limit_record(rep):
    lim = [g for g in rep["games"] if g["turns"] >= 250]
    won = sum(1 for g in lim if g["winner"] == g["aSeat"])
    lost = sum(1 for g in lim if g["winner"] == 1 - g["aSeat"])
    return len(lim), won, lost


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/dev-late-hold-2026-11-05"
    ctrl = bench_pair.load(os.path.join(d, "ctrl-field.json"))
    print("Seeds %s + %s k, k < %s; each map on both seats. S against v08 with its 95 %% CI; field: the arm's map score"
          " against field minus v08's on the same maps (control %.4f); at the limit against v08: games, won, lost."
          % (ctrl["seedBase"], ctrl["seedStride"], ctrl["maps"], ctrl["score"]))
    print("%-16s %-26s %-30s %-22s %s" % ("arm", "S vs v08", "delta vs field", "limit games vs v08", "candidate failures"))
    for t in ARMS:
        rv = bench_pair.load(os.path.join(d, "late_hold-%d-v08.json" % t))
        direct = bench_pair.direct(rv)
        common = bench_pair.common(bench_pair.load(os.path.join(d, "late_hold-%d-field.json" % t)), ctrl)
        fails = {k: direct["candidateFailures"][k] + common["candidateFailures"][k] for k in direct["candidateFailures"]}
        s, dl = direct["score"], common["delta"]
        n, w, l = limit_record(rv)
        print("LATE_HOLD=%-6d %.4f (%.4f..%.4f)   %+.4f (%+.4f..%+.4f)     %4d: %3d won, %3d lost   %s" % (
            t, s["mean"], s["lower"], s["upper"], dl["mean"], dl["lower"], dl["upper"], n, w, l, fails))


if __name__ == "__main__":
    main()
