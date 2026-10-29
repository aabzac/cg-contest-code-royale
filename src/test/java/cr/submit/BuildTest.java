package cr.submit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildTest {
    private static final String PARAMS = "    public static final String PARAMS = \"\";\n";
    /** The shape of a merged artifact: sections, the runner last with its PARAMS and BUILD literals. */
    private static final String SRC = "\n\nclass Player {\n// ---- cr/bot/X.java\nstatic class X {}\n" + Build.RUNNER
        + "static final class CGRunner {\n" + PARAMS + "    " + Build.EMPTY + "\n}\n}\n";

    private static String built(String src) {
        return src.replace(Build.EMPTY, "public static final String BUILD = \"" + Build.id(src) + "\";");
    }

    @Test
    void theIdIsCheckedAgainstTheArtifactsContent() {
        assertEquals("", Build.of(SRC), "the sources' empty literal");
        assertEquals("", Build.of(SRC.replace("    " + Build.EMPTY + "\n", "")), "built before the marker: no literal");
        String id = Build.id(SRC), b = built(SRC);
        assertTrue(id.matches("[0-9a-f]{8}"), id);
        assertEquals(id, Build.of(b));
        assertThrows(IllegalStateException.class, () -> Build.of(b + "// edited\n"));
        assertThrows(IllegalStateException.class, () -> Build.of(SRC.replace(Build.EMPTY, "public static final String BUILD = \"0123456z\";")));
        assertThrows(IllegalStateException.class, () -> Build.of(SRC.replace(Build.EMPTY, "public static final String BUILD = \"" + id.toUpperCase() + "\";")));
        assertThrows(IllegalStateException.class, () -> Build.of(b + "// public static final String BUILD = \"5bace689\";\n"), "a second literal");
        assertEquals("none", Build.describe(SRC));
        assertEquals(id, Build.describe(b));
        assertTrue(Build.describe(b + "// edited\n").startsWith("invalid (BUILD literal " + id + " does not match"));
    }

    @Test
    void theConfigIdIgnoresTheRunnerButNotTheBotOrItsParams() {
        String b = built(SRC), config = Build.config(b);
        assertTrue(config.matches("[0-9a-f]{8}"), config);
        assertEquals(config, Build.config(SRC), "the build id is in the runner's section");
        assertEquals(config, Build.config(b.replace("static final class CGRunner {\n", "static final class CGRunner {\n    static int marker;\n")));
        assertEquals(config, Build.config("import java.io.PrintStream;\n" + b), "the runner's imports join the header");
        assertNotEquals(config, Build.config(b.replace("static class X {}", "static class X { int y; }")));
        assertNotEquals(config, Build.config(b.replace("PARAMS = \"\"", "PARAMS = \"MAX_TOWERS=3\"")));
        assertThrows(IllegalStateException.class, () -> Build.config(b.replace(Build.RUNNER, "")), "no runner section");
        assertThrows(IllegalStateException.class, () -> Build.config(b.replace("}\n}\n", "}\n// ---- cr/bot/Late.java\nstatic class Late {}\n}\n")),
            "a section after the runner's would escape the config id");
        assertThrows(IllegalStateException.class, () -> Build.config(b.replace(PARAMS, "")), "no PARAMS literal");
        assertThrows(IllegalStateException.class, () -> Build.config(b.replace("class Player {\n", "class Bot {\n")), "no class Player");
        assertThrows(IllegalStateException.class, () -> Build.config(b + "class Player {\n}\n"), "class Player twice");
        assertEquals(config, Build.config(b.substring(2)), "class Player on the first line");
        assertTrue(Build.describeConfig("class Player {\n}\n").startsWith("invalid (the runner section"));
    }

    @Test
    void paramsComeFromTheSingleParamsLiteral() {
        assertEquals("", Build.params(SRC));
        assertEquals("MAX_TOWERS=3", Build.params(SRC.replace("PARAMS = \"\"", "PARAMS = \"MAX_TOWERS=3\"")));
        assertThrows(IllegalStateException.class, () -> Build.params("class Player {}"));
        assertThrows(IllegalStateException.class, () -> Build.params(SRC + PARAMS));
    }

    @Test
    void decodeReadsTheMarkerLineOnly() {
        assertEquals("5bace689", Build.decode("TRAIN 5 11 10 12 14 6 8 9"));
        assertEquals("0000ffff", Build.decode("TRAIN 0 0 0 0 15 15 15 15"));
        for (int d = 0; d < 16; d++) {
            String id = String.valueOf(Character.forDigit(d, 16)).repeat(8);
            assertEquals(id, Build.decode(CGRunner.marker(id, 24)), id);
        }
        String[] others = {null, "", "TRAIN", "TRAIN 5 11 10 12 14 6 8", "TRAIN 5 11 10 12 14 6 8 9 1", "TRAIN 5 11 10 12 14 6 8 16",
            "TRAIN 05 11 10 12 14 6 8 9", "TRAIN +5 11 10 12 14 6 8 9", "TRAIN -1 11 10 12 14 6 8 9", "TRAIN 5 11 10 12 14 6 8 9 ",
            "TRAIN  5 11 10 12 14 6 8 9", "TRAIN 5 11 10 12 14 6 8 a", "WAIT 5 11 10 12 14 6 8 9"};
        for (String s : others) assertNull(Build.decode(s), "'" + s + "'");
    }
}
