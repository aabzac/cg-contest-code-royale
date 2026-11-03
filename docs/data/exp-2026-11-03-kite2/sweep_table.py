"""Table of a development sweep: one line per bench report in a directory, sorted by S.
Usage: python -I sweep_table.py <dir> [--mirror mirror.json] [--sort name|score]
Columns: name, S, 95 % CI (over maps), W/D/L, by seat, average turns, candidate failures, changed maps vs the mirror.
"""
import glob
import os
import sys

sys.path.insert(0, os.path.join(os.getcwd(), "tools"))
import bench_pair as bp  # noqa: E402


def main(argv):
    d = argv[0]
    mirror = None
    sort = "score"
    if "--mirror" in argv:
        mirror = bp.by_map(bp.load(argv[argv.index("--mirror") + 1]))
    if "--sort" in argv:
        sort = argv[argv.index("--sort") + 1]
    rows = []
    for path in sorted(glob.glob(os.path.join(d, "*.json"))):
        name = os.path.basename(path)[:-5]
        if name == "mirror":
            continue
        rep = bp.load(path)
        maps = bp.by_map(rep)
        scores = [bp.map_score(p) for p in maps.values()]
        iv = bp.interval(scores)
        rec = bp.record(maps)
        fails, _ = bp.candidate_failures(maps)
        nfail = sum(fails.values())
        turns = sum(g["turns"] for p in maps.values() for g in p) / (2 * len(maps))
        changed = ""
        if mirror is not None:
            c = 0
            for s, pair in maps.items():
                m = mirror.get(s)
                if m is None:
                    continue
                for g in pair:
                    if not bp.same_game(g, m[g["aSeat"]]):
                        c += 1
                        break
            changed = "%d" % c
        w = sum(v["wins"] for v in rec.values())
        dr = sum(v["draws"] for v in rec.values())
        lo = sum(v["losses"] for v in rec.values())
        rows.append((name, iv["mean"], iv["lower"], iv["upper"], w, dr, lo, rec["seat0"]["wins"], rec["seat1"]["wins"], turns, nfail, changed, len(maps)))
    rows.sort(key=(lambda r: -r[1]) if sort == "score" else (lambda r: r[0]))
    print("%-28s %6s %-15s %-14s %-9s %6s %5s %s" % ("arm", "S", "95% CI", "W/D/L", "w0/w1", "turns", "fail", "changed"))
    for r in rows:
        print("%-28s %6.3f %6.3f-%6.3f  %4d/%3d/%4d  %4d/%4d %6.1f %5d %s" % (r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11]))


if __name__ == "__main__":
    main(sys.argv[1:])
