"""Print init entity creations and the entity updates of turns around the first builds.
Usage: python -I peek_view.py <replay.json> [maxTurns]"""
import json
import sys

g = json.load(open(sys.argv[1], encoding="utf-8"))
maxt = int(sys.argv[2]) if len(sys.argv) > 2 else 3
frames = g["frames"]


def view(fr):
    raw = fr["view"]
    nl = raw.index("\n")
    return json.loads(raw[nl + 1:])


init = view(frames[0])
print("init keys:", list(init.keys()))
gl = init.get("global", {})
print("global keys:", list(gl.keys()) if isinstance(gl, dict) else type(gl))
fr0 = init["frame"]
print("frame keys:", list(fr0.keys()))
ents = fr0["entitymodule"]
print("n entity cmds:", len(ents))
for e in ents[:60]:
    print("  ", e[:200])
tt = fr0.get("tooltips")
print("tooltips reg sample:", json.dumps(tt[0])[:600] if tt else None)
turn = 0
shown = 0
for i, fr in enumerate(frames[1:], 1):
    if fr.get("stdout") and "BUILD" in fr["stdout"]:
        pass
    if fr.get("keyframe"):
        turn += 1
        v = view(fr)
        prev = frames[i - 1].get("stdout", "")
        cur = fr.get("stdout", "")
        if ("BUILD" in prev or "BUILD" in cur) and shown < maxt:
            shown += 1
            print("=== turn", turn, "p0:", repr(prev), "p1:", repr(cur))
            print("  view keys:", list(v.keys()))
            for e in v.get("entitymodule", []):
                print("    ", e[:300])
            print("  tooltips:", json.dumps(v.get("tooltips"))[:800])
            print("  other modules:", {k: (json.dumps(val)[:300]) for k, val in v.items() if k not in ("entitymodule", "tooltips")})
