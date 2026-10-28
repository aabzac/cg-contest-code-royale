"""Applies the lean turn-order changes to wf/turnorder (Rules.java, Const.java, CgReplay.java, build.gradle)."""
import sys

ROOT = sys.argv[1]


def edit(path, pairs):
    p = ROOT + '/' + path
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            raise SystemExit('anchor not unique/missing in %s: %r' % (path, old[:80]))
        s = s.replace(old, new)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)


edit('src/main/java/cr/core/Rules.java', [
    ("""    /** Spawn offset (iter, iter) for both players as before csj/code-royale 8a63cd9. */
    public static boolean LEGACY_SPAWN = false;
""", """    /** Spawn offset (iter, iter) for both players as before csj/code-royale 8a63cd9. */
    public static boolean LEGACY_SPAWN = false;
    /**
     * Live: structures act grouped by type - all towers, then barracks (spawns), then mines, each
     * group in site order - as in the Bronze statement's turn sequence; the repository acts in site
     * order. So towers never shoot creeps spawned this turn and aim at positions from before the
     * spawn collisions. Evidence (98 replays, 2026-10-28, tower targets decoded from projectile
     * sprites): in site order 226/226 simulated shots at just-spawned creeps have no counterpart in
     * the replays, and 573 shots whose target is moved later by a spawn collision point at the
     * pre-collision position (5 at the post-collision one); tower deaths precede lower-id mine
     * exhaustions in the anim order of 3/3 frames. With the live collision order and spawn
     * corners: strict 97/98 games fully consistent (site order: 27/98).
     */
    public static boolean STRUCTURES_BY_TYPE = true;
"""),
    ("""    /** With log on: also record unit positions after every phase. */
    public boolean trace;
""", """    /** With log on: also record unit positions after every phase. */
    public boolean trace;

    /** Record the events below during {@link #resolve} (replay validation only; off by default). */
    public boolean recordEvents;
    /** Tower shots of the last resolve: site, target position at the shot, queen target, damage, target HP before. */
    public int nShots;
    public final int[] shotSite = new int[GameState.MAX_SITES], shotDmg = new int[GameState.MAX_SITES], shotHp = new int[GameState.MAX_SITES];
    public final double[] shotX = new double[GameState.MAX_SITES], shotY = new double[GameState.MAX_SITES];
    public final boolean[] shotQueen = new boolean[GameState.MAX_SITES];
    /** Knight hits on each queen in the last resolve. */
    public final int[] knightHits = new int[2];
    /** Positions of the creeps removed as dead at the end of the last resolve. */
    public int nDead;
    public final double[] deadX = new double[GameState.MAX_CREEPS], deadY = new double[GameState.MAX_CREEPS];
"""),
    ("""        lastWarning[0] = lastWarning[1] = lastKill[0] = lastKill[1] = null;
        if (log != null) log.setLength(0);
""", """        lastWarning[0] = lastWarning[1] = lastKill[0] = lastKill[1] = null;
        if (log != null) log.setLength(0);
        nShots = nDead = 0;
        knightHits[0] = knightHits[1] = 0;
"""),
    ("""        for (int i = 0; i < s.nSites; i++) actStructure(s, i);
        tracePositions(s, "after structures");
""", """        if (STRUCTURES_BY_TYPE) {
            for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.TOWER) actStructure(s, i);
            for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.BARRACKS) actStructure(s, i);
            for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.MINE) actStructure(s, i);
        } else {
            for (int i = 0; i < s.nSites; i++) actStructure(s, i);
        }
        tracePositions(s, "after structures");
"""),
    ("""        s.removeDeadCreeps();

        int alive = 0;""", """        // Creeps killed this turn (towers or aging) stay targetable and collide until here.
        if (recordEvents)
            for (int i = 0; i < s.nCreeps; i++) if (s.cHp[i] == 0) { deadX[nDead] = s.cx[i]; deadY[nDead] = s.cy[i]; nDead++; }
        s.removeDeadCreeps();

        int alive = 0;"""),
    ("""        if (sum > s.gold[p]) return "Training too many creeps (" + sum + " total gold requested)";
""", """        if (sum > s.gold[p]) return "Training too many creeps (" + sum + " total gold requested)";
        if (Const.CREEP_LIMIT > 0) {
            int units = 0;
            for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) units++;
            for (int site : sites) units += Const.CREEP_COUNT[s.barrType[site]];
            if (units > Const.CREEP_LIMIT) return "Training too many creeps (" + units + " total units requested; " + Const.CREEP_LIMIT + " maximum)";
        }
"""),
    ("""                damageQueen(s, enemy, Const.KNIGHT_DAMAGE);
        } else if""", """            {
                damageQueen(s, enemy, Const.KNIGHT_DAMAGE);
                if (recordEvents) knightHits[enemy]++;
            }
        } else if"""),
    ("""                    damageCreep(s, t, dmg);
                } else if""", """                    if (recordEvents) recordShot(i, s.cx[t], s.cy[t], false, dmg, s.cHp[t]);
                    damageCreep(s, t, dmg);
                } else if"""),
    ("""                    damageQueen(s, enemy, dmg);
                }
                s.towerHp[i] -= Const.TOWER_MELT_RATE;""", """                    if (recordEvents) recordShot(i, s.qx[enemy], s.qy[enemy], true, dmg, s.hp[enemy]);
                    damageQueen(s, enemy, dmg);
                }
                s.towerHp[i] -= Const.TOWER_MELT_RATE;"""),
    ("""    private void actStructure(GameState s, int i) {""", """    private void recordShot(int site, double x, double y, boolean queen, int dmg, int hpBefore) {
        int k = nShots++;
        shotSite[k] = site; shotX[k] = x; shotY[k] = y; shotQueen[k] = queen; shotDmg[k] = dmg; shotHp[k] = hpBefore;
    }

    private void actStructure(GameState s, int i) {"""),
])

edit('src/main/java/cr/core/Const.java', [
    ("""    public static int[] CREEP_BUILD_TIME = {5, 8, 10};
""", """    public static int[] CREEP_BUILD_TIME = {5, 8, 10};
    /**
     * Live only (not in the repository): TRAIN is refused with "Training too many creeps (N total
     * units requested; 30 maximum)" when the player's creeps plus the requested ones exceed this.
     * 15 such warnings in 98 replays (2026-10-28), N = creeps at turn start + requested in all 9
     * reached by resync; creeps still in training do not count (7 accepted requests over 30 with
     * them). Resync gold corrections 17 -> 10, the rest being mines destroyed only in one side.
     * The order against the gold check is unobserved (both only reject). 0 disables.
     */
    /* live-tunable */ public static int CREEP_LIMIT = 30;
"""),
])

edit('src/main/java/cr/tools/CgReplay.java', [
    ("""        public final Map<Integer, List<String>> texts = new HashMap<>();
""", """        public final Map<Integer, List<String>> texts = new HashMap<>();
        /** Positions (exact doubles) of the anim events "death" of this frame, in event order. */
        public final List<double[]> deaths = new ArrayList<>();
"""),
    ("""                v.texts.put(Integer.parseInt(e.getKey()), lines);
            }
        }
        return v;""", """                v.texts.put(Integer.parseInt(e.getKey()), lines);
            }
        }
        if (frame.has("anims") && frame.get("anims").isJsonArray()) for (JsonElement e : frame.getAsJsonArray("anims")) {
            JsonObject a = e.getAsJsonObject();
            if (!a.has("id") || !a.get("id").getAsString().equals("death") || !a.has("params")) continue;
            JsonObject pr = a.getAsJsonObject("params");
            v.deaths.add(new double[]{pr.get("x").getAsDouble(), pr.get("y").getAsDouble()});
        }
        return v;"""),
])

edit('build.gradle', [
    ("""'traceGame', 'traceTurn']""", """'traceGame', 'traceTurn', 'resyncCount', 'logShots', 'logTrain', 'structByType']"""),
])
print('ok')
