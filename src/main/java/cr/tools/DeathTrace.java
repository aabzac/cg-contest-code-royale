package cr.tools;

import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Physics;
import cr.core.Rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Queen trace of a recorded CG game, replayed through {@link Rules} with the recorded commands:
 * one line per turn for the given agent's seat, from the first turn its queen loses HP (or from
 * turn 0 with "all") to its death or the end. Columns: the turn, the queen's HP and its loss on the
 * turn, the gold, her position, the command, the touched site, own towers and the distance to the
 * nearest, enemy knights within 250 and 500 and the nearest one's distance, enemy archers within
 * 300, enemy towers whose range covers the queen, and the structure counts. For the study of the
 * queen's safety (docs/data/study-2026-11-03-queen-safety).
 * The last two columns (since 2026-11-05, appended so that the older study parsers still match) split
 * the turn's loss by source, each from a probe copy of the turn: {@code kd}, the loss with the enemy
 * towers' ranges set to 0 (the knights alone), and {@code td}, the loss with the enemy knights
 * removed (the towers alone). Only knights and towers damage a queen, so kd + td equals the loss
 * unless removing one source moves the other: a knight's push shifting the queen across a tower's
 * range edge, or an enemy top-up restoring a zeroed range on that turn.
 * With "foe" the trace follows the other seat of the agent's games (its opponents' queens); the header
 * ends with the traced agent's id.
 * Usage: DeathTrace &lt;agentId&gt; [all] [foe] &lt;replay files or directories...&gt;
 */
public final class DeathTrace {
    public static void main(String[] args) throws IOException {
        long agent = Long.parseLong(args[0]);
        int from = 1;
        boolean all = false, foe = false;
        for (; from < args.length; from++) {
            if (args[from].equals("all")) all = true;
            else if (args[from].equals("foe")) foe = true;
            else break;
        }
        final boolean allTurns = all, opponent = foe;
        for (int i = from; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            if (Files.isDirectory(p)) try (Stream<Path> s = Files.walk(p)) { s.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> trace(f, agent, allTurns, opponent)); }
            else trace(p, agent, allTurns, opponent);
        }
    }

    static void trace(Path file, long agent, boolean all, boolean opponent) {
        CgReplay r;
        try { r = CgReplay.load(file); } catch (IOException | RuntimeException e) { System.out.println("skip " + file + ": " + e.getMessage()); return; }
        int seat = r.agentId[0] == agent ? 0 : r.agentId[1] == agent ? 1 : -1;
        if (seat < 0) return;
        int me = opponent ? 1 - seat : seat;
        int foe = 1 - me;
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        System.out.printf(Locale.ROOT, "=== game %d seat %d vs %s (ranks %d/%d, scores %.0f/%.0f, %d turns) agent %d%n", r.gameId, me, r.pseudo[foe], r.ranks[me], r.ranks[foe], r.scores[me], r.scores[foe], r.turns.size(), r.agentId[me]);
        int prevHp = s.hp[me];
        boolean started = all;
        for (int t = 0; t < r.turns.size() && !s.over; t++) {
            String[] a = CgReplay.lines(r.turns.get(t).stdout[0]), b = CgReplay.lines(r.turns.get(t).stdout[1]);
            Command[] cmd = {Command.parse(a[0], a[1], s.nSites), Command.parse(b[0], b[1], s.nSites)};
            String line = describe(s, me, foe, t, cmd[me]);
            GameState knightsOnly = s.copy(), towersOnly = s.copy();
            for (int i = 0; i < s.nSites; i++)
                if (knightsOnly.stType[i] == Const.TOWER && knightsOnly.stOwner[i] == foe) knightsOnly.towerRange[i] = 0;
            for (int i = 0; i < towersOnly.nCreeps; i++)
                if (towersOnly.cOwner[i] == foe && towersOnly.cType[i] == Const.KNIGHT) towersOnly.cHp[i] = 0;
            towersOnly.removeDeadCreeps();
            new Rules().resolve(knightsOnly, cmd[0], cmd[1]);
            new Rules().resolve(towersOnly, cmd[0], cmd[1]);
            rules.resolve(s, cmd[0], cmd[1]);
            int loss = prevHp - s.hp[me];
            if (!started && loss > 0) started = true;
            if (started) System.out.printf(Locale.ROOT, "%s hp %3d (-%d) | kd %d td %d%n", line, s.hp[me], loss,
                prevHp - knightsOnly.hp[me], prevHp - towersOnly.hp[me]);
            prevHp = s.hp[me];
            if (s.hp[me] <= 0) { System.out.println("    queen dead on turn " + (t + 1)); break; }
        }
    }

    static String describe(GameState s, int me, int foe, int t, Command c) {
        double qx = s.qx[me], qy = s.qy[me];
        int towers = 0, mines = 0, barracks = 0, eTowersCover = 0;
        double nearestTower = -1, nearestKnight = -1;
        int k250 = 0, k500 = 0, a300 = 0, g300 = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stOwner[i] == me) {
                if (s.stType[i] == Const.TOWER) {
                    towers++;
                    double d = Math.sqrt(Physics.distSq(s.sx[i], s.sy[i], qx, qy));
                    if (nearestTower < 0 || d < nearestTower) nearestTower = d;
                } else if (s.stType[i] == Const.MINE) mines++;
                else if (s.stType[i] == Const.BARRACKS) barracks++;
            } else if (s.stOwner[i] == foe && s.stType[i] == Const.TOWER) {
                if (Physics.distSq(s.sx[i], s.sy[i], qx, qy) < (double) s.towerRange[i] * s.towerRange[i]) eTowersCover++;
            }
        }
        for (int i = 0; i < s.nCreeps; i++) {
            if (s.cOwner[i] != foe) continue;
            double d = Math.sqrt(Physics.distSq(s.cx[i], s.cy[i], qx, qy));
            if (s.cType[i] == Const.KNIGHT) {
                if (d < 250) k250++;
                if (d < 500) k500++;
                if (nearestKnight < 0 || d < nearestKnight) nearestKnight = d;
            } else if (s.cType[i] == Const.ARCHER && d < 300) a300++;
            else if (s.cType[i] == Const.GIANT && d < 300) g300++;
        }
        int touched = -1;
        for (int i = 0; i < s.nSites; i++)
            if (Physics.lessInt(Physics.distSq(s.sx[i], s.sy[i], qx, qy), s.sr[i] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA)) { touched = i; break; }
        String what = c.queen == Command.BUILD ? "BUILD " + c.site + " " + c.structure : c.queen == Command.MOVE ? "MOVE " + c.x + " " + c.y : "WAIT";
        return String.format(Locale.ROOT, "t%3d gold %4d q(%4.0f,%4.0f) %-22s touch %2d | T%d M%d B%d nearT %4.0f | K250 %d K500 %d nearK %4.0f A300 %d G300 %d eTow %d |",
            t, s.gold[me], qx, qy, what, touched, towers, mines, barracks, nearestTower, k250, k500, nearestKnight, a300, g300, eTowersCover);
    }
}
