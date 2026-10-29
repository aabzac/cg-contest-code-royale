package cr.referee;

import cr.bot.Bot;
import cr.core.Command;
import cr.core.GameState;
import cr.core.InputReader;
import cr.core.MapGen;
import cr.core.Protocol;
import cr.core.Rules;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Plays one seat of a recorded game again: the bot reads the protocol text of the recorded game for
 * that seat (like in {@link Referee#play}) and its two lines are compared with the recorded ones. The
 * game always advances with the recorded commands of both players, so a difference does not change
 * the later input. Equal lines on every turn show that the recorded player behaved as this bot on
 * the positions the game reached; they are not a proof of the same code elsewhere.
 */
public final class Follow {
    /** Why the comparison ended before the recorded game did. */
    public enum Stop {
        NONE,
        /** The caller's turn limit (for example the turns whose state was validated). */
        NOT_VALIDATED,
        /** The recorded player has no two lines on this turn (it was killed or cut short). */
        NO_RECORD,
        /** The bot gave no two lines where the recorded player did: a difference. */
        NO_ANSWER,
        /** The simulated game ended while the record goes on. */
        GAME_OVER
    }

    public static final class Result {
        /** Turns compared, and those where the bot's lines differed from the recorded ones. */
        public int turns, diffs;
        /** First differing turn (-1: none) with the recorded and the bot's lines there. */
        public int firstDiff = -1;
        public String expected, got;
        public Stop stop = Stop.NONE;
        /** The stop with its turn, or null. */
        public String stopped;

        void stop(Stop kind, int turn, String why) {
            stop = kind;
            stopped = "turn " + turn + ": " + why;
        }
    }

    private Follow() {}

    /**
     * @param recorded per player, the two recorded lines of each turn (null for a missing line)
     * @param maxTurns compare at most this many turns (for example those whose state was validated)
     * @param turnMs   reading deadline per turn for a {@link ProcessBot}; in-process bots get it as their deadline
     */
    public static Result follow(long seed, int seat, List<List<String[]>> recorded, Bot bot, int maxTurns, long turnMs)
            throws IOException, InterruptedException {
        GameState s = MapGen.generate(seed, 4);
        Rules rules = new Rules();
        Referee.Feed feed = new Referee.Feed();
        InputReader reader = new InputReader(feed);
        ProcessBot pb = bot instanceof ProcessBot ? (ProcessBot) bot : null;
        Result r = new Result();
        int n = Math.min(recorded.get(0).size(), recorded.get(1).size());
        try {
            String init = Protocol.initInput(s);
            if (pb != null) {
                pb.start();
                pb.send(init);
            } else {
                feed.push(init);
                reader.readInit();
                bot.init(reader.state);
            }
            int t = 0;
            for (; t < n && !s.over; t++) {
                if (t >= maxTurns) { r.stop(Stop.NOT_VALIDATED, t, "not validated"); break; }
                String[] mine = recorded.get(seat).get(t);
                if (mine[0] == null || mine[1] == null) { r.stop(Stop.NO_RECORD, t, "no recorded output"); break; }
                String text = Protocol.turnInput(s, seat);
                long deadline = System.nanoTime() + turnMs * 1_000_000L;
                String l0, l1;
                if (pb != null) {
                    pb.send(text);
                    l0 = pb.readLine(deadline);
                    l1 = l0 == null ? null : pb.readLine(deadline);
                } else {
                    feed.push(text);
                    reader.readTurn();
                    Command c = bot.play(reader.state, reader.touchedSite, deadline);
                    l0 = c.queenLine();
                    l1 = c.trainLine();
                }
                if (l1 == null) { r.stop(Stop.NO_ANSWER, t, "the bot did not answer in " + turnMs + " ms"); break; }
                r.turns++;
                if (!Objects.equals(l0, mine[0]) || !Objects.equals(l1, mine[1])) {
                    if (r.diffs++ == 0) {
                        r.firstDiff = t;
                        r.expected = mine[0] + " | " + mine[1];
                        r.got = l0 + " | " + l1;
                    }
                }
                String[] a = recorded.get(0).get(t), b = recorded.get(1).get(t);
                rules.resolve(s, Command.parse(a[0], a[1], s.nSites), Command.parse(b[0], b[1], s.nSites));
            }
            if (r.stop == Stop.NONE && t < n) r.stop(Stop.GAME_OVER, t, "the simulated game is over");
        } finally {
            if (pb != null) pb.close();
        }
        return r;
    }
}
