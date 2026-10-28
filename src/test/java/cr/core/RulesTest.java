package cr.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test
    void kotlinSplitKeepsEmptyTokens() {
        assertEquals(List.of("TRAIN", ""), Command.split("TRAIN "));
        assertEquals(List.of("MOVE", "", "1", "2"), Command.split("MOVE  1 2"));
    }

    @Test
    void parseKillsLikeTheReferee() {
        assertTrue(Command.parse("WAIT", "TRAIN ", 24).kill);
        assertTrue(Command.parse("WAIT", "train", 24).kill);
        assertTrue(Command.parse("MOVE 1 2 3", "TRAIN", 24).kill);
        assertTrue(Command.parse("MOVE 1", "TRAIN", 24).kill);
        assertTrue(Command.parse("BUILD 24 MINE", "TRAIN", 24).kill);
        assertTrue(Command.parse("WAIT", "TRAIN 3 99", 24).kill);
        assertTrue(Command.parse(null, null, 24).kill);
        Command c = Command.parse("  BUILD 3 TOWER x ", "TRAIN 1 2", 24);
        assertFalse(c.kill);
        assertTrue(c.extraTokens);
        assertArrayEquals(new int[]{1, 2}, c.train);
    }

    /** Sites of CG game 909614601 (seed 277743146), read from its replay init frame. */
    @Test
    void mapGeneratorMatchesReplay() {
        GameState s = MapGen.generate(277743146L, 4);
        assertEquals(24, s.nSites);
        assertEquals(167, s.sx[12]); assertEquals(175, s.sy[12]); assertEquals(77, s.sr[12]);
        assertEquals(1753, s.sx[13]); assertEquals(825, s.sy[13]); assertEquals(77, s.sr[13]);
        assertEquals(343, s.sx[4]); assertEquals(362, s.sy[4]); assertEquals(80, s.sr[4]);
        assertEquals(70, s.hp[0]);
        assertEquals(70, s.hp[1]);
    }

    private static GameState emptyState() {
        GameState s = MapGen.generate(277743146L, 4);
        return s;
    }

    @Test
    void towerActsOnTheTurnItIsBuilt() {
        GameState s = emptyState();
        // queen 0 next to site 4 (343,362 r80)
        s.qx[0] = 343; s.qy[0] = 362 - 80 - 30 - 1;
        new Rules().resolve(s, Command.build(4, "TOWER"), Command.waitCmd());
        assertEquals(Const.TOWER, s.stType[4]);
        assertEquals(196, s.towerHp[4]);
        assertEquals((int) Math.sqrt((196 * 1000 + Math.PI * 80 * 80) / Math.PI), s.towerRange[4]);
    }

    @Test
    void mineIncomeStartsTheTurnItIsBuilt() {
        GameState s = emptyState();
        s.qx[0] = 167; s.qy[0] = 175 + 77 + 30 + 1;
        int gold = s.gold[0];
        new Rules().resolve(s, Command.build(12, "MINE"), Command.waitCmd());
        assertEquals(gold + 1, s.gold[0]);
        assertEquals(1, s.mineRate[12]);
    }

    @Test
    void buildOutOfRangeMovesTheQueen() {
        GameState s = emptyState();
        double x = s.qx[0], y = s.qy[0];
        new Rules().resolve(s, Command.build(13, "MINE"), Command.waitCmd());
        assertEquals(Const.NONE, s.stType[13]);
        double before = Math.hypot(s.sx[13] - x, s.sy[13] - y), after = Math.hypot(s.sx[13] - s.qx[0], s.sy[13] - s.qy[0]);
        // 60 towards the site, then collisions with sites may push the queen sideways
        assertTrue(before - after > 40 && Math.hypot(s.qx[0] - x, s.qy[0] - y) <= 61, before + " -> " + after);
    }

    @Test
    void invalidStructureKillsOnlyWhenBuildExecutes() {
        GameState s = emptyState();
        new Rules().resolve(s, Command.build(13, "FOO"), Command.waitCmd());
        assertFalse(s.over, "out of range: queen just moves");
        s.qx[0] = 167; s.qy[0] = 175 + 77 + 30 + 1;
        new Rules().resolve(s, Command.build(12, "FOO"), Command.waitCmd());
        assertTrue(s.over);
        assertEquals(1, s.winner());
    }

    @Test
    void inputRoundTrip() throws IOException {
        GameState s = emptyState();
        String text = Protocol.initInput(s) + Protocol.turnInput(s, 1);
        InputReader r = new InputReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)));
        r.readInit();
        r.readTurn();
        // player 1's view: itself is player 0
        assertEquals(Math.round(s.qx[1]), r.state.qx[0]);
        assertEquals(Math.round(s.qy[0]), r.state.qy[1]);
        assertEquals(s.gold[1], r.state.gold[0]);
        assertEquals(s.sr[7], r.state.sr[7]);
    }
}
