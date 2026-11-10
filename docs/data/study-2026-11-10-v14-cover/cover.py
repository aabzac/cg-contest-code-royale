"""v14's Gold games beside v13's (2026-11-10): what the overlap rule (HOLD_OVERLAP, IDLE_OVERLAP) did on the arena.
From ArenaProbe's csv files (docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java), with that study's definitions:
  a contact turn      an enemy knight within 250 of our queen, turns from 50;
  the gate's state    turn 30 on, two own towers or more, an enemy knight within 500 and none within 250;
  the cover           the own towers whose range covers the queen (towersCov), capped at 4 in the tables;
  an episode          a run of contact turns; it is "begun on a walk" when the turn before it was a BUILD on a site she did
                      not touch (to a new site or mine, or to an own tower), "at an own tower" when her nearest own tower
                      was within 130, "on open ground" otherwise.
Uncertainty: a bootstrap over games (2000 resamples, seed 1), 95 % intervals.

Usage (from the repository root):
  python -B -I docs/data/study-2026-11-10-v14-cover/cover.py --v14 <probe dir> --v13 <probe dir>
Output: stdout (cover.txt beside this file).
"""
import json
import math
import os
import random
import sys
from collections import Counter

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "study-2026-11-09-v13-gold"))
from probe_tables import by_game, read, turn_class  # noqa: E402
from v13_gold import mean, med  # noqa: E402

AGENTS = {"v13": {6868408: "arena-6868408-games-2026-11-09.json", 6868474: "arena-6868474-games-2026-11-09.json"},
          "v14": {6868933: "arena-6868933-games-2026-11-10.json", 6869279: "arena-6869279-games-2026-11-10.json"}}
BANDS = (("below 17", -99, 17), ("17-20", 17, 20), ("20-22", 20, 22), ("22 and up", 22, 99))
# The versions' Gold ratings from tools/arena_compare.py on the nine Gold uploads (logistic scale 3.00), 2026-11-10.
SCALE, RATING = 3.00, {"v11": 16.88, "v12": 20.50, "v13": 21.84, "v14": 22.34}


def meta(name):
    out = {}
    for upload, (agent, manifest) in enumerate(sorted(AGENTS[name].items()), 1):
        m = json.load(open(os.path.join("docs", "data", manifest), encoding="utf-8"))
        for g in m["games"]:
            seat = g["seat"]
            out[g["gameId"]] = {"rating": g["opponent"]["ratingAtFetch"], "opponent": g["opponent"]["pseudo"], "seat": seat, "upload": upload,
                                "won": g["ranks"][seat] < g["ranks"][1 - seat], "scores": g["scores"], "removed": min(g["scores"]) < 0}
    return out


def gate(r):
    return r["t"] >= 30 and r["T"] >= 2 and r["k500"] > 0 and r["k250"] == 0


def cov(r):
    return min(r["towersCov"], 4)


def top(r):
    return r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T"


def episodes(rs):
    """(first row, the row before it or None, HP lost to knights, length) of every contact episode from turn 50."""
    out, i = [], 0
    while i < len(rs):
        if rs[i]["t"] >= 50 and rs[i]["k250"] > 0 and (i == 0 or rs[i - 1]["k250"] == 0):
            j, kd = i, 0
            while j < len(rs) and rs[j]["k250"] > 0:
                kd += rs[j]["kd"]
                j += 1
            out.append((rs[i], rs[i - 1] if i else None, kd, j - i))
            i = j
        else:
            i += 1
    return out


def began(before):
    if before is None:
        return "on open ground"
    if before["cmd"] == "B" and before["touch"] != before["site"]:
        return "on a walk to an own tower" if before["targetKind"] == "T" else "on a walk to a new site or mine"
    return "at an own tower" if 0 <= before["nearT"] <= 130 else "on open ground"


def open_kind(before):
    """What the queen did on the turn before an episode begun on open ground (added 2026-11-10 with the open-ground experiment)."""
    c = "none" if before is None else turn_class(before)
    if c == "MOVE, knight within 500":
        return "a MOVE with a knight within 500 (the hold's spot off the towers)"
    if c == "mine upgrade":
        return "the upgrade of an own mine she touches"
    if c.startswith("new "):
        return "a new structure on the free site she touches"
    return "other"


def boot_diff(a, b, stat, n=2000):
    """95 % interval of stat(b) - stat(a) over games resampled within each version."""
    rnd = random.Random(1)
    xs = sorted(stat([b[rnd.randrange(len(b))] for _ in range(len(b))]) - stat([a[rnd.randrange(len(a))] for _ in range(len(a))]) for _ in range(n))
    return xs[int(0.025 * n)], xs[int(0.975 * n)]


def pct(x, n):
    return 100.0 * x / n if n else float("nan")


def load(name, d):
    turns = read(os.path.join(d, "turns.csv"))
    g = by_game(turns)
    m = meta(name)
    ours = {gid: rows for (gid, us), rows in g.items() if us == 1}
    theirs = {gid: rows for (gid, us), rows in g.items() if us == 0}
    assert set(ours) == set(m), (name, len(ours), len(m))
    for gid, v in m.items():
        v["died"] = ours[gid][-1]["hpAfter"] <= 0
        v["length"] = len(ours[gid])
        v["race"] = v["length"] >= 250
        v["killed"] = theirs[gid][-1]["hpAfter"] <= 0
    return {"turns": turns, "ours": ours, "theirs": theirs, "meta": m, "waves": read(os.path.join(d, "waves.csv")), "events": read(os.path.join(d, "events.csv"))}


def stand(d, ids):
    m = d["meta"]
    n = len(ids)
    won = sum(1 for k in ids if m[k]["won"])
    return "%3d games, won %3d (%2.0f %%): races %d, kills %d, other %d; lost %3d: races %d, deaths %d, other %d" % (
        n, won, pct(won, n), sum(1 for k in ids if m[k]["won"] and m[k]["race"]), sum(1 for k in ids if m[k]["won"] and m[k]["killed"] and not m[k]["race"]),
        sum(1 for k in ids if m[k]["won"] and not m[k]["race"] and not m[k]["killed"]), n - won, sum(1 for k in ids if not m[k]["won"] and m[k]["race"]),
        sum(1 for k in ids if not m[k]["won"] and m[k]["died"]), sum(1 for k in ids if not m[k]["won"] and not m[k]["race"] and not m[k]["died"]))


def cover_line(rows, n):
    return "%5.1f a game; by the cover 0 | 1 | 2 | 3 | 4+: HP a turn %s; turns a game %s; HP a game %s" % (
        len(rows) / n, " | ".join("%.2f" % mean([r["kd"] for r in rows if cov(r) == c]) for c in range(5)),
        " | ".join("%4.1f" % (sum(1 for r in rows if cov(r) == c) / n) for c in range(5)),
        " | ".join("%4.1f" % (sum(r["kd"] for r in rows if cov(r) == c) / n) for c in range(5)))


def reach_line(rows):
    return "covering now %.2f (3 or more on %2.0f %% of the turns, 4 or more on %2.0f %%); best within 180 %.2f (higher on %2.0f %%), within 300 %.2f (%2.0f %%); own towers %.1f" % (
        mean([r["towersCov"] for r in rows]), pct(sum(1 for r in rows if r["towersCov"] >= 3), len(rows)), pct(sum(1 for r in rows if r["towersCov"] >= 4), len(rows)),
        mean([r["bestCov180"] for r in rows]), pct(sum(1 for r in rows if r["bestCov180"] > r["towersCov"]), len(rows)),
        mean([r["bestCov300"] for r in rows]), pct(sum(1 for r in rows if r["bestCov300"] > r["towersCov"]), len(rows)), mean([r["T"] for r in rows]))


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    data = {"v13": load("v13", arg("--v13", os.path.join("replays", "study", "gold-v13-2026-11-09", "probe"))),
            "v14": load("v14", arg("--v14", os.path.join("replays", "study", "gold-v14-2026-11-10", "probe")))}
    names = ("v13", "v14")

    print("1. Where the versions stand (the game result's ranks; a race is a game that reached turn 250; a death: our queen's HP 0 on the last turn)")
    for n in names:
        d = data[n]
        m = d["meta"]
        print("   %s  %s; opponents' median rating %.2f" % (n, stand(d, list(m)), med([v["rating"] for v in m.values()])))
        for label, lo, hi in BANDS:
            ids = [k for k, v in m.items() if lo <= v["rating"] < hi]
            if ids:
                print("        %-9s %s" % (label, stand(d, ids)))
        if n == "v14":
            for u in (1, 2):
                ids = [k for k, v in m.items() if v["upload"] == u]
                print("        upload %d  %s; median %.2f" % (u, stand(d, ids), med([m[k]["rating"] for k in ids])))
        print("        seat 0: %s" % stand(d, [k for k, v in m.items() if v["seat"] == 0]))
        print("        seat 1: %s" % stand(d, [k for k, v in m.items() if v["seat"] == 1]))

    print("\n2. Our queen's contact turns from turn 50 by the cover")
    for n in names:
        d = data[n]
        ng = float(len(d["meta"]))
        con = [r for rs in d["ours"].values() for r in rs if r["t"] >= 50 and r["k250"] > 0]
        print("   %s  contact turns %s" % (n, cover_line(con, ng)))
        print("        %s" % reach_line(con))
        print("        HP lost to knights on contact turns %.1f a game; under one or two towers %.1f turns and %.1f HP a game; under three or more %.1f turns and %.1f HP" % (
            sum(r["kd"] for r in con) / ng, sum(1 for r in con if cov(r) in (1, 2)) / ng, sum(r["kd"] for r in con if cov(r) in (1, 2)) / ng,
            sum(1 for r in con if cov(r) >= 3) / ng, sum(r["kd"] for r in con if cov(r) >= 3) / ng))
        for u in (1, 2):
            ids = [k for k, v in d["meta"].items() if v["upload"] == u]
            cu = [r for k in ids for r in d["ours"][k] if r["t"] >= 50 and r["k250"] > 0]
            print("        upload %d (%d games, opponents' median %.2f): contact turns %.1f a game, covering %.2f, 3 or more on %2.0f %%; HP on contact turns %.1f a game; under one or two towers %.1f turns, %.1f HP" % (
                u, len(ids), med([d["meta"][k]["rating"] for k in ids]), len(cu) / float(len(ids)), mean([r["towersCov"] for r in cu]),
                pct(sum(1 for r in cu if r["towersCov"] >= 3), len(cu)), sum(r["kd"] for r in cu) / float(len(ids)),
                sum(1 for r in cu if cov(r) in (1, 2)) / float(len(ids)), sum(r["kd"] for r in cu if cov(r) in (1, 2)) / float(len(ids))))
    print("   the opponents' queens, for scale:")
    for n in names:
        d = data[n]
        ng = float(len(d["meta"]))
        con = [r for rs in d["theirs"].values() for r in rs if r["t"] >= 50 and r["k250"] > 0]
        print("   %s  theirs: contact turns %s" % (n, cover_line(con, ng)))
        print("        %s" % reach_line(con))
    print("   uncertainty (v14 less v13; games resampled within each version):")
    per = {}
    for n in names:
        per[n] = []
        for rs in data[n]["ours"].values():
            con = [r for r in rs if r["t"] >= 50 and r["k250"] > 0]
            per[n].append((len(con), sum(1 for r in con if r["towersCov"] >= 3), sum(r["kd"] for r in con), sum(1 for r in con if cov(r) in (1, 2)),
                           sum(r["kd"] for r in con if cov(r) in (1, 2))))
    stats = (("the share of contact turns under three or more towers, %", lambda g: 100.0 * sum(x[1] for x in g) / max(1, sum(x[0] for x in g))),
             ("HP lost to knights on contact turns from turn 50, a game", lambda g: sum(x[2] for x in g) / float(len(g))),
             ("contact turns under one or two towers, a game", lambda g: sum(x[3] for x in g) / float(len(g))),
             ("HP lost under one or two towers, a game", lambda g: sum(x[4] for x in g) / float(len(g))))
    for label, st in stats:
        a, b = st(per["v13"]), st(per["v14"])
        lo, hi = boot_diff(per["v13"], per["v14"], st)
        print("        %-58s v13 %5.1f, v14 %5.1f, difference %+5.1f (%+.1f..%+.1f)" % (label, a, b, b - a, lo, hi))

    print("\n3. The gate's state from turn 50 (a knight within 500, none within 250, two own towers or more): where the queen is and what she does")
    for n in names:
        d = data[n]
        ng = float(len(d["meta"]))
        gt = [r for rs in d["ours"].values() for r in rs if r["t"] >= 50 and gate(r)]
        cls = Counter(turn_class(r) for r in gt)
        print("   %s  %.1f turns a game; %s" % (n, len(gt) / ng, reach_line(gt)))
        print("        her command: %s" % ", ".join("%s %.1f a game (%.0f %%)" % (k, c / ng, pct(c, len(gt))) for k, c in cls.most_common(7)))
        nxt = Counter()
        for rs in d["ours"].values():
            for a, b in zip(rs, rs[1:]):
                if a["t"] >= 50 and gate(a):
                    nxt["a contact" if b["k250"] > 0 else "still within 500" if b["k500"] > 0 else "clear"] += 1
        print("        the next turn: %s" % ", ".join("%s %.0f %%" % (k, pct(c, sum(nxt.values()))) for k, c in nxt.most_common()))

    print("\n4. What the queen does on contact turns from turn 50, and the top-ups")
    for n in names:
        d = data[n]
        ng = float(len(d["meta"]))
        con = [r for rs in d["ours"].values() for r in rs if r["t"] >= 50 and r["k250"] > 0]
        cls = Counter(turn_class(r) for r in con)
        print("   %s  contact turns: %s" % (n, ", ".join("%s %.1f a game (%.0f %%)" % (k, c / ng, pct(c, len(con))) for k, c in cls.most_common(6))))
        allr = [r for rs in d["ours"].values() for r in rs]
        tops = [r for r in allr if top(r)]
        idle = [r for r in allr if r["t"] >= 50 and r["k500"] == 0]
        print("        top-ups %.1f a game, of a tower at 700 or more %.1f, adding 10 HP or less %.1f; MOVE turns %.1f a game (from turn 50: %.1f)" % (
            len(tops) / ng, sum(1 for r in tops if r["touchHp"] >= 700) / ng, sum(1 for r in tops if 800 - r["touchHp"] <= 10) / ng,
            sum(1 for r in allr if r["cmd"] == "M") / ng, sum(1 for r in allr if r["cmd"] == "M" and r["t"] >= 50) / ng))
        print("        turns from 50 with no knight within 500: %.1f a game; covering her %.2f (3 or more on %2.0f %%, 4 or more on %2.0f %%); best within 300 %.2f; own towers %.1f" % (
            len(idle) / ng, mean([r["towersCov"] for r in idle]), pct(sum(1 for r in idle if r["towersCov"] >= 3), len(idle)),
            pct(sum(1 for r in idle if r["towersCov"] >= 4), len(idle)), mean([r["bestCov300"] for r in idle]), mean([r["T"] for r in idle])))

    print("\n5. Contact episodes from turn 50 by the cover on their first turn, and by where they began")
    for n in names:
        d = data[n]
        ng = float(len(d["meta"]))
        ep = [e for rs in d["ours"].values() for e in episodes(rs)]
        print("   %s  %d episodes, %.1f a game, %.1f HP a game" % (n, len(ep), len(ep) / ng, sum(e[2] for e in ep) / ng))
        print("        by the cover on the first turn: %s" % "; ".join(
            "%d%s: %.1f a game, %.2f HP each (%.1f HP a game), %.1f turns" % (c, "+" if c == 4 else "", len(y) / ng, mean([e[2] for e in y]), sum(e[2] for e in y) / ng, mean([float(e[3]) for e in y]))
            for c in range(5) for y in [[e for e in ep if cov(e[0]) == c]] if y))
        print("        the first turn: covering %.2f, 3 or more in %2.0f %% of the episodes; a better spot within 180 in %2.0f %%" % (
            mean([e[0]["towersCov"] for e in ep]), pct(sum(1 for e in ep if e[0]["towersCov"] >= 3), len(ep)), pct(sum(1 for e in ep if e[0]["bestCov180"] > e[0]["towersCov"]), len(ep))))
        acc = {}
        for e in ep:
            a = acc.setdefault(began(e[1]), [0, 0, 0])
            a[0] += 1
            a[1] += e[2]
            a[2] += e[0]["towersCov"]
        print("        begun %s" % "; ".join("%s: %.1f a game, %.2f HP each (%.1f HP a game), covering %.2f on the first turn" % (
            c, a[0] / ng, a[1] / float(a[0]), a[1] / ng, a[2] / float(a[0])) for c, a in sorted(acc.items(), key=lambda kv: -kv[1][1])))
        kinds = {}
        for e in ep:
            if began(e[1]) == "on open ground":
                kinds.setdefault(open_kind(e[1]), []).append(e)
        for k, y in sorted(kinds.items(), key=lambda kv: -len(kv[1])):
            print("        on open ground, the turn before was %s: %d episodes, %.2f a game, %.2f HP each (%.2f HP a game), covering %.2f on the first turn, the knights' HP sum %.1f" % (
                k, len(y), len(y) / ng, mean([e[2] for e in y]), sum(e[2] for e in y) / ng, mean([e[0]["towersCov"] for e in y]), mean([e[0]["kHpSum250"] for e in y])))

    print("\n6. HP lost to knights a game by 50 turns (0-49 | 50-99 | 100-149 | 150-199 | 200-249), ours / theirs; the ladder")
    for n in names:
        d = data[n]
        ng = float(len(d["meta"]))
        o = [r for rs in d["ours"].values() for r in rs]
        t = [r for rs in d["theirs"].values() for r in rs]
        print("   %s  ours %s (from 50: %.1f); theirs %s (from 50: %.1f); to towers: ours %.1f a game" % (
            n, " | ".join("%.1f" % (sum(r["kd"] for r in o if a <= r["t"] < a + 50) / ng) for a in (0, 50, 100, 150, 200)), sum(r["kd"] for r in o if r["t"] >= 50) / ng,
            " | ".join("%.1f" % (sum(r["kd"] for r in t if a <= r["t"] < a + 50) / ng) for a in (0, 50, 100, 150, 200)), sum(r["kd"] for r in t if r["t"] >= 50) / ng,
            sum(r["td"] for r in o) / ng))
        at = lambda turn, key: mean([float(r[key]) for rs in d["ours"].values() for r in rs if r["t"] == turn])
        print("        own towers at turn 100 %.2f, at 150 %.2f; barracks at 100 %.2f; income at 50 %.2f, at 100 %.2f, at 150 %.2f; games with two towers or fewer at turn 100: %d" % (
            at(100, "T"), at(150, "T"), at(100, "B"), at(50, "income"), at(100, "income"), at(150, "income"),
            sum(1 for rs in d["ours"].values() for r in rs if r["t"] == 100 and r["T"] <= 2)))
        ta = lambda turn, key: mean([float(r[key]) for rs in d["theirs"].values() for r in rs if r["t"] == turn])
        print("        theirs: towers at turn 100 %.2f, at 150 %.2f; income at 50 %.2f, at 100 %.2f, at 150 %.2f" % (ta(100, "T"), ta(150, "T"), ta(50, "income"), ta(100, "income"), ta(150, "income")))
        kn = lambda us, lo: 4 * sum(w["barracks"] for w in d["waves"] if w["type"] == "K" and w["us"] == us and w["t"] >= lo) / ng
        print("        knights trained from turn 50: ours %.1f, theirs %.1f a game" % (kn(1, 50), kn(0, 50)))

    print("\n7. Like for like: the same opponent bands (ratings at the fetch)")
    for label, lo, hi in (("below 17", -99, 17), ("17-20", 17, 20), ("20-22", 20, 22), ("22 and up", 22, 99)):
        for n in names:
            d = data[n]
            m = d["meta"]
            ids = [k for k, v in m.items() if lo <= v["rating"] < hi]
            ng = float(len(ids))
            if not ids:
                continue
            con = [r for k in ids for r in d["ours"][k] if r["t"] >= 50 and r["k250"] > 0]
            o = [r for k in ids for r in d["ours"][k] if r["t"] >= 50]
            t = [r for k in ids for r in d["theirs"][k] if r["t"] >= 50]
            print("   %-9s %s  %s" % (label, n, stand(d, ids)))
            print("                  contact turns %.1f a game, covering %.2f, 3 or more on %2.0f %%; HP a contact turn %.2f; under one or two towers %.1f turns, %.1f HP a game" % (
                len(con) / ng, mean([r["towersCov"] for r in con]), pct(sum(1 for r in con if r["towersCov"] >= 3), len(con)), mean([r["kd"] for r in con]),
                sum(1 for r in con if cov(r) in (1, 2)) / ng, sum(r["kd"] for r in con if cov(r) in (1, 2)) / ng))
            print("                  HP lost to knights from turn 50: ours %.1f, theirs %.1f a game; enemy knights within 250 on a contact turn %.2f" % (
                sum(r["kd"] for r in o) / ng, sum(r["kd"] for r in t) / ng, mean([r["k250"] for r in con])))

    print("\n8. Won and lost games: the contact turns from turn 50 under one or two towers")
    for n in names:
        d = data[n]
        m = d["meta"]
        for label, ok in (("won", lambda v: v["won"]), ("lost", lambda v: not v["won"]), ("lost races", lambda v: not v["won"] and v["race"]), ("deaths", lambda v: v["died"])):
            ids = [k for k, v in m.items() if ok(v)]
            ng = float(len(ids))
            con = [r for k in ids for r in d["ours"][k] if r["t"] >= 50 and r["k250"] > 0]
            print("   %s  %-10s (%3d): contact turns %4.1f a game, %4.1f HP; under one or two towers %4.1f turns, %4.1f HP; covering %.2f, 3 or more on %2.0f %%; own towers there %.1f" % (
                n, label, len(ids), len(con) / ng, sum(r["kd"] for r in con) / ng, sum(1 for r in con if cov(r) in (1, 2)) / ng, sum(r["kd"] for r in con if cov(r) in (1, 2)) / ng,
                mean([r["towersCov"] for r in con]), pct(sum(1 for r in con if r["towersCov"] >= 3), len(con)), mean([r["T"] for r in con])))
        races = sorted((d["ours"][k][-1]["hpAfter"] - d["theirs"][k][-1]["hpAfter"] for k, v in m.items() if not v["won"] and v["race"]), reverse=True)
        print("        margins of the lost races: %s" % races)

    print("\n9. v14's losses to opponents rated below 17 (the HP at the end ours:theirs; towers, income and knights alive at turn 100, ours vs theirs; giants: the most on the board)")
    d = data["v14"]
    m = d["meta"]
    for k in sorted((k for k, v in m.items() if v["rating"] < 17 and not v["won"]), key=lambda k: (m[k]["upload"], k)):
        v, o, t = m[k], d["ours"][k], d["theirs"][k]
        at = lambda rows, turn, key: next((r[key] for r in rows if r["t"] == turn), None)
        con = [r for r in o if r["t"] >= 50 and r["k250"] > 0]
        print("   upload %d %d seat %d vs %-15s %5.2f: start HP %3d, %s at turn %3d, HP %3d:%-3d; turn 100: towers %s vs %s, income %s vs %s, knights alive %s vs %s; giants %d; contact turns %3d (HP %3d), under one or two %3d (HP %3d); to enemy towers %d HP" % (
            v["upload"], k, v["seat"], v["opponent"], v["rating"], o[0]["hp"], "death" if v["died"] else "race " if v["race"] else "other", o[-1]["t"], max(0, o[-1]["hpAfter"]),
            max(0, t[-1]["hpAfter"]), at(o, 100, "T"), at(t, 100, "T"), at(o, 100, "income"), at(t, 100, "income"), at(o, 100, "oKnights"), at(o, 100, "eKnights"),
            max(r["eGiants"] for r in o), len(con), sum(r["kd"] for r in con), sum(1 for r in con if cov(r) in (1, 2)), sum(r["kd"] for r in con if cov(r) in (1, 2)), sum(r["td"] for r in o)))

    print("\n10. Every Gold version by the opponent's band against the rating model: the logistic curve of tools/arena_compare.py at scale %.2f and the version's" % SCALE)
    print("    pooled rating (v11 %.2f, v12 %.2f, v13 %.2f, v14 %.2f); the games of the registry's Gold uploads; SD of the expected count" % tuple(RATING[k] for k in ("v11", "v12", "v13", "v14")))
    reg = json.load(open(os.path.join("docs", "data", "arena-uploads.json"), encoding="utf-8"))
    tot = {}
    for u in reg["uploads"]:
        if u["league"] != "gold":
            continue
        for g in json.load(open(u["games"], encoding="utf-8"))["games"]:
            r = g["opponent"]["ratingAtFetch"]
            band = next(b for b, lo, hi in BANDS if lo <= r < hi)
            p = 1.0 / (1.0 + math.exp(-(RATING[u["label"]] - r) / SCALE))
            for key in ((u["label"], band), (u["label"] + "/" + str(u["upload"]), band)):
                t = tot.setdefault(key, [0, 0, 0.0, 0.0])
                t[0] += 1
                t[1] += g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
                t[2] += p
                t[3] += p * (1 - p)
    cell = lambda v, band: "%s %3d games, won %3d (%2.0f %%), expected %5.1f, %+4.1f SD" % (
        v, tot[(v, band)][0], tot[(v, band)][1], pct(tot[(v, band)][1], tot[(v, band)][0]), tot[(v, band)][2], (tot[(v, band)][1] - tot[(v, band)][2]) / math.sqrt(tot[(v, band)][3]))
    for band, _, _ in BANDS:
        print("    %-9s %s" % (band, "; ".join(cell(v, band) for v in ("v11", "v12", "v13", "v14") if (v, band) in tot)))
    print("    below 17 by upload: %s" % "; ".join(cell(v, "below 17") for v in ("v11/3", "v11/4", "v12/1", "v12/2", "v12/3", "v13/1", "v13/2", "v14/1", "v14/2") if (v, "below 17") in tot))


if __name__ == "__main__":
    main()
