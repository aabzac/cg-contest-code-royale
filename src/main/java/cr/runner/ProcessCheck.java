package cr.runner;

import cr.bot.BotFactory;
import cr.referee.ProcessBot;
import cr.referee.Referee;

import java.util.List;

/**
 * Runs the merged build/submit/Player as a real process over the protocol on both seats
 * against an opponent spec. No timeout grace: a late answer is a timeout (kill).
 * Usage: ProcessCheck opponentSpec games seedBase
 */
public final class ProcessCheck {
    public static void main(String[] args) {
        String opp = args[0];
        int games = Integer.parseInt(args[1]);
        long seedBase = Long.parseLong(args[2]);
        int wins = 0, timeouts = 0, kills = 0, total = 0;
        long maxFirst = 0, maxLater = 0;
        for (int g = 0; g < games; g++) {
            for (int seat = 0; seat < 2; seat++) {
                long seed = seedBase + g;
                ProcessBot sub = new ProcessBot("submit", List.of("java", "-cp", "build/submit/classes", "Player"), true);
                Referee.Result r = seat == 0
                    ? Referee.play(seed, sub, BotFactory.create(opp), false, 50)
                    : Referee.play(seed, BotFactory.create(opp), sub, false, 50);
                total++;
                if (r.winner == seat) wins++;
                timeouts += r.timeouts[seat];
                if (r.error[seat] != null) {
                    kills++;
                    System.out.println("seed " + seed + " seat " + seat + " killed: " + r.error[seat]);
                }
                maxFirst = Math.max(maxFirst, r.maxFirstNanos[seat]);
                maxLater = Math.max(maxLater, r.maxNanos[seat]);
            }
        }
        System.out.printf(java.util.Locale.ROOT, "procCheck vs %s: games=%d wins=%d timeouts=%d kills=%d maxFirst=%.1fms maxLater=%.1fms%n",
            opp, total, wins, timeouts, kills, maxFirst / 1e6, maxLater / 1e6);
        if (timeouts > 0 || kills > 0) System.exit(1);
    }
}
