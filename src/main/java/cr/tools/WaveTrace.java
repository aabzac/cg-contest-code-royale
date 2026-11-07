package cr.tools;

import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Physics;
import cr.core.Rules;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The enemy's first knight wave against our queen in recorded CG games, turn by turn (the Gold study of 2026-11-07,
 * docs/data/study-2026-11-07-gold). Each game is replayed through {@link Rules} with the recorded commands. The first
 * contact is the first turn an enemy knight stands within 250 of our queen at the turn's start; the window runs from
 * three turns before it to 29 after. Per turn (the state at the turn's start, the losses from probes of the turn):
 * <ul>
 * <li>our queen: position, HP, the turn's loss to knights (enemy tower ranges zeroed) and to towers (enemy knights
 * removed), the command, how far she moved, and how many own towers cover her;</li>
 * <li>own towers: id, HP, range and distance to the queen;</li>
 * <li>enemy knights within 250 and 500 of the queen, their summed HP, the nearest one's distance, and how many of those
 * within 500 stand inside an own tower's range;</li>
 * <li>what our towers did to the enemy knights this turn: the damage and the kills, from a probe with our towers'
 * ranges zeroed and our queen waiting (knights take damage only from towers and archers);</li>
 * <li>the enemy queen's distance, our knights alive, and the enemy knights spawned this turn.</li>
 * </ul>
 * Writes one file per game, &lt;gameId&gt;.txt, and summary.csv with the window's metrics per game.
 * Usage: WaveTrace &lt;agentId[,agentId...]&gt; &lt;output directory&gt; &lt;replay files or directories...&gt;
 */
public final class WaveTrace {
    private WaveTrace() {}

    static final int BEFORE = 3, AFTER = 29, WINDOW = 20;

    static final class Turn {
        int t, hp, lossK, lossT, towers, coverQ, k250, k500, hpK500, coveredK, dmgK, killK, ourKnights, spawned;
        double qx, qy, moved, nearK, enemyQ;
        String cmd, towerList;
    }

    public static void main(String[] args) throws IOException {
        Set<Long> agents = new HashSet<>();
        for (String a : args[0].split(",")) agents.add(Long.parseLong(a.trim()));
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        List<String> summary = new ArrayList<>();
        summary.add("gameId,seat,opponent,ranks,scores,turns,startHp,contact,towersAtContact,towerHpAtContact,coverQAtContact,"
            + "k500AtContact,hpK500AtContact,lossK20,lossT20,k250Turns20,coverQTurns20,cover2QTurns20,coveredKShare20,dmgK20,killK20,"
            + "clearTurn,moves20,stuckMoves20,meanMove20,meanNearTower20,minEnemyQ20,spawned20,topups20");
        for (int i = 2; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            if (Files.isDirectory(p)) try (Stream<Path> s = Files.walk(p)) { s.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> scan(f, agents, out, summary)); }
            else scan(p, agents, out, summary);
        }
        Files.write(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        System.out.println("games " + (summary.size() - 1) + " -> " + out);
    }

    static boolean covers(GameState s, int owner, double x, double y) {
        return coverCount(s, owner, x, y) > 0;
    }

    static int coverCount(GameState s, int owner, double x, double y) {
        int n = 0;
        for (int j = 0; j < s.nSites; j++)
            if (s.stType[j] == Const.TOWER && s.stOwner[j] == owner && Physics.distSq(s.sx[j], s.sy[j], x, y) < (double) s.towerRange[j] * s.towerRange[j]) n++;
        return n;
    }

    static int[] knights(GameState s, int owner) {
        int n = 0, hp = 0;
        for (int i = 0; i < s.nCreeps; i++)
            if (s.cOwner[i] == owner && s.cType[i] == Const.KNIGHT && s.cHp[i] > 0) { n++; hp += s.cHp[i]; }
        return new int[]{n, hp};
    }

    static void scan(Path file, Set<Long> agents, Path out, List<String> summary) {
        CgReplay r;
        try { r = CgReplay.load(file); } catch (IOException | RuntimeException e) { System.out.println("skip " + file + ": " + e.getMessage()); return; }
        int me = agents.contains(r.agentId[0]) ? 0 : agents.contains(r.agentId[1]) ? 1 : -1;
        if (me < 0) return;
        int foe = 1 - me;
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        List<Turn> turns = new ArrayList<>();
        int contact = -1, startHp = s.hp[me];
        for (int t = 0; t < r.turns.size() && !s.over; t++) {
            if (contact >= 0 && t > contact + AFTER) break;
            String[] a = CgReplay.lines(r.turns.get(t).stdout[0]), b = CgReplay.lines(r.turns.get(t).stdout[1]);
            Command[] cmd = {Command.parse(a[0], a[1], s.nSites), Command.parse(b[0], b[1], s.nSites)};
            Turn u = new Turn();
            u.t = t;
            u.qx = s.qx[me];
            u.qy = s.qy[me];
            u.hp = s.hp[me];
            Command c = cmd[me];
            u.cmd = c.queen == Command.BUILD ? "BUILD " + c.site + " " + c.structure : c.queen == Command.MOVE ? "MOVE " + c.x + " " + c.y : "WAIT";
            StringBuilder tl = new StringBuilder();
            for (int j = 0; j < s.nSites; j++) {
                if (s.stType[j] != Const.TOWER || s.stOwner[j] != me) continue;
                u.towers++;
                tl.append(String.format(Locale.ROOT, " %d:%d/%d@%.0f", j, s.towerHp[j], s.towerRange[j], Math.sqrt(Physics.distSq(s.sx[j], s.sy[j], u.qx, u.qy))));
            }
            u.towerList = tl.toString();
            u.coverQ = coverCount(s, me, u.qx, u.qy);
            u.nearK = -1;
            for (int i = 0; i < s.nCreeps; i++) {
                if (s.cOwner[i] != foe || s.cType[i] != Const.KNIGHT || s.cHp[i] <= 0) continue;
                double d = Math.sqrt(Physics.distSq(s.cx[i], s.cy[i], u.qx, u.qy));
                if (d < 250) u.k250++;
                if (d < 500) {
                    u.k500++;
                    u.hpK500 += s.cHp[i];
                    if (covers(s, me, s.cx[i], s.cy[i])) u.coveredK++;
                }
                if (u.nearK < 0 || d < u.nearK) u.nearK = d;
            }
            u.enemyQ = Math.sqrt(Physics.distSq(s.qx[foe], s.qy[foe], u.qx, u.qy));
            u.ourKnights = knights(s, me)[0];
            if (contact < 0 && u.k250 > 0) contact = t;
            if (contact >= 0 || t >= 0) {
                // our queen's loss by source, as DeathTrace does
                GameState knightsOnly = s.copy(), towersOnly = s.copy(), noOurTowers = s.copy();
                for (int j = 0; j < s.nSites; j++) {
                    if (knightsOnly.stType[j] == Const.TOWER && knightsOnly.stOwner[j] == foe) knightsOnly.towerRange[j] = 0;
                    if (noOurTowers.stType[j] == Const.TOWER && noOurTowers.stOwner[j] == me) noOurTowers.towerRange[j] = 0;
                }
                for (int i = 0; i < towersOnly.nCreeps; i++)
                    if (towersOnly.cOwner[i] == foe && towersOnly.cType[i] == Const.KNIGHT) towersOnly.cHp[i] = 0;
                towersOnly.removeDeadCreeps();
                new Rules().resolve(knightsOnly, cmd[0], cmd[1]);
                new Rules().resolve(towersOnly, cmd[0], cmd[1]);
                Command[] probe = cmd.clone();
                probe[me] = Command.waitCmd();
                new Rules().resolve(noOurTowers, probe[0], probe[1]);
                int[] before = knights(s, foe);
                int prevHp = s.hp[me];
                rules.resolve(s, cmd[0], cmd[1]);
                int[] after = knights(s, foe), without = knights(noOurTowers, foe);
                u.lossK = Math.max(0, prevHp - knightsOnly.hp[me]);
                u.lossT = Math.max(0, prevHp - towersOnly.hp[me]);
                u.dmgK = Math.max(0, without[1] - after[1]);
                u.killK = Math.max(0, without[0] - after[0]);
                u.spawned = Math.max(0, without[0] - before[0]);
                u.moved = Math.sqrt(Physics.distSq(u.qx, u.qy, s.qx[me], s.qy[me]));
            }
            turns.add(u);
        }
        if (contact < 0) return;
        final int c0 = contact;
        // the window's metrics: the WINDOW turns from the contact
        int lossK = 0, lossT = 0, k250 = 0, coverQ = 0, cover2 = 0, coveredK = 0, k500sum = 0, dmgK = 0, killK = 0, moves = 0, stuck = 0, spawned = 0, topups = 0;
        double moveSum = 0, nearSum = 0, minEnemyQ = Double.MAX_VALUE;
        int nearN = 0, clear = -1;
        Turn at = null;
        for (Turn u : turns) {
            if (u.t == c0) at = u;
            if (u.t < c0 || u.t >= c0 + WINDOW) continue;
            lossK += u.lossK;
            lossT += u.lossT;
            if (u.k250 > 0) k250++;
            if (u.coverQ > 0) coverQ++;
            if (u.coverQ > 1) cover2++;
            coveredK += u.coveredK;
            k500sum += u.k500;
            dmgK += u.dmgK;
            killK += u.killK;
            spawned += u.spawned;
            if (u.cmd.startsWith("MOVE")) {
                moves++;
                moveSum += u.moved;
                if (u.moved < 5) stuck++;
            }
            if (u.cmd.endsWith("TOWER") && u.towerList.contains(" " + u.cmd.split(" ")[1] + ":")) topups++;
            minEnemyQ = Math.min(minEnemyQ, u.enemyQ);
        }
        for (Turn u : turns) {
            if (u.t <= c0) continue;
            if (clear < 0 && u.k500 == 0) clear = u.t - c0;
        }
        for (Turn u : turns) {
            if (u.t < c0 || u.t >= c0 + WINDOW) continue;
            double near = nearestTower(u.towerList);
            if (near >= 0) { nearSum += near; nearN++; }
        }
        int turnsTotal = r.turns.size();
        summary.add(String.format(Locale.ROOT, "%d,%d,%s,%d/%d,%.0f/%.0f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%d,%d,%d,%d,%d,%.1f,%.0f,%.0f,%d,%d",
            r.gameId, me, r.pseudo[foe] == null ? "-" : r.pseudo[foe].replace(',', ' '), r.ranks[me], r.ranks[foe], r.scores[me], r.scores[foe],
            turnsTotal, startHp, c0, at.towers, towerHp(at.towerList), at.coverQ, at.k500, at.hpK500, lossK, lossT, k250, coverQ, cover2,
            k500sum == 0 ? 0.0 : (double) coveredK / k500sum, dmgK, killK, clear, moves, stuck, moves == 0 ? 0.0 : moveSum / moves,
            nearN == 0 ? -1.0 : nearSum / nearN, minEnemyQ == Double.MAX_VALUE ? -1.0 : minEnemyQ, spawned, topups));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve(r.gameId + ".txt"), StandardCharsets.UTF_8))) {
            w.printf(Locale.ROOT, "=== game %d seat %d vs %s (ranks %d/%d, scores %.0f/%.0f, %d turns); starting HP %d; first contact turn %d%n",
                r.gameId, me, r.pseudo[foe], r.ranks[me], r.ranks[foe], r.scores[me], r.scores[foe], turnsTotal, startHp, c0);
            w.println("turn | our queen: position, HP, the turn's loss to knights k and towers t, command, moved | own towers covering her; "
                + "own towers id:HP/range@distance | enemy knights within 250 / 500, their HP, nearest, inside our tower ranges | our towers' "
                + "damage to them and kills this turn | enemy queen's distance | our knights alive | enemy knights spawned");
            for (Turn u : turns) {
                if (u.t < c0 - BEFORE) continue;
                w.printf(Locale.ROOT, "t%3d q(%4.0f,%4.0f) hp %3d -k%d -t%d %-22s moved %3.0f | cov %d |%s | eK %d/%d hp %3d near %4.0f inTwr %d | dmg %3d kill %d | eQ %4.0f | ourK %d | spawn %d%n",
                    u.t, u.qx, u.qy, u.hp, u.lossK, u.lossT, u.cmd, u.moved, u.coverQ, u.towerList, u.k250, u.k500, u.hpK500, u.nearK, u.coveredK,
                    u.dmgK, u.killK, u.enemyQ, u.ourKnights, u.spawned);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The nearest own tower's distance in a tower list " id:hp/range@dist ...", or -1. */
    static double nearestTower(String list) {
        double best = -1;
        for (String part : list.trim().split(" ")) {
            int at = part.indexOf('@');
            if (at < 0) continue;
            double d = Double.parseDouble(part.substring(at + 1));
            if (best < 0 || d < best) best = d;
        }
        return best;
    }

    /** The summed HP of the towers in a tower list. */
    static int towerHp(String list) {
        int sum = 0;
        for (String part : list.trim().split(" ")) {
            int colon = part.indexOf(':'), slash = part.indexOf('/');
            if (colon < 0 || slash < 0) continue;
            sum += Integer.parseInt(part.substring(colon + 1, slash));
        }
        return sum;
    }
}
