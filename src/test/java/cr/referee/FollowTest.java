package cr.referee;

import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.core.Command;
import cr.core.GameState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowTest {
    private static final long SEED = 3;

    @Test
    void theBotOfASeatMatchesItOnEveryTurnAndTheOtherBotDoesNot() throws Exception {
        for (int seat = 0; seat < 2; seat++) {
            List<List<String[]>> rec = recorded(seat == 0
                ? Referee.play(SEED, BotFactory.create("current"), BotFactory.create("v00"), true, 5000)
                : Referee.play(SEED, BotFactory.create("v00"), BotFactory.create("current"), true, 5000));
            int n = rec.get(0).size();
            assertTrue(n > 10, "seat " + seat + ": " + n + " turns");

            Follow.Result same = Follow.follow(SEED, seat, rec, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
            assertEquals(n, same.turns, "seat " + seat);
            assertEquals(0, same.diffs, "seat " + seat);
            assertEquals(-1, same.firstDiff);
            assertEquals(Follow.Stop.NONE, same.stop);
            assertNull(same.stopped);

            Follow.Result wrongSeat = Follow.follow(SEED, 1 - seat, rec, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
            assertEquals(0, wrongSeat.firstDiff, "seat " + (1 - seat) + " played v00");
            assertEquals("WAIT | TRAIN", wrongSeat.expected);
            assertEquals(n, wrongSeat.turns, "the recorded game goes on after a difference");

            Follow.Result otherBot = Follow.follow(SEED, seat, rec, BotFactory.create("v00"), Integer.MAX_VALUE, 5000);
            assertEquals(0, otherBot.firstDiff);
            assertEquals("WAIT | TRAIN", otherBot.got);
        }
    }

    @Test
    void aTrainLineDifferenceAloneIsADifference() throws Exception {
        List<List<String[]>> rec = game();
        Bot inner = BotFactory.create("current");
        Bot trainDiffers = new Bot() {
            int turn;
            @Override public String name() { return "train-differs"; }
            @Override public void init(GameState s) { inner.init(s); }
            @Override public Command play(GameState s, int touched, long deadline) {
                Command c = inner.play(s, touched, deadline);
                if (turn++ == 5) c.train(c.train.length == 0 ? new int[]{0} : new int[0]);
                return c;
            }
        };
        Follow.Result r = Follow.follow(SEED, 0, rec, trainDiffers, Integer.MAX_VALUE, 5000);
        assertEquals(rec.get(0).size(), r.turns);
        assertEquals(1, r.diffs);
        assertEquals(5, r.firstDiff);
    }

    /** The program sees end of input only when Follow closes it: its BYE is the one unread line. */
    @Test
    void aProcessBotIsFollowedThroughItsStdinAndClosed() throws Exception {
        List<List<String[]>> rec = game();
        ProcessBot echo = process(ProtocolEcho.class, "-1", "bye");
        Follow.Result r = Follow.follow(SEED, 1, rec, echo, Integer.MAX_VALUE, 5000);
        assertEquals(rec.get(1).size(), r.turns);
        assertEquals(0, r.diffs);
        assertEquals(1, echo.extraLines(), "BYE, printed after end of input");
    }

    /** An extra line shifts the bot's answers; the game still advances with the recorded commands. */
    @Test
    void anExtraLineShiftsTheAnswersButTheRecordedGameGoesOn() throws Exception {
        List<List<String[]>> rec = game();
        int n = rec.get(1).size();
        Follow.Result r = Follow.follow(SEED, 1, rec, process(ProtocolEcho.class, "2"), Integer.MAX_VALUE, 5000);
        assertEquals(n, r.turns);
        assertEquals(3, r.firstDiff);
        assertEquals("EXTRA | WAIT", r.got);
        assertEquals(n - 3, r.diffs);
    }

    @Test
    void aBotThatStopsAnsweringStopsTheComparison() throws Exception {
        // 1500 ms: the helper JVM must start within the first turn
        Follow.Result r = Follow.follow(SEED, 1, game(), process(HalfEcho.class, "4"), Integer.MAX_VALUE, 1500);
        assertEquals(4, r.turns);
        assertEquals(0, r.diffs);
        assertEquals(Follow.Stop.NO_ANSWER, r.stop);
        assertEquals("turn 4: the bot did not answer in 1500 ms", r.stopped);
    }

    @Test
    void theComparisonStopsAtTheTurnLimitAMissingRecordAndTheSimulatedEnd() throws Exception {
        List<List<String[]>> rec = game();
        int n = rec.get(0).size();
        Follow.Result limited = Follow.follow(SEED, 0, rec, BotFactory.create("current"), 5, 5000);
        assertEquals(5, limited.turns);
        assertEquals(Follow.Stop.NOT_VALIDATED, limited.stop);
        assertEquals("turn 5: not validated", limited.stopped);

        List<List<String[]>> longer = game();
        for (int p = 0; p < 2; p++) longer.get(p).add(longer.get(p).get(n - 1));
        Follow.Result over = Follow.follow(SEED, 0, longer, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
        assertEquals(n, over.turns);
        assertEquals(Follow.Stop.GAME_OVER, over.stop);
        assertEquals("turn " + n + ": the simulated game is over", over.stopped);

        rec.get(0).set(9, new String[]{rec.get(0).get(9)[0], null});
        rec.get(0).set(7, new String[]{null, null});
        Follow.Result missing = Follow.follow(SEED, 0, rec, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
        assertEquals(7, missing.turns);
        assertEquals(0, missing.diffs);
        assertEquals(Follow.Stop.NO_RECORD, missing.stop);
        assertEquals("turn 7: no recorded output", missing.stopped);

        rec.get(0).set(7, game().get(0).get(7));
        Follow.Result cut = Follow.follow(SEED, 0, rec, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
        assertEquals(9, cut.turns, "an answer with only its first line is not compared");
        assertEquals(Follow.Stop.NO_RECORD, cut.stop);
    }

    /** A recorded build marker: excused for a bot with an empty TRAIN line there, to be printed exactly by one that prints a marker. */
    @Test
    void aRecordedMarkerIsExcusedForAnEmptyTrainAndMustMatchAPrintedOne() throws Exception {
        int[] mine = {5, 11, 10, 12, 14, 6, 8, 9};
        List<List<String[]>> marked = game();
        marked.get(0).set(0, new String[]{marked.get(0).get(0)[0], "TRAIN 5 11 10 12 14 6 8 9"});

        Follow.Result plainBot = Follow.follow(SEED, 0, marked, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
        assertEquals(0, plainBot.diffs);
        assertEquals("5bace689", plainBot.marker);
        Follow.Result sameMarker = Follow.follow(SEED, 0, marked, marking(mine, 0), Integer.MAX_VALUE, 5000);
        assertEquals(0, sameMarker.diffs);
        Follow.Result otherMarker = Follow.follow(SEED, 0, marked, marking(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, 0), Integer.MAX_VALUE, 5000);
        assertEquals(1, otherMarker.diffs);
        assertEquals(0, otherMarker.firstDiff);

        // the recorded player printed no marker: a bot that prints one is another build
        Follow.Result unrecorded = Follow.follow(SEED, 0, game(), marking(mine, 0), Integer.MAX_VALUE, 5000);
        assertEquals(1, unrecorded.diffs);
        assertNull(unrecorded.marker);
        // only turn 0 carries a marker; the same line later is an ordinary TRAIN line
        Follow.Result later = Follow.follow(SEED, 0, game(), marking(mine, 1), Integer.MAX_VALUE, 5000);
        assertEquals(1, later.firstDiff);
        assertNull(later.marker);
    }

    /** The excuse covers a recorded marker on turn 0 only: not a later TRAIN, not a turn-0 TRAIN that is no marker. */
    @Test
    void theExcuseNeedsTurnZeroAndARecordedMarker() throws Exception {
        List<List<String[]>> trainsLater = game();
        trainsLater.get(0).set(0, new String[]{trainsLater.get(0).get(0)[0], "TRAIN 5 11 10 12 14 6 8 9"});
        trainsLater.get(0).set(5, new String[]{trainsLater.get(0).get(5)[0], "TRAIN 3"});
        // the bot's own turn-5 TRAIN is forced empty, so only the turn guard can tell the lines apart
        Follow.Result r = Follow.follow(SEED, 0, trainsLater, marking(new int[0], 5), Integer.MAX_VALUE, 5000);
        assertEquals(5, r.firstDiff, "turn 5 is no marker turn");

        List<List<String[]>> notMarker = game();
        notMarker.get(0).set(0, new String[]{notMarker.get(0).get(0)[0], "TRAIN 3"});
        Follow.Result plain = Follow.follow(SEED, 0, notMarker, BotFactory.create("current"), Integer.MAX_VALUE, 5000);
        assertEquals(0, plain.firstDiff);
        assertNull(plain.marker);
    }

    /** The marker belongs to the record: it is read even when turn 0 is not compared. */
    @Test
    void theRecordedMarkerIsReadWithoutComparingTurnZero() throws Exception {
        List<List<String[]>> marked = game();
        marked.get(0).set(0, new String[]{marked.get(0).get(0)[0], "TRAIN 5 11 10 12 14 6 8 9"});
        Follow.Result r = Follow.follow(SEED, 0, marked, BotFactory.create("current"), 0, 5000);
        assertEquals(0, r.turns);
        assertEquals(Follow.Stop.NOT_VALIDATED, r.stop);
        assertEquals("5bace689", r.marker);
    }

    /** current, training the given sites on one turn. */
    private static Bot marking(int[] sites, int onTurn) {
        Bot inner = BotFactory.create("current");
        return new Bot() {
            int turn;
            @Override public String name() { return "marking"; }
            @Override public void init(GameState s) { inner.init(s); }
            @Override public Command play(GameState s, int touched, long deadline) {
                Command c = inner.play(s, touched, deadline);
                if (turn++ == onTurn) c.train(sites);
                return c;
            }
        };
    }

    /** current (seat 0) against v00 (seat 1). */
    private static List<List<String[]>> game() {
        return recorded(Referee.play(SEED, BotFactory.create("current"), BotFactory.create("v00"), true, 5000));
    }

    /** Recorded lines per player from a local game's replay ("p l0 | l1" per player and turn). */
    private static List<List<String[]>> recorded(Referee.Result r) {
        List<List<String[]>> rec = List.of(new ArrayList<>(), new ArrayList<>());
        for (String line : r.replay) {
            String rest = line.substring(2);
            rec.get(line.charAt(0) - '0').add(rest.equals("<none>") ? new String[]{null, null} : rest.split(" \\| ", 2));
        }
        return rec;
    }

    private static ProcessBot process(Class<?> main, String... args) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Path classes = Paths.get(main.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(java, "-cp", classes.toString(), main.getName()));
        command.addAll(List.of(args));
        return new ProcessBot("echo", command, false);
    }
}
