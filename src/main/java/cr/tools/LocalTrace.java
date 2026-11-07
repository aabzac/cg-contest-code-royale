package cr.tools;

import cr.bot.BotFactory;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Rules;
import cr.referee.Referee;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Local games turn by turn for one side, in {@link DeathTrace}'s columns (the meet-back experiment of 2026-11-07,
 * docs/data/exp-2026-11-07-meet-back). Each game plays botA against botB with botA on the given seat (both bots are
 * deterministic), then replays the recorded commands through {@link Rules} and prints, per turn, the state at the turn's
 * start, botA's command, its queen's HP after the turn and the turn's loss with two probes: kd to the enemy knights (the
 * enemy towers' ranges zeroed) and td to the enemy towers (the enemy knights removed).
 * <p>
 * Usage: LocalTrace &lt;botA&gt; &lt;botB&gt; &lt;mode&gt; &lt;game&gt; [&lt;game&gt; ...], where a game is seed:seat or
 * seed:seat:from-to (the turns from..to only) and the mode is "all" (every turn), "last=N" (the game's last N turns) or
 * "-" (from the first turn botA's queen loses HP).
 */
public final class LocalTrace {
    private LocalTrace() {}

    public static void main(String[] args) {
        String a = args[0], b = args[1], mode = args[2];
        for (int i = 3; i < args.length; i++) trace(a, b, mode, args[i]);
    }

    static void trace(String a, String b, String mode, String game) {
        String[] g = game.split(":");
        long seed = Long.parseLong(g[0]);
        int me = Integer.parseInt(g[1]), foe = 1 - me;
        int from = -1, to = Integer.MAX_VALUE;
        if (g.length > 2) {
            String[] ft = g[2].split("-");
            from = Integer.parseInt(ft[0]);
            to = Integer.parseInt(ft[1]);
        }
        int last = mode.startsWith("last=") ? Integer.parseInt(mode.substring(5)) : -1;
        Referee.Result r = me == 0 ? Referee.play(seed, BotFactory.create(a), BotFactory.create(b), true, 50)
                                   : Referee.play(seed, BotFactory.create(b), BotFactory.create(a), true, 50);
        Referee.Frame end = r.frames.get(r.frames.size() - 1);
        System.out.printf(Locale.ROOT, "=== %s (seat %d) vs %s, seed %d: winner %d, %d turns, final HP %d/%d%n",
            a, me, b, seed, r.winner, r.turns, end.hp[me], end.hp[foe]);
        GameState s = MapGen.generate(seed, 4);
        Rules rules = new Rules();
        int prevHp = s.hp[me];
        boolean started = mode.equals("all") || last >= 0 || from >= 0;
        Deque<String> tail = new ArrayDeque<>();
        for (int k = 1; k < r.frames.size() && !s.over; k++) {
            int t = s.turn;
            String[] raw = r.frames.get(k).cmds;
            Command[] cmd = {LocalProfile.parse(raw[0], s.nSites), LocalProfile.parse(raw[1], s.nSites)};
            String line = DeathTrace.describe(s, me, foe, t, cmd[me]);
            GameState knightsOnly = s.copy(), towersOnly = s.copy();
            for (int i = 0; i < s.nSites; i++)
                if (knightsOnly.stType[i] == Const.TOWER && knightsOnly.stOwner[i] == foe) knightsOnly.towerRange[i] = 0;
            for (int i = 0; i < towersOnly.nCreeps; i++)
                if (towersOnly.cOwner[i] == foe && towersOnly.cType[i] == Const.KNIGHT) towersOnly.cHp[i] = 0;
            towersOnly.removeDeadCreeps();
            new Rules().resolve(knightsOnly, cmd[0], cmd[1]);
            new Rules().resolve(towersOnly, cmd[0], cmd[1]);
            rules.resolve(s, cmd[0], cmd[1]);
            int loss = prevHp - s.hp[me];
            if (!started && loss > 0) started = true;
            String out = String.format(Locale.ROOT, "%s hp %3d (-%d) | kd %d td %d", line, s.hp[me], loss,
                prevHp - knightsOnly.hp[me], prevHp - towersOnly.hp[me]);
            prevHp = s.hp[me];
            if (s.hp[me] <= 0) out += System.lineSeparator() + "    queen dead on turn " + (t + 1);
            if (!started || t < from || t > to) continue;
            if (last >= 0) {
                tail.addLast(out);
                if (tail.size() > last) tail.removeFirst();
            } else System.out.println(out);
        }
        tail.forEach(System.out::println);
    }
}
