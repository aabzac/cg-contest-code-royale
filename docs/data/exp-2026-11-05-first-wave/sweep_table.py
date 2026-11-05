"""The development sweep's table: each arm's S against v08 and its delta against field over v08's (2026-11-05).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-05-first-wave/sweep_table.py [<report directory>]
The reports are those of sweep.sh (default replays/bench/dev-first-wave-2026-11-05); the output goes to stdout.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "tools"))
import bench_pair  # noqa: E402

ARMS = ("WAVE_TOWERS=2", "WAVE_TOWERS=3", "TOPUP_FROM=2", "WAVE_TOWERS=2,TOPUP_FROM=2")


def name(arm):
    return arm.lower().replace(",", "_").replace("=", "-")


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/dev-first-wave-2026-11-05"
    ctrl = bench_pair.load(os.path.join(d, "ctrl-field.json"))
    print("Seeds %s + %s k, k < %s; each map on both seats. S against v08 with its 95 %% CI; field: the arm's map score"
          " against field minus v08's on the same maps (control %.4f)." % (ctrl["seedBase"], ctrl["seedStride"], ctrl["maps"], ctrl["score"]))
    print("%-30s %-28s %-30s %s" % ("arm", "S vs v08", "delta vs field", "candidate failures (both series)"))
    for arm in ARMS:
        direct = bench_pair.direct(bench_pair.load(os.path.join(d, "%s-v08.json" % name(arm))))
        common = bench_pair.common(bench_pair.load(os.path.join(d, "%s-field.json" % name(arm))), ctrl)
        fails = {k: direct["candidateFailures"][k] + common["candidateFailures"][k] for k in direct["candidateFailures"]}
        s, dl = direct["score"], common["delta"]
        print("%-30s %.4f (%.4f..%.4f)       %+.4f (%+.4f..%+.4f)        %s" % (
            arm, s["mean"], s["lower"], s["upper"], dl["mean"], dl["lower"], dl["upper"], fails))


if __name__ == "__main__":
    main()
