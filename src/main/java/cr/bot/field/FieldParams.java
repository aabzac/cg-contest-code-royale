package cr.bot.field;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Tunables of {@link FieldBot} as public fields; {@link #apply(String)} takes "K=v,K2=v" as cr.bot.Params does. */
public final class FieldParams {
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
