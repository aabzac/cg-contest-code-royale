package cr.tools;

import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Rules;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Per-player features of recorded CG games: each game is replayed through Rules from its seed with the
 * recorded commands of both players (as cgValidate and cgAttribute do), and the state is sampled.
 * One CSV row per game and seat: result, economy, structures, army and queen HP at fixed turns, what
 * each player built and trained over the game, and (since 2026-11-03) the queen's death turn, the first
 * tower lost, BUILDs on own towers and mines (top-ups and upgrades), the opening (the first three
 * structures) and the summed HP of own towers at the fixed turns.
 * Usage: CgStudy out.csv &lt;replay file or directory&gt;... (files that are not games are skipped)
 */
public final class CgStudy {
    static final int[] AT = {25, 50, 75, 100, 150, 200};

    public static void main(String[] args) throws IOException {
        List<Path> files = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            if (Files.isDirectory(p)) try (Stream<Path> s = Files.walk(p)) { s.filter(f -> f.toString().endsWith(".json")).sorted().forEach(files::add); }
            else files.add(p);
        }
        int games = 0, skipped = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
            out.println(header());
            for (Path f : files) {
                CgReplay r;
                try {
                    r = CgReplay.load(f);
                } catch (RuntimeException | IOException e) {
                    skipped++;
                    continue;
                }
                for (String row : study(r, f)) out.println(row);
                games++;
            }
        }
        System.out.printf(Locale.ROOT, "games %d, skipped files %d -> %s%n", games, skipped, args[0]);
    }

    static String header() {
        StringBuilder b = new StringBuilder("file,gameId,seat,agentId,pseudo,rank,oppRank,score,oppScore,turns");
        for (int t : AT) for (String k : new String[]{"inc", "mines", "towers", "bk", "ba", "bg", "knights", "archers", "giants", "hp", "gold"}) b.append(',').append(k).append('_').append(t);
        b.append(",firstBarracks,firstMine,firstTower,maxIncome,meanIncome,meanGold,minesBuilt,towersBuilt,barracksBuiltK,barracksBuiltA,barracksBuiltG");
        b.append(",trainK,trainA,trainG,knightTurns,archerTurns,giantTurns,maxTowers,structuresLost");
        b.append(",deathTurn,firstTowerLost,towerTopUps,mineUpgrades,opening");
        for (int t : AT) b.append(",thp_").append(t);
        return b.toString();
    }

    static List<String> study(CgReplay r, Path file) {
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        int n = r.turns.size();
        String[][] at = new String[2][AT.length];
        int[] firstB = {-1, -1}, firstM = {-1, -1}, firstT = {-1, -1}, maxInc = new int[2], maxTowers = new int[2], lost = new int[2];
        int[] death = {-1, -1}, firstTowerLost = {-1, -1}, topUps = new int[2], upgrades = new int[2], opened = new int[2];
        StringBuilder[] opening = {new StringBuilder(), new StringBuilder()};
        String[][] towerHpAt = new String[2][AT.length];
        long[] incSum = new long[2], goldSum = new long[2];
        int[][] built = new int[2][5], train = new int[2][3];
        long[][] creepTurns = new long[2][3];
        int[] prevType = new int[s.nSites], prevOwner = new int[s.nSites];
        boolean[] prevTraining = new boolean[s.nSites];
        java.util.Arrays.fill(prevType, Const.NONE);
        java.util.Arrays.fill(prevOwner, -1);
        int played = 0;
        for (int t = 0; t < n && !s.over; t++) {
            for (int p = 0; p < 2; p++) {
                int inc = 0, mines = 0, towers = 0, towerHp = 0;
                int[] bar = new int[3], creeps = new int[3];
                for (int i = 0; i < s.nSites; i++) {
                    if (s.stOwner[i] != p) continue;
                    if (s.stType[i] == Const.MINE) { mines++; inc += s.mineRate[i]; }
                    else if (s.stType[i] == Const.TOWER) { towers++; towerHp += s.towerHp[i]; }
                    else if (s.stType[i] == Const.BARRACKS) bar[s.barrType[i]]++;
                }
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) creeps[s.cType[i]]++;
                if (firstB[p] < 0 && bar[0] + bar[1] + bar[2] > 0) firstB[p] = t;
                if (firstM[p] < 0 && mines > 0) firstM[p] = t;
                if (firstT[p] < 0 && towers > 0) firstT[p] = t;
                maxInc[p] = Math.max(maxInc[p], inc);
                maxTowers[p] = Math.max(maxTowers[p], towers);
                incSum[p] += inc;
                goldSum[p] += s.gold[p];
                for (int k = 0; k < 3; k++) creepTurns[p][k] += creeps[k];
                for (int a = 0; a < AT.length; a++) if (AT[a] == t) {
                    at[p][a] = String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d", inc, mines, towers, bar[0], bar[1], bar[2], creeps[0], creeps[1], creeps[2], s.hp[p], s.gold[p]);
                    towerHpAt[p][a] = Integer.toString(towerHp);
                }
            }
            // Structures appearing, changing hands or starting to train since the last turn.
            for (int i = 0; i < s.nSites; i++) {
                int ty = s.stType[i], ow = s.stOwner[i];
                if (ty != Const.NONE && (ty != prevType[i] || ow != prevOwner[i])) {
                    if (ty == Const.MINE) built[ow][0]++;
                    else if (ty == Const.TOWER) built[ow][1]++;
                    else built[ow][2 + Math.min(s.barrType[i], 2)]++;
                    if (opened[ow]++ < 3) opening[ow].append(opened[ow] > 1 ? " " : "")
                        .append(ty == Const.MINE ? "M" : ty == Const.TOWER ? "T" : "B" + "KAG".charAt(Math.min(s.barrType[i], 2)));
                    if (prevType[i] != Const.NONE && prevOwner[i] >= 0 && prevOwner[i] != ow) lost[prevOwner[i]]++;
                }
                if (ty == Const.NONE && prevType[i] != Const.NONE && prevOwner[i] >= 0) lost[prevOwner[i]]++;
                if (ty == Const.BARRACKS && s.barrTraining[i] && !(prevTraining[i] && prevType[i] == ty && prevOwner[i] == ow)) train[ow][s.barrType[i]]++;
                if (prevType[i] == Const.TOWER && prevOwner[i] >= 0 && (ty != Const.TOWER || ow != prevOwner[i]) && firstTowerLost[prevOwner[i]] < 0)
                    firstTowerLost[prevOwner[i]] = t;
                prevType[i] = ty;
                prevOwner[i] = ow;
                prevTraining[i] = ty == Const.BARRACKS && s.barrTraining[i];
            }
            String[] a = CgReplay.lines(r.turns.get(t).stdout[0]), b = CgReplay.lines(r.turns.get(t).stdout[1]);
            Command[] cmd = {Command.parse(a[0], a[1], s.nSites), Command.parse(b[0], b[1], s.nSites)};
            for (int p = 0; p < 2; p++) {
                Command c = cmd[p];
                if (c.queen == Command.BUILD && c.site >= 0 && c.site < s.nSites && s.stOwner[c.site] == p && c.structure != null) {
                    if (s.stType[c.site] == Const.TOWER && c.structure.equals("TOWER")) topUps[p]++;
                    else if (s.stType[c.site] == Const.MINE && c.structure.equals("MINE")) upgrades[p]++;
                }
            }
            rules.resolve(s, cmd[0], cmd[1]);
            played = t + 1;
            for (int p = 0; p < 2; p++) if (death[p] < 0 && s.hp[p] <= 0) death[p] = played;
        }
        List<String> rows = new ArrayList<>();
        for (int p = 0; p < 2; p++) {
            StringBuilder row = new StringBuilder();
            row.append(file.toString().replace('\\', '/')).append(',').append(r.gameId).append(',').append(p).append(',').append(r.agentId[p]).append(',')
                .append(r.pseudo[p] == null ? "" : r.pseudo[p].replace(',', ' ')).append(',').append(r.ranks[p]).append(',').append(r.ranks[1 - p]).append(',')
                .append(fmt(r.scores[p])).append(',').append(fmt(r.scores[1 - p])).append(',').append(played);
            for (int a = 0; a < AT.length; a++) row.append(',').append(at[p][a] == null ? ",,,,,,,,,," : at[p][a]);
            row.append(String.format(Locale.ROOT, ",%d,%d,%d,%d,%.2f,%.1f,%d,%d,%d,%d,%d", firstB[p], firstM[p], firstT[p], maxInc[p],
                played == 0 ? 0.0 : (double) incSum[p] / played, played == 0 ? 0.0 : (double) goldSum[p] / played,
                built[p][0], built[p][1], built[p][2], built[p][3], built[p][4]));
            row.append(String.format(Locale.ROOT, ",%d,%d,%d,%d,%d,%d,%d,%d", train[p][0], train[p][1], train[p][2],
                creepTurns[p][0], creepTurns[p][1], creepTurns[p][2], maxTowers[p], lost[p]));
            row.append(String.format(Locale.ROOT, ",%d,%d,%d,%d,%s", death[p], firstTowerLost[p], topUps[p], upgrades[p], opening[p]));
            for (int a = 0; a < AT.length; a++) row.append(',').append(towerHpAt[p][a] == null ? "" : towerHpAt[p][a]);
            rows.add(row.toString());
        }
        return rows;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
