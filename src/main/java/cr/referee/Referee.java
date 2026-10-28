package cr.referee;

import cr.bot.Bot;
import cr.core.Command;
import cr.core.GameState;
import cr.core.InputReader;
import cr.core.MapGen;
import cr.core.Protocol;
import cr.core.Rules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Local match: bots read the exact protocol text through their own {@link InputReader} and their
 * output goes through {@link Command#parse}, like on CG. In-process bots get no timeout grace
 * accounting here beyond the recorded timing; use ProcessCheck for the real artifact.
 */
public final class Referee {
    public static final class Result {
        public long seed;
        public int winner;           // -1 draw
        public int turns;
        public final int[] score = new int[2];
        public final long[] maxNanos = new long[2];
        public final long[] totalNanos = new long[2];
        public final int[] slow = new int[2];
        public final String[] error = new String[2];
        public List<String> replay;  // optional: per-turn command lines
    }

    /** Byte queue the referee writes and an InputReader reads. */
    static final class Feed extends InputStream {
        private byte[] data = new byte[0];
        private int pos;

        void push(String s) {
            byte[] b = s.getBytes(StandardCharsets.US_ASCII);
            byte[] n = new byte[data.length - pos + b.length];
            System.arraycopy(data, pos, n, 0, data.length - pos);
            System.arraycopy(b, 0, n, data.length - pos, b.length);
            data = n;
            pos = 0;
        }

        @Override public int read() { return pos < data.length ? data[pos++] & 0xff : -1; }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int n = Math.min(len, data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }

    public static Result play(long seed, Bot b0, Bot b1, boolean record, long turnMs) {
        GameState s = MapGen.generate(seed, 4);
        Rules rules = new Rules();
        Bot[] bots = {b0, b1};
        Feed[] feeds = {new Feed(), new Feed()};
        InputReader[] readers = {new InputReader(feeds[0]), new InputReader(feeds[1])};
        Result r = new Result();
        r.seed = seed;
        if (record) r.replay = new ArrayList<>();
        try {
            for (int p = 0; p < 2; p++) {
                feeds[p].push(Protocol.initInput(s));
                readers[p].readInit();
                bots[p].init(readers[p].state);
            }
            while (!s.over) {
                Command[] cmds = new Command[2];
                for (int p = 0; p < 2; p++) {
                    feeds[p].push(Protocol.turnInput(s, p));
                    readers[p].readTurn();
                    long budget = (s.turn == 0 ? 1000 : turnMs) * 1_000_000L;
                    long t0 = System.nanoTime();
                    Command c;
                    try {
                        c = bots[p].play(readers[p].state, readers[p].touchedSite, t0 + budget);
                    } catch (RuntimeException e) {
                        r.error[p] = e.toString();
                        c = null;
                    }
                    long dt = System.nanoTime() - t0;
                    r.totalNanos[p] += dt;
                    r.maxNanos[p] = Math.max(r.maxNanos[p], dt);
                    if (dt > budget) r.slow[p]++;
                    cmds[p] = c == null ? Command.parse(null, null, s.nSites) : Command.parse(c.queenLine(), c.trainLine(), s.nSites);
                    if (record) r.replay.add(p + " " + (c == null ? "<none>" : c.queenLine() + " | " + c.trainLine()));
                }
                rules.resolve(s, cmds[0], cmds[1]);
                for (int p = 0; p < 2; p++) if (rules.lastKill[p] != null && r.error[p] == null) r.error[p] = rules.lastKill[p];
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        r.turns = s.turn;
        r.winner = s.winner();
        r.score[0] = s.score[0];
        r.score[1] = s.score[1];
        return r;
    }
}
