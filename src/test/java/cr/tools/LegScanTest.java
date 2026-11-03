package cr.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegScanTest {
    private static LegScan.Game game(List<LegScan.Game> games, int seat) {
        return games.stream().filter(g -> g.seat == seat).findFirst().orElseThrow();
    }

    private static LegScan.Leg leg(LegScan.Game g, int start) {
        return g.legs.stream().filter(l -> l.start == start).findFirst().orElseThrow();
    }

    /**
     * Seed 110135 (the study's worst v03 leg, docs/data/study-2026-11-01-queen-routes): on turns 139-143
     * she walks to site 15, retargets to site 13 on turn 144 and slides along her own tower's rim for
     * seven turns before she touches 13 on turn 155: 12 turns against a straight bound of 6.
     */
    @Test
    void theSlideOfSeed110135IsOneRouteLegWithSevenSlideTurns() throws Exception {
        List<LegScan.Game> games = LegScan.scan("v03", "v03", 1, 110135, 2);
        assertEquals(2, games.size(), "a mirror plays each map once and scans both seats");
        LegScan.Game g = game(games, 0);
        assertEquals(250, g.turns);
        assertEquals(g.turns, g.cls[0] + g.cls[1] + g.cls[2] + g.cls[3], "every turn has one class");
        assertEquals(g.cls[LegScan.MOVE] + g.cls[LegScan.WALK], g.walking);
        LegScan.Leg retarget = leg(g, 139);
        assertEquals(15, retarget.target);
        assertEquals(143, retarget.end);
        assertFalse(retarget.arrived);
        assertEquals(LegScan.RETARGET, retarget.reason);
        LegScan.Leg slide = leg(g, 144);
        assertEquals(13, slide.target);
        assertEquals(155, slide.end);
        assertTrue(slide.route && slide.arrived);
        assertEquals(12, slide.turns);
        assertEquals(6, slide.bound);
        assertEquals(6, slide.excess);
        assertEquals(7, slide.slides);
        assertEquals(0, slide.stalls);
        assertEquals(-1, g.maxCalls, "the frozen v03 does not count its predictions");
        assertTrue(g.maxNanos > 0);
    }

    /**
     * Seed 110098: her own knight keeps her outside the ring of site 5 for 14 turns (turns 6-21, bound 3),
     * which the scan reports as slide turns and failed final steps with a unit near.
     */
    @Test
    void theKnightBlockedLegOfSeed110098IsFailedFinalStepsWithAUnitNear() throws Exception {
        List<LegScan.Game> games = LegScan.scan("current?ROUTE=0,MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,KITE=0,KITE=0", "v03", 1, 110098, 2);
        assertEquals(2, games.size(), "two games per map against another bot");
        LegScan.Game g = game(games, 0);
        LegScan.Leg blocked = leg(g, 6);
        assertEquals(5, blocked.target);
        assertEquals(21, blocked.end);
        assertTrue(blocked.route && blocked.arrived);
        assertEquals(16, blocked.turns);
        assertEquals(3, blocked.bound);
        assertEquals(13, blocked.excess);
        assertEquals(14, blocked.slides);
        assertEquals(13, g.finalFails);
        assertEquals(13, g.finalFailsUnit);
        assertTrue(g.maxCalls >= 0 && g.calls >= 0, "current counts its queenStep predictions");
        assertTrue(LegScan.summary(games, "current?ROUTE=0,MINES_FIRST=0,MAX_TOWERS=8,BARRACKS=1,TRAIN_SYNC=0,THREAT_TOWERS_NEAR=0,KITE=0,KITE=0", "v03", 1, 110098).contains("queenStep calls per turn: mean"));
    }
}
