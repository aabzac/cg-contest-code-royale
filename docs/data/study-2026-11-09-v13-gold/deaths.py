"""v13's Gold games (study 2026-11-09): the deaths one by one, the opponents by rating band, the giants, and the economy's
course in lost and won games. Inputs and usage as v13_gold.py (same directory); output deaths.txt.
"""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from v13_gold import command_class, ladder_done, load, mean, med, num, pts  # noqa: E402


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    docs = os.path.join("docs", "data")
    games = load("v13", arg("--v13", os.path.join("replays", "study", "gold-v13-2026-11-09")), docs)
    v12 = load("v12", arg("--v12", os.path.join("replays", "study", "gold-v12-2026-11-08")), docs)

    print("A. v13's deaths, one line each (sorted by the death turn)")
    print("   gid seat rating opp | death | our hp t50/t100/t150 | income us/them t50, t100, t150 | towers us@hp t100, t150 | their knights alive t100/t150 | giants trained | our mines lost | last 30 turns: kd, contact turns, T at death, classes")
    deaths = sorted([g for g in games if g["how"] == "died"], key=lambda g: int(g["r"]["deathTurn"]))
    for g in deaths:
        r, o, turns = g["r"], g["o"], g["ours"]
        last = turns[-30:]
        acc = Counter(command_class(t, ladder_done(t)) for t in last)
        mines_lost = sum(1 for a, b in zip(turns, turns[1:]) if b["M"] < a["M"])
        towers_lost = sum(1 for a, b in zip(turns, turns[1:]) if b["T"] < a["T"])
        f = lambda k: "-" if num(r[k]) is None else "%.0f" % num(r[k])
        fo = lambda k: "-" if num(o[k]) is None else "%.0f" % num(o[k])
        print("   %s %d %.1f %-14s | %3s | %s/%s/%s | %s/%s %s/%s %s/%s | %s@%s %s@%s | %s/%s | G%s | M-%d T-%d | kd %d, contact %d, T %d, %s" % (
            g["gid"], g["seat"], g["rating"], g["opp"][:14], r["deathTurn"], f("hp_50"), f("hp_100"), f("hp_150"),
            f("inc_50"), fo("inc_50"), f("inc_100"), fo("inc_100"), f("inc_150"), fo("inc_150"),
            f("towers_100"), f("thp_100"), f("towers_150"), f("thp_150"), fo("knights_100"), fo("knights_150"), fo("trainG"),
            mines_lost, towers_lost, sum(t["kd"] for t in last), sum(1 for t in last if t["k250"] > 0), turns[-1]["T"],
            ", ".join("%s %d" % (k, c) for k, c in acc.most_common(4))))

    print("\nB. Opponents by rating band (v13's 180 games): medians of their game, our score")
    bands = ((0, 20), (20, 21), (21, 22), (22, 23), (23, 99))
    keys = ["inc_50", "inc_100", "inc_150", "maxIncome", "meanIncome", "minesBuilt", "mineUpgrades", "towersBuilt", "towers_100", "thp_100", "thp_150", "towerTopUps",
            "trainK", "trainG", "barracksBuiltK", "firstBarracks", "firstTower", "knights_100", "knights_150"]
    print("   %-14s %s" % ("band", " | ".join("%d-%d" % b for b in bands)))
    print("   %-14s %s" % ("games", " | ".join("%5d" % sum(1 for g in games if lo <= g["rating"] < hi) for lo, hi in bands)))
    print("   %-14s %s" % ("our score %", " | ".join("%5.0f" % (100 * mean([pts(g) for g in games if lo <= g["rating"] < hi])) for lo, hi in bands)))
    print("   %-14s %s" % ("our deaths", " | ".join("%5d" % sum(1 for g in games if lo <= g["rating"] < hi and g["how"] == "died") for lo, hi in bands)))
    print("   %-14s %s" % ("our kills", " | ".join("%5d" % sum(1 for g in games if lo <= g["rating"] < hi and g["how"] == "killed") for lo, hi in bands)))
    print("   %-14s %s" % ("lost races", " | ".join("%5d" % sum(1 for g in games if lo <= g["rating"] < hi and g["how"] == "limit" and g["result"] == "lost") for lo, hi in bands)))
    print("   %-14s %s" % ("won races", " | ".join("%5d" % sum(1 for g in games if lo <= g["rating"] < hi and g["how"] == "limit" and g["result"] == "won") for lo, hi in bands)))
    for k in keys:
        print("   %-14s %s" % (k, " | ".join("%5.1f" % med([num(g["o"][k]) for g in games if lo <= g["rating"] < hi]) for lo, hi in bands)))
    print("   ours, the same bands")
    for k in ("inc_100", "inc_150", "maxIncome", "meanIncome", "mineUpgrades", "trainK", "towerTopUps", "thp_100", "thp_150"):
        print("   %-14s %s" % (k, " | ".join("%5.1f" % med([num(g["r"][k]) for g in games if lo <= g["rating"] < hi]) for lo, hi in bands)))
    print("   openings of the opponents 22+: %s" % Counter(g["o"]["opening"] for g in games if g["rating"] >= 22).most_common(6))
    print("   openings of the opponents below 22: %s" % Counter(g["o"]["opening"] for g in games if g["rating"] < 22).most_common(6))

    print("\nC. Giants and archers by the opponent")
    for name, gs in (("v12", v12), ("v13", games)):
        gi = [g for g in gs if num(g["o"]["trainG"]) > 0]
        no = [g for g in gs if num(g["o"]["trainG"]) == 0]
        ar = [g for g in gs if num(g["o"]["trainA"]) > 0]
        print("   %s: giants in %d games (score %.0f %%, opponents' mean rating %.2f, our deaths %d, lost races %d); none in %d (score %.0f %%, mean rating %.2f, deaths %d); archers in %d (score %.0f %%)" % (
            name, len(gi), 100 * mean([pts(g) for g in gi]), mean([g["rating"] for g in gi]), sum(1 for g in gi if g["how"] == "died"),
            sum(1 for g in gi if g["how"] == "limit" and g["result"] == "lost"),
            len(no), 100 * mean([pts(g) for g in no]), mean([g["rating"] for g in no]), sum(1 for g in no if g["how"] == "died"),
            len(ar), 100 * mean([pts(g) for g in ar]) if ar else float("nan")))
    for lo, hi in ((20, 22), (22, 99)):
        gi = [g for g in games if lo <= g["rating"] < hi and num(g["o"]["trainG"]) > 0]
        no = [g for g in games if lo <= g["rating"] < hi and num(g["o"]["trainG"]) == 0]
        print("   v13, opponents %d-%d: with giants %d games %.0f %% (deaths %d); without %d games %.0f %% (deaths %d)" % (
            lo, hi, len(gi), 100 * mean([pts(g) for g in gi]) if gi else float("nan"), sum(1 for g in gi if g["how"] == "died"),
            len(no), 100 * mean([pts(g) for g in no]), sum(1 for g in no if g["how"] == "died")))
    gi = [g for g in games if num(g["o"]["trainG"]) > 0]
    print("   v13, games with enemy giants: our towers lost a game %.1f (without %.1f); our towers at t150 %.1f (without %.1f); first giant training by the opponent: n/a in the features" % (
        mean([sum(1 for a, b in zip(g["ours"], g["ours"][1:]) if b["T"] < a["T"]) for g in gi]),
        mean([sum(1 for a, b in zip(g["ours"], g["ours"][1:]) if b["T"] < a["T"]) for g in games if num(g["o"]["trainG"]) == 0]),
        mean([num(g["r"]["towers_150"]) for g in gi]), mean([num(g["r"]["towers_150"]) for g in games if num(g["o"]["trainG"]) == 0])))
    print("   giant players (pseudo: games, our points): %s" % ", ".join("%s %d/%.0f" % (p, n, s) for p, (n, s) in sorted(
        {p: (sum(1 for g in gi if g["opp"] == p), sum(pts(g) for g in gi if g["opp"] == p)) for p in {g["opp"] for g in gi}}.items(), key=lambda kv: -kv[1][0])))

    print("\nD. The economy's course (means), v13: our income and theirs, our mines and theirs, by result")
    for label, gs in (("deaths", [g for g in games if g["how"] == "died"]), ("lost races", [g for g in games if g["how"] == "limit" and g["result"] == "lost"]),
                      ("won races", [g for g in games if g["how"] == "limit" and g["result"] == "won"]), ("kills", [g for g in games if g["how"] == "killed"])):
        parts = []
        for t in (25, 50, 75, 100, 150, 200):
            gg = [g for g in gs if num(g["r"]["inc_%d" % t]) is not None]
            if gg:
                parts.append("t%d %.1f/%.1f (mines %.1f/%.1f, knights alive %.1f/%.1f)" % (t, mean([num(g["r"]["inc_%d" % t]) for g in gg]), mean([num(g["o"]["inc_%d" % t]) for g in gg]),
                             mean([num(g["r"]["mines_%d" % t]) for g in gg]), mean([num(g["o"]["mines_%d" % t]) for g in gg]),
                             mean([num(g["r"]["knights_%d" % t]) for g in gg]), mean([num(g["o"]["knights_%d" % t]) for g in gg])))
        print("   %-10s (%2d): %s" % (label, len(gs), "; ".join(parts)))
    print("   mines lost a game (a drop of the M count in the trace), ours / theirs: %s" % "; ".join(
        "%s %.1f / %.1f" % (label, mean([sum(1 for a, b in zip(g["ours"], g["ours"][1:]) if b["M"] < a["M"]) for g in gs]),
                            mean([sum(1 for a, b in zip(g["theirs"], g["theirs"][1:]) if b["M"] < a["M"]) for g in gs]))
        for label, gs in (("deaths", [g for g in games if g["how"] == "died"]), ("lost races", [g for g in games if g["how"] == "limit" and g["result"] == "lost"]),
                          ("won races", [g for g in games if g["how"] == "limit" and g["result"] == "won"]), ("kills", [g for g in games if g["how"] == "killed"]))))
    print("   gold mined over the game (sum of the income over the turns of the features' meanIncome), ours / theirs: %s" % "; ".join(
        "%s %.0f / %.0f" % (label, mean([num(g["r"]["meanIncome"]) * g["turns"] for g in gs]), mean([num(g["o"]["meanIncome"]) * g["turns"] for g in gs]))
        for label, gs in (("deaths", [g for g in games if g["how"] == "died"]), ("lost races", [g for g in games if g["how"] == "limit" and g["result"] == "lost"]),
                          ("won races", [g for g in games if g["how"] == "limit" and g["result"] == "won"]), ("kills", [g for g in games if g["how"] == "killed"]))))

    print("\nE. Score by the income lead at turn 100 (theirs minus ours), v13 and v12")
    for name, gs in (("v12", v12), ("v13", games)):
        parts = []
        for lo, hi in ((-99, 0), (0, 2), (2, 4), (4, 99)):
            gg = [g for g in gs if num(g["r"]["inc_100"]) is not None and lo <= num(g["o"]["inc_100"]) - num(g["r"]["inc_100"]) < hi]
            if gg:
                parts.append("%+d..%+d: %d games %.0f %% (deaths %d, mean rating %.1f)" % (lo, hi - 1, len(gg), 100 * mean([pts(g) for g in gg]), sum(1 for g in gg if g["how"] == "died"), mean([g["rating"] for g in gg])))
        print("   %s: %s" % (name, "; ".join(parts)))


if __name__ == "__main__":
    main()
