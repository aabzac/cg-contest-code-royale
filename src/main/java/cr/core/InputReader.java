package cr.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses the referee protocol into a {@link GameState} from the reading player's perspective:
 * player 0 is always "me". Hidden values (enemy gold, unseen site gold and mine size) keep the
 * last observed value or a neutral estimate. Allocation-free after construction.
 */
public final class InputReader {
    private final InputStream in;
    private final byte[] buf = new byte[1 << 16];
    private int len, pos;

    public final GameState state = new GameState();
    public int touchedSite = -1;
    /** Sites whose gold/maxMineSize were observed at least once. */
    public final boolean[] goldKnown = new boolean[GameState.MAX_SITES];

    public InputReader(InputStream in) { this.in = in; }

    private int read() throws IOException {
        if (pos == len) {
            int n = in.read(buf, 0, buf.length);
            pos = 0;
            len = Math.max(n, 0);
            if (n <= 0) return -1;
        }
        return buf[pos++];
    }

    public int nextInt() throws IOException {
        int c = read();
        while (c == ' ' || c == '\n' || c == '\r' || c == '\t') c = read();
        if (c < 0) throw new IOException("end of input");
        boolean neg = c == '-';
        if (neg) c = read();
        int v = 0;
        while (c >= '0' && c <= '9') { v = v * 10 + (c - '0'); c = read(); }
        return neg ? -v : v;
    }

    public void readInit() throws IOException {
        GameState s = state;
        s.setLevel(4);
        s.nSites = nextInt();
        for (int k = 0; k < s.nSites; k++) {
            int id = nextInt();
            s.sx[id] = nextInt();
            s.sy[id] = nextInt();
            s.sr[id] = nextInt();
            s.sArea[id] = Math.PI * s.sr[id] * s.sr[id];
            s.sGold[id] = 225;
            s.sMaxMine[id] = 2;
            s.clearStructure(id);
        }
        s.active[0] = s.active[1] = true;
    }

    public void readTurn() throws IOException {
        GameState s = state;
        s.gold[0] = nextInt();
        touchedSite = nextInt();
        for (int k = 0; k < s.nSites; k++) {
            int id = nextInt();
            int gold = nextInt(), maxMine = nextInt(), type = nextInt(), owner = nextInt(), p1 = nextInt(), p2 = nextInt();
            if (gold >= 0) { s.sGold[id] = gold; goldKnown[id] = true; }
            if (maxMine >= 0) s.sMaxMine[id] = maxMine;
            int prevRate = s.stType[id] == Const.MINE && s.stOwner[id] == owner ? s.mineRate[id] : 1;
            s.clearStructure(id);
            s.stType[id] = type;
            s.stOwner[id] = owner;
            if (type == Const.MINE) s.mineRate[id] = p1 >= 0 ? p1 : prevRate;
            else if (type == Const.TOWER) { s.towerHp[id] = p1; s.towerRange[id] = p2; }
            else if (type == Const.BARRACKS) {
                s.barrType[id] = p2;
                s.barrTraining[id] = p1 > 0;
                s.barrProgress[id] = p1 > 0 ? Const.CREEP_BUILD_TIME[p2] - p1 : 0;
            }
        }
        int n = nextInt();
        s.nCreeps = 0;
        for (int k = 0; k < n; k++) {
            int x = nextInt(), y = nextInt(), owner = nextInt(), type = nextInt(), hp = nextInt();
            if (type < 0) {
                s.qx[owner] = x;
                s.qy[owner] = y;
                s.hp[owner] = hp;
                s.score[owner] = hp;
            } else {
                int i = s.addCreep(owner, type, x, y);
                s.cHp[i] = hp;
            }
        }
    }
}
