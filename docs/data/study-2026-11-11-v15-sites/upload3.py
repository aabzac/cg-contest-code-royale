"""The addendum of the v15 measurement (2026-11-11, evening): v15's third Gold placement (agent 6873629, 23.76) through
the measures of measure.py, unchanged, beside v14's two placements and v15's first two; the band of opponents rated 22
and up, which holds 54 of its 90 games; its game against the Gold boss; its equal-score game and lost races.
measure.py's tables stay those of the first two placements: this script loads the third from its own probe directory
(probe.sh's last block) by pointing measure.AGENTS at its manifest for one more call of measure.load.

Usage (from the repository root): python -B -I docs/data/study-2026-11-11-v15-sites/upload3.py [<study output dir>]
The directory defaults to replays/study/gold-v15-2026-11-11 (probe-v14, probe, probe-u3). Output: stdout (upload3.txt
beside this file).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import measure  # noqa: E402
from measure import BANDS, GOLD_AGENTS, STATS, boot_diff, mean, med, stand  # noqa: E402

BOSS, TIE = 920054964, 920032727
# The twelve Gold uploads of the addendum, and tools/arena_compare.py's refit on them (2026-11-11, 20:32).
GOLD12 = GOLD_AGENTS + (6873629,)
SCALE3, RATING3 = 3.12, {"v14": 22.41, "v15": 22.15}


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else os.path.join("replays", "study", "gold-v15-2026-11-11")
    v14 = measure.load("v14", os.path.join(base, "probe-v14"))
    v15 = measure.load("v15", os.path.join(base, "probe"))
    measure.AGENTS["v15"] = {6873629: "arena-6873629-games-2026-11-11.json"}
    u3 = measure.load("v15", os.path.join(base, "probe-u3"))
    m = u3["meta"]

    print("1. v15 upload 3 (agent 6873629): %s; opponents' median %.2f" % (stand(m, list(m)), med([v["rating"] for v in m.values()])))
    for label, lo, hi in BANDS:
        ids = [k for k, v in m.items() if lo <= v["rating"] < hi]
        if ids:
            print("   %-9s %s" % (label, stand(m, ids)))
    for seat in (0, 1):
        print("   seat %d:   %s" % (seat, stand(m, [k for k, v in m.items() if v["seat"] == seat])))

    g14, g15, g3 = list(v14["meta"].values()), list(v15["meta"].values()), list(m.values())
    print("\n2. The measures: v14 (180 games) | v15 uploads 1-2 (180) | v15 upload 3 (90) | upload 3 less v14 (95 % interval of a bootstrap over games)")
    print("   | v15's three uploads (270), and less v14 (95 %)")
    for label, st, f in STATS:
        a, b, c, d = st(g14), st(g15), st(g3), st(g15 + g3)
        lo, hi = boot_diff(g14, g3, st)
        lo2, hi2 = boot_diff(g14, g15 + g3, st)
        print(("   %-86s " + f + " | " + f + " | " + f + " | %+.2f (%+.2f..%+.2f) | " + f + ", %+.2f (%+.2f..%+.2f)") % (
            label[:86], a, b, c, c - a, lo, hi, d, d - a, lo2, hi2))

    print("\n3. Opponents rated 22 and up")
    keys = ("... the third tower", "the share of contact turns from turn 50 under three or more, %", "HP lost to knights from turn 50, a game",
            "HP lost to knights before turn 50, a game", "the enemy queen's HP to our knights from turn 50, a game", "own towers at turn 100", "income at turn 100")
    for name, src in (("v14", v14), ("v15 uploads 1-2", v15), ("v15 upload 3", u3)):
        mm = src["meta"]
        ids = [k for k, v in mm.items() if v["rating"] >= 22]
        print("   %-16s %s" % (name, stand(mm, ids)))
        for key in keys:
            st, f = next((s, f) for l, s, f in STATS if l == key)
            print(("        %-66s " + f) % ("the third tower's links within 400" if key.startswith("...") else key, st([mm[k] for k in ids])))

    o, t, v = u3["ours"][BOSS], u3["theirs"][BOSS], m[BOSS]
    print("\n4. The game against the Gold boss, %d (the replay's arenaboss record: Empress Boss, agent 1886843, score %.2f): seat %d, start HP %d," % (
        BOSS, v["rating"], v["seat"], o[0]["hp"]))
    print("   end HP ours %d, theirs %d, %d turns, %s" % (o[-1]["hpAfter"], t[-1]["hpAfter"], len(o), "won" if v["won"] else "lost"))
    print("   HP to knights by 50 turns: ours %s; theirs %s" % (
        " | ".join(str(sum(r["kd"] for r in o if a <= r["t"] < a + 50)) for a in (0, 50, 100, 150, 200)),
        " | ".join(str(sum(r["kd"] for r in t if a <= r["t"] < a + 50)) for a in (0, 50, 100, 150, 200))))
    for turn in (25, 50, 75, 100, 150, 200, 249):
        ro = next((r for r in o if r["t"] == turn), None)
        rt = next((r for r in t if r["t"] == turn), None)
        if ro and rt:
            print("   turn %3d: ours HP %3d, towers %d, mines %d, barracks %d, income %d | theirs HP %3d, towers %d, mines %d, barracks %d, income %d | knights alive: theirs %d, ours %d" % (
                turn, ro["hp"], ro["T"], ro["M"], ro["B"], ro["income"], rt["hp"], rt["T"], rt["M"], rt["B"], rt["income"], ro["eKnights"], ro["oKnights"]))
    con = [r for r in o if r["k250"] > 0]
    print("   contact turns %d (HP %d), the first at turn %s; before turn 50: %d (HP %d)" % (
        len(con), sum(r["kd"] for r in con), con[0]["t"] if con else None, sum(1 for r in con if r["t"] < 50), sum(r["kd"] for r in con if r["t"] < 50)))
    w = [x for x in u3["waves"] if x["gid"] == BOSS]
    print("   knight releases: ours at turns %s; theirs at %s" % (
        ", ".join(str(x["t"]) for x in w if x["us"] == 1 and x["type"] == "K"), ", ".join(str(x["t"]) for x in w if x["us"] == 0 and x["type"] == "K")))

    o, t = u3["ours"][TIE], u3["theirs"][TIE]
    print("\n5. The equal-score game %d: seat %d, start HP %d, end HP ours %d, theirs %d, ranked %s" % (
        TIE, m[TIE]["seat"], o[0]["hp"], o[-1]["hpAfter"], t[-1]["hpAfter"], "a win" if m[TIE]["won"] else "a loss"))
    x = [q for q in m.values() if not q["won"] and q["race"]]
    races = sorted((u3["ours"][k][-1]["hpAfter"] - u3["theirs"][k][-1]["hpAfter"] for k, q in m.items() if not q["won"] and q["race"]), reverse=True)
    print("   margins of upload 3's lost races: %s" % races)
    print("   lost races behind at turn 50 in the knights' HP count: %d of %d; HP to knights before turn 50 ours %.1f, theirs %.1f" % (
        sum(1 for q in x if q["hpFirst"] > q["theirFirst"]), len(x), mean([q["hpFirst"] for q in x]), mean([q["theirFirst"] for q in x])))

    print("\n6. v14 and v15 by the opponent's band against the rating model, with the third upload: the logistic curve of tools/arena_compare.py at the")
    print("   scale refitted on the twelve Gold uploads, %.2f, and the pooled ratings v14 %.2f and v15 %.2f; SD of the expected count" % (SCALE3, RATING3["v14"], RATING3["v15"]))
    reg = json.load(open(os.path.join("docs", "data", "arena-uploads.json"), encoding="utf-8"))
    tot, at14 = {}, [0, 0, 0.0, 0.0]
    for u in reg["uploads"]:
        if u["league"] != "gold" or u["label"] not in RATING3 or u["agent"] not in GOLD12:
            continue
        for g in json.load(open(u["games"], encoding="utf-8"))["games"]:
            r = g["opponent"]["ratingAtFetch"]
            band = next(b for b, lo, hi in BANDS if lo <= r < hi)
            won = g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
            p = 1.0 / (1.0 + math.exp(-(RATING3[u["label"]] - r) / SCALE3))
            for key in ((u["label"], band), (u["label"], "all"), ("%s upload %d" % (u["label"], u["upload"]), band), ("%s upload %d" % (u["label"], u["upload"]), "all")):
                t = tot.setdefault(key, [0, 0, 0.0, 0.0])
                t[0] += 1
                t[1] += won
                t[2] += p
                t[3] += p * (1 - p)
            if u["label"] == "v15":
                p = 1.0 / (1.0 + math.exp(-(RATING3["v14"] - r) / SCALE3))
                at14[0] += 1
                at14[1] += won
                at14[2] += p
                at14[3] += p * (1 - p)
    cell = lambda t: "%3d games, won %3d (%2.0f %%), expected %5.1f, %+4.1f SD" % (t[0], t[1], 100.0 * t[1] / t[0], t[2], (t[1] - t[2]) / math.sqrt(t[3]))
    for name in ("v14", "v15", "v15 upload 1", "v15 upload 2", "v15 upload 3"):
        print("   %-13s %s" % (name, "; ".join("%s: %s" % (band, cell(tot[(name, band)])) for band, _, _ in BANDS if (name, band) in tot)))
    print("   the field v15 met in its three uploads, at v14's rating %.2f: %.1f wins expected of %d, v15 won %d (%+.1f SD of the expected count)" % (
        RATING3["v14"], at14[2], at14[0], at14[1], (at14[1] - at14[2]) / math.sqrt(at14[3])))


if __name__ == "__main__":
    main()
