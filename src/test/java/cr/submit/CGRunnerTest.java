package cr.submit;

import cr.bot.Params;
import cr.core.GameState;
import cr.core.InputReader;
import cr.core.MapGen;
import cr.core.Protocol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CGRunnerTest {
    /** The warm-up must reach every output kind (each has its own first-use cost) and work on copies only. */
    @Test
    void warmUpReachesMoveBuildAndTrainOnCopies() throws IOException {
        for (long seed = 1; seed <= 20; seed++) {
            for (int p = 0; p < 2; p++) {
                InputReader in = firstTurn(seed, p);
                String before = in.state.toString();
                int kinds = CGRunner.warmUp(in.state, new Params(), Long.MAX_VALUE);
                String at = "seed " + seed + " player " + p;
                assertEquals(CGRunner.WARM_MOVE | CGRunner.WARM_BUILD | CGRunner.WARM_TRAIN, kinds, at);
                assertEquals(before, in.state.toString(), at);
            }
        }
    }

    @Test
    void warmUpPlaysNothingAfterItsDeadline() throws IOException {
        assertEquals(0, CGRunner.warmUp(firstTurn(1, 0).state, new Params(), System.nanoTime() - 1));
    }

    /** Site ids must exist (the referee kills on a missing one), so a map needs 16 sites for the 16 digits. */
    @Test
    void theMarkerWritesEachHexDigitAsASiteId() {
        assertEquals("TRAIN 5 11 10 12 14 6 8 9", CGRunner.marker("5bace689", 24));
        assertEquals("TRAIN 0 0 0 0 15 15 15 15", CGRunner.marker("0000ffff", 16));
        assertNull(CGRunner.marker("", 24), "the sources");
        assertNull(CGRunner.marker("5bace68", 24));
        assertNull(CGRunner.marker("5BACE689", 24), "FileMerger writes lower case");
        assertNull(CGRunner.marker("5bace68g", 24));
        assertNull(CGRunner.marker("5bace689", 15));
        assertEquals("", CGRunner.BUILD, "FileMerger sets the id in the artifact only");
    }

    /** The bot's view of the first turn of seed's map as player p. */
    private static InputReader firstTurn(long seed, int p) throws IOException {
        GameState truth = MapGen.generate(seed, 4);
        String text = Protocol.initInput(truth) + Protocol.turnInput(truth, p);
        InputReader in = new InputReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)));
        in.readInit();
        in.readTurn();
        return in;
    }
}
