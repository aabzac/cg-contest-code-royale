package cr.tools;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Static localhost server for the visualizer: serves files under the repository root only.
 * GET /__id returns an identity string so a caller can tell which server answered.
 * Usage: Serve [port]
 */
public final class Serve {
    public static final String ID = "code-royale-visualizer";

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        Path root = Paths.get("").toAbsolutePath().normalize();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (BindException e) {
            System.err.println("port " + port + " is busy; is another visualizer server running? (GET /__id identifies it)");
            System.exit(1);
            return;
        }
        server.createContext("/", ex -> {
            String raw = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            byte[] body;
            int code = 200;
            String type = "text/plain; charset=utf-8";
            if (raw.equals("/__id")) {
                body = ID.getBytes(StandardCharsets.UTF_8);
            } else {
                Path p = root.resolve(raw.replaceFirst("^/+", "")).normalize();
                if (!p.startsWith(root) || !Files.isRegularFile(p)) {
                    code = 404;
                    body = ("not found: " + raw).getBytes(StandardCharsets.UTF_8);
                } else {
                    body = Files.readAllBytes(p);
                    String n = p.getFileName().toString();
                    type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".json") ? "application/json"
                        : n.endsWith(".js") ? "text/javascript" : "application/octet-stream";
                }
            }
            ex.getResponseHeaders().add("Content-Type", type);
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.sendResponseHeaders(code, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.start();
        System.out.println(ID + " serving " + root + " at http://localhost:" + port + "/visualizer/index.html?file=/build/replays/local/<file>.json");
    }
}
