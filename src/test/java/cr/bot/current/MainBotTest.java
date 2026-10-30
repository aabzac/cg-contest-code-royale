package cr.bot.current;

import cr.bot.BotFactory;
import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.InputReader;
import cr.core.Physics;
import cr.referee.Referee;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MainBotTest {
    /**
     * A captured position: the init and turn input that seat 1 of v01 against current read on one
     * turn (written by playing the game as Referee.play does; resources cr/bot/current/*.txt).
     */
    private static InputReader position(String name) throws IOException {
        InputReader in = new InputReader(MainBotTest.class.getResourceAsStream(name));
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

    /**
     * Follows the bot's commands with the sites-only step ({@link Physics#queenStep}) and returns the
     * turns until the queen touches the site, or -1 after maxTurns.
     */
    private static int turnsToTouch(String params, InputReader in, int site, int maxTurns) {
        GameState s = in.state.copy();
        MainBot bot = new MainBot(new Params().apply(params));
        Physics ph = new Physics();
        for (int t = 0; t < maxTurns; t++) {
            if (touches(s, site)) return t;
            Command c = bot.play(s, -1, Long.MAX_VALUE);
            double tx = c.queen == Command.MOVE ? c.x : s.sx[c.site], ty = c.queen == Command.MOVE ? c.y : s.sy[c.site];
            ph.queenStep(s, s.qx[0], s.qy[0], tx, ty, 5);
            s.qx[0] = ph.rx;
            s.qy[0] = ph.ry;
            s.turn++;
        }
        return -1;
    }

    /**
     * Seed 10477, turn 51: the queen at 1802,899 presses against her barracks on site 20, whose centre
     * lies on her line to site 12. BUILD 12 MINE walks her into it and the push puts her back: v01
     * stood here for 43 turns and lost the game.
     */
    @Test
    void aSiteStraightAheadIsWalkedAround() throws IOException {
        InputReader in = position("stall-10477-seat1-turn51.txt");
        GameState s = in.state;
        assertEquals(1802, s.qx[0]);
        assertEquals(899, s.qy[0]);
        assertEquals("BUILD 12 MINE", play("UNSTICK=0", in).queenLine());
        Physics ph = new Physics();
        ph.queenStep(s, s.qx[0], s.qy[0], s.sx[12], s.sy[12], 5);
        assertEquals(1802, ph.rx);
        assertEquals(899, ph.ry);

        Command c = play("UNSTICK=1", in);
        assertEquals(Command.MOVE, c.queen, c.queenLine());
        ph.queenStep(s, s.qx[0], s.qy[0], c.x, c.y, 5);
        assertTrue(Physics.distSq(ph.rx, ph.ry, s.qx[0], s.qy[0]) >= 20 * 20, "the detour moves her");
        int turns = turnsToTouch("UNSTICK=1", in, 12, 20);
        assertTrue(turns > 0 && turns <= 10, "site 12 reached in " + turns + " turns");
        assertEquals(-1, turnsToTouch("UNSTICK=0", in, 12, 20));
    }

    /**
     * Seed 10394, turn 98: the queen at 1551,710 touches site 13 and site 8 (so touchedSite is -1),
     * and her line to site 3 runs between them through a gap narrower than she is. v01 stood here
     * for 10, 13 and 17 turns.
     */
    @Test
    void twoSitesThatWedgeTheQueenAreWalkedAround() throws IOException {
        InputReader in = position("wedge-10394-seat1-turn98.txt");
        assertEquals(-1, in.touchedSite);
        assertEquals("BUILD 3 MINE", play("UNSTICK=0", in).queenLine());
        assertEquals(Command.MOVE, play("UNSTICK=1", in).queen);
        int turns = turnsToTouch("UNSTICK=1", in, 3, 25);
        assertTrue(turns > 0 && turns <= 15, "site 3 reached in " + turns + " turns");
        assertEquals(-1, turnsToTouch("UNSTICK=0", in, 3, 25));
    }

    /** A BUILD whose straight walk makes progress is left alone. */
    @Test
    void aFreeWalkKeepsTheBuild() throws IOException {
        InputReader in = position("stall-10477-seat1-turn51.txt");
        GameState s = in.state;
        s.qx[0] = 1700;   // away from site 20, the line to site 12 is clear
        s.qy[0] = 700;
        Command off = play("UNSTICK=0", in), on = play("UNSTICK=1", in);
        assertEquals(Command.BUILD, on.queen);
        assertEquals(off.queenLine(), on.queenLine());
    }

    /** With UNSTICK off, current plays v01's games command for command. */
    @Test
    void withUnstickOffCurrentPlaysAsV01() {
        for (long seed = 10470; seed < 10480; seed++) {
            Referee.Result mirror = Referee.play(seed, BotFactory.create("v01"), BotFactory.create("v01"), true, 50);
            Referee.Result c0 = Referee.play(seed, BotFactory.create("current?UNSTICK=0"), BotFactory.create("v01"), true, 50);
            Referee.Result c1 = Referee.play(seed, BotFactory.create("v01"), BotFactory.create("current?UNSTICK=0"), true, 50);
            assertEquals(mirror.replay, c0.replay, "seed " + seed + " seat 0");
            assertEquals(mirror.replay, c1.replay, "seed " + seed + " seat 1");
        }
    }

    /** With its defaults, current plays the frozen v02's games command for command (seeds with UNSTICK detours). */
    @Test
    void withDefaultsCurrentPlaysAsV02() {
        for (long seed : new long[]{10035, 10062, 10206, 10394, 10477, 200002, 200035, 200060}) {
            for (String opp : new String[]{"v01", "v02"}) {
                Referee.Result v0 = Referee.play(seed, BotFactory.create("v02"), BotFactory.create(opp), true, 50);
                Referee.Result c0 = Referee.play(seed, BotFactory.create("current"), BotFactory.create(opp), true, 50);
                Referee.Result v1 = Referee.play(seed, BotFactory.create(opp), BotFactory.create("v02"), true, 50);
                Referee.Result c1 = Referee.play(seed, BotFactory.create(opp), BotFactory.create("current"), true, 50);
                assertEquals(v0.replay, c0.replay, "seed " + seed + " seat 0 against " + opp);
                assertEquals(v1.replay, c1.replay, "seed " + seed + " seat 1 against " + opp);
            }
        }
    }
}
