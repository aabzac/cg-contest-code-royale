"""v13's Gold games (study 2026-11-09): the turns our queen spends topping up a tower that already has 700 HP or more.
When they happen, what stands by then, and what else was within reach. Input: ArenaProbe's turns.csv; output idle.txt.

Usage (from the repository root): python -B -I docs/data/study-2026-11-09-v13-gold/idle.py [--v13 <probe dir>] [--v12 <probe dir>]
"""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe_tables import by_game, meta, read  # noqa: E402
from v13_gold import mean, med  # noqa: E402


def full_topup(r):
    return r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T" and r["touchHp"] >= 700


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    dirs = {"v12": arg("--v12", os.path.join("replays", "study", "gold-v13-2026-11-09", "probe-v12")),
            "v13": arg("--v13", os.path.join("replays", "study", "gold-v13-2026-11-09", "probe"))}
    for n in ("v12", "v13"):
        turns = [r for r in read(os.path.join(dirs[n], "turns.csv")) if r["us"] == 1]
        m = meta(n)
        games = by_game(turns)
        ng = float(len(games))
        full = [r for r in turns if full_topup(r)]
        print("%s: top-ups of a tower at 700 HP or more: %.1f a game (%.1f %% of our turns)" % (n, len(full) / ng, 100.0 * len(full) / len(turns)))
        for label, rows in (("a knight within 250", [r for r in full if r["k250"] > 0]), ("a knight within 500, none within 250", [r for r in full if r["k250"] == 0 and r["k500"] > 0]),
                            ("no knight within 500", [r for r in full if r["k500"] == 0])):
            if not rows:
                continue
            print("   %-38s %5.1f a game; towers %.1f (6 or more in %.0f %%), barracks %.1f (2 in %.0f %%), mines %.1f, income %.1f (below 3 in %.0f %%); enemy knights alive %.1f; turn median %.0f" % (
                label, len(rows) / ng, mean([r["T"] for r in rows]), 100 * mean([1 if r["T"] >= 6 else 0 for r in rows]), mean([r["B"] for r in rows]),
                100 * mean([1 if r["B"] >= 2 else 0 for r in rows]), mean([r["M"] for r in rows]), mean([r["income"] for r in rows]),
                100 * mean([1 if r["income"] < 3 else 0 for r in rows]), mean([r["eKnights"] for r in rows]), med([float(r["t"]) for r in rows])))
            print("   %-38s a free site not under an enemy tower exists in %.0f %% (the nearest %.0f away, median); one under an own tower's cover in %.0f %% (with gold %.0f %%; nearest %.0f away, median); another own tower below 700 in %.0f %%" % (
                "", 100 * mean([1 if r["nearFree"] >= 0 else 0 for r in rows]), med([r["nearFree"] for r in rows if r["nearFree"] >= 0]),
                100 * mean([1 if r["covFree"] > 0 else 0 for r in rows]), 100 * mean([1 if r["covFreeGold"] > 0 else 0 for r in rows]),
                med([r["nearCovFree"] for r in rows if r["nearCovFree"] >= 0]), 100 * mean([1 if r["towersLow"] > 0 else 0 for r in rows])))
        clear = [r for r in full if r["k500"] == 0]
        print("   with no knight within 500, by what stands: %s" % ", ".join("T%d B%d: %.1f" % (k[0], k[1], c / ng) for k, c in Counter((min(r["T"], 6), r["B"]) for r in clear).most_common(8)))
        print("   with no knight within 500, by turn: %s" % ", ".join("%d-%d: %.1f" % (lo, hi, sum(1 for r in clear if lo <= r["t"] < hi) / ng) for lo, hi in ((0, 50), (50, 100), (100, 150), (150, 200), (200, 250))))
        late = [r for r in clear if r["t"] >= 200]
        print("   of them from turn 200 (the late hold when ahead, or the ladder's end): %.1f a game" % (len(late) / ng))
        # Per game: the full top-ups and the result.
        per = []
        for (gid, us), rows in games.items():
            f = sum(1 for r in rows if full_topup(r))
            per.append((f, m[gid]["won"], m[gid]["rating"], len(rows)))
        for lo, hi in ((0, 20), (20, 40), (40, 60), (60, 90), (90, 999)):
            gg = [p for p in per if lo <= p[0] < hi]
            if gg:
                print("   games with %3d-%3d such top-ups: %3d, won %.0f %%, mean rating %.1f, mean length %.0f" % (lo, hi - 1, len(gg), 100 * mean([1 if p[1] else 0 for p in gg]), mean([p[2] for p in gg]), mean([float(p[3]) for p in gg])))
        # The first such top-up with no knight near: when, and what stood.
        firsts = []
        for (gid, us), rows in games.items():
            f = next((r for r in rows if full_topup(r) and r["k500"] == 0 and r["t"] >= 30), None)
            if f:
                firsts.append(f)
        print("   the first one with no knight within 500 (from turn 30): in %d games, turn median %.0f; towers %.1f, barracks %.1f, mines %.1f, income %.1f; a covered free gold site in %.0f %%" % (
            len(firsts), med([float(r["t"]) for r in firsts]), mean([r["T"] for r in firsts]), mean([r["B"] for r in firsts]), mean([r["M"] for r in firsts]),
            mean([r["income"] for r in firsts]), 100 * mean([1 if r["covFreeGold"] > 0 else 0 for r in firsts])))
        print()


if __name__ == "__main__":
    main()
