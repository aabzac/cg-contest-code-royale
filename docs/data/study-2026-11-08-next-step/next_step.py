"""v12's 180 Gold placement games (study 2026-11-08): the loss anatomy that the next-step analysis rests on, recomputed
independently of the agents' scripts: outcomes, the HP races by phase, the deaths, medians by result (us / them), the
opponents rated 20 and above, the seat split, and the Gold ladder (the gap to the boss).

Inputs (gitignored, rebuilt from the replays of v12's Gold uploads 1 and 2; commands in README.md):
  replays/study/gold-v12-2026-11-08/features-v12-gold.csv   ./gradlew cgStudy over the two upload directories
  replays/study/gold-v12-2026-11-08/ours-upload-0N.txt      ./gradlew -q deathTrace -Pagent=<ours> -Pall=1 (our queen)
  replays/study/gold-v12-2026-11-08/opp-upload-0N.txt       the same with -Pfoe=1 (the opponent's queen)
Tracked inputs: docs/data/arena-<agent>-games-2026-11-07.json (seats, opponent scores), docs/data/leaderboard-gold-2026-11-08.json.
Usage (from the repository root): python -B -I docs/data/study-2026-11-08-next-step/next_step.py [--data <root holding replays/>]
Output: stdout (next_step.txt beside this file).
"""
import csv
import json
import math
import os
import re
import statistics
import sys
from collections import Counter

AGENTS = {6860036: ("upload-01", "arena-6860036-games-2026-11-07.json"), 6860166: ("upload-02", "arena-6860166-games-2026-11-07.json")}
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
    """game id -> {"hdr": match, "turns": [dict per turn]}."""
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
                                 "k250": int(g[10]), "k500": int(g[11]), "nearK": int(g[12]), "eTow": int(g[15]),
                                 "hp": int(g[16]), "loss": int(g[17]), "kd": int(g[18]), "td": int(g[19])})
    return out


def command_class(turn, ladder_done):
    """What the queen was doing on a turn (DeathTrace columns only)."""
    cmd = turn["cmd"]
    if cmd.startswith("MOVE"):
        return "kite" if turn["k250"] > 0 else "move"
    if cmd.startswith("BUILD"):
        parts = cmd.split()
        site, what = int(parts[1]), parts[2]
        touching = turn["touch"] == site
        if what == "TOWER" and touching and turn["nearT"] >= 0 and turn["nearT"] <= 130:
            if turn["k250"] > 0:
                return "top-up under knights"
            return "idle pump" if ladder_done else "top-up"
        if what == "TOWER" and not touching and turn["k250"] > 0:
            return "flee/defence walk"
        return "walk to build" if not touching else "build on touch"
    return "wait"


def main():
    root = "."
    if "--data" in sys.argv:
        root = sys.argv[sys.argv.index("--data") + 1]
    study = os.path.join(root, "replays", "study", "gold-v12-2026-11-08")
    rows = list(csv.DictReader(open(os.path.join(study, "features-v12-gold.csv"), encoding="utf-8")))
    ours = {r["gameId"]: r for r in rows if r["pseudo"] == "aabzac"}
    theirs = {r["gameId"]: r for r in rows if r["pseudo"] != "aabzac"}
    rating, seat = {}, {}
    for agent, (_, manifest) in AGENTS.items():
        m = json.load(open(os.path.join("docs", "data", manifest), encoding="utf-8"))
        for g in m["games"]:
            rating[str(g["gameId"])] = g["opponent"]["ratingAtFetch"]
            seat[str(g["gameId"])] = g["seat"]
    tr_ours, tr_opp = {}, {}
    for agent, (upload, _) in AGENTS.items():
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
        games.append({"gid": gid, "r": r, "o": o, "result": result, "how": how, "turns": turns, "rating": rating[gid], "seat": seat[gid]})

    print("1. Outcomes of the %d games (v12, Gold uploads 1 and 2):" % len(games))
    for (res, how), n in sorted(Counter((g["result"], g["how"]) for g in games).items()):
        print("   %-5s %-8s %3d" % (res, how, n))
    print("   games reaching turn 250: %d" % sum(1 for g in games if g["turns"] >= 250))

    print("\n2. Score by the opponent's rating at fetch and by seat (points, ties 1/2):")
    pts = lambda g: 1.0 if g["result"] == "won" else 0.5 if g["result"] == "draw" else 0.0
    for lo, hi in ((0, 14), (14, 17), (17, 20), (20, 23), (23, 99)):
        gs = [g for g in games if lo <= g["rating"] < hi]
        if gs:
            print("   opponents %2d-%2d: %3d games, %5.1f points, %3.0f %%" % (lo, hi, len(gs), sum(map(pts, gs)), 100 * sum(map(pts, gs)) / len(gs)))
    for s in (0, 1):
        gs = [g for g in games if g["seat"] == s]
        print("   seat %d: %d games, %.0f %%" % (s, len(gs), 100 * sum(map(pts, gs)) / len(gs)))
    strong = [g for g in games if g["rating"] >= 20]
    print("   against 20 and above (%d games): %s" % (len(strong), dict(Counter((g["result"], g["how"]) for g in strong))))

    print("\n3. HP races at turn 250: our HP minus the enemy's at the checkpoints (medians; means in brackets)")
    races = {res: [g for g in games if g["how"] == "limit" and g["result"] == res] for res in ("lost", "won")}
    for res, gs in races.items():
        parts = []
        for t in (25, 50, 75, 100, 150, 200):
            d = [num(g["r"]["hp_%d" % t]) - num(g["o"]["hp_%d" % t]) for g in gs]
            parts.append("t%d %+.0f (%+.1f)" % (t, med(d), mean(d)))
        fin = [num(g["r"]["score"]) - num(g["o"]["score"]) for g in gs]
        print("   %-4s %3d: %s; final %+.0f (%+.1f); within 5 HP %d, within 10 HP %d" % (res, len(gs), ", ".join(parts), med(fin), mean(fin),
              sum(1 for x in fin if abs(x) <= 5), sum(1 for x in fin if abs(x) <= 10)))
    print("   HP lost by source per phase in the races (means per game): ours to knights / to towers, theirs to our knights / to our towers")
    for res, gs in races.items():
        for a, b in PHASES:
            ok = mean([sum(t["kd"] for t in tr_ours[int(g["gid"])]["turns"] if a <= t["t"] < b) for g in gs])
            ot = mean([sum(t["td"] for t in tr_ours[int(g["gid"])]["turns"] if a <= t["t"] < b) for g in gs])
            ek = mean([sum(t["kd"] for t in tr_opp[int(g["gid"])]["turns"] if a <= t["t"] < b) for g in gs])
            et = mean([sum(t["td"] for t in tr_opp[int(g["gid"])]["turns"] if a <= t["t"] < b) for g in gs])
            print("   %-4s races, turns %3d-%3d: ours %4.1f / %3.1f, theirs %4.1f / %3.1f" % (res, a, b, ok, ot, ek, et))
    print("   What our queen was doing on the turns she lost HP to knights (sum of kd by command class, per game, means):")
    for res, gs in races.items():
        acc = Counter()
        for g in gs:
            turns = tr_ours[int(g["gid"])]["turns"]
            for t in turns:
                if t["kd"] <= 0:
                    continue
                ladder_done = t["T"] >= 6 and t["B"] >= 2 and t["M"] >= 2
                acc[command_class(t, ladder_done)] += t["kd"]
        print("   %-4s races: %s" % (res, ", ".join("%s %.1f" % (k, v / len(gs)) for k, v in acc.most_common())))

    print("\n4. Our queen's deaths: turns, and the last 30 turns")
    deaths = [g for g in games if g["how"] == "died"]
    print("   %d deaths at turns %s" % (len(deaths), sorted(int(g["r"]["deathTurn"]) for g in deaths)))
    kd = mean([sum(t["kd"] for t in tr_ours[int(g["gid"])]["turns"][-30:]) for g in deaths])
    td = mean([sum(t["td"] for t in tr_ours[int(g["gid"])]["turns"][-30:]) for g in deaths])
    print("   HP lost in the last 30 turns: %.1f to knights, %.1f to towers (means)" % (kd, td))
    acc = Counter()
    for g in deaths:
        for t in tr_ours[int(g["gid"])]["turns"][-30:]:
            if t["kd"] > 0:
                acc[command_class(t, t["T"] >= 6 and t["B"] >= 2 and t["M"] >= 2)] += t["kd"]
    print("   by command class (per death, means): %s" % ", ".join("%s %.1f" % (k, v / len(deaths)) for k, v in acc.most_common()))
    print("   opponent income at turn 100 / 150 (medians): %.1f / %.1f; ours %.1f / %.1f" % (
        med([num(g["o"]["inc_100"]) for g in deaths]), med([num(g["o"]["inc_150"]) for g in deaths]),
        med([num(g["r"]["inc_100"]) for g in deaths]), med([num(g["r"]["inc_150"]) for g in deaths])))
    print("   enemy knights alive at turn 100 / 150 (medians): %.1f / %.1f; giants trained by the opponent in %d of %d" % (
        med([num(g["o"]["knights_100"]) for g in deaths]), med([num(g["o"]["knights_150"]) for g in deaths]),
        sum(1 for g in deaths if num(g["o"]["trainG"]) > 0), len(deaths)))

    print("\n5. Medians by result, us / them:")
    keys = ["inc_50", "inc_100", "inc_150", "inc_200", "towers_100", "towers_200", "thp_100", "thp_200", "hp_50", "hp_100", "hp_200",
            "meanIncome", "maxIncome", "minesBuilt", "mineUpgrades", "towersBuilt", "maxTowers", "towerTopUps", "trainK", "trainG",
            "firstBarracks", "firstTower", "barracksBuiltK", "structuresLost"]
    for res in ("lost", "won"):
        gs = [g for g in games if g["result"] == res]
        print("   %s (%d games)" % (res, len(gs)))
        for k in keys:
            print("     %-14s %7.1f / %7.1f" % (k, med([num(g["r"][k]) for g in gs]), med([num(g["o"][k]) for g in gs])))
    print("   against 20 and above (%d games)" % len(strong))
    for k in ("inc_100", "maxIncome", "mineUpgrades", "minesBuilt", "towers_200", "thp_200", "towerTopUps", "trainK", "trainG"):
        print("     %-14s %7.1f / %7.1f" % (k, med([num(g["r"][k]) for g in strong]), med([num(g["o"][k]) for g in strong])))
    print("   opponents' openings: %s" % Counter(g["o"]["opening"] for g in games).most_common(6))
    print("   opponents trained giants in %d games, archers in %d" % (sum(1 for g in games if num(g["o"]["trainG"]) > 0),
                                                                   sum(1 for g in games if num(g["o"]["trainA"]) > 0)))

    print("\n6. The queens' turns (all 180 games): MOVE share, turns under knights, top-ups")
    for who, trs in (("ours", tr_ours), ("theirs", tr_opp)):
        moves = mean([sum(1 for t in v["turns"] if t["cmd"].startswith("MOVE")) / max(1, len(v["turns"])) for v in trs.values()])
        contact = mean([sum(1 for t in v["turns"] if t["k250"] > 0) for v in trs.values()])
        loss_per_contact = sum(t["kd"] for v in trs.values() for t in v["turns"] if t["k250"] > 0) / max(1, sum(1 for v in trs.values() for t in v["turns"] if t["k250"] > 0))
        under_fire = mean([sum(1 for t in v["turns"] if t["eTow"] > 0) for v in trs.values()])
        print("   %-6s MOVE on %.0f %% of turns; %.0f contact turns a game; %.2f HP to knights per contact turn; %.1f turns a game inside an enemy tower range" % (
            who, 100 * moves, contact, loss_per_contact, under_fire))

    print("\n6b. Contact turns (an enemy knight within 250) per phase in the races, ours / theirs (means per game)")
    for res, gs in races.items():
        parts = []
        for a, b in PHASES:
            co = mean([sum(1 for t in tr_ours[int(g["gid"])]["turns"] if a <= t["t"] < b and t["k250"] > 0) for g in gs])
            cp = mean([sum(1 for t in tr_opp[int(g["gid"])]["turns"] if a <= t["t"] < b and t["k250"] > 0) for g in gs])
            parts.append("%d-%d: %.1f / %.1f" % (a, b, co, cp))
        print("   %-4s races: %s" % (res, "; ".join(parts)))

    print("\n6c. Towers: count @ HP per tower at the checkpoints (medians), us / them")
    groups = [("lost", [g for g in games if g["result"] == "lost"]), ("won", [g for g in games if g["result"] == "won"]), ("vs 20+", strong)]
    for name, gs in groups:
        parts = []
        for t in (25, 50, 100, 150, 200):
            tu = med([num(g["r"]["towers_%d" % t]) for g in gs])
            tt = med([num(g["o"]["towers_%d" % t]) for g in gs])
            hu = med([num(g["r"]["thp_%d" % t]) / num(g["r"]["towers_%d" % t]) for g in gs if (num(g["r"]["towers_%d" % t]) or 0) > 0])
            ht = med([num(g["o"]["thp_%d" % t]) / num(g["o"]["towers_%d" % t]) for g in gs if (num(g["o"]["towers_%d" % t]) or 0) > 0])
            parts.append("t%d %.0f@%.0f / %.0f@%.0f" % (t, tu, hu, tt, ht))
        print("   %-6s %s" % (name, "; ".join(parts)))
    legend = list(csv.DictReader(open(os.path.join(study, "features-legend.csv"), encoding="utf-8")))
    parts = []
    for t in (25, 50, 100, 150, 200):
        tu = med([num(r["towers_%d" % t]) for r in legend])
        hu = med([num(r["thp_%d" % t]) / num(r["towers_%d" % t]) for r in legend if (num(r["towers_%d" % t]) or 0) > 0])
        parts.append("t%d %.0f@%.0f" % (t, tu, hu))
    print("   legend %s" % "; ".join(parts))
    print("   legend (%d rows of 88 games, 2026-10-28) medians: income at t100 %.0f, max income %.0f, mines built %.0f, upgrades %.0f, "
          "top-ups %.0f, knight trainings %.0f, game length %.0f; giants in %d rows, archers in %d; openings %s" % (
          len(legend), med([num(r["inc_100"]) for r in legend]), med([num(r["maxIncome"]) for r in legend]),
          med([num(r["minesBuilt"]) for r in legend]), med([num(r["mineUpgrades"]) for r in legend]),
          med([num(r["towerTopUps"]) for r in legend]), med([num(r["trainK"]) for r in legend]), med([num(r["turns"]) for r in legend]),
          sum(1 for r in legend if num(r["trainG"]) > 0), sum(1 for r in legend if num(r["trainA"]) > 0),
          Counter(r["opening"] for r in legend).most_common(4)))

    print("\n6d. The queens' turns by command class, share of all turns (means over games): v12 in Gold, its opponents, Legend players")
    legend_tr = {}
    for name in sorted(os.listdir(study)):
        if name.startswith("legend-") and name.endswith(".txt"):
            for gid, v in traces(os.path.join(study, name)).items():
                legend_tr[(gid, v["hdr"].group(2))] = v
    strong_ids = {int(g["gid"]) for g in strong}
    weak_ids = {int(g["gid"]) for g in games if g["rating"] < 17}
    sets = [("v12", list(tr_ours.values())), ("opponents 20+", [v for k, v in tr_opp.items() if k in strong_ids]),
            ("opponents <17", [v for k, v in tr_opp.items() if k in weak_ids]), ("legend (%d queen-games)" % len(legend_tr), list(legend_tr.values()))]
    for name, trs in sets:
        acc, n = Counter(), 0
        contact = kd = 0
        for v in trs:
            turns = v["turns"]
            n += len(turns)
            for t in turns:
                acc[command_class(t, t["T"] >= 6 and t["B"] >= 2 and t["M"] >= 2)] += 1
                if t["k250"] > 0:
                    contact += 1
                    kd += t["kd"]
        share = ", ".join("%s %.0f %%" % (k, 100.0 * c / max(1, n)) for k, c in acc.most_common())
        print("   %-24s turns/game %.0f; %s; contact turns %.1f %% of turns, %.2f HP to knights per contact turn" % (
            name, n / max(1, len(trs)), share, 100.0 * contact / max(1, n), kd / max(1, contact)))
    print("   (top-up = BUILD TOWER on the touched own tower; idle pump = the same with 6 towers, 2 barracks and 2 mines standing, our ladder's end)")

    print("\n6e. Contact episodes of our queen on turns 50-249 (runs of turns with an enemy knight within 250), by what she was"
          " doing on the last turn before the contact: walking to a site she did not touch, at an own tower (within 130 of its"
          " centre), or other; HP lost to knights per episode and per game (means)")
    def episodes(turns, lo=50, hi=250):
        out, cur, prev = [], None, None
        for t in turns:
            if t["t"] < lo or t["t"] >= hi:
                prev = t
                continue
            if t["k250"] > 0:
                if cur is None:
                    cur = {"kd": 0, "turns": 0, "before": prev, "warn": 0}
                    # warning turns: knights within 500 but none within 250 right before the contact
                    cur["warn"] = 0
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
                return "walking to a site"
        if 0 <= t["nearT"] <= 130:
            return "at an own tower"
        if t["cmd"].startswith("MOVE"):
            return "walking (MOVE)"
        return "other"

    for res, gs in races.items():
        acc = {}
        n_ep = 0
        for g in gs:
            for e in episodes(tr_ours[int(g["gid"])]["turns"]):
                c = before_class(e["before"])
                a = acc.setdefault(c, [0, 0])
                a[0] += 1
                a[1] += e["kd"]
                n_ep += 1
        print("   %-4s races (%d games, %d episodes): %s" % (res, len(gs), n_ep, "; ".join(
            "%s %d episodes, %.2f HP each, %.1f HP a game" % (c, a[0], a[1] / max(1, a[0]), a[1] / len(gs)) for c, a in sorted(acc.items(), key=lambda kv: -kv[1][1]))))
    # The warning: how many turns before the contact a knight was within 500 but none within 250 (lost races, walking episodes).
    warns = []
    for g in races["lost"]:
        turns = tr_ours[int(g["gid"])]["turns"]
        for i, t in enumerate(turns):
            if t["t"] < 50 or t["k250"] == 0 or (i > 0 and turns[i - 1]["k250"] > 0):
                continue
            if before_class(turns[i - 1] if i > 0 else None) != "walking to a site":
                continue
            w = 0
            j = i - 1
            while j >= 0 and turns[j]["k250"] == 0 and turns[j]["k500"] > 0:
                w += 1
                j -= 1
            warns.append(w)
    print("   lost races, walking episodes: turns of warning (a knight within 500, none within 250) before the contact: median %.0f, mean %.1f, none in %d of %d" % (
        statistics.median(warns), mean(warns), sum(1 for w in warns if w == 0), len(warns)))

    print("\n8. Checks of the designers' two main proposals on the 42 lost races (the rest of the game held fixed; a tie counts as a"
          " win on seat 0 only)")
    # (a) The midgame walk gate: HP lost in contact episodes that began while walking (turns 50-249).
    # (b) The first wave: HP lost to knights in the 20 turns from the first contact, against the won races' mean.
    def first_contact(turns):
        for t in turns:
            if t["k250"] > 0:
                return t["t"]
        return None

    def loss_k20(turns):
        c = first_contact(turns)
        if c is None:
            return 0.0, None
        return float(sum(t["kd"] for t in turns if c <= t["t"] < c + 20)), c

    def second_tower_turn(turns):
        for t in turns:
            if t["T"] >= 2:
                return t["t"]
        return None

    won_k20 = mean([loss_k20(tr_ours[int(g["gid"])]["turns"])[0] for g in races["won"]])
    rows_out = []
    for g in races["lost"]:
        turns = tr_ours[int(g["gid"])]["turns"]
        margin = num(g["o"]["score"]) - num(g["r"]["score"])  # how much we were behind
        need = margin if g["seat"] == 0 else margin + 1  # a tie wins only as player 0
        walk_kd = sum(e["kd"] for e in episodes(turns) if before_class(e["before"]) == "walking to a site")
        k20, contact = loss_k20(turns)
        fw_excess = max(0.0, k20 - won_k20)
        rows_out.append((g["gid"], g["seat"], margin, walk_kd, k20, fw_excess, contact, second_tower_turn(turns), g["rating"]))
    flips = lambda key: sum(1 for r in rows_out if key(r) >= (r[2] if r[1] == 0 else r[2] + 1))
    print("   won races' first-wave loss (20 turns from the contact), mean: %.1f HP" % won_k20)
    print("   lost races flipped if ALL walking-caught HP (turns 50-249) were saved: %d of 42; if HALF: %d" % (
        flips(lambda r: r[3]), flips(lambda r: r[3] / 2.0)))
    print("   lost races flipped if the first-wave loss fell to the won races' mean: %d of 42; to zero: %d" % (
        flips(lambda r: r[5]), flips(lambda r: r[4])))
    print("   both (all walking-caught HP and the first-wave excess): %d of 42" % flips(lambda r: r[3] + r[5]))
    print("   lost races within 5 HP: %d; of them with first-wave excess >= 5 HP: %d, with walking-caught HP >= 5: %d" % (
        sum(1 for r in rows_out if r[2] <= 5), sum(1 for r in rows_out if r[2] <= 5 and r[5] >= 5), sum(1 for r in rows_out if r[2] <= 5 and r[3] >= 5)))
    print("   per lost race: gid seat margin walkKd k20 fwExcess contact t2 rating")
    for r in sorted(rows_out, key=lambda r: r[2]):
        print("     %s %d %4.0f %5.0f %5.0f %5.1f %4s %4s %.1f" % (r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8]))
    # (c) The second tower's timing against the contact, all 115 races: loss in the 20 turns and the lost share by (t2 - contact).
    print("   second tower turn minus contact turn (all races with a contact): first-wave loss and lost share by bin")
    bins = {}
    for res, gs in races.items():
        for g in gs:
            turns = tr_ours[int(g["gid"])]["turns"]
            k20, c = loss_k20(turns)
            t2 = second_tower_turn(turns)
            if c is None or t2 is None:
                continue
            rel = t2 - c
            b = "<=-3" if rel <= -3 else "-2..-1" if rel < 0 else "0..2" if rel < 3 else ">=3"
            d = bins.setdefault(b, {"n": 0, "lost": 0, "k20": [], "strong": 0, "strongLost": 0})
            d["n"] += 1
            d["lost"] += res == "lost"
            d["k20"].append(k20)
            if g["rating"] >= 20:
                d["strong"] += 1
                d["strongLost"] += res == "lost"
    for b in ("<=-3", "-2..-1", "0..2", ">=3"):
        d = bins.get(b)
        if d:
            print("     t2-contact %-6s n %3d lost %2d (%.0f %%) first-wave loss mean %.1f; vs 20+: %d games, %d lost" % (
                b, d["n"], d["lost"], 100.0 * d["lost"] / d["n"], mean(d["k20"]), d["strong"], d["strongLost"]))
    # (d) How many top-ups the first tower gets before the second tower is started (turns between T=1 and T=2).
    pumps = []
    for g in games:
        turns = tr_ours[int(g["gid"])]["turns"]
        t1 = next((t["t"] for t in turns if t["T"] >= 1), None)
        t2 = second_tower_turn(turns)
        if t1 is None or t2 is None:
            continue
        n = sum(1 for t in turns if t1 < t["t"] < t2 and t["cmd"].startswith("BUILD") and t["cmd"].split()[2] == "TOWER" and int(t["cmd"].split()[1]) == t["touch"] and t["T"] == 1)
        pumps.append((n, t2 - t1))
    print("   first tower: top-ups before the second tower stands, median %.0f (mean %.1f); turns from the first to the second tower median %.0f (%d games)" % (
        med([float(p[0]) for p in pumps]), mean([float(p[0]) for p in pumps]), med([float(p[1]) for p in pumps]), len(pumps)))

    lb = json.load(open(os.path.join("docs", "data", "leaderboard-gold-2026-11-08.json"), encoding="utf-8"))
    print("\n7. The Gold ladder (%s): %d rows, %d eligible for promotion; scores at local ranks %s; aabzac %s" % (
        lb["fetched"], lb["filteredCount"], lb["eligibleRows"], lb["scoreAtLocalRank"], [(u["localRank"], u["score"]) for u in lb["aabzac"]]))


if __name__ == "__main__":
    main()
