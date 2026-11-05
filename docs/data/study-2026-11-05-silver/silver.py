"""v08's 250 Silver placement games: how the HP races and the queen's deaths are decided, and how the local
opponents compare with the Silver field (study 2026-11-05).

Inputs (gitignored, rebuilt from the replays of v08's Silver uploads 3-5 and from local series; commands in README.md):
  replays/study/features-v08-silver-2026-11-05.csv     ./gradlew cgStudy over the three upload directories
  replays/study/features-v08-bronze-2026-11-05.csv     the same over v08's two Bronze uploads (context)
  replays/study/traces/all-v08s-ours.txt               ./gradlew -q deathTrace -Pagent=<ours> -Pall=1 ... (our queen)
  replays/study/traces/all-v08s-opp.txt                the same with -Pfoe=1 (the opponents' queens)
  replays/bench/study-2026-11-05-silver/*.json         the local bench series (development seeds 150000-150499)
Tracked inputs: docs/data/arena-<agent>-games-2026-11-04.json (opponent ratings), local-profiles.txt beside this file.
Usage (from the repository root): python -B -I docs/data/study-2026-11-05-silver/silver.py [--data <root of replays/>]
Output: silver.txt beside this file.
"""
import argparse
import collections
import csv
import importlib.util
import json
import math
import os
import re
import statistics as st
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
AGENTS = ("6843976", "6844095", "6847999")
BOSS = "1876720"
TOP = 20.5
HDR = re.compile(r"=== game (\d+) seat (\d) vs (.*?) \(ranks (\d)/(\d), scores (-?\d+)/(-?\d+), (\d+) turns\)(?: agent (\d+))?")
LINE = re.compile(r"t\s*(\d+) gold\s+(\d+) q\(\s*(-?\d+),\s*(-?\d+)\) (.*?)\s+touch\s*(-?\d+) \| T(\d) M(\d) B(\d) nearT\s+(-?\d+) "
                  r"\| K250 (\d+) K500 (\d+) nearK\s+(-?\d+) A300 (\d+) G300 (\d+) eTow (\d+) \| hp\s+(\d+) \(-(\d+)\) \| kd (\d+) td (\d+)")
KEYS = ("t", "gold", "x", "y", "cmd", "touch", "T", "M", "B", "nearT", "k250", "k500", "nearK", "a300", "g300", "eTow", "hp", "loss", "kd", "td")
OUT = []


def say(s=""):
    OUT.append(s)


def load_traces(path):
    games, g = {}, None
    for line in open(path, encoding="utf-8"):
        m = HDR.match(line)
        if m:
            g = {"id": m.group(1), "rank": int(m.group(4)), "turns": int(m.group(8)), "agent": m.group(9), "rows": [], "dead": False}
            games[g["id"]] = g
        elif "queen dead" in line:
            g["dead"] = True
        else:
            m = LINE.match(line)
            if m:
                v = m.groups()
                g["rows"].append({k: (x.strip() if k == "cmd" else int(x)) for k, x in zip(KEYS, v)})
    return games


def load_features(path):
    by = collections.defaultdict(dict)
    for r in csv.DictReader(open(path, encoding="utf-8")):
        by[r["gameId"]]["me" if r["pseudo"] == "aabzac" else "opp"] = r
    return {k: v for k, v in by.items() if "me" in v and "opp" in v}


def num(x):
    return float(x) if x not in ("", None) else None


def med(v):
    v = [x for x in v if x is not None]
    return round(st.median(v), 1) if v else "-"


def mean(v):
    v = [x for x in v if x is not None]
    return round(st.mean(v), 1) if v else "-"


def outcome(f):
    s, o = int(f["me"]["score"]), int(f["me"]["oppScore"])
    return 1.0 if s > o else (0.0 if s < o else 0.5)


def group(f):
    o, lim = outcome(f), int(f["me"]["turns"]) >= 250
    if o == 0.5:
        return "draw"
    return ("won" if o == 1 else "lost") + (" at the limit" if lim else " before it")


GROUPS = ("lost at the limit", "won at the limit", "lost before it", "won before it")


def window(rows, a, b):
    return [r for r in rows if a <= r["t"] < b]


def hp_after(rows, t):
    """The queen's HP after the turn with index t (the last row before it if the game ended)."""
    last = None
    for r in rows:
        if r["t"] > t:
            break
        last = r
    return last["hp"] if last else None


def kind(r):
    c = r["cmd"].split()
    if c[0] == "MOVE":
        return "MOVE"
    if c[0] == "BUILD":
        if int(c[1]) == r["touch"]:
            return "top-up" if c[2] == "TOWER" and r["T"] > 0 else "build where she stands"
        return "walk to build"
    return c[0]


def first_contact(rows):
    c = [r for r in rows if r["k250"] > 0]
    if not c:
        return None
    t0 = c[0]["t"]
    w = window(rows, t0, t0 + 20)
    return {"turn": t0, "towers": c[0]["T"], "nearT": c[0]["nearT"], "lost20": sum(r["loss"] for r in w),
            "knights": max(r["k250"] for r in w), "rows": [r for r in w if r["k250"] > 0]}


def start_hp_of_seed(seed):
    spec = importlib.util.spec_from_file_location("start_hp", os.path.join(HERE, "start_hp.py"))
    if "start_hp" not in sys.modules:
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        sys.modules["start_hp"] = m
    return sys.modules["start_hp"].start_hp(seed)


def arena_compare():
    spec = importlib.util.spec_from_file_location("arena_compare", os.path.join(REPO, "tools", "arena_compare.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def section_outcomes(F, Fb):
    say("1. Outcomes")
    for label, feats in (("Silver, 3 uploads", F), ("Bronze, 2 uploads (context)", Fb)):
        n = len(feats)
        c = collections.Counter(group(f) for f in feats.values())
        turns = [int(f["me"]["turns"]) for f in feats.values()]
        lim = sum(t >= 250 for t in turns)
        deaths = [int(f["me"]["deathTurn"]) for f in feats.values() if group(f) == "lost before it"]
        kills = [int(f["opp"]["deathTurn"]) for f in feats.values() if group(f) == "won before it" and int(f["opp"]["deathTurn"]) >= 0]
        say("  %-28s %d games: won %d (at the limit %d), lost %d (at the limit %d), draws %d; turns mean %.1f, median %s; "
            "at the 250 limit %d (%.0f %%); our queen died at turn %s (median), theirs at %s" % (
                label, n, c["won at the limit"] + c["won before it"], c["won at the limit"], c["lost at the limit"] + c["lost before it"],
                c["lost at the limit"], c["draw"], st.mean(turns), st.median(turns), lim, 100 * lim / n, med(deaths), med(kills)))
    say()


def start_hp(rows):
    return rows[0]["hp"] + rows[0]["loss"]


def section_start_hp(F, ours):
    say("1b. By the queens' starting HP (equal for both, 25-100 in steps of 5; the map generator's first draw)")
    bands = ((25, 40), (45, 60), (65, 80), (85, 100))
    for lo, hi in bands:
        ids = [g for g in F if lo <= start_hp(ours[g]["rows"]) <= hi]
        if not ids:
            continue
        lim = sum(1 for g in ids if int(F[g]["me"]["turns"]) >= 250)
        died = sum(1 for g in ids if ours[g]["dead"])
        say("  %3d-%-3d %3d games, score %.0f %%, at the limit %.0f %%, our queen died in %.0f %%, turns median %s" % (
            lo, hi, len(ids), 100 * st.mean(outcome(F[g]) for g in ids), 100 * lim / len(ids), 100 * died / len(ids),
            st.median(int(F[g]["me"]["turns"]) for g in ids)))
    say()


def section_hp_race(F, ours, opp):
    say("2. The HP race: games that reached turn 250")
    lim = {g: f for g, f in F.items() if int(f["me"]["turns"]) >= 250 and outcome(f) != 0.5}
    margins = sorted(int(f["me"]["score"]) - int(f["me"]["oppScore"]) for f in lim.values())
    lm, wm = [m for m in margins if m < 0], [m for m in margins if m > 0]
    say("  %d decided games: %d lost, %d won" % (len(lim), len(lm), len(wm)))
    for k in (5, 10, 20):
        say("    lost by <= %2d HP: %2d of %d; won by <= %2d HP: %2d of %d" % (k, sum(-m <= k for m in lm), len(lm), k, sum(m <= k for m in wm), len(wm)))
    a_l = sum(1 for g in lim if hp_after(ours[g]["rows"], 199) > hp_after(opp[g]["rows"], 199) and outcome(F[g]) == 0)
    b_w = sum(1 for g in lim if hp_after(ours[g]["rows"], 199) < hp_after(opp[g]["rows"], 199) and outcome(F[g]) == 1)
    say("    ahead after turn 200 but lost: %d; behind after turn 200 but won: %d" % (a_l, b_w))
    say()
    say("  Our HP lead (ours - theirs) after each checkpoint, and its mean change per window")
    cps = (-1, 24, 49, 99, 149, 199, 249)

    def lead(g, t):
        return 0 if t < 0 else hp_after(ours[g]["rows"], t) - hp_after(opp[g]["rows"], t)
    for name in ("lost at the limit", "won at the limit"):
        ids = [g for g, f in F.items() if group(f) == name]
        say("    %-18s n=%2d  " % (name, len(ids)) + "  ".join(
            "after %d: ahead %d / behind %d" % (t + 1, sum(lead(g, t) > 0 for g in ids), sum(lead(g, t) < 0 for g in ids)) for t in cps[1:]))
        say("    %-18s       change per window: " % "" + "  ".join(
            "%d-%d %+.1f" % (a + 1, b + 1, st.mean(lead(g, b) - lead(g, a) for g in ids)) for a, b in zip(cps, cps[1:])))
    say()
    say("  Limit games: our HP lost on turns 100-200 and 200-250 (means), our and their towers after turns 150/200/249 (medians)")

    def towers(rows, t):
        r = [x for x in rows if x["t"] <= t]
        return r[-1]["T"] if r else None
    for name in ("lost at the limit", "won at the limit"):
        for gsel in (True, False):
            ids = [g for g, f in F.items() if group(f) == name and (int(f["opp"]["trainG"]) > 0) == gsel]
            say("    %-18s %-9s n=%2d  ours: 100-200 %.1f, 200-250 %.1f (contact turns %.1f) | theirs 200-250 %.1f | towers ours %s/%s/%s, theirs %s/%s/%s" % (
                name, "giants" if gsel else "no giants", len(ids), st.mean(sum(r["loss"] for r in window(ours[g]["rows"], 100, 200)) for g in ids),
                st.mean(sum(r["loss"] for r in window(ours[g]["rows"], 200, 250)) for g in ids),
                st.mean(sum(1 for r in window(ours[g]["rows"], 200, 250) if r["k250"] > 0) for g in ids),
                st.mean(sum(r["loss"] for r in window(opp[g]["rows"], 200, 250)) for g in ids),
                med(towers(ours[g]["rows"], 150) for g in ids), med(towers(ours[g]["rows"], 200) for g in ids), med(towers(ours[g]["rows"], 249) for g in ids),
                med(towers(opp[g]["rows"], 150) for g in ids), med(towers(opp[g]["rows"], 200) for g in ids), med(towers(opp[g]["rows"], 249) for g in ids)))
    say()
    say("  The games lost at the limit after leading after turn 200")
    for g, f in sorted(F.items()):
        if group(f) != "lost at the limit" or not hp_after(ours[g]["rows"], 199) > hp_after(opp[g]["rows"], 199):
            continue
        late = window(ours[g]["rows"], 200, 250)
        kinds = collections.Counter(kind(r) for r in late if r["loss"] > 0)
        say("    %s %-9s after 200 %3d:%-3d end %3s:%-3s | our loss on 200-250 %2d (knights %2d, towers %d), contact turns %2d, own towers %d -> %d; "
            "the losing turns: %s" % (g, "giants" if int(f["opp"]["trainG"]) > 0 else "no giants", hp_after(ours[g]["rows"], 199), hp_after(opp[g]["rows"], 199),
                                      f["me"]["score"], f["me"]["oppScore"], sum(r["loss"] for r in late), sum(r["kd"] for r in late),
                                      sum(r["td"] for r in late), sum(1 for r in late if r["k250"] > 0), late[0]["T"], late[-1]["T"],
                                      ", ".join("%s %d" % kv for kv in kinds.most_common())))
    say()
    say("  HP lost per window (mean over games of the group; ours = knights + towers | theirs = knights + towers)")
    wins = ((0, 25), (25, 50), (50, 100), (100, 150), (150, 200), (200, 250))
    say("    %-18s %4s  " % ("group", "n") + "  ".join("%-17s" % ("%d-%d" % w) for w in wins))
    for name in GROUPS:
        ids = [g for g, f in F.items() if group(f) == name]
        cells = []
        for a, b in wins:
            ok = st.mean(sum(r["kd"] for r in window(ours[g]["rows"], a, b)) for g in ids)
            ot = st.mean(sum(r["td"] for r in window(ours[g]["rows"], a, b)) for g in ids)
            tk = st.mean(sum(r["kd"] for r in window(opp[g]["rows"], a, b)) for g in ids)
            tt = st.mean(sum(r["td"] for r in window(opp[g]["rows"], a, b)) for g in ids)
            cells.append("%-17s" % ("%.1f+%.1f|%.1f+%.1f" % (ok, ot, tk, tt)))
        say("    %-18s %4d  %s" % (name, len(ids), "  ".join(cells)))
    tot = [sum(r[k] for g in ours.values() for r in g["rows"]) for k in ("loss", "kd", "td")]
    tto = [sum(r[k] for g in opp.values() for r in g["rows"]) for k in ("loss", "kd", "td")]
    turns = sum(1 for g in ours.values() for r in g["rows"] if r["loss"] > 0)
    off = sum(1 for g in ours.values() for r in g["rows"] if r["kd"] + r["td"] != r["loss"])
    say("  All games: our queen lost %d HP (knights %d, towers %d), theirs %d (our knights %d, our towers %d); "
        "the split misses the loss on %d of %d turns with a loss" % (tot[0], tot[1], tot[2], tto[0], tto[1], tto[2], off, turns))
    say()


def section_first_contact(F, ours, opp):
    say("3. The first contact (the first turn an enemy knight is within 250 of the queen)")
    say("  medians over the games of each group; 'in 20 turns' counts the first contact turn and the 19 after it")
    for name in GROUPS:
        ids = [g for g, f in F.items() if group(f) == name]
        us = [first_contact(ours[g]["rows"]) for g in ids]
        th = [first_contact(opp[g]["rows"]) for g in ids]
        us, th = [x for x in us if x], [x for x in th if x]
        kinds = collections.Counter(kind(r) for x in us for r in x["rows"])
        n = sum(kinds.values())
        at = [1 if 0 <= r["nearT"] <= 130 else 0 for x in us for r in x["rows"]]
        say("  %-18s ours: turn %s, own towers then %s, knights within 250 up to %s, HP lost in 20 turns %s (mean %s) | "
            "theirs: turn %s, own towers then %s, HP lost in 20 turns %s (mean %s)" % (
                name, med(x["turn"] for x in us), med(x["towers"] for x in us), med(x["knights"] for x in us), med(x["lost20"] for x in us),
                mean(x["lost20"] for x in us), med(x["turn"] for x in th), med(x["towers"] for x in th), med(x["lost20"] for x in th),
                mean(x["lost20"] for x in th)))
        say("  %-18s   our contact turns in those 20: %s; at an own tower (within 130) %.0f %%" % (
            "", ", ".join("%s %.0f %%" % (k, 100 * v / n) for k, v in kinds.most_common()), 100 * st.mean(at)))
    say()
    say("  By our towers at the first contact (all games)")
    b = collections.defaultdict(list)
    for g, f in F.items():
        x = first_contact(ours[g]["rows"])
        if x:
            b[min(x["towers"], 3)].append((x, outcome(f), first_contact(opp[g]["rows"])))
    for k in sorted(b):
        v = b[k]
        say("    %d%s own towers: %3d games, first contact turn %s, HP lost in 20 turns median %s (mean %s), score %.0f %%; "
            "their first contact turn %s" % (k, "+" if k == 3 else " ", len(v), med(x["turn"] for x, _, _ in v), med(x["lost20"] for x, _, _ in v),
                                             mean(x["lost20"] for x, _, _ in v), 100 * st.mean(o for _, o, _ in v),
                                             med(y["turn"] for _, _, y in v if y)))
    say()
    say("  The turn of the k-th own tower (median over games; the first contact for reference)")
    for name in GROUPS:
        ids = [g for g, f in F.items() if group(f) == name]

        def nth(rows, k):
            return next((r["t"] for r in rows if r["T"] >= k), None)
        say("    %-18s ours 1st/2nd/3rd at %s/%s/%s, first contact %s | theirs %s/%s/%s, first contact %s" % (
            name, med(nth(ours[g]["rows"], 1) for g in ids), med(nth(ours[g]["rows"], 2) for g in ids), med(nth(ours[g]["rows"], 3) for g in ids),
            med((first_contact(ours[g]["rows"]) or {}).get("turn") for g in ids),
            med(nth(opp[g]["rows"], 1) for g in ids), med(nth(opp[g]["rows"], 2) for g in ids), med(nth(opp[g]["rows"], 3) for g in ids),
            med((first_contact(opp[g]["rows"]) or {}).get("turn") for g in ids)))
    say()
    say("  The opening (cgStudy: medians; knights alive at turn 25 are the first waves)")
    keys = ("firstMine", "firstBarracks", "firstTower", "towers_25", "knights_25", "inc_25", "hp_25", "hp_50")
    say("    %-18s %-5s " % ("group", "who") + " ".join("%13s" % k for k in keys))
    for name in GROUPS:
        ids = [g for g, f in F.items() if group(f) == name]
        for who in ("me", "opp"):
            say("    %-18s %-5s " % (name, "ours" if who == "me" else "them") + " ".join(
                "%13s" % med(num(F[g][who][k]) for g in ids if num(F[g][who][k]) is None or num(F[g][who][k]) >= 0) for k in keys))
    say()


def section_deaths(F, ours):
    say("4. Our queen's deaths")
    deaths = [g for g in ours if ours[g]["dead"] and outcome(F[g]) == 0]
    for label, sel in (("against giants", True), ("against no giants", False)):
        ids = [g for g in deaths if (int(F[g]["opp"]["trainG"]) > 0) == sel]
        last = {g: ours[g]["rows"][-30:] for g in ids}
        say("  %-17s %2d deaths, turn %s (median) | own towers: most over the game %s, 30 turns before %s, at death %s | "
            "enemy knights within 250 per turn %s, at an own tower %s %% | HP 30 turns before %s, lost to knights %s, to towers %s" % (
                label, len(ids), med(ours[g]["rows"][-1]["t"] + 1 for g in ids), med(max(r["T"] for r in ours[g]["rows"]) for g in ids),
                med(last[g][0]["T"] for g in ids), med(last[g][-1]["T"] for g in ids), med(st.mean(r["k250"] for r in last[g]) for g in ids),
                med(100 * st.mean(1 if 0 <= r["nearT"] <= 130 else 0 for r in last[g]) for g in ids),
                med(last[g][0]["hp"] + last[g][0]["loss"] for g in ids), med(sum(r["kd"] for r in last[g]) for g in ids),
                med(sum(r["td"] for r in last[g]) for g in ids)))
    say()


def behaviour(rows, a, b):
    w = window(rows, a, b)
    if not w:
        return None
    c = [r for r in w if r["k250"] > 0]
    moved = [math.hypot(w[i]["x"] - w[i - 1]["x"], w[i]["y"] - w[i - 1]["y"]) for i in range(1, len(w)) if w[i]["k250"] > 0]
    return {"HP lost": sum(r["loss"] for r in w), "contact turns": len(c),
            "HP per contact turn": sum(r["kd"] for r in c) / len(c) if c else None,
            "% at an own tower": 100 * sum(1 for r in c if 0 <= r["nearT"] <= 130) / len(c) if c else None,
            "% MOVE": 100 * sum(1 for r in c if kind(r) == "MOVE") / len(c) if c else None,
            "% top-up": 100 * sum(1 for r in c if kind(r) == "top-up") / len(c) if c else None,
            "moved per contact turn": st.mean(moved) if moved else None, "own towers": st.mean(r["T"] for r in w)}


def section_opponents(F, ours, opp, rating, ac):
    say("5. Opponent strength, giants and the strongest opponents")
    games = list(F)
    pts = {g: outcome(F[g]) for g in games}
    gi = [g for g in games if int(F[g]["opp"]["trainG"]) > 0]
    ng = [g for g in games if int(F[g]["opp"]["trainG"]) == 0]
    arch = sum(1 for g in games if int(F[g]["opp"]["trainA"]) > 0)
    say("  Opponents who trained giants: %d of %d games (%.0f %%), archers: %d" % (len(gi), len(games), 100 * len(gi) / len(games), arch))
    allg = [(pts[g], rating[g][0]) for g in games]
    scale, fitted = ac.fit_scale([allg])
    say("  Logistic scale fitted on all 250 games (one rating): %.2f (%s); the profile log-likelihood by scale: %s" % (
        scale, "fitted" if fitted else "default",
        ", ".join("%.2f: %.1f" % (s, ac.fit_rating(allg, s)[2]) for s in (1.33, 2.0, 2.5, scale, 4.0, 5.0, 7.0))))
    for label, ids in (("against giants", gi), ("against no giants", ng)):
        r, se, _ = ac.fit_rating([(pts[g], rating[g][0]) for g in ids], scale)
        r2, se2, _ = ac.fit_rating([(pts[g], rating[g][0]) for g in ids], 1.33)
        say("    %-17s %3d games, score %.0f %%, opponents' rating median %.2f; performance rating %.2f ± %.2f (scale %.2f), %.2f ± %.2f (Bronze's scale 1.33)" % (
            label, len(ids), 100 * st.mean(pts[g] for g in ids), st.median(rating[g][0] for g in ids), r, se, scale, r2, se2))
    gl = [(pts[g], rating[g][0]) for g in gi]
    nl = [(pts[g], rating[g][0]) for g in ng]
    s_two, _ = ac.fit_scale([gl, nl])
    ll_one = ac.fit_rating(allg, scale)[2]
    ll_two = ac.fit_rating(gl, s_two)[2] + ac.fit_rating(nl, s_two)[2]
    lr = 2 * (ll_two - ll_one)
    say("    one rating against separate ratings for giant and other opponents, the scale re-fitted in each (%.2f, %.2f): LR %.2f on 1 df, p %.3f" % (
        scale, s_two, lr, math.erfc(math.sqrt(max(0.0, lr) / 2))))
    top = [g for g in games if rating[g][0] >= TOP]
    rest = [g for g in games if rating[g][0] < TOP]
    say("  Opponents rated %.1f or more: %d games against %d agents, score %.1f (%.0f %%); with giants %d games, score %.1f; without %d, score %.1f" % (
        TOP, len(top), len({rating[g][1] for g in top}), sum(pts[g] for g in top), 100 * st.mean(pts[g] for g in top),
        sum(1 for g in top if g in gi), sum(pts[g] for g in top if g in gi), sum(1 for g in top if g not in gi), sum(pts[g] for g in top if g not in gi)))
    for label, ids in (("top", top), ("rest", rest)):
        lost = [g for g in ids if pts[g] == 0]
        say("    %-4s %3d games, lost %d: our queen died in %d (turn %s), at the limit %d" % (
            label, len(ids), len(lost), sum(1 for g in lost if ours[g]["dead"]), med(ours[g]["rows"][-1]["t"] + 1 for g in lost if ours[g]["dead"]),
            sum(1 for g in lost if not ours[g]["dead"])))
    say()
    say("  Both queens, means over games (window turns 25-100, then 100-250; 'contact' = an enemy knight within 250)")
    for a, b in ((25, 100), (100, 250)):
        for label, ids in (("top", top), ("rest", rest)):
            for who, src in (("ours", ours), ("theirs", opp)):
                acc = collections.defaultdict(list)
                for g in ids:
                    x = behaviour(src[g]["rows"], a, b)
                    if x:
                        for k, v in x.items():
                            if v is not None:
                                acc[k].append(v)
                say("    %3d-%3d %-4s %-6s " % (a, b, label, who) + "  ".join("%s %.1f" % (k, st.mean(v)) for k, v in acc.items()))
    say()
    say("  The strongest agents one by one, turns 100-250 (means): our queen | theirs; knight waves trained (median)")
    by = collections.defaultdict(list)
    for g in top:
        by[rating[g][1]].append(g)
    for agent, ids in sorted(by.items(), key=lambda kv: -max(rating[g][0] for g in kv[1])):
        def m(src, k):
            v = [behaviour(src[g]["rows"], 100, 250) for g in ids]
            v = [x[k] for x in v if x and x[k] is not None]
            return ("%.1f" % st.mean(v)) if v else "-"
        say("    %s%-8s %5.2f %2d games, score %4.1f, giants in %2d | ours: HP lost %s, contact %s, per contact %s | theirs: HP lost %s, contact %s, per contact %s | waves %s against our %s" % (
            "boss " if agent == BOSS else "", agent, max(rating[g][0] for g in ids), len(ids), sum(pts[g] for g in ids), sum(1 for g in ids if g in gi),
            m(ours, "HP lost"), m(ours, "contact turns"), m(ours, "HP per contact turn"), m(opp, "HP lost"), m(opp, "contact turns"),
            m(opp, "HP per contact turn"), med(num(F[g]["opp"]["trainK"]) for g in ids), med(num(F[g]["me"]["trainK"]) for g in ids)))
    say()
    say("  Economy and army (cgStudy medians): ours in all games | ours in losses | opponents who beat us | opponents we beat | the strongest")
    keys = ("inc_100", "maxIncome", "minesBuilt", "mineUpgrades", "trainK", "trainG", "towers_100", "thp_100", "towerTopUps", "firstTowerLost")
    lost = [g for g in games if pts[g] == 0]
    won = [g for g in games if pts[g] == 1]
    for k in keys:
        def mm(ids, who):
            return med(num(F[g][who][k]) for g in ids)
        say("    %-15s %8s %8s %8s %8s %8s (ours against them %s)" % (k, mm(games, "me"), mm(lost, "me"), mm(lost, "opp"), mm(won, "opp"), mm(top, "opp"), mm(top, "me")))
    say()


PROFILE_PATTERNS = {
    "head": re.compile(r"^(\S+) vs (\S+): (\d+) games, \S+ wins (\d+) \(([\d.]+) %\)"),
    "mapsinfo": re.compile(r"^maps (\d+) from seed (\d+), stride (\d+): starting HP median (\d+), (\d+) distinct values; decided at the limit (\d+), by <= 10 HP (\d+)"),
    "t100": re.compile(r"^turn 100: income (\S+) mines \S+ towers (\S+) towerHp \S+ queenHp (\S+)"),
    "waves": re.compile(r"^waves: .*game length median (\S+)(?:, mean (\S+))?; at the 250-turn limit (\d+) \((\d+) %\)|^waves: .*game length median (\S+)$"),
    "knights": re.compile(r"^under knights .*: contact turns (\S+), % near an own tower (\S+), HP lost per contact turn (\S+), % MOVE (\S+)"),
    "first": re.compile(r"^first contact .*: turn (\S+), own towers then (\S+), HP lost in the next 20 turns (\S+) \(mean (\S+)\); HP lost on turns 25-50 (\S+) \(mean (\S+),"),
}


def parse_profiles(lines):
    """The LocalProfile blocks of local-profiles.txt (each after its '### ... localProfile ...' command line)."""
    blocks, cur = [], None
    for line in lines:
        if line.startswith("### "):
            cur = {"cmd": line[4:]} if "localProfile" in line else None
            if cur is not None:
                m = re.search(r"-PseedBase=(\d+)", line)
                s = re.search(r"-PseedStride=(\d+)", line)
                n = re.search(r"-Pmaps=(\d+)", line)
                cur.update(seedBase=int(m.group(1)), stride=int(s.group(1)) if s else 1, maps=int(n.group(1)))
                blocks.append(cur)
            continue
        if cur is None:
            continue
        for k, p in PROFILE_PATTERNS.items():
            m = p.match(line)
            if m:
                cur[k] = m.groups()
    return blocks


def section_proxy(F, ours, profiles):
    say("6. The local opponents next to the Silver field (local: cr.tools.LocalProfile with v08 as botA on both seats)")
    say("  Seeds: 'consecutive' = 150000.. (stride 1; the starting HP of 484 of the 500 maps is 35), 'spread' = 1 + 397k (all 16")
    say("  starting HPs, 26-36 maps each, as on CG). Win % is botA's; 'close' = decided at the limit by <= 10 HP, of those decided there.")
    games = list(F)
    turns = [int(F[g]["me"]["turns"]) for g in games]
    fc = [x for x in (first_contact(ours[g]["rows"]) for g in games) if x]
    l2550 = [hp_after(ours[g]["rows"], 24) - hp_after(ours[g]["rows"], 49) for g in games if int(F[g]["me"]["turns"]) >= 50]
    lim = [g for g in games if int(F[g]["me"]["turns"]) >= 250 and outcome(F[g]) != 0.5]
    close = sum(1 for g in lim if abs(int(F[g]["me"]["score"]) - int(F[g]["me"]["oppScore"])) <= 10)
    beh = [behaviour(ours[g]["rows"], 0, 250) for g in games]
    beh = [b for b in beh if b and b["contact turns"] > 0]
    hdr = ("%-36s %6s %6s %7s %9s %8s %9s %13s %11s %8s %8s %7s %6s %9s" % (
        "series", "games", "win %", "start", "at limit", "close", "turn 100", "1st contact", "lost 20", "lost 25-50", "contact", "at tower", "per t", "MOVE"))
    say("  " + hdr)
    say("  %-36s %6d %6.1f %7s %8.0f%% %8s %9s %13s %11s %8s %8s %7s %6s %9s" % (
        "Silver arena (v08)", len(games), 100 * st.mean(1.0 if outcome(F[g]) == 1 else 0.0 for g in games),
        "%g (%d)" % (st.median(start_hp(ours[g]["rows"]) for g in games), len({start_hp(ours[g]["rows"]) for g in games})),
        100 * sum(t >= 250 for t in turns) / len(turns), "%d/%d" % (close, len(lim)),
        "%s/%s/%s" % (med(num(F[g]["me"]["inc_100"]) for g in games), med(num(F[g]["me"]["towers_100"]) for g in games), med(num(F[g]["me"]["hp_100"]) for g in games)),
        "t%s T%s" % (med(x["turn"] for x in fc), med(x["towers"] for x in fc)), "%s (%s)" % (med(x["lost20"] for x in fc), mean(x["lost20"] for x in fc)),
        "%s (%s)" % (med(l2550), mean(l2550)), med(b["contact turns"] for b in beh), med(b["% at an own tower"] for b in beh),
        med(b["HP per contact turn"] for b in beh), med(b["% MOVE"] for b in beh)))
    seen = set()
    for b in parse_profiles(profiles):
        key = (b.get("head", ("",))[0], b.get("head", ("", ""))[1], b["seedBase"], b["stride"], b["maps"])
        if "head" not in b or key in seen:
            continue  # the stride-1 regression run repeats the first run's profile
        seen.add(key)
        a, o = b["head"][0], b["head"][1]
        seeds = "spread" if b["stride"] > 1 else "consecutive"
        hp = [start_hp_of_seed(b["seedBase"] + k * b["stride"]) for k in range(b["maps"])]
        say("  %-36s %6s %6s %7s %8s%% %8s %9s %13s %11s %8s %8s %7s %6s %9s" % (
            "%s vs %s, %s" % (a, o, seeds), b["head"][2], b["head"][4], "%g (%d)" % (st.median(hp), len(set(hp))),
            (b["waves"][3] if b["waves"][3] is not None else "-") if "waves" in b else "-",
            ("%s/%s" % (b["mapsinfo"][6], b["mapsinfo"][5])) if "mapsinfo" in b else "-",
            "%s/%s/%s" % b["t100"] if "t100" in b else "-",
            "t%s T%s" % (b["first"][0], b["first"][1]) if "first" in b else "-",
            "%s (%s)" % (b["first"][2], b["first"][3]) if "first" in b else "-",
            "%s (%s)" % (b["first"][4], b["first"][5]) if "first" in b else "-",
            b["knights"][0] if "knights" in b else "-", b["knights"][1] if "knights" in b else "-",
            b["knights"][2] if "knights" in b else "-", b["knights"][3] if "knights" in b else "-"))
    say("  Columns: start = starting HP median (distinct values); turn 100 = income/towers/queen HP (medians); 1st contact = its turn and")
    say("  our towers then; lost 20 = HP lost in the 20 turns from it, median (mean); lost 25-50 likewise; contact = turns with an enemy")
    say("  knight within 250, at tower = % of them within 130 of an own tower, per t = HP lost per contact turn, MOVE = % MOVE (medians).")
    say()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=REPO, help="the directory holding replays/ (the main checkout)")
    args = ap.parse_args()
    data = args.data
    F = load_features(os.path.join(data, "replays", "study", "features-v08-silver-2026-11-05.csv"))
    Fb = load_features(os.path.join(data, "replays", "study", "features-v08-bronze-2026-11-05.csv"))
    ours = load_traces(os.path.join(data, "replays", "study", "traces", "all-v08s-ours.txt"))
    opp = load_traces(os.path.join(data, "replays", "study", "traces", "all-v08s-opp.txt"))
    assert set(F) == set(ours) == set(opp), "features and traces cover different games"
    rating = {}
    for a in AGENTS:
        m = json.load(open(os.path.join(REPO, "docs", "data", "arena-%s-games-2026-11-04.json" % a), encoding="utf-8"))
        for g in m["games"]:
            rating[str(g["gameId"])] = (g["opponent"]["ratingAtFetch"], str(g["opponent"]["agentId"]), a)
    profiles = open(os.path.join(HERE, "local-profiles.txt"), encoding="utf-8").read().splitlines() if os.path.exists(os.path.join(HERE, "local-profiles.txt")) else []
    say("Study 2026-11-05: v08's Silver placement games (agents %s)" % ", ".join(AGENTS))
    say()
    section_outcomes(F, Fb)
    section_start_hp(F, ours)
    section_hp_race(F, ours, opp)
    section_first_contact(F, ours, opp)
    section_deaths(F, ours)
    section_opponents(F, ours, opp, rating, arena_compare())
    section_proxy(F, ours, profiles)
    text = "\n".join(OUT) + "\n"
    with open(os.path.join(HERE, "silver.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    sys.stdout.reconfigure(encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
