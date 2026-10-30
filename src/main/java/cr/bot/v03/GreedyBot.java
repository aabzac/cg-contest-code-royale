package cr.bot.v03;

import cr.bot.Bot;
import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Physics;

/**
 * v03 (frozen copy of current at 5889248+, early towers: INCOME_TARGET=3, MAX_TOWERS=8 in its snapshot): greedy baseline:
 * one knight barracks, mines up to an income target, then towers near home;
 * flees from close enemy knights towards its towers. Trains whenever affordable.
 */
public final class GreedyBot implements Bot {
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

    public GreedyBot(Params p) { this.p = p; }

    @Override public String name() { return "v03"; }

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
        int inc = 0, bar = 0, tow = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stOwner[i] != 0) continue;
            if (s.stType[i] == Const.MINE) inc += s.mineRate[i];
            if (s.stType[i] == Const.BARRACKS) bar++;
            if (s.stType[i] == Const.TOWER) tow++;
        }
        final int income = inc, barracks = bar, towers = tow;
        double qx = s.qx[0], qy = s.qy[0];
        // Home corner: the side we started on (queen 0 starts top-left or bottom-right).
        boolean left = homeLeft(s);
        double hx = left ? 0 : Const.WIDTH, hy = left ? 0 : Const.HEIGHT;

        Command cmd = null;
        int threat = 0;
        for (int i = 0; i < s.nCreeps; i++)
            if (s.cOwner[i] == 1 && s.cType[i] == Const.KNIGHT && Physics.distSq(s.cx[i], s.cy[i], qx, qy) < 250.0 * 250) threat++;

        if (touched >= 0 && !underEnemyTower(s, touched)) {
            int t = touched;
            if (s.stType[t] == Const.MINE && s.stOwner[t] == 0 && s.mineRate[t] < s.sMaxMine[t] && threat == 0)
                cmd = Command.build(t, "MINE");
            else if (s.stType[t] == Const.TOWER && s.stOwner[t] == 0 && s.towerHp[t] < 700)
                cmd = Command.build(t, "TOWER");
            else if (free(s, t)) {
                if (barracks < p.BARRACKS) cmd = Command.build(t, "BARRACKS-KNIGHT");
                else if (income < p.INCOME_TARGET && s.sGold[t] > 0 && threat == 0) cmd = Command.build(t, "MINE");
                else if (towers < p.MAX_TOWERS) cmd = Command.build(t, "TOWER");
            }
        }
        if (cmd == null && threat > 0) {
            int tw = nearest(s, qx, qy, i -> s.stType[i] == Const.TOWER && s.stOwner[i] == 0);
            if (tw >= 0) cmd = Command.build(tw, "TOWER");
            else cmd = Command.move((int) hx, (int) hy);
        }
        if (cmd == null) {
            int target;
            if (barracks < p.BARRACKS || income < p.INCOME_TARGET || towers < p.MAX_TOWERS) {
                target = nearest(s, (qx + hx) / 2, (qy + hy) / 2,
                    i -> free(s, i) && !underEnemyTower(s, i) && (barracks < p.BARRACKS || income >= p.INCOME_TARGET || s.sGold[i] > 0));
            } else target = -1;
            if (target >= 0) {
                String what = barracks < p.BARRACKS ? "BARRACKS-KNIGHT" : income < p.INCOME_TARGET ? "MINE" : "TOWER";
                cmd = Command.build(target, what);
                if (p.UNSTICK == 1) cmd = unstick(s, cmd);
            } else {
                int tw = nearest(s, hx, hy, i -> s.stType[i] == Const.TOWER && s.stOwner[i] == 0);
                cmd = tw >= 0 ? Command.build(tw, "TOWER") : Command.move((int) hx, (int) hy);
            }
        }

        int gold = s.gold[0];
        int[] train = new int[s.nSites];
        int n = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] == Const.BARRACKS && s.stOwner[i] == 0 && !s.barrTraining[i] && gold >= Const.CREEP_COST[s.barrType[i]]) {
                gold -= Const.CREEP_COST[s.barrType[i]];
                train[n++] = i;
            }
        }
        return cmd.train(java.util.Arrays.copyOf(train, n));
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
