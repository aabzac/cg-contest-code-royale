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
    /** Income target before the greedy bot starts building towers. */
    public int INCOME_TARGET = 10;
    /** Towers kept before further ones are skipped. */
    public int MAX_TOWERS = 4;
    /** Barracks kept by the greedy bot. */
    public int BARRACKS = 1;
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
