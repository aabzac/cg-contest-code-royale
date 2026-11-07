"""v11's 180 Gold placement games (study 2026-11-07): how they are lost, the HP races at turn 250, what the opponents
do, the results by the opponent's score, and the field's profile for a proxy validation.

Inputs (gitignored, rebuilt from the replays of v11's Gold uploads 3 and 4; commands in README.md):
  replays/study/gold-2026-11-07/features-v11-gold.csv     ./gradlew cgStudy over the two upload directories
  replays/study/gold-2026-11-07/ours-upload-0N.txt        ./gradlew -q deathTrace -Pagent=<ours> -Pall=1 (our queen)
  replays/study/gold-2026-11-07/opp-upload-0N.txt         the same with -Pfoe=1 (the opponent's queen)
Tracked inputs: docs/data/arena-<agent>-games-2026-11-07.json (seats, opponent scores) and the upload registry.
Usage (from the repository root): python -B -I docs/data/study-2026-11-07-gold/gold.py [--data <root holding replays/>]
Output: stdout (gold.txt beside this file).
"""
import csv
import json
import math
import os
import re
import statistics
import sys
from collections import Counter, defaultdict

sys.path.insert(0, "tools")
import arena_compare  # noqa: E402

AGENTS = {6857463: ("upload-03", "arena-6857463-games-2026-11-07.json"), 6858567: ("upload-04", "arena-6858567-games-2026-11-07.json")}
HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (-?\d+)/(-?\d+), scores (-?\d+)/(-?\d+), (\d+) turns\)")
LINE = re.compile(r"^t\s*(\d+) .* \| hp\s+(-?\d+) \(-(-?\d+)\) \| kd (-?\d+) td (-?\d+)")
CHECKS = (25, 50, 100, 150, 200, 249)
BRACKETS = [("below 14", None, 14), ("14-17", 14, 17), ("17-20", 17, 20), ("20 and above", 20, None)]


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else float("nan")


def mean(xs):
    xs = [x for x in xs if x is not None]
    return sum(xs) / len(xs) if xs else float("nan")


def traces(path):
    """game id -> {"hdr": match, "hp": {turn: hp after the turn}, "kd": {turn: loss}, "td": {turn: loss}}."""
    out, cur = {}, None
    for line in open(path, encoding="utf-8-sig"):
        h = HDR.search(line)
        if h:
            cur = {"hdr": h, "hp": {}, "kd": {}, "td": {}, "lines": []}
            out[int(h.group(1))] = cur
            continue
        m = LINE.match(line)
        if m and cur is not None:
            cur["lines"].append(line)
            t = int(m.group(1))
            cur["hp"][t] = int(m.group(2))
            cur["kd"][t] = int(m.group(4))
            cur["td"][t] = int(m.group(5))
    return out


CONTACT = re.compile(r"^t\s*(\d+) .* \| T(\d+) M\d+ B\d+ .*\| K250 (\d+) ")


def first_contact(g):
    """(turn, own towers, our knight loss in the next 20 turns, the enemy's loss to our knights then) at the first turn an
    enemy knight stands within 250 of our queen, from the raw trace lines; None without contact."""
    for line in g["ours"]["lines"]:
        m = CONTACT.match(line)
        if m and int(m.group(3)) > 0:
            t = int(m.group(1))
            ours = sum(v for k, v in g["ours"]["kd"].items() if t <= k < t + 20)
            theirs = sum(v for k, v in g["opp"]["kd"].items() if t <= k < t + 20)
            return t, int(m.group(2)), ours, theirs
    return None


def hp_at(tr, t):
    """The queen's HP after turn t (her last known HP before it if she died earlier)."""
    keys = [k for k in tr["hp"] if k <= t]
    return tr["hp"][max(keys)] if keys else None


def num(row, key):
    v = row.get(key, "")
    try:
        x = float(v)
    except ValueError:
        return None
    return None if x == -1 and key in ("deathTurn", "firstTowerLost") else x


def main(data):
    base = os.path.join(data, "replays", "study", "gold-2026-11-07")
    games = {}
    for agent, (up, manifest) in AGENTS.items():
        for g in json.load(open(os.path.join("docs/data", manifest), encoding="utf-8"))["games"]:
            g["agent"] = agent
            games[g["gameId"]] = g
        ours, opp = traces(os.path.join(base, "ours-%s.txt" % up)), traces(os.path.join(base, "opp-%s.txt" % up))
        for gid in ours:
            if gid in games:
                games[gid]["ours"], games[gid]["opp"] = ours[gid], opp.get(gid)
    feats = defaultdict(dict)
    for r in csv.DictReader(open(os.path.join(base, "features-v11-gold.csv"), encoding="utf-8")):
        feats[int(r["gameId"])]["us" if int(r["agentId"]) in AGENTS else "them"] = r

    # 1. Outcomes.
    kinds = Counter()
    for gid, g in games.items():
        me, foe = g["ranks"][g["seat"]], g["ranks"][1 - g["seat"]]
        s_me, s_foe = g["scores"][g["seat"]], g["scores"][1 - g["seat"]]
        turns = (g["frames"] - 1) // 2
        won = me < foe
        if s_foe < 0 or s_me < 0:
            kind = "the referee removed a player"
        elif turns >= 250:
            kind = "at turn 250" + (" (equal scores)" if s_me == s_foe else "")
        else:
            kind = "the enemy queen died" if won else "our queen died"
        g["kind"], g["won"], g["turns"] = kind, won, turns
        kinds[("won" if won else "lost", kind)] += 1
    print("1. Outcomes of the %d games:" % len(games))
    for (res, kind), n in sorted(kinds.items()):
        print("   %-4s %-34s %3d" % (res, kind, n))

    # 2. The HP races at turn 250 (both queens alive at the end).
    races = [g for g in games.values() if g["kind"].startswith("at turn 250") and g.get("ours") and g.get("opp")]
    print("\n2. HP races at turn 250 (%d games): our HP minus the enemy's, by turn (medians; means in brackets)" % len(races))
    for label, sel in (("lost", [g for g in races if not g["won"]]), ("won", [g for g in races if g["won"]])):
        cells = []
        for t in CHECKS:
            d = [hp_at(g["ours"], t) - hp_at(g["opp"], t) for g in sel if hp_at(g["ours"], t) is not None and hp_at(g["opp"], t) is not None]
            cells.append("t%d %+.0f (%+.1f)" % (t + (1 if t == 249 else 0), med(d), mean(d)))
        ahead200 = sum(1 for g in sel if hp_at(g["ours"], 200) > hp_at(g["opp"], 200))
        print("   %-5s %3d: %s; ahead at turn 200 in %d" % (label, len(sel), ", ".join(cells), ahead200))
    lost_races = [g for g in races if not g["won"]]
    for lo, hi in ((0, 50), (50, 100), (100, 150), (150, 200), (200, 250)):
        def lost(tr, key):
            return sum(v for t, v in tr[key].items() if lo <= t < hi)
        ours_k = [lost(g["ours"], "kd") for g in lost_races]
        ours_t = [lost(g["ours"], "td") for g in lost_races]
        theirs_k = [lost(g["opp"], "kd") for g in lost_races]
        theirs_t = [lost(g["opp"], "td") for g in lost_races]
        print("   lost races, turns %3d-%3d: we lose %4.1f HP to knights and %4.1f to towers; the enemy loses %4.1f to our knights"
              " and %4.1f to our towers (means)" % (lo, hi, mean(ours_k), mean(ours_t), mean(theirs_k), mean(theirs_t)))
    for label, sel in (("lost", lost_races), ("won", [g for g in races if g["won"]])):
        rows = [first_contact(g) for g in sel]
        rows = [r for r in rows if r]
        print("   %s races, the first contact (an enemy knight within 250): turn %.0f, own towers then %.0f (medians); in the"
              " next 20 turns we lose %.1f HP to knights and the enemy %.1f to ours (means); %d of %d games" % (
                  label, med([r[0] for r in rows]), med([r[1] for r in rows]), mean([r[2] for r in rows]), mean([r[3] for r in rows]),
                  len(rows), len(sel)))
    margins = sorted(g["scores"][1 - g["seat"]] - g["scores"][g["seat"]] for g in lost_races)
    print("   lost races, the final margin: median %.0f, within 10 HP in %d of %d; %s" % (med(margins), sum(m <= 10 for m in margins),
                                                                                         len(margins), margins))

    # 3. Deaths.
    deaths = [g for g in games.values() if g["kind"] == "our queen died" and g.get("ours")]
    last = []
    for g in deaths:
        end = max(g["ours"]["hp"])
        k = sum(v for t, v in g["ours"]["kd"].items() if t > end - 30)
        tw = sum(v for t, v in g["ours"]["td"].items() if t > end - 30)
        last.append((k, tw))
    print("\n3. Our queen's deaths (%d): median turn %.0f; in their last 30 turns she loses %.1f HP to knights and %.1f to"
          " towers (means)" % (len(deaths), med([g["turns"] for g in deaths]), mean([a for a, _ in last]), mean([b for _, b in last])))
    kills = [g for g in games.values() if g["kind"] == "the enemy queen died"]
    print("   enemy queens killed: %d, median turn %.0f" % (len(kills), med([g["turns"] for g in kills])))

    # 4. The opponents' play, in our wins and losses (cgStudy rows).
    print("\n4. The games by result (medians; us / them)")
    keys = ["inc_50", "inc_100", "inc_150", "inc_200", "mines_150", "towers_100", "towers_200", "thp_200", "knights_150",
            "hp_100", "hp_200", "meanIncome", "minesBuilt", "towersBuilt", "towerTopUps", "trainK"]
    for label, sel in (("lost", [g for g in games.values() if not g["won"]]), ("won", [g for g in games.values() if g["won"]])):
        rows = [(feats[g["gameId"]].get("us"), feats[g["gameId"]].get("them")) for g in sel]
        rows = [(u, t) for u, t in rows if u and t]
        print("   %s %d games" % (label, len(rows)))
        for k in keys:
            print("     %-12s %6.1f / %-6.1f" % (k, med([num(u, k) for u, _ in rows]), med([num(t, k) for _, t in rows])))
        print("     the opponent trained giants in %d, archers in %d" % (
            sum((num(t, "trainG") or 0) > 0 for _, t in rows), sum((num(t, "trainA") or 0) > 0 for _, t in rows)))
    allrows = [feats[g]["them"] for g in feats if "them" in feats[g]]
    print("   all opponents: giants in %d of %d games, archers in %d" % (sum((num(t, "trainG") or 0) > 0 for t in allrows), len(allrows),
                                                                      sum((num(t, "trainA") or 0) > 0 for t in allrows)))

    # 5. Results by the opponent's score at fetch, against the logistic model of arena_compare.py (Gold's own fit).
    report = arena_compare.compare(arena_compare.load("docs/data/arena-uploads.json"))
    gold = report["leagues"]["gold"]
    rating, scale = gold["labels"]["v11"]["performance"], gold["scale"]
    print("\n5. Results by the opponent's score (Gold scale %.2f; v11's performance %.2f; points, ties 1/2)" % (scale, rating))
    rows = defaultdict(lambda: [0, 0.0, 0.0])
    for g in games.values():
        s = g["opponent"].get("ratingAtFetch")
        if not isinstance(s, (int, float)):
            continue
        label = next(b for b, lo, hi in BRACKETS if (lo is None or s >= lo) and (hi is None or s < hi))
        r = rows[label]
        r[0] += 1
        r[1] += arena_compare.points(g)
        r[2] += 1 / (1 + math.exp(-(rating - s) / scale))
    for label, _, _ in BRACKETS:
        n, pts, exp = rows[label]
        if n:
            print("   %-13s %3d games  %5.1f points  %3.0f %%  expected %3.0f %%" % (label, n, pts, 100 * pts / n, 100 * exp / n))

    # 6. The field's profile, for the proxy validation.
    at_limit = sum(1 for g in games.values() if g["turns"] >= 250)
    starts = Counter(hp_at(g["ours"], 0) for g in games.values() if g.get("ours"))
    print("\n6. The Gold field against v11: %d of %d games reach turn 250 (%.0f %%); game length median %.0f; opponents' median"
          " income at turn 100 %.1f; giants in %.0f %% of the games" % (
              at_limit, len(games), 100.0 * at_limit / len(games), med([g["turns"] for g in games.values()]),
              med([num(t, "inc_100") for t in allrows]), 100.0 * sum((num(t, "trainG") or 0) > 0 for t in allrows) / max(1, len(allrows))))
    print("   starting HP spread (our queen after turn 0): %d distinct values" % len(starts))


if __name__ == "__main__":
    args = sys.argv[1:]
    main(args[args.index("--data") + 1] if "--data" in args else ".")
