"""One command for an arena upload (policy P1, docs/arena-uploads.md): save the live entry, find the new
agent after the paste, register it, follow its placement, fetch its games and check which code played them.

Run from the repository root:
  python -B -I tools/arena.py status
      What plays on the arena now (the leaderboard row of aabzac) and which registered upload it is.
      Writes nothing.
  python -B -I tools/arena.py follow <label> [--artifact PATH] [--selected LABEL] [--wait MINUTES]
      Start it, then paste the code in the IDE. Started after the paste, it goes on from step 3.
      1. Saves the live entry: a snapshot and its games (a replacement empties the old agent's battle list).
      2. Waits for the new agent on the leaderboard (30 minutes by default).
      3. Registers it in docs/data/arena-uploads.json as the label's next upload, in the league it plays,
         with the artifact's sha256, build id and config id; the previous entry gets "replaced". The
         artifact is the label's kept copy artifacts/<sha256[:8]>/Player.java when the label was uploaded
         before, else build/submit/Player.java (--artifact names another file); it is kept there.
      4. Follows the placement as P1 says: a snapshot every 3 minutes, every minute from 85 %, and the
         finished games every minute, until percentage 100 with inProgress false. A promotion during the
         placement registers the new agent as the label's next upload in the new league and follows it.
      5. Records the placement, fetches the remaining games, runs cgAttribute against the kept artifact and
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
from arena_paths import DEFAULT_REGISTRY, check_registry, unknown_dir, upload_dir  # noqa: E402

PSEUDO = "aabzac"
WAIT_POLL_S = 20
SNAPSHOT_S, LATE_SNAPSHOT_S, LATE_PERCENT = 180, 60, 85
FETCH_S = 60
FINAL_FETCHES = 10
NOTE_S = 300
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


# ---- the steps

def register(reg_path, label, agent, league, created_ms, log, artifact, previous=None, submission=None, promoted=False):
    """Adds the agent as the label's next upload; artifact is the record identify() made."""
    registry = read_registry(reg_path)
    uploads = registry["uploads"]
    if any(u["agent"] == agent for u in uploads + registry.get("legacyUploads", [])):
        raise Refused("agent %d is registered already" % agent)
    same = [u for u in uploads if u["label"] == label]
    if same and same[0]["artifact"]["config"] != artifact["config"]:
        raise Refused("label %s holds config %s, the artifact has config %s (one label, one configuration): choose a "
                      "new label or --artifact" % (label, same[0]["artifact"]["config"], artifact["config"]))
    number = 1 + max((u["upload"] for u in same), default=0)
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
    for d in sorted((p for p in source.parent.rglob("*") if p.is_dir()), key=lambda p: len(p.parts), reverse=True) + \
            [source.parent, source.parent.parent, source.parent.parent.parent]:
        with contextlib.suppress(OSError):
            d.rmdir()  # only empty directories go


def fetch(entry, log, reg_path):
    """arena_games for the entry: its manifest's summary and the exit code."""
    try:
        code = captured(log, arena_games.main, [str(entry["agent"]), "--registry", str(reg_path)])
    except SystemExit as e:  # argparse's error: the registry or a manifest refused the agent
        code = e.code if isinstance(e.code, int) else 2
    path = Path(entry["games"])
    manifest = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    s, rows = manifest.get("summary") or {}, manifest.get("battleRows") or {}
    info = {"code": code, "games": s.get("games", 0), "wins": s.get("wins", 0), "failures": s.get("failures", 0),
            "notDone": rows.get("notDone"), "markers": s.get("markers", {}), "ties": s.get("scoreTies", 0)}
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


def wait_for_agent(old, deadline, log, clock, sleep):
    """The leaderboard row once it shows an agent outside old."""
    noted = clock()
    while True:
        row = leaderboard_row()
        if row is not None and row.get("agentId") not in old:
            log.say("new agent %d on the leaderboard: %s, placement %s %%" % (row["agentId"], row_league(row), row.get("percentage")))
            return row
        if clock() >= deadline:
            raise Stopped("no new agent on the leaderboard in time; after the paste the same command goes on")
        if clock() - noted >= NOTE_S:
            log.say("still waiting for the paste")
            noted = clock()
        sleep(WAIT_POLL_S)


def watch(reg_path, entry, log, clock, sleep):
    """Follows the placement (P1's cadence) and fetches games every minute; returns the entry with its placement."""
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
            fetch(entry, log, reg_path)
            next_fetch = now + FETCH_S
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
    entry = next((u for u in registry["uploads"] if u["agent"] == row["agentId"]), None)
    if entry is not None and entry.get("promotedTo"):
        raise Stopped("the leaderboard still shows %s, which ended in a promotion" % name(entry))
    if entry is not None and complete(entry):
        if entry["label"] == label and not entry.get("attribution"):
            log.to(entry)
            log.say("resuming %s after its placement" % name(entry))
            return finish(reg_path, entry, log, run_gradle, selected, sleep)
        if live is None or entry["agent"] != live["agent"]:
            raise Stopped("the leaderboard shows %s, which the registry does not list as live" % name(entry))
        log.to(live)
        log.say("live entry: %s, placement %s; saving it before the paste" % (name(live), live["placement"]["score"]))
        captured(log, arena_snapshot.take, live["agent"], PSEUDO, live["league"], check_registry(read_registry(reg_path)))
        fetch(live, log, reg_path)
        log.say("paste the code in the IDE now; waiting up to %d minutes for the new agent" % wait)
        row = wait_for_agent({live["agent"]}, clock() + 60 * wait, log, clock, sleep)
        entry = next((u for u in read_registry(reg_path)["uploads"] if u["agent"] == row["agentId"]), None)
    if entry is None:
        league = row_league(row)
        if live is not None:
            if log.path is None:
                log.to(live)
            log.say("the leaderboard shows the new agent %d in %s; saving what is left of %s" % (row["agentId"], league, name(live)))
            fetch(live, log, reg_path)
        out, _, _ = captured(log, arena_snapshot.take, row["agentId"], PSEUDO, league, check_registry(read_registry(reg_path)))
        uploads = read_registry(reg_path)["uploads"]
        record, data = identify(artifact_for(label, uploads, artifact), uploads, run_gradle)
        record["source"] += "; kept copy " + keep(data, record["sha256"]).as_posix()
        entry = register(reg_path, label, row["agentId"], league, row["creationTime"], log, record, previous=live,
                         submission=own_submission(out))
    elif not complete(entry):
        if entry["label"] != label:
            raise Refused("agent %d is registered as %s, not %s" % (entry["agent"], name(entry), label))
        log.to(entry)
        log.say("resuming %s: its placement is not recorded yet" % name(entry))
    entry = watch(reg_path, entry, log, clock, sleep)
    return finish(reg_path, entry, log, run_gradle, selected, sleep)


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
