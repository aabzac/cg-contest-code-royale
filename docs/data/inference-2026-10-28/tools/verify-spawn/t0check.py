"""Independent check of the spawn offset hypothesis from time-0 commits in replay frames.

For each creep registered in a frame, its token group gets a time-0 update (position right after
`towards`, truncated to int by the Entity.location setter) and a later final update. We predict
the time-0 position as int(towards(siteCentre + offset[iter], enemyQueenFinal, 30)) and count
exact matches for several offset hypotheses. Usage: python -I t0check.py <replays-root>...
"""
import collections
import json
import math
import os
import re
import sys

XY = re.compile(r"(?:^| )x (-?\d+)(?: |$)")
YY = re.compile(r"(?:^| )y (-?\d+)(?: |$)")


def frame_of(view):
    if not view:
        return None, None
    nl = view.find("\n")
    if nl < 0 or nl + 1 >= len(view):
        return None, None
    body = view[nl + 1:].strip()
    if not body:
        return None, None
    o = json.loads(body)
    return view[:nl].strip(), o.get("frame", o)


def towards(px, py, tx, ty, d):
    dx, dy = tx - px, ty - py
    if dx * dx + dy * dy < d * d:
        return tx, ty
    l = math.sqrt(dx * dx + dy * dy)
    return px + dx / l * d, py + dy / l * d


HYP = {
    "corner": [(1, -1), (-1, 1), (1, 1), (-1, -1)],
    "repo_p0": [(0, 0), (1, 1), (2, 2), (3, 3)],
    "repo_p1": [(0, 0), (-1, -1), (-2, -2), (-3, -3)],
    "zero": [(0, 0)] * 4,
    "swap01": [(-1, 1), (1, -1), (1, 1), (-1, -1)],
    "corner_cw": [(1, 1), (-1, -1), (1, -1), (-1, 1)],
}


def analyse(path, stats, resid):
    d = json.load(open(path, encoding="utf-8"))
    frames = d["frames"]
    _, init = frame_of(frames[0]["view"])
    reg0 = init["tooltips"][0]
    pos = {}  # entity id -> (x, y) latest
    img = {}

    def apply(fm):
        t0 = {}
        for e in fm.get("entitymodule", []):
            p = e.split(" ", 3)
            if p[0] != "U":
                continue
            eid, time = int(p[1]), p[2]
            rest = p[3] if len(p) > 3 else ""
            mx, my = XY.search(rest), YY.search(rest)
            if mx or my:
                ox, oy = pos.get(eid, (None, None))
                pos[eid] = (int(mx.group(1)) if mx else ox, int(my.group(1)) if my else oy)
                if time == "0":
                    t0[eid] = pos[eid]
            if " i " in " " + rest:
                m = re.search(r"(?:^| )i (\S+)", rest)
                if m:
                    img[eid] = m.group(1)
        return t0

    apply(init)
    sites = {}
    queens = {}
    for k, v in reg0.items():
        if v.get("type") == "Site":
            sites[v["id"]] = int(k)
        elif v.get("type") == "Queen":
            owner = 1 if "Bleu" in img.get(int(k) - 2, "") else 0
            queens[owner] = int(k)
    site_xy = {sid: pos[eid] for sid, eid in sites.items()}
    for f in frames[1:]:
        hdr, fm = frame_of(f.get("view"))
        if fm is None:
            continue
        t0 = apply(fm)
        tt = fm.get("tooltips")
        if not tt:
            continue
        new = []
        for k, v in tt[0].items():
            ty = v.get("type")
            if ty in ("KNIGHT", "ARCHER", "GIANT"):
                eid = int(k)
                owner = 1 if "Bleu" in img.get(eid - 2, "") else 0
                new.append((eid, ty, owner))
        if not new:
            continue
        # group by (owner, nearest site to time-0 position)
        groups = collections.defaultdict(list)
        for eid, ty, owner in sorted(new):
            if eid not in t0:
                stats["no_t0"] += 1
                continue
            x0, y0 = t0[eid]
            sid = min(site_xy, key=lambda s: (site_xy[s][0] - x0) ** 2 + (site_xy[s][1] - y0) ** 2)
            groups[(owner, sid)].append((eid, ty, x0, y0))
        for (owner, sid), lst in groups.items():
            cx, cy = site_xy[sid]
            qx, qy = pos[queens[1 - owner]]
            for it, (eid, ty, x0, y0) in enumerate(lst):
                stats[("n", ty)] += 1
                stats[("n_iter", ty, it)] += 1
                for h, offs in HYP.items():
                    ox, oy = offs[it % 4]
                    px, py = towards(cx + ox, cy + oy, qx, qy, 30.0)
                    if (int(px), int(py)) == (x0, y0):
                        stats[(h, ty)] += 1
                        stats[(h, ty, it)] += 1
                # corner added after towards
                ox, oy = HYP["corner"][it % 4]
                px, py = towards(cx, cy, qx, qy, 30.0)
                if (int(px + ox), int(py + oy)) == (x0, y0):
                    stats[("corner_after", ty)] += 1
                # residual against towards from the centre (rounded)
                px, py = towards(cx, cy, qx, qy, 30.0)
                resid[(ty, it)][(round(x0 + 0.5 - px), round(y0 + 0.5 - py))] += 1


def main():
    stats = collections.Counter()
    resid = collections.defaultdict(collections.Counter)
    nfiles = 0
    for root in sys.argv[1:]:
        for dp, _, fs in os.walk(root):
            for f in sorted(fs):
                if re.fullmatch(r"\d+\.json", f):
                    analyse(os.path.join(dp, f), stats, resid)
                    nfiles += 1
    print("files", nfiles, "no_t0", stats["no_t0"])
    for ty in ("KNIGHT", "ARCHER", "GIANT"):
        n = stats[("n", ty)]
        if not n:
            continue
        row = [f"{h}={stats[(h, ty)]}" for h in list(HYP) + ["corner_after"]]
        print(f"{ty}: n={n} " + " ".join(row))
        for it in range(4):
            ni = stats[("n_iter", ty, it)]
            if ni:
                print(f"   iter{it}: n={ni} " + " ".join(f"{h}={stats[(h, ty, it)]}" for h in HYP))
    print("residual (time0 + 0.5 - towardsFromCentre, rounded) top values per (type, iter):")
    for k in sorted(resid):
        print("  ", k, resid[k].most_common(4))


main()
