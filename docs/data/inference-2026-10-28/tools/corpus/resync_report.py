"""Summarise CgValidate strict/resync outputs per league and per unit type.

Usage: python -I -X utf8 resync_report.py <replay_root> <strict.txt> <resync-log.txt> <analysis.json> <out.json>

Decodes the replay units per turn (as CgValidate.Units: tooltip-registered token groups, owner
from the token circle image, HP from the circle tooltip, dead creeps dropped) to classify every
turn the resync run processed: spawn turn (a creep registered this frame) or plain turn, and
which creep types were alive at the start of the turn. A turn is unit-exact when the log has no
RESYNC line for it. The resync run stops a game at its first non-resyncable mismatch (site
tooltip, failed resync, end of game); later turns are not counted.
"""
import collections
import json
import os
import re
import sys

XR = re.compile(r"(?:^| )x (-?\d+)")
YR = re.compile(r"(?:^| )y (-?\d+)")
IR = re.compile(r"(?:^| )i (\S+)")
UNIT_RE = re.compile(r"(\d) (Q|KNIGHT|ARCHER|GIANT) (-?\d+) (-?\d+) (-?\d+|\?)")


def last(rx, s):
    r = None
    for m in rx.finditer(s):
        r = m.group(1)
    return r


def decode_units(path):
    """Returns (list per turn t=1..N of alive units after keyframe t, spawned flag per turn, init units)."""
    g = json.load(open(path, encoding="utf-8"))
    pos, img, texts, typ = {}, {}, {}, {}
    out, spawned = [], []

    def apply(v):
        new = False
        for e in v.get("entitymodule", []) or []:
            if not e.startswith("U "):
                continue
            parts = e.split(" ", 3)
            if len(parts) < 4:
                continue
            eid = int(parts[1])
            x, y, im = last(XR, parts[3]), last(YR, parts[3]), last(IR, parts[3])
            p = pos.setdefault(eid, [None, None])
            if x is not None:
                p[0] = int(x)
            if y is not None:
                p[1] = int(y)
            if im is not None:
                img[eid] = im
        tt = v.get("tooltips") or []
        if len(tt) > 0:
            for k, o in tt[0].items():
                t = o.get("type")
                if t and t != "Site":
                    typ[int(k)] = "Q" if t == "Queen" else t
                    if t != "Queen":
                        new = True
        if len(tt) > 1:
            for k, lines in tt[1].items():
                texts[int(k)] = lines
        return new

    def alive():
        r = []
        for eid, t in typ.items():
            lines = texts.get(eid - 2) or []
            hp = lines[0].replace("Health: ", "") if lines else "?"
            if t != "Q" and hp == "0":
                continue
            owner = 1 if img.get(eid - 2) == "Unite_Base_Bleu" else 0
            r.append((owner, t, pos.get(eid, [None, None])[0], pos.get(eid, [None, None])[1], hp))
        return r

    def view(raw):
        nl = raw.find("\n")
        body = raw[nl + 1:].strip() if nl >= 0 else ""
        if not body:
            return {}
        v = json.loads(body)
        return v.get("frame", v)

    apply(view(g["frames"][0]["view"]))
    init = alive()
    for fr in g["frames"][1:]:
        if not fr.get("keyframe"):
            continue
        spawned.append(apply(view(fr["view"])))
        out.append(alive())
    return out, spawned, init


def parse_validator(path):
    """gameId -> {'status': 'ok'|'mismatch'|'error', 'turn': int|None, 'msg': str}"""
    res = {}
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.match(r"^(MISMATCH|ERROR|OK) (\S+?(\d+)\.json)", line)
        if not m:
            continue
        gid = int(m.group(3))
        msg = line.split(": ", 1)[1].strip() if ": " in line else ""
        tm = re.match(r"t(\d+) ", msg)
        res[gid] = {"status": m.group(1).lower(), "turn": int(tm.group(1)) if tm else None, "msg": msg[:400]}
    return res


def parse_resync_log(path):
    """gameId -> {turn: (kind, replay_only[], sim_only[], log)}"""
    res = collections.defaultdict(dict)
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.match(r"^RESYNC g(\d+) t(\d+) (units(?:@spawn)? \S+) replay-only \[(.*?)\] sim-only \[(.*?)\] LOG ?(.*)$", line.rstrip("\n"))
        if not m:
            continue
        ro = [u.groups() for u in UNIT_RE.finditer(m.group(4))]
        so = [u.groups() for u in UNIT_RE.finditer(m.group(5))]
        res[int(m.group(1))][int(m.group(2))] = (m.group(3), ro, so, m.group(6))
    return res


def main():
    root, strict_txt, log_txt, analysis_json, out_json = sys.argv[1:6]
    analysis = {g["gameId"]: g for g in json.load(open(analysis_json, encoding="utf-8")) if "error" not in g}
    strict = parse_validator(strict_txt)
    resync_status = parse_validator(log_txt)
    rlog = parse_resync_log(log_txt)
    per_game = []
    agg = collections.defaultdict(lambda: collections.Counter())
    diff_types = collections.defaultdict(lambda: collections.Counter())
    cat_diff = collections.defaultdict(lambda: collections.Counter())
    examples = collections.defaultdict(list)
    for gid, a in sorted(analysis.items()):
        lg = a["league"]
        units, spawned, init = decode_units(os.path.join(root, a["file"]))
        st = resync_status.get(gid, {"status": "ok", "turn": None, "msg": ""})
        # CgValidate compares units on every processed turn, including the turn of a site or
        # failed-resync mismatch; it stops there. Other final checks (score, winner, end turn)
        # come after all turns.
        if st["status"] == "mismatch" and st["turn"] and re.search(r"^t\d+ (site|units|gold|queenHp)", st["msg"]):
            last_t = st["turn"]
        elif st["status"] == "mismatch" and "replay continues after simulated end" in st["msg"]:
            last_t = st["turn"] - 1
        else:
            last_t = len(units)
        logs = rlog.get(gid, {})
        g_counts = collections.Counter()
        prev = init
        for t in range(1, last_t + 1):
            types_start = {u[1] for u in prev if u[1] != "Q"}
            sp = spawned[t - 1]
            cat = "spawn" if sp else "plain"
            exact = t not in logs
            keys = [cat]
            if not sp:
                if "ARCHER" in types_start:
                    keys.append("plain+archer")
                if "GIANT" in types_start:
                    keys.append("plain+giant")
                if types_start == {"KNIGHT"}:
                    keys.append("plain knights-only")
                if not types_start:
                    keys.append("plain no-creeps")
            for k in keys:
                agg[lg][k + " turns"] += 1
                agg[lg][k + " exact"] += exact
                agg["all"][k + " turns"] += 1
                agg["all"][k + " exact"] += exact
                g_counts[k + " turns"] += 1
                g_counts[k + " exact"] += exact
            if not exact:
                kind, ro, so, lg_shots = logs[t]
                involved = sorted({u[1] for u in ro + so})
                # unit types whose entries differ
                diff_types[lg][(kind, ",".join(involved))] += 1
                diff_types["all"][(kind, ",".join(involved))] += 1
                for k in keys:
                    cat_diff[k][(kind, ",".join(involved))] += 1
                    if k in ("plain+archer", "plain+giant") and len(examples[k]) < 400:
                        examples[k].append({"gameId": gid, "league": lg, "turn": t, "kind": kind,
                                            "replayOnly": [" ".join(u) for u in ro], "simOnly": [" ".join(u) for u in so],
                                            "log": lg_shots[:600]})
            prev = units[t - 1]
        per_game.append({"gameId": gid, "league": lg, "turns": a["turns"],
                         "strict": strict.get(gid, {"status": "ok"}),
                         "resync": st, "resyncComparedTurns": last_t,
                         "resyncTurns": {str(t): v[0] for t, v in sorted(logs.items())},
                         "counts": dict(g_counts)})
    out = {"perGame": per_game,
           "aggregate": {k: dict(v) for k, v in agg.items()},
           "diffTypes": {k: {"%s | %s" % kk: n for kk, n in sorted(v.items(), key=lambda kv: -kv[1])} for k, v in diff_types.items()},
           "diffTypesByCategory": {k: {"%s | %s" % kk: n for kk, n in sorted(v.items(), key=lambda kv: -kv[1])} for k, v in cat_diff.items()},
           "examples": examples}
    json.dump(out, open(out_json, "w", encoding="utf-8"), indent=1)
    for lg in ("bronze", "silver", "gold", "all"):
        if lg not in agg:
            continue
        a = agg[lg]
        print("==", lg)
        for k in ("plain", "spawn", "plain knights-only", "plain+archer", "plain+giant", "plain no-creeps"):
            n, e = a.get(k + " turns", 0), a.get(k + " exact", 0)
            print("   %-20s %5d/%-5d exact (%.1f%%)" % (k, e, n, 100.0 * e / max(1, n)))
        print("   top diff kinds:")
        for kk, n in sorted(diff_types[lg].items(), key=lambda kv: -kv[1])[:12]:
            print("     %5d  %s | %s" % (n, kk[0], kk[1]))

    for k in ("plain+archer", "plain+giant", "plain knights-only"):
        print("== diff kinds on", k, "turns (all leagues)")
        for kk, n in sorted(cat_diff[k].items(), key=lambda kv: -kv[1])[:12]:
            print("     %5d  %s | %s" % (n, kk[0], kk[1]))


main()
