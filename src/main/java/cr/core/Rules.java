package cr.core;


/**
 * Port of Referee.gameTurn (csj/code-royale@349924a) for one simultaneous turn. Order of
 * evaluation, list orders and comparisons follow the Kotlin source; see docs/rules/README.md for
 * live differences. One instance per thread.
 */
public final class Rules {
    /**
     * Index the SDK passes to gameTurn for the first turn; decides who builds when both players
     * build on one site (scheduledBuildings.removeAt(turn % 2)). To be confirmed by replays.
     */
    public static int TURN_BASE = 1;
    /** Spawn offset (iter, iter) for both players as before csj/code-royale 8a63cd9. */
    public static boolean LEGACY_SPAWN = false;

    private final Physics ph = new Physics();
    private final int[] order = new int[GameState.MAX_CREEPS];
    private final int[] schedPlayer = new int[2], schedSite = new int[2];
    private final String[] schedType = new String[2];
    /** Diagnostics for the last resolve: warnings per player (referee game summary). */
    public final String[] lastWarning = new String[2];
    public final String[] lastKill = new String[2];
    /** Optional event log for replay inference (null = off). */
    public StringBuilder log;

    public void resolve(GameState s, Command c0, Command c1) {
        if (s.over) throw new IllegalStateException("game over");
        int turnIdx = s.turn + TURN_BASE;
        s.goldPerTurn[0] = s.goldPerTurn[1] = 0;
        lastWarning[0] = lastWarning[1] = lastKill[0] = lastKill[1] = null;
        if (log != null) log.setLength(0);

        Command[] cmds = {c0, c1};
        int nSched = 0, nAttempt = 0;
        int attempt0 = -1, attempt1 = -1;
        boolean killed = false;
        for (int p = 0; p < 2; p++) {
            if (!s.active[p]) continue;
            Command c = cmds[p];
            if (c.kill) { kill(s, p, c.error); killed = true; continue; }

            String w = train(s, p, c.train);
            if (w != null) lastWarning[p] = w;

            boolean buildWarned = false;
            if (c.queen == Command.MOVE) {
                moveQueen(s, p, c.x, c.y);
            } else if (c.queen == Command.BUILD) {
                int site = c.site;
                double d = Physics.distSq(s.sx[site], s.sy[site], s.qx[p], s.qy[p]);
                if (Physics.lessInt(d, Const.QUEEN_RADIUS + s.sr[site] + Const.TOUCHING_DELTA)) {
                    if (s.stType[site] != Const.NONE && s.stOwner[site] == 1 - p) {
                        buildWarned = true;
                        lastWarning[p] = "Cannot build: owned by enemy player";
                    } else if (s.stType[site] == Const.BARRACKS && s.stOwner[site] == p && s.barrTraining[site]) {
                        buildWarned = true;
                        lastWarning[p] = "Cannot rebuild: training is in progress";
                    } else {
                        if (nAttempt == 0) attempt0 = site; else attempt1 = site;
                        nAttempt++;
                        schedPlayer[nSched] = p; schedSite[nSched] = site; schedType[nSched] = c.structure;
                        nSched++;
                    }
                } else {
                    moveQueen(s, p, s.sx[site], s.sy[site]);
                }
                if (c.extraTokens && !buildWarned) { kill(s, p, "Too many tokens after BUILD command"); killed = true; }
            }
        }

        int skip = -1;
        if (nAttempt == 2 && attempt0 == attempt1) skip = turnIdx % 2;
        for (int k = 0; k < nSched; k++) {
            if (k == skip) continue;
            int p = schedPlayer[k];
            String err = build(s, p, schedSite[k], schedType[k]);
            if (err != null) { kill(s, p, err); killed = true; }
        }

        if (killed) {
            finishKilled(s);
            return;
        }

        processCreeps(s);

        for (int i = 0; i < s.nSites; i++) actStructure(s, i);

        if (s.fixedIncome >= 0) {
            for (int p = 0; p < 2; p++) if (s.active[p]) { s.goldPerTurn[p] = s.fixedIncome; s.gold[p] += s.fixedIncome; }
        }

        s.removeDeadCreeps();

        int alive = 0;
        for (int p = 0; p < 2; p++) {
            if (!s.active[p]) continue;
            if (s.score[p] >= 0) s.score[p] = s.hp[p];
            if (s.hp[p] == 0) s.active[p] = false; else alive++;
        }
        s.turn++;
        if (alive < 2 || s.turn >= Const.MAX_TURNS) s.over = true;

        for (int i = 0; i < s.nCreeps; i++) setCreep(s, i, Math.round(s.cx[i]), Math.round(s.cy[i]));
        for (int p = 0; p < 2; p++) setQueen(s, p, Math.round(s.qx[p]), Math.round(s.qy[p]));
        for (int i = 0; i < s.nSites; i++) { s.sx[i] = Math.round(s.sx[i]); s.sy[i] = Math.round(s.sy[i]); }
    }

    private void kill(GameState s, int p, String reason) {
        s.score[p] = -1;
        s.active[p] = false;
        lastKill[p] = reason;
    }

    /** A kill ends the game this turn; the killed player's -1 score decides the ranking. */
    private void finishKilled(GameState s) {
        for (int p = 0; p < 2; p++) if (s.score[p] >= 0) s.score[p] = s.hp[p];
        s.turn++;
        s.over = true;
    }

    private static void setQueen(GameState s, int p, double x, double y) {
        if (Physics.isZero(x, y)) return;
        s.qx[p] = x;
        s.qy[p] = y;
    }

    private static void setCreep(GameState s, int i, double x, double y) {
        if (Physics.isZero(x, y)) return;
        s.cx[i] = x;
        s.cy[i] = y;
    }

    private void moveQueen(GameState s, int p, double tx, double ty) {
        ph.towards(s.qx[p], s.qy[p], tx, ty, Const.QUEEN_SPEED);
        setQueen(s, p, ph.rx, ph.ry);
    }

    /** TRAIN line semantics; returns a warning (nothing trained) or null. */
    private static String train(GameState s, int p, int[] sites) {
        for (int site : sites) {
            if (s.stType[site] != Const.BARRACKS) return "Cannot spawn from " + site + ": not a barracks";
            if (s.stOwner[site] != p) return "Cannot spawn from " + site + ": not owned";
            if (s.barrTraining[site]) return "Barracks " + site + " is training";
        }
        for (int i = 0; i < sites.length; i++)
            for (int j = i + 1; j < sites.length; j++)
                if (sites[i] == sites[j]) return "Training from some barracks more than once";
        int sum = 0;
        for (int site : sites) sum += Const.CREEP_COST[s.barrType[site]];
        if (sum > s.gold[p]) return "Training too many creeps (" + sum + " total gold requested)";
        s.gold[p] -= sum;
        for (int site : sites) {
            s.barrProgress[site] = 0;
            s.barrTraining[site] = true;
        }
        return null;
    }

    /** Executes a scheduled build; returns a kill reason or null. */
    private static String build(GameState s, int p, int site, String type) {
        String[] toks = splitDash(type);
        String first = toks[0];
        if (first.equals("MINE") && s.mines) {
            if (s.stType[site] == Const.MINE) {
                s.mineRate[site]++;
                if (s.mineRate[site] > s.sMaxMine[site]) s.mineRate[site] = s.sMaxMine[site];
            } else {
                s.clearStructure(site);
                s.stType[site] = Const.MINE; s.stOwner[site] = p; s.mineRate[site] = 1;
            }
        } else if (first.equals("TOWER") && s.towers) {
            if (s.stType[site] == Const.TOWER) {
                s.towerHp[site] += Const.TOWER_HP_INCREMENT;
                if (s.towerHp[site] > Const.TOWER_HP_MAXIMUM) s.towerHp[site] = Const.TOWER_HP_MAXIMUM;
            } else {
                s.clearStructure(site);
                s.stType[site] = Const.TOWER; s.stOwner[site] = p; s.towerHp[site] = Const.TOWER_HP_INITIAL; s.towerRange[site] = 0;
            }
        } else if (first.equals("BARRACKS")) {
            if (toks.length < 2) return "BARRACKS type must be specified";
            int ct = -1;
            for (int t = 0; t < 3; t++) if (Const.CREEP_NAME[t].equals(toks[1])) ct = t;
            if (ct < 0 || (!s.giants && ct == Const.GIANT)) return "Invalid BARRACKS type: " + toks[1];
            s.clearStructure(site);
            s.stType[site] = Const.BARRACKS; s.stOwner[site] = p; s.barrType[site] = ct;
        } else {
            return "Invalid structure type: " + first;
        }
        return null;
    }

    /** Kotlin split('-'), keeping empty tokens. */
    private static String[] splitDash(String s) {
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '-') n++;
        String[] r = new String[n];
        int from = 0, k = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '-') { r[k++] = s.substring(from, i); from = i + 1; }
        }
        return r;
    }

    private int closestEnemyCreep(GameState s, int owner, double x, double y) {
        int best = -1;
        double bd = 0;
        for (int i = 0; i < s.nCreeps; i++) {
            if (s.cOwner[i] == owner) continue;
            double d = Physics.distSq(s.cx[i], s.cy[i], x, y);
            if (best < 0 || d < bd) { best = i; bd = d; }
        }
        return best;
    }

    private static int closestSite(GameState s, double x, double y) {
        int best = -1;
        double bd = 0;
        for (int i = 0; i < s.nSites; i++) {
            double d = Physics.distSq(s.sx[i], s.sy[i], x, y);
            if (best < 0 || d < bd) { best = i; bd = d; }
        }
        return best;
    }

    private void processCreeps(GameState s) {
        int n = 0;
        for (int t = 0; t < 3; t++)
            for (int i = 0; i < s.nCreeps; i++)
                if (s.cType[i] == t && s.active[s.cOwner[i]]) order[n++] = i;

        final double frames = 1.0 / 5;
        for (int step = 0; step < 5; step++) {
            for (int k = 0; k < n; k++) moveCreep(s, order[k], frames);
            ph.fixCollisions(s, 1);
        }
        for (int k = 0; k < n; k++) dealDamage(s, order[k]);

        // Tear down enemy mines
        for (int k = 0; k < n; k++) {
            int i = order[k];
            int site = closestSite(s, s.cx[i], s.cy[i]);
            double d = Physics.distSq(s.sx[site], s.sy[site], s.cx[i], s.cy[i]);
            if (!Physics.lessInt(d, s.sr[site] + Const.CREEP_RADIUS[s.cType[i]] + Const.TOUCHING_DELTA)) continue;
            if (s.stType[site] == Const.MINE && s.stOwner[site] != s.cOwner[i]) s.clearStructure(site);
        }

        for (int k = 0; k < n; k++) damageCreep(s, order[k], 1);

        // Queens tear down enemy mines and barracks
        for (int p = 0; p < 2; p++) {
            if (!s.active[p]) continue;
            int site = closestSite(s, s.qx[p], s.qy[p]);
            double d = Physics.distSq(s.sx[site], s.sy[site], s.qx[p], s.qy[p]);
            if (!Physics.lessInt(d, s.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA)) continue;
            if ((s.stType[site] == Const.MINE || s.stType[site] == Const.BARRACKS) && s.stOwner[site] != p) s.clearStructure(site);
        }
    }

    private static void damageCreep(GameState s, int i, int amount) {
        if (amount <= 0) return;
        s.cHp[i] = Math.max(0, s.cHp[i] - amount);
    }

    private static void damageQueen(GameState s, int p, int amount) {
        if (amount <= 0) return;
        s.hp[p] = Math.max(0, s.hp[p] - amount);
    }

    private void moveCreep(GameState s, int i, double frames) {
        int owner = s.cOwner[i], type = s.cType[i];
        double speed = Const.CREEP_SPEED[type] * frames;
        if (type == Const.GIANT) {
            int best = -1;
            double bd = 0;
            for (int j = 0; j < s.nSites; j++) {
                if (s.stType[j] != Const.TOWER || s.stOwner[j] != 1 - owner) continue;
                double d = Physics.distSq(s.sx[j], s.sy[j], s.cx[i], s.cy[i]);
                if (best < 0 || d < bd) { best = j; bd = d; }
            }
            if (best >= 0) {
                ph.towards(s.cx[i], s.cy[i], s.sx[best], s.sy[best], speed);
                setCreep(s, i, ph.rx, ph.ry);
            }
            return;
        }
        double tx, ty;
        int tr;
        if (type == Const.KNIGHT) {
            tx = s.qx[1 - owner]; ty = s.qy[1 - owner]; tr = Const.QUEEN_RADIUS;
        } else {
            int t = closestEnemyCreep(s, owner, s.cx[i], s.cy[i]);
            if (t >= 0) { tx = s.cx[t]; ty = s.cy[t]; tr = Const.CREEP_RADIUS[s.cType[t]]; }
            else { tx = s.qx[owner]; ty = s.qy[owner]; tr = Const.QUEEN_RADIUS; }
        }
        double d = Physics.distSq(s.cx[i], s.cy[i], tx, ty);
        int reach = Const.CREEP_RADIUS[type] + tr + Const.CREEP_RANGE[type];
        if (d > (double) (reach * reach)) {
            ph.resize(s.cx[i] - tx, s.cy[i] - ty, 3.0);
            ph.towards(s.cx[i], s.cy[i], tx + ph.rx, ty + ph.ry, speed);
            setCreep(s, i, ph.rx, ph.ry);
        }
    }

    private void dealDamage(GameState s, int i) {
        int owner = s.cOwner[i], type = s.cType[i], enemy = 1 - owner;
        if (type == Const.KNIGHT) {
            double d = Physics.distSq(s.cx[i], s.cy[i], s.qx[enemy], s.qy[enemy]);
            if (Physics.lessInt(d, Const.CREEP_RADIUS[type] + Const.QUEEN_RADIUS + Const.CREEP_RANGE[type] + Const.TOUCHING_DELTA))
                damageQueen(s, enemy, Const.KNIGHT_DAMAGE);
        } else if (type == Const.ARCHER) {
            int t = closestEnemyCreep(s, owner, s.cx[i], s.cy[i]);
            if (t < 0) return;
            double d = Physics.distSq(s.cx[i], s.cy[i], s.cx[t], s.cy[t]);
            if (Physics.lessInt(d, Const.CREEP_RADIUS[type] + Const.CREEP_RADIUS[s.cType[t]] + Const.CREEP_RANGE[type] + Const.TOUCHING_DELTA))
                damageCreep(s, t, s.cType[t] == Const.GIANT ? Const.ARCHER_DAMAGE_TO_GIANTS : Const.ARCHER_DAMAGE);
        } else {
            for (int j = 0; j < s.nSites; j++) {
                if (s.stType[j] != Const.TOWER || s.stOwner[j] != enemy) continue;
                double d = Physics.distSq(s.sx[j], s.sy[j], s.cx[i], s.cy[i]);
                if (Physics.lessInt(d, Const.CREEP_RADIUS[type] + s.sr[j] + Const.TOUCHING_DELTA)) {
                    s.towerHp[j] -= Const.GIANT_BUST_RATE;
                    return;
                }
            }
        }
    }

    private void actStructure(GameState s, int i) {
        int owner = s.stOwner[i];
        switch (s.stType[i]) {
            case Const.MINE: {
                int cash = Math.min(s.mineRate[i], s.sGold[i]);
                s.goldPerTurn[owner] += cash;
                s.gold[owner] += cash;
                s.sGold[i] = Math.max(0, s.sGold[i] - cash);
                if (s.sGold[i] <= 0) s.clearStructure(i);
                break;
            }
            case Const.TOWER: {
                int enemy = 1 - owner;
                int range = s.towerRange[i];
                int t = closestEnemyCreep(s, owner, s.sx[i], s.sy[i]);
                if (t >= 0 && Physics.lessInt(Physics.distSq(s.cx[t], s.cy[t], s.sx[i], s.sy[i]), range)) {
                    double shot = Math.sqrt(Physics.distSq(s.cx[t], s.cy[t], s.sx[i], s.sy[i])) - s.sr[i];
                    double diff = range - shot;
                    int dmg = Const.TOWER_CREEP_DAMAGE_MIN + (int) (diff / Const.TOWER_CREEP_DAMAGE_CLIMB_DISTANCE);
                    if (log != null) log.append(String.format("shot site%d r%d range%d hp%d -> creep(%.0f,%.0f) hp%d dist%.1f shot%.1f dmg%d; ",
                        i, s.sr[i], range, s.towerHp[i], s.cx[t], s.cy[t], s.cHp[t], Math.sqrt(Physics.distSq(s.cx[t], s.cy[t], s.sx[i], s.sy[i])), shot, dmg));
                    damageCreep(s, t, dmg);
                } else if (Physics.lessInt(Physics.distSq(s.qx[enemy], s.qy[enemy], s.sx[i], s.sy[i]), range)) {
                    double shot = Math.sqrt(Physics.distSq(s.qx[enemy], s.qy[enemy], s.sx[i], s.sy[i])) - s.sr[i];
                    double diff = range - shot;
                    int dmg = Const.TOWER_QUEEN_DAMAGE_MIN + (int) (diff / Const.TOWER_QUEEN_DAMAGE_CLIMB_DISTANCE);
                    if (log != null) log.append(String.format("shot site%d r%d range%d hp%d -> queen%d hp%d shot%.1f dmg%d; ", i, s.sr[i], range, s.towerHp[i], enemy, s.hp[enemy], shot, dmg));
                    damageQueen(s, enemy, dmg);
                }
                s.towerHp[i] -= Const.TOWER_MELT_RATE;
                s.towerRange[i] = (int) Math.sqrt((s.towerHp[i] * Const.TOWER_COVERAGE_PER_HP + s.sArea[i]) / Math.PI);
                if (s.towerHp[i] <= 0) s.clearStructure(i);
                break;
            }
            case Const.BARRACKS: {
                if (!s.barrTraining[i]) break;
                s.barrProgress[i]++;
                if (s.barrProgress[i] == Const.CREEP_BUILD_TIME[s.barrType[i]]) {
                    s.barrProgress[i] = 0;
                    s.barrTraining[i] = false;
                    spawn(s, i, owner, s.barrType[i]);
                }
                break;
            }
            default:
        }
    }

    /** Experimental spawn variants for replay inference: offset 0=c*iter 1=+iter 2=-iter; fixEach; towards. */
    public static int SPAWN_OFFSET = 0;
    public static boolean SPAWN_FIX_EACH = false, SPAWN_TOWARDS = true;

    private void spawn(GameState s, int site, int owner, int type) {
        int c = owner == 1 && !LEGACY_SPAWN ? -1 : 1;
        if (SPAWN_OFFSET == 1) c = 1;
        if (SPAWN_OFFSET == 2) c = -1;
        int enemy = 1 - owner;
        for (int iter = 0; iter < Const.CREEP_COUNT[type]; iter++) {
            int k = s.addCreep(owner, type, 0.0, 0.0);
            // Unit.location starts at Vector2.Zero; assignments of Zero are ignored.
            setCreep(s, k, s.sx[site] + c * iter, s.sy[site] + c * iter);
            if (SPAWN_TOWARDS) {
                ph.towards(s.cx[k], s.cy[k], s.qx[enemy], s.qy[enemy], 30.0);
                setCreep(s, k, ph.rx, ph.ry);
            }
            if (SPAWN_FIX_EACH) ph.fixCollisions(s, 999);
        }
        ph.fixCollisions(s, 999);
    }
}
