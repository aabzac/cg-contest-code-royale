"""Group comparison of replays/study/features-2026-10-30.csv (run from the repository root)."""
import csv
import glob
import json
import statistics as st
from collections import defaultdict

OURS = {6823071, 6823455, 6824680, 6826276, 6827094}
rows = list(csv.DictReader(open("replays/study/features-2026-10-30.csv", encoding="utf-8")))

rating = {}
for path in ["docs/data/replays-cg2-2026-10-28.json", "docs/data/replays-legend-top-2026-10-28.json"]:
    try:
        d = json.load(open(path, encoding="utf-8"))
    except FileNotFoundError:
        continue
    for g in d.get("games", []):
        for a in g.get("agents", []):
            rating[(int(g["gameId"]), int(a["index"]))] = a.get("ratingAtFetch")
for path in glob.glob("docs/data/arena-68*-games-2026-10-*.json"):
    m = json.load(open(path, encoding="utf-8"))
    for g in m["games"]:
        rating[(int(g["gameId"]), 1 - g["seat"])] = g["opponent"].get("ratingAtFetch")

ours_games = {int(r["gameId"]) for r in rows if int(r["agentId"]) in OURS}


def group(r):
    gid, f = int(r["gameId"]), r["file"]
    if int(r["agentId"]) in OURS:
        return "us"
    if gid in ours_games:
        return "opp-of-us"
    for lg in ("bronze", "silver", "gold"):
        if "/cg2/%s/" % lg in f:
            return "cg2-" + lg
    if "legend" in f:
        return "legend"
    return "other"


def won(r):
    return int(r["rank"]) < int(r["oppRank"])


def num(r, k):
    v = r.get(k, "")
    return float(v) if v not in ("", None) else None


KEYS = ["turns", "inc_25", "inc_50", "inc_100", "mines_50", "towers_50", "towers_100", "bk_50", "bk_100", "ba_100", "bg_100",
        "knights_50", "knights_100", "hp_100", "gold_50", "gold_100", "firstBarracks", "firstMine", "firstTower", "maxIncome",
        "meanIncome", "meanGold", "minesBuilt", "towersBuilt", "barracksBuiltK", "barracksBuiltA", "barracksBuiltG", "trainK", "trainA",
        "trainG", "maxTowers", "structuresLost"]


def summary(label, rs):
    out = [label, "n=%d" % len(rs), "win=%.0f%%" % (100.0 * sum(won(r) for r in rs) / max(1, len(rs)))]
    for k in KEYS:
        vals = [num(r, k) for r in rs]
        vals = [v for v in vals if v is not None and v >= 0]
        out.append("%s=%s" % (k, ("%.1f" % st.median(vals)) if vals else "-"))
    print(" ".join(out))


groups = defaultdict(list)
for r in rows:
    groups[group(r)].append(r)
for g in ("us", "opp-of-us", "cg2-bronze", "cg2-silver", "cg2-gold", "legend"):
    rs = groups[g]
    summary(g, rs)
    if g in ("us", "opp-of-us", "cg2-bronze", "cg2-silver", "cg2-gold"):
        summary(g + "/won", [r for r in rs if won(r)])
        summary(g + "/lost", [r for r in rs if not won(r)])
# Strong opponents of ours: rating >= 20 at fetch
strong = [r for r in groups["opp-of-us"] if (rating.get((int(r["gameId"]), int(r["seat"]))) or 0) >= 20]
summary("opp-of-us/rating>=20", strong)
summary("opp-of-us/rating>=20/won", [r for r in strong if won(r)])
topb = [r for r in groups["cg2-bronze"] if (rating.get((int(r["gameId"]), int(r["seat"]))) or 0) >= 24]
summary("cg2-bronze/rating>=24", topb)
