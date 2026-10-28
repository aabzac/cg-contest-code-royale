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
        Params probe = new Params();
        probe.INCOME_TARGET = -999;
        probe.apply(new java.util.Properties() {{
            try (java.io.InputStream in = BotFactory.class.getResourceAsStream("versions/v01.properties")) { load(in); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }}.getProperty("PARAMS"));
        assertEquals(v01.toString(), probe.toString());
    }

    @Test
    void unknownBotsAndParamsFail() {
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v99"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?NO_SUCH=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.snapshot("v99"));
    }

    @Test
    void overridesApplyOnTopOfSnapshot() {
        assertEquals("v01", BotFactory.create("v01?MAX_TOWERS=2").name());
    }
}
