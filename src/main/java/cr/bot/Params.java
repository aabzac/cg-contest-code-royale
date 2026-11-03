package cr.bot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Tunable weights as public fields. {@link #apply(String)} takes "K=v,K2=v"; unknown keys fail.
 * Frozen versions keep their own snapshot (resources cr/bot/versions/vNN.properties).
 */
public final class Params {
    /** Income target before the greedy bot starts building towers (10 in v01/v02; 3 since v03: early towers). */
    public int INCOME_TARGET = 3;
    /** Towers kept before further ones are skipped (4 in v01/v02; 8 since v03). */
    public int MAX_TOWERS = 8;
    /** Barracks kept by the greedy bot. */
    public int BARRACKS = 1;
    /**
     * 1: when the BUILD towards the next site to take would leave the queen standing (a site straight
     * ahead pushes her back, or two sites wedge her), she detours with MOVEs (MainBot.unstick).
     * 0 plays as v01. A P2 targeted fix (docs/local-screen.md); on by default since v02 (2026-10-30).
     * Replaced by ROUTE when that is on (since v04), so it only plays with ROUTE=0.
     */
    public int UNSTICK = 1;
    /** Experimental final approach toward the same site, favoring the following site; off in v03 and v04 (ROUTE replaces it when on). */
    public int APPROACH = 0;
    /**
     * 1: the walk to the next site to take is planned with the sites only (MainBot's RoutePlanner):
     * a heading fan around obstacles and the landing point for the next target, replacing UNSTICK
     * and APPROACH. Off in v03; on by default since v04 (2026-11-02: P2 docs/data/exp-2026-11-01-route,
     * selected under P1, docs/arena-uploads.md).
     */
    public int ROUTE = 1;
    /**
     * Own mines the greedy bot builds before its knight barracks (0: the barracks first, as v01-v04).
     * From the study of 2026-11-03 (docs/data/study-2026-11-03-losses): every stronger group opens
     * with a mine and builds the barracks on turn 7-13.
     */
    public int MINES_FIRST = 0;
    /** Tower HP below which the queen tops up an own tower she touches (700 in v01-v04). The idle queen still pumps her home tower. */
    public int TOWER_HP = 700;
    /**
     * 1: while the income is below INCOME_TARGET, own mines below their maximum are targets too: the
     * queen walks to the nearest site to take, a free gold site or such a mine, and upgrades it on the
     * touch (0: only free gold sites are targets, as v01-v04; a mine is upgraded only when passed).
     */
    public int UPGRADE = 0;
    public long SEED = 1;
    public int DEBUG = 0;

    public Params copy() {
        Params p = new Params();
        p.apply(toString());
        return p;
    }

    public Params apply(String spec) {
        if (spec == null || spec.isBlank()) return this;
        for (String kv : spec.split(",")) {
            if (kv.isBlank()) continue;
            int eq = kv.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("bad param " + kv);
            String k = kv.substring(0, eq).trim(), v = kv.substring(eq + 1).trim();
            try {
                Field f = Params.class.getField(k);
                if (f.getType() == int.class) f.setInt(this, Integer.parseInt(v));
                else if (f.getType() == long.class) f.setLong(this, Long.parseLong(v));
                else if (f.getType() == double.class) f.setDouble(this, Double.parseDouble(v));
                else if (f.getType() == boolean.class) f.setBoolean(this, Boolean.parseBoolean(v));
                else throw new IllegalArgumentException("unsupported type " + k);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalArgumentException("unknown param " + k, e);
            }
        }
        return this;
    }

    /** Full effective configuration "K=v,..." in field order. */
    @Override
    public String toString() {
        List<String> r = new ArrayList<>();
        for (Field f : Params.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try { r.add(f.getName() + "=" + f.get(this)); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        return String.join(",", r);
    }
}
