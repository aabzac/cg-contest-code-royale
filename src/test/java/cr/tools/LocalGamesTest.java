package cr.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
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
import java.time.Instant;
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
            assertEquals(6, choices.getAsJsonArray("bots").size());
            assertEquals("current", choices.getAsJsonObject("defaults").get("bot0").getAsString());
            assertEquals("v03", choices.getAsJsonObject("defaults").get("bot1").getAsString());
            HttpResponse<String> created = post(base, "{\"bot0\":\"current-approach\",\"bot1\":\"v03\",\"seed\":\"17\"}", base);
            assertEquals(201, created.statusCode(), created.body());
            JsonObject row = object(created);
            file = row.get("file").getAsString();
            assertTrue(file.startsWith("replays/local/current-approach-vs-v03/"), file);
            assertEquals(17, row.get("seed").getAsInt());
            assertTrue(row.get("turns").getAsInt() > 0);
            assertEquals(2, row.getAsJsonArray("errors").size());
            assertTrue(row.getAsJsonArray("errors").get(0).isJsonNull());
            assertTrue(row.getAsJsonArray("errors").get(1).isJsonNull());

            JsonObject saved = JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject();
            assertEquals("local", saved.get("format").getAsString());
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
            HttpResponse<String> second = post(base(server), "{\"bot0\":\"current-approach\",\"bot1\":\"v03\",\"seed\":17}", null);
            assertEquals(201, second.statusCode(), second.body());
            assertNotEquals(file, object(second).get("file").getAsString());
            assertArrayEquals(before, Files.readAllBytes(root.resolve(file)));
            Files.writeString(root.resolve("replays/local/broken.json"), "not json");
            JsonObject partial = object(get(base(server) + "/api/local/games"));
            assertEquals(2, partial.getAsJsonArray("games").size());
            assertEquals(1, partial.getAsJsonArray("warnings").size());
        } finally { server.stop(0); }
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
                "{\"bot0\":\"current?APPROACH=1\",\"bot1\":\"v00\"}",
                "{\"bot0\":42,\"bot1\":\"v00\"}",
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
            HttpResponse<String> wrongType = client.send(HttpRequest.newBuilder(URI.create(base + "/api/local/run"))
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString(good)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, wrongType.statusCode());
            assertEquals(413, post(base, " ".repeat(4097), null).statusCode());
            HttpResponse<String> query = client.send(HttpRequest.newBuilder(URI.create(base + "/api/local/run?seed=1"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(good)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, query.statusCode());
            assertEquals(403, rawHostStatus(server, "evil.example:" + server.getAddress().getPort(), good));
            assertEquals(403, rawHostStatus(server, "127.0.0.1:1", good));
            assertEquals(0, launches.get());
            assertFalse(Files.exists(root.resolve("replays")));
        } finally { server.stop(0); }
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
            CompletableFuture<HttpResponse<String>> first = client.sendAsync(HttpRequest.newBuilder(URI.create(base + "/api/local/run"))
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
    private static JsonObject object(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String base, String body, String origin) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/api/local/run")).header("Content-Type", "application/json");
        if (origin != null) request.header("Origin", origin);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
