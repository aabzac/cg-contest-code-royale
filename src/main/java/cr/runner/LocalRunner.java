package cr.runner;

import cr.bot.BotFactory;
import cr.referee.Referee;

/**
 * One reproducible match. Usage: LocalRunner bot0 bot1 seed [printCommands 0|1] [replayOut.json]
 * The replay file is read by visualizer/index.html (serve with ./gradlew serve).
 */
public final class LocalRunner {
    public static void main(String[] args) throws java.io.IOException {
        long seed = Long.parseLong(args[2]);
        boolean print = args.length > 3 && args[3].equals("1");
        String out = args.length > 4 && !args[4].isBlank() ? args[4] : null;
        Referee.Result r = Referee.play(seed, BotFactory.create(args[0]), BotFactory.create(args[1]), print || out != null, 50);
        if (print) r.replay.forEach(System.out::println);
        if (out != null) {
            java.util.Map<String, Object> rep = new java.util.LinkedHashMap<>();
            rep.put("seed", seed);
            rep.put("bots", new String[]{args[0], args[1]});
            rep.put("winner", r.winner);
            rep.put("sites", r.sites);
            rep.put("frames", r.frames);
            java.nio.file.Path p = java.nio.file.Paths.get(out);
            if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.writeString(p, new com.google.gson.Gson().toJson(rep));
            System.out.println("replay " + p + " (open http://localhost:8765/visualizer/index.html?file=/" + p.toString().replace('\\', '/') + ")");
        }
        System.out.printf(java.util.Locale.ROOT, "seed=%d winner=%d turns=%d score=%d:%d errors=%s/%s extraLines=%d/%d max=%.1f/%.1fms%n", seed, r.winner,
            r.turns, r.score[0], r.score[1], r.error[0], r.error[1], r.extraLines[0], r.extraLines[1], r.maxNanos[0] / 1e6, r.maxNanos[1] / 1e6);
    }
}
