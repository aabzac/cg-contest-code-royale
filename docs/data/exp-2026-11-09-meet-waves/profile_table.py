"""The profiles of profile.sh side by side (2026-11-09, "meet every wave at a tower"): per arm and opponent, the wins, the
queen deaths, the first contact (turn, own towers then, the first-wave loss to knights in the 20 turns from it), the second
tower's turn minus the contact turn (median / mean), the midgame contact episodes of turns 50-249 begun on a walk to a site
and begun at an own tower (episodes a game, HP a game, HP per episode), the queen's HP and towers at turns 50 and 100, the
summed tower HP at turn 50, and the gold mined.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-09-meet-waves/profile_table.py [<profile directory>]
The directory defaults to replays/bench/exp-2026-11-09-meet-waves/profiles; v12's profiles come first, then the arms in the
order they were written. Output: stdout.
"""
import glob
import os
import re
import sys

PATTERNS = {
    "games": r"^\S+ vs \S+: (\d+) games, \S+ wins (\d+) \(([\d.]+) %\), queen deaths (\d+), enemy queens killed (\d+)",
    "t50": r"^turn 50: income (\d+) mines (\d+) towers (\d+) towerHp (\d+) queenHp (\d+)",
    "t100": r"^turn 100: income (\d+) mines (\d+) towers (\d+) towerHp (\d+) queenHp (\d+)",
    "contact": r"^first contact .*?turn (\d+), own towers then (\d+), HP lost in the next 20 turns \d+ \(mean ([\d.]+)\); HP lost on turns 25-50 \d+ \(mean ([\d.]+)",
    "t2": r"^second tower: stands (\S+) turns after the first contact \(median over games with both; mean (\S+)\)",
    "mid": r"^midgame contacts .*?: episodes begun on a walk to a site (\S+) losing (\S+) HP \((\S+) per episode\), begun at an own tower (\S+) losing (\S+) HP \((\S+) per episode\), other (\S+) losing (\S+) HP",
    "economy": r"^economy .*gold mined \d+; ([\d.]+)",
    "flow": r"^HP flow \(means a game, turns 0-49, 50-99, 100-149, 150-199, 200-249\): our queen to the enemy knights ([\d. ]+); our queen to the enemy towers ([\d. ]+); the enemy queen to our knights ([\d. ]+); the enemy queen to our towers ([\d. ]+)$",
}


def read(path):
    text = open(path, encoding="utf-8").read()
    out = {}
    for key, pat in PATTERNS.items():
        m = re.search(pat, text, re.M)
        out[key] = m.groups() if m else None
    return out


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-09-meet-waves/profiles"
    files = sorted(glob.glob(os.path.join(d, "*.txt")), key=lambda p: (not os.path.basename(p).startswith("v12-"), os.path.getmtime(p)))
    print("Seeds 1 + 397 k, k < 200, both seats (400 games). contact: the first contact's turn and our towers then (medians);"
          " loss20: the first-wave loss to knights in the 20 turns from it (mean); t2-c: the second tower's turn minus the"
          " contact turn (median / mean over games with both); walk, tower: the midgame contact episodes (turns 50-249) begun"
          " on a walk to a site / at an own tower, as episodes a game, HP a game and HP per episode (means); hp, tw: the queen's"
          " HP and own towers at turns 50 and 100 (medians); thp50: summed tower HP at turn 50 (median); gold: gold mined (mean).")
    print("%-40s %6s %5s %8s %6s %10s %16s %16s %9s %7s %6s %6s" % (
        "arm vs opponent", "win %", "dead", "contact", "loss20", "t2-c", "walk ep/HP/per", "tower ep/HP/per", "hp 50/100", "tw", "thp50", "gold"))
    for path in files:
        name = os.path.basename(path)[:-4]
        r = read(path)
        if not r["games"] or not r["contact"]:
            print("%-40s (incomplete profile)" % name)
            continue
        g, c, t2, mid = r["games"], r["contact"], r["t2"], r["mid"]
        print("%-40s %6s %5s %8s %6s %10s %16s %16s %9s %7s %6s %6s" % (
            name, g[2], g[3], "%s / %s" % (c[0], c[1]), c[2], "%s / %s" % (t2[0], t2[1]) if t2 else "-",
            "%s/%s/%s" % (mid[0], mid[1], mid[2]) if mid else "-", "%s/%s/%s" % (mid[3], mid[4], mid[5]) if mid else "-",
            "%s/%s" % (r["t50"][4], r["t100"][4]), "%s/%s" % (r["t50"][2], r["t100"][2]), r["t50"][3],
            r["economy"][0] if r["economy"] else "-"))
    flow_table(files)


def flow_table(files):
    rows = [(os.path.basename(p)[:-4], read(p)["flow"]) for p in files]
    rows = [(n, f) for n, f in rows if f]
    if not rows:
        return
    print()
    print("HP flow by phase (means a game over the 400 games; turns 0-49 | 50-99 | 100-149 | 150-199 | 200-249, then the sum of"
          " turns 50-249): what our queen loses to the enemy knights, and what the enemy queen loses to our knights.")
    print("%-40s %-32s %6s   %-32s %6s" % ("arm vs opponent", "our queen to enemy knights", "50-249", "enemy queen to our knights", "50-249"))
    for name, f in rows:
        ours, theirs = [float(x) for x in f[0].split()], [float(x) for x in f[2].split()]
        print("%-40s %-32s %6.1f   %-32s %6.1f" % (name, " ".join("%5.1f" % x for x in ours), sum(ours[1:]),
                                                   " ".join("%5.1f" % x for x in theirs), sum(theirs[1:])))


if __name__ == "__main__":
    main()
