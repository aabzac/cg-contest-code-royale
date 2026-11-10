package cr.bot.v14;

import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Physics;

/**
 * ROUTE: the walk to the next site to take (A), planned with the sites only ({@link Physics#queenStep})
 * and replanned every turn. The straight BUILD walk is predicted first. It is clean when it takes
 * no more turns than the straight bound (no slide along a site), she never stands still, no turn
 * ends inside an enemy tower's range or on a free site the touch rule would build at, and she
 * lands touching A alone. A clean walk whose landing is at most one turn from the next target B
 * ({@link ApproachPlanner#nextTarget}) stands. Otherwise the search runs: a fan of 24 headings
 * (15-degree steps either side of the straight line) with 1 to {@link #MAX_MOVES} MOVEs of 60 and
 * then the straight walk; and, for the straight start and every prefix, the landing point: the ring
 * point of A nearest to B, clamped to the arc she can reach in the same number of turns, walked to
 * with MOVEs (the final step aims at the exact point, so she stops on it without a push) and checked
 * with the step prediction. A turn ending on a free site the touch rule would build at costs one
 * turn more (the build), a stall or an enemy tower's range disqualifies the plan. Own towers and
 * mines are not avoided: a touch tops them up or upgrades them as in v03. Plans are compared by
 * the turns until she touches A, then the turns from the landing to B, then the previous turn's
 * heading (hysteresis), then the fixed search order. The search spends at most {@link #MAX_CALLS}
 * step predictions and stops with the best complete plan found so far, so it is deterministic and
 * needs no clock. Near units (APPROACH's quiet-step guards) and when the enemy queen can contest A,
 * only the obstacle part runs. When on, it replaces UNSTICK and APPROACH.
 */
final class RoutePlanner {
    static final int PASSES = 5, HORIZON = 25, MAX_MOVES = 3, MAX_CALLS = 3000;
    static final double MIN_STEP = 2;
    /** Turns a plan's landing to B counts when the straight walk from there does not touch B. */
    static final int FAR = HORIZON + 1;
    /** A MOVE landing stops the queen at r+33 from A's centre: no push (that starts below r+30), touching (below r+35). */
    static final int LANDING_RING = Const.QUEEN_RADIUS + 3;
    static final int STRAIGHT = 0, HEADING = 1, LANDING = 2;

    private final Physics ph = new Physics();
    private Params p;
    private int barracks, income, towers;
    private long budgetEnd;
    /** Results of the last walk: the landing, the turns ending on a diverting site, whether she lands touching A alone. */
    private double lx, ly;
    private int lastDiverts;
    private boolean lastOnly;
    /** The best plan of this turn. */
    private int bestKind, bestTurns, bestNext, bestX, bestY;
    private double bestHeading;
    /** The previous turn's plan, for hysteresis. */
    private int prevSite = -1, prevTurn = -1;
    private double prevHeading = Double.NaN;

    /** Step predictions made so far (diagnostics). */
    long calls() { return ph.calls; }

    /**
     * The queen's command for the BUILD towards site A she does not touch: the BUILD itself (the
     * straight walk), or the first MOVE of a better plan. Counts are the bot's own structures.
     */
    Command plan(GameState s, Command build, double hx, double hy, Params params, int barracks, int income, int towers) {
        int a = build.site;
        double qx = s.qx[0], qy = s.qy[0];
        if (build.queen != Command.BUILD || a < 0 || touches(s, a, qx, qy)) {
            prevSite = -1;
            return build;
        }
        p = params;
        this.barracks = barracks;
        this.income = income;
        this.towers = towers;
        budgetEnd = ph.calls + MAX_CALLS;
        boolean remember = prevSite == a && prevTurn == s.turn - 1;
        double hysteresis = remember ? prevHeading : Double.NaN;

        // The straight walk, the default and the first candidate. It is clean when it loses no turn
        // to a collision (the straight bound floor((d - (r+35)) / 60) + 1), diverts nowhere and
        // lands touching A alone.
        int t0 = walk(s, a, qx, qy, s.sx[a], s.sy[a], HORIZON);
        double d = Math.sqrt(Physics.distSq(qx, qy, s.sx[a], s.sy[a])) - (s.sr[a] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
        int bound = d < 0 ? 0 : (int) Math.floor(d / Const.QUEEN_SPEED) + 1;
        boolean clean = t0 >= 0 && t0 <= bound && lastDiverts == 0 && lastOnly;
        int b = -1, next0 = 0;
        if (t0 >= 0 && ApproachPlanner.quietStep(s, qx, qy) && !contested(s, a)) {
            b = ApproachPlanner.nextTarget(s, a, build.structure, lx, ly, hx, hy, p);
            if (b >= 0) next0 = nextTurns(s, b, lx, ly, HORIZON);
        }
        if (clean && next0 <= 1) {
            prevSite = -1;
            return build;
        }
        bestKind = STRAIGHT;
        bestTurns = t0 < 0 ? FAR : t0;
        bestNext = next0;
        bestHeading = Double.NaN;
        if (t0 >= 0 && b >= 0) landing(s, a, b, build.structure, qx, qy, t0, 0, Double.NaN, hx, hy, hysteresis);

        double a0 = Math.atan2(s.sy[a] - qy, s.sx[a] - qx);
        search:
        for (int k = 1; k <= 12; k++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                double heading = a0 + sign * k * Math.PI / 12, x = qx, y = qy;
                int diverts = 0;
                for (int n = 1; n <= MAX_MOVES && n + diverts <= bestTurns; n++) {
                    if (ph.calls >= budgetEnd) break search;
                    Command m = heading(x, y, heading);
                    ph.queenStep(s, x, y, m.x, m.y, PASSES);
                    double nx = ph.rx, ny = ph.ry;
                    if (Physics.distSq(x, y, nx, ny) < MIN_STEP * MIN_STEP || !safeSegment(s, x, y, nx, ny)) break;
                    x = nx;
                    y = ny;
                    if (touches(s, a, x, y)) {
                        // The move itself lands her on A.
                        if (touchesOnly(s, a, x, y)) consider(s, a, b, build.structure, HEADING, n + diverts, x, y, m.x, m.y, heading, hx, hy, hysteresis);
                        break;
                    }
                    if (divertsAt(s, a, x, y)) diverts++;
                    int t = walk(s, a, x, y, s.sx[a], s.sy[a], bestTurns - n - diverts);
                    if (t < 0) continue;
                    int turns = n + diverts + t;
                    if (lastOnly && lastDiverts == 0) consider(s, a, b, build.structure, HEADING, turns, lx, ly, m.x, m.y, heading, hx, hy, hysteresis);
                    if (b >= 0 && lastDiverts == 0) landing(s, a, b, build.structure, x, y, t, n + diverts, heading, hx, hy, hysteresis, m.x, m.y);
                }
            }
        }
        prevSite = a;
        prevTurn = s.turn;
        prevHeading = bestHeading;
        if (bestKind == STRAIGHT) return build;
        return Command.move(bestX, bestY);
    }

    private void landing(GameState s, int a, int b, String structure, double x, double y, int k, int prefix, double heading,
                         double hx, double hy, double hysteresis) {
        landing(s, a, b, structure, x, y, k, prefix, heading, hx, hy, hysteresis, 0, 0);
    }

    /**
     * The landing candidate from (x, y) after prefix moves: the ring point nearest B that she can reach
     * in k turns, verified by walking to it. The plan's first command is the prefix's MOVE (mx, my)
     * or, without a prefix, the MOVE to the point itself.
     */
    private void landing(GameState s, int a, int b, String structure, double x, double y, int k, int prefix, double heading,
                         double hx, double hy, double hysteresis, int mx, int my) {
        if (k < 1 || prefix + k > bestTurns) return;
        double rho = s.sr[a] + LANDING_RING;
        double ux = x - s.sx[a], uy = y - s.sy[a], d = Math.sqrt(ux * ux + uy * uy);
        if (d < rho) return;
        ux /= d;
        uy /= d;
        double reach = Const.QUEEN_SPEED * (double) k;
        double cosMax = (d * d + rho * rho - reach * reach) / (2 * d * rho);
        if (cosMax > 1) return;
        double phiMax = cosMax <= -1 ? Math.PI : Math.acos(cosMax);
        double bx = s.sx[b] - s.sx[a], by = s.sy[b] - s.sy[a];
        double phi = Math.atan2(ux * by - uy * bx, ux * bx + uy * by);
        phi = Math.max(-phiMax, Math.min(phiMax, phi));
        double c = Math.cos(phi), sn = Math.sin(phi);
        int px = (int) Math.round(s.sx[a] + rho * (ux * c - uy * sn)), py = (int) Math.round(s.sy[a] + rho * (uy * c + ux * sn));
        if (!inside(px, py)) return;
        int t = walk(s, a, x, y, px, py, k);
        if (t < 0 || lastDiverts > 0 || !lastOnly) return;
        if (prefix == 0) { mx = px; my = py; }
        consider(s, a, b, structure, prefix == 0 ? LANDING : HEADING, prefix + t, lx, ly, mx, my, heading, hx, hy, hysteresis);
    }

    /** Scores a complete plan by its turns, then the turns from its landing to B, then hysteresis, and keeps it if better. */
    private void consider(GameState s, int a, int b, String structure, int kind, int turns, double landX, double landY, int mx, int my,
                          double heading, double hx, double hy, double hysteresis) {
        if (turns > bestTurns) return;
        int next = 0;
        if (b >= 0) {
            if (turns == bestTurns && bestNext == 0) return;
            if (ApproachPlanner.nextTarget(s, a, structure, landX, landY, hx, hy, p) != b) return;
            next = nextTurns(s, b, landX, landY, turns < bestTurns ? HORIZON : bestNext - 1);
            if (next < 0) {
                if (turns == bestTurns) return;
                next = FAR;
            }
        }
        if (turns == bestTurns && next == bestNext && !(!Double.isNaN(hysteresis) && heading == hysteresis && bestHeading != hysteresis)) return;
        bestKind = kind;
        bestTurns = turns;
        bestNext = next;
        bestX = mx;
        bestY = my;
        bestHeading = heading;
    }

    /**
     * Walks from (x, y) towards (tx, ty) until she touches A: the turns, plus one for every turn that
     * ends on a site the touch rule would build at (left in lastDiverts). -1 when she stands still
     * first, a step crosses an enemy tower's range, the budget is spent, or she needs more than max
     * turns. The landing is left in lx, ly; lastOnly says whether she touches A alone there.
     */
    private int walk(GameState s, int a, double x, double y, double tx, double ty, int max) {
        lastDiverts = 0;
        for (int n = 0; ; n++) {
            if (touches(s, a, x, y)) {
                lx = x;
                ly = y;
                lastOnly = touchesOnly(s, a, x, y);
                return n + lastDiverts;
            }
            if (n + lastDiverts >= max || ph.calls >= budgetEnd) return -1;
            ph.queenStep(s, x, y, tx, ty, PASSES);
            double nx = ph.rx, ny = ph.ry;
            if (Physics.distSq(x, y, nx, ny) < MIN_STEP * MIN_STEP && !touches(s, a, nx, ny)) return -1;
            if (!safeSegment(s, x, y, nx, ny)) return -1;
            if (!touches(s, a, nx, ny) && divertsAt(s, a, nx, ny)) lastDiverts++;
            x = nx;
            y = ny;
        }
    }

    /** Turns of the straight walk from (x, y) until she touches B, at most max; -1 if she stalls, crosses an enemy tower's range or needs more. */
    private int nextTurns(GameState s, int b, double x, double y, int max) {
        for (int n = 0; ; n++) {
            if (touches(s, b, x, y)) return n;
            if (n >= max || ph.calls >= budgetEnd) return -1;
            ph.queenStep(s, x, y, s.sx[b], s.sy[b], PASSES);
            double nx = ph.rx, ny = ph.ry;
            if (Physics.distSq(x, y, nx, ny) < MIN_STEP * MIN_STEP || !safeSegment(s, x, y, nx, ny)) return -1;
            x = nx;
            y = ny;
        }
    }

    /** Whether a turn ending at (x, y) touches a site other than A at which the touch rule would build. */
    private boolean divertsAt(GameState s, int a, double x, double y) {
        for (int i = 0; i < s.nSites; i++) if (i != a && touches(s, i, x, y) && diverts(s, i)) return true;
        return false;
    }

    /**
     * Whether the touch rule of GreedyBot.play builds at a free site the queen touches with no knight
     * near, which abandons the walk to A. Own towers and mines are walked past as v03 does: their
     * top-up or upgrade on a touch stays with the touch rule and is not planned around (step 4 of the
     * route plan decides that policy).
     */
    private boolean diverts(GameState s, int i) {
        if (s.stType[i] != Const.NONE || ApproachPlanner.underEnemyTower(s, i)) return false;
        return GreedyBot.wantsBarracks(p, barracks, towers) || income < p.INCOME_TARGET && s.sGold[i] > 0 || towers < p.MAX_TOWERS;
    }

    /** Whether the enemy queen can reach A within two turns: the arrival turn then decides the build conflict, so the landing is left alone. */
    private static boolean contested(GameState s, int a) {
        double reach = s.sr[a] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA + 2 * Const.QUEEN_SPEED;
        return Physics.distSq(s.sx[a], s.sy[a], s.qx[1], s.qy[1]) < reach * reach;
    }

    static boolean touches(GameState s, int site, double x, double y) {
        return Physics.lessInt(Physics.distSq(x, y, s.sx[site], s.sy[site]), s.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
    }

    private static boolean touchesOnly(GameState s, int a, double x, double y) {
        return ApproachPlanner.touchesOnly(s, a, x, y);
    }

    private static boolean safeSegment(GameState s, double x, double y, double nx, double ny) {
        return ApproachPlanner.safeSegment(s, x, y, nx, ny);
    }

    private static boolean inside(double x, double y) {
        return x >= Const.QUEEN_RADIUS && y >= Const.QUEEN_RADIUS && x <= Const.WIDTH - Const.QUEEN_RADIUS && y <= Const.HEIGHT - Const.QUEEN_RADIUS;
    }

    /** A MOVE 200 ahead on the heading, kept on the board. */
    private static Command heading(double x, double y, double a) {
        return Command.move((int) Math.max(0, Math.min(Const.WIDTH, Math.round(x + 200 * Math.cos(a)))),
            (int) Math.max(0, Math.min(Const.HEIGHT, Math.round(y + 200 * Math.sin(a)))));
    }
}
