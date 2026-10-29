package cr.submit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identity of an artifact. The build id names its exact text: FileMerger writes it into CGRunner.BUILD
 * and the artifact prints it on turn 0 as a TRAIN line (CGRunner.marker), which the replay keeps. The
 * config id names the bot it plays: the merged code outside the runner section plus the PARAMS value,
 * so two builds that differ only in the runner (the marker, say) share it. Not shipped.
 */
public final class Build {
    /** The BUILD literal of the sources; FileMerger replaces it with the id. */
    public static final String EMPTY = "public static final String BUILD = \"\";";
    /** Header of the runner's section, which FileMerger writes last. */
    public static final String RUNNER = "// ---- cr/submit/CGRunner.java\n";
    /** The line that opens the merged code, after the import header. */
    private static final String PLAYER = "class Player {\n";
    private static final Pattern LITERAL = Pattern.compile("public static final String BUILD = \"([^\"]*)\";");
    private static final Pattern PARAMS = Pattern.compile("public static final String PARAMS = \"([^\"]*)\";");
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}");

    private Build() {}

    /** The first 8 hex digits of the sha256 of text (ASCII). */
    private static String hash(String text) {
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(sha, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The id of a merged source whose BUILD literal is empty: the first 8 hex digits of its sha256. */
    public static String id(String src) {
        return hash(src);
    }

    /**
     * The build id of an artifact, checked against its content: it must be the id of the same text with
     * an empty literal. "" when there is none: an artifact built before the marker has no BUILD literal,
     * and the sources have an empty one. Fails on a second literal, a malformed one or a mismatch.
     */
    public static String of(String artifact) {
        Matcher m = LITERAL.matcher(artifact);
        if (!m.find()) return "";
        String build = m.group(1), literal = m.group();
        if (m.find()) throw new IllegalStateException("more than one BUILD literal in the artifact");
        if (build.isEmpty()) return build;
        if (!ID.matcher(build).matches()) throw new IllegalStateException("BUILD literal \"" + build + "\" is not 8 hex digits");
        String expected = id(artifact.replace(literal, EMPTY));
        if (!build.equals(expected))
            throw new IllegalStateException("BUILD literal " + build + " does not match the artifact's content (" + expected + ")");
        return build;
    }

    /** {@link #of} for printing: "none" without an id, "invalid (why)" where {@link #of} fails. */
    public static String describe(String artifact) {
        try {
            String b = of(artifact);
            return b.isEmpty() ? "none" : b;
        } catch (IllegalStateException e) {
            return "invalid (" + e.getMessage() + ")";
        }
    }

    /** The value of the single CGRunner.PARAMS literal of an artifact ("" for the defaults). */
    public static String params(String artifact) {
        Matcher m = PARAMS.matcher(artifact);
        if (!m.find()) throw new IllegalStateException("PARAMS literal not found in the artifact");
        String params = m.group(1);
        if (m.find()) throw new IllegalStateException("more than one PARAMS literal in the artifact");
        return params;
    }

    /**
     * The config id of an artifact: the first 8 hex digits of the sha256 of its text from "class Player {"
     * to the runner section, then "PARAMS=" with the PARAMS value as written and a newline. The import
     * header, which the runner's imports join, and the runner section do not count, so changes to the
     * runner alone keep the id; procCheck shows whether they keep the play too. Params spelled
     * differently ("" and the defaults written out) give different ids. Fails unless "class Player {"
     * starts a line once and the runner section occurs once, last.
     */
    public static String config(String artifact) {
        int start = artifact.startsWith(PLAYER) ? 0 : artifact.indexOf("\n" + PLAYER) + 1;
        if (start == 0 && !artifact.startsWith(PLAYER) || artifact.indexOf("\n" + PLAYER, start) >= 0)
            throw new IllegalStateException("\"class Player {\" must start a line exactly once");
        int at = artifact.indexOf(RUNNER);
        if (at < start || artifact.indexOf(RUNNER, at + 1) >= 0) throw new IllegalStateException("the runner section must occur exactly once");
        if (artifact.indexOf("\n// ---- ", at + RUNNER.length() - 1) >= 0) throw new IllegalStateException("the runner section must be the last one");
        return hash(artifact.substring(start, at) + "PARAMS=" + params(artifact) + "\n");
    }

    /** {@link #config} for printing: "invalid (why)" where it fails. */
    public static String describeConfig(String artifact) {
        try {
            return config(artifact);
        } catch (IllegalStateException e) {
            return "invalid (" + e.getMessage() + ")";
        }
    }

    /**
     * The build id a TRAIN line carries, or null: "TRAIN" and 8 site ids 0-15 written as CGRunner.marker
     * writes them, one per hex digit.
     */
    public static String decode(String trainLine) {
        if (trainLine == null) return null;
        List<String> t = List.of(trainLine.split(" ", -1));
        if (t.size() != 9 || !t.get(0).equals("TRAIN")) return null;
        StringBuilder b = new StringBuilder();
        for (String s : t.subList(1, 9)) {
            int d = s.length() == 1 && s.charAt(0) >= '0' && s.charAt(0) <= '9' ? s.charAt(0) - '0'
                : s.length() == 2 && s.charAt(0) == '1' && s.charAt(1) >= '0' && s.charAt(1) <= '5' ? 10 + s.charAt(1) - '0' : -1;
            if (d < 0) return null;
            b.append(Character.forDigit(d, 16));
        }
        return b.toString();
    }
}
