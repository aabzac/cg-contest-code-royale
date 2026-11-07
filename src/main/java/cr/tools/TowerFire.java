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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The turns our queen loses HP to enemy towers in recorded CG games, by what she was doing (the study of
 * 2026-11-07, docs/data/study-2026-11-07-tower-fire). Each game is replayed through {@link Rules} with the
 * recorded commands, as {@link DeathTrace} does; a turn counts when the probe without the enemy knights loses
 * HP (DeathTrace's {@code td}). A turn's class is the queen's command and the state at the turn's start:
 * <ul>
 * <li>the BUILD target: a free site, an own tower, mine or barracks, or another site; or MOVE, WAIT;</li>
 * <li>whether she touches the target already (she stands at it) or walks to it;</li>
 * <li>for a target: whether an enemy tower's range covers its centre (the bot's target filter), and whether any
 * standing point beside it (distance r + 32 from the centre) lies outside every enemy tower range;</li>
 * <li>whether she starts the turn inside an enemy tower's range, and whether an enemy knight is within 250;</li>
 * <li>whether the target alternates (differs from the previous turn's BUILD target and equals the one before).</li>
 * </ul>
 * Usage: TowerFire &lt;agentId[,agentId...]&gt; [lines] &lt;replay files or directories...&gt;; "lines" prints every counted turn.
 */
public final class TowerFire {
    private TowerFire() {}

    static final class Tally {
        int turns, hp;
        final Set<Long> games = new HashSet<>();
        final List<String> examples = new ArrayList<>();
    }

    static final Map<String, Tally> CLASSES = new TreeMap<>();
    static int games, fireGames, fireTurns, fireHp;

    public static void main(String[] args) throws IOException {
        if (args[0].equals("dump")) { dump(Paths.get(args[1]), Long.parseLong(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])); return; }
        Set<Long> agents = new HashSet<>();
        for (String a : args[0].split(",")) agents.add(Long.parseLong(a.trim()));
        int from = 1;
        boolean lines = false;
        if (args.length > 1 && args[1].equals("lines")) { lines = true; from = 2; }
        final boolean print = lines;
        for (int i = from; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            if (Files.isDirectory(p)) try (Stream<Path> s = Files.walk(p)) { s.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> scan(f, agents, print)); }
            else scan(p, agents, print);
        }
        System.out.printf(Locale.ROOT, "games %d, with tower fire %d; turns %d, HP %d%n", games, fireGames, fireTurns, fireHp);
        System.out.println("class | turns | HP | games | examples (game:turn)");
        CLASSES.entrySet().stream().sorted((x, y) -> y.getValue().hp - x.getValue().hp).forEach(e ->
            System.out.printf(Locale.ROOT, "%-75s %4d %5d %4d  %s%n", e.getKey(), e.getValue().turns, e.getValue().hp, e.getValue().games.size(),
                String.join(" ", e.getValue().examples)));
    }

    static void scan(Path file, Set<Long> agents, boolean print) {
        CgReplay r;
        try { r = CgReplay.load(file); } catch (IOException | RuntimeException e) { System.out.println("skip " + file + ": " + e.getMessage()); return; }
        int me = agents.contains(r.agentId[0]) ? 0 : agents.contains(r.agentId[1]) ? 1 : -1;
        if (me < 0) return;
        int foe = 1 - me;
        games++;
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        final double homeX = s.qx[me] < Const.WIDTH / 2.0 ? 0 : Const.WIDTH, homeY = s.qx[me] < Const.WIDTH / 2.0 ? 0 : Const.HEIGHT;
        int prevHp = s.hp[me], prevTarget = -1, prevPrevTarget = -1;
        boolean any = false;
        for (int t = 0; t < r.turns.size() && !s.over; t++) {
            String[] a = CgReplay.lines(r.turns.get(t).stdout[0]), b = CgReplay.lines(r.turns.get(t).stdout[1]);
            Command[] cmd = {Command.parse(a[0], a[1], s.nSites), Command.parse(b[0], b[1], s.nSites)};
            Command c = cmd[me];
            String cls = classify(s, me, foe, c, prevTarget, prevPrevTarget, homeX, homeY);
            GameState towersOnly = s.copy();
            for (int i = 0; i < towersOnly.nCreeps; i++)
                if (towersOnly.cOwner[i] == foe && towersOnly.cType[i] == Const.KNIGHT) towersOnly.cHp[i] = 0;
            towersOnly.removeDeadCreeps();
            new Rules().resolve(towersOnly, cmd[0], cmd[1]);
            int td = prevHp - towersOnly.hp[me];
            if (td > 0) {
                any = true;
                fireTurns++;
                fireHp += td;
                Tally tally = CLASSES.computeIfAbsent(cls, k -> new Tally());
                tally.turns++;
                tally.hp += td;
                tally.games.add(r.gameId);
                if (tally.examples.size() < 4 && !tally.examples.contains(r.gameId + ":" + t)) tally.examples.add(r.gameId + ":" + t);
                if (print) System.out.printf(Locale.ROOT, "%d t%3d td %d q(%4.0f,%4.0f) %s | %s%n", r.gameId, t, td, s.qx[me], s.qy[me],
                    c.queen == Command.BUILD ? "BUILD " + c.site + " " + c.structure : c.queen == Command.MOVE ? "MOVE " + c.x + " " + c.y : "WAIT", cls);
            }
            if (c.queen == Command.BUILD) {
                if (c.site != prevTarget) { prevPrevTarget = prevTarget; prevTarget = c.site; }
            }
            rules.resolve(s, cmd[0], cmd[1]);
            prevHp = s.hp[me];
            if (s.hp[me] <= 0) break;
        }
        if (any) fireGames++;
    }

    /** The board of one game over turns [from, to] for the given agent: sites with structures once, then the queens and commands per turn. */
    static void dump(Path file, long agent, int from, int to) throws IOException {
        CgReplay r = CgReplay.load(file);
        int me = r.agentId[0] == agent ? 0 : 1, foe = 1 - me;
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        for (int t = 0; t < r.turns.size() && !s.over && t <= to; t++) {
            String[] a = CgReplay.lines(r.turns.get(t).stdout[0]), b = CgReplay.lines(r.turns.get(t).stdout[1]);
            Command[] cmd = {Command.parse(a[0], a[1], s.nSites), Command.parse(b[0], b[1], s.nSites)};
            if (t == from || t == to) {
                System.out.printf(Locale.ROOT, "turn %d: our home %s; sites (id x y r type owner hp range):%n", t, s.qx[me] < 0 ? "?" : (r.agentId[me] == agent ? "" : ""));
                for (int i = 0; i < s.nSites; i++)
                    System.out.printf(Locale.ROOT, "  %2d %4.0f %4.0f %3d %s%n", i, s.sx[i], s.sy[i], s.sr[i],
                        s.stType[i] == Const.NONE ? "free" : (s.stOwner[i] == me ? "own " : "FOE ") + (s.stType[i] == Const.TOWER ? "tower hp " + s.towerHp[i] + " range " + s.towerRange[i]
                            : s.stType[i] == Const.MINE ? "mine" : "barracks"));
            }
            if (t >= from) {
                StringBuilder k = new StringBuilder();
                for (int i = 0; i < s.nCreeps; i++)
                    if (s.cOwner[i] == foe && s.cType[i] == Const.KNIGHT && Physics.distSq(s.cx[i], s.cy[i], s.qx[me], s.qy[me]) < 500.0 * 500)
                        k.append(String.format(Locale.ROOT, " (%.0f,%.0f)", s.cx[i], s.cy[i]));
                System.out.printf(Locale.ROOT, "t%3d hp %2d/%2d q(%4.0f,%4.0f) foe q(%4.0f,%4.0f) %s | %s | knights<500:%s%n", t, s.hp[me], s.hp[foe], s.qx[me], s.qy[me],
                    s.qx[foe], s.qy[foe], a[0].equals("") ? "" : (me == 0 ? a[0] : b[0]), inEnemyRange(s, foe, s.qx[me], s.qy[me]) ? "IN RANGE" : "outside", k);
            }
            rules.resolve(s, cmd[0], cmd[1]);
        }
    }

    static boolean inEnemyRange(GameState s, int foe, double x, double y) {
        for (int j = 0; j < s.nSites; j++)
            if (s.stType[j] == Const.TOWER && s.stOwner[j] == foe && Physics.distSq(s.sx[j], s.sy[j], x, y) < (double) s.towerRange[j] * s.towerRange[j])
                return true;
        return false;
    }

    /** Whether some standing point beside the site (r + 32 from its centre, on the board) lies outside every enemy tower range. */
    static boolean freeStandingPoint(GameState s, int foe, int site) {
        double d = s.sr[site] + Const.QUEEN_RADIUS + 2;
        for (int k = 0; k < 72; k++) {
            double ang = k * Math.PI / 36, x = s.sx[site] + d * Math.cos(ang), y = s.sy[site] + d * Math.sin(ang);
            if (x < Const.QUEEN_RADIUS || y < Const.QUEEN_RADIUS || x > Const.WIDTH - Const.QUEEN_RADIUS || y > Const.HEIGHT - Const.QUEEN_RADIUS) continue;
            if (!inEnemyRange(s, foe, x, y)) return true;
        }
        return false;
    }

    /** Whether the straight walk from (x, y) to the site, until she touches it, passes through an enemy tower's range (steps of 20). */
    static boolean pathThroughRange(GameState s, int foe, double x, double y, int site) {
        double d = Math.sqrt(Physics.distSq(x, y, s.sx[site], s.sy[site])), stop = s.sr[site] + Const.QUEEN_RADIUS + 2;
        for (double w = 0; w <= d - stop; w += 20) {
            double f = w / d;
            if (inEnemyRange(s, foe, x + f * (s.sx[site] - x), y + f * (s.sy[site] - y))) return true;
        }
        return false;
    }

    static String classify(GameState s, int me, int foe, Command c, int prevTarget, int prevPrevTarget, double homeX, double homeY) {
        double qx = s.qx[me], qy = s.qy[me];
        boolean knight = false;
        for (int i = 0; i < s.nCreeps; i++)
            if (s.cOwner[i] == foe && s.cType[i] == Const.KNIGHT && Physics.distSq(s.cx[i], s.cy[i], qx, qy) < 250.0 * 250) knight = true;
        String start = inEnemyRange(s, foe, qx, qy) ? "starts in range" : "starts outside";
        String near = knight ? "knight<250" : "no knight";
        if (c.queen != Command.BUILD) return (c.queen == Command.MOVE ? "MOVE" : "WAIT") + ", " + start + ", " + near;
        int t = c.site;
        String target = s.stType[t] == Const.NONE ? "free site"
            : s.stOwner[t] == me ? (s.stType[t] == Const.TOWER ? "own tower" : s.stType[t] == Const.MINE ? "own mine" : "own barracks") : "enemy site";
        boolean touching = Physics.lessInt(Physics.distSq(s.sx[t], s.sy[t], qx, qy), s.sr[t] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
        String centre = inEnemyRange(s, foe, s.sx[t], s.sy[t]) ? "centre in range" : "centre outside";
        String ring = freeStandingPoint(s, foe, t) ? "a safe side" : "no safe side";
        String alternating = t != prevTarget && t == prevPrevTarget ? ", alternating" : "";
        if (s.stType[t] == Const.NONE) {
            boolean enemyHalf = Physics.distSq(s.sx[t], s.sy[t], homeX, homeY) > Physics.distSq(s.sx[t], s.sy[t], Const.WIDTH - homeX, Const.HEIGHT - homeY);
            alternating += (enemyHalf ? ", target on the enemy half" : ", target on our half")
                + (pathThroughRange(s, foe, s.qx[me], s.qy[me], t) ? ", path through range" : ", path clear");
        }
        return "BUILD " + target + ", " + (touching ? "touching" : "walking") + ", " + centre + ", " + ring + ", " + start + ", " + near + alternating;
    }
}
