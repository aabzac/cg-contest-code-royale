package cr.runner;

import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.referee.ProcessBot;
import cr.referee.Referee;
import cr.submit.Build;
import cr.submit.CGRunner;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Does an artifact file play as a frozen version? The file is compiled from its bytes and runs as a
 * process on both seats of a few maps against the in-process version; each game is compared with the
 * version's mirror game on that map, and every command line of both players must be the same (the
 * artifact's build marker in place of an empty turn-0 TRAIN, as in {@link ProcessCheck}).
 *
 * The maps are chosen to tell the version from every other frozen version of this checkout: for
 * each of them some map is in the set on which that version, in the file's place, plays another
 * game. Maps are added until that holds (versions that differ on few maps need a search); a version
 * no map among the first {@link #SCAN} tells apart is reported in notToldFrom. So a file that passes
 * is not a build of another frozen version, whichever one it is.
 *
 * The time budgets are long: this compares commands, not time, so a slow machine does not turn a file
 * into another version. A game cut short by a timeout or a crash is inconclusive, not different. The
 * first different command ends the check.
 *
 * tools/arena.py runs it (gradle playsAs -Pfile=... -Pas=v14) to find the file of a version that was
 * never uploaded and to refuse a file that is another version's. Prints one JSON line: file, sha256,
 * as, known (false: this checkout has no such frozen version), games, same, different, inconclusive,
 * first (the first difference or the first inconclusive game, null without one), toldFrom (how many
 * other versions the maps tell it from) and notToldFrom (those they do not).
 * Exit code 0 when every game is the same, 1 otherwise, 2 for a usage error. Not shipped.
 *
 * It shows the play on the positions these games reach, as procCheck does for packaging: a file whose
 * code differs from the version's only on positions no game here reaches passes.
 */
public final class PlaysAs {
    static final int DEFAULT_MAPS = 3;
    /** How many maps are searched for one that tells two versions apart. */
    static final int SCAN = 150;
    static final long TURN_MS = 5_000, FIRST_MS = 20_000;

    enum Verdict { SAME, DIFFERENT, INCONCLUSIVE }

    private PlaysAs() {}

    /** Map k: seed 1 + 397 k. Consecutive seeds share the queens' starting HP (see MapGen), these do not. */
    static long seed(int k) {
        return 1 + 397L * k;
    }

    /** A frozen in-process version of this checkout: v + two digits that {@link BotFactory} knows. */
    static boolean known(String spec) {
        if (!spec.matches("v\\d\\d")) return false;
        try {
            return !(BotFactory.create(spec) instanceof ProcessBot);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    /** Every frozen version of this checkout, in order. */
    static List<String> versions() {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String spec = String.format("v%02d", i);
            if (known(spec)) all.add(spec);
        }
        return all;
    }

    /** The maps to play, the spec's mirror game on each, and the versions the maps do not tell the spec from. */
    static final class Plan {
        final List<Long> seeds = new ArrayList<>();
        final Map<Long, List<String>> mirror = new HashMap<>();
        final List<String> notTold = new ArrayList<>();
        int told;
    }

    /**
     * The first maps, and for every other version a map on which it plays another game than the spec:
     * one of those if there is one, else the first such map among the first SCAN, added to the set.
     */
    static Plan plan(String spec, int maps, List<String> others) {
        return plan(spec, maps, others, SCAN);
    }

    static Plan plan(String spec, int maps, List<String> others, int scan) {
        Plan p = new Plan();
        for (int k = 1; k <= maps; k++) p.seeds.add(seed(k));
        for (String other : others) {
            boolean told = false;
            for (long s : p.seeds) {
                if (differs(other, spec, s, p.mirror)) {
                    told = true;
                    break;
                }
            }
            for (int k = maps + 1; !told && k <= scan; k++) {
                if (p.seeds.contains(seed(k)) || !differs(other, spec, seed(k), p.mirror)) continue;
                p.seeds.add(seed(k));
                told = true;
            }
            if (told) p.told++;
            else p.notTold.add(other);
        }
        for (long s : p.seeds) mirror(spec, s, p.mirror);
        return p;
    }

    /** The spec's mirror game on the map: what a file of the spec plays in either seat against the spec. */
    private static List<String> mirror(String spec, long seed, Map<Long, List<String>> cache) {
        return cache.computeIfAbsent(seed, s -> lines(s, 0, BotFactory.create(spec), spec));
    }

    /** Whether the other version, in either seat against the spec, plays another game than the spec's mirror. */
    static boolean differs(String other, String spec, long seed, Map<Long, List<String>> cache) {
        List<String> expected = mirror(spec, seed, cache);
        for (int seat = 0; seat < 2; seat++)
            if (!lines(seed, seat, BotFactory.create(other), spec).equals(expected)) return true;
        return false;
    }

    /** The command lines of the game with me in the seat against the in-process spec. */
    private static List<String> lines(long seed, int seat, Bot me, String spec) {
        Bot opp = BotFactory.create(spec);
        return (seat == 0 ? Referee.play(seed, me, opp, true, TURN_MS, FIRST_MS) : Referee.play(seed, opp, me, true, TURN_MS, FIRST_MS)).replay;
    }

    /**
     * The verdict of one game: the artifact's lines against the lines expected of the version. A
     * different command is a difference; a missing answer (the referee writes none) or a game that
     * ends early without a different command is inconclusive: the process timed out or died.
     */
    static Verdict verdict(List<String> played, List<String> expected) {
        int d = ProcessCheck.firstDifference(played, expected);
        if (d < 0) return Verdict.SAME;
        if (d >= played.size() || d >= expected.size()) return Verdict.INCONCLUSIVE;
        return played.get(d).endsWith(" <none>") || expected.get(d).endsWith(" <none>") ? Verdict.INCONCLUSIVE : Verdict.DIFFERENT;
    }

    /** "seed 398 seat 0 line 45: 0 BUILD 3 TOWER | TRAIN, the version: 0 MOVE 1 2 | TRAIN". */
    static String describe(long seed, int seat, List<String> played, List<String> expected) {
        int d = ProcessCheck.firstDifference(played, expected);
        return "seed " + seed + " seat " + seat + " line " + d + ": " + (d < played.size() ? played.get(d) : "<end>")
            + ", the version: " + (d < expected.size() ? expected.get(d) : "<end>");
    }

    static String json(String file, String sha, String spec, boolean known, int games, int same, int different,
                       int inconclusive, String first, int told, List<String> notTold) {
        return "{\"file\": \"" + escape(file) + "\", \"sha256\": \"" + sha + "\", \"as\": \"" + escape(spec) + "\", \"known\": " + known
            + ", \"games\": " + games + ", \"same\": " + same + ", \"different\": " + different + ", \"inconclusive\": " + inconclusive
            + ", \"first\": " + (first == null ? "null" : "\"" + escape(first) + "\"") + ", \"toldFrom\": " + told
            + ", \"notToldFrom\": [" + notTold.stream().map(v -> "\"" + escape(v) + "\"").collect(Collectors.joining(", ")) + "]}";
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args[0].isEmpty() || args[1].isEmpty()) {
            System.err.println("usage: gradlew playsAs -Pfile=<Player.java> -Pas=<version, e.g. v14> [-Pmaps=3]");
            System.exit(2);
        }
        String spec = args[1];
        int maps = args.length > 2 && !args[2].isEmpty() ? Integer.parseInt(args[2]) : DEFAULT_MAPS;
        if (maps < 1 || maps > SCAN) throw new IllegalArgumentException("maps must be 1.." + SCAN);
        Path file = Path.of(args[0]);
        byte[] code = Files.readAllBytes(file);
        String name = file.toString().replace('\\', '/');
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code));
        if (!known(spec)) {
            System.out.println(json(name, sha, spec, false, 0, 0, 0, 0, null, 0, List.of()));
            System.exit(1);
        }
        String build;
        try {
            build = Build.of(new String(code, StandardCharsets.UTF_8));
        } catch (IllegalStateException e) {
            build = "";  // not a build of this project's merger: no marker to allow for
        }
        List<String> others = versions();
        others.remove(spec);
        Plan plan = plan(spec, maps, others);
        Path tmp = Files.createTempDirectory("plays-as");
        int games = 0, same = 0, different = 0, inconclusive = 0;
        String first = null;
        try {
            Path java = tmp.resolve("Player.java"), classes = tmp.resolve("classes");
            Files.createDirectories(classes);
            Files.write(java, code);
            JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
            if (jc.run(null, null, null, "-d", classes.toString(), "-encoding", "UTF-8", java.toString()) != 0)
                throw new IllegalStateException(name + " does not compile");
            List<String> command = List.of("java", "-cp", classes.toString(), "Player");
            play:
            for (long seed : plan.seeds) {
                List<String> mirror = plan.mirror.get(seed);
                for (int seat = 0; seat < 2; seat++) {
                    List<String> played = lines(seed, seat, new ProcessBot("artifact", command, false), spec);
                    List<String> expected = ProcessCheck.withMarker(mirror, seat, CGRunner.marker(build, sites(seed)));
                    Verdict v = verdict(played, expected);
                    games++;
                    if (v == Verdict.SAME) {
                        same++;
                        continue;
                    }
                    if (first == null) first = describe(seed, seat, played, expected);
                    if (v == Verdict.DIFFERENT) {
                        different++;
                        break play;
                    }
                    inconclusive++;
                }
            }
        } finally {
            try (Stream<Path> st = Files.walk(tmp)) {
                for (Path p : st.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.deleteIfExists(p);
            } catch (IOException e) {
                System.err.println("could not delete " + tmp + ": " + e);
            }
        }
        System.out.println(json(name, sha, spec, true, games, same, different, inconclusive, first, plan.told, plan.notTold));
        if (same != games) System.exit(1);
    }

    /** The number of sites of the map: the marker needs 16 (see {@link CGRunner#marker}). */
    private static int sites(long seed) {
        return cr.core.MapGen.generate(seed, 4).nSites;
    }
}
