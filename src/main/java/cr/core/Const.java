package cr.core;

/** Game constants: csj/code-royale@349924a Constants.kt, with live values where they differ. */
public final class Const {
    private Const() {}

    public static final int WIDTH = 1920, HEIGHT = 1000;
    /** Live CG value (statement and replays); the referee repository says 200. */
    public static final int MAX_TURNS = 250;

    public static final int STARTING_GOLD = 100;
    public static final int QUEEN_SPEED = 60, QUEEN_RADIUS = 30, QUEEN_MASS = 10000, QUEEN_VISION = 300;
    /* live-tunable */ public static int TOUCHING_DELTA = 5;

    /* live-tunable */ public static int TOWER_HP_INITIAL = 200, TOWER_HP_INCREMENT = 100, TOWER_HP_MAXIMUM = 800;
    /* live-tunable */ public static int TOWER_CREEP_DAMAGE_MIN = 3, TOWER_CREEP_DAMAGE_CLIMB_DISTANCE = 200;
    /* live-tunable */ public static int TOWER_QUEEN_DAMAGE_MIN = 1, TOWER_QUEEN_DAMAGE_CLIMB_DISTANCE = 200;
    /* live-tunable */ public static int TOWER_MELT_RATE = 4, TOWER_COVERAGE_PER_HP = 1000;
    /* live-tunable */ public static int GIANT_BUST_RATE = 80;

    public static final int OBSTACLE_GAP = 90;
    public static final int OBSTACLE_GOLD_INCREASE = 50;
    public static final int OBSTACLE_GOLD_INCREASE_DISTANCE_1 = 500, OBSTACLE_GOLD_INCREASE_DISTANCE_2 = 200;
    public static final int OBSTACLE_PAIRS_MAX = 12;

    /* live-tunable */ public static int KNIGHT_DAMAGE = 1, ARCHER_DAMAGE = 2, ARCHER_DAMAGE_TO_GIANTS = 10;
    /**
     * Live only (not in the repository): TRAIN is refused with "Training too many creeps (N total
     * units requested; 30 maximum)" when the player's living creeps plus the requested ones exceed
     * this. Creeps still in training do not count; the gold check comes first (22 warnings in 236
     * replays, 2026-10-28).
     */
    /* live-tunable */ public static int CREEP_LIMIT = 30;
    public static final int WOOD_FIXED_INCOME = 10;

    // Structure types as sent to the bots.
    public static final int NONE = -1, MINE = 0, TOWER = 1, BARRACKS = 2;
    // Creep types (ordinal order of the referee enum).
    public static final int KNIGHT = 0, ARCHER = 1, GIANT = 2;
    public static final String[] CREEP_NAME = {"KNIGHT", "ARCHER", "GIANT"};
    public static int[] CREEP_COUNT = {4, 2, 1};
    public static int[] CREEP_COST = {80, 100, 140};
    public static int[] CREEP_SPEED = {100, 75, 50};
    public static int[] CREEP_RANGE = {0, 200, 0};
    public static int[] CREEP_RADIUS = {20, 25, 40};
    public static int[] CREEP_MASS = {400, 900, 2000};
    /** Live: knights spawn with 25 HP (8600 of 8600 replay spawns, 2026-10-28); repository says 30. */
    public static int[] CREEP_HP = {25, 45, 200};
    public static int[] CREEP_BUILD_TIME = {5, 8, 10};
}
