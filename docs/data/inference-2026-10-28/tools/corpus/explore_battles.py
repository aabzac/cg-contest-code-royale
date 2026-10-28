"""Fetch last battles for given agent ids; save raw; print summary.
Usage: python -I explore_battles.py <outdir> <agentId> [...]"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cgapi  # noqa: E402

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for a in sys.argv[2:]:
    raw = cgapi.call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [int(a), None])
    with open(os.path.join(out, "battles-%s.json" % a), "wb") as f:
        f.write(raw)
    d = json.loads(raw.decode("utf-8"))
    done = [b for b in d if b.get("done")]
    print("agent %s: %d battles, %d done, bytes=%d" % (a, len(d), len(done), len(raw)))
    if d:
        print("  keys:", sorted(d[0].keys()), "player keys:", sorted(d[0]["players"][0].keys()))
        ids = [b["gameId"] for b in d]
        print("  gameId range:", min(ids), max(ids))
        for b in d[:3]:
            print("  ", b["gameId"], b["done"], [(p["nickname"], p["playerAgentId"], p["position"]) for p in b["players"]])
