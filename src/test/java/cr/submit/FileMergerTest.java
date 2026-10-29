package cr.submit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileMergerTest {
    private static final String OK = "class Player { public static void main(String[] a) { } }\n";
    private static final String OK2 = "class Player { public static void main(String[] a) { System.exit(0); } }\n";

    @Test
    void mergedSourceIsAsciiWithLfAndTheRequestedParams() throws IOException {
        String src = FileMerger.merge(FileMerger.shippedFiles(), "MAX_TOWERS=3");
        assertEquals(-1, src.indexOf('\r'));
        assertEquals(-1, FileMerger.firstNonAscii(src));
        assertTrue(src.contains("\nclass Player {\n"));
        assertTrue(src.contains("public static final String PARAMS = \"MAX_TOWERS=3\";"));
    }

    /** The text must not depend on the checkout: LF and CRLF copies elsewhere give the same file. */
    @Test
    void lfAndCrlfCopiesMergeToTheSameText(@TempDir Path tmp) throws IOException {
        String expected = FileMerger.merge(FileMerger.shippedFiles(), "");
        assertEquals(expected, FileMerger.merge(tmp.resolve("lf"), copyShipped(tmp.resolve("lf"), t -> t), ""), "LF");
        assertEquals(expected, FileMerger.merge(tmp.resolve("crlf"), copyShipped(tmp.resolve("crlf"), t -> t.replace("\n", "\r\n")), ""), "CRLF");
    }

    @Test
    void loneCrAndNonAsciiAreRejectedWithTheirLine(@TempDir Path tmp) throws IOException {
        List<Path> copies = copyShipped(tmp, t -> t);
        Path victim = copies.get(0);
        String text = Files.readString(victim, StandardCharsets.UTF_8);

        Files.writeString(victim, "// one\r// still line one for Java\n" + text, StandardCharsets.UTF_8);
        String msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp, copies, "")).getMessage();
        assertTrue(msg.startsWith("CR without LF at ") && msg.endsWith(victim.getFileName() + ":1"), msg);

        Files.writeString(victim, "// one\n// café\n" + text, StandardCharsets.UTF_8);
        msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp, copies, "")).getMessage();
        assertTrue(msg.startsWith("non-ASCII character U+00E9 at ") && msg.endsWith(victim.getFileName() + ":2"), msg);
    }

    @Test
    void paramsThatDoNotApplyOrDoNotFitTheLiteralAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge(FileMerger.shippedFiles(), "NO_SUCH_KEY=1"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge(FileMerger.shippedFiles(), "MAX_TOWERS=x"));
        // Integer.parseInt accepts the fullwidth digit three; the US-ASCII artifact cannot hold it
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge(FileMerger.shippedFiles(), "MAX_TOWERS=３"));
    }

    @Test
    void aNonAsciiFileNameAndAMissingOrDoubledParamsMarkerAreRejected(@TempDir Path tmp) throws IOException {
        List<Path> copies = copyShipped(tmp.resolve("name"), t -> t);
        Path odd = copies.get(0).resolveSibling("Zzé.java");
        Files.writeString(odd, "final class Zzx {}\n", StandardCharsets.US_ASCII);
        copies.add(odd);
        String msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp.resolve("name"), copies, "")).getMessage();
        assertTrue(msg.startsWith("non-ASCII character U+00E9 at merged line "), msg);

        String marker = "public static final String PARAMS = \"\";";
        List<Path> twice = copyShipped(tmp.resolve("twice"), t -> t.replace(marker, marker + " // " + marker));
        msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp.resolve("twice"), twice, "")).getMessage();
        assertEquals("PARAMS marker must occur exactly once", msg);

        List<Path> none = copyShipped(tmp.resolve("none"), t -> t.replace(marker, "public static final String PARAMS = null;"));
        msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp.resolve("none"), none, "")).getMessage();
        assertEquals("PARAMS marker must occur exactly once", msg);
    }

    /** The id follows the code and the params; an edited artifact no longer matches it. */
    @Test
    void theBuildIdNamesTheMergedText() throws IOException {
        String a = FileMerger.merge(FileMerger.shippedFiles(), ""), b = FileMerger.merge(FileMerger.shippedFiles(), "MAX_TOWERS=3");
        String id = Build.of(a);
        assertTrue(id.matches("[0-9a-f]{8}"), id);
        assertEquals(id, Build.id(a.replace("BUILD = \"" + id + "\";", "BUILD = \"\";")));
        assertEquals(id, Build.of(FileMerger.merge(FileMerger.shippedFiles(), "")), "the same inputs give the same id");
        assertNotEquals(id, Build.of(b));
        assertThrows(IllegalStateException.class, () -> Build.of(a + "// edited\n"));
    }

    @Test
    void aMissingOrDoubledBuildMarkerIsRejected(@TempDir Path tmp) throws IOException {
        List<Path> twice = copyShipped(tmp.resolve("twice"), t -> t.replace(Build.EMPTY, Build.EMPTY + " // " + Build.EMPTY));
        String msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp.resolve("twice"), twice, "")).getMessage();
        assertEquals("BUILD marker must occur exactly once", msg);
        List<Path> none = copyShipped(tmp.resolve("none"), t -> t.replace(Build.EMPTY, "public static final String BUILD = \"x\";"));
        msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp.resolve("none"), none, "")).getMessage();
        assertEquals("BUILD marker must occur exactly once", msg);
        // merge refuses it, so run never reaches build(): the old artifact stays
        List<Path> second = copyShipped(tmp.resolve("second"), t -> t.replace(Build.EMPTY, Build.EMPTY + " // public static final String BUILD = \"5bace689\";"));
        msg = assertThrows(IllegalStateException.class, () -> FileMerger.merge(tmp.resolve("second"), second, "")).getMessage();
        assertEquals("more than one BUILD literal in the artifact", msg);
    }

    /** The config id names the bot and its params: a change to the runner keeps it, a change to the bot does not. */
    @Test
    void theConfigIdFollowsTheBotNotTheRunner(@TempDir Path tmp) throws IOException {
        String base = Build.config(FileMerger.merge(FileMerger.shippedFiles(), ""));
        // a new constant and an import of the runner's own (imports go into the shared header)
        List<Path> runner = copyShipped(tmp.resolve("runner"), t -> t.contains("class CGRunner")
            ? t.replace("static final long WARM_UP_MS = 300;", "static final long WARM_UP_MS = 301;").replace("import cr.bot.Bot;", "import cr.bot.Bot;\nimport java.io.PrintStream;")
            : t);
        String changed = FileMerger.merge(tmp.resolve("runner"), runner, "");
        assertTrue(changed.contains("WARM_UP_MS = 301;") && (changed.startsWith("import java.io.PrintStream;") || changed.contains("\nimport java.io.PrintStream;")),
            changed.substring(0, 300));
        assertEquals(base, Build.config(changed));
        List<Path> bot = copyShipped(tmp.resolve("bot"), t -> t.contains("class MainBot") ? t + "// changed\n" : t);
        assertNotEquals(base, Build.config(FileMerger.merge(tmp.resolve("bot"), bot, "")));
        assertNotEquals(base, Build.config(FileMerger.merge(FileMerger.shippedFiles(), "MAX_TOWERS=3")));
    }

    /**
     * The built program in a real game: its turn-0 TRAIN line is the marker of its build id and no
     * later line is one, and the referee only warns about it.
     */
    @Test
    void theArtifactPrintsItsMarkerOnTurnZeroOnly(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("submit");
        FileMerger.run(new String[]{""}, out);
        String id = Build.of(Files.readString(out.resolve("Player.java"), StandardCharsets.US_ASCII));
        cr.referee.Referee.Result r = cr.referee.Referee.play(1, cr.bot.BotFactory.create("v00"),
            new cr.referee.ProcessBot("artifact", List.of("java", "-cp", out.resolve("classes").toString(), "Player"), false), true, 1000);
        assertTrue(r.error[1] == null && r.timeouts[1] == 0 && r.extraLines[1] == 0, r.error[1]);
        assertTrue(r.replay.get(1).startsWith("1 ") && r.replay.get(1).endsWith(" | " + CGRunner.marker(id, r.sites.size())), r.replay.get(1));
        for (int i = 2; i < r.replay.size(); i++)
            assertEquals(null, Build.decode(r.replay.get(i).substring(r.replay.get(i).indexOf(" | ") + 3)), r.replay.get(i));
    }

    /** A quote in -Pparams can split the argument on Windows; the rest must not be dropped silently. */
    @Test
    void moreThanOneArgumentIsRejectedBeforeAnythingIsTouched(@TempDir Path tmp) {
        Path out = tmp.resolve("submit");
        assertThrows(IllegalArgumentException.class, () -> FileMerger.run(new String[]{"MAX_TOWERS=3", ",BARRACKS=2"}, out));
        assertFalse(Files.exists(out));
    }

    /** What ./gradlew submit does, into a temporary directory: the merged file compiles. */
    @Test
    void runBuildsTheShippedFilesAndReportsTheirSha256(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("submit");
        String summary = FileMerger.run(new String[]{""}, out);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(out.resolve("Player.java"))));
        assertTrue(summary.endsWith("sha256=" + sha), summary);
        String text = Files.readString(out.resolve("Player.java"), StandardCharsets.US_ASCII);
        assertTrue(summary.contains(", build=" + Build.of(text) + ", config=" + Build.config(text) + ", "), summary);
        assertTrue(Files.exists(out.resolve("classes/Player.class")));
        assertFalse(Files.exists(tmp.resolve("submit.new")) || Files.exists(tmp.resolve("submit.old")));
    }

    @Test
    void aFailedCompileLeavesTheOldArtifactWhole(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("submit");
        FileMerger.build(out, OK);
        Map<String, String> before = snapshot(out);
        String msg = assertThrows(IllegalStateException.class, () -> FileMerger.build(out, "class Player { int x = \"no\"; }\n")).getMessage();
        assertEquals("merged file does not compile", msg);
        assertEquals(before, snapshot(out));
        assertFalse(Files.exists(tmp.resolve("submit.new")) || Files.exists(tmp.resolve("submit.old")));
    }

    @Test
    void leftoversOfAnInterruptedBuildAreClearedFirst(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("submit");
        FileMerger.build(out, OK2);
        Files.createDirectories(tmp.resolve("submit.new/classes"));
        Files.writeString(tmp.resolve("submit.new/classes/Stale.class"), "stale");
        Files.createDirectories(tmp.resolve("submit.old/classes"));
        Files.writeString(tmp.resolve("submit.old/Player.java"), OK2);
        FileMerger.build(out, OK);
        assertEquals(OK, Files.readString(out.resolve("Player.java")));
        assertEquals(Set.of("Player.java", Path.of("classes", "Player.class").toString()), snapshot(out).keySet(), "no stale file mixed in");
        assertFalse(Files.exists(tmp.resolve("submit.new")) || Files.exists(tmp.resolve("submit.old")));
    }

    /** Windows cannot rename a directory with an open file inside: the old artifact must stay as it was. */
    @Test
    void anOldArtifactThatCannotBeMovedStaysWhole(@TempDir Path tmp) throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "file locking as on Windows");
        Path out = tmp.resolve("submit");
        FileMerger.build(out, OK);
        Map<String, String> before = snapshot(out);
        try (InputStream held = Files.newInputStream(out.resolve("classes/Player.class"))) {
            assertThrows(IOException.class, () -> FileMerger.build(out, OK2));
        }
        assertEquals(before, snapshot(out));
        assertFalse(Files.exists(tmp.resolve("submit.new")) || Files.exists(tmp.resolve("submit.old")));
    }

    @Test
    void firstNonAsciiFindsTheCharacter() {
        assertEquals(-1, FileMerger.firstNonAscii("Referee -> bot\n"));
        assertEquals(8, FileMerger.firstNonAscii("Referee → bot"));
    }

    /** Relative path -> sha256 of every file under dir. */
    private static Map<String, String> snapshot(Path dir) throws IOException {
        Map<String, String> m = new TreeMap<>();
        try (Stream<Path> st = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) st.filter(Files::isRegularFile)::iterator) {
                try {
                    m.put(dir.relativize(p).toString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return m;
    }

    /** Copies the shipped files under root at their paths relative to SRC; edit gets each text with LF endings. */
    private static List<Path> copyShipped(Path root, UnaryOperator<String> edit) throws IOException {
        List<Path> copies = new ArrayList<>();
        for (Path f : FileMerger.shippedFiles()) {
            Path c = root.resolve(FileMerger.SRC.relativize(f).toString());
            Files.createDirectories(c.getParent());
            Files.writeString(c, edit.apply(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n")), StandardCharsets.UTF_8);
            copies.add(c);
        }
        return copies;
    }
}
