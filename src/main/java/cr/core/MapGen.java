package cr.core;

import java.util.Random;

/** Port of Referee.init + buildMap (MapBuilding.kt): same java.util.Random call sequence. */
public final class MapGen {
    private MapGen() {}

    private static int sample(Random r, int first, int last) { return r.nextInt(last - first + 1) + first; }

    public static GameState generate(long seed, int level) {
        Random rnd = new Random(seed);
        GameState s = new GameState();
        s.setLevel(level);
        int pairs = Const.OBSTACLE_PAIRS_MAX;
        int queenHp = 100;
        if (level <= 2) pairs = sample(rnd, 6, 12);
        else if (level >= 4) queenHp = sample(rnd, 5, 20) * 5;

        Physics ph = new Physics();
        ph.clear();
        int[] rate = new int[pairs * 2], gold = new int[pairs * 2];
        for (int p = 0; p < pairs; p++) {
            int r = sample(rnd, 1, 3);
            int g = sample(rnd, 200, 250);
            int rad = sample(rnd, 60, 90);
            int x = rnd.nextInt(Const.WIDTH);
            int y = rnd.nextInt(Const.HEIGHT);
            ph.add(x, y, rad, 0, false);
            ph.add((double) Const.WIDTH - x, (double) Const.HEIGHT - y, rad, 0, false);
            rate[2 * p] = rate[2 * p + 1] = r;
            gold[2 * p] = gold[2 * p + 1] = g;
        }
        for (int it = 0; it < 100; it++) {
            for (int p = 0; p < pairs; p++) {
                int a = 2 * p, b = a + 1;
                double mx = (ph.ex[a] + (Const.WIDTH - ph.ex[b])) / 2.0;
                double my = (ph.ey[a] + (Const.HEIGHT - ph.ey[b])) / 2.0;
                ph.ex[a] = mx; ph.ey[a] = my;
                ph.ex[b] = Const.WIDTH - mx; ph.ey[b] = Const.HEIGHT - my;
            }
            if (!ph.collisionCheck(Const.OBSTACLE_GAP)) break;
        }

        s.nSites = pairs * 2;
        for (int i = 0; i < s.nSites; i++) {
            s.sx[i] = Math.round(ph.ex[i]);
            s.sy[i] = Math.round(ph.ey[i]);
            s.sr[i] = ph.er[i];
            s.sArea[i] = Math.PI * s.sr[i] * s.sr[i];
            s.sMaxMine[i] = rate[i];
            s.sGold[i] = gold[i];
            double d = Physics.distSq(s.sx[i], s.sy[i], Const.WIDTH / 2, Const.HEIGHT / 2);
            if (Physics.lessInt(d, Const.OBSTACLE_GOLD_INCREASE_DISTANCE_1)) { s.sMaxMine[i]++; s.sGold[i] += Const.OBSTACLE_GOLD_INCREASE; }
            if (Physics.lessInt(d, Const.OBSTACLE_GOLD_INCREASE_DISTANCE_2)) { s.sMaxMine[i]++; s.sGold[i] += Const.OBSTACLE_GOLD_INCREASE; }
            s.clearStructure(i);
        }

        for (int p = 0; p < 2; p++) {
            s.hp[p] = queenHp;
            s.score[p] = queenHp;
            s.gold[p] = Const.STARTING_GOLD;
            s.active[p] = true;
        }
        s.qx[0] = 200; s.qy[0] = 200;
        s.qx[1] = Const.WIDTH - 200; s.qy[1] = Const.HEIGHT - 200;
        ph.fixCollisions(s, 999);
        return s;
    }
}
