package cr.runner;

import com.google.gson.GsonBuilder;
import cr.bot.BotFactory;
import cr.referee.Referee;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Paired benchmark: every map seed is played twice with seats swapped and fresh bots.
 * Uncertainty is computed over maps (a seat-swapped pair is one sample).
 * Usage: BenchmarkRunner botA botB maps threads seedBase turnMs [report.json]
 * The report (default build/bench/&lt;time&gt;.json) holds every game, what each spec plays
 * ({@link BotFactory#describe}) and the git revision; tools/bench_pair.py pairs reports (policy P2).
 */
public final class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        String a = args[0], b = args[1];
        int maps = Integer.parseInt(args[2]);
        int threads = Integer.parseInt(args[3]);
        long seedBase = Long.parseLong(args[4]);
        long turnMs = Long.parseLong(args[5]);
        String reportPath = args.length > 6 && !args[6].isBlank() ? args[6] : null;
        String effA = BotFactory.describe(a), effB = BotFactory.describe(b);

        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<Referee.Result[]>> fut = new ArrayList<>();
        for (int m = 0; m < maps; m++) {
            long seed = seedBase + m;
            fut.add(ex.submit(() -> new Referee.Result[]{
                Referee.play(seed, BotFactory.create(a), BotFactory.create(b), false, turnMs),
                Referee.play(seed, BotFactory.create(b), BotFactory.create(a), false, turnMs)}));
        }
        double sum = 0, sumSq = 0;
        int wins = 0, draws = 0, losses = 0, winsAsP0 = 0, winsAsP1 = 0, errA = 0, errB = 0, extraA = 0, extraB = 0, slowA = 0, slowB = 0, toA = 0, toB = 0;
        long maxA = 0, maxB = 0, totA = 0, totB = 0, turns = 0;
        List<Map<String, Object>> games = new ArrayList<>();
        for (Future<Referee.Result[]> f : fut) {
            Referee.Result[] r = f.get();
            double mapScore = 0;
            for (int k = 0; k < 2; k++) {
                int aSeat = k == 0 ? 0 : 1;
                Referee.Result g = r[k];
                double sc = g.winner == -1 ? 0.5 : (g.winner == aSeat ? 1 : 0);
                mapScore += sc / 2;
                if (sc == 1) { wins++; if (aSeat == 0) winsAsP0++; else winsAsP1++; } else if (sc == 0) losses++; else draws++;
                if (g.error[aSeat] != null) errA++;
                if (g.error[1 - aSeat] != null) errB++;
                extraA += g.extraLines[aSeat]; extraB += g.extraLines[1 - aSeat];
                slowA += g.slow[aSeat]; slowB += g.slow[1 - aSeat];
                toA += g.timeouts[aSeat]; toB += g.timeouts[1 - aSeat];
                maxA = Math.max(maxA, g.maxNanos[aSeat]); maxB = Math.max(maxB, g.maxNanos[1 - aSeat]);
                totA += g.totalNanos[aSeat]; totB += g.totalNanos[1 - aSeat];
                turns += g.turns;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("seed", g.seed); row.put("aSeat", aSeat); row.put("winner", g.winner); row.put("turns", g.turns);
                row.put("score", g.score); row.put("errors", g.error); row.put("extraLines", g.extraLines);
                row.put("slow", g.slow); row.put("timeouts", g.timeouts);
                games.add(row);
            }
            sum += mapScore;
            sumSq += mapScore * mapScore;
        }
        ex.shutdown();
        double mean = sum / maps;
        double sd = maps > 1 ? Math.sqrt(Math.max(0, (sumSq - maps * mean * mean) / (maps - 1))) : 0;
        double ci = 1.96 * sd / Math.sqrt(maps);
        int n = 2 * maps;
        System.out.printf(java.util.Locale.ROOT, "%s vs %s: maps=%d games=%d score=%.3f +-%.3f (95%% CI, map-paired) W/D/L=%d/%d/%d winsAsP0=%d winsAsP1=%d%n",
            a, b, maps, n, mean, ci, wins, draws, losses, winsAsP0, winsAsP1);
        System.out.printf(java.util.Locale.ROOT, "  avgTurns=%.1f errors A=%d B=%d extraLines A=%d B=%d slow A=%d B=%d timeouts A=%d B=%d time/move avg A=%.2fms B=%.2fms max A=%.1fms B=%.1fms%n",
            (double) turns / n, errA, errB, extraA, extraB, slowA, slowB, toA, toB, totA / 1e6 / Math.max(1, turns), totB / 1e6 / Math.max(1, turns), maxA / 1e6, maxB / 1e6);

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("botA", a); rep.put("botB", b); rep.put("effectiveA", effA); rep.put("effectiveB", effB);
        rep.put("maps", maps); rep.put("seedBase", seedBase); rep.put("turnMs", turnMs); rep.put("threads", threads);
        rep.put("revision", revision()); rep.put("java", System.getProperty("java.version"));
        rep.put("score", mean); rep.put("ci95", ci); rep.put("wins", wins); rep.put("draws", draws); rep.put("losses", losses);
        rep.put("errorsA", errA); rep.put("errorsB", errB); rep.put("extraLinesA", extraA); rep.put("extraLinesB", extraB);
        rep.put("slowA", slowA); rep.put("slowB", slowB); rep.put("timeoutsA", toA); rep.put("timeoutsB", toB);
        rep.put("games", games);
        Path out = reportPath != null ? Paths.get(reportPath)
            : Paths.get("build", "bench", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        try {
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(rep));
            System.out.println("  report " + out);
        } catch (IOException e) {
            System.err.println("could not write report: " + e);
        }
    }

    /**
     * HEAD, the porcelain status lines, the sha256 of `git diff HEAD` and of each untracked file that
     * is not ignored; null where git is unavailable.
     */
    static Map<String, Object> revision() {
        Map<String, Object> r = new LinkedHashMap<>();
        String head = git("rev-parse", "HEAD");
        r.put("commit", head == null ? null : head.trim());
        String status = git("status", "--porcelain");
        r.put("status", status == null ? null : status.lines().collect(java.util.stream.Collectors.toList()));
        String diff = git("diff", "HEAD", "--binary");
        r.put("diffSha256", diff == null ? null : sha256(diff.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String untracked = git("ls-files", "--others", "--exclude-standard");
        if (untracked != null) {
            Map<String, String> files = new java.util.TreeMap<>();
            for (String f : untracked.lines().collect(java.util.stream.Collectors.toList())) {
                try {
                    files.put(f, sha256(Files.readAllBytes(Paths.get(f))));
                } catch (IOException e) {
                    files.put(f, "unreadable");
                }
            }
            r.put("untracked", files);
        }
        return r;
    }

    private static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String git(String... args) {
        try {
            List<String> cmd = new ArrayList<>(List.of("git"));
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return p.waitFor() == 0 ? text : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
