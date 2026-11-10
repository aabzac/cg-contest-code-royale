# Study 2026-11-09: v13 in Gold — what the gate changed, the loss anatomy, and the next step

## Question

The user asked again for an analysis and the single most promising next step ("Можешь сделать анализ и предложить
следующий наиболее перспективный шаг по улучшению бота?"). v13 is the selected version in Gold (placements 21.95 and
21.65, mean 21.80; v12's 20.36). The analysis rests on v13's own 180 Gold games beside v12's 180, replayed through
`cr.core.Rules`, on the same traces of local games against the P2 panel, and on thirteen exploratory arms of existing
flags over v13. No bot code was changed.

## Data

- **Games:** v13's two Gold placements (agents 6868408 and 6868474, artifact `85897d90`, cgAttribute SAME 180/180) and
  v12's two (6860036 and 6860166). Replays in the main checkout's `replays/arena/v13/gold/upload-0{1,2}/games` and
  `replays/arena/v12/gold/upload-0{1,2}/games`; manifests `docs/data/arena-<agent>-games-<date>.json`.
- **Inputs** (gitignored; made in the worktree under `replays/study/gold-v13-2026-11-09/` and copied to the main
  checkout's `replays/study/`; run from the repository root, `<R>` = the main checkout's `replays`, `../../../replays`
  from a worktree):
  ```
  ./gradlew -q cgStudy -Pout=replays/study/gold-v13-2026-11-09/features-v13-gold.csv "-Preplays=<R>/arena/v13/gold/upload-01/games <R>/arena/v13/gold/upload-02/games"
  ./gradlew -q deathTrace -Pagent=6868408 -Pall=1 -Preplays=<R>/arena/v13/gold/upload-01/games > replays/study/gold-v13-2026-11-09/ours-upload-01.txt
  ./gradlew -q deathTrace -Pagent=6868408 -Pall=1 -Pfoe=1 -Preplays=<R>/arena/v13/gold/upload-01/games > replays/study/gold-v13-2026-11-09/opp-upload-01.txt
  ```
  and the same two traces for upload 2 (agent 6868474). v12's features and traces are those of
  `docs/data/study-2026-11-08-next-step/` (`replays/study/gold-v12-2026-11-08/`).
- **The probe** `probes/ArenaProbe.java` (new, outside the build; its header has the build line): both seats of every
  game in one format — the queen's turn with her command's target, the HP lost by source, the tower HPs, the knights near
  her with their HP, the own towers covering her and the best cover within reach (`turns.csv`); structures built and lost
  with the cause (`events.csv`); every barracks release with WaveScan's damage measure (`waves.csv`). Its `local` mode
  traces in-process games the same way.
  ```
  java -cp ... ArenaProbe 6868408,6868474 replays/study/gold-v13-2026-11-09/probe <R>/arena/v13/gold/upload-01/games <R>/arena/v13/gold/upload-02/games
  java -cp ... ArenaProbe 6860036,6860166 replays/study/gold-v13-2026-11-09/probe-v12 <R>/arena/v12/gold/upload-01/games <R>/arena/v12/gold/upload-02/games
  java -cp ... ArenaProbe local v13 <v13|rival|field> 100 1 397 replays/study/gold-v13-2026-11-09/local-v13-<opponent>
  ```

  | file | sha256 |
  | --- | --- |
  | features-v13-gold.csv | `e6354f238f36fa0ebd7d2f976e7212d751a2f623675543390fe86d65902e2dae` |
  | ours-upload-01.txt / 02 | `98612c74d1265902f409627ed0da698ac6ea5a5b3dddaca22fb3f27361ad50bd` / `517eb267c21ee2c15ba8a738b607c3a4c747f594d1efa8f650c5baf9f5665820` |
  | opp-upload-01.txt / 02 | `01fac78a7ea03e0e38b4c7f03bc92534879d15319165c8271358be696fd9a831` / `bbfddaeb66a651e66e1c0efd5c31bca78d30c93d3538b23276450220bf72235b` |
  | probe/turns.csv, events.csv, waves.csv | `b94dd342…bb6a803`, `033cd51d…f22aebca2`, `21791903…ec950cb0` |
  | probe-v12/turns.csv, events.csv, waves.csv | `49d96f34…78f975c`, `060529d6…ef1abb9`, `02086976…7f5c5ad` |
  | local-v13-v13, -rival, -field (turns.csv) | `83beb53a…80ee9a9`, `cfad02e9…0cfc2b9`, `0a39f949…bff90fb` |
- **Scripts and their outputs** (all `python -B -I docs/data/study-2026-11-09-v13-gold/<name>.py`, from the repository
  root; the first three take `--v13 <study dir> --v12 <study dir>`, the others read the probe directories):
  `v13_gold` (the measures of the v12 study for both versions), `deaths` (the deaths one by one, the opponents by
  rating band, giants, the economy's course), `pin` (the ladder under the gate), `probe_tables` (waves, mines, towers,
  the queens' turns, the gate's state), `idle` (top-ups of full towers), `local_vs_arena` (v13 against the panel in the
  arena's terms), `checks` (the ten checks the recommendation rests on); each prints `<name>.txt`.
- **Exploratory arms:** `explore.sh` (development seeds 1 + 397 k, k < 500, both seats; each arm against v13, `rival`
  and `field` with v13's controls), `explore_table.py` → `explore.txt`. Raw reports
  `replays/bench/study-2026-11-09-v13-gold/dev/` (gitignored). Not a P2 screen: no fresh block was used.
- Revision: commit 639c33e, clean but for this directory. Java 17.0.12, Gradle 8.11.1, Windows 11.

No agent workflow ran; every number is from the scripts above.

## Findings

### 1. Where v13 stands (`v13_gold.txt`, `deaths.txt`)

- 180 games: won 107 (78 HP races at the limit, 28 kills, 1 referee), lost 73 (39 races, 34 deaths). The field was
  stronger than v12's: the opponents' median rating 20.90 against 19.69; 122 games against players rated 20 or more
  (v12: 58), 45 against 22 or more (v12: 2).
- By the opponent's rating: below 20: 75 % (57 games); 20-21: 59 % (37); 21-22: 57 % (40); 22-23: 38 % (34); 23 and up:
  45 % (11). **Against the 122 rated 20 or more: 42 won races, 21 kills, 28 lost races, 31 deaths.** The deaths are now
  the larger loss class at the top of the field (v12: 24 lost races, 12 deaths in 58 games).
- Seat: 65 % on seat 0 (80 games, 15 deaths), 55 % on seat 1 (100 games, 19 deaths): the engine's asymmetry, as before.

### 2. What the gate (LADDER_CLEAR) did on the arena (`v13_gold.txt` section 4, `checks.txt` section 7, `pin.txt`)

- **The mechanism works:** midgame contact episodes begun on a walk fell from 3.9 to 0.7 a game, their cost from 9.5 to
  3.9 HP a game.
- **The damage moved rather than fell:** the episodes met at a tower rose from 3.2 to 6.8 a game (4.3 → 11.1 HP a game);
  the midgame episodes cost 15.5 HP a game in v12's games and 15.7 in v13's. Against the same opponent band (rated
  20-22: v12 56 games, v13 77) our queen loses 27.2 and 28.8 HP a game to knights; what changed there is the result
  class: lost races 23 of 56 → 15 of 77, deaths 11 of 56 → 17 of 77, score 39 % → 58 %.
- **The ladder is slower under the gate:** own towers at turn 100: 3.80 (v12 4.22), at turn 150: 4.43 (4.70); two or
  fewer towers at turn 100 in 16 games (v12: 6); the second barracks by turn 100 in 56 games (68). 46 games stall (no
  new structure for 60 turns inside turns 30-150): score 48 %, 11 deaths, 13 lost races; the rest 61 %.
- The gate's state (turn 30 on, two towers, a knight within 500 and none within 250) holds the queen 30.1 turns a game
  in spells of 2.7 turns (11.2 a game; a spell of 10 or more: 0.1 a game), so a spell cap changes nothing, on the arena
  as locally. In 11.6 of those turns the strongest knight within 500 has 15 HP or less and in 6.0 it has 11 or less:
  after such a turn the queen loses 0.02-0.05 HP in the next 8 turns (18.5 turns with a knight of 16 HP or more: 0.72).
  v12, which walked in that state, paid 0.07 HP for a walk under knights of 8 HP or less, 0.28-0.36 under 9-15, 1.60
  under 16 or more (`checks.txt` sections 2 and 9).

### 3. Where the HP goes (`local_vs_arena.txt`, `checks.txt` sections 3-4, `probe_tables.txt` section 1)

HP lost to knights a game by 50 turns, ours / theirs: 7.2 / 14.3, 3.3 / 3.7, 4.6 / 2.9, 5.9 / 2.2, 2.0 / 1.9. **v13 wins
the opening by 7 HP, breaks even to turn 100 and loses turns 100-200 by 5.4 HP.**

- **The count of knights, not their quality:** from turn 50 they train 36.7 knights a game against our 21.7 (releases
  11.2 against 7.3 over the game), and a knight is worth the same on both sides: 0.43 HP for one of theirs, 0.49 for one
  of ours. Income, ours / theirs: 4.1 / 4.4 at turn 50, 3.1 / 5.0 at 100, 2.9 / 5.6 at 150. They build 6.6 mines a game
  to our 3.8: after the opening's two each, they add 0.47 before turn 50 and 1.33 in turns 50-100, we 0.01 and 0.34
  (`probe_tables.txt` section 2). A mine lives a median 100 turns to exhaustion and 16 when knights tear it down (0.8 of
  ours a game, with 180 gold left).
- **The races are decided by the defence, not by the count:** from turn 50 the opponents train the same 41 knights in
  the races we lose and in those we win, and they cost our queen 15.3 HP in the first (0.37 a knight) and 3.8 in the
  second (0.09). Our own knights from turn 50 take 4.4 HP off their queen in the lost races (0.16 a knight) and 14.2 in
  the won ones (0.54), so more knights would not turn the lost races. They are lost late: 8.9 HP in turns 150-249.
- **Their line grows, ours thins:** their towers 3.0 at 457 HP each (turn 50) → 5.3 at 515 (turn 200); ours 2.7 at 630 →
  4.8 at 495, the lowest tower 222-369 HP late in the lost races.

### 4. The deaths (`deaths.txt`, `checks.txt` section 5)

34 deaths at turns 38-246 (median 164), 31 of them against players rated 20 or more. 26 of the 31 with a turn-100
record had an opponent ahead by 2 or more of income at turn 100 (games with such a lead: 86, score 50 %, 26 deaths; the
other 79: 66 %, 5 deaths). The 125 turns before a death: own towers 2.7 → 3.4 and never more, their HP 615 → 457 each,
towers covering the queen 2.0 → 1.6, our income 3.3 → 1.6 against 5.4 → 9.6, enemy knights alive 2.5 → 7.4, ours
2.3 → 1.1; the last 25 turns cost 34 HP. Giants were on the board in 12 of the 32 deaths from turn 90 (2.8 of our
towers busted a game in the deaths, 0.5 elsewhere); against giant players rated 20 or more v13 scores 8 of 23, against
the others 55 of 99 (one player, yoyo_305, 0 of 5). The deaths are the pinned games: two or three towers, a stalled
ladder, mines not replaced, an opponent free to boom.

### 5. The queen's dead turns (`checks.txt` section 1, `idle.txt`, `probe_tables.txt` sections 4-6)

- **49.9 top-ups a game (23.5 % of all our turns) land on a tower that already has 700 HP or more; 42.5 of them add 10 HP
  or less** (a top-up adds 100 up to the 800 cap; a tower melts 4 a turn, so the idle pump adds 4). Our 88.3 top-ups a
  game add 4,325 HP of tower (49 each); the opponents rated 22 or more add 4,484 HP with 47.5 top-ups (94 each).
- Where they happen: 28.3 with no knight within 500 (the ladder complete or held; median turn 209; six towers and two
  barracks in 59 %), 18.5 in the gate's state (median turn 137; 3.8 towers, 1.4 barracks, income 2.5), 3.1 in contact.
  In the lost races 33.7 of them fall in turns 150-249, where the queen loses 8.9 HP.
- At those top-ups another own tower below 600 HP stands in 89 % (315 away on average), a free site inside an own
  tower's range in 81-93 % (with gold in 63-84 %).
- The opponents' queens walk instead: MOVE with no knight within 500 on 25-31 % of their turns in runs of 3.3 turns
  between sites and towers; from turn 60 they top up 4.8-5.2 different towers a game with 10-11 changes of tower (ours
  4.0 with 6.4), upgrade mines on 4.3-4.5 % of turns (ours 2.3 %) and top up a full tower on 2.4 % (`checks.txt`
  section 8).
- A caution (`checks.txt` section 10, the last line): among their releases that start with no knight or giant on the
  board (501), our towers' mean HP shows no protective gradient (1.9 HP of damage at 450 or less, 3.2 at 650 or more —
  many-tower lines have thin towers), so the first table of section 3 in `checks.txt` (damage by towers × HP) is
  confounded by the pressure that thins the towers. What does grade in those releases is the number of towers covering
  the queen (next section). The dead turns are real; their best use is not "thicker towers" by itself.

### 6. The cover: how many own towers reach the queen when the knights do (`checks.txt` section 10)

The HP our queen loses on a contact turn (an enemy knight within 250; turns from 50), by the number of own towers whose
range covers her:

| towers covering her | 0 | 1 | 2 | 3 | 4+ |
| --- | --- | --- | --- | --- | --- |
| v13 on the arena, HP a turn | 1.61 | 1.11 | 0.46 | 0.17 | 0.16 |
| contact turns a game | 0.3 | 4.7 | 16.5 | 9.8 | 4.2 |
| v12 on the arena, HP a turn | 1.32 | 0.94 | 0.44 | 0.28 | 0.17 |
| v13 against v13, local | 0.50 | 0.85 | 0.19 | 0.13 | 0.09 |
| v13 against `rival`, local | 1.53 | 0.64 | 0.17 | 0.03 | 0.03 |
| the opponents' queens on the arena | 0.97 | 0.77 | 0.64 | 0.46 | 0.32 |

- **The third covering tower cuts the loss of a contact turn by 63 % (0.46 → 0.17), the second by 59 % (1.11 → 0.46).**
  The gradient holds inside every stratum tried: by the own tower count (2 to 6+), by the number of knights within 250
  (1 to 5+: with three knights 1.06 / 0.64 / 0.29 / 0.31 for 1 / 2 / 3 / 4+ covering towers), in won and lost games, in
  v12's games and in every local series.
- **It is where the lost games bleed:** in our lost games the queen spends 34.6 contact turns a game under one or two
  towers and loses 27.0 HP there (of 31.6); in the won games 12.1 turns and 3.3 HP. Against 22+: 15.3 of 17.4 HP.
- By episode (1,342 from turn 50): one begun under one tower costs 10.3 HP (75 episodes, 4.3 HP a game), under two 2.0
  (597; 6.7 HP a game), under three 1.2, under four or more 0.4.
- **A better-covered spot is usually within three steps.** On our contact turns 2.37 towers cover the queen; the best
  point within 180 of her (outside enemy tower ranges) has 2.92 and is better than hers on 46 % of the turns; within 300:
  3.14, 57 %. In the gate's state, the two or three turns before the contact: 2.68 now, 3.33 within 180 (better on
  52 %). Of the episodes begun under one tower, 77 % had a better spot within 180 on their first turn; under two, 54 %.
  With every own tower at full range the best spot within 300 would have 3.48 (contacts) and 3.90 (the gate's state).
- **The bot never looks at it:** the gate and the flight send the queen to her nearest own tower, the idle rule to the
  tower nearest the home corner, and the kiting scores a step by the distance to the nearest knight minus the distance to
  the nearest own tower; a step need only end inside one tower's range. The opponents' queens stand under three or more
  towers on 50 % of their contact turns (ours 39 %) and under four or more on 30 % of their gate-state turns (ours 18 %).
- Sized on the arena's episodes, holding everything else: starting the 54 % of two-tower episodes that had a better spot
  under three towers, and the 77 % of one-tower episodes under two, saves about 3.4 HP a game of the 15.2 lost in
  midgame contacts, most of it in the lost games. The 39 lost races were lost by 1, 1, 2, 2, 3, 4, 4, 4, 5, 5, 5 HP and
  more (11 within 5, 18 within 10).
- The local panel shows the same gradient and the same reach (the mirror: 2.65 covering now, 3.33 within 180, better on
  57 %; `rival`: 2.17 and 2.72, 48 %), so P2 can measure a rule built on it: against `rival` 5.6 of the 9.8 HP our queen
  loses to knights after turn 50 are lost under a single tower.

### 7. The economy and the exploratory arms (`explore.txt`)

Development seeds 1 + 397 k, 500 maps, both seats; v13 scores 0.8275 against `rival` and 0.9720 against `field`.

| arm over v13 | S vs v13 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `INCOME_LATE=4,LATE_COVER=1` | 0.5050 (0.4944..0.5156) | +0.60 pp | 0.00 |
| `INCOME_LATE=6,LATE_COVER=1` | 0.4865 (0.4703..0.5027) | +0.55 | 0.00 |
| `INCOME_LATE=8,LATE_COVER=1` | 0.4675 (0.4508..0.4842) | +0.35 | 0.00 |
| `INCOME_LATE=6` | 0.4750 (0.4579..0.4921) | +0.25 | 0.00 |
| `MAX_TOWERS=5,INCOME_LATE=6,LATE_COVER=1` | 0.4870 (0.4640..0.5100) | +1.70 | 0.00 |
| `INCOME_TARGET=4` | 0.4655 (0.4445..0.4865) | +0.70 | −0.25 |
| `INCOME_TARGET=5,UPGRADE=1` | 0.3875 (0.3574..0.4176) | −5.25 | −1.45 |
| `UPGRADE=1` | 0.5000 (1 map changed) | 0.00 | 0.00 |
| `TRAIN_SYNC=0` | 0.5075 (0.4840..0.5310) | −0.75 | +0.10 |
| `LADDER_CLEAR_MAX=10` | 0.4995 (5 maps changed) | +0.10 | −0.10 |
| `BARRACKS_AFTER_TOWERS=3` | 0.4960 (0.4676..0.5244) | −2.15 | +0.15 |
| `TOWER_HP=600` | 0.5030 (0.4726..0.5334) | −3.65 | −1.40 |
| `MAX_TOWERS=8` | 0.4480 (0.4290..0.4670) | −0.15 | 0.00 |

Nothing gains. The late income loses the mirror even under cover and with the gate on, and more towers lose it too: in
the mirror any extra walk costs more than it brings, because v13's own line makes a late knight worth little (1.14 HP of
damage a release against 2.58 on the arena and 3.04 against `rival`). The economy is the largest structural gap (section
3) and the root of the deaths, and it has now failed locally a fifth time; it needs a use of the queen's turns that does
not cost the defence, and a local opponent that makes the midgame matter (section 8).

### 8. The local panel against the Gold field (`local_vs_arena.txt`, `checks.txt` section 6)

- The dead turns are there in every local game (44-66 top-ups of full towers a game), and so are the gate's state
  (33-62 turns) and the cover gradient: a rule on any of them is exercised locally.
- **The local opponents' midgame waves are weak because their barracks stand too far.** From turn 50 the Gold opponents
  release from 991 away on average (52 % from below 1,000) and a release does 5.86 HP of damage; `rival` releases from
  1,228 (12 % below 1,000): 2.90; `field` from 1,255: 0.34. Our queen loses 3.3 / 4.6 / 5.9 HP in turns 50-100 /
  100-150 / 150-200 on the arena, 0.7 / 0.9 / 4.0 against `rival`, 0.9 / 1.1 / 1.7 in the mirror. A rival with a
  forward barracks would put the Gold midgame into P2 (a calibration and the user's decision, as R0 was).

### 9. What is small or not the step

- **The first wave:** the family failed three times; v13 wins turns 0-50 by 7 HP on average (lost races: 11.3 against
  7.3).
- **Giants:** 37 of 180 games (51 % against 62 % without, at ratings 20.6 and 20.3), about 4 games of 180; archers
  failed on a fresh block on 2026-11-06.
- **TRAIN_SYNC:** our double releases do 3.78 HP of damage (1.89 a barracks) against 2.54 for a single one, no more than
  the sum of a near and a far release; `TRAIN_SYNC=0` is level locally.
- **The spell cap, an earlier second barracks, thinner top-ups, more towers:** the table above.
- **The seat, a rollout controller:** as in the v12 study.

## Recommendation

**The next step: stand where the towers overlap.** The queen's position under knights should count the own towers that
cover it. One candidate family, flags off by default, swept on the development seeds against the v13 mirror, `rival` and
`field` and screened under P2 on screen-pv3 block 7:

1. **The hold and the flight (`HOLD_COVER`, the main arm).** Where the gate, the flight and the idle rule now pick "the
   nearest own tower" (or the one nearest home), pick the point within reach — about three steps while a knight is within
   500, any distance when none is — with the most own towers covering it, outside enemy tower ranges: an own tower's
   touch point when one ties (she can top it up there), otherwise a MOVE to the point. Ties go to the point nearer her.
2. **The kiting (`KITE_COVER`).** Add the number of own towers covering a step's end to its score (a weight to sweep,
   of the order of a step, 60-100 a tower), so that she kites inside the overlap rather than around one tower.
3. **Companions for the same sweep, each judged on its own:**
   - no top-up of a full tower: when the rule in force would top up a tower at 700 or more, the weakest own tower in
     reach instead (it widens the overlap: the best spot within 300 has 3.14 covering towers now and 3.48 at full range);
   - the gate ignores dying knights (a knight within 500 with 11 HP or less, to sweep 8 / 11 / 15), which gives the
     ladder back 3.5-11.6 turns a game.

**Measures besides S** (`ArenaProbe local`, 200 maps a series): the share of contact turns under three or more towers,
the HP lost a contact turn and by 50 turns, the episodes begun under one and two towers, top-ups that add 10 HP or less,
towers at turns 100 and 150, deaths per 400 games, and the walking-caught episodes (the tripwire: a point between towers
must not become a walk into the wave). Bar: S above 0.50 with its lower bound above 0.50 on the development seeds, deltas
against `rival` and `field` of at least −1 pp, deaths not above v13's; then the screen (G0-G5), then the final evaluation
on evaluation-pv3 block 3.

**Why this one:** it is the largest measured lever on what v13 still loses (27 of the 31.6 HP a lost game costs our queen
in midgame contacts are lost under one or two towers, and a better spot is within three steps about half the time); it
is the class of change the arena has rewarded every time (v08, v12, v13: the queen under knights); the local panel shows
the same gradient, so P2 can judge it; and it costs no walk, no tower and no gold.

**After it, in this order:** the economy with the dead turns (mines and upgrades under cover in place of the idle pump,
a staged income target once three towers stand), judged with a rival whose barracks stand forward (section 8); the
answer to giants only if the deaths under them remain.

## What is left open

- The cover gradient is observational: fewer covering towers go with harder games. It holds within the tower count, the
  knight count and the result, and in local games where both sides are the same bot, but only the experiment gives its
  causal size; the 3.4 HP a game is the full effect of the starting spot alone.
- A point between towers gives up the top-up she makes while standing at a tower (24.5 a game in the gate's state, 16.7
  of them at 750 HP or more); whether that costs anything is for the sweep.
- The damage-by-tower-HP table (`checks.txt` section 3) is confounded, as section 5 records; it is kept as measured and
  not used.
- WaveScan's damage measure counts overlapping windows (a release every 17 turns against a 35-turn window), so
  per-release damages compare sides and conditions, not HP totals; the HP totals are those of section 3.
- v12 and v13 met different fields (median 19.69 and 20.90); only the comparisons inside the 20-22 band are like for
  like, on 56 and 77 games.
