"""Offline checks of tools/arena.py: a simulated arena on a fake clock, a fake Gradle, a temporary repository.

Run: python -B -I -m unittest discover -s tools/tests -p "test_*.py"   (no network, no Gradle)
"""
import contextlib
import hashlib
import io
import itertools
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import arena  # noqa: E402
import arena_games  # noqa: E402
import arena_snapshot  # noqa: E402

REGISTRY = Path("docs/data/arena-uploads.json")
OLD_BUILD, NEW_BUILD = "0000aaaa", "0123abcd"
NEW_ARTIFACT = b"class Player {}\n// the candidate\n"
NEW_SHA = hashlib.sha256(NEW_ARTIFACT).hexdigest()
T0 = 1000.0
CREATED = 1793744082737


def row(agent, league=3, pct=100, busy=False, score=20.0, created=CREATED):
    return {"pseudo": "aabzac", "agentId": agent, "league": {"divisionIndex": league, "divisionAgentsCount": 200},
            "rank": 400, "localRank": 3, "score": score, "percentage": pct, "inProgress": busy,
            "eligibleForPromotion": False, "programmingLanguage": "Java", "updateTime": created + 1000,
            "creationTime": created}


def battle(gid, agent, done=True, submission=1):
    return {"gameId": gid, "done": done, "players": [
        {"playerAgentId": agent, "position": 0, "nickname": "aabzac", "submissionId": submission},
        {"playerAgentId": 9000 + gid, "position": 1, "nickname": "rival", "submissionId": 5}]}


def replay(gid, agent, build):
    train = "TRAIN " + " ".join(str(int(c, 16)) for c in build)
    return json.dumps({"gameId": gid, "agents": [
        {"agentId": agent, "index": 0, "codingamer": {"pseudo": "aabzac"}},
        {"agentId": 9000 + gid, "index": 1, "codingamer": {"pseudo": "rival"}, "score": 19.0}],
        "frames": [{"agentId": -1}, {"agentId": 0, "stdout": "WAIT\n" + train + "\n"}, {"agentId": 1, "stdout": "WAIT\nTRAIN\n"}],
        "ranks": [0, 1], "scores": [90, 10], "refereeInput": "seed=7\n"}).encode()


class Clock:
    def __init__(self):
        self.t = T0

    def __call__(self):
        return self.t

    def sleep(self, seconds):
        self.t += seconds


class Arena:
    """The public services on a timeline: rows[(from, row)], battles[agent](t), replays[gameId]."""

    def __init__(self, clock):
        self.clock, self.rows, self.battles, self.replays = clock, [], {}, {}

    def __call__(self, service, body):
        t = self.clock()
        if service.startswith("Leaderboards/"):
            current = [r for start, r in self.rows if start <= t]
            users = current[-1:]
            league = users[0]["league"]["divisionIndex"] if users else 3
            return json.dumps({"users": users, "leagues": {str(league): {"divisionAgentsCount": 200}}}).encode()
        if service.startswith("gamesPlayersRanking/"):
            return json.dumps(self.battles.get(body[0], lambda _: [])(t)).encode()
        return self.replays[body[0]]


class Gradle:
    def __init__(self, config="cfg2"):
        self.calls, self.config = [], config

    def __call__(self, args):
        self.calls.append(args)
        if "artifactInfo" in args:
            return 0, "> Task :artifactInfo\n" + json.dumps(
                {"sha256": NEW_SHA, "chars": len(NEW_ARTIFACT), "params": "", "build": NEW_BUILD, "config": self.config}) + "\n"
        agent = next(a for a in args if a.startswith("-Pagent="))[8:]
        games = Path(next(a for a in args if a.startswith("-Preplays="))[10:])
        n = len(list(games.glob("*.json")))
        return 0, "attribution of %s to file:x: games=%d same=%d differ=0 partial=0 error=0 comparedTurns=%d markers={%s=%d}\n" % (
            agent, n, n, 100 * n, NEW_BUILD, n)


class FollowTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        old = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, old)
        self.clock = Clock()
        self.arena = Arena(self.clock)
        self.gradle = Gradle()
        live = {"label": "v01", "upload": 1, "agent": 100, "submission": 11, "league": "bronze",
                "artifact": {"sha256": "ab" * 32, "build": OLD_BUILD, "config": "cfg1", "source": "test"},
                "created": "2026-11-01T10:00:00+02:00", "placement": {"score": 20.0},
                "snapshots": "docs/data/arena-100-placement.json", "games": "docs/data/arena-100-games.json"}
        REGISTRY.parent.mkdir(parents=True)
        REGISTRY.write_bytes((json.dumps({"what": "test", "uploads": [live], "legacyUploads": []}, indent=1) + "\n")
                             .replace("\n", "\r\n").encode())
        Path("build/submit").mkdir(parents=True)
        Path("build/submit/Player.java").write_bytes(NEW_ARTIFACT)
        self.arena.battles[100] = lambda t: [battle(11, 100, submission=11)]
        self.arena.replays[11] = replay(11, 100, OLD_BUILD)
        for gid in (21, 22, 31):
            self.arena.replays[gid] = replay(gid, 300 if gid == 31 else 200, NEW_BUILD)
        self.arena.battles[200] = lambda t: [battle(21, 200, submission=22), battle(22, 200, done=t >= T0 + 600, submission=22)]

    def follow(self, label="v02", **kw):
        log = arena.Log()
        with patch.object(arena_games, "call_raw", side_effect=self.arena), \
                patch.object(arena_snapshot, "call_raw", side_effect=self.arena), \
                patch("time.time_ns", side_effect=itertools.count(10 ** 9, 1000)), \
                contextlib.redirect_stdout(io.StringIO()) as out:
            code = arena.follow(label, log, reg_path=REGISTRY, run_gradle=self.gradle, clock=self.clock,
                                sleep=self.clock.sleep, **kw)
        return code, out.getvalue()

    def entries(self):
        return {(u["label"], u["upload"]): u for u in json.loads(REGISTRY.read_text(encoding="utf-8"))["uploads"]}

    def test_a_paste_during_follow_is_registered_followed_fetched_and_checked(self):
        self.arena.rows = [(0, row(100)), (T0 + 100, row(200, pct=40, busy=True)),
                           (T0 + 700, row(200, pct=100, score=21.5))]
        code, out = self.follow()
        self.assertEqual(0, code, out)
        e = self.entries()
        new, old = e[("v02", 1)], e[("v01", 1)]
        self.assertEqual((200, "bronze", 22, NEW_SHA, NEW_BUILD, "cfg2"),
                         (new["agent"], new["league"], new["submission"], new["artifact"]["sha256"], new["artifact"]["build"],
                          new["artifact"]["config"]))
        self.assertIn("kept copy artifacts/%s/Player.java" % NEW_SHA[:8], new["artifact"]["source"])
        self.assertEqual(NEW_ARTIFACT, Path("artifacts/%s/Player.java" % NEW_SHA[:8]).read_bytes())
        self.assertTrue(old["replaced"].endswith("by v02 upload 1"), old["replaced"])
        p = new["placement"]
        self.assertEqual((21.5, 3, 200, 2, True), (p["score"], p["rank"], p["agents"], p["games"], p["watched"]))
        self.assertIn("SAME in 2 of 2 games", new["attribution"])
        self.assertEqual("the user in the IDE (registered by tools/arena.py follow)", new["submittedBy"])
        up = Path("replays/arena/v02/bronze/upload-01")
        self.assertEqual(["21.json", "22.json"], sorted(f.name for f in (up / "games").iterdir()))
        self.assertTrue(list((up / "snapshots").glob("battles-*.json")))
        self.assertFalse(Path("replays/arena/unknown").exists(), "the pre-registration snapshot moved to the upload")
        for f in ("arena.log", "cgAttribute.log", "arena-compare.json"):
            self.assertTrue((up / "checks" / f).is_file(), f)
        self.assertTrue(Path("replays/arena/v01/bronze/upload-01/games/11.json").is_file(), "the live entry was saved")
        self.assertIn("paste the code", Path("replays/arena/v01/bronze/upload-01/checks/arena.log").read_text())
        manifest = json.loads(Path(new["games"]).read_text(encoding="utf-8"))
        self.assertEqual({NEW_BUILD: 2}, manifest["summary"]["markers"])
        self.assertEqual(json.loads(Path(new["snapshots"]).read_text(encoding="utf-8"))["entry"]["percentage"], 100)
        self.assertIn(b"\r\n", REGISTRY.read_bytes(), "the registry keeps its line endings")
        self.assertEqual(1, sum("artifactInfo" in c for c in self.gradle.calls))
        self.assertIn("placement complete: score 21.5", out)

    def test_a_promotion_during_the_placement_continues_in_the_new_league(self):
        self.arena.rows = [(0, row(200, pct=40, busy=True)), (T0 + 400, row(300, league=4, pct=20, busy=True, created=CREATED + 9)),
                           (T0 + 900, row(300, league=4, pct=100, score=18.0, created=CREATED + 9))]
        self.arena.battles[300] = lambda t: [battle(31, 300, submission=22)]
        code, out = self.follow()
        self.assertEqual(0, code, out)
        e = self.entries()
        first, second = e[("v02", 1)], e[("v02", 2)]
        self.assertEqual(("silver", None), (first["promotedTo"], first["placement"]["score"]))
        self.assertIn("40 %", first["placement"]["lastPartial"])
        self.assertTrue(first["replaced"].endswith("by v02 upload 2 in Silver"), first["replaced"])
        self.assertEqual((300, "silver", 18.0), (second["agent"], second["league"], second["placement"]["score"]))
        self.assertEqual(first["artifact"]["sha256"], second["artifact"]["sha256"])
        self.assertIn("during its placement", second["submittedBy"])
        self.assertEqual(1, sum("artifactInfo" in c for c in self.gradle.calls), "the second agent reuses the identity")
        self.assertTrue(Path("replays/arena/v02/silver/upload-02/games/31.json").is_file())

    def test_another_paste_in_the_same_league_stops_the_follow(self):
        self.arena.rows = [(0, row(200, pct=40, busy=True)), (T0 + 400, row(400, pct=10, busy=True))]
        with self.assertRaises(arena.Stopped):
            self.follow()
        self.assertIsNone(self.entries()[("v02", 1)]["placement"]["score"])

    def test_an_interrupted_follow_resumes_with_the_registered_agent(self):
        self.arena.rows = [(0, row(200, pct=40, busy=True))]
        real, calls = arena.fetch, itertools.count()

        def interrupted(*args):  # the live entry's fetch passes, the first one of the placement is interrupted
            if next(calls):
                raise KeyboardInterrupt
            return real(*args)

        with self.assertRaises(KeyboardInterrupt), patch.object(arena, "fetch", side_effect=interrupted):
            self.follow()
        self.assertIn(("v02", 1), self.entries())
        self.arena.rows = [(0, row(200, pct=100, score=21.0))]
        code, out = self.follow()
        self.assertEqual(0, code, out)
        self.assertIn("resuming v02 upload 1", out)
        self.assertEqual(21.0, self.entries()[("v02", 1)]["placement"]["score"])
        self.assertEqual(2, len(self.entries()))

    def test_an_artifact_of_another_configuration_is_refused_for_a_label(self):
        self.arena.rows = [(0, row(200, pct=40, busy=True))]
        before = REGISTRY.read_bytes()
        with self.assertRaises(arena.Refused) as e:
            self.follow("v01", artifact="build/submit/Player.java")
        self.assertIn("one label, one configuration", str(e.exception))
        self.assertEqual(before, REGISTRY.read_bytes())

    def test_status_names_an_unregistered_agent(self):
        self.arena.rows = [(0, row(200, pct=40, busy=True))]
        with patch.object(arena_games, "call_raw", side_effect=self.arena), contextlib.redirect_stdout(io.StringIO()) as out:
            arena.status(REGISTRY)
        self.assertIn("agent 200 in bronze, placement 40 % (in progress)", out.getvalue())
        self.assertIn("agent 200 is not registered", out.getvalue())
        self.assertIn("registry's live entry: v01 upload 1 (agent 100, bronze)", out.getvalue())


if __name__ == "__main__":
    unittest.main()
