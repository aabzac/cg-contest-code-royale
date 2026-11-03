package cr.bot.current;

import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Protocol;
import cr.core.Rules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The strategy parameters of 2026-11-03 (MINES_FIRST, TOWER_HP, UPGRADE), on a small built position:
 * the queen at home, a gold site A next to her, a site B without gold and a site C further away.
 * The defaults play as v05 ({@link MainBotTest#withDefaultsCurrentPlaysAsV05}), MINES_FIRST=0 as v04.
 */
class StrategyParamsTest {
    private static void site(GameState s, int id, int x, int y, int gold, int maxMine) {
        s.nSites = Math.max(s.nSites, id + 1);
        s.sx[id] = x; s.sy[id] = y; s.sr[id] = 70;
        s.sArea[id] = Math.PI * 70 * 70;
        s.sGold[id] = gold; s.sMaxMine[id] = maxMine;
        s.clearStructure(id);
    }

    /** A (gold) at 350,350, B (no gold) at 680,160, C (gold) at 300,700; the queen at 500,350 with 100 gold. */
    private static GameState position() {
        GameState s = new GameState();
        site(s, 0, 350, 350, 300, 3);
        site(s, 1, 680, 160, 0, 0);
        site(s, 2, 300, 700, 300, 3);
        s.qx[0] = 500; s.qy[0] = 350;
        s.qx[1] = 1800; s.qy[1] = 900;
        for (int p = 0; p < 2; p++) { s.active[p] = true; s.hp[p] = s.score[p] = 100; }
        s.gold[0] = 100; s.gold[1] = 100;
        return s;
    }

    /** Plays the bot alone (the opponent waits) for turns and returns the structures it built, as "site:type" in order. */
    private static String builds(GameState s, String params, int turns) {
        MainBot bot = new MainBot(new Params().apply(params));
        Rules rules = new Rules();
        StringBuilder b = new StringBuilder();
        int[] seen = new int[s.nSites];
        java.util.Arrays.fill(seen, Const.NONE);
        for (int t = 0; t < turns; t++) {
            Command c = bot.play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
            assertFalse(c.kill);
            rules.resolve(s, c, Command.waitCmd());
            assertNull(rules.lastKill[0]);
            for (int i = 0; i < s.nSites; i++) {
                int ty = s.stOwner[i] == 0 ? s.stType[i] : Const.NONE;
                if (ty != seen[i] && ty != Const.NONE) b.append(b.length() > 0 ? " " : "").append(i).append(':').append(ty == Const.MINE ? "M" : ty == Const.TOWER ? "T" : "B");
                seen[i] = ty;
            }
        }
        return b.toString();
    }

    @Test
    void minesFirstOpensWithAMineThenTheBarracks() {
        assertTrue(builds(position(), "MINES_FIRST=0", 20).startsWith("0:B 2:M"), "v04: the barracks at the nearest site, then a mine");
        assertTrue(builds(position(), "MINES_FIRST=1", 20).startsWith("0:M 1:B"), "a mine at A, then the barracks at the next site");
        assertTrue(builds(position(), "MINES_FIRST=2", 30).startsWith("0:M 2:M 1:B"), "two mines, then the barracks");
    }

    /** With no gold site known, the mines-first phase gives way to the ladder instead of idling. */
    @Test
    void minesFirstWithoutAGoldSiteBuildsTheBarracks() {
        GameState s = position();
        s.sGold[0] = 0; s.sGold[2] = -1;
        assertEquals("0:B", builds(s, "MINES_FIRST=1", 10));
    }

    @Test
    void towerHpIsTheTopUpThreshold() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 500; s.towerRange[0] = 300;
        s.stType[1] = Const.BARRACKS; s.stOwner[1] = 0; s.barrType[1] = Const.KNIGHT;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; // touching A
        Command v04 = new MainBot(new Params().apply("")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertEquals("BUILD 0 TOWER", v04.queenLine(), "700: a touched tower at 500 is topped up");
        Command thin = new MainBot(new Params().apply("TOWER_HP=500")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertNotEquals("BUILD 0 TOWER", thin.queenLine(), "500: she walks on");
        s.towerHp[0] = 499;
        Command low = new MainBot(new Params().apply("TOWER_HP=500")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertEquals("BUILD 0 TOWER", low.queenLine());
    }

    /** Income 1 below a target of 2: v04 walks to the free gold site C; with UPGRADE=1 the nearer own mine A is the target. */
    @Test
    void upgradeMakesAnOwnMineBelowItsMaximumATarget() {
        GameState s = position();
        s.stType[0] = Const.MINE; s.stOwner[0] = 0; s.mineRate[0] = 1;
        s.stType[1] = Const.BARRACKS; s.stOwner[1] = 0; s.barrType[1] = Const.KNIGHT;
        s.qx[0] = 600; s.qy[0] = 350;
        String p = "MINES_FIRST=0,INCOME_TARGET=2,MAX_TOWERS=0,ROUTE=0,UNSTICK=0,";
        assertEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "UPGRADE=0")).play(s, -1, Long.MAX_VALUE).queenLine());
        assertEquals("BUILD 0 MINE", new MainBot(new Params().apply(p + "UPGRADE=1")).play(s, -1, Long.MAX_VALUE).queenLine());
        // the next-site prediction agrees: after the upgrade at A the income meets the target, nothing is next
        assertEquals(-1, ApproachPlanner.nextTarget(s, 0, "MINE", 450, 350, 0, 0, new Params().apply(p + "UPGRADE=1")));
        // a mine at its maximum is no target (income 3 below a target of 4: the free gold site C again)
        s.mineRate[0] = 3;
        assertEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "UPGRADE=1,INCOME_TARGET=4")).play(s, -1, Long.MAX_VALUE).queenLine());
    }

    /** One barracks at A, the income met: the second barracks waits for BARRACKS_AFTER_TOWERS own towers, then takes the next free site before further towers. */
    @Test
    void secondBarracksWaitsForTheTowers() {
        GameState s = position();
        s.stType[0] = Const.BARRACKS; s.stOwner[0] = 0; s.barrType[0] = Const.KNIGHT;
        s.qx[0] = 600; s.qy[0] = 350;
        String p = "MINES_FIRST=0,INCOME_TARGET=0,ROUTE=0,UNSTICK=0,BARRACKS=2,";
        assertEquals("BUILD 1 BARRACKS-KNIGHT", new MainBot(new Params().apply(p + "BARRACKS_AFTER_TOWERS=0")).play(s, -1, Long.MAX_VALUE).queenLine(), "no gate: the second barracks at once");
        assertEquals("BUILD 1 TOWER", new MainBot(new Params().apply(p + "BARRACKS_AFTER_TOWERS=1")).play(s, -1, Long.MAX_VALUE).queenLine(), "gate 1: a tower first");
        // the next-site prediction follows the gate: after that tower the barracks is next (gate 1), or nothing (gate 2 with the tower cap met)
        assertEquals(2, ApproachPlanner.nextTarget(s, 1, "TOWER", 650, 200, 0, 0, new Params().apply(p + "MAX_TOWERS=1,BARRACKS_AFTER_TOWERS=1")));
        assertEquals(-1, ApproachPlanner.nextTarget(s, 1, "TOWER", 650, 200, 0, 0, new Params().apply(p + "MAX_TOWERS=1,BARRACKS_AFTER_TOWERS=2")));
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0; s.towerHp[1] = 700; s.towerRange[1] = 300;
        assertEquals("BUILD 2 BARRACKS-KNIGHT", new MainBot(new Params().apply(p + "BARRACKS_AFTER_TOWERS=1")).play(s, -1, Long.MAX_VALUE).queenLine(), "the tower stands: the barracks");
        assertEquals("BUILD 2 TOWER", new MainBot(new Params().apply(p + "BARRACKS=1,BARRACKS_AFTER_TOWERS=1")).play(s, -1, Long.MAX_VALUE).queenLine(), "BARRACKS=1: the gate does nothing");
    }

    /** BARRACKS_FORWARD moves only the second barracks: to the free site nearest to the point that far along home to the enemy's corner. */
    @Test
    void forwardBarracksGoesTowardsTheEnemy() {
        GameState s = position();
        site(s, 3, 1100, 600, 0, 0);
        s.qx[0] = 600; s.qy[0] = 350;
        String p = "MINES_FIRST=0,INCOME_TARGET=0,ROUTE=0,UNSTICK=0,BARRACKS=2,BARRACKS_AFTER_TOWERS=0,";
        assertEquals("BUILD 0 BARRACKS-KNIGHT", new MainBot(new Params().apply(p + "BARRACKS_FORWARD=50")).play(s, -1, Long.MAX_VALUE).queenLine(), "the first barracks at the usual site");
        s.stType[0] = Const.BARRACKS; s.stOwner[0] = 0; s.barrType[0] = Const.KNIGHT;
        assertEquals("BUILD 1 BARRACKS-KNIGHT", new MainBot(new Params().apply(p + "BARRACKS_FORWARD=0")).play(s, -1, Long.MAX_VALUE).queenLine(), "0: the site nearest to the midpoint of the queen and home");
        assertEquals("BUILD 3 BARRACKS-KNIGHT", new MainBot(new Params().apply(p + "BARRACKS_FORWARD=50")).play(s, -1, Long.MAX_VALUE).queenLine(), "50: the site nearest to the centre");
        assertEquals(3, ApproachPlanner.nextTarget(s, 1, "TOWER", 650, 200, 0, 0, new Params().apply(p + "BARRACKS_FORWARD=50,BARRACKS_AFTER_TOWERS=1")), "the prediction agrees");
    }

    /** Knights on the queen at her only tower A, a free site B near: v05 tops A up; with THREAT_TOWERS_NEAR=2 she builds a tower at B; with two towers near, or B out of THREAT_RADIUS, she tops up. */
    @Test
    void threatBuildsASecondTowerNearby() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 400; s.towerRange[0] = 300;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350; // touching A at (350,350); B (680,160) is 297 away
        s.nCreeps = 3;
        for (int i = 0; i < 3; i++) { s.cOwner[i] = 1; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = s.qx[0] + 60 + 20 * i; s.cy[i] = 350; }
        int touched = Protocol.touchedSite(s, 0);
        assertEquals(0, touched);
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=0,KITE=0,KITE=0")).play(s, touched, Long.MAX_VALUE).queenLine(), "v05: top up the touched tower");
        assertEquals("BUILD 1 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=2")).play(s, touched, Long.MAX_VALUE).queenLine(), "one tower near: a new one at B");
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=2,THREAT_RADIUS=250")).play(s, touched, Long.MAX_VALUE).queenLine(), "B beyond the radius: top up");
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=1")).play(s, touched, Long.MAX_VALUE).queenLine(), "one tower near suffices");
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0; s.towerHp[1] = 200; s.towerRange[1] = 250;
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=2")).play(s, touched, Long.MAX_VALUE).queenLine(), "two towers near: top up");
        // the knights 300 away: no threat at 250, a threat at 400
        for (int i = 0; i < 3; i++) s.cx[i] = s.qx[0] + 300;
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=2")).play(s, touched, Long.MAX_VALUE).queenLine(), "no threat at 250: the touch rule tops A up");
        s.stType[1] = Const.NONE; s.stOwner[1] = -1;
        assertEquals("BUILD 1 TOWER", new MainBot(new Params().apply("KITE=0,THREAT_TOWERS_NEAR=2,THREAT_RANGE=400")).play(s, touched, Long.MAX_VALUE).queenLine(), "THREAT_RANGE=400 sees them");
    }

    /** The ladder complete (a barracks, income 3, no towers wanted): v06 pumps the home tower; with INCOME_LATE=5 the queen upgrades the nearer own mine A, builds a mine at the gold site C once A is full, and stops at the target; a knight near keeps v06's flight. */
    @Test
    void lateIncomeRaisesTheIncomeAfterTheLadder() {
        GameState s = position();
        s.stType[0] = Const.MINE; s.stOwner[0] = 0; s.mineRate[0] = 2; s.sMaxMine[0] = 3; s.sGold[0] = 300;
        s.stType[1] = Const.BARRACKS; s.stOwner[1] = 0; s.barrType[1] = Const.KNIGHT;
        site(s, 3, 100, 100, 0, 0);
        s.stType[3] = Const.TOWER; s.stOwner[3] = 0; s.towerHp[3] = 700; s.towerRange[3] = 400;
        s.qx[0] = 600; s.qy[0] = 350;
        String p = "MINES_FIRST=0,INCOME_TARGET=2,MAX_TOWERS=0,BARRACKS=1,ROUTE=0,UNSTICK=0,THREAT_TOWERS_NEAR=0,KITE=0,";
        assertEquals("BUILD 3 TOWER", new MainBot(new Params().apply(p + "INCOME_LATE=0")).play(s, -1, Long.MAX_VALUE).queenLine(), "v06: the ladder is complete, pump the home tower");
        assertEquals("BUILD 0 MINE", new MainBot(new Params().apply(p + "INCOME_LATE=5")).play(s, -1, Long.MAX_VALUE).queenLine(), "income 2 < 5: upgrade the nearer own mine A");
        s.mineRate[0] = 3;
        assertEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "INCOME_LATE=5")).play(s, -1, Long.MAX_VALUE).queenLine(), "A is full: the gold site C");
        assertEquals("BUILD 3 TOWER", new MainBot(new Params().apply(p + "INCOME_LATE=3")).play(s, -1, Long.MAX_VALUE).queenLine(), "income 3 meets the late target: pump");
        // touching the gold site C in the late phase builds the mine; the touch rule of v06 would not
        s.qx[0] = 300; s.qy[0] = 700 - 70 - Const.QUEEN_RADIUS - 2;
        int touched = Protocol.touchedSite(s, 0);
        assertEquals(2, touched);
        assertEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "INCOME_LATE=5")).play(s, touched, Long.MAX_VALUE).queenLine());
        assertNotEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "INCOME_LATE=0")).play(s, touched, Long.MAX_VALUE).queenLine());
        // a knight near: no late economy, the flight as before
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = s.qx[0] + 100; s.cy[0] = s.qy[0];
        assertEquals("BUILD 3 TOWER", new MainBot(new Params().apply(p + "INCOME_LATE=5")).play(s, touched, Long.MAX_VALUE).queenLine());
    }

    /** Knights east of the queen at her tower A (range 300): v06 tops A up; KITE=1 MOVEs west, away from them, to a point still under A's cover; a knight 400 away kites only with KITE_RANGE=500. */
    @Test
    void kiteMovesAwayFromKnightsUnderTowerCover() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 400; s.towerRange[0] = 300;
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0; s.towerHp[1] = 400; s.towerRange[1] = 300; // a second tower near, so the defence rule has nothing to build
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350; // touching A from the east
        s.nCreeps = 2;
        for (int i = 0; i < 2; i++) { s.cOwner[i] = 1; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = s.qx[0] + 120 + 30 * i; s.cy[i] = 350; }
        int touched = Protocol.touchedSite(s, 0);
        assertEquals(0, touched);
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=0")).play(s, touched, Long.MAX_VALUE).queenLine(), "v06: top up");
        Command k = new MainBot(new Params().apply("KITE=1")).play(s, touched, Long.MAX_VALUE);
        assertEquals(Command.MOVE, k.queen, "KITE=1: a MOVE");
        assertTrue(k.x < s.qx[0], "away from the knights in the east: " + k.queenLine());
        // the knights 400 away: the touch rule at 250, the kiting at 500
        for (int i = 0; i < 2; i++) s.cx[i] = s.qx[0] + 400 + 30 * i;
        assertEquals("BUILD 0 TOWER", new MainBot(new Params().apply("KITE=1")).play(s, touched, Long.MAX_VALUE).queenLine());
        assertEquals(Command.MOVE, new MainBot(new Params().apply("KITE=1,KITE_RANGE=500")).play(s, touched, Long.MAX_VALUE).queen);
    }

    /** KITE=2: the step must end under an own tower's cover; out of reach of any tower the flight of v06 applies where KITE=1 would run; KITE_MAX caps the run. */
    @Test
    void boundKitingStaysUnderTheTowersAndFleesOutOfReach() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 400; s.towerRange[0] = 300;
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0; s.towerHp[1] = 400; s.towerRange[1] = 300;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350;
        s.nCreeps = 2;
        for (int i = 0; i < 2; i++) { s.cOwner[i] = 1; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = s.qx[0] + 120 + 30 * i; s.cy[i] = 350; }
        int touched = Protocol.touchedSite(s, 0);
        Command k2 = new MainBot(new Params().apply("KITE=2")).play(s, touched, Long.MAX_VALUE);
        assertEquals(Command.MOVE, k2.queen, "under cover: a kiting MOVE");
        assertTrue(k2.x < s.qx[0], "away from the knights: " + k2.queenLine());
        // the queen out of every tower's reach (1100, 350: 461 from B, 750 from A, ranges 300), the knights east of her: KITE=1 runs, KITE=2 flees to the nearest tower as v06
        s.qx[0] = 1100; s.qy[0] = 350;
        for (int i = 0; i < 2; i++) s.cx[i] = s.qx[0] + 120 + 30 * i;
        assertEquals(Command.MOVE, new MainBot(new Params().apply("KITE=1")).play(s, -1, Long.MAX_VALUE).queen);
        Command far = new MainBot(new Params().apply("KITE=2")).play(s, -1, Long.MAX_VALUE);
        assertEquals("BUILD 1 TOWER", far.queenLine(), "out of reach: the flight to the nearest own tower B");
        // KITE_MAX=2: two kiting turns, then one turn for the other rules
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2;
        for (int i = 0; i < 2; i++) s.cx[i] = s.qx[0] + 120 + 30 * i;
        MainBot capped = new MainBot(new Params().apply("KITE=2,KITE_MAX=2"));
        assertEquals(Command.MOVE, capped.play(s, touched, Long.MAX_VALUE).queen);
        assertEquals(Command.MOVE, capped.play(s, touched, Long.MAX_VALUE).queen);
        assertEquals("BUILD 0 TOWER", capped.play(s, touched, Long.MAX_VALUE).queenLine(), "the cap: the top-up gets a turn");
        assertEquals(Command.MOVE, capped.play(s, touched, Long.MAX_VALUE).queen, "then kiting again");
    }

    /** Two own knight barracks: TRAIN_SYNC=1 trains both or neither; 0 trains whatever the gold allows, in id order. */
    @Test
    void trainSyncReleasesBothBarracksTogether() {
        GameState s = position();
        for (int i = 0; i < 2; i++) { s.stType[i] = Const.BARRACKS; s.stOwner[i] = 0; s.barrType[i] = Const.KNIGHT; }
        MainBot greedy = new MainBot(new Params().apply("TRAIN_SYNC=0")), sync = new MainBot(new Params().apply("TRAIN_SYNC=1"));
        s.gold[0] = 100;
        assertArrayEquals(new int[]{0}, greedy.train(s));
        assertArrayEquals(new int[0], sync.train(s), "100 gold covers one barracks: wait");
        s.gold[0] = 160;
        assertArrayEquals(new int[]{0, 1}, greedy.train(s));
        assertArrayEquals(new int[]{0, 1}, sync.train(s));
        s.barrTraining[0] = true;
        s.gold[0] = 200;
        assertArrayEquals(new int[]{1}, greedy.train(s));
        assertArrayEquals(new int[0], sync.train(s), "one barracks trains: wait for it");
        s.barrTraining[0] = false;
        s.stOwner[1] = 1;
        s.gold[0] = 100;
        assertArrayEquals(new int[]{0}, sync.train(s), "one own barracks: as greedy");
    }

    /** The live cap: a wave is skipped when the creeps alive plus the wave would exceed 30 (the referee refuses the whole line). */
    @Test
    void trainKeepsWithinTheCreepLimit() {
        GameState s = position();
        s.stType[0] = Const.BARRACKS; s.stOwner[0] = 0; s.barrType[0] = Const.KNIGHT;
        s.gold[0] = 100;
        MainBot bot = new MainBot(new Params());
        s.nCreeps = 27;
        for (int i = 0; i < 27; i++) { s.cOwner[i] = 0; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = 1500; s.cy[i] = 900; }
        assertArrayEquals(new int[0], bot.train(s), "27 alive + 4 > 30");
        s.cOwner[26] = 1;
        assertArrayEquals(new int[]{0}, bot.train(s), "26 own alive + 4 = 30");
    }
}
