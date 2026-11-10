"""v13's 180 Gold placement games beside v12's 180 (study 2026-11-09): what the ladder gate changed on the arena, and the
loss anatomy that is left. Sections 1-9 print the same measures for both versions.

Inputs (gitignored; commands in README.md):
  <v13 study>/features-v13-gold.csv, ours-upload-0N.txt, opp-upload-0N.txt   cgStudy and deathTrace over v13's uploads
  <v12 study>/features-v12-gold.csv, ours-upload-0N.txt, opp-upload-0N.txt   the inputs of study-2026-11-08-next-step
Tracked inputs: docs/data/arena-<agent>-games-<date>.json (seats, opponent ratings).
Usage (from the repository root):
  python -B -I docs/data/study-2026-11-09-v13-gold/v13_gold.py --v13 <dir> --v12 <dir>
Output: stdout (v13_gold.txt beside this file).
"""
import csv
import json
import math
import os
import re
import statistics
import sys
from collections import Counter

VERSIONS = {
    "v12": {"features": "features-v12-gold.csv",
            "agents": {6860036: ("upload-01", "arena-6860036-games-2026-11-07.json"),
                       6860166: ("upload-02", "arena-6860166-games-2026-11-07.json")}},
    "v13": {"features": "features-v13-gold.csv",
            "agents": {6868408: ("upload-01", "arena-6868408-games-2026-11-09.json"),
                       6868474: ("upload-02", "arena-6868474-games-2026-11-09.json")}},
}
HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (-?\d+)/(-?\d+), scores (-?\d+)/(-?\d+), (\d+) turns\)")
LINE = re.compile(r"^t\s*(\d+) gold\s+(-?\d+) q\(\s*(-?\d+),\s*(-?\d+)\) (.*?)\s+touch\s+(-?\d+) \| T(\d+) M(\d+) B(\d+) nearT\s+(-?\d+) \| K250 (\d+) K500 (\d+) nearK\s+(-?\d+) A300 (\d+) G300 (\d+) eTow (\d+) \| hp\s+(-?\d+) \(-(-?\d+)\) \| kd (-?\d+) td (-?\d+)")
PHASES = [(0, 50), (50, 100), (100, 150), (150, 200), (200, 250)]


def med(xs):
    xs = [x for x in xs if x is not None and not (isinstance(x, float) and math.isnan(x))]
    return statistics.median(xs) if xs else float("nan")


def mean(xs):
    xs = [x for x in xs if x is not None and not (isinstance(x, float) and math.isnan(x))]
    return sum(xs) / len(xs) if xs else float("nan")


def num(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


def traces(path):
    out, cur = {}, None
    for line in open(path, encoding="utf-8-sig"):
        h = HDR.search(line)
        if h:
            cur = {"hdr": h, "turns": []}
            out[int(h.group(1))] = cur
            continue
        m = LINE.match(line)
        if m and cur is not None:
            g = m.groups()
            cur["turns"].append({"t": int(g[0]), "gold": int(g[1]), "x": int(g[2]), "y": int(g[3]), "cmd": g[4].strip(),
                                 "touch": int(g[5]), "T": int(g[6]), "M": int(g[7]), "B": int(g[8]), "nearT": int(g[9]),
                                 "k250": int(g[10]), "k500": int(g[11]), "nearK": int(g[12]), "g300": int(g[14]), "eTow": int(g[15]),
                                 "hp": int(g[16]), "loss": int(g[17]), "kd": int(g[18]), "td": int(g[19])})
    return out


def command_class(turn, ladder_done):
    """What the queen was doing on a turn (DeathTrace columns only), as in study-2026-11-08-next-step."""
    cmd = turn["cmd"]
    if cmd.startswith("MOVE"):
        return "kite" if turn["k250"] > 0 else "move"
    if cmd.startswith("BUILD"):
        parts = cmd.split()
        site, what = int(parts[1]), parts[2]
        touching = turn["touch"] == site
        if what == "TOWER" and touching and 0 <= turn["nearT"] <= 130:
            if turn["k250"] > 0:
                return "top-up under knights"
            return "idle pump" if ladder_done else "top-up"
        if what == "TOWER" and not touching and turn["k250"] > 0:
            return "flee/defence walk"
        return "walk to build" if not touching else "build on touch"
    return "wait"


def ladder_done(t):
    return t["T"] >= 6 and t["B"] >= 2 and t["M"] >= 2


def episodes(turns, lo=50, hi=250):
    out, cur, prev = [], None, None
    for t in turns:
        if t["t"] < lo or t["t"] >= hi:
            prev = t
            continue
        if t["k250"] > 0:
            if cur is None:
                cur = {"kd": 0, "turns": 0, "before": prev, "start": t["t"]}
            cur["kd"] += t["kd"]
            cur["turns"] += 1
        elif cur is not None:
            out.append(cur)
            cur = None
        prev = t
    if cur is not None:
        out.append(cur)
    return out


def before_class(t):
    if t is None:
        return "other"
    if t["cmd"].startswith("BUILD"):
        parts = t["cmd"].split()
        if int(parts[1]) != t["touch"]:
            # A walk: to a new site, or (v13's gate, the flight) to an own tower.
            return "walking to a site"
    if 0 <= t["nearT"] <= 130:
        return "at an own tower"
    if t["cmd"].startswith("MOVE"):
        return "walking (MOVE)"
    return "other"


def load(name, study, docs):
    v = VERSIONS[name]
    rows = list(csv.DictReader(open(os.path.join(study, v["features"]), encoding="utf-8")))
    ours = {r["gameId"]: r for r in rows if r["pseudo"] == "aabzac"}
    theirs = {r["gameId"]: r for r in rows if r["pseudo"] != "aabzac"}
    rating, seat, opp = {}, {}, {}
    for agent, (_, manifest) in v["agents"].items():
        m = json.load(open(os.path.join(docs, manifest), encoding="utf-8"))
        for g in m["games"]:
            rating[str(g["gameId"])] = g["opponent"]["ratingAtFetch"]
            seat[str(g["gameId"])] = g["seat"]
            opp[str(g["gameId"])] = g["opponent"]["pseudo"]
    tr_ours, tr_opp = {}, {}
    for agent, (upload, _) in v["agents"].items():
        tr_ours.update(traces(os.path.join(study, "ours-%s.txt" % upload)))
        tr_opp.update(traces(os.path.join(study, "opp-%s.txt" % upload)))
    games = []
    for gid, r in ours.items():
        o = theirs[gid]
        rk, ork, turns, dt, odt = int(r["rank"]), int(r["oppRank"]), int(r["turns"]), int(r["deathTurn"]), int(o["deathTurn"])
        result = "won" if rk < ork else "lost" if rk > ork else "draw"
        if turns >= 250:
            how = "limit"
        elif result == "lost" and dt >= 0:
            how = "died"
        elif result == "won" and odt >= 0:
            how = "killed"
        else:
            how = "referee"
        games.append({"gid": gid, "r": r, "o": o, "result": result, "how": how, "turns": turns, "rating": rating[gid], "seat": seat[gid],
                      "opp": opp[gid], "ours": tr_ours[int(gid)]["turns"], "theirs": tr_opp[int(gid)]["turns"]})
    return games


def pts(g):
    return 1.0 if g["result"] == "won" else 0.5 if g["result"] == "draw" else 0.0


def section(title):
    print("\n" + title)


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    docs = os.path.join("docs", "data")
    data = {"v12": load("v12", arg("--v12", os.path.join("replays", "study", "gold-v12-2026-11-08")), docs),
            "v13": load("v13", arg("--v13", os.path.join("replays", "study", "gold-v13-2026-11-09")), docs)}
    names = ("v12", "v13")

    section("1. Outcomes (Gold, two placements each)")
    for n in names:
        games = data[n]
        c = Counter((g["result"], g["how"]) for g in games)
        print("   %s: %d games, won %d (limit %d, killed %d, referee %d), lost %d (limit %d, died %d, referee %d), draws %d; to turn 250: %d" % (
            n, len(games), sum(1 for g in games if g["result"] == "won"), c[("won", "limit")], c[("won", "killed")], c[("won", "referee")],
            sum(1 for g in games if g["result"] == "lost"), c[("lost", "limit")], c[("lost", "died")], c[("lost", "referee")],
            sum(1 for g in games if g["result"] == "draw"), sum(1 for g in games if g["turns"] >= 250)))

    section("2. Score by the opponent's rating at fetch and by seat (points, ties 1/2)")
    for n in names:
        games = data[n]
        parts = []
        for lo, hi in ((0, 17), (17, 20), (20, 22), (22, 24), (24, 99)):
            gs = [g for g in games if lo <= g["rating"] < hi]
            if gs:
                parts.append("%d-%d: %d games %.0f %%" % (lo, hi, len(gs), 100 * sum(map(pts, gs)) / len(gs)))
        print("   %s: %s" % (n, "; ".join(parts)))
        for s in (0, 1):
            gs = [g for g in games if g["seat"] == s]
            d = sum(1 for g in gs if g["how"] == "died")
            print("      seat %d: %d games, %.0f %%, our queen died in %d" % (s, len(gs), 100 * sum(map(pts, gs)) / len(gs), d))
        strong = [g for g in games if g["rating"] >= 20]
        c = Counter((g["result"], g["how"]) for g in strong)
        print("      against 20 and above: %d games, %.0f %%: won races %d, kills %d, lost races %d, deaths %d; opponents' median rating %.2f (all games)" % (
            len(strong), 100 * sum(map(pts, strong)) / len(strong), c[("won", "limit")], c[("won", "killed")], c[("lost", "limit")], c[("lost", "died")],
            med([g["rating"] for g in games])))

    section("3. HP races at turn 250: our HP minus the enemy's (medians; means in brackets), and the HP lost to knights per phase")
    for n in names:
        games = data[n]
        races = {res: [g for g in games if g["how"] == "limit" and g["result"] == res] for res in ("lost", "won")}
        for res, gs in races.items():
            parts = []
            for t in (50, 100, 150, 200):
                d = [num(g["r"]["hp_%d" % t]) - num(g["o"]["hp_%d" % t]) for g in gs]
                parts.append("t%d %+.0f (%+.1f)" % (t, med(d), mean(d)))
            fin = [num(g["r"]["score"]) - num(g["o"]["score"]) for g in gs]
            print("   %s %-4s %3d: %s; final %+.0f (%+.1f); within 5 HP %d, within 10 HP %d" % (n, res, len(gs), ", ".join(parts), med(fin), mean(fin),
                  sum(1 for x in fin if abs(x) <= 5), sum(1 for x in fin if abs(x) <= 10)))
        for res, gs in races.items():
            ours = " | ".join("%.1f" % mean([sum(t["kd"] for t in g["ours"] if a <= t["t"] < b) for g in gs]) for a, b in PHASES)
            them = " | ".join("%.1f" % mean([sum(t["kd"] for t in g["theirs"] if a <= t["t"] < b) for g in gs]) for a, b in PHASES)
            co = " | ".join("%.1f" % mean([sum(1 for t in g["ours"] if a <= t["t"] < b and t["k250"] > 0) for g in gs]) for a, b in PHASES)
            ct = " | ".join("%.1f" % mean([sum(1 for t in g["theirs"] if a <= t["t"] < b and t["k250"] > 0) for g in gs]) for a, b in PHASES)
            print("      %s %-4s races, per 50 turns: our HP to knights %s; theirs to ours %s; contact turns ours %s; theirs %s" % (n, res, ours, them, co, ct))
    print("   all games (means per game): HP our queen lost to knights / to towers, the enemy queen to our knights / our towers, by phase")
    for n in names:
        games = data[n]
        for a, b in PHASES:
            gs = [g for g in games if g["turns"] > a]
            print("      %s turns %3d-%3d (%3d games): ours %4.1f / %3.1f; theirs %4.1f / %3.1f" % (n, a, b, len(gs),
                  mean([sum(t["kd"] for t in g["ours"] if a <= t["t"] < b) for g in gs]), mean([sum(t["td"] for t in g["ours"] if a <= t["t"] < b) for g in gs]),
                  mean([sum(t["kd"] for t in g["theirs"] if a <= t["t"] < b) for g in gs]), mean([sum(t["td"] for t in g["theirs"] if a <= t["t"] < b) for g in gs])))

    section("4. The gate on the arena: contact episodes of our queen on turns 50-249 by what she did on the turn before the contact")
    for n in names:
        games = data[n]
        for label, gs in (("all games", games), ("lost races", [g for g in games if g["how"] == "limit" and g["result"] == "lost"]),
                          ("won races", [g for g in games if g["how"] == "limit" and g["result"] == "won"]),
                          ("deaths", [g for g in games if g["how"] == "died"])):
            acc, total = {}, 0
            for g in gs:
                for e in episodes(g["ours"]):
                    a = acc.setdefault(before_class(e["before"]), [0, 0, 0])
                    a[0] += 1
                    a[1] += e["kd"]
                    a[2] += e["turns"]
                    total += 1
            print("   %s %-10s (%3d games): %s" % (n, label, len(gs), "; ".join(
                "%s %.1f a game, %.2f HP each, %.1f HP a game" % (c, a[0] / len(gs), a[1] / max(1, a[0]), a[1] / len(gs))
                for c, a in sorted(acc.items(), key=lambda kv: -kv[1][1]))))
    print("   the gate's state (turn 30 on, two own towers or more, an enemy knight within 500 and none within 250): turns a game, and the queen's command there")
    for n in names:
        games = data[n]
        acc, turns = Counter(), 0
        for g in games:
            for t in g["ours"]:
                if t["t"] >= 30 and t["T"] >= 2 and t["k500"] > 0 and t["k250"] == 0:
                    turns += 1
                    cmd = t["cmd"]
                    if cmd.startswith("BUILD"):
                        p = cmd.split()
                        touching = int(p[1]) == t["touch"]
                        if p[2] == "TOWER":
                            acc["BUILD TOWER on the touched site" if touching else "walk with BUILD TOWER"] += 1
                        else:
                            acc[("touch " if touching else "walk to ") + p[2]] += 1
                    else:
                        acc[cmd.split()[0]] += 1
        print("      %s: %.1f turns a game; %s" % (n, turns / len(games), ", ".join("%s %.1f" % (k, c / len(games)) for k, c in acc.most_common())))

    section("5. Our queen's deaths")
    for n in names:
        games = data[n]
        deaths = [g for g in games if g["how"] == "died"]
        kd = mean([sum(t["kd"] for t in g["ours"][-30:]) for g in deaths])
        td = mean([sum(t["td"] for t in g["ours"][-30:]) for g in deaths])
        print("   %s: %d deaths, turns %s (median %.0f)" % (n, len(deaths), sorted(int(g["r"]["deathTurn"]) for g in deaths), med([float(g["r"]["deathTurn"]) for g in deaths])))
        print("      last 30 turns: %.1f HP to knights, %.1f to towers; opponent income at t100 / t150 %.1f / %.1f, ours %.1f / %.1f; enemy knights alive t100 / t150 %.1f / %.1f; giants by the opponent in %d" % (
            kd, td, med([num(g["o"]["inc_100"]) for g in deaths]), med([num(g["o"]["inc_150"]) for g in deaths]),
            med([num(g["r"]["inc_100"]) for g in deaths]), med([num(g["r"]["inc_150"]) for g in deaths]),
            med([num(g["o"]["knights_100"]) for g in deaths]), med([num(g["o"]["knights_150"]) for g in deaths]),
            sum(1 for g in deaths if num(g["o"]["trainG"]) > 0)))
        print("      by the opponent's rating: %s" % ", ".join("%.1f" % g["rating"] for g in sorted(deaths, key=lambda g: g["rating"])))

    section("6. Medians, us / them")
    keys = ["inc_50", "inc_100", "inc_150", "inc_200", "maxIncome", "meanIncome", "minesBuilt", "mineUpgrades", "towersBuilt", "towers_100", "towers_200",
            "thp_100", "thp_200", "towerTopUps", "trainK", "trainG", "barracksBuiltK", "structuresLost", "meanGold", "gold_100", "gold_150", "gold_200"]
    for n in names:
        games = data[n]
        groups = [("all", games), ("lost", [g for g in games if g["result"] == "lost"]), ("won", [g for g in games if g["result"] == "won"]),
                  ("vs 20+", [g for g in games if g["rating"] >= 20])]
        print("   %s: %s" % (n, " | ".join("%s (%d)" % (name, len(gs)) for name, gs in groups)))
        for k in keys:
            print("      %-14s %s" % (k, " | ".join("%5.1f / %5.1f" % (med([num(g["r"][k]) for g in gs]), med([num(g["o"][k]) for g in gs])) for _, gs in groups)))

    section("7. The queens' turns by command class, share of all turns")
    for n in names:
        games = data[n]
        sets = [(n, [g["ours"] for g in games]), ("opponents 20+", [g["theirs"] for g in games if g["rating"] >= 20]),
                ("opponents 22+", [g["theirs"] for g in games if g["rating"] >= 22]), ("opponents <20", [g["theirs"] for g in games if g["rating"] < 20])]
        for name, trs in sets:
            acc, total, contact, kd = Counter(), 0, 0, 0
            for turns in trs:
                total += len(turns)
                for t in turns:
                    acc[command_class(t, ladder_done(t))] += 1
                    if t["k250"] > 0:
                        contact += 1
                        kd += t["kd"]
            print("   %-16s %3d queens, %3.0f turns: %s; contact %.1f %%, %.2f HP per contact turn" % (
                name, len(trs), total / max(1, len(trs)), ", ".join("%s %.0f %%" % (k, 100.0 * c / max(1, total)) for k, c in acc.most_common()),
                100.0 * contact / max(1, total), kd / max(1, contact)))

    section("8. The first wave: the second tower's turn minus the first contact, the loss in the 20 turns from the contact")
    def first_contact(turns):
        return next((t["t"] for t in turns if t["k250"] > 0), None)

    def loss_k20(turns):
        c = first_contact(turns)
        return (0.0, None) if c is None else (float(sum(t["kd"] for t in turns if c <= t["t"] < c + 20)), c)

    def second_tower(turns):
        return next((t["t"] for t in turns if t["T"] >= 2), None)

    for n in names:
        games = data[n]
        bins = {}
        for g in games:
            k20, c = loss_k20(g["ours"])
            t2 = second_tower(g["ours"])
            if c is None or t2 is None or c >= 60:
                continue
            rel = t2 - c
            b = "<=-3" if rel <= -3 else "-2..-1" if rel < 0 else "0..2" if rel < 3 else ">=3"
            d = bins.setdefault(b, {"n": 0, "pts": 0.0, "k20": []})
            d["n"] += 1
            d["pts"] += pts(g)
            d["k20"].append(k20)
        allk = [loss_k20(g["ours"])[0] for g in games]
        print("   %s: first-wave loss, all games mean %.1f; lost %.1f, won %.1f; first contact turn median %.0f" % (
            n, mean(allk), mean([loss_k20(g["ours"])[0] for g in games if g["result"] == "lost"]), mean([loss_k20(g["ours"])[0] for g in games if g["result"] == "won"]),
            med([float(first_contact(g["ours"])) for g in games if first_contact(g["ours"]) is not None])))
        for b in ("<=-3", "-2..-1", "0..2", ">=3"):
            d = bins.get(b)
            if d:
                print("      t2-contact %-6s %3d games, score %.0f %%, first-wave loss %.1f" % (b, d["n"], 100 * d["pts"] / d["n"], mean(d["k20"])))

    section("9. Gold: the bank at the end of the game and the trainings")
    for n in names:
        games = data[n]
        for label, gs in (("lost races", [g for g in games if g["how"] == "limit" and g["result"] == "lost"]),
                          ("won races", [g for g in games if g["how"] == "limit" and g["result"] == "won"]), ("all", games)):
            end = [g["ours"][-1]["gold"] for g in gs]
            oend = [g["theirs"][-1]["gold"] for g in gs]
            print("   %s %-10s: our gold on the last turn median %.0f (80 or more in %d of %d); theirs %.0f; our gold above 160 on %.0f %% of turns, theirs %.0f %%" % (
                n, label, med(end), sum(1 for x in end if x >= 80), len(gs), med(oend),
                100 * mean([sum(1 for t in g["ours"] if t["gold"] >= 160) / len(g["ours"]) for g in gs]),
                100 * mean([sum(1 for t in g["theirs"] if t["gold"] >= 160) / len(g["theirs"]) for g in gs])))


if __name__ == "__main__":
    main()
