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
 * For calibrating the field-balance opponent (docs/data/exp-2026-11-03-field-opponent).
 * Usage: LocalProfile &lt;botA&gt; &lt;botB&gt; &lt;maps&gt; &lt;seedBase&gt;
 */
public final class LocalProfile {
    static final int[] AT = {50, 100, 150};

    public static void main(String[] args) {
        String a = args[0], b = args[1];
        int maps = Integer.parseInt(args[2]);
        long base = Long.parseLong(args[3]);
        List<double[]> rows = new ArrayList<>();
        int wins = 0, deaths = 0, killed = 0, games = 0;
        for (long seed = base; seed < base + maps; seed++) {
            for (int seat = 0; seat < 2; seat++) {
                Referee.Result r = seat == 0 ? Referee.play(seed, BotFactory.create(a), BotFactory.create(b), true, 50)
                                             : Referee.play(seed, BotFactory.create(b), BotFactory.create(a), true, 50);
                games++;
                if (r.winner == seat) wins++;
                double[] row = new double[AT.length * 5 + 4];
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
                row[AT.length * 5] = waves;
                row[AT.length * 5 + 1] = giants;
                int death = -1;
                for (Referee.Frame f : r.frames) if (f.hp[seat] <= 0) { death = f.turn; break; }
                if (death >= 0) deaths++;
                boolean theirs = false;
                for (Referee.Frame f : r.frames) if (f.hp[1 - seat] <= 0) { theirs = true; break; }
                if (theirs) killed++;
                row[AT.length * 5 + 2] = death;
                row[AT.length * 5 + 3] = r.turns;
                rows.add(row);
            }
        }
        System.out.printf(Locale.ROOT, "%s vs %s: %d games, %s wins %d (%.1f %%), queen deaths %d, enemy queens killed %d%n", a, b, games, a, wins, 100.0 * wins / games, deaths, killed);
        String[] names = {"income", "mines", "towers", "towerHp", "queenHp"};
        for (int k = 0; k < AT.length; k++) {
            StringBuilder sb = new StringBuilder("turn " + AT[k] + ":");
            for (int j = 0; j < 5; j++) sb.append(' ').append(names[j]).append(' ').append(fmt(median(rows, k * 5 + j, AT[k])));
            System.out.println(sb);
        }
        System.out.printf(Locale.ROOT, "waves: knight %s, giant %s (medians); death turn median %s over the deaths; game length median %s%n",
            fmt(median(rows, AT.length * 5, 0)), fmt(median(rows, AT.length * 5 + 1, 0)), fmt(medianIf(rows, AT.length * 5 + 2)), fmt(median(rows, AT.length * 5 + 3, 0)));
    }

    /** The median of column c over the games that reached turn t (their length is the last column). */
    static double median(List<double[]> rows, int c, int t) {
        double[] v = rows.stream().filter(r -> r[r.length - 1] >= t).mapToDouble(r -> r[c]).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    static double medianIf(List<double[]> rows, int c) {
        double[] v = rows.stream().filter(r -> r[c] >= 0).mapToDouble(r -> r[c]).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    static String fmt(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.0f", v); }
}
