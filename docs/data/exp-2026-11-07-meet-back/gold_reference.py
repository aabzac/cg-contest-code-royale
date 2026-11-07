"""The Gold reference row of the local first-wave check (2026-11-07): LocalProfile's first-wave measures over the WaveTrace files
of a Gold upload pair (all games with a contact, not only the HP races): v11's two placements by default, v12's in
replays/study/gold-v12-2026-11-07/waves (the arena check).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-07-meet-back/gold_reference.py [<waves dir>]
The waves directory (default replays/study/gold-2026-11-07/waves, gitignored) is written by the study's waveTrace command
(docs/data/study-2026-11-07-gold/wave_mechanisms.py names it). The measures are those of cr.tools.LocalProfile.firstWave:
- the contact: the first turn an enemy knight stands within 250 of our queen;
- her distance from her back edge then (seat 0 starts on the left);
- the loss to knights in the 20 turns from the contact;
- a merged wave: a knight within 250 on one turn and 2 or more new enemy knights within 500 on the next.
Output: stdout.
"""
import importlib.util
import os
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("wave_mechanisms", os.path.join(HERE, "..", "study-2026-11-07-gold", "wave_mechanisms.py"))
wm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wm)


def mean(xs):
    return sum(xs) / len(xs) if xs else float("nan")


def main(waves):
    games = []
    for name in sorted(os.listdir(waves)):
        if not name.endswith(".txt"):
            continue
        g = wm.parse(os.path.join(waves, name))
        if g["contact"] < 0:
            continue
        w = [r for r in g["rows"] if g["contact"] <= r["t"] < g["contact"] + 20]
        at = next(r for r in g["rows"] if r["t"] == g["contact"])
        games.append({"contact": g["contact"], "fx": at["x"] if g["seat"] == 0 else 1920 - at["x"],
                      "loss": sum(r["lossK"] for r in w),
                      "merged": any(w[i]["k250"] >= 1 and w[i + 1]["k500"] - w[i]["k500"] >= 2 for i in range(len(w) - 1))})
    beyond = [g for g in games if g["fx"] > 406]
    within = [g for g in games if g["fx"] <= 406]
    merged = [g for g in games if g["merged"]]
    print("Gold, %s: %d games with a contact; contact turn %s (median); our queen %s from her back edge then"
          " (median; mean %.0f; %d beyond 406); loss to knights in the 20 turns from it %.1f (mean); merged waves in %d (loss %.1f"
          " against %.1f unmerged); beyond 406 the loss is %.1f (%d games), within 406 %.1f (%d games)" % (
              waves, len(games), statistics.median(g["contact"] for g in games), statistics.median(g["fx"] for g in games),
              mean([g["fx"] for g in games]), len(beyond), mean([g["loss"] for g in games]), len(merged),
              mean([g["loss"] for g in merged]), mean([g["loss"] for g in games if not g["merged"]]),
              mean([g["loss"] for g in beyond]), len(beyond), mean([g["loss"] for g in within]), len(within)))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "replays/study/gold-2026-11-07/waves")
