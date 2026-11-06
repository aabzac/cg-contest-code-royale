"""The giants sweep's table (2026-11-06): each arm's S against v09 and its deltas against the rival R0, field and field
with giants over v09's own scores on the same maps.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-06-giants/sweep_table.py [<report directory>]
The reports are those of sweep.sh (default replays/bench/dev-giants-2026-11-06), the arms in the order they were run;
the output goes to stdout.
"""
import glob
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "tools"))
import bench_pair  # noqa: E402

OPPONENTS = ("rival", "field", "fieldg")


def iv(x, signed=True):
    f = "%+.4f (%+.4f..%+.4f)" if signed else "%.4f (%.4f..%.4f)"
    return f % (x["mean"], x["lower"], x["upper"])


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/dev-giants-2026-11-06"
    ctrl = {o: bench_pair.load(os.path.join(d, "ctrl-%s.json" % o)) for o in OPPONENTS}
    c = ctrl["rival"]
    print("Seeds %s + %s k, k < %s; each map on both seats. S against v09 with its 95 %% CI; deltas: the arm's map score"
          " minus v09's on the same maps (v09 scores %.4f against rival, %.4f against field, %.4f against"
          " field?GIANTS=1); changedR: maps whose games against rival differ from v09's."
          % (c["seedBase"], c["seedStride"], c["maps"], c["score"], ctrl["field"]["score"], ctrl["fieldg"]["score"]))
    print("%-34s %-26s %-28s %-28s %-28s %-9s %s" % ("arm", "S vs v09", "delta vs rival", "delta vs field",
                                                     "delta vs field?GIANTS=1", "changedR", "candidate failures"))
    for path in sorted(glob.glob(os.path.join(d, "*-v09.json")), key=os.path.getmtime):
        name = os.path.basename(path)[:-len("-v09.json")]
        direct = bench_pair.direct(bench_pair.load(path))
        com = {o: bench_pair.common(bench_pair.load(os.path.join(d, "%s-%s.json" % (name, o))), ctrl[o]) for o in OPPONENTS}
        fails = {k: direct["candidateFailures"][k] + sum(com[o]["candidateFailures"][k] for o in OPPONENTS)
                 for k in direct["candidateFailures"]}
        arm = bench_pair.load(path)["botA"].split("?", 1)[-1]
        print("%-34s %-26s %-28s %-28s %-28s %-9d %s" % (arm, iv(direct["score"], False), iv(com["rival"]["delta"]),
                                                        iv(com["field"]["delta"]), iv(com["fieldg"]["delta"]),
                                                        len(com["rival"]["changedMaps"]),
                                                        {k: v for k, v in fails.items() if v} or 0))


if __name__ == "__main__":
    main()
