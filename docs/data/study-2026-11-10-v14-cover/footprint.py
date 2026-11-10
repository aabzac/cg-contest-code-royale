"""The overlap rule's footprint in v14's Gold games (2026-11-10): where v13 would have commanded something else.
Input: the output of `./gradlew cgAttribute -Preplays=<v14 games> -Pagent=<agent> -Pbot=v13` for both uploads. cgAttribute
replays every recorded game through the simulator and, on each turn, asks the in-process v13 for its two lines on the
recorded state; the game goes on by the recorded commands. A DIFF line counts the turns on which v13's lines differ from
v14's recorded ones and names the first. v13's own memory follows the recorded states, so the count is of v13's answers
on v14's positions, not of a game v13 would have played.

Usage (from the repository root):
  python -B -I docs/data/study-2026-11-10-v14-cover/footprint.py <as-v13-upload-01.txt> <as-v13-upload-02.txt>
Output: stdout (footprint.txt beside this file).
"""
import json
import os
import re
import statistics
import sys
from collections import Counter

LINE = re.compile(r"^(SAME|DIFF) (\d+) seat (\d) vs (.*?): compared (\d+) of (\d+) turns \(validated (\d+)\), diffs (\d+)"
                  r"(?:, first at turn (\d+): recorded \[(.*?) \| (.*?)\] bot \[(.*?) \| (.*?)\])?; marker")
MANIFESTS = ("arena-6868933-games-2026-11-10.json", "arena-6869279-games-2026-11-10.json")


def kind(rec, bot):
    r, b = rec.split(), bot.split()
    if r[0] == "MOVE" and b[0] == "BUILD":
        return "a MOVE where v13 builds or walks to a site (BUILD %s)" % b[2]
    if r[0] == "BUILD" and b[0] == "BUILD":
        if r[2] == b[2]:
            return "another site, the same structure (BUILD %s)" % r[2]
        return "BUILD %s where v13 has BUILD %s" % (r[2], b[2])
    if r[0] == "BUILD" and b[0] == "MOVE":
        return "a BUILD %s where v13 moves" % r[2]
    if r[0] == "MOVE" and b[0] == "MOVE":
        return "a MOVE to another point"
    return "%s where v13 has %s" % (r[0], b[0])


def main():
    won = {}
    for m in MANIFESTS:
        for g in json.load(open(os.path.join("docs", "data", m), encoding="utf-8"))["games"]:
            won[g["gameId"]] = g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
    games = []
    for path in sys.argv[1:3]:
        for line in open(path, encoding="utf-8", errors="replace"):
            m = LINE.match(line)
            if m:
                games.append({"same": m.group(1) == "SAME", "gid": int(m.group(2)), "turns": int(m.group(6)), "compared": int(m.group(5)), "diffs": int(m.group(8)),
                              "first": int(m.group(9)) if m.group(9) else None, "rec": m.group(10), "recTrain": m.group(11), "bot": m.group(12), "botTrain": m.group(13)})
    assert len(games) == 180 and all(g["compared"] == g["turns"] for g in games), len(games)
    diff = [g for g in games if not g["same"]]
    same = [g for g in games if g["same"]]
    turns = sum(g["turns"] for g in games)
    print("v14's 180 Gold games, %d turns: v13 would command differently in %d games (%d the same game for game)" % (turns, len(diff), len(same)))
    print("  the games without a difference: %d turns each (median), %d of them shorter than 100 turns, %d reached turn 250; won %d of %d" % (
        statistics.median(g["turns"] for g in same), sum(1 for g in same if g["turns"] < 100), sum(1 for g in same if g["turns"] >= 250), sum(1 for g in same if won[g["gid"]]), len(same)))
    d = sorted(g["diffs"] for g in diff)
    print("  turns with a different command: %d in all, %.1f %% of the turns; a game with a difference: mean %.1f, median %d, quartiles %d and %d, the most %d" % (
        sum(d), 100.0 * sum(d) / turns, statistics.mean(d), statistics.median(d), d[len(d) // 4], d[3 * len(d) // 4], d[-1]))
    f = sorted(g["first"] for g in diff)
    print("  the first difference: turn %d (median), quartiles %d and %d, the earliest %d; before turn 50 in %d games, in turns 50-99 in %d, 100-149 in %d, later in %d" % (
        statistics.median(f), f[len(f) // 4], f[3 * len(f) // 4], f[0], sum(1 for x in f if x < 50), sum(1 for x in f if 50 <= x < 100), sum(1 for x in f if 100 <= x < 150),
        sum(1 for x in f if x >= 150)))
    kinds = Counter(kind(g["rec"], g["bot"]) for g in diff)
    print("  what the first difference is (v14's recorded command):")
    for k, c in kinds.most_common():
        print("    %3d  %s" % (c, k))
    print("  the queen's line differs on the first difference in %d games, the TRAIN line in %d" % (
        sum(1 for g in diff if g["rec"] != g["bot"]), sum(1 for g in diff if g["recTrain"] != g["botTrain"])))
    for label, ok in (("won", lambda g: won[g["gid"]]), ("lost", lambda g: not won[g["gid"]])):
        x = [g for g in games if ok(g)]
        print("  %-4s games (%3d): %.1f different turns a game, %.1f %% of their turns" % (label, len(x), sum(g["diffs"] for g in x) / float(len(x)),
                                                                                    100.0 * sum(g["diffs"] for g in x) / sum(g["turns"] for g in x)))


if __name__ == "__main__":
    main()
