package cr.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's turn output (two lines), parsed with the referee's tokenizing. Errors the referee
 * reports as PlayerInputException set {@link #kill}; state-dependent warnings and the build-time
 * structure-type check are left to {@link Rules}.
 */
public final class Command {
    public static final int WAIT = 0, MOVE = 1, BUILD = 2;

    public boolean kill;
    public String error;

    public int queen = WAIT;
    public int x, y, site = -1;
    /** Raw structure argument of BUILD, e.g. "BARRACKS-KNIGHT". */
    public String structure;
    /** BUILD followed by extra tokens: kill unless the build raises a warning first. */
    public boolean extraTokens;

    public int[] train = new int[0];

    public static Command waitCmd() { return new Command(); }

    public static Command move(int x, int y) {
        Command c = new Command();
        c.queen = MOVE; c.x = x; c.y = y;
        return c;
    }

    public static Command build(int site, String structure) {
        Command c = new Command();
        c.queen = BUILD; c.site = site; c.structure = structure;
        return c;
    }

    public Command train(int... sites) {
        train = sites;
        return this;
    }

    public String queenLine() {
        switch (queen) {
            case MOVE: return "MOVE " + x + " " + y;
            case BUILD: return "BUILD " + site + " " + structure;
            default: return "WAIT";
        }
    }

    public String trainLine() {
        StringBuilder b = new StringBuilder("TRAIN");
        for (int s : train) b.append(' ').append(s);
        return b.toString();
    }

    private Command fail(String msg) {
        kill = true;
        error = msg;
        return this;
    }

    /** Kotlin String.split(" "): keeps empty and trailing tokens. */
    static List<String> split(String s) {
        List<String> r = new ArrayList<>();
        int from = 0;
        while (true) {
            int i = s.indexOf(' ', from);
            if (i < 0) { r.add(s.substring(from)); return r; }
            r.add(s.substring(from, i));
            from = i + 1;
        }
    }

    private static Integer toInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    /** Parses lines exactly as Referee.processPlayerActions reads them. Missing lines kill (timeout). */
    public static Command parse(String line0, String line1, int nSites) {
        Command c = new Command();
        if (line0 == null || line1 == null) return c.fail("Timeout");

        List<String> t = split(line1);
        if (!t.get(0).equals("TRAIN")) return c.fail("Expected TRAIN on the second line");
        int[] ids = new int[t.size() - 1];
        for (int i = 1; i < t.size(); i++) {
            Integer v = toInt(t.get(i));
            if (v == null) return c.fail("Couldn't process siteId: " + t.get(i));
            ids[i - 1] = v;
        }
        for (int id : ids) if (id < 0 || id >= nSites) return c.fail("No site with id = " + id);
        c.train = ids;

        List<String> q = split(line0.strip());
        String cmd = q.get(0);
        int next = 1;
        switch (cmd) {
            case "WAIT":
                c.queen = WAIT;
                break;
            case "MOVE": {
                Integer x = next < q.size() ? toInt(q.get(next++)) : null;
                Integer y = x != null && next < q.size() ? toInt(q.get(next++)) : null;
                if (x == null || y == null) return c.fail("In MOVE command, x and y must be integers");
                c.queen = MOVE; c.x = x; c.y = y;
                break;
            }
            case "BUILD": {
                Integer id = next < q.size() ? toInt(q.get(next++)) : null;
                if (id == null) return c.fail("Could not parse siteId");
                if (id < 0 || id >= nSites) return c.fail("Site id " + id + " does not exist");
                if (next >= q.size()) return c.fail("Missing structure type in BUILD command");
                c.queen = BUILD; c.site = id; c.structure = q.get(next++);
                break;
            }
            default:
                return c.fail("Didn't understand command: " + cmd);
        }
        if (next < q.size()) {
            // For BUILD the referee checks this after scheduling, so a build warning hides it.
            if (c.queen == BUILD) c.extraTokens = true;
            else return c.fail("Too many tokens after " + cmd + " command");
        }
        return c;
    }
}
