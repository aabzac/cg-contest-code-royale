"""The contact episodes of our queen in the profiles of profile.sh (2026-11-10, "stand where the towers overlap"): by the
phase and the cover on their first turn, how many a game and what they cost, the warning before them (turns with a knight
within 500 and none within 250, and the turns of the gate's state), whether a better-covered spot lay within 180 and 300
of her on the first turn, and what she did on the turn before. It shows what the hold leaves: the first wave, and the
episodes she meets at a tower she is topping up or on her way to the spot.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-overlap/episodes.py <profile name> [...]
A profile name is a directory of replays/bench/exp-2026-11-10-overlap/profiles (v13-v13, HOLD_OVERLAP-300_IDLE_OVERLAP-1-v13, ...).
Output: stdout (episodes.txt beside this file).
"""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "study-2026-11-09-v13-gold"))
from probe_tables import by_game, read, turn_class  # noqa: E402
from v13_gold import mean  # noqa: E402

ROOT = os.path.join("replays", "bench", "exp-2026-11-10-overlap", "profiles")


def gate(r):
    return r["t"] >= 30 and r["T"] >= 2 and r["k500"] > 0 and r["k250"] == 0


def main():
    for name in sys.argv[1:]:
        turns = [r for r in read(os.path.join(ROOT, name, "turns.csv")) if r["us"] == 1]
        g = by_game(turns)
        n = float(len(g))
        eps = []
        for rs in g.values():
            i = 0
            while i < len(rs):
                if rs[i]["k250"] > 0 and (i == 0 or rs[i - 1]["k250"] == 0):
                    j, kd = i, 0
                    while j < len(rs) and rs[j]["k250"] > 0:
                        kd += rs[j]["kd"]
                        j += 1
                    warn, k = 0, i - 1
                    while k >= 0 and rs[k]["k500"] > 0 and rs[k]["k250"] == 0:
                        warn += 1
                        k -= 1
                    held, k = 0, i - 1
                    while k >= 0 and gate(rs[k]):
                        held += 1
                        k -= 1
                    eps.append({"t": rs[i]["t"], "cov": rs[i]["towersCov"], "kd": kd, "T": rs[i]["T"], "warn": warn, "gate": held,
                                "before": turn_class(rs[i - 1]) if i > 0 else "start", "best180": rs[i]["bestCov180"], "best300": rs[i]["bestCov300"]})
                    i = j
                else:
                    i += 1
        print("%s: %.1f episodes a game, %.1f HP a game" % (name, len(eps) / n, sum(e["kd"] for e in eps) / n))
        for label, sel in (("before turn 50", lambda e: e["t"] < 50), ("turn 50 on, cover 0-1", lambda e: e["t"] >= 50 and e["cov"] <= 1),
                           ("turn 50 on, cover 2", lambda e: e["t"] >= 50 and e["cov"] == 2), ("turn 50 on, cover 3+", lambda e: e["t"] >= 50 and e["cov"] >= 3)):
            x = [e for e in eps if sel(e)]
            if not x:
                continue
            print("   %-22s %4.1f a game, %4.1f HP a game (%.2f each); own towers %.1f; warning turns %.1f, the gate's turns %.1f (none before %2.0f %%);"
                  " a better-covered spot within 180 on %2.0f %%, within 300 on %2.0f %%; the turn before: %s" % (
                      label, len(x) / n, sum(e["kd"] for e in x) / n, mean([e["kd"] for e in x]), mean([e["T"] for e in x]), mean([e["warn"] for e in x]),
                      mean([e["gate"] for e in x]), 100 * mean([1 if e["gate"] == 0 else 0 for e in x]), 100 * mean([1 if e["best180"] > e["cov"] else 0 for e in x]),
                      100 * mean([1 if e["best300"] > e["cov"] else 0 for e in x]),
                      ", ".join("%s %.0f %%" % (k, 100.0 * c / len(x)) for k, c in Counter(e["before"] for e in x).most_common(4))))


if __name__ == "__main__":
    main()
