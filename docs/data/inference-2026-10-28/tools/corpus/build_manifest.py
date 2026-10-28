"""Merge corpus facts into the replays/cg2 manifest.

Usage: python -I -X utf8 build_manifest.py <analysis.json> <selection.json> <agents.json> <resync-report.json> <lbdir> <out.json>
"""
import collections
import json
import os
import sys

analysis_p, sel_p, agents_p, report_p, lbdir, out_p = sys.argv[1:7]
analysis = [g for g in json.load(open(analysis_p, encoding="utf-8"))]
sel = json.load(open(sel_p, encoding="utf-8"))
agents_doc = json.load(open(agents_p, encoding="utf-8"))
report = {g["gameId"]: g for g in json.load(open(report_p, encoding="utf-8"))["perGame"]}
selrec = {g["gameId"]: g for g in sel["games"]}

lb = {}
for lg in ("bronze", "silver", "gold", "legend"):
    d = json.load(open(os.path.join(lbdir, "leaderboard-league-%s.json" % lg), encoding="utf-8"))
    lb[lg] = {"listedUsers": len(d["users"]), "filteredCount": d.get("filteredCount"), "count": d.get("count")}
    lb["divisions"] = {v["divisionIndex"]: v["divisionAgentsCount"] for v in d["leagues"].values()}

games = []
for g in sorted(analysis, key=lambda x: (x["league"], x["gameId"])):
    if "error" in g:
        raise SystemExit("analysis error: %r" % g)
    s = selrec[g["gameId"]]
    r = report.get(g["gameId"], {})
    su = g["spawnUnits"]
    conflicts = [{"turn": c["turn"], "site": c["site"], "radius": c["radius"], "commands": c["types"],
                  "queenDistToSite": c["queenDist"], "touchThreshold": 30 + c["radius"] + 5,
                  "builtBy": c["builtBy"], "ownerImage": c["ownerColor"], "structureAfterTurn": c["structAfter"],
                  "turnParity": "odd" if c["turn"] % 2 else "even"} for c in g["buildConflicts"]]
    games.append({
        "gameId": g["gameId"],
        "league": g["league"],
        "file": "replays/cg2/" + g["file"],
        "seed": g["seed"],
        "refereeInput": g["refereeInput"],
        "agents": [{"index": a["index"], "pseudo": a["pseudo"], "agentId": a["agentId"],
                    "ratingAtFetch": a["rating"], "valid": a["valid"]} for a in g["agents"]],
        "ranks": g["ranks"],
        "scores": g["scores"],
        "turns": g["turns"],
        "sha256": g["sha256"],
        "bytes": g["bytes"],
        "hasArcher": g["hasArcher"],
        "hasGiant": g["hasGiant"],
        "hasBuildConflict": g["hasBuildConflict"],
        "archerSpawned": g["spawnEvents"]["ARCHER"] > 0,
        "giantSpawned": g["spawnEvents"]["GIANT"] > 0,
        "barracksCommands": g["stdoutBarracks"],
        "barracksBuilt": g["barracksBuilt"],
        "spawnEvents": g["spawnEvents"],
        "spawnUnits": su,
        "firstSpawnTurn": g["firstSpawnTurn"],
        "buildConflicts": conflicts,
        "sameSiteBuildTurns": [c["turn"] for c in g["sameSiteBuilds"]],
        "selection": {"reason": s["reason"], "sourceAgent": s["sourceAgent"], "opponent": s["opponent"],
                      "battlePlayers": s["battlePlayers"]},
        "baseline": {"strict": r.get("strict"), "resync": r.get("resync"),
                     "resyncComparedTurns": r.get("resyncComparedTurns")},
    })

by = collections.OrderedDict()
for lg in ("bronze", "silver", "gold"):
    gs = [x for x in games if x["league"] == lg]
    by[lg] = {
        "games": len(gs), "turns": sum(x["turns"] for x in gs),
        "hasArcher": sum(x["hasArcher"] for x in gs), "hasGiant": sum(x["hasGiant"] for x in gs),
        "archerSpawned": sum(x["archerSpawned"] for x in gs), "giantSpawned": sum(x["giantSpawned"] for x in gs),
        "archerUnits": sum(x["spawnUnits"]["0"]["ARCHER"] + x["spawnUnits"]["1"]["ARCHER"] for x in gs),
        "giantUnits": sum(x["spawnUnits"]["0"]["GIANT"] + x["spawnUnits"]["1"]["GIANT"] for x in gs),
        "knightUnits": sum(x["spawnUnits"]["0"]["KNIGHT"] + x["spawnUnits"]["1"]["KNIGHT"] for x in gs),
        "hasBuildConflict": sum(x["hasBuildConflict"] for x in gs),
        "reasons": dict(collections.Counter(x["selection"]["reason"] for x in gs)),
        "gameIdRange": [min(x["gameId"] for x in gs), max(x["gameId"] for x in gs)],
        "fullLength250": sum(x["turns"] == 250 for x in gs),
    }

calls = sel.get("calls", []) + agents_doc.get("calls", [])
manifest = {
    "fetched": "2026-10-28",
    "path": "replays/cg2/<league>/<gameId>.json (gitignored); raw gameResult/findByGameId responses, unchanged bytes",
    "refetch": "POST https://www.codingame.com/services/gameResult/findByGameId body [gameId, null]; compare sha256",
    "source": {
        "slug": "code-royale",
        "services": ["Leaderboards/getFilteredPuzzleLeaderboard [\"code-royale\", null, \"global\", {\"active\":true,\"column\":\"LEAGUE\",\"filter\":\"<bronze|silver|gold|legend>\"}]",
                     "gamesPlayersRanking/findLastBattlesByAgentId [agentId, null]",
                     "gameResult/findByGameId [gameId, null]"],
        "leagueFilters": {"bronze": "divisionIndex 3 (top 1000 of 2295 listed)", "silver": "divisionIndex 4 (193 listed)",
                          "gold": "divisionIndex 5 (342 listed)", "legend": "divisionIndex 6 (108 listed, used only to label opponents)"},
        "leaderboardSnapshot": lb,
        "pacing": ">= 0.8 s between calls, exponential backoff retries; no errors occurred",
        "recordedCalls": len(calls),
    },
    "selection": {
        "league": "league of the agent whose battle list supplied the game (current leaderboard snapshot); opponents known to be in another league were skipped",
        "bronze": "24 agents ranked around our entry aabzac 3315948 (Bronze local rank 547..571 except 559) + every 40th listed Bronze agent",
        "silver": "every 6th listed Silver agent", "gold": "every 10th listed Gold agent",
        "probe": "newest eligible done game per agent (gameId >= 880000000, not in replays/cg, <= 2 games per opponent pseudo per league)",
        "focus": "extra games of agents seen spawning archers (Paloudi, Octet8, Tionkje, Taraxacum, gjjhgyu, -XraY-) and of Editsira (opponent of the first observed build conflict)",
        "excluded": "games already in replays/cg (aabzac's 10 arena games are there); games with gameId < 880000000 (years old)",
    },
    "flags": {
        "hasArcher/hasGiant": "a stdout line contains BARRACKS-ARCHER / BARRACKS-GIANT (command issued, maybe not executed)",
        "archerSpawned/giantSpawned": "an ARCHER / GIANT unit was registered in the replay view",
        "hasBuildConflict": "both players BUILD the same site on one turn, both queens touch it (dist < 30 + r + 5 at turn start) and it is empty; builtBy from the owner colour of the structure image (Rouge = player 0, Bleu = player 1)",
        "turn": "1-based: the t-th keyframe after the init frame (view header = 2t)",
    },
    "summaryByLeague": by,
    "games": games,
}
json.dump(manifest, open(out_p, "w", encoding="utf-8"), indent=1)
print(json.dumps(by, indent=1))
print("games:", len(games), "calls recorded:", len(calls))
