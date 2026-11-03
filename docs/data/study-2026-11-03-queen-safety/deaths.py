"""How v05's queen loses HP and dies in its arena games (study 2026-11-03, queen safety).

Inputs (gitignored, rebuilt from the replays):
  replays/study/features-v05-3up-2026-11-03.csv   ./gradlew cgStudy over the three v05 upload directories
  replays/study/traces/all-v05.txt                 cr.tools.DeathTrace for agents 6841317, 6841407, 6841579
Usage: python -I docs/data/study-2026-11-03-queen-safety/deaths.py  (from the repository root; output deaths.txt beside it)
"""
import collections
import csv
import re
import statistics as st

OURS = {"6841317", "6841407", "6841579"}
HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (\d)/(\d), scores (-?\d+)/(-?\d+), (\d+) turns\)")
LINE = re.compile(r"t\s*(\d+) gold\s+(\d+) q\(\s*(-?\d+),\s*(-?\d+)\) (.*?)\s+touch\s*(-?\d+) \| T(\d) M(\d) B(\d) nearT\s+(-?\d+) "
                  r"\| K250 (\d+) K500 (\d+) nearK\s+(-?\d+) A300 (\d+) G300 (\d+) eTow (\d+) \| hp\s+(\d+) \(-(\d+)\)")


def load_traces(path):
    games, g = [], None
    for line in open(path, encoding="utf-8"):
        m = HDR.match(line)
        if m:
            g = {"id": m.group(1), "seat": int(m.group(2)), "opp": m.group(3), "rank": int(m.group(4)), "turns": int(m.group(8)), "rows": [], "dead": False}
            games.append(g)
            continue
        if "queen dead" in line:
            g["dead"] = True
            continue
        m = LINE.match(line)
        if m and g is not None:
            t, gold, qx, qy, cmd, touch, T, M, B, nearT, k250, k500, nearK, a, gi, eTow, hp, loss = m.groups()
            g["rows"].append(dict(t=int(t), cmd=cmd.strip(), touch=int(touch), T=int(T), nearT=int(nearT), k250=int(k250), k500=int(k500),
                                  nearK=int(nearK), a=int(a), gi=int(gi), eTow=int(eTow), hp=int(hp), loss=int(loss)))
    return games


def main():
    out = []
    games = load_traces("replays/study/traces/all-v05.txt")
    rows = [r for r in csv.DictReader(open("replays/study/features-v05-3up-2026-11-03.csv", encoding="utf-8")) if r["agentId"] in OURS]
    out.append("games %d, wins %d, losses %d" % (len(rows), sum(r["rank"] == "0" for r in rows), sum(r["rank"] == "1" for r in rows)))
    losses = [r for r in rows if r["rank"] == "1"]
    dead = [int(r["deathTurn"]) for r in losses if int(r["deathTurn"]) >= 0]
    out.append("losses by the queen's death %d (median turn %d, before turn 80: %d), on HP at the limit %d" % (len(dead), st.median(dead), sum(t < 80 for t in dead), len(losses) - len(dead)))
    tot = collections.Counter()
    for g in games:
        for r in g["rows"]:
            if r["loss"]:
                tot[("deaths" if g["dead"] else "survived", "knights" if r["k250"] > 0 else "enemy tower" if r["eTow"] > 0 else "other")] += r["loss"]
    out.append("HP lost by source: " + ", ".join("%s/%s %d" % (k[0], k[1], v) for k, v in sorted(tot.items())))
    act, first_towers, hit, at_tower, k_when_hit = collections.Counter(), [], 0, 0, []
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
                k_when_hit.append(r["k250"])
                if 0 <= r["nearT"] <= 130:
                    at_tower += 1
                c = r["cmd"].split()
                kind = c[0]
                if kind == "BUILD":
                    kind = ("BUILD touched " if int(c[1]) == r["touch"] else "BUILD walk ") + c[2]
                act[kind] += 1
    out.append("deaths: %d hit turns; at her own tower (within 130) on %d of them; knights on her: median %d" % (hit, at_tower, st.median(k_when_hit)))
    out.append("deaths: the command on hit turns: " + ", ".join("%s %d" % kv for kv in act.most_common()))
    out.append("deaths: own towers at the first contact: " + ", ".join("%d towers: %d" % kv for kv in sorted(collections.Counter(first_towers).items())))
    start = {g["id"]: g["rows"][0]["hp"] + g["rows"][0]["loss"] for g in games if g["rows"]}
    buck = collections.defaultdict(lambda: [0, 0, 0, 0])
    for r in rows:
        s = start.get(r["gameId"])
        if s is None:
            s = int(r["hp_25"]) if r["hp_25"] else int(r["score"])
        b = "25-40" if s <= 40 else "45-60" if s <= 60 else "65-80" if s <= 80 else "85-100"
        buck[b][0] += 1
        buck[b][1] += r["rank"] == "0"
        buck[b][2] += r["rank"] == "1" and int(r["deathTurn"]) >= 0
        buck[b][3] += r["rank"] == "1" and 0 <= int(r["deathTurn"]) < 80
    for b in sorted(buck):
        n, w, d, e = buck[b]
        out.append("start HP %s: games %d, wins %d (%.0f %%), deaths %d, early deaths %d" % (b, n, w, 100 * w / n, d, e))
    text = "\n".join(out) + "\n"
    print(text, end="")
    open("docs/data/study-2026-11-03-queen-safety/deaths.txt", "w", encoding="utf-8", newline="\n").write(text)


if __name__ == "__main__":
    main()
