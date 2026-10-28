package cr.bot;

import cr.bot.current.MainBot;
import cr.bot.v00.WaitBot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Bot specs: "name" or "name?K=v,K2=v". Frozen versions load their full parameter snapshot from
 * cr/bot/versions/&lt;name&gt;.properties (key PARAMS) before overrides; a missing snapshot fails.
 */
public final class BotFactory {
    private BotFactory() {}

    public static Bot create(String spec) {
        String name = spec, overrides = "";
        int q = spec.indexOf('?');
        if (q >= 0) { name = spec.substring(0, q); overrides = spec.substring(q + 1); }
        switch (name) {
            case "v00": return new WaitBot();
            case "current": return new MainBot(new Params().apply(overrides));
            default:
                throw new IllegalArgumentException("unknown bot " + name);
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
