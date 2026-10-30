"""Proxy validation PV1 of policy P2 (docs/local-screen.md): what v01's arena games look like next to the local series.

Run from the repository root: python -B -I docs/data/pv1-2026-10-30/pv1.py > docs/data/pv1-2026-10-30/pv1.txt
Reads the games manifests of v01's three Bronze placements (docs/data/arena-<agent>-games-2026-10-29.json), their
replays (the manifest's rawDirectory, gitignored; re-fetched by gameId with tools/arena_games.py) and the two pilot
bench reports of 2026-10-30 (replays/bench/pilot-2026-10-30, gitignored; the commands are in docs/DEVLOG.md).
"""
import collections
import json
import os
import re
import statistics

AGENTS = ("6823071", "6823455", "6824680")
PILOTS = ("replays/bench/pilot-2026-10-30/maxtowers3-vs-v01-500@1000.json",
          "replays/bench/pilot-2026-10-30/v01-vs-boss-60@1000.json")
BUILD = re.compile(r"BUILD \d+ (MINE|TOWER|BARRACKS-KNIGHT|BARRACKS-ARCHER|BARRACKS-GIANT)")
KINDS = ("BARRACKS-KNIGHT", "BARRACKS-ARCHER", "BARRACKS-GIANT", "TOWER", "MINE")


def builds(lines):
    c = collections.Counter()
    for s in lines:
        c.update(m.group(1) for m in BUILD.finditer(s))
    return c


def style(c):
    return "+".join(k for k, n in (("giant", c["BARRACKS-GIANT"]), ("archer", c["BARRACKS-ARCHER"]), ("knight", c["BARRACKS-KNIGHT"])) if n) or "no barracks"


def lengths(label, turns):
    at_limit = sum(1 for t in turns if t >= 250)
    print("%s: %d games, turns mean %.1f, median %g, at the 250-turn limit %d (%.0f %%)"
          % (label, len(turns), statistics.mean(turns), statistics.median(turns), at_limit, 100.0 * at_limit / len(turns)))


def arena():
    rows = []
    for agent in AGENTS:
        with open("docs/data/arena-%s-games-2026-10-29.json" % agent, encoding="utf-8") as f:
            manifest = json.load(f)
        for g in manifest["games"]:
            with open(os.path.join(manifest["rawDirectory"], "%s.json" % g["gameId"]), encoding="utf-8") as f:
                frames = json.load(f)["frames"]
            out = {0: [], 1: []}
            for fr in frames:
                if fr.get("agentId") in (0, 1) and fr.get("stdout"):
                    out[fr["agentId"]].append(fr["stdout"])
            seat = g["seat"]
            rows.append({"win": g["ranks"][seat] < g["ranks"][1 - seat], "turns": (g["frames"] - 1) // 2,
                         "me": builds(out[seat]), "opp": builds(out[1 - seat])})
    n = len(rows)
    print("Arena: v01's placement games, agents %s" % ", ".join(AGENTS))
    for who in ("me", "opp"):
        for k in KINDS:
            used = sum(1 for r in rows if r[who][k])
            print("  %-8s games with BUILD %-16s %3d of %d (%.0f %%)" % ("v01" if who == "me" else "opponent", k, used, n, 100.0 * used / n))
    either = sum(1 for r in rows if r["opp"]["BARRACKS-ARCHER"] or r["opp"]["BARRACKS-GIANT"])
    print("  opponent with archer or giant barracks: %d of %d (%.0f %%)" % (either, n, 100.0 * either / n))
    by = collections.defaultdict(lambda: [0, 0])
    for r in rows:
        by[style(r["opp"])][0] += 1
        by[style(r["opp"])][1] += r["win"]
    for k, (cnt, w) in sorted(by.items(), key=lambda x: -x[1][0]):
        print("  opponent style %-20s %3d games, v01 won %3d (%.0f %%)" % (k, cnt, w, 100.0 * w / cnt))
    lengths("  arena", [r["turns"] for r in rows])


def local():
    for path in PILOTS:
        with open(path, encoding="utf-8") as f:
            rep = json.load(f)
        print("Local: %s vs %s, seeds %d+%d, turnMs %s (%s)" % (rep["botA"], rep["botB"], rep["seedBase"], rep["maps"], rep["turnMs"], path))
        lengths("  local", [g["turns"] for g in rep["games"]])
        maps = collections.defaultdict(list)
        for g in rep["games"]:
            maps[g["seed"]].append(0.5 if g["winner"] == -1 else 1.0 if g["winner"] == g["aSeat"] else 0.0)
        scores = [sum(v) / 2 for v in maps.values()]
        print("  map scores %s, SD %.3f" % (sorted(collections.Counter(scores).items()), statistics.stdev(scores)))


if __name__ == "__main__":
    arena()
    local()
