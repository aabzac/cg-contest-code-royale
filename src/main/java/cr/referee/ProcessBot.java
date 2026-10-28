package cr.referee;

import cr.bot.Bot;
import cr.core.Command;
import cr.core.GameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An external program speaking the CG text protocol (one process per game). The Referee feeds it
 * the exact input text and parses its raw output lines; {@link #play} is not used.
 */
public final class ProcessBot implements Bot, AutoCloseable {
    private final String name;
    private final List<String> command;
    private final boolean inheritStderr;
    private Process proc;
    private OutputStream out;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    public ProcessBot(String name, List<String> command, boolean inheritStderr) {
        this.name = name;
        this.command = command;
        this.inheritStderr = inheritStderr;
    }

    @Override public String name() { return name; }

    @Override
    public Command play(GameState s, int touchedSite, long deadlineNanos) {
        throw new UnsupportedOperationException("ProcessBot speaks the text protocol through the Referee");
    }

    public void start() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(inheritStderr ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.DISCARD);
        proc = pb.start();
        out = proc.getOutputStream();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.US_ASCII))) {
                String l;
                while ((l = r.readLine()) != null) lines.add(l);
            } catch (IOException ignored) {
                // process ended
            }
        }, "bot-" + name);
        t.setDaemon(true);
        t.start();
    }

    public void send(String text) throws IOException {
        try {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            // A dead process cannot read; the missing answer is reported as a timeout.
        }
    }

    /** Next output line, or null if none arrives before the deadline. */
    public String readLine(long deadlineNanos) throws InterruptedException {
        long wait = deadlineNanos - System.nanoTime();
        return wait <= 0 ? lines.poll() : lines.poll(wait, TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() {
        if (proc != null) proc.destroyForcibly();
    }
}
