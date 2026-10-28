"""Dump the replay state around a site-tooltip mismatch: commands of turn t, site tooltip before
and after, and every unit within 300 of the site at the start and the end of turn t.
Usage: python -I -X utf8 site_case.py <replay.json> <turn> <siteId>"""
import json
import math
import re
import sys

XR = re.compile(r"(?:^| )x (-?\d+)")
YR = re.compile(r"(?:^| )y (-?\d+)")
IR = re.compile(r"(?:^| )i (\S+)")


def last(rx, s):
    r = None
    for m in rx.finditer(s):
        r = m.group(1)
    return r


g = json.load(open(sys.argv[1], encoding="utf-8"))
T, S = int(sys.argv[2]), int(sys.argv[3])
pos, img, texts, typ, site_ent = {}, {}, {}, {}, {}


def view(raw):
    nl = raw.find("\n")
    body = raw[nl + 1:].strip() if nl >= 0 else ""
    if not body:
        return {}
    v = json.loads(body)
    return v.get("frame", v)


def apply(v):
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
            if o.get("type") == "Site":
                site_ent[o["id"]] = int(k)
            elif o.get("type"):
                typ[int(k)] = o["type"]
    if len(tt) > 1:
        for k, lines in tt[1].items():
            texts[int(k)] = lines


def snapshot(label):
    se = site_ent[S]
    sx, sy = pos[se]
    r = int(texts[se][0].split(": ")[1])
    print("  [%s] site %d at (%d,%d) r=%d tooltip=%s" % (label, S, sx, sy, r, texts[se]))
    for eid, t in sorted(typ.items()):
        lines = texts.get(eid - 2) or []
        hp = lines[0].replace("Health: ", "") if lines else "?"
        if t != "Queen" and hp == "0":
            continue
        x, y = pos.get(eid, [None, None])
        if x is None:
            continue
        d = math.hypot(x - sx, y - sy)
        if d > 300:
            continue
        owner = 1 if img.get(eid - 2) == "Unite_Base_Bleu" else 0
        rad = {"Queen": 30, "KNIGHT": 20, "ARCHER": 25, "GIANT": 40}[t]
        print("     %s owner%d (%d,%d) hp%s dist %.2f  touch-threshold %d  gap %.2f" % (t, owner, x, y, hp, d, r + rad + 5, d - (r + rad + 5)))


apply(view(g["frames"][0]["view"]))
print("agents", [(a["index"], (a.get("codingamer") or {}).get("pseudo")) for a in g["agents"]])
t = 0
cur = {}
for fr in g["frames"][1:]:
    if fr.get("agentId", -1) >= 0:
        cur[fr["agentId"]] = fr.get("stdout")
    if not fr.get("keyframe"):
        continue
    t += 1
    if t == T:
        print("turn", T, "cmds", cur)
        snapshot("start of turn %d" % T)
    v = view(fr["view"])
    apply(v)
    if t == T:
        snapshot("end of turn %d" % T)
        print("  anims:", json.dumps(v.get("anims"))[:600])
        break
    cur = {}
