package cr.core;

/**
 * Bit-exact ports of the referee's Vector2 helpers and collision resolution (MapBuilding.kt).
 * Distances compared with numbers use squared values like the referee's Distance class.
 * One instance per thread: results are returned through {@link #rx}/{@link #ry}.
 */
public final class Physics {
    public double rx, ry;

    // Collision scratch: entities in referee order (creeps, queens, obstacles).
    final double[] ex, ey;
    final int[] er, em;
    final boolean[] unit;
    int n;

    public Physics() {
        int cap = GameState.MAX_CREEPS + 2 + GameState.MAX_SITES;
        ex = new double[cap]; ey = new double[cap]; er = new int[cap]; em = new int[cap]; unit = new boolean[cap];
    }

    public static double distSq(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** Distance &lt; d for an integer d (referee: squareDistance &lt; d*d with int multiplication). */
    public static boolean lessInt(double distSq, int d) { return distSq < (double) (d * d); }

    /** Vector2.resizedTo: (vx,vy).normalized * len into rx,ry. */
    public void resize(double vx, double vy, double len) {
        double lsq = vx * vx + vy * vy;
        if (lsq < 1e-6 * 1e-6) {
            rx = 1.0 * len;
            ry = 0.0 * len;
        } else {
            double l = Math.sqrt(lsq);
            rx = (vx / l) * len;
            ry = (vy / l) * len;
        }
    }

    /** Vector2.towards into rx,ry. */
    public void towards(double px, double py, double tx, double ty, double maxDist) {
        if (distSq(px, py, tx, ty) < maxDist * maxDist) {
            rx = tx;
            ry = ty;
        } else {
            resize(tx - px, ty - py, maxDist);
            rx = px + rx;
            ry = py + ry;
        }
    }

    /** Unit.location setter ignores Vector2.Zero (data-class equality, so -0.0 is not zero). */
    static boolean isZero(double x, double y) {
        return Double.compare(x, 0.0) == 0 && Double.compare(y, 0.0) == 0;
    }

    private void set(int k, double x, double y) {
        if (unit[k] && isZero(x, y)) return;
        ex[k] = x;
        ey[k] = y;
    }

    void clear() { n = 0; }

    void add(double x, double y, int radius, int mass, boolean isUnit) {
        ex[n] = x; ey[n] = y; er[n] = radius; em[n] = mass; unit[n] = isUnit;
        n++;
    }

    /** collisionCheck: true if any correction was made. */
    boolean collisionCheck(double acceptableGap) {
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            double rad = er[i];
            double clampDist = em[i] == 0 ? Const.OBSTACLE_GAP + rad : rad;
            double x = ex[i], y = ey[i];
            double minX = clampDist, maxX = Const.WIDTH - clampDist, minY = clampDist, maxY = Const.HEIGHT - clampDist;
            double nx = x < minX ? minX : (x > maxX ? maxX : x);
            double ny = y < minY ? minY : (y > maxY ? maxY : y);
            set(i, nx, ny);

            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                double overlap = er[i] + er[j] + acceptableGap - Math.sqrt(distSq(ex[i], ey[i], ex[j], ey[j]));
                if (overlap <= 1e-6) continue;
                double d1, d2;
                if (em[i] == 0 && em[j] == 0) { d1 = 0.5; d2 = 0.5; }
                else if (em[i] == 0) { d1 = 0.0; d2 = 1.0; }
                else if (em[j] == 0) { d1 = 1.0; d2 = 0.0; }
                else {
                    d1 = (double) em[j] / (em[i] + em[j]);
                    d2 = (double) em[i] / (em[i] + em[j]);
                }
                double vx = ex[j] - ex[i], vy = ey[j] - ey[i];
                double gap = (em[i] == 0 && em[j] == 0) ? 20.0 : 1.0;
                resize(vx, vy, d1 * overlap + ((em[i] == 0 && em[j] > 0) ? 0.0 : gap));
                set(i, ex[i] - rx, ey[i] - ry);
                resize(vx, vy, d2 * overlap + ((em[j] == 0 && em[i] > 0) ? 0.0 : gap));
                set(j, ex[j] + rx, ey[j] + ry);
                changed = true;
            }
        }
        return changed;
    }

    void fixCollisions(int maxIterations) {
        for (int it = 0; it < maxIterations; it++) if (!collisionCheck(0.0)) return;
    }

    /**
     * Entity order of Referee.allEntities. Legacy (pre-918c375): per player its creeps then its
     * queen; repository head: all creeps, then both queens. Then obstacles.
     */
    public static boolean LEGACY_ENTITY_ORDER = false;

    /** Loads creeps, queens and sites of the state in referee order. */
    void load(GameState s) {
        clear();
        if (LEGACY_ENTITY_ORDER) {
            for (int p = 0; p < 2; p++) {
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) addCreep(s, i);
                add(s.qx[p], s.qy[p], Const.QUEEN_RADIUS, Const.QUEEN_MASS, true);
            }
        } else {
            for (int i = 0; i < s.nCreeps; i++) addCreep(s, i);
            for (int p = 0; p < 2; p++) add(s.qx[p], s.qy[p], Const.QUEEN_RADIUS, Const.QUEEN_MASS, true);
        }
        for (int i = 0; i < s.nSites; i++) add(s.sx[i], s.sy[i], s.sr[i], 0, false);
    }

    private void addCreep(GameState s, int i) {
        add(s.cx[i], s.cy[i], Const.CREEP_RADIUS[s.cType[i]], Const.CREEP_MASS[s.cType[i]], true);
    }

    void store(GameState s) {
        int k = 0;
        if (LEGACY_ENTITY_ORDER) {
            for (int p = 0; p < 2; p++) {
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) { s.cx[i] = ex[k]; s.cy[i] = ey[k]; k++; }
                s.qx[p] = ex[k]; s.qy[p] = ey[k]; k++;
            }
        } else {
            for (int i = 0; i < s.nCreeps; i++, k++) { s.cx[i] = ex[k]; s.cy[i] = ey[k]; }
            for (int p = 0; p < 2; p++, k++) { s.qx[p] = ex[k]; s.qy[p] = ey[k]; }
        }
        for (int i = 0; i < s.nSites; i++, k++) { s.sx[i] = ex[k]; s.sy[i] = ey[k]; }
    }

    public void fixCollisions(GameState s, int maxIterations) {
        load(s);
        fixCollisions(maxIterations);
        store(s);
    }
}
