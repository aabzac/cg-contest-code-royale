"""The arms' profiles minus v14's on the same maps (2026-11-10, "hold at the towers only"): differences a game with
95 % intervals from a bootstrap over maps (a map is its two games, one on each seat; 2000 resamples, seed 1). The
definitions are begun.py's. Per arm and opponent:
  the map score (win 1, draw 1/2); our queen's deaths per 400 games;
  the HP she loses to knights from turn 50; the HP of the contact episodes begun on open ground, and of those begun at
  or on a walk to an own tower;
  the contact episodes begun on open ground; the contact turns from turn 50 under one or two own towers, and the share
  under three or more (in points).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-open-ground/paired.py [<profile directory>]
Output: stdout (paired.txt beside this file).
"""
import glob
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-10-v14-cover"))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-09-v13-gold"))
from cover import began, episodes  # noqa: E402
from probe_tables import by_game, read  # noqa: E402

OPP = ("v14", "rival", "field")
TOWER = ("at an own tower", "on a walk to an own tower")


def per_map(d):
    """{seed: the sums over the map's two games}."""
    games = {g["gid"]: g for g in read(os.path.join(d, "games.csv"))}
    ours = {gid: rows for (gid, us), rows in by_game(read(os.path.join(d, "turns.csv"))).items() if us == 1}
    out = {}
    for gid, rs in ours.items():
        m = out.setdefault(gid // 10, {"games": 0, "score": 0.0, "dead": 0, "hp50": 0, "openHp": 0, "towerHp": 0, "open": 0, "low": 0, "high": 0})
        g = games[gid]
        eps = episodes(rs)
        con = [r for r in rs if r["t"] >= 50 and r["k250"] > 0]
        m["games"] += 1
        m["score"] += 1.0 if g["won"] else 0.5 if g["draw"] else 0.0
        m["dead"] += 1 if rs[-1]["hpAfter"] <= 0 else 0
        m["hp50"] += sum(r["kd"] for r in rs if r["t"] >= 50)
        m["openHp"] += sum(e[2] for e in eps if began(e[1]) == "on open ground")
        m["towerHp"] += sum(e[2] for e in eps if began(e[1]) in TOWER)
        m["open"] += sum(1 for e in eps if began(e[1]) == "on open ground")
        m["low"] += sum(1 for r in con if r["towersCov"] <= 2)
        m["high"] += sum(1 for r in con if r["towersCov"] >= 3)
    return out


STATS = (
    ("score", lambda a: sum(m["score"] for m in a) / sum(m["games"] for m in a), "%+.4f"),
    ("deaths per 400 games", lambda a: 400.0 * sum(m["dead"] for m in a) / sum(m["games"] for m in a), "%+.1f"),
    ("HP to knights from turn 50, a game", lambda a: sum(m["hp50"] for m in a) / sum(m["games"] for m in a), "%+.2f"),
    ("HP of episodes begun on open ground, a game", lambda a: sum(m["openHp"] for m in a) / sum(m["games"] for m in a), "%+.2f"),
    ("HP of episodes begun at or on a walk to a tower, a game", lambda a: sum(m["towerHp"] for m in a) / sum(m["games"] for m in a), "%+.2f"),
    ("episodes begun on open ground, a game", lambda a: sum(m["open"] for m in a) / sum(m["games"] for m in a), "%+.2f"),
    ("contact turns under one or two towers, a game", lambda a: sum(m["low"] for m in a) / sum(m["games"] for m in a), "%+.2f"),
    ("share of contact turns under three or more, points", lambda a: 100.0 * sum(m["high"] for m in a) / max(1, sum(m["high"] + m["low"] for m in a)), "%+.1f"),
)


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-open-ground/profiles"
    base = {o: per_map(os.path.join(d, "v14-" + o)) for o in OPP}
    arms = []
    for p in sorted(glob.glob(os.path.join(d, "*-v14")), key=lambda p: os.path.getmtime(os.path.join(p, "games.csv"))):
        name = os.path.basename(p)[:-len("-v14")]
        if name != "v14":
            arms.append(name)
    print("Seeds 1 + 397 k, k < 200, both seats: the arm minus v14 on the same maps, with the 95 % interval of a bootstrap over maps;"
          " v14's own value in brackets.")
    for name in arms:
        for o in OPP:
            arm = per_map(os.path.join(d, "%s-%s" % (name, o)))
            seeds = sorted(base[o])
            assert seeds == sorted(arm), (name, o)
            print("\n%s against %s (%d maps)" % (name, o, len(seeds)))
            rnd = random.Random(1)
            draws = [[seeds[rnd.randrange(len(seeds))] for _ in seeds] for _ in range(2000)]
            for label, stat, f in STATS:
                v0 = stat([base[o][s] for s in seeds])
                xs = sorted(stat([arm[s] for s in dr]) - stat([base[o][s] for s in dr]) for dr in draws)
                print(("   %-58s " + f + "  (" + f + ".." + f + ")  [v14 %s]") % (label, stat([arm[s] for s in seeds]) - v0, xs[50], xs[1949], (f % v0).lstrip("+")))


if __name__ == "__main__":
    main()
