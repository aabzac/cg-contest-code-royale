"""Study 2026-11-07: v10's Silver results by the opponent's rating, and our queen under enemy tower fire (v10 against v08;
v11, with SAFE_LADDER=1, added after its two Silver uploads).

Usage, from the repository root: python -B -I docs/data/study-2026-11-07-tower-fire/towers.py <trace directory>
The traces are `cr.tools.DeathTrace` outputs named trace-<agentId>.txt (README.md has the commands); the game
manifests and the upload registry are read from docs/data/. Prints per version:
- the results by the opponent's score at fetch, with the share the logistic model of tools/arena_compare.py expects
  from the version's performance rating (the scale is the league's, fitted by that tool), and the boss separately;
- every turn the queen lost HP to enemy towers (the trace's `td` column), with the command she played and whether an
  enemy knight stood within 250;
- the deaths, with the share of the HP lost to towers in their last 30 turns.
"""
import json
import math
import os
import re
import sys
from collections import Counter

sys.path.insert(0, "tools")
import arena_compare  # noqa: E402

VERSIONS = {
    "v08": [("6843976", "arena-6843976-games-2026-11-04.json"), ("6844095", "arena-6844095-games-2026-11-04.json"),
            ("6847999", "arena-6847999-games-2026-11-04.json")],
    "v10": [("6857063", "arena-6857063-games-2026-11-07.json"), ("6857144", "arena-6857144-games-2026-11-07.json")],
    "v11": [("6857351", "arena-6857351-games-2026-11-07.json"), ("6857388", "arena-6857388-games-2026-11-07.json")],
}
BOSS = 1876720  # the Silver boss's agent id (its games carry no pseudo)
BRACKETS = [("below 17", None, 17), ("17-19", 17, 19), ("19-21", 19, 21), ("21 and above", 21, None)]
HDR = re.compile(r"game (\d+) seat (\d) vs (\S+) \(ranks (-?\d+)/(-?\d+), scores (-?\d+)/(-?\d+), (\d+) turns\)")
LINE = re.compile(r"t\s*(\d+) gold\s+(-?\d+) q\(\s*(\d+),\s*(\d+)\) (\S+)\s+(\S*)\s*(\S*)\s+touch\s*(-?\d+) \| T(\d+) M(\d+) B(\d+) "
                  r"nearT\s+(-?\d+) \| K250 (\d+) K500 (\d+) nearK\s+(-?\d+) A300 (\d+) G300 (\d+) eTow (\d+) \| hp\s+(\d+) "
                  r"\(-?(\d+)\) \| kd (-?\d+) td (-?\d+)")


def by_rating(name, games, rating, scale):
    rows = {label: [0, 0.0, 0.0] for label, _, _ in BRACKETS + [("the boss", None, None)]}
    for g in games:
        s = g["opponent"].get("ratingAtFetch")
        if not isinstance(s, (int, float)):
            continue
        if g["opponent"].get("agentId") == BOSS:
            label = "the boss"
        else:
            label = next(b for b, lo, hi in BRACKETS if (lo is None or s >= lo) and (hi is None or s < hi))
        row = rows[label]
        row[0] += 1
        row[1] += arena_compare.points(g)
        row[2] += 1 / (1 + math.exp(-(rating - s) / scale))
    print("%s: performance %.2f; results by the opponent's score at fetch (points, ties 1/2; expected from the rating)" % (name, rating))
    combined = {"below 19": ["below 17", "17-19"], "21 up, boss": ["21 and above", "the boss"]}
    for label, parts in combined.items():
        rows[label] = [sum(rows[p][i] for p in parts) for i in range(3)]
    for label, (n, pts, exp) in rows.items():
        if n:
            print("  %-14s %3d games  %5.1f points  %3.0f %%  expected %3.0f %% (%4.1f points)"
                  % (label, n, pts, 100 * pts / n, 100 * exp / n, exp))


def bracket(g):
    s = g["opponent"].get("ratingAtFetch")
    if g["opponent"].get("agentId") == BOSS:
        return "the boss"
    if not isinstance(s, (int, float)):
        return None
    return next(b for b, lo, hi in BRACKETS if (lo is None or s >= lo) and (hi is None or s < hi))


def mean_se(xs):
    m = sum(xs) / len(xs)
    sd = math.sqrt(sum((x - m) ** 2 for x in xs) / (len(xs) - 1)) if len(xs) > 1 else 0.0
    return m, sd / math.sqrt(len(xs))


def fire(name, agents, traces):
    games, turns_fire, fire_hp, cmds, near = 0, 0, 0, Counter(), Counter()
    deaths, last_lost, last_td, tower_deaths = 0, 0, 0, 0
    lost_at_limit, within = 0, 0
    per_game = {}  # game id -> (HP lost to towers, the opponent's bracket); games absent from a trace lost none
    for agent, manifest in agents:
        trace = os.path.join(traces, "trace-%s.txt" % agent)
        if not os.path.exists(trace):
            print("  (missing %s)" % os.path.basename(trace))
            continue
        man = arena_compare.load(os.path.join("docs/data", manifest))["games"]
        games += len(man)
        per_game.update({g["gameId"]: [0, bracket(g)] for g in man})
        for block in re.split(r"(?m)^=== ", open(trace, encoding="utf-8-sig").read())[1:]:
            h = HDR.search(block.splitlines()[0])
            turns = [m for m in (LINE.search(x) for x in block.splitlines()[1:]) if m]
            for m in turns:
                td = int(m.group(22))
                if td > 0:
                    turns_fire += 1
                    fire_hp += td
                    per_game[int(h.group(1))][0] += td
                    cmds[m.group(5) if m.group(5) != "BUILD" else "BUILD " + m.group(7)] += 1
                    near["a knight within 250" if int(m.group(13)) > 0 else "no knight within 250"] += 1
            if h and int(h.group(6)) == 0 and int(h.group(4)) == 1:
                deaths += 1
                last = turns[-30:]
                last_lost += sum(int(m.group(20)) for m in last)
                td = sum(int(m.group(22)) for m in last)
                last_td += td
                tower_deaths += td >= 10
            elif h and int(h.group(4)) == 1 and int(h.group(8)) == 250:
                lost_at_limit += 1
                margin = int(h.group(7)) - int(h.group(6))
                within += per_game[int(h.group(1))][0] >= margin
    print("%s: %d games" % (name, games))
    print("  lost on HP at turn 250: %d; in %d of them the HP lost to towers is at least the margin" % (lost_at_limit, within))
    m, se = mean_se([v[0] for v in per_game.values()])
    print("  turns under enemy tower fire %d, HP %d, %.1f +- %.1f HP a game (SE); %s" % (turns_fire, fire_hp, m, se, dict(near)))
    print("  HP a game to enemy towers by the opponent's score: %s" % ", ".join(
        "%s %.1f +- %.1f (%d)" % ((label,) + mean_se(xs) + (len(xs),))
        for label in [b for b, _, _ in BRACKETS] + ["the boss"]
        for xs in [[v[0] for v in per_game.values() if v[1] == label]] if xs))
    print("  commands on those turns: %s" % cmds.most_common(5))
    print("  deaths %d: in their last 30 turns %d HP lost, %d to towers (%.0f %%); deaths with 10+ HP from towers: %d"
          % (deaths, last_lost, last_td, 100.0 * last_td / max(1, last_lost), tower_deaths))


def main(traces):
    report = arena_compare.compare(arena_compare.load("docs/data/arena-uploads.json"))
    silver = report["leagues"]["silver"]
    print("Silver logistic scale %.2f" % silver["scale"])
    for name, agents in VERSIONS.items():
        games = [g for _, manifest in agents for g in arena_compare.load(os.path.join("docs/data", manifest))["games"]]
        by_rating(name, games, silver["labels"][name]["performance"], silver["scale"])
    for name, agents in VERSIONS.items():
        fire(name, agents, traces)


if __name__ == "__main__":
    main(sys.argv[1])
