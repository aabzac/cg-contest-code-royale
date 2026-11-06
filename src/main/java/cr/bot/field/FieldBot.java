package cr.bot.field;

import cr.bot.Bot;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Physics;

import java.util.function.IntPredicate;

/**
 * A local opponent with the arena field's balance (study 2026-11-03 of the players who beat v06: a
 * mine first, the knight barracks around turn 10, three mines upgraded to income 8, four towers at about
 * 450-600 HP, giants against towers as an option). Not a copy of any player: a greedy ladder of its own, so that the
 * economy, the tower line and the answer to giants can be measured locally against something other
 * than our own mirror. Spec "field", parameters {@link FieldParams}. Not shipped.
 *
 * <p>Order of the rules each turn: the defence (a second tower near the queen under threat, as v06),
 * the touch rule (upgrade an own mine, top up an own tower below TOWER_HP, build what the plan wants on
 * a free site), the flight to the nearest own tower under threat, then the plan: mine, knight
 * barracks, tower, mines up to MINES, towers up to TOWERS, a giant barracks once the enemy holds
 * GIANT_TOWERS towers, then upgrades and new mines up to INCOME; idle: top up the tower nearest home.
 * Sites are taken nearest to the midpoint of the queen and her home corner, never under an enemy
 * tower. The walk is a straight BUILD; when the predicted step leaves her standing, a MOVE on the
 * heading that gets closest to the site. Training: giants first while the enemy has towers, then
 * knights, each wave within the live creep cap.
 */
public final class FieldBot implements Bot {
    private static final int PASSES = 5;
    private final FieldParams p;
    private final Physics ph = new Physics();
    private Boolean homeLeft;
    /** Giant trainings started so far (GIANTS_MAX). */
    private int giantsTrained;

    public FieldBot(FieldParams p) { this.p = p; }

    @Override public String name() { return "field"; }

    @Override public long queenSteps() { return ph.calls; }

    private static boolean free(GameState s, int i) { return s.stType[i] == Const.NONE; }

    private static boolean underEnemyTower(GameState s, int site) {
        for (int j = 0; j < s.nSites; j++) {
            if (s.stType[j] != Const.TOWER || s.stOwner[j] != 1) continue;
            if (Physics.distSq(s.sx[j], s.sy[j], s.sx[site], s.sy[site]) < (double) s.towerRange[j] * s.towerRange[j]) return true;
        }
        return false;
    }

    private static int nearest(GameState s, double x, double y, IntPredicate ok) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < s.nSites; i++) {
            if (!ok.test(i)) continue;
            double d = Physics.distSq(s.sx[i], s.sy[i], x, y);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    private static boolean touches(GameState s, int site, double x, double y) {
        return Physics.lessInt(Physics.distSq(s.sx[site], s.sy[site], x, y), s.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
    }

    @Override
    public Command play(GameState s, int touched, long deadlineNanos) {
        int income = 0, mines = 0, towers = 0, kBarracks = 0, gBarracks = 0, enemyTowers = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stOwner[i] == 1 && s.stType[i] == Const.TOWER) enemyTowers++;
            if (s.stOwner[i] != 0) continue;
            if (s.stType[i] == Const.MINE) { income += s.mineRate[i]; mines++; }
            else if (s.stType[i] == Const.TOWER) towers++;
            else if (s.stType[i] == Const.BARRACKS) { if (s.barrType[i] == Const.GIANT) gBarracks++; else kBarracks++; }
        }
        double qx = s.qx[0], qy = s.qy[0];
        if (homeLeft == null) homeLeft = qx < Const.WIDTH / 2.0;
        double hx = homeLeft ? 0 : Const.WIDTH, hy = homeLeft ? 0 : Const.HEIGHT;
        double cx = (qx + hx) / 2, cy = (qy + hy) / 2;
        int threat = 0;
        for (int i = 0; i < s.nCreeps; i++)
            if (s.cOwner[i] == 1 && s.cType[i] == Const.KNIGHT && Physics.distSq(s.cx[i], s.cy[i], qx, qy) < (double) p.THREAT_RANGE * p.THREAT_RANGE) threat++;
        final int fIncome = income, fMines = mines, fTowers = towers, fK = kBarracks, fG = gBarracks, fEnemyTowers = enemyTowers, fThreat = threat;

        Command cmd = null;
        if (threat > 0 && p.THREAT_TOWERS_NEAR > 0) {
            double r2 = (double) p.THREAT_RADIUS * p.THREAT_RADIUS;
            int near = 0;
            for (int i = 0; i < s.nSites; i++)
                if (s.stType[i] == Const.TOWER && s.stOwner[i] == 0 && Physics.distSq(s.sx[i], s.sy[i], qx, qy) < r2) near++;
            if (near < p.THREAT_TOWERS_NEAR) {
                int d = nearest(s, qx, qy, i -> free(s, i) && !underEnemyTower(s, i) && Physics.distSq(s.sx[i], s.sy[i], qx, qy) < r2);
                if (d >= 0) cmd = Command.build(d, "TOWER");
            }
        }
        if (cmd == null && p.KITE == 1) cmd = kite(s, qx, qy);
        if (cmd == null && touched >= 0 && !underEnemyTower(s, touched)) {
            int t = touched;
            if (s.stType[t] == Const.MINE && s.stOwner[t] == 0 && s.mineRate[t] < s.sMaxMine[t] && threat == 0) cmd = Command.build(t, "MINE");
            else if (s.stType[t] == Const.TOWER && s.stOwner[t] == 0 && s.towerHp[t] < p.TOWER_HP) cmd = Command.build(t, "TOWER");
            else if (free(s, t)) {
                String what = wantFree(s, t, fIncome, fMines, fTowers, fK, fG, fEnemyTowers, fThreat);
                if (what != null) cmd = Command.build(t, what);
            }
        }
        if (cmd == null && threat > 0) {
            int tw = nearest(s, qx, qy, i -> s.stType[i] == Const.TOWER && s.stOwner[i] == 0);
            cmd = tw >= 0 ? Command.build(tw, "TOWER") : Command.move((int) hx, (int) hy);
        }
        if (cmd == null) {
            IntPredicate ok = i -> free(s, i) && !underEnemyTower(s, i);
            IntPredicate gold = i -> ok.test(i) && s.sGold[i] > 0;
            int target = -1;
            String what = null;
            if (mines < p.MINES_FIRST && (target = nearest(s, cx, cy, gold)) >= 0) what = "MINE";
            else if (kBarracks < 1 && (target = nearest(s, cx, cy, ok)) >= 0) what = "BARRACKS-KNIGHT";
            else if (towers < 1 && (target = nearest(s, cx, cy, ok)) >= 0) what = "TOWER";
            else if (mines < p.MINES && (target = nearest(s, cx, cy, gold)) >= 0) what = "MINE";
            else if (towers < p.TOWERS && (target = nearest(s, cx, cy, ok)) >= 0) what = "TOWER";
            else if (wantsGiantBarracks(s, gBarracks, enemyTowers) && (target = nearest(s, cx, cy, ok)) >= 0) what = "BARRACKS-GIANT";
            else if (income < p.INCOME && (target = nearest(s, cx, cy, i -> !underEnemyTower(s, i)
                && (free(s, i) ? s.sGold[i] > 0 : s.stType[i] == Const.MINE && s.stOwner[i] == 0 && s.mineRate[i] < s.sMaxMine[i]))) >= 0) what = "MINE";
            if (what != null) cmd = walk(s, Command.build(target, what));
            else {
                int tw = nearest(s, hx, hy, i -> s.stType[i] == Const.TOWER && s.stOwner[i] == 0);
                cmd = tw >= 0 ? Command.build(tw, "TOWER") : Command.move((int) hx, (int) hy);
            }
        }
        return cmd.train(train(s, enemyTowers));
    }

    /**
     * KITE=1, the kiting of v08 (MainBot.kite with KITE=2): with enemy knights within KITE_RANGE, the MOVE among 24
     * headings (200 ahead, the first one away from the board's centre) whose predicted step ends within an own
     * tower's range and outside every enemy tower's, scored by the distance to the nearest knight minus KITE_PULL
     * percent of the distance to the nearest own tower; taken when it beats standing by 5. Null otherwise.
     */
    private Command kite(GameState s, double qx, double qy) {
        double r2 = (double) p.KITE_RANGE * p.KITE_RANGE, d0 = Double.MAX_VALUE;
        int n = 0;
        for (int i = 0; i < s.nCreeps; i++) {
            if (s.cOwner[i] != 1 || s.cType[i] != Const.KNIGHT) continue;
            double d = Physics.distSq(s.cx[i], s.cy[i], qx, qy);
            if (d < r2) n++;
            if (d < d0) d0 = d;
        }
        if (n == 0) return null;
        double pull = p.KITE_PULL / 100.0;
        double a0 = Math.atan2(qy - Const.HEIGHT / 2.0, qx - Const.WIDTH / 2.0);
        Command best = null;
        double bestScore = Math.sqrt(d0) - pull * Math.sqrt(nearestOwnTowerSq(s, qx, qy)) + 5, bestTower = Double.MAX_VALUE;
        for (int k = 0; k < 24; k++) {
            double a = a0 + k * Math.PI / 12;
            Command m = Command.move((int) Math.max(0, Math.min(Const.WIDTH, Math.round(qx + 200 * Math.cos(a)))),
                (int) Math.max(0, Math.min(Const.HEIGHT, Math.round(qy + 200 * Math.sin(a)))));
            ph.queenStep(s, qx, qy, m.x, m.y, PASSES);
            double x = ph.rx, y = ph.ry;
            if (Physics.distSq(x, y, qx, qy) < 4) continue;
            if (underEnemyTowerAt(s, x, y) || !coveredByOwnTower(s, x, y)) continue;
            double near = Double.MAX_VALUE;
            for (int i = 0; i < s.nCreeps; i++)
                if (s.cOwner[i] == 1 && s.cType[i] == Const.KNIGHT) near = Math.min(near, Physics.distSq(s.cx[i], s.cy[i], x, y));
            double tower = nearestOwnTowerSq(s, x, y);
            double score = Math.sqrt(near) - pull * Math.sqrt(tower);
            if (score > bestScore || score == bestScore && tower < bestTower) { bestScore = score; bestTower = tower; best = m; }
        }
        return best;
    }

    private static double nearestOwnTowerSq(GameState s, double x, double y) {
        double tower = Double.MAX_VALUE;
        for (int i = 0; i < s.nSites; i++)
            if (s.stType[i] == Const.TOWER && s.stOwner[i] == 0) tower = Math.min(tower, Physics.distSq(s.sx[i], s.sy[i], x, y));
        return tower == Double.MAX_VALUE ? 0 : tower;
    }

    private static boolean coveredByOwnTower(GameState s, double x, double y) {
        for (int i = 0; i < s.nSites; i++)
            if (s.stType[i] == Const.TOWER && s.stOwner[i] == 0 && Physics.distSq(s.sx[i], s.sy[i], x, y) < (double) s.towerRange[i] * s.towerRange[i]) return true;
        return false;
    }

    private static boolean underEnemyTowerAt(GameState s, double x, double y) {
        for (int j = 0; j < s.nSites; j++)
            if (s.stType[j] == Const.TOWER && s.stOwner[j] == 1 && Physics.distSq(s.sx[j], s.sy[j], x, y) < (double) s.towerRange[j] * s.towerRange[j]) return true;
        return false;
    }

    /** A giant barracks is wanted: GIANTS=1, none stands, the enemy holds GIANT_TOWERS towers, from GIANT_TURN on, and GIANTS_MAX not reached. */
    private boolean wantsGiantBarracks(GameState s, int gBarracks, int enemyTowers) {
        return p.GIANTS == 1 && gBarracks < 1 && enemyTowers >= p.GIANT_TOWERS && s.turn >= p.GIANT_TURN && (p.GIANTS_MAX == 0 || giantsTrained < p.GIANTS_MAX);
    }

    /** What the plan builds on the free site t the queen touches, or null when it wants nothing there. */
    private String wantFree(GameState s, int t, int income, int mines, int towers, int kBarracks, int gBarracks, int enemyTowers, int threat) {
        boolean gold = s.sGold[t] > 0;
        if (mines < p.MINES_FIRST && gold && threat == 0) return "MINE";
        if (kBarracks < 1) return "BARRACKS-KNIGHT";
        if (towers < 1) return "TOWER";
        if (mines < p.MINES && gold && threat == 0) return "MINE";
        if (towers < p.TOWERS) return "TOWER";
        if (wantsGiantBarracks(s, gBarracks, enemyTowers)) return "BARRACKS-GIANT";
        if (income < p.INCOME && gold && threat == 0) return "MINE";
        return null;
    }

    /** The BUILD walk, or a MOVE around whatever leaves the queen standing on the straight line. */
    private Command walk(GameState s, Command build) {
        int t = build.site;
        double qx = s.qx[0], qy = s.qy[0];
        if (touches(s, t, qx, qy)) return build;
        ph.queenStep(s, qx, qy, s.sx[t], s.sy[t], PASSES);
        if (Physics.distSq(ph.rx, ph.ry, qx, qy) >= 4 || touches(s, t, ph.rx, ph.ry)) return build;
        double a0 = Math.atan2(s.sy[t] - qy, s.sx[t] - qx);
        Command best = build;
        double bd = Double.MAX_VALUE;
        for (int k = 1; k <= 5; k++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                double a = a0 + sign * k * Math.PI / 6;
                int mx = (int) Math.max(0, Math.min(Const.WIDTH, Math.round(qx + 200 * Math.cos(a))));
                int my = (int) Math.max(0, Math.min(Const.HEIGHT, Math.round(qy + 200 * Math.sin(a))));
                ph.queenStep(s, qx, qy, mx, my, PASSES);
                if (Physics.distSq(ph.rx, ph.ry, qx, qy) < 4) continue;
                double d = Physics.distSq(ph.rx, ph.ry, s.sx[t], s.sy[t]);
                if (d < bd) { bd = d; best = Command.move(mx, my); }
            }
        }
        return best;
    }

    /**
     * Giants first while the enemy has towers, then knights, in id order, each wave within the creep
     * cap; while an idle giant barracks waits for its 140 gold, the knights wait too (otherwise the
     * cheaper wave would spend the gold every time).
     */
    private boolean giantsLeft() { return p.GIANTS_MAX == 0 || giantsTrained < p.GIANTS_MAX; }

    private int[] train(GameState s, int enemyTowers) {
        int gold = s.gold[0], units = 0;
        for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == 0) units++;
        boolean saveForGiant = false;
        for (int i = 0; i < s.nSites; i++)
            if (s.stType[i] == Const.BARRACKS && s.stOwner[i] == 0 && !s.barrTraining[i] && s.barrType[i] == Const.GIANT && enemyTowers > 0 && gold < Const.CREEP_COST[Const.GIANT] && giantsLeft()) saveForGiant = true;
        int[] train = new int[s.nSites];
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            int type = pass == 0 ? Const.GIANT : Const.KNIGHT;
            if (type == Const.KNIGHT && saveForGiant) break;
            for (int i = 0; i < s.nSites; i++) {
                if (s.stType[i] != Const.BARRACKS || s.stOwner[i] != 0 || s.barrTraining[i] || s.barrType[i] != type) continue;
                if (type == Const.GIANT && (enemyTowers == 0 || !giantsLeft())) continue;
                int c = Const.CREEP_COST[type], u = Const.CREEP_COUNT[type];
                if (gold < c || units + u > Const.CREEP_LIMIT) continue;
                gold -= c;
                units += u;
                train[n++] = i;
                if (type == Const.GIANT) giantsTrained++;
            }
        }
        return java.util.Arrays.copyOf(train, n);
    }
}
