"""Fetch the Code Royale leaderboard filtered by league; save raw responses; print a summary.
Usage: python -I explore_lb.py <outdir> <filter> [<filter> ...]"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cgapi  # noqa: E402

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for flt in sys.argv[2:]:
    body = ["code-royale", None, "global", {"active": True, "column": "LEAGUE", "filter": flt}]
    raw = cgapi.call_raw("Leaderboards/getFilteredPuzzleLeaderboard", body)
    path = os.path.join(out, "leaderboard-league-%s.json" % flt)
    with open(path, "wb") as f:
        f.write(raw)
    d = json.loads(raw.decode("utf-8"))
    users = d.get("users", [])
    print("filter=%s bytes=%d keys=%s users=%d" % (flt, len(raw), sorted(d.keys()), len(users)))
    if "leagues" in d:
        print("  leagues:", json.dumps(d["leagues"])[:600])
    if users:
        u = users[0]
        print("  user keys:", sorted(u.keys()))
        for u in users[:3] + users[-2:]:
            lg = u.get("league") or {}
            print("   rank=%s localRank=%s pseudo=%s score=%s agentId=%s league=%s lang=%s" % (
                u.get("rank"), u.get("localRank"), u.get("pseudo"), u.get("score"), u.get("agentId"),
                lg.get("divisionIndex"), u.get("programmingLanguage")))
        divs = {}
        for u in users:
            k = (u.get("league") or {}).get("divisionIndex")
            divs[k] = divs.get(k, 0) + 1
        print("  divisionIndex counts:", divs)
