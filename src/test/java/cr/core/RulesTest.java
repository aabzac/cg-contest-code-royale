package cr.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    /**
     * Physics.queenStep, the bots' prediction of a walk among the sites, lands where Rules.resolve
     * puts a queen alone on the board (turn 0: no creeps, no structures), from random starts to
     * random targets, including steps that sites cut short.
     */
    @Test
    void queenStepPredictsTheRules() {
        java.util.Random rnd = new java.util.Random(7);
        Physics ph = new Physics();
        int compared = 0, blocked = 0;
        for (long seed : new long[]{10394, 10477, 277743146L}) {
            for (int k = 0; k < 400; k++) {
                GameState s = MapGen.generate(seed, 4);
                int x = 30 + rnd.nextInt(Const.WIDTH - 60), y = 30 + rnd.nextInt(Const.HEIGHT - 60);
                if (Physics.distSq(x, y, s.qx[1], s.qy[1]) < 250 * 250) continue;
                int tx = rnd.nextInt(Const.WIDTH + 1), ty = rnd.nextInt(Const.HEIGHT + 1);
                s.qx[0] = x;
                s.qy[0] = y;
                ph.queenStep(s, x, y, tx, ty, 5);
                double px = ph.rx, py = ph.ry;
                ph.towards(x, y, tx, ty, Const.QUEEN_SPEED);
                if (Math.round(ph.rx) != px || Math.round(ph.ry) != py) blocked++;
                new Rules().resolve(s, Command.move(tx, ty), Command.waitCmd());
                assertEquals(px, s.qx[0], "seed " + seed + " from " + x + "," + y + " to " + tx + "," + ty);
                assertEquals(py, s.qy[0], "seed " + seed + " from " + x + "," + y + " to " + tx + "," + ty);
                compared++;
            }
        }
        assertTrue(compared > 1000, "compared " + compared);
        assertTrue(blocked > 50, "steps cut short by a site: " + blocked);
    }

    @Test
    void kotlinSplitKeepsEmptyTokens() {
        assertEquals(List.of("TRAIN", ""), Command.split("TRAIN "));
        assertEquals(List.of("MOVE", "", "1", "2"), Command.split("MOVE  1 2"));
    }

    @Test
    void parseKillsLikeTheReferee() {
        assertTrue(Command.parse("WAIT", "TRAIN ", 24).kill);
        assertTrue(Command.parse("WAIT", "train", 24).kill);
        assertTrue(Command.parse("MOVE 1 2 3", "TRAIN", 24).kill);
        assertTrue(Command.parse("MOVE 1", "TRAIN", 24).kill);
        assertTrue(Command.parse("BUILD 24 MINE", "TRAIN", 24).kill);
        assertTrue(Command.parse("WAIT", "TRAIN 3 99", 24).kill);
        assertTrue(Command.parse(null, null, 24).kill);
        Command c = Command.parse("  BUILD 3 TOWER x ", "TRAIN 1 2", 24);
        assertFalse(c.kill);
        assertTrue(c.extraTokens);
        assertArrayEquals(new int[]{1, 2}, c.train);
    }

    /** Sites of CG game 909614601 (seed 277743146), read from its replay init frame. */
    @Test
    void mapGeneratorMatchesReplay() {
        GameState s = MapGen.generate(277743146L, 4);
        assertEquals(24, s.nSites);
        assertEquals(167, s.sx[12]); assertEquals(175, s.sy[12]); assertEquals(77, s.sr[12]);
        assertEquals(1753, s.sx[13]); assertEquals(825, s.sy[13]); assertEquals(77, s.sr[13]);
        assertEquals(343, s.sx[4]); assertEquals(362, s.sy[4]); assertEquals(80, s.sr[4]);
        assertEquals(70, s.hp[0]);
        assertEquals(70, s.hp[1]);
    }

    private static GameState emptyState() {
        GameState s = MapGen.generate(277743146L, 4);
        return s;
    }

    @Test
    void towerActsOnTheTurnItIsBuilt() {
        GameState s = emptyState();
        // queen 0 next to site 4 (343,362 r80)
        s.qx[0] = 343; s.qy[0] = 362 - 80 - 30 - 1;
        new Rules().resolve(s, Command.build(4, "TOWER"), Command.waitCmd());
        assertEquals(Const.TOWER, s.stType[4]);
        assertEquals(196, s.towerHp[4]);
        assertEquals((int) Math.sqrt((196 * 1000 + Math.PI * 80 * 80) / Math.PI), s.towerRange[4]);
    }

    @Test
    void mineIncomeStartsTheTurnItIsBuilt() {
        GameState s = emptyState();
        s.qx[0] = 167; s.qy[0] = 175 + 77 + 30 + 1;
        int gold = s.gold[0];
        new Rules().resolve(s, Command.build(12, "MINE"), Command.waitCmd());
        assertEquals(gold + 1, s.gold[0]);
        assertEquals(1, s.mineRate[12]);
    }

    @Test
    void buildOutOfRangeMovesTheQueen() {
        GameState s = emptyState();
        double x = s.qx[0], y = s.qy[0];
        new Rules().resolve(s, Command.build(13, "MINE"), Command.waitCmd());
        assertEquals(Const.NONE, s.stType[13]);
        double before = Math.hypot(s.sx[13] - x, s.sy[13] - y), after = Math.hypot(s.sx[13] - s.qx[0], s.sy[13] - s.qy[0]);
        // 60 towards the site, then collisions with sites may push the queen sideways
        assertTrue(before - after > 40 && Math.hypot(s.qx[0] - x, s.qy[0] - y) <= 61, before + " -> " + after);
    }

    @Test
    void invalidStructureKillsOnlyWhenBuildExecutes() {
        GameState s = emptyState();
        new Rules().resolve(s, Command.build(13, "FOO"), Command.waitCmd());
        assertFalse(s.over, "out of range: queen just moves");
        s.qx[0] = 167; s.qy[0] = 175 + 77 + 30 + 1;
        new Rules().resolve(s, Command.build(12, "FOO"), Command.waitCmd());
        assertTrue(s.over);
        assertEquals(1, s.winner());
    }

    /** Live: tower damage is MIN + (range - distance from the site centre) / 200; the repository measures from the edge. */
    @Test
    void towerDamageCountsFromSiteCentre() {
        // Tower on site 4 (343,362 r80); queen 1 at 111 from its centre, her archer at 167.
        GameState s = emptyState();
        s.stType[4] = Const.TOWER; s.stOwner[4] = 0; s.towerHp[4] = 800; s.towerRange[4] = 111 + 150;
        s.qx[1] = 343; s.qy[1] = 473;
        int queenHp = s.hp[1];
        new Rules().resolve(s, Command.waitCmd(), Command.waitCmd());
        assertEquals(queenHp - 1, s.hp[1], "1 + 150/200; from the edge 1 + 230/200 = 2");

        s = emptyState();
        s.stType[4] = Const.TOWER; s.stOwner[4] = 0; s.towerHp[4] = 800; s.towerRange[4] = 167 + 150;
        s.qx[1] = 343; s.qy[1] = 473;
        s.cHp[s.addCreep(1, Const.ARCHER, 343, 529)] = Const.CREEP_HP[Const.ARCHER];
        new Rules().resolve(s, Command.waitCmd(), Command.waitCmd());
        assertEquals(queenHp, s.hp[1], "the archer is the closest target");
        assertEquals(Const.CREEP_HP[Const.ARCHER] - 1 - 3, s.cHp[0], "aging 1, then 3 + 150/200; from the edge 3 + 230/200 = 4");
    }

    /** CG game 892851569 (seed 160691458) turn 8: player 1's knight barracks on site 5 completes. */
    private static GameState spawnTurn892851569() {
        GameState s = MapGen.generate(160691458L, 4);
        assertEquals(1477, s.sx[5]); assertEquals(836, s.sy[5]); assertEquals(74, s.sr[5]);
        s.qx[0] = 246; s.qy[0] = 319;
        s.qx[1] = 1647; s.qy[1] = 744;
        s.stType[5] = Const.BARRACKS; s.stOwner[5] = 1; s.barrType[5] = Const.KNIGHT;
        s.barrTraining[5] = true; s.barrProgress[5] = Const.CREEP_BUILD_TIME[Const.KNIGHT] - 1;
        return s;
    }

    @Test
    void spawnUsesLiveCornerOffsets() {
        GameState s = spawnTurn892851569();
        new Rules().resolve(s, Command.waitCmd(), Command.waitCmd());
        // Replay frame, creation order: centre + (1,-1), (-1,1), (1,1), (-1,-1), 30 towards the
        // enemy queen, then collisions push the knights out of the barracks.
        int[][] exp = {{1476, 740}, {1385, 860}, {1435, 750}, {1387, 805}};
        assertEquals(4, s.nCreeps);
        for (int k = 0; k < 4; k++) {
            assertEquals(1, s.cOwner[k]);
            assertEquals(exp[k][0], s.cx[k], "x of knight " + k);
            assertEquals(exp[k][1], s.cy[k], "y of knight " + k);
            assertEquals(Const.CREEP_HP[Const.KNIGHT], s.cHp[k]);
        }
    }

    @Test
    void towersDoNotShootCreepsSpawnedThisTurn() {
        GameState s = spawnTurn892851569();
        // Enemy tower on site 19 (after site 5 in site order) covers the barracks.
        s.stType[19] = Const.TOWER; s.stOwner[19] = 0; s.towerHp[19] = 400;
        s.towerRange[19] = (int) Math.sqrt((400 * Const.TOWER_COVERAGE_PER_HP + s.sArea[19]) / Math.PI);
        int queenHp = s.hp[1];
        new Rules().resolve(s, Command.waitCmd(), Command.waitCmd());
        assertEquals(4, s.nCreeps);
        for (int k = 0; k < 4; k++) assertEquals(Const.CREEP_HP[Const.KNIGHT], s.cHp[k], "knight " + k);
        assertTrue(s.hp[1] < queenHp, "the tower shoots the queen instead");
    }

    /** Retained Kotlin behaviour; the statement says dead creeps are removed after the towers shoot. */
    @Test
    void deadCreepsStayTargetableUntilEndOfTurn() {
        for (boolean knight : new boolean[]{true, false}) {
            GameState s = emptyState();
            for (int site : new int[]{4, 12}) { s.stType[site] = Const.TOWER; s.stOwner[site] = 0; s.towerHp[site] = 800; s.towerRange[site] = 2000; }
            if (knight) s.cHp[s.addCreep(1, Const.KNIGHT, 960, 500)] = 2;
            int queenHp = s.hp[1];
            new Rules().resolve(s, Command.waitCmd(), Command.waitCmd());
            assertEquals(0, s.nCreeps);
            if (knight) assertEquals(queenHp, s.hp[1], "tower 12 fires at the knight tower 4 killed");
            else assertTrue(s.hp[1] < queenHp, "without creeps the towers reach queen 1");
        }
    }

    /** Player 1's knight barracks on site 5 (1577,638 r80) completes; optionally a dying player 0 giant where the knights leave the site. */
    private static double[] knightsSpawnedNextTo(boolean dyingGiant) {
        GameState s = emptyState();
        s.stType[5] = Const.BARRACKS; s.stOwner[5] = 1; s.barrType[5] = Const.KNIGHT;
        s.barrTraining[5] = true; s.barrProgress[5] = Const.CREEP_BUILD_TIME[Const.KNIGHT] - 1;
        // No enemy towers, so the giant stands still; aging takes its last HP this turn.
        if (dyingGiant) s.cHp[s.addCreep(0, Const.GIANT, 1460, 603)] = 1;
        new Rules().resolve(s, Command.waitCmd(), Command.waitCmd());
        assertEquals(4, s.nCreeps);
        double[] r = new double[8];
        for (int k = 0; k < 4; k++) { assertEquals(1, s.cOwner[k]); r[2 * k] = s.cx[k]; r[2 * k + 1] = s.cy[k]; }
        return r;
    }

    /** Retained Kotlin behaviour (replays 910380279 t56, 895529937 t39); the statement removes dead creeps before the spawns. */
    @Test
    void creepsKilledThisTurnTakePartInSpawnCollisions() {
        assertFalse(java.util.Arrays.equals(knightsSpawnedNextTo(false), knightsSpawnedNextTo(true)),
            "the dead giant still pushes the new knights in the spawn's fixCollisions");
    }

    /** Start of a replay turn: generated map, queen positions, creeps {owner, type, x, y, hp}. */
    private static GameState replayTurn(long seed, int[] queens, int[][] creeps) {
        GameState s = MapGen.generate(seed, 4);
        s.qx[0] = queens[0]; s.qy[0] = queens[1]; s.qx[1] = queens[2]; s.qy[1] = queens[3];
        for (int[] c : creeps) s.cHp[s.addCreep(c[0], c[1], c[2], c[3])] = c[4];
        return s;
    }

    /**
     * CG game 892855131 (seed 316391638) turn 26: queen 0 is pushed out of site 6, then by an
     * enemy knight. Live collision order (player 0 creeps, queen 0, player 1 creeps, queen 1,
     * sites) gives the replay's (613,457); the repository order gives (613,456).
     */
    @Test
    void liveCollisionOrderQueenPushedByKnight() {
        GameState s = replayTurn(316391638L, new int[]{606, 449, 1364, 515}, new int[][]{{1, Const.KNIGHT, 625, 400, 4}});
        new Rules().resolve(s, Command.build(6, "MINE"), Command.build(7, "MINE"));
        assertEquals(613, s.qx[0]); assertEquals(457, s.qy[0]);
        assertEquals(1307, s.qx[1]); assertEquals(534, s.qy[1]);
    }

    /** CG game 892851569 (seed 160691458) turn 24: seven knights, three of them on queen 0. */
    @Test
    void liveCollisionOrderKnightCrowd() {
        GameState s = replayTurn(160691458L, new int[]{415, 329, 1413, 380}, new int[][]{
            {1, Const.KNIGHT, 473, 319, 10}, {1, Const.KNIGHT, 412, 275, 2}, {1, Const.KNIGHT, 451, 285, 10},
            {1, Const.KNIGHT, 1482, 740, 25}, {1, Const.KNIGHT, 1385, 858, 25}, {1, Const.KNIGHT, 1440, 747, 25},
            {1, Const.KNIGHT, 1388, 803, 25}});
        new Rules().resolve(s, Command.build(18, "MINE"), Command.build(15, "MINE"));
        int[][] replay = {{456, 321}, {389, 289}, {431, 293}, {1388, 705}, {1297, 811}, {1347, 710}, {1306, 766}};
        assertEquals(replay.length, s.nCreeps);
        for (int i = 0; i < replay.length; i++) {
            assertEquals(replay[i][0], s.cx[i], "creep " + i + " x");
            assertEquals(replay[i][1], s.cy[i], "creep " + i + " y");
        }
        assertEquals(401, s.qx[0]); assertEquals(341, s.qy[0]);
    }

    @Test
    void liveCreepLimitRefusesTraining() {
        for (int knights : new int[]{27, 26}) {
            GameState s = emptyState();
            s.stType[3] = Const.BARRACKS; s.stOwner[3] = 1; s.barrType[3] = Const.KNIGHT;
            s.gold[1] = 200;
            for (int i = 0; i < knights; i++) s.addCreep(1, Const.KNIGHT, 1700, 100 + 30 * i);
            Rules r = new Rules();
            r.resolve(s, Command.waitCmd(), Command.waitCmd().train(3));
            if (knights == 27) {
                assertEquals("Training too many creeps (31 total units requested; 30 maximum)", r.lastWarning[1]);
                assertEquals(200, s.gold[1]);
                assertFalse(s.barrTraining[3]);
            } else {
                assertNull(r.lastWarning[1]);
                assertEquals(120, s.gold[1]);
                assertTrue(s.barrTraining[3]);
            }
        }
    }

    @Test
    void liveCreepLimitWarnsOnEmptyTrain() {
        GameState s = emptyState();
        for (int i = 0; i < 31; i++) s.addCreep(1, Const.KNIGHT, 1700, 100 + 25 * i);
        Rules r = new Rules();
        r.resolve(s, Command.waitCmd(), Command.waitCmd());
        assertEquals("Training too many creeps (31 total units requested; 30 maximum)", r.lastWarning[1]);
    }

    @Test
    void warningsAccumulatePerPlayer() {
        GameState s = emptyState();
        s.stType[4] = Const.TOWER; s.stOwner[4] = 0; s.towerHp[4] = 200;
        s.qx[1] = 343; s.qy[1] = 362 + 80 + 30 + 1;
        Rules r = new Rules();
        r.resolve(s, Command.waitCmd(), Command.build(4, "MINE").train(3));
        assertEquals("Cannot spawn from 3: not a barracks\nCannot build: owned by enemy player", r.lastWarning[1]);
        assertNull(r.lastWarning[0]);
    }

    /**
     * The build marker (CGRunner.marker) on turn 0: a warning about its first site, nothing trained and
     * no gold spent. Live replays show this for single-site TRAINs of sites without barracks, and the
     * first offending site of multi-site TRAINs for barracks in training; eight sites without barracks
     * follow the referee's per-site order and are checked live by the first marked upload.
     */
    @Test
    void theTurnZeroBuildMarkerOnlyWarns() {
        GameState marked = MapGen.generate(1, 4), plain = MapGen.generate(1, 4);
        Command m = Command.parse("WAIT", "TRAIN 5 11 10 12 14 6 8 9", marked.nSites);
        assertFalse(m.kill, m.error);
        Rules r = new Rules();
        r.resolve(marked, m, Command.parse("WAIT", "TRAIN", marked.nSites));
        assertEquals("Cannot spawn from 5: not a barracks", r.lastWarning[0]);
        assertNull(r.lastKill[0]);
        new Rules().resolve(plain, Command.parse("WAIT", "TRAIN", plain.nSites), Command.parse("WAIT", "TRAIN", plain.nSites));
        assertEquals(plain.toString(), marked.toString());
    }

    /** Both queens touch empty site 4 (343,362 r80) and build towers; towers survive queen contact. */
    private static int conflictWinner(int turnsPlayed) {
        GameState s = emptyState();
        s.turn = turnsPlayed;
        s.qx[0] = 343; s.qy[0] = 362 - 80 - 30 - 1;
        s.qx[1] = 343; s.qy[1] = 362 + 80 + 30 + 1;
        new Rules().resolve(s, Command.build(4, "TOWER"), Command.build(4, "TOWER"));
        assertEquals(Const.TOWER, s.stType[4]);
        return s.stOwner[4];
    }

    /** Live parity (replays/cg2): player 1 wins a build conflict on odd 1-based turns, player 0 on even. */
    @Test
    void buildConflictParity() {
        assertEquals(1, conflictWinner(0), "turn 1");
        assertEquals(0, conflictWinner(1), "turn 2");
        assertEquals(1, conflictWinner(118), "turn 119");
        assertEquals(0, conflictWinner(119), "turn 120");
    }

    @Test
    void inputRoundTrip() throws IOException {
        GameState s = emptyState();
        String text = Protocol.initInput(s) + Protocol.turnInput(s, 1);
        InputReader r = new InputReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)));
        r.readInit();
        r.readTurn();
        // player 1's view: itself is player 0
        assertEquals(Math.round(s.qx[1]), r.state.qx[0]);
        assertEquals(Math.round(s.qy[0]), r.state.qy[1]);
        assertEquals(s.gold[1], r.state.gold[0]);
        assertEquals(s.sr[7], r.state.sr[7]);
    }

    /** The bot's state carries the referee's turn counter (Rules uses it for the build-conflict parity). */
    @Test
    void inputCountsTurnsLikeTheReferee() throws IOException {
        GameState s = MapGen.generate(7, 4);
        Rules rules = new Rules();
        StringBuilder text = new StringBuilder(Protocol.initInput(s));
        int[] turns = new int[5];
        for (int k = 0; k < turns.length; k++) {
            turns[k] = s.turn;
            text.append(Protocol.turnInput(s, 1));
            rules.resolve(s, Command.waitCmd(), Command.waitCmd());
        }
        InputReader r = new InputReader(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.US_ASCII)));
        r.readInit();
        for (int turn : turns) {
            r.readTurn();
            assertEquals(turn, r.state.turn);
        }
    }
}
