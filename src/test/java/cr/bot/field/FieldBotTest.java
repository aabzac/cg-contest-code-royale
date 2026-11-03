package cr.bot.field;

import cr.bot.BotFactory;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Protocol;
import cr.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The field-balance opponent: legal play against v06, the giant barracks against towers, the touch rule's ladder. */
class FieldBotTest {
    private static void site(GameState s, int id, int x, int y, int gold, int maxMine) {
        s.nSites = Math.max(s.nSites, id + 1);
        s.sx[id] = x; s.sy[id] = y; s.sr[id] = 70;
        s.sArea[id] = Math.PI * 70 * 70;
        s.sGold[id] = gold; s.sMaxMine[id] = maxMine;
        s.clearStructure(id);
    }

    /** A (gold) at 350,350, B (no gold) at 680,160, C (gold) at 300,700, D at 1100,600; the queen at 600,350; the enemy at 1800,900. */
    private static GameState position() {
        GameState s = new GameState();
        site(s, 0, 350, 350, 300, 3);
        site(s, 1, 680, 160, 0, 0);
        site(s, 2, 300, 700, 300, 3);
        site(s, 3, 1100, 600, 0, 0);
        s.qx[0] = 600; s.qy[0] = 350;
        s.qx[1] = 1800; s.qy[1] = 900;
        for (int p = 0; p < 2; p++) { s.active[p] = true; s.hp[p] = s.score[p] = 100; }
        s.gold[0] = 200; s.gold[1] = 100;
        return s;
    }

    /** Ten maps on both seats against v06 with GIANTS=1: no error, kill, timeout or extra line; giants trained in at least one game; the spec describes itself. */
    @Test
    void playsLegallyAgainstV06AndTrainsGiants() {
        int giantGames = 0, wins = 0;
        for (long seed = 10000; seed < 10010; seed++) {
            for (int seat = 0; seat < 2; seat++) {
                Referee.Result r = seat == 0 ? Referee.play(seed, BotFactory.create("field?GIANTS=1"), BotFactory.create("v06"), true, 50)
                                             : Referee.play(seed, BotFactory.create("v06"), BotFactory.create("field?GIANTS=1"), true, 50);
                assertNull(r.error[0], "seed " + seed);
                assertNull(r.error[1], "seed " + seed);
                assertEquals(0, r.timeouts[seat], "seed " + seed);
                assertEquals(0, r.extraLines[seat], "seed " + seed);
                assertTrue(r.score[seat] >= 0, "seed " + seed + ": the referee removed field");
                if (r.winner == seat) wins++;
                boolean giants = false;
                for (Referee.Frame f : r.frames) for (int[] c : f.creeps) if (c[0] == seat && c[1] == Const.GIANT) giants = true;
                if (giants) giantGames++;
            }
        }
        assertTrue(giantGames >= 1, "giants trained in " + giantGames + " of 20 games");
        assertTrue(wins <= 19, "field wins " + wins + " of 20");
        int defaultWins = 0;
        for (long seed = 10000; seed < 10010; seed++) {
            Referee.Result r = Referee.play(seed, BotFactory.create("field"), BotFactory.create("v06"), false, 50);
            assertNull(r.error[0], "seed " + seed);
            assertTrue(r.score[0] >= 0, "seed " + seed);
            if (r.winner == 0) defaultWins++;
        }
        assertTrue(defaultWins >= 1 && defaultWins <= 9, "field (defaults) wins " + defaultWins + " of 10 on seat 0: a playable opponent, not a wall and not a pushover");
        assertEquals("field " + new FieldParams(), BotFactory.describe("field"));
        assertEquals("field " + new FieldParams().apply("INCOME=6"), BotFactory.describe("field?INCOME=6"));
    }

    /** The ladder from an empty board: a mine first, then the knight barracks, then a tower; a giant barracks once the enemy holds two towers and the line stands. */
    @Test
    void ladderAndGiantBarracks() {
        GameState s = position();
        FieldBot bot = new FieldBot(new FieldParams());
        assertEquals("BUILD 0 MINE", bot.play(s, -1, Long.MAX_VALUE).queenLine(), "a mine first");
        s.stType[0] = Const.MINE; s.stOwner[0] = 0; s.mineRate[0] = 1;
        assertEquals("BUILD 1 BARRACKS-KNIGHT", bot.play(s, -1, Long.MAX_VALUE).queenLine(), "then the knight barracks");
        s.stType[1] = Const.BARRACKS; s.stOwner[1] = 0; s.barrType[1] = Const.KNIGHT;
        assertEquals("BUILD 2 TOWER", bot.play(s, -1, Long.MAX_VALUE).queenLine(), "then a tower, at the nearest free site (C)");
        // the line of 1 tower, 1 mine; TOWERS=1, MINES=1: the enemy's two towers call for a giant barracks
        FieldBot small = new FieldBot(new FieldParams().apply("TOWERS=1,MINES=1,GIANTS=1"));
        s.stType[2] = Const.TOWER; s.stOwner[2] = 0; s.towerHp[2] = 500; s.towerRange[2] = 300;
        assertEquals("BUILD 0 MINE", small.play(s, -1, Long.MAX_VALUE).queenLine(), "no enemy tower: the income phase upgrades the own mine A (D has no gold)");
        s.stType[3] = Const.TOWER; s.stOwner[3] = 1; s.towerHp[3] = 400; s.towerRange[3] = 300;
        site(s, 4, 900, 300, 0, 0);
        s.stType[4] = Const.TOWER; s.stOwner[4] = 1; s.towerHp[4] = 400; s.towerRange[4] = 300;
        site(s, 5, 500, 550, 0, 0); // 472 from the enemy tower at 900,300: outside its range of 300
        assertEquals("BUILD 5 BARRACKS-GIANT", small.play(s, -1, Long.MAX_VALUE).queenLine(), "two enemy towers: the giant barracks at the free site");
        // the touch rule builds what the plan wants: touching the free site 5
        s.qx[0] = 500 - 70 - Const.QUEEN_RADIUS - 2; s.qy[0] = 550;
        int touched = Protocol.touchedSite(s, 0);
        assertEquals(5, touched);
        assertEquals("BUILD 5 BARRACKS-GIANT", small.play(s, touched, Long.MAX_VALUE).queenLine());
        // training: a giant barracks idle, the enemy has towers, 140 gold: giants; then the knights from the other barracks
        s.stType[5] = Const.BARRACKS; s.stOwner[5] = 0; s.barrType[5] = Const.GIANT;
        s.gold[0] = 220;
        Command c = small.play(s, -1, Long.MAX_VALUE);
        assertArrayEquals(new int[]{5, 1}, c.train, "giants first, then knights (140 + 80 = 220)");
        s.gold[0] = 200;
        assertArrayEquals(new int[]{5}, small.play(s, -1, Long.MAX_VALUE).train, "200 gold: giants only");
    }
}
