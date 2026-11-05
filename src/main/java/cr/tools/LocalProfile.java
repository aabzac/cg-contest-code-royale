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
 * seedBase.. (both seats), the medians at turns 50, 100 and 150 of income, mines, towers and their
 * summed HP, the knight and giant waves trained, the queen's HP, the death turn and the outcome.
 * For calibrating the field-balance opponent (docs/data/exp-2026-11-03-field-opponent). Since
 * 2026-11-05 also the first contact (the first turn with an enemy knight within 250 of the queen,
 * her own towers then and the HP she loses in the next 20 turns), the HP lost on turns 25-50 and the
 * games at the 250-turn limit, for the Silver study (docs/data/study-2026-11-05-silver). The maps are
 * seedBase + k * stride (stride 1 by default): the queens' starting HP is the generator's first draw,
 * which consecutive seeds barely change, so a stride such as 397 spreads it over all 16 values as on CG.
 * Usage: LocalProfile &lt;botA&gt; &lt;botB&gt; &lt;maps&gt; &lt;seedBase&gt; [stride]
 */
public final class LocalProfile {
    static final int[] AT = {50, 100, 150};
    /** Columns after the AT.length * 5 per-turn values. */
    static final int WAVES = AT.length * 5, GIANTS = WAVES + 1, DEATH = WAVES + 2, TURNS = WAVES + 3, CONTACT = WAVES + 4,
        NEAR_TOWER = WAVES + 5, LOST_PER_CONTACT = WAVES + 6, MOVES = WAVES + 7, FIRST_CONTACT = WAVES + 8,
        TOWERS_AT_CONTACT = WAVES + 9, LOST_AFTER_CONTACT = WAVES + 10, LOST_25_50 = WAVES + 11, COLUMNS = WAVES + 12;

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
                int waves = 0, giants = 0, prevTraining = 0;
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
                        if (training && !wasTraining[i]) { if (st[3] == Const.GIANT) giants++; else waves++; }
                        wasTraining[i] = training;
                    }
                }
                row[WAVES] = waves;
                row[GIANTS] = giants;
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
        System.out.printf(Locale.ROOT, "waves: knight %s, giant %s (medians); death turn median %s over the deaths; game length median %s, mean %s; at the %d-turn limit %d (%.0f %%)%n",
            fmt(median(rows, WAVES, 0)), fmt(median(rows, GIANTS, 0)), fmt(medianIf(rows, DEATH)), fmt(median(rows, TURNS, 0)),
            fmt1(rows.stream().mapToDouble(r -> r[TURNS]).average().orElse(Double.NaN)), Const.MAX_TURNS, atLimit, 100.0 * atLimit / games);
        System.out.printf(Locale.ROOT, "under knights (medians over games with contact): contact turns %s, %% near an own tower %s, HP lost per contact turn %s, %% MOVE %s%n",
            fmt(medianNum(rows.stream().filter(r -> r[CONTACT] > 0).toList(), CONTACT)), fmt1(medianNum(rows, NEAR_TOWER)), fmt1(medianNum(rows, LOST_PER_CONTACT)), fmt1(medianNum(rows, MOVES)));
        System.out.printf(Locale.ROOT, "first contact (medians over games with contact): turn %s, own towers then %s, HP lost in the next 20 turns %s (mean %s); HP lost on turns 25-50 %s (mean %s, games reaching turn 50)%n",
            fmt(medianNum(rows, FIRST_CONTACT)), fmt(medianNum(rows, TOWERS_AT_CONTACT)), fmt(medianNum(rows, LOST_AFTER_CONTACT)), fmt1(meanNum(rows, LOST_AFTER_CONTACT)),
            fmt(medianNum(rows, LOST_25_50)), fmt1(meanNum(rows, LOST_25_50)));
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
