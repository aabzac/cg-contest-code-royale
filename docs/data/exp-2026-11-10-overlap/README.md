# Experiment 2026-11-10: stand where the towers overlap (Gold, over v13)

## Hypothesis and success bar (written before the comparisons)

The study of v13's 180 Gold games (`docs/data/study-2026-11-09-v13-gold/`) found that the HP our queen loses on a turn
with an enemy knight within 250 depends on how many own towers' ranges cover her: 1.11 HP under one, 0.46 under two, 0.17
under three, 0.16 under four or more. In the lost games 27.0 of the 31.6 HP her midgame contacts cost go under one or two
towers; a point with more cover lies within 180 of her on 46 % of the contact turns and 52 % of the turns of the ladder
gate's hold; the local series show the same gradient. The bot does not count the cover: the gate and the flight send her
to her nearest own tower, the idle rule to the tower nearest the home corner, and a kiting step need only end inside one
tower's range. Beside it, 42.5 of her turns a game top up a tower that is already full, and 6.0 turns of the gate's hold
wait for knights with 11 HP or less, which do her 0.02-0.03 HP of damage.

The user's decision of 2026-11-10 ("Да, начинай"): build the rule and test it.

The flags, all off by default (`current` plays as v13; `MainBotTest.withDefaultsCurrentPlaysAsV13`):
- `HOLD_OVERLAP=R` (the main arm): where the gate (`LADDER_CLEAR`) and the flight pick her nearest own tower, she takes
  the spot with the most own towers covering it among those she can reach in ceil(R / 60) steps: her own position, the
  predicted positions along 24 headings, and the touch points of own towers on her straight walk to them. Spots inside an
  enemy tower's range are out, and in the gate's hold those within `THREAT_RANGE` of an enemy knight too. Among the best,
  an own tower she touches (a top-up, as today), then an own tower's touch point (BUILD, so she tops it up on arrival),
  then standing where she is, then the nearest of the others (the deeper inside the ranges on a tie). With no spot better
  than the rule of v13 gives, v13's command stands.
- `KITE_OVERLAP=W`: the kiting scores a step by the distance to the nearest knight minus the pull to the nearest own
  tower; W is added for every own tower covering the step's end (and the place she stands on, the baseline a step must
  beat).
- `IDLE_OVERLAP=1`: the idle queen (the ladder complete, or the late hold) goes to the own tower with the most own towers
  covering its centre instead of the one nearest the home corner (a tie: nearer the corner; towers inside an enemy
  tower's range only when no other stands).
- `PUMP_LOW=H`: the idle queen goes to her weakest own tower when it has less than H HP (the touch rule then tops it up
  to `TOWER_HP`), instead of pumping the full idle tower.
- `CLEAR_HP=K`: the gate does not count enemy knights with K HP or less.

**Hypothesis.** Against the v13 mirror, `rival` and `field` on the development seeds 1 + 397 k, k < 500, both seats:
- `HOLD_OVERLAP` and `KITE_OVERLAP` raise the share of contact turns (from turn 50) with three or more own towers covering
  the queen and lower the HP she loses to knights after turn 50, without more contact episodes begun on a walk and without
  more deaths;
- `IDLE_OVERLAP` raises the cover on the first turn of a contact episode; `PUMP_LOW` cuts the top-ups that add 10 HP or
  less and raises the tower HP late; `CLEAR_HP` raises the towers standing at turns 100 and 150 without more HP lost on
  walks;
- S against v13 rises above 0.50 for at least one arm, with deltas against `rival` and `field` of at least −1 pp.

**Success bar** (as in the meet-waves experiment): on the development seeds S against v13 above 0.50 with its lower CI
above 0.50 for the arm taken further, deltas against `rival` and `field` of at least −1 pp, deaths per 400 profile games
not above v13's, and the mechanism measures moving as predicted. Only then a P2 screen on a fresh block: screen-pv3 block
7, seeds 1007000000 + 397 i. Several arms are compared on the development seeds, so the screen's block is used once, for
the single arm or bundle chosen here.

## Tools

- `sweep.sh`, `sweep_table.py`: the arms against v13 (direct), `rival` and `field`, with v13's control series; S with the
  changed maps, deltas with CIs (`tools/bench_pair.py`). Raw reports in `replays/bench/exp-2026-11-10-overlap/dev/`
  (gitignored); `sweep.txt` is the table. With `BASE=3 OUT=.../dev-b` the same on a second development set, 3 + 397 k
  (`sweep-b.txt`).
- `profile.sh`, `profile_table.py`: the study's probe in its local mode
  (`docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java`), 200 maps 1 + 397 k, both seats, of v13 and of each arm
  against v13, `rival` and `field`; the table (`profiles.txt`) gives the cover on contact turns, the HP by cover and by
  phase, the episodes by the cover on their first turn, the top-ups of full towers, the towers and the deaths.
- `screen.sh`: the P2 series and gates against v13 on PV3 screen block 7.
- Code: `cr.bot.Params` (six parameters, all 0 by default) and `MainBot` (`overlapSpot`, `idleTower`, `cover`, the kiting's
  bonus, `clearOfKnights` with a minimum HP). One parameter was added while the code was written, before any series:
  `OVERLAP_FIRST=N` — in the hold's state the search of `HOLD_OVERLAP` also comes before the touch rule's top-up of an own
  tower (N = 1 always; N of 2 or more only while fewer than N own towers cover her; from sweep 3 on also before the
  upgrade of an own mine).
- Tests: `StrategyParamsTest.holdOverlapStandsWhereTheTowersOverlap`, `kiteOverlapPrefersAStepUnderMoreTowers`,
  `idleOverlapAndPumpLowChooseTheIdleTower`, `clearHpIgnoresDyingKnights`; the version identity
  `MainBotTest.withDefaultsCurrentPlaysAsV13`; the frozen snapshots `versions/v01-v13.properties` list the six parameters at
  0 (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults`). The full suite passed before the screen (2026-11-10, in
  the worktree, `./gradlew test`: 177 tests, 3 corpus tests skipped for want of the arena replays, 29 s). After the last code change `current` at its defaults against v13 on the 500 development maps repeats v13's mirror game for game (`dev/identity-current-v13.json`, `bench_pair.py direct`: 0 maps changed).

## Development sweeps (`sweep.txt`, raw reports in `dev/`)

Seeds 1 + 397 k, k < 500, both seats, in-process (8 threads, 50 ms); each arm against v13 (direct), `rival` and `field`,
with v13's own series as the controls (v13 scores 0.8275 against `rival` and 0.9720 against `field`). Slow turns of the
arms (0-15 a series, host noise at 8 threads beside other work) are listed in `sweep.txt` and count for nothing here.

**Sweep 1, one flag at a time:**

| arm | S vs v13 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `HOLD_OVERLAP=120` | 0.5255 (0.5007..0.5503) | +0.0100 (−0.0152..+0.0352) | +0.0030 (−0.0086..+0.0146) |
| `HOLD_OVERLAP=180` | 0.5350 (0.5101..0.5599) | +0.0285 (+0.0029..+0.0541) | +0.0085 (−0.0019..+0.0189) |
| `HOLD_OVERLAP=300` | **0.5490 (0.5237..0.5743)** | +0.0245 (−0.0013..+0.0503) | +0.0105 (−0.0003..+0.0213) |
| `HOLD_OVERLAP=180,OVERLAP_FIRST=1` | 0.5380 (0.5118..0.5642) | +0.0010 (−0.0257..+0.0277) | +0.0045 (−0.0071..+0.0161) |
| `KITE_OVERLAP=30` | 0.4745 (0.4511..0.4979) | +0.0050 | −0.0070 |
| `KITE_OVERLAP=60` | 0.3800 (0.3526..0.4074) | −0.0145 | −0.0060 |
| `KITE_OVERLAP=100` | 0.3460 (0.3175..0.3745) | −0.0395 | −0.0285 |
| `KITE_OVERLAP=150` | 0.3335 (0.3050..0.3620) | −0.0430 | −0.0310 |
| `IDLE_OVERLAP=1` | 0.5155 (0.4989..0.5321) | +0.0015 | +0.0000 |
| `PUMP_LOW=500` | 0.5010 (0.4844..0.5176) | +0.0035 | +0.0005 |
| `PUMP_LOW=600` | 0.5050 (0.4872..0.5228) | +0.0050 | +0.0005 |
| `CLEAR_HP=8` | 0.5045 (0.4826..0.5264) | −0.0020 | +0.0020 |
| `CLEAR_HP=11` | 0.5000 (0.4757..0.5243) | −0.0020 | +0.0010 |
| `CLEAR_HP=15` | 0.4995 (0.4731..0.5259) | −0.0200 | −0.0155 |

- **The hold on the best-covered spot passes the bar** at every reach, and the gain grows with the reach up to 300.
- **The kiting must stay free.** Any weight on the cover loses, the more the heavier: a step that leaves the overlap is
  what gets her away from adjacent knights, and with the bonus she stands under them instead. The cover pays before the
  contact, not during it.
- The idle tower by cover is a small gain alone; the weakest-tower pump and the HP filter of the gate are level with v13
  (`CLEAR_HP=15` loses). The study's caution about "thicker towers" holds: the dead top-ups are real, their redirection to
  the weak towers buys nothing by itself.

**Sweep 2, the reach and the bundles; sweep 3, the search before the touch rule:**

| arm | S vs v13 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `HOLD_OVERLAP=420` | 0.5400 (0.5151..0.5649) | +0.0120 | +0.0085 |
| `HOLD_OVERLAP=600` | 0.5505 (0.5257..0.5753) | +0.0205 | +0.0090 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1` | **0.5545 (0.5288..0.5802)** | +0.0210 (−0.0047..+0.0467) | +0.0085 (−0.0027..+0.0197) |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,PUMP_LOW=600` | 0.5460 (0.5197..0.5723) | +0.0225 | +0.0075 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,CLEAR_HP=8` | 0.5730 (0.5467..0.5993) | +0.0390 (+0.0122..+0.0658) | +0.0065 |
| `HOLD_OVERLAP=300,OVERLAP_FIRST=1` | 0.5495 (0.5225..0.5765) | −0.0055 | +0.0130 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,OVERLAP_FIRST=2` | 0.5485 (0.5228..0.5742) | +0.0230 | +0.0140 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,OVERLAP_FIRST=3` | 0.5575 (0.5306..0.5844) | +0.0115 | +0.0135 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,CLEAR_HP=8,OVERLAP_FIRST=2` | 0.5725 (0.5460..0.5990) | +0.0380 | +0.0085 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,CLEAR_HP=11` | 0.5560 (0.5285..0.5835) | +0.0190 | +0.0090 |
| `HOLD_OVERLAP=300,CLEAR_HP=8` | 0.5570 (0.5306..0.5834) | +0.0265 | +0.0070 |
| `LADDER_CLEAR=600,HOLD_OVERLAP=300,IDLE_OVERLAP=1` | 0.5640 (0.5381..0.5899) | +0.0160 | +0.0165 |
| `HOLD_OVERLAP=600,IDLE_OVERLAP=1` | 0.5545 (0.5287..0.5803) | +0.0280 | +0.0070 |

- The reach is a plateau from 300 up (0.549, 0.540, 0.5505 at 300, 420, 600; with the idle tower 0.5545 at 300 and at
  600). 300, five steps, is its near end.
- The search before the touch rule (`OVERLAP_FIRST` 1, 2, 3) adds nothing to the bundle and costs the top-ups against
  `rival` when it always applies: off.
- `CLEAR_HP=8` looked like a gain on top of the hold (+0.008 to +0.024 in three pairs of arms); the second set below does
  not repeat it. A wider gate (`LADDER_CLEAR=600`) is +0.0095, inside the noise, and would change a parameter of v13: left.

## The second development set (`sweep-b.txt`, raw reports in `dev-b/`)

The four leading arms on other maps, seeds 3 + 397 k, k < 500 (below 200000, so development seeds), to choose among
them before the one fresh block is spent:

| arm | S vs v13, set A | set B (95 % CI) | delta vs rival, A / B | delta vs field, A / B |
| --- | --- | --- | --- | --- |
| `HOLD_OVERLAP=300` | 0.5490 | 0.5390 (0.5143..0.5637) | +0.0245 / −0.0075 | +0.0105 / +0.0175 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1` | 0.5545 | **0.5505 (0.5247..0.5763)** | +0.0210 / −0.0015 | +0.0085 / +0.0185 |
| `HOLD_OVERLAP=300,IDLE_OVERLAP=1,CLEAR_HP=8` | 0.5730 | 0.5420 (0.5152..0.5688) | +0.0390 / +0.0065 | +0.0065 / +0.0140 |
| `HOLD_OVERLAP=300,CLEAR_HP=8` | 0.5570 | 0.5340 (0.5073..0.5607) | +0.0265 / +0.0005 | +0.0070 / +0.0120 |

- The direct gain repeats on every arm (0.534-0.5505, every lower bound above 0.50). The gain against `rival` of set A
  does not (about zero on set B), the one against `field` is larger on set B.
- `CLEAR_HP=8` is +0.0185 and +0.008 on set A and −0.0085 and −0.005 on set B: noise. Off.
- `IDLE_OVERLAP=1` adds +0.0055 on set A and +0.0115 on set B: small and of one sign.

**The candidate: `current?HOLD_OVERLAP=300,IDLE_OVERLAP=1`** — the two flags of one idea (the spot she waits on is chosen
by the cover: in the hold and when idle), the best arm of set B and within the noise of the best of set A.

## Profiles (`profiles.txt`; the probe's csv files, 426 MB, are not kept: `profile.sh` rebuilds them)

`ArenaProbe local`, 200 maps 1 + 397 k, both seats (400 games), against v13, `rival` and `field`:

| arm vs opponent | win % | queen deaths | contact turns from turn 50: own towers covering her / under 3 or more / HP a turn | episodes begun under 0-1, 2, 3+ towers: a game (HP a game) | begun on a walk or a MOVE: a game (HP) | her HP to knights, turns 50-249 | top-ups of a full tower |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v13 vs v13 | 42.0 | 11 | 2.65 / 53 % / 0.22 | 0.4 (2.2), 2.5 (2.1), 4.1 (1.1) | 0.6 (2.0) | 5.4 | 59.2 |
| candidate vs v13 | 46.5 | 9 | 3.04 / 66 % / 0.19 | 0.2 (1.5), 1.5 (1.9), 5.8 (1.2) | 2.4 (1.8) | 4.6 | 44.3 |
| v13 vs rival | 81.2 | 30 | 2.15 / 35 % / 0.25 | 1.6 (6.1), 4.0 (2.5), 4.4 (0.8) | 0.8 (1.7) | 9.3 | 45.8 |
| candidate vs rival | 84.8 | 20 | 2.47 / 48 % / 0.20 | 0.7 (3.6), 2.9 (2.2), 6.4 (0.7) | 2.7 (1.9) | 6.5 | 31.2 |
| v13 vs field | 97.8 | 3 | 2.44 / 44 % / 0.05 | 0.9 (1.0), 5.9 (0.7), 6.4 (0.1) | 1.0 (0.3) | 1.8 | 64.4 |
| candidate vs field | 98.0 | 2 | 2.86 / 65 % / 0.05 | 0.2 (0.5), 3.0 (0.5), 9.2 (0.5) | 3.5 (0.9) | 1.5 | 44.3 |

- **The mechanism moves as predicted.** On contact turns the queen has 0.3-0.4 more towers over her and stands under
  three or more on 48-66 % of them (35-53 %); the episodes begun under one tower or none halve (0.4 → 0.2, 1.6 → 0.7,
  0.9 → 0.2 a game) and so does what they cost against `rival` (6.1 → 3.6 HP a game); she loses 0.8 HP less to knights
  after turn 50 in the mirror and 2.8 less against `rival`, and dies less (11 → 9, 30 → 20, 3 → 2 per 400 games).
- **The tripwire holds.** More episodes begin while she moves (0.6-1.0 → 2.4-3.5 a game: the MOVE to the spot); in the
  mirror and against `rival` they cost together what the few cost before (2.0 → 1.8 and 1.7 → 1.9 HP a game), against
  `field` 0.3 → 0.9.
- **Nothing else moves:** towers at turns 100 and 150 (4.0 and 5.1 in the mirror, 3.7 and 4.3 against `rival`), the HP of
  a tower at turn 150 (545-570; v13's 542-565), the income, the knight releases (8.4, 8.0, 7.0 a game) and the damage to
  the enemy queen are v13's. The top-ups of a full tower fall by a quarter to a third (she stands between towers part of
  the hold), with no loss of tower HP.
- The first wave is untouched (4.3, 3.9 and 5.8 HP in turns 0-49 for both bots): the gate does not act before turn 30,
  and with two towers a better spot is within reach on about one first contact in ten (`episodes.txt`).
- Alone, `HOLD_OVERLAP=300` gives 2.92 / 63 % and `IDLE_OVERLAP=1` 2.85 / 59 % in the mirror; the two add up to 3.04 /
  66 % (`profiles.txt` has every arm profiled).

What the hold leaves (`episodes.txt`, the candidate in the mirror, 8.9 HP a game in contact episodes):
- the first wave, 4.4 HP (before turn 50, two towers, untouched);
- episodes begun under two towers, 1.9 HP, and under one or none, 1.5 HP (0.2 a game at 8.7 HP each): on the turn before
  them she tops up the tower she touches (the touch rule comes first), upgrades a mine, or is on her way to the spot;
  `OVERLAP_FIRST` moves the search before the touch rule and gains nothing (sweep 3);
- episodes begun under three or more, 1.2 HP.

## P2 screen (`screen.sh`, `rerun.sh`; summaries `g1.json`, `g2.json`, `g4.json`, `g4-rerun.json`, `g5.json`; raw reports in `screen/`)

Candidate `current?HOLD_OVERLAP=300,IDLE_OVERLAP=1` against the control v13 (the local champion and the selected
version), PV3 screen block 7: seeds 1007000000 + 397 i, i < 2000, both seats; `boss` on the first 500 maps (3 threads,
200 ms). Commit 639c33e with this experiment's and the study's uncommitted changes (the reports record the status). The
block was reserved in `docs/data/seed-ledger.json` before the run.

| gate | result |
| --- | --- |
| G1 | **passes**: S 0.5473 (95 % CI 0.5347..0.5598) against v13, W/D/L 1882/614/1504; 1802 of 2000 maps differ from v13's mirror (a strategy change) |
| G2 | passes: boss 1.0000 against 1.0000, delta 0.0000 (97 of 500 maps changed) |
| G4 | **passes**: field 0.9829 against 0.9667, +0.0161 (CI +0.0107..+0.0216); G0 see below |
| G5 | **passes**: rival 0.8404 against 0.8129, +0.0275 (CI +0.0149..+0.0401) |
| G3 | skipped: the code of `old` is still missing |
| G0 | the candidate had no error, timeout or extra line in its 13,000 games and **one slow turn**, in the field series on map 1007611777 (8 threads; the candidate's longest turn there 51.7 ms, mean 0.09 ms; v13 had two slow turns in its own field series). The field series was repeated at 2 threads (`rerun.sh`): the same 4000 games (`same_games.py`: 0 differ), no slow turn (longest 42.5 ms, mean 0.05 ms); `g4-rerun.json`: G0 pass, delta +0.0161, pass |

The development numbers replicate on the fresh block: S 0.5545 and 0.5505 → 0.5473; rival +2.1 and −0.2 → +2.75 pp;
field +0.85 and +1.85 → +1.61 pp. By seat the candidate scores 985/317/698 on seat 0 and 897/297/806 on seat 1 against
v13 (W/D/L), the mirror's tilt (v13 against v13: 908 against 783 wins for player 0).

## Verdict

`current?HOLD_OVERLAP=300,IDLE_OVERLAP=1` passes P2's screen (G0 by the quiet repeat, G1, G2, G4, G5; G3 skipped): S
0.5473 against v13 on the fresh block, +1.6 pp against `field`, +2.75 pp against `rival`, boss unchanged. By P2 the passing
candidate is the local champion. What it does: while an enemy knight is within 500 and none within 250 (v13's hold), and in
the flight, the queen goes to the spot within five steps that the most own towers cover instead of to her nearest tower;
idle, she waits at the own tower with the most towers over it instead of the one nearest home. Locally she then meets the
knights under 0.3-0.4 more towers, under three or more on half to two thirds of the contact turns, loses 0.8-2.8 HP less
to knights after turn 50 and dies less often; the ladder, the economy and the army are unchanged.

About +0.5 rating points head-to-head at Gold's scale (the logit of 0.5473 is 0.19; the scale 2.71): below the 0.60 upload
recommendation, as v10 (0.5764), v12 (0.5716) and v13 (0.5958) were.

Rejected here: the cover in the kiting's score (`KITE_OVERLAP`, 0.33-0.47 against v13), the search before the touch rule
(`OVERLAP_FIRST`), the weakest-tower pump (`PUMP_LOW`, level) and the gate's HP filter (`CLEAR_HP`: level alone, a gain on
one development set that the other did not repeat). All stay in the code, off.

**The user's decision (2026-11-10, "Да, v14"):** `HOLD_OVERLAP=300,IDLE_OVERLAP=1` is the `current` default and v14 is
frozen (below); the final evaluation and the artifact follow. S is below the 0.60 upload recommendation, so an upload is
an exception and the user's decision.

## v14 (2026-11-10)

- `Params`: `HOLD_OVERLAP = 300` and `IDLE_OVERLAP = 1` by default (`OVERLAP_FIRST`, `KITE_OVERLAP`, `PUMP_LOW` and
  `CLEAR_HP` stay 0).
- `cr.bot.v14.GreedyBot`, `RoutePlanner` and `ApproachPlanner`: copies of `current` at the freeze (the package and class
  names, the frozen header and `name()` changed; `queenSteps()` left out, as in the earlier copies).
- `versions/v14.properties`: v13's snapshot with `HOLD_OVERLAP=300` and `IDLE_OVERLAP=1`. `BotFactory`, `LocalGames` (v14
  the default opponent), `visualizer/local.js`, `docs/visualizer.md` and `.ai/project.md` (resynced into `AGENTS.md`)
  know v14.
- Tests: `MainBotTest.withDefaultsCurrentPlaysAsV14` (fixture seeds and PV3 screen block 7's first maps),
  `withV13ParamsCurrentPlaysAsV13` (`HOLD_OVERLAP=0,IDLE_OVERLAP=0`; the older versions' parameter strings carry the same
  two zeros), the chain v13 → v14 in `BotFactoryTest`, `LocalGamesTest`'s bot count; the two overlap tests of
  `StrategyParamsTest` pin v13's flags where they assert v13's commands. The full suite is green (178 tests, 3 corpus
  tests skipped for want of the arena replays in the worktree); the visualizer's node tests pass (18).
- Identity on the 500 development maps (`replays/bench/exp-2026-11-10-overlap/freeze/`): `current` against v14 repeats
  v14's mirror game for game (0 maps changed), and v14 against v13 repeats the screened arm's 1000 games (0 differ).
- `docs/bot-architecture.html` is v14's: the decision flow (steps 2 and 2b, the idle tower), the pseudocode with
  `overlapSpot`, the target choice, the versions table, the gaps and the directions.

## Final evaluation (`eval.sh`; summaries `eval-g1.json`, `eval-g2.json`, `eval-g4.json`, `eval-g5.json`; raw reports in `eval/`)

v14 against the selected version v13 on PV3 evaluation block 3: seeds 2003000000 + 397 i, i < 2000, both seats; `boss`
on the first 500 maps (3 threads, 200 ms). The block was reserved in the ledger before the run; the next evaluation
block is 4.

| gate | result |
| --- | --- |
| G0 | **passes**: v14 had no error, timeout, slow turn or extra line in its 13,000 games (its longest turn 37.2 ms at 8 threads; v13 had two slow turns as the opponent of the direct series and three in its own field series) |
| G1 | **passes**: S 0.5533 (95 % CI 0.5404..0.5661) against v13, W/D/L 1921/584/1495, 1800 of 2000 maps changed |
| G2 | passes: boss 1.0000 against 1.0000, delta 0.0000 on 499 maps (map 2003145302 dropped from both arms: the boss timed out in v14's series) |
| G4 | **passes**: field 0.9816 against 0.9665, +0.0151 (CI +0.0092..+0.0210) |
| G5 | **passes**: rival 0.8414 against 0.8169, +0.0245 (CI +0.0122..+0.0368) |
| G3 | skipped (no `old`) |

The screen's numbers replicate: S 0.5473 → 0.5533, field +1.61 → +1.51 pp, rival +2.75 → +2.45 pp. S is below the 0.60
upload recommendation: an upload is an exception and the user's decision, as v10's (0.5764), v12's (0.5716) and v13's
(0.5958) were. By seat v14 scores 1026/292/682 on seat 0 and 895/292/813 on seat 1 against v13 (W/D/L).

The clock of this run (see Artifact for the laptop's standby that night): the in-process series ended by 01:33:25, before
it. v14's boss series ran 01:33:25-01:35:43 and Windows entered Modern Standby at 01:35:12, about when the boss process
timed out on map 2003145302 (the 367th of the 500, three threads); P2 drops such a map from both arms (1 of 500). v13's
boss series ran 01:35:43-01:37:50 inside the standby's first minutes, when the processes still ran, and has no failure.

## Artifact

`./gradlew submit` with no parameters (the defaults are v14): `build/submit/Player.java`, 95,590 characters (limit
100,000), 13 files, sha256 `e4c8eeec7fb4cfc010999efe18e1d630711f942bcb6b718031c872e5ccd27914`, build `62ad918b` (turn 0:
`TRAIN 6 2 10 13 9 1 8 11`), config `337e3891`; a second build and a clean export of the working tree through a temporary index (tree d2e1a1b0) rebuilt the same bytes.
The kept copy is `artifacts/e4c8eeec/Player.java` in the main checkout (outside Git).

| procCheck | games | wins | identical to `current` | timeouts | extra / late / stderr lines | shortest game | max first turn | max later turn |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `-Popp=v01 -Pgames=100 -PseedBase=5000`, the first run (01:39:28-01:41:29): **failed** | 200 | 199 | 199/200 | 1 | 0 / 2 / 0 | 12 turns | 247.8 ms | 63,710 ms |
| `-Popp=boss -Pgames=100 -PseedBase=6000` (01:41:29-01:43:57) | 200 | 200 | 200/200 | 0 | 0 / 0 / 0 | 48 turns | 353.8 ms | 7.9 ms |
| `-Popp=v01 -Pgames=100 -PseedBase=5000`, the repeat (ended 01:45:49) | 200 | 200 | 200/200 | 0 | 0 / 0 / 0 | 32 turns | 217.6 ms | 8.1 ms |

**The failed run was the laptop's standby, not the bot.** In the first run against v01 one game failed: seed 5079, seat 1,
the artifact was killed on turn 11 by the timeout and reported the turn itself (`SLOW turn 11 63709ms`, its two answer
lines arriving after the deadline); the other 199 games were identical to `current`. Windows' System log (provider
Kernel-Power) for that night:

| time | event |
| --- | --- |
| 01:35:12 | 506, "The system is entering Modern Standby, Reason: Idle Timeout" (the user was away; the session ran in the background) |
| 01:40:22 | 172, "Connectivity state in standby: Disconnected, Reason: Adaptive Connected Standby" |
| 01:41:17 | 172, "Connected", and 507, "The system is exiting Modern Standby, Reason: Input Mouse" |

`ProcessCheck` plays one game at a time, so one game was on the clock: the turn that ended at the wake began about
01:40:14, when the standby froze the processes, and lasted that minute. The boss series and the repeat of the v01 series
ran with the machine awake (no standby event after 01:41:17) and are clean, seed 5079 included. P2's re-read rule is
written for a timeout without a SLOW report; here the artifact's own clock saw the frozen turn, so both results are
recorded: one run failed by the standby, one clean repeat. The bot's own turns are nowhere near the limit: at most 37.2 ms
in the 13,000 in-process games of the final evaluation at 8 threads, at most 8.1 ms after the first turn as a process.

Logs: `replays/bench/exp-2026-11-10-overlap/submit.log`, `proccheck-v01.log` (the failed run), `proccheck-boss.log` and
`proccheck-v01-rerun.log`, in the worktree and copied to the main checkout.

## Not tried, for a later step

- The towers' sites chosen for overlap (the ladder takes the free site nearest the midpoint of the queen and home; a
  cluster whose towers cover each other would raise the cover everywhere).
- The first wave: half the contact HP of the mirror, and no spot to move to with two towers.
- The economy with the dead turns, and a rival whose barracks stand forward (the study's sections 7 and 8).
