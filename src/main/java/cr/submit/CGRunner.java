package cr.submit;

import cr.bot.Bot;
import cr.bot.Params;
import cr.bot.current.MainBot;
import cr.core.Command;
import cr.core.InputReader;

/** CodinGame entry point (ships). Diagnostics go to stderr only. */
public final class CGRunner {
    public static final long FIRST_TURN_MS = 1000, TURN_MS = 50, MARGIN_MS = 10;
    /** Effective parameters of the submitted bot; FileMerger checks this matches the requested build. */
    public static final String PARAMS = "";

    public static void main(String[] args) throws Exception {
        InputReader in = new InputReader(System.in);
        Bot bot = new MainBot(new Params().apply(PARAMS));
        in.readInit();
        bot.init(in.state);
        int turn = 0;
        while (true) {
            try {
                in.readTurn();
            } catch (java.io.IOException e) {
                return;
            }
            long start = System.nanoTime();
            long budget = (turn == 0 ? FIRST_TURN_MS : TURN_MS) - MARGIN_MS;
            Command c = bot.play(in.state, in.touchedSite, start + budget * 1_000_000L);
            System.out.println(c.queenLine());
            System.out.println(c.trainLine());
            System.out.flush();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            if (ms > budget) System.err.println("SLOW turn " + turn + " " + ms + "ms");
            turn++;
        }
    }
}
