package cr.runner;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaysAsTest {
    /** A version is v + two digits that the factory knows in-process: a label of anything else has no play to compare. */
    @Test
    void aVersionIsAFrozenBotOfThisCheckout() {
        assertTrue(PlaysAs.known("v00"));
        assertTrue(PlaysAs.known("v14"));
        assertFalse(PlaysAs.known("v99"));
        assertFalse(PlaysAs.known("v1"));
        assertFalse(PlaysAs.known("current"));
        assertFalse(PlaysAs.known("submit"));
        assertFalse(PlaysAs.known("boss"));
        assertFalse(PlaysAs.known("v14?HOLD_OVERLAP=0"));
        List<String> all = PlaysAs.versions();
        assertEquals("v00", all.get(0));
        assertTrue(all.contains("v13") && all.contains("v14"), all.toString());
        assertEquals(all.stream().sorted().distinct().toList(), all);
    }

    /** A different command is a difference; a missing answer or a game cut short says nothing about the version. */
    @Test
    void aMissingAnswerIsInconclusiveNotDifferent() {
        List<String> version = List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 MOVE 1 2 | TRAIN", "1 WAIT | TRAIN");
        assertEquals(PlaysAs.Verdict.SAME, PlaysAs.verdict(version, version));
        assertEquals(PlaysAs.Verdict.DIFFERENT,
            PlaysAs.verdict(List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 BUILD 3 TOWER | TRAIN", "1 WAIT | TRAIN"), version));
        assertEquals(PlaysAs.Verdict.INCONCLUSIVE, PlaysAs.verdict(List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 <none>"), version));
        assertEquals(PlaysAs.Verdict.INCONCLUSIVE, PlaysAs.verdict(List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN"), version));
        assertEquals("seed 398 seat 0 line 2: 0 BUILD 3 TOWER | TRAIN, the version: 0 MOVE 1 2 | TRAIN",
            PlaysAs.describe(398, 0, List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 BUILD 3 TOWER | TRAIN"), version));
        assertEquals("seed 398 seat 1 line 2: <end>, the version: 0 MOVE 1 2 | TRAIN",
            PlaysAs.describe(398, 1, List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN"), version));
    }

    /** The first maps tell v14 from v13 and v12: either of them in v14's place plays another game than v14's mirror. */
    @Test
    void theMapsTellAVersionFromTheOthers() {
        PlaysAs.Plan p = PlaysAs.plan("v14", 3, List.of("v13", "v12"));
        assertEquals(List.of(398L, 795L, 1192L), p.seeds);
        assertEquals(2, p.told);
        assertTrue(p.notTold.isEmpty());
        assertEquals(p.seeds.size(), p.mirror.size(), "the mirror game of every map is kept for the comparison");
        assertTrue(PlaysAs.differs("v13", "v14", 398, new HashMap<>()));
        assertFalse(PlaysAs.differs("v14", "v14", 398, new HashMap<>()));
    }

    /**
     * v11 is v10 with a targeted fix that changes few maps: the first three maps do not tell them apart, so a
     * map that does is added (a file of v10 would pass as v11 without it).
     */
    @Test
    void aRarelyDifferentVersionGetsAMapThatTellsItApart() {
        HashMap<Long, List<String>> cache = new HashMap<>();
        for (long seed : List.of(398L, 795L, 1192L)) assertFalse(PlaysAs.differs("v10", "v11", seed, cache), "seed " + seed);
        PlaysAs.Plan p = PlaysAs.plan("v11", 3, List.of("v10"));
        assertEquals(4, p.seeds.size(), p.seeds.toString());
        assertTrue(PlaysAs.differs("v10", "v11", p.seeds.get(3), cache));
        assertEquals(1, p.told);
        assertTrue(p.notTold.isEmpty());
    }

    /** A version that plays as the spec on every map searched is reported: the search has an end. */
    @Test
    void aVersionNoMapTellsApartIsReported() {
        PlaysAs.Plan p = PlaysAs.plan("v00", 1, List.of("v00"), 5);
        assertEquals(List.of(398L), p.seeds);
        assertEquals(0, p.told);
        assertEquals(List.of("v00"), p.notTold);
    }

    @Test
    void theVerdictIsOneJsonLine() {
        assertEquals("{\"file\": \"a/Player.java\", \"sha256\": \"ab\", \"as\": \"v14\", \"known\": true, \"games\": 6, \"same\": 5, "
                + "\"different\": 1, \"inconclusive\": 0, \"first\": \"seed 1 seat 0 line 2: \\\"x\\\", the version: y\", \"toldFrom\": 13, "
                + "\"notToldFrom\": [\"v10\"]}",
            PlaysAs.json("a/Player.java", "ab", "v14", true, 6, 5, 1, 0, "seed 1 seat 0 line 2: \"x\", the version: y", 13, List.of("v10")));
        assertEquals("{\"file\": \"f\", \"sha256\": \"ab\", \"as\": \"v15\", \"known\": false, \"games\": 0, \"same\": 0, \"different\": 0, "
                + "\"inconclusive\": 0, \"first\": null, \"toldFrom\": 0, \"notToldFrom\": []}",
            PlaysAs.json("f", "ab", "v15", false, 0, 0, 0, 0, null, 0, List.of()));
    }
}
