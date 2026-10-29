package cr.referee;

import cr.bot.BotFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefereeTest {
    /**
     * As on CG, the extra line is read as the next answer. The Result reports the extra lines for the
     * bot's seat once the program has ended: the BYE it prints at end of input counts too.
     */
    @Test
    void anExtraLineDesyncsAProcessBotAndIsReportedForItsSeat() throws Exception {
        for (int seat = 0; seat < 2; seat++) {
            ProcessBot echo = protocolEcho("2", "bye");
            Referee.Result r = seat == 0
                ? Referee.play(1, echo, BotFactory.create("v00"), true, 5000)
                : Referee.play(1, BotFactory.create("v00"), echo, true, 5000);
            assertEquals("Expected TRAIN on the second line", r.error[seat], "seat " + seat);
            assertEquals(4, r.turns, "killed on turn 3, the one after the extra line");
            assertEquals(seat + " EXTRA | WAIT", r.replay.get(2 * 3 + seat));
            assertEquals(2, r.extraLines[seat], "seat " + seat + ": the shifted TRAIN and BYE, unread at close");
            assertEquals(0, r.extraLines[1 - seat], "seat " + seat);
        }
    }

    private static ProcessBot protocolEcho(String... args) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Path classes = Paths.get(ProtocolEcho.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(java, "-cp", classes.toString(), ProtocolEcho.class.getName()));
        command.addAll(List.of(args));
        return new ProcessBot("echo", command, false);
    }
}
