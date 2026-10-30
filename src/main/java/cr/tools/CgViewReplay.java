package cr.tools;

import com.google.gson.Gson;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recorded CG viewer states in the local viewer's compact format. No game rules or bots run here.
 * Entity and tooltip deltas are accumulated to their end-of-frame values. The authoritative outcome
 * is ranks/scores, not the final HUD: disqualification scores (-1) are not queen HP.
 */
public final class CgViewReplay {
    private static final Gson JSON = new Gson();
    private static final Pattern UPDATE = Pattern.compile("^U (\\d+) ([0-9.]+) (.*)$");
    private static final Pattern NUMBER = Pattern.compile("(?:^| )(x|y|v|a) (-?\\d+(?:\\.\\d+)?)(?= |$)");
    private static final Pattern IMAGE = Pattern.compile("(?:^| )(i|I) (\\S+)");
    private static final Pattern TEXT_NUMBER = Pattern.compile("(?:^| )T (-?\\d+)(?= |$)");
    private static final Pattern MINE = Pattern.compile("MINE \\(\\+(\\d+)\\)");

    private CgViewReplay() {}

    public static final class Replay {
        public String format = "cg";
        public long gameId, seed;
        public String[] bots;
        public long[] agents;
        public int[] ranks;
        public double[] scores;
        public int winner;
        public final List<int[]> sites = new ArrayList<>();
        public final List<Frame> frames = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
    }

    public static final class Frame {
        public int turn;
        public Integer[] gold = new Integer[2], hp = new Integer[2];
        public Integer[][] queens = new Integer[2][2];
        public final List<Integer[]> creeps = new ArrayList<>();
        /** type, owner, mine rate / tower HP / null (barracks countdown), range / unit type, gold. */
        public final List<Integer[]> structures = new ArrayList<>();
        /** Recorded gauge visibility for each barracks, null for other sites/unavailable gauge. */
        public final List<Boolean> training = new ArrayList<>();
        public String[] cmds;
        public String summary;
    }

    private static final class Entity {
        Integer x, y, text;
        String image, images;
        boolean visible = true;
        double alpha = 1;
    }

    private static final class State {
        final Map<Integer, Entity> entities = new TreeMap<>();
        final Map<Integer, JsonObject> registered = new TreeMap<>();
        final Map<Integer, List<String>> texts = new HashMap<>();

        void apply(CgReplay.View view) {
            registered.putAll(view.registered);
            texts.putAll(view.texts);
            // Within a frame commands are serialized by animation time, as the SDK viewer consumes them.
            for (String command : view.entities) {
                Matcher update = UPDATE.matcher(command);
                if (!update.matches()) continue;
                Entity entity = entities.computeIfAbsent(Integer.parseInt(update.group(1)), ignored -> new Entity());
                String props = update.group(3);
                Matcher number = NUMBER.matcher(props);
                while (number.find()) {
                    double value = Double.parseDouble(number.group(2));
                    switch (number.group(1)) {
                        case "x": entity.x = (int) value; break;
                        case "y": entity.y = (int) value; break;
                        case "v": entity.visible = value != 0; break;
                        case "a": entity.alpha = value; break;
                        default: break;
                    }
                }
                Matcher image = IMAGE.matcher(props);
                while (image.find()) {
                    if (image.group(1).equals("i")) entity.image = image.group(2);
                    else entity.images = image.group(2);
                }
                Matcher text = TEXT_NUMBER.matcher(props);
                while (text.find()) entity.text = Integer.parseInt(text.group(1));
            }
        }

        Entity entity(int id) {
            Entity found = entities.get(id);
            if (found == null) throw new IllegalArgumentException("missing recorded entity " + id);
            return found;
        }

        Integer value(int id, String prefix) { return value(texts.get(id), prefix); }

        static Integer value(List<String> lines, String prefix) {
            if (lines != null) for (String line : lines)
                if (line.startsWith(prefix)) return Integer.valueOf(line.substring(prefix.length()));
            return null;
        }

        Integer hud(int x, int y) {
            for (Entity entity : entities.values())
                if (Integer.valueOf(x).equals(entity.x) && Integer.valueOf(y).equals(entity.y) && entity.text != null)
                    return entity.text;
            return null;
        }

        int unitOwner(int group) {
            Entity circle = entity(group - 2);
            if ("Unite_Base_Rouge".equals(circle.image)) return 0;
            if ("Unite_Base_Bleu".equals(circle.image)) return 1;
            throw new IllegalArgumentException("unknown recorded unit owner for entity " + group);
        }

        Integer buildingOwner(int type, int x, int y) {
            for (Entity entity : entities.values()) {
                if (!entity.visible || entity.alpha == 0 || !Integer.valueOf(x).equals(entity.x)) continue;
                if (type == 0 && Integer.valueOf(y - 40).equals(entity.y)) {
                    if ("Mine_Rouge".equals(entity.image)) return 0;
                    if ("Mine_Bleu".equals(entity.image)) return 1;
                }
                if (!Integer.valueOf(y).equals(entity.y)) continue;
                if (type == 1 && entity.images != null) {
                    if (entity.images.startsWith("TR01,")) return 0;
                    if (entity.images.startsWith("TB01,")) return 1;
                }
                if (type == 2) {
                    if ("Caserne_Rouge".equals(entity.image)) return 0;
                    if ("Caserne_Bleu".equals(entity.image)) return 1;
                }
            }
            return null;
        }

        Boolean training(int x, int y, int radius, Integer owner) {
            if (owner == null) return null;
            String image = owner == 0 ? "Caserne_Rouge_Jauge" : "Caserne_Bleu_Jauge";
            // A replaced barracks leaves hidden sprites behind; the newest matching gauge belongs to it.
            Boolean result = null;
            for (Entity entity : entities.values())
                if (image.equals(entity.image) && Integer.valueOf(x).equals(entity.x)
                        && Integer.valueOf(y + radius * 53 / 68).equals(entity.y)) result = entity.visible;
            return result;
        }
    }

    /** Local files already contain viewer frames; CG responses are decoded from their recorded graphics. */
    public static JsonObject load(Path file) throws IOException {
        JsonObject raw;
        try {
            raw = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (raw.has("sites") && raw.has("frames") && raw.has("bots")) {
                if (raw.getAsJsonArray("frames").isEmpty()) throw new IllegalArgumentException("local replay has no frames");
                raw.addProperty("format", "local");
                return raw;
            }
            return JSON.toJsonTree(decode(CgReplay.load(file))).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Cannot decode replay: " + e.getMessage(), e);
        }
    }

    public static Replay decode(CgReplay input) {
        Replay replay = new Replay();
        replay.gameId = input.gameId;
        replay.seed = input.seed;
        replay.bots = input.pseudo.clone();
        replay.agents = input.agentId.clone();
        replay.ranks = input.ranks.clone();
        replay.scores = input.scores.clone();
        replay.winner = input.ranks[0] == input.ranks[1] ? -1 : input.ranks[0] < input.ranks[1] ? 0 : 1;
        for (int p = 0; p < 2; p++) if (replay.bots[p] == null) replay.bots[p] = "Agent " + replay.agents[p];
        State state = new State();
        state.apply(input.init);
        TreeMap<Integer, Integer> siteEntities = new TreeMap<>();
        for (Map.Entry<Integer, JsonObject> entry : state.registered.entrySet()) {
            JsonObject properties = entry.getValue();
            if ("Site".equals(type(properties))) siteEntities.put(properties.get("id").getAsInt(), entry.getKey());
        }
        if (siteEntities.isEmpty()) throw new IllegalArgumentException("no recorded sites");
        for (Map.Entry<Integer, Integer> site : siteEntities.entrySet()) {
            Entity entity = state.entity(site.getValue());
            Integer radius = state.value(site.getValue(), "Radius: ");
            if (entity.x == null || entity.y == null || radius == null)
                throw new IllegalArgumentException("incomplete recorded site " + site.getKey());
            replay.sites.add(new int[]{site.getKey(), entity.x, entity.y, radius});
        }
        replay.frames.add(frame(state, siteEntities, replay, 0, new String[]{"", ""}, null));
        int turn = 0;
        for (CgReplay.Turn inputTurn : input.turns) {
            state.apply(inputTurn.view);
            replay.frames.add(frame(state, siteEntities, replay, ++turn, inputTurn.stdout.clone(), inputTurn.summary));
        }
        return replay;
    }

    private static Frame frame(State state, TreeMap<Integer, Integer> sites, Replay replay, int turn,
                               String[] commands, String summary) {
        Frame result = new Frame();
        result.turn = turn;
        result.cmds = commands;
        result.summary = summary;
        result.gold = new Integer[]{state.hud(700, 1040), state.hud(1020, 1040)};
        result.hp = new Integer[]{state.hud(553, 1053), state.hud(1771, 1053)};
        for (Map.Entry<Integer, JsonObject> entry : state.registered.entrySet()) {
            String type = type(entry.getValue());
            if ("Site".equals(type)) continue;
            int id = entry.getKey(), owner = state.unitOwner(id);
            Entity entity = state.entity(id);
            if (entity.x == null || entity.y == null) throw new IllegalArgumentException("missing unit position " + id);
            if ("Queen".equals(type)) result.queens[owner] = new Integer[]{entity.x, entity.y};
            else {
                Integer hp = state.value(id - 2, "Health: ");
                if (hp != null && hp <= 0) continue;
                if (!entity.visible || state.entity(id - 2).alpha == 0) continue;
                result.creeps.add(new Integer[]{owner, creepType(type), entity.x, entity.y, hp});
            }
        }
        for (int p = 0; p < 2; p++) if (result.queens[p][0] == null)
            throw new IllegalArgumentException("missing queen " + p);
        int index = 0;
        for (Map.Entry<Integer, Integer> site : sites.entrySet()) {
            int[] geometry = replay.sites.get(index++);
            List<String> lines = state.texts.getOrDefault(site.getValue(), List.of());
            int type = -1;
            Integer param1 = null, param2 = null;
            for (String line : lines) {
                Matcher mine = MINE.matcher(line);
                if (mine.matches()) { type = 0; param1 = Integer.valueOf(mine.group(1)); }
                else if (line.equals("TOWER")) {
                    type = 1;
                    param1 = State.value(lines, "Health: ");
                    param2 = State.value(lines, "Range: ");
                } else if (line.startsWith("BARRACKS (") && line.endsWith(")")) {
                    type = 2;
                    param2 = creepType(line.substring(10, line.length() - 1));
                }
            }
            Integer owner = type == -1 ? -1 : state.buildingOwner(type, geometry[1], geometry[2]);
            if (type != -1 && owner == null) {
                String warning = "No recorded owner for site " + site.getKey() + " on turn " + turn;
                replay.warnings.add(warning);
            }
            result.structures.add(new Integer[]{type, owner, param1, param2, State.value(lines, "Remaining gold: ")});
            result.training.add(type == 2 ? state.training(geometry[1], geometry[2], geometry[3], owner) : null);
        }
        return result;
    }

    private static String type(JsonObject properties) {
        return properties.has("type") ? properties.get("type").getAsString() : "";
    }

    private static int creepType(String name) {
        return switch (name) {
            case "KNIGHT" -> 0;
            case "ARCHER" -> 1;
            case "GIANT" -> 2;
            default -> throw new IllegalArgumentException("unknown recorded unit type " + name);
        };
    }
}
