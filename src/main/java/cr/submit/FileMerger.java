package cr.submit;

import cr.bot.Params;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Merges the shipped sources into build/submit/Player.java (top-level types become static
 * nested classes of Player), sets CGRunner.PARAMS and CGRunner.BUILD (see {@link Build}), compiles
 * the result and checks its size.
 * The file is ASCII with LF line endings and the same file order whatever the checkout and OS, so
 * its sha256 identifies the code. All checks run before anything is touched, and the new artifact
 * replaces the old one only once it has compiled, so build/submit always holds a complete artifact:
 * after any failure, the old one.
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
        System.out.println(run(args, Paths.get("build/submit")));
    }

    /** Merges the shipped files with the params in args into outDir; returns the summary line. */
    public static String run(String[] args, Path outDir) throws Exception {
        // On Windows a quote in -Pparams does not survive the command line and can split the argument.
        if (args.length > 1) throw new IllegalArgumentException("expected at most one params argument, got " + args.length + ": " + List.of(args));
        String params = args.length > 0 ? args[0] : "";
        List<Path> files = shippedFiles();
        String src = merge(files, params);
        String build = Build.of(src), config = Build.config(src);
        Path out = build(outDir, src);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(src.getBytes(StandardCharsets.US_ASCII)));
        return String.format("%s: %d chars (limit %d), %d files, params=\"%s\" (effective %s), build=%s, config=%s, sha256=%s",
            out, src.length(), MAX_CHARS, files.size(), params, new Params().apply(params), build, config, sha);
    }

    /**
     * Writes src to outDir/Player.java and compiles it into outDir/classes. Both are made in a sibling
     * directory (.new) that replaces outDir only after the compile: if anything fails, outDir keeps the
     * old artifact whole. Returns the Player.java path.
     */
    static Path build(Path outDir, String src) throws IOException {
        if (src.length() > MAX_CHARS) throw new IllegalStateException("source too large: " + src.length());
        Path fresh = outDir.resolveSibling(outDir.getFileName() + ".new"), old = outDir.resolveSibling(outDir.getFileName() + ".old");
        deleteTree(fresh);
        deleteTree(old);
        try {
            Path classes = fresh.resolve("classes"), out = fresh.resolve("Player.java");
            Files.createDirectories(classes);
            Files.writeString(out, src, StandardCharsets.US_ASCII);
            JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
            if (jc.run(null, null, null, "-d", classes.toString(), "-encoding", "US-ASCII", out.toString()) != 0)
                throw new IllegalStateException("merged file does not compile");
            // Renames: a directory with a file held open stays where it is, whole.
            if (Files.exists(outDir)) Files.move(outDir, old, StandardCopyOption.ATOMIC_MOVE);
            Files.move(fresh, outDir, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            try {
                if (!Files.exists(outDir) && Files.exists(old)) Files.move(old, outDir, StandardCopyOption.ATOMIC_MOVE);
                deleteTree(fresh);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        try {
            deleteTree(old);
        } catch (IOException e) {
            // The new artifact is in place; the next build retries (and stops before touching anything).
            System.err.println("could not delete " + old + ": " + e);
        }
        return outDir.resolve("Player.java");
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> st = Files.walk(dir)) {
            for (Path p : st.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(p);
        }
    }

    /** Shipped source files in merge order: by file name within each directory, the same on every OS. */
    static List<Path> shippedFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String s : SHIP) {
            Path p = SRC.resolve(s);
            if (Files.isDirectory(p)) {
                try (Stream<Path> st = Files.list(p)) {
                    files.addAll(st.filter(f -> f.toString().endsWith(".java"))
                        .sorted(Comparator.comparing(f -> f.getFileName().toString())).collect(Collectors.toList()));
                }
            } else files.add(p);
        }
        for (String s : SKIP) files.remove(SRC.resolve(s));
        return files;
    }

    /** The merged Player.java text for shipped files under SRC. */
    static String merge(List<Path> files, String params) throws IOException {
        return merge(SRC, files, params);
    }

    /**
     * The merged Player.java text; each file is headed by its path relative to root. Its BUILD literal
     * holds the {@link Build#id} of the same text with an empty one. Fails on params that do not apply
     * or do not fit the PARAMS literal, on non-ASCII sources or file names, on a CR outside CRLF, unless
     * the PARAMS and BUILD markers occur exactly once, and unless the result passes {@link Build#of}
     * (no second BUILD literal) and {@link Build#config} (the runner's section once, last).
     */
    static String merge(Path root, List<Path> files, String params) throws IOException {
        for (int i = 0; i < params.length(); i++) {
            char ch = params.charAt(i);
            if (ch < 0x20 || ch > 0x7e || ch == '"' || ch == '\\')
                throw new IllegalArgumentException(String.format("params character U+%04X does not fit the PARAMS literal", (int) ch));
        }
        new Params().apply(params);
        Set<String> imports = new LinkedHashSet<>();
        StringBuilder body = new StringBuilder();
        for (Path f : files) {
            // Line endings depend on the checkout (core.autocrlf); the artifact always gets LF.
            String text = Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
            int cr = text.indexOf('\r');
            if (cr >= 0) throw new IllegalStateException("CR without LF at " + f + ":" + lineOf(text, cr));
            int bad = firstNonAscii(text);
            if (bad >= 0)
                throw new IllegalStateException(String.format("non-ASCII character U+%04X at %s:%d", (int) text.charAt(bad), f, lineOf(text, bad)));
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
            body.append("// ---- ").append(root.relativize(f).toString().replace('\\', '/')).append('\n').append(code);
        }
        String src = String.join("\n", imports) + "\n\n"
            + "class Player {\n"
            + "    public static void main(String[] args) throws Exception { CGRunner.main(args); }\n\n"
            + body + "}\n";
        String marker = "public static final String PARAMS = \"\";";
        int at = src.indexOf(marker);
        if (at < 0 || src.indexOf(marker, at + 1) >= 0) throw new IllegalStateException("PARAMS marker must occur exactly once");
        String merged = src.replace(marker, "public static final String PARAMS = \"" + params + "\";");
        int bad = firstNonAscii(merged);
        if (bad >= 0)
            throw new IllegalStateException(String.format("non-ASCII character U+%04X at merged line %d", (int) merged.charAt(bad), lineOf(merged, bad)));
        at = merged.indexOf(Build.EMPTY);
        if (at < 0 || merged.indexOf(Build.EMPTY, at + 1) >= 0) throw new IllegalStateException("BUILD marker must occur exactly once");
        String built = merged.replace(Build.EMPTY, "public static final String BUILD = \"" + Build.id(merged) + "\";");
        Build.of(built);
        Build.config(built);
        return built;
    }

    /** 1-based line of the character at index i. */
    private static int lineOf(String text, int i) {
        return text.substring(0, i).split("\n", -1).length;
    }

    /** Index of the first character outside ASCII, or -1. */
    static int firstNonAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 0x80) return i;
        return -1;
    }
}
