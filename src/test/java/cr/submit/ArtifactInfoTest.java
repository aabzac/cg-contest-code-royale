package cr.submit;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtifactInfoTest {
    private static final String SRC = "\n\nclass Player {\n// ---- cr/bot/X.java\nstatic class X {}\n" + Build.RUNNER
        + "static final class CGRunner {\n    public static final String PARAMS = \"KITE=2\";\n    " + Build.EMPTY + "\n}\n}\n";

    @Test
    void reportsTheIdsBuildAndSubmitPrint() throws Exception {
        String built = SRC.replace(Build.EMPTY, "public static final String BUILD = \"" + Build.id(SRC) + "\";");
        byte[] code = built.getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code));
        assertEquals("{\"sha256\": \"" + sha + "\", \"chars\": " + built.length() + ", \"params\": \"KITE=2\", \"build\": \""
            + Build.id(SRC) + "\", \"config\": \"" + Build.config(built) + "\"}", ArtifactInfo.json(code));
        assertEquals("{\"sha256\": \"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(SRC.getBytes(StandardCharsets.UTF_8)))
            + "\", \"chars\": " + SRC.length() + ", \"params\": \"KITE=2\", \"build\": null, \"config\": \"" + Build.config(SRC) + "\"}",
            ArtifactInfo.json(SRC.getBytes(StandardCharsets.UTF_8)), "no id: null");
        assertThrows(IllegalStateException.class, () -> ArtifactInfo.json((built + "// edited\n").getBytes(StandardCharsets.UTF_8)));
    }
}
