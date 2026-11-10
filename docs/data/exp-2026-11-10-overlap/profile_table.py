"""The profiles of profile.sh side by side (2026-11-10, "stand where the towers overlap"): per arm and opponent, from the
probe's csv files of 200 maps on both seats:
  win %, our queen's deaths;
  contact turns from turn 50 (an enemy knight within 250) a game, the mean number of own towers covering the queen on
  them, the share under three or more, and the HP lost a contact turn;
  the contact episodes from turn 50 by the cover on their first turn (one or none, two, three or more): episodes a game
  and HP a game; and those begun on a walk (a BUILD to a site she does not touch, or a MOVE): episodes and HP a game;
  the HP our queen loses to knights by 50 turns, and the enemy queen's to ours over turns 50-249;
  the gate's state (turn 30 on, two towers, a knight within 500 and none within 250) in turns a game;
  top-ups of a tower at 700 HP or more a game, and those adding 10 HP or less;
  own towers at turns 100 and 150 and the HP of a tower at turn 150; our income at turn 100; knight releases a game.

Usage (from the repository root): python -B -I docs/data/exp-2026-11-10-overlap/profile_table.py [<profile directory>]
The directory defaults to replays/bench/exp-2026-11-10-overlap/profiles; v13's profiles come first, then the arms in the
order they were written. Output: stdout (profiles.txt beside this file).
"""
import glob
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "study-2026-11-09-v13-gold"))
from probe_tables import by_game, read  # noqa: E402
from v13_gold import mean  # noqa: E402


def top(r):
    return r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T"


def gate(r):
    return r["t"] >= 30 and r["T"] >= 2 and r["k500"] > 0 and r["k250"] == 0


def profile(d):
    turns = read(os.path.join(d, "turns.csv"))
    waves = read(os.path.join(d, "waves.csv"))
    games = read(os.path.join(d, "games.csv"))
    g = by_game(turns)
    ours = {gid: rows for (gid, us), rows in g.items() if us == 1}
    theirs = {gid: rows for (gid, us), rows in g.items() if us == 0}
    n = float(len(ours))
    our_rows = [r for rows in ours.values() for r in rows]
    their_rows = [r for rows in theirs.values() for r in rows]
    con = [r for r in our_rows if r["t"] >= 50 and r["k250"] > 0]
    ep = {"low": [0, 0], "two": [0, 0], "high": [0, 0], "walk": [0, 0]}
    for rs in ours.values():
        i = 0
        while i < len(rs):
            if rs[i]["t"] >= 50 and rs[i]["k250"] > 0 and (i == 0 or rs[i - 1]["k250"] == 0):
                j, kd = i, 0
                while j < len(rs) and rs[j]["k250"] > 0:
                    kd += rs[j]["kd"]
                    j += 1
                c = rs[i]["towersCov"]
                key = "low" if c <= 1 else "two" if c == 2 else "high"
                ep[key][0] += 1
                ep[key][1] += kd
                b = rs[i - 1] if i > 0 else None
                if b is not None and (b["cmd"] == "M" or b["cmd"] == "B" and b["touch"] != b["site"]):
                    ep["walk"][0] += 1
                    ep["walk"][1] += kd
                i = j
            else:
                i += 1
    out = {
        "win": 100.0 * sum(x["won"] for x in games) / len(games),
        "dead": sum(1 for rows in ours.values() if rows[-1]["hpAfter"] <= 0),
        "contact": len(con) / n,
        "cover": mean([r["towersCov"] for r in con]),
        "cov3": 100.0 * mean([1 if r["towersCov"] >= 3 else 0 for r in con]),
        "hpTurn": sum(r["kd"] for r in con) / max(1, len(con)),
        "ep": {k: (v[0] / n, v[1] / n) for k, v in ep.items()},
        "phase": [sum(r["kd"] for r in our_rows if a <= r["t"] < a + 50) / n for a in (0, 50, 100, 150, 200)],
        "their": sum(r["kd"] for r in their_rows if r["t"] >= 50) / n,
        "gate": sum(1 for r in our_rows if gate(r)) / n,
        "full": sum(1 for r in our_rows if top(r) and r["touchHp"] >= 700) / n,
        "dead10": sum(1 for r in our_rows if top(r) and 800 - r["touchHp"] <= 10) / n,
        "t100": mean([r["T"] for r in our_rows if r["t"] == 100]),
        "t150": mean([r["T"] for r in our_rows if r["t"] == 150]),
        "thp150": sum(r["thpSum"] for r in our_rows if r["t"] == 150) / max(1, sum(r["T"] for r in our_rows if r["t"] == 150)),
        "inc100": mean([r["income"] for r in our_rows if r["t"] == 100]),
        "rel": sum(1 for w in waves if w["us"] == 1 and w["type"] == "K") / n,
    }
    return out


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "replays/bench/exp-2026-11-10-overlap/profiles"
    dirs = [p for p in glob.glob(os.path.join(d, "*")) if os.path.exists(os.path.join(p, "games.csv"))]
    dirs.sort(key=lambda p: (not os.path.basename(p).startswith("v13-"), os.path.getmtime(os.path.join(p, "games.csv"))))
    print("Seeds 1 + 397 k, both seats. contact: contact turns from turn 50 a game / own towers covering the queen on them (mean) /"
          " share under three or more / HP lost a contact turn. episodes from turn 50 by the cover on their first turn, a game: <=1,"
          " 2, 3+ as episodes/HP; walk: those begun on a walk or a MOVE. HP by 50 turns: our queen's loss to knights; their: the"
          " enemy queen's to ours over turns 50-249. gate: turns a game in the gate's state. full/dead: top-ups of a tower at 700+ /"
          " adding 10 HP or less, a game. towers at turns 100 and 150 @ the HP of a tower at 150. inc: income at turn 100. rel: our"
          " knight releases a game.")
    print("%-44s %5s %4s  %-22s  %-39s %-9s  %-24s %5s %5s  %-9s %-14s %4s %4s" % (
        "arm vs opponent", "win %", "dead", "contact/cover/3+/HP", "episodes <=1 | 2 | 3+ (n/HP a game)", "walk n/HP", "HP 0-50|..|200-250", "their",
        "gate", "full/dead", "towers", "inc", "rel"))
    for p in dirs:
        o = profile(p)
        e = o["ep"]
        print("%-44s %5.1f %4d  %4.1f / %.2f / %2.0f / %.2f  %.1f/%4.1f | %.1f/%4.1f | %.1f/%4.1f  %.1f/%4.1f   %s %5.1f %5.1f  %4.1f/%4.1f %.1f %.1f @ %3.0f %4.1f %4.1f" % (
            os.path.basename(p), o["win"], o["dead"], o["contact"], o["cover"], o["cov3"], o["hpTurn"],
            e["low"][0], e["low"][1], e["two"][0], e["two"][1], e["high"][0], e["high"][1], e["walk"][0], e["walk"][1],
            " ".join("%4.1f" % x for x in o["phase"]), o["their"], o["gate"], o["full"], o["dead10"], o["t100"], o["t150"], o["thp150"], o["inc100"], o["rel"]))


if __name__ == "__main__":
    main()
