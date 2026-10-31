package cr.bot.current;

import com.google.gson.Gson;
import cr.bot.Params;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.Protocol;
import cr.core.Rules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApproachPlannerTest {
    private static final int A = 0, B = 1;
    private static final Gson JSON = new Gson();

    private static void site(GameState s, int id, int x, int y) {
        s.nSites = Math.max(s.nSites, id + 1);
        s.sx[id] = x; s.sy[id] = y; s.sr[id] = 70;
        s.sArea[id] = Math.PI * 70 * 70;
        s.sGold[id] = 225; s.sMaxMine[id] = 3;
        s.clearStructure(id);
    }

    /** A is the ordinary home-biased choice; B is the next remaining free site. */
    private static GameState position() {
        GameState s = new GameState();
        site(s, A, 350, 350);
        site(s, B, 680, 160);
        s.qx[0] = 500; s.qy[0] = 350;
        s.qx[1] = 1800; s.qy[1] = 900;
        for (int p = 0; p < 2; p++) {
            s.active[p] = true; s.hp[p] = s.score[p] = 100;
        }
        // Isolate the movement geometry from new unit spawns in the construction timeline.
        s.gold[0] = s.gold[1] = 0;
        return s;
    }

    private static Command play(MainBot bot, GameState s) {
        return bot.play(s, Protocol.touchedSite(s, 0), Long.MAX_VALUE);
    }

    private static int[] constructionTurns(GameState s, int enabled) {
        MainBot bot = new MainBot(new Params().apply("APPROACH=" + enabled));
        Rules rules = new Rules();
        int[] built = {-1, -1};
        for (int t = 0; t < 20 && built[B] < 0; t++) {
            Command c = play(bot, s);
            assertFalse(c.kill);
            rules.resolve(s, c, Command.waitCmd());
            assertNull(rules.lastKill[0]);
            assertNull(rules.lastWarning[0]);
            for (int id = A; id <= B; id++) {
                if (built[id] < 0 && s.stOwner[id] == 0) built[id] = s.turn;
            }
        }
        return built;
    }

    @Test void anotherContactPointBuildsTheSameSiteOnTimeAndTheNextSiteEarlier() {
        GameState original = position(), baseline = original.copy(), candidate = original.copy();
        String observation = JSON.toJson(original);
        Command normal = play(new MainBot(new Params()), baseline);
        Command improved = play(new MainBot(new Params().apply("APPROACH=1")), candidate);
        assertEquals(observation, JSON.toJson(baseline));
        assertEquals(observation, JSON.toJson(candidate), "successful planning must not mutate the observation");
        assertEquals("BUILD 0 BARRACKS-KNIGHT", normal.queenLine());
        assertEquals(Command.MOVE, improved.queen, improved.queenLine());

        Rules rules = new Rules();
        rules.resolve(baseline, normal, Command.waitCmd());
        rules.resolve(candidate, improved, Command.waitCmd());
        assertEquals(A, Protocol.touchedSite(baseline, 0));
        assertEquals(A, Protocol.touchedSite(candidate, 0), "the actual engine must report only A as touched");
        assertEquals(Const.NONE, baseline.stType[A], "arrival does not also build");
        assertEquals(Const.NONE, candidate.stType[A]);
        assertTrue(baseline.qx[0] != candidate.qx[0] || baseline.qy[0] != candidate.qy[0]);

        int[] normalTurns = constructionTurns(original.copy(), 0);
        int[] improvedTurns = constructionTurns(original.copy(), 1);
        assertArrayEquals(new int[]{2, 7}, normalTurns);
        assertArrayEquals(new int[]{2, 6}, improvedTurns,
                "same A and build turn, then the same B one turn earlier under actual Rules");
    }

    @Test void approachDoesNotChangeTheIndependentTrainingOrder() {
        GameState s = position();
        site(s, 2, 1500, 500);
        s.stType[2] = Const.BARRACKS; s.stOwner[2] = 0; s.barrType[2] = Const.KNIGHT;
        s.gold[0] = 80;
        Command normal = play(new MainBot(new Params()), s.copy());
        Command improved = play(new MainBot(new Params().apply("APPROACH=1")), s.copy());
        assertEquals("BUILD 0 MINE", normal.queenLine());
        assertEquals(Command.MOVE, improved.queen, improved.queenLine());
        assertEquals("TRAIN 2", normal.trainLine());
        assertEquals(normal.trainLine(), improved.trainLine());
    }

    @Test void fullMineUpgradePhaseFinishesOnTimeBeforeTheSameNextTowerIsBuiltEarlier() {
        for (int enabled = 0; enabled <= 1; enabled++) {
            GameState s = position();
            site(s, 2, 1500, 500);
            s.stType[2] = Const.BARRACKS; s.stOwner[2] = 0; s.barrType[2] = Const.KNIGHT;
            MainBot bot = new MainBot(new Params().apply("APPROACH=" + enabled));
            Rules rules = new Rules();
            // First reached turns for mine rates 1, 2, 3 and then B's first tower.
            int[] milestones = {-1, -1, -1, -1};
            for (int t = 0; t < 20 && milestones[3] < 0; t++) {
                Command c = play(bot, s);
                assertEquals("TRAIN", c.trainLine(), "zero starting gold keeps the geometry free of spawns");
                rules.resolve(s, c, Command.waitCmd());
                assertNull(rules.lastKill[0]);
                assertNull(rules.lastWarning[0]);
                if (s.stOwner[A] == 0 && s.stType[A] == Const.MINE) {
                    int rate = s.mineRate[A];
                    if (milestones[rate - 1] < 0) milestones[rate - 1] = s.turn;
                }
                if (s.stOwner[B] == 0 && s.stType[B] == Const.TOWER) milestones[3] = s.turn;
            }
            assertArrayEquals(new int[]{2, 3, 4, enabled == 0 ? 9 : 8}, milestones,
                    "APPROACH=" + enabled + ": all A upgrades retain their timing, then B gains one turn");
            assertEquals(s.sMaxMine[A], s.mineRate[A]);
        }
    }

    private static void unchanged(GameState s) {
        unchanged(s, "BARRACKS-KNIGHT");
    }

    private static void unchanged(GameState s, String structure) {
        Command original = Command.build(A, structure);
        String before = JSON.toJson(s);
        Command result = new ApproachPlanner().improve(s, original, 0, 0, new Params());
        assertSame(original, result, "unsafe or unhelpful approach must keep the ordinary command");
        assertEquals(before, JSON.toJson(s), "planning must not mutate the observation");
    }

    @Test void movementIsUntouchedWhenNotOnTheLastApproachOrAlreadyInContact() {
        GameState far = position(); far.qx[0] = 650;
        unchanged(far);
        GameState touching = position(); touching.qx[0] = 451;
        unchanged(touching);
    }

    @Test void noExtraMovementWithoutANextGoalOrAnIntegerTurnGain() {
        GameState noNext = position(); noNext.nSites = 1;
        unchanged(noNext);
        GameState alreadyWellPlaced = position(); alreadyWellPlaced.sy[B] = 350;
        unchanged(alreadyWellPlaced);
    }

    @Test void anUnknownOrAlmostExhaustedMineDoesNotInventTheNextEconomicStage() {
        GameState unknown = position(); unknown.sMaxMine[A] = -1;
        unchanged(unknown, "MINE");
        GameState exhausted = position(); exhausted.sGold[A] = 6;
        unchanged(exhausted, "MINE");
    }

    @Test void nearUnitsPotentialSpawnsAndEnemyFireKeepTheOrdinaryApproach() {
        GameState creep = position();
        creep.addCreep(0, Const.KNIGHT, 700, 350);
        unchanged(creep);
        GameState queen = position(); queen.qx[1] = 690; queen.qy[1] = 350;
        unchanged(queen);
        GameState contested = position(); contested.qx[1] = 140; contested.qy[1] = 350;
        unchanged(contested); // Far from our queen, but can reach A before its construction resolves.
        GameState spawn = position();
        site(spawn, 2, 750, 350);
        spawn.stOwner[2] = 1; spawn.stType[2] = Const.BARRACKS;
        spawn.barrType[2] = Const.KNIGHT; spawn.barrTraining[2] = true; spawn.barrProgress[2] = 4;
        unchanged(spawn);
        GameState tower = position();
        site(tower, 2, 650, 350);
        tower.stOwner[2] = 1; tower.stType[2] = Const.TOWER;
        tower.towerHp[2] = 400; tower.towerRange[2] = 300;
        unchanged(tower);
    }
}
