package cr.bot;

import cr.bot.current.MainBot;
import cr.bot.v00.WaitBot;
import cr.bot.v01.GreedyBot;
import cr.referee.ProcessBot;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Bot specs: "name" or "name?K=v,K2=v". Frozen versions load their full parameter snapshot from
 * cr/bot/versions/&lt;name&gt;.properties (key PARAMS) before overrides; a missing snapshot fails.
 * External programs: "submit" (build/submit/classes Player, run `submit` first), "bossN" (the
 * referee repository's level-N default AI, compiled from refs/referee-csj; boss = boss4), "old"
 * (the code of the replaced arena entry 3315948, which the user keeps outside the project:
 * refs/old-3315948/Player.java, compiled into build/opponents/old) and "proc:&lt;command line&gt;".
 * In-process opponents: "field" ({@link cr.bot.field.FieldBot} with its defaults) and "rival" (the same bot
 * with {@link cr.bot.field.FieldParams#RIVAL} before the overrides, P2's gate G5).
 */
public final class BotFactory {
    static final Path OLD_SOURCE = Paths.get("refs/old-3315948/Player.java");

    private BotFactory() {}

    public static Bot create(String spec) {
        String name = spec, overrides = "";
        int q = spec.indexOf('?');
        if (q >= 0 && !spec.startsWith("proc:")) { name = spec.substring(0, q); overrides = spec.substring(q + 1); }
        if (name.startsWith("proc:")) return new ProcessBot(name, Arrays.asList(name.substring(5).trim().split("\\s+")), false);
        // Their parameters are fixed when they are built (submit: ./gradlew submit -Pparams=...).
        if (!overrides.isEmpty() && (name.equals("submit") || name.startsWith("boss") || name.equals("old") || name.equals("v00")))
            throw new IllegalArgumentException(name + " takes no parameters: " + spec);
        if (name.equals("submit")) {
            if (!Files.exists(Paths.get("build/submit/classes/Player.class"))) throw new IllegalStateException("run ./gradlew submit first");
            return new ProcessBot(name, List.of("java", "-cp", "build/submit/classes", "Player"), false);
        }
        if (name.startsWith("boss")) {
            int level = name.length() > 4 ? Integer.parseInt(name.substring(4)) : 4;
            return new ProcessBot(name, List.of("java", "-cp", compileBoss(level).toString(), "Player"), false);
        }
        if (name.equals("old")) {
            Path out = compile(OLD_SOURCE, Paths.get("build/opponents/old"),
                "the code of entry 3315948, which the user keeps from the IDE");
            return new ProcessBot(name, List.of("java", "-cp", out.toString(), "Player"), false);
        }
        switch (name) {
            case "v00": return new WaitBot();
            case "v01": return new GreedyBot(snapshot("v01").apply(overrides));
            case "v02": return new cr.bot.v02.GreedyBot(snapshot("v02").apply(overrides));
            case "v03": return new cr.bot.v03.GreedyBot(snapshot("v03").apply(overrides));
            case "v04": return new cr.bot.v04.GreedyBot(snapshot("v04").apply(overrides));
            case "v05": return new cr.bot.v05.GreedyBot(snapshot("v05").apply(overrides));
            case "v06": return new cr.bot.v06.GreedyBot(snapshot("v06").apply(overrides));
            case "v07": return new cr.bot.v07.GreedyBot(snapshot("v07").apply(overrides));
            case "v08": return new cr.bot.v08.GreedyBot(snapshot("v08").apply(overrides));
            case "v09": return new cr.bot.v09.GreedyBot(snapshot("v09").apply(overrides));
            case "v10": return new cr.bot.v10.GreedyBot(snapshot("v10").apply(overrides));
            case "v11": return new cr.bot.v11.GreedyBot(snapshot("v11").apply(overrides));
            case "v12": return new cr.bot.v12.GreedyBot(snapshot("v12").apply(overrides));
            case "v13": return new cr.bot.v13.GreedyBot(snapshot("v13").apply(overrides));
            case "v14": return new cr.bot.v14.GreedyBot(snapshot("v14").apply(overrides));
            case "current": return new MainBot(new Params().apply(overrides));
            case "field": return new cr.bot.field.FieldBot(new cr.bot.field.FieldParams().apply(overrides));
            case "rival": return new cr.bot.field.FieldBot(new cr.bot.field.FieldParams().apply(cr.bot.field.FieldParams.RIVAL).apply(overrides));
            default:
                throw new IllegalArgumentException("unknown bot " + name);
        }
    }

    /**
     * What a spec plays, for experiment records: the full effective parameters of an in-process bot,
     * the sha256 of an external program's source.
     */
    public static String describe(String spec) {
        String name = spec, overrides = "";
        int q = spec.indexOf('?');
        if (q >= 0 && !spec.startsWith("proc:")) { name = spec.substring(0, q); overrides = spec.substring(q + 1); }
        if (name.startsWith("proc:")) return spec;
        if (name.equals("submit")) return "submit sha256=" + sha256(Paths.get("build/submit/Player.java"));
        if (name.startsWith("boss")) {
            int level = name.length() > 4 ? Integer.parseInt(name.substring(4)) : 4;
            return name + " sha256=" + sha256(Paths.get("refs/referee-csj/config/level" + level + "/Boss.java"));
        }
        if (name.equals("old")) return "old sha256=" + sha256(OLD_SOURCE);
        switch (name) {
            case "v00": return "v00";
            case "v01": return "v01 " + snapshot("v01").apply(overrides);
            case "v02": return "v02 " + snapshot("v02").apply(overrides);
            case "v03": return "v03 " + snapshot("v03").apply(overrides);
            case "v04": return "v04 " + snapshot("v04").apply(overrides);
            case "v05": return "v05 " + snapshot("v05").apply(overrides);
            case "v06": return "v06 " + snapshot("v06").apply(overrides);
            case "v07": return "v07 " + snapshot("v07").apply(overrides);
            case "v08": return "v08 " + snapshot("v08").apply(overrides);
            case "v09": return "v09 " + snapshot("v09").apply(overrides);
            case "v10": return "v10 " + snapshot("v10").apply(overrides);
            case "v11": return "v11 " + snapshot("v11").apply(overrides);
            case "v12": return "v12 " + snapshot("v12").apply(overrides);
            case "v13": return "v13 " + snapshot("v13").apply(overrides);
            case "v14": return "v14 " + snapshot("v14").apply(overrides);
            case "current": return "current " + new Params().apply(overrides);
            case "field": return "field " + new cr.bot.field.FieldParams().apply(overrides);
            case "rival": return "rival " + new cr.bot.field.FieldParams().apply(cr.bot.field.FieldParams.RIVAL).apply(overrides);
            default:
                throw new IllegalArgumentException("unknown bot " + name);
        }
    }

    /** Compiles refs/referee-csj/config/level&lt;N&gt;/Boss.java once into build/opponents/boss&lt;N&gt;. */
    static Path compileBoss(int level) {
        return compile(Paths.get("refs/referee-csj/config/level" + level + "/Boss.java"), Paths.get("build/opponents/boss" + level),
            "clone csj/code-royale into refs/referee-csj");
    }

    /** Compiles a single-file Player into out, unless its class is newer than the source. */
    static synchronized Path compile(Path src, Path out, String hint) {
        try {
            if (!Files.exists(src)) throw new IllegalStateException("missing " + src + " (" + hint + ")");
            Path cls = out.resolve("Player.class");
            if (Files.exists(cls) && Files.getLastModifiedTime(cls).compareTo(Files.getLastModifiedTime(src)) >= 0) return out;
            Files.createDirectories(out);
            int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", out.toString(), "-nowarn", src.toString());
            if (rc != 0) throw new IllegalStateException(src + " does not compile");
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** sha256 of a file in hex, or "missing". */
    static String sha256(Path file) {
        try {
            if (!Files.exists(file)) return "missing";
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Full parameter snapshot of a frozen version. */
    static Params snapshot(String version) {
        try (InputStream in = BotFactory.class.getResourceAsStream("versions/" + version + ".properties")) {
            if (in == null) throw new IllegalArgumentException("no parameter snapshot for " + version);
            Properties p = new Properties();
            p.load(in);
            return new Params().apply(p.getProperty("PARAMS"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
