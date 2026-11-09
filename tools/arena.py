"""One command for an arena upload (policy P1, docs/arena-uploads.md): save the live entry, find the new
agent after the paste, register it, follow its placement, fetch its games and check which code played them.

Run from the repository root:
  python -B -I tools/arena.py status
      What plays on the arena now (the leaderboard row of aabzac) and which registered upload it is.
      Writes nothing.
  python -B -I tools/arena.py follow <label> [--artifact PATH] [--selected LABEL] [--wait MINUTES]
      Start it, wait for the line naming the file to paste, then paste that file in the IDE once. Started
      after the paste, it goes on from step 4.
      1. Chooses the artifact and checks it: the label's kept copy artifacts/<sha256[:8]>/Player.java when
         the label was uploaded before, else build/submit/Player.java (--artifact names another file). Its
         sha256, build id and config id; another configuration under the label is refused here. A default
         older than the newest kept copy gets a warning: a candidate built in another checkout leaves
         this file at the previous version.
      2. Saves the live entry: a snapshot and its games (a replacement empties the old agent's battle list).
      3. Names the file to paste and waits for the new agent on the leaderboard (30 minutes by default). CG
         may list it minutes late: an empty battle list of the old agent shows that the paste has arrived,
         and the command says so instead of asking for another paste.
      4. Checks the new agent's first finished game: it must print the artifact's build marker on turn 0.
         When it prints the build of a kept copy in artifacts/ that no other label owns, that copy is the
         artifact registered, with a warning (the expected file was a stale default). Any other build
         stops the command with nothing registered (the replay stays under unknown/).
      5. Registers the agent in docs/data/arena-uploads.json as the label's next upload, in the league it
         plays; the previous entry gets "replaced"; the artifact is kept in artifacts/.
      6. Follows the placement as P1 says: a snapshot every 3 minutes, every minute from 85 %, and the
         finished games a minute after the last fetch ended, until percentage 100 with inProgress false. A
         promotion during the placement registers the new agent as the label's next upload in the new
         league and follows it; games with another build's marker stop it.
      7. Records the placement, fetches the remaining games, runs cgAttribute against the kept artifact and
         records its result; prints the label's arena comparison, with --selected the P1 verdict.
      Interrupted (Ctrl+C) or stopped, the same command resumes with the registered agent.
  python -B -I tools/arena.py fetch [--agent ID] [--no-attribute]
      The games of a registered upload (the live entry by default), then cgAttribute again.

Files of an upload: replays/arena/<label>/<league>/upload-NN/ holds games/ (unchanged CG replays),
snapshots/ (raw leaderboard and battle-list responses) and checks/ (arena.log of this tool with the output
of the tools it runs, cgAttribute.log, arena-compare.json); docs/data/arena-<agent>-placement-<date>.json
and docs/data/arena-<agent>-games-<date>.json are kept in Git. The records for people (docs/BOTS.md,
STATUS, SUMMARY, DEVLOG) are not written here. Exit codes: 0 done, 1 a fetch or a check failed (the log
says which), 2 refused (usage, registry, artifact), 3 stopped (no agent to follow), 130 interrupted.
"""
import argparse
import contextlib
import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

# -I removes the script directory from sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import arena_compare  # noqa: E402
import arena_games  # noqa: E402
import arena_snapshot  # noqa: E402
from arena_paths import DEFAULT_REGISTRY, check_registry, decode_battles, unknown_dir, upload_dir, write_raw  # noqa: E402

PSEUDO = "aabzac"
WAIT_POLL_S = 20
BATTLES_CHECK_S = 60  # while waiting: how often the old agent's battle list is read
SNAPSHOT_S, LATE_SNAPSHOT_S, LATE_PERCENT = 180, 60, 85
FETCH_S = 60
FINAL_FETCHES = 10
NOTE_S = 300
MARKER_GAMES, MARKER_WAIT_S = 3, 600  # the build check: games read at most, time for a first finished game
FOREIGN = "differs from registered build"  # arena_games' failure for a game with another build's marker
NAMES = arena_snapshot.NAMES
ATTRIBUTION = re.compile(r"attribution of (\d+) to .*?: games=(\d+) same=(\d+) differ=(\d+) partial=(\d+) "
                         r"error=(\d+) comparedTurns=(\d+) markers=\{([^}]*)\}")


class Refused(Exception):
    """A request the tool does not carry out (exit code 2)."""


class Stopped(Exception):
    """Nothing the tool can follow (exit code 3)."""


class Log:
    """Prints progress and appends it, with the captured output of the tools it runs, to checks/arena.log.

    Lines written before the upload is known wait for the first to(); to() can move on to another upload.
    """

    def __init__(self):
        self.path, self.pending = None, []

    def to(self, entry):
        self.path = upload_dir(entry) / "checks" / "arena.log"
        self.path.parent.mkdir(parents=True, exist_ok=True)
        lines, self.pending = self.pending, []
        self._write(lines)

    def _write(self, lines):
        if self.path is None:
            self.pending.extend(lines)
            return
        with open(self.path, "a", encoding="utf-8", newline="\n") as f:
            f.writelines(line + "\n" for line in lines)

    def say(self, text):
        print("%s %s" % (time.strftime("%H:%M:%S"), text), flush=True)
        self._write(["%s %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), text)])

    def detail(self, text):
        self._write(["    " + line for line in text.splitlines() if line.strip()])


def captured(log, fn, *args):
    """fn(*args) with its printed output in the log only."""
    buf = io.StringIO()
    try:
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            return fn(*args)
    finally:
        log.detail(buf.getvalue())


# ---- the registry

def read_registry(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_registry(path, registry):
    """Checks the identities, then writes the registry as before: indent 1, UTF-8, its line endings kept."""
    check_registry(registry)
    path = Path(path)
    text = json.dumps(registry, indent=1, ensure_ascii=False) + "\n"
    if b"\r\n" in path.read_bytes():
        text = text.replace("\n", "\r\n")
    temporary = path.with_name(path.name + ".part")
    temporary.write_bytes(text.encode("utf-8"))
    temporary.replace(path)


def update_entry(path, agent, **fields):
    """Re-reads the registry, sets the fields of the agent's entry, writes it and returns the entry."""
    registry = read_registry(path)
    entry = next(u for u in registry["uploads"] if u["agent"] == agent)
    entry.update(fields)
    write_registry(path, registry)
    return entry


def created(u):
    try:
        d = datetime.datetime.fromisoformat(u["created"])
    except (KeyError, TypeError, ValueError):
        return datetime.datetime.min.replace(tzinfo=datetime.timezone.utc)
    return d if d.tzinfo else d.replace(tzinfo=datetime.timezone.utc)


def live_entry(uploads):
    """The latest upload nothing replaced: what the registry says plays on the arena."""
    alive = [u for u in uploads if not u.get("replaced") and not u.get("promotedTo")]
    return max(alive, key=created, default=None)


def complete(u):
    return isinstance((u.get("placement") or {}).get("score"), (int, float))


def name(u):
    return "%s upload %d (agent %d, %s)" % (u["label"], u["upload"], u["agent"], u["league"])


def local_iso(ms):
    return datetime.datetime.fromtimestamp(ms / 1000).astimezone().isoformat(timespec="seconds")


def stamp_iso(stamp):
    """arena_snapshot's "2026-11-04T00:31:47+0200" as "2026-11-04T00:31:47+02:00"."""
    try:
        return datetime.datetime.strptime(stamp, "%Y-%m-%dT%H:%M:%S%z").isoformat()
    except (TypeError, ValueError):
        return stamp


# ---- CodinGame and Gradle

def leaderboard_row(pseudo=PSEUDO):
    """The active leaderboard row of pseudo (no file is written), or None."""
    raw = arena_games.call_raw("Leaderboards/getFilteredPuzzleLeaderboard",
                               ["code-royale", None, "global", {"active": True, "column": "KEYWORD", "filter": pseudo}])
    return next((u for u in json.loads(raw)["users"] if u.get("pseudo") == pseudo), None)


def row_league(row):
    return NAMES[row["league"]["divisionIndex"]]


def own_submission(summary):
    return next((g["submissionId"] for g in summary["battles"]["games"] if g.get("submissionId")), None)


def gradle(args):
    """The project's Gradle wrapper, run from the repository root: (exit code, stdout and stderr)."""
    wrapper = Path.cwd() / ("gradlew.bat" if os.name == "nt" else "gradlew")
    p = subprocess.run([str(wrapper), *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return p.returncode, p.stdout + p.stderr


# ---- the artifact

def kept_copy(sha):
    return Path("artifacts") / sha[:8] / "Player.java"


def artifact_for(label, uploads, given):
    if given:
        return Path(given)
    mine = [u for u in uploads if u["label"] == label]
    return kept_copy(mine[-1]["artifact"]["sha256"]) if mine else Path("build/submit/Player.java")


def identify(path, uploads, run_gradle):
    """(artifact record, bytes) of the file: sha256, build id, config id; bytes in the registry reuse its record."""
    if not path.is_file():
        raise Refused("no artifact at %s; --artifact names the pasted file" % path.as_posix())
    data = path.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    known = next((u for u in uploads if (u.get("artifact") or {}).get("sha256") == sha), None)
    if known:
        a = known["artifact"]
        return {"sha256": sha, "build": a.get("build"), "config": a["config"],
                "source": "the same bytes as %s upload %d" % (known["label"], known["upload"])}, data
    code, output = run_gradle(["-q", "artifactInfo", "-Pfile=" + path.resolve().as_posix()])
    line = next((l for l in reversed(output.splitlines()) if l.startswith("{")), None)
    if code != 0 or line is None:
        raise Refused("artifactInfo failed for %s:\n%s" % (path.as_posix(), output.strip()))
    info = json.loads(line)
    if info["sha256"] != sha:
        raise Refused("artifactInfo read other bytes than %s" % path.as_posix())
    return {"sha256": sha, "build": info["build"], "config": info["config"],
            "source": "%s, %d chars, PARAMS \"%s\"" % (path.as_posix(), info["chars"], info["params"])}, data


def keep(data, sha):
    """The kept copy artifacts/<sha256[:8]>/Player.java, written once; other bytes there are refused."""
    kept = kept_copy(sha)
    if kept.exists():
        if hashlib.sha256(kept.read_bytes()).hexdigest() != sha:
            raise Refused("%s holds other bytes than sha256 %s" % (kept.as_posix(), sha))
        return kept
    kept.parent.mkdir(parents=True, exist_ok=True)
    temporary = kept.with_name(kept.name + ".part")
    temporary.write_bytes(data)
    temporary.replace(kept)
    return kept


def same_configuration(label, uploads, record):
    """Refuses an artifact whose config id differs from the label's: one label, one configuration."""
    same = [u for u in uploads if u["label"] == label]
    if same and same[0]["artifact"]["config"] != record["config"]:
        raise Refused("label %s holds config %s, the artifact has config %s (one label, one configuration): choose a "
                      "new label or --artifact" % (label, same[0]["artifact"]["config"], record["config"]))


def other_bytes(path, record, uploads):
    """A warning when build/submit/Player.java, the file usually pasted, is not the artifact to paste."""
    submit = Path("build/submit/Player.java")
    if not submit.is_file() or path.resolve() == submit.resolve():
        return ""
    sha = hashlib.sha256(submit.read_bytes()).hexdigest()
    if sha == record["sha256"]:
        return ""
    known = next((u for u in uploads if (u.get("artifact") or {}).get("sha256") == sha), None)
    return "; not build/submit/Player.java, which holds other bytes (%s%s)" % (
        sha[:8], ", %s's artifact" % known["label"] if known else "")


def kept_copies():
    """The kept copies artifacts/*/Player.java, the newest first."""
    return sorted(Path("artifacts").glob("*/Player.java"), key=lambda p: p.stat().st_mtime, reverse=True)


def file_time(path):
    mtime = path.stat().st_mtime
    try:
        return datetime.datetime.fromtimestamp(mtime).astimezone().isoformat(timespec="minutes")
    except (OSError, OverflowError, ValueError):  # a time the platform cannot render (Windows: before 1970)
        return "%.0f s" % mtime


def stale_default(path, log):
    """A warning when the default build/submit/Player.java is older than the newest kept copy: a build in another
    checkout (a worktree) leaves this checkout's file at the previous version (v13 upload 1, 2026-11-09)."""
    kept = kept_copies()
    if not kept or not path.is_file():
        return
    newest = kept[0]
    if path.stat().st_mtime < newest.stat().st_mtime:
        log.say("WARNING: %s (%s) is older than the newest kept copy %s (%s); if the file to paste is another build, stop "
                "now and pass --artifact" % (path.as_posix(), file_time(path), newest.as_posix(), file_time(newest)))


def kept_with_build(build, label, uploads, run_gradle):
    """(record, bytes) of the kept copy that prints this build marker and belongs to no other label, or None.
    Known bytes take their record from the registry; new bytes are identified by Gradle, one call per unknown copy."""
    for path in kept_copies():
        sha = hashlib.sha256(path.read_bytes()).hexdigest()
        known = next((u for u in uploads if (u.get("artifact") or {}).get("sha256") == sha), None)
        if known is not None:
            if known["artifact"].get("build") == build and known["label"] == label:
                return identify(path, uploads, run_gradle)
            continue
        record, data = identify(path, uploads, run_gradle)
        if record["build"] == build:
            return record, data
    return None


# ---- the steps

def register(reg_path, label, agent, league, created_ms, log, artifact, previous=None, submission=None, promoted=False):
    """Adds the agent as the label's next upload; artifact is the record identify() made."""
    registry = read_registry(reg_path)
    uploads = registry["uploads"]
    if any(u["agent"] == agent for u in uploads + registry.get("legacyUploads", [])):
        raise Refused("agent %d is registered already" % agent)
    same_configuration(label, uploads, artifact)
    number = 1 + max((u["upload"] for u in uploads if u["label"] == label), default=0)
    day = time.strftime("%Y-%m-%d")
    if promoted:
        by = "CG's promotion of %s upload %d during its placement" % (previous["label"], previous["upload"])
    elif previous is not None and submission is not None and submission == previous.get("submission"):
        by = "CG's promotion of %s upload %d (the same submission %d)" % (previous["label"], previous["upload"], submission)
    else:
        by = "the user in the IDE (registered by tools/arena.py follow)"
    entry = {"label": label, "upload": number, "agent": agent, "submission": submission, "league": league,
             "artifact": artifact, "created": local_iso(created_ms), "placement": {"score": None},
             "snapshots": "docs/data/arena-%d-placement-%s.json" % (agent, day),
             "games": "docs/data/arena-%d-games-%s.json" % (agent, day), "submittedBy": by}
    if previous is not None:
        old = next(u for u in uploads if u["agent"] == previous["agent"])
        if promoted:
            old["promotedTo"] = league
        if not old.get("replaced"):
            old["replaced"] = "%s by %s upload %d%s" % (entry["created"], label, number,
                                                       "" if league == old["league"] else " in " + league.capitalize())
    uploads.append(entry)
    write_registry(reg_path, registry)
    adopt_unknown(entry)
    log.to(entry)
    log.say("registered agent %d as %s upload %d in %s: artifact %s, build %s, config %s; %s"
            % (agent, label, number, league, artifact["sha256"][:8], artifact["build"], artifact["config"], by))
    return entry


def adopt_unknown(entry):
    """Moves snapshots saved under replays/arena/unknown before the registration to the upload (bytes unchanged)."""
    source = unknown_dir(entry["agent"], entry["league"]) / "snapshots"
    if not source.is_dir():
        return
    target = upload_dir(entry) / "snapshots"
    for f in sorted(p for p in source.rglob("*") if p.is_file()):
        dest = target / f.relative_to(source)
        if dest.exists():
            if dest.read_bytes() != f.read_bytes():
                raise ValueError("different files at %s and %s" % (f.as_posix(), dest.as_posix()))
            f.unlink()
        else:
            dest.parent.mkdir(parents=True, exist_ok=True)
            f.replace(dest)
    prune_unknown(entry["agent"], entry["league"])


def prune_unknown(agent, league):
    """Removes the empty directories of the agent under replays/arena/unknown (games there go through arena_games)."""
    top = unknown_dir(agent, league)
    if not top.is_dir():
        return
    for d in sorted((p for p in top.rglob("*") if p.is_dir()), key=lambda p: len(p.parts), reverse=True) + \
            [top, top.parent, top.parent.parent]:
        with contextlib.suppress(OSError):
            d.rmdir()  # only empty directories go


def fetch(entry, log, reg_path):
    """arena_games for the entry: its manifest's summary, the games with another build's marker and the exit code."""
    try:
        code = captured(log, arena_games.main, [str(entry["agent"]), "--registry", str(reg_path)])
    except SystemExit as e:  # argparse's error: the registry or a manifest refused the agent
        code = e.code if isinstance(e.code, int) else 2
    prune_unknown(entry["agent"], entry["league"])
    path = Path(entry["games"])
    manifest = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    s, rows = manifest.get("summary") or {}, manifest.get("battleRows") or {}
    info = {"code": code, "games": s.get("games", 0), "wins": s.get("wins", 0), "failures": s.get("failures", 0),
            "notDone": rows.get("notDone"), "markers": s.get("markers", {}), "ties": s.get("scoreTies", 0),
            "foreign": [f["error"] for f in manifest.get("failures", []) if FOREIGN in f.get("error", "")]}
    log.say("games of %s: %d fetched, %d won, markers %s, %d failures, %s unfinished%s" % (
        name(entry), info["games"], info["wins"], info["markers"], info["failures"], info["notDone"],
        "" if code == 0 else "; FAIL exit %s, see checks/arena.log" % code))
    return info


def fetch_all(entry, log, reg_path, sleep):
    """Fetches a minute apart until no finished game is missing (at most FINAL_FETCHES times)."""
    for attempt in range(FINAL_FETCHES):
        info = fetch(entry, log, reg_path)
        if info["notDone"] == 0:
            return info
        if attempt + 1 < FINAL_FETCHES:
            sleep(FETCH_S)
    log.say("%s battles still unfinished after %d fetches; `tools/arena.py fetch` gets them later"
            % (info["notDone"], FINAL_FETCHES))
    return info


def wait_for_agent(old, deadline, log, clock, sleep, pasted=False, wait=30):
    """The leaderboard row once it shows an agent other than old.

    CG can list a new agent minutes after the paste; the old agent's battle list empties at once, so an empty
    list means the paste has arrived. Then the wait gets its full length again and nobody is asked to paste.
    """
    noted = checked = clock()
    while True:
        row = leaderboard_row()
        if row is not None and row.get("agentId") != old:
            log.say("new agent %d on the leaderboard: %s, placement %s %%" % (row["agentId"], row_league(row), row.get("percentage")))
            return row
        if not pasted and clock() - checked >= BATTLES_CHECK_S:
            checked = clock()
            if not decode_battles(arena_games.call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [old, None]), old):
                pasted, deadline = True, max(deadline, clock() + 60 * wait)
                log.say("the battle list of agent %d is empty: CG has the paste; waiting for it to list the new agent "
                        "(do not paste again)" % old)
        if clock() >= deadline:
            raise Stopped("no new agent on the leaderboard in time; after the paste the same command goes on")
        if clock() - noted >= NOTE_S:
            log.say("still waiting for CG to list the new agent" if pasted else "still waiting for the paste")
            noted = clock()
        sleep(WAIT_POLL_S)


def check_build(agent, league, expected, record, data, label, uploads, log, clock, sleep, run_gradle):
    """The (record, bytes) to register: the expected artifact when the agent's first finished games print its build
    marker; the kept copy in artifacts/ that prints the agent's build when the expected file was another one (a
    stale default: the candidate built in another checkout), with a warning; otherwise the command stops.

    Another label's artifact is never adopted: that marker means CG got another version's file. The replays stay under
    replays/arena/unknown/<league>/agent-<id>/games: arena_games takes them from there after the registration, and
    after a stop they are the evidence. At most MARKER_GAMES games are read (a game without a turn-0 answer has no
    marker); the first finished game is waited for.
    """
    if record["build"] is None:
        log.say("the artifact prints no build marker: the games cannot show its build")
        return record, data
    deadline, seen = clock() + MARKER_WAIT_S, []
    while True:
        rows = decode_battles(arena_games.call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [agent, None]), agent)
        for gid in sorted(r["gameId"] for r in rows if r.get("done") and r["gameId"] not in seen)[:MARKER_GAMES - len(seen)]:
            path = unknown_dir(agent, league) / "games" / ("%d.json" % gid)
            raw = path.read_bytes() if path.exists() else arena_games.call_raw("gameResult/findByGameId", [gid, None])
            try:
                marker = arena_games.describe(raw, gid, agent)["marker"]
            except ValueError:
                continue  # not a complete game yet: read again on the next round
            write_raw(path, raw)
            seen.append(gid)
            if marker == record["build"]:
                log.say("the new agent plays build %s (game %d), the artifact's" % (marker, gid))
                return record, data
            if marker is not None:
                other = next((u for u in uploads if (u.get("artifact") or {}).get("build") == marker), None)
                found = None if other is not None and other["label"] != label else kept_with_build(marker, label, uploads, run_gradle)
                if found is not None:
                    log.say("WARNING: the expected file %s prints build %s, but agent %d plays build %s (game %d), the build of the "
                            "kept copy %s: that copy is registered (the expected file was not the one pasted; a stale default?)"
                            % (expected.as_posix(), record["build"], agent, marker, gid, kept_copy(found[0]["sha256"]).as_posix()))
                    return found
                raise Stopped("agent %d plays build %s (game %d)%s, not build %s of the artifact %s: CG got another file. "
                              "Nothing is registered; the replay stays in %s" % (
                                  agent, marker, gid, ", the artifact of %s upload %d" % (other["label"], other["upload"])
                                  if other else "", record["build"], record["sha256"][:8], path.parent.as_posix()))
        if len(seen) >= MARKER_GAMES:
            raise Stopped("agent %d printed no build marker in its first %d games (the artifact's build is %s); nothing is "
                          "registered" % (agent, MARKER_GAMES, record["build"]))
        if clock() >= deadline:
            raise Stopped("agent %d finished no game in %d minutes to show its build; the same command goes on"
                          % (agent, MARKER_WAIT_S // 60))
        sleep(WAIT_POLL_S)


def watch(reg_path, entry, log, clock, sleep):
    """Follows the placement (P1's cadence) and fetches games a minute after the last fetch ended.

    Returns the entry with its placement; games with another build's marker stop it.
    """
    next_snapshot = next_fetch = clock()
    first = last = None
    while True:
        now = clock()
        if now >= next_snapshot:
            uploads = check_registry(read_registry(reg_path))
            out, raw_dir, tag = captured(log, arena_snapshot.take, entry["agent"], PSEUDO, entry["league"], uploads)
            e = out["entry"] or {}
            if out["agentId"] != entry["agent"]:
                league = NAMES.get(e.get("league"))
                if league is None or league == entry["league"]:
                    raise Stopped("the leaderboard shows agent %d instead of %s: another paste replaced it; follow the new "
                                  "agent with its label" % (out["agentId"], name(entry)))
                log.say("promoted to %s during the placement: agent %d" % (league, out["agentId"]))
                if last is not None:  # P1: no placement score in the old league; the last partial one is context
                    update_entry(reg_path, entry["agent"], placement={
                        "score": None, "lastPartial": "%s %% at %s, score %s, rank %s" % (last[1], stamp_iso(last[0]), last[2], last[3])})
                artifact = dict(entry["artifact"], source="the same artifact as %s upload %d" % (entry["label"], entry["upload"]))
                entry = register(reg_path, entry["label"], out["agentId"], league, e["creationTime"], log, artifact,
                                 previous=entry, submission=own_submission(out), promoted=True)
                next_snapshot = next_fetch = clock()
                first = last = None
                continue
            submission = own_submission(out)
            if entry.get("submission") is None and submission is not None:
                entry = update_entry(reg_path, entry["agent"], submission=submission)
            captured(log, arena_snapshot.write_summary, out, entry["snapshots"], raw_dir, tag)
            pct, b = e.get("percentage"), out["battles"]
            first = first or (out["fetched"], pct)
            last = (out["fetched"], pct, e.get("score"), e.get("localRank"))
            log.say("placement %s %%%s: score %s, rank %s of %s; battles %d, finished %d, won %d" % (
                pct, " (in progress)" if e.get("inProgress") else "", e.get("score"), e.get("localRank"),
                e.get("leagueAgents"), b["count"], b["done"], b["wins"]))
            if pct == 100 and e.get("inProgress") is False:
                placement = {
                    "score": e.get("score"), "rank": e.get("localRank"), "agents": e.get("leagueAgents"),
                    "games": b["count"], "firstCompleteSnapshot": stamp_iso(out["fetched"]),
                    "watched": first[1] is not None and first[1] < 100,
                    "lastUpdate": (local_iso(e["updateTime"]) + " (leaderboard updateTime)") if e.get("updateTime") else None,
                    "globalRank": e.get("rank"), "eligibleForPromotion": e.get("eligibleForPromotion"),
                    "note": "followed by tools/arena.py from %s at %s %%; every snapshot in checks/arena.log"
                            % (stamp_iso(first[0]), first[1])}
                entry = update_entry(reg_path, entry["agent"], placement=placement)
                log.say("placement complete: score %s, rank %s of %s (global %s), eligibleForPromotion %s" % (
                    e.get("score"), e.get("localRank"), e.get("leagueAgents"), e.get("rank"), e.get("eligibleForPromotion")))
                return entry
            next_snapshot = now + (LATE_SNAPSHOT_S if (pct or 0) >= LATE_PERCENT else SNAPSHOT_S)
        if now >= next_fetch:
            info = fetch(entry, log, reg_path)
            if info["foreign"]:
                raise Stopped("games of %s carry another build's marker, not the registered %s: %s; they wait under "
                              "replays/arena/unknown" % (name(entry), entry["artifact"]["build"], info["foreign"][0]))
            next_fetch = clock() + FETCH_S  # from the end: a slow fetch is not followed at once by another
        sleep(max(1.0, min(next_snapshot, next_fetch) - clock()))


def attribute(reg_path, entry, log, run_gradle):
    """cgAttribute of the upload's games against its kept artifact; the result goes to the registry."""
    kept, games = kept_copy(entry["artifact"]["sha256"]), upload_dir(entry) / "games"
    log.say("cgAttribute: %d games against %s" % (len(list(games.glob("*.json"))), kept.as_posix()))
    code, output = run_gradle(["cgAttribute", "-Preplays=" + games.as_posix(), "-Pagent=%d" % entry["agent"],
                               "-Pbot=file:" + kept.as_posix()])
    report = upload_dir(entry) / "checks" / "cgAttribute.log"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(output, encoding="utf-8", newline="\n")
    m = ATTRIBUTION.search(output)
    if not m:
        log.say("FAIL cgAttribute printed no summary (exit %d); see %s" % (code, report.as_posix()))
        return None
    n, same, differ, partial, error, turns = (int(g) for g in m.groups()[1:7])
    text = "cgAttribute against the kept copy %s: SAME in %d of %d games, %s turns; markers {%s}" % (
        kept.as_posix(), same, n, format(turns, ","), m.group(8))
    if differ or partial or error:
        text += "; DIFF %d, PARTIAL %d, ERROR %d" % (differ, partial, error)
    update_entry(reg_path, entry["agent"], attribution=text + " (tools/arena.py, %s)" % time.strftime("%Y-%m-%d"))
    log.say(("code: " if same == n else "FAIL code: ") + text)
    return {"games": n, "same": same}


def compare(reg_path, entry, selected, log):
    """The league's arena comparison for the entry's label; with selected, the P1 verdict."""
    try:
        report = arena_compare.compare(arena_compare.load(reg_path), entry["label"], selected)
    except (arena_compare.RegistryError, OSError, ValueError) as e:
        log.say("FAIL arena_compare: %s" % e)
        return None
    league = report["leagues"][entry["league"]]
    path = upload_dir(entry) / "checks" / "arena-compare.json"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(report, f, indent=1)
        f.write("\n")
    fmt = arena_compare.fmt
    me = next(r for r in league["uploads"] if r["agent"] == entry["agent"])
    lab = league["labels"][entry["label"]]
    n = league["noise"]
    log.say("this upload: placement %s, %d games, %s %% won, performance %s +- %s" % (
        fmt(me["placement"]), me["games"], fmt(100 * me["rate"], 1) if me["games"] else "-", fmt(me["performance"]),
        fmt(me["performanceSe"])))
    log.say("%s in %s: placements %s, mean %s; performance %s +- %s over %d games" % (
        entry["label"], entry["league"], " ".join(fmt(x) for x in lab["placementScores"]) or "-", fmt(lab["mean"]),
        fmt(lab["performance"]), fmt(lab["performanceSe"]), lab["games"]))
    log.say("noise: M %s (%d df)%s" % (fmt(n["M"]), n["df"], "; verdicts use M %s from %s" % (
        fmt(n["usedM"]), n["borrowed"]["from"]) if n.get("borrowed") else ""))
    if "verdict" in league:
        log.say("P1 verdict against %s: %s (%s)" % (selected, league["verdict"]["decision"], league["verdict"]["why"]))
    return league


def finish(reg_path, entry, log, run_gradle, selected, sleep):
    """Steps after the placement: the remaining games, cgAttribute, the comparison and the summary."""
    games = fetch_all(entry, log, reg_path, sleep)
    code = attribute(reg_path, entry, log, run_gradle)
    compare(reg_path, entry, selected, log)
    entry = next(u for u in read_registry(reg_path)["uploads"] if u["agent"] == entry["agent"])
    p = entry.get("placement") or {}
    log.say("done: %s: placement %s, rank %s of %s; %d games, %d won; code %s" % (
        name(entry), p.get("score"), p.get("rank"), p.get("agents"), games["games"], games["wins"],
        "SAME %d of %d" % (code["same"], code["games"]) if code else "not checked"))
    log.say("files: %s/{games,snapshots,checks}, %s, %s and the entry in %s; the records in docs/BOTS.md, STATUS, "
            "SUMMARY and DEVLOG are still to write" % (upload_dir(entry).as_posix(), entry["games"], entry["snapshots"], reg_path))
    if p.get("eligibleForPromotion"):
        log.say("eligibleForPromotion: CG may move the agent up; once it plays there, `tools/arena.py follow %s` "
                "registers it" % entry["label"])
    ok = games["code"] == 0 and games["failures"] == 0 and code is not None and code["same"] == code["games"]
    return 0 if ok else 1


def follow(label, log, artifact=None, selected=None, wait=30, reg_path=DEFAULT_REGISTRY, run_gradle=gradle,
           clock=time.time, sleep=time.sleep):
    upload_dir({"label": label, "league": "bronze", "upload": 1})  # the label's spelling, before anything is done
    registry = read_registry(reg_path)
    check_registry(registry)
    live = live_entry(registry["uploads"])
    row = leaderboard_row()
    if row is None:
        raise Stopped("no active leaderboard row for %s" % PSEUDO)
    uploads = registry["uploads"]
    entry = next((u for u in uploads if u["agent"] == row["agentId"]), None)
    if entry is not None and entry.get("promotedTo"):
        raise Stopped("the leaderboard still shows %s, which ended in a promotion" % name(entry))
    if entry is not None and not complete(entry):
        if entry["label"] != label:
            raise Refused("agent %d is registered as %s, not %s" % (entry["agent"], name(entry), label))
        log.to(entry)
        log.say("resuming %s: its placement is not recorded yet" % name(entry))
        return finish(reg_path, watch(reg_path, entry, log, clock, sleep), log, run_gradle, selected, sleep)
    if entry is not None and entry["label"] == label and not entry.get("attribution"):
        log.to(entry)
        log.say("resuming %s after its placement" % name(entry))
        return finish(reg_path, entry, log, run_gradle, selected, sleep)
    if entry is not None and (live is None or entry["agent"] != live["agent"]):
        raise Stopped("the leaderboard shows %s, which the registry does not list as live" % name(entry))
    # A new upload: the file and the label are checked before anything is saved or pasted.
    path = artifact_for(label, uploads, artifact)
    if artifact is None and not any(u["label"] == label for u in uploads):
        stale_default(path, log)
    record, data = identify(path, uploads, run_gradle)
    same_configuration(label, uploads, record)
    saved = False
    if entry is not None:  # the leaderboard still shows the live entry
        log.to(live)
        log.say("live entry: %s, placement %s; saving it before the paste" % (name(live), live["placement"]["score"]))
        out, _, _ = captured(log, arena_snapshot.take, live["agent"], PSEUDO, live["league"], check_registry(read_registry(reg_path)))
        fetch(live, log, reg_path)
        saved = True
        pasted = out["agentId"] != live["agent"] or not out["battles"]["count"]
        if pasted:
            log.say("the battle list of agent %d is empty: CG has a paste already; waiting up to %d minutes for it to list "
                    "the new agent (do not paste again)" % (live["agent"], wait))
        else:
            log.say("paste %s in the IDE now (sha256 %s..., build %s)%s; waiting up to %d minutes for the new agent" % (
                path.as_posix(), record["sha256"][:8], record["build"], other_bytes(path, record, uploads), wait))
        row = wait_for_agent(live["agent"], clock() + 60 * wait, log, clock, sleep, pasted, wait)
        if any(u["agent"] == row["agentId"] for u in read_registry(reg_path)["uploads"]):
            raise Stopped("agent %d was registered meanwhile; run the command again" % row["agentId"])
    league = row_league(row)
    if live is not None and not saved:
        log.to(live)
        log.say("the leaderboard shows the new agent %d in %s; saving what is left of %s" % (row["agentId"], league, name(live)))
        fetch(live, log, reg_path)
    out, _, _ = captured(log, arena_snapshot.take, row["agentId"], PSEUDO, league, check_registry(read_registry(reg_path)))
    record, data = check_build(row["agentId"], league, path, record, data, label, uploads, log, clock, sleep, run_gradle)
    same_configuration(label, uploads, record)
    record["source"] += "; kept copy " + keep(data, record["sha256"]).as_posix()
    entry = register(reg_path, label, row["agentId"], league, row["creationTime"], log, record, previous=live,
                     submission=own_submission(out))
    return finish(reg_path, watch(reg_path, entry, log, clock, sleep), log, run_gradle, selected, sleep)


def status(reg_path=DEFAULT_REGISTRY):
    registry = read_registry(reg_path)
    uploads = registry["uploads"]
    live = live_entry(uploads)
    row = leaderboard_row()
    if row is None:
        print("arena: no active leaderboard row for %s" % PSEUDO)
    else:
        print("arena: agent %d in %s, placement %s %%%s, score %s, rank %s of %s (global %s), eligibleForPromotion %s" % (
            row["agentId"], row_league(row), row.get("percentage"), " (in progress)" if row.get("inProgress") else "",
            row.get("score"), row.get("localRank"), row["league"].get("divisionAgentsCount"), row.get("rank"),
            row.get("eligibleForPromotion")))
        entry = next((u for u in uploads if u["agent"] == row["agentId"]), None)
        if entry is None:
            print("registry: agent %d is not registered; `tools/arena.py follow <label>` registers and follows it" % row["agentId"])
        else:
            games = upload_dir(entry) / "games"
            print("registry: %s, placement %s, %d games saved in %s" % (
                name(entry), (entry.get("placement") or {}).get("score"), len(list(games.glob("*.json"))), games.as_posix()))
    if live is not None and (row is None or row["agentId"] != live["agent"]):
        print("registry's live entry: %s" % name(live))
    return 0


def fetch_command(log, agent=None, check=True, reg_path=DEFAULT_REGISTRY, run_gradle=gradle):
    uploads = read_registry(reg_path)["uploads"]
    entry = next((u for u in uploads if u["agent"] == agent), None) if agent else live_entry(uploads)
    if entry is None:
        raise Refused("agent %s is not registered" % agent if agent else "the registry lists no live entry")
    log.to(entry)
    info = fetch(entry, log, reg_path)
    code = attribute(reg_path, entry, log, run_gradle) if check else None
    ok = info["code"] == 0 and info["failures"] == 0 and (not check or (code is not None and code["same"] == code["games"]))
    return 0 if ok else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--registry", default=DEFAULT_REGISTRY)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("status", help="what plays on the arena now; writes nothing")
    f = sub.add_parser("follow", help="one upload, from the paste to its checked games")
    f.add_argument("label", help="the version uploaded, e.g. v09; the same label again for a re-upload")
    f.add_argument("--artifact", help="the pasted file (default: the label's kept copy, else build/submit/Player.java)")
    f.add_argument("--selected", help="the selected version's label, for the P1 verdict")
    f.add_argument("--wait", type=int, default=30, help="minutes to wait for the new agent (default 30)")
    g = sub.add_parser("fetch", help="the games of a registered upload, then cgAttribute")
    g.add_argument("--agent", type=int, help="default: the live entry")
    g.add_argument("--no-attribute", action="store_true")
    args = parser.parse_args(argv)
    log = Log()
    try:
        if args.command == "status":
            return status(args.registry)
        if args.command == "fetch":
            return fetch_command(log, args.agent, not args.no_attribute, args.registry)
        return follow(args.label, log, args.artifact, args.selected, args.wait, args.registry)
    except Refused as e:
        log.say("REFUSED: %s" % e)
        return 2
    except Stopped as e:
        log.say("STOPPED: %s" % e)
        return 3
    except KeyboardInterrupt:
        log.say("interrupted; the same command resumes")
        return 130
    except (OSError, ValueError, KeyError) as e:
        log.say("FAIL: %r; the same command resumes" % (e,))
        return 1


if __name__ == "__main__":
    sys.exit(main())
