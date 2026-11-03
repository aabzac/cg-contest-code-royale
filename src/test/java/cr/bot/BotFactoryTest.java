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
        assertEquals(0, v01.UNSTICK);
        Params v02 = BotFactory.snapshot("v02");
        assertEquals(v01.toString().replace("UNSTICK=0", "UNSTICK=1"), v02.toString());
        Params v03 = BotFactory.snapshot("v03");
        assertEquals(v02.toString().replace("INCOME_TARGET=10", "INCOME_TARGET=3").replace("MAX_TOWERS=4", "MAX_TOWERS=8"), v03.toString());
        Params v04 = BotFactory.snapshot("v04");
        assertEquals(v03.toString().replace("ROUTE=0", "ROUTE=1"), v04.toString());
        Params v05 = BotFactory.snapshot("v05");
        assertEquals(v04.toString().replace("MINES_FIRST=0", "MINES_FIRST=2"), v05.toString());
        Params v06 = BotFactory.snapshot("v06");
        assertEquals(v05.toString().replace("MAX_TOWERS=8", "MAX_TOWERS=6").replace("BARRACKS=1", "BARRACKS=2").replace("BARRACKS_AFTER_TOWERS=0", "BARRACKS_AFTER_TOWERS=4")
            .replace("TRAIN_SYNC=0", "TRAIN_SYNC=1").replace("THREAT_TOWERS_NEAR=0", "THREAT_TOWERS_NEAR=2").replace("THREAT_RADIUS=400", "THREAT_RADIUS=500"), v06.toString());
        Params v07 = BotFactory.snapshot("v07");
        assertEquals(v06.toString().replace("KITE=0", "KITE=1"), v07.toString());
        assertEquals(new Params().toString(), v07.toString(), "the live defaults are v07's until the next champion");
        // every Params field must be present in each snapshot, so new live defaults cannot leak in silently
        for (String version : new String[]{"v01", "v02", "v03", "v04", "v05", "v06", "v07"}) {
            String spec = new java.util.Properties() {{
                try (java.io.InputStream in = BotFactory.class.getResourceAsStream("versions/" + version + ".properties")) { load(in); }
                catch (java.io.IOException e) { throw new IllegalStateException(e); }
            }}.getProperty("PARAMS");
            java.util.Set<String> keys = new java.util.TreeSet<>(), fields = new java.util.TreeSet<>();
            for (String kv : spec.split(",")) keys.add(kv.substring(0, kv.indexOf('=')).trim());
            for (java.lang.reflect.Field f : Params.class.getFields())
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) fields.add(f.getName());
            assertEquals(fields, keys, version);
        }
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
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("old?MAX_TOWERS=3"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v00?MAX_TOWERS=3"));
    }

    @Test
    void overridesApplyOnTopOfSnapshot() {
        assertEquals("v01", BotFactory.create("v01?MAX_TOWERS=2").name());
    }

    /** Experiment records name what played: every parameter of an in-process bot, a program's source hash. */
    @Test
    void describeGivesTheEffectiveConfiguration() {
        assertEquals("current " + new Params().apply("MAX_TOWERS=3"), BotFactory.describe("current?MAX_TOWERS=3"));
        assertEquals("v01 " + BotFactory.snapshot("v01").apply("MAX_TOWERS=2"), BotFactory.describe("v01?MAX_TOWERS=2"));
        assertEquals("v02 " + BotFactory.snapshot("v02"), BotFactory.describe("v02"));
        assertEquals("v03 " + BotFactory.snapshot("v03"), BotFactory.describe("v03"));
        assertEquals("v04 " + BotFactory.snapshot("v04"), BotFactory.describe("v04"));
        assertEquals("v05 " + BotFactory.snapshot("v05"), BotFactory.describe("v05"));
        assertEquals("v06 " + BotFactory.snapshot("v06"), BotFactory.describe("v06"));
        assertEquals("v07 " + BotFactory.snapshot("v07"), BotFactory.describe("v07"));
        assertEquals("proc:java -cp x Player", BotFactory.describe("proc:java -cp x Player"));
        assertTrue(BotFactory.describe("boss").matches("boss sha256=([0-9a-f]{64}|missing)"));
        assertTrue(BotFactory.describe("old").matches("old sha256=([0-9a-f]{64}|missing)"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.describe("v99"));
    }
}
