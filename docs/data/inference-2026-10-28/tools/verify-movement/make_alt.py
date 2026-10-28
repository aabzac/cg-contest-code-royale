"""Patch verify-movement-alt Physics.java: entity order from -DaltOrder=C0,Q0,C1,Q1 (sites always last)."""
import re
import sys

path = sys.argv[1]
src = open(path, 'rb').read().decode('utf-8')
start = src.index('    /** Loads creeps, queens and sites of the state in referee order. */')
end = src.index('    public void fixCollisions(GameState s, int maxIterations) {')
new = '''    /** Experimental order: tokens C0, C1 (that player's creeps in creation order), Q0, Q1; sites last. */
    public static String[] ALT = System.getProperty("altOrder", LEGACY_ENTITY_ORDER ? "C0,Q0,C1,Q1" : "C0,C1,Q0,Q1").split(",");
    private final int[] slot = new int[GameState.MAX_CREEPS + 2];
    private int nSlot;

    void load(GameState s) {
        clear();
        nSlot = 0;
        for (String t : ALT) {
            int p = t.charAt(1) - '0';
            if (t.charAt(0) == 'C') {
                for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) { slot[nSlot++] = i; addCreep(s, i); }
            } else {
                slot[nSlot++] = -1 - p;
                add(s.qx[p], s.qy[p], Const.QUEEN_RADIUS, Const.QUEEN_MASS, true);
            }
        }
        for (int i = 0; i < s.nSites; i++) add(s.sx[i], s.sy[i], s.sr[i], 0, false);
    }

    private void addCreep(GameState s, int i) {
        add(s.cx[i], s.cy[i], Const.CREEP_RADIUS[s.cType[i]], Const.CREEP_MASS[s.cType[i]], true);
    }

    void store(GameState s) {
        int k = 0;
        for (; k < nSlot; k++) {
            int e = slot[k];
            if (e >= 0) { s.cx[e] = ex[k]; s.cy[e] = ey[k]; }
            else { s.qx[-1 - e] = ex[k]; s.qy[-1 - e] = ey[k]; }
        }
        for (int i = 0; i < s.nSites; i++, k++) { s.sx[i] = ex[k]; s.sy[i] = ey[k]; }
    }

'''.replace('\n', '\r\n')
src = src[:start] + new + src[end:]
open(path, 'wb').write(src.encode('utf-8'))
print("patched", path)
