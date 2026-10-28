package cr.runner;

import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.core.Command;
import cr.core.GameState;
import cr.core.InputReader;
import cr.core.MapGen;
import cr.core.Protocol;
import cr.core.Rules;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs the merged build/submit/Player as a real process over the protocol on both seats
 * against an in-process opponent. No timeout grace: a late answer is a timeout (kill).
 * Usage: ProcessCheck opponentSpec games seedBase
 */
public final class ProcessCheck {
    static final class Proc implements AutoCloseable {
        final Process p;
        final OutputStream out;
        final BlockingQueue<String> lines = new ArrayBlockingQueue<>(1024);

        Proc() throws IOException {
            p = new ProcessBuilder("java", "-cp", "build/submit/classes", "Player").redirectError(ProcessBuilder.Redirect.INHERIT).start();
            out = p.getOutputStream();
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.US_ASCII))) {
                    String l;
                    while ((l = r.readLine()) != null) lines.put(l);
                } catch (IOException | InterruptedException ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void send(String s) throws IOException {
            out.write(s.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        String line(long deadlineNanos) throws InterruptedException {
            long wait = deadlineNanos - System.nanoTime();
            return wait <= 0 ? lines.poll() : lines.poll(wait, TimeUnit.NANOSECONDS);
        }

        @Override public void close() { p.destroyForcibly(); }
    }

    public static void main(String[] args) throws Exception {
        String opp = args[0];
        int games = Integer.parseInt(args[1]);
        long seedBase = Long.parseLong(args[2]);
        int wins = 0, timeouts = 0, errors = 0, total = 0;
        long maxFirst = 0, maxLater = 0;
        for (int g = 0; g < games; g++) {
            for (int seat = 0; seat < 2; seat++) {
                long seed = seedBase + g;
                GameState s = MapGen.generate(seed, 4);
                Rules rules = new Rules();
                Bot bot = BotFactory.create(opp);
                String init = Protocol.initInput(s);
                try (Proc proc = new Proc()) {
                    proc.send(init);
                    InputReader r = new InputReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.US_ASCII)));
                    r.readInit();
                    bot.init(r.state);
                    String oppText = init;
                    while (!s.over) {
                        Command[] cmds = new Command[2];
                        // process side
                        long t0 = System.nanoTime();
                        proc.send(Protocol.turnInput(s, seat));
                        long limit = (s.turn == 0 ? 1000 : 50) * 1_000_000L;
                        String l0 = proc.line(t0 + limit), l1 = l0 == null ? null : proc.line(t0 + limit);
                        long dt = System.nanoTime() - t0;
                        if (s.turn == 0) maxFirst = Math.max(maxFirst, dt); else maxLater = Math.max(maxLater, dt);
                        if (l1 == null) timeouts++;
                        cmds[seat] = Command.parse(l0, l1, s.nSites);
                        // in-process opponent reads the full text history so far
                        oppText = oppText + Protocol.turnInput(s, 1 - seat);
                        r = new InputReader(new ByteArrayInputStream(oppText.getBytes(StandardCharsets.US_ASCII)));
                        r.readInit();
                        for (int k = 0; k <= s.turn; k++) r.readTurn();
                        Command c = bot.play(r.state, r.touchedSite, System.nanoTime() + 50_000_000L);
                        cmds[1 - seat] = Command.parse(c.queenLine(), c.trainLine(), s.nSites);
                        rules.resolve(s, cmds[0], cmds[1]);
                        if (rules.lastKill[seat] != null) { errors++; System.out.println("seed " + seed + " seat " + seat + " killed: " + rules.lastKill[seat]); }
                    }
                }
                total++;
                if (s.winner() == seat) wins++;
            }
        }
        System.out.printf(java.util.Locale.ROOT, "procCheck vs %s: games=%d wins=%d timeouts=%d kills=%d maxFirst=%.1fms maxLater=%.1fms%n",
            opp, total, wins, timeouts, errors, maxFirst / 1e6, maxLater / 1e6);
        if (timeouts > 0 || errors > 0) System.exit(1);
    }
}
