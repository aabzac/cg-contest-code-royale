"""The "hold at the towers only" sweep over v14 as a table (2026-11-10): each arm's S against v14 with the maps changed
against v14's mirror, and its deltas against the rival R0 and field over v14's own scores on the same maps; a line for
every development set and one for the sets pooled (their maps are disjoint; the pooled interval is taken over all of
them, mean +- 1.96 SD / sqrt(maps), as tools/bench_pair.py takes it over one set). At the end the arms against each
other: the difference of their map scores on the same maps, against each opponent.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-open-ground/sweep_table.py [<report directory>]
The directory (default replays/bench/exp-2026-11-10-open-ground) holds sweep.sh's dev-<base> directories; the arms come
in the order they were first run. Output: stdout (sweep.txt beside this file).
"""
import glob
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "tools"))
import bench_pair  # noqa: E402

OPP = ("v14", "rival", "field")


def iv(x, signed=True):
    f = "%+.4f (%+.4f..%+.4f)" if signed else "%.4f (%.4f..%.4f)"
    return f % (x["mean"], x["lower"], x["upper"])


def scores(rep):
    """{seed: botA's map score} of the maps where the opponent did not fail."""
    maps = bench_pair.by_map(rep)
    return {s: bench_pair.map_score(p) for s, p in maps.items() if not any(bench_pair.failures(g, "B", bench_pair.OPPONENT_FAILURES) for g in p)}


def line(label, direct, changed, rival, rchanged, field, fchanged, n, fails):
    print("%-34s %5d  %-26s %-8s %-28s %-8s %-28s %-8s %s" % (label, n, iv(direct, False), changed, iv(rival), rchanged, iv(field), fchanged, fails))


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-open-ground"
    sets = sorted(glob.glob(os.path.join(root, "dev-*")), key=lambda p: int(p.rsplit("-", 1)[1]))
    arms = []
    for d in sets:
        for path in sorted(glob.glob(os.path.join(d, "*-v14.json")), key=os.path.getmtime):
            name = os.path.basename(path)[:-len("-v14.json")]
            if name != "ctrl" and name not in arms:
                arms.append(name)
    ctrl = {d: {o: bench_pair.load(os.path.join(d, "ctrl-%s.json" % o)) for o in OPP} for d in sets}
    print("Development sets BASE + 397 k, k < 500, each map on both seats. S against v14 with its 95 % CI and the maps whose games"
          " differ from v14's mirror; rival and field: the arm's map score minus v14's on the same maps, and the maps whose games differ.")
    for d in sets:
        c = ctrl[d]
        print("set %s: v14 scores %.4f against rival and %.4f against field" % (os.path.basename(d), bench_pair.interval(list(scores(c["rival"]).values()))["mean"],
                                                                              bench_pair.interval(list(scores(c["field"]).values()))["mean"]))
    print("%-34s %5s  %-26s %-8s %-28s %-8s %-28s %-8s %s" % ("arm, set", "maps", "S vs v14", "changed", "delta vs rival", "changed", "delta vs field", "changed", "candidate failures"))
    by_arm = {}
    for name in arms:
        pooled = {"direct": [], "rival": [], "field": []}
        changed = {"direct": 0, "rival": 0, "field": 0}
        fails = {}
        arm = None
        for d in sets:
            paths = {o: os.path.join(d, "%s-%s.json" % (name, o)) for o in OPP}
            if not all(os.path.exists(p) for p in paths.values()):
                continue
            rep = bench_pair.load(paths["v14"])
            arm = rep["botA"].split("?", 1)[-1]
            for o in OPP:
                by_arm.setdefault(arm, {}).setdefault(o, {}).update({(d, s): v for s, v in scores(bench_pair.load(paths[o])).items()})
            direct = bench_pair.direct(rep, ctrl[d]["v14"])
            rival = bench_pair.common(bench_pair.load(paths["rival"]), ctrl[d]["rival"])
            field = bench_pair.common(bench_pair.load(paths["field"]), ctrl[d]["field"])
            f = {k: direct["candidateFailures"][k] + rival["candidateFailures"][k] + field["candidateFailures"][k] for k in direct["candidateFailures"]}
            for k, v in f.items():
                fails[k] = fails.get(k, 0) + v
            line("%s, %s" % (arm, os.path.basename(d)), direct["score"], len(direct["changedMaps"]), rival["delta"], len(rival["changedMaps"]),
                 field["delta"], len(field["changedMaps"]), direct["keptMaps"], {k: v for k, v in f.items() if v} or 0)
            pooled["direct"] += list(scores(rep).values())
            for o in ("rival", "field"):
                a, b = scores(bench_pair.load(paths[o])), scores(ctrl[d][o])
                pooled[o] += [a[s] - b[s] for s in a if s in b]
            changed["direct"] += len(direct["changedMaps"])
            changed["rival"] += len(rival["changedMaps"])
            changed["field"] += len(field["changedMaps"])
        if arm is not None:
            line("%s, pooled" % arm, bench_pair.interval(pooled["direct"]), changed["direct"], bench_pair.interval(pooled["rival"]), changed["rival"],
                 bench_pair.interval(pooled["field"]), changed["field"], len(pooled["direct"]), {k: v for k, v in fails.items() if v} or 0)
            print()
    names = list(by_arm)
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            for o in OPP:
                keys = [k for k in by_arm[a][o] if k in by_arm[b][o]]
                differ = [k for k in keys if by_arm[a][o][k] != by_arm[b][o][k]]
                print("%s minus %s against %s, the sets pooled: %s on %d maps (their map scores differ on %d)"
                      % (b, a, o, iv(bench_pair.interval([by_arm[b][o][k] - by_arm[a][o][k] for k in keys])), len(keys), len(differ)))


if __name__ == "__main__":
    main()
