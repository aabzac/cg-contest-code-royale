package cr.referee;

import cr.bot.Bot;
import cr.core.Command;
import cr.core.GameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An external program speaking the CG text protocol (one process per game). The Referee feeds it
 * the exact input text and parses its raw output lines; {@link #play} is not used. The program runs
 * without the JVM option variables (JDK_JAVA_OPTIONS, JAVA_TOOL_OPTIONS, _JAVA_OPTIONS, in any case):
 * CG does not set them, and the Java launcher would announce them on stderr.
 */
public final class ProcessBot implements Bot, AutoCloseable {
    private static final List<String> JVM_OPTION_VARS = List.of("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS");

    private final String name;
    private final List<String> command;
    private final boolean captureStderr;
    private Process proc;
    private OutputStream out;
    private Thread stdoutReader, stderrReader;
    private final BlockingQueue<Line> lines = new LinkedBlockingQueue<>();
    private final List<String> stderr = new ArrayList<>();
    private long lastArrival;
    private boolean timedOut, killedAtClose;
    private int extraLines, lateLines;

    /** An output line and System.nanoTime() when the reader thread received it. */
    private static final class Line {
        final String text;
        final long nanos;

        Line(String text, long nanos) { this.text = text; this.nanos = nanos; }
    }

    /** @param captureStderr keep the program's stderr lines (see {@link #stderr}); otherwise they are discarded */
    public ProcessBot(String name, List<String> command, boolean captureStderr) {
        this.name = name;
        this.command = command;
        this.captureStderr = captureStderr;
    }

    @Override public String name() { return name; }

    @Override
    public Command play(GameState s, int touchedSite, long deadlineNanos) {
        throw new UnsupportedOperationException("ProcessBot speaks the text protocol through the Referee");
    }

    public void start() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        clearJvmOptions(pb.environment());
        if (!captureStderr) pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        proc = pb.start();
        out = proc.getOutputStream();
        stdoutReader = reader(proc.getInputStream(), "bot-" + name, l -> lines.add(new Line(l, System.nanoTime())));
        if (captureStderr) stderrReader = reader(proc.getErrorStream(), "bot-" + name + "-stderr", l -> {
            synchronized (stderr) { stderr.add(l); }
        });
    }

    /** Removes the JVM option variables; Windows reads them whatever their case, so any case goes. */
    static void clearJvmOptions(Map<String, String> env) {
        env.keySet().removeIf(k -> JVM_OPTION_VARS.stream().anyMatch(k::equalsIgnoreCase));
    }

    private static Thread reader(InputStream in, String threadName, Consumer<String> sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
                String l;
                while ((l = r.readLine()) != null) sink.accept(l);
            } catch (IOException ignored) {
                // process ended
            }
        }, threadName);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Sends input text. Lines still unread were printed after the answer the Referee read; they stay
     * queued and are read as the next answer, as on CG, which reads a fixed number of lines per turn.
     * They are counted in {@link #extraLines}.
     */
    public void send(String text) throws IOException {
        extraLines = Math.max(extraLines, lines.size());
        try {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            // A dead process cannot read; the missing answer is reported as a timeout.
        }
    }

    /**
     * Next output line, or null if none arrived by the deadline. The reader thread stamps each
     * line on arrival, so a late line is refused even when it is already queued.
     */
    public String readLine(long deadlineNanos) throws InterruptedException {
        long wait = deadlineNanos - System.nanoTime();
        Line l = wait <= 0 ? lines.poll() : lines.poll(wait, TimeUnit.NANOSECONDS);
        if (l == null || l.nanos > deadlineNanos) {
            timedOut = true;
            if (l != null) lateLines++;
            return null;
        }
        lastArrival = l.nanos;
        return l.text;
    }

    /** Arrival time (System.nanoTime()) of the last line {@link #readLine} returned. */
    public long lastArrivalNanos() { return lastArrival; }

    /**
     * How far the program's output ran ahead of the answers the Referee read: the most lines left
     * unread when an input was sent or, unless the program missed a deadline, when it ended. 0 for a
     * program that keeps to the protocol. One extra line counts once, although the answers after it
     * stay shifted.
     */
    public int extraLines() { return extraLines; }

    /** Lines that arrived after a missed deadline: the late answer and anything printed after it. */
    public int lateLines() { return lateLines; }

    /**
     * Whether {@link #close} had to kill the program: it was still running after its grace period, so
     * the answer or report it had not printed yet is lost.
     */
    public boolean killedAtClose() { return killedAtClose; }

    /** The captured stderr lines; complete once {@link #close} has returned. */
    public List<String> stderr() {
        synchronized (stderr) { return new ArrayList<>(stderr); }
    }

    /**
     * Closes the program's input so that it can exit on its own and flush its output, kills it and
     * whatever it started if it still runs 500 ms later (2 s after a missed deadline, so that a late
     * program can still print its answer and report), then counts the output it left.
     */
    @Override
    public void close() {
        if (proc == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
            // the process is gone already
        }
        try {
            if (!proc.waitFor(timedOut ? 2000 : 500, TimeUnit.MILLISECONDS)) kill();
            if (stdoutReader != null) stdoutReader.join(1000);
            if (stderrReader != null) stderrReader.join(1000);
        } catch (InterruptedException e) {
            kill();
            Thread.currentThread().interrupt();
        }
        int left = lines.size();
        if (timedOut) lateLines += left;
        else extraLines = Math.max(extraLines, left);
        lines.clear();
        proc = null;
    }

    /** A wrapper's child (cmd /c java ...) would otherwise survive and keep the pipes open. */
    private void kill() {
        killedAtClose = true;
        proc.descendants().forEach(ProcessHandle::destroyForcibly);
        proc.destroyForcibly();
    }
}
