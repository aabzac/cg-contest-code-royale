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

    /**
     * The first-wave position of the Silver study: one fresh own tower at A, two own mines (income 2, below
     * the target 3) and a knight barracks, so v08 tops A up and then builds a third mine. Sites: B (680,160,
     * no gold) is the free site nearest to the ladder's midpoint; C (300,700) has gold.
     */
    private static GameState firstWave(boolean enemyTrains) {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 200; s.towerRange[0] = 262;
        site(s, 3, 900, 600, 300, 3); s.stType[3] = Const.MINE; s.stOwner[3] = 0; s.mineRate[3] = 1;
        site(s, 4, 1100, 300, 300, 3); s.stType[4] = Const.MINE; s.stOwner[4] = 0; s.mineRate[4] = 1;
        site(s, 5, 800, 850, 300, 3); s.stType[5] = Const.BARRACKS; s.stOwner[5] = 0; s.barrType[5] = Const.KNIGHT;
        site(s, 6, 1600, 800, 300, 3); s.stType[6] = Const.BARRACKS; s.stOwner[6] = 1; s.barrType[6] = Const.KNIGHT;
        s.barrTraining[6] = enemyTrains; s.barrProgress[6] = enemyTrains ? 1 : 0;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350; // touching A
        return s;
    }

    private static String queen(GameState s, String params) {
        return new MainBot(new Params().apply(params)).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE).queenLine();
    }

    @Test
    void waveTowersBuildTheSecondTowerBeforeTopUpsAndMines() {
        String plain = "ROUTE=0,UNSTICK=0,WAVE_ANCHOR=0";   // the plain BUILD towards the target, so that the target shows; the ladder of v11
        GameState quiet = firstWave(false);
        assertEquals(0, Protocol.touchedSite(quiet, 0));
        assertEquals("BUILD 0 TOWER", queen(quiet, plain), "v08: top up the fresh tower");
        assertEquals("BUILD 0 TOWER", queen(quiet, plain + ",WAVE_TOWERS=2"), "no wave coming: as v08");
        GameState trains = firstWave(true);
        assertEquals("BUILD 0 TOWER", queen(trains, plain), "v08 ignores the enemy barracks");
        assertEquals("BUILD 1 TOWER", queen(trains, plain + ",WAVE_TOWERS=2"), "the enemy trains knights: a second tower at B, not the top-up");
        assertEquals("BUILD 0 TOWER", queen(trains, plain + ",WAVE_TOWERS=1"), "one tower is enough for N=1");
        // enemy knights already out, the barracks idle: the wave is still coming
        GameState out = firstWave(false);
        out.nCreeps = 1; out.cOwner[0] = 1; out.cType[0] = Const.KNIGHT; out.cHp[0] = 25; out.cx[0] = 1500; out.cy[0] = 700;
        assertEquals("BUILD 1 TOWER", queen(out, plain + ",WAVE_TOWERS=2"));
        // a second own tower stands: the top-up again
        GameState two = firstWave(true);
        site(two, 7, 150, 150, 300, 3); two.stType[7] = Const.TOWER; two.stOwner[7] = 0; two.towerHp[7] = 300; two.towerRange[7] = 300;
        assertEquals("BUILD 0 TOWER", queen(two, plain + ",WAVE_TOWERS=2"));
        // touching an own mine below its maximum: v08 upgrades it, the wave skips the upgrade for the tower
        GameState mine = firstWave(true);
        mine.qx[0] = 900 + 70 + Const.QUEEN_RADIUS + 2; mine.qy[0] = 600;
        assertEquals(3, Protocol.touchedSite(mine, 0));
        assertEquals("BUILD 3 MINE", queen(mine, plain));
        assertEquals("BUILD 1 TOWER", queen(mine, plain + ",WAVE_TOWERS=2"));
    }

    /**
     * WAVE_TOWER_HP (2026-11-09, docs/data/exp-2026-11-09-meet-waves) on the first-wave position with the touched tower A at
     * 550 HP: v12 tops it up to 700; with the cap 500 and the enemy's wave coming she leaves it (the ladder's mine at C),
     * keeps pumping below the cap, and pumps as v12 with no wave coming or with a second tower standing.
     */
    @Test
    void waveTowerHpCapsTheFirstTowersPumpWhileAWaveComes() {
        String plain = "ROUTE=0,UNSTICK=0,WAVE_ANCHOR=0";
        GameState quiet = firstWave(false);
        quiet.towerHp[0] = 550;
        assertEquals("BUILD 0 TOWER", queen(quiet, plain), "v12: pump to 700");
        assertEquals("BUILD 0 TOWER", queen(quiet, plain + ",WAVE_TOWER_HP=500"), "no wave coming: the cap does not apply");
        GameState trains = firstWave(true);
        trains.towerHp[0] = 550;
        assertEquals("BUILD 0 TOWER", queen(trains, plain), "v12 pumps on");
        assertEquals("BUILD 2 MINE", queen(trains, plain + ",WAVE_TOWER_HP=500"), "the enemy trains knights and A is above the cap: the ladder's next site");
        trains.towerHp[0] = 450;
        assertEquals("BUILD 0 TOWER", queen(trains, plain + ",WAVE_TOWER_HP=500"), "below the cap: pump");
        // enemy knights out, the barracks idle: the wave is still coming
        GameState out = firstWave(false);
        out.towerHp[0] = 550;
        out.nCreeps = 1; out.cOwner[0] = 1; out.cType[0] = Const.KNIGHT; out.cHp[0] = 25; out.cx[0] = 1500; out.cy[0] = 700;
        assertEquals("BUILD 2 MINE", queen(out, plain + ",WAVE_TOWER_HP=500"));
        // a second own tower stands: the cap is off, pump to 700
        GameState two = firstWave(true);
        two.towerHp[0] = 550;
        site(two, 7, 150, 150, 300, 3); two.stType[7] = Const.TOWER; two.stOwner[7] = 0; two.towerHp[7] = 300; two.towerRange[7] = 300;
        assertEquals("BUILD 0 TOWER", queen(two, plain + ",WAVE_TOWER_HP=500"));
    }

    /**
     * LADDER_CLEAR (2026-11-09, docs/data/exp-2026-11-09-meet-waves): own towers at A and B at 700 HP, the queen 150 from A
     * with no mine, so the ladder walks her to C for a mine (turn 60, no enemy barracks). An enemy knight 400 away is no
     * threat, so v12 walks; LADDER_CLEAR=500 sends her to A, her nearest tower, 300 does not see the knight, the spell cap
     * ends the hold until the knights are clear, a knight within 250 is the kiting's, one tower or the first-wave window
     * keep the ladder.
     */
    @Test
    void ladderClearHoldsTheQueenAtHerTowerWhileAKnightIsNear() {
        String plain = "ROUTE=0,UNSTICK=0,LADDER_CLEAR=0";   // v12: no ladder gate (500 by default since v13)
        GameState s = position();
        s.turn = 60;
        for (int i : new int[]{0, 1}) { s.stType[i] = Const.TOWER; s.stOwner[i] = 0; s.towerHp[i] = 700; s.towerRange[i] = 400; }
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = 900; s.cy[0] = 350;
        assertEquals(-1, Protocol.touchedSite(s, 0));
        assertEquals("BUILD 2 MINE", queen(s, plain), "v12: the ladder's mine at C with a knight 400 away");
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",LADDER_CLEAR=500"), "a knight within 500: her nearest tower A");
        assertEquals("BUILD 2 MINE", queen(s, plain + ",LADDER_CLEAR=300"), "the knight is beyond 300");
        MainBot capped = new MainBot(new Params().apply(plain + ",LADDER_CLEAR=500,LADDER_CLEAR_MAX=1"));
        assertEquals("BUILD 0 TOWER", capped.play(s, -1, Long.MAX_VALUE).queenLine(), "the first hold of the spell");
        assertEquals("BUILD 2 MINE", capped.play(s, -1, Long.MAX_VALUE).queenLine(), "the cap: the ladder again");
        assertEquals("BUILD 2 MINE", capped.play(s, -1, Long.MAX_VALUE).queenLine(), "and still, while the knight stays");
        s.cx[0] = 1400;
        assertEquals("BUILD 2 MINE", capped.play(s, -1, Long.MAX_VALUE).queenLine(), "the knight 900 away: clear");
        s.cx[0] = 900;
        assertEquals("BUILD 0 TOWER", capped.play(s, -1, Long.MAX_VALUE).queenLine(), "a new spell");
        s.cx[0] = 700;
        assertTrue(queen(s, plain + ",LADDER_CLEAR=500").startsWith("MOVE"), "a knight within 250 is the kiting's");
        s.cx[0] = 900;
        s.stType[1] = Const.NONE; s.stOwner[1] = -1;
        assertEquals("BUILD 2 MINE", queen(s, plain + ",LADDER_CLEAR=500"), "one tower: the ladder");
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0;
        s.turn = 20;
        site(s, 3, 1600, 800, 300, 3); s.stType[3] = Const.BARRACKS; s.stOwner[3] = 1; s.barrType[3] = Const.KNIGHT;
        assertEquals("BUILD 2 MINE", queen(s, plain + ",LADDER_CLEAR=500"), "the first-wave window: the ladder (the anchor's business)");
        s.turn = 60;
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",LADDER_CLEAR=500"), "past the window: the hold");
    }

    /**
     * LATE_HOLD: an own tower at A (the one nearest home), the queen 150 from it with no mine, so the ladder
     * walks her to C for a mine. Late and ahead on HP she stays with the home tower instead; under threat
     * the defence would walk her to B for a second tower, which the hold leaves to the flight.
     */
    @Test
    void lateHoldKeepsTheQueenHomeWhenAheadLate() {
        String plain = "ROUTE=0,UNSTICK=0,KITE=0,LATE_HOLD=0";   // v08's rule; the hold arms set LATE_HOLD after it
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 750; s.towerRange[0] = 300;
        s.turn = 210; s.hp[0] = 50; s.hp[1] = 40;
        assertEquals(-1, Protocol.touchedSite(s, 0));
        assertEquals("BUILD 2 MINE", queen(s, plain), "v08: the ladder's mine at C");
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",LATE_HOLD=200"), "late and ahead: hold at the home tower");
        assertEquals("BUILD 0 TOWER", queen(s, "ROUTE=0,UNSTICK=0,KITE=0"), "the defaults hold since v09");
        assertEquals("BUILD 2 MINE", queen(s, plain + ",LATE_HOLD=220"), "before the hold turn: the ladder");
        s.hp[1] = 50;
        assertEquals("BUILD 2 MINE", queen(s, plain + ",LATE_HOLD=200"), "level on HP: no hold");
        s.hp[1] = 40;
        // a knight within 250, one own tower near: the defence walks to B, the hold flees to A
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = 650; s.cy[0] = 350;
        assertEquals("BUILD 1 TOWER", queen(s, plain), "v08: a second tower at B");
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",LATE_HOLD=200"), "the hold: the flight to the own tower");
    }

    @Test
    void topUpFromWaitsForTheTowerCount() {
        String plain = "ROUTE=0,UNSTICK=0";
        GameState s = firstWave(false);
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",TOPUP_FROM=1"), "one tower stands: top up");
        assertEquals("BUILD 2 MINE", queen(s, plain + ",TOPUP_FROM=2"), "no top-up yet: the ladder's income phase, a mine at C");
        site(s, 7, 150, 150, 300, 3); s.stType[7] = Const.TOWER; s.stOwner[7] = 0; s.towerHp[7] = 300; s.towerRange[7] = 300;
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",TOPUP_FROM=2"), "two towers: top up");
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

    /** The ladder complete as in {@link #lateIncomeRaisesTheIncomeAfterTheLadder}, the own mine A full, the home tower at 100,100. */
    private static GameState ladderComplete() {
        GameState s = position();
        s.stType[0] = Const.MINE; s.stOwner[0] = 0; s.mineRate[0] = 3; s.sMaxMine[0] = 3; s.sGold[0] = 300;
        s.stType[1] = Const.BARRACKS; s.stOwner[1] = 0; s.barrType[1] = Const.KNIGHT;
        site(s, 3, 100, 100, 0, 0);
        s.stType[3] = Const.TOWER; s.stOwner[3] = 0; s.towerHp[3] = 700; s.towerRange[3] = 400;
        s.qx[0] = 600; s.qy[0] = 350;
        return s;
    }

    private static final String LADDER_DONE = "MINES_FIRST=0,INCOME_TARGET=2,MAX_TOWERS=0,BARRACKS=1,ROUTE=0,UNSTICK=0,THREAT_TOWERS_NEAR=0,KITE=0";

    /**
     * INCOME_LATE's safety conditions (2026-11-06): the late site is the gold site C, 632 from the home tower; LATE_COVER=1
     * pumps instead until the tower's range covers C. A gold site D on the enemy's half is left with LATE_HALF=1. A knight
     * 400 away (beyond the threat range) keeps the queen home with LATE_CLEAR=600, not with 300.
     */
    @Test
    void lateIncomeSafetyConditions() {
        GameState s = ladderComplete();
        String p = LADDER_DONE + ",INCOME_LATE=5";
        assertEquals("BUILD 2 MINE", queen(s, p), "A is full: the gold site C");
        assertEquals("BUILD 3 TOWER", queen(s, p + ",LATE_COVER=1"), "the home tower (range 400) does not cover C: pump");
        s.towerRange[3] = 700;
        assertEquals("BUILD 2 MINE", queen(s, p + ",LATE_COVER=1"), "range 700 covers C");
        s.sGold[2] = 0;
        site(s, 4, 1300, 600, 300, 3);
        assertEquals("BUILD 4 MINE", queen(s, p), "C spent: the gold site D");
        assertEquals("BUILD 3 TOWER", queen(s, p + ",LATE_HALF=1"), "D is nearer the enemy's corner: pump");
        s.sGold[2] = 300;
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = s.qx[0] + 400; s.cy[0] = s.qy[0];
        assertEquals("BUILD 2 MINE", queen(s, p), "a knight 400 away is no threat");
        assertEquals("BUILD 3 TOWER", queen(s, p + ",LATE_CLEAR=600"), "LATE_CLEAR=600 waits for it to leave");
        assertEquals("BUILD 2 MINE", queen(s, p + ",LATE_CLEAR=300"));
    }

    /**
     * GIANTS (2026-11-06): with the ladder complete and an enemy tower standing, the queen builds a giant barracks on the
     * free site C from GIANT_TURN on, not before and not without an enemy tower. The giant barracks trains first, the
     * knights wait while it lacks its 140 gold, and GIANTS_MAX caps its trainings.
     */
    @Test
    void giantsBarracksAndTraining() {
        GameState s = ladderComplete();
        site(s, 4, 1700, 800, 0, 0);
        s.stType[4] = Const.TOWER; s.stOwner[4] = 1; s.towerHp[4] = 700; s.towerRange[4] = 300;
        s.turn = 80;
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE), "v09: the ladder is complete, pump");
        assertEquals("BUILD 2 BARRACKS-GIANT", queen(s, LADDER_DONE + ",GIANTS=1,GIANT_TURN=80"), "turn 80: the giant barracks on C");
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE + ",GIANTS=1,GIANT_TURN=81"), "not before GIANT_TURN");
        s.stOwner[4] = 0;
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE + ",GIANTS=1,GIANT_TURN=80"), "no enemy tower: no giant barracks");
        s.stOwner[4] = 1;
        // C is the giant barracks now, B the knight barracks
        s.stType[2] = Const.BARRACKS; s.stOwner[2] = 0; s.barrType[2] = Const.GIANT;
        MainBot bot = new MainBot(new Params().apply(LADDER_DONE + ",GIANTS=1,GIANTS_MAX=1"));
        s.gold[0] = 100;
        assertArrayEquals(new int[0], bot.train(s), "the idle giant barracks saves for 140: the knights wait");
        s.gold[0] = 230;
        assertArrayEquals(new int[]{2, 1}, bot.train(s), "the giant first, the knights with the rest");
        s.gold[0] = 100;
        assertArrayEquals(new int[]{1}, bot.train(s), "GIANTS_MAX=1 reached: the knights only");
        assertArrayEquals(new int[]{1}, new MainBot(new Params().apply(LADDER_DONE)).train(s), "GIANTS=0 never trains giants");
    }

    /** One enemy giant, at (430, 700). */
    private static void enemyGiant(GameState s) {
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.GIANT; s.cHp[0] = 200; s.cx[0] = 430; s.cy[0] = 700;
    }

    /**
     * GIANT_GUARD (2026-11-06): the ladder complete, an own tower at C (300,700) with an enemy giant 130 from its centre.
     * v09 pumps the home tower; GIANT_GUARD=200 walks to C and tops it up; with 100 the giant is too far to count.
     */
    @Test
    void giantGuardTopsUpTheAttackedTower() {
        GameState s = ladderComplete();
        s.stType[2] = Const.TOWER; s.stOwner[2] = 0; s.towerHp[2] = 500; s.towerRange[2] = 300;
        enemyGiant(s);
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE), "v09: pump the home tower");
        assertEquals("BUILD 2 TOWER", queen(s, LADDER_DONE + ",GIANT_GUARD=200"), "the giant at C: top C up");
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE + ",GIANT_GUARD=100"), "130 from C is beyond 100");
        s.nCreeps = 0;
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE + ",GIANT_GUARD=200"), "no giant: pump");
    }

    /**
     * ARCHERS (2026-11-06): the ladder complete, an enemy giant 685 from the home tower. ARCHERS=1 builds an archer
     * barracks on the free site C (any giant counts with ARCHER_RANGE=0; 800 sees it, 200 does not). The archer barracks
     * trains before the knights while a giant is near, the knights wait while it lacks its 100 gold, and with no giant
     * near only the knights train.
     */
    @Test
    void archersAgainstGiants() {
        GameState s = ladderComplete();
        enemyGiant(s);
        assertEquals("BUILD 2 BARRACKS-ARCHER", queen(s, LADDER_DONE + ",ARCHERS=1"), "a giant out: the archer barracks on C");
        assertEquals("BUILD 2 BARRACKS-ARCHER", queen(s, LADDER_DONE + ",ARCHERS=1,ARCHER_RANGE=800"));
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE + ",ARCHERS=1,ARCHER_RANGE=200"), "685 from the tower is beyond 200");
        s.nCreeps = 0;
        assertEquals("BUILD 3 TOWER", queen(s, LADDER_DONE + ",ARCHERS=1"), "no giant: pump");
        // C is the archer barracks now, B the knight barracks
        s.stType[2] = Const.BARRACKS; s.stOwner[2] = 0; s.barrType[2] = Const.ARCHER;
        MainBot bot = new MainBot(new Params().apply(LADDER_DONE + ",ARCHERS=1"));
        s.gold[0] = 100;
        assertArrayEquals(new int[]{1}, bot.train(s), "no giant: the knights only");
        enemyGiant(s);
        s.gold[0] = 50;
        assertArrayEquals(new int[0], bot.train(s), "a giant out: the archer barracks saves for 100, the knights wait");
        s.gold[0] = 180;
        assertArrayEquals(new int[]{2, 1}, bot.train(s), "the archers first, the knights with the rest");
        assertArrayEquals(new int[]{1}, new MainBot(new Params().apply(LADDER_DONE)).train(s), "ARCHERS=0 never trains archers");
    }

    /**
     * RELEASE_NEAR / RELEASE_HOLD (2026-11-06): one knight barracks B at (680,160), 100 gold, the enemy queen 1342 away.
     * v09 releases at once. RELEASE_NEAR=800,RELEASE_HOLD=3 waits from the turn the release is ready and releases after 3
     * turns, or at once when the enemy queen stands within 800 of B. A turn without the gold resets the wait.
     */
    @Test
    void releaseWaitsForTheEnemyQueen() {
        GameState s = position();
        s.stType[1] = Const.BARRACKS; s.stOwner[1] = 0; s.barrType[1] = Const.KNIGHT;
        String p = "BARRACKS=1,RELEASE_NEAR=800,RELEASE_HOLD=3";
        s.turn = 10;
        assertArrayEquals(new int[]{1}, new MainBot(new Params().apply("BARRACKS=1")).train(s), "v09: release at once");
        MainBot bot = new MainBot(new Params().apply(p));
        for (int t = 10; t < 13; t++) {
            s.turn = t;
            assertArrayEquals(new int[0], bot.train(s), "turn " + t + ": the enemy queen is far, wait");
        }
        s.turn = 13;
        assertArrayEquals(new int[]{1}, bot.train(s), "3 turns waited: release");
        s.turn = 20;
        s.gold[0] = 50;
        assertArrayEquals(new int[0], bot.train(s), "no gold: not ready");
        s.turn = 21;
        s.gold[0] = 100;
        assertArrayEquals(new int[0], bot.train(s), "ready again on turn 21: the wait starts over");
        s.qx[1] = 1000; s.qy[1] = 400;
        s.turn = 22;
        assertArrayEquals(new int[]{1}, bot.train(s), "the enemy queen 400 from B: release");
    }

    /**
     * SAFE_LADDER (2026-11-07, docs/data/study-2026-11-07-tower-fire): the ladder wants a third tower and its only free
     * site is D (1500,700) on the enemy half. The straight walk from the queen (600,350) to D passes 44 from an enemy
     * tower E (1100,500, range 300) whose range misses D's centre, so v10 walks through it. SAFE_LADDER=1 drops D and
     * pumps the home tower; with E off the walk (1100,900) it takes D, SAFE_LADDER=2 never does.
     */
    @Test
    void safeLadderKeepsTheQueenOutOfTheEnemyBase() {
        GameState s = ladderComplete();
        s.stType[2] = Const.TOWER; s.stOwner[2] = 0; s.towerHp[2] = 700; s.towerRange[2] = 400;
        site(s, 4, 1500, 700, 300, 3);
        site(s, 5, 1100, 500, 0, 0);
        s.stType[5] = Const.TOWER; s.stOwner[5] = 1; s.towerHp[5] = 500; s.towerRange[5] = 300;
        String p = "MINES_FIRST=0,INCOME_TARGET=2,MAX_TOWERS=3,BARRACKS=1,ROUTE=0,UNSTICK=0,THREAT_TOWERS_NEAR=0,KITE=0";
        assertEquals("BUILD 4 TOWER", queen(s, p + ",SAFE_LADDER=0"), "v10: D through E's range");
        assertEquals("BUILD 3 TOWER", queen(s, p + ",SAFE_LADDER=1"), "the walk to D crosses E's range: pump");
        assertEquals("BUILD 3 TOWER", queen(s, p + ",SAFE_LADDER=2"));
        s.sy[5] = 900;
        assertEquals("BUILD 4 TOWER", queen(s, p + ",SAFE_LADDER=1"), "E off the walk: D");
        assertEquals("BUILD 3 TOWER", queen(s, p + ",SAFE_LADDER=2"), "never a site on the enemy half");
        s.qx[0] = 900; s.qy[0] = 800;
        assertEquals("BUILD 3 TOWER", queen(s, p + ",SAFE_LADDER=1"), "the queen inside E's range: every walk starts in it");
        s.clearStructure(2);
        assertEquals("BUILD 2 TOWER", queen(s, p + ",SAFE_LADDER=1"), "C on our half stays a target from inside the range");
    }

    /** The position mirrored through the board's centre (x -> 1920 - x, y -> 1000 - y): our home becomes the bottom-right corner. */
    private static GameState mirror(GameState s) {
        GameState m = s.copy();
        for (int i = 0; i < m.nSites; i++) { m.sx[i] = Const.WIDTH - s.sx[i]; m.sy[i] = Const.HEIGHT - s.sy[i]; }
        for (int p = 0; p < 2; p++) { m.qx[p] = Const.WIDTH - s.qx[p]; m.qy[p] = Const.HEIGHT - s.qy[p]; }
        for (int i = 0; i < m.nCreeps; i++) { m.cx[i] = Const.WIDTH - s.cx[i]; m.cy[i] = Const.HEIGHT - s.cy[i]; }
        return m;
    }

    /**
     * WAVE_ANCHOR's position: the tower phase (income 3, a knight barracks at (150,450)) with one own tower at (100,100),
     * the queen at (500,350), an enemy knight barracks standing, turn 20. v11's target is B (680,160), nearest the midpoint
     * (250,175) of the queen and the home corner; the anchor at her height, (250,350) for 50 and (0,350) for 100, takes C
     * (300,700) instead.
     */
    private static GameState anchorPosition() {
        GameState s = position();
        s.stType[0] = Const.MINE; s.stOwner[0] = 0; s.mineRate[0] = 3;   // income 3: the tower phase
        site(s, 3, 150, 450, 0, 0); s.stType[3] = Const.BARRACKS; s.stOwner[3] = 0; s.barrType[3] = Const.KNIGHT;
        site(s, 4, 1600, 800, 0, 0); s.stType[4] = Const.BARRACKS; s.stOwner[4] = 1; s.barrType[4] = Const.KNIGHT;
        site(s, 5, 100, 100, 0, 0); s.stType[5] = Const.TOWER; s.stOwner[5] = 0; s.towerHp[5] = 700; s.towerRange[5] = 400;
        s.turn = 20;
        return s;
    }

    private static final String ANCHOR_PLAIN = "MINES_FIRST=1,ROUTE=0,UNSTICK=0,WAVE_ANCHOR=0", ANCHOR_ON = ANCHOR_PLAIN + ",WAVE_UNTIL=50,WAVE_ANCHOR=";

    /**
     * WAVE_ANCHOR (2026-11-07, docs/data/exp-2026-11-07-meet-back) on {@link #anchorPosition()}, on both sides of the board.
     * Outside the window (past WAVE_UNTIL, no enemy knight barracks or knight), for the first tower, in the mine phase and for
     * the first barracks the target is v11's.
     */
    @Test
    void waveAnchorKeepsTheTowerTargetsBack() {
        String p = ANCHOR_PLAIN, on = ANCHOR_ON;
        GameState s = anchorPosition();
        for (GameState x : new GameState[]{s, mirror(s)}) {
            assertEquals("BUILD 1 TOWER", queen(x, p), "v11: B, nearest the midpoint toward the home corner");
            assertEquals("BUILD 1 TOWER", queen(x, p + ",WAVE_ANCHOR=0"), "0 is v11");
            assertEquals("BUILD 2 TOWER", queen(x, on + 50), "the anchor halfway back at her height: C");
            assertEquals("BUILD 2 TOWER", queen(x, on + 100), "the anchor on the back edge: C");
            assertEquals("BUILD 1 TOWER", queen(x, p + ",WAVE_UNTIL=20,WAVE_ANCHOR=50"), "turn 20 is past a window to 20");
            assertEquals("BUILD 2 TOWER", queen(x, p + ",WAVE_UNTIL=21,WAVE_ANCHOR=50"), "turn 20 is inside a window to 21");
            assertEquals("BUILD 2 TOWER", queen(x, p + ",WAVE_UNTIL=0,WAVE_ANCHOR=50"), "WAVE_UNTIL=0: no end");
            assertEquals("BUILD 2 TOWER", queen(x, "MINES_FIRST=1,ROUTE=0,UNSTICK=0"), "the defaults since v12: the anchor at 50 to turn 30");
        }
        // A free site G (100,780) near the back edge: v11 keeps B, the anchor at 50 takes C, at 100 G.
        GameState g = anchorPosition();
        site(g, 6, 100, 780, 0, 0);
        for (GameState x : new GameState[]{g, mirror(g)}) {
            assertEquals("BUILD 1 TOWER", queen(x, p), "v11 still B");
            assertEquals("BUILD 2 TOWER", queen(x, on + 50), "C, 354 from (250,350)");
            assertEquals("BUILD 6 TOWER", queen(x, on + 100), "G, 442 from (0,350)");
        }
        // The window opens on an enemy knight barracks or an enemy knight only.
        s.barrType[4] = Const.GIANT;
        assertEquals("BUILD 1 TOWER", queen(s, on + 50), "no enemy knight barracks or knight: the window is closed");
        s.nCreeps = 1; s.cOwner[0] = 0; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = 1500; s.cy[0] = 700;
        assertEquals("BUILD 1 TOWER", queen(s, on + 50), "our own knight does not open it");
        s.cOwner[0] = 1; s.cType[0] = Const.GIANT;
        assertEquals("BUILD 1 TOWER", queen(s, on + 50), "an enemy giant does not open it");
        s.cType[0] = Const.KNIGHT;
        assertEquals("BUILD 2 TOWER", queen(s, on + 50), "an enemy knight on the board opens it");
        s.nSites = 5;   // no own tower: the first tower keeps v11's choice
        assertEquals("BUILD 1 TOWER", queen(s, on + 50), "the first tower is v11's");
        assertEquals("BUILD 1 TOWER", queen(s, p), "v11 without a tower");
        // The mine phase and the first barracks keep v11's choice: a free site D (600,100) nearest the midpoint.
        GameState mine = anchorPosition();
        mine.mineRate[0] = 2;   // income 2: the mine phase
        site(mine, 6, 600, 100, 300, 3);
        assertEquals("BUILD 6 MINE", queen(mine, p), "v11: the gold site D");
        assertEquals("BUILD 6 MINE", queen(mine, on + 50), "the mine phase is v11's");
        GameState bar = anchorPosition();
        bar.stOwner[3] = 1;     // no own barracks
        site(bar, 6, 600, 100, 0, 0);
        assertEquals("BUILD 6 BARRACKS-KNIGHT", queen(bar, p), "v11: the first barracks at D");
        assertEquals("BUILD 6 BARRACKS-KNIGHT", queen(bar, on + 50), "the first barracks is v11's");
    }

    /**
     * DEFENCE_FWD (2026-11-07): knights on the queen at her only tower A, as in {@link #threatBuildsASecondTowerNearby}.
     * The defence rule of v11 builds at B (680,160), 228 ahead of her (452,350); DEFENCE_FWD=50 takes C (300,700), 152
     * behind, and with C taken builds nothing; the site she touches stays allowed however far ahead it lies. Both sides of
     * the board. DEFENCE_UNTIL ends the limit at a turn.
     */
    @Test
    void defenceForwardLimitKeepsTheDefenceTowerBack() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 400; s.towerRange[0] = 300;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350; // touching A
        s.nCreeps = 3;
        for (int i = 0; i < 3; i++) { s.cOwner[i] = 1; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = s.qx[0] + 60 + 20 * i; s.cy[i] = 350; }
        for (GameState x : new GameState[]{s, mirror(s)}) {
            assertEquals("BUILD 1 TOWER", queen(x, ""), "v11: B, 228 ahead");
            assertEquals("BUILD 2 TOWER", queen(x, "DEFENCE_FWD=50"), "C, 152 behind");
            assertEquals("BUILD 1 TOWER", queen(x, "DEFENCE_FWD=300"), "B is less than 300 ahead");
        }
        s.turn = 70;
        assertEquals("BUILD 2 TOWER", queen(s, "DEFENCE_FWD=50"), "DEFENCE_UNTIL=0: the whole game");
        assertEquals("BUILD 2 TOWER", queen(s, "DEFENCE_FWD=50,DEFENCE_UNTIL=71"), "turn 70 is inside a limit to 71");
        assertEquals("BUILD 1 TOWER", queen(s, "DEFENCE_FWD=50,DEFENCE_UNTIL=70"), "turn 70 is past a limit to 70: v11's B");
        s.turn = 0;
        s.stType[2] = Const.MINE; s.stOwner[2] = 0; s.mineRate[2] = 1;
        for (GameState x : new GameState[]{s, mirror(s)}) {
            assertEquals("BUILD 1 TOWER", queen(x, ""), "v11 still B");
            assertTrue(queen(x, "DEFENCE_FWD=50").startsWith("MOVE"), "no site behind: no defence tower, she kites");
        }
        // the touched site is allowed: the queen at (610,230) touches B, 70 ahead
        GameState t = position();
        t.stType[0] = Const.TOWER; t.stOwner[0] = 0; t.towerHp[0] = 400; t.towerRange[0] = 300;
        t.qx[0] = 610; t.qy[0] = 230;
        t.nCreeps = 1; t.cOwner[0] = 1; t.cType[0] = Const.KNIGHT; t.cHp[0] = 25; t.cx[0] = 700; t.cy[0] = 300;
        for (GameState x : new GameState[]{t, mirror(t)}) {
            assertEquals(1, Protocol.touchedSite(x, 0));
            assertEquals("BUILD 1 TOWER", queen(x, "DEFENCE_FWD=50"), "the touched site B");
        }
        t.qx[0] = 560; t.qy[0] = 260;   // 120 behind B, not touching it; C is beyond THREAT_RADIUS
        for (GameState x : new GameState[]{t, mirror(t)}) {
            assertEquals(-1, Protocol.touchedSite(x, 0));
            assertEquals("BUILD 1 TOWER", queen(x, ""), "v11: B");
            assertNotEquals("BUILD 1 TOWER", queen(x, "DEFENCE_FWD=50"), "B is 120 ahead and not touched");
        }
    }

    /**
     * STAND_REACH (2026-11-07): the queen touches her tower A, a second tower B is near, two knights stand 60 above and
     * below her. No kite step gains, so v11 stands and tops A up; STAND_REACH=155 takes the best-scoring step that moves
     * her 40 or more and ends under a tower: east between the knights, or, with the ranges cut so that the east step ends
     * uncovered, the best covered one. With the knights 200 away it changes nothing.
     */
    @Test
    void standReachStepsAwayInsteadOfStanding() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 400; s.towerRange[0] = 300;
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0; s.towerHp[1] = 400; s.towerRange[1] = 300;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350; // touching A
        s.nCreeps = 2;
        for (int i = 0; i < 2; i++) { s.cOwner[i] = 1; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = s.qx[0]; s.cy[i] = i == 0 ? 410 : 290; }
        assertEquals("BUILD 0 TOWER", queen(s, ""), "v11: no step gains, she tops A up");
        assertEquals("MOVE 652 355", queen(s, "STAND_REACH=155"), "the best-scoring step: east, 84 from the knights");
        assertForcedStep(s, queen(s, "STAND_REACH=155"));
        GameState m = mirror(s);
        assertEquals("BUILD 0 TOWER", queen(m, ""), "v11 on the right side");
        assertForcedStep(m, queen(m, "STAND_REACH=155"));
        GameState cut = s.copy();
        cut.towerRange[0] = 150; cut.towerRange[1] = 200;   // the east step ends outside both ranges
        assertEquals("BUILD 0 TOWER", queen(cut, ""), "v11 with the ranges cut");
        assertEquals("MOVE 307 488", queen(cut, "STAND_REACH=155"), "the best covered step");
        assertForcedStep(cut, queen(cut, "STAND_REACH=155"));
        s.cy[0] = 550; s.cy[1] = 150;
        assertEquals("BUILD 0 TOWER", queen(s, "STAND_REACH=155"), "the knights 200 away: she tops A up");
    }

    /** Follows the bot's commands for some turns with the sites-only step (the units stand still) and returns the position. */
    private static GameState follow(GameState from, String params, int turns) {
        GameState s = from.copy();
        MainBot bot = new MainBot(new Params().apply(params));
        cr.core.Physics ph = new cr.core.Physics();
        for (int t = 0; t < turns; t++) {
            int touched = Protocol.touchedSite(s, 0);
            Command c = bot.play(s, touched, Long.MAX_VALUE);
            if (c.queen == Command.MOVE || c.queen == Command.BUILD && c.site != touched) {
                ph.queenStep(s, s.qx[0], s.qy[0], c.queen == Command.MOVE ? c.x : s.sx[c.site], c.queen == Command.MOVE ? c.y : s.sy[c.site], 5);
                s.qx[0] = ph.rx;
                s.qy[0] = ph.ry;
            }
            s.turn++;
        }
        return s;
    }

    /**
     * HOLD_OVERLAP (2026-11-10, docs/data/exp-2026-11-10-overlap): own towers A (range 300) and B at 700 HP, an enemy
     * knight 400 from the queen, turn 60: the ladder gate's hold. Touching A from the west she stands under A alone and
     * v13 holds there; with a reach of three steps she walks to where B covers her too, with one step she stays. Standing
     * between the towers under A alone, v13 walks to A, her nearest; B's touch point lies under both and is taken, unless
     * the knight is within 250 of it. The flight (a knight within 250, no kiting) picks the same spot. With OVERLAP_FIRST
     * the search comes before the top-up of the touched tower.
     */
    @Test
    void holdOverlapStandsWhereTheTowersOverlap() {
        String plain = "ROUTE=0,UNSTICK=0,IDLE_OVERLAP=0,HOLD_OVERLAP=0";   // v13: the hold at her nearest tower (300 and 1 by default since v14)
        GameState s = position();
        s.turn = 60;
        for (int i : new int[]{0, 1}) { s.stType[i] = Const.TOWER; s.stOwner[i] = 0; s.towerHp[i] = 700; }
        s.towerRange[0] = 300; s.towerRange[1] = 350;
        s.qx[0] = 350 - 70 - Const.QUEEN_RADIUS - 2; s.qy[0] = 350;   // touching A from the west, 472 from B
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = 650; s.cy[0] = 550;   // 449 from her
        assertEquals(0, Protocol.touchedSite(s, 0));
        assertEquals(1, MainBot.cover(s, s.qx[0], s.qy[0]));
        assertEquals("BUILD 0 TOWER", queen(s, plain), "v13: the hold at her nearest tower, under A alone");
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",HOLD_OVERLAP=60"), "one step reaches no better spot: v13's command");
        assertTrue(queen(s, plain + ",HOLD_OVERLAP=180").startsWith("MOVE"), "three steps reach the overlap: " + queen(s, plain + ",HOLD_OVERLAP=180"));
        assertTrue(queen(s, "ROUTE=0,UNSTICK=0").startsWith("MOVE"), "the defaults search five steps since v14");
        GameState after = follow(s, plain + ",HOLD_OVERLAP=180", 3);
        assertEquals(2, MainBot.cover(after, after.qx[0], after.qy[0]), "after three turns both towers cover her at " + after.qx[0] + "," + after.qy[0]);
        GameState stays = follow(after, plain + ",HOLD_OVERLAP=180", 3);
        assertEquals(2, MainBot.cover(stays, stays.qx[0], stays.qy[0]), "and she keeps the cover");

        GameState low = s.copy();
        low.towerHp[0] = 600;
        assertEquals("BUILD 0 TOWER", queen(low, plain + ",HOLD_OVERLAP=180"), "the touched tower below 700: the touch rule's top-up first");
        assertTrue(queen(low, plain + ",HOLD_OVERLAP=180,OVERLAP_FIRST=1").startsWith("MOVE"), "OVERLAP_FIRST: the better spot before the top-up");
        assertTrue(queen(low, plain + ",HOLD_OVERLAP=180,OVERLAP_FIRST=2").startsWith("MOVE"), "one tower covers her, fewer than 2: the same");
        assertEquals("BUILD 0 TOWER", queen(low, plain + ",OVERLAP_FIRST=1"), "nothing without HOLD_OVERLAP");
        GameState both = low.copy();
        both.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2;   // touching A from the east: B covers her too
        assertEquals(2, MainBot.cover(both, both.qx[0], both.qy[0]));
        assertEquals("BUILD 0 TOWER", queen(both, plain + ",HOLD_OVERLAP=180,OVERLAP_FIRST=2"), "two towers cover her: the top-up");
        // Touching her own mine C under A alone: the upgrade is the touch rule's; OVERLAP_FIRST walks her to the overlap first.
        GameState mine = s.copy();
        mine.stType[2] = Const.MINE; mine.stOwner[2] = 0; mine.mineRate[2] = 1;
        mine.qx[0] = 300; mine.qy[0] = 700 - 70 - Const.QUEEN_RADIUS - 2;
        mine.cx[0] = 700; mine.cy[0] = 700;   // 413 from her
        assertEquals(2, Protocol.touchedSite(mine, 0));
        assertEquals(1, MainBot.cover(mine, mine.qx[0], mine.qy[0]));
        assertEquals("BUILD 2 MINE", queen(mine, plain + ",HOLD_OVERLAP=300"), "the touch rule's upgrade first");
        assertTrue(queen(mine, plain + ",HOLD_OVERLAP=300,OVERLAP_FIRST=1").startsWith("MOVE"), "OVERLAP_FIRST: the overlap before the upgrade");
        assertTrue(queen(mine, plain + ",HOLD_OVERLAP=300,OVERLAP_FIRST=2").startsWith("MOVE"), "and with fewer than 2 covering towers");

        GameState t = s.copy();
        t.towerRange[1] = 200;
        t.qx[0] = 540; t.qy[0] = 330;   // 191 from A, 220 from B: under A alone
        t.cx[0] = 540; t.cy[0] = 730;
        assertEquals(-1, Protocol.touchedSite(t, 0));
        assertEquals(1, MainBot.cover(t, t.qx[0], t.qy[0]));
        assertEquals("BUILD 0 TOWER", queen(t, plain), "v13: her nearest tower A, where A alone covers her");
        assertEquals("BUILD 1 TOWER", queen(t, plain + ",HOLD_OVERLAP=180"), "B's touch point lies under both towers");
        t.cx[0] = 800; t.cy[0] = 100;   // 347 from her, 229 from B's touch point
        assertNotEquals("BUILD 1 TOWER", queen(t, plain + ",HOLD_OVERLAP=180"), "no spot within 250 of the knight");
        t.cx[0] = 540; t.cy[0] = 530;   // 200 from her: the flight (no kiting)
        assertEquals("BUILD 0 TOWER", queen(t, plain + ",KITE=0"), "v13's flight: her nearest tower");
        assertEquals("BUILD 1 TOWER", queen(t, plain + ",KITE=0,HOLD_OVERLAP=180"), "the flight to the better-covered touch point");
    }

    /**
     * OVERLAP_TOUCH (2026-11-10, docs/data/exp-2026-11-10-open-ground): how many covering towers a spot off the towers
     * needs above the best touch point. The hold's position of the test above (she touches A under A alone, the overlap
     * of A and B three steps away on a heading): v14 moves for one more tower; with OVERLAP_TOUCH=1 she stays at A, and
     * moves once a third tower D covers the overlap too (two more); with 9 never. Standing between the towers A and E
     * under both, 240 and 260 from them, where each touch point lies under one: v14 stands, OVERLAP_TOUCH walks her to A.
     */
    @Test
    void overlapTouchKeepsTheHoldAtTheTowers() {
        String hold = "ROUTE=0,UNSTICK=0,IDLE_OVERLAP=0,HOLD_OVERLAP=180";
        GameState s = position();
        s.turn = 60;
        for (int i : new int[]{0, 1}) { s.stType[i] = Const.TOWER; s.stOwner[i] = 0; s.towerHp[i] = 700; }
        s.towerRange[0] = 300; s.towerRange[1] = 350;
        s.qx[0] = 350 - 70 - Const.QUEEN_RADIUS - 2; s.qy[0] = 350;   // touching A from the west, under A alone
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = 650; s.cy[0] = 550;
        assertTrue(queen(s, hold).startsWith("MOVE"), "v14: the heading to the overlap of A and B");
        assertEquals(queen(s, hold), queen(s, hold + ",OVERLAP_TOUCH=0"), "0 is v14");
        assertEquals("BUILD 0 TOWER", queen(s, hold + ",OVERLAP_TOUCH=1"), "one more tower is not enough: v13's command at A");
        assertEquals("BUILD 0 TOWER", queen(s, hold + ",OVERLAP_TOUCH=9"), "the touch points only");
        GameState three = s.copy();
        site(three, 3, 500, 100, 0, 0);
        three.stType[3] = Const.TOWER; three.stOwner[3] = 0; three.towerHp[3] = 700; three.towerRange[3] = 300;   // D covers the overlap, not her
        assertEquals(1, MainBot.cover(three, three.qx[0], three.qy[0]));
        GameState there = follow(three, hold, 3);
        assertEquals(3, MainBot.cover(there, there.qx[0], there.qy[0]), "v14 reaches the spot under A, B and D at " + there.qx[0] + "," + there.qy[0]);
        assertEquals(queen(three, hold), queen(three, hold + ",OVERLAP_TOUCH=1"), "two more towers: the same heading");
        assertEquals("BUILD 0 TOWER", queen(three, hold + ",OVERLAP_TOUCH=2"), "two more are not enough for 2");
        assertEquals("BUILD 0 TOWER", queen(three, hold + ",OVERLAP_TOUCH=9"), "the touch points only");

        GameState open = position();
        open.turn = 60;
        site(open, 3, 850, 350, 0, 0);
        for (int i : new int[]{0, 3}) { open.stType[i] = Const.TOWER; open.stOwner[i] = 0; open.towerHp[i] = 700; open.towerRange[i] = 300; }
        open.qx[0] = 590; open.qy[0] = 350;   // 240 from A, 260 from E: under both, touching neither
        open.nCreeps = 1; open.cOwner[0] = 1; open.cType[0] = Const.KNIGHT; open.cHp[0] = 25; open.cx[0] = 590; open.cy[0] = 800;   // 450 from her
        assertEquals(-1, Protocol.touchedSite(open, 0));
        assertEquals(2, MainBot.cover(open, open.qx[0], open.qy[0]));
        assertEquals("MOVE 590 350", queen(open, hold), "v14 stands where both towers cover her");
        assertEquals("BUILD 0 TOWER", queen(open, hold + ",OVERLAP_TOUCH=1"), "each touch point lies under one tower: she walks to A, her nearest");
        assertEquals("BUILD 0 TOWER", queen(open, hold + ",OVERLAP_TOUCH=9"), "the touch points only");
    }

    /**
     * CLEAR_TOUCH (2026-11-10, docs/data/exp-2026-11-10-gate-touch): in the gate's state (turn 60, own towers A and B, an
     * enemy knight 413 from the queen) the hold comes before the touch rule's upgrade of the mine she touches (1), a new
     * mine or barracks on the free site she touches (2) and a new tower there (4). With the knight beyond 500 the touch
     * rule builds as before. Where the hold leaves her standing (under both towers A and E, each touch point under one)
     * the upgrade stands.
     */
    @Test
    void clearTouchPutsTheHoldBeforeUpgradesAndBuilds() {
        String v13 = "ROUTE=0,UNSTICK=0,IDLE_OVERLAP=0,HOLD_OVERLAP=0";   // the hold at her nearest tower
        GameState s = position();
        s.turn = 60;
        for (int i : new int[]{0, 1}) { s.stType[i] = Const.TOWER; s.stOwner[i] = 0; s.towerHp[i] = 700; }
        s.towerRange[0] = 300; s.towerRange[1] = 350;
        s.qx[0] = 300; s.qy[0] = 700 - 70 - Const.QUEEN_RADIUS - 2;   // touching C from the north, under A alone
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 25; s.cx[0] = 700; s.cy[0] = 700;   // 413 from her
        assertEquals(2, Protocol.touchedSite(s, 0));

        GameState mine = s.copy();
        mine.stType[2] = Const.MINE; mine.stOwner[2] = 0; mine.mineRate[2] = 1;
        assertEquals("BUILD 2 MINE", queen(mine, v13), "v14: the touch rule's upgrade first");
        assertEquals("BUILD 0 TOWER", queen(mine, v13 + ",CLEAR_TOUCH=1"), "the hold first: her nearest tower");
        assertTrue(queen(mine, "ROUTE=0,UNSTICK=0,CLEAR_TOUCH=1").startsWith("MOVE"), "with v14's hold: the overlap of A and B");
        assertEquals("BUILD 2 MINE", queen(mine, v13 + ",CLEAR_TOUCH=6"), "2 and 4 leave the upgrade");
        GameState far = mine.copy();
        far.cx[0] = 900;   // 608 from her: not the gate's state
        assertEquals("BUILD 2 MINE", queen(far, v13 + ",CLEAR_TOUCH=7"), "no knight within 500: the touch rule");
        GameState early = mine.copy();
        early.turn = 20;
        site(early, 3, 1700, 800, 0, 0);
        early.stType[3] = Const.BARRACKS; early.stOwner[3] = 1; early.barrType[3] = Const.KNIGHT;
        assertEquals("BUILD 2 MINE", queen(early, v13 + ",CLEAR_TOUCH=7"), "the first-wave window: no gate, the touch rule");

        // The free site C: the ladder wants a tower there (no mines or barracks wanted), or a mine (the income below its target).
        String tower = v13 + ",MINES_FIRST=0,BARRACKS=0,INCOME_TARGET=0", gold = v13 + ",MINES_FIRST=0,BARRACKS=0,INCOME_TARGET=5";
        assertEquals("BUILD 2 TOWER", queen(s, tower), "v14: the new tower on the touched site");
        assertEquals("BUILD 2 TOWER", queen(s, tower + ",CLEAR_TOUCH=3"), "1 and 2 leave the new tower");
        assertEquals("BUILD 0 TOWER", queen(s, tower + ",CLEAR_TOUCH=4"), "the hold before the new tower");
        assertEquals("BUILD 2 MINE", queen(s, gold), "v14: the new mine on the touched site");
        assertEquals("BUILD 2 MINE", queen(s, gold + ",CLEAR_TOUCH=5"), "1 and 4 leave the new mine");
        assertEquals("BUILD 0 TOWER", queen(s, gold + ",CLEAR_TOUCH=2"), "the hold before the new mine");
        String barracks = v13 + ",MINES_FIRST=0,BARRACKS=1";
        assertEquals("BUILD 2 BARRACKS-KNIGHT", queen(s, barracks), "v14: the barracks on the touched site");
        assertEquals("BUILD 0 TOWER", queen(s, barracks + ",CLEAR_TOUCH=2"), "the hold before the new barracks");

        // Her mine M under both towers A and E, each tower's touch point under one: the hold stands, so the upgrade does.
        GameState open = position();
        open.turn = 60;
        site(open, 3, 850, 350, 0, 0);
        site(open, 4, 600, 450, 300, 3);
        for (int i : new int[]{0, 3}) { open.stType[i] = Const.TOWER; open.stOwner[i] = 0; open.towerHp[i] = 700; open.towerRange[i] = 300; }
        open.stType[4] = Const.MINE; open.stOwner[4] = 0; open.mineRate[4] = 1;
        open.qx[0] = 600; open.qy[0] = 350;   // touching M from the north, 250 from A and from E
        open.nCreeps = 1; open.cOwner[0] = 1; open.cType[0] = Const.KNIGHT; open.cHp[0] = 25; open.cx[0] = 600; open.cy[0] = 800;   // 450 from her
        assertEquals(4, Protocol.touchedSite(open, 0));
        assertEquals(2, MainBot.cover(open, open.qx[0], open.qy[0]));
        assertEquals("BUILD 4 MINE", queen(open, "ROUTE=0,UNSTICK=0,CLEAR_TOUCH=1"), "the hold stands where she is: the upgrade stands");
        assertEquals("BUILD 0 TOWER", queen(open, v13 + ",CLEAR_TOUCH=1"), "v13's hold walks her to A");
    }

    /**
     * KITE_OVERLAP: the queen at her tower A under A alone (B's range ends short of her), two knights east of her. The
     * kiting of v13 steps to a spot under A alone; with a weight of 500 a tower the step ends under both towers.
     */
    @Test
    void kiteOverlapPrefersAStepUnderMoreTowers() {
        GameState s = position();
        s.stType[0] = Const.TOWER; s.stOwner[0] = 0; s.towerHp[0] = 400; s.towerRange[0] = 300;
        s.stType[1] = Const.TOWER; s.stOwner[1] = 0; s.towerHp[1] = 400; s.towerRange[1] = 280;
        s.qx[0] = 350 + 70 + Const.QUEEN_RADIUS + 2; s.qy[0] = 350;   // touching A from the east, 297 from B
        s.nCreeps = 2;
        for (int i = 0; i < 2; i++) { s.cOwner[i] = 1; s.cType[i] = Const.KNIGHT; s.cHp[i] = 25; s.cx[i] = s.qx[0] + 120 + 30 * i; s.cy[i] = 350; }
        assertEquals(1, MainBot.cover(s, s.qx[0], s.qy[0]));
        cr.core.Physics ph = new cr.core.Physics();
        Command v13 = new MainBot(new Params()).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertEquals(Command.MOVE, v13.queen, v13.queenLine());
        ph.queenStep(s, s.qx[0], s.qy[0], v13.x, v13.y, 5);
        assertEquals(1, MainBot.cover(s, ph.rx, ph.ry), "v13's step ends under A alone");
        Command over = new MainBot(new Params().apply("KITE_OVERLAP=500")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertEquals(Command.MOVE, over.queen, over.queenLine());
        ph.queenStep(s, s.qx[0], s.qy[0], over.x, over.y, 5);
        assertEquals(2, MainBot.cover(s, ph.rx, ph.ry), "with the weight the step ends under both towers");
    }

    /**
     * IDLE_OVERLAP and PUMP_LOW on the complete ladder: the home tower H (100,100) stands alone, the towers D (900,500) and
     * E (1100,400) cover each other. v13 idles at H; IDLE_OVERLAP at D, the better covered of the two nearer the home
     * corner, or at E when D lies inside an enemy tower's range. PUMP_LOW=600 sends her to the weakest tower below 600.
     */
    @Test
    void idleOverlapAndPumpLowChooseTheIdleTower() {
        GameState s = ladderComplete();
        site(s, 4, 900, 500, 0, 0); s.stType[4] = Const.TOWER; s.stOwner[4] = 0; s.towerHp[4] = 700; s.towerRange[4] = 400;
        site(s, 5, 1100, 400, 0, 0); s.stType[5] = Const.TOWER; s.stOwner[5] = 0; s.towerHp[5] = 700; s.towerRange[5] = 400;
        String v13 = LADDER_DONE + ",IDLE_OVERLAP=0";   // the idle tower nearest home (IDLE_OVERLAP=1 by default since v14)
        assertEquals("BUILD 3 TOWER", queen(s, v13), "v13: the tower nearest home");
        assertEquals("BUILD 4 TOWER", queen(s, v13 + ",IDLE_OVERLAP=1"), "D and E cover each other; D is nearer the corner");
        assertEquals("BUILD 4 TOWER", queen(s, LADDER_DONE), "the default since v14");
        assertEquals("BUILD 3 TOWER", queen(s, v13 + ",PUMP_LOW=600"), "no tower below 600: the idle tower");
        s.towerHp[5] = 550; s.towerHp[4] = 650;
        assertEquals("BUILD 5 TOWER", queen(s, v13 + ",PUMP_LOW=600"), "E is the weakest and below 600");
        assertEquals("BUILD 5 TOWER", queen(s, v13 + ",PUMP_LOW=600,IDLE_OVERLAP=1"), "the weak tower comes before the best-covered one");
        s.towerHp[4] = 500;
        assertEquals("BUILD 4 TOWER", queen(s, v13 + ",PUMP_LOW=600"), "D is weaker");
        site(s, 6, 1000, 800, 0, 0); s.stType[6] = Const.TOWER; s.stOwner[6] = 1; s.towerHp[6] = 500; s.towerRange[6] = 400;   // 316 from D, 412 from E
        assertEquals("BUILD 5 TOWER", queen(s, v13 + ",PUMP_LOW=600"), "D lies inside an enemy tower's range: E");
        assertEquals("BUILD 5 TOWER", queen(s, v13 + ",IDLE_OVERLAP=1"), "and E is the idle tower");
    }

    /** CLEAR_HP: the gate's knight (400 away) with 10 HP holds her as v13 with CLEAR_HP=8 and not with CLEAR_HP=10. */
    @Test
    void clearHpIgnoresDyingKnights() {
        String plain = "ROUTE=0,UNSTICK=0";
        GameState s = position();
        s.turn = 60;
        for (int i : new int[]{0, 1}) { s.stType[i] = Const.TOWER; s.stOwner[i] = 0; s.towerHp[i] = 700; s.towerRange[i] = 400; }
        s.nCreeps = 1; s.cOwner[0] = 1; s.cType[0] = Const.KNIGHT; s.cHp[0] = 10; s.cx[0] = 900; s.cy[0] = 350;
        assertEquals("BUILD 0 TOWER", queen(s, plain), "v13: the hold");
        assertEquals("BUILD 0 TOWER", queen(s, plain + ",CLEAR_HP=8"), "10 HP is above 8: the hold");
        assertEquals("BUILD 2 MINE", queen(s, plain + ",CLEAR_HP=10"), "10 HP or less does not count: the ladder");
        s.cx[0] = 700;
        assertTrue(queen(s, plain + ",CLEAR_HP=10").startsWith("MOVE"), "within 250 it is still the kiting's");
    }

    /** The forced step's MOVE moves the queen 40 or more and ends within an own tower's range. */
    private static void assertForcedStep(GameState s, String line) {
        assertTrue(line.startsWith("MOVE "), line);
        String[] m = line.split(" ");
        cr.core.Physics ph = new cr.core.Physics();
        ph.queenStep(s, s.qx[0], s.qy[0], Integer.parseInt(m[1]), Integer.parseInt(m[2]), 5);
        double moved = Math.hypot(ph.rx - s.qx[0], ph.ry - s.qy[0]);
        assertTrue(moved >= 40, "the forced step moves her " + moved);
        boolean covered = false;
        for (int i = 0; i < s.nSites; i++)
            if (s.stType[i] == Const.TOWER && s.stOwner[i] == 0 && Math.hypot(ph.rx - s.sx[i], ph.ry - s.sy[i]) < s.towerRange[i]) covered = true;
        assertTrue(covered, "and ends under an own tower");
    }
}
