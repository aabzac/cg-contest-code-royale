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
 * Local match: bots read the exact protocol text (in-process bots through their own
 * {@link InputReader}, {@link ProcessBot}s through stdin) and their output goes through
 * {@link Command#parse}, like on CG. Process bots get no grace: a late answer is a timeout and
 * kills the bot; in-process bots are only timed.
 */
public final class Referee {
    public static final class Result {
        public long seed;
        public int winner;           // -1 draw
        public int turns;
        public final int[] score = new int[2];
        public final long[] maxNanos = new long[2];
        public final long[] maxFirstNanos = new long[2];
        public final long[] totalNanos = new long[2];
        public final int[] slow = new int[2];
        public final int[] timeouts = new int[2];
        public final String[] error = new String[2];
        /** Process bots only: see {@link ProcessBot#extraLines}. */
        public final int[] extraLines = new int[2];
        public List<String> replay;  // optional: per-turn command lines
        /** Optional visualizer data: static sites and one frame per turn (state after it). */
        public List<int[]> sites;
        public List<Frame> frames;
    }

    /** State after a turn, for the visualizer (visualizer/index.html). */
    public static final class Frame {
        public int turn;
        public int[] gold, hp;
        public int[][] queens;
        /** owner, type, x, y, hp */
        public int[][] creeps;
        /** type, owner, param1 (mine rate / tower hp / barracks turns left), param2 (tower range / creep type), gold */
        public int[][] structures;
        public String[] cmds;

        static Frame of(GameState s, String[] cmds) {
            Frame f = new Frame();
            f.turn = s.turn;
            f.gold = s.gold.clone();
            f.hp = s.hp.clone();
            f.queens = new int[][]{{(int) Math.round(s.qx[0]), (int) Math.round(s.qy[0])}, {(int) Math.round(s.qx[1]), (int) Math.round(s.qy[1])}};
            f.creeps = new int[s.nCreeps][];
            for (int i = 0; i < s.nCreeps; i++)
                f.creeps[i] = new int[]{s.cOwner[i], s.cType[i], (int) Math.round(s.cx[i]), (int) Math.round(s.cy[i]), s.cHp[i]};
            f.structures = new int[s.nSites][];
            for (int i = 0; i < s.nSites; i++) {
                int t = s.stType[i], p1 = -1, p2 = -1;
                if (t == cr.core.Const.MINE) p1 = s.mineRate[i];
                else if (t == cr.core.Const.TOWER) { p1 = s.towerHp[i]; p2 = s.towerRange[i]; }
                else if (t == cr.core.Const.BARRACKS) {
                    p1 = s.barrTraining[i] ? cr.core.Const.CREEP_BUILD_TIME[s.barrType[i]] - s.barrProgress[i] : 0;
                    p2 = s.barrType[i];
                }
                f.structures[i] = new int[]{t, s.stOwner[i], p1, p2, s.sGold[i]};
            }
            f.cmds = cmds;
            return f;
        }
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
        if (record) {
            r.replay = new ArrayList<>();
            r.sites = new ArrayList<>();
            for (int i = 0; i < s.nSites; i++) r.sites.add(new int[]{i, (int) Math.round(s.sx[i]), (int) Math.round(s.sy[i]), s.sr[i]});
            r.frames = new ArrayList<>();
            r.frames.add(Frame.of(s, new String[]{"", ""}));
        }
        try {
            String init = Protocol.initInput(s);
            for (int p = 0; p < 2; p++) {
                if (bots[p] instanceof ProcessBot) {
                    ProcessBot pb = (ProcessBot) bots[p];
                    pb.start();
                    pb.send(init);
                } else {
                    feeds[p].push(init);
                    readers[p].readInit();
                    bots[p].init(readers[p].state);
                }
            }
            while (!s.over) {
                Command[] cmds = new Command[2];
                String[] raw = new String[2];
                for (int p = 0; p < 2; p++) {
                    String text = Protocol.turnInput(s, p);
                    long budget = (s.turn == 0 ? 1000 : turnMs) * 1_000_000L;
                    String l0, l1;
                    long t0 = System.nanoTime(), dt;
                    if (bots[p] instanceof ProcessBot) {
                        ProcessBot pb = (ProcessBot) bots[p];
                        pb.send(text);
                        l0 = pb.readLine(t0 + budget);
                        l1 = l0 == null ? null : pb.readLine(t0 + budget);
                        if (l1 == null) r.timeouts[p]++;
                        // Until the second line arrived, not until this thread took it.
                        dt = (l1 == null ? System.nanoTime() : pb.lastArrivalNanos()) - t0;
                    } else {
                        feeds[p].push(text);
                        readers[p].readTurn();
                        t0 = System.nanoTime();
                        Command c;
                        try {
                            c = bots[p].play(readers[p].state, readers[p].touchedSite, t0 + budget);
                        } catch (RuntimeException e) {
                            r.error[p] = e.toString();
                            c = null;
                        }
                        l0 = c == null ? null : c.queenLine();
                        l1 = c == null ? null : c.trainLine();
                        dt = System.nanoTime() - t0;
                    }
                    r.totalNanos[p] += dt;
                    if (s.turn == 0) r.maxFirstNanos[p] = Math.max(r.maxFirstNanos[p], dt);
                    else r.maxNanos[p] = Math.max(r.maxNanos[p], dt);
                    if (dt > budget) r.slow[p]++;
                    cmds[p] = Command.parse(l0, l1, s.nSites);
                    raw[p] = l1 == null ? "<none>" : l0 + " | " + l1;
                    if (record) r.replay.add(p + " " + raw[p]);
                }
                rules.resolve(s, cmds[0], cmds[1]);
                if (record) r.frames.add(Frame.of(s, raw));
                for (int p = 0; p < 2; p++) if (rules.lastKill[p] != null && r.error[p] == null) r.error[p] = rules.lastKill[p];
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            for (int p = 0; p < 2; p++) {
                if (!(bots[p] instanceof ProcessBot)) continue;
                ProcessBot pb = (ProcessBot) bots[p];
                pb.close();
                r.extraLines[p] = pb.extraLines();
            }
        }
        r.turns = s.turn;
        r.winner = s.winner();
        r.score[0] = s.score[0];
        r.score[1] = s.score[1];
        return r;
    }
}
