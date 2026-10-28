"""Check the live 'Training too many creeps (N total units requested; M maximum)' warning against
replay state: N =? alive creeps of the player at the start of the turn + units of the TRAIN list.
Also lists turns where alive + requested > 30 but no warning was given (to bound the rule).
Usage: python -I -X utf8 unit_cap.py <replay.json>..."""
import json
import re
import sys

COUNT = {"KNIGHT": 4, "ARCHER": 2, "GIANT": 1}
WARN = re.compile(r"\$(\d): \[WARNING\] Training too many creeps \((\d+) total units requested; (\d+) maximum\)")
MONEY = re.compile(r"\$(\d): \[WARNING\] Training too many creeps \((\d+) total gold requested\)")


def view(raw):
    nl = raw.find("\n")
    body = raw[nl + 1:].strip() if nl >= 0 else ""
    if not body:
        return {}
    v = json.loads(body)
    return v.get("frame", v)


for path in sys.argv[1:]:
    g = json.load(open(path, encoding="utf-8"))
    img, texts, typ, site_ent = {}, {}, {}, {}

    def apply(v):
        for e in v.get("entitymodule", []) or []:
            if e.startswith("U "):
                parts = e.split(" ", 3)
                if len(parts) == 4:
                    m = None
                    for m in re.finditer(r"(?:^| )i (\S+)", parts[3]):
                        pass
                    if m:
                        img[int(parts[1])] = m.group(1)
        tt = v.get("tooltips") or []
        if len(tt) > 0:
            for k, o in tt[0].items():
                if o.get("type") == "Site":
                    site_ent[o["id"]] = int(k)
                elif o.get("type") in COUNT:
                    typ[int(k)] = o["type"]
        if len(tt) > 1:
            for k, lines in tt[1].items():
                texts[int(k)] = lines

    def alive(p):
        n = 0
        for eid, t in typ.items():
            lines = texts.get(eid - 2) or []
            if lines and lines[0] == "Health: 0":
                continue
            owner = 1 if img.get(eid - 2) == "Unite_Base_Bleu" else 0
            n += owner == p
        return n

    def barracks_type(s):
        lines = texts.get(site_ent.get(s, -1), [])
        for l in lines:
            if l.startswith("BARRACKS ("):
                return l[10:-1]
        return None

    apply(view(g["frames"][0]["view"]))
    t = 0
    cur = {}
    for fr in g["frames"][1:]:
        if fr.get("agentId", -1) >= 0:
            cur[fr["agentId"]] = fr.get("stdout") or ""
        if not fr.get("keyframe"):
            continue
        t += 1
        summ = fr.get("summary") or ""
        warned = {int(m.group(1)): (int(m.group(2)), int(m.group(3))) for m in WARN.finditer(summ)}
        money = {int(m.group(1)) for m in MONEY.finditer(summ)}
        for p in (0, 1):
            so = cur.get(p, "").split("\n")
            if len(so) < 2 or not so[1].startswith("TRAIN"):
                continue
            ids = [int(x) for x in so[1].split()[1:] if x.isdigit()]
            req = 0
            ok = True
            for s in ids:
                bt = barracks_type(s)
                if bt is None:
                    ok = False
                else:
                    req += COUNT[bt]
            a = alive(p)
            if p in warned:
                n, mx = warned[p]
                print("%s t%d p%d warn N=%d max=%d | alive=%d requested=%d (sites %s%s) -> alive+req=%d %s" % (
                    path.replace("\\", "/").split("/replays/")[-1], t, p, n, mx, a, req, ids, "" if ok else ", some not barracks",
                    a + req, "MATCH" if a + req == n else "DIFF"))
            elif ok and ids and a + req > 30 and p not in money:
                print("%s t%d p%d NO-WARN alive=%d requested=%d sites %s summary=%r" % (
                    path.replace("\\", "/").split("/replays/")[-1], t, p, a, req, ids, summ[:120]))
        apply(view(fr["view"]))
        cur = {}
