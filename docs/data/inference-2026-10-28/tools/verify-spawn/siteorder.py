"""Checks the creation order of creeps when several barracks complete on the same turn.
Groups new creeps of a frame by (owner, nearest site to their time-0 position) and checks that
entity ids (creation order) increase with site id. Usage: python -I siteorder.py <root>..."""
import collections
import json
import os
import re
import sys

XY = re.compile(r"(?:^| )x (-?\d+)(?: |$)")
YY = re.compile(r"(?:^| )y (-?\d+)(?: |$)")


def frame_of(view):
    if not view:
        return None
    nl = view.find("\n")
    if nl < 0:
        return None
    body = view[nl + 1:].strip()
    if not body:
        return None
    o = json.loads(body)
    return o.get("frame", o)


def main():
    multi = ok = mixed = 0
    bad = []
    for root in sys.argv[1:]:
        for dp, _, fs in os.walk(root):
            for f in sorted(fs):
                if not re.fullmatch(r"\d+\.json", f):
                    continue
                d = json.load(open(os.path.join(dp, f), encoding="utf-8"))
                frames = d["frames"]
                init = frame_of(frames[0]["view"])
                pos, img = {}, {}

                def apply(fm):
                    t0 = {}
                    for e in fm.get("entitymodule", []):
                        p = e.split(" ", 3)
                        if p[0] != "U":
                            continue
                        eid, time, rest = int(p[1]), p[2], (p[3] if len(p) > 3 else "")
                        mx, my = XY.search(rest), YY.search(rest)
                        if mx or my:
                            ox, oy = pos.get(eid, (None, None))
                            pos[eid] = (int(mx.group(1)) if mx else ox, int(my.group(1)) if my else oy)
                            if time == "0":
                                t0[eid] = pos[eid]
                        m = re.search(r"(?:^| )i (\S+)", rest)
                        if m:
                            img[eid] = m.group(1)
                    return t0

                apply(init)
                site_xy = {v["id"]: pos[int(k)] for k, v in init["tooltips"][0].items() if v.get("type") == "Site"}
                for fi, fr in enumerate(frames[1:], 1):
                    fm = frame_of(fr.get("view"))
                    if fm is None:
                        continue
                    t0 = apply(fm)
                    tt = fm.get("tooltips")
                    if not tt:
                        continue
                    groups = collections.defaultdict(list)
                    for k, v in tt[0].items():
                        if v.get("type") in ("KNIGHT", "ARCHER", "GIANT"):
                            eid = int(k)
                            owner = 1 if "Bleu" in img.get(eid - 2, "") else 0
                            x0, y0 = t0[eid]
                            sid = min(site_xy, key=lambda s: (site_xy[s][0] - x0) ** 2 + (site_xy[s][1] - y0) ** 2)
                            groups[sid].append((eid, owner))
                    if len(groups) < 2:
                        continue
                    multi += 1
                    owners = {o for g in groups.values() for _, o in g}
                    if len(owners) > 1:
                        mixed += 1
                    by_site = sorted(groups)
                    ids = [sorted(e for e, _ in groups[s]) for s in by_site]
                    if all(ids[i][-1] < ids[i + 1][0] for i in range(len(ids) - 1)):
                        ok += 1
                    else:
                        bad.append((f, fi, {s: groups[s] for s in by_site}))
    print(f"multi-barracks spawn frames={multi} (mixed owners {mixed}) site-id order={ok} other={len(bad)}")
    for b in bad[:10]:
        print("  ", b)


main()
