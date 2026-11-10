"""v13's Gold games (study 2026-11-09): the pin. How long the ladder gate holds the queen at her towers, what the ladder
has built by then, and how the games with a long hold end; v12 beside it on the same opponent band. Inputs and usage as
v13_gold.py (same directory); output pin.txt.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from v13_gold import load, mean, med, num, pts  # noqa: E402


def gate_state(t):
    """The ladder gate's state: turn 30 on, two own towers or more, an enemy knight within 500 and none within 250."""
    return t["t"] >= 30 and t["T"] >= 2 and t["k500"] > 0 and t["k250"] == 0


def near_state(t):
    """An enemy knight within 500 (the gate's state or the threat rules')."""
    return t["t"] >= 30 and t["k500"] > 0


def at(turns, t, key):
    row = next((x for x in turns if x["t"] == t), None)
    return None if row is None else row[key]


def structures(turns, upto):
    """New structures stood up to a turn: rises of the T, M and B counts."""
    n = 0
    for a, b in zip(turns, turns[1:]):
        if b["t"] > upto:
            break
        n += max(0, b["T"] - a["T"]) + max(0, b["M"] - a["M"]) + max(0, b["B"] - a["B"])
    return n


def longest_gap(turns, lo, hi):
    """The longest run of turns in [lo, hi) without a new structure (a rise of T, M or B)."""
    last, best = lo, 0
    for a, b in zip(turns, turns[1:]):
        if b["t"] < lo or b["t"] >= hi:
            continue
        if b["T"] > a["T"] or b["M"] > a["M"] or b["B"] > a["B"]:
            best = max(best, b["t"] - last)
            last = b["t"]
    end = min(hi, turns[-1]["t"] + 1)
    return max(best, end - last)


def no_income_turns(turns, lo=30):
    return sum(1 for t in turns if t["t"] >= lo and t["M"] == 0)


def main():
    arg = lambda k, d: sys.argv[sys.argv.index(k) + 1] if k in sys.argv else d
    docs = os.path.join("docs", "data")
    data = {"v12": load("v12", arg("--v12", os.path.join("replays", "study", "gold-v12-2026-11-08")), docs),
            "v13": load("v13", arg("--v13", os.path.join("replays", "study", "gold-v13-2026-11-09")), docs)}

    print("A. The ladder's progress, all games and the opponents rated 20-22 (means; games that reached the turn)")
    for band, lo, hi in (("all", 0, 99), ("20-22", 20, 22), ("below 20", 0, 20)):
        for n in ("v12", "v13"):
            gs = [g for g in data[n] if lo <= g["rating"] < hi]
            parts = []
            for t in (50, 75, 100, 150, 200):
                gg = [g for g in gs if at(g["ours"], t, "T") is not None]
                if not gg:
                    continue
                parts.append("t%d: T %.2f M %.2f B %.2f (T<=2 in %d of %d)" % (t, mean([at(g["ours"], t, "T") for g in gg]), mean([at(g["ours"], t, "M") for g in gg]),
                             mean([at(g["ours"], t, "B") for g in gg]), sum(1 for g in gg if at(g["ours"], t, "T") <= 2), len(gg)))
            print("   %-8s %s (%3d games, score %.0f %%, deaths %d): %s" % (band, n, len(gs), 100 * mean([pts(g) for g in gs]), sum(1 for g in gs if g["how"] == "died"), "; ".join(parts)))
            print("            structures stood by t100 %.1f, by t150 %.1f; second barracks by t100 in %d, by t150 in %d; knight trainings %.1f; turns with no mine from turn 30: %.1f; longest run without a new structure, turns 30-150: median %.0f" % (
                mean([structures(g["ours"], 100) for g in gs if g["turns"] > 100]), mean([structures(g["ours"], 150) for g in gs if g["turns"] > 150]),
                sum(1 for g in gs if (at(g["ours"], 100, "B") or 0) >= 2), sum(1 for g in gs if (at(g["ours"], 150, "B") or 0) >= 2),
                mean([num(g["r"]["trainK"]) for g in gs]), mean([no_income_turns(g["ours"]) for g in gs]),
                med([float(longest_gap(g["ours"], 30, 150)) for g in gs if g["turns"] > 60])))

    print("\nB. Turns 30-150 with an enemy knight within 500 of our queen (share of those turns), and the result")
    for n in ("v12", "v13"):
        gs = [g for g in data[n] if g["turns"] > 60]
        rows = []
        for g in gs:
            span = [t for t in g["ours"] if 30 <= t["t"] < 150]
            rows.append((sum(1 for t in span if near_state(t)) / max(1, len(span)), g))
        for lo, hi in ((0, 0.25), (0.25, 0.4), (0.4, 0.55), (0.55, 0.7), (0.7, 1.01)):
            gg = [g for s, g in rows if lo <= s < hi]
            if gg:
                print("   %s share %.2f-%.2f: %3d games, score %3.0f %%, deaths %2d, lost races %2d; towers at t100 %.1f, mines at t100 %.1f, income at t100 %.1f / theirs %.1f, at t150 %.1f / %.1f; knight trainings %.1f / %.1f; mean rating %.1f" % (
                    n, lo, min(hi, 1.0), len(gg), 100 * mean([pts(g) for g in gg]), sum(1 for g in gg if g["how"] == "died"),
                    sum(1 for g in gg if g["how"] == "limit" and g["result"] == "lost"),
                    mean([at(g["ours"], 100, "T") for g in gg if at(g["ours"], 100, "T") is not None]),
                    mean([at(g["ours"], 100, "M") for g in gg if at(g["ours"], 100, "M") is not None]),
                    mean([num(g["r"]["inc_100"]) for g in gg]), mean([num(g["o"]["inc_100"]) for g in gg]),
                    mean([num(g["r"]["inc_150"]) for g in gg]), mean([num(g["o"]["inc_150"]) for g in gg]),
                    mean([num(g["r"]["trainK"]) for g in gg]), mean([num(g["o"]["trainK"]) for g in gg]), mean([g["rating"] for g in gg])))

    print("\nC. v13: the games where the ladder stalls (no new structure for 60 turns or more inside turns 30-150), one line each")
    print("   gid seat rating opp result | longest run | T M B at t50, t100, t150 | income us/them t100, t150 | trainings us/them | hold turns (gate state) 30-150 | no-mine turns | their giants")
    stall = [g for g in data["v13"] if g["turns"] > 90 and longest_gap(g["ours"], 30, 150) >= 60]
    for g in sorted(stall, key=lambda g: -longest_gap(g["ours"], 30, 150)):
        tr = g["ours"]
        f = lambda t: "%s/%s/%s" % tuple("-" if at(tr, t, k) is None else at(tr, t, k) for k in ("T", "M", "B"))
        fi = lambda k: "%s/%s" % ("-" if num(g["r"][k]) is None else "%.0f" % num(g["r"][k]), "-" if num(g["o"][k]) is None else "%.0f" % num(g["o"][k]))
        print("   %s %d %.1f %-14s %-4s %-6s | %3d | %s %s %s | %s %s | %.0f/%.0f | %3d | %3d | G%.0f" % (
            g["gid"], g["seat"], g["rating"], g["opp"][:14], g["result"], g["how"], longest_gap(tr, 30, 150), f(50), f(100), f(150), fi("inc_100"), fi("inc_150"),
            num(g["r"]["trainK"]), num(g["o"]["trainK"]), sum(1 for t in tr if 30 <= t["t"] < 150 and gate_state(t)), no_income_turns(tr), num(g["o"]["trainG"])))
    for n in ("v12", "v13"):
        gs = [g for g in data[n] if g["turns"] > 90]
        st = [g for g in gs if longest_gap(g["ours"], 30, 150) >= 60]
        ok = [g for g in gs if longest_gap(g["ours"], 30, 150) < 60]
        print("   %s: stalled %d games, score %.0f %% (deaths %d, lost races %d, mean rating %.1f); the rest %d games, score %.0f %% (deaths %d, mean rating %.1f)" % (
            n, len(st), 100 * mean([pts(g) for g in st]) if st else float("nan"), sum(1 for g in st if g["how"] == "died"),
            sum(1 for g in st if g["how"] == "limit" and g["result"] == "lost"), mean([g["rating"] for g in st]) if st else float("nan"),
            len(ok), 100 * mean([pts(g) for g in ok]), sum(1 for g in ok if g["how"] == "died"), mean([g["rating"] for g in ok])))

    print("\nD. Turns with no own mine (from turn 30), and the result")
    for n in ("v12", "v13"):
        gs = data[n]
        for lo, hi in ((0, 1), (1, 20), (20, 50), (50, 100), (100, 999)):
            gg = [g for g in gs if lo <= no_income_turns(g["ours"]) < hi]
            if gg:
                print("   %s no-mine turns %3d-%3d: %3d games, score %3.0f %%, deaths %2d, lost races %2d, mean rating %.1f; gold mined ours / theirs %.0f / %.0f" % (
                    n, lo, hi - 1, len(gg), 100 * mean([pts(g) for g in gg]), sum(1 for g in gg if g["how"] == "died"),
                    sum(1 for g in gg if g["how"] == "limit" and g["result"] == "lost"), mean([g["rating"] for g in gg]),
                    mean([num(g["r"]["meanIncome"]) * g["turns"] for g in gg]), mean([num(g["o"]["meanIncome"]) * g["turns"] for g in gg])))


if __name__ == "__main__":
    main()
