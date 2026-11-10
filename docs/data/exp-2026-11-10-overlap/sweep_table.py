"""The "stand where the towers overlap" sweep over v13 as a table (2026-11-10): each arm's S against v13 with the maps
changed against v13's mirror, and its deltas against the rival R0 and field over v13's own scores on the same maps.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-overlap/sweep_table.py [<report directory>]
The reports are those of sweep.sh (default replays/bench/exp-2026-11-10-overlap/dev), the arms in the order they were
run; the output goes to stdout (sweep.txt beside this file).
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
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-overlap/dev"
    ctrl = {o: bench_pair.load(os.path.join(d, "ctrl-%s.json" % o)) for o in ("v13", "rival", "field")}
    c = ctrl["rival"]
    print("Seeds %s + %s k, k < %s; each map on both seats. S against v13 with its 95 %% CI and the maps whose games differ"
          " from v13's mirror; rival and field: the arm's map score minus v13's on the same maps (v13 scores %.4f against"
          " rival, %.4f against field) and the maps whose games differ." % (c["seedBase"], c["seedStride"], c["maps"],
                                                                           c["score"], ctrl["field"]["score"]))
    print("%-58s %-26s %-8s %-28s %-8s %-28s %-8s %s" % ("arm", "S vs v13", "changed", "delta vs rival", "changed",
                                                         "delta vs field", "changed", "candidate failures"))
    for path in sorted(glob.glob(os.path.join(d, "*-v13.json")), key=os.path.getmtime):
        name = os.path.basename(path)[:-len("-v13.json")]
        if name == "ctrl" or not (os.path.exists(os.path.join(d, name + "-rival.json")) and os.path.exists(os.path.join(d, name + "-field.json"))):
            continue
        direct = bench_pair.direct(bench_pair.load(path), ctrl["v13"])
        rival = bench_pair.common(bench_pair.load(os.path.join(d, name + "-rival.json")), ctrl["rival"])
        field = bench_pair.common(bench_pair.load(os.path.join(d, name + "-field.json")), ctrl["field"])
        fails = {k: direct["candidateFailures"][k] + rival["candidateFailures"][k] + field["candidateFailures"][k]
                 for k in direct["candidateFailures"]}
        arm = bench_pair.load(path)["botA"].split("?", 1)[-1]
        print("%-58s %-26s %-8d %-28s %-8d %-28s %-8d %s" % (arm, iv(direct["score"], False), len(direct["changedMaps"]),
                                                             iv(rival["delta"]), len(rival["changedMaps"]), iv(field["delta"]),
                                                             len(field["changedMaps"]), {k: v for k, v in fails.items() if v} or 0))


if __name__ == "__main__":
    main()
