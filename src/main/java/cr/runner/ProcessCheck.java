package cr.runner;

import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.referee.ProcessBot;
import cr.referee.Referee;
import cr.submit.Build;
import cr.submit.CGRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Runs the merged build/submit/Player as a real process over the protocol on both seats
 * against an opponent spec. No timeout grace: a late answer is a timeout (kill). Each game is
 * replayed with the in-process "current" bot (with the artifact's PARAMS) in place of the process,
 * and every command line of both players must be the same, except that the artifact prints its build
 * marker where current's turn-0 TRAIN line is empty (it must print exactly that marker, see
 * CGRunner.marker); a game where the opponent was killed
 * (timeout or invalid output) in either run cannot be compared and fails the check as well. So does
 * any extra output line and any stderr line other than the runner's SLOW report (a stack trace from
 * its fallback, say).
 * This shows that packaging leaves the source bot's play unchanged on the positions the games
 * reach; it does not test the bot's logic. The artifact's sha256, PARAMS, build id (checked against
 * its content) and config id are printed first, and a
 * game with a timeout prints a command that rebuilds the same artifact and replays that seed.
 * Usage: ProcessCheck opponentSpec games seedBase
 */
public final class ProcessCheck {
    public static void main(String[] args) throws Exception {
        String opp = args[0];
        int games = Integer.parseInt(args[1]);
        if (games < 1) throw new IllegalArgumentException("games must be at least 1");
        long seedBase = Long.parseLong(args[2]);
        byte[] artifact = Files.readAllBytes(Paths.get("build/submit/Player.java"));
        String text = new String(artifact, StandardCharsets.US_ASCII);
        String params = artifactParams(text), build = Build.of(text);
        String current = params.isEmpty() ? "current" : "current?" + params;
        System.out.println("artifact build/submit/Player.java sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact))
            + " params=\"" + params + "\" build=" + (build.isEmpty() ? "none" : build) + " config=" + Build.describeConfig(text));
        String rerun = rerunCommand(params, opp);
        Tally t = new Tally();
        int stderrShown = 0;
        for (int g = 0; g < games; g++) {
            for (int seat = 0; seat < 2; seat++) {
                long seed = seedBase + g;
                ProcessBot sub = new ProcessBot("submit", List.of("java", "-cp", "build/submit/classes", "Player"), true);
                Referee.Result r = play(seed, seat, sub, BotFactory.create(opp));
                Referee.Result ref = play(seed, seat, BotFactory.create(current), BotFactory.create(opp));
                String marker = CGRunner.marker(build, r.sites.size());
                Game v = judge(r, ref, seat, marker, sub.lateLines(), sub.killedAtClose(), sub.stderr());
                String at = "seed " + seed + " seat " + seat;
                for (String n : v.notes)
                    if (!n.startsWith("stderr: ") || stderrShown++ < 20) System.out.println(at + " " + n);
                if (v.anyTimeouts > 0) System.out.println(at + " rerun: " + rerun + " -Pgames=1 -PseedBase=" + seed);
                t.add(v);
            }
        }
        System.out.printf(java.util.Locale.ROOT,
            "procCheck vs %s: games=%d wins=%d timeouts=%d kills=%d sameAsCurrent=%d/%d notCompared=%d extraLines=%d lateLines=%d"
                + " stderrLines=%d slowLines=%d minTurns=%d maxFirst=%.1fms maxLater=%.1fms%n",
            opp, t.total, t.wins, t.timeouts, t.kills, t.same, t.total, t.notCompared, t.extra, t.late,
            t.stderrLines, t.slowLines, t.minTurns, t.maxFirst / 1e6, t.maxLater / 1e6);
        if (!t.passed()) System.exit(1);
    }

    private static Referee.Result play(long seed, int seat, Bot me, Bot opp) {
        return seat == 0 ? Referee.play(seed, me, opp, true, 50) : Referee.play(seed, opp, me, true, 50);
    }

    /** The procCheck command up to the seed: submit runs first, so it must get the same params. */
    static String rerunCommand(String params, String opp) {
        return "./gradlew procCheck" + (params.isEmpty() ? "" : " " + quoted("-Pparams=" + params)) + " " + quoted("-Popp=" + opp);
    }

    private static String quoted(String arg) {
        return arg.contains(" ") ? "\"" + arg + "\"" : arg;
    }

    /** What one game says about the artifact: its counts and the lines to print. */
    static final class Game {
        final List<String> notes = new ArrayList<>();
        boolean win, compared, same;
        int timeouts, kills, extraLines, lateLines, stderrLines, slowLines, turns;
        /** Timeouts of either player in either run: a machine stall can cause them, so a rerun is printed. */
        int anyTimeouts;
        long maxFirst, maxLater;
    }

    /**
     * Judges the artifact's game r (artifact in seat) against the replay ref with in-process current.
     * marker: the TRAIN line the artifact prints on turn 0 in place of an empty one (null: none).
     * Only SLOW reports are allowed on stderr; the first 20 other lines of the game become notes.
     * killedAtClose: the artifact had to be killed after the game (see ProcessBot.killedAtClose).
     */
    static Game judge(Referee.Result r, Referee.Result ref, int seat, String marker, int lateLines, boolean killedAtClose, List<String> stderr) {
        Game v = new Game();
        int o = 1 - seat;
        v.win = r.winner == seat;
        v.turns = r.turns;
        v.timeouts = r.timeouts[seat];
        v.anyTimeouts = r.timeouts[seat] + r.timeouts[o] + ref.timeouts[o];
        v.maxFirst = r.maxFirstNanos[seat];
        v.maxLater = r.maxNanos[seat];
        v.extraLines = r.extraLines[seat];
        v.lateLines = lateLines;
        List<String> slow = new ArrayList<>();
        for (String l : stderr) {
            if (l.startsWith("SLOW turn ")) slow.add(l);
            else if (v.stderrLines++ < 20) v.notes.add("stderr: " + l);
        }
        v.slowLines = slow.size();
        if (r.error[seat] != null) {
            v.kills = 1;
            String note = "killed on turn " + (r.turns - 1) + ": " + r.error[seat];
            if (v.timeouts > 0) note += "; " + timeoutNote(r.turns - 1, lateLines, killedAtClose, slow);
            v.notes.add(note);
        }
        if (v.extraLines > 0) v.notes.add(v.extraLines + " extra output line(s)");
        if (ref.error[seat] != null) v.notes.add("in-process current killed on turn " + (ref.turns - 1) + ": " + ref.error[seat]);
        if (r.error[o] != null || ref.error[o] != null) {
            v.notes.add("not compared: the opponent was killed (game with the artifact: " + killNote(r, o)
                + "; game with current: " + killNote(ref, o) + ")");
            return v;
        }
        v.compared = true;
        List<String> expected = withMarker(ref.replay, seat, marker);
        int d = firstDifference(r.replay, expected);
        v.same = d < 0;
        if (!v.same)
            v.notes.add("differs from current at line " + d + ": " + (d < r.replay.size() ? r.replay.get(d) : "<end>")
                + " vs " + (d < expected.size() ? expected.get(d) : "<end>"));
        return v;
    }

    /**
     * The lines the artifact in seat must produce where current produced replay: the same, with the
     * marker in place of an empty TRAIN on turn 0 (the seat's line comes first for player 0, second for 1).
     */
    static List<String> withMarker(List<String> replay, int seat, String marker) {
        if (marker == null || replay.size() <= seat) return replay;
        String line = replay.get(seat), empty = " | TRAIN";
        if (!line.startsWith(seat + " ") || !line.endsWith(empty)) return replay;
        List<String> lines = new ArrayList<>(replay);
        lines.set(seat, line.substring(0, line.length() - "TRAIN".length()) + marker);
        return lines;
    }

    /**
     * What the artifact saw of its missed deadline. With no SLOW report from a program that ran to the
     * end, the time went outside its own turn timing (which on turn 0 starts at its first input token,
     * after JVM start and init); a program killed at close could not report, so nothing follows.
     */
    private static String timeoutNote(int turn, int lateLines, boolean killedAtClose, List<String> slow) {
        String note = lateLines + " line(s) arrived after the deadline; ";
        if (killedAtClose) return note + "the artifact was still running at close and was killed before it could answer or report: no conclusion";
        note += "the artifact's own timing: " + (slow.isEmpty() ? "no SLOW report" : String.join(", ", slow));
        return turn == 0 ? note + " (its turn-0 clock starts at its first input token, after JVM start and init)" : note;
    }

    private static String killNote(Referee.Result r, int p) {
        return r.error[p] == null ? "not killed" : r.error[p] + " on turn " + (r.turns - 1);
    }

    /** The summary over all games and the verdict. */
    static final class Tally {
        int total, wins, timeouts, kills, same, notCompared, extra, late, stderrLines, slowLines;
        int minTurns = Integer.MAX_VALUE;
        long maxFirst, maxLater;

        void add(Game v) {
            total++;
            if (v.win) wins++;
            timeouts += v.timeouts;
            kills += v.kills;
            if (!v.compared) notCompared++;
            if (v.same) same++;
            extra += v.extraLines;
            late += v.lateLines;
            stderrLines += v.stderrLines;
            slowLines += v.slowLines;
            minTurns = Math.min(minTurns, v.turns);
            maxFirst = Math.max(maxFirst, v.maxFirst);
            maxLater = Math.max(maxLater, v.maxLater);
        }

        /** Every game compared and the same, and no timeout, kill, extra line or stderr line other than SLOW. */
        boolean passed() {
            return total > 0 && same == total && timeouts == 0 && kills == 0 && extra == 0 && stderrLines == 0;
        }
    }

    /** The value of the single CGRunner.PARAMS literal of a merged source ("" for the defaults). */
    public static String artifactParams(String src) {
        return Build.params(src);
    }

    /** Index of the first differing line, or -1 if the lists are equal. */
    static int firstDifference(List<String> a, List<String> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) if (!a.get(i).equals(b.get(i))) return i;
        return a.size() == b.size() ? -1 : n;
    }
}
