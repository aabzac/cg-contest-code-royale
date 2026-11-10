"""The profiles of profile.sh by where the contact episodes began (2026-11-10, "hold at the towers only"), with the
definitions of the arena measurement (docs/data/study-2026-11-10-v14-cover/cover.py):
  a contact turn   an enemy knight within 250 of our queen, turns from 50;
  an episode       a run of contact turns; begun "on a walk" when the turn before it was a BUILD on a site she did not
                   touch (to an own tower, or to a new site or mine), "at an own tower" when her nearest own tower was
                   within 130, "on open ground" otherwise;
  the gate's state turn 30 on, two own towers or more, an enemy knight within 500 and none within 250.
Per profile (200 maps on both seats): the result, our queen's deaths, the contact turns a game with the share under three
or more own towers, the HP she loses to knights from turn 50 a game; the gate's turns from turn 50 a game and what they
go to (a MOVE, a walk to an own tower she does not touch, a top-up of the tower she touches); then the episodes of every
kind as episodes a game, HP a game, HP each, the own towers covering her on the first turn and the HP sum of the
knights within 250 on it; and the episodes begun on open ground by what the turn before them was (cover.py's open_kind:
a MOVE with a knight within 500, which is the hold's spot off the towers; the upgrade of an own mine she touches; a new
structure on the free site she touches; other).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-open-ground/begun.py [<profile directory>]
The directory defaults to replays/bench/exp-2026-11-10-open-ground/profiles; v14's profiles come first, then the arms in
the order they were written. Output: stdout (begun.txt beside this file).
"""
import glob
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-10-v14-cover"))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-09-v13-gold"))
from cover import began, episodes, gate, open_kind  # noqa: E402
from probe_tables import by_game, read  # noqa: E402
from v13_gold import mean  # noqa: E402

KINDS = ("at an own tower", "on a walk to an own tower", "on open ground", "on a walk to a new site or mine")


def profile(d):
    turns = read(os.path.join(d, "turns.csv"))
    games = read(os.path.join(d, "games.csv"))
    ours = {gid: rows for (gid, us), rows in by_game(turns).items() if us == 1}
    n = float(len(ours))
    rows = [r for rs in ours.values() for r in rs]
    con = [r for r in rows if r["t"] >= 50 and r["k250"] > 0]
    gt = [r for r in rows if r["t"] >= 50 and gate(r)]
    eps = [e for rs in ours.values() for e in episodes(rs)]
    by = {k: [e for e in eps if began(e[1]) == k] for k in KINDS}
    by["all"] = eps
    kinds = {}
    for e in by["on open ground"]:
        kinds.setdefault(open_kind(e[1]), []).append(e)
    return {
        "win": 100.0 * sum(x["won"] for x in games) / len(games), "draw": 100.0 * sum(x["draw"] for x in games) / len(games),
        "dead": sum(1 for rs in ours.values() if rs[-1]["hpAfter"] <= 0),
        "contact": len(con) / n, "cov3": 100.0 * mean([1 if r["towersCov"] >= 3 else 0 for r in con]),
        "hp50": sum(r["kd"] for r in rows if r["t"] >= 50) / n,
        "gate": len(gt) / n, "gateMove": sum(1 for r in gt if r["cmd"] == "M") / n,
        "gateWalk": sum(1 for r in gt if r["cmd"] == "B" and r["touch"] != r["site"] and r["targetKind"] == "T") / n,
        "gateTop": sum(1 for r in gt if r["cmd"] == "B" and r["touch"] == r["site"] and r["touchKind"] == "T") / n,
        "ep": {k: (len(v) / n, sum(e[2] for e in v) / n, mean([e[2] for e in v]) if v else float("nan"),
                   mean([e[0]["towersCov"] for e in v]) if v else float("nan"), mean([e[0]["kHpSum250"] for e in v]) if v else float("nan"))
               for k, v in by.items()},
        "open": [(k, len(v) / n, sum(e[2] for e in v) / n, mean([e[2] for e in v]), mean([e[0]["towersCov"] for e in v]), mean([e[0]["kHpSum250"] for e in v]))
                 for k, v in sorted(kinds.items(), key=lambda kv: -len(kv[1]))],
    }


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-open-ground/profiles"
    dirs = [p for p in glob.glob(os.path.join(d, "*")) if os.path.exists(os.path.join(p, "games.csv"))]
    dirs.sort(key=lambda p: (not os.path.basename(p).startswith("v14-"), os.path.getmtime(os.path.join(p, "games.csv"))))
    print("Seeds 1 + 397 k, both seats. Episodes: a game / HP a game / HP each / own towers covering her on the first turn / HP sum of"
          " the knights within 250 on it.")
    for p in dirs:
        o = profile(p)
        print("\n%s: won %.1f %%, drawn %.1f %%, queen deaths %d; contact turns from turn 50 %.1f a game, %2.0f %% under three or more own"
              " towers; HP to knights from turn 50 %.2f a game" % (os.path.basename(p), o["win"], o["draw"], o["dead"], o["contact"], o["cov3"], o["hp50"]))
        print("   the gate's state from turn 50: %.1f turns a game; a MOVE %.1f, a walk to an own tower %.1f, a top-up of the touched tower %.1f"
              % (o["gate"], o["gateMove"], o["gateWalk"], o["gateTop"]))
        for k in KINDS + ("all",):
            e = o["ep"][k]
            print("   %-32s %5.2f a game  %5.2f HP a game  %5.2f HP each  cover %.2f  knights' HP %5.1f" % (k, e[0], e[1], e[2], e[3], e[4]))
        for k, a, hp, each, c, kh in o["open"]:
            print("   on open ground, the turn before was %s: %.2f a game  %.2f HP a game  %.2f HP each  cover %.2f  knights' HP %.1f" % (k, a, hp, each, c, kh))


if __name__ == "__main__":
    main()
