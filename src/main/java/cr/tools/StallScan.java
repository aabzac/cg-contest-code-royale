package cr.tools;

import cr.bot.BotFactory;
import cr.core.Const;
import cr.referee.Referee;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Queen stalls of a bot: turns where it sent BUILD to a site its queen did not touch, so the queen
 * should have walked, and she ended the turn where she started. Every map is played on both seats.
 * Usage: StallScan bot opponent maps seedBase [threads]
 */
public final class StallScan {
    /** One stall streak: consecutive stall turns of the bot in one game. */
    record Streak(long seed, int seat, int firstTurn, int length, String command, int qx, int qy, boolean won) {}

    public static void main(String[] args) throws Exception {
        String bot = args[0], opp = args[1];
        int maps = Integer.parseInt(args[2]);
        long seedBase = Long.parseLong(args[3]);
        int threads = args.length > 4 ? Integer.parseInt(args[4]) : 8;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<List<Streak>>> fut = new ArrayList<>();
        for (int m = 0; m < maps; m++) {
            long seed = seedBase + m;
            for (int seat = 0; seat < 2; seat++) {
                int s = seat;
                fut.add(ex.submit(() -> streaks(seed, s, s == 0
                    ? Referee.play(seed, BotFactory.create(bot), BotFactory.create(opp), true, 50)
                    : Referee.play(seed, BotFactory.create(opp), BotFactory.create(bot), true, 50))));
            }
        }
        List<Streak> all = new ArrayList<>();
        for (Future<List<Streak>> f : fut) all.addAll(f.get());
        ex.shutdown();

        int turns = 0;
        int[] hist = new int[6];
        Streak worst = null;
        java.util.Set<String> longGames = new java.util.TreeSet<>();
        int longLost = 0;
        for (Streak k : all) {
            turns += k.length;
            hist[k.length == 1 ? 0 : k.length <= 4 ? 1 : k.length <= 9 ? 2 : k.length <= 19 ? 3 : k.length <= 39 ? 4 : 5]++;
            if (worst == null || k.length > worst.length) worst = k;
            if (k.length >= 10 && longGames.add(k.seed + "/" + k.seat) && !k.won) longLost++;
        }
        System.out.printf(Locale.ROOT, "%s vs %s: maps=%d games=%d seeds %d..%d%n", bot, opp, maps, 2 * maps, seedBase, seedBase + maps - 1);
        System.out.printf(Locale.ROOT, "  stall turns %d, streaks %d [1:%d, 2-4:%d, 5-9:%d, 10-19:%d, 20-39:%d, 40+:%d]%n",
            turns, all.size(), hist[0], hist[1], hist[2], hist[3], hist[4], hist[5]);
        System.out.printf(Locale.ROOT, "  games with a streak of 10+ turns: %d, of them lost or drawn %d%n", longGames.size(), longLost);
        all.sort((a, b) -> b.length - a.length);
        for (Streak k : all.subList(0, Math.min(8, all.size())))
            System.out.printf(Locale.ROOT, "  seed %d seat %d turns %d-%d (%d): queen %d,%d '%s' %s%n", k.seed, k.seat, k.firstTurn,
                k.firstTurn + k.length - 1, k.length, k.qx, k.qy, k.command, k.won ? "won" : "not won");
    }

    /** The stall streaks of the bot on seat in a recorded game. */
    static List<Streak> streaks(long seed, int seat, Referee.Result r) {
        List<Streak> out = new ArrayList<>();
        int start = -1, len = 0;
        String cmd = null;
        int sx = 0, sy = 0;
        for (int k = 1; k < r.frames.size(); k++) {
            Referee.Frame before = r.frames.get(k - 1), after = r.frames.get(k);
            String line = after.cmds[seat];
            boolean stall = false;
            if (line.startsWith("BUILD ")) {
                int site = Integer.parseInt(line.split(" ")[1]);
                int[] st = r.sites.get(site);
                int bx = before.queens[seat][0], by = before.queens[seat][1];
                double dx = st[1] - bx, dy = st[2] - by, reach = st[3] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA;
                stall = dx * dx + dy * dy >= reach * reach && bx == after.queens[seat][0] && by == after.queens[seat][1];
            }
            if (stall) {
                if (len == 0) { start = before.turn; cmd = line.split(" \\| ")[0]; sx = before.queens[seat][0]; sy = before.queens[seat][1]; }
                len++;
            } else if (len > 0) {
                out.add(new Streak(seed, seat, start, len, cmd, sx, sy, r.winner == seat));
                len = 0;
            }
        }
        if (len > 0) out.add(new Streak(seed, seat, start, len, cmd, sx, sy, r.winner == seat));
        return out;
    }
}
