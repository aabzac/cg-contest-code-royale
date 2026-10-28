package cr.core;


/** Referee → bot input (Player.kt printObstacleInit/printObstaclePerTurn, Referee.sendGameStates). */
public final class Protocol {
    private Protocol() {}

    public static String initInput(GameState s) {
        StringBuilder b = new StringBuilder();
        b.append(s.nSites).append('\n');
        for (int i = 0; i < s.nSites; i++)
            b.append(i).append(' ').append(Math.round(s.sx[i])).append(' ').append(Math.round(s.sy[i])).append(' ').append(s.sr[i]).append('\n');
        return b.toString();
    }

    public static int touchedSite(GameState s, int p) {
        int found = -1;
        for (int i = 0; i < s.nSites; i++) {
            if (Physics.lessInt(Physics.distSq(s.sx[i], s.sy[i], s.qx[p], s.qy[p]), s.sr[i] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA)) {
                if (found >= 0) return -1; // singleOrNull
                found = i;
            }
        }
        return found;
    }

    private static int rel(int owner, int p) { return owner < 0 ? -1 : (owner == p ? 0 : 1); }

    public static String turnInput(GameState s, int p) {
        StringBuilder b = new StringBuilder();
        b.append(s.gold[p]).append(' ').append(touchedSite(s, p)).append('\n');
        for (int i = 0; i < s.nSites; i++) {
            int t = s.stType[i];
            boolean visible = (t != Const.NONE && s.stOwner[i] == p)
                || Physics.lessInt(Physics.distSq(s.sx[i], s.sy[i], s.qx[p], s.qy[p]), Const.QUEEN_VISION);
            b.append(i).append(' ').append(visible ? s.sGold[i] : -1).append(' ').append(visible ? s.sMaxMine[i] : -1).append(' ');
            switch (t) {
                case Const.MINE: b.append("0 ").append(rel(s.stOwner[i], p)).append(' ').append(visible ? s.mineRate[i] : -1).append(" -1"); break;
                case Const.TOWER: b.append("1 ").append(rel(s.stOwner[i], p)).append(' ').append(s.towerHp[i]).append(' ').append(s.towerRange[i]); break;
                case Const.BARRACKS:
                    b.append("2 ").append(rel(s.stOwner[i], p)).append(' ')
                        .append(s.barrTraining[i] ? Const.CREEP_BUILD_TIME[s.barrType[i]] - s.barrProgress[i] : 0).append(' ').append(s.barrType[i]);
                    break;
                default: b.append("-1 -1 -1 -1");
            }
            b.append('\n');
        }
        int units = 0;
        for (int q = 0; q < 2; q++) if (s.active[q]) {
            for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == q) units++;
            units++;
        }
        b.append(units).append('\n');
        for (int q = 0; q < 2; q++) {
            if (!s.active[q]) continue;
            for (int i = 0; i < s.nCreeps; i++) {
                if (s.cOwner[i] != q) continue;
                b.append(Math.round(s.cx[i])).append(' ').append(Math.round(s.cy[i])).append(' ').append(q == p ? 0 : 1)
                    .append(' ').append(s.cType[i]).append(' ').append(s.cHp[i]).append('\n');
            }
            b.append(Math.round(s.qx[q])).append(' ').append(Math.round(s.qy[q])).append(' ').append(q == p ? 0 : 1)
                .append(" -1 ").append(s.hp[q]).append('\n');
        }
        return b.toString();
    }
}
