package cr.bot.v00;

import cr.bot.Bot;
import cr.core.Command;
import cr.core.GameState;

/** v00: trivial legal bot (frozen). */
public final class WaitBot implements Bot {
    @Override public String name() { return "v00"; }

    @Override
    public Command play(GameState s, int touchedSite, long deadlineNanos) {
        return Command.waitCmd();
    }
}
