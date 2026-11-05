"""Do a series and its quiet repeat hold the same games? Compares winner, turns and final HP per (seed, seat).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-05-late-hold/same_games.py FIRST.json REPEAT.json [...pairs]
Prints, per pair, the games compared, those that differ, and each report's slow turns, timeouts and errors of both sides.
"""
import json
import sys


def games(path):
    d = json.load(open(path, encoding="utf-8"))
    return {(g["seed"], g["aSeat"]): g for g in d["games"]}, d


def failures(d, side):
    i = {"A": 0, "B": 1}[side]
    out = {}
    for k in ("slow", "timeouts", "errors"):
        out[k] = sum(1 for g in d["games"] if (g[k][g["aSeat"] if i == 0 else 1 - g["aSeat"]] not in (None, 0)))
    return out


def main(argv):
    for a, b in zip(argv[0::2], argv[1::2]):
        ga, da = games(a)
        gb, db = games(b)
        common = sorted(set(ga) & set(gb))
        diff = [k for k in common if (ga[k]["winner"], ga[k]["turns"], list(ga[k]["score"])) != (gb[k]["winner"], gb[k]["turns"], list(gb[k]["score"]))]
        print("%s vs %s: %d games in both, %d differ %s" % (a, b, len(common), len(diff), diff[:10]))
        print("   first:  candidate %s, opponent %s" % (failures(da, "A"), failures(da, "B")))
        print("   repeat: candidate %s, opponent %s" % (failures(db, "A"), failures(db, "B")))


if __name__ == "__main__":
    main(sys.argv[1:])
