package cr.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import cr.bot.Params;
import cr.referee.Referee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalGamesTest {
    @TempDir Path temp;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test void shortFilenamesPreserveBothReplaysWhenTimestampAndSeedCoincide() throws Exception {
        Instant created = Instant.parse("2026-10-31T10:33:02.644Z");
        Path first = LocalGames.saveReplay(temp, created, 42, "first replay");
        Path second = LocalGames.saveReplay(temp, created, 42, "second replay");
        assertEquals("20261031T103302644Z-seed-42.json", first.getFileName().toString());
        assertEquals("20261031T103302644Z-seed-42-2.json", second.getFileName().toString());
        assertEquals("first replay", Files.readString(first));
        assertEquals("second replay", Files.readString(second));
        try (var files = Files.list(temp)) { assertEquals(2, files.count()); }
    }

    @Test void launchesRealGameAndReloadsSavedReplayWithParametersAndRuntimeIdentity() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        HttpServer server = Serve.start(root, 0);
        String file;
        try {
            String base = base(server);
            JsonObject choices = object(get(base + "/api/local/bots"));
            assertEquals(12, choices.getAsJsonArray("bots").size());
            assertEquals("current", choices.getAsJsonObject("defaults").get("bot0").getAsString());
            assertEquals("v09", choices.getAsJsonObject("defaults").get("bot1").getAsString());
            JsonArray flags = choices.getAsJsonArray("bots").get(0).getAsJsonObject().getAsJsonArray("flags");
            assertEquals(List.of("UNSTICK", "APPROACH", "ROUTE"), flags.asList().stream()
                .map(f -> f.getAsJsonObject().get("id").getAsString()).toList());
            Params defaults = new Params();
            assertEquals(defaults.UNSTICK == 1, flags.get(0).getAsJsonObject().get("default").getAsBoolean());
            assertEquals(defaults.APPROACH == 1, flags.get(1).getAsJsonObject().get("default").getAsBoolean());
            assertEquals(defaults.ROUTE == 1, flags.get(2).getAsJsonObject().get("default").getAsBoolean());
            assertTrue(choices.getAsJsonArray("bots").get(1).getAsJsonObject().getAsJsonArray("flags").isEmpty());
            String request = "{\"bot0\":\"current\",\"flags0\":{\"UNSTICK\":false,\"APPROACH\":true},\"bot1\":\"v03\",\"seed\":\"17\"}";
            HttpResponse<String> created = post(base, request, base);
            assertEquals(201, created.statusCode(), created.body());
            JsonObject row = object(created);
            file = row.get("file").getAsString();
            assertTrue(file.startsWith("replays/local/current-unstick0-approach1-route1-vs-v03/"), file);
            assertEquals(17, row.get("seed").getAsInt());
            assertTrue(row.get("turns").getAsInt() > 0);
            assertEquals(2, row.getAsJsonArray("errors").size());
            assertTrue(row.getAsJsonArray("errors").get(0).isJsonNull());
            assertTrue(row.getAsJsonArray("errors").get(1).isJsonNull());

            JsonObject saved = JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject();
            assertEquals("local", saved.get("format").getAsString());
            assertEquals(2, saved.get("localGameVersion").getAsInt());
            assertEquals("current?UNSTICK=0,APPROACH=1,ROUTE=1", saved.getAsJsonArray("botSpecs").get(0).getAsString());
            assertEquals("current", saved.getAsJsonArray("botIds").get(0).getAsString());
            assertFalse(saved.getAsJsonArray("botFlags").get(0).getAsJsonObject().get("UNSTICK").getAsBoolean());
            assertTrue(saved.getAsJsonArray("botFlags").get(0).getAsJsonObject().get("APPROACH").getAsBoolean());
            assertTrue(saved.getAsJsonArray("botFlags").get(0).getAsJsonObject().get("ROUTE").getAsBoolean());
            assertEquals("current-unstick0-approach1-route1", row.getAsJsonArray("botConfigIds").get(0).getAsString());
            assertTrue(saved.getAsJsonArray("effectiveParams").get(0).getAsString().contains("APPROACH=1"));
            assertTrue(saved.getAsJsonArray("effectiveParams").get(1).getAsString().contains("APPROACH=0"));
            assertEquals("server-classpath-snapshot", saved.getAsJsonObject("provenance").get("kind").getAsString());
            assertEquals(64, saved.getAsJsonObject("provenance").get("sha256").getAsString().length());
            assertTrue(saved.getAsJsonObject("provenance").getAsJsonObject("files").has("classes/cr/referee/Referee.class"));
            JsonArray frames = saved.getAsJsonArray("frames");
            assertEquals(24, saved.getAsJsonArray("sites").size());
            assertEquals(row.get("turns").getAsInt() + 1, frames.size());
            assertEquals(0, frames.get(0).getAsJsonObject().get("turn").getAsInt());
            assertFalse(frames.get(1).getAsJsonObject().getAsJsonArray("cmds").get(0).getAsString().isBlank());
            assertEquals(row.get("turns"), frames.get(frames.size() - 1).getAsJsonObject().get("turn"));
            JsonObject decoded = object(get(base + "/api/replay?file=" + file));
            assertTrue(saved.equals(decoded), "Replay API must preserve every recorded field, including null provenance fields");
            JsonArray list = object(get(base + "/api/local/games")).getAsJsonArray("games");
            assertEquals(1, list.size());
            assertEquals(row, list.get(0));
        } finally { server.stop(0); }

        // A new server/catalog finds the durable file without any in-memory run history.
        server = Serve.start(root, 0);
        try {
            JsonObject listed = object(get(base(server) + "/api/local/games"));
            assertEquals(file, listed.getAsJsonArray("games").get(0).getAsJsonObject().get("file").getAsString());
            byte[] before = Files.readAllBytes(root.resolve(file));
            HttpResponse<String> second = post(base(server), "{\"bot0\":\"current\",\"flags0\":{\"APPROACH\":true},\"bot1\":\"v03\",\"seed\":17}", null);
            assertEquals(201, second.statusCode(), second.body());
            assertNotEquals(file, object(second).get("file").getAsString());
            assertArrayEquals(before, Files.readAllBytes(root.resolve(file)));
            Files.writeString(root.resolve("replays/local/broken.json"), "not json");
            JsonObject partial = object(get(base(server) + "/api/local/games"));
            assertEquals(2, partial.getAsJsonArray("games").size());
            assertEquals(1, partial.getAsJsonArray("warnings").size());
        } finally { server.stop(0); }
    }

    @Test void canonicalConfigurationsIgnoreRequestOrderAndDefaultsButKeepSidesAndAllFlagCombinations() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        List<List<String>> played = new ArrayList<>();
        LocalGames local = new LocalGames(root, (seed, a, b) -> { played.add(List.of(a, b)); return recorded(seed); });
        JsonObject defaults = run(local, "{\"bot0\":\"current\",\"bot1\":\"v03\",\"seed\":1}");
        JsonObject explicit = run(local, "{\"bot0\":\"current\",\"flags0\":{\"APPROACH\":false,\"UNSTICK\":true},\"bot1\":\"v03\",\"seed\":1}");
        assertEquals(played.get(0), played.get(1));
        assertEquals(defaults.get("botConfigIds"), explicit.get("botConfigIds"));
        assertEquals(defaults.get("bots"), explicit.get("bots"));
        assertEquals(defaults.get("botFlags"), explicit.get("botFlags"));
        assertEquals("current?UNSTICK=1,APPROACH=0,ROUTE=1", played.get(0).get(0));
        assertNotEquals(defaults.get("file"), explicit.get("file"));

        JsonObject approach = run(local, "{\"bot0\":\"current\",\"flags0\":{\"APPROACH\":true},\"bot1\":\"v03\",\"seed\":1}");
        assertEquals("current-unstick1-approach1-route1", approach.getAsJsonArray("botConfigIds").get(0).getAsString());
        assertEquals("current?UNSTICK=1,APPROACH=1,ROUTE=1", played.get(2).get(0));
        JsonObject route = run(local, "{\"bot0\":\"current\",\"flags0\":{\"ROUTE\":true},\"bot1\":\"v03\",\"seed\":1}");
        assertEquals("current-unstick1-approach0-route1", route.getAsJsonArray("botConfigIds").get(0).getAsString());
        assertEquals("current [UNSTICK=1, ROUTE=1]", route.getAsJsonArray("bots").get(0).getAsString());
        assertEquals("current?UNSTICK=1,APPROACH=0,ROUTE=1", played.get(3).get(0));

        java.util.Set<String> configurations = new java.util.HashSet<>();
        for (int bits = 0; bits < 8; bits++) {
            String flags = "{\"UNSTICK\":" + ((bits & 1) != 0) + ",\"APPROACH\":" + ((bits & 2) != 0) + ",\"ROUTE\":" + ((bits & 4) != 0) + "}";
            JsonObject row = run(local, "{\"bot0\":\"current\",\"flags0\":" + flags + ",\"bot1\":\"v03\",\"seed\":1}");
            String id = row.getAsJsonArray("botConfigIds").get(0).getAsString();
            assertTrue(configurations.add(id), "Every on/off combination has its own stable storage identity");
            assertTrue(row.get("file").getAsString().startsWith("replays/local/" + id + "-vs-v03/"));
        }
        JsonObject both = run(local, "{\"bot0\":\"current\",\"flags0\":{\"APPROACH\":false},\"bot1\":\"current\",\"flags1\":{\"UNSTICK\":false,\"APPROACH\":true},\"seed\":1}");
        JsonObject swapped = run(local, "{\"bot0\":\"current\",\"flags0\":{\"UNSTICK\":false,\"APPROACH\":true},\"bot1\":\"current\",\"flags1\":{\"APPROACH\":false},\"seed\":1}");
        assertEquals(both.getAsJsonArray("botConfigIds").get(0), swapped.getAsJsonArray("botConfigIds").get(1));
        assertEquals(both.getAsJsonArray("botConfigIds").get(1), swapped.getAsJsonArray("botConfigIds").get(0));
        assertNotEquals(Path.of(both.get("file").getAsString()).getParent(), Path.of(swapped.get("file").getAsString()).getParent());
        assertEquals(List.of("current?UNSTICK=1,APPROACH=0,ROUTE=1", "current?UNSTICK=0,APPROACH=1,ROUTE=1"), played.get(played.size() - 2));
    }

    @Test void historyKeepsSavedConfigurationEvidenceAndLegacyLabelsWithoutCurrentCatalogDefaults() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        LocalGames local = new LocalGames(root, (seed, a, b) -> recorded(seed));
        JsonObject row = run(local, "{\"bot0\":\"current\",\"flags0\":{\"APPROACH\":true},\"bot1\":\"v03\",\"seed\":1}");
        Path replayFile = root.resolve(row.get("file").getAsString());
        JsonObject saved = JsonParser.parseString(Files.readString(replayFile)).getAsJsonObject();
        saved.getAsJsonArray("bots").set(0, new com.google.gson.JsonPrimitive("Saved historical label"));
        saved.getAsJsonArray("botSpecs").set(0, new com.google.gson.JsonPrimitive("current?UNSTICK=1,APPROACH=1,ROUTE=0,FUTURE=1"));
        saved.getAsJsonArray("effectiveParams").set(0, new com.google.gson.JsonPrimitive("historical effective values"));
        saved.getAsJsonArray("botFlags").get(0).getAsJsonObject().addProperty("FUTURE", true); // unknown to this catalog
        saved.getAsJsonArray("botConfigIds").set(0, new com.google.gson.JsonPrimitive("current-unstick1-approach1-route0-future1"));
        Files.writeString(replayFile, saved.toString());

        JsonObject legacy = saved.deepCopy();
        legacy.addProperty("localGameVersion", 1);
        legacy.getAsJsonArray("botIds").set(0, new com.google.gson.JsonPrimitive("current-approach"));
        legacy.getAsJsonArray("bots").set(0, new com.google.gson.JsonPrimitive("current + APPROACH"));
        legacy.getAsJsonArray("botSpecs").set(0, new com.google.gson.JsonPrimitive("current?APPROACH=1"));
        legacy.remove("botFlags"); legacy.remove("botConfigIds");
        Path old = Files.createDirectories(root.resolve("replays/local/current-approach-vs-v03")).resolve("old.json");
        Files.writeString(old, legacy.toString());
        byte[] oldBytes = Files.readAllBytes(old);
        JsonObject listed = JsonParser.parseString(new com.google.gson.Gson().toJson(new LocalGames(root).games())).getAsJsonObject();
        assertTrue(listed.getAsJsonArray("warnings").isEmpty());
        assertEquals(2, listed.getAsJsonArray("games").size());
        for (var game : listed.getAsJsonArray("games")) {
            JsonObject item = game.getAsJsonObject();
            if (item.get("file").getAsString().endsWith("old.json")) {
                assertEquals("current-approach", item.getAsJsonArray("botIds").get(0).getAsString());
                assertEquals("current + APPROACH", item.getAsJsonArray("bots").get(0).getAsString());
                assertFalse(item.has("botFlags")); assertFalse(item.has("botConfigIds"));
                assertEquals(legacy.get("botSpecs"), item.get("botSpecs"));
            } else {
                for (String field : List.of("bots", "botSpecs", "effectiveParams", "botFlags", "botConfigIds"))
                    assertEquals(saved.get(field), item.get(field), field);
            }
        }
        assertArrayEquals(oldBytes, Files.readAllBytes(old));
    }

    @Test void rejectsUnsafeRequestsBeforeStartingAnyMatch() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        AtomicInteger launches = new AtomicInteger();
        LocalGames local = new LocalGames(root, (seed, a, b) -> { launches.incrementAndGet(); throw new AssertionError("Unexpected match"); });
        HttpServer server = Serve.start(root, 0, local);
        try {
            String base = base(server), good = "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":1}";
            for (String bad : new String[]{
                "{\"bot0\":\"proc:calc\",\"bot1\":\"v00\"}",
                "{\"bot0\":\"current-approach\",\"bot1\":\"v00\"}",
                "{\"bot0\":\"current\",\"bot1\":\"current-approach\"}",
                "{\"bot0\":\"current?APPROACH=1\",\"bot1\":\"v00\"}",
                "{\"bot0\":42,\"bot1\":\"v00\"}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":{\"DEBUG\":true}}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":{\"APPROACH\":1}}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":{\"APPROACH\":\"true\"}}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":{\"APPROACH\":null}}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":null}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":[]}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags0\":false}",
                "{\"bot0\":\"v03\",\"bot1\":\"current\",\"flags0\":{\"ROUTE\":false}}",
                "{\"bot0\":\"current\",\"bot1\":\"v00\",\"flags1\":{}}",
                "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":200000}",
                "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":-1}",
                "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":1.5}",
                "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":true}",
                "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":\"999999999999999999999999\"}",
                "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"out\":\"../other.json\"}",
                "{\"bot0\":\"v00\",\"bot0\":\"v03\",\"bot1\":\"v00\"}",
                "{bot0:'v00',bot1:'v00'}", "[]", good + " {}"}) {
                HttpResponse<String> response = post(base, bad, null);
                assertEquals(400, response.statusCode(), bad + " -> " + response.body());
            }
            assertEquals(405, get(base + "/api/local/run").statusCode());
            assertEquals(403, post(base, good, "https://example.com").statusCode());
            assertEquals(403, post(base, good, "http://localhost:" + server.getAddress().getPort()).statusCode());
            assertEquals(403, post(base, good, "null").statusCode());
            HttpResponse<String> wrongType = client.send(request(base + "/api/local/run")
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString(good)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, wrongType.statusCode());
            assertEquals(413, post(base, " ".repeat(4097), null).statusCode());
            HttpResponse<String> query = client.send(request(base + "/api/local/run?seed=1")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(good)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, query.statusCode());
            assertEquals(403, rawHostStatus(server, "evil.example:" + server.getAddress().getPort(), good));
            assertEquals(403, rawHostStatus(server, "127.0.0.1:1", good));
            assertEquals(0, launches.get());
            assertFalse(Files.exists(root.resolve("replays")));
        } finally { server.stop(0); }
    }

    private static JsonObject run(LocalGames local, String request) throws IOException {
        return JsonParser.parseString(new com.google.gson.Gson().toJson(local.run(JsonParser.parseString(request).getAsJsonObject()))).getAsJsonObject();
    }

    private static Referee.Result recorded(long seed) {
        Referee.Result result = new Referee.Result();
        result.seed = seed; result.winner = -1;
        result.score[0] = result.score[1] = 100;
        result.sites = List.of(new int[]{0, 100, 100, 40});
        result.frames = List.of(new Referee.Frame());
        return result;
    }

    @Test void concurrentLaunchGetsConflictWhileReadsRemainAvailable() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        LocalGames local = new LocalGames(root, (seed, a, b) -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Release timed out"); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
            return Referee.play(seed, cr.bot.BotFactory.create(a), cr.bot.BotFactory.create(b), true, 50);
        });
        HttpServer server = Serve.start(root, 0, local);
        try {
            String base = base(server), request = "{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":null}";
            CompletableFuture<HttpResponse<String>> first = client.sendAsync(request(base + "/api/local/run")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request)).build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(409, post(base, request, null).statusCode());
            assertEquals(200, get(base + "/api/local/games").statusCode());
            assertEquals(200, get(base + "/api/local/bots").statusCode());
            release.countDown();
            HttpResponse<String> completed = first.get(15, TimeUnit.SECONDS);
            assertEquals(201, completed.statusCode(), completed.body());
            long seed = object(completed).get("seed").getAsLong();
            assertTrue(seed >= 0 && seed < 200000);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void rejectsLinkedStorageBeforeCreatingAnythingOutsideCheckout() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Path link = root.resolve("replays");
        try { Files.createSymbolicLink(link, outside); }
        catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Creating symlinks is unavailable: " + e);
            // Windows directory junctions do not require the symlink privilege. Both paths belong to this test's temp directory.
            String script = "New-Item -ItemType Junction -Path '" + link.toString().replace("'", "''")
                + "' -Target '" + outside.toString().replace("'", "''") + "' | Out-Null";
            Process junction = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start();
            String output = new String(junction.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assumeTrue(junction.waitFor() == 0, "Creating a directory junction is unavailable: " + output);
        }
        try {
            AtomicInteger launches = new AtomicInteger();
            LocalGames local = new LocalGames(root, (seed, a, b) -> { launches.incrementAndGet(); throw new AssertionError("Unexpected match"); });
            assertThrows(IOException.class, () -> local.run(JsonParser.parseString("{\"bot0\":\"v00\",\"bot1\":\"v00\",\"seed\":1}").getAsJsonObject()));
            assertEquals(0, launches.get());
            try (var children = Files.list(outside)) { assertEquals(0, children.count()); }
        } finally {
            // Delete the link itself; JUnit's recursive cleanup does not remove Windows junctions.
            Files.deleteIfExists(link);
            assertTrue(Files.isDirectory(outside));
        }
    }

    @Test void historySkipsMalformedMetadataAndOrdersInstantsWithDifferentPrecision() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        Path local = Files.createDirectories(root.resolve("replays/local/v00-vs-v00"));
        String source = "{\"localGameVersion\":1,\"createdAt\":\"2026-10-31T10:00:00Z\",\"botIds\":[\"v00\",\"v00\"],"
            + "\"bots\":[\"v00\",\"v00\"],\"seed\":1,\"winner\":-1,\"turns\":0,\"score\":[100,100],\"errors\":[null,null],\"frames\":[{}]}";
        Files.writeString(local.resolve("first.json"), source);
        Files.writeString(local.resolve("later.json"), source.replace("10:00:00Z", "10:00:00.100Z"));
        int invalid = 0;
        for (String mutation : new String[]{source.replace("\"botIds\":[\"v00\",\"v00\"]", "\"botIds\":\"v00\""),
            source.replace("\"errors\":[null,null]", "\"errors\":{}"), source.replace("\"score\":[100,100]", "\"score\":null"),
            source.replace("\"turns\":0", "\"turns\":0.5"), source.replace("\"winner\":-1", "\"winner\":7")})
            Files.writeString(local.resolve("broken-" + invalid++ + ".json"), mutation);
        Map<String, Object> listing = new LocalGames(root).games();
        JsonObject data = JsonParser.parseString(new com.google.gson.Gson().toJson(listing)).getAsJsonObject();
        assertEquals(2, data.getAsJsonArray("games").size());
        assertTrue(data.getAsJsonArray("games").get(0).getAsJsonObject().get("file").getAsString().endsWith("later.json"));
        assertEquals(invalid, data.getAsJsonArray("warnings").size());
    }

    private static int rawHostStatus(HttpServer server, String host, String body) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getAddress().getPort())) {
            socket.setSoTimeout(5000);
            String request = "POST /api/local/run HTTP/1.1\r\nHost: " + host + "\r\nContent-Type: application/json\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\nConnection: close\r\n\r\n" + body;
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return Integer.parseInt(response.split(" ", 3)[1]);
        }
    }

    private static String base(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    /** A server that never answers (an Error escaping a handler) fails the test in a minute instead of hanging the suite. */
    private static HttpRequest.Builder request(String url) { return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)); }
    private static JsonObject object(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return client.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String base, String body, String origin) throws IOException, InterruptedException {
        HttpRequest.Builder builder = request(base + "/api/local/run").header("Content-Type", "application/json");
        if (origin != null) builder.header("Origin", origin);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
