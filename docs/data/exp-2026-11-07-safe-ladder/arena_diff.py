"""Where SAFE_LADDER=1 would change v10's arena games (2026-11-07).

`cgAttribute -Pbot=current?SAFE_LADDER=1` replays v10's 160 Silver games with the recorded commands and compares the
flagged bot's answer with v10's on every turn; a DIFF game is one where the flag changes at least one decision. For each
such game this prints the result, the first changed turn and the two commands there, and from the queen's trace
(`cr.tools.DeathTrace`, the study of 2026-11-07) the HP the recorded game lost to enemy towers from that turn on and
whether the queen died. The games themselves would go on differently from the first changed turn, so these are the
recorded consequences of the decision the flag changes, not a prediction.

Usage (from the repository root):
python -B -I docs/data/exp-2026-11-07-safe-ladder/arena_diff.py <attribute output> [<attribute output> ...]
The attribute outputs are those of the commands in README.md; the traces are read from
replays/study/tower-fire-2026-11-07/trace-<agent>.txt and the manifests from docs/data/.
"""
import json
import re
import sys

MANIFESTS = {"6857063": "docs/data/arena-6857063-games-2026-11-07.json", "6857144": "docs/data/arena-6857144-games-2026-11-07.json"}
DIFF = re.compile(r"^DIFF (\d+) seat (\d) vs (\S+): .*first at turn (\d+): recorded \[(.*?)\] bot \[(.*?)\]")
ATTR = re.compile(r"attribution of (\d+) to")
HDR = re.compile(r"=== game (\d+) seat (\d) vs (\S+) \(ranks (-?\d+)/(-?\d+), scores (-?\d+)/(-?\d+), (\d+) turns\)")
TURN = re.compile(r"^t\s*(\d+) .* kd (-?\d+) td (-?\d+)")


def traces(agent):
    out, game = {}, None
    for line in open("replays/study/tower-fire-2026-11-07/trace-%s.txt" % agent, encoding="utf-8-sig"):
        h = HDR.search(line)
        if h:
            game = int(h.group(1))
            out[game] = {"died": int(h.group(6)) == 0 and int(h.group(4)) == 1, "td": {}}
            continue
        m = TURN.match(line)
        if m and game is not None:
            out[game]["td"][int(m.group(1))] = int(m.group(3))
    return out


def main(paths):
    rows = []
    for path in paths:
        text = open(path, encoding="utf-8-sig").read()
        agent = ATTR.search(text).group(1)
        manifest = {g["gameId"]: g for g in json.load(open(MANIFESTS[agent], encoding="utf-8"))["games"]}
        tr = traces(agent)
        for line in text.splitlines():
            m = DIFF.match(line)
            if not m:
                continue
            gid, turn = int(m.group(1)), int(m.group(4))
            g = manifest[gid]
            me, foe = g["ranks"][g["seat"]], g["ranks"][1 - g["seat"]]
            result = "won" if me < foe else "lost" if me > foe else "tie"
            t = tr.get(gid, {"died": False, "td": {}})
            after = sum(v for k, v in t["td"].items() if k >= turn)
            total = sum(t["td"].values())
            rows.append((result, gid, m.group(3), turn, m.group(5), m.group(6), after, total, t["died"]))
    rows.sort(key=lambda r: (r[0], -r[6]))
    print("%-5s %-10s %-16s %5s  %-34s %-34s %s" % ("game", "id", "opponent", "turn", "recorded (v10)", "SAFE_LADDER=1",
                                                   "tower HP after / in game, queen died"))
    for r in rows:
        print("%-5s %-10d %-16s %5d  %-34s %-34s %3d / %3d %s" % (r[0], r[1], r[2][:16], r[3], r[4][:34], r[5][:34], r[6], r[7],
                                                                  "died" if r[8] else ""))
    for result in ("won", "lost", "tie"):
        sel = [r for r in rows if r[0] == result]
        if sel:
            print("%s: %d games; tower HP after the first changed turn %d (in %d games); queens died %d" % (
                result, len(sel), sum(r[6] for r in sel), sum(1 for r in sel if r[6] > 0), sum(1 for r in sel if r[8])))


if __name__ == "__main__":
    main(sys.argv[1:])
