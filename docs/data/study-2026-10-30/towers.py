"""Our 300 placement games (v01 uploads 1-3, v02 uploads 1-2): results against our and the opponents' towers, and how we lose.

Run from the repository root: python -B -I docs/data/study-2026-10-30/towers.py
Input: replays/study/features-2026-10-30.csv (./gradlew cgStudy, see README.md).
"""
import csv
import statistics as st
from collections import defaultdict

OURS = {6823071, 6823455, 6824680, 6826276, 6827094}
rows = list(csv.DictReader(open("replays/study/features-2026-10-30.csv", encoding="utf-8")))
by = defaultdict(dict)
for r in rows:
    by[r["gameId"]][int(r["seat"])] = r
us = [(r, d[1 - s]) for d in by.values() for s, r in d.items() if int(r["agentId"]) in OURS]


def f(r, k):
    return float(r[k]) if r[k] != "" else None


def won(r):
    return int(r["rank"]) < int(r["oppRank"])


def table(name, key, buckets, who=0):
    out = defaultdict(lambda: [0, 0])
    for me, op in us:
        x = f(me if who == 0 else op, key)
        if x is None:
            continue
        b = next(lbl for lbl, lo, hi in buckets if lo <= x < hi)
        out[b][0] += 1
        out[b][1] += won(me)
    print(name, " | ".join("%s: %d games, we win %.0f%%" % (l, out[l][0], 100.0 * out[l][1] / out[l][0]) for l, _, _ in buckets if out[l][0]))


print("games", len(us))
B = [("0", 0, 1), ("1", 1, 2), ("2", 2, 3), ("3+", 3, 99)]
table("our towers at turn 50:", "towers_50", B)
table("opponent towers at turn 50:", "towers_50", B, who=1)
table("our most towers:", "maxTowers", B)
table("our first tower:", "firstTower", [("never", -1, 0), ("<25", 0, 25), ("25-39", 25, 40), ("40+", 40, 999)])
table("opponent first tower:", "firstTower", [("never", -1, 0), ("<15", 0, 15), ("15-24", 15, 25), ("25+", 25, 999)], who=1)
lost = [(me, op) for me, op in us if not won(me)]
dead = [me for me, op in lost if f(me, "score") == 0]
print("losses", len(lost), "queen killed", len(dead), "lost on HP at turn 250", sum(1 for me, op in lost if (f(me, "score") or 0) > 0))
T = [int(me["turns"]) for me in dead]
print("turn of our queen's death: median %d, quartiles %s" % (st.median(T), [int(x) for x in st.quantiles(T, n=4)]))
for k in ("hp_50", "hp_75", "hp_100", "knights_50", "knights_75"):
    a = [f(me, k) for me, op in lost if f(me, k) is not None]
    b = [f(me, k) for me, op in us if won(me) and f(me, k) is not None]
    c = [f(op, k) for me, op in lost if f(op, k) is not None]
    d = [f(op, k) for me, op in us if won(me) and f(op, k) is not None]
    print("%-10s ours: lost %5.1f won %5.1f | opponent: in our losses %5.1f in our wins %5.1f" % (k, st.median(a), st.median(b), st.median(c), st.median(d)))
