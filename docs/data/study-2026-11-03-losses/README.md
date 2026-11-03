# Study 2026-11-03: how v04 loses, and what the opening, the economy and the towers look like against stronger players

**Question.** v04 (the `route` configuration, ROUTE=1 over v03) scored 22.89 and 22.10 in two Bronze
placements, 4.6 points below the boss. The movement branch is near its end (1-2 walking turns per game
left, about 0.014 S each). Which part of the strategy loses games, and what do stronger players do that
we do not? Two hypotheses from the user: build a mine first (no creep threat on the first turns, income
always pays), and do not pump towers to 700 HP.

## Data and tool

- **Tool:** `./gradlew cgStudy -Pout=<csv> -Preplays="<dirs or files>"` (`cr.tools.CgStudy`), extended
  on 2026-11-03 with the queen's death turn, the first tower lost, BUILDs on own towers and mines
  (top-ups and upgrades), the opening (the first three structures, `BK M T` = knight barracks, mine,
  tower) and the summed HP of own towers at the fixed turns (`thp_<t>`). The older columns are unchanged.
  `CgStudyTest` pins the new columns on game 915331914 (skipped without the replay). From Git Bash the
  Windows absolute paths in `-Preplays` are mangled by MSYS path conversion: use relative paths.
- **Corpus** (526 games, 1052 rows): v04's 120 placement games (`route` uploads 1-2, agents 6836411 and
  6836589), v03's 180 (uploads 1-3), `replays/cg2` (58 Bronze, 38 Silver, 42 Gold; opponents' ratings
  from `docs/data/replays-cg2-2026-10-28.json`) and `replays/cg/legend-top` (88).
- **Command** (from the worktree, the main checkout's corpora by relative path):
  `./gradlew cgStudy -Pout=replays/study/features-2026-11-03.csv "-Preplays=replays/arena/route/bronze/upload-01/games
  replays/arena/route/bronze/upload-02/games ../../../replays/arena/v03/bronze/upload-01/games … ../../../replays/cg2
  ../../../replays/cg/legend-top"`. Output kept as `replays/study/features-2026-11-03.csv` (gitignored,
  sha256 `dd108fba576d50e1…`), copied to the main checkout.
- **Script:** `losses.py` (output `losses.txt` beside it; run from the repository root).
- Groups: `route` (v04), `v03`, the opponents who beat v04 (41 rows) and who lost to it (79),
  `cg2-bronze` (all Bronze rows of cg2), `cg2-bronze-24+` (Bronze rows with rating ≥ 24, 24 rows),
  `silver-gold` (160) and `legend` (176).

## Findings

**1. How v04's 120 games end.** 79 wins: 50 by killing the opponent's queen (median turn 104), 26 on
HP at the 250-turn limit, 3 referee removals. 41 losses: 26 by our queen's death (median turn 114,
quartiles 77-161; 7 before turn 80) and 15 on HP at the limit (our HP below theirs by a median of
about 20). Losses by seat: 18 of 61 on seat 0, 23 of 59 on seat 1.

**2. Opponent style.** Knights only: 98 games, we win 64 %. Knights and giants: 12 games, 58 %; with
archers: 5 games, 80 %; all three: 3 games, won. Against giants we lose 3 structures (median) with the
first tower lost on turn 105, against the others 2 and turn 210. Giants cost a few points, not the
bulk.

**3. Our profile against every stronger group** (medians; `losses.txt`, sections 4, 5 and 7):

| group | n | opening | first barracks / mine / tower | income t50 / t100 | mines t100 | towers t100 | tower HP t100 | top-ups per game (per 100 turns) | mine upgrades | knight waves | mean banked gold |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| route (v04) | 120 | BK M M 58 %, BK M T 42 % | 1 / 4 / 13 | 3 / 3 | 2 | 6 | 3,592 | 79 (57) | 4 | 7 | 43 |
| v03 | 180 | BK M M 67 %, BK M T 33 % | 1 / 4 / 15 | 3 / 3 | 2 | 5 | | 77 | 3 | 6 | 43 |
| opponents who beat v04 | 41 | M first 80 % | 10 / 1 / 14 | 4 / 4 | 3 | 4 | 1,596 | 45 (39) | 6 | 9 | 61 |
| opponents who lost to v04 | 79 | M first 66 % | 10 / 1 / 17 | 5 / 4 | 3 | 3 | | 24 | 4 | 7 | 55 |
| cg2 Bronze, rating ≥ 24 | 24 | M first 71 % | 7 / 1 / 14.5 | 4.5 / 4 | 2 | 4 | 1,832 | 34 (30) | 5 | 6 | 51 |
| Silver and Gold | 160 | M first 81 % | 11 / 1 / 17 | 4 / 5 | 3 | 4 | 1,942 | 34 (29) | 5 | 7 | 58 |
| Legend | 176 | M first 89 %, M M BK 47 % | 13 / 1 / 17 | 7 / 6 | 3 | 4 | 1,826 | 29 (23) | 8 | 10 | 82 |

- **Opening.** We build the knight barracks on turn 1 in 99 % of games, the first mine on turn 4. Every
  stronger group, and 80 % of the opponents who beat us, build a mine first (turn 1) and the barracks
  on turn 7-13. The first tower comes at turn 13-17 everywhere, so v03's early towers closed that gap.
- **Income.** Ours stays at 3 for the whole game (`INCOME_TARGET=3` and 2 mines; 4 mine upgrades per
  game). The opponents who beat us hold 4, Silver/Gold 4-5, Legend 6-7 with 3 mines and 8 upgrades.
- **Towers.** We hold 6 towers at turn 100 with 3,600 summed HP (about 700 per tower, our top-up
  threshold), Legend 4 towers with 1,800 (about 450 per tower). The pumping costs queen turns: 79
  BUILD-on-own-tower commands per game, 57 per 100 turns, against 23-30 for the stronger groups. More
  than half of our queen's turns go to top-ups; the opponents who beat us spend 39 per 100 turns.
- **Army.** 7 knight waves per game against 9 for the opponents who beat us and 10 for Legend; 0 of
  our knights alive at turn 50 (median) against 3-4 for the Bronze and Silver/Gold groups. Our banked
  gold is lower (43 against 51-82), so the gap is income, not hoarding.

**4. Top-ups and results.** Tower HP per tower at turn 50 is 693 in our wins and 715 in our losses; we
top up 78 times in wins and 87 in losses. The data shows no benefit from the 700 threshold; it shows
the cost (section 3). Our towers do protect us: with 4 or more towers at turn 50 we win 74 %, with 2-3
about 61 %. The opponent's early army feeds them: with 8+ enemy knights alive at turn 50 we win 88 %,
with 4-7 74 %, with none 54 % (`losses.txt`, section 6). The opponents who beat us are the economic
ones: no knights at turn 50, 4 towers, income 4, 9 waves over a longer game (171 turns).

**5. Kills.** Our 50 kills come with 4 waves trained and 2 knights alive at turn 50; the opponent's
queen is already at 45 HP by turn 25 and 40 by turn 50 (they walk into our towers, and knights finish
her at turn 104). Our 26 deaths come against 8.5 enemy waves (giants in 3 games): our HP 62 → 43 → 34
at turns 25/50/75 with 3 towers at turn 50. The 7 early deaths (before turn 80) start with 1 tower at
turn 25 and 30 HP, against a barracks built on turn 16.

## Conclusions

1. **The strategy is spending the queen on towers, not on the economy or the army.** The ladder
   "barracks on turn 1, mines to income 3, eight towers, then pump the nearest tower to 700 HP forever"
   leaves income at 3 and the queen busy with top-ups for most of the game. Stronger players hold the
   same 3-4 towers at 450 HP, twice the income and more waves.
2. **The user's first hypothesis holds in every group:** a mine first, the barracks about ten turns
   later. The opponents who beat us do exactly that.
3. **The second one too:** the 700 HP top-up shows no benefit in our results and costs about a third of
   the queen's turns; a cap near 4-5 towers and a threshold near 400-500 would free 30-40 turns per
   game for mines, upgrades and walking.
4. The losses are of two kinds: a killed queen against an army we did not match (26), and an HP race
   lost by about 20 HP at the limit (15). Both point at the same economy gap.

## Candidates for P2 (parameters and small rules, screened against v04)

1. **Opening order:** mine first, barracks when gold allows after the first mine (turn 5-10); a flag
   with the barracks turn or "after N mines".
2. **Income target** 5-6 instead of 3, and mine upgrades on the way (the touch rule already upgrades a
   mine below its max; the ladder decides when the queen goes to mines).
3. **Tower cap** 4-5 and **top-up threshold** 400-500 instead of 8 and 700; the freed turns go to the
   ladder (mines) and to walking.
4. Later: a second barracks or saving for two waves (Legend keeps 2 barracks at turn 100), queen HP
   race at the limit.

Items 1-3 are parameter changes with a dev sweep on seeds 10000-10499 and a P2 strategy screen on a
fresh block; the local opponents are knights-only (v04, `boss`), so the arena effect of a thinner
tower line against giants (12 % of opponents) is not measured locally and needs the P1 placements.

## What this study cannot say

- Correlations in recorded games: income and waves go with winning, but a game where we are pressed
  early also has no time for mines. The sweep and the screen decide.
- The groups differ in league, map pool and opponent strength; the Legend numbers are a direction, not
  a target for Bronze.
- Tower damage to the enemy queen is not separated from knight damage; the kill analysis infers it
  from HP at turns 25 and 50.
