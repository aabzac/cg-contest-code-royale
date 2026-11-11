"""v15's Gold games beside v14's (2026-11-11): what the tower-sites rule (SITE_NEAR=50) did on the arena.
From ArenaProbe's csv files (docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java), with the definitions of the v14
measurement (docs/data/study-2026-11-10-v14-cover/cover.py) and of the local profiles of the experiment
(docs/data/exp-2026-11-10-tower-sites/sites.py):
  a contact turn      an enemy knight within 250 of our queen, turns from 50;
  the gate's state    turn 30 on, two own towers or more, an enemy knight within 500 and none within 250;
  the cover           the own towers whose range covers the queen (towersCov);
  a spot              a point within 300 of her under three or more own towers (bestCov300 >= 3);
  linked              two own towers whose centres are within 400 (or 500) of each other; a tower's links are counted on
                      the turn it is built, over the own towers standing then (events.csv); the towers are numbered in
                      the order they are built, a rebuilt one included;
  covered at birth    the new tower's centre lies inside another own tower's range on that turn (the probe's column).
Uncertainty: a bootstrap over games resampled within each version (2000 resamples, seed 1), 95 % intervals.

Usage (from the repository root):
  python -B -I docs/data/study-2026-11-11-v15-sites/measure.py --v15 <probe dir> --v14 <probe dir>
The directories default to replays/study/gold-v15-2026-11-11/probe and probe-v14. Output: stdout (measure.txt beside
this file).
"""
import json
import math
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-10-v14-cover"))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-09-v13-gold"))
sys.path.insert(0, HERE)
from cover import boot_diff, cov, cover_line, episodes, gate, pct, reach_line  # noqa: E402
from decompose import CLASSES, cls  # noqa: E402
from probe_tables import by_game, read, turn_class  # noqa: E402
from v13_gold import mean, med  # noqa: E402

AGENTS = {"v14": {6868933: "arena-6868933-games-2026-11-10.json", 6869279: "arena-6869279-games-2026-11-10.json"},
          "v15": {6871160: "arena-6871160-games-2026-11-11.json", 6871202: "arena-6871202-games-2026-11-11.json"}}
NAMES = ("v14", "v15")
BANDS = (("below 17", -99, 17), ("17-20", 17, 20), ("20-22", 20, 22), ("22 and up", 22, 99))
# The versions' Gold ratings from tools/arena_compare.py on the eleven Gold uploads (logistic scale 3.25), 2026-11-11.
SCALE, RATING = 3.25, {"v11": 16.98, "v12": 20.60, "v13": 21.91, "v14": 22.48, "v15": 21.31}
# Those eleven uploads. Section 8 reads the registry, which grows: an upload registered after the measurement (v15's
# third came the same evening; upload3.py has it) is left out, so that the output stays the measurement's.
GOLD_AGENTS = (6857463, 6858567, 6860036, 6860166, 6868408, 6868474, 6868868, 6868933, 6869279, 6871160, 6871202)


def meta(name):
    out = {}
    for upload, (agent, manifest) in enumerate(sorted(AGENTS[name].items()), 1):
        m = json.load(open(os.path.join("docs", "data", manifest), encoding="utf-8"))
        for g in m["games"]:
            seat = g["seat"]
            out[g["gameId"]] = {"rating": g["opponent"]["ratingAtFetch"], "opponent": g["opponent"]["pseudo"], "seat": seat, "upload": upload,
                                "won": g["ranks"][seat] < g["ranks"][1 - seat], "scores": g["scores"], "removed": min(g["scores"]) < 0}
    return out


def tower_births(events):
    """The own towers of a game in the order they were built: (number, turn, links within 400, links within 500, covered at birth)."""
    standing, out, k = {}, [], 0
    for e in sorted(events, key=lambda e: (e["t"], e["event"] != "lost")):
        if e["event"] == "lost":
            standing.pop(e["site"], None)
            continue
        if e["cause"] == "over-own" and e["site"] in standing:
            continue
        k += 1
        d = [math.hypot(px - e["x"], py - e["y"]) for (px, py) in standing.values()]
        out.append({"k": k, "t": e["t"], "l400": sum(1 for x in d if x < 400), "l500": sum(1 for x in d if x < 500), "born": e["covered"],
                    "second": sorted(d)[1] if len(d) >= 2 else None, "nearest": min(d) if d else None, "standing": len(d), "home": e["homeDist"], "fwd": e["fwd"]})
        standing[e["site"]] = (e["x"], e["y"])
    return out


def load(name, d):
    turns = read(os.path.join(d, "turns.csv"))
    g = by_game(turns)
    m = meta(name)
    ours = {gid: rows for (gid, us), rows in g.items() if us == 1}
    theirs = {gid: rows for (gid, us), rows in g.items() if us == 0}
    assert set(ours) == set(m), (name, len(ours), len(m))
    ev = {}
    for e in read(os.path.join(d, "events.csv")):
        if e["us"] == 1 and e["kind"] == "TOWER":
            ev.setdefault(e["gid"], []).append(e)
    for gid, v in m.items():
        rs = ours[gid]
        v["died"] = rs[-1]["hpAfter"] <= 0
        v["length"] = len(rs)
        v["race"] = v["length"] >= 250
        v["killed"] = theirs[gid][-1]["hpAfter"] <= 0
        v["towers"] = tower_births(ev.get(gid, []))
        con = [r for r in rs if r["t"] >= 50 and r["k250"] > 0]
        gt = [r for r in rs if r["t"] >= 50 and gate(r)]
        v["con"], v["con3"], v["con12"] = len(con), sum(1 for r in con if r["towersCov"] >= 3), sum(1 for r in con if cov(r) in (1, 2))
        v["conHp"], v["con12Hp"] = sum(r["kd"] for r in con), sum(r["kd"] for r in con if cov(r) in (1, 2))
        v["gate"], v["gate3"] = len(gt), sum(1 for r in gt if r["towersCov"] >= 3)
        v["con4"] = sum(1 for r in con if r["towersCov"] >= 4)
        v["cls"] = {c: sum(1 for r in con if cls(r) == c) for c in CLASSES}
        v["clsHp"] = {c: sum(r["kd"] for r in con if cls(r) == c) for c in CLASSES}
        v["line60"] = any(r["T"] >= 3 for r in rs if r["t"] <= 60)
        v["hp50"] = sum(r["kd"] for r in rs if r["t"] >= 50)
        v["hpFirst"] = sum(r["kd"] for r in rs if r["t"] < 50)
        v["their50"] = sum(r["kd"] for r in theirs[gid] if r["t"] >= 50)
        v["theirFirst"] = sum(r["kd"] for r in theirs[gid] if r["t"] < 50)
        v["start"] = rs[0]["hp"]
        v["n50"], v["spot50"] = sum(1 for r in rs if 50 <= r["t"] < 100), sum(1 for r in rs if 50 <= r["t"] < 100 and r["bestCov300"] >= 3)
        v["n100"], v["spot100"] = sum(1 for r in rs if 100 <= r["t"] < 150), sum(1 for r in rs if 100 <= r["t"] < 150 and r["bestCov300"] >= 3)
        v["stand"] = next((r["t"] for r in rs if r["towersCov"] >= 3), None)
        v["spot"] = next((r["t"] for r in rs if r["bestCov300"] >= 3), None)
        v["bar2"] = next((r["t"] for r in rs if r["B"] >= 2), None)
        v["at"] = {t: next((r for r in rs if r["t"] == t), None) for t in (50, 75, 100, 150)}
        v["walk"] = sum(1 for r in rs if turn_class(r) == "walk to a site")
    return {"ours": ours, "theirs": theirs, "meta": m, "waves": read(os.path.join(d, "waves.csv"))}


def stand(m, ids):
    n = len(ids)
    won = sum(1 for k in ids if m[k]["won"])
    return "%3d games, won %3d (%2.0f %%): races %d, kills %d, other %d; lost %3d: races %d, deaths %d, other %d" % (
        n, won, pct(won, n), sum(1 for k in ids if m[k]["won"] and m[k]["race"]), sum(1 for k in ids if m[k]["won"] and m[k]["killed"] and not m[k]["race"]),
        sum(1 for k in ids if m[k]["won"] and not m[k]["race"] and not m[k]["killed"]), n - won, sum(1 for k in ids if not m[k]["won"] and m[k]["race"]),
        sum(1 for k in ids if not m[k]["won"] and m[k]["died"]), sum(1 for k in ids if not m[k]["won"] and not m[k]["race"] and not m[k]["died"]))


def ratio(num, den, scale=1.0):
    return lambda g: scale * sum(num(v) for v in g) / float(max(1, sum(den(v) for v in g)))


def a_game(num, scale=1.0):
    return lambda g: scale * sum(num(v) for v in g) / float(len(g))


def tower_stat(k, key, scale=1.0):
    """Mean of key over the k-th tower of every game that built one (k = 0: every tower from the second on)."""
    pick = lambda v: [t for t in v["towers"] if (t["k"] >= 2 if k == 0 else t["k"] == k)]
    return ratio(lambda v: sum(t[key] for t in pick(v) if t[key] is not None), lambda v: sum(1 for t in pick(v) if t[key] is not None), scale)


def at(turn, key):
    return ratio(lambda v: v["at"][turn][key] if v["at"][turn] else 0, lambda v: 1 if v["at"][turn] else 0)


STATS = (
    ("own towers within 400 of a new tower at its birth, from the second on", tower_stat(0, "l400"), "%.2f"),
    ("... the second tower", tower_stat(2, "l400"), "%.2f"),
    ("... the third tower", tower_stat(3, "l400"), "%.2f"),
    ("... the fourth tower", tower_stat(4, "l400"), "%.2f"),
    ("own towers within 500 at birth, from the second on", tower_stat(0, "l500"), "%.2f"),
    ("the distance to the second-nearest own tower at birth, the third tower", tower_stat(3, "second"), "%.0f"),
    ("... the fourth tower", tower_stat(4, "second"), "%.0f"),
    ("towers from the second on built inside another own tower's range, %", ratio(lambda v: sum(t["born"] for t in v["towers"] if t["k"] >= 2), lambda v: sum(1 for t in v["towers"] if t["k"] >= 2), 100), "%.1f"),
    ("the distance from the home corner at birth, towers from the second on", tower_stat(0, "home"), "%.0f"),
    ("how far forward they stand, % of the way from the home corner to the enemy's", tower_stat(0, "fwd", 100), "%.1f"),
    ("how far forward the third tower stands, %", tower_stat(3, "fwd", 100), "%.1f"),
    ("towers built, a game", a_game(lambda v: len(v["towers"])), "%.2f"),
    ("the turn the third tower is built", tower_stat(3, "t"), "%.1f"),
    ("the turn the fourth tower is built", tower_stat(4, "t"), "%.1f"),
    ("games in which she stands under three or more by turn 60, %", a_game(lambda v: v["stand"] is not None and v["stand"] <= 60, 100), "%.1f"),
    ("... by turn 80, %", a_game(lambda v: v["stand"] is not None and v["stand"] <= 80, 100), "%.1f"),
    ("games with a spot under three or more within 300 by turn 60, %", a_game(lambda v: v["spot"] is not None and v["spot"] <= 60, 100), "%.1f"),
    ("turns 50-99 with such a spot, %", ratio(lambda v: v["spot50"], lambda v: v["n50"], 100), "%.1f"),
    ("turns 100-149 with such a spot, %", ratio(lambda v: v["spot100"], lambda v: v["n100"], 100), "%.1f"),
    ("the share of contact turns from turn 50 under three or more, %", ratio(lambda v: v["con3"], lambda v: v["con"], 100), "%.1f"),
    ("the share of the gate's turns from turn 50 under three or more, %", ratio(lambda v: v["gate3"], lambda v: v["gate"], 100), "%.1f"),
    ("contact turns from turn 50, a game", a_game(lambda v: v["con"]), "%.1f"),
    ("contact turns under one or two own towers, a game", a_game(lambda v: v["con12"]), "%.1f"),
    ("the share of contact turns from turn 50 under four or more, %", ratio(lambda v: v["con4"], lambda v: v["con"], 100), "%.1f"),
    ("games in which three own towers stand by turn 60, %", a_game(lambda v: v["line60"], 100), "%.1f"),
    ("contact turns by class (decompose.py), a game: no line (two own towers or fewer stand)", a_game(lambda v: v["cls"]["no line"]), "%.1f"),
    ("... off the line (three stand, two or fewer cover her, a spot within 300)", a_game(lambda v: v["cls"]["off the line"]), "%.1f"),
    ("... no spot (three stand, two or fewer cover her, no spot within 300)", a_game(lambda v: v["cls"]["no spot"]), "%.1f"),
    ("... under exactly three", a_game(lambda v: v["cls"]["under three"]), "%.1f"),
    ("... under four or more", a_game(lambda v: v["cls"]["under four"]), "%.1f"),
    ("HP lost to knights with no line, a game", a_game(lambda v: v["clsHp"]["no line"]), "%.1f"),
    ("HP lost off the line, a game", a_game(lambda v: v["clsHp"]["off the line"]), "%.1f"),
    ("HP lost with no spot, a game", a_game(lambda v: v["clsHp"]["no spot"]), "%.1f"),
    ("the share under three or more of the contact turns with three own towers standing, %",
     ratio(lambda v: v["con3"], lambda v: v["con"] - v["cls"]["no line"], 100), "%.1f"),
    ("HP lost to knights on contact turns from turn 50, a game", a_game(lambda v: v["conHp"]), "%.1f"),
    ("HP lost under one or two own towers, a game", a_game(lambda v: v["con12Hp"]), "%.1f"),
    ("HP lost to knights from turn 50, a game", a_game(lambda v: v["hp50"]), "%.1f"),
    ("HP lost to knights before turn 50, a game", a_game(lambda v: v["hpFirst"]), "%.1f"),
    ("the enemy queen's HP to our knights from turn 50, a game", a_game(lambda v: v["their50"]), "%.1f"),
    ("own towers at turn 50", at(50, "T"), "%.2f"),
    ("own towers at turn 75", at(75, "T"), "%.2f"),
    ("own towers at turn 100", at(100, "T"), "%.2f"),
    ("own towers at turn 150", at(150, "T"), "%.2f"),
    ("income at turn 100", at(100, "income"), "%.2f"),
    ("income at turn 150", at(150, "income"), "%.2f"),
    ("games that build the second barracks, %", a_game(lambda v: v["bar2"] is not None, 100), "%.1f"),
    ("the turn of the second barracks", ratio(lambda v: v["bar2"] or 0, lambda v: 1 if v["bar2"] is not None else 0), "%.1f"),
    ("turns on a walk to a new site, a game", a_game(lambda v: v["walk"]), "%.1f"),
    ("queen deaths, % of the games", a_game(lambda v: v["died"], 100), "%.1f"),
    ("games won, %", a_game(lambda v: v["won"], 100), "%.1f"),
)


def table(title, groups):
    """groups: {name: list of per-game records}; prints v14, v15 and the difference with its interval."""
    print(title)
    for label, st, f in STATS:
        a, b = st(groups["v14"]), st(groups["v15"])
        lo, hi = boot_diff(groups["v14"], groups["v15"], st)
        print(("     %-72s v14 " + f + "   v15 " + f + "   difference %+.2f (%+.2f..%+.2f)") % (label, a, b, b - a, lo, hi))


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    data = {"v14": load("v14", arg("--v14", os.path.join("replays", "study", "gold-v15-2026-11-11", "probe-v14"))),
            "v15": load("v15", arg("--v15", os.path.join("replays", "study", "gold-v15-2026-11-11", "probe")))}

    print("1. Where the versions stand (the game result's ranks; a race is a game that reached turn 250; a death: our queen's HP 0 on the last turn)")
    for n in NAMES:
        m = data[n]["meta"]
        print("   %s  %s; opponents' median rating %.2f" % (n, stand(m, list(m)), med([v["rating"] for v in m.values()])))
        for label, lo, hi in BANDS:
            ids = [k for k, v in m.items() if lo <= v["rating"] < hi]
            if ids:
                print("        %-9s %s" % (label, stand(m, ids)))
        for u in (1, 2):
            ids = [k for k, v in m.items() if v["upload"] == u]
            print("        upload %d  %s; median %.2f" % (u, stand(m, ids), med([m[k]["rating"] for k in ids])))
        print("        seat 0: %s" % stand(m, [k for k, v in m.items() if v["seat"] == 0]))
        print("        seat 1: %s" % stand(m, [k for k, v in m.items() if v["seat"] == 1]))

    print("\n2. The rule's measures, all games (v15 less v14; the 95 % interval of a bootstrap over games within each version)")
    table("   all 180 games of each version", {n: list(data[n]["meta"].values()) for n in NAMES})
    for u in (1, 2):
        print("   by upload %d: third tower's links within 400 %s; contact turns under three or more %s; HP to knights from turn 50 %s; own towers at turn 100 %s" % (
            u, " / ".join("%s %.2f" % (n, tower_stat(3, "l400")([v for v in data[n]["meta"].values() if v["upload"] == u])) for n in NAMES),
            " / ".join("%s %.0f %%" % (n, ratio(lambda v: v["con3"], lambda v: v["con"], 100)([v for v in data[n]["meta"].values() if v["upload"] == u])) for n in NAMES),
            " / ".join("%s %.1f" % (n, a_game(lambda v: v["hp50"])([v for v in data[n]["meta"].values() if v["upload"] == u])) for n in NAMES),
            " / ".join("%s %.2f" % (n, at(100, "T")([v for v in data[n]["meta"].values() if v["upload"] == u])) for n in NAMES)))

    print("\n3. Our queen's contact turns from turn 50 by the cover")
    for n in NAMES:
        d = data[n]
        ng = float(len(d["meta"]))
        con = [r for rs in d["ours"].values() for r in rs if r["t"] >= 50 and r["k250"] > 0]
        print("   %s  contact turns %s" % (n, cover_line(con, ng)))
        print("        %s" % reach_line(con))
        gt = [r for rs in d["ours"].values() for r in rs if r["t"] >= 50 and gate(r)]
        cls = Counter(turn_class(r) for r in gt)
        print("        the gate's state: %.1f turns a game; %s" % (len(gt) / ng, reach_line(gt)))
        print("        her command there: %s" % ", ".join("%s %.1f a game (%.0f %%)" % (k, c / ng, pct(c, len(gt))) for k, c in cls.most_common(6)))
        ep = [e for rs in d["ours"].values() for e in episodes(rs)]
        print("        contact episodes: %.1f a game, %.1f HP a game; the first turn under three or more in %2.0f %%; by the cover on the first turn: %s" % (
            len(ep) / ng, sum(e[2] for e in ep) / ng, pct(sum(1 for e in ep if e[0]["towersCov"] >= 3), len(ep)), "; ".join(
                "%d%s: %.1f a game, %.2f HP each" % (c, "+" if c == 4 else "", len(y) / ng, mean([e[2] for e in y]))
                for c in range(5) for y in [[e for e in ep if cov(e[0]) == c]] if y)))

    print("\n4. HP lost to knights a game by 50 turns (0-49 | 50-99 | 100-149 | 150-199 | 200-249), ours / theirs; the economy and the army")
    for n in NAMES:
        d = data[n]
        ng = float(len(d["meta"]))
        o = [r for rs in d["ours"].values() for r in rs]
        t = [r for rs in d["theirs"].values() for r in rs]
        print("   %s  ours %s (from 50: %.1f); theirs %s (from 50: %.1f); to towers: ours %.1f a game" % (
            n, " | ".join("%.1f" % (sum(r["kd"] for r in o if a <= r["t"] < a + 50) / ng) for a in (0, 50, 100, 150, 200)), sum(r["kd"] for r in o if r["t"] >= 50) / ng,
            " | ".join("%.1f" % (sum(r["kd"] for r in t if a <= r["t"] < a + 50) / ng) for a in (0, 50, 100, 150, 200)), sum(r["kd"] for r in t if r["t"] >= 50) / ng,
            sum(r["td"] for r in o) / ng))
        ta = lambda turn, key: mean([float(r[key]) for rs in d["theirs"].values() for r in rs if r["t"] == turn])
        print("        theirs: towers at turn 100 %.2f, at 150 %.2f; income at 50 %.2f, at 100 %.2f, at 150 %.2f" % (ta(100, "T"), ta(150, "T"), ta(50, "income"), ta(100, "income"), ta(150, "income")))
        kn = lambda us, lo: 4 * sum(w["barracks"] for w in d["waves"] if w["type"] == "K" and w["us"] == us and w["t"] >= lo) / ng
        print("        knights trained from turn 50: ours %.1f, theirs %.1f a game; games with enemy giants: %d" % (
            kn(1, 50), kn(0, 50), sum(1 for rs in d["ours"].values() if max(r["eGiants"] for r in rs) > 0)))

    print("\n5. Like for like: the same opponent bands (ratings at the fetch)")
    for label, lo, hi in BANDS:
        groups = {n: [v for v in data[n]["meta"].values() if lo <= v["rating"] < hi] for n in NAMES}
        print("   %-9s v14 %s" % (label, stand(data["v14"]["meta"], [k for k, v in data["v14"]["meta"].items() if lo <= v["rating"] < hi])))
        print("             v15 %s" % stand(data["v15"]["meta"], [k for k, v in data["v15"]["meta"].items() if lo <= v["rating"] < hi]))
        for key in ("... the third tower", "the share of contact turns from turn 50 under three or more, %", "contact turns under one or two own towers, a game",
                    "HP lost to knights from turn 50, a game", "the enemy queen's HP to our knights from turn 50, a game", "own towers at turn 100", "income at turn 100"):
            st, f = next((s, f) for l, s, f in STATS if l == key)
            a, b = st(groups["v14"]), st(groups["v15"])
            lo2, hi2 = boot_diff(groups["v14"], groups["v15"], st)
            print(("             %-66s v14 " + f + "   v15 " + f + "   difference %+.2f (%+.2f..%+.2f)") % (
                "the third tower's links within 400" if key.startswith("...") else key, a, b, b - a, lo2, hi2))

    shared = set(v["opponent"] for v in data["v14"]["meta"].values()) & set(v["opponent"] for v in data["v15"]["meta"].values())
    print("\n5b. The games against the %d opponents both versions met (by the opponent's name; pooled, not weighted by the opponent)" % len(shared))
    groups = {n: [v for v in data[n]["meta"].values() if v["opponent"] in shared] for n in NAMES}
    for n in NAMES:
        m = data[n]["meta"]
        ids = [k for k, v in m.items() if v["opponent"] in shared]
        print("   %s  %s; opponents' median rating %.2f" % (n, stand(m, ids), med([m[k]["rating"] for k in ids])))
    for key in ("... the third tower", "games in which she stands under three or more by turn 60, %", "the share of contact turns from turn 50 under three or more, %",
                "contact turns under one or two own towers, a game", "HP lost to knights from turn 50, a game", "HP lost to knights before turn 50, a game",
                "the enemy queen's HP to our knights from turn 50, a game", "own towers at turn 100", "income at turn 100", "queen deaths, % of the games", "games won, %"):
        st, f = next((s, f) for l, s, f in STATS if l == key)
        a, b = st(groups["v14"]), st(groups["v15"])
        lo2, hi2 = boot_diff(groups["v14"], groups["v15"], st)
        print(("   %-66s v14 " + f + "   v15 " + f + "   difference %+.2f (%+.2f..%+.2f)") % (
            "the third tower's links within 400" if key.startswith("...") else key, a, b, b - a, lo2, hi2))

    print("\n6. Won and lost games: the contact turns from turn 50 and the towers")
    for n in NAMES:
        d = data[n]
        m = d["meta"]
        for label, ok in (("won", lambda v: v["won"]), ("lost", lambda v: not v["won"]), ("lost races", lambda v: not v["won"] and v["race"]), ("deaths", lambda v: v["died"])):
            ids = [k for k, v in m.items() if ok(v)]
            ng = float(len(ids))
            con = [r for k in ids for r in d["ours"][k] if r["t"] >= 50 and r["k250"] > 0]
            print("   %s  %-10s (%3d): contact turns %4.1f a game, %4.1f HP; under one or two towers %4.1f turns, %4.1f HP; covering %.2f, 3 or more on %2.0f %%; own towers there %.1f; the third tower's links %.2f" % (
                n, label, len(ids), len(con) / ng, sum(r["kd"] for r in con) / ng, sum(1 for r in con if cov(r) in (1, 2)) / ng, sum(r["kd"] for r in con if cov(r) in (1, 2)) / ng,
                mean([r["towersCov"] for r in con]), pct(sum(1 for r in con if r["towersCov"] >= 3), len(con)), mean([r["T"] for r in con]), tower_stat(3, "l400")([m[k] for k in ids])))
        races = sorted((d["ours"][k][-1]["hpAfter"] - d["theirs"][k][-1]["hpAfter"] for k, v in m.items() if not v["won"] and v["race"]), reverse=True)
        print("        margins of the lost races: %s" % races)
        for label, ok in (("won races", lambda v: v["won"] and v["race"]), ("lost races", lambda v: not v["won"] and v["race"])):
            x = [v for v in m.values() if ok(v)]
            ahead = sum(1 for v in x if v["hpFirst"] < v["theirFirst"])
            level = sum(1 for v in x if v["hpFirst"] == v["theirFirst"])
            print("        %-10s (%3d): HP to knights before turn 50 ours %4.1f, theirs %4.1f; from turn 50 ours %4.1f, theirs %4.1f; start HP %4.1f; ahead at turn 50 in %d, level in %d, behind in %d" % (
                label, len(x), mean([v["hpFirst"] for v in x]), mean([v["theirFirst"] for v in x]), mean([v["hp50"] for v in x]), mean([v["their50"] for v in x]),
                mean([v["start"] for v in x]), ahead, level, len(x) - ahead - level))
        x = [v for v in m.values() if not v["won"] and v["race"]]
        print("        the lost races by the knights' HP count at turn 50: behind then and never caught up %d; level or ahead then, lost later %d" % (
            sum(1 for v in x if v["hpFirst"] > v["theirFirst"]), sum(1 for v in x if v["hpFirst"] <= v["theirFirst"])))

    print("\n7. v15's losses to opponents rated below 17 (the HP at the end ours:theirs; towers, income and knights alive at turn 100, ours vs theirs; giants: the most on the board)")
    d = data["v15"]
    m = d["meta"]
    for k in sorted((k for k, v in m.items() if v["rating"] < 17 and not v["won"]), key=lambda k: (m[k]["upload"], k)):
        v, o, t = m[k], d["ours"][k], d["theirs"][k]
        val = lambda rows, turn, key: next((r[key] for r in rows if r["t"] == turn), None)
        con = [r for r in o if r["t"] >= 50 and r["k250"] > 0]
        print("   upload %d %d seat %d vs %-15s %5.2f: start HP %3d, %s at turn %3d, HP %3d:%-3d; turn 100: towers %s vs %s, income %s vs %s, knights alive %s vs %s; giants %d; contact turns %3d (HP %3d), under one or two %3d (HP %3d); to enemy towers %d HP" % (
            v["upload"], k, v["seat"], v["opponent"], v["rating"], o[0]["hp"], "death" if v["died"] else "race " if v["race"] else "other", o[-1]["t"], max(0, o[-1]["hpAfter"]),
            max(0, t[-1]["hpAfter"]), val(o, 100, "T"), val(t, 100, "T"), val(o, 100, "income"), val(t, 100, "income"), val(o, 100, "oKnights"), val(o, 100, "eKnights"),
            max(r["eGiants"] for r in o), len(con), sum(r["kd"] for r in con), sum(1 for r in con if cov(r) in (1, 2)), sum(r["kd"] for r in con if cov(r) in (1, 2)), sum(r["td"] for r in o)))

    print("\n8. Every Gold version by the opponent's band against the rating model: the logistic curve of tools/arena_compare.py at scale %.2f and the version's" % SCALE)
    print("   pooled rating (%s); the games of the registry's Gold uploads; SD of the expected count" % ", ".join("%s %.2f" % (k, RATING[k]) for k in sorted(RATING)))
    reg = json.load(open(os.path.join("docs", "data", "arena-uploads.json"), encoding="utf-8"))
    tot = {}
    for u in reg["uploads"]:
        if u["league"] != "gold" or u["agent"] not in GOLD_AGENTS:
            continue
        for g in json.load(open(u["games"], encoding="utf-8"))["games"]:
            r = g["opponent"]["ratingAtFetch"]
            band = next(b for b, lo, hi in BANDS if lo <= r < hi)
            p = 1.0 / (1.0 + math.exp(-(RATING[u["label"]] - r) / SCALE))
            for key in ((u["label"], band), (u["label"], "all"), (u["label"] + "/" + str(u["upload"]), band), (u["label"] + "/" + str(u["upload"]), "all")):
                t = tot.setdefault(key, [0, 0, 0.0, 0.0])
                t[0] += 1
                t[1] += g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
                t[2] += p
                t[3] += p * (1 - p)
    cell = lambda v, band: "%s %3d games, won %3d (%2.0f %%), expected %5.1f, %+4.1f SD" % (
        v, tot[(v, band)][0], tot[(v, band)][1], pct(tot[(v, band)][1], tot[(v, band)][0]), tot[(v, band)][2], (tot[(v, band)][1] - tot[(v, band)][2]) / math.sqrt(tot[(v, band)][3]))
    for band, _, _ in BANDS:
        print("   %-9s %s" % (band, "; ".join(cell(v, band) for v in sorted(RATING) if (v, band) in tot)))
    print("   v15 by upload: %s" % "; ".join("%s: %s" % (band, cell(v, band)) for v in ("v15/1", "v15/2") for band, _, _ in BANDS if (v, band) in tot))
    # v15's games at v14's rating: what v14's rating expects of the field v15 met.
    for label in ("v15",):
        exp = var = won = n = 0.0
        for u in reg["uploads"]:
            if u["league"] == "gold" and u["label"] == label and u["agent"] in GOLD_AGENTS:
                for g in json.load(open(u["games"], encoding="utf-8"))["games"]:
                    p = 1.0 / (1.0 + math.exp(-(RATING["v14"] - g["opponent"]["ratingAtFetch"]) / SCALE))
                    exp += p
                    var += p * (1 - p)
                    won += g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
                    n += 1
        print("   the field v15 met, at v14's rating %.2f: %.1f wins expected of %d, v15 won %d (%+.1f SD of the expected count)" % (RATING["v14"], exp, n, won, (won - exp) / math.sqrt(var)))


if __name__ == "__main__":
    main()
