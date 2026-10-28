"""Decode CG Code Royale replays: archer/giant usage, spawns, and BUILD conflicts.

Usage: python -I -X utf8 analyze.py <root> <out.json>
<root> holds <league>/<gameId>.json files (raw gameResult responses). Writes per-game facts.

Turn t (1-based) = the t-th keyframe after the init keyframe; its commands are the agent frames
before that keyframe. Queen/site state at the start of turn t comes from keyframes 0..t-1
(positions are integers between turns). A BUILD conflict is a turn where both players BUILD the
same site, both queens touch it (dist < 30 + r + 5) and the site is empty at the start of the turn
(otherwise the referee warns one player and schedules a single build).
"""
import glob
import hashlib
import json
import math
import os
import re
import sys

XR = re.compile(r"(?:^| )x (-?\d+)")
YR = re.compile(r"(?:^| )y (-?\d+)")
IR = re.compile(r"(?:^| )i (\S+)")
CR = re.compile(r"(?:^| )c (-?\d+)")
OWNER_IMG = re.compile(r"^(Mine|Caserne|Eclair)_(Rouge|Bleu)$")
BUILD_RE = re.compile(r"^BUILD (\d+) (\S+)$")


def parse_view(raw):
    if raw is None:
        return None, None
    nl = raw.find("\n")
    head = raw[:nl].strip() if nl >= 0 else raw.strip()
    body = raw[nl + 1:].strip() if nl >= 0 else ""
    if not body:
        return head, {}
    v = json.loads(body)
    return head, (v["frame"] if "frame" in v else v)


def updates(v):
    """entity id -> concatenated props of this frame's U commands (in order)."""
    out = {}
    for e in v.get("entitymodule", []) or []:
        if not e.startswith("U "):
            continue
        parts = e.split(" ", 3)
        if len(parts) < 4:
            continue
        eid = int(parts[1])
        out[eid] = out.get(eid, "") + " " + parts[3]
    return out


def last(rx, s):
    r = None
    for m in rx.finditer(s):
        r = m.group(1)
    return r


def analyze(path, league):
    with open(path, "rb") as f:
        raw = f.read()
    sha = hashlib.sha256(raw).hexdigest()
    g = json.loads(raw.decode("utf-8"))
    frames = g["frames"]
    seed = None
    for line in g.get("refereeInput", "").split("\n"):
        if line.strip().startswith("seed="):
            seed = int(line.strip()[5:])
    agents = sorted(({"index": a["index"], "pseudo": (a.get("codingamer") or {}).get("pseudo"),
                      "agentId": a["agentId"], "rating": a.get("score"), "valid": a.get("valid")}
                     for a in g["agents"]), key=lambda a: a["index"])
    nkey = sum(1 for f in frames if f.get("keyframe"))

    pos = {}      # entity -> [x, y]
    img = {}      # entity -> image
    texts = {}    # entity -> tooltip lines
    reg = {}      # entity -> registration
    site_ent = {}  # site id -> entity

    def apply(v):
        for eid, props in updates(v).items():
            x, y, im = last(XR, props), last(YR, props), last(IR, props)
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
                reg[int(k)] = o
                if o.get("type") == "Site":
                    site_ent[o["id"]] = int(k)
        if len(tt) > 1:
            for k, lines in tt[1].items():
                texts[int(k)] = lines

    head0, v0 = parse_view(frames[0]["view"])
    apply(v0)
    queen_ent = {}
    for eid, o in reg.items():
        if o.get("type") == "Queen":
            owner = 1 if "Unite_Base_Bleu" == img.get(eid - 2) else 0
            queen_ent[owner] = eid

    def site_radius(s):
        for l in texts.get(site_ent[s], []):
            if l.startswith("Radius: "):
                return int(l[8:])
        return None

    def site_struct(s):
        lines = texts.get(site_ent[s], [])
        return lines[2] if len(lines) > 2 else None

    res = {"gameId": g["gameId"], "league": league, "seed": seed, "refereeInput": g.get("refereeInput"),
           "agents": agents, "ranks": g.get("ranks"), "scores": g.get("scores"), "turns": nkey - 1,
           "sha256": sha, "bytes": len(raw), "file": league + "/" + os.path.basename(path)}
    stdout_build = {p: {"KNIGHT": 0, "ARCHER": 0, "GIANT": 0} for p in (0, 1)}
    barracks_built = {"KNIGHT": 0, "ARCHER": 0, "GIANT": 0}
    spawn_units = {p: {"KNIGHT": 0, "ARCHER": 0, "GIANT": 0} for p in (0, 1)}
    spawn_events = {"KNIGHT": 0, "ARCHER": 0, "GIANT": 0}
    first_spawn = None
    same_site = []
    conflicts = []
    cur = {}
    t = 0
    for fr in frames[1:]:
        a = fr.get("agentId", -1)
        if a >= 0 and "stdout" in fr:
            cur[a] = fr["stdout"]
        if not fr.get("keyframe"):
            continue
        t += 1
        cmds = [None, None]
        for p in (0, 1):
            so = cur.get(p)
            if so is not None:
                ls = so.split("\n")
                cmds[p] = ls[0].strip() if ls else None
                for ct in ("KNIGHT", "ARCHER", "GIANT"):
                    if ("BARRACKS-" + ct) in so:
                        stdout_build[p][ct] += 1
        # state at the start of turn t (before applying keyframe t)
        m0 = BUILD_RE.match(cmds[0] or "")
        m1 = BUILD_RE.match(cmds[1] or "")
        conflict_info = None
        if m0 and m1 and m0.group(1) == m1.group(1) and int(m0.group(1)) in site_ent:
            s = int(m0.group(1))
            se = site_ent[s]
            r = site_radius(s)
            sx, sy = pos[se]
            ds = []
            for p in (0, 1):
                qx, qy = pos[queen_ent[p]]
                ds.append(math.hypot(qx - sx, qy - sy))
            touch = [d < 30 + r + 5 for d in ds]
            st = site_struct(s)
            conflict_info = {"turn": t, "site": s, "radius": r, "types": [m0.group(2), m1.group(2)],
                             "queenDist": [round(d, 2) for d in ds], "touch": touch, "structBefore": st}
            same_site.append(conflict_info)
        head, v = parse_view(fr["view"])
        cur = {}
        if v is None:
            continue
        before_texts = {k: list(val) for k, val in texts.items()}
        new_imgs = {}
        for eid, props in updates(v).items():
            im = last(IR, props)
            if im is not None and OWNER_IMG.match(im):
                new_imgs[eid] = (im, last(XR, props), last(YR, props))
        tints = {}
        for eid, props in updates(v).items():
            c = last(CR, props)
            if c is not None:
                tints[eid] = int(c)
        apply(v)
        # spawns registered this frame
        tt = v.get("tooltips") or []
        spawned_types = set()
        if len(tt) > 0:
            for k, o in tt[0].items():
                ty = o.get("type")
                if ty in ("KNIGHT", "ARCHER", "GIANT"):
                    owner = 1 if img.get(int(k) - 2) == "Unite_Base_Bleu" else 0
                    spawn_units[owner][ty] += 1
                    spawned_types.add(ty)
        for ty in spawned_types:
            spawn_events[ty] += 1
        if spawned_types and first_spawn is None:
            first_spawn = t
        # barracks transitions this frame
        for s, se in site_ent.items():
            old = before_texts.get(se, [])
            new = texts.get(se, [])
            olds = old[2] if len(old) > 2 else None
            news = new[2] if len(new) > 2 else None
            if news and news.startswith("BARRACKS (") and news != olds:
                barracks_built[news[10:-1]] += 1
        if conflict_info is not None:
            s = conflict_info["site"]
            se = site_ent[s]
            sx, sy = pos[se]
            after = site_struct(s)
            conflict_info["structAfter"] = after
            conflict_info["viewHead"] = head
            owners = []
            for eid, (im, x, y) in new_imgs.items():
                p = pos.get(eid, [None, None])
                ex, ey = p
                d = math.hypot(ex - sx, ey - sy) if ex is not None and ey is not None else None
                owners.append({"entity": eid, "image": im, "dist": None if d is None else round(d, 1)})
            conflict_info["ownerImages"] = owners
            near = [o for o in owners if o["dist"] is not None and o["dist"] <= conflict_info["radius"] + 60]
            cols = {o["image"].split("_")[1] for o in (near or owners)}
            conflict_info["ownerColor"] = cols.pop() if len(cols) == 1 else ("ambiguous" if cols else None)
            conflict_info["builtBy"] = {"Rouge": 0, "Bleu": 1}.get(conflict_info["ownerColor"])
            conflict_info["tints"] = {str(k): val for k, val in tints.items()
                                      if pos.get(k, [None, None])[0] is not None
                                      and math.hypot(pos[k][0] - sx, pos[k][1] - sy) < 5}
            if all(conflict_info["touch"]) and conflict_info["structBefore"] is None:
                conflicts.append(conflict_info)
    res.update({
        "stdoutBarracks": stdout_build,
        "hasArcher": any(stdout_build[p]["ARCHER"] for p in (0, 1)),
        "hasGiant": any(stdout_build[p]["GIANT"] for p in (0, 1)),
        "barracksBuilt": barracks_built,
        "spawnUnits": spawn_units,
        "spawnEvents": spawn_events,
        "firstSpawnTurn": first_spawn,
        "sameSiteBuilds": same_site,
        "buildConflicts": conflicts,
        "hasBuildConflict": bool(conflicts),
    })
    return res


def main():
    root, out = sys.argv[1], sys.argv[2]
    games = []
    for path in sorted(glob.glob(os.path.join(root, "*", "*.json"))):
        name = os.path.basename(path)[:-5]
        if not name.isdigit():
            continue
        league = os.path.basename(os.path.dirname(path))
        try:
            games.append(analyze(path, league))
        except Exception as e:  # keep going, report
            games.append({"gameId": int(name), "league": league, "error": repr(e)})
            print("ERROR", path, repr(e))
    with open(out, "w", encoding="utf-8") as f:
        json.dump(games, f, indent=1)
    by = {}
    for gm in games:
        if "error" in gm:
            continue
        d = by.setdefault(gm["league"], {"games": 0, "archer": 0, "giant": 0, "archerSpawn": 0, "giantSpawn": 0,
                                          "conflict": 0, "sameSite": 0, "turns": 0})
        d["games"] += 1
        d["turns"] += gm["turns"]
        d["archer"] += gm["hasArcher"]
        d["giant"] += gm["hasGiant"]
        d["archerSpawn"] += gm["spawnEvents"]["ARCHER"] > 0
        d["giantSpawn"] += gm["spawnEvents"]["GIANT"] > 0
        d["conflict"] += gm["hasBuildConflict"]
        d["sameSite"] += bool(gm["sameSiteBuilds"])
    for k, d in sorted(by.items()):
        print(k, d)


if __name__ == "__main__":
    main()
