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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Bot specs: "name" or "name?K=v,K2=v". Frozen versions load their full parameter snapshot from
 * cr/bot/versions/&lt;name&gt;.properties (key PARAMS) before overrides; a missing snapshot fails.
 * External programs: "submit" (build/submit/classes Player, run `submit` first), "bossN" (the
 * referee repository's level-N default AI, compiled from refs/referee-csj; boss = boss4, the
 * Bronze candidate) and "proc:&lt;command line&gt;".
 */
public final class BotFactory {
    private BotFactory() {}

    public static Bot create(String spec) {
        String name = spec, overrides = "";
        int q = spec.indexOf('?');
        if (q >= 0 && !spec.startsWith("proc:")) { name = spec.substring(0, q); overrides = spec.substring(q + 1); }
        if (name.startsWith("proc:")) return new ProcessBot(name, Arrays.asList(name.substring(5).trim().split("\\s+")), false);
        if (name.equals("submit")) {
            if (!Files.exists(Paths.get("build/submit/classes/Player.class"))) throw new IllegalStateException("run ./gradlew submit first");
            return new ProcessBot(name, List.of("java", "-cp", "build/submit/classes", "Player"), false);
        }
        if (name.startsWith("boss")) {
            int level = name.length() > 4 ? Integer.parseInt(name.substring(4)) : 4;
            return new ProcessBot(name, List.of("java", "-cp", compileBoss(level).toString(), "Player"), false);
        }
        switch (name) {
            case "v00": return new WaitBot();
            case "v01": return new GreedyBot(snapshot("v01").apply(overrides));
            case "current": return new MainBot(new Params().apply(overrides));
            default:
                throw new IllegalArgumentException("unknown bot " + name);
        }
    }

    /** Compiles refs/referee-csj/config/level&lt;N&gt;/Boss.java once into build/opponents/boss&lt;N&gt;. */
    static synchronized Path compileBoss(int level) {
        Path src = Paths.get("refs/referee-csj/config/level" + level + "/Boss.java");
        Path out = Paths.get("build/opponents/boss" + level);
        try {
            Path cls = out.resolve("Player.class");
            if (Files.exists(cls) && Files.getLastModifiedTime(cls).compareTo(Files.getLastModifiedTime(src)) >= 0) return out;
            if (!Files.exists(src)) throw new IllegalStateException("missing " + src + " (clone csj/code-royale into refs/referee-csj)");
            Files.createDirectories(out);
            int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", out.toString(), "-nowarn", src.toString());
            if (rc != 0) throw new IllegalStateException("boss" + level + " does not compile");
            return out;
        } catch (IOException e) {
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
