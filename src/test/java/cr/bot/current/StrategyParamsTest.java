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
 * Their defaults play as v04 ({@link MainBotTest#withDefaultsCurrentPlaysAsV04}).
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
        assertTrue(builds(position(), "", 20).startsWith("0:B 2:M"), "v04: the barracks at the nearest site, then a mine");
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
        String p = "INCOME_TARGET=2,MAX_TOWERS=0,ROUTE=0,UNSTICK=0,";
        assertEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "UPGRADE=0")).play(s, -1, Long.MAX_VALUE).queenLine());
        assertEquals("BUILD 0 MINE", new MainBot(new Params().apply(p + "UPGRADE=1")).play(s, -1, Long.MAX_VALUE).queenLine());
        // the next-site prediction agrees: after the upgrade at A the income meets the target, nothing is next
        assertEquals(-1, ApproachPlanner.nextTarget(s, 0, "MINE", 450, 350, 0, 0, new Params().apply(p + "UPGRADE=1")));
        // a mine at its maximum is no target (income 3 below a target of 4: the free gold site C again)
        s.mineRate[0] = 3;
        assertEquals("BUILD 2 MINE", new MainBot(new Params().apply(p + "UPGRADE=1,INCOME_TARGET=4")).play(s, -1, Long.MAX_VALUE).queenLine());
    }
}
