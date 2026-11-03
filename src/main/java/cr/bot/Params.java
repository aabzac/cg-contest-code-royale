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
    /** Towers kept before further ones are skipped (4 in v01/v02; 8 in v03-v05; 6 since v06, with the threat towers counted). */
    public int MAX_TOWERS = 6;
    /** Knight barracks kept by the greedy bot (1 in v01-v05; 2 since v06). */
    public int BARRACKS = 2;
    /**
     * Own towers that must stand before the second and every later barracks is built (0: the
     * barracks come one after the other, before the mines of the income phase). Does nothing with
     * BARRACKS=1. The army direction of 2026-11-03 (docs/bot-architecture.html, the directions section);
     * 4 since v06 (P2 docs/data/exp-2026-11-03-army and -safety).
     */
    public int BARRACKS_AFTER_TOWERS = 4;
    /**
     * Where the second and every later barracks goes: 0, the ladder's usual site (nearest to the
     * midpoint of the queen and home); otherwise the free site, not under an enemy tower, nearest to
     * the point this many percent of the way from the home corner to the enemy's corner (50: the
     * centre of the board). Knights from there walk less under the enemy's towers.
     */
    public int BARRACKS_FORWARD = 0;
    /**
     * 1: TRAIN only when every own barracks is idle and the gold covers all of them, so two barracks
     * release their waves on the same turn (one wave of 8 knights rather than two of 4). With one
     * barracks it plays as 0 (train whenever affordable). On since v06.
     */
    public int TRAIN_SYNC = 1;
    /** Enemy knights closer than this to the queen are the threat that stops her mines and sends her to a tower (250 in v01-v05). */
    public int THREAT_RANGE = 250;
    /**
     * Under threat, when fewer than this many own towers stand within THREAT_RADIUS of the queen, she
     * builds a new tower on the nearest free site within that radius (not under an enemy tower)
     * instead of topping up the tower she touches or walking to her nearest tower. 0: v05's flight
     * (the nearest own tower, topped up). The queen's safety direction of 2026-11-03: in v05's arena
     * deaths 1029 of 1103 hit turns were spent at one tower topping it up, while a tower deals 3
     * damage a turn at 200 HP and 4 at 700, so a second tower doubles the kill rate and a top-up
     * adds a quarter (docs/data/study-2026-11-03-queen-safety). 2 since v06 (P2 docs/data/exp-2026-11-03-safety:
     * S 0.64-0.65 against v05 with the army flags).
     */
    public int THREAT_TOWERS_NEAR = 2;
    /** The radius around the queen for THREAT_TOWERS_NEAR: own towers counted and free sites considered (500 since v06). */
    public int THREAT_RADIUS = 500;
    /**
     * Income target once the ladder is complete (0: none, the idle queen pumps her home tower as in
     * v01-v06). Below it, with no knight near, she walks to the nearest of a free gold site (a new mine)
     * and an own mine below its maximum (an upgrade) and builds there; the touch rule builds a mine on a
     * free gold site too. The economy direction of 2026-11-03 (docs/data/exp-2026-11-03-late-income): the
     * opponents who beat v06 hold income 8 at turn 100 against our 3 and release twice the waves, while
     * our queen spends 74 turns a game topping up towers after the ladder.
     */
    public int INCOME_LATE = 0;
    /**
     * 1: kiting. With enemy knights within KITE_RANGE and nothing for the defence rule to build, the
     * queen MOVEs on the heading that leaves her farthest from the nearest knight after the predicted
     * step, staying under her own towers' cover when she is covered now (KITE_COVER=1) and out of enemy
     * tower range, instead of standing at her tower topping it up. 0: v01-v06. From the study of
     * 2026-11-03 (docs/data/study-2026-11-03-arena-winners): the arena opponents who beat v06 MOVE on
     * 42 % of their turns under knights and lose a third less HP per contact turn; v06 never does. On since
     * v07 (P2 docs/data/exp-2026-11-03-kite: S 0.68-0.69 against v06, +3 pp against field).
     */
    public int KITE = 2;  // 1: v07's rule; 2: bound to the towers (KITE_PULL, KITE_MAX), since v08
    /** Knights closer than this start the kiting (250: contact; 400-500: before contact, as the winners do). */
    public int KITE_RANGE = 250;
    /** 1: a kiting step must stay within an own tower's range when the queen is covered now (KITE=1 only). */
    public int KITE_COVER = 1;
    /**
     * KITE=2, kiting bound to the towers (2026-11-03, after v07's arena games: with KITE=1 the queen ran
     * out of her towers' cover and the knights chased her for 50-130 turns without dying): every step must
     * end within an own tower's range, the score is the distance to the nearest knight minus KITE_PULL
     * percent of the distance to the nearest own tower, and with no own tower in reach the flight of v06
     * applies. KITE_MAX > 0 caps the consecutive kiting turns: after that many the other rules get a turn
     * (the top-up at the tower she stands by).
     */
    public int KITE_PULL = 100;
    public int KITE_MAX = 0;
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
     * Own mines the greedy bot builds before its knight barracks (0: the barracks first, as v01-v04;
     * 2 by default since v05, 2026-11-03: P2 docs/data/exp-2026-11-03-mines-first, S 0.76 against v04).
     * From the study of 2026-11-03 (docs/data/study-2026-11-03-losses): every stronger group opens
     * with a mine and builds the barracks on turn 7-13.
     */
    public int MINES_FIRST = 2;
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
