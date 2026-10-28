package cr.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A CG gameResult/findByGameId response decoded into turns. The view uses the SDK entity and
 * tooltip modules; see docs/replay_format.md.
 */
public final class CgReplay {
    public long gameId;
    public long seed;
    public String[] pseudo = new String[2];
    public long[] agentId = new long[2];
    public int[] ranks = new int[2];
    public double[] scores = new double[2];
    /** Init frame. */
    public View init;
    public final List<Turn> turns = new ArrayList<>();

    public static final class Turn {
        /** Raw stdout per player, null when the frame had none. */
        public final String[] stdout = new String[2];
        public String summary;
        public View view;
    }

    public static final class View {
        public final List<String> entities = new ArrayList<>();
        /** Tooltip registrations (entity id → properties) and extra-text updates of this frame. */
        public final Map<Integer, JsonObject> registered = new HashMap<>();
        public final Map<Integer, List<String>> texts = new HashMap<>();
    }

    private static final Pattern T_PROP = Pattern.compile("(?:^| )T (\\S*)");
    private static final Pattern X_PROP = Pattern.compile("(?:^| )x (-?\\d+)");
    private static final Pattern Y_PROP = Pattern.compile("(?:^| )y (-?\\d+)");

    /** Properties of entity update commands "U id time props" for one entity in this view. */
    public static String entityProps(View v, int id) {
        String prefix = "U " + id + " ";
        StringBuilder b = new StringBuilder();
        for (String e : v.entities) {
            if (!e.startsWith(prefix)) continue;
            int sp = e.indexOf(' ', prefix.length());
            if (sp >= 0) b.append(' ').append(e.substring(sp + 1));
        }
        return b.toString();
    }

    public static String textOf(String props) { return last(T_PROP, props); }
    public static String xOf(String props) { return last(X_PROP, props); }
    public static String yOf(String props) { return last(Y_PROP, props); }

    private static String last(Pattern p, String s) {
        Matcher m = p.matcher(s);
        String r = null;
        while (m.find()) r = m.group(1);
        return r;
    }

    private static View parseView(String raw) {
        View v = new View();
        if (raw == null) return v;
        int nl = raw.indexOf('\n');
        if (nl < 0 || nl + 1 >= raw.length()) return v;
        String body = raw.substring(nl + 1).trim();
        if (body.isEmpty()) return v;
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        JsonObject frame = o.has("frame") ? o.getAsJsonObject("frame") : o;
        if (frame.has("entitymodule")) for (JsonElement e : frame.getAsJsonArray("entitymodule")) v.entities.add(e.getAsString());
        if (frame.has("tooltips")) {
            JsonArray tt = frame.getAsJsonArray("tooltips");
            if (tt.size() > 0) for (Map.Entry<String, JsonElement> e : tt.get(0).getAsJsonObject().entrySet())
                v.registered.put(Integer.parseInt(e.getKey()), e.getValue().getAsJsonObject());
            if (tt.size() > 1) for (Map.Entry<String, JsonElement> e : tt.get(1).getAsJsonObject().entrySet()) {
                List<String> lines = new ArrayList<>();
                for (JsonElement l : e.getValue().getAsJsonArray()) lines.add(l.getAsString());
                v.texts.put(Integer.parseInt(e.getKey()), lines);
            }
        }
        return v;
    }

    public static CgReplay load(Path file) throws IOException {
        JsonObject g = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!g.has("frames")) throw new IOException("no frames in " + file);
        CgReplay r = new CgReplay();
        r.gameId = g.get("gameId").getAsLong();
        for (String line : g.get("refereeInput").getAsString().split("\n")) {
            line = line.trim();
            if (line.startsWith("seed=")) r.seed = Long.parseLong(line.substring(5));
        }
        for (JsonElement ae : g.getAsJsonArray("agents")) {
            JsonObject a = ae.getAsJsonObject();
            int idx = a.get("index").getAsInt();
            r.agentId[idx] = a.get("agentId").getAsLong();
            if (a.has("codingamer") && a.getAsJsonObject("codingamer").has("pseudo"))
                r.pseudo[idx] = a.getAsJsonObject("codingamer").get("pseudo").getAsString();
        }
        JsonArray ranks = g.getAsJsonArray("ranks"), scores = g.getAsJsonArray("scores");
        for (int i = 0; i < 2; i++) { r.ranks[i] = ranks.get(i).getAsInt(); r.scores[i] = scores.get(i).getAsDouble(); }

        JsonArray frames = g.getAsJsonArray("frames");
        r.init = parseView(frames.get(0).getAsJsonObject().get("view").getAsString());
        Turn cur = new Turn();
        for (int i = 1; i < frames.size(); i++) {
            JsonObject f = frames.get(i).getAsJsonObject();
            int agent = f.has("agentId") ? f.get("agentId").getAsInt() : -1;
            if (agent >= 0 && f.has("stdout")) cur.stdout[agent] = f.get("stdout").getAsString();
            if (f.has("summary") && !f.get("summary").isJsonNull() && !f.get("summary").getAsString().isEmpty())
                cur.summary = (cur.summary == null ? "" : cur.summary + " | ") + f.get("summary").getAsString();
            if (f.has("keyframe") && f.get("keyframe").getAsBoolean()) {
                cur.view = parseView(f.get("view").getAsString());
                r.turns.add(cur);
                cur = new Turn();
            }
        }
        return r;
    }

    /** First two lines of a stdout block (the referee reads exactly two per turn). */
    public static String[] lines(String stdout) {
        if (stdout == null) return new String[]{null, null};
        String[] ls = stdout.split("\n", -1);
        return new String[]{ls.length > 0 ? ls[0] : null, ls.length > 1 ? ls[1] : null};
    }
}
