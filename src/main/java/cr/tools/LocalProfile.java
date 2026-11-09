package cr.tools;

import cr.bot.BotFactory;
import cr.core.Command;
import cr.core.Const;
import cr.core.GameState;
import cr.core.MapGen;
import cr.core.Physics;
import cr.core.Protocol;
import cr.core.Rules;
import cr.referee.Referee;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Profile of a bot's local games, in the terms of the arena studies (cgStudy): for seat A on maps
 * seedBase.. (both seats), the medians at turns 50, 100, 150 and 200 (since 2026-11-06; 50-150 before)
 * of income, mines, towers and their summed HP, the knight and giant waves trained, the queen's HP, the
 * death turn and the outcome. Since 2026-11-06 also the economy over the game: own mines built,
 * upgrades, mines torn down (gold left on the site) or depleted, and the gold the mines took.
 * For calibrating the field-balance opponent (docs/data/exp-2026-11-03-field-opponent). Since
 * 2026-11-05 also the first contact (the first turn with an enemy knight within 250 of the queen,
 * her own towers then and the HP she loses in the next 20 turns), the HP lost on turns 25-50 and the
 * games at the 250-turn limit, for the Silver study (docs/data/study-2026-11-05-silver). The maps are
 * seedBase + k * stride (stride 1 by default): the queens' starting HP is the generator's first draw,
 * which consecutive seeds barely change, so a stride such as 397 spreads it over all 16 values as on CG.
 * Usage: LocalProfile &lt;botA&gt; &lt;botB&gt; &lt;maps&gt; &lt;seedBase&gt; [stride]
 */
public final class LocalProfile {
    static final int[] AT = {50, 100, 150, 200};
    /** Columns after the AT.length * 5 per-turn values. */
    static final int WAVES = AT.length * 5, GIANTS = WAVES + 1, DEATH = WAVES + 2, TURNS = WAVES + 3, CONTACT = WAVES + 4,
        NEAR_TOWER = WAVES + 5, LOST_PER_CONTACT = WAVES + 6, MOVES = WAVES + 7, FIRST_CONTACT = WAVES + 8,
        TOWERS_AT_CONTACT = WAVES + 9, LOST_AFTER_CONTACT = WAVES + 10, LOST_25_50 = WAVES + 11,
        BUILT = WAVES + 12, UPGRADES = WAVES + 13, TORN_DOWN = WAVES + 14, DEPLETED = WAVES + 15, MINED = WAVES + 16,
        GIANTS_IN = WAVES + 17, LOST_TO_GIANTS = WAVES + 18, LOST_OTHER = WAVES + 19, FIRST_BUST = WAVES + 20,
        ARCHER_WAVES = WAVES + 21, EXPOSED = WAVES + 22, EXPOSED_AT_RELEASE = WAVES + 23, TO_BARRACKS = WAVES + 24,
        HIT_PER_TRAINING = WAVES + 25, TOWER_FIRE = WAVES + 26, FAR_WALK = WAVES + 27, FW_CONTACT = WAVES + 28,
        FW_FORWARD = WAVES + 29, FW_LOSS = WAVES + 30, FW_MERGED = WAVES + 31, FW_STAND = WAVES + 32, FW_HELD = WAVES + 33,
        FW_DEF_AHEAD = WAVES + 34, FW_DEF_LOSS = WAVES + 35, FLOW = WAVES + 36, PHASES = 5, T2_REL = FLOW + 4 * PHASES,
        MG_WALK_EP = T2_REL + 1, MG_WALK_LOSS = T2_REL + 2, MG_TOWER_EP = T2_REL + 3, MG_TOWER_LOSS = T2_REL + 4,
        MG_OTHER_EP = T2_REL + 5, MG_OTHER_LOSS = T2_REL + 6, COLUMNS = T2_REL + 7;

    public static void main(String[] args) {
        String a = args[0], b = args[1];
        int maps = Integer.parseInt(args[2]);
        long base = Long.parseLong(args[3]);
        long stride = args.length > 4 ? Long.parseLong(args[4]) : 1;
        List<double[]> rows = new ArrayList<>();
        List<Integer> startHp = new ArrayList<>();
        int wins = 0, deaths = 0, killed = 0, games = 0, atLimit = 0, decidedAtLimit = 0, closeAtLimit = 0;
        for (int m = 0; m < maps; m++) {
            long seed = base + m * stride;
            for (int seat = 0; seat < 2; seat++) {
                Referee.Result r = seat == 0 ? Referee.play(seed, BotFactory.create(a), BotFactory.create(b), true, 50)
                                             : Referee.play(seed, BotFactory.create(b), BotFactory.create(a), true, 50);
                games++;
                if (r.winner == seat) wins++;
                if (seat == 0 && !r.frames.isEmpty()) startHp.add(r.frames.get(0).hp[0]);
                if (r.turns >= Const.MAX_TURNS) {
                    atLimit++;
                    Referee.Frame end = r.frames.get(r.frames.size() - 1);
                    if (end.hp[0] != end.hp[1]) { decidedAtLimit++; if (Math.abs(end.hp[0] - end.hp[1]) <= 10) closeAtLimit++; }
                }
                double[] row = new double[COLUMNS];
                int waves = 0, giants = 0, archers = 0, prevTraining = 0;
                boolean[] wasTraining = new boolean[64];
                for (Referee.Frame f : r.frames) {
                    for (int k = 0; k < AT.length; k++) {
                        if (f.turn != AT[k]) continue;
                        int inc = 0, mines = 0, towers = 0, thp = 0;
                        for (int[] st : f.structures) {
                            if (st[1] != seat) continue;
                            if (st[0] == Const.MINE) { mines++; inc += st[2]; }
                            else if (st[0] == Const.TOWER) { towers++; thp += st[2]; }
                        }
                        row[k * 5] = inc; row[k * 5 + 1] = mines; row[k * 5 + 2] = towers; row[k * 5 + 3] = thp; row[k * 5 + 4] = f.hp[seat];
                    }
                    for (int i = 0; i < f.structures.length; i++) {
                        int[] st = f.structures[i];
                        boolean training = st[0] == Const.BARRACKS && st[1] == seat && st[2] > 0;
                        if (training && !wasTraining[i]) { if (st[3] == Const.GIANT) giants++; else if (st[3] == Const.ARCHER) archers++; else waves++; }
                        wasTraining[i] = training;
                    }
                }
                row[WAVES] = waves;
                row[GIANTS] = giants;
                row[ARCHER_WAVES] = archers;
                waves(r, seat, row);
                // The economy over the game: own mines built (a site turning into an own mine), upgrades (an own mine's
                // rate rising), own mines lost with gold left on the site (torn down by an enemy creep or queen) or
                // without (depleted), and the gold the own mines took from their sites.
                int built = 0, upgrades = 0, tornDown = 0, depleted = 0, mined = 0;
                int[][] prevSt = null;
                for (Referee.Frame f : r.frames) {
                    if (prevSt != null)
                        for (int i = 0; i < f.structures.length; i++) {
                            int[] was = prevSt[i], now = f.structures[i];
                            boolean wasMine = was[0] == Const.MINE && was[1] == seat, isMine = now[0] == Const.MINE && now[1] == seat;
                            if (isMine && !wasMine) built++;
                            else if (isMine && now[2] > was[2]) upgrades++;
                            else if (wasMine && !isMine) { if (now[4] > 0) tornDown++; else depleted++; }
                            if (wasMine || isMine) mined += Math.max(0, was[4] - now[4]);
                        }
                    prevSt = f.structures;
                }
                row[BUILT] = built; row[UPGRADES] = upgrades; row[TORN_DOWN] = tornDown; row[DEPLETED] = depleted; row[MINED] = mined;
                // Enemy giants: those that came out (the rise of their count between frames), own towers lost while an enemy
                // giant touched them on the frame before, the other own towers lost, and the first turn a giant touched one.
                int giantsIn = 0, lostToGiants = 0, lostOther = 0, firstBust = -1;
                Referee.Frame prevF = null;
                for (Referee.Frame f : r.frames) {
                    if (prevF != null) {
                        int g = giantCount(f, seat), pg = giantCount(prevF, seat);
                        if (g > pg) giantsIn += g - pg;
                        for (int i = 0; i < f.structures.length; i++) {
                            boolean was = prevF.structures[i][0] == Const.TOWER && prevF.structures[i][1] == seat;
                            boolean is = f.structures[i][0] == Const.TOWER && f.structures[i][1] == seat;
                            if (was && !is) { if (giantTouches(prevF, r.sites.get(i), seat)) lostToGiants++; else lostOther++; }
                        }
                    }
                    if (firstBust < 0)
                        for (int i = 0; i < f.structures.length; i++)
                            if (f.structures[i][0] == Const.TOWER && f.structures[i][1] == seat && giantTouches(f, r.sites.get(i), seat)) { firstBust = f.turn; break; }
                    prevF = f;
                }
                row[GIANTS_IN] = giantsIn; row[LOST_TO_GIANTS] = lostToGiants; row[LOST_OTHER] = lostOther;
                row[FIRST_BUST] = firstBust < 0 ? Double.NaN : firstBust;
                // Under knights: contact turns (an enemy knight within 250 of our queen), the HP lost on them, the turns
                // near an own tower (within 130 of its centre) and the MOVE commands among them.
                int contact = 0, nearTower = 0, moves = 0, lostHp = 0, prevHp = -1;
                int firstContact = -1, towersAtContact = 0, secondTower = -1;
                for (Referee.Frame f : r.frames) {
                    int qx = f.queens[seat][0], qy = f.queens[seat][1];
                    boolean knightNear = false;
                    for (int[] c : f.creeps) if (c[0] == 1 - seat && c[1] == Const.KNIGHT && (c[2] - qx) * (c[2] - qx) + (c[3] - qy) * (c[3] - qy) < 250 * 250) knightNear = true;
                    if (knightNear && firstContact < 0) {
                        firstContact = f.turn;
                        for (int[] st : f.structures) if (st[0] == Const.TOWER && st[1] == seat) towersAtContact++;
                    }
                    if (secondTower < 0) {
                        int own = 0;
                        for (int[] st : f.structures) if (st[0] == Const.TOWER && st[1] == seat) own++;
                        if (own >= 2) secondTower = f.turn;
                    }
                    if (knightNear) {
                        contact++;
                        if (prevHp >= 0) lostHp += Math.max(0, prevHp - f.hp[seat]);
                        for (int i = 0; i < f.structures.length; i++) {
                            int[] st = f.structures[i];
                            if (st[0] != Const.TOWER || st[1] != seat) continue;
                            int[] site = r.sites.get(i);
                            if ((site[1] - qx) * (site[1] - qx) + (site[2] - qy) * (site[2] - qy) <= 130 * 130) { nearTower++; break; }
                        }
                        if (f.cmds != null && f.cmds[seat] != null && f.cmds[seat].startsWith("MOVE")) moves++;
                    }
                    prevHp = f.hp[seat];
                }
                row[CONTACT] = contact;
                row[NEAR_TOWER] = contact == 0 ? Double.NaN : 100.0 * nearTower / contact;
                row[LOST_PER_CONTACT] = contact == 0 ? Double.NaN : (double) lostHp / contact;
                row[MOVES] = contact == 0 ? Double.NaN : 100.0 * moves / contact;
                row[FIRST_CONTACT] = firstContact < 0 ? Double.NaN : firstContact;
                row[TOWERS_AT_CONTACT] = firstContact < 0 ? Double.NaN : towersAtContact;
                row[LOST_AFTER_CONTACT] = firstContact < 0 ? Double.NaN : hpAt(r, seat, firstContact) - hpAt(r, seat, firstContact + 20);
                row[LOST_25_50] = r.turns < 50 ? Double.NaN : hpAt(r, seat, 25) - hpAt(r, seat, 50);
                row[T2_REL] = firstContact < 0 || secondTower < 0 ? Double.NaN : secondTower - firstContact;
                int death = -1;
                for (Referee.Frame f : r.frames) if (f.hp[seat] <= 0) { death = f.turn; break; }
                if (death >= 0) deaths++;
                boolean theirs = false;
                for (Referee.Frame f : r.frames) if (f.hp[1 - seat] <= 0) { theirs = true; break; }
                if (theirs) killed++;
                row[DEATH] = death;
                row[TURNS] = r.turns;
                towerFire(r, seat, row);
                firstWave(r, seat, row);
                hpFlow(r, seat, row);
                midgame(r, seat, row);
                rows.add(row);
            }
        }
        System.out.printf(Locale.ROOT, "%s vs %s: %d games, %s wins %d (%.1f %%), queen deaths %d, enemy queens killed %d%n", a, b, games, a, wins, 100.0 * wins / games, deaths, killed);
        int[] hp = startHp.stream().mapToInt(Integer::intValue).sorted().toArray();
        System.out.printf(Locale.ROOT, "maps %d from seed %d, stride %d: starting HP median %s, %d distinct values; decided at the limit %d, by <= 10 HP %d%n",
            maps, base, stride, hp.length == 0 ? "-" : String.valueOf(hp[hp.length / 2]), Arrays.stream(hp).distinct().count(), decidedAtLimit, closeAtLimit);
        String[] names = {"income", "mines", "towers", "towerHp", "queenHp"};
        for (int k = 0; k < AT.length; k++) {
            StringBuilder sb = new StringBuilder("turn " + AT[k] + ":");
            for (int j = 0; j < 5; j++) sb.append(' ').append(names[j]).append(' ').append(fmt(median(rows, k * 5 + j, AT[k])));
            System.out.println(sb);
        }
        System.out.printf(Locale.ROOT, "waves: knight %s, giant %s (medians), archer %s (mean); death turn median %s over the deaths; game length median %s, mean %s; at the %d-turn limit %d (%.0f %%)%n",
            fmt(median(rows, WAVES, 0)), fmt(median(rows, GIANTS, 0)), fmt1(meanNum(rows, ARCHER_WAVES)), fmt(medianIf(rows, DEATH)), fmt(median(rows, TURNS, 0)),
            fmt1(rows.stream().mapToDouble(r -> r[TURNS]).average().orElse(Double.NaN)), Const.MAX_TURNS, atLimit, 100.0 * atLimit / games);
        System.out.printf(Locale.ROOT, "under knights (medians over games with contact): contact turns %s, %% near an own tower %s, HP lost per contact turn %s, %% MOVE %s%n",
            fmt(medianNum(rows.stream().filter(r -> r[CONTACT] > 0).toList(), CONTACT)), fmt1(medianNum(rows, NEAR_TOWER)), fmt1(medianNum(rows, LOST_PER_CONTACT)), fmt1(medianNum(rows, MOVES)));
        System.out.printf(Locale.ROOT, "first contact (medians over games with contact): turn %s, own towers then %s, HP lost in the next 20 turns %s (mean %s); HP lost on turns 25-50 %s (mean %s, games reaching turn 50)%n",
            fmt(medianNum(rows, FIRST_CONTACT)), fmt(medianNum(rows, TOWERS_AT_CONTACT)), fmt(medianNum(rows, LOST_AFTER_CONTACT)), fmt1(meanNum(rows, LOST_AFTER_CONTACT)),
            fmt(medianNum(rows, LOST_25_50)), fmt1(meanNum(rows, LOST_25_50)));
        System.out.printf(Locale.ROOT, "economy over the game (medians; means): own mines built %s; %s, upgrades %s; %s, torn down %s; %s, depleted %s; %s, gold mined %s; %s%n",
            fmt(median(rows, BUILT, 0)), fmt1(meanNum(rows, BUILT)), fmt(median(rows, UPGRADES, 0)), fmt1(meanNum(rows, UPGRADES)),
            fmt(median(rows, TORN_DOWN, 0)), fmt1(meanNum(rows, TORN_DOWN)), fmt(median(rows, DEPLETED, 0)), fmt1(meanNum(rows, DEPLETED)),
            fmt(median(rows, MINED, 0)), fmt1(meanNum(rows, MINED)));
        System.out.printf(Locale.ROOT, "our waves (means over games, from turn 30): the enemy queen outside her towers' ranges on %s %% of the turns and at %s %% of our knight trainings, %s from our nearest knight barracks; she loses %s HP with our knights within 250 per knight training (whole game)%n",
            fmt1(meanNum(rows, EXPOSED)), fmt1(meanNum(rows, EXPOSED_AT_RELEASE)), fmt(meanNum(rows, TO_BARRACKS)), fmt1(meanNum(rows, HIT_PER_TRAINING)));
        System.out.printf(Locale.ROOT, "tower fire: our queen loses %s HP a game to enemy towers (mean; in %d of %d games), walks %s turns a game to a free site on the enemy half through an enemy tower's range (mean)%n",
            fmt1(meanNum(rows, TOWER_FIRE)), rows.stream().filter(r -> r[TOWER_FIRE] > 0).count(), rows.size(), fmt1(meanNum(rows, FAR_WALK)));
        List<double[]> contacts = rows.stream().filter(r -> r[FW_CONTACT] >= 0).toList();
        System.out.printf(Locale.ROOT, "first wave (the Gold study's measures, %d games with a contact): contact turn %s (median), our queen %s from her back edge then (median; mean %s; %d beyond 406), loss to knights in the 20 turns from it %s (mean), merged waves in %d (loss %s against %s unmerged), standing at an own tower with a knight within 155 %s turns a game (mean)%n",
            contacts.size(), fmt(medianNum(contacts, FW_CONTACT)), fmt(medianNum(contacts, FW_FORWARD)), fmt(meanNum(contacts, FW_FORWARD)),
            contacts.stream().filter(r -> r[FW_FORWARD] > 406).count(), fmt1(meanNum(contacts, FW_LOSS)), contacts.stream().filter(r -> r[FW_MERGED] > 0).count(),
            fmt1(meanNum(contacts.stream().filter(r -> r[FW_MERGED] > 0).toList(), FW_LOSS)), fmt1(meanNum(contacts.stream().filter(r -> r[FW_MERGED] == 0).toList(), FW_LOSS)),
            fmt1(meanNum(contacts, FW_STAND)));
        System.out.printf(Locale.ROOT, "first wave by where it meets her: beyond 406 from her back edge the loss is %s (mean, %d games), within 406 %s (%d games)%n",
            fmt1(meanNum(contacts.stream().filter(r -> r[FW_FORWARD] > 406).toList(), FW_LOSS)), contacts.stream().filter(r -> r[FW_FORWARD] > 406).count(),
            fmt1(meanNum(contacts.stream().filter(r -> r[FW_FORWARD] <= 406).toList(), FW_LOSS)), contacts.stream().filter(r -> r[FW_FORWARD] <= 406).count());
        List<double[]> held = contacts.stream().filter(r -> r[FW_HELD] >= 0).toList();
        List<double[]> ahead = contacts.stream().filter(r -> r[FW_DEF_AHEAD] > 0).toList();
        System.out.printf(Locale.ROOT, "first wave, mechanisms: the new tower she was sent to last before the contact %s from her back edge (median; mean %s; %d games, %d beyond 500); in the 20 turns from the contact, BUILDs of a free site she does not touch, more than 50 ahead of her, under a knight within 250 (the defence rule), %s a game (mean; in %d games), losing %s HP on those turns (mean over those games)%n",
            fmt(medianNum(held, FW_HELD)), fmt(meanNum(held, FW_HELD)), held.size(), held.stream().filter(r -> r[FW_HELD] > 500).count(),
            fmt1(meanNum(contacts, FW_DEF_AHEAD)), ahead.size(), fmt1(meanNum(ahead, FW_DEF_LOSS)));
        String[] flow = {"our queen to the enemy knights", "our queen to the enemy towers", "the enemy queen to our knights", "the enemy queen to our towers"};
        StringBuilder fl = new StringBuilder("HP flow (means a game, turns 0-49, 50-99, 100-149, 150-199, 200-249):");
        for (int k = 0; k < 4; k++) {
            fl.append(k == 0 ? " " : "; ").append(flow[k]);
            for (int ph = 0; ph < PHASES; ph++) fl.append(' ').append(fmt1(meanNum(rows, FLOW + k * PHASES + ph)));
        }
        System.out.println(fl);
        System.out.printf(Locale.ROOT, "second tower: stands %s turns after the first contact (median over games with both; mean %s)%n",
            fmt(medianNum(rows, T2_REL)), fmt1(meanNum(rows, T2_REL)));
        System.out.printf(Locale.ROOT, "midgame contacts (turns 50-249, means a game; the study of 2026-11-08): episodes begun on a walk to a site %s losing %s HP"
                + " (%s per episode), begun at an own tower %s losing %s HP (%s per episode), other %s losing %s HP%n",
            fmt1(meanNum(rows, MG_WALK_EP)), fmt1(meanNum(rows, MG_WALK_LOSS)), fmt1(sum(rows, MG_WALK_LOSS) / Math.max(1, sum(rows, MG_WALK_EP))),
            fmt1(meanNum(rows, MG_TOWER_EP)), fmt1(meanNum(rows, MG_TOWER_LOSS)), fmt1(sum(rows, MG_TOWER_LOSS) / Math.max(1, sum(rows, MG_TOWER_EP))),
            fmt1(meanNum(rows, MG_OTHER_EP)), fmt1(meanNum(rows, MG_OTHER_LOSS)));
        List<double[]> withGiants = rows.stream().filter(r -> r[GIANTS_IN] > 0).toList();
        System.out.printf(Locale.ROOT, "enemy giants: in %d of %d games (mean %s a game); in those games own towers lost to giants %s, to other causes %s (means), first giant at an own tower turn %s (median)%n",
            withGiants.size(), rows.size(), fmt1(rows.stream().mapToDouble(r -> r[GIANTS_IN]).average().orElse(Double.NaN)),
            fmt1(meanNum(withGiants, LOST_TO_GIANTS)), fmt1(meanNum(withGiants, LOST_OTHER)), fmt(medianNum(withGiants, FIRST_BUST)));
    }

    /**
     * Our waves and the enemy queen, from turn 30 on: the share of turns she stands outside all her towers' ranges, the
     * same at our knight barracks' training starts, her mean distance to our nearest knight barracks, and over the game
     * the HP she loses on turns with our knights within 250 of her, per knight barracks training of ours.
     */
    static void waves(Referee.Result r, int seat, double[] row) {
        int turns = 0, out = 0, releases = 0, outAtRelease = 0, distN = 0, lost = 0, trainings = 0;
        double dist = 0;
        boolean[] wasTraining = new boolean[64];
        Referee.Frame prev = null;
        for (Referee.Frame f : r.frames) {
            int ex = f.queens[1 - seat][0], ey = f.queens[1 - seat][1];
            boolean exposed = true;
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < f.structures.length; i++) {
                int[] st = f.structures[i], site = r.sites.get(i);
                double d2 = (double) (site[1] - ex) * (site[1] - ex) + (double) (site[2] - ey) * (site[2] - ey);
                if (st[0] == Const.TOWER && st[1] == 1 - seat && d2 < (double) st[3] * st[3]) exposed = false;
                if (st[0] == Const.BARRACKS && st[1] == seat && st[3] == Const.KNIGHT) nearest = Math.min(nearest, Math.sqrt(d2));
            }
            for (int i = 0; i < f.structures.length; i++) {
                int[] st = f.structures[i];
                boolean training = st[0] == Const.BARRACKS && st[1] == seat && st[3] == Const.KNIGHT && st[2] > 0;
                if (training && !wasTraining[i]) {
                    trainings++;
                    if (f.turn >= 30) { releases++; if (exposed) outAtRelease++; }
                }
                wasTraining[i] = training;
            }
            if (f.turn >= 30) {
                turns++;
                if (exposed) out++;
                if (nearest < Double.MAX_VALUE) { dist += nearest; distN++; }
            }
            if (prev != null) {
                boolean near = false;
                for (int[] c : f.creeps)
                    if (c[0] == seat && c[1] == Const.KNIGHT && (double) (c[2] - ex) * (c[2] - ex) + (double) (c[3] - ey) * (c[3] - ey) < 250.0 * 250) near = true;
                if (near) lost += Math.max(0, prev.hp[1 - seat] - f.hp[1 - seat]);
            }
            prev = f;
        }
        row[EXPOSED] = turns == 0 ? Double.NaN : 100.0 * out / turns;
        row[EXPOSED_AT_RELEASE] = releases == 0 ? Double.NaN : 100.0 * outAtRelease / releases;
        row[TO_BARRACKS] = distN == 0 ? Double.NaN : dist / distN;
        row[HIT_PER_TRAINING] = trainings == 0 ? Double.NaN : (double) lost / trainings;
    }

    /**
     * The game replayed through {@link Rules} with the recorded commands (frame k holds the commands of the turn that
     * made it): the HP our queen loses to enemy towers, from a probe of each turn without the enemy knights (as
     * {@link DeathTrace}'s {@code td}), and the turns her BUILD target is a free site on the enemy half (nearer the
     * enemy's corner) with the straight walk to it through an enemy tower's range ({@link TowerFire}).
     */
    static void towerFire(Referee.Result r, int seat, double[] row) {
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        int foe = 1 - seat, hp = 0, far = 0;
        double hx = s.qx[seat] < Const.WIDTH / 2.0 ? 0 : Const.WIDTH, hy = hx == 0 ? 0 : Const.HEIGHT;
        for (int k = 1; k < r.frames.size() && !s.over; k++) {
            String[] raw = r.frames.get(k).cmds;
            Command[] cmd = {parse(raw[0], s.nSites), parse(raw[1], s.nSites)};
            Command c = cmd[seat];
            if (c.queen == Command.BUILD && c.site >= 0 && s.stType[c.site] == Const.NONE
                && Physics.distSq(s.sx[c.site], s.sy[c.site], hx, hy) > Physics.distSq(s.sx[c.site], s.sy[c.site], Const.WIDTH - hx, Const.HEIGHT - hy)
                && TowerFire.pathThroughRange(s, foe, s.qx[seat], s.qy[seat], c.site)) far++;
            GameState probe = s.copy();
            for (int i = 0; i < probe.nCreeps; i++)
                if (probe.cOwner[i] == foe && probe.cType[i] == Const.KNIGHT) probe.cHp[i] = 0;
            probe.removeDeadCreeps();
            new Rules().resolve(probe, cmd[0], cmd[1]);
            hp += Math.max(0, s.hp[seat] - probe.hp[seat]);
            rules.resolve(s, cmd[0], cmd[1]);
        }
        row[TOWER_FIRE] = hp;
        row[FAR_WALK] = far;
    }

    /**
     * The enemy's first knight wave, in the Gold replay study's terms (docs/data/study-2026-11-07-gold, cr.tools.WaveTrace):
     * the first contact is the first turn an enemy knight stands within 250 of our queen at the turn's start. Records its
     * turn, the queen's distance from her back edge then (x for a home on the left, 1920 - x otherwise), the HP she loses
     * to knights (a probe with the enemy towers' ranges zeroed) in the 20 turns from it, whether the wave merged (a turn
     * with a knight within 250 followed by 2 or more new enemy knights within 500), and the turns she stood at an own tower
     * (a BUILD of it, moving under 20) with a knight within 155. -1 in every column without a contact.
     * Two mechanism measures of the meet-back experiment (docs/data/exp-2026-11-07-meet-back): the distance from her back
     * edge of the free site of the last BUILD ... TOWER she issued before the contact turn (-1 without one; the contact turn's
     * own command is left out, since the defence rule may already act on it), and, in the 20
     * turns from the contact, the turns with a BUILD of a free site she does not touch, more than 50 ahead of her (toward
     * the enemy's side, along x), with an enemy knight within 250 (the defence rule's walks forward), with her loss to
     * knights on those turns.
     */
    static void firstWave(Referee.Result r, int seat, double[] row) {
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        int foe = 1 - seat;
        boolean left = s.qx[seat] < Const.WIDTH / 2.0;
        int contact = -1, loss = 0, stand = 0, prevK500 = -1, prevK250 = 0, defAhead = 0, defLoss = 0;
        boolean merged = false;
        double forward = -1, held = -1;
        for (int k = 1; k < r.frames.size() && !s.over; k++) {
            int t = s.turn;
            if (contact >= 0 && t >= contact + 20) break;
            String[] raw = r.frames.get(k).cmds;
            Command[] cmd = {parse(raw[0], s.nSites), parse(raw[1], s.nSites)};
            double qx = s.qx[seat], qy = s.qy[seat], nearK = Double.MAX_VALUE;
            int k250 = 0, k500 = 0;
            for (int i = 0; i < s.nCreeps; i++) {
                if (s.cOwner[i] != foe || s.cType[i] != Const.KNIGHT || s.cHp[i] <= 0) continue;
                double d = Math.sqrt(Physics.distSq(s.cx[i], s.cy[i], qx, qy));
                if (d < 250) k250++;
                if (d < 500) k500++;
                nearK = Math.min(nearK, d);
            }
            if (contact < 0 && k250 > 0) {
                contact = t;
                forward = left ? qx : Const.WIDTH - qx;
            }
            boolean inWindow = contact >= 0;
            if (inWindow && prevK500 >= 0 && prevK250 >= 1 && k500 - prevK500 >= 2) merged = true;
            Command c = cmd[seat];
            boolean ownTower = c.queen == Command.BUILD && c.site >= 0 && s.stType[c.site] == Const.TOWER && s.stOwner[c.site] == seat;
            boolean buildsFree = c.queen == Command.BUILD && c.site >= 0 && c.site < s.nSites && s.stType[c.site] == Const.NONE;
            if (contact < 0 && buildsFree && "TOWER".equals(c.structure)) held = left ? s.sx[c.site] : Const.WIDTH - s.sx[c.site];
            boolean defenceAhead = inWindow && buildsFree && k250 >= 1 && c.site != Protocol.touchedSite(s, seat)
                && (left ? s.sx[c.site] - qx : qx - s.sx[c.site]) > 50;
            GameState knightsOnly = null;
            if (inWindow) {
                knightsOnly = s.copy();
                for (int j = 0; j < knightsOnly.nSites; j++)
                    if (knightsOnly.stType[j] == Const.TOWER && knightsOnly.stOwner[j] == foe) knightsOnly.towerRange[j] = 0;
                new Rules().resolve(knightsOnly, cmd[0], cmd[1]);
            }
            int hpBefore = s.hp[seat];
            rules.resolve(s, cmd[0], cmd[1]);
            if (inWindow) {
                loss += Math.max(0, hpBefore - knightsOnly.hp[seat]);
                if (defenceAhead) { defAhead++; defLoss += Math.max(0, hpBefore - knightsOnly.hp[seat]); }
                double moved = Math.sqrt(Physics.distSq(qx, qy, s.qx[seat], s.qy[seat]));
                if (ownTower && k250 >= 1 && moved < 20 && nearK < 155) stand++;
                prevK500 = k500;
                prevK250 = k250;
            }
        }
        row[FW_CONTACT] = contact;
        row[FW_FORWARD] = contact < 0 ? -1 : forward;
        row[FW_LOSS] = contact < 0 ? -1 : loss;
        row[FW_MERGED] = contact < 0 ? -1 : merged ? 1 : 0;
        row[FW_STAND] = contact < 0 ? -1 : stand;
        row[FW_HELD] = contact < 0 ? -1 : held;
        row[FW_DEF_AHEAD] = contact < 0 ? -1 : defAhead;
        row[FW_DEF_LOSS] = contact < 0 ? -1 : defLoss;
    }

    /**
     * The HP flow of a game by phase of 50 turns (the meet-back experiment, docs/data/exp-2026-11-07-meet-back): what our
     * queen loses to the enemy knights (a probe with the enemy towers' ranges zeroed) and to the enemy towers (a probe with
     * the enemy knights removed), and what the enemy queen loses to our knights and our towers, the same way.
     */
    static void hpFlow(Referee.Result r, int seat, double[] row) {
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        int foe = 1 - seat;
        for (int k = 1; k < r.frames.size() && !s.over; k++) {
            int ph = Math.min(PHASES - 1, s.turn / 50);
            String[] raw = r.frames.get(k).cmds;
            Command[] cmd = {parse(raw[0], s.nSites), parse(raw[1], s.nSites)};
            int[] side = {seat, foe};
            for (int q = 0; q < 2; q++) {
                int victim = side[q], attacker = 1 - victim;
                GameState knights = s.copy(), towers = s.copy();
                for (int j = 0; j < s.nSites; j++)
                    if (knights.stType[j] == Const.TOWER && knights.stOwner[j] == attacker) knights.towerRange[j] = 0;
                for (int i = 0; i < towers.nCreeps; i++)
                    if (towers.cOwner[i] == attacker && towers.cType[i] == Const.KNIGHT) towers.cHp[i] = 0;
                towers.removeDeadCreeps();
                new Rules().resolve(knights, cmd[0], cmd[1]);
                new Rules().resolve(towers, cmd[0], cmd[1]);
                row[FLOW + (2 * q) * PHASES + ph] += Math.max(0, s.hp[victim] - knights.hp[victim]);
                row[FLOW + (2 * q + 1) * PHASES + ph] += Math.max(0, s.hp[victim] - towers.hp[victim]);
            }
            rules.resolve(s, cmd[0], cmd[1]);
        }
    }

    /**
     * The midgame contact episodes of the study of 2026-11-08 (docs/data/study-2026-11-08-next-step): on turns 50-249, a run
     * of turns with an enemy knight within 250 of our queen at the turn's start is one episode; it is classed by what she did
     * on the turn before it began: a BUILD of a site she did not touch (a walk to a site), standing within 130 of an own
     * tower's centre (at an own tower), or anything else. Records the episodes of each class and the HP they cost her to
     * knights (a probe with the enemy towers' ranges zeroed).
     */
    static void midgame(Referee.Result r, int seat, double[] row) {
        GameState s = MapGen.generate(r.seed, 4);
        Rules rules = new Rules();
        int foe = 1 - seat;
        boolean inEpisode = false, prevWalk = false, prevAtTower = false;
        int epClass = 0, epLoss = 0;
        int[] episodes = new int[3], losses = new int[3];
        for (int k = 1; k < r.frames.size() && !s.over; k++) {
            int t = s.turn;
            if (t >= 250) break;
            String[] raw = r.frames.get(k).cmds;
            Command[] cmd = {parse(raw[0], s.nSites), parse(raw[1], s.nSites)};
            double qx = s.qx[seat], qy = s.qy[seat];
            int k250 = 0;
            for (int i = 0; i < s.nCreeps; i++)
                if (s.cOwner[i] == foe && s.cType[i] == Const.KNIGHT && s.cHp[i] > 0 && Physics.distSq(s.cx[i], s.cy[i], qx, qy) < 250.0 * 250.0) k250++;
            if (t >= 50) {
                if (k250 > 0 && !inEpisode) { inEpisode = true; epLoss = 0; epClass = prevWalk ? 0 : prevAtTower ? 1 : 2; }
                else if (k250 == 0 && inEpisode) { inEpisode = false; episodes[epClass]++; losses[epClass] += epLoss; }
            }
            Command c = cmd[seat];
            int touched = Protocol.touchedSite(s, seat);
            prevWalk = c.queen == Command.BUILD && c.site >= 0 && c.site < s.nSites && c.site != touched;
            prevAtTower = false;
            for (int j = 0; j < s.nSites; j++)
                if (s.stType[j] == Const.TOWER && s.stOwner[j] == seat && Physics.distSq(s.sx[j], s.sy[j], qx, qy) <= 130.0 * 130.0) { prevAtTower = true; break; }
            int hpBefore = s.hp[seat];
            if (inEpisode) {
                GameState knights = s.copy();
                for (int j = 0; j < knights.nSites; j++)
                    if (knights.stType[j] == Const.TOWER && knights.stOwner[j] == foe) knights.towerRange[j] = 0;
                new Rules().resolve(knights, cmd[0], cmd[1]);
                epLoss += Math.max(0, hpBefore - knights.hp[seat]);
            }
            rules.resolve(s, cmd[0], cmd[1]);
        }
        if (inEpisode) { episodes[epClass]++; losses[epClass] += epLoss; }
        row[MG_WALK_EP] = episodes[0]; row[MG_WALK_LOSS] = losses[0];
        row[MG_TOWER_EP] = episodes[1]; row[MG_TOWER_LOSS] = losses[1];
        row[MG_OTHER_EP] = episodes[2]; row[MG_OTHER_LOSS] = losses[2];
    }

    /** The sum of column c over the games where it is a number. */
    static double sum(List<double[]> rows, int c) {
        return rows.stream().mapToDouble(r -> r[c]).filter(x -> !Double.isNaN(x)).sum();
    }

    /** A recorded command pair "queen line | train line" (or "<none>"), as the referee parsed it. */
    static Command parse(String raw, int sites) {
        int bar = raw.indexOf(" | ");
        return bar < 0 ? Command.parse(null, null, sites) : Command.parse(raw.substring(0, bar), raw.substring(bar + 3), sites);
    }

    static int giantCount(Referee.Frame f, int seat) {
        int n = 0;
        for (int[] c : f.creeps) if (c[0] == 1 - seat && c[1] == Const.GIANT) n++;
        return n;
    }

    /** An enemy giant touches the site (within its radius, the giant's and the touching delta, plus 5 for the rounding). */
    static boolean giantTouches(Referee.Frame f, int[] site, int seat) {
        double reach = site[3] + Const.CREEP_RADIUS[Const.GIANT] + Const.TOUCHING_DELTA + 5;
        for (int[] c : f.creeps)
            if (c[0] == 1 - seat && c[1] == Const.GIANT && (double) (c[2] - site[1]) * (c[2] - site[1]) + (double) (c[3] - site[2]) * (c[3] - site[2]) < reach * reach) return true;
        return false;
    }

    /** The queen's HP on the frame of turn t, or on the last frame if the game ended before t. */
    static double hpAt(Referee.Result r, int seat, int t) {
        Referee.Frame last = null;
        for (Referee.Frame f : r.frames) { if (f.turn > t) break; last = f; }
        return last == null ? Double.NaN : last.hp[seat];
    }

    /** The median of column c over the games that reached turn t. */
    static double median(List<double[]> rows, int c, int t) {
        double[] v = rows.stream().filter(r -> r[TURNS] >= t).mapToDouble(r -> r[c]).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    /** The mean of column c over the games where it is a number. */
    static double meanNum(List<double[]> rows, int c) {
        return rows.stream().mapToDouble(r -> r[c]).filter(x -> !Double.isNaN(x)).average().orElse(Double.NaN);
    }

    static double medianIf(List<double[]> rows, int c) {
        double[] v = rows.stream().filter(r -> r[c] >= 0).mapToDouble(r -> r[c]).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    static String fmt(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.0f", v); }

    static String fmt1(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.1f", v); }

    /** The median of column c over the games where it is a number. */
    static double medianNum(List<double[]> rows, int c) {
        double[] v = rows.stream().mapToDouble(r -> r[c]).filter(x -> !Double.isNaN(x)).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }
}
