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
 * Usage: BenchmarkRunner botA botB maps threads seedBase turnMs
 */
public final class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        String a = args[0], b = args[1];
        int maps = Integer.parseInt(args[2]);
        int threads = Integer.parseInt(args[3]);
        long seedBase = Long.parseLong(args[4]);
        long turnMs = Long.parseLong(args[5]);

        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<Referee.Result[]>> fut = new ArrayList<>();
        for (int m = 0; m < maps; m++) {
            long seed = seedBase + m;
            fut.add(ex.submit(() -> new Referee.Result[]{
                Referee.play(seed, BotFactory.create(a), BotFactory.create(b), false, turnMs),
                Referee.play(seed, BotFactory.create(b), BotFactory.create(a), false, turnMs)}));
        }
        double sum = 0, sumSq = 0;
        int wins = 0, draws = 0, losses = 0, winsAsP0 = 0, winsAsP1 = 0, errA = 0, errB = 0, extraA = 0, extraB = 0, slowA = 0, slowB = 0;
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
                maxA = Math.max(maxA, g.maxNanos[aSeat]); maxB = Math.max(maxB, g.maxNanos[1 - aSeat]);
                totA += g.totalNanos[aSeat]; totB += g.totalNanos[1 - aSeat];
                turns += g.turns;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("seed", g.seed); row.put("aSeat", aSeat); row.put("winner", g.winner); row.put("turns", g.turns);
                row.put("score", g.score); row.put("errors", g.error); row.put("extraLines", g.extraLines);
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
        System.out.printf(java.util.Locale.ROOT, "  avgTurns=%.1f errors A=%d B=%d extraLines A=%d B=%d slow A=%d B=%d time/move avg A=%.2fms B=%.2fms max A=%.1fms B=%.1fms%n",
            (double) turns / n, errA, errB, extraA, extraB, slowA, slowB, totA / 1e6 / Math.max(1, turns), totB / 1e6 / Math.max(1, turns), maxA / 1e6, maxB / 1e6);

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("botA", a); rep.put("botB", b); rep.put("maps", maps); rep.put("seedBase", seedBase); rep.put("turnMs", turnMs);
        rep.put("score", mean); rep.put("ci95", ci); rep.put("wins", wins); rep.put("draws", draws); rep.put("losses", losses);
        rep.put("errorsA", errA); rep.put("errorsB", errB); rep.put("extraLinesA", extraA); rep.put("extraLinesB", extraB);
        rep.put("games", games);
        Path dir = Paths.get("build", "bench");
        Files.createDirectories(dir);
        Path out = dir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        try {
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(rep));
            System.out.println("  report " + out);
        } catch (IOException e) {
            System.err.println("could not write report: " + e);
        }
    }
}
