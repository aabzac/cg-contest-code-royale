"""Noise of v01's three Bronze uploads beyond tools/arena_compare.py (2026-10-30, docs/arena-uploads.md).

Run from the repository root: python -B -I docs/data/noise-2026-10-30/noise.py > docs/data/noise-2026-10-30/noise.txt
Reads the registry docs/data/arena-uploads.json (the three uploads by agent id, so later entries change nothing) and their
games manifests; imports tools/arena_compare.py for the logistic rating, so the numbers use the same model as the P1 tool
(noise.txt was written with the tool as of the commit that adds this directory):
- the distinct opponents per upload;
- the shared logistic scale and its 95 % profile interval (separate ratings, likelihood within 1.92 of the maximum);
- each upload's rating and Fisher SE at the fitted scale and at 2.0, their SD, Cochran's Q, and a likelihood-ratio test
  of one rating for all games against one per upload with the scale fitted in each model (chi-square, 2 df);
- upload 3, the newest, against the pooled rating of uploads 1-2;
- the placement scores' SD and M = 2 SD with 95 % chi-square intervals (2 df: the quantiles are -2 ln(1 - p));
- the seats over all games: record, the opponents' median score, a two-proportion z, and a logistic regression with an
  intercept per upload, the opponent's score and seat 1 (and the game's order within its placement);
- the games where the referee removed a player (game score -1: a timeout or invalid output) with their frame counts (3
  frames: the game ended on turn 0), and every figure without them.
"""
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.join(os.getcwd(), "tools"))
import arena_compare as ac  # noqa: E402

REGISTRY = "docs/data/arena-uploads.json"
AGENTS = (6823071, 6823455, 6824680)  # v01 uploads 1-3, Bronze


def chi2_sf_2df(x):
    return math.exp(-x / 2)


def uploads():
    reg = {u["agent"]: u for u in ac.load(REGISTRY)["uploads"]}
    return [(ac.name(reg[a]), ac.score(reg[a]), ac.load(reg[a]["games"])) for a in AGENTS]


def removed(game):
    return isinstance(game.get("scores"), list) and -1 in game["scores"]


def groups_of(ups, keep):
    return [[(ac.points(g), g["opponent"]["ratingAtFetch"]) for g in m["games"] if keep(g)
             and isinstance(g["opponent"].get("ratingAtFetch"), (int, float))] for _, _, m in ups]


GRID = [0.3 + 0.005 * i for i in range(1941)]  # 0.3 .. 10.0


def heterogeneity(groups):
    scale, fitted = ac.fit_scale(groups)
    print("  logistic scale %.3f (%s), %d games" % (scale, "fitted" if fitted else "default", sum(map(len, groups))))

    def separate(s):
        return sum(ac.fit_rating(g, s)[2] for g in groups)

    def pooled(s):
        return ac.fit_rating([x for g in groups for x in g], s)[2]

    best = separate(scale)
    inside = [s for s in GRID if separate(s) >= best - 1.920729]
    print("  scale 95 %% profile interval %.2f-%.2f" % (min(inside), max(inside)))
    for s in (scale, 2.0):
        fits = [ac.fit_rating(g, s) for g in groups]
        w = [1 / f[1] ** 2 for f in fits]
        mean = sum(wi * f[0] for wi, f in zip(w, fits)) / sum(w)
        q = sum(wi * (f[0] - mean) ** 2 for wi, f in zip(w, fits))
        ratings = [f[0] for f in fits]
        print("  scale %.2f: ratings %s +- %s; SD %.2f = %.2f x the mean SE; Cochran Q %.2f (2 df), p %.4f" % (
            s, " ".join("%.2f" % r for r in ratings), " ".join("%.2f" % f[1] for f in fits),
            statistics.stdev(ratings), statistics.stdev(ratings) / statistics.mean(f[1] for f in fits), q, chi2_sf_2df(q)))
    lr = 2 * (max(separate(s) for s in GRID) - max(pooled(s) for s in GRID))
    print("  likelihood ratio, one rating vs one per upload (scale fitted in each model): %.2f (2 df), p %.4f" % (
        lr, chi2_sf_2df(lr)))
    r3, se3, _ = ac.fit_rating(groups[2], scale)
    r12, se12, _ = ac.fit_rating(groups[0] + groups[1], scale)
    print("  upload 3 %.2f +- %.2f vs uploads 1-2 pooled %.2f +- %.2f: z %.2f" % (
        r3, se3, r12, se12, (r3 - r12) / math.hypot(se3, se12)))


def solve(a, b):
    """x with a x = b (Gauss-Jordan with partial pivoting), and the inverse of a."""
    n = len(a)
    m = [row[:] + [b[i]] + [1.0 if j == i else 0.0 for j in range(n)] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        pivot = m[c][c]
        m[c] = [v / pivot for v in m[c]]
        for r in range(n):
            if r != c and m[r][c]:
                f = m[r][c]
                m[r] = [v - f * w for v, w in zip(m[r], m[c])]
    return [m[i][n] for i in range(n)], [m[i][n + 1:] for i in range(n)]


def logistic(rows, ys):
    """Maximum-likelihood logistic regression (Newton); returns coefficients and their SEs."""
    k = len(rows[0])
    beta = [0.0] * k
    for _ in range(100):
        grad, hess = [0.0] * k, [[0.0] * k for _ in range(k)]
        for x, y in zip(rows, ys):
            p = 1 / (1 + math.exp(-sum(b * v for b, v in zip(beta, x))))
            for i in range(k):
                grad[i] += (y - p) * x[i]
                for j in range(k):
                    hess[i][j] += p * (1 - p) * x[i] * x[j]
        step, cov = solve(hess, grad)
        beta = [b + s for b, s in zip(beta, step)]
        if max(abs(s) for s in step) < 1e-10:
            break
    return beta, [math.sqrt(cov[i][i]) for i in range(k)]


def seats(ups, keep):
    by_seat = {0: [], 1: []}
    rows, ys, rows_order = [], [], []
    for u, (_, _, m) in enumerate(ups):
        games = sorted((g for g in m["games"] if keep(g)), key=lambda g: g["gameId"])
        for i, g in enumerate(games):
            s = g["opponent"].get("ratingAtFetch")
            by_seat[g["seat"]].append((ac.points(g), s))
            if isinstance(s, (int, float)):
                base = [1.0 if v == u else 0.0 for v in range(len(ups))] + [s, float(g["seat"])]
                rows.append(base)
                rows_order.append(base + [i / max(1, len(games) - 1)])
                ys.append(ac.points(g))
    rate = {}
    for seat, games in by_seat.items():
        n, w = len(games), sum(p for p, _ in games)
        rate[seat] = (w, n)
        print("  seat %d: %g of %d (%.1f %%), opponents' median score %.2f" % (
            seat, w, n, 100 * w / n, statistics.median(s for _, s in games if isinstance(s, (int, float)))))
    (w0, n0), (w1, n1) = rate[0], rate[1]
    p0, p1 = w0 / n0, w1 / n1
    print("  two-proportion z %.2f (unpooled SE)" % ((p0 - p1) / math.sqrt(p0 * (1 - p0) / n0 + p1 * (1 - p1) / n1)))
    for label, design in (("upload intercepts + opponent score + seat 1", rows),
                          ("... + order within the placement", rows_order)):
        beta, se = logistic(design, ys)
        k = len(ups)
        # The rating model's logit is (rating - opponent score) / scale, so a rating shift d moves the logit by
        # -d * (opponent-score slope): seat 1's logit term c is a rating shift of -c / slope.
        print("  logistic, %s: seat 1 %.3f +- %.3f (z %.2f), opponent score %.3f per point -> seat 1 shifts the rating "
              "by %+.2f points" % (label, beta[k + 1], se[k + 1], beta[k + 1] / se[k + 1], beta[k], -beta[k + 1] / beta[k]))


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ups = uploads()
    scores = [s for _, s, _ in ups]
    sd = statistics.stdev(scores)
    df = len(scores) - 1
    assert df == 2, "the closed-form chi-square quantiles below are for 2 df"
    lo, hi = sd * math.sqrt(df / (-2 * math.log(0.025))), sd * math.sqrt(df / (-2 * math.log(0.975)))
    print("v01 in Bronze: %s" % ", ".join("%s %.2f" % (n, s) for n, s, _ in ups))
    print("distinct opponents: %s" % ", ".join("%s %d of %d games" % (
        n, len({g["opponent"]["agentId"] for g in m["games"]}), len(m["games"])) for n, _, m in ups))
    print("placement scores: mean %.2f, SD %.2f (%d df, 95 %% interval %.2f-%.2f), M = 2 SD = %.2f (%.2f-%.2f)" % (
        statistics.mean(scores), sd, df, lo, hi, 2 * sd, 2 * lo, 2 * hi))
    kills = [(n, g) for n, _, m in ups for g in m["games"] if removed(g)]
    print("games where the referee removed a player (score -1): %d" % len(kills))
    for n, g in kills:
        print("  %s game %d: agent on seat %d, scores %s, %d frames -> %s removed" % (
            n, g["gameId"], g["seat"], g["scores"], g["frames"], "the agent" if g["scores"][g["seat"]] == -1 else "the opponent"))
    for title, keep in (("all games", lambda g: True), ("without those games", lambda g: not removed(g))):
        print("== %s" % title)
        for n, _, m in ups:
            part = [ac.points(g) for g in m["games"] if keep(g)]
            print("  %s: %g of %d" % (n, sum(part), len(part)))
        heterogeneity(groups_of(ups, keep))
        seats(ups, keep)


if __name__ == "__main__":
    main()
