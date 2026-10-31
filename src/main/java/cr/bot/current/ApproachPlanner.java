package cr.bot.current;

import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Physics;

/**
 * A final-step approach experiment. The selected construction site stays fixed and both
 * routes must touch it this turn. Among safe landing points, accept only a strict reduction
 * in predicted walking turns to the same next site. Predictions include static sites only;
 * nearby units and possible barracks spawns disable the experiment.
 */
final class ApproachPlanner {
    private static final int PASSES = 5, SAMPLES = 72, HORIZON = 25;
    private final Physics physics = new Physics();

    Command improve(GameState s, Command build, double hx, double hy, Params p) {
        if (build.queen != Command.BUILD || build.site < 0 || build.site >= s.nSites) return build;
        int a = build.site;
        double qx = s.qx[0], qy = s.qy[0];
        if (s.stType[a] != Const.NONE || touches(s, a, qx, qy) || !quietStep(s, qx, qy)) return build;
        double contestReach = s.sr[a] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA + 2 * Const.QUEEN_SPEED;
        if (Physics.distSq(s.sx[a], s.sy[a], s.qx[1], s.qy[1]) < contestReach * contestReach) return build;

        physics.queenStep(s, qx, qy, s.sx[a], s.sy[a], PASSES);
        double baseX = physics.rx, baseY = physics.ry;
        if (!touchesOnly(s, a, baseX, baseY) || !safeSegment(s, qx, qy, baseX, baseY)) return build;
        int b = nextTarget(s, a, build.structure, baseX, baseY, hx, hy, p);
        if (b < 0) return build;
        int bestTurns = travelTurns(s, b, baseX, baseY, HORIZON);
        if (bestTurns <= 1) return build;

        Command best = build;
        double radius = s.sr[a] + Const.QUEEN_RADIUS + 2;
        // The order is fixed; equal scores retain the original BUILD or the first improvement.
        for (int k = 0; k < SAMPLES; k++) {
            double angle = 2 * Math.PI * k / SAMPLES;
            int tx = (int) Math.round(s.sx[a] + radius * Math.cos(angle));
            int ty = (int) Math.round(s.sy[a] + radius * Math.sin(angle));
            if (!inside(tx, ty)) continue;
            physics.queenStep(s, qx, qy, tx, ty, PASSES);
            double x = physics.rx, y = physics.ry;
            if (!inside(x, y) || !touchesOnly(s, a, x, y) || !safeSegment(s, qx, qy, x, y)) continue;
            if (nextTarget(s, a, build.structure, x, y, hx, hy, p) != b) continue;
            int turns = travelTurns(s, b, x, y, bestTurns - 1);
            if (turns < 0) continue;
            bestTurns = turns;
            best = Command.move(tx, ty).train(build.train);
            if (bestTurns <= 1) break;
        }
        return best;
    }

    /** v03's next new-site choice after the current site's ordinary build/upgrade phase. */
    static int nextTarget(GameState s, int a, String structure, double x, double y,
                          double hx, double hy, Params p) {
        int income = 0, barracks = 0, towers = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stOwner[i] != 0) continue;
            if (s.stType[i] == Const.MINE) income += s.mineRate[i];
            else if (s.stType[i] == Const.BARRACKS) barracks++;
            else if (s.stType[i] == Const.TOWER) towers++;
        }
        if ("BARRACKS-KNIGHT".equals(structure)) barracks++;
        else if ("TOWER".equals(structure)) towers++;
        else if ("MINE".equals(structure)) {
            // A last-step target is within vision. Avoid an exhausted/unknown upgrade plan.
            int rate = s.sMaxMine[a];
            if (rate < 1 || rate > 5 || s.sGold[a] <= rate * (rate + 1) / 2) return -1;
            income += rate;
        } else return -1;
        if (barracks >= p.BARRACKS && income >= p.INCOME_TARGET && towers >= p.MAX_TOWERS) return -1;
        double cx = (x + hx) / 2, cy = (y + hy) / 2;
        int best = -1;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < s.nSites; i++) {
            if (i == a || s.stType[i] != Const.NONE || underEnemyTower(s, i)) continue;
            if (barracks >= p.BARRACKS && income < p.INCOME_TARGET && s.sGold[i] <= 0) continue;
            double d = Physics.distSq(cx, cy, s.sx[i], s.sy[i]);
            if (d < distance) { distance = d; best = i; }
        }
        return best;
    }

    /** Straight BUILD movement with static collisions; -1 means unsafe, stalled or too far. */
    int travelTurns(GameState s, int target, double x, double y, int max) {
        for (int turn = 0; turn <= max; turn++) {
            if (touches(s, target, x, y)) return turn;
            if (turn == max) break;
            physics.queenStep(s, x, y, s.sx[target], s.sy[target], PASSES);
            double nx = physics.rx, ny = physics.ry;
            if (!safeSegment(s, x, y, nx, ny) || Physics.distSq(x, y, nx, ny) < 4) return -1;
            x = nx; y = ny;
        }
        return -1;
    }

    static boolean touchesOnly(GameState s, int target, double x, double y) {
        if (!touches(s, target, x, y)) return false;
        for (int i = 0; i < s.nSites; i++) if (i != target && touches(s, i, x, y)) return false;
        return true;
    }

    private static boolean touches(GameState s, int site, double x, double y) {
        return Physics.lessInt(Physics.distSq(x, y, s.sx[site], s.sy[site]),
            s.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
    }

    private static boolean inside(double x, double y) {
        return x >= Const.QUEEN_RADIUS && y >= Const.QUEEN_RADIUS
            && x <= Const.WIDTH - Const.QUEEN_RADIUS && y <= Const.HEIGHT - Const.QUEEN_RADIUS;
    }

    private static boolean quietStep(GameState s, double x, double y) {
        // Includes friendly units: they can change the landing point through collisions.
        for (int i = 0; i < s.nCreeps; i++) {
            // A knight must not enter the 250-unit threat zone before next turn's BUILD.
            double reach = s.cOwner[i] == 1 && s.cType[i] == Const.KNIGHT ? 420 : 300;
            if (Physics.distSq(x, y, s.cx[i], s.cy[i]) < reach * reach) return false;
        }
        if (Physics.distSq(x, y, s.qx[1], s.qy[1]) < 200.0 * 200) return false;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] != Const.BARRACKS || !s.barrTraining[i]) continue;
            double reach = s.sr[i] + (s.stOwner[i] == 1 && s.barrType[i] == Const.KNIGHT ? 400 : 300);
            if (Physics.distSq(x, y, s.sx[i], s.sy[i]) < reach * reach) return false;
        }
        return true;
    }

    private static boolean underEnemyTower(GameState s, int site) {
        for (int i = 0; i < s.nSites; i++)
            if (s.stType[i] == Const.TOWER && s.stOwner[i] == 1
                && Physics.lessInt(Physics.distSq(s.sx[site], s.sy[site], s.sx[i], s.sy[i]), s.towerRange[i])) return true;
        return false;
    }

    private static boolean safeSegment(GameState s, double x, double y, double nx, double ny) {
        double dx = nx - x, dy = ny - y, length = dx * dx + dy * dy;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] != Const.TOWER || s.stOwner[i] != 1) continue;
            double t = length == 0 ? 0 : ((s.sx[i] - x) * dx + (s.sy[i] - y) * dy) / length;
            t = Math.max(0, Math.min(1, t));
            double margin = s.towerRange[i] + 2;
            if (Physics.distSq(s.sx[i], s.sy[i], x + t * dx, y + t * dy) <= margin * margin) return false;
        }
        return true;
    }
}
