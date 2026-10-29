"""Snapshot of an arena entry: leaderboard row and last battles (public CodinGame services).

Usage: python -I tools/arena_snapshot.py <agentId> <pseudo> <league> <out.json> <raw_dir>
  league: wood3|wood2|wood1|bronze|silver|gold|legend, the league the entry is expected in; the row
  is looked up by pseudo (leaderboard KEYWORD filter, no 1000-row limit) and a different league is
  reported, as after a promotion

Writes only non-personal fields to <out.json>; the raw responses go to <raw_dir>, which should be
outside Git (for example replays/cg/arena-<agentId>/). Calls are paced one second apart.
Run it before replacing an entry and while following a placement.
"""
import json
import os
import sys
import time
import urllib.request

BASE = "https://www.codingame.com/services/"
LEAGUES = {"wood3": 0, "wood2": 1, "wood1": 2, "bronze": 3, "silver": 4, "gold": 5, "legend": 6}
NAMES = {v: k for k, v in LEAGUES.items()}


def call(service, body, raw_dir, name):
    req = urllib.request.Request(BASE + service, data=json.dumps(body).encode(), method="POST",
                                 headers={"Content-Type": "application/json;charset=UTF-8",
                                          "Accept": "application/json",
                                          "User-Agent": "cr-arena-snapshot/1.0 (paced)"})
    with urllib.request.urlopen(req, timeout=60) as r:
        raw = r.read()
    with open(os.path.join(raw_dir, name), "wb") as f:
        f.write(raw)
    time.sleep(1.0)
    return json.loads(raw)


def main():
    agent, pseudo, league, out_path, raw_dir = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
    os.makedirs(raw_dir, exist_ok=True)
    stamp = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    tag = time.strftime("%Y%m%d-%H%M%S")
    lb = call("Leaderboards/getFilteredPuzzleLeaderboard",
              ["code-royale", None, "global", {"active": True, "column": "KEYWORD", "filter": pseudo}],
              raw_dir, f"leaderboard-{pseudo}-{tag}.json")
    row = next((u for u in lb["users"] if u.get("pseudo") == pseudo), None)
    entry = None
    division = LEAGUES[league]
    if row:
        keep = ["rank", "localRank", "score", "eligibleForPromotion", "programmingLanguage", "updateTime",
                "creationTime", "percentage", "agentId", "inProgress"]
        entry = {k: row.get(k) for k in keep}
        division = row["league"]["divisionIndex"]
        entry["league"] = division
        entry["leagueAgents"] = row["league"]["divisionAgentsCount"]
        entry["pseudo"] = pseudo
        if division != LEAGUES[league]:
            print(f"{pseudo} is in league {NAMES.get(division, division)}, not {league}")
    else:
        print(f"no active leaderboard row for {pseudo}; battles of the requested agent {agent}")

    # A new submission may get a new agent id; the leaderboard row (matched by pseudo) has the current one.
    if row and row.get("agentId") != agent:
        print(f"leaderboard row of {pseudo} has agent {row.get('agentId')} (requested {agent}); using it for battles")
        agent = row["agentId"]
    battles = call("gamesPlayersRanking/findLastBattlesByAgentId", [agent, None], raw_dir, f"battles-{tag}.json")
    games = []
    for b in battles:
        players = b.get("players") or []
        me = next((p for p in players if p.get("playerAgentId") == agent), None)
        opp = next((p for p in players if p.get("playerAgentId") != agent), None)
        games.append({
            "gameId": b.get("gameId"), "done": b.get("done"),
            # "position" in a battle is the finishing rank (0 = winner), not the seat.
            "myRank": me.get("position") if me else None,
            "submissionId": me.get("submissionId") if me else None,
            "opponent": {"pseudo": opp.get("nickname"), "agentId": opp.get("playerAgentId"),
                         "rank": opp.get("position")} if opp else None,
        })
    done = [g for g in games if g["done"]]
    wins = sum(1 for g in done if g["myRank"] == 0 and g["opponent"] and g["opponent"]["rank"] == 1)
    out = {
        "fetched": stamp,
        "services": [f"Leaderboards/getFilteredPuzzleLeaderboard [code-royale, null, global, {{active, KEYWORD, {pseudo}}}]",
                     "gamesPlayersRanking/findLastBattlesByAgentId [agentId, null]"],
        "entry": entry,
        "leagueTotal": lb["leagues"][str(division)]["divisionAgentsCount"],
        "battles": {"count": len(games), "done": len(done), "wins": wins, "games": games},
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(json.dumps({"fetched": stamp, "entry": entry, "leagueTotal": out["leagueTotal"]}, ensure_ascii=False))
    print(f"battles {len(games)} done {len(done)} wins {wins}")


if __name__ == "__main__":
    main()
