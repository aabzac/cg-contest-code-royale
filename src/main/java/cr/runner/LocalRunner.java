package cr.runner;

import cr.bot.BotFactory;
import cr.referee.Referee;

/** One reproducible match. Usage: LocalRunner bot0 bot1 seed [printCommands 0|1] */
public final class LocalRunner {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[2]);
        boolean print = args.length > 3 && args[3].equals("1");
        Referee.Result r = Referee.play(seed, BotFactory.create(args[0]), BotFactory.create(args[1]), print, 50);
        if (print) r.replay.forEach(System.out::println);
        System.out.printf(java.util.Locale.ROOT, "seed=%d winner=%d turns=%d score=%d:%d errors=%s/%s max=%.1f/%.1fms%n", seed, r.winner, r.turns,
            r.score[0], r.score[1], r.error[0], r.error[1], r.maxNanos[0] / 1e6, r.maxNanos[1] / 1e6);
    }
}
