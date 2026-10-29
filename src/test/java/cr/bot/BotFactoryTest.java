package cr.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotFactoryTest {
    @Test
    void frozenSnapshotIsIndependentOfLiveDefaults() {
        Params v01 = BotFactory.snapshot("v01");
        assertEquals(10, v01.INCOME_TARGET);
        assertEquals(4, v01.MAX_TOWERS);
        assertEquals(1, v01.BARRACKS);
        // every Params field must be present in the snapshot, so new live defaults cannot leak in silently
        String spec = new java.util.Properties() {{
            try (java.io.InputStream in = BotFactory.class.getResourceAsStream("versions/v01.properties")) { load(in); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }}.getProperty("PARAMS");
        java.util.Set<String> keys = new java.util.TreeSet<>(), fields = new java.util.TreeSet<>();
        for (String kv : spec.split(",")) keys.add(kv.substring(0, kv.indexOf('=')).trim());
        for (java.lang.reflect.Field f : Params.class.getFields())
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) fields.add(f.getName());
        assertEquals(fields, keys);
    }

    @Test
    void unknownBotsAndParamsFail() {
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v99"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?NO_SUCH=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.snapshot("v99"));
    }

    /** Their parameters are fixed when they are built: an override would be dropped silently. */
    @Test
    void programsAndV00TakeNoParameters() {
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("submit?MAX_TOWERS=3"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("boss?MAX_TOWERS=3"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("boss4?MAX_TOWERS=3"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v00?MAX_TOWERS=3"));
    }

    @Test
    void overridesApplyOnTopOfSnapshot() {
        assertEquals("v01", BotFactory.create("v01?MAX_TOWERS=2").name());
    }
}
