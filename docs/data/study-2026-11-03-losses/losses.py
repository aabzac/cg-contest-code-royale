"""How v04 (the `route` configuration) loses its 120 placement games, and what the opening order and
tower top-ups look like in our games and in stronger players' games.

Run from the repository root: python -B -I docs/data/study-2026-11-03-losses/losses.py
Input: replays/study/features-2026-11-03.csv (./gradlew cgStudy, see README.md); the cg2 manifest
docs/data/replays-cg2-2026-10-28.json for the opponents' ratings.
"""
import csv
import json
import statistics as st
from collections import Counter, defaultdict

ROUTE = {6836411, 6836589}
V03 = {6827580, 6828013, 6829912}
rows = list(csv.DictReader(open("replays/study/features-2026-11-03.csv", encoding="utf-8")))
by = defaultdict(dict)
for r in rows:
    by[r["gameId"]][int(r["seat"])] = r

rating = {}
d = json.load(open("docs/data/replays-cg2-2026-10-28.json", encoding="utf-8"))
for g in d["games"]:
    for a in g["agents"]:
        rating[(int(g["gameId"]), int(a["index"]))] = a.get("ratingAtFetch")


def f(r, k):
    return float(r[k]) if r[k] != "" else None


def won(r):
    return int(r["rank"]) < int(r["oppRank"])


def group(r):
    gid, fl = int(r["gameId"]), r["file"]
    if int(r["agentId"]) in ROUTE:
        return "route"
    if int(r["agentId"]) in V03:
        return "v03"
    if "/cg2/bronze/" in fl:
        rt = rating.get((gid, int(r["seat"])))
        return "cg2-bronze-24+" if rt is not None and rt >= 24 else "cg2-bronze"
    if "/cg2/silver/" in fl or "/cg2/gold/" in fl:
        return "silver-gold"
    if "legend" in fl:
        return "legend"
    return "opp"


def med(xs):
    xs = [x for x in xs if x is not None]
    return "%.1f" % st.median(xs) if xs else "-"


def pct(n, d):
    return "%.0f%%" % (100.0 * n / d) if d else "-"


pairs = [(r, by[r["gameId"]][1 - int(r["seat"])]) for r in rows if int(r["agentId"]) in ROUTE]
print("route games", len(pairs), "wins", sum(won(m) for m, o in pairs))

# 1. How the games end.
print("\n== 1. How the 120 games end ==")
for name, sel in (("wins", [p for p in pairs if won(p[0])]), ("losses", [p for p in pairs if not won(p[0])])):
    killed = [m for m, o in sel if f(m, "deathTurn") is not None and f(m, "deathTurn") >= 0]
    oppkilled = [o for m, o in sel if f(o, "deathTurn") is not None and f(o, "deathTurn") >= 0]
    limit = [m for m, o in sel if int(m["turns"]) >= 250]
    removed = [m for m, o in sel if f(o, "score") == -1 or f(m, "score") == -1]
    T = [f(m, "deathTurn") for m in killed]
    print("%s %d: our queen killed %d (death turn median %s, quartiles %s), opponent's queen killed %d, turn limit %d, referee removals %d"
          % (name, len(sel), len(killed), med(T), [int(x) for x in st.quantiles(T, n=4)] if len(T) > 3 else T, len(oppkilled), len(limit), len(removed)))
losses = [p for p in pairs if not won(p[0])]
print("losses by seat:", Counter(int(m["seat"]) for m, o in losses), "games by seat:", Counter(int(m["seat"]) for m, o in pairs))
print("losses at the limit, HP ours vs theirs:", [(int(f(m, "score")), int(f(o, "score"))) for m, o in losses if int(m["turns"]) >= 250])

# 2. Opponent style.
print("\n== 2. Opponent style (what the opponent trained) ==")
def style(o):
    k, a, g = int(o["trainK"]) > 0, int(o["trainA"]) > 0, int(o["trainG"]) > 0
    return ("K" if k else "") + ("A" if a else "") + ("G" if g else "") or "none"
sty = defaultdict(lambda: [0, 0])
for m, o in pairs:
    sty[style(o)][0] += 1
    sty[style(o)][1] += won(m)
for k, (n, w) in sorted(sty.items(), key=lambda kv: -kv[1][0]):
    print("  %-5s %3d games, we win %s" % (k, n, pct(w, n)))
giant = [(m, o) for m, o in pairs if int(o["trainG"]) > 0]
print("against giants: our towers lost (median) %s, first tower lost turn %s; in other games %s and %s" % (
    med([f(m, "structuresLost") for m, o in giant]), med([f(m, "firstTowerLost") for m, o in giant if f(m, "firstTowerLost") >= 0]),
    med([f(m, "structuresLost") for m, o in pairs if int(o["trainG"]) == 0]),
    med([f(m, "firstTowerLost") for m, o in pairs if int(o["trainG"]) == 0 and f(m, "firstTowerLost") >= 0])))

# 3. Trajectories in wins and losses.
print("\n== 3. Medians at fixed turns: ours in losses / wins, opponent's in our losses / wins ==")
for k in ("hp", "towers", "knights", "inc", "gold", "thp"):
    line = "%-8s" % k
    for t in (25, 50, 75, 100):
        key = "%s_%d" % (k, t)
        a = [f(m, key) for m, o in losses]
        b = [f(m, key) for m, o in pairs if won(m)]
        c = [f(o, key) for m, o in losses]
        dd = [f(o, key) for m, o in pairs if won(m)]
        line += " | t%d: %s/%s vs %s/%s" % (t, med(a), med(b), med(c), med(dd))
    print(line)
print("(empty medians: the game was over before that turn)")

# 4. Opening order: us, the opponents who beat us, stronger groups.
print("\n== 4. Opening: the first three structures, and the first build turns ==")
groups = defaultdict(list)
for r in rows:
    groups[group(r)].append(r)
groups["opp-beat-route"] = [o for m, o in losses]
groups["opp-lost-to-route"] = [o for m, o in pairs if won(m)]
for g in ("route", "v03", "opp-beat-route", "opp-lost-to-route", "cg2-bronze", "cg2-bronze-24+", "silver-gold", "legend"):
    rs = groups[g]
    if not rs:
        continue
    op = Counter(r["opening"] for r in rs)
    first = Counter(r["opening"].split(" ")[0] if r["opening"] else "-" for r in rs)
    print("%-18s n=%3d first: %s | top openings: %s | first barracks %s, mine %s, tower %s | top-ups %s, upgrades %s, towers built %s, max towers %s"
          % (g, len(rs), ", ".join("%s %s" % (k, pct(v, len(rs))) for k, v in first.most_common(3)),
             ", ".join("%s %s" % (k, pct(v, len(rs))) for k, v in op.most_common(4)),
             med([f(r, "firstBarracks") for r in rs if f(r, "firstBarracks") >= 0]), med([f(r, "firstMine") for r in rs if f(r, "firstMine") >= 0]),
             med([f(r, "firstTower") for r in rs if f(r, "firstTower") >= 0]),
             med([f(r, "towerTopUps") for r in rs]), med([f(r, "mineUpgrades") for r in rs]), med([f(r, "towersBuilt") for r in rs]), med([f(r, "maxTowers") for r in rs])))

# 5. Tower top-ups: how many turns we spend, and the tower HP we hold, against stronger groups.
print("\n== 5. Tower top-ups and tower HP ==")
for g in ("route", "opp-beat-route", "cg2-bronze-24+", "silver-gold", "legend"):
    rs = groups[g]
    if not rs:
        continue
    print("%-16s top-ups per game median %s mean %.1f (per 100 turns %.1f); summed tower HP at t50 %s, t100 %s, t150 %s; towers at t50 %s, t100 %s"
          % (g, med([f(r, "towerTopUps") for r in rs]), st.mean(f(r, "towerTopUps") for r in rs),
             100.0 * sum(f(r, "towerTopUps") for r in rs) / sum(f(r, "turns") for r in rs),
             med([f(r, "thp_50") for r in rs]), med([f(r, "thp_100") for r in rs]), med([f(r, "thp_150") for r in rs]),
             med([f(r, "towers_50") for r in rs]), med([f(r, "towers_100") for r in rs])))
rt = groups["route"]
print("route: top-ups in wins %s, in losses %s; HP per tower at t50 in wins %s, losses %s"
      % (med([f(r, "towerTopUps") for r in rt if won(r)]), med([f(r, "towerTopUps") for r in rt if not won(r)]),
         med([f(r, "thp_50") / f(r, "towers_50") for r in rt if won(r) and f(r, "towers_50")]),
         med([f(r, "thp_50") / f(r, "towers_50") for r in rt if not won(r) and f(r, "towers_50")])))

# 6. Our result by what we and they held at turn 50.
print("\n== 6. Results by towers and knights at turn 50 ==")
def table(name, key, buckets, who=0):
    out = defaultdict(lambda: [0, 0])
    for m, o in pairs:
        x = f(m if who == 0 else o, key)
        if x is None:
            continue
        b = next(lbl for lbl, lo, hi in buckets if lo <= x < hi)
        out[b][0] += 1
        out[b][1] += won(m)
    print(name, " | ".join("%s: %d games, we win %s" % (l, out[l][0], pct(out[l][1], out[l][0])) for l, _, _ in buckets if out[l][0]))
B = [("0", 0, 1), ("1", 1, 2), ("2", 2, 3), ("3", 3, 4), ("4+", 4, 99)]
table("our towers at t50:", "towers_50", B)
table("opponent towers at t50:", "towers_50", B, who=1)
table("opponent knights alive at t50:", "knights_50", [("0", 0, 1), ("1-3", 1, 4), ("4-7", 4, 8), ("8+", 8, 99)], who=1)
table("our queen HP at t50:", "hp_50", [("<30", 0, 30), ("30-49", 30, 50), ("50-69", 50, 70), ("70+", 70, 200)])
table("opponent income at t50:", "inc_50", [("0-2", 0, 3), ("3-5", 3, 6), ("6-8", 6, 9), ("9+", 9, 99)], who=1)

# 7. Economy and army by group: what stronger players hold and train.
print("\n== 7. Economy and army by group (medians) ==")
print("%-16s %4s | %s | %s | %s" % ("group", "n", "t50: inc mines towers barr knights hp gold", "t100: inc mines towers barr knights hp gold", "per game: trainK trainA trainG meanInc meanGold minesBuilt turns"))
for g in ("route", "v03", "opp-beat-route", "opp-lost-to-route", "cg2-bronze", "cg2-bronze-24+", "silver-gold", "legend"):
    rs = groups[g]
    if not rs:
        continue
    def at(t):
        return " ".join(med([f(r, "%s_%d" % (k, t)) for r in rs]) for k in ("inc", "mines", "towers")) + " " + \
            med([(f(r, "bk_%d" % t) or 0) + (f(r, "ba_%d" % t) or 0) + (f(r, "bg_%d" % t) or 0) if f(r, "bk_%d" % t) is not None else None for r in rs]) + " " + \
            " ".join(med([f(r, "%s_%d" % (k, t)) for r in rs]) for k in ("knights", "hp", "gold"))
    print("%-16s %4d | %s | %s | %s" % (g, len(rs), at(50), at(100),
          " ".join(med([f(r, k) for r in rs]) for k in ("trainK", "trainA", "trainG", "meanIncome", "meanGold", "minesBuilt", "turns"))))

# 8. Our wins by a kill: what we trained; our losses by a kill: what the opponent held.
print("\n== 8. Kills ==")
kw = [(m, o) for m, o in pairs if won(m) and f(o, "deathTurn") >= 0]
kl = [(m, o) for m, o in pairs if not won(m) and f(m, "deathTurn") >= 0]
print("wins by a kill %d: our trainK median %s, knights alive at t50 %s, opponent's towers at t50 %s, opponent's hp at t25/t50 %s/%s, death turn median %s"
      % (len(kw), med([f(m, "trainK") for m, o in kw]), med([f(m, "knights_50") for m, o in kw]), med([f(o, "towers_50") for m, o in kw]),
         med([f(o, "hp_25") for m, o in kw]), med([f(o, "hp_50") for m, o in kw]), med([f(o, "deathTurn") for m, o in kw])))
print("losses by a kill %d: opponent's trainK median %s, knights alive at t50/t75 %s/%s, giants trained in %d, our towers at t50 %s, our hp at t25/t50/t75 %s/%s/%s"
      % (len(kl), med([f(o, "trainK") for m, o in kl]), med([f(o, "knights_50") for m, o in kl]), med([f(o, "knights_75") for m, o in kl]),
         sum(1 for m, o in kl if int(o["trainG"]) > 0), med([f(m, "towers_50") for m, o in kl]),
         med([f(m, "hp_25") for m, o in kl]), med([f(m, "hp_50") for m, o in kl]), med([f(m, "hp_75") for m, o in kl])))
early = [(m, o) for m, o in kl if f(m, "deathTurn") < 80]
print("early kills (before turn 80) %d: opponent first barracks %s, trainK by then n/a, our towers at t25/t50 %s/%s, our hp at t25/t50 %s/%s"
      % (len(early), med([f(o, "firstBarracks") for m, o in early]), med([f(m, "towers_25") for m, o in early]), med([f(m, "towers_50") for m, o in early]),
         med([f(m, "hp_25") for m, o in early]), med([f(m, "hp_50") for m, o in early])))
