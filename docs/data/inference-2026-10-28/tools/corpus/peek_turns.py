"""Print commands, keyframe entity updates (filtered by entity ids or all), tooltips and anims for
turns a..b. Usage: python -I -X utf8 peek_turns.py <replay.json> <a> <b> [filter-substring]"""
import json
import sys

g = json.load(open(sys.argv[1], encoding="utf-8"))
a, b = int(sys.argv[2]), int(sys.argv[3])
flt = sys.argv[4] if len(sys.argv) > 4 else None
print("agents:", [(x["index"], (x.get("codingamer") or {}).get("pseudo"), x["agentId"]) for x in g["agents"]],
      "ranks", g["ranks"], "scores", g["scores"])
t = 0
cur = {}
for fr in g["frames"][1:]:
    if fr.get("agentId", -1) >= 0:
        cur[fr["agentId"]] = (fr.get("stdout"), fr.get("summary"))
    if not fr.get("keyframe"):
        continue
    t += 1
    if a <= t <= b:
        raw = fr["view"]
        nl = raw.find("\n")
        print("=== turn", t, "head", raw[:nl].strip(), "cmds", cur)
        body = raw[nl + 1:].strip()
        if body:
            v = json.loads(body)
            v = v.get("frame", v)
            for e in v.get("entitymodule", []):
                if flt is None or flt in e:
                    print("   ", e[:240])
            print("  tooltips:", json.dumps(v.get("tooltips"))[:1500])
            print("  anims:", json.dumps(v.get("anims"))[:800])
    cur = {}
