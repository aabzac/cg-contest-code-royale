"""Offline integration checks for arena storage, resuming downloads and upload identity."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import arena_games  # noqa: E402
import arena_paths  # noqa: E402
import arena_snapshot  # noqa: E402


AGENT = 12345
BUILD = "a2c71dbb"
ROOT = Path("replays/arena/v02/bronze/upload-02")
REGISTRY = Path("docs/data/arena-uploads.json")
MANIFEST = Path("docs/data/games.json")


def entry(agent=AGENT, label="v02", league="bronze", upload=2, build=BUILD):
    return {"agent": agent, "label": label, "league": league, "upload": upload,
            "artifact": {"build": build, "config": "173dd43b"}, "games": str(MANIFEST)}


def battle(gid, agent=AGENT, done=True):
    return {"gameId": gid, "done": done, "players": [
        {"playerAgentId": agent, "position": 0, "nickname": "me", "submissionId": 111},
        {"playerAgentId": 98765, "position": 1, "nickname": "opponent", "submissionId": 222}]}


def replay(gid, agent=AGENT, build=BUILD):
    train = "TRAIN" if build is None else "TRAIN " + " ".join(str(int(c, 16)) for c in build)
    # Our global agent is in seat 1 and wins. The battle's position 0 is its rank, not its seat.
    obj = {"gameId": gid, "agents": [
        {"agentId": agent, "index": 1, "codingamer": {"pseudo": "me"}},
        {"agentId": 98765, "index": 0, "codingamer": {"pseudo": "opponent"}, "score": 20.0}],
        "frames": [{"agentId": -1}, {"agentId": 0, "stdout": "WAIT\nTRAIN\n"},
                   {"agentId": 1, "stdout": "WAIT\n" + train + "\n"}],
        "ranks": [1, 0], "scores": [0, 80], "refereeInput": "seed=321\n"}
    return (json.dumps(obj, indent=2) + " \n").encode()


def leaderboard(agent=AGENT, league=3):
    return {"users": [{"pseudo": "me", "agentId": agent,
                       "league": {"divisionIndex": league, "divisionAgentsCount": 200},
                       "rank": 50, "score": 22.0, "percentage": 100, "inProgress": False}],
            "leagues": {str(league): {"divisionAgentsCount": 200}}}


class ArenaDownloadsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        old_cwd = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, old_cwd)
        self.write(REGISTRY, {"uploads": [entry()]})

    @staticmethod
    def write(path, data):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data if isinstance(data, bytes) else json.dumps(data).encode())

    def run_games(self, *args, responses=()):
        with patch.object(arena_games, "call_raw", side_effect=responses) as request, \
                contextlib.redirect_stdout(io.StringIO()):
            code = arena_games.main([str(AGENT), *args])
        return code, request

    def test_download_uses_registry_and_preserves_bytes_and_seat(self):
        raw = replay(42)
        code, request = self.run_games(responses=[json.dumps([battle(42)]).encode(), raw])
        self.assertEqual(0, code)
        self.assertEqual(raw, (ROOT / "games/42.json").read_bytes())
        self.assertEqual(2, request.call_count)
        self.assertEqual(("gameResult/findByGameId", [42, None]), request.call_args.args)
        saved_lists = list((ROOT / "snapshots").rglob("battles*.json"))
        self.assertEqual(1, len(saved_lists))
        data = json.loads(MANIFEST.read_text())
        self.assertEqual(1, data["games"][0]["seat"])
        self.assertEqual(hashlib.sha256(raw).hexdigest(), data["games"][0]["sha256"])
        self.assertEqual(1, data["summary"]["wins"])

    def test_empty_live_list_keeps_cached_games_and_downloads_saved_finished_games(self):
        self.write(ROOT / "games/41.json", replay(41))
        self.write(ROOT / "snapshots/placement/raw/battles-old.json", [battle(42)])
        code, request = self.run_games(responses=[b"[]", replay(42)])
        self.assertEqual(0, code)
        self.assertEqual([41, 42], [g["gameId"] for g in json.loads(MANIFEST.read_text())["games"]])
        self.assertEqual(2, request.call_count, "the cached replay must not be fetched again")

    def test_offline_reads_cache_and_never_uses_network(self):
        self.write(ROOT / "games/41.json", replay(41))
        code, request = self.run_games("--offline")
        self.assertEqual(0, code)
        request.assert_not_called()
        self.assertEqual([41], [g["gameId"] for g in json.loads(MANIFEST.read_text())["games"]])

    def test_unfinished_live_game_is_not_fetched(self):
        code, request = self.run_games(responses=[json.dumps([battle(42, done=False)]).encode()])
        self.assertEqual(0, code)
        self.assertEqual(1, request.call_count)
        self.assertEqual(1, json.loads(MANIFEST.read_text())["battleRows"]["notDone"])
        self.assertFalse((ROOT / "games/42.json").exists())

    def test_foreign_battle_list_cannot_download_into_our_upload(self):
        foreign = json.dumps([battle(42, agent=999)]).encode()
        with patch.object(arena_games, "call_raw", return_value=foreign) as request, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            try:
                code = arena_games.main([str(AGENT)])
            except ValueError:
                code = 2
        self.assertNotEqual(0, code)
        self.assertEqual(1, request.call_count, "a foreign battle must not cause a replay request")
        self.assertFalse((ROOT / "games/42.json").exists())

    def test_rejected_download_battle_response_does_not_poison_a_valid_retry(self):
        bad_responses = (b"{not-json", json.dumps([battle(42, agent=999)]).encode())
        snapshots = ROOT / "snapshots"
        for i, bad in enumerate(bad_responses):
            with self.subTest(raw=bad):
                prior_lists = set(snapshots.glob("battles*.json"))
                prior_rejected = set(snapshots.glob("rejected-battles*.json"))
                with contextlib.redirect_stderr(io.StringIO()):
                    code, request = self.run_games(responses=[bad])
                self.assertEqual(1, code)
                self.assertEqual(1, request.call_count)
                self.assertEqual(prior_lists, set(snapshots.glob("battles*.json")))
                rejected = set(snapshots.glob("rejected-battles*.json")) - prior_rejected
                self.assertEqual(1, len(rejected))
                self.assertEqual(bad, rejected.pop().read_bytes())
                gid = 42 + i
                code, request = self.run_games(responses=[json.dumps([battle(gid)]).encode(), replay(gid)])
                self.assertEqual(0, code)
                self.assertEqual(2, request.call_count, "retry must reach the service despite the rejected capture")
                self.assertTrue((ROOT / f"games/{gid}.json").exists())

    def test_rejected_snapshot_battle_response_does_not_poison_later_downloads(self):
        bad_responses = (b"{not-json", json.dumps([battle(42, agent=999)]).encode())
        snapshots = ROOT / "snapshots"
        for i, bad in enumerate(bad_responses):
            with self.subTest(raw=bad):
                prior_lists = set(snapshots.glob("battles*.json"))
                prior_rejected = set(snapshots.glob("rejected-battles*.json"))
                out = Path(f"docs/data/bad-snapshot-{i}.json")
                with patch.object(arena_snapshot, "call_raw",
                                  side_effect=[json.dumps(leaderboard()).encode(), bad]) as request, \
                        contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    code = arena_snapshot.main([str(AGENT), "me", "bronze", str(out)])
                self.assertEqual(1, code)
                self.assertEqual(2, request.call_count)
                self.assertFalse(out.exists())
                self.assertEqual(prior_lists, set(snapshots.glob("battles*.json")))
                rejected = set(snapshots.glob("rejected-battles*.json")) - prior_rejected
                self.assertEqual(1, len(rejected))
                self.assertEqual(bad, rejected.pop().read_bytes())
                gid = 42 + i
                code, request = self.run_games(responses=[json.dumps([battle(gid)]).encode(), replay(gid)])
                self.assertEqual(0, code)
                self.assertEqual(2, request.call_count)
                self.assertTrue((ROOT / f"games/{gid}.json").exists())

    def test_old_unmarked_and_missing_marker_games_are_kept(self):
        for build in (None, BUILD):
            with self.subTest(expected_build=build):
                self.write(REGISTRY, {"uploads": [entry(build=build)]})
                code, _ = self.run_games(responses=[json.dumps([battle(42)]).encode(), replay(42, build=None)])
                self.assertEqual(0, code)
                self.assertIsNone(json.loads(MANIFEST.read_text())["games"][0]["marker"])

    def test_marker_mismatch_is_preserved_outside_the_claimed_version(self):
        raw = replay(42, build="e5417a83")
        code, _ = self.run_games(responses=[json.dumps([battle(42)]).encode(), raw])
        self.assertEqual(1, code)
        self.assertFalse((ROOT / "games/42.json").exists())
        self.assertEqual(raw, Path(f"replays/arena/unknown/bronze/agent-{AGENT}/games/42.json").read_bytes())
        data = json.loads(MANIFEST.read_text())
        self.assertEqual([], data["games"])
        self.assertEqual(1, data["summary"]["failures"])

    def test_cached_marker_mismatch_is_preserved_before_leaving_version_directory(self):
        raw = replay(42, build="e5417a83")
        self.write(ROOT / "games/42.json", raw)
        code, request = self.run_games("--offline")
        self.assertEqual(1, code)
        request.assert_not_called()
        self.assertFalse((ROOT / "games/42.json").exists())
        self.assertEqual(raw, Path(f"replays/arena/unknown/bronze/agent-{AGENT}/games/42.json").read_bytes())

    def test_quarantined_mismatch_is_reported_again_on_offline_resume(self):
        raw = replay(42, build="e5417a83")
        quarantine = Path(f"replays/arena/unknown/bronze/agent-{AGENT}/games/42.json")
        self.write(quarantine, raw)
        for _ in range(2):
            code, request = self.run_games("--offline")
            self.assertEqual(1, code)
            request.assert_not_called()
            self.assertEqual(raw, quarantine.read_bytes())
            self.assertFalse((ROOT / "games/42.json").exists())
            data = json.loads(MANIFEST.read_text())
            self.assertEqual(1, data["summary"]["failures"])
            self.assertEqual(42, data["failures"][0]["gameId"])

    def test_registration_recovers_previously_unknown_capture_with_empty_live_list(self):
        unknown = Path(f"replays/arena/unknown/bronze/agent-{AGENT}")
        raw = replay(41)
        self.write(unknown / "games/41.json", raw)
        self.write(unknown / "snapshots/battles-promoted.json", [battle(41), battle(42)])
        code, request = self.run_games(responses=[b"[]", replay(42)])
        self.assertEqual(0, code)
        self.assertEqual(2, request.call_count, "only the uncached game needs fetching")
        self.assertEqual(raw, (ROOT / "games/41.json").read_bytes())
        self.assertFalse((unknown / "games/41.json").exists())
        self.assertTrue((unknown / "snapshots/battles-promoted.json").exists())
        self.assertEqual([41, 42], [g["gameId"] for g in json.loads(MANIFEST.read_text())["games"]])

    def test_previous_manifest_can_recover_a_missing_replay_after_replacement(self):
        self.write(MANIFEST, {"agentId": AGENT, "games": [{"gameId": 42}]})
        code, request = self.run_games(responses=[b"[]", replay(42)])
        self.assertEqual(0, code)
        self.assertEqual(2, request.call_count)
        self.assertTrue((ROOT / "games/42.json").exists())
        self.assertEqual([42], [g["gameId"] for g in json.loads(MANIFEST.read_text())["games"]])

    def test_failed_missing_replay_stays_known_until_a_later_download_succeeds(self):
        self.write(MANIFEST, {"agentId": AGENT, "games": [{"gameId": 42}]})
        for _ in range(2):
            code, request = self.run_games("--offline")
            self.assertEqual(1, code)
            request.assert_not_called()
            failed = json.loads(MANIFEST.read_text())
            self.assertEqual([], failed["games"])
            self.assertEqual([42], [g["gameId"] for g in failed["failures"]])
        raw = replay(42)
        code, request = self.run_games(responses=[b"[]", raw])
        self.assertEqual(0, code)
        self.assertEqual(2, request.call_count)
        self.assertEqual(raw, (ROOT / "games/42.json").read_bytes())
        recovered = json.loads(MANIFEST.read_text())
        self.assertEqual([42], [g["gameId"] for g in recovered["games"]])
        self.assertEqual([], recovered["failures"])

    def test_alternate_report_retains_completed_game_despite_a_stale_pending_list(self):
        self.write(MANIFEST, {"agentId": AGENT, "games": [{"gameId": 42}]})
        original = MANIFEST.read_bytes()
        self.write(ROOT / "snapshots/battles-stale.json", [battle(42, done=False)])
        alternate = Path("docs/data/check.json")
        code, request = self.run_games("--manifest", str(alternate), "--offline")
        self.assertEqual(1, code, "known finished replay missing from cache must fail, not become pending")
        request.assert_not_called()
        failed = json.loads(alternate.read_text())
        self.assertEqual(0, failed["battleRows"]["notDone"])
        self.assertEqual([42], [g["gameId"] for g in failed["failures"]])
        self.assertEqual(original, MANIFEST.read_bytes())

        raw = replay(42)
        code, request = self.run_games("--manifest", str(alternate),
                                       responses=[json.dumps([battle(42, done=False)]).encode(), raw])
        self.assertEqual(0, code)
        self.assertEqual(2, request.call_count)
        self.assertEqual(raw, (ROOT / "games/42.json").read_bytes())
        self.assertEqual([42], [g["gameId"] for g in json.loads(alternate.read_text())["games"]])
        self.assertEqual(original, MANIFEST.read_bytes(), "alternate report must leave the canonical manifest intact")

    def test_existing_alternate_manifest_for_another_agent_is_rejected_before_network(self):
        alternate = Path("docs/data/another-agent.json")
        self.write(alternate, {"agentId": 999, "games": [{"gameId": 42}]})
        original = alternate.read_bytes()
        with patch.object(arena_games, "call_raw") as request, \
                contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as failure:
            arena_games.main([str(AGENT), "--manifest", str(alternate)])
        self.assertEqual(2, failure.exception.code)
        request.assert_not_called()
        self.assertEqual(original, alternate.read_bytes())
        self.assertFalse(ROOT.exists())

    def test_legacy_registry_entry_has_an_explicit_separate_destination(self):
        old = entry(label="legacy", upload=1, build=None)
        self.write(REGISTRY, {"uploads": [], "legacyUploads": [old]})
        self.assertEqual(Path("replays/arena/legacy/bronze/upload-01"),
                         arena_paths.upload_dir(arena_paths.upload_for(AGENT)))

    def test_registry_rejects_unknown_agent_duplicates_and_unsafe_paths(self):
        with self.assertRaises(ValueError):
            arena_paths.upload_for(999)
        for entries in ([entry(), entry()],
                        [entry(), entry(agent=45678)],
                        [entry(label="../elsewhere")],
                        [entry(league="../silver")],
                        [entry(upload=0)]):
            with self.subTest(entries=entries):
                self.write(REGISTRY, {"uploads": entries})
                with self.assertRaises(ValueError):
                    arena_paths.upload_for(AGENT)

    def test_snapshot_routes_a_promoted_unknown_agent_to_unknown_storage(self):
        promoted = 45678
        lb = json.dumps(leaderboard(promoted, 4)).encode()
        battles = json.dumps([battle(42, agent=promoted)]).encode()
        with patch.object(arena_snapshot, "call_raw", side_effect=[lb, battles]) as request, \
                contextlib.redirect_stdout(io.StringIO()):
            code = arena_snapshot.main([str(AGENT), "me", "bronze", "docs/data/snapshot.json"])
        self.assertEqual(0, code)
        self.assertEqual(("gamesPlayersRanking/findLastBattlesByAgentId", [promoted, None]), request.call_args.args)
        unknown = Path(f"replays/arena/unknown/silver/agent-{promoted}/snapshots")
        saved = list(unknown.rglob("*.json"))
        self.assertEqual(2, len(saved))
        self.assertCountEqual([lb, battles], [p.read_bytes() for p in saved])
        self.assertFalse((ROOT / "snapshots").exists())
        self.assertEqual(promoted, json.loads(Path("docs/data/snapshot.json").read_text())["entry"]["agentId"])

    def test_snapshot_routes_known_new_agent_to_its_registered_upload(self):
        new_agent = 45678
        self.write(REGISTRY, {"uploads": [entry(), entry(agent=new_agent, label="v03", upload=1)]})
        responses = [json.dumps(leaderboard(new_agent)).encode(), json.dumps([battle(42, agent=new_agent)]).encode()]
        with patch.object(arena_snapshot, "call_raw", side_effect=responses), \
                contextlib.redirect_stdout(io.StringIO()):
            code = arena_snapshot.main([str(AGENT), "me", "bronze", "docs/data/snapshot.json"])
        self.assertEqual(0, code)
        self.assertEqual(2, len(list(Path("replays/arena/v03/bronze/upload-01/snapshots").rglob("*.json"))))
        self.assertFalse((ROOT / "snapshots").exists())

    def test_snapshot_preserves_previous_agents_summary_after_promotion(self):
        previous_summaries = ({"entry": {"agentId": AGENT}},
                              {"agentId": AGENT, "entry": None})
        for i, previous in enumerate(previous_summaries):
            with self.subTest(previous_summary=previous):
                promoted = 45678 + i
                out = Path(f"docs/data/old-snapshot-{i}.json")
                self.write(out, previous)
                original = out.read_bytes()
                lb = json.dumps(leaderboard(promoted, 4)).encode()
                battles = json.dumps([battle(42, agent=promoted)]).encode()
                errors = io.StringIO()
                with patch.object(arena_snapshot, "call_raw", side_effect=[lb, battles]), \
                        contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
                    code = arena_snapshot.main([str(AGENT), "me", "bronze", str(out)])
                self.assertEqual(1, code, "the changed output destination must be explicit to callers")
                self.assertTrue(errors.getvalue())
                self.assertEqual(original, out.read_bytes())
                unknown = Path(f"replays/arena/unknown/silver/agent-{promoted}/snapshots")
                summaries = list(unknown.glob("summary-*.json"))
                self.assertEqual(1, len(summaries))
                self.assertEqual(promoted, json.loads(summaries[0].read_text())["agentId"])
                self.assertEqual(lb, next(unknown.glob("leaderboard-*.json")).read_bytes())
                self.assertEqual(battles, next(unknown.glob("battles-*.json")).read_bytes())
                self.assertFalse((ROOT / "snapshots").exists())


if __name__ == "__main__":
    unittest.main()
