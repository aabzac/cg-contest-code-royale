package cr.runner;

import cr.referee.Referee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCheckTest {
    private static final List<String> LINES = List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN");

    @Test
    void firstDifferenceFindsTheFirstDifferingLine() {
        assertEquals(-1, ProcessCheck.firstDifference(List.of("a", "b"), List.of("a", "b")));
        assertEquals(1, ProcessCheck.firstDifference(List.of("a", "b"), List.of("a", "c")));
        assertEquals(2, ProcessCheck.firstDifference(List.of("a", "b"), List.of("a", "b", "c")));
        assertEquals(0, ProcessCheck.firstDifference(List.of(), List.of("a")));
    }

    @Test
    void artifactParamsComeFromTheSingleParamsLiteral() {
        String empty = "    public static final String PARAMS = \"\";\n";
        assertEquals("", ProcessCheck.artifactParams("class Player {\n" + empty + "}\n"));
        assertEquals("MAX_TOWERS=3", ProcessCheck.artifactParams("public static final String PARAMS = \"MAX_TOWERS=3\";"));
        assertThrows(IllegalStateException.class, () -> ProcessCheck.artifactParams("class Player {}"));
        assertThrows(IllegalStateException.class, () -> ProcessCheck.artifactParams(empty + empty));
    }

    /** procCheck rebuilds the artifact first, so a rerun must pass the same params. */
    @Test
    void theRerunCommandRebuildsTheSameArtifact() {
        assertEquals("./gradlew procCheck -Popp=boss", ProcessCheck.rerunCommand("", "boss"));
        assertEquals("./gradlew procCheck -Pparams=MAX_TOWERS=3,BARRACKS=2 -Popp=v01", ProcessCheck.rerunCommand("MAX_TOWERS=3,BARRACKS=2", "v01"));
        assertEquals("./gradlew procCheck \"-Pparams=MAX_TOWERS=3, BARRACKS=2\" \"-Popp=proc:java -cp x Bot\"",
            ProcessCheck.rerunCommand("MAX_TOWERS=3, BARRACKS=2", "proc:java -cp x Bot"));
    }

    @Test
    void aFullGameWithTheSameLinesAndOnlySlowReportsPasses() {
        ProcessCheck.Game v = ProcessCheck.judge(game(LINES), game(LINES), 0, null, 0, false, List.of("SLOW turn 3 41ms"));
        assertTrue(v.compared && v.same);
        assertEquals(1, v.slowLines);
        assertEquals(List.of(), v.notes);
        assertTrue(passes(v));
    }

    @Test
    void differentLinesFail() {
        ProcessCheck.Game v = ProcessCheck.judge(game(LINES), game(List.of("0 WAIT | TRAIN", "1 MOVE 0 0 | TRAIN")), 0, null, 0, false, List.of());
        assertTrue(v.compared);
        assertFalse(v.same);
        assertFalse(passes(v));
    }

    /** A game the opponent left early compares only a few turns, so it cannot count as the same. */
    @Test
    void aGameWhereTheOpponentWasKilledIsNotCompared() {
        for (int run = 0; run < 2; run++) {
            Referee.Result r = game(LINES), ref = game(LINES);
            (run == 0 ? r : ref).error[1] = "Expected TRAIN on the second line";
            ProcessCheck.Game v = ProcessCheck.judge(r, ref, 0, null, 0, false, List.of());
            assertFalse(v.compared, "opponent killed in run " + run);
            assertTrue(v.notes.get(0).startsWith("not compared: the opponent was killed"), v.notes.get(0));
            ProcessCheck.Tally t = new ProcessCheck.Tally();
            t.add(v);
            assertEquals(1, t.notCompared);
            assertFalse(t.passed());
        }
    }

    /** Both runs end the same way when the bot itself prints an invalid command: the kill still fails. */
    @Test
    void theArtifactKilledForItsOwnOutputFailsEvenWhenCurrentMatches() {
        Referee.Result r = game(LINES), ref = game(LINES);
        r.error[0] = ref.error[0] = "Didn't understand command: FOO";
        ProcessCheck.Game v = ProcessCheck.judge(r, ref, 0, null, 0, false, List.of());
        assertTrue(v.same);
        assertEquals(1, v.kills);
        assertEquals("killed on turn 0: Didn't understand command: FOO", v.notes.get(0));
        assertEquals("in-process current killed on turn 0: Didn't understand command: FOO", v.notes.get(1));
        assertFalse(passes(v));
    }

    @Test
    void aTimeoutSaysWhatTheArtifactSawOfIt() {
        Referee.Result r = game(List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 WAIT | TRAIN", "1 <none>"));
        r.timeouts[1] = 1;
        r.error[1] = "Timeout";
        ProcessCheck.Game v = ProcessCheck.judge(r, game(LINES), 1, null, 2, false, List.of("SLOW turn 1 81ms"));
        assertEquals(1, v.timeouts);
        assertEquals(1, v.kills);
        assertEquals(2, v.lateLines);
        assertEquals(1, v.anyTimeouts);
        assertEquals("killed on turn 1: Timeout; 2 line(s) arrived after the deadline; the artifact's own timing: SLOW turn 1 81ms", v.notes.get(0));
        assertFalse(passes(v));

        r = game(LINES);
        r.timeouts[1] = 1;
        r.error[1] = "Timeout";
        String note = ProcessCheck.judge(r, game(LINES), 1, null, 0, false, List.of()).notes.get(0);
        assertEquals("killed on turn 0: Timeout; 0 line(s) arrived after the deadline; the artifact's own timing: no SLOW report"
            + " (its turn-0 clock starts at its first input token, after JVM start and init)", note);
        note = ProcessCheck.judge(r, game(LINES), 1, null, 0, true, List.of()).notes.get(0);
        assertTrue(note.endsWith("killed before it could answer or report: no conclusion"), note);
    }

    @Test
    void anOpponentTimeoutIsWorthARerun() {
        Referee.Result ref = game(LINES);
        ref.timeouts[1] = 1;
        ref.error[1] = "Timeout";
        ProcessCheck.Game v = ProcessCheck.judge(game(LINES), ref, 0, null, 0, false, List.of());
        assertEquals(0, v.timeouts);
        assertEquals(1, v.anyTimeouts);
        assertFalse(passes(v));
    }

    @Test
    void extraLinesAndOtherStderrFail() {
        Referee.Result r = game(LINES);
        r.extraLines[0] = 1;
        assertFalse(passes(ProcessCheck.judge(r, game(LINES), 0, null, 0, false, List.of())));
        for (String l : List.of("java.lang.IllegalStateException: boom", "", "SLOW", " SLOW turn 3 41ms")) {
            ProcessCheck.Game v = ProcessCheck.judge(game(LINES), game(LINES), 0, null, 0, false, List.of(l));
            assertEquals(1, v.stderrLines, "'" + l + "'");
            assertFalse(passes(v));
        }
    }

    @Test
    void theSummaryKeepsTheShortestGame() {
        ProcessCheck.Tally t = new ProcessCheck.Tally();
        Referee.Result shortGame = game(LINES), longGame = game(LINES);
        shortGame.turns = 5;
        longGame.turns = 9;
        t.add(ProcessCheck.judge(longGame, longGame, 0, null, 0, false, List.of()));
        t.add(ProcessCheck.judge(shortGame, shortGame, 0, null, 0, false, List.of()));
        assertEquals(5, t.minTurns);
        assertEquals(2, t.same);
        assertTrue(t.passed());
    }

    /** The artifact must print exactly its marker where current's turn-0 TRAIN line is empty, and nothing else differs. */
    @Test
    void theArtifactPrintsItsMarkerInPlaceOfAnEmptyTurnZeroTrain() {
        String m = "TRAIN 5 11 10 12 14 6 8 9";
        List<String> plain = List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 WAIT | TRAIN", "1 WAIT | TRAIN");
        List<String> marked0 = List.of("0 WAIT | " + m, "1 WAIT | TRAIN", "0 WAIT | TRAIN", "1 WAIT | TRAIN");
        List<String> marked1 = List.of("0 WAIT | TRAIN", "1 WAIT | " + m, "0 WAIT | TRAIN", "1 WAIT | TRAIN");
        List<String> late = List.of("0 WAIT | TRAIN", "1 WAIT | TRAIN", "0 WAIT | " + m, "1 WAIT | TRAIN");
        assertTrue(passes(ProcessCheck.judge(game(marked0), game(plain), 0, m, 0, false, List.of())));
        assertTrue(passes(ProcessCheck.judge(game(marked1), game(plain), 1, m, 0, false, List.of())));
        assertFalse(passes(ProcessCheck.judge(game(plain), game(plain), 0, m, 0, false, List.of())), "no marker");
        assertFalse(passes(ProcessCheck.judge(game(marked0), game(plain), 0, "TRAIN 0 0 0 0 0 0 0 0", 0, false, List.of())), "another marker");
        assertFalse(passes(ProcessCheck.judge(game(marked0), game(plain), 0, null, 0, false, List.of())), "an artifact without an id");
        assertFalse(passes(ProcessCheck.judge(game(marked1), game(plain), 0, m, 0, false, List.of())), "the other seat's line");
        assertFalse(passes(ProcessCheck.judge(game(late), game(plain), 0, m, 0, false, List.of())), "on turn 1");
    }

    @Test
    void withMarkerChangesOnlyAnEmptyTurnZeroTrainOfTheSeat() {
        String m = "TRAIN 1 2 3 4 5 6 7 8";
        List<String> ref = List.of("0 WAIT | TRAIN 3", "1 WAIT | TRAIN");
        assertEquals(ref, ProcessCheck.withMarker(ref, 0, m), "a TRAIN with sites stays");
        assertEquals(List.of("0 WAIT | TRAIN 3", "1 WAIT | " + m), ProcessCheck.withMarker(ref, 1, m));
        assertEquals(ref, ProcessCheck.withMarker(ref, 1, null));
        assertEquals(List.of("0 <none>"), ProcessCheck.withMarker(List.of("0 <none>"), 0, m));
        assertEquals(List.of(), ProcessCheck.withMarker(List.of(), 0, m));
    }

    @Test
    void noGamesDoNotPass() {
        assertFalse(new ProcessCheck.Tally().passed());
    }

    private static boolean passes(ProcessCheck.Game v) {
        ProcessCheck.Tally t = new ProcessCheck.Tally();
        t.add(v);
        return t.passed();
    }

    private static Referee.Result game(List<String> replay) {
        Referee.Result r = new Referee.Result();
        r.turns = replay.size() / 2;
        r.replay = replay;
        return r;
    }
}
