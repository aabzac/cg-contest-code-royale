"""Pairs bench reports as policy P2 judges a candidate (docs/local-screen.md, .ai/project.md).

Usage:
  python -I tools/bench_pair.py direct REPORT [--mirror REPORT] [--min-lower X] [--min-score X]
                                [--max-changed F] [--out SUMMARY.json]
  python -I tools/bench_pair.py common CANDIDATE_REPORT CONTROL_REPORT [--min-delta X] [--max-changed F]
                                [--out SUMMARY.json]

A report is written by ./gradlew bench (cr.runner.BenchmarkRunner): botA plays botB on the maps seedBase ..
seedBase+maps-1, each map twice with the seats swapped. botA's map score is the mean of its two games
(win 1, draw 1/2, loss 0). Means and 95 % intervals are taken over maps, mean +- 1.96 SD / sqrt(maps), as
the bench prints them: the two games of a map are one sample.

direct  The candidate (botA) against the control (botB). S is the mean map score. --min-lower X passes
        when the lower bound of S is above X (G1: 0.50), --min-score X when S is at least X (a targeted
        fix: 0.500). With --mirror (the control against itself on the same maps) a map is changed when
        either of its games differs from the mirror's game (winner, turns or final score).
common  The candidate and the control, each as botA against the same opponent (botB) on the same maps.
        Delta is the mean over maps of the candidate's map score minus the control's. --min-delta X passes
        when Delta is at least X (G2 and G3: -0.02; a targeted fix against boss: -0.01). A map is changed
        when either of its games differs between the two reports.

A map where the opponent or the control failed in a game (an error, a timeout or extra output lines) is
dropped, from both reports in common mode. More than 1 % of the maps dropped makes the series invalid.
Gate G0 always applies: the candidate has no error, timeout, slow turn or extra line in any game.
--max-changed F passes when fewer than F of the maps are changed (a targeted fix: 0.10).
Exit code 0 when every gate passes, 1 when one fails, 2 when the input or the series is invalid.
"""
import argparse
import hashlib
import json
import math
import sys

Z = 1.96
MAX_DROPPED = 0.01
EPS = 1e-12
FIELDS = ("seed", "aSeat", "winner", "turns", "score", "errors", "extraLines", "slow", "timeouts")
CANDIDATE_FAILURES = ("errors", "timeouts", "slow", "extraLines")
# A slow in-process opponent still answered in time for the game; a process opponent's late answer is a timeout.
OPPONENT_FAILURES = ("errors", "timeouts", "extraLines")


class Invalid(ValueError):
    pass


def load(path):
    with open(path, "rb") as f:
        data = f.read()
    try:
        rep = json.loads(data.decode("utf-8"))
    except ValueError as e:
        raise Invalid("%s: not a bench report (%s)" % (path, e))
    if not isinstance(rep, dict):
        raise Invalid("%s: not a bench report" % path)
    for k in ("botA", "botB", "maps", "seedBase", "turnMs", "games"):
        if k not in rep:
            raise Invalid("%s: no %r" % (path, k))
    rep["_path"], rep["_sha256"] = path, hashlib.sha256(data).hexdigest()
    return rep


def by_map(rep):
    """{seed: (botA's game on seat 0, on seat 1)}, which must cover seedBase .. seedBase+maps-1 exactly."""
    games = {}
    for g in rep["games"]:
        for k in FIELDS:
            if k not in g:
                raise Invalid("%s: a game has no %r (written before policy P2? rerun the bench)" % (rep["_path"], k))
        key = (g["seed"], g["aSeat"])
        if g["aSeat"] not in (0, 1) or key in games:
            raise Invalid("%s: seed %s has a bad or repeated seat %r" % (rep["_path"], g["seed"], g["aSeat"]))
        games[key] = g
    seeds = range(rep["seedBase"], rep["seedBase"] + rep["maps"])
    if rep["maps"] < 1 or set(games) != {(s, a) for s in seeds for a in (0, 1)}:
        raise Invalid("%s: the games do not cover seeds %d..%d on both seats exactly"
                      % (rep["_path"], rep["seedBase"], rep["seedBase"] + rep["maps"] - 1))
    return {s: (games[(s, 0)], games[(s, 1)]) for s in seeds}


def points(g):
    return 0.5 if g["winner"] == -1 else 1.0 if g["winner"] == g["aSeat"] else 0.0


def map_score(pair):
    return (points(pair[0]) + points(pair[1])) / 2


def failures(g, side, kinds):
    """The failure kinds of botA (side "A") or botB ("B") in game g."""
    i = g["aSeat"] if side == "A" else 1 - g["aSeat"]
    return [k for k in kinds if g[k][i] not in (None, 0)]


def same_game(g, h):
    return g["winner"] == h["winner"] and g["turns"] == h["turns"] and list(g["score"]) == list(h["score"])


def interval(values):
    n = len(values)
    mean = sum(values) / n
    sd = math.sqrt(sum((v - mean) ** 2 for v in values) / (n - 1)) if n > 1 else 0.0
    half = Z * sd / math.sqrt(n)
    return {"mean": mean, "sd": sd, "lower": mean - half, "upper": mean + half}


def record(maps):
    """botA's wins, draws and losses on each seat."""
    r = {"seat0": [0, 0, 0], "seat1": [0, 0, 0]}
    for pair in maps.values():
        for g in pair:
            p = points(g)
            r["seat%d" % g["aSeat"]][0 if p == 1.0 else 1 if p == 0.5 else 2] += 1
    return {k: {"wins": v[0], "draws": v[1], "losses": v[2]} for k, v in r.items()}


def candidate_failures(maps):
    """Games with each failure kind of the candidate (botA), and the seeds where any occurred."""
    count, seeds = {k: 0 for k in CANDIDATE_FAILURES}, set()
    for s, pair in maps.items():
        for g in pair:
            for k in failures(g, "A", CANDIDATE_FAILURES):
                count[k] += 1
                seeds.add(s)
    return count, sorted(seeds)


def same_series(a, b):
    for k in ("seedBase", "maps", "turnMs"):
        if a[k] != b[k]:
            raise Invalid("%s and %s differ in %s (%r, %r)" % (a["_path"], b["_path"], k, a[k], b[k]))
    ra, rb = a.get("revision") or {}, b.get("revision") or {}
    if ra.get("commit") != rb.get("commit"):
        raise Invalid("%s and %s come from different commits (%s, %s)" % (a["_path"], b["_path"], ra.get("commit"), rb.get("commit")))


def same_bot(a, side_a, b, side_b, what):
    if a["bot" + side_a] != b["bot" + side_b]:
        raise Invalid("%s: %s is %r in %s and %r in %s" % (what, what, a["bot" + side_a], a["_path"], b["bot" + side_b], b["_path"]))
    ea, eb = a.get("effective" + side_a), b.get("effective" + side_b)
    if ea is not None and eb is not None and ea != eb:
        raise Invalid("%s plays differently in %s (%s) and %s (%s)" % (what, a["_path"], ea, b["_path"], eb))


def check_dropped(dropped, n):
    if len(dropped) > MAX_DROPPED * n:
        raise Invalid("%d of %d maps dropped for opponent or control failures (more than %.0f %%): rerun with fewer threads"
                      " or a larger turnMs; seeds %s" % (len(dropped), n, 100 * MAX_DROPPED, dropped[:20]))
    if len(dropped) == n:
        raise Invalid("every map dropped")


def source(rep):
    return {k: rep.get(k) for k in ("botA", "botB", "effectiveA", "effectiveB", "seedBase", "maps", "turnMs", "threads",
                                     "java", "revision")} | {"path": rep["_path"], "sha256": rep["_sha256"]}


def direct(rep, mirror=None):
    if rep["botA"] == rep["botB"]:
        raise Invalid("%s: botA and botB are the same spec; a direct comparison needs the candidate against the control" % rep["_path"])
    maps = by_map(rep)
    dropped = sorted(s for s, pair in maps.items() if any(failures(g, "B", OPPONENT_FAILURES) for g in pair))
    check_dropped(dropped, len(maps))
    kept = {s: p for s, p in maps.items() if s not in dropped}
    out = {"mode": "direct", "inputs": [source(rep)], "maps": len(maps), "keptMaps": len(kept), "droppedMaps": dropped,
           "score": interval([map_score(p) for p in kept.values()]), "record": record(kept)}
    if mirror is not None:
        same_bot(mirror, "A", rep, "B", "the control")
        same_bot(mirror, "B", rep, "B", "the control")
        same_series(rep, mirror)
        mm = by_map(mirror)
        out["inputs"].append(source(mirror))
        out["changedMaps"] = sorted(s for s in kept if not all(same_game(g, h) for g, h in zip(maps[s], mm[s])))
    out["candidateFailures"], out["candidateFailureSeeds"] = candidate_failures(maps)
    return out


def common(cand, ctrl):
    if cand["botA"] == ctrl["botA"]:
        raise Invalid("candidate and control are the same spec %r" % cand["botA"])
    same_bot(cand, "B", ctrl, "B", "the opponent")
    same_series(cand, ctrl)
    mc, mk = by_map(cand), by_map(ctrl)
    dropped = sorted(s for s in mc if any(failures(g, "B", OPPONENT_FAILURES) for g in mc[s] + mk[s])
                     or any(failures(g, "A", OPPONENT_FAILURES) for g in mk[s]))
    check_dropped(dropped, len(mc))
    kept = [s for s in mc if s not in dropped]
    out = {"mode": "common", "inputs": [source(cand), source(ctrl)], "maps": len(mc), "keptMaps": len(kept), "droppedMaps": dropped,
           "candidate": interval([map_score(mc[s]) for s in kept]), "control": interval([map_score(mk[s]) for s in kept]),
           "delta": interval([map_score(mc[s]) - map_score(mk[s]) for s in kept]),
           "candidateRecord": record({s: mc[s] for s in kept}), "controlRecord": record({s: mk[s] for s in kept}),
           "changedMaps": sorted(s for s in kept if not all(same_game(g, h) for g, h in zip(mc[s], mk[s])))}
    out["candidateFailures"], out["candidateFailureSeeds"] = candidate_failures(mc)
    return out


def gates(out, args):
    g = {"G0": not any(out["candidateFailures"].values())}
    if out["mode"] == "direct":
        if args.min_lower is not None:
            g["lower > %g" % args.min_lower] = out["score"]["lower"] > args.min_lower
        if args.min_score is not None:
            g["score >= %g" % args.min_score] = out["score"]["mean"] >= args.min_score - EPS
    elif args.min_delta is not None:
        g["delta >= %g" % args.min_delta] = out["delta"]["mean"] >= args.min_delta - EPS
    if args.max_changed is not None:
        if "changedMaps" not in out:
            raise Invalid("--max-changed needs --mirror in direct mode")
        g["changed < %g" % args.max_changed] = len(out["changedMaps"]) < args.max_changed * out["keptMaps"] - EPS
    return g


def fmt(iv):
    return "%.4f (95 %% CI %.4f..%.4f, SD %.3f)" % (iv["mean"], iv["lower"], iv["upper"], iv["sd"])


def report(out):
    for i in out["inputs"]:
        rev = i.get("revision") or {}
        print("input %s sha256 %s: %s vs %s, seeds %s+%s, turnMs %s, threads %s, commit %s%s"
              % (i["path"], i["sha256"][:12], i["botA"], i["botB"], i["seedBase"], i["maps"], i["turnMs"], i.get("threads"),
                 (rev.get("commit") or "?")[:10], " dirty" if rev.get("status") else ""))
        for side in ("A", "B"):
            if i.get("effective" + side):
                print("  %s plays %s" % (side, i["effective" + side]))
    print("maps %d, kept %d, dropped %d %s" % (out["maps"], out["keptMaps"], len(out["droppedMaps"]), out["droppedMaps"][:20]))
    if out["mode"] == "direct":
        print("S = " + fmt(out["score"]))
        print("candidate by seat: %s" % out["record"])
    else:
        print("candidate %s" % fmt(out["candidate"]))
        print("control   %s" % fmt(out["control"]))
        print("delta     %s" % fmt(out["delta"]))
        print("candidate by seat: %s" % out["candidateRecord"])
        print("control by seat:   %s" % out["controlRecord"])
    if "changedMaps" in out:
        n = len(out["changedMaps"])
        print("changed maps %d of %d (%.1f %%) %s" % (n, out["keptMaps"], 100.0 * n / out["keptMaps"], out["changedMaps"][:20]))
    print("candidate failures (games): %s %s" % (out["candidateFailures"], out["candidateFailureSeeds"][:20]))
    for name, ok in out["gates"].items():
        print("gate %-16s %s" % (name, "pass" if ok else "FAIL"))
    print("verdict: %s" % out["verdict"])


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="mode", required=True)
    d = sub.add_parser("direct")
    d.add_argument("report")
    d.add_argument("--mirror")
    d.add_argument("--min-lower", type=float)
    d.add_argument("--min-score", type=float)
    c = sub.add_parser("common")
    c.add_argument("candidate")
    c.add_argument("control")
    c.add_argument("--min-delta", type=float)
    for p in (d, c):
        p.add_argument("--max-changed", type=float)
        p.add_argument("--out")
    args = ap.parse_args(argv)
    try:
        if args.mode == "direct":
            out = direct(load(args.report), load(args.mirror) if args.mirror else None)
        else:
            out = common(load(args.candidate), load(args.control))
        out["gates"] = gates(out, args)
    except Invalid as e:
        print("invalid: %s" % e)
        return 2
    out["policy"] = "P2"
    out["verdict"] = "pass" if all(out["gates"].values()) else "fail"
    report(out)
    if args.out:
        with open(args.out, "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, indent=1)
            f.write("\n")
    return 0 if out["verdict"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
