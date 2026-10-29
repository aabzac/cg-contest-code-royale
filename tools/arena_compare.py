"""Compares arena uploads within one league, as policy P1 judges them (.ai/project.md, docs/arena-uploads.md).

Usage: python -I tools/arena_compare.py [registry.json] [--candidate LABEL --selected LABEL] [--out report.json]
  registry.json (default docs/data/arena-uploads.json) lists the uploads: label, upload number, agent,
  league, artifact (sha256, build, config), placement and the games manifest written by
  tools/arena_games.py. No requests are made. Exit code 2 when the registry is inconsistent.

The registry is checked first: one config id per label (the bot and its params, cr.submit.Build.config;
builds that differ only in the runner share it), unique labels/uploads and agents (numbers), each
manifest loading and belonging to its entry's agent, the league one of wood3 ... legend (case and
spaces aside), and the recorded markers: none other than the entry's build, and with a build, at least
one game carrying it. A game without a marker (no turn-0 answer) is listed, not refused; manifests
written before the marker field are not checked.

Per upload: the placement score (first snapshot at 100 %), the record by CG ranks, the median of the
opponents' scores at fetch time and the performance rating: the opponent score at which the upload
would score 50 %, a logistic fit of every result against the opponent's score, with a scale shared by
all uploads of the league (profile likelihood; 2.0 until the league has 60 games, or when the fit is
not identifiable) and the standard error from the Fisher information, which treats the scale as known.
Opponent scores are those at download time, not at game time. An upload whose placement ended in a
promotion ("promotedTo") has no placement score in the old league: its games still count for the
ratings, its score for no mean.

Per label: the mean placement score and its SD between uploads. The noise margin M is twice the SD
pooled over every label with two or more placement scores in the league. Shared opponents (by pseudo)
give a stratified head-to-head per pair of uploads, weights 1 / (1/n_a + 1/n_b): context only.

With --candidate and --selected, the P1 verdict: a candidate promoted during a placement is kept and the
baseline is rebuilt in the new league; roll back when the candidate's mean placement score is below the
selected label's by more than M and its pooled performance rating below by more than two standard
errors of the difference; with one candidate score, roll back early when it is below the selected mean
by more than 2 M, otherwise upload the candidate again; otherwise there is no clear sign that the
candidate is worse (which does not show that it is not).
"""
import json
import math
import statistics
import sys

DEFAULT_SCALE = 2.0


class RegistryError(ValueError):
    pass


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def points(game):
    me, opp = game["ranks"][game["seat"]], game["ranks"][1 - game["seat"]]
    return 1.0 if me < opp else 0.0 if me > opp else 0.5


def results(manifest):
    """(points, opponent score at fetch) per game with a known opponent score."""
    return [(points(g), g["opponent"]["ratingAtFetch"]) for g in manifest["games"]
            if isinstance(g["opponent"].get("ratingAtFetch"), (int, float))]


def fit_rating(games, scale):
    """MLE of r in P(win) = 1 / (1 + exp(-(r - s) / scale)); returns (r, se, log-likelihood)."""
    if not games:
        return None, None, 0.0
    low, high = min(s for _, s in games) - 15, max(s for _, s in games) + 15
    r = statistics.median(s for _, s in games)
    for _ in range(200):
        p = [1 / (1 + math.exp(-(r - s) / scale)) for _, s in games]
        gradient = sum(y - q for (y, _), q in zip(games, p)) / scale
        curvature = sum(q * (1 - q) for q in p) / scale ** 2
        step = max(-3.0, min(3.0, gradient / curvature)) if curvature > 1e-12 else math.copysign(3.0, gradient or 1)
        r = max(low, min(high, r + step))
        if abs(step) < 1e-9:
            break
    p = [1 / (1 + math.exp(-(r - s) / scale)) for _, s in games]
    info = sum(q * (1 - q) for q in p) / scale ** 2
    loglik = sum(y * math.log(max(q, 1e-12)) + (1 - y) * math.log(max(1 - q, 1e-12)) for (y, _), q in zip(games, p))
    return r, (1 / math.sqrt(info) if info > 1e-12 else None), loglik


def fit_scale(groups):
    """The logistic scale shared by the groups (profile likelihood, golden section on a log scale); (scale, fitted)."""
    groups = [g for g in groups if len(g) >= 10]
    # Each group's rating absorbs a scale change when all its opponents share one score: no information.
    if sum(len(g) for g in groups) < 60 or all(len({s for _, s in g}) == 1 for g in groups):
        return DEFAULT_SCALE, False

    def loglik(log_scale):
        return sum(fit_rating(g, math.exp(log_scale))[2] for g in groups)

    a, b = math.log(0.3), math.log(20.0)
    ratio = (math.sqrt(5) - 1) / 2
    c, d = b - ratio * (b - a), a + ratio * (b - a)
    fc, fd = loglik(c), loglik(d)
    for _ in range(60):
        if fc > fd:
            b, d, fd = d, c, fc
            c = b - ratio * (b - a)
            fc = loglik(c)
        else:
            a, c, fc = c, d, fd
            d = a + ratio * (b - a)
            fd = loglik(d)
    best = math.exp((a + b) / 2)
    if best < 0.31 or best > 19.5:
        return DEFAULT_SCALE, False
    return best, True


def record(manifest):
    pts = [points(g) for g in manifest["games"]]
    n = len(pts)
    wins, draws = sum(1 for p in pts if p == 1.0), sum(1 for p in pts if p == 0.5)
    rate = sum(pts) / n if n else None
    out = {"games": n, "wins": wins, "draws": draws, "losses": n - wins - draws, "rate": rate,
           "rateSe": math.sqrt(rate * (1 - rate) / n) if n else None}
    for seat in (0, 1):
        part = [points(g) for g in manifest["games"] if g["seat"] == seat]
        out["seat%d" % seat] = "%d of %d" % (sum(1 for p in part if p == 1.0), len(part))
    return out


def shared(a, b):
    """Head-to-head on shared opponents (by pseudo), stratified by opponent with weights 1 / (1/n_a + 1/n_b)."""
    def by_pseudo(manifest):
        table = {}
        for g in manifest["games"]:
            table.setdefault(g["opponent"]["pseudo"], []).append(points(g))
        return table

    ta, tb = by_pseudo(a), by_pseudo(b)
    common = sorted(set(ta) & set(tb) - {None})
    if not common:
        return {"opponents": 0}
    w = {u: 1 / (1 / len(ta[u]) + 1 / len(tb[u])) for u in common}
    total = sum(w.values())
    delta = sum(w[u] * (sum(ta[u]) / len(ta[u]) - sum(tb[u]) / len(tb[u])) for u in common) / total
    ga, gb = sum(len(ta[u]) for u in common), sum(len(tb[u]) for u in common)
    return {"opponents": len(common), "gamesA": ga, "gamesB": gb,
            "rateA": sum(sum(ta[u]) for u in common) / ga, "rateB": sum(sum(tb[u]) for u in common) / gb,
            "delta": delta, "deltaSe": 0.5 / math.sqrt(total)}


def name(u):
    return "%s/upload-%s" % (u.get("label"), u.get("upload"))


def score(u):
    """The upload's placement score, or None (not complete, or ended in a promotion)."""
    s = (u.get("placement") or {}).get("score")
    return s if isinstance(s, (int, float)) and not u.get("promotedTo") else None


LEAGUES = ("wood3", "wood2", "wood1", "bronze", "silver", "gold", "legend")


def league_of(u):
    """The entry's league in lower case, or None unless it is one of LEAGUES (spaces aside)."""
    lg = u.get("league")
    lg = lg.strip().lower() if isinstance(lg, str) else None
    return lg if lg in LEAGUES else None


def load_manifests(uploads):
    """name(u) -> manifest for every entry whose games file loads, and the problems of those that do not."""
    manifests, problems = {}, []
    for u in uploads:
        if not u.get("games"):
            continue
        try:
            manifests[name(u)] = load(u["games"])
        except (OSError, ValueError) as e:
            problems.append("%s: games manifest %s does not load: %s" % (name(u), u["games"], e))
    return manifests, problems


def validate(registry, manifests):
    """The registry's problems (empty when consistent); manifests maps name(u) to the loaded manifest."""
    problems, seen, agents, configs = [], set(), {}, {}
    for u in registry["uploads"]:
        n = name(u)
        for key in ("label", "games"):
            if u.get(key) in (None, ""):
                problems.append("%s: no %s" % (n, key))
        for key in ("upload", "agent"):
            if not isinstance(u.get(key), int) or isinstance(u.get(key), bool):
                problems.append("%s: %s must be a number, not %r" % (n, key, u.get(key)))
        if league_of(u) is None:
            problems.append("%s: league %r is none of %s" % (n, u.get("league"), ", ".join(LEAGUES)))
        config = (u.get("artifact") or {}).get("config")
        if not config:
            problems.append("%s: no artifact.config" % n)
        if n in seen:
            problems.append("%s: listed twice" % n)
        seen.add(n)
        if u.get("agent") in agents:
            problems.append("%s: agent %s is also %s" % (n, u.get("agent"), agents[u.get("agent")]))
        agents[u.get("agent")] = n
        if config:
            first = configs.setdefault(u.get("label"), (config, n))
            if first[0] != config:
                problems.append("%s: config %s differs from %s's %s (one label, one configuration)" % (n, config, first[1], first[0]))
        m = manifests.get(n)
        if m is None:
            continue
        if m.get("agentId") != u.get("agent"):
            problems.append("%s: its games manifest is of agent %s, not %s" % (n, m.get("agentId"), u.get("agent")))
        build = (u.get("artifact") or {}).get("build")
        # A game without a marker is one the agent did not answer on turn 0 (or a build before the marker).
        recorded = [g["marker"] for g in m["games"] if "marker" in g]
        wrong = sorted({mk for mk in recorded if mk is not None and mk != build})
        if wrong:
            problems.append("%s: games carry marker(s) %s, the entry's build is %s" % (n, wrong, build))
        if build and recorded and build not in recorded:
            problems.append("%s: no game carries the entry's build %s" % (n, build))
    return problems


def compare(registry, candidate=None, selected=None):
    manifests, problems = load_manifests(registry["uploads"])
    problems += validate(registry, manifests)
    uploads = [dict(u, league=league_of(u)) for u in registry["uploads"]]
    if problems:
        raise RegistryError("registry problems:\n- " + "\n- ".join(problems))
    report = {"leagues": {}}
    for league in sorted({u["league"] for u in uploads}):
        members = [u for u in uploads if u["league"] == league]
        scale, fitted = fit_scale([results(manifests[name(u)]) for u in members])
        rows = []
        for u in members:
            m = manifests[name(u)]
            games = results(m)
            rating, se, _ = fit_rating(games, scale)
            known = sorted(s for _, s in games)
            rows.append({"upload": name(u), "agent": u["agent"], "placement": score(u), "promotedTo": u.get("promotedTo"),
                         "rank": (u.get("placement") or {}).get("rank"), "config": u["artifact"]["config"],
                         "build": u["artifact"].get("build"),
                         "noMarker": [g["gameId"] for g in m["games"] if "marker" in g and g["marker"] is None]
                         if u["artifact"].get("build") else [], **record(m),
                         "opponentScoreMedian": statistics.median(known) if known else None, "ratedGames": len(games),
                         "performance": rating, "performanceSe": se})
        labels = {}
        for label in sorted({u["label"] for u in members}):
            mine = [u for u in members if u["label"] == label]
            scores = [score(u) for u in mine if score(u) is not None]
            games = [r for u in mine for r in results(manifests[name(u)])]
            rating, se, _ = fit_rating(games, scale)
            labels[label] = {"uploads": len(mine), "config": mine[0]["artifact"]["config"], "placementScores": scores,
                             "promoted": [{"upload": name(u), "to": u["promotedTo"]} for u in mine if u.get("promotedTo")],
                             "pending": [name(u) for u in mine if score(u) is None and not u.get("promotedTo")],
                             "mean": statistics.mean(scores) if scores else None,
                             "sd": statistics.stdev(scores) if len(scores) > 1 else None,
                             "performance": rating, "performanceSe": se, "games": len(games)}
        scored = [v for v in labels.values() if len(v["placementScores"]) > 1]
        df = sum(len(v["placementScores"]) - 1 for v in scored)
        pooled = math.sqrt(sum((len(v["placementScores"]) - 1) * v["sd"] ** 2 for v in scored) / df) if df else None
        pairs = []
        for i, a in enumerate(members):
            for b in members[i + 1:]:
                pairs.append({"a": name(a), "b": name(b), **shared(manifests[name(a)], manifests[name(b)])})
        entry = {"scale": scale, "scaleFitted": fitted, "uploads": rows, "labels": labels,
                 "noise": {"pooledSd": pooled, "df": df, "M": 2 * pooled if pooled is not None else None},
                 "sharedOpponents": pairs}
        if candidate and selected:
            entry["verdict"] = verdict(labels, members, entry["noise"]["M"], candidate, selected)
        report["leagues"][league] = entry
    return report


def verdict(labels, members, margin, candidate, selected):
    c, s = labels.get(candidate), labels.get(selected)
    if c is not None and c.get("promoted"):
        p = c["promoted"][0]
        return {"decision": "promoted: keep the candidate",
                "why": "%s was promoted to %s during its placement; rebuild the baseline there" % (p["upload"], p["to"])}
    if c is None or s is None:
        return {"decision": "no data", "why": "both labels need uploads in this league"}
    if c.get("pending"):
        return {"decision": "pending: wait for the placement", "why": "%s has no placement score yet" % ", ".join(c["pending"])}
    if len(s["placementScores"]) < 2:
        return {"decision": "no baseline", "why": "%s has %d placement score(s) here; it needs two" % (selected, len(s["placementScores"]))}
    if margin is None:
        return {"decision": "no noise margin", "why": "no label has two placement scores here"}
    if not c["placementScores"]:
        return {"decision": "no data", "why": "%s has no placement score here yet" % candidate}
    out = {"meanDiff": c["mean"] - s["mean"], "M": margin}
    if len(c["placementScores"]) == 1:
        first = c["placementScores"][0]
        early = first < s["mean"] - 2 * margin
        out.update(decision="roll back early" if early else "upload the candidate again",
                   why="upload score %.2f vs the selected mean %.2f, 2M = %.2f" % (first, s["mean"], 2 * margin))
        return out
    if c["performanceSe"] is None or s["performanceSe"] is None:
        out.update(decision="undecided: no performance rating", why="the second condition cannot be evaluated")
        return out
    se = math.sqrt(c["performanceSe"] ** 2 + s["performanceSe"] ** 2)
    perf = c["performance"] - s["performance"]
    worse_mean, worse_perf = out["meanDiff"] < -margin, perf < -2 * se
    out.update(perfDiff=perf, perfDiffSe=se,
               decision="roll back" if worse_mean and worse_perf else "not clearly worse",
               why="mean %+.2f vs -M = %.2f; performance %+.2f vs -2 SE = %.2f" % (out["meanDiff"], -margin, perf, -2 * se))
    return out


def fmt(value, digits=2):
    return "-" if value is None else "%.*f" % (digits, value)


def print_report(report):
    for league, e in report["leagues"].items():
        print("League %s: logistic scale %.2f (%s); scores compare within a league only." %
              (league, e["scale"], "fitted" if e["scaleFitted"] else "default"))
        print("%-16s %-8s %-16s %-6s %-12s %-8s %-8s %-10s %-14s" %
              ("upload", "agent", "placement", "games", "win %", "seat0", "seat1", "opp.median", "performance"))
        for r in e["uploads"]:
            place = ("promoted to %s" % r["promotedTo"]) if r["promotedTo"] else fmt(r["placement"]) + (" #%d" % r["rank"] if r.get("rank") else "")
            print("%-16s %-8d %-16s %-6d %-12s %-8s %-8s %-10s %-14s" % (
                r["upload"], r["agent"], place, r["games"],
                "%s +- %s" % (fmt(100 * r["rate"], 1), fmt(100 * r["rateSe"], 1)) if r["games"] else "-", r["seat0"], r["seat1"],
                fmt(r["opponentScoreMedian"]), "%s +- %s" % (fmt(r["performance"]), fmt(r["performanceSe"]))))
        for r in e["uploads"]:
            if r["noMarker"]:
                print("%s: %d game(s) without the marker (no turn-0 answer?): %s" % (r["upload"], len(r["noMarker"]), r["noMarker"]))
        for label, v in e["labels"].items():
            print("label %s (config %s): %d upload(s), placement %s, mean %s, SD %s; performance %s +- %s (%d games)" % (
                label, v["config"], v["uploads"], " ".join(fmt(x) for x in v["placementScores"]) or "-", fmt(v["mean"]),
                fmt(v["sd"]), fmt(v["performance"]), fmt(v["performanceSe"]), v["games"]))
        n = e["noise"]
        print("noise: pooled SD %s (%d df), M = 2 SD = %s" % (fmt(n["pooledSd"]), n["df"], fmt(n["M"])))
        for p in e["sharedOpponents"]:
            if p["opponents"]:
                print("shared opponents %s vs %s: %d players, %d / %d games, %s vs %s %%, stratified %+.1f +- %.1f pp (context)" % (
                    p["a"], p["b"], p["opponents"], p["gamesA"], p["gamesB"], fmt(100 * p["rateA"], 1),
                    fmt(100 * p["rateB"], 1), 100 * p["delta"], 100 * p["deltaSe"]))
            else:
                print("shared opponents %s vs %s: none" % (p["a"], p["b"]))
        if "verdict" in e:
            print("verdict: %s (%s)" % (e["verdict"]["decision"], e["verdict"]["why"]))


def main(argv):
    args, opts = [], {}
    it = iter(argv)
    for a in it:
        if a in ("--candidate", "--selected", "--out"):
            opts[a[2:]] = next(it)
        else:
            args.append(a)
    registry = load(args[0] if args else "docs/data/arena-uploads.json")
    try:
        report = compare(registry, opts.get("candidate"), opts.get("selected"))
    except RegistryError as e:
        print(e)
        sys.exit(2)
    print_report(report)
    if "out" in opts:
        with open(opts["out"], "w", encoding="utf-8", newline="\n") as f:
            json.dump(report, f, indent=1)
            f.write("\n")


if __name__ == "__main__":
    main(sys.argv[1:])
