"""List games where archers/giants were commanded or spawned, and same-site builds.
Usage: python -I -X utf8 list_usage.py <analysis.json>"""
import json
import sys

A = json.load(open(sys.argv[1], encoding="utf-8"))
users = {}
for g in A:
    if "error" in g:
        print("ERR", g)
        continue
    sb, su = g["stdoutBarracks"], g["spawnUnits"]
    tags = []
    for p in ("0", "1"):
        a = g["agents"][int(p)]
        s = []
        for ct in ("ARCHER", "GIANT"):
            if sb[p][ct] or su[p][ct]:
                s.append("%s cmd%d units%d" % (ct[0], sb[p][ct], su[p][ct]))
                u = users.setdefault((g["league"], a["agentId"], a["pseudo"]), {"A": 0, "G": 0, "Aunits": 0, "Gunits": 0, "games": set()})
                u[ct[0]] += 1 if sb[p][ct] else 0
                u[ct[0] + "units"] += su[p][ct]
                u["games"].add(g["gameId"])
        if s:
            tags.append("p%s %s(%d): %s" % (p, a["pseudo"], a["agentId"], ", ".join(s)))
    if tags:
        print(g["league"], g["gameId"], "turns", g["turns"], " | ".join(tags))
    for c in g["sameSiteBuilds"]:
        print("   same-site", g["league"], g["gameId"], json.dumps(c))
print()
for k, u in sorted(users.items(), key=lambda kv: (kv[0][0], -kv[1]["Aunits"], -kv[1]["Gunits"])):
    print(k, {kk: (sorted(vv) if isinstance(vv, set) else vv) for kk, vv in u.items()})
