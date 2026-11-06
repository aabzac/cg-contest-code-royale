package cr.bot.field;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Tunables of {@link FieldBot} as public fields; {@link #apply(String)} takes "K=v,K2=v" as cr.bot.Params does. */
public final class FieldParams {
    /**
     * The rival R0 (spec "rival"): the field ladder with v08's kiting, two mines first, top-ups to 700 and at most
     * two giants from turn 80; P2's gate G5 since PV4 (2026-11-06, docs/data/exp-2026-11-06-rival/).
     */
    public static final String RIVAL = "KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2";

    /** Income the bot upgrades its mines and takes gold sites towards, once the towers stand. */
    public int INCOME = 8;
    /** Mines taken before the tower line is completed. */
    public int MINES = 3;
    /** Towers kept; further free sites are not taken for towers. */
    public int TOWERS = 4;
    /** A touched own tower below this is topped up (600: 450 and 800 lose 20 and 23 pp against v06 in the calibration). */
    public int TOWER_HP = 600;
    /** 1: a giant barracks once the enemy holds GIANT_TOWERS towers; giants are trained before knights while the enemy has towers (off by default: the giants cost the knight waves and 20 pp against v06). */
    public int GIANTS = 0;
    public int GIANT_TOWERS = 2;
    /** Under threat (enemy knights closer than THREAT_RANGE), fewer own towers than this within THREAT_RADIUS makes her build another. */
    public int THREAT_TOWERS_NEAR = 2;
    public int THREAT_RADIUS = 500;
    public int THREAT_RANGE = 250;
    /**
     * 1: under enemy knights within KITE_RANGE the queen kites within her towers' cover, as v08's KITE=2 (every
     * step ends within an own tower's range; the score is the distance to the nearest knight minus KITE_PULL
     * percent of the distance to the nearest own tower). 0 (default): she stands at her towers, as the field
     * opponent of PV2. Added 2026-11-06 for an opponent that plays like the strongest Silver players, who kite
     * and expand (docs/data/exp-2026-11-06-rival/).
     */
    public int KITE = 0;
    public int KITE_RANGE = 250;
    public int KITE_PULL = 100;
    /** Mines before the knight barracks (1: the field opponent's opening, a mine first; the strongest Silver players open with two). */
    public int MINES_FIRST = 1;
    /** With GIANTS=1: the giant barracks not before this turn (0: whenever the enemy holds GIANT_TOWERS towers; the strongest Silver players build it around turn 84). */
    public int GIANT_TURN = 0;
    /** With GIANTS=1: at most this many giant trainings in a game (0: no limit; the strongest Silver players train one or two). */
    public int GIANTS_MAX = 0;
    public long SEED = 1;

    public FieldParams apply(String spec) {
        if (spec == null || spec.isBlank()) return this;
        for (String kv : spec.split(",")) {
            if (kv.isBlank()) continue;
            int eq = kv.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("bad param " + kv);
            String k = kv.substring(0, eq).trim(), v = kv.substring(eq + 1).trim();
            try {
                Field f = FieldParams.class.getField(k);
                if (f.getType() == int.class) f.setInt(this, Integer.parseInt(v));
                else if (f.getType() == long.class) f.setLong(this, Long.parseLong(v));
                else throw new IllegalArgumentException("unsupported type " + k);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalArgumentException("unknown param " + k, e);
            }
        }
        return this;
    }

    @Override
    public String toString() {
        List<String> r = new ArrayList<>();
        for (Field f : FieldParams.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try { r.add(f.getName() + "=" + f.get(this)); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        return String.join(",", r);
    }
}
