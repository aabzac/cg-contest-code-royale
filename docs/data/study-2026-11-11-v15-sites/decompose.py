"""Where the contact turns stand, on the arena and in the local profiles (2026-11-11): why the share of the contact
turns under three or more own towers rose locally with SITE_NEAR=50 and did not on the arena.
A contact turn (an enemy knight within 250 of our queen, turns from 50) falls in one of five classes:
  no line      two own towers or fewer stand: no place under three exists on the board;
  off the line three or more stand, two or fewer cover her, and a spot under three or more lies within 300 of her;
  no spot      three or more stand, two or fewer cover her, and no such spot lies within 300;
  under three  exactly three cover her;
  under four   four or more cover her.
The rule moves a tower's site: it can turn "no spot" and "off the line" turns into covered ones and three into four;
it cannot act while two towers or fewer stand.
Sources: the arena probe files of probe.sh (v14's and v15's 180 Gold games) and the local profiles of the experiment
(docs/data/exp-2026-11-10-tower-sites/, 400 games each of v14 and of SITE_NEAR=50 against v14, rival and field; the
csv files are in the worktree's replays/bench/exp-2026-11-10-tower-sites/profiles only).

Usage (from the repository root):
  python -B -I docs/data/study-2026-11-11-v15-sites/decompose.py [<arena dir> [<profiles dir>]]
Defaults: replays/study/gold-v15-2026-11-11 and replays/bench/exp-2026-11-10-tower-sites/profiles. Output: stdout
(decompose.txt beside this file).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "study-2026-11-09-v13-gold"))
from probe_tables import by_game, read  # noqa: E402

CLASSES = ("no line", "off the line", "no spot", "under three", "under four")


def cls(r):
    if r["towersCov"] >= 4:
        return "under four"
    if r["towersCov"] == 3:
        return "under three"
    if r["T"] <= 2:
        return "no line"
    return "off the line" if r["bestCov300"] >= 3 else "no spot"


def profile(d):
    g = by_game(read(os.path.join(d, "turns.csv")))
    ours = [rs for (gid, us), rs in g.items() if us == 1]
    n = float(len(ours))
    turns = dict.fromkeys(CLASSES, 0)
    hp = dict.fromkeys(CLASSES, 0)
    games = dict.fromkeys(CLASSES, 0)
    dead = line = 0
    for rs in ours:
        seen = set()
        for r in rs:
            if r["t"] >= 50 and r["k250"] > 0:
                c = cls(r)
                turns[c] += 1
                hp[c] += r["kd"]
                seen.add(c)
        for c in seen:
            games[c] += 1
        dead += rs[-1]["hpAfter"] <= 0
        line += any(r["T"] >= 3 for r in rs if r["t"] <= 60)
    return {"n": n, "turns": {c: turns[c] / n for c in CLASSES}, "hp": {c: hp[c] / n for c in CLASSES}, "games": {c: 100.0 * games[c] / n for c in CLASSES},
            "dead": 100.0 * dead / n, "line60": 100.0 * line / n}


def show(label, p):
    total = sum(p["turns"].values())
    print("   %-28s %3d games; contact turns %5.1f a game: %s; under three or more %2.0f %%; deaths %4.1f %%; three towers stand by turn 60 in %2.0f %%" % (
        label, p["n"], total, " | ".join("%5.1f" % p["turns"][c] for c in CLASSES), 100.0 * (p["turns"]["under three"] + p["turns"]["under four"]) / max(1e-9, total),
        p["dead"], p["line60"]))
    print("   %-28s           HP to knights there      : %s; games with such a turn, %%: %s" % (
        "", " | ".join("%5.1f" % p["hp"][c] for c in CLASSES), " | ".join("%3.0f" % p["games"][c] for c in CLASSES)))


def delta(label, a, b):
    print("   %-28s           the rule's change, turns : %s;  HP: %s" % (
        label, " | ".join("%+5.1f" % (b["turns"][c] - a["turns"][c]) for c in CLASSES), " | ".join("%+5.1f" % (b["hp"][c] - a["hp"][c]) for c in CLASSES)))


def main():
    arena = sys.argv[1] if len(sys.argv) > 1 else os.path.join("replays", "study", "gold-v15-2026-11-11")
    local = sys.argv[2] if len(sys.argv) > 2 else os.path.join("replays", "bench", "exp-2026-11-10-tower-sites", "profiles")
    print("Contact turns from turn 50 a game by class: %s" % " | ".join(CLASSES))
    print("\n1. The arena (Gold): v14's and v15's two placements")
    a, b = profile(os.path.join(arena, "probe-v14")), profile(os.path.join(arena, "probe"))
    show("v14", a)
    show("v15", b)
    delta("v15 less v14", a, b)
    print("\n2. The local profiles: v14 and SITE_NEAR=50 on the same 200 maps, both seats")
    for opp in ("v14", "rival", "field"):
        a, b = profile(os.path.join(local, "v14-" + opp)), profile(os.path.join(local, "SITE_NEAR-50-" + opp))
        show("v14 against " + opp, a)
        show("SITE_NEAR=50 against " + opp, b)
        delta("the arm less v14", a, b)


if __name__ == "__main__":
    main()
