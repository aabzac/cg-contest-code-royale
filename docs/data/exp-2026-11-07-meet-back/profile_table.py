"""The first-wave profiles of profile.sh side by side (2026-11-07): per arm and opponent, the wins, the queen deaths, the first
contact (turn, our queen's distance from her back edge, the games beyond 406), the first-wave loss to knights (all games,
merged and unmerged waves, beyond and within 406), the standing turns, the HP lost on turns 25-50, the queen's HP and towers
at turns 50 and 100, the gold mined, and the two mechanism measures: the distance from her back edge of the new tower she
was sent to last by the contact (median, games beyond 500) and the defence rule's builds more than 50 ahead of her in the 20
turns from the contact (a game, mean; games with one).

Usage (from the repository root): python -B -I docs/data/exp-2026-11-07-meet-back/profile_table.py [<profile directory>]
The directory defaults to replays/bench/exp-2026-11-07-meet-back/profiles; v11's profiles come first, then the arms in the
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
    "wave": r"^first wave \(the Gold study's measures, (\d+) games with a contact\): contact turn (\d+) \(median\), our queen (\d+) from her back edge then \(median; mean (\d+); (\d+) beyond 406\), loss to knights in the 20 turns from it ([\d.]+) \(mean\), merged waves in (\d+) \(loss ([\d.-]+) against ([\d.-]+) unmerged\), standing at an own tower with a knight within 155 ([\d.]+)",
    "where": r"^first wave by where it meets her: beyond 406 from her back edge the loss is ([\d.-]+) \(mean, (\d+) games\), within 406 ([\d.-]+) \((\d+) games\)",
    "economy": r"^economy .*gold mined \d+; ([\d.]+)",
    "flow": r"^HP flow \(means a game, turns 0-49, 50-99, 100-149, 150-199, 200-249\): our queen to the enemy knights ([\d. ]+); our queen to the enemy towers ([\d. ]+); the enemy queen to our knights ([\d. ]+); the enemy queen to our towers ([\d. ]+)$",
    "mech": r"^first wave, mechanisms: the new tower she was sent to last (?:by|before) the contact (\S+) from her back edge \(median; mean (\S+); (\d+) games, (\d+) beyond 500\); in the 20 turns from the contact, BUILDs of a free site she does not touch, more than 50 ahead of her, under a knight within 250 \(the defence rule\), ([\d.]+) a game \(mean; in (\d+) games\), losing (\S+) HP",
}


def read(path):
    text = open(path, encoding="utf-8").read()
    out = {}
    for key, pat in PATTERNS.items():
        m = re.search(pat, text, re.M)
        out[key] = m.groups() if m else None
    return out


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-07-meet-back/profiles"
    files = sorted(glob.glob(os.path.join(d, "*.txt")), key=lambda p: (not os.path.basename(p).startswith("v11-"), os.path.getmtime(p)))
    print("Seeds 1 + 397 k, k < 200, both seats (400 games). fwd: our queen's distance from her back edge at the first contact"
          " (median/mean, games beyond 406); loss: the first-wave loss to knights in the 20 turns from the contact (mean; merged/"
          "unmerged waves; beyond/within 406); stand: turns at an own tower with a knight within 155; 25-50: HP lost on turns"
          " 25-50 (mean); hp/tw: the queen's HP and own towers at turns 50 and 100 (medians); gold: gold mined (mean); held:"
          " the new tower she was sent to last by the contact, its distance from her back edge (median, games beyond 500);"
          " def>50: the defence rule's builds more than 50 ahead of her in the 20 turns (mean a game, games).")
    print("%-34s %6s %5s %4s %13s %5s %6s %13s %13s %5s %6s %9s %9s %6s %9s %9s" % (
        "arm vs opponent", "win %", "dead", "turn", "fwd", "merge", "loss", "merged/unm", "beyond/within", "stand", "25-50",
        "hp 50/100", "tw 50/100", "gold", "held", "def>50"))
    for path in files:
        name = os.path.basename(path)[:-4]
        r = read(path)
        if not r["games"] or not r["wave"]:
            print("%-34s (incomplete profile)" % name)
            continue
        g, w, wh, c = r["games"], r["wave"], r["where"], r["contact"]
        mech = r["mech"]
        print("%-34s %6s %5s %4s %13s %5s %6s %13s %13s %5s %6s %9s %9s %6s %9s %9s" % (
            name, g[2], g[3], w[1], "%s/%s %s" % (w[2], w[3], w[4]), w[6], w[5], "%s/%s" % (w[7], w[8]),
            "%s/%s" % (wh[0], wh[2]), w[9], c[3], "%s/%s" % (r["t50"][4], r["t100"][4]), "%s/%s" % (r["t50"][2], r["t100"][2]),
            r["economy"][0] if r["economy"] else "-", "%s %s" % (mech[0], mech[3]) if mech else "-",
            "%s %s" % (mech[4], mech[5]) if mech else "-"))


def flow_table(files):
    rows = [(os.path.basename(p)[:-4], read(p)["flow"]) for p in files]
    rows = [(n, f) for n, f in rows if f]
    if not rows:
        return
    print()
    print("HP flow by phase (means a game over the 400 games; turns 0-49 | 50-99 | 100-149 | 150-199 | 200-249, then the sum of"
          " turns 50-249): what our queen loses to the enemy knights, and what the enemy queen loses to our knights.")
    print("%-34s %-32s %6s   %-32s %6s" % ("arm vs opponent", "our queen to enemy knights", "50-249", "enemy queen to our knights", "50-249"))
    for name, f in rows:
        ours, theirs = [float(x) for x in f[0].split()], [float(x) for x in f[2].split()]
        print("%-34s %-32s %6.1f   %-32s %6.1f" % (name, " ".join("%5.1f" % x for x in ours), sum(ours[1:]),
                                                   " ".join("%5.1f" % x for x in theirs), sum(theirs[1:])))


if __name__ == "__main__":
    main()
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-07-meet-back/profiles"
    flow_table(sorted(glob.glob(os.path.join(d, "*.txt")), key=lambda p: (not os.path.basename(p).startswith("v11-"), os.path.getmtime(p))))
