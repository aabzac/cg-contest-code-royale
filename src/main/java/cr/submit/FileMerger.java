package cr.submit;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Merges the shipped sources into build/submit/Player.java (top-level types become static
 * nested classes of Player), sets CGRunner.PARAMS, compiles the result and checks its size.
 * Usage: FileMerger [params]
 */
public final class FileMerger {
    static final Path SRC = Paths.get("src/main/java");
    /** Shipped files, relative to SRC; directories include all their direct .java files. */
    static final String[] SHIP = {"cr/core", "cr/bot/Bot.java", "cr/bot/Params.java", "cr/bot/current", "cr/submit/CGRunner.java"};
    static final String[] SKIP = {"cr/core/MapGen.java"};
    public static final int MAX_CHARS = 100_000;

    private static final Pattern TOP = Pattern.compile("^(public )?(final |abstract )?(class|interface|enum|record) ", Pattern.MULTILINE);

    public static void main(String[] args) throws Exception {
        String params = args.length > 0 ? args[0] : "";
        List<Path> files = new ArrayList<>();
        for (String s : SHIP) {
            Path p = SRC.resolve(s);
            if (Files.isDirectory(p)) {
                try (Stream<Path> st = Files.list(p)) {
                    files.addAll(st.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList()));
                }
            } else files.add(p);
        }
        for (String s : SKIP) files.remove(SRC.resolve(s));

        Set<String> imports = new LinkedHashSet<>();
        StringBuilder body = new StringBuilder();
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            StringBuilder kept = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                String t = line.trim();
                if (t.startsWith("package ")) continue;
                if (t.startsWith("import ")) {
                    if (!t.startsWith("import cr.") && !t.startsWith("import static cr.")) imports.add(t);
                    else if (t.startsWith("import static cr.")) throw new IllegalStateException("static import of project class in " + f);
                    continue;
                }
                kept.append(line).append('\n');
            }
            String code = TOP.matcher(kept.toString()).replaceAll("static $2$3 ");
            if (code.contains("cr.core.") || code.contains("cr.bot.") || code.contains("cr.submit."))
                throw new IllegalStateException("qualified project reference left in " + f);
            body.append("// ---- ").append(SRC.relativize(f).toString().replace('\\', '/')).append('\n').append(code);
        }
        String src = String.join("\n", imports) + "\n\n"
            + "class Player {\n"
            + "    public static void main(String[] args) throws Exception { CGRunner.main(args); }\n\n"
            + body + "}\n";
        String marker = "public static final String PARAMS = \"\";";
        if (!src.contains(marker)) throw new IllegalStateException("PARAMS marker not found");
        src = src.replace(marker, "public static final String PARAMS = \"" + params.replace("\"", "") + "\";");

        Path outDir = Paths.get("build/submit");
        Files.createDirectories(outDir.resolve("classes"));
        Path out = outDir.resolve("Player.java");
        Files.writeString(out, src, StandardCharsets.UTF_8);

        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        int rc = jc.run(null, null, null, "-d", outDir.resolve("classes").toString(), "-encoding", "UTF-8", out.toString());
        if (rc != 0) throw new IllegalStateException("merged file does not compile");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(src.getBytes(StandardCharsets.UTF_8)));
        System.out.printf("%s: %d chars (limit %d), %d files, params=\"%s\", sha256=%s%n", out, src.length(), MAX_CHARS, files.size(), params, sha);
        if (src.length() > MAX_CHARS) throw new IllegalStateException("source too large: " + src.length());
    }
}
