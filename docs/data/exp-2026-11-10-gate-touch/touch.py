"""The profiles of the "hold before the touch rule" arms (2026-11-10): what the gate's turns go to, the contact episodes
that begin at an upgrade or a build, and what the flag costs. From the probe's csv files of profile.sh (the open-ground
experiment's script with this experiment's directory), 200 maps on both seats, with the definitions of the arena
measurement (docs/data/study-2026-11-10-v14-cover/cover.py) and the v13 study's turn classes (probe_tables.turn_class):
  a contact turn    an enemy knight within 250 of our queen, turns from 50; an episode is a run of them;
  the gate's state  turn 30 on, two own towers or more, an enemy knight within 500 and none within 250;
  begun at          the class of the turn before the episode: a mine upgrade (a BUILD MINE on the own mine she touches), a
                    new mine, barracks or tower (a BUILD on the free site she touches).
Per profile: the result, our queen's deaths, the HP she loses to knights from turn 50 and the enemy queen's to ours; the
episodes begun at a mine upgrade and at a new structure (a game, HP a game) and all episodes' HP; the gate's turns a
game that go to a mine upgrade, a new mine, a new barracks and a new tower; then the costs: mine upgrades a game, the
income at turns 100, 150 and 200, own towers at turns 100 and 150, own mines and barracks at turn 150, knight releases
a game. Then every arm minus v14 on the same maps with the 95 % interval of a bootstrap over maps (a map is its two
games; 2000 resamples, seed 1).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-gate-touch/touch.py [<profile directory>]
The directory defaults to replays/bench/exp-2026-11-10-gate-touch/profiles; v14's profiles come first, then the arms in
the order they were written. Output: stdout (touch.txt beside this file).
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
KEYS = ("games", "score", "dead", "hp50", "their50", "upN", "upHp", "newN", "newHp", "allHp", "gUp", "gMine", "gBar", "gTow", "ups", "rel",
        "inc100", "n100", "inc150", "n150", "inc200", "n200", "t100", "t150", "m150", "b150")


def per_map(d):
    """{seed: the sums over the map's two games}."""
    games = {g["gid"]: g for g in read(os.path.join(d, "games.csv"))}
    g = by_game(read(os.path.join(d, "turns.csv")))
    rel = {}
    for w in read(os.path.join(d, "waves.csv")):
        if w["us"] == 1 and w["type"] == "K":
            rel[w["gid"]] = rel.get(w["gid"], 0) + 1
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
        m["rel"] += rel.get(gid, 0)
        for first, before, kd, length in episodes(rs):
            c = "none" if before is None else turn_class(before)
            m["allHp"] += kd
            if c == "mine upgrade":
                m["upN"] += 1
                m["upHp"] += kd
            elif c.startswith("new "):
                m["newN"] += 1
                m["newHp"] += kd
        for r in rs:
            c = turn_class(r)
            if c == "mine upgrade":
                m["ups"] += 1
            if gate(r):
                m["gUp"] += c == "mine upgrade"
                m["gMine"] += c == "new mine"
                m["gBar"] += c == "new barracks"
                m["gTow"] += c == "new tower"
            for t in (100, 150, 200):
                if r["t"] == t:
                    m["inc%d" % t] += r["income"]
                    m["n%d" % t] += 1
            if r["t"] == 100:
                m["t100"] += r["T"]
            if r["t"] == 150:
                m["t150"] += r["T"]
                m["m150"] += r["M"]
                m["b150"] += r["B"]
    return out


def a_game(key):
    return lambda a: sum(m[key] for m in a) / float(sum(m["games"] for m in a))


def at_turn(key, t):
    return lambda a: sum(m[key] for m in a) / float(max(1, sum(m["n%d" % t] for m in a)))


STATS = (
    ("score", a_game("score"), "%+.4f"),
    ("deaths per 400 games", lambda a: 400 * a_game("dead")(a), "%+.1f"),
    ("HP to knights from turn 50, a game", a_game("hp50"), "%+.2f"),
    ("HP of episodes begun at a mine upgrade, a game", a_game("upHp"), "%+.2f"),
    ("HP of episodes begun at a new structure, a game", a_game("newHp"), "%+.2f"),
    ("HP of all contact episodes from turn 50, a game", a_game("allHp"), "%+.2f"),
    ("the enemy queen's HP to our knights from turn 50, a game", a_game("their50"), "%+.2f"),
    ("mine upgrades a game", a_game("ups"), "%+.2f"),
    ("income at turn 100", at_turn("inc100", 100), "%+.2f"),
    ("income at turn 150", at_turn("inc150", 150), "%+.2f"),
    ("income at turn 200", at_turn("inc200", 200), "%+.2f"),
    ("own towers at turn 150", at_turn("t150", 150), "%+.2f"),
    ("own mines at turn 150", at_turn("m150", 150), "%+.2f"),
    ("own barracks at turn 150", at_turn("b150", 150), "%+.2f"),
    ("knight releases a game", a_game("rel"), "%+.2f"),
)


def line(name, a):
    f = lambda key: a_game(key)(a)
    print("%-22s score %.4f, deaths %2d; HP from turn 50 %5.2f (theirs %5.2f); episodes begun at a mine upgrade %.2f a game / %.2f HP, at a new structure"
          " %.2f / %.2f, all %.2f HP; the gate's turns to an upgrade %.2f, a new mine %.2f, barracks %.2f, tower %.2f; upgrades %.2f a game; income"
          " %.2f / %.2f / %.2f; towers %.2f / %.2f; mines %.2f, barracks %.2f at 150; releases %.2f"
          % (name, f("score"), sum(m["dead"] for m in a), f("hp50"), f("their50"), f("upN"), f("upHp"), f("newN"), f("newHp"), f("allHp"),
             f("gUp"), f("gMine"), f("gBar"), f("gTow"), f("ups"), at_turn("inc100", 100)(a), at_turn("inc150", 150)(a), at_turn("inc200", 200)(a),
             at_turn("t100", 100)(a), at_turn("t150", 150)(a), at_turn("m150", 150)(a), at_turn("b150", 150)(a), f("rel")))


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-gate-touch/profiles"
    arms = []
    for p in sorted(glob.glob(os.path.join(d, "*-v14")), key=lambda p: os.path.getmtime(os.path.join(p, "games.csv"))):
        name = os.path.basename(p)[:-len("-v14")]
        if name != "v14" and all(os.path.exists(os.path.join(d, "%s-%s" % (name, o), "games.csv")) for o in OPP):
            arms.append(name)
    data = {(n, o): per_map(os.path.join(d, "%s-%s" % (n, o))) for n in ["v14"] + arms for o in OPP}
    print("Seeds 1 + 397 k, k < 200, both seats. Income at turns 100 / 150 / 200; own towers at turns 100 / 150.")
    for o in OPP:
        print("\nagainst %s" % o)
        for n in ["v14"] + arms:
            line(n, list(data[(n, o)].values()))
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
