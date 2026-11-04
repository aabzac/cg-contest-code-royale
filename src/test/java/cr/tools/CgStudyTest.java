package cr.tools;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CgStudyTest {
    /**
     * Game 915331914 of v04 upload 2 (labelled `route` until 2026-11-04; gitignored replay; skipped without it): seat 0 is v04, which
     * opens with the knight barracks and two mines, tops its towers up 97 times and never loses one;
     * seat 1 opens with three mines, loses a tower on turn 90 and its queen on turn 172 (the last turn).
     * Every row has as many fields as the header.
     */
    @Test
    void deathTurnTopUpsAndOpeningOfARecordedGame() throws Exception {
        Path game = Path.of("replays/arena/v04/bronze/upload-02/games/915331914.json");
        assumeTrue(Files.exists(game), "no local replay");
        List<String> header = List.of(CgStudy.header().split(",", -1));
        assertEquals(106, header.size());
        List<String> rows = CgStudy.study(CgReplay.load(game), game);
        assertEquals(2, rows.size());
        for (String row : rows) assertEquals(header.size(), row.split(",", -1).length, row);
        String[] us = rows.get(0).split(",", -1), them = rows.get(1).split(",", -1);
        assertEquals("172", us[header.indexOf("turns")]);
        assertEquals("-1", us[header.indexOf("deathTurn")]);
        assertEquals("172", them[header.indexOf("deathTurn")]);
        assertEquals("-1", us[header.indexOf("firstTowerLost")]);
        assertEquals("90", them[header.indexOf("firstTowerLost")]);
        assertEquals("97", us[header.indexOf("towerTopUps")]);
        assertEquals("82", them[header.indexOf("towerTopUps")]);
        assertEquals("2", us[header.indexOf("mineUpgrades")]);
        assertEquals("12", them[header.indexOf("mineUpgrades")]);
        assertEquals("BK M M", us[header.indexOf("opening")]);
        assertEquals("M M M", them[header.indexOf("opening")]);
        assertEquals("2148", us[header.indexOf("thp_50")]);
        assertEquals("724", them[header.indexOf("thp_50")]);
        assertEquals("", us[header.indexOf("thp_200")], "the game ended before turn 200");
    }
}
