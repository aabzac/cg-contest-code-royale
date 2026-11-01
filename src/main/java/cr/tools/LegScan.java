package cr.tools;

import cr.bot.Bot;
import cr.bot.BotFactory;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Physics;
import cr.referee.ProcessBot;
import cr.referee.Referee;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Where a bot's queen spends her walking turns, per game, on both seats of every map. A walking turn
 * is a MOVE or a BUILD towards a site she does not touch; a leg is a maximal run of walking turns
 * with one target (MOVE turns take the target of the BUILD that follows them, or the site they
 * arrive at). A route leg arrives at a free site without fleeing; its excess is the leg's turns
 * over the straight bound floor((d - (r+35)) / 60) + 1. Predictions use the sites-only step
 * ({@link Physics#queenStep}), so unit effects show as differences from them. The scanned bot must
 * run in process; the opponent may be a process. Usage: LegScan bot opp maps seedBase [threads] [outDir]
 */
public final class LegScan {
    /** The rule ladder of v03 and current: one barracks, income 3, eight towers. */
    static final int LADDER_BARRACKS = 1, LADDER_INCOME = 3, LADDER_TOWERS = 8;
    static final int PASSES = 5, HORIZON = 25, SAMPLES = 72;
    /** A step shorter than this that does not arrive is a slide (or a stall at 0). */
    static final int SLIDE_STEP = 55;
    static final double MIN_STEP = 2;

    /** Turn classes. */
    static final int WAIT = 0, MOVE = 1, WALK = 2, STAND = 3;
    /** Why a leg to a free site ended before she touched the site. */
    static final int FLEE = 0, TOP_UP = 1, RETARGET = 2, TOUCHED_SITE = 3, OTHER = 4;
    static final String[] REASON = {"flee", "own-tower top-up", "retarget", "build on a site touched on the way", "other"};

    static final class Turn {
        int t, cls, site = -1;
        boolean flee, toOwnTower, threat, free;
        int bx, by, ax, ay, disp;
    }

    static final class Leg {
        int start, end, turns, target = -1;
        boolean ownTower, flee, arrived, route;
        int sx, sy, ex, ey, bound = -1, excess, slides, stalls, standAfter, reason = -1;
        double dist;
        /** Landing potential when a route leg to B follows directly: the straight prediction from the actual landing and the best sampled landings. */
        int nextTarget = -1, nextPred = -1, bestLast = -1, bestFull = -1;
    }

    /** One queen-game: the bot's seat in one recorded game. */
    public static final class Game {
        public long seed;
        public int seat, turns, won;
        public final int[] cls = new int[4];
        public int walking, fleeTurns, routeLegs, excess, slides, stalls, finalFails, finalFailsUnit, abandoned, abandonedTurns;
        public final int[] abandonedBy = new int[5];
        public int freeSiteBuilds, potentialLast, potentialFull, ladder = -1;
        /** From the bot itself: the longest play() after turn 0 and the most queenStep calls in one turn (-1: not counted). */
        public long maxNanos, maxCalls = -1, calls = -1;
        final List<Leg> legs = new ArrayList<>();
    }

    /** Wraps the scanned bot: play() times and queenStep calls per turn. */
    static final class Probe implements Bot {
        final Bot inner;
        long maxNanos, maxCalls = -1, calls = -1;

        Probe(Bot inner) {
            if (inner instanceof ProcessBot) throw new IllegalArgumentException("the scanned bot must run in process: " + inner.name());
            this.inner = inner;
        }

        @Override public String name() { return inner.name(); }
        @Override public void init(GameState s) { inner.init(s); }
        @Override public long queenSteps() { return inner.queenSteps(); }

        @Override
        public Command play(GameState s, int touched, long deadlineNanos) {
            long c0 = inner.queenSteps(), t0 = System.nanoTime();
            Command c = inner.play(s, touched, deadlineNanos);
            long dt = System.nanoTime() - t0, c1 = inner.queenSteps();
            if (s.turn > 0) maxNanos = Math.max(maxNanos, dt);
            if (c1 >= 0) {
                if (calls < 0) calls = 0;
                calls += c1 - c0;
                maxCalls = Math.max(maxCalls, c1 - c0);
            }
            return c;
        }
    }

    public static void main(String[] args) throws Exception {
        String bot = args[0], opp = args[1];
        int maps = Integer.parseInt(args[2]);
        long seedBase = Long.parseLong(args[3]);
        int threads = args.length > 4 ? Integer.parseInt(args[4]) : 8;
        Path outDir = args.length > 5 && !args[5].isBlank() ? Paths.get(args[5]) : null;
        List<Game> games = scan(bot, opp, maps, seedBase, threads);
        String summary = summary(games, bot, opp, maps, seedBase);
        System.out.print(summary);
        if (outDir != null) {
            Files.createDirectories(outDir);
            String tag = fileName(bot) + "-vs-" + fileName(opp);
            Files.writeString(outDir.resolve(tag + "-summary.txt"), summary);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outDir.resolve(tag + "-games.csv")))) {
                w.println("seed,seat,turns,won,wait,move,walkBuild,standBuild,walking,fleeTurns,routeLegs,excess,slides,stalls,finalFails,finalFailsUnit,"
                    + "abandoned,abandonedTurns,abFlee,abTopUp,abRetarget,abTouchedSite,abOther,freeSiteBuilds,potentialLast,potentialFull,ladder,maxPlayMs,maxCalls,calls");
                for (Game g : games)
                    w.println(String.join(",", "" + g.seed, "" + g.seat, "" + g.turns, "" + g.won, "" + g.cls[0], "" + g.cls[1], "" + g.cls[2], "" + g.cls[3],
                        "" + g.walking, "" + g.fleeTurns, "" + g.routeLegs, "" + g.excess, "" + g.slides, "" + g.stalls, "" + g.finalFails, "" + g.finalFailsUnit,
                        "" + g.abandoned, "" + g.abandonedTurns, "" + g.abandonedBy[0], "" + g.abandonedBy[1], "" + g.abandonedBy[2], "" + g.abandonedBy[3], "" + g.abandonedBy[4],
                        "" + g.freeSiteBuilds, "" + g.potentialLast, "" + g.potentialFull, "" + g.ladder,
                        String.format(Locale.ROOT, "%.3f", g.maxNanos / 1e6), "" + g.maxCalls, "" + g.calls));
            }
            System.out.println("  written " + outDir.resolve(tag + "-summary.txt") + " and " + tag + "-games.csv");
        }
    }

    static String fileName(String spec) { return spec.replaceAll("[^A-Za-z0-9._-]+", "_"); }

    /** Plays every map on both seats (a mirror once per map) and analyzes the bot's queen-games. */
    public static List<Game> scan(String bot, String opp, int maps, long seedBase, int threads) throws Exception {
        boolean mirror = bot.equals(opp);
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<List<Game>>> fut = new ArrayList<>();
        for (int m = 0; m < maps; m++) {
            long seed = seedBase + m;
            if (mirror) {
                fut.add(ex.submit(() -> {
                    Probe p0 = new Probe(BotFactory.create(bot)), p1 = new Probe(BotFactory.create(bot));
                    Referee.Result r = Referee.play(seed, p0, p1, true, 50);
                    return List.of(analyze(seed, 0, r, p0), analyze(seed, 1, r, p1));
                }));
            } else {
                for (int seat = 0; seat < 2; seat++) {
                    int s = seat;
                    fut.add(ex.submit(() -> {
                        Probe p = new Probe(BotFactory.create(bot));
                        Referee.Result r = s == 0 ? Referee.play(seed, p, BotFactory.create(opp), true, 50)
                            : Referee.play(seed, BotFactory.create(opp), p, true, 50);
                        return List.of(analyze(seed, s, r, p));
                    }));
                }
            }
        }
        List<Game> games = new ArrayList<>();
        try {
            for (Future<List<Game>> f : fut) games.addAll(f.get());
        } finally {
            ex.shutdown();
        }
        return games;
    }

    static boolean touches(int[] site, double x, double y) {
        return Physics.lessInt(Physics.distSq(site[1], site[2], x, y), site[3] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
    }

    static boolean touchesOnly(List<int[]> sites, int target, double x, double y) {
        if (!touches(sites.get(target), x, y)) return false;
        for (int i = 0; i < sites.size(); i++) if (i != target && touches(sites.get(i), x, y)) return false;
        return true;
    }

    /** Straight bound: the smallest n with d - 60 n < r + 35 (no rounding, no other sites). */
    static int bound(double d, int r) {
        double free = d - (r + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA);
        return free < 0 ? 0 : (int) Math.floor(free / Const.QUEEN_SPEED) + 1;
    }

    /** Turns of the straight sites-only walk until she touches the site; -1 if she stands still first or needs more than max. */
    static int pred(Physics ph, GameState g, List<int[]> sites, int target, double x, double y, int max) {
        int[] st = sites.get(target);
        for (int n = 0; n <= max; n++) {
            if (touches(st, x, y)) return n;
            if (n == max) break;
            ph.queenStep(g, x, y, st[1], st[2], PASSES);
            if (Physics.distSq(ph.rx, ph.ry, x, y) < MIN_STEP * MIN_STEP && !touches(st, ph.rx, ph.ry)) return -1;
            x = ph.rx;
            y = ph.ry;
        }
        return -1;
    }

    static Game analyze(long seed, int seat, Referee.Result r, Probe probe) {
        Game g = new Game();
        g.seed = seed;
        g.seat = seat;
        g.turns = r.frames.size() - 1;
        g.won = r.winner == seat ? 1 : r.winner < 0 ? 0 : -1;
        g.maxNanos = probe.maxNanos;
        g.maxCalls = probe.maxCalls;
        g.calls = probe.calls;
        List<int[]> sites = r.sites;
        int enemy = 1 - seat;
        int hx = seat == 0 ? 0 : Const.WIDTH, hy = seat == 0 ? 0 : Const.HEIGHT;
        GameState map = MapGen.generate(seed, 4);
        Physics ph = new Physics();

        Turn[] turns = new Turn[g.turns];
        for (int t = 0; t < g.turns; t++) {
            Referee.Frame before = r.frames.get(t), after = r.frames.get(t + 1);
            Turn tu = new Turn();
            tu.t = t;
            tu.bx = before.queens[seat][0]; tu.by = before.queens[seat][1];
            tu.ax = after.queens[seat][0]; tu.ay = after.queens[seat][1];
            tu.disp = (int) Math.round(Math.sqrt(Physics.distSq(tu.bx, tu.by, tu.ax, tu.ay)));
            for (int[] c : before.creeps)
                if (c[0] == enemy && c[1] == Const.KNIGHT && Physics.distSq(c[2], c[3], tu.bx, tu.by) < 250.0 * 250) tu.threat = true;
            String line = after.cmds[seat].split(" \\| ")[0];
            if (line.startsWith("BUILD ")) {
                tu.site = Integer.parseInt(line.split(" ")[1]);
                int[] st = sites.get(tu.site), str = before.structures[tu.site];
                tu.cls = touches(st, tu.bx, tu.by) ? STAND : WALK;
                tu.toOwnTower = str[0] == Const.TOWER && str[1] == seat;
                tu.free = str[0] == Const.NONE;
                tu.flee = tu.threat && tu.toOwnTower;
                if (tu.cls == WALK && tu.free && !tu.threat) {
                    double d = Math.sqrt(Physics.distSq(st[1], st[2], tu.bx, tu.by));
                    if (d - (st[3] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA) < Const.QUEEN_SPEED && !touches(st, tu.ax, tu.ay)) {
                        g.finalFails++;
                        boolean unit = Physics.distSq(before.queens[enemy][0], before.queens[enemy][1], tu.bx, tu.by) < 120.0 * 120;
                        for (int[] c : before.creeps) if (Physics.distSq(c[2], c[3], tu.bx, tu.by) < 90.0 * 90) unit = true;
                        if (unit) g.finalFailsUnit++;
                    }
                }
            } else if (line.startsWith("MOVE ")) {
                tu.cls = MOVE;
                String[] tok = line.split(" ");
                tu.flee = tu.threat && Integer.parseInt(tok[1]) == hx && Integer.parseInt(tok[2]) == hy;
            } else tu.cls = WAIT;
            turns[t] = tu;
            g.cls[tu.cls]++;
            if (tu.flee) g.fleeTurns++;
            if (g.ladder < 0) {
                int inc = 0, bar = 0, tow = 0;
                for (int[] s : after.structures) {
                    if (s[1] != seat) continue;
                    if (s[0] == Const.MINE) inc += s[2];
                    if (s[0] == Const.BARRACKS) bar++;
                    if (s[0] == Const.TOWER) tow++;
                }
                if (bar >= LADDER_BARRACKS && inc >= LADDER_INCOME && tow >= LADDER_TOWERS) g.ladder = t + 1;
            }
        }
        g.walking = g.cls[MOVE] + g.cls[WALK];

        // MOVE runs take the target of the walking BUILD that follows, else the site they arrive at, else the BUILD before.
        for (int t = 0; t < g.turns; t++) {
            if (turns[t].cls != MOVE) continue;
            int a = t;
            while (t < g.turns && turns[t].cls == MOVE) t++;
            int b = t - 1, target = -1;
            boolean ownTower = false;
            if (t < g.turns && (turns[t].cls == WALK || turns[t].cls == STAND)) { target = turns[t].site; ownTower = turns[t].toOwnTower; }
            else if (a > 0 && turns[a - 1].cls == WALK) { target = turns[a - 1].site; ownTower = turns[a - 1].toOwnTower; }
            for (int k = a; k <= b; k++) { turns[k].site = target; turns[k].toOwnTower = ownTower; }
            t = b;
        }

        // Legs: maximal runs of walking turns with one target; flee MOVEs (home corner) form legs of their own.
        for (int t = 0; t < g.turns; t++) {
            Turn first = turns[t];
            if (first.cls != MOVE && first.cls != WALK) continue;
            int a = t;
            boolean fleeRun = first.cls == MOVE && first.flee;
            while (t < g.turns && (turns[t].cls == MOVE || turns[t].cls == WALK) && turns[t].site == first.site
                && (turns[t].cls == MOVE && turns[t].flee) == fleeRun) t++;
            int b = t - 1;
            t = b;
            Leg L = new Leg();
            L.start = a; L.end = b; L.turns = b - a + 1;
            L.target = fleeRun ? -1 : first.site;
            L.ownTower = first.toOwnTower;
            L.sx = turns[a].bx; L.sy = turns[a].by; L.ex = turns[b].ax; L.ey = turns[b].ay;
            for (int k = a; k <= b; k++) if (turns[k].flee) L.flee = true;
            if (L.target >= 0) {
                int[] st = sites.get(L.target);
                L.arrived = touches(st, L.ex, L.ey);
                L.dist = Math.sqrt(Physics.distSq(L.sx, L.sy, st[1], st[2]));
                L.bound = bound(L.dist, st[3]);
                for (int k = a; k <= b; k++) {
                    Turn tu = turns[k];
                    if (touches(st, tu.ax, tu.ay)) continue;
                    if (tu.disp == 0) L.stalls++;
                    else if (tu.disp < SLIDE_STEP) L.slides++;
                }
                int k = b + 1;
                while (k < g.turns && turns[k].cls == STAND && turns[k].site == L.target) { L.standAfter++; k++; }
            }
            L.route = L.target >= 0 && !L.ownTower && !L.flee;
            if (L.route && L.arrived) {
                L.excess = Math.max(0, L.turns - L.bound);
                g.routeLegs++;
                g.excess += L.excess;
                g.slides += L.slides;
                g.stalls += L.stalls;
            } else if (L.route) {
                g.abandoned++;
                g.abandonedTurns += L.turns;
                L.reason = OTHER;
                if (b + 1 < g.turns) {
                    Turn next = turns[b + 1];
                    if (next.flee) L.reason = FLEE;
                    else if (next.cls == STAND && next.toOwnTower) L.reason = TOP_UP;
                    else if (next.cls == STAND && next.site != L.target) {
                        L.reason = TOUCHED_SITE;
                        if (next.free) g.freeSiteBuilds++;
                    } else if ((next.cls == WALK || next.cls == MOVE) && next.site >= 0 && !next.toOwnTower) L.reason = RETARGET;
                }
                g.abandonedBy[L.reason]++;
            }
            g.legs.add(L);
        }

        // Landing potential: a route leg A arrived, then (after the standing builds) a route leg B arrived.
        List<Leg> legs = g.legs;
        for (int i = 0; i + 1 < legs.size(); i++) {
            Leg A = legs.get(i), B = legs.get(i + 1);
            if (!A.route || !A.arrived || !B.route || !B.arrived || B.target == A.target || B.start != A.end + 1 + A.standAfter) continue;
            A.nextTarget = B.target;
            A.nextPred = pred(ph, map, sites, B.target, B.sx, B.sy, HORIZON);
            if (A.nextPred < 0) continue;
            int[] sa = sites.get(A.target);
            double radius = sa[3] + Const.QUEEN_RADIUS + 2;
            int bestFull = Integer.MAX_VALUE, bestLast = Integer.MAX_VALUE;
            int lx = turns[A.end].bx, ly = turns[A.end].by;
            for (int k = 0; k < SAMPLES; k++) {
                double ang = 2 * Math.PI * k / SAMPLES;
                double px = Math.round(sa[1] + radius * Math.cos(ang)), py = Math.round(sa[2] + radius * Math.sin(ang));
                if (px < Const.QUEEN_RADIUS || py < Const.QUEEN_RADIUS || px > Const.WIDTH - Const.QUEEN_RADIUS || py > Const.HEIGHT - Const.QUEEN_RADIUS) continue;
                double x = A.sx, y = A.sy;
                int reached = -1;
                for (int n = 1; n <= A.turns; n++) {
                    ph.queenStep(map, x, y, px, py, PASSES);
                    if (Physics.distSq(ph.rx, ph.ry, x, y) < MIN_STEP * MIN_STEP) break;
                    x = ph.rx; y = ph.ry;
                    if (touchesOnly(sites, A.target, x, y)) { reached = n; break; }
                }
                if (reached > 0) {
                    int tb = pred(ph, map, sites, B.target, x, y, HORIZON);
                    if (tb >= 0) bestFull = Math.min(bestFull, tb);
                }
                ph.queenStep(map, lx, ly, px, py, PASSES);
                if (touchesOnly(sites, A.target, ph.rx, ph.ry)) {
                    int tb = pred(ph, map, sites, B.target, ph.rx, ph.ry, HORIZON);
                    if (tb >= 0) bestLast = Math.min(bestLast, tb);
                }
            }
            if (bestFull < Integer.MAX_VALUE) { A.bestFull = bestFull; g.potentialFull += Math.max(0, A.nextPred - bestFull); }
            if (bestLast < Integer.MAX_VALUE) { A.bestLast = bestLast; g.potentialLast += Math.max(0, A.nextPred - bestLast); }
        }
        return g;
    }

    static String summary(List<Game> games, String bot, String opp, int maps, long seedBase) {
        StringBuilder o = new StringBuilder();
        int n = games.size();
        o.append(String.format(Locale.ROOT, "LegScan %s vs %s: maps=%d seeds %d..%d queen-games=%d%n", bot, opp, maps, seedBase, seedBase + maps - 1, n));
        for (int seat = -1; seat < 2; seat++) {
            long turns = 0, walking = 0, flee = 0, legs = 0, excess = 0, slides = 0, stalls = 0, ff = 0, ffu = 0, ab = 0, abT = 0, fsb = 0, pl = 0, pf = 0, ladderSum = 0, calls = 0, callTurns = 0;
            long[] abBy = new long[5];
            int[] cls = new int[4];
            int cnt = 0, won = 0, drew = 0, ladder = 0, limit = 0;
            long maxNanos = 0, maxCalls = -1;
            boolean counted = false;
            for (Game g : games) {
                if (seat >= 0 && g.seat != seat) continue;
                cnt++;
                turns += g.turns; walking += g.walking; flee += g.fleeTurns; legs += g.routeLegs; excess += g.excess; slides += g.slides; stalls += g.stalls;
                ff += g.finalFails; ffu += g.finalFailsUnit; ab += g.abandoned; abT += g.abandonedTurns; fsb += g.freeSiteBuilds; pl += g.potentialLast; pf += g.potentialFull;
                for (int k = 0; k < 5; k++) abBy[k] += g.abandonedBy[k];
                for (int k = 0; k < 4; k++) cls[k] += g.cls[k];
                if (g.won > 0) won++; else if (g.won == 0) drew++;
                if (g.turns >= Const.MAX_TURNS) limit++;
                if (g.ladder >= 0) { ladder++; ladderSum += g.ladder; }
                maxNanos = Math.max(maxNanos, g.maxNanos);
                if (g.calls >= 0) { counted = true; calls += g.calls; callTurns += g.turns; maxCalls = Math.max(maxCalls, g.maxCalls); }
            }
            if (cnt == 0) continue;
            double c = cnt;
            String head = seat < 0 ? "all" : "seat " + seat;
            o.append(String.format(Locale.ROOT, "%s: games %d won %d drew %d lost %d; mean turns %.1f, at the %d-turn limit %d; ladder complete in %d (%.1f%%), mean turn %.1f%n",
                head, cnt, won, drew, cnt - won - drew, turns / c, Const.MAX_TURNS, limit, ladder, 100.0 * ladder / c, ladder > 0 ? (double) ladderSum / ladder : -1));
            o.append(String.format(Locale.ROOT, "  per game: walking turns %.2f (MOVE %.2f, walking BUILD %.2f), standing BUILD %.2f, WAIT %.2f, flee turns %.2f%n",
                walking / c, cls[MOVE] / c, cls[WALK] / c, cls[STAND] / c, cls[WAIT] / c, flee / c));
            o.append(String.format(Locale.ROOT, "  per game: route legs %.2f, excess over the straight bound %.2f (per leg %.3f), mid-leg slide turns %.2f, stall turns %.2f, failed final steps %.2f (with a unit near %.2f)%n",
                legs / c, excess / c, legs == 0 ? 0 : (double) excess / legs, slides / c, stalls / c, ff / c, ffu / c));
            o.append(String.format(Locale.ROOT, "  per game: abandoned legs %.2f (%.2f turns): flee %.2f, own-tower top-up %.2f, retarget %.2f, build on a site touched on the way %.2f (free site %.2f), other %.2f%n",
                ab / c, abT / c, abBy[FLEE] / c, abBy[TOP_UP] / c, abBy[RETARGET] / c, abBy[TOUCHED_SITE] / c, fsb / c, abBy[OTHER] / c));
            o.append(String.format(Locale.ROOT, "  per game: landing potential last step %.2f, whole walk %.2f (turns to the next route target saved by the best sampled landing, sites only)%n", pl / c, pf / c));
            o.append(String.format(Locale.ROOT, "  bot: max play() %.2f ms (after turn 0); queenStep calls per turn: %s%n", maxNanos / 1e6,
                counted ? String.format(Locale.ROOT, "mean %.1f, max %d", (double) calls / Math.max(1, callTurns), maxCalls) : "not counted"));
        }
        List<Leg> worst = new ArrayList<>();
        List<Game> owner = new ArrayList<>();
        for (Game g : games) for (Leg L : g.legs) if (L.route && L.arrived && L.excess > 0) { worst.add(L); owner.add(g); }
        Integer[] idx = new Integer[worst.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> worst.get(b).excess != worst.get(a).excess ? worst.get(b).excess - worst.get(a).excess
            : Long.compare(owner.get(a).seed * 2 + owner.get(a).seat, owner.get(b).seed * 2 + owner.get(b).seat));
        o.append("worst route legs by excess:\n");
        for (int i = 0; i < Math.min(5, idx.length); i++) {
            Leg L = worst.get(idx[i]);
            Game g = owner.get(idx[i]);
            o.append(String.format(Locale.ROOT, "  seed %d seat %d turns %d-%d (%d, bound %d, excess %d) target %d from %d,%d dist %.0f slides %d stalls %d%n",
                g.seed, g.seat, L.start, L.end, L.turns, L.bound, L.excess, L.target, L.sx, L.sy, L.dist, L.slides, L.stalls));
        }
        return o.toString();
    }
}
