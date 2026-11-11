# Study 2026-11-11: what the tower-sites rule did on the arena (v15's 180 Gold games beside v14's)

## Question

v15 = v14 with `SITE_NEAR=50` (the ladder builds each tower, from the second on, beside the towers that stand;
`docs/data/exp-2026-11-10-tower-sites/`) was selected under P1 on 2026-11-11: placements 21.87 and 21.05 against v14's
23.11 and 22.10, "not clearly worse". The records said that the arena did not show the local gain (S 0.5510 against
v14) and that what the rule did there was not measured. The user's "Да, измерь": measure it, with the probe of the v14
measurement on v15's games beside v14's. No bot code was changed and no P2 block was used.

## Data

- **Games:** v15's two Gold placements (agents 6871160 and 6871202, artifact `7a5b28c4`, cgAttribute SAME 180/180) and
  v14's two (6868933 and 6869279, artifact `e4c8eeec`). Replays in the main checkout's
  `replays/arena/v15/gold/upload-0{1,2}/games` and `replays/arena/v14/gold/upload-0{1,2}/games`; manifests
  `docs/data/arena-<agent>-games-<date>.json`.
- **The probe:** `docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java`, unchanged, run by `probe.sh` (beside
  this file; from the repository root after `./gradlew compileJava`, nothing else running; 2026-11-11 01:58). Its
  second run, on v14's games, is the control: the three files are byte for byte those of the v14 study
  (`docs/data/study-2026-11-10-v14-cover/`), so both versions are traced by the same code.

  | file (gitignored; `replays/study/gold-v15-2026-11-11/`, copied to the main checkout) | sha256 |
  | --- | --- |
  | probe/turns.csv (79,336 rows) | `a6baf2e93589144f1b70cfbfa1811501c0a76f2fcc1f054dba994df542378279` |
  | probe/events.csv | `d31c8eb510e53cc87912cbb8b332895632f9eb5552c5ad612b6f305ec5f1c05f` |
  | probe/waves.csv | `6725f5eb2e87c7922b215be62c0799e5c673c1d29f4f11ec59b39fbe3e6e8999` |
  | probe-v14/turns.csv, events.csv, waves.csv | `7a0e612a…f69f944`, `dc0f7f19…c21ceb2`, `77adafc6…7c784` (the v14 study's) |
  | as-v14-upload-01.txt, as-v14-upload-02.txt | `51ea1d95…60255617`, `5a49815d…dbdba45` (they hold Gradle's timing line, so a rerun differs in it) |
- **v14's answers on v15's positions:** `./gradlew -q cgAttribute -Preplays=<R>/arena/v15/gold/upload-01/games
  -Pagent=6871160 -Pbot=v14` and the same for upload 2 (agent 6871202), both in `probe.sh`. Exit code 1 is expected:
  the games are not v14's.
- **The local profiles** beside which the arena is read: the experiment's, 400 games each of v14 and of
  `SITE_NEAR=50` against v14, `rival` and `field` on the same 200 maps (its README, Profiles; the csv files are in
  the worktree's `replays/bench/exp-2026-11-10-tower-sites/profiles` only).
- **Scripts** (from the repository root, `python -B -I docs/data/study-2026-11-11-v15-sites/<script>`):
  `footprint.py <as-v14-upload-01.txt> <as-v14-upload-02.txt>` → `footprint.txt`; `measure.py` → `measure.txt` (the
  measures with a bootstrap over games, the bands, the shared opponents, the rating model); `decompose.py` →
  `decompose.txt` (the contact turns by class, the arena beside the local profiles). They use the readers and the
  definitions of the v13 and v14 studies (a contact turn, the gate's state, the cover, an episode) and the
  experiment's definition of a tower's links at birth; each script's header has them.
- Revision: commit a55fa1e with the upload records uncommitted (docs only; no source file differs). Java 17.0.12,
  Gradle 8.11.1, Windows 11.

## Findings

### 1. The rule acts in nine games of ten, early and on few turns (`footprint.txt`)

On v15's recorded positions v14 would have given another command in **161 of the 180 games**, on 1,015 of 39,668
turns (2.6 %): 6 turns in a game with a difference (median; quartiles 3 and 9, the most 25). The first difference
comes at turn 31 (median; quartiles 18 and 42), before turn 50 in 131 of the 161 games. It is always the queen's
line: a tower on another site (26 games), or the walk to a tower's site aimed elsewhere (a MOVE to another point 70,
a MOVE where v14 has BUILD TOWER 38, a BUILD TOWER where v14 moves 23). The earliest are at turns 7-11, on the walk
from the barracks to the first tower's site: the approach planner picks the touch point of the tower being built by
the site that comes after it, and the rule already chooses that next site (`ApproachPlanner.nextTarget` counts the
tower as standing).

For scale: v14's own rule (the hold where the towers overlap) differed from v13 on 15 % of the turns, first at turn
81. This rule moves a few decisions early in the game; the game after them is another game, which the count of
different turns does not show.

### 2. The towers stand where the rule puts them, at the local size (`measure.txt`, section 2)

Differences are v15 less v14 with the 95 % interval of a bootstrap over games; the local column is the arm less v14
on the same maps against v14 / `rival` / `field`.

| | arena, v14 → v15 | difference | local |
| --- | --- | --- | --- |
| own towers within 400 of a new tower when it is built, from the second on | 1.20 → 1.35 | **+0.15 (+0.08..+0.22)** | +0.12 / +0.18 / +0.17 |
| ... the third tower | 1.13 → 1.55 | **+0.43 (+0.29..+0.55)** | +0.42 / +0.46 / +0.39 |
| the third tower's distance to the second-nearest own tower | 506 → 390 | −116 (−148..−84) | |
| towers from the second on built inside another own tower's range | 87.1 % → 92.6 % | +5.5 (+0.9..+10.1) | |
| how far forward the third tower stands, % of the way to the enemy corner | 28.0 → 30.1 | +2.1 (+0.7..+3.6) | |
| the turn the third tower is built | 50.8 → 50.7 | −0.1 (−5.2..+5.1) | |
| the turn the fourth tower is built | 75.2 → 69.5 | −5.7 (−12.5..+0.9) | |
| own towers at turn 75 / 100 / 150 | 3.57 / 3.94 / 4.51 → 3.71 / 3.98 / 4.60 | +0.13 / +0.04 / +0.09, no interval off zero | +0.10..+0.19 at 100 and 150 |
| games with the second barracks | 66.7 % → 72.2 % | +5.6 (−3.9..+15.0) | +2.5 / +5.2 / +6.8 |
| turns on a walk to a new site, a game | 37.8 → 37.4 | −0.4 (−3.5..+2.6) | −0.4 / −1.1 / +0.7 |
| income at turn 100 / 150 | 2.75 / 2.87 → 2.85 / 3.01 | +0.10 / +0.15, no interval off zero | +0.05..+0.14 at 150 |

- **The site choice is reproduced to the second digit:** the third tower is built with 1.55 own towers within 400
  (locally 1.50-1.59), 116 closer to its second neighbour. Both placements show it (1.47 and 1.64 against v14's 1.17
  and 1.08).
- **The ladder does not pay for it on the arena either:** the third tower comes on the same turn, the fourth and the
  second barracks no later, the walks take as many turns, the income is not lower. No interval points to a cost.

### 3. The queen: under three sooner, four in place of three, and no more contact turns under three or more (`measure.txt` sections 2-3, `decompose.txt`)

| | arena, v14 → v15 | difference | local |
| --- | --- | --- | --- |
| games in which she stands under three or more by turn 60 | 61.7 % → 71.7 % | **+10.0 (+0.6..+19.4)** | +17.5 / +16.5 / +11.0 |
| turns 50-99 with a spot under three or more within 300 of her | 80.4 % → 84.4 % | +4.0 (−2.5..+10.7) | +8.5 / +8.0 / +7.2 |
| contact turns from turn 50 under three or more | 53.7 % → 53.5 % | **−0.3 (−9.2..+8.5)** | **+13.2 / +7.8 / +12.1** |
| ... under four or more | 21.3 % → 26.8 % | +5.6 (−0.4..+11.4) | |
| contact turns under one or two own towers, a game | 15.4 → 15.1 | −0.3 (−4.4..+4.1) | −3.9 / −3.8 / −4.4 |
| the gate's state (a knight within 500, none within 250): three or more | 71.0 % → 74.5 % | +3.5 (−3.7..+10.3) | |

The share of the contact turns under three or more did not move. The mirror's shift (+9.1..+17.3) lies wholly above
the arena's interval, `field`'s point value above it and `rival`'s at its edge. Where the turns went, by class
(`decompose.py`: *no line* = two own towers or fewer stand; *off the line* = three stand, two or fewer cover her, a
spot under three lies within 300; *no spot* = the same with no such spot):

| contact turns from turn 50, a game | arena v14 | arena v15 | difference | HP to knights there, v14 → v15 | local change in turns |
| --- | --- | --- | --- | --- | --- |
| no line | 6.9 | 8.1 | +1.3 (−2.2..+4.7) | 4.9 → 5.1 | −1.1 / −1.7 / −2.2 |
| off the line | 6.6 | 6.0 | −0.6 (−2.5..+1.3) | 2.6 → 3.3 | −1.8 / −0.7 / −1.1 |
| no spot | 2.1 | 1.1 | −1.0 (−2.1..+0.1) | **2.6 → 1.0, −1.6 (−3.2..−0.0)** | −1.0 / −1.4 / −1.1 |
| under exactly three | 10.9 | 8.8 | −2.2 (−4.4..+0.0) | 1.7 → 1.4 | −0.5 / −0.6 / −1.7 |
| under four or more | 7.2 | 8.8 | +1.6 (−0.2..+3.4) | 0.8 → 0.7 | +1.8 / +1.7 / +2.9 |

- **What the rule changes directly shows at the local size:** the turns with three towers standing and no covered
  spot within reach halve (2.1 → 1.1 a game; locally −1.0 to −1.4), and the turns under four or more rise by 1.6
  (locally +1.7 to +2.9). With three own towers standing the share under three or more is 67.5 % → 71.1 %
  (+3.6, −3.6..+10.8).
- **Two things take the shift away.** The turns under exactly three fell by 2.2 a game where locally they fell by
  0.5-1.7: on the arena the rule mostly turned three into four. And the turns with no line did not fall as they did
  locally: 6.9 → 8.1 a game, in 33 % and 30 % of the games. Locally the rule also brought the line sooner (three
  towers stand by turn 60 in 80 % → 90 %, 78 % → 89 %, 84 % → 92 % of the games); on the arena 75.6 % → 80.0 %
  (+4.4, −3.9..+12.8). Whether the no-line turns differ by the opponents met or by chance cannot be told from 180
  games; the rule cannot act while two towers or fewer stand.
- **The arena's contact turns are not the local ones.** With no line the queen loses 4.9-5.1 HP a game on the arena:
  two fifths of all she loses from turn 50. In the mirror that class costs 0.3 HP a game and against `field` 0.2;
  only `rival` has it (2.8).

### 4. The HP and the deaths: the direction of the prediction, not established (`measure.txt` sections 2-4)

- HP lost to knights from turn 50: **12.6 → 11.5 a game, −1.1 (95 % interval −5.4..+3.3)**; locally −2.3 / −1.7 /
  −0.7. The placements overlap (v14 11.0 and 14.2, v15 12.8 and 10.1). The only HP interval off zero is the class the
  rule removes: 2.6 → 1.0 with no spot.
- What a contact turn costs under a given cover is as before: 1.23 / 0.44 / 0.15 / 0.11 HP under 1 / 2 / 3 / 4+
  towers for v14, 1.04 / 0.45 / 0.16 / 0.08 for v15.
- Before turn 50: 7.2 → 8.0 (+0.9, −1.2..+2.8); locally the first wave cost the same (+0.1 / −0.2 / +0.1).
- Queen deaths: 26 → 25 of 180. In v15's deaths she has 2.8 own towers on her contact turns and 2.00 cover her (v14:
  2.8 and 1.83); a death costs 46.8 HP on contact turns from turn 50, 38.8 of them under one or two towers.
- The opponents' queens lose the same to our knights from turn 50 (12.9 and 12.9).

### 5. Like for like: the opponent bands and the shared opponents (`measure.txt` sections 1, 5, 5b)

| opponents rated | v14: games, won, deaths, lost races | v15: games, won, deaths, lost races | the third tower's links, v14 → v15 | three or more on contact turns | our HP to knights from turn 50 |
| --- | --- | --- | --- | --- | --- |
| below 17 | 32, 78 %, 3, 4 | 48, 77 %, 7, 4 | 1.04 → 1.66 | 44 % → 50 % | 14.1 → 13.3 |
| 17-20 | 45, 82 %, 3, 5 | 67, 70 %, 6, 14 | 1.09 → 1.53 | 60 % → 56 % | 8.7 → 9.3 |
| 20-22 | 62, 65 %, 14, 8 | 53, 60 %, 8, 13 | 1.18 → 1.56 | 52 % → 61 % | 15.8 → 10.8 |
| 22 and up | 41, 44 %, 6, 17 | 12, 42 %, 4, 3 | 1.16 → 1.25 | 57 % → 34 % | 10.9 → 19.1 |

- The links rise in every band (the intervals are off zero in the first three). No other difference in any band has
  an interval off zero, and the signs alternate: the cover and the HP are better at 20-22 and below 17, worse at
  17-20 and at 22 and up (12 games).
- The 53 opponents both versions met (95 and 89 games, medians 21.00 and 20.36): v14 won 69 %, v15 64 %
  (−5.4, −19.3..+7.7); the third tower's links 1.13 → 1.51; three or more on contact turns 55.7 % → 52.8 %
  (−2.8, −15.7..+10.1); HP from turn 50 10.5 → 11.2 (+0.7, −4.9..+6.3). Nothing is off zero but the links.

### 6. The results (`measure.txt` sections 1, 6, 8)

- v14: 120 won, 60 lost (34 races, 26 deaths). v15: 121 won, 59 lost (34 races, 25 deaths), against a weaker field:
  opponents' median 19.21 against 20.45, 12 games against 22 and up (41) and 48 below 17 (32).
- **Against the rating model** (the logistic curve at Gold's scale 3.25): at v15's own rating 21.31 the bands are as
  expected from 17 up (+0.2, +1.0, 0.0 SD of the expected count) and short below 17 (37 of 48 where 41.4 are
  expected, −1.9 SD). At v14's rating 22.48 the field v15 met expects 133.1 wins; v15 won 121 (−2.2 SD of the count,
  which is smaller than the spread between placements that P1's margin measures).
- **Below 17 the shortfall is upload 2's again:** 28 of 37 (−2.0 SD; upload 1: 9 of 11). v14 had 25 of 32 over its
  two uploads (−2.2 SD), 21 of 28 of them in upload 2; v12's upload 2 had 21 of 29. v15's eleven losses there: seven
  deaths and four lost races. In six of the seven deaths the opponent out-earns us at turn 100 (income 3, 5, 10, 10,
  9, 8 against our 1, 2, 4, 2, 3, 0); three are against one player (FLPMLN, met by no earlier version). Two of the
  four races have no contact turn after turn 50: they were lost in the first wave. v14 would have played two of the
  eleven turn for turn and differs on 1 to 8 turns in the others (`footprint.txt`).
- **The lost races are decided early, in both versions:** in 22 of v15's 34 lost races (22 of v14's 34) our queen has
  lost more HP to knights than theirs by turn 50, and she loses the race; over the 34 it is 13.5 HP against 7.1 by
  then, in the won races 5.3 against 13.2. 19 of v15's lost races were lost by 7 HP or less.

### 7. Where the HP goes on the arena

v15's queen loses 19.5 HP to knights a game:

| | HP a game | share |
| --- | --- | --- |
| before turn 50 (the first wave) | 8.0 | 41 % |
| from turn 50 with two own towers or fewer standing (no line) | 5.1 | 26 % |
| from turn 50 with three or more standing: off the line 3.3, no spot 1.0, under three 1.4, under four 0.7 | 6.4 | 33 % |

The hold (v14) and the sites (v15) act on the last row only, and the sites' own class in it is down to 1.0.

## What it says about the next step

No decision is taken here; these are the measured openings.
- **The rule does on the arena what it does locally to the board:** the sites, the earlier stand under three, the
  halved turns without a covered spot, the pace of the ladder. Nothing in the games shows it costing.
- **Its effect on the result is below what two placements resolve.** The mirror's S 0.5510 is at most +0.67 of rating
  at Gold's scale; against `field` and `rival` the local gain was +0.4 and +2.5 points of score. The arena's
  differences are −1.14 (placement mean) and −1.17 ± 0.77 (performance), inside P1's margin. The games give no
  mechanism for a loss of that size and none for a gain: the cover on contact turns, the HP, the deaths and the
  share of wins band for band all have intervals around zero.
- **Two thirds of the knights' damage is outside the reach of both cover rules:** the first 50 turns (8.0 HP a game;
  two lost races of three are behind at turn 50) and the turns with no line (5.1 HP a game, in three games of ten).
  That is the first wave and the economy under pressure: the v13 study's sections 5, 7 and 8 (the dead turns, a rival
  whose barracks stand forward) and the opponents with twice our income in the deaths of section 6.
- A heavier weight of the same rule (`SITE_NEAR=100`, or both measures) raised the local cover further; on the arena
  the class it acts on holds 1.0 HP a game, which bounds what it can add there.
- On average the first 50 turns are ours (our queen loses 8.0 HP to knights, theirs 14.6); they are also where the
  lost races are lost (13.5 against 7.1). The first wave is the largest single part of our losses and the most
  uneven one.

## Limits

- The two versions met different fields (opponents' medians 19.21 and 20.45; v15 met 12 opponents at 22 and up,
  v14 41). The band table is like for like on 12 to 67 games a cell; the shared opponents are pooled, not weighted.
- The intervals resample games within a version. They do not hold the difference between placements that P1's noise
  margin measures; with two placements a version that cannot be estimated here.
- The opponents' ratings are those at the fetch; an opponent in placement is rated below its strength.
- The footprint counts v14's answers on v15's positions, not a game v14 would have played; a tower built elsewhere
  changes every later position.
- The local profiles are 400 games against three opponents; the arena is 180 games against 100 and more. The
  classes of section 3 are read from the probe's columns (own towers standing, towers covering her, the best cover
  within 300), not from a replay of the rule.
