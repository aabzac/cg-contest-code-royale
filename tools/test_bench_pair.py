"""Offline tests of tools/bench_pair.py (pairing of bench reports, policy P2 gates).

Run: python -B -I -m unittest discover -s tools -p "test_*.py"   (no network)
"""
import contextlib
import io
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bench_pair  # noqa: E402


def game(seed, a_seat, outcome, turns=100, fail_a=None, fail_b=None, score=None):
    """botA's game on a_seat: outcome "A" (botA wins), "B" or "D"; fail_* a failure kind of that side."""
    winner = a_seat if outcome == "A" else 1 - a_seat if outcome == "B" else -1
    g = {"seed": seed, "aSeat": a_seat, "winner": winner, "turns": turns,
         "score": score or ([50, 0] if winner == 0 else [0, 50] if winner == 1 else [20, 20]),
         "errors": [None, None], "extraLines": [0, 0], "slow": [0, 0], "timeouts": [0, 0]}
    for side, kind in (("A", fail_a), ("B", fail_b)):
        if kind:
            i = a_seat if side == "A" else 1 - a_seat
            if kind == "errors":
                g["errors"][i] = "killed"
            else:
                g[kind][i] = 1
    return g


def report(bot_a, bot_b, maps, seed_base=100, commit="c1", effective_b=None):
    """maps: per map (game spec on seat 0, game spec on seat 1), each an outcome or a dict of game() arguments."""
    games = []
    for m, pair in enumerate(maps):
        for a_seat, spec in enumerate(pair):
            spec = {"outcome": spec} if isinstance(spec, str) else spec
            games.append(game(seed_base + m, a_seat, **spec))
    rep = {"botA": bot_a, "botB": bot_b, "maps": len(maps), "seedBase": seed_base, "turnMs": 50, "threads": 8,
           "revision": {"commit": commit, "status": [], "diffSha256": "d"}, "games": games}
    if effective_b:
        rep["effectiveB"] = effective_b
    return rep


class PairTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def path(self, rep, name):
        p = os.path.join(self.dir.name, name)
        with open(p, "w", encoding="utf-8") as f:
            json.dump(rep, f)
        return p

    def run_tool(self, *argv):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = bench_pair.main(list(argv))
        return code, buf.getvalue()

    def summary(self, *argv):
        out = os.path.join(self.dir.name, "summary.json")
        code, text = self.run_tool(*argv, "--out", out)
        self.assertNotEqual(2, code, text)
        with open(out, encoding="utf-8") as f:
            return code, json.load(f)

    def test_map_scores_and_their_interval(self):
        p = self.path(report("cand", "ctrl", [("A", "A"), ("A", "B"), ("D", "D"), ("B", "B")]), "r.json")
        code, s = self.summary("direct", p)
        self.assertEqual(0, code)
        self.assertAlmostEqual(0.5, s["score"]["mean"])
        sd = (0.5 / 3) ** 0.5
        self.assertAlmostEqual(sd, s["score"]["sd"])
        self.assertAlmostEqual(0.5 - 1.96 * sd / 2, s["score"]["lower"])
        self.assertEqual({"seat0": {"wins": 2, "draws": 1, "losses": 1}, "seat1": {"wins": 1, "draws": 1, "losses": 2}}, s["record"])

    def test_a_draw_counts_half(self):
        p = self.path(report("cand", "ctrl", [("D", "A")]), "r.json")
        self.assertAlmostEqual(0.75, self.summary("direct", p)[1]["score"]["mean"])

    def test_direct_gates(self):
        # S = 0.5 exactly: --min-score 0.5 passes, a lower bound above 0.5 cannot
        p = self.path(report("cand", "ctrl", [("A", "B")] * 10), "r.json")
        self.assertEqual(0, self.run_tool("direct", p, "--min-score", "0.5")[0])
        self.assertEqual(1, self.run_tool("direct", p, "--min-lower", "0.5")[0])
        q = self.path(report("cand", "ctrl", [("A", "A")] * 9 + [("A", "B")]), "q.json")
        self.assertEqual(0, self.run_tool("direct", q, "--min-lower", "0.5")[0])
        r = self.path(report("cand", "ctrl", [("A", "B")] * 9 + [("B", "B")]), "w.json")
        self.assertEqual(1, self.run_tool("direct", r, "--min-score", "0.5")[0])

    def test_maps_that_differ_from_the_mirror_are_changed(self):
        mirror = self.path(report("ctrl", "ctrl", [("A", "B")] * 5), "m.json")
        maps = [("A", "B")] * 5
        maps[1] = ({"outcome": "A", "turns": 101}, "B")   # same winner, another game
        maps[3] = ("B", "B")
        p = self.path(report("cand", "ctrl", maps), "r.json")
        code, s = self.summary("direct", p, "--mirror", mirror)
        self.assertEqual([101, 103], s["changedMaps"])
        self.assertEqual(1, self.run_tool("direct", p, "--mirror", mirror, "--max-changed", "0.4")[0])
        self.assertEqual(0, self.run_tool("direct", p, "--mirror", mirror, "--max-changed", "0.41")[0])

    def test_common_pairs_the_maps(self):
        cand = self.path(report("cand", "boss", [("A", "A"), ("A", "B"), ("A", "A")]), "c.json")
        ctrl = self.path(report("ctrl", "boss", [("A", "B"), ("A", "B"), ("A", "A")]), "k.json")
        code, s = self.summary("common", cand, ctrl, "--min-delta", "-0.02")
        self.assertEqual(0, code)
        self.assertAlmostEqual(1 / 6, s["delta"]["mean"])
        self.assertAlmostEqual(5 / 6, s["candidate"]["mean"])
        self.assertEqual([100], s["changedMaps"])
        worse = self.path(report("cand", "boss", [("A", "B"), ("A", "B"), ("A", "B")]), "w.json")
        self.assertEqual(1, self.run_tool("common", worse, ctrl, "--min-delta", "-0.02")[0])

    def test_an_opponent_failure_drops_the_map_from_both_reports(self):
        n = 200
        cand_maps, ctrl_maps = [("A", "A")] * n, [("A", "B")] * n
        cand_maps[5] = ({"outcome": "A", "fail_b": "timeouts"}, "A")
        ctrl_maps[9] = ("A", {"outcome": "B", "fail_b": "errors"})
        cand = self.path(report("cand", "boss", cand_maps), "c.json")
        ctrl = self.path(report("ctrl", "boss", ctrl_maps), "k.json")
        code, s = self.summary("common", cand, ctrl)
        self.assertEqual([105, 109], s["droppedMaps"])
        self.assertEqual(198, s["keptMaps"])
        ctrl_maps[11] = ({"outcome": "A", "fail_b": "extraLines"}, "B")   # a third: more than 1 %
        ctrl = self.path(report("ctrl", "boss", ctrl_maps), "k.json")
        self.assertEqual(2, self.run_tool("common", cand, ctrl)[0])

    def test_a_control_failure_drops_the_map(self):
        cand = self.path(report("cand", "boss", [("A", "A")] * 100), "c.json")
        ctrl_maps = [("A", "B")] * 100
        ctrl_maps[0] = ({"outcome": "B", "fail_a": "errors"}, "B")
        ctrl = self.path(report("ctrl", "boss", ctrl_maps), "k.json")
        self.assertEqual([100], self.summary("common", cand, ctrl)[1]["droppedMaps"])
        direct_maps = [("A", "B")] * 100
        direct_maps[2] = ({"outcome": "A", "fail_b": "errors"}, "B")
        p = self.path(report("cand", "ctrl", direct_maps), "r.json")
        self.assertEqual([102], self.summary("direct", p)[1]["droppedMaps"])

    def test_any_candidate_failure_fails_g0(self):
        for kind in ("errors", "timeouts", "slow", "extraLines"):
            maps = [("A", "A")] * 10
            maps[4] = ("A", {"outcome": "A", "fail_a": kind})
            p = self.path(report("cand", "ctrl", maps), "r.json")
            code, s = self.summary("direct", p, "--min-lower", "0.5")
            self.assertEqual(1, code, kind)
            self.assertFalse(s["gates"]["G0"], kind)
            self.assertEqual(1, s["candidateFailures"][kind], kind)
            self.assertEqual([104], s["candidateFailureSeeds"], kind)
        # a slow in-process opponent is no failure
        maps = [("A", "A")] * 10
        maps[4] = ("A", {"outcome": "A", "fail_b": "slow"})
        self.assertEqual([], self.summary("direct", self.path(report("cand", "ctrl", maps), "s.json"))[1]["droppedMaps"])

    def test_inconsistent_inputs_are_refused(self):
        good = report("cand", "ctrl", [("A", "B")] * 3)
        p = self.path(good, "good.json")
        missing = json.loads(json.dumps(good))
        del missing["games"][1]
        repeated = json.loads(json.dumps(good))
        repeated["games"][1]["aSeat"] = 0
        old = json.loads(json.dumps(good))
        for g in old["games"]:
            del g["slow"]
        same = report("ctrl", "ctrl", [("A", "B")] * 3)
        for rep in (missing, repeated, old, same):
            self.assertEqual(2, self.run_tool("direct", self.path(rep, "bad.json"))[0])
        self.assertEqual(2, self.run_tool("direct", self.path([], "list.json"))[0])
        self.assertEqual(2, self.run_tool("direct", p, "--max-changed", "0.1")[0], "needs --mirror")
        # the mirror must be the control against itself, on the same maps, from the same commit
        for mirror in (report("cand", "cand", [("A", "B")] * 3), report("ctrl", "ctrl", [("A", "B")] * 4),
                       report("ctrl", "ctrl", [("A", "B")] * 3, seed_base=200), report("ctrl", "ctrl", [("A", "B")] * 3, commit="c2")):
            self.assertEqual(2, self.run_tool("direct", p, "--mirror", self.path(mirror, "m.json"))[0])
        # common: one opponent, playing the same code
        cand = self.path(report("cand", "boss", [("A", "B")] * 3, effective_b="boss4 sha256=1"), "c.json")
        for ctrl in (report("ctrl", "old", [("A", "B")] * 3), report("ctrl", "boss", [("A", "B")] * 3, effective_b="boss4 sha256=2"),
                     report("cand", "boss", [("A", "B")] * 3)):
            self.assertEqual(2, self.run_tool("common", cand, self.path(ctrl, "k.json"))[0])

    def test_the_summary_names_its_inputs(self):
        rep = report("cand", "ctrl", [("A", "B")] * 3)
        p = self.path(rep, "r.json")
        code, s = self.summary("direct", p)
        self.assertEqual("P2", s["policy"])
        self.assertEqual("pass", s["verdict"])
        self.assertEqual(p, s["inputs"][0]["path"])
        self.assertEqual(64, len(s["inputs"][0]["sha256"]))
        self.assertEqual("c1", s["inputs"][0]["revision"]["commit"])


if __name__ == "__main__":
    unittest.main()
