package cr.tools;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.Map;

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
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", ex -> handle(realRoot, ex));
        server.start();
        return server;
    }

    private static void handle(Path root, HttpExchange ex) throws IOException {
        String requestPath = ex.getRequestURI().getPath();
        boolean api = requestPath.equals("/api/replay");
        if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
            ex.getResponseHeaders().set("Allow", "GET, HEAD");
            send(ex, 405, "application/json; charset=utf-8", JSON.toJson(Map.of("error", "GET or HEAD required")));
            return;
        }
        try {
            if (requestPath.equals("/__id")) {
                send(ex, 200, "text/plain; charset=utf-8", ID);
            } else if (api) {
                String file = replayFile(ex.getRequestURI().getRawQuery());
                Path path = resolveFile(root, file);
                if (!path.getFileName().toString().endsWith(".json")) throw new IllegalArgumentException("Replay must be a JSON file");
                send(ex, 200, "application/json; charset=utf-8", JSON.toJson(CgViewReplay.load(path)));
            } else {
                if (requestPath.equals("/") || requestPath.equals("/visualizer") || requestPath.equals("/visualizer/"))
                    requestPath = "/visualizer/index.html";
                Path path = resolveFile(root, requestPath);
                send(ex, 200, contentType(path), Files.readAllBytes(path));
            }
        } catch (NoSuchFileException e) {
            send(ex, 404, "application/json; charset=utf-8", JSON.toJson(Map.of("error", "File not found in this checkout")));
        } catch (IllegalArgumentException | SecurityException e) {
            send(ex, 400, "application/json; charset=utf-8", JSON.toJson(Map.of("error", e.getMessage())));
        } catch (IOException e) {
            send(ex, api ? 422 : 500, "application/json; charset=utf-8", JSON.toJson(Map.of("error", e.getMessage())));
        }
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
