"""Show keyframe entity updates for the first turns where a given structure first appears in a
site tooltip. Usage: python -I peek_build.py <replay.json> <TOWER|MINE|BARRACKS> [n]"""
import json
import sys

g = json.load(open(sys.argv[1], encoding="utf-8"))
want = sys.argv[2]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 1
frames = g["frames"]
state = {}
turn = -1
shown = 0
cur_stdout = {}
for i, fr in enumerate(frames):
    if fr.get("agentId", -1) >= 0 and "stdout" in fr:
        cur_stdout[fr["agentId"]] = fr["stdout"]
    if not fr.get("keyframe"):
        continue
    turn += 1  # 0 = init keyframe
    raw = fr["view"]
    nl = raw.find("\n")
    body = raw[nl + 1:].strip() if nl >= 0 else ""
    cmds = dict(cur_stdout)
    cur_stdout = {}
    if not body:
        continue
    v = json.loads(body)
    if "frame" in v:
        v = v["frame"]
    tt = v.get("tooltips") or [{}, {}]
    texts = tt[1] if len(tt) > 1 else {}
    hit = []
    for ent, lines in texts.items():
        old = state.get(ent)
        state[ent] = lines
        if any(l.startswith(want) for l in lines) and not (old and any(l.startswith(want) for l in old)):
            hit.append((ent, lines))
    if hit and turn > 0 and shown < n:
        shown += 1
        print("=== turn", turn, "view prefix", raw[:nl].strip(), "cmds", cmds)
        print("  new:", hit)
        for e in v.get("entitymodule", []):
            print("    ", e[:260])
        print("  anims:", json.dumps(v.get("anims"))[:600])
