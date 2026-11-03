"""v07 on the arena against v06 on the arena: outcomes, the queen under knights, the opponents (study 2026-11-03).

Inputs (gitignored, rebuilt from the replays of the 120 placement games of each version):
  replays/study/features-v0{6,7}-2026-11-03.csv            ./gradlew cgStudy over the two upload directories of each
  replays/study/traces/all-v0{6,7}-{ours,opp}.txt          cr.tools.DeathTrace <agent> all ... for both queens
Usage: python -I docs/data/study-2026-11-03-v07-arena/v07.py  (from the repository root; output v07.txt beside it)
"""
import collections
import csv
import importlib.util
import statistics as st

spec = importlib.util.spec_from_file_location("winners", "docs/data/study-2026-11-03-arena-winners/winners.py")
W = importlib.util.module_from_spec(spec)
spec.loader.exec_module(W)

VERSIONS = {"v06": {"6842260", "6842539"}, "v07": {"6843218", "6843287"}}


def features(version):
    rows = list(csv.DictReader(open("replays/study/features-%s-2026-11-03.csv" % version, encoding="utf-8")))
    by = {}
    for r in rows:
        by.setdefault(r["gameId"], {})[r["agentId"] in VERSIONS[version]] = r
    return [r for r in rows if r["agentId"] in VERSIONS[version]], by


def med(k, rs):
    v = [float(r[k]) for r in rs if r[k] not in ("", None)]
    return st.median(v) if v else float("nan")


def main():
    out = []
    data = {}
    for v in ("v06", "v07"):
        ours, by = features(v)
        tr_ours = W.load("replays/study/traces/all-%s-ours.txt" % v)
        tr_opp = {g["id"]: g for g in W.load("replays/study/traces/all-%s-opp.txt" % v)}
        data[v] = (ours, by, tr_ours, tr_opp)
    # 1. outcomes
    out.append("Outcomes (120 placement games each)")
    out.append("%-44s %12s %12s" % ("", "v06", "v07"))
    rowsf = []
    for v in ("v06", "v07"):
        ours, by, tr, _ = data[v]
        wins = [r for r in ours if r["rank"] == "0"]
        losses = [r for r in ours if r["rank"] == "1"]
        dead = [int(r["deathTurn"]) for r in losses if int(r["deathTurn"]) >= 0]
        opp_dead = [int(by[r["gameId"]][False]["deathTurn"]) for r in wins if int(by[r["gameId"]][False]["deathTurn"]) >= 0]
        army = collections.Counter()
        for r in losses:
            x = by[r["gameId"]][False]
            army[("A" if int(x["trainA"]) > 0 else "") + ("G" if int(x["trainG"]) > 0 else "") + ("K" if int(x["trainK"]) > 0 else "")] += 1
        rowsf.append({
            "wins": len(wins), "losses": len(losses),
            "losses by our queen's death": len(dead), "  of them before turn 80": sum(t < 80 for t in dead),
            "  median death turn": st.median(dead) if dead else "-",
            "losses on HP at the limit": len(losses) - len(dead),
            "wins by killing their queen": len(opp_dead), "  median kill turn": st.median(opp_dead) if opp_dead else "-",
            "losses to knights-only opponents": army["K"], "losses to opponents with giants": army["GK"] + army["AGK"] + army["G"],
            "losses to opponents with archers": army["AK"] + army["AGK"] + army["A"],
            "our HP at turn 100 (median)": med("hp_100", ours), "our towers at 100 / HP": "%s / %s" % (med("towers_100", ours), med("thp_100", ours)),
            "our income at 100 / waves": "%s / %s" % (med("inc_100", ours), med("trainK", ours)),
            "our top-ups per game": med("towerTopUps", ours),
        })
    for k in rowsf[0]:
        out.append("%-44s %12s %12s" % (k, rowsf[0][k], rowsf[1][k]))
    # 2. behaviour under knights, ours and the opponents, per version
    out.append("")
    out.append("Behaviour under knights (median over games with contact)")
    groups = {}
    for v in ("v06", "v07"):
        ours, by, tr, tr_opp = data[v]
        groups["%s, losses" % v] = [g for g in tr if g["rank"] == 1]
        groups["%s, wins" % v] = [g for g in tr if g["rank"] == 0]
        groups["beat %s" % v] = [tr_opp[g["id"]] for g in tr if g["rank"] == 1 and g["id"] in tr_opp]
    table = {n: W.behaviour(gs) for n, gs in groups.items()}
    keys = list(table["v07, losses"].keys())
    out.append("%-46s" % "metric" + "".join("%14s" % n for n in groups))
    for k in keys:
        out.append("%-46s" % k + "".join("%14s" % ("%.1f" % table[n][k][0] if k in table[n] else "-") for n in groups))
    # 3. the opponents who beat v07: economy and army
    out.append("")
    out.append("Opponents' medians (cgStudy features)")
    fkeys = ("inc_100", "mines_100", "towers_100", "thp_100", "trainK", "trainA", "trainG", "towerTopUps", "hp_100")
    cols = {}
    for v in ("v06", "v07"):
        ours, by, _, _ = data[v]
        cols["beat %s" % v] = [by[r["gameId"]][False] for r in ours if r["rank"] == "1"]
        cols["lost to %s" % v] = [by[r["gameId"]][False] for r in ours if r["rank"] == "0"]
    out.append("%-16s" % "feature" + "".join("%14s" % n for n in cols))
    for k in fkeys:
        out.append("%-16s" % k + "".join("%14.1f" % med(k, cols[n]) for n in cols))
    # 4. v07's losses one by one
    out.append("")
    out.append("v07's losses: game, seat, opponent, how, our death turn, their army (K/A/G waves), our contact turns, our MOVE % under knights, HP lost per contact turn")
    ours, by, tr, tr_opp = data["v07"]
    trm = {g["id"]: g for g in tr}
    for r in sorted([r for r in ours if r["rank"] == "1"], key=lambda r: int(r["deathTurn"]) if int(r["deathTurn"]) >= 0 else 999):
        x = by[r["gameId"]][False]
        g = trm[r["gameId"]]
        contact = [q for q in g["rows"] if q["k250"] > 0]
        mv = 100.0 * sum(1 for q in contact if q["cmd"].startswith("MOVE")) / len(contact) if contact else 0
        hp = sum(q["loss"] for q in contact) / len(contact) if contact else 0
        how = "death t%s" % r["deathTurn"] if int(r["deathTurn"]) >= 0 else "HP %s:%s" % (r["score"], r["oppScore"])
        out.append("%s seat %s vs %-18s %-12s army %s/%s/%s  contact %3d  MOVE %3.0f %%  HP/turn %.2f" % (
            r["gameId"], r["seat"], x["pseudo"][:18], how, x["trainK"], x["trainA"], x["trainG"], len(contact), mv, hp))
    text = "\n".join(out) + "\n"
    print(text, end="")
    open("docs/data/study-2026-11-03-v07-arena/v07.txt", "w", encoding="utf-8", newline="\n").write(text)


if __name__ == "__main__":
    main()
