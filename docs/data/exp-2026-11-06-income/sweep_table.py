"""The late-income sweep's table (2026-11-06): each arm's S against v09, and its deltas against the rival R0 and
field over v09's own scores on the same maps.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-06-income/sweep_table.py [<report directory>]
The reports are those of sweep.sh (default replays/bench/dev-income-2026-11-06), the arms in the order they were run;
the output goes to stdout.
"""
import glob
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "tools"))
import bench_pair  # noqa: E402


def iv(x, signed=True):
    f = "%+.4f (%+.4f..%+.4f)" if signed else "%.4f (%.4f..%.4f)"
    return f % (x["mean"], x["lower"], x["upper"])


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/dev-income-2026-11-06"
    ctrl = {o: bench_pair.load(os.path.join(d, "ctrl-%s.json" % o)) for o in ("rival", "field")}
    c = ctrl["rival"]
    print("Seeds %s + %s k, k < %s; each map on both seats. S against v09 with its 95 %% CI; rival and field: the arm's"
          " map score minus v09's on the same maps (v09 scores %.4f against rival, %.4f against field); changed: maps"
          " whose games against rival differ from v09's." % (c["seedBase"], c["seedStride"], c["maps"], c["score"],
                                                            ctrl["field"]["score"]))
    print("%-34s %-26s %-28s %-28s %-8s %s" % ("arm", "S vs v09", "delta vs rival", "delta vs field", "changed",
                                               "candidate failures"))
    for path in sorted(glob.glob(os.path.join(d, "*-v09.json")), key=os.path.getmtime):
        name = os.path.basename(path)[:-len("-v09.json")]
        direct = bench_pair.direct(bench_pair.load(path))
        rival = bench_pair.common(bench_pair.load(os.path.join(d, name + "-rival.json")), ctrl["rival"])
        field = bench_pair.common(bench_pair.load(os.path.join(d, name + "-field.json")), ctrl["field"])
        fails = {k: direct["candidateFailures"][k] + rival["candidateFailures"][k] + field["candidateFailures"][k]
                 for k in direct["candidateFailures"]}
        arm = bench_pair.load(path)["botA"].split("?", 1)[-1]
        print("%-34s %-26s %-28s %-28s %-8d %s" % (arm, iv(direct["score"], False), iv(rival["delta"]), iv(field["delta"]),
                                                   len(rival["changedMaps"]), {k: v for k, v in fails.items() if v} or 0))


if __name__ == "__main__":
    main()
