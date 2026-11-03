package cr.bot.current;

import com.google.gson.Gson;
import cr.bot.BotFactory;
import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.InputReader;
import cr.core.Physics;
import cr.core.Protocol;
import cr.core.Rules;
import cr.referee.Referee;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerTest {
    private static final Gson JSON = new Gson();
    /** v01's and v02's parameters: the stall fixtures were captured with them. */
    private static final String V02 = "INCOME_TARGET=10,MAX_TOWERS=4,MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,";

    private static InputReader position(String name) throws IOException {
        InputReader in = new InputReader(RoutePlannerTest.class.getResourceAsStream(name));
        in.readInit();
        in.readTurn();
        return in;
    }

    private static Command play(String params, InputReader in) {
        return new MainBot(new Params().apply(params)).play(in.state, in.touchedSite, Long.MAX_VALUE);
    }

    private static boolean touches(GameState s, int site) {
        return Physics.distSq(s.sx[site], s.sy[site], s.qx[0], s.qy[0]) < Math.pow(s.sr[site] + Const.QUEEN_RADIUS + Const.TOUCHING_DELTA, 2);
    }

    /** Follows the bot's commands with the sites-only step and returns the turns until the queen touches the site, or -1. */
    private static int turnsToTouch(String params, InputReader in, int site, int maxTurns) {
        GameState s = in.state.copy();
        MainBot bot = new MainBot(new Params().apply(params));
        Physics ph = new Physics();
        for (int t = 0; t < maxTurns; t++) {
            if (touches(s, site)) return t;
            Command c = bot.play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
            double tx = c.queen == Command.MOVE ? c.x : s.sx[c.site], ty = c.queen == Command.MOVE ? c.y : s.sy[c.site];
            ph.queenStep(s, s.qx[0], s.qy[0], tx, ty, 5);
            s.qx[0] = ph.rx;
            s.qy[0] = ph.ry;
            s.turn++;
        }
        return -1;
    }

    /**
     * The two stall fixtures of UNSTICK (MainBotTest): ROUTE walks around the site ahead (bound 5) and
     * out of the wedge, in no more turns than UNSTICK's detour (7 and 10 in the sites-only replay).
     */
    @Test
    void theStallFixturesAreWalkedAround() throws IOException {
        InputReader headOn = position("stall-10477-seat1-turn51.txt");
        assertEquals("BUILD 12 MINE", play(V02 + "UNSTICK=0,ROUTE=0", headOn).queenLine());
        assertEquals(Command.MOVE, play(V02 + "ROUTE=1", headOn).queen);
        int route = turnsToTouch(V02 + "ROUTE=1", headOn, 12, 20), unstick = turnsToTouch(V02 + "UNSTICK=1,ROUTE=0", headOn, 12, 20);
        assertTrue(route > 0 && route <= unstick, "site 12 reached in " + route + " turns, UNSTICK " + unstick);
        assertEquals(-1, turnsToTouch(V02 + "UNSTICK=0,ROUTE=0", headOn, 12, 20));
        InputReader wedge = position("wedge-10394-seat1-turn98.txt");
        assertEquals(-1, wedge.touchedSite);
        assertEquals(Command.MOVE, play(V02 + "ROUTE=1", wedge).queen);
        route = turnsToTouch(V02 + "ROUTE=1", wedge, 3, 25);
        unstick = turnsToTouch(V02 + "UNSTICK=1,ROUTE=0", wedge, 3, 25);
        assertTrue(route > 0 && route <= unstick, "site 3 reached in " + route + " turns, UNSTICK " + unstick);
        assertEquals(-1, turnsToTouch(V02 + "UNSTICK=0,ROUTE=0", wedge, 3, 25));
    }

    /**
     * Seed 110135, turn 144 (the study's worst v03 leg): the queen at 307,621 is sent to site 13 and
     * slides along her own tower's rim for seven turns, touching 13 on turn 155 (12 turns, bound 6).
     * ROUTE sees the slide in the prediction and goes around.
     */
    @Test
    void theSlideOfSeed110135IsAvoided() throws IOException {
        InputReader in = position("slide-110135-seat0-turn144.txt");
        GameState s = in.state;
        assertEquals(307, s.qx[0]);
        assertEquals(621, s.qy[0]);
        assertEquals("BUILD 13 MINE", play("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=0", in).queenLine());
        // The captured enemy knights would make the sites-only replay flee before she touches 13.
        s.nCreeps = 0;
        int straight = turnsToTouch("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=0", in, 13, 25);
        assertTrue(straight >= 10, "v03's walk slides: " + straight + " turns in the prediction");
        Command c = play("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1", in);
        assertEquals(Command.MOVE, c.queen, c.queenLine());
        int turns = turnsToTouch("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1", in, 13, 25);
        assertTrue(turns > 0 && turns <= 7, "site 13 reached in " + turns + " turns");
    }

    /** A clean straight walk whose next target is one turn from its landing is left alone. */
    @Test
    void aFreeWalkKeepsTheBuild() throws IOException {
        InputReader in = position("stall-10477-seat1-turn51.txt");
        GameState s = in.state;
        s.qx[0] = 1700;
        s.qy[0] = 700;
        Command off = play(V02 + "ROUTE=0", in), on = play(V02 + "ROUTE=1", in);
        assertEquals(Command.BUILD, on.queen, on.queenLine());
        assertEquals(off.queenLine(), on.queenLine());
    }

    private static void site(GameState s, int id, int x, int y) {
        s.nSites = Math.max(s.nSites, id + 1);
        s.sx[id] = x; s.sy[id] = y; s.sr[id] = 70;
        s.sArea[id] = Math.PI * 70 * 70;
        s.sGold[id] = 225; s.sMaxMine[id] = 3;
        s.clearStructure(id);
    }

    /** ApproachPlannerTest's position: A at 350,350 is the next site, B at 680,160 the one after. */
    private static GameState landingPosition() {
        GameState s = new GameState();
        site(s, 0, 350, 350);
        site(s, 1, 680, 160);
        s.qx[0] = 500; s.qy[0] = 350;
        s.qx[1] = 1800; s.qy[1] = 900;
        for (int p = 0; p < 2; p++) { s.active[p] = true; s.hp[p] = s.score[p] = 100; }
        s.gold[0] = s.gold[1] = 0;
        return s;
    }

    private static int[] constructionTurns(GameState s, String params) {
        MainBot bot = new MainBot(new Params().apply(params));
        Rules rules = new Rules();
        int[] built = {-1, -1};
        for (int t = 0; t < 20 && built[1] < 0; t++) {
            Command c = bot.play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
            assertFalse(c.kill);
            rules.resolve(s, c, Command.waitCmd());
            assertNull(rules.lastKill[0]);
            assertNull(rules.lastWarning[0]);
            for (int id = 0; id <= 1; id++) if (built[id] < 0 && s.stOwner[id] == 0) built[id] = s.turn;
        }
        return built;
    }

    /** APPROACH's landing case under the real rules: the same A on the same turn, B one turn earlier. */
    @Test
    void theLandingPointBringsTheNextSiteATurnCloser() {
        GameState s = landingPosition();
        String before = JSON.toJson(s);
        Command c = new MainBot(new Params().apply("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertEquals(before, JSON.toJson(s), "planning must not change the observation");
        assertEquals(Command.MOVE, c.queen, c.queenLine());
        assertArrayEquals(new int[]{2, 7}, constructionTurns(landingPosition(), "MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=0"));
        assertArrayEquals(new int[]{2, 6}, constructionTurns(landingPosition(), "MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1"));
    }

    /** With an enemy knight within 250 the flee rule decides before the walk is planned: ROUTE changes nothing. */
    @Test
    void aThreatenedQueenFleesAsWithoutRoute() {
        GameState s = landingPosition();
        site(s, 2, 150, 150);
        s.stType[2] = Const.TOWER; s.stOwner[2] = 0; s.towerHp[2] = 500; s.towerRange[2] = 300;
        s.addCreep(1, Const.KNIGHT, 650, 350);
        Command off = new MainBot(new Params().apply("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=0")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        Command on = new MainBot(new Params().apply("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1")).play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
        assertEquals("BUILD 2 TOWER", off.queenLine());
        assertEquals(off.queenLine(), on.queenLine());
    }

    /** The same position gives the same command, in a fresh planner and in one that has planned before; whole games repeat. */
    @Test
    void plansAreDeterministic() throws IOException {
        InputReader in = position("slide-110135-seat0-turn144.txt");
        MainBot bot = new MainBot(new Params().apply("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1"));
        String first = bot.play(in.state, in.touchedSite, Long.MAX_VALUE).queenLine();
        assertEquals(first, bot.play(in.state, in.touchedSite, Long.MAX_VALUE).queenLine());
        assertEquals(first, play("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1", in).queenLine());
        for (long seed : new long[]{110000, 110135}) {
            Referee.Result a = Referee.play(seed, BotFactory.create("current?MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1"), BotFactory.create("v03"), true, 50);
            Referee.Result b = Referee.play(seed, BotFactory.create("current?MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1"), BotFactory.create("v03"), true, 50);
            assertEquals(a.replay, b.replay, "seed " + seed);
            assertNull(a.error[0]);
        }
    }

    /** The search is bounded by step predictions, never by the clock: a deadline in the past changes nothing. */
    @Test
    void theSearchIgnoresTheClock() throws IOException {
        InputReader in = position("slide-110135-seat0-turn144.txt");
        MainBot bot = new MainBot(new Params().apply("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1"));
        long before = bot.queenSteps();
        Command c = bot.play(in.state, in.touchedSite, System.nanoTime() - 1_000_000_000L);
        long calls = bot.queenSteps() - before;
        assertEquals(play("MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,ROUTE=1", in).queenLine(), c.queenLine());
        assertTrue(calls > 0 && calls <= RoutePlanner.MAX_CALLS + HORIZON_SLACK, "calls " + calls);
    }

    /** One walk may run past the budget check by at most its horizon. */
    private static final int HORIZON_SLACK = RoutePlanner.HORIZON;
}
