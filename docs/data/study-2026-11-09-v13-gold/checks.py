"""v13's Gold games (study 2026-11-09): the checks behind the recommendation, from ArenaProbe's csv files.
  1. what a top-up adds, ours and the opponents';
  2. the ladder gate's spells and the knights that open them;
  3. the damage of a knight release by the defender's towers and their HP, and by the turn;
  4. the HP races by 25 turns: the tower lines, the top-ups of full towers, the HP both queens lose;
  5. the turns before our queen's deaths;
  6. the releases of the local opponents beside the arena's;
  7. v12 and v13 against the same opponent band (20-22): the HP flow and the midgame contact episodes;
  8. how the opponents' queens move and keep their towers.

Usage (from the repository root): python -B -I docs/data/study-2026-11-09-v13-gold/checks.py [--root <dir with probe/, probe-v12/ and local-v13-*/>]
Output: stdout (checks.txt beside this file).
"""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe_tables import by_game, meta, read, turn_class  # noqa: E402
from v13_gold import mean, med  # noqa: E402


def top(r):
    return r["cmd"] == "B" and r["what"] == "TOWER" and r["touch"] == r["site"] and r["touchKind"] == "T"


def full(r):
    return top(r) and r["touchHp"] >= 700


def gate(r):
    return r["t"] >= 30 and r["T"] >= 2 and r["k500"] > 0 and r["k250"] == 0


def per_tower(rows):
    return sum(r["thpSum"] for r in rows) / max(1, sum(r["T"] for r in rows))


def main():
    root = sys.argv[sys.argv.index("--root") + 1] if "--root" in sys.argv else os.path.join("replays", "study", "gold-v13-2026-11-09")
    turns = read(os.path.join(root, "probe", "turns.csv"))
    waves = read(os.path.join(root, "probe", "waves.csv"))
    events = read(os.path.join(root, "probe", "events.csv"))
    m = meta("v13")
    g = by_game(turns)
    ours = {gid: rows for (gid, us), rows in g.items() if us == 1}
    theirs = {gid: rows for (gid, us), rows in g.items() if us == 0}

    print("1. What a top-up adds (a BUILD TOWER on the touched own tower: +100 HP up to 800), v13's 180 Gold games")
    for name, us, ok in (("ours", 1, lambda x: True), ("theirs 22+", 0, lambda x: m[x]["rating"] >= 22), ("theirs 20-22", 0, lambda x: 20 <= m[x]["rating"] < 22),
                         ("theirs <20", 0, lambda x: m[x]["rating"] < 20)):
        rows = [r for r in turns if r["us"] == us and ok(r["gid"]) and top(r)]
        n = len({r["gid"] for r in turns if r["us"] == us and ok(r["gid"])})
        gain = sum(min(100, 800 - r["touchHp"]) for r in rows)
        print("   %-13s %5.1f top-ups a game add %4.0f HP a game (%.0f each); %4.0f HP a game is lost at the cap; top-ups that add 10 or less: %.1f a game, 50 or less: %.1f" % (
            name, len(rows) / n, gain / n, gain / max(1, len(rows)), (100 * len(rows) - gain) / n, sum(1 for r in rows if 800 - r["touchHp"] <= 10) / n,
            sum(1 for r in rows if 800 - r["touchHp"] <= 50) / n))

    print("\n2. The ladder gate's state (turn 30 on, two own towers or more, an enemy knight within 500, none within 250), ours in v13")
    lens = []
    for rows in ours.values():
        cur = 0
        for r in rows:
            if gate(r):
                cur += 1
            elif cur:
                lens.append(cur)
                cur = 0
        if cur:
            lens.append(cur)
    print("   spells: %.1f a game, length median %.0f, mean %.1f; of 10 turns or more: %.1f a game (%.0f %% of the state's turns)" % (
        len(lens) / 180.0, med([float(x) for x in lens]), mean([float(x) for x in lens]), sum(1 for x in lens if x >= 10) / 180.0, 100.0 * sum(x for x in lens if x >= 10) / sum(lens)))
    rows = [r for r in turns if r["us"] == 1 and gate(r)]
    print("   knights within 500 there: %.1f (mean), the strongest has %.0f HP (mean); own towers covering the queen %.1f" % (
        mean([r["k500"] for r in rows]), mean([r["kHpMax500"] for r in rows]), mean([r["towersCov"] for r in rows])))
    nxt = Counter()
    for rs in ours.values():
        for a, b in zip(rs, rs[1:]):
            if gate(a):
                nxt["a contact" if b["k250"] > 0 else "still within 500" if b["k500"] > 0 else "clear"] += 1
    print("   the next turn: %s" % ", ".join("%s %.0f %%" % (k, 100.0 * v / sum(nxt.values())) for k, v in nxt.most_common()))
    for lo, hi in ((0, 6), (6, 9), (9, 12), (12, 16), (16, 26)):
        c = t = hp = 0
        for rs in ours.values():
            for i, a in enumerate(rs):
                if gate(a) and lo <= a["kHpMax500"] < hi:
                    t += 1
                    c += any(r["k250"] > 0 for r in rs[i + 1:i + 5])
                    hp += sum(r["kd"] for r in rs[i + 1:i + 9])
        print("   the strongest knight within 500 has %2d-%2d HP: %4.1f turns a game; a contact within 4 turns in %2.0f %%; HP lost to knights in the next 8 turns %.2f" % (
            lo, hi - 1, t / 180.0, 100.0 * c / max(1, t), hp / max(1, t)))

    print("\n3. The damage of a knight release (the HP the enemy queen loses in the next 35 turns on turns with the releaser's knights within 250), releases from turn 50")
    kw = [w for w in waves if w["type"] == "K" and w["t"] >= 50]
    for us, label in ((0, "their releases against our towers"), (1, "our releases against their towers")):
        ws = [w for w in kw if w["us"] == us]
        print("   %s (%d): the defender's towers (rows) by their mean HP (columns): releases, damage" % (label, len(ws)))
        for tl, th in ((1, 3), (3, 4), (4, 5), (5, 6), (6, 99)):
            parts = []
            for hl, hh in ((0, 400), (400, 550), (550, 700), (700, 801)):
                x = [w for w in ws if tl <= w["enemyTowers"] < th and hl <= w["enemyTowerHp"] / w["enemyTowers"] < hh]
                parts.append("%3d-%3d: %4d, %5.2f" % (hl, min(hh, 800), len(x), mean([w["damage"] for w in x])))
            print("      %s towers | %s" % ("%d-%d" % (tl, th - 1) if th < 99 else "6+ ", " | ".join(parts)))
        print("      by turn: %s" % "; ".join("%d-%d: %d releases, damage %.2f, the defender's towers %.1f @ %.0f" % (
            lo, hi, len(x), mean([w["damage"] for w in x]), mean([w["enemyTowers"] for w in x]), sum(w["enemyTowerHp"] for w in x) / max(1, sum(w["enemyTowers"] for w in x)))
            for lo, hi in ((50, 100), (100, 150), (150, 200)) for x in [[w for w in ws if lo <= w["t"] < hi]]))
    our_hp = sum(r["kd"] for rows in ours.values() for r in rows if r["t"] >= 50) / 180.0
    their_hp = sum(r["kd"] for rows in theirs.values() for r in rows if r["t"] >= 50) / 180.0
    our_kn = 4 * sum(w["barracks"] for w in kw if w["us"] == 1) / 180.0
    their_kn = 4 * sum(w["barracks"] for w in kw if w["us"] == 0) / 180.0
    print("   from turn 50, a game: our queen loses %.1f HP to knights, theirs %.1f; knights trained from turn 50: ours %.1f, theirs %.1f; HP a knight: ours %.2f, theirs %.2f" % (
        our_hp, their_hp, our_kn, their_kn, their_hp / our_kn, our_hp / their_kn))

    print("\n4. The HP races by 25 turns (v13): towers @ HP each (lowest), top-ups of a tower at 700+ / below, HP lost to knights; then theirs; knights alive; income")
    races = [(rows[-1]["hpAfter"] - theirs[gid][-1]["hpAfter"], gid) for gid, rows in ours.items() if len(rows) >= 250]
    print("   margins of the lost races: %s" % sorted([x[0] for x in races if x[0] < 0], reverse=True))
    for label, ids in (("lost races", [x[1] for x in races if x[0] < 0]), ("won races", [x[1] for x in races if x[0] > 0])):
        n = float(len(ids))
        print("   %s (%d)" % (label, n))
        for a in range(50, 250, 25):
            o = [r for gid in ids for r in ours[gid] if a <= r["t"] < a + 25]
            t = [r for gid in ids for r in theirs[gid] if a <= r["t"] < a + 25]
            print("      %3d-%3d: ours %.1f @ %3.0f (%3.0f), top-ups %4.1f / %4.1f, HP %4.2f | theirs %.1f @ %3.0f (%3.0f), top-ups %4.1f / %4.1f, HP %4.2f | knights alive: theirs %.1f, ours %.1f | income %.1f / %.1f" % (
                a, a + 25, mean([r["T"] for r in o]), per_tower(o), mean([r["minTHp"] for r in o if r["minTHp"] >= 0]), sum(1 for r in o if full(r)) / n,
                sum(1 for r in o if top(r) and not full(r)) / n, sum(r["kd"] for r in o) / n,
                mean([r["T"] for r in t]), per_tower(t), mean([r["minTHp"] for r in t if r["minTHp"] >= 0]), sum(1 for r in t if full(r)) / n,
                sum(1 for r in t if top(r) and not full(r)) / n, sum(r["kd"] for r in t) / n,
                mean([r["eKnights"] for r in o]), mean([r["oKnights"] for r in o]), mean([r["income"] for r in o]), mean([r["income"] for r in t])))
        late = [r for gid in ids for r in ours[gid] if r["t"] >= 150]
        print("      turns 150-249: top-ups of a tower at 700+ %.1f a game, HP lost to knights %.1f" % (sum(1 for r in late if full(r)) / n, sum(r["kd"] for r in late) / n))
        idset = set(ids)
        their_loss = sum(r["kd"] for gid in ids for r in theirs[gid] if r["t"] >= 50) / n
        our_loss = sum(r["kd"] for gid in ids for r in ours[gid] if r["t"] >= 50) / n
        our_knights = 4 * sum(w["barracks"] for w in kw if w["us"] == 1 and w["gid"] in idset) / n
        their_knights = 4 * sum(w["barracks"] for w in kw if w["us"] == 0 and w["gid"] in idset) / n
        print("      from turn 50: our knights trained %.1f a game take %.1f HP off their queen (%.2f a knight); theirs %.1f take %.1f off ours (%.2f a knight)" % (
            our_knights, their_loss, their_loss / our_knights, their_knights, our_loss, our_loss / their_knights))

    print("\n5. The turns before our queen's death (v13, deaths from turn 90)")
    deaths = [gid for gid, rows in ours.items() if rows[-1]["hpAfter"] <= 0 and len(rows) >= 90]
    n = float(len(deaths))
    for back in range(125, 0, -25):
        o, t = [], []
        for gid in deaths:
            end = len(ours[gid])
            o += [r for r in ours[gid] if end - back <= r["t"] < end - back + 25]
            t += [r for r in theirs[gid] if end - back <= r["t"] < end - back + 25]
        print("   %3d-%3d turns before: towers %.1f @ %3.0f (lowest %3.0f), covering her %.1f; top-ups at 700+ %.1f, below %.1f, MOVE turns %4.1f; HP to knights %5.2f; enemy knights alive %.1f, giants %.2f; income %.1f / %.1f; our knights alive %.1f" % (
            back - 25, back, mean([r["T"] for r in o]), per_tower(o), mean([r["minTHp"] for r in o if r["minTHp"] >= 0]), mean([r["towersCov"] for r in o]),
            sum(1 for r in o if full(r)) / n, sum(1 for r in o if top(r) and not full(r)) / n, sum(1 for r in o if r["cmd"] == "M") / n, sum(r["kd"] for r in o) / n,
            mean([r["eKnights"] for r in o]), mean([r["eGiants"] for r in o]), mean([r["income"] for r in o]), mean([r["income"] for r in t]), mean([r["oKnights"] for r in o])))
    dset = set(deaths)
    ev = [e for e in events if e["us"] == 1 and e["event"] == "lost" and e["kind"] == "TOWER"]
    gi = [gid for gid in deaths if any(r["eGiants"] > 0 for r in ours[gid])]
    print("   %d deaths; our towers lost a game: %.1f to giants and %.1f melted in them, %.1f and %.1f in the other games; enemy giants on the board in %d of the deaths" % (
        len(deaths), sum(1 for e in ev if e["gid"] in dset and e["cause"] == "giant") / n, sum(1 for e in ev if e["gid"] in dset and e["cause"] == "melt") / n,
        sum(1 for e in ev if e["gid"] not in dset and e["cause"] == "giant") / (180.0 - n), sum(1 for e in ev if e["gid"] not in dset and e["cause"] == "melt") / (180.0 - n), len(gi)))
    for label, ids in (("with giants", gi), ("without giants", [x for x in deaths if x not in gi])):
        last = [r for gid in ids for r in ours[gid][-30:]]
        print("      %-15s (%2d) the last 30 turns: towers %.1f @ %.0f, covering her %.1f, enemy knights alive %.1f" % (label, len(ids), mean([r["T"] for r in last]), per_tower(last),
              mean([r["towersCov"] for r in last]), mean([r["eKnights"] for r in last])))

    print("\n6. Knight releases from turn 50: the releasing barracks' distance to the enemy queen and the damage, the arena beside the local panel")
    for name in ("probe", "local-v13-v13", "local-v13-rival", "local-v13-field"):
        w = [x for x in read(os.path.join(root, name, "waves.csv")) if x["type"] == "K" and x["t"] >= 50]
        games = len({x["gid"] for x in w})
        for us in (1, 0):
            ws = [x for x in w if x["us"] == us]
            print("   %-16s %s %4.1f releases a game, %4.0f away (below 1000: %2.0f %%, 1200 or more: %2.0f %%), damage %.2f" % (
                "arena" if name == "probe" else name, "ours  " if us else "theirs", len(ws) / float(games), mean([x["dist"] for x in ws]),
                100.0 * sum(1 for x in ws if x["dist"] < 1000) / len(ws), 100.0 * sum(1 for x in ws if x["dist"] >= 1200) / len(ws), mean([x["damage"] for x in ws])))

    print("\n7. v12 and v13 against the opponents rated 20-22: the HP both queens lose to knights by 50 turns, the midgame contact episodes of our queen by where they began")
    for name, sub in (("v12", "probe-v12"), ("v13", "probe")):
        mm = meta(name)
        tt = turns if name == "v13" else read(os.path.join(root, sub, "turns.csv"))
        gg = by_game(tt)
        ids = [gid for gid in mm if 20 <= mm[gid]["rating"] < 22]
        n = float(len(ids))
        o = [r for gid in ids for r in gg[(gid, 1)]]
        t = [r for gid in ids for r in gg[(gid, 0)]]
        acc = {}
        for gid in ids:
            rs = gg[(gid, 1)]
            i = 0
            while i < len(rs):
                if rs[i]["t"] >= 50 and rs[i]["k250"] > 0 and (i == 0 or rs[i - 1]["k250"] == 0):
                    j = i
                    kd = 0
                    while j < len(rs) and rs[j]["k250"] > 0:
                        kd += rs[j]["kd"]
                        j += 1
                    b = rs[i - 1] if i > 0 else None
                    c = "other" if b is None else "on a walk" if b["cmd"] == "B" and b["touch"] != b["site"] else "at an own tower" if 0 <= b["nearT"] <= 130 else "other"
                    a = acc.setdefault(c, [0, 0])
                    a[0] += 1
                    a[1] += kd
                    i = j
                else:
                    i += 1
        print("   %s (%d games, won %.0f %%): ours %s; theirs %s; episodes: %s" % (
            name, n, 100 * mean([1 if mm[gid]["won"] else 0 for gid in ids]),
            " | ".join("%.1f" % (sum(r["kd"] for r in o if a <= r["t"] < a + 50) / n) for a in (0, 50, 100, 150, 200)),
            " | ".join("%.1f" % (sum(r["kd"] for r in t if a <= r["t"] < a + 50) / n) for a in (0, 50, 100, 150, 200)),
            "; ".join("%s %.1f a game, %.1f HP a game" % (c, a[0] / n, a[1] / n) for c, a in sorted(acc.items(), key=lambda kv: -kv[1][1]))))

    print("\n8. How the queens move and keep their towers (v13's games)")
    for name, us, ok in (("theirs 22+", 0, lambda x: m[x]["rating"] >= 22), ("theirs 20-22", 0, lambda x: 20 <= m[x]["rating"] < 22), ("ours", 1, lambda x: True)):
        ends, lens, n_runs, n_games, distinct, switches = Counter(), [], 0, 0, [], []
        for (gid, u), rows in g.items():
            if u != us or not ok(gid):
                continue
            n_games += 1
            cur = []
            for r in rows:
                if r["cmd"] == "M" and r["k500"] == 0:
                    cur.append(r)
                elif cur:
                    n_runs += 1
                    lens.append(float(len(cur)))
                    ends[turn_class(r)] += 1
                    cur = []
            tops = [r["site"] for r in rows if top(r) and r["t"] >= 60]
            distinct.append(float(len(set(tops))))
            switches.append(float(sum(1 for a, b in zip(tops, tops[1:]) if a != b)))
        print("   %-12s MOVE runs with no knight within 500: %.1f a game, %.1f turns each; followed by: %s" % (
            name, n_runs / float(n_games), mean(lens), ", ".join("%s %.0f %%" % (k, 100.0 * c / n_runs) for k, c in ends.most_common(6))))
        print("   %-12s from turn 60: %.1f different towers topped up a game, %.1f changes of the tower" % ("", mean(distinct), mean(switches)))

    print("\n9. What a walk in the gate's state cost v12, which had no gate: walks to a free site or an own mine beside turns at the touched own tower, by the strongest knight within 500")
    v12 = by_game([r for r in read(os.path.join(root, "probe-v12", "turns.csv")) if r["us"] == 1])
    for lo, hi in ((0, 9), (9, 12), (12, 16), (16, 26)):
        parts = []
        for label, pred in (("on a walk", lambda r: r["cmd"] == "B" and r["touch"] != r["site"] and r["targetKind"] in ("F", "M")),
                            ("at the tower", lambda r: r["cmd"] == "B" and r["touch"] == r["site"] and r["touchKind"] == "T")):
            n = hp = c = 0
            for rs in v12.values():
                for i, a in enumerate(rs):
                    if gate(a) and lo <= a["kHpMax500"] < hi and pred(a):
                        n += 1
                        hp += sum(r["kd"] for r in rs[i + 1:i + 9])
                        c += any(r["k250"] > 0 for r in rs[i + 1:i + 5])
            parts.append("%s %.1f turns a game, a contact within 4 turns in %2.0f %%, HP lost in the next 8 turns %.2f" % (label, n / 180.0, 100.0 * c / max(1, n), hp / max(1, n)))
        print("   the strongest has %2d-%2d HP: %s" % (lo, hi - 1, "; ".join(parts)))

    print("\n10. The own towers whose range covers the queen (turns from 50). Best within 180 / 300: the most covering towers at a point within three / five"
          " steps of her, outside the enemy towers' ranges (16 headings a ring of 60); full: as if every own tower had its full range, 510")
    cov = lambda r: min(r["towersCov"], 4)
    for name in ("probe", "probe-v12", "local-v13-v13", "local-v13-rival", "local-v13-field"):
        tt = turns if name == "probe" else read(os.path.join(root, name, "turns.csv"))
        gg = by_game(tt)
        n = float(len({r["gid"] for r in tt}))
        label = {"probe": "v13 on the arena", "probe-v12": "v12 on the arena"}.get(name, name)
        for us, who in ((1, "ours"), (0, "theirs")):
            if name.startswith("local") and us == 0 and name.endswith("v13-v13"):
                continue
            con = [r for r in tt if r["us"] == us and r["t"] >= 50 and r["k250"] > 0]
            gt = [r for r in tt if r["us"] == us and r["t"] >= 50 and gate(r)]
            print("   %-16s %-6s contact turns %4.1f a game, HP lost a turn by the cover (0, 1, 2, 3, 4+): %s; turns a game: %s" % (
                label, who, len(con) / n, " | ".join("%.2f" % mean([r["kd"] for r in con if cov(r) == c]) for c in range(5)),
                " | ".join("%.1f" % (sum(1 for r in con if cov(r) == c) / n) for c in range(5))))
            for what, x in (("contact turns", con), ("the gate's state", gt)):
                print("   %-16s %-6s %-16s covering now %.2f (3 or more on %2.0f %% of the turns, 4 or more on %2.0f %%); best within 180 %.2f (higher in %2.0f %% of the turns), within 300 %.2f (%2.0f %%), within 300 at full HP %.2f; own towers %.1f" % (
                    "", "", what, mean([r["towersCov"] for r in x]), 100 * mean([1 if r["towersCov"] >= 3 else 0 for r in x]), 100 * mean([1 if r["towersCov"] >= 4 else 0 for r in x]),
                    mean([r["bestCov180"] for r in x]), 100 * mean([1 if r["bestCov180"] > r["towersCov"] else 0 for r in x]),
                    mean([r["bestCov300"] for r in x]), 100 * mean([1 if r["bestCov300"] > r["towersCov"] else 0 for r in x]), mean([r["bestCovFull300"] for r in x]), mean([r["T"] for r in x])))
    con = [r for r in turns if r["us"] == 1 and r["t"] >= 50 and r["k250"] > 0]
    print("   v13 on the arena, ours, within strata (HP lost a contact turn by the cover 1, 2, 3, 4+; cells of 30 turns or more):")
    for T in (2, 3, 4, 5, 6):
        x = [r for r in con if (r["T"] == T if T < 6 else r["T"] >= 6)]
        print("      own towers %d%s: %s" % (T, "+" if T == 6 else " ", " | ".join("%.2f" % mean([r["kd"] for r in y]) if len(y) >= 30 else "  - " for c in range(1, 5) for y in [[r for r in x if cov(r) == c]])))
    for k in (1, 2, 3, 4, 5):
        x = [r for r in con if (r["k250"] == k if k < 5 else r["k250"] >= 5)]
        print("      knights within 250: %d%s: %s" % (k, "+" if k == 5 else " ", " | ".join("%.2f" % mean([r["kd"] for r in y]) if len(y) >= 30 else "  - " for c in range(1, 5) for y in [[r for r in x if cov(r) == c]])))
    for label, ok in (("won games", lambda x: m[x]["won"]), ("lost games", lambda x: not m[x]["won"]), ("against 22+", lambda x: m[x]["rating"] >= 22)):
        x = [r for r in con if ok(r["gid"])]
        ng = float(len({r["gid"] for r in turns if ok(r["gid"])}))
        print("      %-11s (cover 0, 1, 2, 3, 4+): HP a turn %s; turns a game %s; HP a game %s" % (
            label, " | ".join("%.2f" % mean([r["kd"] for r in x if cov(r) == c]) for c in range(5)), " | ".join("%.1f" % (sum(1 for r in x if cov(r) == c) / ng) for c in range(5)),
            " | ".join("%.1f" % (sum(r["kd"] for r in x if cov(r) == c) / ng) for c in range(5))))
    ep = []
    for rs in ours.values():
        i = 0
        while i < len(rs):
            if rs[i]["t"] >= 50 and rs[i]["k250"] > 0 and (i == 0 or rs[i - 1]["k250"] == 0):
                j, kd = i, 0
                while j < len(rs) and rs[j]["k250"] > 0:
                    kd += rs[j]["kd"]
                    j += 1
                ep.append((cov(rs[i]), kd, j - i, rs[i]["bestCov180"] > rs[i]["towersCov"]))
                i = j
            else:
                i += 1
    print("   v13 on the arena, ours: contact episodes from turn 50 (%d, %.1f a game) by the cover on their first turn: %s" % (len(ep), len(ep) / 180.0, "; ".join(
        "%d%s: %d episodes, %.2f HP each (%.1f HP a game), %.1f turns" % (c, "+" if c == 4 else "", len(y), mean([e[1] for e in y]), sum(e[1] for e in y) / 180.0, mean([float(e[2]) for e in y]))
        for c in range(5) for y in [[e for e in ep if e[0] == c]] if y)))
    for c in (1, 2):
        y = [e for e in ep if e[0] == c]
        print("      begun with %d covering: a better spot within 180 on the first turn in %.0f %% of them; HP each with such a spot %.2f, without %.2f" % (
            c, 100 * mean([1 if e[3] else 0 for e in y]), mean([e[1] for e in y if e[3]]), mean([e[1] for e in y if not e[3]])))
    quiet = [w for w in waves if w["type"] == "K" and w["t"] >= 50 and w["us"] == 0]
    idx = {(r["gid"], r["t"]): r for r in turns if r["us"] == 1}
    quiet = [w for w in quiet if idx[(w["gid"], w["t"])]["eKnights"] == 0 and idx[(w["gid"], w["t"])]["eGiants"] == 0]
    print("   their releases from turn 50 with no enemy knight or giant alive at the release (%d): the damage by our towers' mean HP: %s; by the towers covering our queen at the release: %s" % (
        len(quiet), "; ".join("%d-%d: %.2f (n %d)" % (lo, hi, mean([w["damage"] for w in x]), len(x)) for lo, hi in ((0, 450), (450, 550), (550, 650), (650, 801))
                               for x in [[w for w in quiet if w["enemyTowers"] > 0 and lo <= w["enemyTowerHp"] / w["enemyTowers"] < hi]]),
        "; ".join("%d%s: %.2f (n %d)" % (c, "+" if c == 4 else "", mean([w["damage"] for w in x]), len(x)) for c in range(1, 5)
                  for x in [[w for w in quiet if cov(idx[(w["gid"], w["t"])]) == c]])))


if __name__ == "__main__":
    main()
