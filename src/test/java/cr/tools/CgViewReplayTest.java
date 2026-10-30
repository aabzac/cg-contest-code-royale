package cr.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CgViewReplayTest {
    @TempDir Path temp;

    private static void register(CgReplay.View view, int entity, String json) {
        view.registered.put(entity, JsonParser.parseString(json).getAsJsonObject());
    }

    private static CgReplay fixture() {
        CgReplay replay = new CgReplay();
        replay.gameId = 42;
        replay.seed = 17;
        replay.pseudo = new String[]{"first", "second"};
        replay.agentId = new long[]{101, 202};
        replay.ranks = new int[]{1, 0};
        replay.scores = new double[]{-1, 75};
        replay.init = new CgReplay.View();
        CgReplay.View init = replay.init;
        register(init, 3, "{type:Site,id:0}");
        register(init, 4, "{type:Site,id:1}");
        register(init, 29, "{type:Queen}");
        register(init, 32, "{type:Queen}");
        init.entities.addAll(List.of("U 3 1 x 100 y 200", "U 4 1 x 900 y 800",
                "U 27 1 i Unite_Base_Rouge", "U 30 1 i Unite_Base_Bleu", "U 29 1 x 60 y 70", "U 32 1 x 1800 y 900",
                "U 39 0 ff 'Arial Black' x 700 y 1040 T 100", "U 47 0 x 1020 y 1040 T 100",
                "U 38 1 x 553 y 1053 T 75", "U 46 1 x 1771 y 1053 T 75"));
        init.texts.put(3, List.of("Radius: 70", "Remaining gold: 200"));
        init.texts.put(4, List.of("Radius: 80", "Remaining gold: 300"));
        CgReplay.Turn first = new CgReplay.Turn();
        first.stdout[0] = "BUILD 0 MINE\nTRAIN\n";
        first.stdout[1] = "BUILD 1 TOWER\nTRAIN\n";
        first.view = new CgReplay.View();
        first.view.entities.addAll(List.of("U 100 0.5 x 100 y 160 i Mine_Rouge v 1", "U 101 0.5 x 900 y 800 I TB01,TB02 v 1",
                "U 200 1 i Unite_Base_Rouge a 1", "U 202 1 x 300 y 400", "U 39 0 T 102"));
        first.view.texts.put(3, List.of("Radius: 70", "Remaining gold: 198", "MINE (+2)"));
        first.view.texts.put(4, List.of("Radius: 80", "Remaining gold: 300", "TOWER", "Range: 300", "Health: 200"));
        register(first.view, 202, "{type:KNIGHT}");
        first.view.texts.put(200, List.of("Health: 25"));
        replay.turns.add(first);
        CgReplay.Turn second = new CgReplay.Turn();
        second.stdout[1] = "BUILD 0 BARRACKS-ARCHER\nTRAIN 0\n";
        second.summary = "$0 failed to provide 2 lines of output in time.";
        second.view = new CgReplay.View();
        second.view.entities.addAll(List.of("U 100 0.51 v 0", "U 101 0.51 v 0",
                "U 203 0.5 x 100 y 200 i Caserne_Bleu v 1", "U 204 0 x 100 y 254 i Caserne_Bleu_Jauge v 1",
                "U 202 1 x 350 y 450", "U 200 1 a 0", "U 32 1 x 1700", "U 300 1 i Unite_Base_Bleu",
                "U 302 1 x 800 y 700", "U 47 0 T 20"));
        second.view.texts.put(3, List.of("Radius: 70", "Remaining gold: 198", "BARRACKS (ARCHER)"));
        second.view.texts.put(4, List.of("Radius: 80", "Remaining gold: 300"));
        second.view.texts.put(200, List.of("Health: 0"));
        second.view.texts.put(300, List.of("Health: 200"));
        register(second.view, 302, "{type:GIANT}");
        replay.turns.add(second);
        return replay;
    }

    @Test void recordedDeltasKeepOwnershipDeathsAndMissingCommandWithoutSimulating() {
        CgViewReplay.Replay decoded = CgViewReplay.decode(fixture());
        assertEquals(3, decoded.frames.size());
        assertArrayEquals(new int[]{0, 100, 200, 70}, decoded.sites.get(0));
        CgViewReplay.Frame first = decoded.frames.get(1);
        assertArrayEquals(new Integer[]{0, 0, 2, null, 198}, first.structures.get(0));
        assertArrayEquals(new Integer[]{1, 1, 200, 300, 300}, first.structures.get(1));
        assertArrayEquals(new Integer[]{0, 0, 300, 400, 25}, first.creeps.get(0));
        assertArrayEquals(new Integer[]{102, 100}, first.gold);
        CgViewReplay.Frame last = decoded.frames.get(2);
        assertArrayEquals(new Integer[]{2, 1, null, 1, 198}, last.structures.get(0));
        assertArrayEquals(new Integer[]{-1, -1, null, null, 300}, last.structures.get(1));
        assertEquals(Boolean.TRUE, last.training.get(0));
        assertEquals(1, last.creeps.size(), "dead creep must disappear");
        assertArrayEquals(new Integer[]{1, 2, 800, 700, 200}, last.creeps.get(0));
        assertArrayEquals(new Integer[]{1700, 900}, last.queens[1], "unchanged y must be retained");
        assertNull(last.cmds[0], "a timeout is not an invented WAIT command");
        assertTrue(last.summary.contains("failed to provide"));
        assertEquals(1, decoded.winner);
        assertEquals(-1, decoded.scores[0]);
        assertEquals(75, last.hp[0], "disqualification score is not queen HP");
        assertTrue(decoded.warnings.isEmpty());
    }

    @Test void localReplayIsPassedThroughAndMalformedReplayFails() throws IOException {
        Path local = temp.resolve("local.json");
        Files.writeString(local, "{sites:[[0,10,20,30]],bots:['a','b'],frames:[{turn:0}],winner:0}");
        JsonObject view = CgViewReplay.load(local);
        assertEquals("local", view.get("format").getAsString());
        assertEquals(1, view.getAsJsonArray("frames").size());
        Files.writeString(local, "{wrong:true}");
        assertThrows(IOException.class, () -> CgViewReplay.load(local));
    }

    @Test void allAvailableArenaGamesKeepRecordedOutcomesCommandsAndVisibleState() throws IOException {
        Path root = Path.of("replays/arena");
        assumeTrue(Files.isDirectory(root), "optional ignored replay corpus");
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(p -> p.getFileName().toString().matches("\\d+\\.json") && p.getParent().endsWith("games")).toList();
        }
        assumeTrue(!files.isEmpty(), "optional ignored replay corpus");
        int timeouts = 0, ties = 0;
        for (Path file : files) {
            CgReplay source = CgReplay.load(file);
            CgViewReplay.Replay decoded = CgViewReplay.decode(source);
            String name = file.toString();
            assertEquals(source.turns.size() + 1, decoded.frames.size(), name);
            assertArrayEquals(source.ranks, decoded.ranks, name);
            assertArrayEquals(source.scores, decoded.scores, name);
            assertTrue(decoded.warnings.isEmpty(), () -> name + " " + decoded.warnings);
            assertEquals(24, decoded.sites.size(), name);
            for (int turn = 0; turn < decoded.frames.size(); turn++) {
                CgViewReplay.Frame frame = decoded.frames.get(turn);
                assertEquals(turn, frame.turn, name);
                assertNotNull(frame.gold[0], name); assertNotNull(frame.gold[1], name);
                assertNotNull(frame.hp[0], name); assertNotNull(frame.hp[1], name);
                assertEquals(decoded.sites.size(), frame.structures.size(), name);
                for (Integer[] creep : frame.creeps) for (Integer field : creep) assertNotNull(field, name);
                if (turn > 0) assertArrayEquals(source.turns.get(turn - 1).stdout, frame.cmds, name);
            }
            CgViewReplay.Frame last = decoded.frames.get(decoded.frames.size() - 1);
            if (source.scores[0] >= 0 && source.scores[1] >= 0) {
                assertEquals(source.scores[0], last.hp[0].doubleValue(), name);
                assertEquals(source.scores[1], last.hp[1].doubleValue(), name);
            } else timeouts++;
            if (source.scores[0] == source.scores[1]) ties++;
        }
        System.out.printf("Recorded viewer corpus: %d games, %d disqualifications, %d equal-score games%n", files.size(), timeouts, ties);
    }
}
