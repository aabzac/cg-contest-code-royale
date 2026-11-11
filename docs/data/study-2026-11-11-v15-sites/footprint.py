"""The tower-sites rule's footprint in v15's Gold games (2026-11-11): where v14 would have commanded something else.
Input: the output of `./gradlew cgAttribute -Preplays=<v15 games> -Pagent=<agent> -Pbot=v14` for both uploads (probe.sh
beside this file). cgAttribute replays every recorded game through the simulator and, on each turn, asks the in-process
v14 for its two lines on the recorded state; the game goes on by the recorded commands. A DIFF line counts the turns on
which v14's lines differ from v15's recorded ones and names the first. v14's own memory follows the recorded states, so
the count is of v14's answers on v15's positions, not of a game v14 would have played. The same script as the v14
measurement's (docs/data/study-2026-11-10-v14-cover/footprint.py), with v15's manifests and one more table: the first
difference by the turn it comes on and by what it is.

Usage (from the repository root):
  python -B -I docs/data/study-2026-11-11-v15-sites/footprint.py <as-v14-upload-01.txt> <as-v14-upload-02.txt>
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
MANIFESTS = ("arena-6871160-games-2026-11-11.json", "arena-6871202-games-2026-11-11.json")
OLD = "v14"


def kind(rec, bot):
    r, b = rec.split(), bot.split()
    if r[0] == "MOVE" and b[0] == "BUILD":
        return "a MOVE where %s builds or walks to a site (BUILD %s)" % (OLD, b[2])
    if r[0] == "BUILD" and b[0] == "BUILD":
        if r[2] == b[2]:
            return "another site, the same structure (BUILD %s)" % r[2]
        return "BUILD %s where %s has BUILD %s" % (r[2], OLD, b[2])
    if r[0] == "BUILD" and b[0] == "MOVE":
        return "a BUILD %s where %s moves" % (r[2], OLD)
    if r[0] == "MOVE" and b[0] == "MOVE":
        return "a MOVE to another point"
    return "%s where %s has %s" % (r[0], OLD, b[0])


def main():
    won, rating = {}, {}
    for m in MANIFESTS:
        for g in json.load(open(os.path.join("docs", "data", m), encoding="utf-8"))["games"]:
            won[g["gameId"]] = g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]]
            rating[g["gameId"]] = g["opponent"]["ratingAtFetch"]
    games = []
    for upload, path in enumerate(sys.argv[1:3], 1):
        for line in open(path, encoding="utf-8", errors="replace"):
            m = LINE.match(line)
            if m:
                games.append({"same": m.group(1) == "SAME", "gid": int(m.group(2)), "turns": int(m.group(6)), "compared": int(m.group(5)), "diffs": int(m.group(8)),
                              "first": int(m.group(9)) if m.group(9) else None, "rec": m.group(10), "recTrain": m.group(11), "bot": m.group(12), "botTrain": m.group(13),
                              "upload": upload})
    assert len(games) == 180 and all(g["compared"] == g["turns"] for g in games) and set(g["gid"] for g in games) == set(won), len(games)
    diff = [g for g in games if not g["same"]]
    same = [g for g in games if g["same"]]
    turns = sum(g["turns"] for g in games)
    print("v15's 180 Gold games, %d turns: %s would command differently in %d games (%d the same game for game); by upload %s" % (
        turns, OLD, len(diff), len(same), ", ".join("%d of %d" % (sum(1 for g in diff if g["upload"] == u), sum(1 for g in games if g["upload"] == u)) for u in (1, 2))))
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
    print("  what the first difference is (v15's recorded command):")
    for k, c in kinds.most_common():
        fs = sorted(g["first"] for g in diff if kind(g["rec"], g["bot"]) == k)
        print("    %3d  %-62s first on turn %3d (median), from %d to %d" % (c, k, statistics.median(fs), fs[0], fs[-1]))
    print("  the queen's line differs on the first difference in %d games, the TRAIN line in %d" % (
        sum(1 for g in diff if g["rec"] != g["bot"]), sum(1 for g in diff if g["recTrain"] != g["botTrain"])))
    for label, ok in (("won", lambda g: won[g["gid"]]), ("lost", lambda g: not won[g["gid"]])):
        x = [g for g in games if ok(g)]
        print("  %-4s games (%3d): %.1f different turns a game, %.1f %% of their turns; without a difference %d" % (
            label, len(x), sum(g["diffs"] for g in x) / float(len(x)), 100.0 * sum(g["diffs"] for g in x) / sum(g["turns"] for g in x), sum(1 for g in x if g["same"])))
    low = sorted((g for g in games if rating[g["gid"]] < 17 and not won[g["gid"]]), key=lambda g: (g["upload"], g["gid"]))
    print("  the %d losses to opponents rated below 17: %s" % (len(low), "; ".join(
        "%d: %s" % (g["gid"], "turn for turn %s's" % OLD if g["same"] else "%d of %d turns differ, the first at %d" % (g["diffs"], g["turns"], g["first"])) for g in low)))
    print("  by the number of different turns: %s" % "; ".join("%s: %d games, won %d (%.0f %%)" % (
        label, len(x), sum(1 for g in x if won[g["gid"]]), 100.0 * sum(1 for g in x if won[g["gid"]]) / max(1, len(x)))
        for label, x in (("none", same), ("1-5", [g for g in diff if g["diffs"] <= 5]), ("6-10", [g for g in diff if 5 < g["diffs"] <= 10]),
                         ("11-20", [g for g in diff if 10 < g["diffs"] <= 20]), ("more than 20", [g for g in diff if g["diffs"] > 20]))))


if __name__ == "__main__":
    main()
