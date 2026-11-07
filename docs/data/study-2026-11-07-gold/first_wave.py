"""The enemy's first wave in v11's 180 Gold games (study 2026-11-07, added for the first-wave candidate): is our tower
count at the first contact a cause of the HP lost, or does it stand in for the wave's timing and size, as in Silver?

Inputs: those of gold.py (the cgStudy features and our queen's traces in replays/study/gold-2026-11-07/).
Usage (from the repository root): python -B -I docs/data/study-2026-11-07-gold/first_wave.py [--data <root holding replays/>]
Output: stdout (first_wave.txt beside this file).
"""
import csv
import json
import os
import re
import statistics
import sys
from collections import defaultdict

AGENTS = {6857463: ("upload-03", "arena-6857463-games-2026-11-07.json"), 6858567: ("upload-04", "arena-6858567-games-2026-11-07.json")}
HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (-?\d+)/(-?\d+), scores (-?\d+)/(-?\d+), (\d+) turns\)")
LINE = re.compile(r"^t\s*(\d+) gold\s+(-?\d+) q\(\s*-?\d+,\s*-?\d+\) (\S+)\s+(\S*)\s*(\S*)\s+touch\s*-?\d+ \| T(\d+) M(\d+) B(\d+) nearT\s+(-?\d+) \| "
                  r"K250 (\d+) K500 (\d+) .* \| hp\s+(-?\d+) \(-(-?\d+)\) \| kd (-?\d+) td (-?\d+)")


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else float("nan")


def fl(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def mean(xs):
    xs = [x for x in xs if x is not None]
    return sum(xs) / len(xs) if xs else float("nan")


def window_cmds(turns, c):
    """The queen's commands over the 20 turns from the contact, counted by kind."""
    out = defaultdict(int)
    for t in turns:
        if c <= t["t"] < c + 20:
            out[t["cmd"]] += 1
    return out


def main(data):
    base = os.path.join(data, "replays", "study", "gold-2026-11-07")
    feats = defaultdict(dict)
    for r in csv.DictReader(open(os.path.join(base, "features-v11-gold.csv"), encoding="utf-8")):
        feats[int(r["gameId"])]["us" if int(r["agentId"]) in AGENTS else "them"] = r
    rows = []
    for agent, (up, manifest) in AGENTS.items():
        man = {g["gameId"]: g for g in json.load(open(os.path.join("docs/data", manifest), encoding="utf-8"))["games"]}
        game, turns = None, []

        def close():
            if game is None:
                return
            g = man.get(game)
            f = feats.get(game, {})
            if not g or "us" not in f or "them" not in f:
                return
            contact = next((t for t in turns if t["k250"] > 0), None)
            if contact is None:
                return
            c = contact["t"]
            loss = sum(t["kd"] for t in turns if c <= t["t"] < c + 20)
            s_me, s_foe = g["scores"][g["seat"]], g["scores"][1 - g["seat"]]
            race = (g["frames"] - 1) // 2 >= 250 and s_me >= 0 and s_foe >= 0
            won = g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
            rows.append({"race": race, "won": won, "contact": c, "towers": contact["towers"], "k500": contact["k500"],
                         "loss": loss, "enemy_k25": fl(f["them"]["knights_25"]), "enemy_k50": fl(f["them"]["knights_50"]),
                         "first_tower": fl(f["us"]["firstTower"]), "towers25": fl(f["us"]["towers_25"]),
                         "enemy_bk25": fl(f["them"]["bk_25"]), "our_k25": fl(f["us"]["knights_25"]), "our_k50": fl(f["us"]["knights_50"]),
                         "start_hp": turns[0]["hp"] if turns else None, "cmds": window_cmds(turns, c),
                         "near_t": mean([t["near_t"] for t in turns if c <= t["t"] < c + 20 and t["near_t"] >= 0]),
                         "k250_turns": sum(1 for t in turns if c <= t["t"] < c + 20 and t["k250"] > 0)})

        for line in open(os.path.join(base, "ours-%s.txt" % up), encoding="utf-8-sig"):
            h = HDR.search(line)
            if h:
                close()
                game, turns = int(h.group(1)), []
                continue
            m = LINE.match(line)
            if m:
                cmd = m.group(3) if m.group(3) != "BUILD" else "BUILD " + m.group(5)
                turns.append({"t": int(m.group(1)), "towers": int(m.group(6)), "near_t": int(m.group(9)), "k250": int(m.group(10)),
                              "k500": int(m.group(11)), "hp": int(m.group(12)), "kd": int(m.group(14)), "cmd": cmd})
        close()

    def show(label, sel):
        if not sel:
            return
        print("   %-38s %3d games: contact t%.0f, own towers %.1f (mean), enemy knights within 500 %.1f, enemy knights alive at"
              " t25 %.1f / t50 %.1f, our first tower t%.0f; loss in the next 20 turns %.1f (mean), %d %% won" % (
                  label, len(sel), med([r["contact"] for r in sel]), mean([r["towers"] for r in sel]), mean([r["k500"] for r in sel]),
                  mean([r["enemy_k25"] for r in sel]), mean([r["enemy_k50"] for r in sel]), med([r["first_tower"] for r in sel]),
                  mean([r["loss"] for r in sel]), round(100.0 * sum(r["won"] for r in sel) / len(sel))))

    races = [r for r in rows if r["race"]]
    print("The first contact in v11's Gold games (an enemy knight within 250 of our queen); %d games, %d races at turn 250" % (len(rows), len(races)))
    print("1. Races, by result:")
    show("lost races", [r for r in races if not r["won"]])
    show("won races", [r for r in races if r["won"]])
    print("2. All games, by our towers at the first contact:")
    for n in (0, 1, 2, 3):
        show("%d own towers" % n, [r for r in rows if r["towers"] == n])
    show("4+ own towers", [r for r in rows if r["towers"] >= 4])
    print("3. All games, by the first contact's turn:")
    for lo, hi in ((0, 22), (22, 25), (25, 28), (28, 35), (35, 1000)):
        show("contact at turns %d-%d" % (lo, hi - 1), [r for r in rows if lo <= r["contact"] < hi])
    print("4. One own tower against two, at the same contact timing (turns 22-27):")
    for n in (1, 2):
        show("%d own towers, contact t22-27" % n, [r for r in rows if r["towers"] == n and 22 <= r["contact"] <= 27])
    print("5. By the enemy's knights within 500 at the contact:")
    for lo, hi in ((0, 3), (3, 5), (5, 100)):
        show("%d-%d enemy knights within 500" % (lo, hi - 1), [r for r in rows if lo <= r["k500"] < hi])
    print("6. The 20 turns from the contact in the races (means over the games):")
    for label, sel in (("lost races", [r for r in races if not r["won"]]), ("won races", [r for r in races if r["won"]])):
        if not sel:
            continue
        cmds = defaultdict(float)
        for r in sel:
            for k, v in r["cmds"].items():
                cmds[k] += v / len(sel)
        print("   %-11s queen's turns: %s; nearest own tower %.0f away; enemy knights within 250 on %.1f turns; our knights alive at"
              " t25 %.1f / t50 %.1f, the enemy's %.1f / %.1f; starting HP %.0f" % (
                  label, ", ".join("%s %.1f" % (k, v) for k, v in sorted(cmds.items(), key=lambda x: -x[1])),
                  mean([r["near_t"] for r in sel]), mean([r["k250_turns"] for r in sel]), mean([r["our_k25"] for r in sel]),
                  mean([r["our_k50"] for r in sel]), mean([r["enemy_k25"] for r in sel]), mean([r["enemy_k50"] for r in sel]),
                  mean([r["start_hp"] for r in sel])))


if __name__ == "__main__":
    args = sys.argv[1:]
    main(args[args.index("--data") + 1] if "--data" in args else ".")
