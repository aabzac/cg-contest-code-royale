"""Downloads the finished games of an arena agent unchanged and writes a manifest (public CodinGame services).

Usage: python -I tools/arena_games.py <agentId> [--manifest path] [--saved-list battles.json ...]
  [--registry docs/data/arena-uploads.json] [--offline]
  The registry chooses replays/arena/<label>/<league>/upload-NN/games and the default manifest.
  Battle lists go to the upload's snapshots directory. Saved lists there, existing game files and
  the previous manifest are discovered automatically, so replacing an agent cannot erase its games.
  Use --saved-list repeatedly for extra lists. --offline rebuilds the manifest from saved responses
  without a network call (a missing finished replay is a failure). A game counts as finished when
  any list says so or its file is already stored. Stored files are not fetched again, so a run resumes.
  A response that is not a complete two-player game is a failure
  and is not stored; one bad game does not stop the run. Calls are paced one second apart, and 429,
  5xx and network errors are retried after 5, 20 and 60 s.

The manifest lists per game: the agent's seat (gameResult agents[].index), opponent, ranks, scores,
seed, frame count, sha256 and size, the build marker of the agent's turn-0 TRAIN line (CGRunner.marker,
null without one), and per failure the reason. Wins follow CG's ranks, which also order games with
equal scores (scoreTies counts those). Refetch: POST gameResult/findByGameId [gameId, null] and
compare sha256.
"""
import argparse
import hashlib
import http.client
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request

# -I intentionally removes the script directory from sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from arena_paths import DEFAULT_REGISTRY, decode_battles, unknown_dir, upload_dir, upload_for, write_raw

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


def battles_of(agent, saved, raw_dir, tag, offline=False):
    """gameId -> battle row, from every saved list and the live one; a finished row replaces an unfinished one."""
    rows = {}

    def add(b):
        old = rows.get(b["gameId"])
        if old is None or (b.get("done") and not old.get("done")):
            rows[b["gameId"]] = b

    for path in saved:
        for b in decode_battles(Path(path).read_bytes(), agent):
            add(b)
    if offline:
        return rows, None
    raw = call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [agent, None])
    try:
        live = decode_battles(raw, agent)
    except ValueError:
        write_raw(Path(raw_dir) / ("rejected-battles-%s.json" % tag), raw)
        raise
    write_raw(Path(raw_dir) / ("battles-%s.json" % tag), raw)
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
    if sorted(a["index"] for a in agents) != [0, 1] or len({a["agentId"] for a in agents}) != 2:
        raise ValueError("invalid player indices or duplicate agent identities")
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


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("agent", type=int)
    parser.add_argument("--registry", default=DEFAULT_REGISTRY)
    parser.add_argument("--manifest")
    parser.add_argument("--saved-list", action="append", default=[])
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args(argv)
    agent = args.agent
    try:
        upload = upload_for(agent, args.registry)
        raw_dir = upload_dir(upload) / "games"
        snapshots = upload_dir(upload) / "snapshots"
        out_path = Path(args.manifest or upload["games"])
        previous_games = set()
        # An alternate report destination must not hide the registry's previously captured games.
        for manifest_path in {Path(upload["games"]), out_path}:
            if not manifest_path.exists():
                continue
            previous = json.loads(manifest_path.read_text(encoding="utf-8"))
            if previous.get("agentId") != agent:
                raise ValueError("manifest belongs to another agent: %s" % manifest_path)
            for record in previous.get("games", []) + previous.get("failures", []):
                gid = record.get("gameId")
                if type(gid) is not int or gid < 1:
                    raise ValueError("invalid game id in manifest %s: %r" % (manifest_path, gid))
                # A failed retrieval is still a known finished game, including after its lists disappear.
                previous_games.add(gid)
    except (OSError, ValueError, KeyError) as e:
        parser.error(str(e))
    raw_dir.mkdir(parents=True, exist_ok=True)
    snapshots.mkdir(parents=True, exist_ok=True)
    unknown = unknown_dir(agent, upload["league"])
    saved = sorted(set(snapshots.rglob("battles*.json")) |
                   set((unknown / "snapshots").rglob("battles*.json")) |
                   {Path(p) for p in args.saved_list})
    stamp = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    tag = time.strftime("%Y%m%d-%H%M%S") + "-%09d" % (time.time_ns() % 1000000000)
    try:
        rows, live = battles_of(agent, saved, snapshots, tag, args.offline)
    except (OSError, ValueError) as e:
        print("FAIL battle lists: %s" % e, file=sys.stderr)
        return 1
    listed = set(rows)
    cached_ids = {int(p.stem) for p in raw_dir.glob("*.json") if p.stem.isdigit()}
    unknown_ids = {int(p.stem) for p in (unknown / "games").glob("*.json") if p.stem.isdigit()}
    for gid in cached_ids | unknown_ids | previous_games:
        rows[gid] = dict(rows.get(gid, {"gameId": gid}), done=True)
    games, failures, pending = [], [], 0
    for gid in sorted(rows):
        path = raw_dir / ("%d.json" % gid)
        unresolved_path = unknown / "games" / path.name
        cached = path.exists()
        unresolved = unresolved_path.exists()
        if not cached and not unresolved and not rows[gid].get("done"):
            pending += 1
            continue
        try:
            if cached:
                raw = path.read_bytes()
            elif unresolved:
                raw = unresolved_path.read_bytes()
            else:
                if args.offline:
                    raise ValueError("finished replay is missing in offline mode")
                raw = call_raw("gameResult/findByGameId", [gid, None])
            rec = describe(raw, gid, agent)
            expected = upload.get("artifact", {}).get("build")
            if rec["marker"] is not None and rec["marker"] != expected:
                quarantine = unknown_dir(agent, upload["league"]) / "games" / path.name
                write_raw(quarantine, raw)
                if cached:
                    # This single file is now preserved in unknown; do not leave it under a false version.
                    path.unlink()
                raise ValueError("marker %s differs from registered build %s; saved %s" %
                                 (rec["marker"], expected, quarantine.as_posix()))
            if not cached:
                write_raw(path, raw)
            if unresolved:
                if unresolved_path.read_bytes() != raw:
                    raise ValueError("conflicting cached response under unknown: %s" % unresolved_path)
                unresolved_path.unlink()
        except Exception as e:  # one bad game must not stop the run
            failures.append({"gameId": gid, "error": repr(e)})
            print("FAIL %d: %r" % (gid, e))
            continue
        games.append(rec)
        print("%d seat %d vs %-20s ranks %s %s" % (gid, rec["seat"], rec["opponent"]["pseudo"], rec["ranks"],
                                                    "cached" if cached or unresolved else "fetched"))
    stray = sorted(cached_ids - listed)
    wins = sum(1 for g in games if g["ranks"][g["seat"]] < g["ranks"][1 - g["seat"]])
    ties = sum(1 for g in games if g["scores"][0] == g["scores"][1])
    markers = {}
    for g in games:
        markers[g["marker"] or "none"] = markers.get(g["marker"] or "none", 0) + 1
    out = {
        "fetched": stamp, "agentId": agent,
        "offline": args.offline,
        "services": ["gamesPlayersRanking/findLastBattlesByAgentId [agentId, null]", "gameResult/findByGameId [gameId, null]"],
        "raw": raw_dir.as_posix() + "/<gameId>.json (gitignored), unchanged response bytes",
        "rawDirectory": raw_dir.as_posix(),
        "snapshotDirectory": snapshots.as_posix(),
        "battleRows": {"live": live, "merged": len(rows), "savedLists": [p.as_posix() for p in saved],
                       "notDone": pending},
        "summary": {"games": len(games), "wins": wins, "scoreTies": ties, "markers": dict(sorted(markers.items())),
                    "failures": len(failures), "filesNotInLists": stray},
        "games": games, "failures": failures,
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = out_path.with_suffix(out_path.suffix + ".part")
    with open(temporary, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    temporary.replace(out_path)
    print("games %d wins %d score ties %d markers %s failures %d not done %d (live list %s, merged %d)"
          % (len(games), wins, ties, dict(sorted(markers.items())), len(failures), pending, live, len(rows)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
