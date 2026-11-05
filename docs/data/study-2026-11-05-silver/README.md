# Study 2026-11-05: v08 in Silver — the first wave, the late game, giants and the local proxy

**Question.** v08 stands about 1.4 below the Silver boss: its placements are 20.70, 20.74 and 20.21, the
boss stands at 21.87-21.99. P2's proxy validation (PV1/PV2) lapsed with the promotion, and none of v08's
arena games had been studied. The study asks three things:
- how v08's Silver games are won and lost;
- what the opponents who beat it do;
- whether the local opponents represent the Silver field.

## Data and tools

- **Corpus:** v08's 250 Silver placement games and, for context, its 120 Bronze games (uploads 1-2).
  - Silver uploads 3-5, agents 6843976, 6844095 and 6847999.
  - Replays: `replays/arena/v08/silver/upload-0{3,4,5}/games`.
- **Features:** `cgStudy` over the Silver and the Bronze upload directories.
  - Silver: `./gradlew cgStudy -Pout=replays/study/features-v08-silver-2026-11-05.csv
    -Preplays="replays/arena/v08/silver/upload-03/games replays/arena/v08/silver/upload-04/games
    replays/arena/v08/silver/upload-05/games"` (sha256 `8a2d927c827316e2…`).
  - Bronze: the two Bronze upload directories into `features-v08-bronze-2026-11-05.csv` (`ff28ef5d591050a4…`).
- **Traces:** two runs for each upload NN with our agent A.
  - Our queen: `./gradlew -q deathTrace -Pagent=A -Pall=1
    -Preplays=replays/arena/v08/silver/upload-NN/games > replays/study/traces/all-v08s-ours-upNN.txt`.
  - The opponent's queen: the same with `-Pfoe=1`, into `all-v08s-opp-upNN.txt`.
  - Concatenated in upload order into `all-v08s-ours.txt` (`a5f645fa7dfe868c…`) and `all-v08s-opp.txt`
    (`ffc38dbe3499452d…`).
  - Features and traces are gitignored and live in the main checkout's `replays/study/`.
- **Local series:** development seeds only; the profiles are in `local-profiles.txt`.
  - Bench, 500 maps from seed 150000: v08 against v08, `field`, `field?GIANTS=1` and `boss`.
    In-process series: 8 threads, 50 ms; `boss`: 3 threads, 200 ms. Reports in
    `replays/bench/study-2026-11-05-silver/`.
  - Profiles: `./gradlew -q localProfile -PbotA=<a> -PbotB=<b> -Pmaps=<n> -PseedBase=<s> [-PseedStride=397]`.
    Two seed sets: 200 maps from 150000 (100 against `boss`), and 500 maps 1 + 397k (100 against `boss`).
  - The mirror bench had 14 slow turns: the host was busy with this study's scripts. The proxy comparison
    uses no gate, so they do not matter here.
- **Scripts:** run from the repository root. `silver.py` writes `silver.txt`; run it with
  `--data <main checkout>` from a worktree. `start_hp.py` writes `start_hp.txt`.

### Tool changes (2026-11-05)

The bot and the artifact are unchanged.
- **`cr.tools.DeathTrace`:** two new columns at the end of each line split the turn's HP loss by source.
  Each comes from a probe copy of the turn:
  - `kd`: the loss with the enemy towers' ranges set to 0, that is, the knights' share;
  - `td`: the loss with the enemy knights removed, that is, the towers' share.

  Only knights and towers damage a queen. `kd + td` misses the real loss on 20 of the 4,944 turns with a
  loss, when removing one source moves the other. The `foe` flag traces the other seat; the header ends
  with the traced agent's id. The older parsers still match, since the columns come last.
- **Gradle tasks:** `deathTrace` and `localProfile`.
- **`cr.tools.LocalProfile`:** a fix and new lines.
  - **The fix.** Since da6384a (2026-11-03) the medians at turns 50/100/150, the median waves and the game
    length were filtered on the column of the MOVE share instead of the game length. No record used those
    medians after that commit: the kiting record quotes only the under-knights line, which never used the
    filter. With stride 1 the fixed tool repeats this study's first profile exactly.
  - **New lines:** the first contact, the HP lost on turns 25-50, the games at the limit and the close
    ones, the starting HP of the maps, and an optional seed stride.

## Findings

Medians unless marked. Contact means an enemy knight within 250 of the queen. The first contact is the first
such turn; its HP loss is counted over that turn and the 19 after it.

1. **How the games end.**
   - 250 games: 153 won, 95 lost, 2 draws. 120 reached turn 250 (48 %), against 26 % in Bronze.
   - The losses: 61 are deaths of our queen (median turn 166), 34 are lost on HP at the limit.
   - The starting HP sets how a game ends, not our score. With 25-40 HP 28 % of the games reach the limit,
     with 85-100 HP 68 %. Our score stays at 60-64 % in every HP band.
2. **Knights do the damage.** Our queen lost 7,897 HP over the 250 games: 7,083 to knights and 805 (10 %) to
   enemy towers. Theirs lost 10,023: 9,463 to our knights and 567 to our towers.
3. **The first wave decides most HP races, and the boss games above all.**
   - The first contact comes at turn 23-28. Our second tower stands at turn 25 in every outcome group. What
     differs is when the enemy's wave arrives.
   - **One own tower at the first contact** (86 games): 12 HP lost (mean 13.3), score 48 %, first contact at
     turn 23.
   - **Two towers** (132 games): 5 HP lost, score 65 %, first contact at turn 28.
   - **Three or more** (28 games): 0 HP lost, score 82 %, first contact at turn 51.
   - In the races we won, our lead grows by 10.3 HP on turns 25-50 (mean), and 70 of 85 lead after turn 50.
   - The opponents also have one tower when our first wave arrives (turn 23-24), and lose 10-20 HP to it.
   - **The Silver boss** (16 games, 13 to the limit, 6 won): we met its first wave with one tower in 12
     games. 6 of the 10 losses were decided on turns 25-50, where we lost 14-37 HP against its 3-19. The
     other 4 losses collapsed later: 3 in the last 50 turns (2 of them against giants), and one death at
     turn 109.
4. **Races lost at both ends.**
   - In the 34 races we lost, the lead falls on turns 25-50 (−3.1 mean), 100-150 (−3.9) and most of all
     on 200-250 (−6.8).
   - 21 of the 34 were lost by 10 HP or less.
   - **Lost from ahead:** 10 of the 34 we led after turn 200. In the last 50 turns the queen lost 10-73 HP
     each, 278 of the 292 HP to knights and 12 to towers. She was walking to build, topping up a tower or
     kiting. Four of the ten were against giants, and in three of those only 0-2 own towers stood.
5. **Deaths come under knights at her towers.**
   - In the last 30 turns before a death she lost 26-30 HP to knights and none to towers. She stood at an
     own tower on 67-73 % of those turns.
   - **Against giants** the deaths come late (turn 196): 3 towers left of the 5 she had at most, and our
     first tower falls around turn 138. Without giants our towers usually last to the end.
6. **Giants are a likely weakness, about 2 rating points.**
   - Opponents trained giants in 62 of the 250 games and archers in 2.
   - Raw score: 40 % against giant players, 69 % against the rest. The giant players are also stronger
     (median rating 20.77 against 18.79).
   - With the logistic scale fitted on these games, v08's performance rating is 19.02 ± 0.84 against giant
     players and 20.96 ± 0.52 against the others. A likelihood-ratio test gives LR 4.66 on 1 df, p 0.03,
     with the scale re-fitted under each model.
   - The fitted scale is 3.16. Bronze's scale 1.33 fits much worse (log-likelihood −167.6 against −154.6),
     and with it the two ratings come out equal: that was this session's earlier quick-pass conclusion,
     which this corrects.
7. **The strongest four: the boss and players rated 20.9-21.7.**
   - We scored 16.5 of 55 against them (30 %). They trained giants in 36 of those games.
   - 26 of our 38 losses to them were deaths, at turn 182.
   - Per contact turn our queen loses less than theirs: 0.5 HP against 0.8-1.1.
   - After turn 100 their knights reach our queen far more often: 32 contact turns against 9 for our knights
     near theirs.
   - They out-produce us: 10-13.5 knight waves against our 6-7, income 6 against 3 at turn 100, 7 mines
     against 4, 9 upgrades against 3. The boss is the exception: 10 waves against our 11, with little
     contact on either side.
8. **The local proxy: what each local opponent represents.** Table in `silver.txt`, section 6.
   - **Starting HP.** `MapGen` draws the starting HP first, `Random(seed).nextInt(16) * 5 + 25`.
     - Consecutive seeds barely move that first draw, so a local block plays 1-6 of the 16 values.
     - Examples: 484 of the 500 maps of 150000-150499 start at 35 HP, and so do all 200 maps of the `field`
       calibration (11000-11199).
     - Every P2 block mixes only 4-6 values; v08's screen block was mostly 30-60 HP, its evaluation block
       mostly 85-95.
     - CG's seeds give all 16 values evenly, median 65 (`start_hp.txt`). The formula matches all 250
       replays.
     - The HP changes the local games a lot: v08 against `field` reaches the limit in 21 % of the games at
       35 HP and in 58 % with spread seeds.
   - **The mirror is all HP races.** With spread seeds v08 against v08 goes to the limit in 90 % of the
     games, and 70 % of those are decided by 10 HP or less. In Silver: 48 % and 33 %.
   - **The other opponents end early.** `field` reaches the limit in 58 % of the games, `field?GIANTS=1` in
     38 %, `boss` in none.
   - **The first contact is represented.** All local opponents meet v08 at turn 19-24 with one own tower, at
     a cost of 5-7 HP; Silver: turn 26.5, 7 HP (mean 10.8).
   - **Giants are not represented.** No local opponent plays them the way Silver does. `field?GIANTS=1`
     loses 94 % of its games against v08.
   - **The economy is over-played.** `field` holds income 8 and trains 18 waves against the strongest four's
     6 and 11. It does not kite (0 % MOVE under knights), so it loses 93 % of its games.

## What follows

**A proposal for PV3.** The user adopted it as proposed on 2026-11-05; the policy text is in
`docs/local-screen.md` (PV3) and the pools are in `docs/data/seed-ledger.json`.
- **Spread the starting HP in P2's series:** a seed stride (397 covers all 16 values evenly) in `bench`,
  `bench_pair.py` and the ledger. The new blocks would get their own seed range, so the old blocks and
  their verdicts stay valid as they are.
- **Keep the panel as it is,** and record what each opponent stands for:
  - the mirror: the first wave and the close HP races, which it over-weights;
  - `field`: the field's economy, without its kiting;
  - `boss`: a floor.
- **Record what the panel does not represent:** giants (25 % of the Silver games, 65 % of the strongest
  four's) and the late game of strong economies. Candidates whose effect depends on those are judged by
  the arena.

**Candidates, in the order the evidence supports:**
1. **The first wave.** A second tower before the enemy's first knights arrive: a TOWER before more
   mines and upgrades while an enemy knight barracks trains or its wave is on the way.
   - The evidence: points 3 and 7.
   - Locally measurable: every local opponent meets v08 with one tower at turn 19-24.
   - **Tried on 2026-11-05: it loses on the development seeds** (`docs/data/exp-2026-11-05-first-wave/`). Two
     towers met the wave, but the loss there did not shrink on average and the queen died twice as often.
2. **Holding a lead late.** After turn 200, with more HP than the enemy queen, no walking to build under
   knights; stay at the strongest own towers.
   - The evidence: 10 races lost from ahead (point 4).
   - The mirror would over-reward it (point 8), so the arena judges.
   - **Tried on 2026-11-05: `LATE_HOLD=200` passes the P2 screen** (S 0.527 against v08, +1.5 pp against `field`;
     `docs/data/exp-2026-11-05-late-hold/`), below the 0.60 upload recommendation.
3. **Giants.** A response to giants (archers, or tower spacing), once a local opponent plays giants as the
   Silver field does.
4. **Economy against the strongest four.** Income 3 against 6 and 7 waves against 11. Untestable locally
   until an opponent both kites and expands.
