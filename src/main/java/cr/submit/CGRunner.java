package cr.submit;

import cr.bot.Bot;
import cr.bot.Params;
import cr.bot.current.MainBot;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.InputReader;

/** CodinGame entry point (ships). Diagnostics go to stderr only. */
public final class CGRunner {
    public static final long FIRST_TURN_MS = 1000, TURN_MS = 50, MARGIN_MS = 10;
    /** Part of the first turn the warm-up may use, counted from the turn's start. */
    static final long WARM_UP_MS = 300;
    /** Output kinds the warm-up produced (see {@link #warmUp}). */
    static final int WARM_MOVE = 1, WARM_BUILD = 2, WARM_TRAIN = 4;
    /** Parameters of the submitted bot ("" = defaults); FileMerger sets them and checks that they apply. */
    public static final String PARAMS = "";

    public static void main(String[] args) throws Exception {
        InputReader in = new InputReader(System.in);
        Params params = new Params().apply(PARAMS);
        Bot bot = new MainBot(params);
        in.readInit();
        bot.init(in.state);
        int turn = 0;
        while (true) {
            try {
                in.readTurn();
            } catch (java.io.IOException e) {
                return;
            }
            long start = in.turnStartNanos;
            long budget = (turn == 0 ? FIRST_TURN_MS : TURN_MS) - MARGIN_MS;
            if (turn == 0) {
                try {
                    warmUp(in.state, params, start + WARM_UP_MS * 1_000_000L);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            Command c;
            try {
                c = bot.play(in.state, in.touchedSite, start + budget * 1_000_000L);
            } catch (Throwable t) {
                t.printStackTrace();
                c = null;
            }
            if (c == null) c = Command.waitCmd();
            System.out.println(c.queenLine());
            System.out.println(c.trainLine());
            System.out.flush();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            if (ms > budget) System.err.println(new StringBuilder("SLOW turn ").append(turn).append(' ').append(ms).append("ms"));
            turn++;
        }
    }

    /**
     * Plays a throw-away bot on altered copies of the first state, so that one-off costs of first
     * use (class loading, lambda and string-concatenation bootstraps) fall in the 1000 ms first turn
     * instead of a later 50 ms one. Neither the real state nor the real bot is touched. The copies
     * reach a touched site, the nearest-site search, the flight from a knight (MOVE) and the
     * fallback to an own tower with a TRAIN. Returns the WARM_* kinds of output produced.
     */
    static int warmUp(GameState real, Params params, long deadlineNanos) {
        Bot bot = new MainBot(params);
        int kinds = 0;
        for (int k = 0; k < 4 && System.nanoTime() < deadlineNanos; k++) {
            GameState s = real.copy();
            int touched = -1;
            if (k == 0) {
                touched = 0;
            } else if (k == 2) {
                for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.TOWER && s.stOwner[i] == 0) s.clearStructure(i);
                if (s.nCreeps < GameState.MAX_CREEPS) s.addCreep(1, Const.KNIGHT, s.qx[0] + 10, s.qy[0]);
            } else if (k == 3) {
                for (int i = 0; i < s.nSites; i++) {
                    s.clearStructure(i);
                    s.stType[i] = Const.TOWER;
                    s.stOwner[i] = 0;
                    s.towerHp[i] = Const.TOWER_HP_MAXIMUM;
                    s.towerRange[i] = 400;
                }
                s.clearStructure(0);
                s.stType[0] = Const.BARRACKS;
                s.stOwner[0] = 0;
                s.barrType[0] = Const.KNIGHT;
                s.gold[0] = 1000;
            }
            Command c = bot.play(s, touched, deadlineNanos);
            String queen = c.queenLine(), train = c.trainLine();
            if (queen.startsWith("MOVE ")) kinds |= WARM_MOVE;
            if (queen.startsWith("BUILD ")) kinds |= WARM_BUILD;
            if (train.length() > "TRAIN".length()) kinds |= WARM_TRAIN;
        }
        return kinds;
    }
}
