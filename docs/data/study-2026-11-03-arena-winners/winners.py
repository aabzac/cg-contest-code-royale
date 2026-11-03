"""What the arena opponents who beat v06 do that v06 does not (study 2026-11-03).

Inputs (gitignored, rebuilt from the replays of v06's 120 placement games):
  replays/study/traces/all-v06-ours.txt   cr.tools.DeathTrace <ourAgent> all ..., every turn of our queen
  replays/study/traces/all-v06-opp.txt    the same for each opponent's agent (replays/study/traces/v06-games.txt lists them)
  replays/study/features-v06-2026-11-03.csv   ./gradlew cgStudy over the two upload directories
Usage: python -I docs/data/study-2026-11-03-arena-winners/winners.py  (from the repository root; output winners.txt beside it)
"""
import collections
import csv
import math
import re
import statistics as st

OURS = {"6842260", "6842539"}
HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (\d)/(\d), scores (-?\d+)/(-?\d+), (\d+) turns\)")
LINE = re.compile(r"t\s*(\d+) gold\s+(\d+) q\(\s*(-?\d+),\s*(-?\d+)\) (.*?)\s+touch\s*(-?\d+) \| T(\d) M(\d) B(\d) nearT\s+(-?\d+) "
                  r"\| K250 (\d+) K500 (\d+) nearK\s+(-?\d+) A300 (\d+) G300 (\d+) eTow (\d+) \| hp\s+(\d+) \(-(\d+)\)")


def load(path):
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
            g["rows"].append(dict(t=int(t), gold=int(gold), x=int(qx), y=int(qy), cmd=cmd.strip(), touch=int(touch), T=int(T), M=int(M), B=int(B),
                                  nearT=int(nearT), k250=int(k250), k500=int(k500), nearK=int(nearK), eTow=int(eTow), hp=int(hp), loss=int(loss)))
    return games


def behaviour(games):
    """Per-game behaviour under knights, averaged over the games of a group."""
    acc = collections.defaultdict(list)
    for g in games:
        rows = g["rows"]
        hit = [r for r in rows if r["loss"] > 0 and r["k250"] > 0]
        contact = [r for r in rows if r["k250"] > 0]
        approach = [r for r in rows if r["k250"] == 0 and 0 <= r["nearK"] < 500]
        if not contact:
            continue
        kinds = collections.Counter()
        for r in contact:
            c = r["cmd"].split()
            k = c[0]
            if k == "BUILD":
                k = "topup" if int(c[1]) == r["touch"] and c[2] == "TOWER" else ("build-touched" if int(c[1]) == r["touch"] else "build-walk")
            kinds[k] += 1
        n = len(contact)
        acc["contact turns per game"].append(n)
        acc["HP lost per contact turn"].append(sum(r["loss"] for r in contact) / n)
        acc["knights within 250 when in contact"].append(st.mean(r["k250"] for r in contact))
        for k in ("MOVE", "topup", "build-walk", "build-touched", "WAIT"):
            acc["%% %s while in contact" % k].append(100.0 * kinds[k] / n)
        acc["% at an own tower (<=130) while in contact"].append(100.0 * sum(1 for r in contact if 0 <= r["nearT"] <= 130) / n)
        acc["own towers when in contact"].append(st.mean(r["T"] for r in contact))
        # displacement per contact turn
        moved = []
        for i in range(1, len(rows)):
            if rows[i]["k250"] > 0:
                moved.append(math.hypot(rows[i]["x"] - rows[i - 1]["x"], rows[i]["y"] - rows[i - 1]["y"]))
        if moved:
            acc["distance moved per contact turn"].append(st.mean(moved))
        if approach:
            ak = collections.Counter(r["cmd"].split()[0] for r in approach)
            acc["% MOVE when knights at 250-500"].append(100.0 * ak["MOVE"] / len(approach))
        acc["% MOVE over the whole game"].append(100.0 * sum(1 for r in rows if r["cmd"].startswith("MOVE")) / len(rows))
        acc["HP lost to enemy towers"].append(sum(r["loss"] for r in rows if r["k250"] == 0 and r["eTow"] > 0))
    return {k: (st.median(v), st.mean(v), len(v)) for k, v in acc.items()}


def main():
    ours = load("replays/study/traces/all-v06-ours.txt")
    opp = load("replays/study/traces/all-v06-opp.txt")
    by_id = {g["id"]: g for g in opp}
    rows = list(csv.DictReader(open("replays/study/features-v06-2026-11-03.csv", encoding="utf-8")))
    feats = {}
    for r in rows:
        feats.setdefault(r["gameId"], {})[r["agentId"] in OURS] = r
    out = []
    groups = {
        "v06 in its losses": [g for g in ours if g["rank"] == 1],
        "v06 in its wins": [g for g in ours if g["rank"] == 0],
        "opponents who beat v06": [by_id[g["id"]] for g in ours if g["rank"] == 1 and g["id"] in by_id],
        "opponents who lost to v06": [by_id[g["id"]] for g in ours if g["rank"] == 0 and g["id"] in by_id],
    }
    keys = None
    table = {}
    for name, gs in groups.items():
        table[name] = behaviour(gs)
        keys = keys or list(table[name].keys())
    out.append("Behaviour under knights (median over games; n = games with contact)")
    out.append("%-46s" % "metric" + "".join("%26s" % n for n in groups))
    for k in keys:
        out.append("%-46s" % k + "".join("%26s" % ("%.1f (n=%d)" % (table[n][k][0], table[n][k][2]) if k in table[n] else "-") for n in groups))

    def med(k, rs):
        v = [float(r[k]) for r in rs if r[k] not in ("", None)]
        return st.median(v) if v else float("nan")
    fkeys = ("inc_50", "inc_100", "mines_100", "towers_50", "towers_100", "thp_100", "bk_100", "ba_100", "bg_100", "trainK", "trainA", "trainG", "meanGold", "maxIncome", "towerTopUps", "mineUpgrades", "firstBarracks", "firstTower", "hp_100", "deathTurn")
    fg = {
        "v06 in its losses": [feats[g["id"]][True] for g in groups["v06 in its losses"]],
        "v06 in its wins": [feats[g["id"]][True] for g in groups["v06 in its wins"]],
        "opponents who beat v06": [feats[g["id"]][False] for g in groups["v06 in its losses"]],
        "opponents who lost to v06": [feats[g["id"]][False] for g in groups["v06 in its wins"]],
    }
    out.append("")
    out.append("Economy, towers and waves (medians of cgStudy features)")
    out.append("%-16s" % "feature" + "".join("%26s" % n for n in fg))
    for k in fkeys:
        out.append("%-16s" % k + "".join("%26.1f" % med(k, fg[n]) for n in fg))
    # the winners' openings and barracks counts
    out.append("")
    for name in ("opponents who beat v06", "opponents who lost to v06"):
        c = collections.Counter(r["opening"] for r in fg[name])
        out.append("%s: openings %s" % (name, ", ".join("%s %d" % kv for kv in c.most_common(5))))
        c2 = collections.Counter(int(r["barracksBuiltK"]) + int(r["barracksBuiltA"]) + int(r["barracksBuiltG"]) for r in fg[name])
        out.append("%s: barracks built %s" % (name, ", ".join("%d: %d" % kv for kv in sorted(c2.items()))))
    text = "\n".join(out) + "\n"
    print(text, end="")
    open("docs/data/study-2026-11-03-arena-winners/winners.txt", "w", encoding="utf-8", newline="\n").write(text)


if __name__ == "__main__":
    main()
