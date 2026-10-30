"""Snapshot of an arena entry: leaderboard row and last battles (public CodinGame services).

Usage: python -I tools/arena_snapshot.py <agentId> <pseudo> <league> <out.json> [--registry path]
  league: wood3|wood2|wood1|bronze|silver|gold|legend, the league the entry is expected in; the row
  is looked up by pseudo (leaderboard KEYWORD filter, no 1000-row limit) and a different league is
  reported, as after a promotion

Writes only non-personal fields to <out.json>; raw responses go to the observed agent's registered
replays/arena/<label>/<league>/upload-NN/snapshots. An unregistered replacement or promotion is
preserved under replays/arena/unknown/<league>/agent-<id>/snapshots for later registration.
If out.json already belongs to another agent, it is kept; the new summary goes beside the raw
responses and the command fails with that path, so a promotion cannot overwrite the old placement.
Calls are paced one second apart and use the replay downloader's retry policy.
Run it before replacing an entry and while following a placement.
"""
import argparse
import json
from pathlib import Path
import sys
import time
sys.path.insert(0, str(Path(__file__).resolve().parent))
from arena_games import call_raw
from arena_paths import DEFAULT_REGISTRY, decode_battles, registry_entries, unknown_dir, upload_dir, write_raw

LEAGUES = {"wood3": 0, "wood2": 1, "wood1": 2, "bronze": 3, "silver": 4, "gold": 5, "legend": 6}
NAMES = {v: k for k, v in LEAGUES.items()}


def snapshot(agent, pseudo, league, out_path, uploads):
    stamp = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    tag = time.strftime("%Y%m%d-%H%M%S") + "-%09d" % (time.time_ns() % 1000000000)
    leaderboard_raw = call_raw("Leaderboards/getFilteredPuzzleLeaderboard",
                               ["code-royale", None, "global", {"active": True, "column": "KEYWORD", "filter": pseudo}])
    lb = json.loads(leaderboard_raw)
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
    actual_league = NAMES[division]
    upload = next((u for u in uploads if u["agent"] == agent), None)
    if upload and upload["league"] == actual_league:
        raw_dir = upload_dir(upload) / "snapshots"
    else:
        raw_dir = unknown_dir(agent, actual_league) / "snapshots"
        print("agent %d has no matching registered upload in %s; saved under %s; "
              "register its label, league and upload before downloading games" % (agent, actual_league, raw_dir.as_posix()))
    # The nickname is query data, not part of a filesystem path.
    write_raw(raw_dir / ("leaderboard-%s.json" % tag), leaderboard_raw)
    battles_raw = call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [agent, None])
    try:
        battles = decode_battles(battles_raw, agent)
    except ValueError:
        write_raw(raw_dir / ("rejected-battles-%s.json" % tag), battles_raw)
        raise
    write_raw(raw_dir / ("battles-%s.json" % tag), battles_raw)
    games = []
    for b in battles:
        players = b.get("players") or []
        me = next((p for p in players if p.get("playerAgentId") == agent), None)
        opp = next((p for p in players if p.get("playerAgentId") != agent), None)
        if me is None:
            raise ValueError("battle %r does not contain observed agent %d" % (b.get("gameId"), agent))
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
        "agentId": agent,
        "raw": raw_dir.as_posix(),
        "services": [f"Leaderboards/getFilteredPuzzleLeaderboard [code-royale, null, global, {{active, KEYWORD, {pseudo}}}]",
                     "gamesPlayersRanking/findLastBattlesByAgentId [agentId, null]"],
        "entry": entry,
        "leagueTotal": lb["leagues"][str(division)]["divisionAgentsCount"],
        "battles": {"count": len(games), "done": len(done), "wins": wins, "games": games},
    }
    out_path = Path(out_path)
    if out_path.exists():
        previous = json.loads(out_path.read_text(encoding="utf-8"))
        previous_agent = previous.get("agentId") or (previous.get("entry") or {}).get("agentId")
        if previous_agent is not None and previous_agent != agent:
            preserved = raw_dir / ("summary-%s.json" % tag)
            write_raw(preserved, (json.dumps(out, ensure_ascii=False, indent=1) + "\n").encode("utf-8"))
            print("FAIL requested output %s belongs to agent %s, not observed agent %d; "
                  "kept the previous summary; new snapshot saved at %s. Choose an output for the new agent." %
                  (out_path, previous_agent, agent, preserved.as_posix()), file=sys.stderr)
            return 1
    out_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = out_path.with_suffix(out_path.suffix + ".part")
    with open(temporary, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    temporary.replace(out_path)
    print(json.dumps({"fetched": stamp, "entry": entry, "leagueTotal": out["leagueTotal"]}, ensure_ascii=False))
    print(f"battles {len(games)} done {len(done)} wins {wins}")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("agent", type=int)
    parser.add_argument("pseudo")
    parser.add_argument("league", choices=LEAGUES)
    parser.add_argument("out")
    parser.add_argument("--registry", default=DEFAULT_REGISTRY)
    args = parser.parse_args(argv)
    try:
        uploads = registry_entries(args.registry)
        return snapshot(args.agent, args.pseudo, args.league, args.out, uploads)
    except (OSError, ValueError, KeyError) as e:
        print("FAIL snapshot: %s" % e, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
