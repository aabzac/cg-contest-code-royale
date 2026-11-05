package cr.runner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkRunnerTest {
    /** PV3 (2026-11-05): a block's maps are seedBase + seedStride * k, and the report says so for bench_pair.py. */
    @Test
    void theMapsFollowTheSeedStride(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("strided.json");
        BenchmarkRunner.main(new String[]{"v00", "v00", "3", "1", "1000000", "50", out.toString(), "397"});
        JsonObject rep = JsonParser.parseString(Files.readString(out)).getAsJsonObject();
        assertEquals(1000000, rep.get("seedBase").getAsLong());
        assertEquals(397, rep.get("seedStride").getAsLong());
        assertEquals(Set.of(1000000L, 1000397L, 1000794L), seeds(rep.getAsJsonArray("games")));
        assertEquals(6, rep.getAsJsonArray("games").size(), "each map on both seats");
    }

    @Test
    void withoutAStrideTheSeedsAreConsecutive(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("plain.json");
        BenchmarkRunner.main(new String[]{"v00", "v00", "2", "1", "5000", "50", out.toString()});
        JsonObject rep = JsonParser.parseString(Files.readString(out)).getAsJsonObject();
        assertEquals(1, rep.get("seedStride").getAsLong());
        assertEquals(Set.of(5000L, 5001L), seeds(rep.getAsJsonArray("games")));
    }

    @Test
    void aStrideBelowOneIsRefused(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
            () -> BenchmarkRunner.main(new String[]{"v00", "v00", "1", "1", "5000", "50", dir.resolve("x.json").toString(), "0"}));
    }

    private static Set<Long> seeds(JsonArray games) {
        Set<Long> s = new TreeSet<>();
        for (JsonElement g : games) s.add(g.getAsJsonObject().get("seed").getAsLong());
        return s;
    }
}
