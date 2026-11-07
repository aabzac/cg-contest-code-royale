"""Study 2026-11-07: what the opponents rated below 19 who beat us do, against those we beat (v10 against v08).

Usage, from the repository root:
python -B -I docs/data/study-2026-11-07-tower-fire/opponents.py <cgStudy csv of v08> <cgStudy csv of v10>
The csv files are `./gradlew cgStudy` outputs over each version's Silver games (README.md has the commands); the
opponent's score at fetch comes from the game manifests in docs/data/. Prints, per version and outcome, the medians
of our and the opponent's features at turns 50, 100 and 150, the opponent's army and the turn our queen died.
"""
import csv
import json
import statistics
import sys

AGENTS = {
    "v08": {6843976: "arena-6843976-games-2026-11-04.json", 6844095: "arena-6844095-games-2026-11-04.json",
            6847999: "arena-6847999-games-2026-11-04.json"},
    "v10": {6857063: "arena-6857063-games-2026-11-07.json", 6857144: "arena-6857144-games-2026-11-07.json"},
}
LOW = 19.0
FEATURES = ["inc_50", "inc_100", "inc_150", "mines_100", "towers_100", "towers_150", "thp_100", "thp_150", "bk_100",
            "knights_100", "hp_100", "hp_150"]


def med(xs):
    xs = [x for x in xs if x is not None]
    return "%.1f" % statistics.median(xs) if xs else "-"


def num(row, key):
    v = row.get(key, "")
    return float(v) if v not in ("", "-1") else None


def study(name, path):
    ratings = {}
    for agent, manifest in AGENTS[name].items():
        for g in json.load(open("docs/data/" + manifest, encoding="utf-8"))["games"]:
            ratings[g["gameId"]] = g["opponent"].get("ratingAtFetch")
    rows = {}
    for r in csv.DictReader(open(path, encoding="utf-8")):
        rows.setdefault(int(r["gameId"]), []).append(r)
    groups = {"won": [], "lost": []}
    for gid, pair in rows.items():
        ours = [r for r in pair if int(r["agentId"]) in AGENTS[name]]
        theirs = [r for r in pair if int(r["agentId"]) not in AGENTS[name]]
        s = ratings.get(gid)
        if len(ours) != 1 or len(theirs) != 1 or not isinstance(s, (int, float)) or s >= LOW or s < 0:
            continue
        us, them = ours[0], theirs[0]
        outcome = "won" if int(us["rank"]) < int(us["oppRank"]) else "lost" if int(us["rank"]) > int(us["oppRank"]) else None
        if outcome:
            groups[outcome].append((us, them))
    print("%s against opponents rated 0-%g at fetch:" % (name, LOW))
    for outcome, games in groups.items():
        print("  %s %d games (medians; us / them)" % (outcome, len(games)))
        for f in FEATURES:
            print("    %-12s %6s / %-6s" % (f, med([num(u, f) for u, _ in games]), med([num(t, f) for _, t in games])))
        died = [num(u, "deathTurn") for u, _ in games]
        print("    our queen died in %d (median turn %s); went to turn 250: %d" % (
            sum(d is not None for d in died), med(died), sum(int(u["turns"]) >= 250 for u, _ in games)))
        print("    the opponent trained giants in %d, archers in %d; knight trainings %s / %s" % (
            sum((num(t, "trainG") or 0) > 0 for _, t in games), sum((num(t, "trainA") or 0) > 0 for _, t in games),
            med([num(u, "trainK") for u, _ in games]), med([num(t, "trainK") for _, t in games])))
        print("    the opponent's mean income %s, mines built %s, towers built %s, top-ups %s (ours %s, %s, %s, %s)" % (
            med([num(t, "meanIncome") for _, t in games]), med([num(t, "minesBuilt") for _, t in games]),
            med([num(t, "towersBuilt") for _, t in games]), med([num(t, "towerTopUps") for _, t in games]),
            med([num(u, "meanIncome") for u, _ in games]), med([num(u, "minesBuilt") for u, _ in games]),
            med([num(u, "towersBuilt") for u, _ in games]), med([num(u, "towerTopUps") for u, _ in games])))


if __name__ == "__main__":
    study("v08", sys.argv[1])
    study("v10", sys.argv[2])
