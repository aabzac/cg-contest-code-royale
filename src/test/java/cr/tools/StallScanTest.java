package cr.tools;

import cr.bot.BotFactory;
import cr.referee.Referee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StallScanTest {
    /** The stall streaks of bot on seat 1 against v01. */
    private static List<StallScan.Streak> scan(long seed, String bot) {
        return StallScan.streaks(seed, 1, Referee.play(seed, BotFactory.create("v01"), BotFactory.create(bot), true, 50));
    }

    private static int longest(List<StallScan.Streak> streaks) {
        return streaks.stream().mapToInt(StallScan.Streak::length).max().orElse(0);
    }

    /**
     * The audit's worst stalls (StallScan current v01 500 10000; presubmit audit 2026-10-29) and the
     * same games with UNSTICK: seed 10477 head-on (43 turns on BUILD 12 MINE) and seed 10394 wedged
     * between two sites (17 turns on BUILD 3 MINE).
     */
    @Test
    void unstickEndsTheAuditsStalls() {
        List<StallScan.Streak> headOn = scan(10477, "current?INCOME_TARGET=10,MAX_TOWERS=4,UNSTICK=0");
        assertEquals(43, longest(headOn));
        StallScan.Streak worst = headOn.stream().filter(k -> k.length() == 43).findFirst().orElseThrow();
        assertEquals(51, worst.firstTurn());
        assertEquals("BUILD 12 MINE", worst.command());
        assertEquals(17, longest(scan(10394, "current?INCOME_TARGET=10,MAX_TOWERS=4,UNSTICK=0")));
        assertTrue(longest(scan(10477, "current?INCOME_TARGET=10,MAX_TOWERS=4,UNSTICK=1")) <= 2);
        assertTrue(longest(scan(10394, "current?INCOME_TARGET=10,MAX_TOWERS=4,UNSTICK=1")) <= 2);
    }
}
