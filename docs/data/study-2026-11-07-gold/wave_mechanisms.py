"""The headline findings of the first-wave replay study (2026-11-07), recomputed from the WaveTrace files.

Usage (from the repository root): python -B -I docs/data/study-2026-11-07-gold/wave_mechanisms.py [<waves dir>]
The waves directory (default replays/study/gold-2026-11-07/waves, gitignored) is written by
  ./gradlew waveTrace -Pagents=6857463,6858567 -Pout=replays/study/gold-2026-11-07/waves -Preplays="<the two Gold upload games dirs>"
Output: stdout (wave_mechanisms.txt beside this file). Races are the games reaching turn 250 with both queens scored.
Window: the 20 turns from the first contact (an enemy knight within 250 of our queen). Prints, for lost and won races:
- the first-wave loss to knights (the trace's -k summed over the window);
- a merged wave: a turn pair (t, t+1) in the window where a knight is within 250 at t and the enemy knights within 500
  grow by 2 or more at t+1 (a second group joining), and the loss from the first such turn on;
- the queen's forward distance at the contact (x for seat 0, 1920 - x for seat 1) and the split at its median;
- standing within reach: a BUILD of an own tower listed on that line, a knight within 250, the queen moving under 20
  (a top-up or the flee BUILD when kite() found no step), with the nearest knight under 155, and its loss.
"""
import csv
import os
import random
import re
import statistics
import sys

HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (-?\d+)/(-?\d+), scores (-?\d+)/(-?\d+), (\d+) turns\); starting HP (\d+); first contact turn (\d+)")
LINE = re.compile(r"^t\s*(\d+) q\(\s*(-?\d+),\s*(-?\d+)\) hp\s+(-?\d+) -k(\d+) -t(\d+) (\S+)\s+(\S*)\s*(\S*)\s+moved\s+(\d+) \| cov (\d+) \|(.*?)\| "
                  r"eK (\d+)/(\d+) hp\s+(\d+) near\s+(-?\d+) inTwr (\d+) \| dmg\s+(\d+) kill (\d+) \|")


def mean(xs):
    return sum(xs) / len(xs) if xs else float("nan")


def boot_diff(a, b, draws=2000, seed=20261107):
    rnd = random.Random(seed)
    diffs = sorted(mean([rnd.choice(a) for _ in a]) - mean([rnd.choice(b) for _ in b]) for _ in range(draws))
    return diffs[int(0.025 * draws)], diffs[int(0.975 * draws)]


def parse(path):
    lines = open(path, encoding="utf-8").read().splitlines()
    h = HDR.search(lines[0])
    seat, me, foe = int(h.group(2)), int(h.group(4)), int(h.group(5))
    s_me, s_foe, turns, contact = int(h.group(6)), int(h.group(7)), int(h.group(8)), int(h.group(10))
    rows = []
    for line in lines[2:]:
        m = LINE.match(line)
        if not m:
            continue
        towers = set(int(x.split(":")[0]) for x in m.group(12).split() if ":" in x)
        rows.append({"t": int(m.group(1)), "x": int(m.group(2)), "lossK": int(m.group(5)), "cmd": m.group(7),
                     "site": m.group(8), "type": m.group(9), "moved": int(m.group(10)), "towers": towers,
                     "k250": int(m.group(13)), "k500": int(m.group(14)), "near": int(m.group(16))})
    return {"seat": seat, "won": me < foe, "race": turns >= 250 and s_me >= 0 and s_foe >= 0, "contact": contact, "rows": rows}


def main(waves):
    games = []
    for name in sorted(os.listdir(waves)):
        if name.endswith(".txt"):
            g = parse(os.path.join(waves, name))
            if g["race"]:
                games.append(g)
    for g in games:
        w = [r for r in g["rows"] if g["contact"] <= r["t"] < g["contact"] + 20]
        g["loss"] = sum(r["lossK"] for r in w)
        merge = next((w[i]["t"] for i in range(len(w) - 1) if w[i]["k250"] >= 1 and w[i + 1]["k500"] - w[i]["k500"] >= 2), None)
        g["merged"] = merge is not None
        g["postMerge"] = sum(r["lossK"] for r in w if merge is not None and r["t"] >= merge)
        at = next(r for r in g["rows"] if r["t"] == g["contact"])
        g["fx"] = at["x"] if g["seat"] == 0 else 1920 - at["x"]
        stand = [r for r in w if r["cmd"] == "BUILD" and r["type"] == "TOWER" and r["site"].isdigit() and int(r["site"]) in r["towers"]
                 and r["k250"] >= 1 and r["moved"] < 20]
        g["standReach"] = sum(1 for r in stand if 0 <= r["near"] < 155)
        g["standLoss"] = sum(r["lossK"] for r in stand)
    lost = [g for g in games if not g["won"]]
    won = [g for g in games if g["won"]]
    print("%d races: %d lost, %d won" % (len(games), len(lost), len(won)))
    for key, label in (("loss", "first-wave loss to knights (20 turns)"), ("postMerge", "loss from the first merge on"),
                       ("standLoss", "loss while standing at an own tower within reach"), ("standReach", "standing turns with a knight under 155"),
                       ("fx", "forward distance at the contact")):
        a, b = [g[key] for g in lost], [g[key] for g in won]
        lo, hi = boot_diff(a, b)
        print("  %-48s lost %6.2f  won %6.2f  difference %+6.2f (95 %% CI %+.2f..%+.2f)" % (label, mean(a), mean(b), mean(a) - mean(b), lo, hi))
    print("  merged waves: lost %d of %d, won %d of %d" % (sum(g["merged"] for g in lost), len(lost), sum(g["merged"] for g in won), len(won)))
    print("  standing within reach in: lost %d of %d, won %d of %d" % (sum(g["standReach"] > 0 for g in lost), len(lost),
                                                                     sum(g["standReach"] > 0 for g in won), len(won)))
    cut = statistics.median(g["fx"] for g in games)
    print("Split at the median forward distance at the contact (%.0f):" % cut)
    for label, sel in (("forward half", [g for g in games if g["fx"] > cut]), ("back half", [g for g in games if g["fx"] <= cut])):
        l, w_ = [g for g in sel if not g["won"]], [g for g in sel if g["won"]]
        print("  %-12s %3d races (%d lost): first-wave loss lost %5.2f won %5.2f; merged waves %d" % (
            label, len(sel), len(l), mean([g["loss"] for g in l]), mean([g["loss"] for g in w_]), sum(g["merged"] for g in sel)))
    unmerged = [g for g in games if not g["merged"]]
    print("Unmerged races only: first-wave loss lost %.2f won %.2f" % (mean([g["loss"] for g in unmerged if not g["won"]]),
                                                                       mean([g["loss"] for g in unmerged if g["won"]])))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "replays/study/gold-2026-11-07/waves")
