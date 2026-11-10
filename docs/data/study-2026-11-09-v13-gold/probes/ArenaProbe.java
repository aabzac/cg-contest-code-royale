import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Physics;
import cr.core.Rules;
import cr.tools.CgReplay;

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
 * Study probe (2026-11-09, docs/data/study-2026-11-09-v13-gold): recorded CG games replayed through Rules with the
 * recorded commands, both seats traced in one format. Not part of the build; compiled against build/classes/java/main.
 * Writes three csv files into the output directory:
 *   turns.csv  one row per game, seat and turn, on the state before the turn: the queen, her command and its target
 *              (touchKind / targetKind: F free, T own tower, M own mine, B own barracks, E enemy, N none), the HP she loses
 *              on the turn split by source as DeathTrace does (kd knights, td towers), what stands, the enemy knights near
 *              her and their HP, the free sites under her towers' cover, the own towers covering her (towersCov), those
 *              within 400 and 510 of her, and the best cover at a point within 180 and 300 of her (bestCov*);
 *   events.csv structures built and lost, with the cause of a loss (a mine: exhausted, creep, queen, rebuilt; a tower:
 *              melt, giant, rebuilt) and where the site lies;
 *   waves.csv  every release of a barracks (a training started), with the distance to the enemy queen and the HP she
 *              loses to knights in the next 35 turns on turns with the owner's knights within 250 (WaveScan's measure;
 *              the windows of releases that follow each other overlap).
 * Usage: ArenaProbe <agentId,agentId,...> <outDir> <replay files or directories...>
 *        ArenaProbe local <botA> <botB> <maps> <seedBase> <stride> <outDir>   local games of bot A on both seats against
 *        bot B (cr.referee.Referee, in process), traced the same way; the game id is seed * 10 + A's seat, "us" is A;
 *        games.csv lists the results.
 * Build and run (from the repository root, after ./gradlew compileJava; GSON is the gson jar of the Gradle cache):
 *   javac -encoding UTF-8 -cp "build/classes/java/main;$GSON" -d <classes> docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java
 *   java -cp "<classes>;build/classes/java/main;build/resources/main;$GSON" ArenaProbe ...
 */
public final class ArenaProbe {
    static final int WINDOW = 35;

    public static void main(String[] args) throws IOException {
        if (args[0].equals("local")) { local(args); return; }
        Set<Long> agents = new HashSet<>();
        for (String a : args[0].split(",")) agents.add(Long.parseLong(a.trim()));
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        List<Path> files = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            if (Files.isDirectory(p)) try (Stream<Path> s = Files.walk(p)) { s.filter(f -> f.toString().endsWith(".json")).sorted().forEach(files::add); }
            else files.add(p);
        }
        try (PrintWriter turns = new PrintWriter(Files.newBufferedWriter(out.resolve("turns.csv"), StandardCharsets.UTF_8));
             PrintWriter events = new PrintWriter(Files.newBufferedWriter(out.resolve("events.csv"), StandardCharsets.UTF_8));
             PrintWriter waves = new PrintWriter(Files.newBufferedWriter(out.resolve("waves.csv"), StandardCharsets.UTF_8))) {
            headers(turns, events, waves);
            int n = 0;
            for (Path f : files) {
                CgReplay r;
                try { r = CgReplay.load(f); } catch (IOException | RuntimeException e) { System.out.println("skip " + f + ": " + e.getMessage()); continue; }
                int us = agents.contains(r.agentId[0]) ? 0 : agents.contains(r.agentId[1]) ? 1 : -1;
                if (us < 0) continue;
                List<String[]> lines = new ArrayList<>();
                for (CgReplay.Turn t : r.turns) {
                    String[] a = CgReplay.lines(t.stdout[0]), b = CgReplay.lines(t.stdout[1]);
                    lines.add(new String[]{a[0], a[1], b[0], b[1]});
                }
                game(r.gameId, r.seed, lines, us, turns, events, waves);
                n++;
            }
            System.out.println("games " + n + " -> " + out);
        }
    }

    static void headers(PrintWriter turns, PrintWriter events, PrintWriter waves) {
        turns.println("gid,seat,us,t,hp,hpAfter,kd,td,gold,income,qx,qy,cmd,site,what,touch,touchKind,touchHp,targetKind,targetCov,targetDist,"
            + "T,M,B,nearT,nearTHp,k250,k500,k800,nearK,eKnights,oKnights,eGiants,covFree,covFreeGold,nearCovFree,nearFree,minesCov,towersLow,thpSum,qCov,eTow,kHpMax500,kHpSum500,kHpSum250,towersCov,minTHp,minTDist,lowTDist,lowTCovDist,towersIn510,towersIn400,bestCov180,bestCov300,bestCovFull300");
        events.println("gid,seat,us,t,event,kind,cause,site,x,y,homeDist,fwd,gold,maxMine,rate,covered,ownQueenDist,enemyQueenDist,k500");
        waves.println("gid,seat,us,t,type,barracks,dist,fwd,enemyTowers,enemyTowerHp,goldLeft,damage,contactTurns");
    }

    /** Local games: bot A on both seats against bot B; the commands come from the referee's record ("seat queen line | train line"). */
    static void local(String[] args) throws IOException {
        String a = args[1], b = args[2];
        int maps = Integer.parseInt(args[3]);
        long base = Long.parseLong(args[4]), stride = Long.parseLong(args[5]);
        Path out = Paths.get(args[6]);
        Files.createDirectories(out);
        try (PrintWriter turns = new PrintWriter(Files.newBufferedWriter(out.resolve("turns.csv"), StandardCharsets.UTF_8));
             PrintWriter events = new PrintWriter(Files.newBufferedWriter(out.resolve("events.csv"), StandardCharsets.UTF_8));
             PrintWriter waves = new PrintWriter(Files.newBufferedWriter(out.resolve("waves.csv"), StandardCharsets.UTF_8));
             PrintWriter games = new PrintWriter(Files.newBufferedWriter(out.resolve("games.csv"), StandardCharsets.UTF_8))) {
            headers(turns, events, waves);
            games.println("gid,seed,seat,won,draw,turns,score,oppScore");
            for (int m = 0; m < maps; m++) {
                long seed = base + m * stride;
                for (int seat = 0; seat < 2; seat++) {
                    cr.referee.Referee.Result r = seat == 0 ? cr.referee.Referee.play(seed, cr.bot.BotFactory.create(a), cr.bot.BotFactory.create(b), true, 50)
                                                            : cr.referee.Referee.play(seed, cr.bot.BotFactory.create(b), cr.bot.BotFactory.create(a), true, 50);
                    List<String[]> lines = new ArrayList<>();
                    for (int k = 0; k + 1 < r.replay.size(); k += 2) {
                        String[] row = new String[4];
                        for (int p = 0; p < 2; p++) {
                            String e = r.replay.get(k + p);
                            int seatOf = e.charAt(0) - '0';
                            String rest = e.substring(2);
                            int bar = rest.indexOf(" | ");
                            if (bar >= 0) { row[2 * seatOf] = rest.substring(0, bar); row[2 * seatOf + 1] = rest.substring(bar + 3); }
                        }
                        lines.add(row);
                    }
                    long gid = seed * 10 + seat;
                    game(gid, seed, lines, seat, turns, events, waves);
                    games.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d%n", gid, seed, seat, r.winner == seat ? 1 : 0, r.winner < 0 ? 1 : 0, r.turns, r.score[seat], r.score[1 - seat]);
                }
            }
            System.out.println("local " + a + " vs " + b + ": " + maps + " maps -> " + out);
        }
    }

    static double dist(double ax, double ay, double bx, double by) { return Math.sqrt(Physics.distSq(ax, ay, bx, by)); }

    static boolean covered(GameState s, int owner, double x, double y, int except) {
        for (int i = 0; i < s.nSites; i++)
            if (i != except && s.stType[i] == Const.TOWER && s.stOwner[i] == owner && Physics.distSq(s.sx[i], s.sy[i], x, y) < (double) s.towerRange[i] * s.towerRange[i]) return true;
        return false;
    }

    /** Own towers whose range covers the point (full: as if every own tower had 800 HP, a range of about 510). */
    static int coverAt(GameState s, int owner, double x, double y, boolean full) {
        int n = 0;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] != Const.TOWER || s.stOwner[i] != owner) continue;
            double r = full ? 510 : s.towerRange[i];
            if (Physics.distSq(s.sx[i], s.sy[i], x, y) < r * r) n++;
        }
        return n;
    }

    static String kind(GameState s, int me, int site) {
        if (site < 0) return "N";
        if (s.stType[site] == Const.NONE) return "F";
        if (s.stOwner[site] != me) return "E";
        return s.stType[site] == Const.TOWER ? "T" : s.stType[site] == Const.MINE ? "M" : "B";
    }

    /** How far the point lies from the owner's home corner toward the enemy's, 0..1 along the diagonal. */
    static double fwd(int owner, double x, double y) {
        double hx = owner == 0 ? 0 : Const.WIDTH, hy = owner == 0 ? 0 : Const.HEIGHT;
        double ex = Const.WIDTH - hx, ey = Const.HEIGHT - hy;
        double dx = ex - hx, dy = ey - hy;
        return ((x - hx) * dx + (y - hy) * dy) / (dx * dx + dy * dy);
    }

    static void game(long gameId, long seed, List<String[]> lines, int us, PrintWriter turns, PrintWriter events, PrintWriter waves) {
        GameState s = MapGen.generate(seed, 4);
        Rules rules = new Rules();
        int nT = lines.size();
        int[][] kdOf = new int[2][nT + 1];
        boolean[][] foeNear = new boolean[2][nT + 1];
        List<int[]> waveRows = new ArrayList<>();   // seat, turn, type, barracks, dist, fwd*1000, enemyTowers, enemyTowerHp, goldLeft
        for (int t = 0; t < nT && !s.over; t++) {
            String[] l = lines.get(t);
            Command[] cmd = {Command.parse(l[0], l[1], s.nSites), Command.parse(l[2], l[3], s.nSites)};
            GameState pre = s.copy();
            int[] kd = new int[2], td = new int[2];
            for (int me = 0; me < 2; me++) {
                int foe = 1 - me;
                GameState knightsOnly = s.copy(), towersOnly = s.copy();
                for (int i = 0; i < s.nSites; i++)
                    if (knightsOnly.stType[i] == Const.TOWER && knightsOnly.stOwner[i] == foe) knightsOnly.towerRange[i] = 0;
                for (int i = 0; i < towersOnly.nCreeps; i++)
                    if (towersOnly.cOwner[i] == foe && towersOnly.cType[i] == Const.KNIGHT) towersOnly.cHp[i] = 0;
                towersOnly.removeDeadCreeps();
                new Rules().resolve(knightsOnly, cmd[0], cmd[1]);
                new Rules().resolve(towersOnly, cmd[0], cmd[1]);
                kd[me] = pre.hp[me] - knightsOnly.hp[me];
                td[me] = pre.hp[me] - towersOnly.hp[me];
            }
            rules.resolve(s, cmd[0], cmd[1]);
            for (int me = 0; me < 2; me++) {
                kdOf[me][t] = kd[me];
                turnRow(turns, gameId, pre, s, me, us, t, cmd[me], kd[me], td[me]);
                for (int i = 0; i < pre.nCreeps; i++)
                    if (pre.cOwner[i] == 1 - me && pre.cType[i] == Const.KNIGHT && Physics.distSq(pre.cx[i], pre.cy[i], pre.qx[me], pre.qy[me]) < 250.0 * 250.0) foeNear[me][t] = true;
            }
            // Structures built and lost on this turn.
            for (int i = 0; i < s.nSites; i++) {
                int bt = pre.stType[i], bo = pre.stOwner[i], at = s.stType[i], ao = s.stOwner[i];
                if (bt == at && bo == ao) continue;
                if (bt != Const.NONE) {
                    String cause;
                    if (bt == Const.MINE) cause = at == Const.NONE && s.sGold[i] <= 0 ? "exhausted" : at != Const.NONE && ao == bo ? "rebuilt" : nearEnemyQueen(s, bo, i) ? "queen" : "creep";
                    else if (bt == Const.TOWER) cause = at != Const.NONE && ao == bo ? "rebuilt" : pre.towerHp[i] <= Const.TOWER_MELT_RATE ? "melt" : "giant";
                    else cause = at != Const.NONE && ao == bo ? "rebuilt" : "queen";
                    event(events, gameId, pre, bo, us, t, "lost", structName(bt, pre.barrType[i]), cause, i, pre);
                }
                if (at != Const.NONE) event(events, gameId, pre, ao, us, t, "built", structName(at, s.barrType[i]), bt == Const.NONE ? "free" : bo == ao ? "over-own" : "over-enemy", i, s);
            }
            // Releases: a barracks that starts training on this turn.
            for (int p = 0; p < 2; p++) {
                for (int type = 0; type < 3; type++) {
                    int nb = 0;
                    double best = Double.MAX_VALUE, bf = 0;
                    for (int i = 0; i < s.nSites; i++) {
                        if (s.stType[i] != Const.BARRACKS || s.stOwner[i] != p || s.barrType[i] != type) continue;
                        // A training started this turn: TRAIN is refused while a barracks trains, and a training never ends
                        // on the turn it starts, so "trains now and did not before" is the start.
                        boolean wasTraining = pre.stType[i] == Const.BARRACKS && pre.stOwner[i] == p && pre.barrTraining[i];
                        if (wasTraining || !s.barrTraining[i]) continue;
                        nb++;
                        double d = dist(s.sx[i], s.sy[i], pre.qx[1 - p], pre.qy[1 - p]);
                        if (d < best) { best = d; bf = fwd(p, s.sx[i], s.sy[i]); }
                    }
                    if (nb == 0) continue;
                    int et = 0, eh = 0;
                    for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.TOWER && s.stOwner[i] == 1 - p) { et++; eh += s.towerHp[i]; }
                    waveRows.add(new int[]{p, t, type, nb, (int) Math.round(best), (int) Math.round(bf * 1000), et, eh, s.gold[p]});
                }
            }
        }
        for (int[] w : waveRows) {
            int p = w[0], t = w[1], foe = 1 - p, dmg = 0, contact = 0;
            for (int k = t + 1; k <= t + WINDOW && k < nT; k++) if (foeNear[foe][k]) { dmg += kdOf[foe][k]; contact++; }
            waves.printf(Locale.ROOT, "%d,%d,%d,%d,%s,%d,%d,%.3f,%d,%d,%d,%d,%d%n", gameId, p, p == us ? 1 : 0, t, Const.CREEP_NAME[w[2]].charAt(0), w[3], w[4], w[5] / 1000.0, w[6], w[7], w[8], dmg, contact);
        }
    }

    static String structName(int type, int barrType) {
        return type == Const.MINE ? "MINE" : type == Const.TOWER ? "TOWER" : "BARRACKS-" + Const.CREEP_NAME[barrType].charAt(0);
    }

    /** The enemy queen touches the site after the turn (she tears down mines and barracks she touches). */
    static boolean nearEnemyQueen(GameState post, int owner, int site) {
        int e = 1 - owner;
        return dist(post.sx[site], post.sy[site], post.qx[e], post.qy[e]) < post.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA + 15;
    }

    static void event(PrintWriter out, long gameId, GameState pre, int owner, int us, int t, String ev, String kind, String cause, int i, GameState goldState) {
        double hx = owner == 0 ? 0 : Const.WIDTH, hy = owner == 0 ? 0 : Const.HEIGHT;
        int k500 = 0;
        for (int c = 0; c < pre.nCreeps; c++)
            if (pre.cOwner[c] == 1 - owner && pre.cType[c] == Const.KNIGHT && Physics.distSq(pre.cx[c], pre.cy[c], pre.qx[owner], pre.qy[owner]) < 500.0 * 500.0) k500++;
        out.printf(Locale.ROOT, "%d,%d,%d,%d,%s,%s,%s,%d,%.0f,%.0f,%.0f,%.3f,%d,%d,%d,%d,%.0f,%.0f,%d%n", gameId, owner, owner == us ? 1 : 0, t, ev, kind, cause, i,
            pre.sx[i], pre.sy[i], dist(pre.sx[i], pre.sy[i], hx, hy), fwd(owner, pre.sx[i], pre.sy[i]), goldState.sGold[i], pre.sMaxMine[i], pre.mineRate[i],
            covered(pre, owner, pre.sx[i], pre.sy[i], i) ? 1 : 0, dist(pre.sx[i], pre.sy[i], pre.qx[owner], pre.qy[owner]), dist(pre.sx[i], pre.sy[i], pre.qx[1 - owner], pre.qy[1 - owner]), k500);
    }

    static void turnRow(PrintWriter out, long gameId, GameState s, GameState post, int me, int us, int t, Command c, int kd, int td) {
        int foe = 1 - me;
        double qx = s.qx[me], qy = s.qy[me];
        int towers = 0, mines = 0, barracks = 0, income = 0, low = 0, thp = 0, eTow = 0, minesCov = 0;
        double nearT = -1, minTDist = -1, lowTDist = -1, lowTCovDist = -1;
        int nearTHp = -1, minTHp = -1;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stOwner[i] == me) {
                if (s.stType[i] == Const.TOWER) {
                    towers++;
                    thp += s.towerHp[i];
                    if (s.towerHp[i] < 700) low++;
                    double d = dist(s.sx[i], s.sy[i], qx, qy);
                    if (nearT < 0 || d < nearT) { nearT = d; nearTHp = s.towerHp[i]; }
                    if (minTHp < 0 || s.towerHp[i] < minTHp) { minTHp = s.towerHp[i]; minTDist = d; }
                    if (s.towerHp[i] < 600 && (lowTDist < 0 || d < lowTDist)) lowTDist = d;
                    // A low tower she can walk to inside her towers' cover: its site lies in another own tower's range.
                    if (s.towerHp[i] < 600 && covered(s, me, s.sx[i], s.sy[i], i) && (lowTCovDist < 0 || d < lowTCovDist)) lowTCovDist = d;
                } else if (s.stType[i] == Const.MINE) {
                    mines++;
                    income += s.mineRate[i];
                    if (covered(s, me, s.sx[i], s.sy[i], -1)) minesCov++;
                } else if (s.stType[i] == Const.BARRACKS) barracks++;
            } else if (s.stOwner[i] == foe && s.stType[i] == Const.TOWER && Physics.distSq(s.sx[i], s.sy[i], qx, qy) < (double) s.towerRange[i] * s.towerRange[i]) eTow++;
        }
        int k250 = 0, k500 = 0, k800 = 0, eKnights = 0, oKnights = 0, eGiants = 0, kHpMax500 = 0, kHpSum500 = 0, kHpSum250 = 0, towersCov = 0, towersIn510 = 0, towersIn400 = 0;
        double nearK = -1;
        for (int i = 0; i < s.nSites; i++)
            if (s.stType[i] == Const.TOWER && s.stOwner[i] == me) {
                double d2 = Physics.distSq(s.sx[i], s.sy[i], qx, qy);
                if (d2 < (double) s.towerRange[i] * s.towerRange[i]) towersCov++;
                // The towers that would cover her at full HP (800 HP on a 60-90 site: a range of about 510) and at 500 HP (about 400).
                if (d2 < 510.0 * 510.0) towersIn510++;
                if (d2 < 400.0 * 400.0) towersIn400++;
            }
        for (int i = 0; i < s.nCreeps; i++) {
            if (s.cOwner[i] == me) { if (s.cType[i] == Const.KNIGHT) oKnights++; continue; }
            if (s.cType[i] == Const.GIANT) eGiants++;
            if (s.cType[i] != Const.KNIGHT) continue;
            eKnights++;
            double d = dist(s.cx[i], s.cy[i], qx, qy);
            if (d < 250) { k250++; kHpSum250 += s.cHp[i]; }
            if (d < 500) { k500++; kHpSum500 += s.cHp[i]; kHpMax500 = Math.max(kHpMax500, s.cHp[i]); }
            if (d < 800) k800++;
            if (nearK < 0 || d < nearK) nearK = d;
        }
        int touched = -1;
        for (int i = 0; i < s.nSites; i++)
            if (Physics.lessInt(Physics.distSq(s.sx[i], s.sy[i], qx, qy), s.sr[i] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA)) { touched = i; break; }
        int covFree = 0, covFreeGold = 0;
        double nearCovFree = -1, nearFree = -1;
        for (int i = 0; i < s.nSites; i++) {
            if (s.stType[i] != Const.NONE || covered(s, foe, s.sx[i], s.sy[i], -1)) continue;
            double d = dist(s.sx[i], s.sy[i], qx, qy);
            if (nearFree < 0 || d < nearFree) nearFree = d;
            if (!covered(s, me, s.sx[i], s.sy[i], -1)) continue;
            covFree++;
            if (s.sGold[i] > 0) covFreeGold++;
            if (nearCovFree < 0 || d < nearCovFree) nearCovFree = d;
        }
        // The most own towers covering one point she could stand on within 180 (three steps) and within 300 (five) of her,
        // outside the enemy towers' ranges; and the same within 300 if every own tower had its full range (510).
        int bestCov180 = coverAt(s, me, qx, qy, false), bestCov300 = bestCov180, bestCovFull300 = coverAt(s, me, qx, qy, true);
        for (int ring = 1; ring <= 5; ring++) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8, x = qx + 60 * ring * Math.cos(a), y = qy + 60 * ring * Math.sin(a);
                if (x < 0 || y < 0 || x > Const.WIDTH || y > Const.HEIGHT || covered(s, foe, x, y, -1)) continue;
                int cnow = coverAt(s, me, x, y, false);
                if (ring <= 3) bestCov180 = Math.max(bestCov180, cnow);
                bestCov300 = Math.max(bestCov300, cnow);
                bestCovFull300 = Math.max(bestCovFull300, coverAt(s, me, x, y, true));
            }
        }
        String cmd = c.queen == Command.BUILD ? "B" : c.queen == Command.MOVE ? "M" : "W";
        int site = c.queen == Command.BUILD ? c.site : -1;
        String what = c.queen == Command.BUILD ? c.structure : c.queen == Command.MOVE ? c.x + ":" + c.y : "";
        out.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.0f,%.0f,%s,%d,%s,%d,%s,%d,%s,%d,%.0f,%d,%d,%d,%.0f,%d,%d,%d,%d,%.0f,%d,%d,%d,%d,%d,%.0f,%.0f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.0f,%.0f,%.0f,%d,%d,%d,%d,%d%n",
            gameId, me, me == us ? 1 : 0, t, s.hp[me], post.hp[me], kd, td, s.gold[me], income, qx, qy, cmd, site, what, touched, kind(s, me, touched),
            touched >= 0 && s.stType[touched] == Const.TOWER && s.stOwner[touched] == me ? s.towerHp[touched] : -1,
            kind(s, me, site), site >= 0 && covered(s, me, s.sx[site], s.sy[site], site) ? 1 : 0, site >= 0 ? dist(s.sx[site], s.sy[site], qx, qy) : -1.0,
            towers, mines, barracks, nearT, nearTHp, k250, k500, k800, nearK, eKnights, oKnights, eGiants, covFree, covFreeGold, nearCovFree, nearFree, minesCov, low, thp,
            covered(s, me, qx, qy, -1) ? 1 : 0, eTow, kHpMax500, kHpSum500, kHpSum250, towersCov, minTHp, minTDist, lowTDist, lowTCovDist, towersIn510, towersIn400, bestCov180, bestCov300, bestCovFull300);
    }
}
