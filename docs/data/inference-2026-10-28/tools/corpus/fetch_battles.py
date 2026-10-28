"""Select agents per league from the saved league leaderboards and fetch their last battles.

Usage: python -I -X utf8 fetch_battles.py <lbdir> <battlesdir> <agents.json>
Bronze: the 24 agents ranked around our arena entry 3315948 (local rank +-12) plus a spread
over the listed top 1000; Silver: every 6th local rank; Gold: every 10th local rank.
Saved battle lists are reused (resume by agent id)."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cgapi  # noqa: E402

lbdir, bdir, agents_out = sys.argv[1:4]
os.makedirs(bdir, exist_ok=True)
OUR = 3315948


def users(league):
    with open(os.path.join(lbdir, "leaderboard-league-%s.json" % league), encoding="utf-8") as f:
        return json.load(f)["users"]


sel = []
bz = users("bronze")
pos = next(i for i, u in enumerate(bz) if u["agentId"] == OUR)
picked = set()
for u in bz[pos - 12:pos + 13]:
    if u["agentId"] == OUR:
        continue
    sel.append(("bronze", u, "near-%d" % OUR))
    picked.add(u["agentId"])
for i in range(0, len(bz), 40):
    u = bz[i]
    if u["agentId"] in picked or u["agentId"] == OUR:
        continue
    sel.append(("bronze", u, "spread"))
    picked.add(u["agentId"])
for league, step in (("silver", 6), ("gold", 10)):
    us = users(league)
    for i in range(0, len(us), step):
        sel.append((league, us[i], "spread"))

agents = []
for league, u, why in sel:
    a = u["agentId"]
    path = os.path.join(bdir, "battles-%d.json" % a)
    if not os.path.exists(path):
        raw = cgapi.call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [a, None])
        with open(path, "wb") as f:
            f.write(raw)
    with open(path, encoding="utf-8") as f:
        battles = json.load(f)
    agents.append({"league": league, "agentId": a, "pseudo": u["pseudo"], "localRank": u["localRank"],
                   "rank": u["rank"], "score": u["score"], "language": u["programmingLanguage"],
                   "why": why, "battles": len(battles), "done": sum(1 for b in battles if b.get("done"))})
    print("%-6s %-8s lr=%4d %-20s battles=%d" % (league, why[:8], u["localRank"], u["pseudo"], len(battles)))
with open(agents_out, "w", encoding="utf-8") as f:
    json.dump({"agents": agents, "calls": cgapi.LOG}, f, indent=1)
print("agents:", len(agents))
