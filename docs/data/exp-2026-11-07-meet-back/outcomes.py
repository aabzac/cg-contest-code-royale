"""How each game's outcome changes from v11 to an arm on the same seed and seat (the meet-back sweeps, 2026-11-07).

Usage (from the repository root):
  python -B -I docs/data/exp-2026-11-07-meet-back/outcomes.py <report directory> <arm file prefix> <opponent> [<opponent> ...]
The directory holds sweep.sh's reports (ctrl-<opponent>.json and <prefix>-<opponent>.json), e.g.
  python -B -I docs/data/exp-2026-11-07-meet-back/outcomes.py replays/bench/exp-2026-11-07-meet-back/dev DEFENCE_FWD-50 field
Classes of a game for the arm's side: kill (the enemy queen died), limit-win, limit-draw, limit-loss (decided at turn 250),
dead (our queen died), both (both died). Rows are v11's class, columns the arm's; each table has 2 games per map.
"""
import collections
import json
import os
import sys

CLASSES = ["kill", "limit-win", "limit-draw", "both", "limit-loss", "dead"]


def load(path):
    out = {}
    for g in json.load(open(path, encoding="utf-8"))["games"]:
        me, foe = g["aSeat"], 1 - g["aSeat"]
        s = g["score"]
        if g["turns"] < 250 or s[me] <= 0 or s[foe] <= 0:
            cls = "kill" if g["winner"] == me else "dead" if g["winner"] == foe else "both"
        else:
            cls = "limit-win" if g["winner"] == me else "limit-loss" if g["winner"] == foe else "limit-draw"
        out[(g["seed"], me)] = cls
    return out


def main():
    d, arm = sys.argv[1], sys.argv[2]
    for opp in sys.argv[3:]:
        base = load(os.path.join(d, "ctrl-%s.json" % opp))
        cand = load(os.path.join(d, "%s-%s.json" % (arm, opp)))
        t = collections.Counter((base[k], cand[k]) for k in base)
        print("%s against %s (rows: v11's outcome; columns: the arm's)" % (arm, opp))
        print("%-11s" % "" + "".join("%11s" % c for c in CLASSES) + "%8s" % "total")
        for a in CLASSES:
            print("%-11s" % a + "".join("%11d" % t[(a, b)] for b in CLASSES) + "%8d" % sum(t[(a, b)] for b in CLASSES))
        print("%-11s" % "total" + "".join("%11d" % sum(t[(a, b)] for a in CLASSES) for b in CLASSES))
        print()


if __name__ == "__main__":
    main()
