"""Downloads the finished games of an arena agent unchanged and writes a manifest (public CodinGame services).

Usage: python -I tools/arena_games.py <agentId> <raw_dir> <manifest.json> [battles.json ...]
  The battle list is fetched now (gamesPlayersRanking/findLastBattlesByAgentId), saved as
  <raw_dir>/battles-<time>.json and merged with the saved lists given after the manifest, so the games
  stay in the manifest after the agent is replaced (a promotion or the next upload empties its list).
  A game counts as finished when any row says so or its file is already stored. Games are stored as
  <raw_dir>/<gameId>.json (raw gameResult/findByGameId bytes, should be outside Git); stored files are
  not fetched again, so a run resumes. A response that is not a complete two-player game is a failure
  and is not stored; one bad game does not stop the run. Calls are paced one second apart, and 429,
  5xx and network errors are retried after 5, 20 and 60 s.

The manifest lists per game: the agent's seat (gameResult agents[].index), opponent, ranks, scores,
seed, frame count, sha256 and size, the build marker of the agent's turn-0 TRAIN line (CGRunner.marker,
null without one), and per failure the reason. Wins follow CG's ranks, which also order games with
equal scores (scoreTies counts those). Refetch: POST gameResult/findByGameId [gameId, null] and
compare sha256.
"""
import glob
import hashlib
import http.client
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "https://www.codingame.com/services/"
RETRY_S = (5, 20, 60)


def call_raw(service, body):
    for attempt in range(len(RETRY_S) + 1):
        req = urllib.request.Request(BASE + service, data=json.dumps(body).encode(), method="POST",
                                     headers={"Content-Type": "application/json;charset=UTF-8",
                                              "Accept": "application/json",
                                              "User-Agent": "cr-arena-games/1.0 (paced)"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if (e.code != 429 and e.code < 500) or attempt == len(RETRY_S):
                raise
            print("  HTTP %d from %s, retry in %d s" % (e.code, service, RETRY_S[attempt]))
        except (OSError, http.client.HTTPException) as e:
            if attempt == len(RETRY_S):
                raise
            print("  %r from %s, retry in %d s" % (e, service, RETRY_S[attempt]))
        finally:
            time.sleep(1.0)
        time.sleep(RETRY_S[attempt])


def battles_of(agent, saved, raw_dir, tag):
    """gameId -> battle row, from every saved list and the live one; a finished row replaces an unfinished one."""
    rows = {}

    def add(b):
        old = rows.get(b["gameId"])
        if old is None or (b.get("done") and not old.get("done")):
            rows[b["gameId"]] = b

    for path in saved:
        with open(path, encoding="utf-8") as f:
            for b in json.load(f):
                add(b)
    raw = call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [agent, None])
    with open(os.path.join(raw_dir, "battles-%s.json" % tag), "wb") as f:
        f.write(raw)
    live = json.loads(raw)
    for b in live:
        add(b)
    return rows, len(live)


MARKER_SITES = [str(d) for d in range(16)]


def marker(train_line):
    """The build id a TRAIN line carries ("TRAIN" and 8 site ids 0-15, one per hex digit), or None (cr.submit.Build.decode)."""
    t = (train_line or "").split(" ")
    if len(t) != 9 or t[0] != "TRAIN" or any(s not in MARKER_SITES for s in t[1:]):
        return None
    return "".join("%x" % int(s) for s in t[1:])


def turn0_marker(frames, seat):
    """The marker in the seat's first answer (frames carry the in-game index as agentId), or None."""
    first = next((f for f in frames[1:] if f.get("agentId") == seat), None)
    lines = ((first or {}).get("stdout") or "").split("\n")
    return marker(lines[1]) if len(lines) > 1 else None


def describe(raw, gid, agent):
    """Manifest record of a response, or raises ValueError for one that is not a complete two-player game."""
    g = json.loads(raw.decode("utf-8"))
    if not isinstance(g, dict) or not g.get("frames") or g.get("gameId") != gid:
        raise ValueError("incomplete response (%d bytes): %r" % (len(raw), raw[:200]))
    agents = g.get("agents")
    if not isinstance(agents, list) or len(agents) != 2 or any(
            not isinstance(a, dict) or "index" not in a or "agentId" not in a for a in agents):
        raise ValueError("agents: %r" % (agents,))
    for key in ("ranks", "scores"):
        if not isinstance(g.get(key), list) or len(g[key]) != 2:
            raise ValueError("%s: %r" % (key, g.get(key)))
    if not isinstance(g.get("refereeInput"), str):
        raise ValueError("no refereeInput")
    seat = next((a["index"] for a in agents if a["agentId"] == agent), None)
    if seat is None:
        raise ValueError("agent %d not among the players" % agent)
    opp = next(a for a in agents if a["index"] != seat)
    seed = next((l.strip()[5:] for l in g["refereeInput"].split("\n") if l.strip().startswith("seed=")), None)
    return {
        "gameId": gid, "seat": seat,
        "opponent": {"pseudo": (opp.get("codingamer") or {}).get("pseudo"), "agentId": opp["agentId"],
                     "ratingAtFetch": opp.get("score")},
        "ranks": g["ranks"], "scores": g["scores"], "seed": seed, "frames": len(g["frames"]),
        "marker": turn0_marker(g["frames"], seat),
        "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw),
    }


def main():
    agent, raw_dir, out_path, saved = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4:]
    os.makedirs(raw_dir, exist_ok=True)
    stamp = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    rows, live = battles_of(agent, saved, raw_dir, time.strftime("%Y%m%d-%H%M%S"))
    games, failures, pending = [], [], 0
    for gid in sorted(rows):
        path = os.path.join(raw_dir, "%d.json" % gid)
        cached = os.path.exists(path)
        if not cached and not rows[gid].get("done"):
            pending += 1
            continue
        try:
            if cached:
                with open(path, "rb") as f:
                    rec = describe(f.read(), gid, agent)
            else:
                raw = call_raw("gameResult/findByGameId", [gid, None])
                rec = describe(raw, gid, agent)
                with open(path + ".part", "wb") as f:
                    f.write(raw)
                os.replace(path + ".part", path)
        except Exception as e:  # one bad game must not stop the run
            failures.append({"gameId": gid, "error": repr(e)})
            print("FAIL %d: %r" % (gid, e))
            continue
        games.append(rec)
        print("%d seat %d vs %-20s ranks %s %s" % (gid, rec["seat"], rec["opponent"]["pseudo"], rec["ranks"],
                                                    "cached" if cached else "fetched"))
    stray = sorted(int(os.path.basename(p)[:-5]) for p in glob.glob(os.path.join(raw_dir, "*.json"))
                   if os.path.basename(p)[:-5].isdigit() and int(os.path.basename(p)[:-5]) not in rows)
    wins = sum(1 for g in games if g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]])
    ties = sum(1 for g in games if g["scores"][0] == g["scores"][1])
    markers = {}
    for g in games:
        markers[g["marker"] or "none"] = markers.get(g["marker"] or "none", 0) + 1
    out = {
        "fetched": stamp, "agentId": agent,
        "services": ["gamesPlayersRanking/findLastBattlesByAgentId [agentId, null]", "gameResult/findByGameId [gameId, null]"],
        "raw": raw_dir.replace("\\", "/") + "/<gameId>.json (gitignored), unchanged response bytes; battle lists battles-<time>.json",
        "battleRows": {"live": live, "merged": len(rows), "savedLists": [p.replace("\\", "/") for p in saved],
                       "notDone": pending},
        "summary": {"games": len(games), "wins": wins, "scoreTies": ties, "markers": dict(sorted(markers.items())),
                    "failures": len(failures), "filesNotInLists": stray},
        "games": games, "failures": failures,
    }
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("games %d wins %d score ties %d markers %s failures %d not done %d (live list %d, merged %d)"
          % (len(games), wins, ties, dict(sorted(markers.items())), len(failures), pending, live, len(rows)))


if __name__ == "__main__":
    main()
