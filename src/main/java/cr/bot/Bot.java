package cr.bot;

import cr.core.Command;
import cr.core.GameState;

/** A bot sees the state from its own perspective: player 0 is itself (see InputReader). */
public interface Bot {
    String name();

    default void init(GameState s) {}

    /** @param deadlineNanos System.nanoTime() by which the command must be returned */
    Command play(GameState s, int touchedSite, long deadlineNanos);

    /** Sites-only step predictions (Physics.queenStep) made so far, or -1 when the bot does not count them. */
    default long queenSteps() { return -1; }
}
