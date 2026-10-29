package cr.tools;

import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.referee.Follow;
import cr.referee.ProcessBot;
import cr.runner.ProcessCheck;
import cr.submit.Build;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks which code an arena agent ran: replays its CG games through {@link CgValidate} and, on the
 * turns whose state matched the replay, compares the bot's two lines with the agent's recorded
 * stdout ({@link Follow}). Usage: CgAttribute &lt;file|dir&gt; &lt;bot&gt; &lt;agentId|pseudo&gt;, where bot is
 * "submit" (build/submit/Player.java), "file:&lt;single-file Java Player&gt;" or a {@link BotFactory} spec.
 * A Java file is compiled from the bytes whose sha256 is printed, so the hash names the code that runs.
 * <p>
 * Per game: SAME (every recorded turn compared, no difference), DIFF (a difference, or no answer where
 * the agent answered), PARTIAL (no difference, but not every turn compared: an unvalidated state, a
 * missing record or an earlier simulated end), ERROR. Exit code 0 only when every game is SAME.
 * The build and config ids of a Java file and the build marker recorded on turn 0 of each game are
 * printed. A bot that prints a marker must repeat the recorded one; a bot with an empty turn-0 TRAIN
 * line (in-process, or an artifact built before the marker) is excused it ({@link Follow}). So SAME
 * alone does not name the build: the recorded markers do.
 */
public final class CgAttribute {
    /** Reading deadline per turn for process bots: identity, not timing, is checked here. */
    static final long TURN_MS = 10_000;

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || args[0].isEmpty() || args[1].isEmpty() || args[2].isEmpty()) {
            System.err.println("usage: CgAttribute <replay file|dir> <bot: submit|file:<Player.java>|spec> <agentId|pseudo>");
            System.exit(2);
        }
        System.exit(run(Paths.get(args[0]), args[1], args[2], System.out));
    }

    static int run(Path root, String spec, String who, PrintStream out) throws Exception {
        List<Path> files;
        try (Stream<Path> st = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
            files = st.filter(p -> p.getFileName().toString().matches("\\d+\\.json")).sorted().collect(Collectors.toList());
        }
        Path tmp = Files.createTempDirectory("cgattribute");
        try {
            Supplier<Bot> bots = bots(spec, tmp, out);
            int same = 0, differ = 0, partial = 0, failed = 0;
            long turns = 0;
            Set<Long> agents = new TreeSet<>();
            Map<String, Integer> markers = new TreeMap<>();
            for (Path f : files) {
                String id = f.getFileName().toString().replace(".json", ""), marker = null;
                try {
                    CgReplay g = CgReplay.load(f);
                    int seat = seatOf(g, who);
                    if (seat < 0) { failed++; out.println("ERROR " + id + ": " + who + " did not play"); continue; }
                    agents.add(g.agentId[seat]);
                    List<List<String[]>> rec = recorded(g);
                    // From the record itself, so a game that fails later still reports what it printed.
                    marker = rec.get(seat).isEmpty() ? null : Build.decode(rec.get(seat).get(0)[1]);
                    markers.merge(marker == null ? "none" : marker, 1, Integer::sum);
                    int n = g.turns.size(), valid = validatedTurns(CgValidate.validate(g), n);
                    Follow.Result r = Follow.follow(g.seed, seat, rec, bots.get(), valid, TURN_MS);
                    turns += r.turns;
                    String v = verdict(r, n);
                    if (v.equals("SAME")) same++;
                    else if (v.equals("DIFF")) differ++;
                    else partial++;
                    out.println(v + " " + id + " seat " + seat + " vs " + g.pseudo[1 - seat] + ": compared " + r.turns + " of " + n
                        + " turns (validated " + valid + "), diffs " + r.diffs
                        + (r.firstDiff >= 0 ? ", first at turn " + r.firstDiff + ": recorded [" + r.expected + "] bot [" + r.got + "]" : "")
                        + (r.stopped != null ? "; stopped at " + r.stopped : "")
                        + (marker != null ? "; marker " + marker : ""));
                } catch (Exception e) {
                    failed++;
                    out.println("ERROR " + id + ": " + e + (marker != null ? "; marker " + marker : ""));
                }
            }
            if (agents.size() > 1) {
                failed++;
                out.println("ERROR " + who + " matched several agents " + agents + ": give an agentId");
            }
            out.printf("attribution of %s to %s: games=%d same=%d differ=%d partial=%d error=%d comparedTurns=%d markers=%s%n",
                who, spec, files.size(), same, differ, partial, failed, turns, markers);
            return differ + partial + failed > 0 || files.isEmpty() ? 1 : 0;
        } finally {
            try (Stream<Path> st = Files.walk(tmp)) {
                for (Path p : st.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.deleteIfExists(p);
            } catch (IOException e) {
                out.println("could not delete " + tmp + ": " + e);
            }
        }
    }

    /**
     * New bot instances for the spec. A Java file is compiled once into tmp from the bytes it hashes and
     * runs as a process; other specs go to {@link BotFactory}, which refuses parameters for programs
     * whose parameters are fixed when they are built. Prints what runs.
     */
    static Supplier<Bot> bots(String spec, Path tmp, PrintStream out) throws Exception {
        if (!spec.equals("submit") && !spec.startsWith("file:")) {
            BotFactory.create(spec).name();
            out.println("bot " + spec);
            return () -> BotFactory.create(spec);
        }
        Path src = Paths.get(spec.equals("submit") ? "build/submit/Player.java" : spec.substring(5));
        byte[] code = Files.readAllBytes(src);
        String text = new String(code, java.nio.charset.StandardCharsets.UTF_8), params;
        try {
            params = "params=\"" + ProcessCheck.artifactParams(text) + "\"";
        } catch (IllegalStateException e) {
            params = "no PARAMS literal";
        }
        out.println("bot " + spec + ": " + src.toString().replace('\\', '/') + " sha256="
            + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code)) + " " + params + " build=" + Build.describe(text)
            + " config=" + Build.describeConfig(text));
        Path java = tmp.resolve("Player.java"), classes = tmp.resolve("classes");
        Files.createDirectories(classes);
        Files.write(java, code);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc.run(null, null, null, "-d", classes.toString(), "-encoding", "UTF-8", java.toString()) != 0)
            throw new IllegalStateException(src + " does not compile");
        return () -> new ProcessBot(spec, List.of("java", "-cp", classes.toString(), "Player"), false);
    }

    /** SAME needs every recorded turn compared without a difference; a missing answer is a difference. */
    static String verdict(Follow.Result r, int n) {
        if (r.diffs > 0 || r.stop == Follow.Stop.NO_ANSWER) return "DIFF";
        return n > 0 && r.turns == n && r.stop == Follow.Stop.NONE ? "SAME" : "PARTIAL";
    }

    /** Seat of the agent, matched by agent id when who is a number, otherwise by pseudo; -1 if absent. */
    static int seatOf(CgReplay g, String who) {
        for (int p = 0; p < 2; p++)
            if (who.matches("\\d+") ? g.agentId[p] == Long.parseLong(who) : who.equals(g.pseudo[p])) return p;
        return -1;
    }

    /**
     * Turns whose input state CgValidate checked against the replay: all when the game matched, none on
     * a map or read error, otherwise those before the first mismatching state (an end-of-game mismatch,
     * such as the winner of a tie, keeps every turn). The check covers gold, queen health, sites and
     * units; owners, barracks countdowns, maxMineSize, gold visibility and touchedSite are derived from
     * that state, so an error in them could only turn a SAME into a false DIFF.
     */
    static int validatedTurns(CgValidate.Result v, int n) {
        if (v.error != null) return 0;
        return v.mismatches.isEmpty() ? n : Math.min(v.firstBadTurn, n);
    }

    /** Each player's two lines per turn; a line that is not there (no output, or an answer cut short) is null. */
    static List<List<String[]>> recorded(CgReplay g) {
        List<List<String[]>> rec = List.of(new ArrayList<>(), new ArrayList<>());
        for (CgReplay.Turn t : g.turns)
            for (int p = 0; p < 2; p++) rec.get(p).add(lines(t.stdout[p]));
        return rec;
    }

    /** "A\nB\n" -> {A, B}; "A\n" has no second line (split gives {"A", ""}), unlike "A\n\n" with an empty one. */
    static String[] lines(String stdout) {
        if (stdout == null) return new String[]{null, null};
        String[] ls = stdout.split("\n", -1);
        return new String[]{ls[0], ls.length > 2 || ls.length == 2 && !ls[1].isEmpty() ? ls[1] : null};
    }
}
