"""Tables from ArenaProbe's csv files (study 2026-11-09): the waves, the mines, what the queens' turns go to, and the
ladder gate's holds, for v13's and v12's Gold games, us beside the opponents.

Usage (from the repository root):
  python -B -I docs/data/study-2026-11-09-v13-gold/probe_tables.py --v13 <probe dir> --v12 <probe dir>
Output: stdout (probe_tables.txt beside this file).
"""
import csv
import json
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from v13_gold import VERSIONS, mean, med  # noqa: E402

INT = {"gid", "seat", "us", "t", "hp", "hpAfter", "kd", "td", "gold", "income", "site", "touch", "touchHp", "targetCov", "T", "M", "B", "nearTHp", "k250", "k500",
       "k800", "eKnights", "oKnights", "eGiants", "covFree", "covFreeGold", "minesCov", "towersLow", "thpSum", "qCov", "eTow", "barracks", "dist", "enemyTowers",
       "enemyTowerHp", "goldLeft", "damage", "contactTurns", "maxMine", "rate", "covered", "k500", "kHpMax500", "kHpSum500", "kHpSum250", "towersCov", "minTHp", "towersIn510", "towersIn400", "bestCov180", "bestCov300", "bestCovFull300",
       "seed", "won", "draw", "turns", "score", "oppScore"}
FLOAT = {"qx", "qy", "targetDist", "nearT", "nearK", "nearCovFree", "nearFree", "fwd", "x", "y", "homeDist", "ownQueenDist", "enemyQueenDist", "minTDist", "lowTDist",
         "lowTCovDist"}


def read(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            for k, v in r.items():
                if k in INT:
                    r[k] = int(v)
                elif k in FLOAT:
                    r[k] = float(v)
            rows.append(r)
    return rows


def meta(name):
    """gid -> (rating, our seat, result for us: 1 won, 0 lost, how)."""
    out = {}
    for agent, (_, manifest) in VERSIONS[name]["agents"].items():
        m = json.load(open(os.path.join("docs", "data", manifest), encoding="utf-8"))
        for g in m["games"]:
            seat = g["seat"]
            out[g["gameId"]] = {"rating": g["opponent"]["ratingAtFetch"], "seat": seat, "won": g["ranks"][seat] < g["ranks"][1 - seat], "frames": g["frames"]}
    return out


def by_game(rows):
    d = defaultdict(list)
    for r in rows:
        d[(r["gid"], r["us"])].append(r)
    return d


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    dirs = {"v12": arg("--v12", os.path.join("replays", "study", "gold-v13-2026-11-09", "probe-v12")),
            "v13": arg("--v13", os.path.join("replays", "study", "gold-v13-2026-11-09", "probe"))}
    data = {}
    for n, d in dirs.items():
        data[n] = {"turns": read(os.path.join(d, "turns.csv")), "events": read(os.path.join(d, "events.csv")), "waves": read(os.path.join(d, "waves.csv")), "meta": meta(n)}
        t = by_game(data[n]["turns"])
        data[n]["tg"] = t
        data[n]["length"] = {gid: len(rows) for (gid, us), rows in t.items() if us == 1}
        died = {}
        for (gid, us), rows in t.items():
            died[(gid, us)] = rows[-1]["hpAfter"] <= 0
        data[n]["died"] = died

    def groups(n):
        """Game sets of a version: name -> set of game ids."""
        m = data[n]["meta"]
        died = data[n]["died"]
        length = data[n]["length"]
        g = {"all": set(m), "won": {k for k, v in m.items() if v["won"]}, "lost": {k for k, v in m.items() if not v["won"]},
             "our deaths": {k for k in m if died.get((k, 1))}, "lost races": {k for k, v in m.items() if not v["won"] and length.get(k, 0) >= 250},
             "won races": {k for k, v in m.items() if v["won"] and length.get(k, 0) >= 250}, "vs 22+": {k for k, v in m.items() if v["rating"] >= 22},
             "vs 20-22": {k for k, v in m.items() if 20 <= v["rating"] < 22}, "vs <20": {k for k, v in m.items() if v["rating"] < 20}}
        return g

    print("1. Knight waves (a release: the knight barracks that start training on one turn), us / the opponents")
    for n in ("v12", "v13"):
        gs = groups(n)
        waves = [w for w in data[n]["waves"] if w["type"] == "K"]
        for label in ("all", "won races", "lost races", "our deaths", "vs <20", "vs 20-22", "vs 22+"):
            ids = gs[label]
            if not ids:
                continue
            parts = []
            for us in (1, 0):
                ws = [w for w in waves if w["us"] == us and w["gid"] in ids]
                late = [w for w in ws if w["t"] >= 50]
                parts.append("%.1f releases a game (%.1f knights), barracks %4.0f from the enemy queen (fwd %.2f), damage %.2f a release; from turn 50: %.1f releases, %4.0f away, damage %.2f" % (
                    len(ws) / len(ids), 4 * sum(w["barracks"] for w in ws) / len(ids), mean([w["dist"] for w in ws]), mean([w["fwd"] for w in ws]), mean([w["damage"] for w in ws]),
                    len(late) / len(ids), mean([w["dist"] for w in late]), mean([w["damage"] for w in late])))
            print("   %s %-10s (%3d): ours %s\n   %s %-10s        theirs %s" % (n, label, len(ids), parts[0], " " * len(n), "", parts[1]))
    print("   damage a release by the distance from the releasing barracks to the enemy queen (releases from turn 30), v13's games")
    waves = [w for w in data["v13"]["waves"] if w["type"] == "K" and w["t"] >= 30]
    for lo, hi in ((0, 600), (600, 800), (800, 1000), (1000, 1200), (1200, 1500), (1500, 9999)):
        o = [w for w in waves if w["us"] == 1 and lo <= w["dist"] < hi]
        t = [w for w in waves if w["us"] == 0 and lo <= w["dist"] < hi]
        print("      %4d-%4d: ours %4d releases (%4.1f %%) damage %.2f, contact turns %.1f | theirs %4d (%4.1f %%) damage %.2f, contact turns %.1f" % (
            lo, hi, len(o), 100.0 * len(o) / max(1, sum(1 for w in waves if w["us"] == 1)), mean([w["damage"] for w in o]), mean([w["contactTurns"] for w in o]),
            len(t), 100.0 * len(t) / max(1, sum(1 for w in waves if w["us"] == 0)), mean([w["damage"] for w in t]), mean([w["contactTurns"] for w in t])))
    print("   damage a release by the enemy's towers at the release (from turn 30), v13's games")
    for lo, hi in ((0, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 99)):
        o = [w for w in waves if w["us"] == 1 and lo <= w["enemyTowers"] < hi]
        t = [w for w in waves if w["us"] == 0 and lo <= w["enemyTowers"] < hi]
        print("      towers %d-%d: ours %4d releases, damage %.2f | theirs %4d, damage %.2f" % (lo, hi - 1, len(o), mean([w["damage"] for w in o]), len(t), mean([w["damage"] for w in t])))
    print("   releases by size (knight barracks released together), v13's games: ours %s; theirs %s" % (
        dict(sorted(Counter(w["barracks"] for w in waves if w["us"] == 1).items())), dict(sorted(Counter(w["barracks"] for w in waves if w["us"] == 0).items()))))
    for nb in (1, 2, 3):
        o = [w for w in waves if w["us"] == 1 and w["barracks"] == nb]
        t = [w for w in waves if w["us"] == 0 and w["barracks"] == nb]
        if o or t:
            print("      %d barracks: ours damage %.2f a release (%.2f a barracks), %4.0f away | theirs %.2f (%.2f a barracks), %4.0f away" % (
                nb, mean([w["damage"] for w in o]), mean([w["damage"] for w in o]) / nb, mean([w["dist"] for w in o]),
                mean([w["damage"] for w in t]), mean([w["damage"] for w in t]) / nb, mean([w["dist"] for w in t])))

    print("\n2. Mines: built and lost, a game, us / the opponents")
    for n in ("v12", "v13"):
        gs = groups(n)
        ev = data[n]["events"]
        for label in ("all", "won", "lost", "our deaths", "vs 22+"):
            ids = gs[label]
            if not ids:
                continue
            parts = []
            for us in (1, 0):
                e = [x for x in ev if x["us"] == us and x["gid"] in ids and x["kind"] == "MINE"]
                built = [x for x in e if x["event"] == "built"]
                lost = Counter(x["cause"] for x in e if x["event"] == "lost")
                torn = [x for x in e if x["event"] == "lost" and x["cause"] == "creep"]
                parts.append("built %.1f (fwd %.2f, covered at build %.0f %%, max rate %.1f); lost to creeps %.1f (gold left %3.0f each, covered %.0f %%), exhausted %.1f, to the enemy queen %.1f, rebuilt as another %.1f" % (
                    len(built) / len(ids), mean([x["fwd"] for x in built]), 100 * mean([x["covered"] for x in built]), mean([x["maxMine"] for x in built]),
                    lost["creep"] / len(ids), mean([x["gold"] for x in torn]), 100 * mean([x["covered"] for x in torn]) if torn else float("nan"),
                    lost["exhausted"] / len(ids), lost["queen"] / len(ids), lost["rebuilt"] / len(ids)))
            print("   %s %-10s (%3d): ours   %s\n   %s %-10s        theirs %s" % (n, label, len(ids), parts[0], " " * len(n), "", parts[1]))
    print("   gold under our structures, v13 (the site's gold when a tower or a barracks is built on it, summed a game): ours %.0f on %.1f sites; theirs %.0f on %.1f sites" % (
        sum(x["gold"] for x in data["v13"]["events"] if x["us"] == 1 and x["event"] == "built" and x["kind"] != "MINE" and x["cause"] == "free") / 180.0,
        sum(1 for x in data["v13"]["events"] if x["us"] == 1 and x["event"] == "built" and x["kind"] != "MINE" and x["cause"] == "free" and x["gold"] > 0) / 180.0,
        sum(x["gold"] for x in data["v13"]["events"] if x["us"] == 0 and x["event"] == "built" and x["kind"] != "MINE" and x["cause"] == "free") / 180.0,
        sum(1 for x in data["v13"]["events"] if x["us"] == 0 and x["event"] == "built" and x["kind"] != "MINE" and x["cause"] == "free" and x["gold"] > 0) / 180.0))
    print("   the lifetime of a mine (turns from built to lost), v13: ours median %.0f (lost to creeps %.0f, exhausted %.0f); theirs %.0f (creeps %.0f, exhausted %.0f)" % tuple(
        med(x) for x in lifetimes(data["v13"]["events"])))
    print("   mines built by turn, v13: %s" % "; ".join("turns %d-%d ours %.2f / theirs %.2f" % (lo, hi, sum(1 for x in data["v13"]["events"] if x["us"] == 1 and x["event"] == "built" and x["kind"] == "MINE" and lo <= x["t"] < hi) / 180.0,
                                                 sum(1 for x in data["v13"]["events"] if x["us"] == 0 and x["event"] == "built" and x["kind"] == "MINE" and lo <= x["t"] < hi) / 180.0)
                                                 for lo, hi in ((0, 15), (15, 50), (50, 100), (100, 150), (150, 250))))

    print("\n3. Towers: built and lost, a game, us / the opponents (v13's games)")
    ev = data["v13"]["events"]
    gs = groups("v13")
    for label in ("all", "won", "lost", "our deaths", "vs 22+"):
        ids = gs[label]
        parts = []
        for us in (1, 0):
            e = [x for x in ev if x["us"] == us and x["gid"] in ids and x["kind"] == "TOWER"]
            built = [x for x in e if x["event"] == "built"]
            lost = Counter(x["cause"] for x in e if x["event"] == "lost")
            parts.append("built %.1f (fwd %.2f); melted %.1f, busted by giants %.1f, rebuilt as another %.1f" % (len(built) / len(ids), mean([x["fwd"] for x in built]),
                         lost["melt"] / len(ids), lost["giant"] / len(ids), lost["rebuilt"] / len(ids)))
        print("   %-10s (%3d): ours %s | theirs %s" % (label, len(ids), parts[0], parts[1]))

    print("\n4. What a queen's turn goes to (share of all turns), us / the opponents by band")
    for n in ("v12", "v13"):
        gs = groups(n)
        sets = [("ours", 1, gs["all"]), ("ours, lost", 1, gs["lost"]), ("theirs <20", 0, gs["vs <20"]), ("theirs 20-22", 0, gs["vs 20-22"]), ("theirs 22+", 0, gs["vs 22+"])]
        for name, us, ids in sets:
            acc, total = Counter(), 0
            for gid in ids:
                for r in data[n]["tg"].get((gid, us), []):
                    acc[turn_class(r)] += 1
                    total += 1
            if total:
                print("   %s %-13s %3d games: %s" % (n, name, len(ids), ", ".join("%s %.1f %%" % (k, 100.0 * c / total) for k, c in acc.most_common())))

    print("\n5. The ladder gate's state in v13 (turn 30 on, two own towers or more, an enemy knight within 500 and none within 250), ours")
    rows = [r for r in data["v13"]["turns"] if r["us"] == 1 and r["t"] >= 30 and r["T"] >= 2 and r["k500"] > 0 and r["k250"] == 0]
    n_games = 180.0
    print("   %.1f turns a game" % (len(rows) / n_games))
    top = [r for r in rows if r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T"]
    print("   top-ups of the touched tower: %.1f a game; by its HP before the top-up: below 500 %.1f, 500-699 %.1f, 700-749 %.1f, 750-800 %.1f (a top-up adds 100 up to 800, so above 700 it is partly lost and at 796-800 it adds 0-4)" % (
        len(top) / n_games, sum(1 for r in top if r["touchHp"] < 500) / n_games, sum(1 for r in top if 500 <= r["touchHp"] < 700) / n_games,
        sum(1 for r in top if 700 <= r["touchHp"] < 750) / n_games, sum(1 for r in top if r["touchHp"] >= 750) / n_games))
    full = [r for r in top if r["touchHp"] >= 700]
    print("   of the %.1f top-ups a game at 700 or more: another own tower below 700 stands in %.0f %%; a free site lies inside an own tower's range in %.0f %% (with gold in %.0f %%), within 200 of her in %.0f %%, within 300 in %.0f %%; own mines under cover %.2f, the income %.1f, towers %.1f" % (
        len(full) / n_games, 100 * mean([1 if r["towersLow"] > 0 else 0 for r in full]), 100 * mean([1 if r["covFree"] > 0 else 0 for r in full]),
        100 * mean([1 if r["covFreeGold"] > 0 else 0 for r in full]), 100 * mean([1 if 0 <= r["nearCovFree"] <= 200 else 0 for r in full]),
        100 * mean([1 if 0 <= r["nearCovFree"] <= 300 else 0 for r in full]), mean([r["minesCov"] for r in full]), mean([r["income"] for r in full]), mean([r["T"] for r in full])))
    walk = [r for r in rows if r["cmd"] == "B" and r["touch"] != r["site"]]
    print("   walks in the state: %.1f a game; to an own tower %.1f (it stands %.0f away, mean), to a free site %.1f, to an own mine %.1f" % (
        len(walk) / n_games, sum(1 for r in walk if r["targetKind"] == "T") / n_games, mean([r["targetDist"] for r in walk if r["targetKind"] == "T"]),
        sum(1 for r in walk if r["targetKind"] == "F") / n_games, sum(1 for r in walk if r["targetKind"] == "M") / n_games))
    allrows = [r for r in data["v13"]["turns"] if r["us"] == 1 and r["t"] >= 30 and r["T"] >= 2]
    print("   in the state the queen has: towers %.1f, mines %.1f, income %.1f, a free site under an own tower's cover in %.0f %% of the turns (with gold %.0f %%); outside the state: towers %.1f, mines %.1f, income %.1f" % (
        mean([r["T"] for r in rows]), mean([r["M"] for r in rows]), mean([r["income"] for r in rows]), 100 * mean([1 if r["covFree"] > 0 else 0 for r in rows]),
        100 * mean([1 if r["covFreeGold"] > 0 else 0 for r in rows]),
        mean([r["T"] for r in allrows if r["k500"] == 0]), mean([r["M"] for r in allrows if r["k500"] == 0]), mean([r["income"] for r in allrows if r["k500"] == 0])))

    print("\n6. All top-ups by the tower's HP before the top-up (a game), us / the opponents 22+ (v13's games)")
    for name, us, ids in (("ours", 1, gs["all"]), ("theirs 22+", 0, gs["vs 22+"]), ("theirs 20-22", 0, gs["vs 20-22"])):
        tops = [r for gid in ids for r in data["v13"]["tg"].get((gid, us), []) if r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T"]
        print("   %-12s %.1f a game: below 300 %.1f, 300-499 %.1f, 500-699 %.1f, 700-749 %.1f, 750-800 %.1f; with a knight within 250 %.1f, within 500 only %.1f, none within 500 %.1f" % (
            name, len(tops) / len(ids), sum(1 for r in tops if r["touchHp"] < 300) / len(ids), sum(1 for r in tops if 300 <= r["touchHp"] < 500) / len(ids),
            sum(1 for r in tops if 500 <= r["touchHp"] < 700) / len(ids), sum(1 for r in tops if 700 <= r["touchHp"] < 750) / len(ids), sum(1 for r in tops if r["touchHp"] >= 750) / len(ids),
            sum(1 for r in tops if r["k250"] > 0) / len(ids), sum(1 for r in tops if r["k250"] == 0 and r["k500"] > 0) / len(ids), sum(1 for r in tops if r["k500"] == 0) / len(ids)))
    print("   own towers and their summed HP by turn (means over the games still running), ours / theirs 22+ / theirs 20-22")
    for t in (50, 75, 100, 150, 200):
        parts = []
        for us, ids in ((1, gs["all"]), (0, gs["vs 22+"]), (0, gs["vs 20-22"])):
            rr = [r for gid in ids for r in data["v13"]["tg"].get((gid, us), []) if r["t"] == t]
            parts.append("%.1f towers, %4.0f HP, %.0f each" % (mean([r["T"] for r in rr]), mean([r["thpSum"] for r in rr]), sum(r["thpSum"] for r in rr) / max(1, sum(r["T"] for r in rr))))
        print("      t%d: %s" % (t, " | ".join(parts)))


def lifetimes(events):
    """Mine lifetimes: (ours all, ours creep, ours exhausted, theirs all, theirs creep, theirs exhausted)."""
    out = []
    for us in (1, 0):
        built = {}
        life = {"all": [], "creep": [], "exhausted": []}
        for x in events:
            if x["us"] != us or x["kind"] != "MINE":
                continue
            key = (x["gid"], x["site"])
            if x["event"] == "built":
                built[key] = x["t"]
            elif key in built:
                d = x["t"] - built.pop(key)
                life["all"].append(d)
                if x["cause"] in life:
                    life[x["cause"]].append(d)
        out += [life["all"], life["creep"], life["exhausted"]]
    return out


def turn_class(r):
    """What the turn goes to (the probe's columns)."""
    if r["cmd"] == "M":
        if r["k250"] > 0:
            return "MOVE, knight within 250"
        return "MOVE, knight within 500" if r["k500"] > 0 else "MOVE, clear"
    if r["cmd"] == "W":
        return "WAIT"
    touching = r["touch"] == r["site"]
    if touching:
        if r["touchKind"] == "T" and r["what"] == "TOWER":
            if r["k250"] > 0:
                return "top-up, knight within 250"
            if r["touchHp"] >= 700:
                return "top-up at 700+"
            return "top-up below 700"
        if r["touchKind"] == "M" and r["what"] == "MINE":
            return "mine upgrade"
        if r["touchKind"] == "F":
            return "new " + ("barracks" if r["what"].startswith("BARRACKS") else r["what"].lower())
        return "rebuild"
    if r["targetKind"] == "T":
        return "walk to an own tower"
    if r["targetKind"] == "M":
        return "walk to an own mine"
    return "walk to a site"


if __name__ == "__main__":
    main()
