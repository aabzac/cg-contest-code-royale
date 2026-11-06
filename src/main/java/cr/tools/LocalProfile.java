package cr.tools;

import cr.bot.BotFactory;
import cr.core.Const;
import cr.referee.Referee;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Profile of a bot's local games, in the terms of the arena studies (cgStudy): for seat A on maps
 * seedBase.. (both seats), the medians at turns 50, 100, 150 and 200 (since 2026-11-06; 50-150 before)
 * of income, mines, towers and their summed HP, the knight and giant waves trained, the queen's HP, the
 * death turn and the outcome. Since 2026-11-06 also the economy over the game: own mines built,
 * upgrades, mines torn down (gold left on the site) or depleted, and the gold the mines took.
 * For calibrating the field-balance opponent (docs/data/exp-2026-11-03-field-opponent). Since
 * 2026-11-05 also the first contact (the first turn with an enemy knight within 250 of the queen,
 * her own towers then and the HP she loses in the next 20 turns), the HP lost on turns 25-50 and the
 * games at the 250-turn limit, for the Silver study (docs/data/study-2026-11-05-silver). The maps are
 * seedBase + k * stride (stride 1 by default): the queens' starting HP is the generator's first draw,
 * which consecutive seeds barely change, so a stride such as 397 spreads it over all 16 values as on CG.
 * Usage: LocalProfile &lt;botA&gt; &lt;botB&gt; &lt;maps&gt; &lt;seedBase&gt; [stride]
 */
public final class LocalProfile {
    static final int[] AT = {50, 100, 150, 200};
    /** Columns after the AT.length * 5 per-turn values. */
    static final int WAVES = AT.length * 5, GIANTS = WAVES + 1, DEATH = WAVES + 2, TURNS = WAVES + 3, CONTACT = WAVES + 4,
        NEAR_TOWER = WAVES + 5, LOST_PER_CONTACT = WAVES + 6, MOVES = WAVES + 7, FIRST_CONTACT = WAVES + 8,
        TOWERS_AT_CONTACT = WAVES + 9, LOST_AFTER_CONTACT = WAVES + 10, LOST_25_50 = WAVES + 11,
        BUILT = WAVES + 12, UPGRADES = WAVES + 13, TORN_DOWN = WAVES + 14, DEPLETED = WAVES + 15, MINED = WAVES + 16,
        GIANTS_IN = WAVES + 17, LOST_TO_GIANTS = WAVES + 18, LOST_OTHER = WAVES + 19, FIRST_BUST = WAVES + 20,
        ARCHER_WAVES = WAVES + 21, EXPOSED = WAVES + 22, EXPOSED_AT_RELEASE = WAVES + 23, TO_BARRACKS = WAVES + 24,
        HIT_PER_TRAINING = WAVES + 25, COLUMNS = WAVES + 26;

    public static void main(String[] args) {
        String a = args[0], b = args[1];
        int maps = Integer.parseInt(args[2]);
        long base = Long.parseLong(args[3]);
        long stride = args.length > 4 ? Long.parseLong(args[4]) : 1;
        List<double[]> rows = new ArrayList<>();
        List<Integer> startHp = new ArrayList<>();
        int wins = 0, deaths = 0, killed = 0, games = 0, atLimit = 0, decidedAtLimit = 0, closeAtLimit = 0;
        for (int m = 0; m < maps; m++) {
            long seed = base + m * stride;
            for (int seat = 0; seat < 2; seat++) {
                Referee.Result r = seat == 0 ? Referee.play(seed, BotFactory.create(a), BotFactory.create(b), true, 50)
                                             : Referee.play(seed, BotFactory.create(b), BotFactory.create(a), true, 50);
                games++;
                if (r.winner == seat) wins++;
                if (seat == 0 && !r.frames.isEmpty()) startHp.add(r.frames.get(0).hp[0]);
                if (r.turns >= Const.MAX_TURNS) {
                    atLimit++;
                    Referee.Frame end = r.frames.get(r.frames.size() - 1);
                    if (end.hp[0] != end.hp[1]) { decidedAtLimit++; if (Math.abs(end.hp[0] - end.hp[1]) <= 10) closeAtLimit++; }
                }
                double[] row = new double[COLUMNS];
                int waves = 0, giants = 0, archers = 0, prevTraining = 0;
                boolean[] wasTraining = new boolean[64];
                for (Referee.Frame f : r.frames) {
                    for (int k = 0; k < AT.length; k++) {
                        if (f.turn != AT[k]) continue;
                        int inc = 0, mines = 0, towers = 0, thp = 0;
                        for (int[] st : f.structures) {
                            if (st[1] != seat) continue;
                            if (st[0] == Const.MINE) { mines++; inc += st[2]; }
                            else if (st[0] == Const.TOWER) { towers++; thp += st[2]; }
                        }
                        row[k * 5] = inc; row[k * 5 + 1] = mines; row[k * 5 + 2] = towers; row[k * 5 + 3] = thp; row[k * 5 + 4] = f.hp[seat];
                    }
                    for (int i = 0; i < f.structures.length; i++) {
                        int[] st = f.structures[i];
                        boolean training = st[0] == Const.BARRACKS && st[1] == seat && st[2] > 0;
                        if (training && !wasTraining[i]) { if (st[3] == Const.GIANT) giants++; else if (st[3] == Const.ARCHER) archers++; else waves++; }
                        wasTraining[i] = training;
                    }
                }
                row[WAVES] = waves;
                row[GIANTS] = giants;
                row[ARCHER_WAVES] = archers;
                waves(r, seat, row);
                // The economy over the game: own mines built (a site turning into an own mine), upgrades (an own mine's
                // rate rising), own mines lost with gold left on the site (torn down by an enemy creep or queen) or
                // without (depleted), and the gold the own mines took from their sites.
                int built = 0, upgrades = 0, tornDown = 0, depleted = 0, mined = 0;
                int[][] prevSt = null;
                for (Referee.Frame f : r.frames) {
                    if (prevSt != null)
                        for (int i = 0; i < f.structures.length; i++) {
                            int[] was = prevSt[i], now = f.structures[i];
                            boolean wasMine = was[0] == Const.MINE && was[1] == seat, isMine = now[0] == Const.MINE && now[1] == seat;
                            if (isMine && !wasMine) built++;
                            else if (isMine && now[2] > was[2]) upgrades++;
                            else if (wasMine && !isMine) { if (now[4] > 0) tornDown++; else depleted++; }
                            if (wasMine || isMine) mined += Math.max(0, was[4] - now[4]);
                        }
                    prevSt = f.structures;
                }
                row[BUILT] = built; row[UPGRADES] = upgrades; row[TORN_DOWN] = tornDown; row[DEPLETED] = depleted; row[MINED] = mined;
                // Enemy giants: those that came out (the rise of their count between frames), own towers lost while an enemy
                // giant touched them on the frame before, the other own towers lost, and the first turn a giant touched one.
                int giantsIn = 0, lostToGiants = 0, lostOther = 0, firstBust = -1;
                Referee.Frame prevF = null;
                for (Referee.Frame f : r.frames) {
                    if (prevF != null) {
                        int g = giantCount(f, seat), pg = giantCount(prevF, seat);
                        if (g > pg) giantsIn += g - pg;
                        for (int i = 0; i < f.structures.length; i++) {
                            boolean was = prevF.structures[i][0] == Const.TOWER && prevF.structures[i][1] == seat;
                            boolean is = f.structures[i][0] == Const.TOWER && f.structures[i][1] == seat;
                            if (was && !is) { if (giantTouches(prevF, r.sites.get(i), seat)) lostToGiants++; else lostOther++; }
                        }
                    }
                    if (firstBust < 0)
                        for (int i = 0; i < f.structures.length; i++)
                            if (f.structures[i][0] == Const.TOWER && f.structures[i][1] == seat && giantTouches(f, r.sites.get(i), seat)) { firstBust = f.turn; break; }
                    prevF = f;
                }
                row[GIANTS_IN] = giantsIn; row[LOST_TO_GIANTS] = lostToGiants; row[LOST_OTHER] = lostOther;
                row[FIRST_BUST] = firstBust < 0 ? Double.NaN : firstBust;
                // Under knights: contact turns (an enemy knight within 250 of our queen), the HP lost on them, the turns
                // near an own tower (within 130 of its centre) and the MOVE commands among them.
                int contact = 0, nearTower = 0, moves = 0, lostHp = 0, prevHp = -1;
                int firstContact = -1, towersAtContact = 0;
                for (Referee.Frame f : r.frames) {
                    int qx = f.queens[seat][0], qy = f.queens[seat][1];
                    boolean knightNear = false;
                    for (int[] c : f.creeps) if (c[0] == 1 - seat && c[1] == Const.KNIGHT && (c[2] - qx) * (c[2] - qx) + (c[3] - qy) * (c[3] - qy) < 250 * 250) knightNear = true;
                    if (knightNear && firstContact < 0) {
                        firstContact = f.turn;
                        for (int[] st : f.structures) if (st[0] == Const.TOWER && st[1] == seat) towersAtContact++;
                    }
                    if (knightNear) {
                        contact++;
                        if (prevHp >= 0) lostHp += Math.max(0, prevHp - f.hp[seat]);
                        for (int i = 0; i < f.structures.length; i++) {
                            int[] st = f.structures[i];
                            if (st[0] != Const.TOWER || st[1] != seat) continue;
                            int[] site = r.sites.get(i);
                            if ((site[1] - qx) * (site[1] - qx) + (site[2] - qy) * (site[2] - qy) <= 130 * 130) { nearTower++; break; }
                        }
                        if (f.cmds != null && f.cmds[seat] != null && f.cmds[seat].startsWith("MOVE")) moves++;
                    }
                    prevHp = f.hp[seat];
                }
                row[CONTACT] = contact;
                row[NEAR_TOWER] = contact == 0 ? Double.NaN : 100.0 * nearTower / contact;
                row[LOST_PER_CONTACT] = contact == 0 ? Double.NaN : (double) lostHp / contact;
                row[MOVES] = contact == 0 ? Double.NaN : 100.0 * moves / contact;
                row[FIRST_CONTACT] = firstContact < 0 ? Double.NaN : firstContact;
                row[TOWERS_AT_CONTACT] = firstContact < 0 ? Double.NaN : towersAtContact;
                row[LOST_AFTER_CONTACT] = firstContact < 0 ? Double.NaN : hpAt(r, seat, firstContact) - hpAt(r, seat, firstContact + 20);
                row[LOST_25_50] = r.turns < 50 ? Double.NaN : hpAt(r, seat, 25) - hpAt(r, seat, 50);
                int death = -1;
                for (Referee.Frame f : r.frames) if (f.hp[seat] <= 0) { death = f.turn; break; }
                if (death >= 0) deaths++;
                boolean theirs = false;
                for (Referee.Frame f : r.frames) if (f.hp[1 - seat] <= 0) { theirs = true; break; }
                if (theirs) killed++;
                row[DEATH] = death;
                row[TURNS] = r.turns;
                rows.add(row);
            }
        }
        System.out.printf(Locale.ROOT, "%s vs %s: %d games, %s wins %d (%.1f %%), queen deaths %d, enemy queens killed %d%n", a, b, games, a, wins, 100.0 * wins / games, deaths, killed);
        int[] hp = startHp.stream().mapToInt(Integer::intValue).sorted().toArray();
        System.out.printf(Locale.ROOT, "maps %d from seed %d, stride %d: starting HP median %s, %d distinct values; decided at the limit %d, by <= 10 HP %d%n",
            maps, base, stride, hp.length == 0 ? "-" : String.valueOf(hp[hp.length / 2]), Arrays.stream(hp).distinct().count(), decidedAtLimit, closeAtLimit);
        String[] names = {"income", "mines", "towers", "towerHp", "queenHp"};
        for (int k = 0; k < AT.length; k++) {
            StringBuilder sb = new StringBuilder("turn " + AT[k] + ":");
            for (int j = 0; j < 5; j++) sb.append(' ').append(names[j]).append(' ').append(fmt(median(rows, k * 5 + j, AT[k])));
            System.out.println(sb);
        }
        System.out.printf(Locale.ROOT, "waves: knight %s, giant %s (medians), archer %s (mean); death turn median %s over the deaths; game length median %s, mean %s; at the %d-turn limit %d (%.0f %%)%n",
            fmt(median(rows, WAVES, 0)), fmt(median(rows, GIANTS, 0)), fmt1(meanNum(rows, ARCHER_WAVES)), fmt(medianIf(rows, DEATH)), fmt(median(rows, TURNS, 0)),
            fmt1(rows.stream().mapToDouble(r -> r[TURNS]).average().orElse(Double.NaN)), Const.MAX_TURNS, atLimit, 100.0 * atLimit / games);
        System.out.printf(Locale.ROOT, "under knights (medians over games with contact): contact turns %s, %% near an own tower %s, HP lost per contact turn %s, %% MOVE %s%n",
            fmt(medianNum(rows.stream().filter(r -> r[CONTACT] > 0).toList(), CONTACT)), fmt1(medianNum(rows, NEAR_TOWER)), fmt1(medianNum(rows, LOST_PER_CONTACT)), fmt1(medianNum(rows, MOVES)));
        System.out.printf(Locale.ROOT, "first contact (medians over games with contact): turn %s, own towers then %s, HP lost in the next 20 turns %s (mean %s); HP lost on turns 25-50 %s (mean %s, games reaching turn 50)%n",
            fmt(medianNum(rows, FIRST_CONTACT)), fmt(medianNum(rows, TOWERS_AT_CONTACT)), fmt(medianNum(rows, LOST_AFTER_CONTACT)), fmt1(meanNum(rows, LOST_AFTER_CONTACT)),
            fmt(medianNum(rows, LOST_25_50)), fmt1(meanNum(rows, LOST_25_50)));
        System.out.printf(Locale.ROOT, "economy over the game (medians; means): own mines built %s; %s, upgrades %s; %s, torn down %s; %s, depleted %s; %s, gold mined %s; %s%n",
            fmt(median(rows, BUILT, 0)), fmt1(meanNum(rows, BUILT)), fmt(median(rows, UPGRADES, 0)), fmt1(meanNum(rows, UPGRADES)),
            fmt(median(rows, TORN_DOWN, 0)), fmt1(meanNum(rows, TORN_DOWN)), fmt(median(rows, DEPLETED, 0)), fmt1(meanNum(rows, DEPLETED)),
            fmt(median(rows, MINED, 0)), fmt1(meanNum(rows, MINED)));
        System.out.printf(Locale.ROOT, "our waves (means over games, from turn 30): the enemy queen outside her towers' ranges on %s %% of the turns and at %s %% of our knight trainings, %s from our nearest knight barracks; she loses %s HP with our knights within 250 per knight training (whole game)%n",
            fmt1(meanNum(rows, EXPOSED)), fmt1(meanNum(rows, EXPOSED_AT_RELEASE)), fmt(meanNum(rows, TO_BARRACKS)), fmt1(meanNum(rows, HIT_PER_TRAINING)));
        List<double[]> withGiants = rows.stream().filter(r -> r[GIANTS_IN] > 0).toList();
        System.out.printf(Locale.ROOT, "enemy giants: in %d of %d games (mean %s a game); in those games own towers lost to giants %s, to other causes %s (means), first giant at an own tower turn %s (median)%n",
            withGiants.size(), rows.size(), fmt1(rows.stream().mapToDouble(r -> r[GIANTS_IN]).average().orElse(Double.NaN)),
            fmt1(meanNum(withGiants, LOST_TO_GIANTS)), fmt1(meanNum(withGiants, LOST_OTHER)), fmt(medianNum(withGiants, FIRST_BUST)));
    }

    /**
     * Our waves and the enemy queen, from turn 30 on: the share of turns she stands outside all her towers' ranges, the
     * same at our knight barracks' training starts, her mean distance to our nearest knight barracks, and over the game
     * the HP she loses on turns with our knights within 250 of her, per knight barracks training of ours.
     */
    static void waves(Referee.Result r, int seat, double[] row) {
        int turns = 0, out = 0, releases = 0, outAtRelease = 0, distN = 0, lost = 0, trainings = 0;
        double dist = 0;
        boolean[] wasTraining = new boolean[64];
        Referee.Frame prev = null;
        for (Referee.Frame f : r.frames) {
            int ex = f.queens[1 - seat][0], ey = f.queens[1 - seat][1];
            boolean exposed = true;
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < f.structures.length; i++) {
                int[] st = f.structures[i], site = r.sites.get(i);
                double d2 = (double) (site[1] - ex) * (site[1] - ex) + (double) (site[2] - ey) * (site[2] - ey);
                if (st[0] == Const.TOWER && st[1] == 1 - seat && d2 < (double) st[3] * st[3]) exposed = false;
                if (st[0] == Const.BARRACKS && st[1] == seat && st[3] == Const.KNIGHT) nearest = Math.min(nearest, Math.sqrt(d2));
            }
            for (int i = 0; i < f.structures.length; i++) {
                int[] st = f.structures[i];
                boolean training = st[0] == Const.BARRACKS && st[1] == seat && st[3] == Const.KNIGHT && st[2] > 0;
                if (training && !wasTraining[i]) {
                    trainings++;
                    if (f.turn >= 30) { releases++; if (exposed) outAtRelease++; }
                }
                wasTraining[i] = training;
            }
            if (f.turn >= 30) {
                turns++;
                if (exposed) out++;
                if (nearest < Double.MAX_VALUE) { dist += nearest; distN++; }
            }
            if (prev != null) {
                boolean near = false;
                for (int[] c : f.creeps)
                    if (c[0] == seat && c[1] == Const.KNIGHT && (double) (c[2] - ex) * (c[2] - ex) + (double) (c[3] - ey) * (c[3] - ey) < 250.0 * 250) near = true;
                if (near) lost += Math.max(0, prev.hp[1 - seat] - f.hp[1 - seat]);
            }
            prev = f;
        }
        row[EXPOSED] = turns == 0 ? Double.NaN : 100.0 * out / turns;
        row[EXPOSED_AT_RELEASE] = releases == 0 ? Double.NaN : 100.0 * outAtRelease / releases;
        row[TO_BARRACKS] = distN == 0 ? Double.NaN : dist / distN;
        row[HIT_PER_TRAINING] = trainings == 0 ? Double.NaN : (double) lost / trainings;
    }

    static int giantCount(Referee.Frame f, int seat) {
        int n = 0;
        for (int[] c : f.creeps) if (c[0] == 1 - seat && c[1] == Const.GIANT) n++;
        return n;
    }

    /** An enemy giant touches the site (within its radius, the giant's and the touching delta, plus 5 for the rounding). */
    static boolean giantTouches(Referee.Frame f, int[] site, int seat) {
        double reach = site[3] + Const.CREEP_RADIUS[Const.GIANT] + Const.TOUCHING_DELTA + 5;
        for (int[] c : f.creeps)
            if (c[0] == 1 - seat && c[1] == Const.GIANT && (double) (c[2] - site[1]) * (c[2] - site[1]) + (double) (c[3] - site[2]) * (c[3] - site[2]) < reach * reach) return true;
        return false;
    }

    /** The queen's HP on the frame of turn t, or on the last frame if the game ended before t. */
    static double hpAt(Referee.Result r, int seat, int t) {
        Referee.Frame last = null;
        for (Referee.Frame f : r.frames) { if (f.turn > t) break; last = f; }
        return last == null ? Double.NaN : last.hp[seat];
    }

    /** The median of column c over the games that reached turn t. */
    static double median(List<double[]> rows, int c, int t) {
        double[] v = rows.stream().filter(r -> r[TURNS] >= t).mapToDouble(r -> r[c]).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    /** The mean of column c over the games where it is a number. */
    static double meanNum(List<double[]> rows, int c) {
        return rows.stream().mapToDouble(r -> r[c]).filter(x -> !Double.isNaN(x)).average().orElse(Double.NaN);
    }

    static double medianIf(List<double[]> rows, int c) {
        double[] v = rows.stream().filter(r -> r[c] >= 0).mapToDouble(r -> r[c]).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    static String fmt(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.0f", v); }

    static String fmt1(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.1f", v); }

    /** The median of column c over the games where it is a number. */
    static double medianNum(List<double[]> rows, int c) {
        double[] v = rows.stream().mapToDouble(r -> r[c]).filter(x -> !Double.isNaN(x)).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }
}
