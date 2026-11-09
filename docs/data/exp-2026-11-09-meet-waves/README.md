# Experiment 2026-11-09: meet every wave at a standing tower (Gold, over v12)

## Hypothesis and success bar (written before the comparisons)

The study of v12's 180 Gold games (`docs/data/study-2026-11-08-next-step/`) found two places where our queen meets the
enemy's knights worse than she needs to:
- **The first wave.** In the HP races v12 lost, the second tower stands after the contact (the contact at turn 24.5, the
  tower at 25.2); with the tower 3 or more turns before the contact 25 % of the races are lost and the wave costs 1.4 HP,
  with it 3 or more turns after, 63 % and 11.6 HP. The first tower gets 6 top-ups (median) before the queen leaves for
  the second site, 11 turns from the first tower to the second.
- **The midgame.** On turns 50-249 of the lost races 200 of 360 contact episodes began while the queen walked to a site
  (mostly to rebuild a mine), at 2.04 HP each against 0.97 for episodes met at an own tower; a knight was already within
  500 before 197 of the 199 (median 2 turns of warning).

The flags, both off by default (`current` plays as v12; `MainBotTest.withDefaultsCurrentPlaysAsV12`):
- `WAVE_TOWER_HP=H`: while fewer than 2 own towers stand and an enemy knight wave is coming (`knightWaveComing`: an
  enemy knight barracks trains or enemy knights are out), the touched own tower is topped up only while its HP is below H
  instead of `TOWER_HP` (700). The rejected `WAVE_TOWERS` and `TOPUP_FROM` skipped the first tower's top-ups altogether
  (it stayed at 200 HP, melted, and the queen died twice as often); here it keeps a partial pump.
- `LADDER_CLEAR=R` with `LADDER_CLEAR_MAX=N`: after the first-wave window (`firstWaveWindow`, WAVE_ANCHOR's) and once
  2 own towers stand, while an enemy knight is within R of the queen and none within `THREAT_RANGE` (250), the walk to the
  ladder's next site (and INCOME_LATE's, and the idle walk to the home tower) is replaced by a walk to her nearest own
  tower not under an enemy tower (a top-up when she touches it), for at most N holds a spell (N = 0: no cap); the spell
  ends when the knights are beyond R. The defence rule, the kiting, the flight and the touch rule are unchanged. The
  rejected early reactions (THREAT_RANGE 350/450, KITE_RANGE 500) changed the whole threat response from turn 0 and sent
  the queen away from her towers; this gates only the ladder's walk, after the first wave, toward a tower.

**Hypothesis.** Against the v12 mirror, `field` and `rival` on the development seeds 1 + 397 k, k < 500, both seats:
- `WAVE_TOWER_HP` puts the second tower up earlier relative to the first contact (`LocalProfile`'s new "second tower"
  line: the turn it stands minus the contact turn falls by 2-3) and lowers the first-wave loss, without raising the
  queen's deaths (the rejected family's failure) or lowering the summed tower HP at turn 50 much;
- `LADDER_CLEAR` lowers the contact episodes begun on a walk to a site on turns 50-249 and the HP they cost
  (`LocalProfile`'s new "midgame contacts" line), without delaying the ladder so much that the economy or the towers fall
  behind (towers and income at turns 100 and 150, gold mined);
- S against v12 rises above 0.50 for at least one arm, with deltas against `field` and `rival` of at least −1 pp.

**Success bar** (as in the meet-back experiment): on the development seeds S against v12 above 0.50 with its lower CI
above 0.50 for the arm taken further, deltas against `rival` and `field` of at least −1 pp, deaths per 1,000 maps not
above v12's, and the mechanism measures moving as predicted. Only then a P2 screen on a fresh block: screen-pv3 block 6,
seeds 1006000000 + 397 i. Several arms are compared on the development seeds, so the screen's block is used once for the
single arm (or the bundle of the two flags) chosen here.

## Tools

- `sweep.sh`, `sweep_table.py`: the arms against v12 (direct), `rival` and `field`, with v12's control series; S with the
  changed maps, deltas with CIs (`tools/bench_pair.py`). Raw reports in `replays/bench/exp-2026-11-09-meet-waves/dev/`
  (gitignored, main checkout).
- `profile.sh`, `profile_table.py`: `LocalProfile` of v12 and of each arm against the three opponents, 200 maps, both
  seats; `LocalProfile` gained two lines for this experiment (`cr.tools.LocalProfile`, 2026-11-09): the second tower's
  turn minus the first contact (median and mean over games with both), and the midgame contact episodes (turns 50-249)
  begun on a walk to a site, at an own tower or otherwise, with the HP each class costs to knights.
- Tests: `StrategyParamsTest.waveTowerHpCapsTheFirstTowersPumpWhileAWaveComes`,
  `StrategyParamsTest.ladderClearHoldsTheQueenAtHerTowerWhileAKnightIsNear`; the version identity
  `MainBotTest.withDefaultsCurrentPlaysAsV12`. The frozen snapshots `versions/v01-v12.properties` list the three new
  parameters at 0 (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults`). The full suite passed (2026-11-09, in the
  worktree, `./gradlew test`, 27 s).
- `screen.sh` (PV3 screen block 6, seeds 1006000000 + 397 i) and `eval.sh` (PV3 evaluation block 2, seeds
  2002000000 + 397 i): the P2 series and gates against v12, as in the meet-back experiment.

## Development sweep 1 (`sweep.txt`, raw reports in `dev/`)

Seeds 1 + 397 k, k < 500, both seats, in-process (8 threads, 50 ms); each arm against v12 (direct), `rival` and `field`,
with v12's own series as the controls (v12 scores 0.7610 against `rival`, 0.9390 against `field`). The table is
`sweep_table.py`'s.

| arm | S vs v12 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `WAVE_TOWER_HP=400` | 0.4740 (0.4430..0.5050) | −0.0310 (−0.0656..+0.0036) | −0.0260 (−0.0455..−0.0065) |
| `WAVE_TOWER_HP=500` | 0.5025 (0.4727..0.5323) | −0.0130 (−0.0471..+0.0211) | −0.0175 (−0.0366..+0.0016) |
| `WAVE_TOWER_HP=600` | 0.4990 (0.4695..0.5285) | −0.0090 (−0.0403..+0.0223) | −0.0010 (−0.0170..+0.0150) |
| `LADDER_CLEAR=400` | 0.5800 (0.5529..0.6071) | +0.0570 (+0.0265..+0.0875) | +0.0240 (+0.0079..+0.0401) |
| `LADDER_CLEAR=500` | **0.6025 (0.5745..0.6305)** | **+0.0665 (+0.0349..+0.0981)** | **+0.0330 (+0.0167..+0.0493)** |
| `LADDER_CLEAR=400,LADDER_CLEAR_MAX=15` | 0.5800 (0.5529..0.6071) | +0.0570 (+0.0265..+0.0875) | +0.0240 (+0.0079..+0.0401) |
| `LADDER_CLEAR=500,LADDER_CLEAR_MAX=15` | 0.6025 (0.5745..0.6305) | +0.0655 (+0.0339..+0.0971) | +0.0320 (+0.0155..+0.0485) |

No candidate failure in any arm (no error, timeout, slow turn or extra line).
- **The first-wave cap does not help locally.** `WAVE_TOWER_HP` at 400 loses to everyone, at 500 and 600 it is level with
  v12 and 1-3 pp down against `rival` and `field`. The third variant of the family fails like the first two; whether the
  second tower stood earlier is in the profiles below.
- **The ladder gate passes the bar at once.** `LADDER_CLEAR=500` wins 0.60 against v12 with the lower bound 0.575, and
  gains against both other opponents: +6.7 pp against `rival`, +3.3 pp against `field`. 400 is a little weaker on all
  three. The spell cap of 15 changes nothing: the spells are short, since the knights that come within 500 either reach
  the queen (then the threat rules take over) or die to the towers within a few turns.
- The gate changes nearly every map (480-484 of 500 against the mirror), so it is a strategy change, not a targeted fix.

## Development sweep 2: the radius (`sweep.txt`, the same controls)

| arm | S vs v12 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `LADDER_CLEAR=600` | 0.6050 (0.5759..0.6341) | +0.0495 (+0.0164..+0.0826) | +0.0360 (+0.0182..+0.0538) |
| `LADDER_CLEAR=700` | 0.5720 (0.5416..0.6024) | +0.0455 (+0.0131..+0.0779) | +0.0260 (+0.0092..+0.0428) |

The plateau is 400-600 (S 0.58, 0.60, 0.605); 700 falls off against v12. **The candidate is `LADDER_CLEAR=500`**, the
middle of the plateau and the best arm against `rival`; it is also the radius the study measured the warning with. No
spell cap (`LADDER_CLEAR_MAX=0`): the cap did nothing at 15.

## Profiles (`profiles.txt`, `profile_table.py`; raw profiles in `profiles/`)

`LocalProfile`, 200 maps 1 + 397 k, both seats, against v12, `rival` and `field`:

| arm vs opponent | win % | queen deaths | midgame episodes begun on a walk: a game / HP a game / HP each | begun at a tower: a game / HP / each | our queen's HP to knights, turns 50-249 | the enemy queen's to our knights, 50-249 | gold mined |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v12 vs v12 | 47.0 | 21 | 3.3 / 4.9 / 1.5 | 3.0 / 2.2 / 0.7 | 8.7 | 8.7 | 812 |
| `LADDER_CLEAR=500` vs v12 | 57.8 | 18 | 0.6 / 1.9 / 3.0 | 6.2 / 3.1 / 0.5 | 5.7 | 8.2 | 807 |
| v12 vs rival | 76.0 | 39 | 4.5 / 5.9 / 1.3 | 4.1 / 4.1 / 1.0 | 12.8 | 16.3 | 730 |
| `LADDER_CLEAR=500` vs rival | 81.3 | 30 | 0.8 / 1.6 / 2.0 | 8.9 / 6.2 / 0.7 | 9.3 | 15.2 | 725 |
| v12 vs field | 94.3 | 13 | 8.0 / 4.6 / 0.6 | 3.9 / 1.3 / 0.3 | 6.8 | 24.3 | 580 |
| `LADDER_CLEAR=500` vs field | 97.8 | 3 | 1.0 / 0.3 / 0.3 | 11.9 / 1.3 / 0.1 | 1.8 | 21.6 | 573 |

- **The mechanism moves as predicted.** The contact episodes begun on a walk fall from 3.3-8.0 a game to 0.6-1.0, and the
  HP they cost from 4.6-5.9 to 0.3-1.9; the waves are met at a tower instead (episodes there 3.0-4.1 → 6.2-11.9 at
  0.1-0.7 HP each). Our queen loses 3.0-5.0 HP less to knights on turns 50-249 against every opponent, and dies less often
  (21 → 18, 39 → 30, 13 → 3 per 400 games).
- **The ladder is not starved.** Towers and income at turns 50 and 100 are unchanged (3 / 4 towers, the queen's HP 60),
  the gold mined is within 1 %, the enemy queen loses 0.5-2.7 HP less to our knights (the delayed walks cost a little
  army tempo), the summed tower HP at turn 50 is 2-7 % lower.
- `LADDER_CLEAR=400` shows the same pattern a little weaker (`profiles.txt`).
- **`WAVE_TOWER_HP=500` moved its mechanism too, without a score:** the second tower stands 2 turns earlier relative to
  the contact (0 → −2 in the mirror, −1 → −2 against `rival`, +3 → +1 against `field`), the first-wave loss falls
  (4.4 → 3.3, 4.0 → 3.0, 5.7 → 4.2 HP) and our queen's deaths in the mirror halve (21 → 9), but S against v12 is 0.50 and
  the deltas against `rival` and `field` −1.3 and −1.8 pp: the summed tower HP at turn 50 is 4-7 % lower and the midgame
  flow is not better. The local first wave is weak (4.4 HP against Gold's 6.9-10.2), so the panel cannot resolve the
  rule's value; it stays off and is recorded as a candidate for an arena-judged bundle, not for P2 on its own.

## P2 screen (`screen.sh`; summaries `g1.json`, `g2.json`, `g4.json`, `g5.json`; raw reports in `screen/`)

Candidate `current?LADDER_CLEAR=500` against the control v12 (the local champion and the selected version), PV3 screen
block 6: seeds 1006000000 + 397 i, i < 2000, both seats; `boss` on the first 500 maps (3 threads, 200 ms). Commit 06cfc4c
with this experiment's uncommitted changes (the reports record the status).

| gate | result |
| --- | --- |
| G1 | **passes**: S 0.5960 (95 % CI 0.5817..0.6103) against v12, W/D/L 2202/364/1434; 1931 of 2000 maps differ from v12's mirror (a strategy change) |
| G2 | passes: boss 0.9990 against 0.9990, delta 0.0000 |
| G4 | delta **passes**: field 0.9690 against 0.9180, +0.0510 (CI +0.0414..+0.0606); G0 see below |
| G5 | **passes**: rival 0.8140 against 0.7392, +0.0747 (CI +0.0588..+0.0907) |
| G3 | skipped: the code of `old` is still missing |
| G0 | the candidate had no error, timeout or extra line in its 13,000 games and **one slow turn**, in game 1006278297 of the field series (8 threads; the candidate's longest turn there 52.7 ms, mean 0.06 ms; v12 itself had a 64.1 ms turn in the mirror series). The field series was repeated at 2 threads (`field-cand-rerun.json`, `g4-rerun.json`): see below |

The development numbers replicate on the fresh block: S 0.6025 → 0.5960, rival +6.7 → +7.5 pp, field +3.3 → +5.1 pp. By
seat, the candidate scores 1156/184/660 on seat 0 and 1046/180/774 on seat 1 against v12 (W/D/L), the same tilt as v12's
mirror (995 against 831 wins for player 0).

**G0 by the quiet repeat** (the practice of the waves experiment, `docs/data/exp-2026-11-06-waves/rerun.sh`): the field
series was repeated with 2 threads, the same seeds and 50 ms (`screen/field-cand-rerun.json`). `same_games.py` of the
late-hold experiment: 4000 games in both, 0 differ; the repeat has no slow turn (the candidate's longest turn 11.5 ms,
mean 0.02 ms). `g4-rerun.json`: G0 pass, delta +0.0510 (CI +0.0414..+0.0606), pass. The other series had no candidate
failure in the first run.

## Verdict

`LADDER_CLEAR=500` passes P2's screen (G0 by the quiet repeat, G1, G2, G4, G5; G3 skipped): S 0.5960 against v12 on
the fresh block, +5.1 pp against `field`, +7.5 pp against `rival`, boss unchanged. By P2 the passing candidate is the local
champion. What it does: after the first-wave window and with two towers up, no walk to a new site while an enemy knight is
within 500; the queen goes to her nearest covered tower and tops it up instead, and the walk resumes once the knights are
gone. Locally that removes four fifths of the midgame contacts begun on a walk (3.3-8.0 → 0.6-1.0 a game), saves our queen
3-5 HP of knight damage on turns 50-249 against every opponent, and cuts her deaths (21 → 18, 39 → 30, 13 → 3 per 400
games), at a cost of 0.5-2.7 HP less damage by our knights on the enemy queen.

The first-wave cap `WAVE_TOWER_HP` stays off: the mechanism moved (the second tower 2 turns earlier relative to the
contact, the mirror's deaths halved) but S against v12 is 0.50 and `field` and `rival` lose 1-2 pp.

**The user's decision (2026-11-09, "Заморозим v13"):** `LADDER_CLEAR=500` is the `current` default and v13 is frozen (below);
the final evaluation, the artifact and the upload follow. S 0.5960 is just below the 0.60 upload recommendation, as v12's
0.5716 was; the arena rewarded v12 with +3.3 points.

## v13 (2026-11-09)

- `Params`: `LADDER_CLEAR = 500` by default (`LADDER_CLEAR_MAX = 0`, `WAVE_TOWER_HP = 0`).
- `cr.bot.v13.GreedyBot`, `RoutePlanner` and `ApproachPlanner`: copies of `current` at the freeze (the package and class
  names, the frozen header and `name()` changed; `queenSteps()` left out, as in the earlier copies).
- `versions/v13.properties`: v12's snapshot with `LADDER_CLEAR=500`. `BotFactory`, `LocalGames` (v13 the default
  opponent), `visualizer/local.js`, `docs/visualizer.md` and `.ai/project.md` (resynced) know v13.
- Tests: `MainBotTest.withDefaultsCurrentPlaysAsV13` (fixture seeds and PV3 screen block 6's first maps),
  `withV12ParamsCurrentPlaysAsV12` (`LADDER_CLEAR=0`), the chain v12 → v13 in `BotFactoryTest`, `LocalGamesTest`'s bot
  count; the full suite green (173 tests, 3 corpus tests skipped for want of the arena replays in the worktree).
- `docs/bot-architecture.html` is v13's: the decision flow (step 2b), the pseudocode, the target choice, the versions table,
  the gaps and the directions.

## Final evaluation (`eval.sh`; summaries `eval-g1.json`, `eval-g2.json`, `eval-g4.json`, `eval-g4-rerun.json`, `eval-g5.json`; raw reports in `eval/`)

v13 against the selected version v12 on PV3 evaluation block 2: seeds 2002000000 + 397 i, i < 2000, both seats; `boss`
on the first 500 maps (3 threads, 200 ms). The ledger's next evaluation block is 3.

| gate | result |
| --- | --- |
| G0 | v13 had no error, timeout or extra line in its 13,000 games; 5 slow turns in the field series at 8 threads (v13's longest 93.3 ms; v12's own control series had 2 slow turns with a 100.1 ms turn, its mirror 1): the series repeated at 2 threads holds the same 4000 games (`same_games.py`: 0 differ) with no slow turn (longest 5.7 ms), so **G0 passes by the quiet repeat** (`eval-g4-rerun.json`) |
| G1 | **passes**: S 0.5958 (95 % CI 0.5819..0.6096) against v12, W/D/L 2190/386/1424, 1942 of 2000 maps changed |
| G2 | passes: boss 1.0000 against 1.0000, delta 0.0000 |
| G4 | **passes**: field 0.9673 against 0.9239, +0.0434 (CI +0.0342..+0.0526) |
| G5 | **passes**: rival 0.8170 against 0.7509, +0.0661 (CI +0.0505..+0.0817) |
| G3 | skipped (no `old`) |

The screen's numbers replicate: S 0.5960 → 0.5958, field +5.1 → +4.3 pp, rival +7.5 → +6.6 pp. S is below the 0.60
upload recommendation by 0.004, as v12's 0.5716 was by 0.03: an upload is an exception and the user's decision. By seat
the candidate scores 1148/181/671 on seat 0 and 1042/205/753 on seat 1 against v12 (W/D/L).

## On the arena (Gold, 2026-11-09)

Two uploads of the kept copy by the user (one IDE game against the boss first), followed by `tools/arena.py follow v13
--selected v12`: 21.95 (rank 18/348) and 21.65 (rank 22/348), mean 21.80 against v12's 20.36; performance 21.75 ± 0.43
against 20.27 ± 0.45 at Gold's refitted scale 2.71. cgAttribute SAME 180/180. P1: not clearly worse (+1.44 against −M
−2.46), **v13 selected**; the section and the tables are in `docs/arena-uploads.md`. The first follow stopped once because
a new label defaults to the main checkout's `build/submit/Player.java`, which still held v12's artifact; the rerun with
`--artifact` resumed (same file). The arena's view of the rule (walking-caught episodes, deaths) is not yet measured on
these 180 games; the DeathTrace classes of `docs/data/study-2026-11-08-next-step/next_step.py` can do it.

## Artifact

`./gradlew submit` with empty PARAMS (v13's defaults); a second build gave the same bytes:
- `85897d90c377ada9354eb5238081aa16c3bfb6c6de3bca4a88ae089d6bc9cddd`, 89,252 characters, 13 files;
- build `a19a87ca` (turn 0: `TRAIN 10 1 9 10 8 7 12 10`), config `0c433665`.

procCheck vs v01, 100 maps @5000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr
lines, max first turn 199.7 ms, later 8.0 ms. procCheck vs boss, 100 maps @6000: 200/200 identical to current, 200 wins,
0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 148.0 ms, later 6.1 ms. The kept copy is
`artifacts/85897d90/Player.java` (gitignored, the main checkout). Logs: `replays/bench/exp-2026-11-09-meet-waves/submit.log`,
`proccheck-v01.log`, `proccheck-boss.log`.
