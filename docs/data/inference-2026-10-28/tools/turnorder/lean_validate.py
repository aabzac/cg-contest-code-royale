"""Applies the lean validator changes to wf/turnorder/src/main/java/cr/tools/CgValidate.java."""
import sys

ROOT = sys.argv[1]
path = ROOT + '/src/main/java/cr/tools/CgValidate.java'
s = open(path, encoding='utf-8').read()


def rep(old, new):
    global s
    if s.count(old) != 1:
        raise SystemExit('anchor not unique/missing: %r' % old[:90])
    s = s.replace(old, new)


rep("""import cr.core.MapGen;
""", """import cr.core.MapGen;
import cr.core.Physics;
""")

rep("""        public final Map<String, Integer> resynced = new TreeMap<>();
""", """        public final Map<String, Integer> resynced = new TreeMap<>();
        /** Turn classes and replay-event comparisons (tower shots, knight hits, deaths), see {@link Events}. */
        public final Map<String, Integer> events = new TreeMap<>();
""")

rep("""    static boolean RESYNC = Boolean.getBoolean("resync");
""", """    static boolean RESYNC = Boolean.getBoolean("resync");
    /** With RESYNC: when a player's creep count differs, rebuild its creeps from the replay instead of stopping. */
    static boolean RESYNC_COUNT = Boolean.getBoolean("resyncCount");
""")

rep("""        Rules.SPAWN_TOWARDS = !Boolean.getBoolean("spawnNoTowards");
""", """        Rules.SPAWN_TOWARDS = !Boolean.getBoolean("spawnNoTowards");
        if (System.getProperty("structByType") != null) Rules.STRUCTURES_BY_TYPE = Boolean.getBoolean("structByType");
""")

rep("""        Map<String, Integer> resyncTotal = new TreeMap<>();
""", """        Map<String, Integer> resyncTotal = new TreeMap<>();
        Map<String, Integer> eventTotal = new TreeMap<>();
""")

rep("""            r.resynced.forEach((k, v) -> resyncTotal.merge(k, v, Integer::sum));
""", """            r.resynced.forEach((k, v) -> resyncTotal.merge(k, v, Integer::sum));
            r.events.forEach((k, v) -> eventTotal.merge(k, v, Integer::sum));
""")

rep("""        if (RESYNC) System.out.println("resynced turns by kind: " + resyncTotal);
""", """        if (RESYNC) System.out.println("resynced turns by kind: " + resyncTotal);
        System.out.println("events: " + eventTotal);
""")

rep("""                List<Integer> sim = new ArrayList<>();
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) sim.add(i);
                if (sim.size() != ids.size()) return false;
""", """                List<Integer> sim = new ArrayList<>();
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) sim.add(i);
                if (sim.size() != ids.size() && RESYNC_COUNT) {
                    // Rebuild this player's creeps from the replay (creation order = entity id order).
                    for (int i : sim) s.cHp[i] = 0;
                    s.removeDeadCreeps();
                    for (int id : ids) {
                        int ct = -1;
                        for (int c = 0; c < 3; c++) if (Const.CREEP_NAME[c].equals(type.get(id))) ct = c;
                        int k = s.addCreep(p, ct, Integer.parseInt(x.get(id)), Integer.parseInt(y.get(id)));
                        s.cHp[k] = Integer.parseInt(texts.get(id - 2).get(0).replace("Health: ", ""));
                    }
                    sim.clear();
                    for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) sim.add(i);
                }
                if (sim.size() != ids.size()) return false;
""")

EVENTS = r'''
    /**
     * Replay events decoded from entity updates (docs/replay_format.md). Entity ids follow creation
     * order; a tower creates (range circle, sprite animation, projectile). A shot shows the
     * projectile at t=0 with v=1, location int((source + target) / 2) and scaleX = |target -
     * source| / 200, source = site centre - (0, 0.6 * radius), so the target distance is known to
     * 1e-4. A tower that dies in its own act never draws its last shot (Obstacle.act updates the
     * entities after the structure is gone). A knight hit sets its character sprite anchorX 0.2 at
     * t=0.7. Death anims carry the exact double position of removed creeps.
     */
    static final class Events {
        int nextId;
        final Map<Integer, Integer> projSite = new HashMap<>();
        final Map<Integer, double[]> projLast = new HashMap<>();
        /** Shots of the current frame: site -> {midX, midY, length}. */
        final Map<Integer, double[]> shots = new TreeMap<>();
        /** Knight hits per attacked player; tower shots at each queen (replay, simulation); replay queen positions. */
        final int[] hits = new int[2], qShots = new int[2], simQShots = new int[2];
        final Integer[] queenX = new Integer[2], queenY = new Integer[2];

        static String update(CgReplay.View v, int id, String time) {
            String prefix = "U " + id + " " + time + " ";
            for (String e : v.entities) if (e.startsWith(prefix)) return " " + e.substring(prefix.length());
            return null;
        }

        static Double num(String props, String key) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(" " + key + " (-?[0-9.E]+)").matcher(props);
            Double r = null;
            while (m.find()) r = Double.parseDouble(m.group(1));
            return r;
        }

        void frame(CgReplay.View v, GameState s, Units units) {
            List<Integer> circles = new ArrayList<>();
            for (String e : v.entities) if (e.startsWith("C ")) { nextId++; if (e.equals("C C")) circles.add(nextId); }
            for (int c : circles) {
                String props = CgReplay.entityProps(v, c);
                String x = CgReplay.xOf(props), y = CgReplay.yOf(props);
                if (x == null || y == null) continue;
                for (int i = 0; i < s.nSites; i++)
                    if (String.valueOf(Math.round(s.sx[i])).equals(x) && String.valueOf(Math.round(s.sy[i])).equals(y)) projSite.put(c + 2, i);
            }
            shots.clear();
            for (Map.Entry<Integer, Integer> e : projSite.entrySet()) {
                int id = e.getKey();
                String u = update(v, id, "0");
                double[] last = projLast.computeIfAbsent(id, k -> new double[]{Double.NaN, Double.NaN, Double.NaN});
                if (u == null) continue;
                Double x = num(u, "x"), y = num(u, "y"), sx = num(u, "sx");
                if (x != null) last[0] = x;
                if (y != null) last[1] = y;
                if (sx != null) last[2] = sx;
                if (u.contains(" v 1")) shots.put(e.getValue(), new double[]{last[0], last[1], last[2] * 200});
            }
            for (Map.Entry<Integer, String> e : units.type.entrySet()) {
                if (!e.getValue().equals("Q")) continue;
                int p = units.owner.get(e.getKey());
                queenX[p] = Integer.parseInt(units.x.get(e.getKey()));
                queenY[p] = Integer.parseInt(units.y.get(e.getKey()));
            }
            hits[0] = hits[1] = 0;
            for (Map.Entry<Integer, String> e : units.type.entrySet()) {
                if (!e.getValue().equals("KNIGHT")) continue;
                String u = update(v, e.getKey() - 1, "0.7");
                if (u != null && u.contains(" ax 0.2")) hits[1 - units.owner.get(e.getKey())]++;
            }
        }
    }

    /** Compares the simulated tower shots and knight hits of the last resolve with the replay frame. */
    static void compareEvents(Result res, Events ev, Rules rules, GameState s, long gameId, int tn, boolean posOk, StringBuilder out) {
        String sfx = posOk ? "@posOk" : "";
        Map<Integer, Integer> simBySite = new HashMap<>();
        for (int k = 0; k < rules.nShots; k++) simBySite.put(rules.shotSite[k], k);
        java.util.Set<Integer> sites = new java.util.TreeSet<>(simBySite.keySet());
        sites.addAll(ev.shots.keySet());
        for (int site : sites) {
            Integer k = simBySite.get(site);
            double[] r = ev.shots.get(site);
            double srcX = s.sx[site], srcY = s.sy[site] - s.sr[site] * 0.6;
            String desc;
            if (k == null) desc = "replayOnly";
            else if (r == null) desc = s.stType[site] != Const.TOWER ? "simOnly towerDied" : "simOnly";
            else {
                double tx = rules.shotX[k], ty = rules.shotY[k];
                int mx = (int) ((srcX + tx) / 2.0), my = (int) ((srcY + ty) / 2.0);
                double len = Math.sqrt(Physics.distSq(tx, ty, srcX, srcY));
                boolean sameMid = Math.abs(mx - r[0]) <= 1 && Math.abs(my - r[1]) <= 1;
                desc = sameMid && Math.abs(len - r[2]) < 2e-3 ? "exact" : (sameMid && Math.abs(len - r[2]) < 3 ? "near" : "otherTarget");
            }
            res.events.merge("shot " + desc + sfx, 1, Integer::sum);
            if (out != null && !desc.equals("exact")) {
                out.append(String.format(java.util.Locale.ROOT, "SHOT g%d t%d site%d %s%s", gameId, tn, site, desc, sfx));
                if (k != null) out.append(String.format(java.util.Locale.ROOT, " sim->%s(%.3f,%.3f) hp%d dmg%d len%.4f", rules.shotQueen[k] ? "Q" : "c",
                    rules.shotX[k], rules.shotY[k], rules.shotHp[k], rules.shotDmg[k], Math.sqrt(Physics.distSq(rules.shotX[k], rules.shotY[k], srcX, srcY))));
                if (r != null) out.append(String.format(java.util.Locale.ROOT, " rep->(%.1f..%.1f,%.1f..%.1f) len%.4f", 2 * r[0] - srcX, 2 * r[0] + 2 - srcX,
                    2 * r[1] - srcY, 2 * r[1] + 2 - srcY, r[2]));
                out.append('\n');
            }
        }
        // Tower shots at each queen: replay targets within 3 units of the replay queen position.
        ev.qShots[0] = ev.qShots[1] = ev.simQShots[0] = ev.simQShots[1] = 0;
        for (int k = 0; k < rules.nShots; k++)
            if (rules.shotQueen[k]) ev.simQShots[s.stOwner[rules.shotSite[k]] == 0 ? 1 : 0]++;
        for (Map.Entry<Integer, double[]> e : ev.shots.entrySet()) {
            int site = e.getKey();
            double[] r = e.getValue();
            double tx = 2 * r[0] + 1 - s.sx[site], ty = 2 * r[1] + 1 - (s.sy[site] - s.sr[site] * 0.6);
            for (int p = 0; p < 2; p++)
                if (ev.queenX[p] != null && Math.abs(tx - ev.queenX[p]) <= 3 && Math.abs(ty - ev.queenY[p]) <= 3) ev.qShots[p]++;
        }
        for (int p = 0; p < 2; p++) {
            boolean same = rules.knightHits[p] == ev.hits[p];
            res.events.merge("knightHits " + (same ? "same" : "diff") + sfx, 1, Integer::sum);
            if (!same && out != null) out.append(String.format("HITS g%d t%d queen%d sim%d rep%d%s%n", gameId, tn, p, rules.knightHits[p], ev.hits[p], sfx));
        }
    }
'''

rep("""    public static Result validate(CgReplay g) {""", EVENTS + """
    public static Result validate(CgReplay g) {""")

rep("""        Rules rules = new Rules();
        if (Boolean.getBoolean("logResync")) rules.log = new StringBuilder();
""", """        Rules rules = new Rules();
        rules.recordEvents = true;
        if (Boolean.getBoolean("logResync")) rules.log = new StringBuilder();
""")

rep("""        Units units = new Units();
        units.update(g.init, texts);
""", """        Units units = new Units();
        units.update(g.init, texts);
        Events ev = new Events();
        ev.frame(g.init, s, units);
        boolean logShots = Boolean.getBoolean("logShots");
""")

rep("""            rules.resolve(s, Command.parse(l0[0], l0[1], s.nSites), Command.parse(l1[0], l1[1], s.nSites));
""", """            if (Boolean.getBoolean("logTrain")) logTrain(g, t, s, l0, l1);
            rules.resolve(s, Command.parse(l0[0], l0[1], s.nSites), Command.parse(l1[0], l1[1], s.nSites));
""")

CLASSIFY = r'''            if (s.over && (rules.lastKill[0] != null || rules.lastKill[1] != null)) break;
            units.update(t.view, texts);
            ev.frame(t.view, s, units);
            classify(res, ev, rules, s, g.gameId, tn, t.view, units, hud, logShots);
'''
rep("""            if (s.over && (rules.lastKill[0] != null || rules.lastKill[1] != null)) break;
""", CLASSIFY)

rep("""            boolean spawned = t.view.registered.values().stream().anyMatch(o -> o.has("type") && !o.get("type").getAsString().equals("Queen"));
            units.update(t.view, texts);
            List<String> got""", """            boolean spawned = t.view.registered.values().stream().anyMatch(o -> o.has("type") && !o.get("type").getAsString().equals("Queen"));
            List<String> got""")

HELPERS = r'''
    /**
     * Classifies a turn before any correction: "posOk" when every unit present during the turn
     * (alive units plus creeps removed as dead, the latter from the exact death-anim positions) is
     * at the same rounded position, "countOnly" when one side only has extra units, else
     * "posErr". In posOk turns HP differences come from HP/damage rules, not from movement.
     */
    static void classify(Result res, Events ev, Rules rules, GameState s, long gameId, int tn, CgReplay.View view, Units units, String[] hud, boolean log) {
        List<String> gotU = units.alive(), expU = simUnits(s);
        List<String> gotD = new ArrayList<>(), expD = new ArrayList<>();
        for (double[] d : view.deaths) gotD.add(Math.round(d[0]) + " " + Math.round(d[1]));
        for (int k = 0; k < rules.nDead; k++) expD.add(Math.round(rules.deadX[k]) + " " + Math.round(rules.deadY[k]));
        Map<String, Integer> pa = new HashMap<>(), pb = new HashMap<>();
        for (String u : gotU) { String[] f = u.split(" "); pa.merge(f[2] + " " + f[3], 1, Integer::sum); }
        for (String u : expU) { String[] f = u.split(" "); pb.merge(f[2] + " " + f[3], 1, Integer::sum); }
        for (String u : gotD) pa.merge(u, 1, Integer::sum);
        for (String u : expD) pb.merge(u, 1, Integer::sum);
        boolean aOnly = false, bOnly = false;
        for (Map.Entry<String, Integer> e : pa.entrySet()) if (e.getValue() > pb.getOrDefault(e.getKey(), 0)) aOnly = true;
        for (Map.Entry<String, Integer> e : pb.entrySet()) if (e.getValue() > pa.getOrDefault(e.getKey(), 0)) bOnly = true;
        String cls = aOnly && bOnly ? "posErr" : (aOnly || bOnly ? "countOnly" : "posOk");
        res.events.merge("turn " + cls, 1, Integer::sum);
        StringBuilder sb = log ? new StringBuilder() : null;
        compareEvents(res, ev, rules, s, gameId, tn, cls.equals("posOk"), sb);
        if (cls.equals("posOk")) {
            List<String> ca = new ArrayList<>(), cb = new ArrayList<>();
            for (String u : gotU) if (!u.contains(" Q ")) ca.add(u);
            for (String u : expU) if (!u.contains(" Q ")) cb.add(u);
            if (!ca.equals(cb)) {
                res.events.merge("turn posOk creepHpBad", 1, Integer::sum);
                if (log) sb.append("HPBAD g" + gameId + " t" + tn + " " + diff(ca, cb) + "\n");
            }
            for (double[] d : view.deaths) {
                boolean exact = false;
                for (int k = 0; k < rules.nDead && !exact; k++)
                    exact = Math.abs(d[0] - rules.deadX[k]) < 1e-6 && Math.abs(d[1] - rules.deadY[k]) < 1e-6;
                res.events.merge(exact ? "death exact@posOk" : "death inexact@posOk", 1, Integer::sum);
                if (!exact && log) sb.append(String.format(java.util.Locale.ROOT, "DEATH g%d t%d rep(%.6f,%.6f)%n", gameId, tn, d[0], d[1]));
            }
        } else if (cls.equals("countOnly") && log) sb.append("COUNT g" + gameId + " t" + tn + " " + diff(gotU, expU) + " dead rep" + gotD + " sim" + expD + "\n");
        for (int p = 0; p < 2; p++) {
            if (!String.valueOf(s.hp[p]).equals(hud[2 + p])) {
                String why = rules.knightHits[p] != ev.hits[p] ? (ev.simQShots[p] != ev.qShots[p] ? "hits+shots" : "hits")
                    : (ev.simQShots[p] != ev.qShots[p] ? "shots" : "damage");
                res.events.merge("queenHp bad@" + cls, 1, Integer::sum);
                res.events.merge("queenHp why " + why, 1, Integer::sum);
                if (log) sb.append("QHP g" + gameId + " t" + tn + " p" + p + " rep" + hud[2 + p] + " sim" + s.hp[p] + " " + cls
                    + " hits sim" + rules.knightHits[p] + " rep" + ev.hits[p] + " qshots sim" + ev.simQShots[p] + " rep" + ev.qShots[p] + " " + why + "\n");
            }
            if (!String.valueOf(s.gold[p]).equals(hud[p])) {
                res.events.merge("gold bad@" + cls, 1, Integer::sum);
                if (log) sb.append("GOLD g" + gameId + " t" + tn + " p" + p + " rep" + hud[p] + " sim" + s.gold[p] + " " + cls + "\n");
            }
        }
        if (log && sb.length() > 0) System.out.print(sb);
    }

    /** Prints TRAIN requests near the creep limit with the replay's game summary (cap inference). */
    static void logTrain(CgReplay g, CgReplay.Turn t, GameState s, String[] l0, String[] l1) {
        for (int p = 0; p < 2; p++) {
            Command c = Command.parse(p == 0 ? l0[0] : l1[0], p == 0 ? l0[1] : l1[1], s.nSites);
            if (c.kill || c.train.length == 0) continue;
            int alive = 0, inTraining = 0, req = 0;
            for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) alive++;
            for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.BARRACKS && s.stOwner[i] == p && s.barrTraining[i]) inTraining += Const.CREEP_COUNT[s.barrType[i]];
            for (int site : c.train) if (s.stType[site] == Const.BARRACKS) req += Const.CREEP_COUNT[s.barrType[site]];
            String sum = t.summary == null ? "" : t.summary.replace("\n", " / ");
            if (alive + req + inTraining > 30 || sum.contains("$" + p + ": [WARNING] Training too many"))
                System.out.println("TRAIN g" + g.gameId + " t" + (s.turn + 1) + " p" + p + " alive" + alive + " inTraining" + inTraining + " req" + req + " gold" + s.gold[p] + " :: " + sum);
        }
    }
}
'''
# replace the final closing brace of the class with helpers + brace
idx = s.rstrip().rfind('}')
s = s[:idx] + HELPERS.lstrip('\n')
open(path, 'w', encoding='utf-8', newline='\n').write(s)
print('ok')
