package cr.bot.current;

import cr.bot.Bot;
import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Physics;

/**
 * Greedy baseline: MINES_FIRST mines, a knight barracks (BARRACKS of them, the later ones once
 * BARRACKS_AFTER_TOWERS towers stand), mines (and with UPGRADE=1 upgrades) up to
 * an income target, then towers near home; tops up a touched tower below TOWER_HP; flees from close
 * enemy knights towards its towers. Trains whenever affordable. The walk to the next site is
 * UNSTICK's detour (and APPROACH's last step) or, with ROUTE=1, {@link RoutePlanner}.
 */
public final class MainBot implements Bot {
    /** Collision passes of a turn without spawns, one per creep sub-step (Rules.processCreeps). */
    private static final int PASSES = 5;
    /** A predicted step shorter than this counts as standing still (creeps make a stuck queen wobble by a unit). */
    private static final double MIN_STEP = 2;
    /** UNSTICK's plans: up to this many detour moves, and this many turns until the queen touches the site. */
    private static final int MAX_DETOUR = 6, HORIZON = 25;

    private final Params p;
    private final Physics ph = new Physics();
    /** UNSTICK's detour: the BUILD site it serves, the turn of its last move, its heading (radians) and the moves left. */
    private int detourSite = -1, detourTurn, detourLeft;
    private double detourHeading;
    private ApproachPlanner approach;
    private RoutePlanner route;

    public MainBot(Params p) { this.p = p; }

    @Override public String name() { return "current"; }

    @Override public long queenSteps() { return ph.calls + (approach == null ? 0 : approach.calls()) + (route == null ? 0 : route.calls()); }

    private static boolean free(GameState s, int i) { return s.stType[i] == Const.NONE; }

    private static boolean underEnemyTower(GameState s, int site) {
        for (int j = 0; j < s.nSites; j++) {
            if (s.stType[j] != Const.TOWER || s.stOwner[j] != 1) continue;
            if (Physics.distSq(s.sx[j], s.sy[j], s.sx[site], s.sy[site]) < (double) s.towerRange[j] * s.towerRange[j]) return true;
        }
        return false;
    }

    private static int nearest(GameState s, double x, double y, java.util.function.IntPredicate ok) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < s.nSites; i++) {
            if (!ok.test(i)) continue;
            double d = Physics.distSq(s.sx[i], s.sy[i], x, y);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    @Override
    public Command play(GameState s, int touched, long deadlineNanos) {
        int inc = 0, bar = 0, tow = 0, min = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stOwner[i] != 0) continue;
            if (s.stType[i] == Const.MINE) { inc += s.mineRate[i]; min++; }
            if (s.stType[i] == Const.BARRACKS) bar++;
            if (s.stType[i] == Const.TOWER) tow++;
        }
        final int income = inc, barracks = bar, towers = tow, mines = min;
        double qx = s.qx[0], qy = s.qy[0];
        // Home corner: the side we started on (queen 0 starts top-left or bottom-right).
        boolean left = homeLeft(s);
        double hx = left ? 0 : Const.WIDTH, hy = left ? 0 : Const.HEIGHT;

        Command cmd = null;
        int threat = 0;
        for (int i = 0; i < s.nCreeps; i++)
            if (s.cOwner[i] == 1 && s.cType[i] == Const.KNIGHT && Physics.distSq(s.cx[i], s.cy[i], qx, qy) < (double) p.THREAT_RANGE * p.THREAT_RANGE) threat++;

        if (threat > 0 && p.THREAT_TOWERS_NEAR > 0) {
            // Defence first: another tower near her beats topping up the one she touches.
            double r2 = (double) p.THREAT_RADIUS * p.THREAT_RADIUS;
            int near = 0;
            for (int i = 0; i < s.nSites; i++)
                if (s.stType[i] == Const.TOWER && s.stOwner[i] == 0 && Physics.distSq(s.sx[i], s.sy[i], qx, qy) < r2) near++;
            if (near < p.THREAT_TOWERS_NEAR) {
                int d = nearest(s, qx, qy, i -> free(s, i) && !underEnemyTower(s, i) && Physics.distSq(s.sx[i], s.sy[i], qx, qy) < r2);
                if (d >= 0) cmd = Command.build(d, "TOWER");
            }
        }
        if (cmd == null && touched >= 0 && !underEnemyTower(s, touched)) {
            int t = touched;
            if (s.stType[t] == Const.MINE && s.stOwner[t] == 0 && s.mineRate[t] < s.sMaxMine[t] && threat == 0)
                cmd = Command.build(t, "MINE");
            else if (s.stType[t] == Const.TOWER && s.stOwner[t] == 0 && s.towerHp[t] < p.TOWER_HP)
                cmd = Command.build(t, "TOWER");
            else if (free(s, t)) {
                if (mines < p.MINES_FIRST && s.sGold[t] > 0 && threat == 0) cmd = Command.build(t, "MINE");
                else if (wantsBarracks(p, barracks, towers)) cmd = Command.build(t, "BARRACKS-KNIGHT");
                else if (income < p.INCOME_TARGET && s.sGold[t] > 0 && threat == 0) cmd = Command.build(t, "MINE");
                else if (towers < p.MAX_TOWERS) cmd = Command.build(t, "TOWER");
                else if (income < p.INCOME_LATE && s.sGold[t] > 0 && threat == 0) cmd = Command.build(t, "MINE");
            }
        }
        if (cmd == null && threat > 0) {
            int tw = nearest(s, qx, qy, i -> s.stType[i] == Const.TOWER && s.stOwner[i] == 0);
            if (tw >= 0) cmd = Command.build(tw, "TOWER");
            else cmd = Command.move((int) hx, (int) hy);
        }
        if (cmd == null) {
            int target = nextSite(s, (qx + hx) / 2, (qy + hy) / 2, hx, hy, p, mines, barracks, income, towers, -1);
            if (target >= 0) {
                String what = !free(s, target) || mines < p.MINES_FIRST && s.sGold[target] > 0 ? "MINE"
                    : wantsBarracks(p, barracks, towers) ? "BARRACKS-KNIGHT" : income < p.INCOME_TARGET ? "MINE" : "TOWER";
                cmd = Command.build(target, what);
                if (p.ROUTE == 1) {
                    if (route == null) route = new RoutePlanner();
                    cmd = route.plan(s, cmd, hx, hy, p, barracks, income, towers);
                } else {
                    if (p.UNSTICK == 1) cmd = unstick(s, cmd);
                    if (p.APPROACH == 1 && threat == 0 && cmd.queen == Command.BUILD) {
                        if (approach == null) approach = new ApproachPlanner();
                        cmd = approach.improve(s, cmd, hx, hy, p);
                    }
                }
            } else {
                int late = income < p.INCOME_LATE && threat == 0 ? lateSite(s, (qx + hx) / 2, (qy + hy) / 2) : -1;
                if (late >= 0) {
                    // The ladder is complete: the idle queen raises the income instead of pumping her home tower.
                    cmd = Command.build(late, "MINE");
                    if (p.ROUTE == 1) {
                        if (route == null) route = new RoutePlanner();
                        cmd = route.plan(s, cmd, hx, hy, p, barracks, income, towers);
                    } else if (p.UNSTICK == 1) cmd = unstick(s, cmd);
                } else {
                    int tw = nearest(s, hx, hy, i -> s.stType[i] == Const.TOWER && s.stOwner[i] == 0);
                    cmd = tw >= 0 ? Command.build(tw, "TOWER") : Command.move((int) hx, (int) hy);
                }
            }
        }

        return cmd.train(train(s));
    }

    /**
     * TRAIN: own idle barracks in id order while the gold lasts, each wave kept within the live cap
     * of {@link Const#CREEP_LIMIT} creeps alive plus requested (the referee refuses the whole line
     * otherwise). With TRAIN_SYNC=1 nothing is trained while any own barracks trains or the gold
     * does not cover every own barracks, so that all of them start together.
     */
    int[] train(GameState s) {
        int gold = s.gold[0], units = 0, idle = 0, cost = 0;
        for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == 0) units++;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] != Const.BARRACKS || s.stOwner[i] != 0) continue;
            if (s.barrTraining[i]) idle = Integer.MIN_VALUE;
            else { idle++; cost += Const.CREEP_COST[s.barrType[i]]; }
        }
        if (p.TRAIN_SYNC == 1 && (idle <= 0 || gold < cost)) return new int[0];
        int[] train = new int[s.nSites];
        int n = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] != Const.BARRACKS || s.stOwner[i] != 0 || s.barrTraining[i]) continue;
            int c = Const.CREEP_COST[s.barrType[i]], u = Const.CREEP_COUNT[s.barrType[i]];
            if (gold < c || units + u > Const.CREEP_LIMIT) continue;
            gold -= c;
            units += u;
            train[n++] = i;
        }
        return java.util.Arrays.copyOf(train, n);
    }

    /** INCOME_LATE's target: the nearest to (cx, cy) of a free gold site not under an enemy tower and an own mine below its maximum, or -1. */
    static int lateSite(GameState s, double cx, double cy) {
        return nearest(s, cx, cy, i -> !underEnemyTower(s, i)
            && (free(s, i) ? s.sGold[i] > 0 : s.stType[i] == Const.MINE && s.stOwner[i] == 0 && s.mineRate[i] < s.sMaxMine[i]));
    }

    /**
     * Whether the ladder wants a knight barracks next: fewer than BARRACKS stand, and the second and
     * later ones wait until BARRACKS_AFTER_TOWERS own towers stand.
     */
    static boolean wantsBarracks(Params p, int barracks, int towers) {
        return barracks < p.BARRACKS && (barracks == 0 || towers >= p.BARRACKS_AFTER_TOWERS);
    }

    /**
     * The ladder's next site to take, the nearest to (cx, cy) (the midpoint of the queen and home),
     * or -1 when the ladder is complete; site skip is left out. Counts are the bot's own structures.
     * (hx, hy) is the home corner, for the forward second barracks (BARRACKS_FORWARD).
     * A free gold site while fewer than MINES_FIRST mines stand; otherwise, until the barracks, the
     * income target and the tower cap are met, a free site, which needs gold in the mine phase (the
     * barracks built, the income below its target), where with UPGRADE=1 an own mine below its
     * maximum is a target too. Sites under an enemy tower are never targets.
     */
    static int nextSite(GameState s, double cx, double cy, double hx, double hy, Params p, int mines, int barracks, int income, int towers, int skip) {
        if (mines < p.MINES_FIRST) {
            int t = nearest(s, cx, cy, i -> i != skip && free(s, i) && !underEnemyTower(s, i) && s.sGold[i] > 0);
            if (t >= 0) return t;
        }
        boolean wantBarracks = wantsBarracks(p, barracks, towers);
        if (!wantBarracks && income >= p.INCOME_TARGET && towers >= p.MAX_TOWERS) return -1;
        if (wantBarracks && barracks >= 1 && p.BARRACKS_FORWARD > 0) {
            // The second barracks forward: the free site nearest to the point BARRACKS_FORWARD % of the way from home to the enemy's corner.
            double f = p.BARRACKS_FORWARD / 100.0, fx = hx + f * (Const.WIDTH - 2 * hx), fy = hy + f * (Const.HEIGHT - 2 * hy);
            int t = nearest(s, fx, fy, i -> i != skip && free(s, i) && !underEnemyTower(s, i));
            if (t >= 0) return t;
        }
        boolean minePhase = !wantBarracks && income < p.INCOME_TARGET;
        return nearest(s, cx, cy, i -> i != skip && !underEnemyTower(s, i)
            && (free(s, i) ? !minePhase || s.sGold[i] > 0
                : p.UPGRADE == 1 && minePhase && s.stType[i] == Const.MINE && s.stOwner[i] == 0 && s.mineRate[i] < s.sMaxMine[i]));
    }

    /**
     * UNSTICK, for the BUILD towards the next site to take. Such a BUILD walks the queen straight at
     * the site's centre. When that step would leave her standing (a site straight ahead pushes her
     * back, or two sites wedge her; v01 stood up to 43 turns), she detours: MOVEs on one heading, then
     * the straight walk. The plan is predicted with the sites only ({@link Physics#queenStep}):
     * headings turn from the straight line in 15-degree steps, with 1 to {@link #MAX_DETOUR} moves,
     * and the plan that touches the site in the fewest turns wins (ties: the smaller turn, +15 degrees
     * before -15, fewer moves). Its moves go on over the next turns while they move her and the BUILD
     * site stays the same. Fleeing to a tower is left alone: there she may stand.
     */
    private Command unstick(GameState s, Command build) {
        int t = build.site;
        double qx = s.qx[0], qy = s.qy[0];
        if (detourSite == t && detourTurn == s.turn - 1 && detourLeft > 0) {
            Command m = heading(qx, qy, detourHeading);
            if (steps(s, qx, qy, m.x, m.y)) {
                detourTurn = s.turn;
                detourLeft--;
                return m;
            }
        }
        detourSite = -1;
        if (touches(s, t, qx, qy) || advances(s, qx, qy, t)) return build;
        double a0 = Math.atan2(s.sy[t] - qy, s.sx[t] - qx);
        int bestTurns = Integer.MAX_VALUE, bestMoves = 0;
        double bestHeading = 0;
        for (int k = 1; k <= 12; k++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                double a = a0 + sign * k * Math.PI / 12, x = qx, y = qy;
                for (int n = 1; n <= MAX_DETOUR && n < bestTurns; n++) {
                    Command m = heading(x, y, a);
                    if (!steps(s, x, y, m.x, m.y)) break;
                    x = ph.rx;
                    y = ph.ry;
                    int walk = walkTurns(s, x, y, t, Math.min(HORIZON, bestTurns - 1) - n);
                    if (walk >= 0 && n + walk < bestTurns) {
                        bestTurns = n + walk;
                        bestMoves = n;
                        bestHeading = a;
                    }
                }
            }
        }
        if (bestTurns == Integer.MAX_VALUE) return build;
        detourSite = t;
        detourTurn = s.turn;
        detourHeading = bestHeading;
        detourLeft = bestMoves - 1;
        return heading(qx, qy, bestHeading);
    }

    private static boolean touches(GameState s, int site, double x, double y) {
        return Physics.lessInt(Physics.distSq(s.sx[site], s.sy[site], x, y), s.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
    }

    /** Whether walking from (x,y) towards (tx,ty) moves the queen at least {@link #MIN_STEP}; she ends in ph.rx, ph.ry. */
    private boolean steps(GameState s, double x, double y, double tx, double ty) {
        ph.queenStep(s, x, y, tx, ty, PASSES);
        return Physics.distSq(ph.rx, ph.ry, x, y) >= MIN_STEP * MIN_STEP;
    }

    /** Whether the straight step from (x,y) towards the site makes her touch it or moves her; she ends in ph.rx, ph.ry. */
    private boolean advances(GameState s, double x, double y, int site) {
        return steps(s, x, y, s.sx[site], s.sy[site]) || touches(s, site, ph.rx, ph.ry);
    }

    /** Turns the straight walk from (x,y) needs to touch the site, or -1 if she stands still first or needs more than max. */
    private int walkTurns(GameState s, double x, double y, int site, int max) {
        for (int n = 0; n <= max; n++) {
            if (touches(s, site, x, y)) return n;
            if (!advances(s, x, y, site)) return -1;
            x = ph.rx;
            y = ph.ry;
        }
        return -1;
    }

    /** A MOVE 200 ahead on the heading, kept on the board. */
    private static Command heading(double x, double y, double a) {
        return Command.move((int) Math.max(0, Math.min(Const.WIDTH, Math.round(x + 200 * Math.cos(a)))),
            (int) Math.max(0, Math.min(Const.HEIGHT, Math.round(y + 200 * Math.sin(a)))));
    }

    private Boolean homeLeft;

    private boolean homeLeft(GameState s) {
        if (homeLeft == null) homeLeft = s.qx[0] < Const.WIDTH / 2.0;
        return homeLeft;
    }
}
