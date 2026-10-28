package cr.bot.v01;

import cr.bot.Bot;
import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Physics;

/**
 * v01 (frozen copy of current at b748311+): greedy baseline: one knight barracks, mines up to an income target, then towers near home;
 * flees from close enemy knights towards its towers. Trains whenever affordable.
 */
public final class GreedyBot implements Bot {
    private final Params p;

    public GreedyBot(Params p) { this.p = p; }

    @Override public String name() { return "v01"; }

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

    private Boolean homeLeft;

    private boolean homeLeft(GameState s) {
        if (homeLeft == null) homeLeft = s.qx[0] < Const.WIDTH / 2.0;
        return homeLeft;
    }
}
