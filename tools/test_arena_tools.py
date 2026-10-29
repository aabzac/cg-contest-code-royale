"""Offline tests of tools/arena_games.py (marker reading) and tools/arena_compare.py (registry checks, statistics, P1 verdicts).

Run: python -B -I -m unittest discover -s tools -p "test_*.py"   (no network)
"""
import json
import math
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import arena_compare  # noqa: E402
import arena_games  # noqa: E402


class MarkerTest(unittest.TestCase):
    def test_reads_the_marker_line_only(self):
        self.assertEqual("5bace689", arena_games.marker("TRAIN 5 11 10 12 14 6 8 9"))
        self.assertEqual("0000ffff", arena_games.marker("TRAIN 0 0 0 0 15 15 15 15"))
        for line in (None, "", "TRAIN", "TRAIN 5 11 10 12 14 6 8", "TRAIN 5 11 10 12 14 6 8 9 1",
                     "TRAIN 5 11 10 12 14 6 8 16", "TRAIN 05 11 10 12 14 6 8 9", "TRAIN +5 11 10 12 14 6 8 9",
                     "TRAIN 5 11 10 12 14 6 8 9 ", "TRAIN  5 11 10 12 14 6 8 9", "WAIT 5 11 10 12 14 6 8 9"):
            self.assertIsNone(arena_games.marker(line), repr(line))

    def test_the_seats_first_answer_carries_it(self):
        frames = [{"agentId": -1}, {"agentId": 0, "stdout": "WAIT\nTRAIN\n"},
                  {"agentId": 1, "stdout": "MOVE 1 2\nTRAIN 14 5 4 1 7 10 8 3\n"},
                  {"agentId": 0, "stdout": "WAIT\nTRAIN 14 5 4 1 7 10 8 3\n"}]
        self.assertEqual("e5417a83", arena_games.turn0_marker(frames, 1))
        self.assertIsNone(arena_games.turn0_marker(frames, 0), "only turn 0 counts")
        self.assertIsNone(arena_games.turn0_marker([{"agentId": -1}, {"agentId": 0}], 0), "no stdout")


def manifest(agent, results, marker=None):
    """A games manifest of agent with (points, opponent score, opponent pseudo) per game, alternating seats."""
    games = []
    for i, (pts, score, pseudo) in enumerate(results):
        seat = i % 2
        ranks = [0, 0] if pts == 0.5 else ([0, 1] if (pts == 1.0) == (seat == 0) else [1, 0])
        games.append({"gameId": i, "seat": seat, "ranks": ranks, "marker": marker,
                      "opponent": {"pseudo": pseudo, "agentId": 100 + i, "ratingAtFetch": score}})
    return {"agentId": agent, "games": games}


EVEN = [((1.0 if i % 2 else 0.0), 10.0 + i % 7, "p%d" % i) for i in range(20)]


class CompareTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def registry(self, uploads):
        """uploads: dicts with label, upload, league, score and optional results, config, build, marker, agent, promotedTo."""
        entries = []
        for i, spec in enumerate(uploads):
            agent = spec.get("agent", 1000 + i)
            path = os.path.join(self.dir.name, "%d.json" % i)
            with open(path, "w", encoding="utf-8") as f:
                json.dump(manifest(spec.get("manifestAgent", agent), spec.get("results", EVEN), spec.get("marker")), f)
            entry = {"label": spec["label"], "upload": spec["upload"], "agent": agent, "league": spec.get("league", "bronze"),
                     "artifact": {"config": spec.get("config", "cfg-" + spec["label"]), "build": spec.get("build")},
                     "placement": {"score": spec.get("score")}, "games": path}
            if spec.get("promotedTo"):
                entry["promotedTo"] = spec["promotedTo"]
            entries.append(entry)
        return {"uploads": entries}

    def test_points_follow_cg_ranks_for_either_seat(self):
        m = manifest(1, [(1.0, 10, "a"), (1.0, 10, "b"), (0.0, 10, "c"), (0.5, 10, "d")])
        self.assertEqual([1.0, 1.0, 0.0, 0.5], [arena_compare.points(g) for g in m["games"]])

    def test_the_rating_sits_where_the_results_balance(self):
        games = [(1.0, 10.0), (0.0, 10.0), (1.0, 12.0), (0.0, 8.0)]
        r, se, _ = arena_compare.fit_rating(games, 2.0)
        self.assertAlmostEqual(10.0, r, places=6)
        p = 1 / (1 + math.exp(-(r - 12.0) / 2.0)), 0.5, 1 / (1 + math.exp(-(r - 8.0) / 2.0))
        info = (p[0] * (1 - p[0]) + 2 * 0.25 + p[2] * (1 - p[2])) / 4.0
        self.assertAlmostEqual(1 / math.sqrt(info), se, places=6)
        self.assertEqual((2.0, False), arena_compare.fit_scale([games]), "fewer than 60 games: the default scale")

    def test_a_scale_the_results_cannot_fix_falls_back_to_the_default(self):
        flat = [[((1.0 if i % 2 else 0.0), 15.0) for i in range(30)] for _ in range(3)]
        self.assertEqual((2.0, False), arena_compare.fit_scale(flat), "every opponent at one score")

    def test_the_noise_margin_pools_versions_within_a_league(self):
        reg = self.registry([{"label": "v01", "upload": 1, "score": 18.0}, {"label": "v01", "upload": 2, "score": 16.0},
                             {"label": "v02", "upload": 1, "score": 12.0}, {"label": "v02", "upload": 2, "score": 12.5},
                             {"label": "v02", "upload": 3, "score": 13.0},
                             {"label": "v01", "upload": 3, "score": 30.0, "league": "Silver"}])
        report = arena_compare.compare(reg, "v02", "v01")
        noise = report["leagues"]["bronze"]["noise"]
        sd = math.sqrt((1 * 2.0 + 2 * 0.25) / 3)  # v01: variance 2 (1 df), v02: variance 0.25 (2 df)
        self.assertAlmostEqual(sd, noise["pooledSd"], places=9)
        self.assertEqual(3, noise["df"])
        self.assertAlmostEqual(2 * sd, noise["M"], places=9)
        self.assertEqual([30.0], report["leagues"]["silver"]["labels"]["v01"]["placementScores"], "leagues apart, in lower case")
        self.assertIsNone(report["leagues"]["silver"]["noise"]["M"])
        self.assertEqual("no data", report["leagues"]["silver"]["verdict"]["decision"])
        self.assertEqual("not clearly worse", arena_compare.compare(reg, "v01", "v01")["leagues"]["bronze"]["verdict"]["decision"])

    def test_verdict_rules(self):
        base = {"placementScores": [18.0, 16.0], "mean": 17.0, "performance": 17.0, "performanceSe": 0.5}
        worse = {"placementScores": [13.0, 13.0], "mean": 13.0, "performance": 15.0, "performanceSe": 0.5}

        def decide(c, s=base, margin=3.0):
            return arena_compare.verdict({"c": c, "s": s}, [], margin, "c", "s")["decision"]

        self.assertEqual("roll back", decide(worse), "mean -4 < -3 and rating -2 < -1.41")
        self.assertEqual("not clearly worse", decide(dict(worse, performance=16.0)), "rating within 2 SE")
        self.assertEqual("not clearly worse", decide(dict(worse, mean=14.5)), "mean within M")
        one = {"placementScores": [10.0], "mean": 10.0, "performance": 10.0, "performanceSe": 1.0}
        self.assertEqual("roll back early", decide(one), "10 < 17 - 2 * 3")
        self.assertEqual("upload the candidate again", decide(dict(one, placementScores=[12.0], mean=12.0)))
        self.assertEqual("no baseline", decide(worse, s=dict(base, placementScores=[18.0])))
        self.assertEqual("no noise margin", decide(worse, margin=None))
        self.assertEqual("no data", decide(dict(worse, placementScores=[], mean=None)))
        self.assertEqual("undecided: no performance rating", decide(dict(worse, performanceSe=None)))
        self.assertEqual("no data", arena_compare.verdict({"s": base}, [], 3.0, "c", "s")["decision"])
        promoted = dict(one, promoted=[{"upload": "c/upload-1", "to": "silver"}])
        self.assertEqual("promoted: keep the candidate", decide(promoted))

    def test_a_placement_ended_by_a_promotion_has_no_score_in_the_old_league(self):
        reg = self.registry([{"label": "v01", "upload": 1, "score": 18.0}, {"label": "v01", "upload": 2, "score": 16.0},
                             {"label": "v02", "upload": 1, "score": 19.5, "promotedTo": "silver"},
                             {"label": "v02", "upload": 2, "score": None, "league": "silver"}])
        report = arena_compare.compare(reg, "v02", "v01")
        bronze = report["leagues"]["bronze"]
        self.assertEqual([], bronze["labels"]["v02"]["placementScores"])
        self.assertEqual(20, bronze["labels"]["v02"]["games"], "its games still count for the rating")
        self.assertAlmostEqual(math.sqrt(2.0), bronze["noise"]["pooledSd"], places=9)
        self.assertEqual("promoted: keep the candidate", bronze["verdict"]["decision"])
        self.assertIsNone(report["leagues"]["silver"]["labels"]["v02"]["mean"], "not complete yet")

    def test_an_inconsistent_registry_is_refused(self):
        cases = {
            "one label, one configuration": [{"label": "v01", "upload": 1, "score": 18.0, "config": "a"},
                                             {"label": "v01", "upload": 2, "score": 16.0, "config": "b"}],
            "games manifest is of agent": [{"label": "v01", "upload": 1, "score": 18.0, "manifestAgent": 7}],
            "games carry marker": [{"label": "v01", "upload": 1, "score": 18.0, "build": "e5417a83", "marker": "5bace689"}],
            "no game carries the entry's build": [{"label": "v01", "upload": 1, "score": 18.0, "build": "e5417a83"}],
            "listed twice": [{"label": "v01", "upload": 1, "score": 18.0}, {"label": "v01", "upload": 1, "score": 16.0, "agent": 2}],
            "is also": [{"label": "v01", "upload": 1, "score": 18.0, "agent": 5}, {"label": "v01", "upload": 2, "score": 16.0, "agent": 5}],
            "no artifact.config": [{"label": "v01", "upload": 1, "score": 18.0, "config": ""}],
            "league None is none of": [{"label": "v01", "upload": 1, "score": 18.0, "league": None}],
            "league 'brnze' is none of": [{"label": "v01", "upload": 1, "score": 18.0, "league": "brnze"}],
            "agent must be a number": [{"label": "v01", "upload": 1, "score": 18.0, "agent": "6823455", "manifestAgent": "6823455"}],
        }
        for why, uploads in cases.items():
            with self.assertRaises(arena_compare.RegistryError, msg=why) as caught:
                arena_compare.compare(self.registry(uploads))
            self.assertIn(why, str(caught.exception))
        missing = self.registry([{"label": "v01", "upload": 1, "score": 18.0}])
        missing["uploads"][0]["games"] = os.path.join(self.dir.name, "absent.json")
        with self.assertRaises(arena_compare.RegistryError) as caught:
            arena_compare.compare(missing)
        self.assertIn("does not load", str(caught.exception))
        ok = self.registry([{"label": "v01", "upload": 1, "score": 18.0, "build": "e5417a83", "marker": "e5417a83", "league": " Bronze"},
                            {"label": "v01", "upload": 2, "score": 16.0}])
        self.assertEqual(["bronze"], list(arena_compare.compare(ok)["leagues"]), "a matching marker, no marker without a build, case and spaces")

    def test_a_game_without_a_turn0_answer_is_listed_not_refused(self):
        reg = self.registry([{"label": "v01", "upload": 1, "score": 18.0, "build": "e5417a83", "marker": "e5417a83"},
                             {"label": "v01", "upload": 2, "score": 16.0}])
        with open(reg["uploads"][0]["games"], encoding="utf-8") as f:
            m = json.load(f)
        m["games"][3]["marker"] = None
        with open(reg["uploads"][0]["games"], "w", encoding="utf-8") as f:
            json.dump(m, f)
        rows = arena_compare.compare(reg)["leagues"]["bronze"]["uploads"]
        self.assertEqual([3], rows[0]["noMarker"])
        self.assertEqual([], rows[1]["noMarker"], "no build, nothing to miss")

    def test_a_candidate_still_placing_is_waited_for(self):
        reg = self.registry([{"label": "v01", "upload": 1, "score": 18.0}, {"label": "v01", "upload": 2, "score": 16.0},
                             {"label": "v02", "upload": 1, "score": 12.0}, {"label": "v02", "upload": 2, "score": None}])
        v = arena_compare.compare(reg, "v02", "v01")["leagues"]["bronze"]["verdict"]
        self.assertEqual("pending: wait for the placement", v["decision"])
        self.assertIn("v02/upload-2", v["why"])

    def test_shared_opponents_are_stratified(self):
        a = manifest(1, [(1.0, 10, "x"), (1.0, 10, "x"), (0.0, 10, "y")])
        b = manifest(2, [(0.0, 10, "x"), (1.0, 10, "y"), (1.0, 10, "z")])
        s = arena_compare.shared(a, b)
        self.assertEqual(2, s["opponents"])
        wx, wy = 1 / (1 / 2 + 1 / 1), 1 / (1 / 1 + 1 / 1)
        self.assertAlmostEqual((wx * (1.0 - 0.0) + wy * (0.0 - 1.0)) / (wx + wy), s["delta"], places=9)
        self.assertAlmostEqual(0.5 / math.sqrt(wx + wy), s["deltaSe"], places=9)


if __name__ == "__main__":
    unittest.main()
