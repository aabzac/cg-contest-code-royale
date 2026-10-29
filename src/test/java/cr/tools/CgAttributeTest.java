package cr.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.referee.Follow;
import cr.referee.Referee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CgAttributeTest {
    /** A single-file Java Player answering WAIT and TRAIN, like the v00 bot. */
    private static final String ECHO = String.join("\n",
        "import java.util.Scanner;",
        "class Player {",
        "    public static void main(String[] a) {",
        "        Scanner in = new Scanner(System.in);",
        "        int sites = in.nextInt();",
        "        for (int i = 0; i < 4 * sites; i++) in.nextInt();",
        "        while (in.hasNextInt()) {",
        "            in.nextInt(); in.nextInt();",
        "            for (int i = 0; i < 7 * sites; i++) in.nextInt();",
        "            int units = in.nextInt();",
        "            for (int i = 0; i < 5 * units; i++) in.nextInt();",
        "            System.out.println(\"WAIT\");",
        "            System.out.println(\"TRAIN\");",
        "        }",
        "    }",
        "}", "");

    @Test
    void onlyTurnsWithAValidatedInputStateAreCompared() {
        CgValidate.Result ok = new CgValidate.Result();
        assertEquals(250, CgAttribute.validatedTurns(ok, 250));

        CgValidate.Result unread = new CgValidate.Result();
        unread.error = "sites: replay 20 vs generated 21";
        assertEquals(0, CgAttribute.validatedTurns(unread, 250));

        // the state after turn index 6 (firstBadTurn 7) differs: inputs of turns 0..6 were still right
        CgValidate.Result midGame = new CgValidate.Result();
        midGame.mismatches.add("t7 units ...");
        midGame.firstBadTurn = 7;
        assertEquals(7, CgAttribute.validatedTurns(midGame, 250));

        CgValidate.Result tie = new CgValidate.Result();
        tie.mismatches.add("winner 0 vs -1");
        tie.firstBadTurn = 251;
        assertEquals(250, CgAttribute.validatedTurns(tie, 250));
    }

    @Test
    void sameNeedsEveryRecordedTurnAndAMissingAnswerIsADifference() {
        assertEquals("SAME", CgAttribute.verdict(result(82, 0, Follow.Stop.NONE), 82));
        assertEquals("DIFF", CgAttribute.verdict(result(82, 3, Follow.Stop.NONE), 82));
        assertEquals("DIFF", CgAttribute.verdict(result(10, 0, Follow.Stop.NO_ANSWER), 82));
        assertEquals("PARTIAL", CgAttribute.verdict(result(11, 0, Follow.Stop.NOT_VALIDATED), 82));
        assertEquals("PARTIAL", CgAttribute.verdict(result(30, 0, Follow.Stop.NO_RECORD), 82));
        assertEquals("PARTIAL", CgAttribute.verdict(result(81, 0, Follow.Stop.GAME_OVER), 82));
        assertEquals("PARTIAL", CgAttribute.verdict(result(0, 0, Follow.Stop.NOT_VALIDATED), 82));
        assertEquals("PARTIAL", CgAttribute.verdict(result(0, 0, Follow.Stop.NONE), 0), "a record without turns");
    }

    @Test
    void theSeatIsFoundByAgentIdOrPseudo() {
        CgReplay g = new CgReplay();
        g.agentId[0] = 111; g.pseudo[0] = "someone";
        g.agentId[1] = 6823071; g.pseudo[1] = "aabzac";
        assertEquals(1, CgAttribute.seatOf(g, "6823071"));
        assertEquals(1, CgAttribute.seatOf(g, "aabzac"));
        assertEquals(0, CgAttribute.seatOf(g, "111"));
        assertEquals(-1, CgAttribute.seatOf(g, "3315948"));
    }

    @Test
    void recordedKeepsEachSeatsLinesAndAnAnswerCutShortHasNoSecondLine() {
        CgReplay g = new CgReplay();
        CgReplay.Turn t0 = new CgReplay.Turn(), t1 = new CgReplay.Turn();
        t0.stdout[0] = "WAIT\nTRAIN\n";
        t0.stdout[1] = "MOVE 1 2\nTRAIN 3\n";
        t1.stdout[0] = "BUILD 4 TOWER\n";
        g.turns.add(t0);
        g.turns.add(t1);
        List<List<String[]>> rec = CgAttribute.recorded(g);
        assertArrayEquals(new String[]{"WAIT", "TRAIN"}, rec.get(0).get(0));
        assertArrayEquals(new String[]{"MOVE 1 2", "TRAIN 3"}, rec.get(1).get(0));
        assertArrayEquals(new String[]{"BUILD 4 TOWER", null}, rec.get(0).get(1));
        assertArrayEquals(new String[]{null, null}, rec.get(1).get(1));

        assertArrayEquals(new String[]{"WAIT", ""}, CgAttribute.lines("WAIT\n\n"), "an empty second line is a line");
        assertArrayEquals(new String[]{"WAIT", "TRAIN"}, CgAttribute.lines("WAIT\nTRAIN"));
        assertArrayEquals(new String[]{"", null}, CgAttribute.lines(""));
    }

    /** The hash names the code that runs: the file's bytes are compiled and played as a process. */
    @Test
    void aJavaFileIsCompiledFromTheHashedBytesAndPlayed(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Echo.java");
        Files.writeString(src, ECHO, StandardCharsets.US_ASCII);
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Path work = Files.createDirectories(tmp.resolve("work"));
        Supplier<Bot> bots = CgAttribute.bots("file:" + src, work, new PrintStream(log, true, StandardCharsets.UTF_8));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(src)));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("sha256=" + sha + " no PARAMS literal"), log.toString(StandardCharsets.UTF_8));

        List<List<String[]>> rec = recorded(Referee.play(3, BotFactory.create("current"), BotFactory.create("v00"), true, 5000));
        Follow.Result r = Follow.follow(3, 1, rec, bots.get(), Integer.MAX_VALUE, 5000);
        assertEquals("SAME", CgAttribute.verdict(r, rec.get(1).size()));
        Follow.Result other = Follow.follow(3, 0, rec, bots.get(), Integer.MAX_VALUE, 5000);
        assertEquals("DIFF", CgAttribute.verdict(other, rec.get(0).size()));
    }

    @Test
    void parametersForTheBuiltArtifactAreRefused(@TempDir Path tmp) {
        PrintStream quiet = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> CgAttribute.bots("submit?MAX_TOWERS=3", tmp, quiet));
        assertThrows(IllegalArgumentException.class, () -> CgAttribute.bots("boss?MAX_TOWERS=3", tmp, quiet));
    }

    @Test
    void noGamesOrUnreadableGamesFail(@TempDir Path tmp) throws Exception {
        PrintStream quiet = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        assertEquals(1, CgAttribute.run(empty, "v00", "6823071", quiet));

        Path bad = Files.createDirectories(tmp.resolve("bad"));
        Files.writeString(bad.resolve("123.json"), "{}");
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(1, CgAttribute.run(bad, "v00", "6823071", new PrintStream(log, true, StandardCharsets.UTF_8)));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("ERROR 123: "), log.toString(StandardCharsets.UTF_8));
    }

    /**
     * Two placement games of agent 6823071 (gitignored replays; skipped without them): seat 0 in
     * 913027389, seat 1 in 913027383, played by a build without the marker. current matches both; an
     * artifact of today's sources differs at turn 0 only, by its marker, and matches once the replay
     * carries that marker and the referee's warning about it. v00 and the opponent's seat do not match;
     * an unvalidated state stops the comparison, and the recorded marker is still reported.
     */
    @Test
    void realPlacementGamesAreAttributedToTheBotThatPlayedThem(@TempDir Path tmp) throws Exception {
        Path games = Path.of("replays/cg/arena-6823071/games");
        assumeTrue(Files.exists(games.resolve("913027389.json")) && Files.exists(games.resolve("913027383.json")), "no local replays");
        Path two = Files.createDirectories(tmp.resolve("two"));
        for (String id : List.of("913027389", "913027383")) Files.copy(games.resolve(id + ".json"), two.resolve(id + ".json"));

        assertEquals(0, CgAttribute.run(two, "current", "6823071", quiet()));
        // an artifact of today's sources prints its marker on turn 0, which the recorded (older) build did not
        String artifact = cr.submit.FileMerger.run(new String[]{""}, tmp.resolve("submit"));
        assertTrue(artifact.contains("sha256="), artifact);
        String id = cr.submit.Build.of(Files.readString(tmp.resolve("submit/Player.java"), StandardCharsets.US_ASCII));
        String marker = cr.submit.CGRunner.marker(id, 24);
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(1, CgAttribute.run(two, "file:" + tmp.resolve("submit/Player.java"), "6823071", new PrintStream(log, true, StandardCharsets.UTF_8)));
        String text = log.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains(" build=" + id) && text.contains("same=0 differ=2 partial=0 error=0") && text.contains("markers={none=2}"), text);
        assertTrue(text.matches("(?s).*DIFF 913027389 seat 0 vs Brutus79: compared 35 of 35 turns \\(validated 35\\), diffs 1, first at turn 0: "
            + "recorded \\[[A-Z0-9 -]+ \\| TRAIN\\] bot \\[[A-Z0-9 -]+ \\| " + marker + "\\]\\R.*"), text);

        // the same game as that build would have played it: its marker and the referee's warning about it
        JsonObject withMarker = JsonParser.parseString(Files.readString(games.resolve("913027389.json"))).getAsJsonObject();
        JsonObject turn0 = withMarker.getAsJsonArray("frames").get(1).getAsJsonObject(), key0 = withMarker.getAsJsonArray("frames").get(2).getAsJsonObject();
        String out0 = turn0.get("stdout").getAsString();
        assertTrue(out0.endsWith("\nTRAIN\n"), out0);
        turn0.addProperty("stdout", out0.substring(0, out0.length() - "TRAIN\n".length()) + marker + "\n");
        String summary = key0.has("summary") && !key0.get("summary").isJsonNull() ? key0.get("summary").getAsString() : "";
        key0.addProperty("summary", summary + "$0: [WARNING] Cannot spawn from " + Character.digit(id.charAt(0), 16) + ": not a barracks\n");
        Path markedDir = Files.createDirectories(tmp.resolve("marked"));
        Files.writeString(markedDir.resolve("913027389.json"), withMarker.toString());
        log.reset();
        assertEquals(0, CgAttribute.run(markedDir, "file:" + tmp.resolve("submit/Player.java"), "6823071", new PrintStream(log, true, StandardCharsets.UTF_8)));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("diffs 0; marker " + id), log.toString(StandardCharsets.UTF_8));
        log.reset();
        assertEquals(0, CgAttribute.run(markedDir, "current", "6823071", new PrintStream(log, true, StandardCharsets.UTF_8)));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("markers={" + id + "=1}"), log.toString(StandardCharsets.UTF_8));
        // another seed: the map does not match, no turn is validated or compared, the marker is still reported
        String input = withMarker.get("refereeInput").getAsString();
        java.util.regex.Matcher seed = java.util.regex.Pattern.compile("seed=(-?\\d+)").matcher(input);
        assertTrue(seed.find(), input);
        withMarker.addProperty("refereeInput", input.replace(seed.group(), "seed=" + (Long.parseLong(seed.group(1)) + 1)));
        Files.writeString(markedDir.resolve("913027389.json"), withMarker.toString());
        log.reset();
        assertEquals(1, CgAttribute.run(markedDir, "current", "6823071", new PrintStream(log, true, StandardCharsets.UTF_8)));
        text = log.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("compared 0 of 35 turns (validated 0), diffs 0; stopped at turn 0: not validated; marker " + id)
            && text.contains("markers={" + id + "=1}"), text);
        assertEquals(1, CgAttribute.run(two, "v00", "6823071", quiet()));
        assertEquals(1, CgAttribute.run(two.resolve("913027389.json"), "current", "2742118", quiet()), "the opponent's seat");

        // a summary warning the simulation does not raise: the state after turn index 10 is unconfirmed
        JsonObject g = JsonParser.parseString(Files.readString(games.resolve("913027389.json"))).getAsJsonObject();
        g.getAsJsonArray("frames").get(1 + 2 * 10).getAsJsonObject().addProperty("summary", "$0: [WARNING] probe");
        Path tampered = Files.createDirectories(tmp.resolve("tampered")).resolve("913027389.json");
        Files.writeString(tampered, g.toString());
        log.reset();
        assertEquals(1, CgAttribute.run(tampered, "current", "6823071", new PrintStream(log, true, StandardCharsets.UTF_8)));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("PARTIAL 913027389 seat 0 vs Brutus79: compared 11 of 35 turns (validated 11), diffs 0; stopped at turn 11: not validated"),
            log.toString(StandardCharsets.UTF_8));

        // one pseudo, two agents (the old entry had the same pseudo): only an agent id names one of them
        JsonObject old = JsonParser.parseString(Files.readString(games.resolve("913027389.json"))).getAsJsonObject();
        for (var a : old.getAsJsonArray("agents"))
            if (a.getAsJsonObject().get("agentId").getAsLong() == 6823071) a.getAsJsonObject().addProperty("agentId", 3315948);
        Path mixed = Files.createDirectories(tmp.resolve("mixed"));
        Files.copy(games.resolve("913027383.json"), mixed.resolve("913027383.json"));
        Files.writeString(mixed.resolve("913027389.json"), old.toString());
        log.reset();
        assertEquals(1, CgAttribute.run(mixed, "current", "aabzac", new PrintStream(log, true, StandardCharsets.UTF_8)));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("ERROR aabzac matched several agents [3315948, 6823071]: give an agentId"),
            log.toString(StandardCharsets.UTF_8));
    }

    private static PrintStream quiet() {
        return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    }

    private static Follow.Result result(int turns, int diffs, Follow.Stop stop) {
        Follow.Result r = new Follow.Result();
        r.turns = turns;
        r.diffs = diffs;
        r.stop = stop;
        return r;
    }

    private static List<List<String[]>> recorded(Referee.Result r) {
        List<List<String[]>> rec = List.of(new ArrayList<>(), new ArrayList<>());
        for (String line : r.replay) {
            String rest = line.substring(2);
            rec.get(line.charAt(0) - '0').add(rest.equals("<none>") ? new String[]{null, null} : rest.split(" \\| ", 2));
        }
        return rec;
    }
}
