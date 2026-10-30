package cr.tools;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServeTest {
    @TempDir Path temp;

    @Test void apiAndStaticRoutesUseCorrectTypesAndRejectOutsidePaths() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        Path viewer = Files.createDirectory(root.resolve("visualizer"));
        Files.writeString(viewer.resolve("index.html"), "dashboard");
        Files.writeString(viewer.resolve("styles.css"), "body {} ");
        Files.writeString(root.resolve("recorded game.json"), "{sites:[],bots:['a','b'],frames:[{turn:0}],winner:0}");
        Files.writeString(temp.resolve("outside.json"), "secret");
        HttpServer server = Serve.start(root, 0);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            for (String url : new String[]{"/", "/visualizer/", "/visualizer/index.html"}) {
                HttpResponse<String> response = get(client, base + url);
                assertEquals(200, response.statusCode());
                assertEquals("dashboard", response.body());
            }
            assertEquals(Serve.ID, get(client, base + "/__id").body());
            assertTrue(get(client, base + "/visualizer/styles.css").headers().firstValue("Content-Type").orElseThrow().startsWith("text/css"));
            HttpResponse<String> replay = get(client, base + "/api/replay?file=%2Frecorded%20game.json");
            assertEquals(200, replay.statusCode());
            assertEquals("local", JsonParser.parseString(replay.body()).getAsJsonObject().get("format").getAsString());
            assertEquals(400, get(client, base + "/api/replay").statusCode());
            assertEquals(400, get(client, base + "/api/replay?file=../outside.json").statusCode());
            assertEquals(400, get(client, base + "/%2e%2e/outside.json").statusCode());
            assertEquals(404, get(client, base + "/api/replay?file=missing.json").statusCode());
            assertThrows(IllegalArgumentException.class, () -> Serve.resolveFile(root.toRealPath(), "../outside.json"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpResponse<String> get(HttpClient client, String uri) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
