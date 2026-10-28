"""Count tower-health events in replays that only giants explain (drop > melt rate 4) and check
whether the validator compared those turns. Usage:
python -I -X utf8 tower_events.py <replay_root> <analysis.json> <resync-report.json>"""
import collections
import json
import os
import sys

root, analysis_p, report_p = sys.argv[1:4]
analysis = {g["gameId"]: g for g in json.load(open(analysis_p, encoding="utf-8"))}
report = {g["gameId"]: g for g in json.load(open(report_p, encoding="utf-8"))["perGame"]}
tot = collections.Counter()
for gid, a in sorted(analysis.items()):
    g = json.load(open(os.path.join(root, a["file"]), encoding="utf-8"))
    texts = {}
    site_ent = {}

    def view(raw):
        nl = raw.find("\n")
        body = raw[nl + 1:].strip() if nl >= 0 else ""
        if not body:
            return {}
        v = json.loads(body)
        return v.get("frame", v)

    def apply(v):
        tt = v.get("tooltips") or []
        if len(tt) > 0:
            for k, o in tt[0].items():
                if o.get("type") == "Site":
                    site_ent[o["id"]] = int(k)
        if len(tt) > 1:
            for k, lines in tt[1].items():
                texts[int(k)] = lines

    def tower_hp():
        r = {}
        for s, e in site_ent.items():
            lines = texts.get(e, [])
            if len(lines) >= 5 and lines[2] == "TOWER":
                r[s] = int(lines[4].split(": ")[1])
        return r

    apply(view(g["frames"][0]["view"]))
    prev = tower_hp()
    t = 0
    compared = report[gid]["resyncComparedTurns"]
    for fr in g["frames"][1:]:
        if not fr.get("keyframe"):
            continue
        t += 1
        apply(view(fr["view"]))
        cur = tower_hp()
        for s, hp in prev.items():
            after = cur.get(s)
            drop = hp - (after if after is not None else 0)
            if after is None:
                # removed: giant bust or melt below 0 (hp <= 4) or replaced
                if hp > 4:
                    tot[(a["league"], "tower removed with hp>4", t <= compared)] += 1
            elif drop > 4 and drop % 80 == 4:
                tot[(a["league"], "giant bust x%d" % (drop // 80), t <= compared)] += 1
            elif drop > 4:
                tot[(a["league"], "other drop %d" % drop, t <= compared)] += 1
        prev = cur
for k, v in sorted(tot.items()):
    print(k, v)
