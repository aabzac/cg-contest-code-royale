package cr.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import cr.bot.BotFactory;
import cr.bot.Params;
import cr.referee.Referee;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local development matches only. The allowlist never accepts process commands or arbitrary parameters. */
final class LocalGames {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final Set<String> ROW_FIELDS = Set.of("localGameVersion", "createdAt", "botIds", "botConfigIds", "botFlags",
        "bots", "botSpecs", "effectiveParams", "seed", "winner", "turns", "score", "errors");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private static final List<Choice> CHOICES = List.of(
        new Choice("current", "current", "Текущий бот с выбираемыми флагами движения", "current"),
        new Choice("v08", "v08", "Кайтинг у башен: выбранная замороженная версия", "v08"),
        new Choice("v07", "v07", "Кайтинг под рыцарями: прежняя замороженная версия", "v07"),
        new Choice("v06", "v06", "Вторая башня под угрозой и второй барак: прежняя замороженная версия", "v06"),
        new Choice("v05", "v05", "Две шахты до барака: прежняя замороженная версия", "v05"),
        new Choice("v04", "v04", "Планировщик маршрута королевы: прежняя замороженная версия", "v04"),
        new Choice("v03", "v03", "Ранние башни: прежняя замороженная версия", "v03"),
        new Choice("v02", "v02", "Замороженная версия с обходом застреваний", "v02"),
        new Choice("v01", "v01", "Первый жадный бот", "v01"),
        new Choice("field", "field", "Соперник с балансом поля: три шахты, доход 8, четыре башни по 600", "field"),
        new Choice("v00", "v00", "Простой бот WAIT", "v00")
    );
    private static final List<Flag> FLAGS = availableFlags();
    private record Choice(String id, String label, String description, String spec) {}
    private record Flag(String id, String label, String description, boolean defaultValue) {}
    private record Selection(String id, String configId, String label, String spec, Map<String, Boolean> flags) {}
    @FunctionalInterface interface MatchRunner { Referee.Result play(long seed, String bot0, String bot1); }
    static final class Conflict extends RuntimeException { Conflict(String message) { super(message); } }

    /** Known UI flags that actually exist in this server's bot; unrelated Params stay private. */
    private static List<Flag> availableFlags() {
        Params defaults = new Params();
        List<Flag> flags = new ArrayList<>();
        addAvailableFlag(flags, defaults, "UNSTICK", "Обход застреваний", "Обход, когда прямое движение к площадке почти останавливается");
        addAvailableFlag(flags, defaults, "APPROACH", "Подход к следующей цели", "Выбор точки касания на последнем шаге с учётом следующей площадки");
        addAvailableFlag(flags, defaults, "ROUTE", "Планирование маршрута", "Заблаговременный обход препятствий до выбранной площадки");
        return List.copyOf(flags);
    }

    private static void addAvailableFlag(List<Flag> flags, Params defaults, String id, String label, String description) {
        try {
            var field = Params.class.getField(id);
            if (field.getType() != int.class || java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                throw new IllegalStateException("Current flag must be an instance int parameter: " + id);
            int value = field.getInt(defaults);
            if (value != 0 && value != 1) throw new IllegalStateException("Current flag default must be 0 or 1: " + id);
            flags.add(new Flag(id, label, description, value == 1));
        } catch (NoSuchFieldException absent) {
            // This checkout does not implement the optional flag: neither advertise nor accept it.
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read current flag: " + id, failure);
        }
    }

    private final Path root;
    private final AtomicBoolean running = new AtomicBoolean();
    private final MatchRunner runner;
    private final Map<String, String> runtimeFiles;
    private final Map<String, Object> provenance;

    LocalGames(Path root) throws IOException {
        this(root, (seed, a, b) -> Referee.play(seed, BotFactory.create(a), BotFactory.create(b), true, 50));
    }

    LocalGames(Path root, MatchRunner runner) throws IOException {
        this.root = root.toRealPath();
        this.runner = runner;
        runtimeFiles = runtimeSnapshot();
        provenance = new LinkedHashMap<>();
        provenance.put("capturedAt", Instant.now().toString());
        provenance.put("kind", "server-classpath-snapshot");
        provenance.put("sha256", sha256(JSON.toJson(runtimeFiles).getBytes(StandardCharsets.UTF_8)));
        provenance.put("files", runtimeFiles);
        provenance.put("javaVersion", System.getProperty("java.version"));
        provenance.put("javaVendor", System.getProperty("java.vendor"));
        provenance.put("revisionAtServerStart", revision());
        provenance.put("note", "Compiled class/resource bytes observed at server startup, not a source-to-bytecode attestation. Restart gradlew serve after editing or compiling code. Source revision is recorded separately.");
        verifyRuntime();
    }

    Map<String, Object> bots() {
        return Map.of("bots", CHOICES.stream().map(c -> Map.of("id", c.id, "label", c.label, "description", c.description,
                "flags", c.id.equals("current") ? FLAGS.stream().map(f -> Map.of("id", f.id, "label", f.label,
                    "description", f.description, "default", f.defaultValue)).toList() : List.of())).toList(),
            "defaults", Map.of("bot0", "current", "bot1", "v08"));
    }

    Map<String, Object> run(JsonObject request) throws IOException {
        for (String key : request.keySet()) if (!Set.of("bot0", "bot1", "flags0", "flags1", "seed").contains(key))
            throw new IllegalArgumentException("Unknown request field: " + key);
        Selection bot0 = selection(request.get("bot0"), request.get("flags0"));
        Selection bot1 = selection(request.get("bot1"), request.get("flags1"));
        long seed = seed(request.get("seed"));
        if (!running.compareAndSet(false, true)) throw new Conflict("A local game is already running. Wait for it to finish.");
        try {
            verifyRuntime();
            String pairDirectory = "replays/local/" + bot0.configId + "-vs-" + bot1.configId;
            Path directory = directory(pairDirectory, true);
            Instant created = Instant.now();
            Referee.Result result = runner.play(seed, bot0.spec, bot1.spec);
            verifyRuntime();
            if (result.frames == null || result.frames.isEmpty() || result.sites == null)
                throw new IOException("The match did not produce a recorded replay");
            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("format", "local");
            replay.put("localGameVersion", 2);
            replay.put("createdAt", created.toString());
            replay.put("botIds", List.of(bot0.id, bot1.id));
            replay.put("botConfigIds", List.of(bot0.configId, bot1.configId));
            replay.put("botFlags", List.of(bot0.flags, bot1.flags));
            replay.put("bots", List.of(bot0.label, bot1.label));
            replay.put("botSpecs", List.of(bot0.spec, bot1.spec));
            replay.put("effectiveParams", List.of(BotFactory.describe(bot0.spec), BotFactory.describe(bot1.spec)));
            replay.put("provenance", provenance);
            replay.put("seed", seed);
            replay.put("winner", result.winner);
            replay.put("turns", result.turns);
            replay.put("score", result.score);
            replay.put("errors", result.error.clone());
            replay.put("timing", Map.of("turnMs", 50, "firstTurnMs", 1000, "enforcement", "in-process bots are timed, not interrupted",
                "maxNanos", result.maxNanos, "maxFirstNanos", result.maxFirstNanos, "slow", result.slow, "timeouts", result.timeouts));
            replay.put("sites", result.sites);
            replay.put("frames", result.frames);
            // Recheck every directory component after the match, before touching the output file.
            directory = directory(pairDirectory, false);
            Path destination = saveReplay(directory, created, seed, JSON.toJson(replay));
            return row(destination, JSON.toJsonTree(replay).getAsJsonObject(), true);
        } finally {
            running.set(false);
        }
    }

    static Path saveReplay(Path directory, Instant created, long seed, String json) throws IOException {
        String base = FILE_TIME.format(created) + "-seed-" + seed;
        Path pending = Files.createTempFile(directory, ".local-", ".part");
        try {
            Files.writeString(pending, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            for (int copy = 1; ; copy++) {
                Path destination = directory.resolve(base + (copy == 1 ? "" : "-" + copy) + ".json");
                try {
                    Files.move(pending, destination); // Never replace an earlier replay.
                    return destination;
                } catch (FileAlreadyExistsException collision) {
                    // Same timestamp and seed: keep both files with a short counter only when needed.
                }
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    Map<String, Object> games() throws IOException {
        List<Map<String, Object>> games = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Path local = root.resolve("replays/local");
        if (!Files.exists(local, LinkOption.NOFOLLOW_LINKS)) return Map.of("games", games, "warnings", warnings);
        directory("replays/local", false);
        Files.walkFileTree(local, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.toRealPath().equals(dir)) {
                    warnings.add("Skipped linked directory: " + relative(dir));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().endsWith(".json")) return FileVisitResult.CONTINUE;
                try {
                    if (!attrs.isRegularFile() || !file.toRealPath().equals(file)) throw new IOException("Linked file is not allowed");
                    games.add(readRow(file));
                } catch (IOException | RuntimeException e) {
                    warnings.add("Cannot read " + relative(file) + ": " + e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                warnings.add("Cannot read " + relative(file) + ": " + error.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        games.sort(Comparator.comparing((Map<String, Object> row) -> Instant.parse((String) row.get("createdAt"))).reversed()
            .thenComparing(row -> (String) row.get("file")));
        return Map.of("games", games, "warnings", warnings);
    }

    /** Read only row metadata; frame trees and provenance can grow without inflating the history response. */
    private Map<String, Object> readRow(Path file) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file))) {
            reader.setStrictness(Strictness.STRICT);
            JsonObject metadata = new JsonObject();
            Set<String> fields = new HashSet<>();
            boolean frames = false;
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!fields.add(key)) throw new IllegalArgumentException("Duplicate field: " + key);
                if (ROW_FIELDS.contains(key)) metadata.add(key, JsonParser.parseReader(reader));
                else if (key.equals("frames")) {
                    reader.beginArray();
                    frames = reader.hasNext();
                    while (reader.hasNext()) reader.skipValue();
                    reader.endArray();
                } else reader.skipValue();
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Expected a single replay object");
            return row(file, metadata, frames);
        }
    }

    private Map<String, Object> row(Path file, JsonObject replay, boolean hasFrames) {
        if (!integer(replay.get("localGameVersion"), 1, 2) || !hasFrames)
            throw new IllegalArgumentException("Not a saved local launcher replay");
        if (!text(replay.get("createdAt")) || !pair(replay.get("botIds"), false) || !pair(replay.get("bots"), false) ||
            !pair(replay.get("errors"), true) || !integer(replay.get("seed"), 0, 199999) ||
            !integer(replay.get("winner"), -1, 1) || !integer(replay.get("turns"), 0, cr.core.Const.MAX_TURNS))
            throw new IllegalArgumentException("Invalid local game metadata");
        JsonElement scores = replay.get("score");
        if (scores == null || !scores.isJsonArray() || scores.getAsJsonArray().size() != 2 ||
            !integer(scores.getAsJsonArray().get(0), -1, Integer.MAX_VALUE) || !integer(scores.getAsJsonArray().get(1), -1, Integer.MAX_VALUE))
            throw new IllegalArgumentException("Invalid local game scores");
        boolean configured = replay.get("localGameVersion").getAsInt() >= 2;
        for (String key : List.of("botConfigIds", "botSpecs", "effectiveParams")) {
            if ((configured || replay.has(key)) && !pair(replay.get(key), false))
                throw new IllegalArgumentException("Invalid local game " + key);
        }
        if ((configured || replay.has("botFlags")) && !flagPair(replay.get("botFlags")))
            throw new IllegalArgumentException("Invalid local game botFlags");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("file", relative(file));
        for (String key : List.of("localGameVersion", "createdAt", "botIds", "bots", "seed", "winner", "turns", "score", "errors")) {
            if (!replay.has(key)) throw new IllegalArgumentException("Missing " + key);
            row.put(key, replay.get(key));
        }
        // Saved identities are historical evidence, even after choices/defaults change. Never
        // reconstruct old flags or labels from the current catalog or a filename.
        for (String key : List.of("botConfigIds", "botFlags", "botSpecs", "effectiveParams"))
            if (replay.has(key)) row.put(key, replay.get(key));
        // A stable timestamp is required for reload ordering, even if a file was manually edited.
        Instant.parse(replay.get("createdAt").getAsString());
        row.put("createdAt", replay.get("createdAt").getAsString());
        return row;
    }

    private static boolean text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean pair(JsonElement value, boolean nullable) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 2) return false;
        for (JsonElement element : value.getAsJsonArray()) if (!(nullable && element.isJsonNull()) && !text(element)) return false;
        return true;
    }

    private static boolean flagPair(JsonElement value) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 2) return false;
        for (JsonElement flags : value.getAsJsonArray()) {
            if (!flags.isJsonObject()) return false;
            for (JsonElement flag : flags.getAsJsonObject().asMap().values())
                if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) return false;
        }
        return true;
    }

    private static boolean integer(JsonElement value, long min, long max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try {
            long number = value.getAsBigDecimal().longValueExact();
            return number >= min && number <= max;
        } catch (ArithmeticException | NumberFormatException e) { return false; }
    }

    private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }

    /** No links/junctions, including links whose target happens to be elsewhere inside the checkout. */
    private Path directory(String relative, boolean create) throws IOException {
        Path current = root;
        for (Path part : Path.of(relative)) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS) && create) Files.createDirectory(current);
            if (!Files.isDirectory(current) || !current.toRealPath().equals(current))
                throw new IOException("Local replay storage must be a real directory inside this checkout: " + relative(current));
        }
        return current;
    }

    private static Selection selection(JsonElement value, JsonElement suppliedFlags) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("bot0 and bot1 must be bot ids");
        String id = value.getAsString();
        Choice choice = CHOICES.stream().filter(c -> c.id.equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown bot id: " + value.getAsString()));
        if (!id.equals("current")) {
            if (suppliedFlags != null) throw new IllegalArgumentException("Flags can only be supplied for current");
            return new Selection(choice.id, choice.id, choice.label, choice.spec, Map.of());
        }
        if (suppliedFlags != null && !suppliedFlags.isJsonObject())
            throw new IllegalArgumentException("Current flags must be an object of boolean values");
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Flag flag : FLAGS) flags.put(flag.id, flag.defaultValue);
        if (suppliedFlags != null) for (var entry : suppliedFlags.getAsJsonObject().entrySet()) {
            if (!flags.containsKey(entry.getKey())) throw new IllegalArgumentException("Unknown current flag: " + entry.getKey());
            JsonElement flag = entry.getValue();
            if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Current flag " + entry.getKey() + " must be boolean");
            flags.put(entry.getKey(), flag.getAsBoolean());
        }
        String overrides = flags.entrySet().stream().map(e -> e.getKey() + "=" + (e.getValue() ? 1 : 0))
            .collect(java.util.stream.Collectors.joining(","));
        String configId = "current" + flags.entrySet().stream().map(e -> "-" + e.getKey().toLowerCase(java.util.Locale.ROOT)
            + (e.getValue() ? 1 : 0)).collect(java.util.stream.Collectors.joining());
        String enabled = flags.entrySet().stream().filter(Map.Entry::getValue).map(e -> e.getKey() + "=1")
            .collect(java.util.stream.Collectors.joining(", "));
        return new Selection("current", configId, enabled.isEmpty() ? "current" : "current [" + enabled + "]",
            "current?" + overrides, flags);
    }

    private static long seed(JsonElement value) {
        if (value == null || value.isJsonNull()) return ThreadLocalRandom.current().nextLong(200000);
        if (!value.isJsonPrimitive() || (!value.getAsJsonPrimitive().isString() && !value.getAsJsonPrimitive().isNumber()) ||
            !value.getAsString().matches("-?\\d+")) throw new IllegalArgumentException("Seed must be an integer from 0 through 199999, or null");
        try {
            long seed = Long.parseLong(value.getAsString());
            if (seed >= 0 && seed < 200000) return seed;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Seed must be an integer from 0 through 199999, or null");
    }

    private void verifyRuntime() throws IOException {
        if (!runtimeFiles.equals(runtimeSnapshot()))
            throw new Conflict("Compiled code or parameter resources changed. Restart gradlew serve before launching another game.");
    }

    private static Map<String, String> runtimeSnapshot() throws IOException {
        Map<String, String> result = new TreeMap<>();
        try {
            Path code = Path.of(LocalGames.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(code)) {
                try (var files = Files.walk(code.resolve("cr"))) {
                    for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).sorted().toList())
                        result.put("classes/" + code.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
                }
            } else result.put("codeJar", sha256(Files.readAllBytes(code)));
            for (String version : List.of("v01", "v02", "v03", "v04", "v05", "v06", "v07", "v08")) {
                String resource = "/cr/bot/versions/" + version + ".properties";
                try (InputStream in = BotFactory.class.getResourceAsStream(resource)) {
                    if (in == null) throw new IOException("Missing parameter resource " + resource);
                    result.put("resource" + resource, sha256(in.readAllBytes()));
                }
            }
            Path gson = Path.of(Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(gson)) result.put("library/gson", sha256(Files.readAllBytes(gson)));
        } catch (URISyntaxException e) { throw new IOException("Cannot identify the server classpath", e); }
        return result;
    }

    private Map<String, Object> revision() {
        Map<String, Object> revision = new LinkedHashMap<>();
        String commit = git("rev-parse", "HEAD"), status = git("status", "--porcelain"), diff = git("diff", "HEAD", "--binary");
        revision.put("commit", commit == null ? null : commit.strip());
        revision.put("status", status == null ? null : status.lines().toList());
        revision.put("diffSha256", diff == null ? null : sha256(diff.getBytes(StandardCharsets.UTF_8)));
        return revision;
    }

    private String git(String... args) {
        try {
            List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (IOException e) { return null; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
