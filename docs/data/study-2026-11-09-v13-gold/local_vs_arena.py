"""v13 on the arena beside v13 against the local panel (study 2026-11-09), in ArenaProbe's terms: does a local opponent put
the queen in the states the Gold games do? The pressure (knights near, releases against us), the top-ups of full towers,
the tower line, the mines.

Usage (from the repository root): python -B -I docs/data/study-2026-11-09-v13-gold/local_vs_arena.py [--root <dir with probe/ and local-v13-*/>]
Output: stdout (local_vs_arena.txt beside this file).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe_tables import by_game, read  # noqa: E402
from v13_gold import mean  # noqa: E402


def full_topup(r):
    return r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T" and r["touchHp"] >= 700


def gate(r):
    return r["t"] >= 30 and r["T"] >= 2 and r["k500"] > 0 and r["k250"] == 0


def profile(name, d):
    turns = read(os.path.join(d, "turns.csv"))
    waves = [w for w in read(os.path.join(d, "waves.csv")) if w["type"] == "K"]
    events = read(os.path.join(d, "events.csv"))
    g = by_game(turns)
    ours = {gid: rows for (gid, us), rows in g.items() if us == 1}
    theirs = {gid: rows for (gid, us), rows in g.items() if us == 0}
    n = float(len(ours))
    won = sum(1 for rows in ours.values() if rows[-1]["hpAfter"] > theirs_last(theirs, rows)) / n
    dead = sum(1 for rows in ours.values() if rows[-1]["hpAfter"] <= 0)
    print("%s: %d games, we end ahead on HP in %.0f %%, our queen dead in %d, mean length %.0f" % (name, n, 100 * won, dead, mean([float(len(r)) for r in ours.values()])))
    our_rows = [r for rows in ours.values() for r in rows]
    their_rows = [r for rows in theirs.values() for r in rows]
    print("   turns with an enemy knight within 250 / within 500 only (the gate's state from turn 30): ours %.1f / %.1f a game; theirs %.1f / %.1f" % (
        sum(1 for r in our_rows if r["k250"] > 0) / n, sum(1 for r in our_rows if gate(r)) / n,
        sum(1 for r in their_rows if r["k250"] > 0) / n, sum(1 for r in their_rows if gate(r)) / n))
    print("   HP lost to knights by 50 turns: ours %s; theirs %s" % (
        " | ".join("%.1f" % (sum(r["kd"] for r in our_rows if a <= r["t"] < a + 50) / n) for a in (0, 50, 100, 150, 200)),
        " | ".join("%.1f" % (sum(r["kd"] for r in their_rows if a <= r["t"] < a + 50) / n) for a in (0, 50, 100, 150, 200))))
    print("   knight releases a game: ours %.1f (from turn 50 %.1f), theirs %.1f (from turn 50 %.1f); enemy knights alive, mean over our turns from turn 50: %.1f" % (
        sum(1 for w in waves if w["us"] == 1) / n, sum(1 for w in waves if w["us"] == 1 and w["t"] >= 50) / n,
        sum(1 for w in waves if w["us"] == 0) / n, sum(1 for w in waves if w["us"] == 0 and w["t"] >= 50) / n,
        mean([r["eKnights"] for r in our_rows if r["t"] >= 50])))
    full = [r for r in our_rows if full_topup(r)]
    print("   our top-ups of a tower at 700 or more: %.1f a game (%.1f %% of our turns): a knight within 250 %.1f, within 500 only %.1f, none within 500 %.1f; their queen's: %.1f a game" % (
        len(full) / n, 100.0 * len(full) / len(our_rows), sum(1 for r in full if r["k250"] > 0) / n, sum(1 for r in full if r["k250"] == 0 and r["k500"] > 0) / n,
        sum(1 for r in full if r["k500"] == 0) / n, sum(1 for r in their_rows if full_topup(r)) / n))
    parts = []
    for t in (50, 100, 150, 200):
        a = [r for r in our_rows if r["t"] == t]
        b = [r for r in their_rows if r["t"] == t]
        parts.append("t%d %.1f @ %.0f / %.1f @ %.0f" % (t, mean([r["T"] for r in a]), sum(r["thpSum"] for r in a) / max(1, sum(r["T"] for r in a)),
                                                      mean([r["T"] for r in b]), sum(r["thpSum"] for r in b) / max(1, sum(r["T"] for r in b))))
    print("   towers @ HP each, ours / theirs: %s" % "; ".join(parts))
    print("   income, ours / theirs: %s; mines built a game: ours %.1f, theirs %.1f; our mines lost to creeps %.1f" % (
        "; ".join("t%d %.1f / %.1f" % (t, mean([r["income"] for r in our_rows if r["t"] == t]), mean([r["income"] for r in their_rows if r["t"] == t])) for t in (50, 100, 150, 200)),
        sum(1 for e in events if e["us"] == 1 and e["event"] == "built" and e["kind"] == "MINE") / n, sum(1 for e in events if e["us"] == 0 and e["event"] == "built" and e["kind"] == "MINE") / n,
        sum(1 for e in events if e["us"] == 1 and e["event"] == "lost" and e["kind"] == "MINE" and e["cause"] == "creep") / n))
    low = [r for r in full if r["lowTDist"] >= 0]
    print("   at those top-ups: an own tower below 600 stands in %.0f %% (nearest %.0f away, mean; within 200 in %.0f %%, within 300 in %.0f %%), one inside another own tower's range in %.0f %%; the lowest own tower has %.0f HP (mean)" % (
        100.0 * len(low) / max(1, len(full)), mean([r["lowTDist"] for r in low]), 100.0 * sum(1 for r in low if r["lowTDist"] <= 200) / max(1, len(full)),
        100.0 * sum(1 for r in low if r["lowTDist"] <= 300) / max(1, len(full)), 100.0 * sum(1 for r in full if r["lowTCovDist"] >= 0) / max(1, len(full)),
        mean([r["minTHp"] for r in full if r["minTHp"] >= 0])))


def theirs_last(theirs, rows):
    return theirs[rows[0]["gid"]][-1]["hpAfter"]


def main():
    root = sys.argv[sys.argv.index("--root") + 1] if "--root" in sys.argv else os.path.join("replays", "study", "gold-v13-2026-11-09")
    profile("v13 on the arena (Gold, 180 games)", os.path.join(root, "probe"))
    profile("v12 on the arena (Gold, 180 games)", os.path.join(root, "probe-v12"))
    for o in ("v13", "rival", "field"):
        profile("v13 against %s (local, seeds 1 + 397 k, k < 100, both seats)" % o, os.path.join(root, "local-v13-" + o))


if __name__ == "__main__":
    main()
