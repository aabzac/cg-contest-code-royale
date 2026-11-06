package cr.tools;

import cr.bot.BotFactory;
import cr.core.Const;
import cr.referee.Referee;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Our knight waves and what they do (2026-11-06, docs/data/exp-2026-11-06-waves): for bot A on maps seedBase + k * stride
 * (both seats) against bot B, every wave of A (the knight barracks of A that start training on one turn) with, at its
 * release, the enemy queen's distance to the nearest releasing barracks, whether an enemy tower covers her, the enemy
 * towers and their summed HP, A's gold left, and the HP the enemy queen loses in the next 35 turns on turns with A's
 * knights within 250 of her. Prints the mean damage per wave by distance and by enemy towers.
 * Usage: WaveScan &lt;botA&gt; &lt;botB&gt; &lt;maps&gt; &lt;seedBase&gt; [stride]
 */
public final class WaveScan {
    static final int WINDOW = 35, FROM_TURN = 30;

    record Wave(int turn, int barracks, double dist, boolean covered, int enemyTowers, int enemyTowerHp, int damage) {}

    public static void main(String[] args) {
        String a = args[0], b = args[1];
        int maps = Integer.parseInt(args[2]);
        long base = Long.parseLong(args[3]);
        long stride = args.length > 4 ? Long.parseLong(args[4]) : 1;
        List<Wave> waves = new ArrayList<>();
        for (int m = 0; m < maps; m++) {
            long seed = base + m * stride;
            for (int seat = 0; seat < 2; seat++) {
                Referee.Result r = seat == 0 ? Referee.play(seed, BotFactory.create(a), BotFactory.create(b), true, 50)
                                             : Referee.play(seed, BotFactory.create(b), BotFactory.create(a), true, 50);
                waves.addAll(scan(r, seat));
            }
        }
        System.out.printf(Locale.ROOT, "%s vs %s: %d maps from seed %d, stride %d; %d waves of %s from turn %d (knight barracks starting on one turn)%n",
            a, b, maps, base, stride, waves.size(), a, FROM_TURN);
        System.out.printf(Locale.ROOT, "mean damage to the enemy queen in the next %d turns with our knights within 250: %.2f HP; covered by an enemy tower at release: %.1f %%%n",
            WINDOW, waves.stream().mapToInt(Wave::damage).average().orElse(Double.NaN), 100.0 * waves.stream().filter(Wave::covered).count() / Math.max(1, waves.size()));
        int[] edges = {0, 600, 800, 1000, 1200, 1500, 99999};
        for (int i = 0; i + 1 < edges.length; i++) {
            int lo = edges[i], hi = edges[i + 1];
            List<Wave> w = waves.stream().filter(x -> x.dist >= lo && x.dist < hi).toList();
            System.out.printf(Locale.ROOT, "  enemy queen %4d-%5d from the barracks: %5d waves (%4.1f %%), damage %.2f%n", lo, hi, w.size(),
                100.0 * w.size() / Math.max(1, waves.size()), w.stream().mapToInt(Wave::damage).average().orElse(Double.NaN));
        }
        for (int t = 0; t <= 6; t++) {
            final int tt = t;
            List<Wave> w = waves.stream().filter(x -> tt < 6 ? x.enemyTowers == tt : x.enemyTowers >= 6).toList();
            System.out.printf(Locale.ROOT, "  enemy towers %s: %5d waves, damage %.2f%n", t < 6 ? String.valueOf(t) : "6+", w.size(),
                w.stream().mapToInt(Wave::damage).average().orElse(Double.NaN));
        }
    }

    static List<Wave> scan(Referee.Result r, int seat) {
        List<Wave> out = new ArrayList<>();
        boolean[] wasTraining = new boolean[64];
        for (int k = 0; k < r.frames.size(); k++) {
            Referee.Frame f = r.frames.get(k);
            int ex = f.queens[1 - seat][0], ey = f.queens[1 - seat][1];
            double dist = Double.MAX_VALUE;
            int started = 0, barracks = -1;
            for (int i = 0; i < f.structures.length; i++) {
                int[] st = f.structures[i];
                boolean training = st[0] == Const.BARRACKS && st[1] == seat && st[3] == Const.KNIGHT && st[2] > 0;
                if (training && !wasTraining[i]) {
                    started++;
                    int[] site = r.sites.get(i);
                    double d = Math.hypot(site[1] - ex, site[2] - ey);
                    if (d < dist) { dist = d; barracks = i; }
                }
                wasTraining[i] = training;
            }
            if (started == 0 || f.turn < FROM_TURN) continue;
            boolean covered = false;
            int towers = 0, towerHp = 0;
            for (int i = 0; i < f.structures.length; i++) {
                int[] st = f.structures[i];
                if (st[0] != Const.TOWER || st[1] != 1 - seat) continue;
                towers++;
                towerHp += st[2];
                int[] site = r.sites.get(i);
                if ((double) (site[1] - ex) * (site[1] - ex) + (double) (site[2] - ey) * (site[2] - ey) < (double) st[3] * st[3]) covered = true;
            }
            int damage = 0;
            for (int j = k + 1; j < r.frames.size() && j <= k + WINDOW; j++) {
                Referee.Frame g = r.frames.get(j), p = r.frames.get(j - 1);
                int gx = g.queens[1 - seat][0], gy = g.queens[1 - seat][1];
                boolean near = false;
                for (int[] c : g.creeps)
                    if (c[0] == seat && c[1] == Const.KNIGHT && (double) (c[2] - gx) * (c[2] - gx) + (double) (c[3] - gy) * (c[3] - gy) < 250.0 * 250) near = true;
                if (near) damage += Math.max(0, p.hp[1 - seat] - g.hp[1 - seat]);
            }
            out.add(new Wave(f.turn, barracks, dist, covered, towers, towerHp, damage));
        }
        return out;
    }
}
