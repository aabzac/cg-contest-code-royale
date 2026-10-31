package cr.tools;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Local dashboard server. Static files and recorded replay decoding stay under the repository root.
 * GET /__id returns an identity string so a caller can tell which server answered.
 * Usage: Serve [port]
 */
public final class Serve {
    public static final String ID = "code-royale-visualizer";
    private static final Gson JSON = new Gson();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        Path root = Paths.get("").toRealPath();
        try {
            start(root, port);
        } catch (BindException e) {
            System.err.println("port " + port + " is busy; is another visualizer server running? (GET /__id identifies it)");
            System.exit(1);
            return;
        }
        System.out.println(ID + " serving " + root + " at http://localhost:" + port + "/visualizer/");
    }

    static HttpServer start(Path root, int port) throws IOException {
        Path realRoot = root.toRealPath();
        return start(realRoot, port, new LocalGames(realRoot));
    }

    static HttpServer start(Path root, int port, LocalGames localGames) throws IOException {
        Path realRoot = root.toRealPath();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        // A running match must not block GETs or prevent a second launch from receiving 409.
        server.setExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "visualizer-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/", ex -> handle(realRoot, localGames, ex));
        server.start();
        return server;
    }

    private static void handle(Path root, LocalGames localGames, HttpExchange ex) throws IOException {
        String requestPath = ex.getRequestURI().getPath();
        boolean api = requestPath.startsWith("/api/"), run = requestPath.equals("/api/local/run");
        if (run ? !ex.getRequestMethod().equals("POST") : !ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
            ex.getResponseHeaders().set("Allow", run ? "POST" : "GET, HEAD");
            error(ex, 405, run ? "POST required" : "GET or HEAD required");
            return;
        }
        try {
            if (requestPath.equals("/__id")) {
                send(ex, 200, "text/plain; charset=utf-8", ID);
            } else if (run) {
                validateLaunchOrigin(ex);
                send(ex, 201, "application/json; charset=utf-8", JSON.toJson(localGames.run(readLaunch(ex))));
            } else if (requestPath.equals("/api/local/bots")) {
                send(ex, 200, "application/json; charset=utf-8", JSON.toJson(localGames.bots()));
            } else if (requestPath.equals("/api/local/games")) {
                send(ex, 200, "application/json; charset=utf-8", JSON.toJson(localGames.games()));
            } else if (requestPath.equals("/api/replay")) {
                String file = replayFile(ex.getRequestURI().getRawQuery());
                Path path = resolveFile(root, file);
                if (!path.getFileName().toString().endsWith(".json")) throw new IllegalArgumentException("Replay must be a JSON file");
                send(ex, 200, "application/json; charset=utf-8", CgViewReplay.load(path).toString());
            } else if (api) {
                error(ex, 404, "Unknown API route");
            } else {
                if (requestPath.equals("/") || requestPath.equals("/visualizer") || requestPath.equals("/visualizer/"))
                    requestPath = "/visualizer/index.html";
                Path path = resolveFile(root, requestPath);
                send(ex, 200, contentType(path), Files.readAllBytes(path));
            }
        } catch (NoSuchFileException e) {
            error(ex, 404, "File not found in this checkout");
        } catch (RequestError e) {
            error(ex, e.status, e.getMessage());
        } catch (LocalGames.Conflict e) {
            error(ex, 409, e.getMessage());
        } catch (IllegalArgumentException | SecurityException e) {
            error(ex, 400, e.getMessage());
        } catch (IOException e) {
            error(ex, api && !run ? 422 : 500, e.getMessage());
        } catch (RuntimeException e) {
            error(ex, 500, "Cannot complete request: " + e.getMessage());
        }
    }

    private static final class RequestError extends RuntimeException {
        final int status;
        RequestError(int status, String message) { super(message); this.status = status; }
    }

    private static void validateLaunchOrigin(HttpExchange ex) {
        int port = ex.getLocalAddress().getPort();
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null || ex.getRequestHeaders().get("Host").size() != 1)
            throw new RequestError(403, "A loopback Host header is required");
        String authority = host.toLowerCase(Locale.ROOT);
        if (!authority.equals("localhost:" + port) && !authority.equals("127.0.0.1:" + port) &&
            !(port == 80 && (authority.equals("localhost") || authority.equals("127.0.0.1"))))
            throw new RequestError(403, "Local games can only be launched on this server's loopback address");
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            if (ex.getRequestHeaders().get("Origin").size() != 1) throw new RequestError(403, "One same-origin Origin is required");
            try {
                URI uri = URI.create(origin);
                if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getRawAuthority() == null ||
                    !uri.getRawAuthority().equalsIgnoreCase(host) || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new RequestError(403, "Cross-origin game launch is not allowed"); }
        }
        if (ex.getRequestURI().getRawQuery() != null) throw new RequestError(400, "Launch options must be supplied in the JSON body");
    }

    private static JsonObject readLaunch(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/json"))
            throw new RequestError(415, "Content-Type must be application/json");
        byte[] bytes = ex.getRequestBody().readNBytes(4097);
        if (bytes.length > 4096) throw new RequestError(413, "Launch request is too large");
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setStrictness(Strictness.STRICT);
            JsonObject request = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (request.has(key)) throw new IllegalArgumentException("Duplicate request field: " + key);
                request.add(key, JsonParser.parseReader(reader));
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Expected a single JSON object");
            return request;
        } catch (IOException | RuntimeException e) {
            throw new RequestError(400, "Invalid JSON request: " + e.getMessage());
        }
    }

    private static void error(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, "application/json; charset=utf-8", JSON.toJson(Map.of("error", message == null ? "Request failed" : message)));
    }

    static Path resolveFile(Path root, String input) throws IOException {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("File path is required");
        Path relative = Paths.get(input.replaceFirst("^/+", ""));
        Path normalized = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !normalized.startsWith(root)) throw new IllegalArgumentException("File must be inside this checkout");
        Path real = normalized.toRealPath();
        if (!real.startsWith(root)) throw new IllegalArgumentException("File must be inside this checkout");
        if (!Files.isRegularFile(real)) throw new NoSuchFileException(input);
        return real;
    }

    private static String replayFile(String query) {
        String file = null;
        if (query != null) for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals("file")) {
                if (file != null) throw new IllegalArgumentException("Only one file is allowed");
                file = URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8);
            }
        }
        if (file == null || file.isBlank()) throw new IllegalArgumentException("Missing file query parameter");
        return file;
    }

    private static String contentType(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, String contentType, String text) throws IOException {
        send(ex, status, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (ex.getRequestMethod().equals("HEAD")) {
            ex.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
            ex.sendResponseHeaders(status, -1);
            ex.close();
        } else {
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }
}
