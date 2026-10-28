package cr.tools;

import com.google.gson.JsonObject;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Replays CG games through {@link Rules} and compares the map, HUD (gold, queen health), site
 * tooltips (gold, structure, tower range/health) every turn, the end turn, scores and ranks.
 * Read-only: never modifies or drops inputs. Usage: CgValidate &lt;file|dir&gt; [verbose 0|1].
 */
public final class CgValidate {
    public static final class Result {
        public final List<String> mismatches = new ArrayList<>();
        public int turns, firstBadTurn;
        /** Unit comparisons: turns without / with a creep spawn in the replay frame, and exact matches. */
        public int plainTurns, plainExact, spawnTurns, spawnExact;
        public final Map<String, Integer> resynced = new TreeMap<>();
        public String firstResync;
        public String error;
        boolean ok() { return error == null && mismatches.isEmpty(); }
    }

    /** Copy replay unit positions/HP and HUD values into the simulation instead of stopping. */
    static boolean RESYNC = Boolean.getBoolean("resync");

    static String diffKind(List<String> got, List<String> exp) {
        if (got.size() != exp.size()) return "count";
        boolean pos = false, hp = false;
        for (int i = 0; i < got.size(); i++) {
            String[] a = got.get(i).split(" "), b = exp.get(i).split(" ");
            if (!a[0].equals(b[0]) || !a[1].equals(b[1])) return "set";
            if (!a[2].equals(b[2]) || !a[3].equals(b[3])) pos = true;
            if (!a[4].equals(b[4])) hp = true;
        }
        return (pos ? "pos" : "") + (hp ? "hp" : "");
    }

    /** "NAME=v,ARRAY[i]=v" overrides of non-final Const fields, for rule inference only. */
    static void applyConstOverrides(String spec) {
        for (String kv : spec.split(",")) {
            if (kv.isBlank()) continue;
            String[] p = kv.split("=");
            try {
                String name = p[0].trim();
                int v = Integer.parseInt(p[1].trim());
                if (name.endsWith("]")) {
                    int b = name.indexOf('[');
                    ((int[]) Const.class.getField(name.substring(0, b)).get(null))[Integer.parseInt(name.substring(b + 1, name.length() - 1))] = v;
                } else Const.class.getField(name).setInt(null, v);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("bad override " + kv, e);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : "replays/cg");
        boolean verbose = args.length > 1 && args[1].equals("1");
        cr.core.Physics.LEGACY_ENTITY_ORDER = Boolean.parseBoolean(System.getProperty("legacyOrder", "false"));
        Rules.LEGACY_SPAWN = Boolean.parseBoolean(System.getProperty("legacySpawn", "false"));
        Rules.TURN_BASE = Integer.parseInt(System.getProperty("turnBase", "1"));
        Rules.SPAWN_OFFSET = Integer.getInteger("spawnOffset", 0);
        Rules.TOWER_FROM_CENTER = Boolean.parseBoolean(System.getProperty("towerFromCenter", "true"));
        Rules.TOWER_QUEEN_FROM_CENTER = Boolean.parseBoolean(System.getProperty("towerQueenFromCenter", "true"));
        Rules.SPAWN_FIX_EACH = Boolean.getBoolean("spawnFixEach");
        Rules.SPAWN_TOWARDS = !Boolean.getBoolean("spawnNoTowards");
        if (System.getProperty("knightHp") != null) Const.CREEP_HP[Const.KNIGHT] = Integer.getInteger("knightHp");
        applyConstOverrides(System.getProperty("consts", ""));
        List<Path> files;
        try (Stream<Path> st = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
            files = st.filter(p -> p.getFileName().toString().matches("\\d+\\.json")).sorted().collect(Collectors.toList());
        }
        int ok = 0, bad = 0, err = 0;
        long matchedTurns = 0;
        Map<String, Integer> resyncTotal = new TreeMap<>();
        int plainTurns = 0, plainExact = 0, spawnTurns = 0, spawnExact = 0;
        Map<String, Integer> kinds = new TreeMap<>();
        for (Path f : files) {
            Result r;
            try {
                r = validate(CgReplay.load(f));
            } catch (Exception e) {
                r = new Result();
                r.error = e.toString();
            }
            if (r.error != null) { err++; System.out.println("ERROR " + f + ": " + r.error); continue; }
            r.resynced.forEach((k, v) -> resyncTotal.merge(k, v, Integer::sum));
            plainTurns += r.plainTurns; plainExact += r.plainExact; spawnTurns += r.spawnTurns; spawnExact += r.spawnExact;
            if (r.firstResync != null && verbose) System.out.println("  first resync " + f.getFileName() + ": " + r.firstResync);
            if (r.ok()) { ok++; matchedTurns += r.turns; if (verbose) System.out.println("OK " + f + " turns=" + r.turns); continue; }
            bad++;
            matchedTurns += r.firstBadTurn - 1;
            if (!Boolean.getBoolean("quiet")) System.out.println("MISMATCH " + f + " (" + r.mismatches.size() + "): " + r.mismatches.get(0));
            if (verbose) r.mismatches.stream().skip(1).limit(10).forEach(m -> System.out.println("    " + m));
            String kind = r.mismatches.get(0).replaceAll("^t\\d+ ", "").replaceAll("[-\\d]+", "#");
            kinds.merge(kind, 1, Integer::sum);
        }
        System.out.printf("games=%d ok=%d mismatch=%d error=%d matchedTurns=%d%n", files.size(), ok, bad, err, matchedTurns);
        kinds.forEach((k, v) -> System.out.println("  first mismatch kind x" + v + ": " + k));
        if (RESYNC) System.out.println("resynced turns by kind: " + resyncTotal);
        System.out.printf(java.util.Locale.ROOT, "unit match: plain %d/%d (%.2f%%) spawn %d/%d (%.2f%%)%n",
            plainExact, plainTurns, 100.0 * plainExact / Math.max(1, plainTurns),
            spawnExact, spawnTurns, 100.0 * spawnExact / Math.max(1, spawnTurns));
        if (bad + err > 0) System.exit(1);
    }

    static List<String> expectedSiteLines(GameState s, int i) {
        List<String> l = new ArrayList<>();
        l.add("Radius: " + s.sr[i]);
        if (s.mines) l.add("Remaining gold: " + s.sGold[i]);
        switch (s.stType[i]) {
            case Const.MINE: l.add("MINE (+" + s.mineRate[i] + ")"); break;
            case Const.TOWER: l.add("TOWER"); l.add("Range: " + s.towerRange[i]); l.add("Health: " + s.towerHp[i]); break;
            case Const.BARRACKS: l.add("BARRACKS (" + Const.CREEP_NAME[s.barrType[i]] + ")"); break;
            default:
        }
        return l;
    }

    /** "owner type x y hp" per live unit (queens: type Q), sorted. */
    static List<String> simUnits(GameState s) {
        List<String> r = new ArrayList<>();
        for (int p = 0; p < 2; p++) r.add(p + " Q " + Math.round(s.qx[p]) + " " + Math.round(s.qy[p]) + " " + s.hp[p]);
        for (int i = 0; i < s.nCreeps; i++)
            r.add(s.cOwner[i] + " " + Const.CREEP_NAME[s.cType[i]] + " " + Math.round(s.cx[i]) + " " + Math.round(s.cy[i]) + " " + s.cHp[i]);
        r.sort(null);
        return r;
    }

    static String diff(List<String> got, List<String> exp) {
        List<String> a = new ArrayList<>(got), b = new ArrayList<>(exp);
        for (String x : got) if (b.remove(x)) a.remove(x);
        return "replay-only " + a + " sim-only " + b;
    }

    /** Units of the replay view: tooltip-registered token groups, owner from the token image. */
    static final class Units {
        final Map<Integer, String> type = new HashMap<>();
        final Map<Integer, Integer> owner = new HashMap<>();
        final Map<Integer, String> x = new HashMap<>(), y = new HashMap<>();
        Map<Integer, List<String>> texts;

        void update(CgReplay.View v, Map<Integer, List<String>> allTexts) {
            texts = allTexts;
            for (Map.Entry<Integer, JsonObject> e : v.registered.entrySet()) {
                String t = e.getValue().has("type") ? e.getValue().get("type").getAsString() : "";
                if (t.equals("Site")) continue;
                int id = e.getKey();
                type.put(id, t.equals("Queen") ? "Q" : t);
                String circle = CgReplay.entityProps(v, id - 2);
                owner.put(id, circle.contains("Unite_Base_Bleu") ? 1 : 0);
            }
            for (int id : type.keySet()) {
                String props = CgReplay.entityProps(v, id);
                String nx = CgReplay.xOf(props), ny = CgReplay.yOf(props);
                if (nx != null) x.put(id, nx);
                if (ny != null) y.put(id, ny);
            }
        }

        /** Overwrites sim creep positions/HP with replay creeps matched by owner and creation order. */
        boolean resync(GameState s) {
            for (int p = 0; p < 2; p++) {
                List<Integer> ids = new ArrayList<>();
                for (int id : new java.util.TreeSet<>(type.keySet())) {
                    if (type.get(id).equals("Q") || owner.get(id) != p) continue;
                    List<String> t = texts.get(id - 2);
                    if (t != null && !t.isEmpty() && t.get(0).equals("Health: 0")) continue;
                    ids.add(id);
                }
                List<Integer> sim = new ArrayList<>();
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) sim.add(i);
                if (sim.size() != ids.size()) return false;
                for (int k = 0; k < sim.size(); k++) {
                    int i = sim.get(k), id = ids.get(k);
                    if (!Const.CREEP_NAME[s.cType[i]].equals(type.get(id))) return false;
                    s.cx[i] = Integer.parseInt(x.get(id));
                    s.cy[i] = Integer.parseInt(y.get(id));
                    s.cHp[i] = Integer.parseInt(texts.get(id - 2).get(0).replace("Health: ", ""));
                }
            }
            for (int id : type.keySet()) {
                if (!type.get(id).equals("Q")) continue;
                int p = owner.get(id);
                s.qx[p] = Integer.parseInt(x.get(id));
                s.qy[p] = Integer.parseInt(y.get(id));
            }
            return true;
        }

        List<String> alive() {
            List<String> r = new ArrayList<>();
            for (int id : type.keySet()) {
                List<String> t = texts.get(id - 2);
                String hp = t == null || t.isEmpty() ? "?" : t.get(0).replace("Health: ", "");
                if (!type.get(id).equals("Q") && hp.equals("0")) continue;
                r.add(owner.get(id) + " " + type.get(id) + " " + x.get(id) + " " + y.get(id) + " " + hp);
            }
            r.sort(null);
            return r;
        }
    }

    public static Result validate(CgReplay g) {
        Result res = new Result();
        GameState s = MapGen.generate(g.seed, 4);
        Rules rules = new Rules();
        if (Boolean.getBoolean("logResync")) rules.log = new StringBuilder();

        // Entity ids: sites from tooltip registrations, HUD texts by their fixed screen positions.
        Map<Integer, Integer> siteEntity = new HashMap<>();
        for (Map.Entry<Integer, JsonObject> e : g.init.registered.entrySet())
            if (e.getValue().has("type") && e.getValue().get("type").getAsString().equals("Site"))
                siteEntity.put(e.getValue().get("id").getAsInt(), e.getKey());
        int[] goldId = {-1, -1}, hpId = {-1, -1};
        for (String e : g.init.entities) {
            if (!e.startsWith("U ")) continue;
            int id = Integer.parseInt(e.split(" ")[1]);
            String props = CgReplay.entityProps(g.init, id);
            String x = CgReplay.xOf(props), y = CgReplay.yOf(props);
            if (x == null || y == null) continue;
            if (y.equals("1040") && x.equals("700")) goldId[0] = id;
            if (y.equals("1040") && x.equals("1020")) goldId[1] = id;
            if (y.equals("1053") && x.equals("553")) hpId[0] = id;
            if (y.equals("1053") && x.equals("1771")) hpId[1] = id;
        }
        if (siteEntity.size() != s.nSites) { res.error = "sites: replay " + siteEntity.size() + " vs generated " + s.nSites; return res; }

        Map<Integer, List<String>> texts = new HashMap<>(g.init.texts);
        String[] hud = new String[4];
        for (int p = 0; p < 2; p++) {
            hud[p] = CgReplay.textOf(CgReplay.entityProps(g.init, goldId[p]));
            hud[2 + p] = CgReplay.textOf(CgReplay.entityProps(g.init, hpId[p]));
        }
        List<String> mm = res.mismatches;
        Units units = new Units();
        units.update(g.init, texts);
        for (int i = 0; i < s.nSites; i++) {
            int ent = siteEntity.get(i);
            String props = CgReplay.entityProps(g.init, ent);
            String x = CgReplay.xOf(props), y = CgReplay.yOf(props);
            if (!String.valueOf(Math.round(s.sx[i])).equals(x) || !String.valueOf(Math.round(s.sy[i])).equals(y))
                mm.add("t0 site " + i + " pos " + x + "," + y + " vs " + Math.round(s.sx[i]) + "," + Math.round(s.sy[i]));
            List<String> exp = expectedSiteLines(s, i);
            if (!exp.equals(texts.get(ent))) mm.add("t0 site " + i + " " + texts.get(ent) + " vs " + exp);
        }
        for (int p = 0; p < 2; p++)
            if (!String.valueOf(s.hp[p]).equals(hud[2 + p])) mm.add("t0 queenHp" + p + " " + hud[2 + p] + " vs " + s.hp[p]);
        if (!mm.isEmpty()) return res;

        for (CgReplay.Turn t : g.turns) {
            if (s.over) { mm.add("t" + s.turn + " replay continues after simulated end"); break; }
            String[] l0 = CgReplay.lines(t.stdout[0]), l1 = CgReplay.lines(t.stdout[1]);
            boolean tracing = g.gameId == Long.getLong("traceGame", -1L) && s.turn + 1 == Integer.getInteger("traceTurn", -1);
            if (tracing) {
                if (rules.log == null) rules.log = new StringBuilder();
                rules.trace = true;
                System.out.println("TRACE g" + g.gameId + " turn " + (s.turn + 1) + " cmds: [" + l0[0] + " | " + l0[1] + "] [" + l1[0] + " | " + l1[1] + "]");
                System.out.println("  before:\n" + s);
            }
            rules.resolve(s, Command.parse(l0[0], l0[1], s.nSites), Command.parse(l1[0], l1[1], s.nSites));
            if (tracing) {
                rules.trace = false;
                System.out.println("  log:" + rules.log);
            }
            int tn = s.turn;
            for (Map.Entry<Integer, List<String>> e : t.view.texts.entrySet()) texts.put(e.getKey(), e.getValue());
            for (int p = 0; p < 2; p++) {
                String gv = CgReplay.textOf(CgReplay.entityProps(t.view, goldId[p]));
                if (gv != null) hud[p] = gv;
                String hv = CgReplay.textOf(CgReplay.entityProps(t.view, hpId[p]));
                if (hv != null) hud[2 + p] = hv;
            }
            if (s.over && (rules.lastKill[0] != null || rules.lastKill[1] != null)) break;
            for (int p = 0; p < 2; p++) {
                if (!String.valueOf(s.gold[p]).equals(hud[p])) {
                    if (RESYNC) { res.resynced.merge("gold", 1, Integer::sum); s.gold[p] = Integer.parseInt(hud[p]); }
                    else mm.add("t" + tn + " gold" + p + " " + hud[p] + " vs " + s.gold[p]);
                }
                if (!String.valueOf(s.hp[p]).equals(hud[2 + p])) {
                    if (RESYNC) { res.resynced.merge("queenHp", 1, Integer::sum); s.hp[p] = Integer.parseInt(hud[2 + p]); if (s.score[p] >= 0) s.score[p] = s.hp[p]; }
                    else mm.add("t" + tn + " queenHp" + p + " " + hud[2 + p] + " vs " + s.hp[p]);
                }
            }
            for (int i = 0; i < s.nSites; i++) {
                int ent = siteEntity.get(i);
                List<String> exp = expectedSiteLines(s, i);
                if (!exp.equals(texts.get(ent))) mm.add("t" + tn + " site " + i + " " + texts.get(ent) + " vs " + exp);
            }
            boolean spawned = t.view.registered.values().stream().anyMatch(o -> o.has("type") && !o.get("type").getAsString().equals("Queen"));
            units.update(t.view, texts);
            List<String> got = units.alive(), exp = simUnits(s);
            if (tracing) System.out.println("  replay units after: " + got + "\n  sim units after:    " + exp);
            if (spawned) { res.spawnTurns++; if (got.equals(exp)) res.spawnExact++; }
            else { res.plainTurns++; if (got.equals(exp)) res.plainExact++; }
            if (!got.equals(exp)) {
                if (RESYNC) {
                    String kind = (spawned ? "units@spawn " : "units ") + diffKind(got, exp);
                    res.resynced.merge(kind, 1, Integer::sum);
                    if (res.firstResync == null) res.firstResync = "t" + tn + " " + kind + " " + diff(got, exp);
                    if (Boolean.getBoolean("logResync")) System.out.println("RESYNC g" + g.gameId + " t" + tn + " " + kind + " " + diff(got, exp) + " LOG " + rules.log);
                    if (!units.resync(s)) mm.add("t" + tn + " units (resync failed) " + diff(got, exp));
                } else mm.add("t" + tn + " units " + diff(got, exp));
            }
            if (!mm.isEmpty()) res.firstBadTurn = tn;
            if (!mm.isEmpty() && Boolean.getBoolean("dumpState")) mm.add("state:\n" + s + "replay units " + got);
            if (!mm.isEmpty()) return res;
        }
        res.turns = s.turn;
        if (!s.over) mm.add("t" + s.turn + " simulated game not over after " + g.turns.size() + " replay turns");
        if (s.turn != g.turns.size()) mm.add("end turn " + g.turns.size() + " vs " + s.turn);
        for (int p = 0; p < 2; p++)
            if ((int) Math.round(g.scores[p]) != s.score[p]) mm.add("score" + p + " " + g.scores[p] + " vs " + s.score[p]);
        int w = s.winner();
        int cgWinner = g.ranks[0] == g.ranks[1] ? -1 : (g.ranks[0] < g.ranks[1] ? 0 : 1);
        if (w != cgWinner) mm.add("winner " + cgWinner + " vs " + w);
        return res;
    }
}
