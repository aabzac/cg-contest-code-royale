package cr.referee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessBotTest {
    private static final long GENEROUS = 20_000_000_000L;

    @Test
    void lateLineIsRefusedEvenWhenAlreadyQueued() throws Exception {
        ProcessBot b = echo(false);
        try {
            answer(b, "0");
            long t0 = System.nanoTime();
            b.send("300\n");
            Thread.sleep(1500);
            assertNull(b.readLine(t0 + 100_000_000L), "answered after 300 ms, deadline 100 ms");
        } finally {
            b.close();
        }
        assertEquals(2, b.lateLines(), "WAIT, refused when it was taken, and TRAIN, left at close");
    }

    @Test
    void arrivalIsStampedWhenTheLineArrivesNotWhenItIsTaken() throws Exception {
        try (ProcessBot b = echo(false)) {
            answer(b, "0");
            long t0 = System.nanoTime();
            b.send("0\n");
            Thread.sleep(1000);
            assertEquals("WAIT", b.readLine(t0 + GENEROUS));
            assertEquals("TRAIN", b.readLine(t0 + GENEROUS));
            long ms = (b.lastArrivalNanos() - t0) / 1_000_000L;
            assertTrue(ms < 500, "answer stamped " + ms + " ms after the input; it was taken after 1000 ms");
        }
    }

    /** As on CG, a leftover line is read as the next answer, which shifts the answers after it. */
    @Test
    void anExtraLineBecomesTheNextAnswerAndIsCountedOnce() throws Exception {
        ProcessBot b = echo(true);
        try {
            answer(b, "0 err");
            answer(b, "0 extra");
            Thread.sleep(300);
            long t0 = System.nanoTime();
            b.send("0\n");
            assertEquals("EXTRA", b.readLine(t0 + GENEROUS));
            assertEquals("WAIT", b.readLine(t0 + GENEROUS));
            assertEquals(1, b.extraLines(), "EXTRA, unread when the input was sent");
            Thread.sleep(300);
            t0 = System.nanoTime();
            b.send("0\n");
            assertEquals("TRAIN", b.readLine(t0 + GENEROUS), "the answers stay shifted");
            assertEquals("WAIT", b.readLine(t0 + GENEROUS));
            assertEquals(1, b.extraLines(), "the shifted TRAIN, unread at this input, is the same extra line");
        } finally {
            b.close();
        }
        assertEquals(1, b.extraLines(), "and so is the TRAIN left at close");
        assertEquals(0, b.lateLines());
        assertEquals(List.of("boom"), b.stderr());
    }

    @Test
    void linesAfterAMissedDeadlineAreLateNotExtra() throws Exception {
        ProcessBot b = echo(false);
        try {
            answer(b, "0");
            long t0 = System.nanoTime();
            b.send("300\n");
            assertNull(b.readLine(t0 + 100_000_000L), "answered after 300 ms, deadline 100 ms");
        } finally {
            b.close();
        }
        assertEquals(2, b.lateLines(), "WAIT and TRAIN, printed 300 ms after the input");
        assertEquals(0, b.extraLines());
        assertFalse(b.killedAtClose(), "it answered within the grace period and exited");
    }

    /** After a missed deadline close() waits longer, so a late program still prints its answer and report. */
    @Test
    void aLateProgramGetsTimeToAnswerAndReportAtClose() throws Exception {
        ProcessBot b = echo(true);
        try {
            answer(b, "0");
            long t0 = System.nanoTime();
            b.send("1000 err\n");
            assertNull(b.readLine(t0 + 100_000_000L), "answers after 1000 ms, deadline 100 ms");
        } finally {
            b.close();
        }
        assertFalse(b.killedAtClose());
        assertEquals(2, b.lateLines());
        assertEquals(List.of("boom"), b.stderr(), "what it wrote before its late answer");
    }

    /** close() ends the input and waits: what the program writes on its way out is still collected. */
    @Test
    void outputAfterEndOfInputIsCollected() throws Exception {
        ProcessBot b = echo(true);
        try {
            answer(b, "0 bye");
        } finally {
            b.close();
        }
        assertEquals(List.of("bye"), b.stderr());
        assertEquals(1, b.extraLines(), "BYE, printed after end of input");
        assertFalse(b.killedAtClose());
    }

    @Test
    void closeKillsAProgramThatIgnoresEndOfInput() throws Exception {
        ProcessBot b = echo(false);
        ProcessHandle program;
        try {
            long t0 = System.nanoTime();
            b.send("0 pid hang\n");
            program = ProcessHandle.of(Long.parseLong(b.readLine(t0 + GENEROUS))).orElseThrow();
            assertEquals("TRAIN", b.readLine(t0 + GENEROUS));
        } finally {
            b.close();
        }
        try {
            program.onExit().get(10, TimeUnit.SECONDS);
        } finally {
            program.destroyForcibly();
        }
        assertTrue(b.killedAtClose());
    }

    @Test
    void closeKillsWhatAWrapperStarted() throws Exception {
        ProcessBot b = echo(false, "wrap");
        ProcessHandle child;
        try {
            long t0 = System.nanoTime();
            b.send("0 pid hang\n");
            child = ProcessHandle.of(Long.parseLong(b.readLine(t0 + GENEROUS))).orElseThrow();
            assertEquals("TRAIN", b.readLine(t0 + GENEROUS));
        } finally {
            b.close();
        }
        try {
            child.onExit().get(10, TimeUnit.SECONDS);
        } finally {
            child.destroyForcibly();
        }
        assertTrue(b.killedAtClose());
    }

    @Test
    void jvmOptionVariablesAreClearedInAnyCase() {
        Map<String, String> env = new HashMap<>(Map.of("JDK_JAVA_OPTIONS", "a", "java_tool_options", "b", "_Java_Options", "c", "PATH", "d", "JAVA_HOME", "e"));
        ProcessBot.clearJvmOptions(env);
        assertEquals(Set.of("PATH", "JAVA_HOME"), env.keySet());
    }

    /** End to end: a helper JVM that sees the variables starts a program through ProcessBot. */
    @Test
    void jvmOptionVariablesDoNotReachTheProgram(@TempDir Path tmp) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), EnvCheck.class.getName());
        pb.environment().put("JDK_JAVA_OPTIONS", "-Xshare:auto");
        pb.environment().put("Java_Tool_Options", "-Xshare:auto");
        pb.redirectError(tmp.resolve("err.txt").toFile());
        Process p = pb.start();
        String programStderr = new String(p.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String helperStderr = Files.readString(tmp.resolve("err.txt"), StandardCharsets.US_ASCII);
        assertTrue(helperStderr.contains("Picked up JDK_JAVA_OPTIONS"), "the helper itself saw the variables: " + helperStderr);
        assertEquals("", programStderr.trim(), "the program's stderr");
    }

    private static ProcessBot echo(boolean captureStderr, String... args) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Path classes = Paths.get(DelayedEcho.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(java, "-cp", classes.toString(), DelayedEcho.class.getName()));
        command.addAll(List.of(args));
        ProcessBot b = new ProcessBot("echo", command, captureStderr);
        b.start();
        return b;
    }

    /** One exchange with a generous deadline (the first one includes the JVM start). */
    private static void answer(ProcessBot b, String input) throws Exception {
        long t0 = System.nanoTime();
        b.send(input + "\n");
        assertEquals("WAIT", b.readLine(t0 + GENEROUS));
        assertEquals("TRAIN", b.readLine(t0 + GENEROUS));
    }
}
