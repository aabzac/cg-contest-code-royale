"""Profile of v06's 120 arena games (uploads 1-2) with the tools of the queen's safety study, 2026-11-03.

Inputs (gitignored, rebuilt from the replays):
  replays/study/features-v06-2026-11-03.csv   ./gradlew cgStudy over replays/arena/v06/bronze/upload-0{1,2}/games
  replays/study/traces/all-v06.txt            cr.tools.DeathTrace for agents 6842260 and 6842539
Usage: python -I docs/data/exp-2026-11-03-late-income/profile.py  (from the repository root; output profile.txt beside it)
"""
import collections
import csv
import importlib.util
import statistics as st

OURS = {"6842260", "6842539"}


def main():
    spec = importlib.util.spec_from_file_location("deaths", "docs/data/study-2026-11-03-queen-safety/deaths.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    games = m.load_traces("replays/study/traces/all-v06.txt")
    allrows = list(csv.DictReader(open("replays/study/features-v06-2026-11-03.csv", encoding="utf-8")))
    rows = [r for r in allrows if r["agentId"] in OURS]
    by = {}
    for r in allrows:
        by.setdefault(r["gameId"], {})[r["agentId"] in OURS] = r
    out = []
    out.append("games %d, wins %d, losses %d" % (len(rows), sum(r["rank"] == "0" for r in rows), sum(r["rank"] == "1" for r in rows)))
    losses = [r for r in rows if r["rank"] == "1"]
    dead = [int(r["deathTurn"]) for r in losses if int(r["deathTurn"]) >= 0]
    out.append("losses by the queen's death %d (median turn %s, before turn 80: %d), on HP at the limit %d" % (len(dead), st.median(dead) if dead else "-", sum(t < 80 for t in dead), len(losses) - len(dead)))
    tot = collections.Counter()
    for g in games:
        for r in g["rows"]:
            if r["loss"]:
                tot[("deaths" if g["dead"] else "survived", "knights" if r["k250"] > 0 else "enemy tower" if r["eTow"] > 0 else "other")] += r["loss"]
    out.append("HP lost by source: " + ", ".join("%s/%s %d" % (k[0], k[1], v) for k, v in sorted(tot.items())))
    act, hit, at_tower, first_towers = collections.Counter(), 0, 0, []
    for g in games:
        if not g["dead"]:
            continue
        first = True
        for r in g["rows"]:
            if r["k250"] > 0 and first:
                first_towers.append(r["T"])
                first = False
            if r["loss"] > 0 and r["k250"] > 0:
                hit += 1
                if 0 <= r["nearT"] <= 130:
                    at_tower += 1
                c = r["cmd"].split()
                k = c[0]
                if k == "BUILD":
                    k = ("BUILD touched " if int(c[1]) == r["touch"] else "BUILD walk ") + c[2]
                act[k] += 1
    out.append("deaths: %d hit turns, at her own tower on %d; own towers at the first contact: %s; commands on hit turns: %s"
               % (hit, at_tower, sorted(collections.Counter(first_towers).items()), act.most_common()))
    wins = [r for r in rows if r["rank"] == "0"]
    o = [by[r["gameId"]][False] for r in wins]
    killed = [int(x["deathTurn"]) for x in o if int(x["deathTurn"]) >= 0]
    out.append("wins by killing their queen: %d of %d (median turn %s); on HP at the limit: %d" % (len(killed), len(wins), st.median(killed) if killed else "-", len(wins) - len(killed)))
    c = collections.Counter()
    for r in losses:
        x = by[r["gameId"]][False]
        c[("A" if int(x["trainA"]) > 0 else "") + ("G" if int(x["trainG"]) > 0 else "") + ("K" if int(x["trainK"]) > 0 else "")] += 1
    out.append("losses by the opponent's army: " + ", ".join("%s %d" % kv for kv in sorted(c.items())))

    def med(k, rs):
        v = [int(r[k]) for r in rs if r[k]]
        return st.median(v) if v else "-"
    keys = ("towers_50", "towers_100", "thp_100", "inc_50", "inc_100", "mines_100", "bk_100", "trainK", "towerTopUps", "mineUpgrades", "hp_100")
    out.append("our medians: " + ", ".join("%s %s" % (k, med(k, rows)) for k in keys))
    opp = [by[r["gameId"]][False] for r in losses]
    out.append("opponents who beat us, medians: " + ", ".join("%s %s" % (k, med(k, opp)) for k in keys))
    oppw = [by[r["gameId"]][False] for r in wins]
    out.append("opponents who lost to us, medians: " + ", ".join("%s %s" % (k, med(k, oppw)) for k in keys))
    text = "\n".join(out) + "\n"
    print(text, end="")
    open("docs/data/exp-2026-11-03-late-income/profile.txt", "w", encoding="utf-8", newline="\n").write(text)


if __name__ == "__main__":
    main()
