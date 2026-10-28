package cr.core;

import java.util.Arrays;

/**
 * Full referee state. Sites keep referee obstacle order (id = index); creeps keep the referee
 * order "player 0 creeps, then player 1 creeps", each in insertion order. Coordinates are doubles
 * because they are fractional inside a turn; they are integers between turns.
 */
public final class GameState {
    public static final int MAX_SITES = 24, MAX_CREEPS = 400;

    // League switches (referee Leagues object).
    public int level = 4;
    public boolean mines = true, towers = true, giants = true;
    public int fixedIncome = -1;

    /** Number of resolved turns. */
    public int turn;
    public boolean over;
    /** Referee score: queen health, -1 after a kill (invalid output / timeout). */
    public final int[] score = new int[2];
    public final boolean[] active = new boolean[2];

    public int nSites;
    public final double[] sx = new double[MAX_SITES], sy = new double[MAX_SITES];
    public final int[] sr = new int[MAX_SITES];
    public final double[] sArea = new double[MAX_SITES];
    public final int[] sGold = new int[MAX_SITES], sMaxMine = new int[MAX_SITES];
    public final int[] stType = new int[MAX_SITES], stOwner = new int[MAX_SITES];
    public final int[] mineRate = new int[MAX_SITES];
    public final int[] towerHp = new int[MAX_SITES], towerRange = new int[MAX_SITES];
    public final int[] barrType = new int[MAX_SITES], barrProgress = new int[MAX_SITES];
    public final boolean[] barrTraining = new boolean[MAX_SITES];

    public final double[] qx = new double[2], qy = new double[2];
    public final int[] hp = new int[2], gold = new int[2], goldPerTurn = new int[2];

    public int nCreeps;
    public final double[] cx = new double[MAX_CREEPS], cy = new double[MAX_CREEPS];
    public final int[] cOwner = new int[MAX_CREEPS], cType = new int[MAX_CREEPS], cHp = new int[MAX_CREEPS];

    public void setLevel(int level) {
        this.level = level;
        mines = towers = giants = true;
        fixedIncome = -1;
        if (level == 1) { mines = false; towers = false; giants = false; fixedIncome = Const.WOOD_FIXED_INCOME; }
        if (level == 2) { mines = false; fixedIncome = Const.WOOD_FIXED_INCOME; }
    }

    public void clearStructure(int i) {
        stType[i] = Const.NONE;
        stOwner[i] = -1;
        mineRate[i] = 0;
        towerHp[i] = 0;
        towerRange[i] = 0;
        barrType[i] = 0;
        barrProgress[i] = 0;
        barrTraining[i] = false;
    }

    /** Appends a creep at the end of its owner's block. Returns its index. */
    public int addCreep(int owner, int type, double x, double y) {
        if (nCreeps >= MAX_CREEPS) throw new IllegalStateException("too many creeps");
        int at = nCreeps;
        if (owner == 0) {
            at = 0;
            while (at < nCreeps && cOwner[at] == 0) at++;
            int n = nCreeps - at;
            System.arraycopy(cx, at, cx, at + 1, n);
            System.arraycopy(cy, at, cy, at + 1, n);
            System.arraycopy(cOwner, at, cOwner, at + 1, n);
            System.arraycopy(cType, at, cType, at + 1, n);
            System.arraycopy(cHp, at, cHp, at + 1, n);
        }
        cx[at] = x;
        cy[at] = y;
        cOwner[at] = owner;
        cType[at] = type;
        cHp[at] = Const.CREEP_HP[type];
        nCreeps++;
        return at;
    }

    /** Removes creeps with zero health, preserving order. */
    public void removeDeadCreeps() {
        int w = 0;
        for (int r = 0; r < nCreeps; r++) {
            if (cHp[r] == 0) continue;
            if (w != r) {
                cx[w] = cx[r];
                cy[w] = cy[r];
                cOwner[w] = cOwner[r];
                cType[w] = cType[r];
                cHp[w] = cHp[r];
            }
            w++;
        }
        nCreeps = w;
    }

    public GameState copy() {
        GameState c = new GameState();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(GameState o) {
        level = o.level; mines = o.mines; towers = o.towers; giants = o.giants; fixedIncome = o.fixedIncome;
        turn = o.turn; over = o.over;
        System.arraycopy(o.score, 0, score, 0, 2);
        System.arraycopy(o.active, 0, active, 0, 2);
        nSites = o.nSites;
        int n = nSites;
        System.arraycopy(o.sx, 0, sx, 0, n);
        System.arraycopy(o.sy, 0, sy, 0, n);
        System.arraycopy(o.sr, 0, sr, 0, n);
        System.arraycopy(o.sArea, 0, sArea, 0, n);
        System.arraycopy(o.sGold, 0, sGold, 0, n);
        System.arraycopy(o.sMaxMine, 0, sMaxMine, 0, n);
        System.arraycopy(o.stType, 0, stType, 0, n);
        System.arraycopy(o.stOwner, 0, stOwner, 0, n);
        System.arraycopy(o.mineRate, 0, mineRate, 0, n);
        System.arraycopy(o.towerHp, 0, towerHp, 0, n);
        System.arraycopy(o.towerRange, 0, towerRange, 0, n);
        System.arraycopy(o.barrType, 0, barrType, 0, n);
        System.arraycopy(o.barrProgress, 0, barrProgress, 0, n);
        System.arraycopy(o.barrTraining, 0, barrTraining, 0, n);
        System.arraycopy(o.qx, 0, qx, 0, 2);
        System.arraycopy(o.qy, 0, qy, 0, 2);
        System.arraycopy(o.hp, 0, hp, 0, 2);
        System.arraycopy(o.gold, 0, gold, 0, 2);
        System.arraycopy(o.goldPerTurn, 0, goldPerTurn, 0, 2);
        nCreeps = o.nCreeps;
        int m = nCreeps;
        System.arraycopy(o.cx, 0, cx, 0, m);
        System.arraycopy(o.cy, 0, cy, 0, m);
        System.arraycopy(o.cOwner, 0, cOwner, 0, m);
        System.arraycopy(o.cType, 0, cType, 0, m);
        System.arraycopy(o.cHp, 0, cHp, 0, m);
    }

    /** -1 while running or on a draw, otherwise the winning player. */
    public int winner() {
        if (!over || score[0] == score[1]) return -1;
        return score[0] > score[1] ? 0 : 1;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("turn=").append(turn).append(" over=").append(over)
            .append(" hp=").append(Arrays.toString(hp)).append(" gold=").append(Arrays.toString(gold)).append('\n');
        for (int p = 0; p < 2; p++) b.append("Q").append(p).append(' ').append(qx[p]).append(',').append(qy[p]).append('\n');
        for (int i = 0; i < nSites; i++) {
            b.append("S").append(i).append(' ').append(sx[i]).append(',').append(sy[i]).append(" r").append(sr[i])
                .append(" g").append(sGold[i]).append(" m").append(sMaxMine[i]).append(" t").append(stType[i])
                .append(" o").append(stOwner[i]).append(" rate").append(mineRate[i]).append(" thp").append(towerHp[i])
                .append(" trg").append(towerRange[i]).append(" bt").append(barrType[i]).append(" bp").append(barrProgress[i])
                .append(barrTraining[i] ? " T" : "").append('\n');
        }
        for (int i = 0; i < nCreeps; i++) {
            b.append("C").append(i).append(" o").append(cOwner[i]).append(" t").append(cType[i]).append(' ')
                .append(cx[i]).append(',').append(cy[i]).append(" hp").append(cHp[i]).append('\n');
        }
        return b.toString();
    }
}
