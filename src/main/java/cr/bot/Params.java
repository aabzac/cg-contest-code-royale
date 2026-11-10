package cr.bot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Tunable weights as public fields. {@link #apply(String)} takes "K=v,K2=v"; unknown keys fail.
 * Frozen versions keep their own snapshot (resources cr/bot/versions/vNN.properties).
 */
public final class Params {
    /** Income target before the greedy bot starts building towers (10 in v01/v02; 3 since v03: early towers). */
    public int INCOME_TARGET = 3;
    /** Towers kept before further ones are skipped (4 in v01/v02; 8 in v03-v05; 6 since v06, with the threat towers counted). */
    public int MAX_TOWERS = 6;
    /** Knight barracks kept by the greedy bot (1 in v01-v05; 2 since v06). */
    public int BARRACKS = 2;
    /**
     * Own towers that must stand before the second and every later barracks is built (0: the
     * barracks come one after the other, before the mines of the income phase). Does nothing with
     * BARRACKS=1. The army direction of 2026-11-03 (docs/bot-architecture.html, the directions section);
     * 4 since v06 (P2 docs/data/exp-2026-11-03-army and -safety).
     */
    public int BARRACKS_AFTER_TOWERS = 4;
    /**
     * Where the second and every later barracks goes: 0, the ladder's usual site (nearest to the
     * midpoint of the queen and home); otherwise the free site, not under an enemy tower, nearest to
     * the point this many percent of the way from the home corner to the enemy's corner (50: the
     * centre of the board). Knights from there walk less under the enemy's towers. 50 by default since v10
     * (2026-11-06: P2 docs/data/exp-2026-11-06-waves, screen S 0.5426 against v09, +3.09 pp against the rival R0;
     * 0 in v01-v09).
     */
    public int BARRACKS_FORWARD = 50;
    /**
     * 1: TRAIN only when every own barracks is idle and the gold covers all of them, so two barracks
     * release their waves on the same turn (one wave of 8 knights rather than two of 4). With one
     * barracks it plays as 0 (train whenever affordable). On since v06.
     */
    public int TRAIN_SYNC = 1;
    /** Enemy knights closer than this to the queen are the threat that stops her mines and sends her to a tower (250 in v01-v05). */
    public int THREAT_RANGE = 250;
    /**
     * Under threat, when fewer than this many own towers stand within THREAT_RADIUS of the queen, she
     * builds a new tower on the nearest free site within that radius (not under an enemy tower)
     * instead of topping up the tower she touches or walking to her nearest tower. 0: v05's flight
     * (the nearest own tower, topped up). The queen's safety direction of 2026-11-03: in v05's arena
     * deaths 1029 of 1103 hit turns were spent at one tower topping it up, while a tower deals 3
     * damage a turn at 200 HP and 4 at 700, so a second tower doubles the kill rate and a top-up
     * adds a quarter (docs/data/study-2026-11-03-queen-safety). 2 since v06 (P2 docs/data/exp-2026-11-03-safety:
     * S 0.64-0.65 against v05 with the army flags).
     */
    public int THREAT_TOWERS_NEAR = 2;
    /** The radius around the queen for THREAT_TOWERS_NEAR: own towers counted and free sites considered (500 since v06). */
    public int THREAT_RADIUS = 500;
    /**
     * Income target once the ladder is complete (0: none, the idle queen pumps her home tower as in
     * v01-v06). Below it, with no knight near, she walks to the nearest of a free gold site (a new mine)
     * and an own mine below its maximum (an upgrade) and builds there; the touch rule builds a mine on a
     * free gold site too. The economy direction of 2026-11-03 (docs/data/exp-2026-11-03-late-income): the
     * opponents who beat v06 hold income 8 at turn 100 against our 3 and release twice the waves, while
     * our queen spends 74 turns a game topping up towers after the ladder.
     */
    public int INCOME_LATE = 0;
    /**
     * 1: kiting. With enemy knights within KITE_RANGE and nothing for the defence rule to build, the
     * queen MOVEs on the heading that leaves her farthest from the nearest knight after the predicted
     * step, staying under her own towers' cover when she is covered now (KITE_COVER=1) and out of enemy
     * tower range, instead of standing at her tower topping it up. 0: v01-v06. From the study of
     * 2026-11-03 (docs/data/study-2026-11-03-arena-winners): the arena opponents who beat v06 MOVE on
     * 42 % of their turns under knights and lose a third less HP per contact turn; v06 never does. On since
     * v07 (P2 docs/data/exp-2026-11-03-kite: S 0.68-0.69 against v06, +3 pp against field).
     */
    public int KITE = 2;  // 1: v07's rule; 2: bound to the towers (KITE_PULL, KITE_MAX), since v08
    /** Knights closer than this start the kiting (250: contact; 400-500: before contact, as the winners do). */
    public int KITE_RANGE = 250;
    /** 1: a kiting step must stay within an own tower's range when the queen is covered now (KITE=1 only). */
    public int KITE_COVER = 1;
    /**
     * KITE=2, kiting bound to the towers (2026-11-03, after v07's arena games: with KITE=1 the queen ran
     * out of her towers' cover and the knights chased her for 50-130 turns without dying): every step must
     * end within an own tower's range, the score is the distance to the nearest knight minus KITE_PULL
     * percent of the distance to the nearest own tower, and with no own tower in reach the flight of v06
     * applies. KITE_MAX > 0 caps the consecutive kiting turns: after that many the other rules get a turn
     * (the top-up at the tower she stands by).
     */
    public int KITE_PULL = 100;
    public int KITE_MAX = 0;
    /**
     * 1: when the BUILD towards the next site to take would leave the queen standing (a site straight
     * ahead pushes her back, or two sites wedge her), she detours with MOVEs (MainBot.unstick).
     * 0 plays as v01. A P2 targeted fix (docs/local-screen.md); on by default since v02 (2026-10-30).
     * Replaced by ROUTE when that is on (since v04), so it only plays with ROUTE=0.
     */
    public int UNSTICK = 1;
    /** Experimental final approach toward the same site, favoring the following site; off in v03 and v04 (ROUTE replaces it when on). */
    public int APPROACH = 0;
    /**
     * 1: the walk to the next site to take is planned with the sites only (MainBot's RoutePlanner):
     * a heading fan around obstacles and the landing point for the next target, replacing UNSTICK
     * and APPROACH. Off in v03; on by default since v04 (2026-11-02: P2 docs/data/exp-2026-11-01-route,
     * selected under P1, docs/arena-uploads.md).
     */
    public int ROUTE = 1;
    /**
     * Own mines the greedy bot builds before its knight barracks (0: the barracks first, as v01-v04;
     * 2 by default since v05, 2026-11-03: P2 docs/data/exp-2026-11-03-mines-first, S 0.76 against v04).
     * From the study of 2026-11-03 (docs/data/study-2026-11-03-losses): every stronger group opens
     * with a mine and builds the barracks on turn 7-13.
     */
    public int MINES_FIRST = 2;
    /** Tower HP below which the queen tops up an own tower she touches (700 in v01-v04). The idle queen still pumps her home tower. */
    public int TOWER_HP = 700;
    /**
     * 1: while the income is below INCOME_TARGET, own mines below their maximum are targets too: the
     * queen walks to the nearest site to take, a free gold site or such a mine, and upgrades it on the
     * touch (0: only free gold sites are targets, as v01-v04; a mine is upgraded only when passed).
     */
    public int UPGRADE = 0;
    /**
     * N > 0: while an enemy knight wave is coming (an enemy knight barracks trains, or enemy knights are
     * on the board) and fewer than N own towers stand, the ladder takes towers before its income phase,
     * and the queen neither upgrades a mine nor tops up a tower she touches (0: off, as v01-v08). From the
     * Silver study of 2026-11-05 (docs/data/study-2026-11-05-silver): the enemy's first wave meets v08
     * with one own tower in a third of the games, which lose 12 HP in its first 20 turns (5 with two).
     */
    public int WAVE_TOWERS = 0;
    /** N > 0: the queen tops up an own tower she touches only once N own towers stand (0: from the first, as v01-v08). */
    public int TOPUP_FROM = 0;
    /**
     * T > 0: from turn T on, while our queen has more HP than the enemy's (the score at the 250-turn limit),
     * the queen holds: no walk to the ladder's next site (she builds her own tower nearest home instead), and
     * the defence under threat builds only on a site she touches. Kiting, the flight and the touch rules
     * stay (0: off, as v01-v08). From the Silver study of 2026-11-05: 10 of the 34 races v08 lost at the
     * limit it led after turn 200. 200 by default since v09 (2026-11-05: P2 docs/data/exp-2026-11-05-late-hold,
     * screen S 0.5269 against v08, +1.46 pp against field; the local champion, not uploaded).
     */
    public int LATE_HOLD = 200;
    /**
     * Safety conditions on INCOME_LATE's walks to a late site (0: off, as v06-v09; the touch rule is unchanged).
     * LATE_COVER=1: only sites an own tower covers. LATE_HALF=1: only sites nearer the home corner than the enemy's.
     * LATE_CLEAR=R &gt; 0: no walk while an enemy knight is within R of the queen. From the income experiment of
     * 2026-11-06 (docs/data/exp-2026-11-06-income): INCOME_LATE gains against the rival R0 but loses the v09 mirror,
     * where our queen dies more often.
     */
    public int LATE_COVER = 0;
    public int LATE_HALF = 0;
    public int LATE_CLEAR = 0;
    /**
     * 1: giants. From GIANT_TURN on, while the enemy holds a tower and fewer than GIANTS_MAX giants have been trained
     * (0: no cap), the queen builds a giant barracks before the ladder's next site (a free site nearest the midpoint of
     * herself and home, not under an enemy tower; the touch rule builds it on a free site too); its giants are trained
     * before the knights, which wait while the idle giant barracks saves for its 140 gold. The knight barracks of the
     * ladder (BARRACKS) do not count it. 0: off, as v01-v09. From the income experiment of 2026-11-06
     * (docs/data/exp-2026-11-06-income): the late income buys knights that the mirror's six towers grind down, while
     * the rival R0's two giants from turn 80 cost v09 6 pp (docs/data/exp-2026-11-06-rival).
     */
    public int GIANTS = 0;
    public int GIANT_TURN = 0;
    public int GIANTS_MAX = 0;
    /**
     * Answers to the enemy's giants (0: off, as v01-v09). From the giants experiment of 2026-11-06
     * (docs/data/exp-2026-11-06-giants): the rival R0's giants knock down 3.8 of v09's towers a game and double its
     * queen's deaths. GIANT_GUARD=D &gt; 0: with no knight threat, while an enemy giant stands within D of an own tower's
     * centre, the queen walks to that tower (the one with the giant nearest) and tops it up, after the defence and the
     * kiting and before the touch rule (a top-up adds 100 HP, a giant busts 80 a turn). ARCHERS=1: while an enemy giant
     * stands within ARCHER_RANGE of an own tower's centre (0: anywhere on the board), the queen builds an archer barracks
     * on the free site nearest the midpoint of herself and home before the ladder's next site, and its archers train
     * before the knights, which wait while it saves for 100 gold.
     */
    public int GIANT_GUARD = 0;
    public int ARCHERS = 0;
    public int ARCHER_RANGE = 0;
    /**
     * RELEASE_NEAR=R &gt; 0 with RELEASE_HOLD=T &gt; 0 (TRAIN_SYNC=1 only): a ready knight release (every knight barracks
     * idle and the gold for all of them) waits until the enemy queen is within R of one of our knight barracks, at most
     * T turns; 0: off, as v01-v09. From the waves experiment of 2026-11-06 (docs/data/exp-2026-11-06-waves): v09's waves
     * released with the enemy queen within 600 of the barracks do 8.2 HP of damage against the rival R0, those from
     * 1000-1200 away 2.8.
     */
    public int RELEASE_NEAR = 0;
    public int RELEASE_HOLD = 0;
    /**
     * SAFE_LADDER: the ladder's sites on the enemy half (nearer the enemy's corner than ours). 1: such a site is a target
     * only while the straight walk from the queen to it stays out of every enemy tower's range; 2: never a target; 0: as
     * v01-v10. From the study of 2026-11-07 (docs/data/study-2026-11-07-tower-fire): with the free sites of our half
     * taken or under enemy towers, v10's ladder walked the queen to free sites deep in the enemy's base, through their
     * towers' ranges; those walks took 436 of the 810 HP enemy towers cost her in 160 Silver games. 1 by default since
     * v11 (2026-11-07: a P2 targeted fix, docs/data/exp-2026-11-07-safe-ladder, 148 of 2000 maps changed against v10's
     * mirror, S 0.5030; the user's decision).
     */
    public int SAFE_LADDER = 1;
    /**
     * Meeting the enemy's first knight wave nearer the back edge (0: off, as v01-v11; the anchor since v12). From the replay study of 2026-11-07
     * (docs/data/study-2026-11-07-gold, section 6): in the Gold HP races v11 lost, the queen met the first wave 444 from her
     * back edge against 380 in those it won, and beyond 406 it cost 15.8 HP against 5.6; a second enemy group joining the
     * first (19 of 44 lost races, 7 of 62 won) carried the whole gap. Experiment docs/data/exp-2026-11-07-meet-back.
     * WAVE_ANCHOR=A &gt; 0: while the first-wave window is open (an enemy knight barracks stands or an enemy knight is on
     * the board, and the turn is below WAVE_UNTIL; 0: no end), once an own tower stands or is planned, the ladder's
     * tower-phase target is the free site nearest the point at the walker's height A % of the way from her to the back
     * edge, instead of the midpoint of her and the home corner (the mines, the first barracks, the forward barracks and
     * the first tower are as before). WAVE_ANCHOR=50 with WAVE_UNTIL=30 by default since v12 (2026-11-07: P2 on PV3 screen
     * block 5, S 0.5654 against v11, field -1.49 pp, rival +1.35 pp; the user's decision); longer windows cost the midgame
     * against field and the rival on the development seeds.
     */
    public int WAVE_ANCHOR = 50;
    public int WAVE_UNTIL = 30;
    /**
     * DEFENCE_FWD=F &gt; 0: the defence rule (THREAT_TOWERS_NEAR) builds only on a free site less than F ahead of the queen
     * toward the enemy's side (along x), or on the site she touches; with none, it builds nothing (0: any site within
     * THREAT_RADIUS, as v06-v11). Same study: in the costliest Gold first wave the defence rule walked her 230 toward the
     * enemy through five knights. DEFENCE_UNTIL=T &gt; 0 keeps the limit to the turns before T (0: the whole game).
     */
    public int DEFENCE_FWD = 0;
    public int DEFENCE_UNTIL = 0;
    /**
     * STAND_REACH=R &gt; 0: when kiting finds no step that gains and the nearest enemy knight is within R, the queen still
     * takes the best-scoring kite step that moves her at least 40, rather than standing (0: she stands, as v07-v11). Same
     * study: standing at an own tower with a knight within 155 cost 2.6 HP a turn against 1.25 for a kite step.
     */
    public int STAND_REACH = 0;
    /**
     * WAVE_TOWER_HP=H &gt; 0: while fewer than 2 own towers stand and an enemy knight wave is coming (an enemy knight barracks
     * trains, or enemy knights are on the board), the queen tops up the own tower she touches only while its HP is below H
     * instead of TOWER_HP, so she leaves for the second tower earlier (0: off, as v01-v12). From the study of 2026-11-08
     * (docs/data/study-2026-11-08-next-step): in v12's Gold HP races the first wave is lost when the second tower stands
     * after the contact (the contact at turn 24.5, the tower at 25.2; the tower 3 or more turns before the contact: 25 % of
     * the races lost and 1.4 HP to the wave, 3 or more after: 63 % and 11.6 HP), and the first tower got 6 top-ups before
     * the walk to the second. Unlike WAVE_TOWERS and TOPUP_FROM (rejected twice: the first tower stayed at 200 HP and
     * melted), the first tower keeps a partial pump. Experiment docs/data/exp-2026-11-09-meet-waves.
     */
    public int WAVE_TOWER_HP = 0;
    /**
     * LADDER_CLEAR=R &gt; 0: after the first-wave window (WAVE_ANCHOR's: an enemy knight barracks or knight out, before
     * WAVE_UNTIL) and once 2 own towers stand, while an enemy knight is within R of the queen and none within THREAT_RANGE,
     * the walk to the ladder's next site (and INCOME_LATE's site, and the idle walk to the home tower) is replaced by a walk to
     * her nearest own tower not under an enemy tower, a top-up when she touches it, for at most LADDER_CLEAR_MAX consecutive
     * turns a spell (0: no cap); 0: off, as v01-v12. The defence rule, kiting, the flight and the touch rule are unchanged.
     * Same study: in the lost Gold races 200 of 360 contact episodes on turns 50-249 began while she walked to a site, at
     * 2.04 HP each against 0.97 for those met at an own tower, and a knight was within 500 before 197 of the 199.
     * 500 by default since v13 (2026-11-09: P2 docs/data/exp-2026-11-09-meet-waves, PV3 screen block 6, S 0.5960 against
     * v12, field +5.1 pp, rival +7.5 pp; the plateau 400-600 on the development seeds, the spell cap does nothing; the
     * user's decision); 0 in v01-v12.
     */
    public int LADDER_CLEAR = 500;
    public int LADDER_CLEAR_MAX = 0;
    /**
     * Standing where the own towers overlap (0: off, as v01-v13). From the study of 2026-11-09
     * (docs/data/study-2026-11-09-v13-gold, section 6): the HP v13's queen loses on a turn with an enemy knight within 250
     * is 1.11 under one covering own tower, 0.46 under two, 0.17 under three; in its lost Gold games 27.0 of the 31.6 HP
     * of the midgame contacts go under one or two, and a better-covered spot lies within 180 of her on about half of
     * those turns. Experiment docs/data/exp-2026-11-10-overlap.
     * HOLD_OVERLAP=R &gt; 0: where LADDER_CLEAR's hold and the flight pick her nearest own tower, she takes the spot with
     * the most own towers covering it among those within ceil(R / 60) steps (her position, the predicted positions on 24
     * headings, the touch points of own towers on the straight walk to them; none inside an enemy tower's range, and in
     * the hold none within THREAT_RANGE of an enemy knight): an own tower she touches, else an own tower's touch point,
     * else where she stands, else the nearest of the others. With no spot better covered than the end of v13's walk,
     * v13's command stands. OVERLAP_FIRST=N &gt; 0: in the hold's state that search also comes before the touch rule's
     * top-up of an own tower and upgrade of an own mine (0: the touch rule first, as v13); N = 1 always, N &gt;= 2 only
     * while fewer than N own towers cover her.
     * KITE_OVERLAP=W &gt; 0: the kiting adds W for every own tower covering a step's end (and the place she stands on).
     * IDLE_OVERLAP=1: the idle queen (the ladder complete or held) goes to the own tower with the most own towers
     * covering its centre instead of the one nearest the home corner (a tie: nearer the corner; a tower inside an enemy
     * tower's range only when no other stands).
     * HOLD_OVERLAP=300 and IDLE_OVERLAP=1 by default since v14 (2026-11-10: P2 on PV3 screen block 7, S 0.5473 against
     * v13, field +1.6 pp, rival +2.75 pp; the reach is a plateau from 300 up on the development seeds; the user's
     * decision); 0 in v01-v13. KITE_OVERLAP loses (0.33-0.47 against v13) and OVERLAP_FIRST adds nothing: off.
     */
    public int HOLD_OVERLAP = 300;
    public int OVERLAP_FIRST = 0;
    public int KITE_OVERLAP = 0;
    public int IDLE_OVERLAP = 1;
    /**
     * PUMP_LOW=H &gt; 0: the idle queen goes to her weakest own tower (outside the enemy towers' ranges) when it has less
     * than H HP, where the touch rule tops it up to TOWER_HP, instead of pumping the idle tower (0: off, as v01-v13). Same
     * study, section 5: 42.5 of v13's turns a game top up a tower with 790 HP or more, which adds 10 HP or less, while
     * another own tower below 600 HP stands in 89 % of them.
     */
    public int PUMP_LOW = 0;
    /**
     * CLEAR_HP=K &gt; 0: LADDER_CLEAR does not count enemy knights with K HP or less (0: every knight, as v13). Same study,
     * section 2: after a turn of the hold with no knight above 11 HP within 500 the queen loses 0.02-0.03 HP in the next 8
     * turns (0.72 with one of 16 HP or more), on 6.0 of the hold's 30.1 turns a game.
     */
    public int CLEAR_HP = 0;
    /**
     * OVERLAP_TOUCH=N &gt; 0: HOLD_OVERLAP takes a spot off the towers (where she stands, or a heading) only when it has
     * more than N covering own towers above the best of the own towers' touch points within reach and the end of v13's
     * walk (0: one more is enough, as v14; 1: two more; 9: never, the touch points only). From the measurement of v14's
     * Gold games (docs/data/study-2026-11-10-v14-cover, section 7): a contact episode begun on open ground cost 3.40 HP
     * against 1.13 for one begun at an own tower, at the same cover. Experiment docs/data/exp-2026-11-10-open-ground:
     * on 2000 development maps 9 scores 0.4909 against v14 and 1 scores 0.4994, the cover on contact turns falls and the
     * HP she loses does not: off.
     */
    public int OVERLAP_TOUCH = 0;
    /**
     * CLEAR_TOUCH=B &gt; 0, a sum of 1, 2 and 4: in LADDER_CLEAR's state (after the first-wave window, two own towers, an
     * enemy knight within LADDER_CLEAR and none within THREAT_RANGE) the hold comes before the touch rule's upgrade of an
     * own mine she touches (1), its new mine or barracks on a free site she touches (2) and its new tower there (4); 0:
     * the touch rule first, as v13 and v14. The hold then acts as if she touched nothing; when it leaves her standing
     * where she is, or finds no own tower to go to, the touch rule's build stands. The top-up of a touched tower is not
     * changed. From the open-ground experiment (docs/data/exp-2026-11-10-open-ground): the contact episodes that begin
     * at a mine upgrade or a new structure cost 3.1 and 7.5 HP each in the mirror against 0.44 at an own tower, 1.0 HP a
     * game together, and 1.4 HP a game on the arena. Experiment docs/data/exp-2026-11-10-gate-touch, 2000 development
     * maps: 1 scores 0.5034 against v14 (the queen loses 0.4-0.7 HP a game less, 0.5-0.8 mine upgrades a game are lost and
     * our knights do as much less damage), 2 is level, 4 loses (0.4904: the towers stand later); no value passes the
     * bar: off.
     */
    public int CLEAR_TOUCH = 0;
    /**
     * The towers' sites chosen for overlap (0: off, as v01-v14). v14 stands where her towers overlap; the ladder that
     * builds them took the free site nearest its point (the midpoint of the walker and home, or WAVE_ANCHOR's point)
     * whatever towers stood. Both flags act on the ladder's tower-phase target only, once an own tower stands or is
     * planned; the first tower, the mines, the barracks, the touch rule and the defence rule's towers are unchanged.
     * SITE_COVER=W &gt; 0: the target is the free site with the smallest distance to the ladder's point less W for each
     * own tower whose range covers the site's centre, up to two (a tower being built counts with its range at TOWER_HP).
     * SITE_NEAR=P &gt; 0: the smallest distance to the ladder's point plus P % of the distance to the second-nearest own
     * tower (the nearest while one stands). Experiment docs/data/exp-2026-11-10-tower-sites: on 2000 development maps
     * every value scores 0.537-0.556 against v14 and +1.5 to +5.6 pp against the rival R0 (the heavier the weight, the
     * lower against v14 and the higher against R0); the queen stands under three or more own towers on 8-13 points more
     * of her contact turns and loses 0.7-2.3 HP a game less to knights from turn 50, and the towers stand no later.
     * SITE_NEAR=50 by default since v15 (2026-11-10: P2 on PV3 screen block 8, S 0.5425 against v14, field +0.8 pp,
     * rival +3.2 pp; the user's decision); 0 in v01-v14. SITE_COVER is the same effect by another measure and was not
     * screened: off.
     */
    public int SITE_COVER = 0;
    public int SITE_NEAR = 50;
    public long SEED = 1;
    public int DEBUG = 0;

    public Params copy() {
        Params p = new Params();
        p.apply(toString());
        return p;
    }

    public Params apply(String spec) {
        if (spec == null || spec.isBlank()) return this;
        for (String kv : spec.split(",")) {
            if (kv.isBlank()) continue;
            int eq = kv.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("bad param " + kv);
            String k = kv.substring(0, eq).trim(), v = kv.substring(eq + 1).trim();
            try {
                Field f = Params.class.getField(k);
                if (f.getType() == int.class) f.setInt(this, Integer.parseInt(v));
                else if (f.getType() == long.class) f.setLong(this, Long.parseLong(v));
                else if (f.getType() == double.class) f.setDouble(this, Double.parseDouble(v));
                else if (f.getType() == boolean.class) f.setBoolean(this, Boolean.parseBoolean(v));
                else throw new IllegalArgumentException("unsupported type " + k);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalArgumentException("unknown param " + k, e);
            }
        }
        return this;
    }

    /** Full effective configuration "K=v,..." in field order. */
    @Override
    public String toString() {
        List<String> r = new ArrayList<>();
        for (Field f : Params.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try { r.add(f.getName() + "=" + f.get(this)); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        return String.join(",", r);
    }
}
