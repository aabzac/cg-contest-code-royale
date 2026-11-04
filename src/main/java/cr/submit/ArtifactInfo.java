package cr.submit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Prints the identity of an artifact file as one JSON line: its sha256, length, PARAMS value, build id
 * ({@link Build#of}, null for a build before the marker) and config id ({@link Build#config}). tools/arena.py
 * runs it (gradle artifactInfo -Pfile=...) to register an upload. Exit code 1 when the file is not a valid
 * artifact: a malformed or mismatched BUILD literal, no runner section or no single PARAMS literal. Not shipped.
 */
public final class ArtifactInfo {
    private ArtifactInfo() {}

    static String json(byte[] code) throws Exception {
        String text = new String(code, StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code));
        String build = Build.of(text), config = Build.config(text), params = Build.params(text);
        return "{\"sha256\": \"" + sha + "\", \"chars\": " + text.length() + ", \"params\": \"" + escape(params)
            + "\", \"build\": " + (build.isEmpty() ? "null" : "\"" + build + "\"") + ", \"config\": \"" + config + "\"}";
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0].isEmpty()) {
            System.err.println("usage: gradlew artifactInfo -Pfile=<Player.java>");
            System.exit(2);
        }
        try {
            System.out.println(json(Files.readAllBytes(Path.of(args[0]))));
        } catch (IllegalStateException e) {
            System.err.println("not a valid artifact: " + args[0] + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
