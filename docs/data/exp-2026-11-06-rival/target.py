"""The calibration target: the strongest Silver opponents of v08 in LocalProfile's terms (2026-11-06).

The four agents rated 20.5 or more in v08's 250 Silver placement games (the boss and three players; the Silver
study, docs/data/study-2026-11-05-silver/). For them as "botA" against v08: their score, the medians at turn 100
(income, mines, towers and their HP, queen HP; cgStudy), knight and giant trainings, game length and limit share,
and their queen under our knights (DeathTrace of their queen: contact = one of our knights within 250).
Usage (from the repository root): python -B -I docs/data/exp-2026-11-06-rival/target.py [--data <main checkout>]
"""
import argparse
import importlib.util
import json
import os
import statistics as st
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
spec = importlib.util.spec_from_file_location("silver", os.path.join(REPO, "docs", "data", "study-2026-11-05-silver", "silver.py"))
S = importlib.util.module_from_spec(spec)
spec.loader.exec_module(S)


def med(v):
    v = [x for x in v if x is not None]
    return st.median(v) if v else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=REPO)
    a = ap.parse_args()
    F = S.load_features(os.path.join(a.data, "replays", "study", "features-v08-silver-2026-11-05.csv"))
    opp = S.load_traces(os.path.join(a.data, "replays", "study", "traces", "all-v08s-opp.txt"))
    rating = {}
    for ag in S.AGENTS:
        for g in json.load(open(os.path.join(REPO, "docs", "data", "arena-%s-games-2026-11-04.json" % ag), encoding="utf-8"))["games"]:
            rating[str(g["gameId"])] = g["opponent"]["ratingAtFetch"] or 0
    sys.stdout.reconfigure(encoding="utf-8")
    for label, sel in (("the strongest four (rated 20.5+)", lambda r: r >= S.TOP), ("the rest of the Silver field", lambda r: r < S.TOP)):
        ids = [g for g in F if sel(rating[g])]
        their = [1.0 - S.outcome(F[g]) for g in ids]
        num = lambda g, k: S.num(F[g]["opp"][k])
        turns = [int(F[g]["me"]["turns"]) for g in ids]
        contact, near, per, move = [], [], [], []
        for g in ids:
            rows = opp[g]["rows"]
            c = [r for r in rows if r["k250"] > 0]
            if not c:
                continue
            contact.append(len(c))
            near.append(100.0 * sum(1 for r in c if 0 <= r["nearT"] <= 130) / len(c))
            per.append(sum(r["loss"] for r in c) / len(c))
            move.append(100.0 * sum(1 for r in c if r["cmd"].startswith("MOVE")) / len(c))
        print("%s: %d games against v08, their score %.0f %%" % (label, len(ids), 100 * st.mean(their)))
        print("  turn 100: income %g mines %g towers %g towerHp %g queenHp %g" % (
            med(num(g, "inc_100") for g in ids), med(num(g, "mines_100") for g in ids), med(num(g, "towers_100") for g in ids),
            med(num(g, "thp_100") for g in ids), med(num(g, "hp_100") for g in ids)))
        print("  trainings: knight %g, giant %g (medians); games with giants %d of %d; game length median %g, mean %.1f; at the limit %.0f %%" % (
            med(num(g, "trainK") for g in ids), med(num(g, "trainG") for g in ids), sum(1 for g in ids if num(g, "trainG") > 0), len(ids),
            st.median(turns), st.mean(turns), 100.0 * sum(t >= 250 for t in turns) / len(turns)))
        print("  under our knights (medians over games with contact): contact turns %g, %% near an own tower %.1f, HP lost per contact turn %.2f, %% MOVE %.1f" % (
            med(contact), med(near), med(per), med(move)))


if __name__ == "__main__":
    main()
