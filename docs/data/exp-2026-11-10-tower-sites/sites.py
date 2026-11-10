"""The profiles of the "tower sites chosen for overlap" arms (2026-11-10): where the ladder's towers stand, what the
queen gets from it and what it costs. From the probe's csv files of profile.sh (the open-ground experiment's script with
this experiment's directory), 200 maps on both seats, with the definitions of the arena measurement
(docs/data/study-2026-11-10-v14-cover/cover.py) and the v13 study's turn classes (probe_tables.turn_class):
  a contact turn    an enemy knight within 250 of our queen, turns from 50; an episode is a run of them;
  the gate's state  turn 30 on, two own towers or more, an enemy knight within 500 and none within 250;
  linked            two own towers whose centres are within 400 (or 500) of each other; a tower's links are counted on
                    the turn it is built, over the own towers standing then (events.csv);
  covered at birth  the new tower's centre lies inside another own tower's range on that turn (the probe's column).
Per profile: the result, our queen's deaths; the contact turns from turn 50 a game, the mean number of own towers
covering her on them, the share under three or more and the HP she loses to knights from turn 50 (and the enemy queen's
to ours); the cover the board offers: the share of our turns at 50-99 and 100-149 with a spot under three or more own
towers within 300 of her (bestCov300), the first turn she stands under three or more and the first turn such a spot is
within 300, as the share of games by turns 60, 80, 100 and the mean over the games that get there; the towers: built
in a game, the links at birth of the second, third and fourth tower and of all from the second on (within 400 and
within 500), the share covered at birth; then the ladder's pace and the economy: own towers at turns 50, 75, 100 and
150, the HP of a tower at 150, the turn the second barracks stands (share of games, mean turn), income at 100 and 150,
mines at 150, knight releases, and the turns a game that go to a walk to a new site.
Then every arm minus v14 on the same maps with the 95 % interval of a bootstrap over maps (a map is its two games;
2000 resamples, seed 1).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-tower-sites/sites.py [<profile directory>]
The directory defaults to replays/bench/exp-2026-11-10-tower-sites/profiles; v14's profiles come first, then the arms
in the order they were written. Output: stdout (sites.txt beside this file).
"""
import glob
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-10-v14-cover"))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-09-v13-gold"))
from cover import episodes, gate  # noqa: E402
from probe_tables import by_game, read, turn_class  # noqa: E402

OPP = ("v14", "rival", "field")
KEYS = ("games", "score", "dead", "con", "covSum", "con3", "conLow", "hp50", "their50", "epHp", "gateTurns", "gate3",
        "n50", "spot50", "n100", "spot100", "std60", "std80", "std100", "stdN", "stdSum", "sp60", "sp80", "sp100", "spN", "spSum",
        "built", "from2", "l400", "l500", "born", "k2", "k2l400", "k2l500", "k3", "k3l400", "k3l500", "k4", "k4l400", "k4l500",
        "t50", "c50", "t75", "c75", "t100", "c100", "t150", "c150", "thp150", "bar2", "bar2Sum", "inc100", "inc150", "m150", "rel", "walk",
        "full", "hpFirst")


def per_map(d):
    """{seed: the sums over the map's two games}."""
    games = {g["gid"]: g for g in read(os.path.join(d, "games.csv"))}
    g = by_game(read(os.path.join(d, "turns.csv")))
    rel = {}
    for w in read(os.path.join(d, "waves.csv")):
        if w["us"] == 1 and w["type"] == "K":
            rel[w["gid"]] = rel.get(w["gid"], 0) + 1
    towers = {}
    for e in read(os.path.join(d, "events.csv")):
        if e["us"] == 1 and e["kind"] == "TOWER":
            towers.setdefault(e["gid"], []).append(e)
    out = {}
    for (gid, us), rs in g.items():
        m = out.setdefault(gid // 10, dict.fromkeys(KEYS, 0))
        if us == 0:
            m["their50"] += sum(r["kd"] for r in rs if r["t"] >= 50)
            continue
        x = games[gid]
        m["games"] += 1
        m["score"] += 1.0 if x["won"] else 0.5 if x["draw"] else 0.0
        m["dead"] += 1 if rs[-1]["hpAfter"] <= 0 else 0
        m["hp50"] += sum(r["kd"] for r in rs if r["t"] >= 50)
        m["hpFirst"] += sum(r["kd"] for r in rs if r["t"] < 50)
        m["rel"] += rel.get(gid, 0)
        m["epHp"] += sum(e[2] for e in episodes(rs))
        std = sp = bar2 = None
        for r in rs:
            t = r["t"]
            if t >= 50 and r["k250"] > 0:
                m["con"] += 1
                m["covSum"] += r["towersCov"]
                m["con3" if r["towersCov"] >= 3 else "conLow"] += 1
            if t >= 50 and gate(r):
                m["gateTurns"] += 1
                m["gate3"] += r["towersCov"] >= 3
            if 50 <= t < 100:
                m["n50"] += 1
                m["spot50"] += r["bestCov300"] >= 3
            elif 100 <= t < 150:
                m["n100"] += 1
                m["spot100"] += r["bestCov300"] >= 3
            if std is None and r["towersCov"] >= 3:
                std = t
            if sp is None and r["bestCov300"] >= 3:
                sp = t
            if bar2 is None and r["B"] >= 2:
                bar2 = t
            for at in (50, 75, 100, 150):
                if t == at:
                    m["t%d" % at] += r["T"]
                    m["c%d" % at] += 1
            if t == 100:
                m["inc100"] += r["income"]
            if t == 150:
                m["inc150"] += r["income"]
                m["m150"] += r["M"]
                m["thp150"] += r["thpSum"]
            c = turn_class(r)
            m["walk"] += c == "walk to a site"
            m["full"] += c == "top-up at 700+"
        for key, v in (("std", std), ("sp", sp)):
            for lim in (60, 80, 100):
                m["%s%d" % (key, lim)] += v is not None and v <= lim
            if v is not None:
                m[key + "N"] += 1
                m[key + "Sum"] += v
        if bar2 is not None:
            m["bar2"] += 1
            m["bar2Sum"] += bar2
        # The towers in the order they were built, with the own towers standing on that turn.
        standing, k = {}, 0
        for e in sorted(towers.get(gid, []), key=lambda e: (e["t"], e["event"] != "lost")):
            if e["event"] == "lost":
                standing.pop(e["site"], None)
                continue
            if e["cause"] == "over-own" and e["site"] in standing:
                continue
            k += 1
            m["built"] += 1
            l4 = sum(1 for (px, py) in standing.values() if (px - e["x"]) ** 2 + (py - e["y"]) ** 2 < 400.0 ** 2)
            l5 = sum(1 for (px, py) in standing.values() if (px - e["x"]) ** 2 + (py - e["y"]) ** 2 < 500.0 ** 2)
            if k >= 2:
                m["from2"] += 1
                m["l400"] += l4
                m["l500"] += l5
                m["born"] += e["covered"]
            if k in (2, 3, 4):
                m["k%d" % k] += 1
                m["k%dl400" % k] += l4
                m["k%dl500" % k] += l5
            standing[e["site"]] = (e["x"], e["y"])
    return out


def ratio(num, den, scale=1.0):
    return lambda a: scale * sum(m[num] for m in a) / float(max(1, sum(m[den] for m in a)))


STATS = (
    ("score", ratio("score", "games"), "%+.4f"),
    ("deaths per 400 games", ratio("dead", "games", 400), "%+.1f"),
    ("HP to knights from turn 50, a game", ratio("hp50", "games"), "%+.2f"),
    ("HP to knights before turn 50, a game", ratio("hpFirst", "games"), "%+.2f"),
    ("the enemy queen's HP to our knights from turn 50, a game", ratio("their50", "games"), "%+.2f"),
    ("contact turns from turn 50, a game", ratio("con", "games"), "%+.2f"),
    ("contact turns under one or two own towers, a game", ratio("conLow", "games"), "%+.2f"),
    ("share of contact turns under three or more, points", ratio("con3", "con", 100), "%+.1f"),
    ("share of the gate's turns under three or more, points", ratio("gate3", "gateTurns", 100), "%+.1f"),
    ("turns 50-99 with a spot under 3+ within 300, points", ratio("spot50", "n50", 100), "%+.1f"),
    ("turns 100-149 with a spot under 3+ within 300, points", ratio("spot100", "n100", 100), "%+.1f"),
    ("games standing under 3+ by turn 60, points", ratio("std60", "games", 100), "%+.1f"),
    ("games standing under 3+ by turn 80, points", ratio("std80", "games", 100), "%+.1f"),
    ("games with a spot under 3+ by turn 60, points", ratio("sp60", "games", 100), "%+.1f"),
    ("games with a spot under 3+ by turn 80, points", ratio("sp80", "games", 100), "%+.1f"),
    ("links within 400 at birth, towers from the second on", ratio("l400", "from2"), "%+.2f"),
    ("links within 500 at birth, towers from the second on", ratio("l500", "from2"), "%+.2f"),
    ("towers built, a game", ratio("built", "games"), "%+.2f"),
    ("own towers at turn 50", ratio("t50", "c50"), "%+.2f"),
    ("own towers at turn 75", ratio("t75", "c75"), "%+.2f"),
    ("own towers at turn 100", ratio("t100", "c100"), "%+.2f"),
    ("own towers at turn 150", ratio("t150", "c150"), "%+.2f"),
    ("HP of a tower at turn 150", ratio("thp150", "t150"), "%+.0f"),
    ("games with the second barracks, points", ratio("bar2", "games", 100), "%+.1f"),
    ("the turn of the second barracks", ratio("bar2Sum", "bar2"), "%+.1f"),
    ("income at turn 100", ratio("inc100", "c100"), "%+.2f"),
    ("income at turn 150", ratio("inc150", "c150"), "%+.2f"),
    ("own mines at turn 150", ratio("m150", "c150"), "%+.2f"),
    ("knight releases, a game", ratio("rel", "games"), "%+.2f"),
    ("turns on a walk to a new site, a game", ratio("walk", "games"), "%+.2f"),
    ("top-ups of a tower at 700+, a game", ratio("full", "games"), "%+.2f"),
)


def block(name, a):
    f = lambda num, den, scale=1.0: ratio(num, den, scale)(a)
    print("%s: score %.4f, queen deaths %d" % (name, f("score", "games"), sum(m["dead"] for m in a)))
    print("   contact turns from turn 50: %.1f a game, %.2f own towers over her, %.0f %% under three or more; HP to knights from turn 50 %.2f a game"
          " (before turn 50 %.2f; the enemy queen's from turn 50 %.2f); the gate's turns %.1f a game, %.0f %% under three or more"
          % (f("con", "games"), f("covSum", "con"), f("con3", "con", 100), f("hp50", "games"), f("hpFirst", "games"), f("their50", "games"),
             f("gateTurns", "games"), f("gate3", "gateTurns", 100)))
    print("   a spot under 3+ within 300 of her: %.0f %% of the turns 50-99, %.0f %% of 100-149; she stands under 3+ by turn 60 / 80 / 100 in"
          " %.0f / %.0f / %.0f %% of the games (first at turn %.0f in the %.0f %% that get there); such a spot within 300 by 60 / 80 / 100 in"
          " %.0f / %.0f / %.0f %% (first at %.0f)"
          % (f("spot50", "n50", 100), f("spot100", "n100", 100), f("std60", "games", 100), f("std80", "games", 100), f("std100", "games", 100),
             f("stdSum", "stdN"), f("stdN", "games", 100), f("sp60", "games", 100), f("sp80", "games", 100), f("sp100", "games", 100), f("spSum", "spN")))
    print("   towers built %.2f a game; links at birth within 400 / 500: the second %.2f / %.2f, the third %.2f / %.2f, the fourth %.2f / %.2f,"
          " all from the second %.2f / %.2f; covered at birth %.0f %%"
          % (f("built", "games"), f("k2l400", "k2"), f("k2l500", "k2"), f("k3l400", "k3"), f("k3l500", "k3"), f("k4l400", "k4"), f("k4l500", "k4"),
             f("l400", "from2"), f("l500", "from2"), f("born", "from2", 100)))
    print("   own towers at 50 / 75 / 100 / 150: %.2f / %.2f / %.2f / %.2f @ %.0f HP at 150; the second barracks in %.0f %% of the games at turn %.0f;"
          " income %.2f / %.2f, mines %.2f at 150; releases %.2f; walks to a new site %.1f turns a game; top-ups at 700+ %.1f"
          % (f("t50", "c50"), f("t75", "c75"), f("t100", "c100"), f("t150", "c150"), f("thp150", "t150"), f("bar2", "games", 100), f("bar2Sum", "bar2"),
             f("inc100", "c100"), f("inc150", "c150"), f("m150", "c150"), f("rel", "games"), f("walk", "games"), f("full", "games")))


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-tower-sites/profiles"
    arms = []
    for p in sorted(glob.glob(os.path.join(d, "*-v14")), key=lambda p: os.path.getmtime(os.path.join(p, "games.csv"))):
        name = os.path.basename(p)[:-len("-v14")]
        if name != "v14" and all(os.path.exists(os.path.join(d, "%s-%s" % (name, o), "games.csv")) for o in OPP):
            arms.append(name)
    data = {(n, o): per_map(os.path.join(d, "%s-%s" % (n, o))) for n in ["v14"] + arms for o in OPP}
    print("Seeds 1 + 397 k, k < 200, both seats.")
    for o in OPP:
        print("\nagainst %s" % o)
        for n in ["v14"] + arms:
            block(n, list(data[(n, o)].values()))
    if arms:
        print("\nThe arm minus v14 on the same maps, with the 95 % interval of a bootstrap over maps; v14's own value in brackets.")
    for n in arms:
        for o in OPP:
            base, arm = data[("v14", o)], data[(n, o)]
            seeds = sorted(base)
            assert seeds == sorted(arm), (n, o)
            print("\n%s against %s (%d maps)" % (n, o, len(seeds)))
            rnd = random.Random(1)
            draws = [[seeds[rnd.randrange(len(seeds))] for _ in seeds] for _ in range(2000)]
            for label, stat, f in STATS:
                v0 = stat([base[s] for s in seeds])
                xs = sorted(stat([arm[s] for s in dr]) - stat([base[s] for s in dr]) for dr in draws)
                print(("   %-58s " + f + "  (" + f + ".." + f + ")  [v14 %s]") % (label, stat([arm[s] for s in seeds]) - v0, xs[50], xs[1949], (f % v0).lstrip("+")))


if __name__ == "__main__":
    main()
