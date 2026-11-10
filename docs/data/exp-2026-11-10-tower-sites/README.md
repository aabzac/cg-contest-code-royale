# Experiment 2026-11-10: the towers' sites chosen for overlap (Gold, over v14)

## Hypothesis and success bar (written before the comparisons)

v14's rule (`HOLD_OVERLAP=300`, `IDLE_OVERLAP=1`; `docs/data/exp-2026-11-10-overlap/`) makes the queen wait for the
knights where the most own towers cover her. It only uses the overlaps the ladder happens to produce: the ladder
(`MainBot.nextSite`) takes the free site nearest the midpoint of the queen and her home corner (in the first-wave
window, nearest `WAVE_ANCHOR`'s point) and does not look at the towers that stand. The measurement of v14's 180 Gold
games (`docs/data/study-2026-11-10-v14-cover/`) found the rule's ceiling there: three or more own towers cover her on
54 % of the contact turns from turn 50, and the games still lost by death are those with 2.8 towers and no line to
stand in. "The towers' sites chosen for overlap" is the first of the directions the overlap experiment left untried.
The task was set by the session that keeps the project's plan (the brief of 2026-11-10, after the merge of 3d4d4d3).

**What v14's ladder does now** (v14's own profiles, taken before the flags were designed: `sites.txt`, 200 maps
1 + 397 k, both seats, against v14 / `rival` / `field`):

| | v14 | `rival` | `field` |
| --- | --- | --- | --- |
| own towers within 400 of the new tower when it is built: the second, the third, the fourth | 0.84, 1.15, 1.29 | 0.81, 1.13, 1.34 | 0.89, 1.11, 1.35 |
| ... within 500 | 0.93, 1.49, 1.97 | 0.90, 1.48, 1.98 | 0.95, 1.56, 1.93 |
| towers from the second on built inside another own tower's range | 92 % | 83 % | 93 % |
| our turns 50-99 / 100-149 with a spot under three or more own towers within 300 of her | 83 % / 94 % | 83 % / 89 % | 88 % / 89 % |
| games in which she stands under three or more by turn 60 / 80 | 67 % / 82 % | 68 % / 82 % | 78 % / 86 % |
| contact turns from turn 50 under three or more | 65 % | 48 % | 65 % |
| the gate's turns from turn 50 under three or more | 78 % | 64 % | 75 % |

So the midpoint rule already clusters the towers: the second stands within 400 of the first in five games of six, and
a spot under three towers is within five steps of her on most turns from 50. What is left to the choice of the site is
the third and the fourth tower (1.1-1.3 of the 2 and 3 towers they could stand next to) and the 6-17 % of the turns
without such a spot.

The flags, both off by default (`current` plays as v14; `MainBotTest.withDefaultsCurrentPlaysAsV14`). They act only on
the ladder's tower-phase target (`nextSite`, for the queen and for ROUTE's landing point), once an own tower stands
or is planned; the mines, the barracks, the first tower, the touch rule and the defence rule's towers are unchanged:
- `SITE_COVER=W`: the target is the free site with the smallest distance to the ladder's point (the midpoint of the
  walker and home, or the first-wave anchor) less W for each own tower whose range covers the site's centre, up to two
  (a third covering tower adds nothing: under three towers a contact turn costs 0.17 HP, under four 0.16). A tower
  being built counts with the range it has at `TOWER_HP`.
- `SITE_NEAR=P`: the target is the free site with the smallest distance to the ladder's point plus P % of its distance
  to the second-nearest own tower (the nearest while one stands), so the new tower closes a tight triangle.

The arms: `SITE_COVER` 100, 200, 400 and 1000 (1000 is "the most covering towers first"), `SITE_NEAR` 50, 100 and 200,
each against v14 (direct), `rival` and `field`. If both families meet the bar, their best values are also run together.

**Hypothesis.** On the development seeds, both seats:
- the towers stand closer together: more own towers within 400 of a new tower when it is built (the third and the
  fourth above all);
- the queen gets a place under three towers earlier: more games in which she stands under three or more by turns 60
  and 80, more of the turns 50-99 with such a spot within 300 of her;
- the share of the contact turns from turn 50 under three or more own towers rises, and the HP she loses to knights
  from turn 50 falls;
- the ladder keeps its pace: own towers at turns 100 and 150, the second barracks (the share of games that build it
  and its turn) and the income at turns 100 and 150 are not lower;
- S against v14 rises above 0.50, with deltas against `rival` and `field` of at least −1 pp.

**The alternative**, which the same series can show: the ladder's overlap is already what the board gives (the table
above), and a site farther from the midpoint costs walking turns or leaves the towers covering less ground. Then the
links at birth rise, the cover on contact turns barely moves, the towers stand later and S against v14 is level or
below 0.50.

**Success bar** (that of the open-ground and gate-touch experiments). On the four development sets pooled (seeds 1, 3,
5 and 7 + 397 k, k < 500; 2000 maps, both seats), for the arm taken further, which is the arm with the highest pooled S
against v14 among those that meet the bar:
- S against v14 with its lower 95 % bound above 0.50;
- deltas against `rival` and `field` of at least −1 pp;
- the queen's deaths per 400 profile games not above v14's against each opponent (10, 20 and 2);
- the mechanism moving as predicted: the share of the contact turns from turn 50 under three or more rises and the
  HP she loses to knights from turn 50 does not rise;
- the ladder's pace holding: own towers at turns 100 and 150, the second barracks and the income at turn 150 not
  established lower (the 95 % interval of the arm minus v14 on the same maps does not lie below zero).

Only then a P2 screen on a fresh block: screen-pv3 block 8, seeds 1008000000 + 397 i, used once, for the single arm
chosen here. Otherwise the flags stay in the code, off, and v14's ladder stands.

## Tools

- `sweep.sh`: the identity series, then the arms against v14 (direct), `rival` and `field` on the four development
  sets with v14's control series (it calls the open-ground experiment's `sweep.sh` once a set). The table is the
  open-ground experiment's `sweep_table.py` with this experiment's directory
  (`python -B -I docs/data/exp-2026-11-10-open-ground/sweep_table.py replays/bench/exp-2026-11-10-tower-sites` →
  `sweep.txt`): S with the changed maps and the deltas with their intervals for every set and for the sets pooled, and
  the arms against each other on the same maps (`tools/bench_pair.py`). Raw reports in
  `replays/bench/exp-2026-11-10-tower-sites/dev-<base>/` (gitignored).
- The open-ground experiment's `profile.sh` (`OUT=replays/bench/exp-2026-11-10-tower-sites/profiles`): the study's probe
  in its local mode (`docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java`, unchanged), 200 maps 1 + 397 k, both
  seats, of v14 and of each arm against v14, `rival` and `field`. `sites.py` reads them (`sites.txt`): the cover on the
  contact turns and the HP, when the queen first stands under three towers, the links of each tower at its birth, the
  ladder's pace and the economy, and every arm minus v14 on the same maps with bootstrap intervals.
- `screen.sh`: the P2 series and gates against v14 on PV3 screen block 8 (summaries `g1.json`, `g2.json`, `g4.json`,
  `g5.json`).
- `eval.sh` and `rerun.sh`, after the user's decision: the freeze's identity series, the final evaluation of v15 against
  v14 on PV3 evaluation block 4 and the quiet repeats of its two series with slow turns (summaries `eval-g*.json`).
- Code: `cr.bot.Params.SITE_COVER` and `SITE_NEAR` (0 by default) and `MainBot.nextSite`, which takes one more argument,
  the site of a tower being built (`ApproachPlanner.nextTarget` passes it for the planner's next target).
- Tests: `StrategyParamsTest.siteCoverAndSiteNearChooseTheTowersSites` (both flags on a computed position, on both
  sides of the board: the weights at which the target changes, the cap of two covering towers, one tower and none, the
  mine phase and the first barracks unchanged, the first-wave anchor, the planner's next target); the version identity
  `MainBotTest.withDefaultsCurrentPlaysAsV14`; the frozen snapshots `versions/v01-v14.properties` list both parameters
  at 0 (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults`).

## Development sweep (`sweep.txt`, raw reports in `dev-1`, `dev-3`, `dev-5`, `dev-7`)

Seeds 1, 3, 5 and 7 + 397 k, k < 500, both seats, in-process (8 threads, 50 ms), 2026-11-10 22:48-23:10; each arm
against v14 (direct), `rival` and `field`, with v14's own series as the controls (v14 scores 0.8260-0.8610 against
`rival` and 0.9805-0.9905 against `field` on the four sets). Commit 3d4d4d3 with the uncommitted flags (the reports
carry the status). Before the sweep `current` at its defaults repeated v14's mirror game for game on the 500 maps of
the first set (`identity-current-v14.json`, `bench_pair.py direct --mirror`: 0 maps changed, no failure).

| arm | S vs v14, the four sets | pooled, 2000 maps (95 % CI) | maps changed against v14's mirror | delta vs rival, pooled | delta vs field, pooled |
| --- | --- | --- | --- | --- | --- |
| `SITE_COVER=100` | 0.5480, 0.5545, 0.5455, 0.5455 | 0.5484 (0.5358..0.5609) | 1536 | +0.0344 (+0.0225..+0.0462) | +0.0024 (−0.0015..+0.0063) |
| `SITE_COVER=200` | 0.5600, 0.5455, 0.5345, 0.5330 | 0.5433 (0.5297..0.5568) | 1760 | +0.0369 (+0.0239..+0.0499) | +0.0022 (−0.0021..+0.0066) |
| `SITE_COVER=400` | 0.5540, 0.5495, 0.5260, 0.5345 | 0.5410 (0.5270..0.5550) | 1802 | +0.0418 (+0.0284..+0.0551) | +0.0025 (−0.0019..+0.0069) |
| `SITE_COVER=1000` | 0.5530, 0.5610, 0.5345, 0.5355 | 0.5460 (0.5320..0.5600) | 1813 | +0.0441 (+0.0307..+0.0575) | +0.0037 (−0.0005..+0.0080) |
| `SITE_NEAR=50` | 0.5565, 0.5710, 0.5630, 0.5340 | **0.5561 (0.5426..0.5697)** | 1725 | +0.0345 (+0.0217..+0.0473) | +0.0011 (−0.0030..+0.0052) |
| `SITE_NEAR=100` | 0.5540, 0.5595, 0.5440, 0.5190 | 0.5441 (0.5296..0.5587) | 1857 | +0.0466 (+0.0335..+0.0597) | +0.0010 (−0.0032..+0.0052) |
| `SITE_NEAR=200` | 0.5355, 0.5700, 0.5345, 0.5150 | 0.5387 (0.5237..0.5538) | 1888 | +0.0560 (+0.0426..+0.0694) | +0.0015 (−0.0029..+0.0059) |
| `SITE_COVER=100,SITE_NEAR=50` (the two best together, as planned) | 0.5565, 0.5710, 0.5500, 0.5240 | 0.5504 (0.5363..0.5645) | 1823 | +0.0488 (+0.0353..+0.0622) | +0.0008 (−0.0035..+0.0050) |
| `SITE_COVER=50` (added after the first sweep) | 0.5410, 0.5425, 0.5375, 0.5275 | 0.5371 (0.5265..0.5478) | 1200 | +0.0149 (+0.0042..+0.0256) | +0.0010 (−0.0025..+0.0045) |
| `SITE_NEAR=25` (added after the first sweep) | 0.5565, 0.5490, 0.5325, 0.5330 | 0.5427 (0.5308..0.5547) | 1483 | +0.0205 (+0.0087..+0.0323) | +0.0003 (−0.0034..+0.0039) |

- **Every arm passes the score part of the bar**: pooled S 0.537-0.556 against v14 with lower bounds of 0.524-0.543,
  +1.5 to +5.6 pp against `rival`, +0.0 to +0.4 pp against `field` (v14 already scores 0.98-0.99 there). Every arm
  scores above 0.50 on each of the four sets (the lowest set scores are 0.5150 and 0.5190, the two heaviest
  `SITE_NEAR` weights on the fourth set).
- **The weight trades the mirror for `rival`.** Against v14 the lightest weights of the first sweep were the best
  (0.548 at `SITE_COVER=100`, 0.556 at `SITE_NEAR=50`), against `rival` the gain grows with the weight (+3.4 → +4.4 pp
  and +3.5 → +5.6 pp). `SITE_NEAR=200` minus `SITE_NEAR=50` on the same maps: −1.7 pp (−3.3..−0.2) against v14, +2.2 pp
  (+1.0..+3.3) against `rival`.
- **Two arms were added after the first sweep** because the best mirror scores stood at the lower edge of the grid:
  half the weight of each family. Both are lower against v14 and against `rival` (`SITE_NEAR=25` minus `SITE_NEAR=50`:
  −1.3 pp, −0.0263..−0.0004, and −1.4 pp), so the edge is bracketed: the mirror's best is at about 100 and 50.
- **The two measures act alike.** `SITE_NEAR=50` and `SITE_COVER=100` differ by +0.8 pp (−0.6..+2.2) against v14 and
  by nothing against `rival`; together (`SITE_COVER=100,SITE_NEAR=50`) they give what a heavier single weight gives:
  0.5504 against v14 (−0.6 pp against `SITE_NEAR=50` alone, not established) and +4.9 pp against `rival` (+1.4 pp over
  `SITE_NEAR=50` alone, +0.5..+2.3).
- Slow turns of the arms (1-13 over an arm's twelve series, host noise at 8 threads; v14 has 1 and 8 in two of its
  own control series) count for nothing in a development sweep: the games do not depend on the clock.

**The arm taken further: `SITE_NEAR=50`**, the highest pooled S against v14, by the rule written before the series.

## Profiles (`sites.txt`; the probe's csv files, 935 MB, stay in the worktree only: `profile.sh` rebuilds them)

`ArenaProbe local`, 200 maps 1 + 397 k, both seats (400 games), against v14, `rival` and `field`, of v14 and of all ten
arms (2026-11-10 23:09-23:28).

**`SITE_NEAR=50`, the arm minus v14 on the same maps** (95 % interval of a bootstrap over maps; v14's value in brackets):

| a game | against v14 | against `rival` | against `field` |
| --- | --- | --- | --- |
| own towers within 400 of a new tower when it is built, from the second on | +0.12 (+0.09..+0.15) [1.29] | +0.18 (+0.12..+0.24) [1.15] | +0.17 (+0.14..+0.21) [1.18] |
| ... the third tower | 1.15 → 1.57 | 1.13 → 1.59 | 1.11 → 1.50 |
| games in which she stands under three or more by turn 60, points | +17.5 (+12.0..+23.0) [67.0] | +16.5 (+11.0..+22.0) [68.0] | +11.0 (+6.2..+15.5) [78.5] |
| turns 50-99 with a spot under three or more within 300 of her, points | +8.5 (+5.3..+11.8) [83.2] | +8.0 (+4.1..+12.0) [82.6] | +7.2 (+3.7..+10.8) [87.5] |
| share of the contact turns from turn 50 under three or more, points | **+13.2 (+9.1..+17.3) [65.0]** | **+7.8 (+3.5..+12.1) [47.6]** | **+12.1 (+7.7..+16.3) [64.7]** |
| contact turns from turn 50 under one or two own towers | −3.86 (−5.12..−2.62) [8.73] | −3.82 (−5.86..−1.73) [17.51] | −4.41 (−6.03..−2.90) [10.65] |
| HP to knights from turn 50 | **−2.33 (−3.37..−1.29) [5.38]** | **−1.65 (−3.22..−0.13) [6.53]** | **−0.69 (−1.31..−0.20) [1.50]** |
| HP to knights before turn 50 | +0.08 (−0.24..+0.44) [4.33] | −0.23 (−0.57..+0.13) [3.87] | +0.14 (−0.03..+0.33) [5.75] |
| queen deaths per 400 games | −5 (−11..+1) [10] | −11 (−20..−2) [20] | −2 (−5..0) [2] |
| own towers at turn 100 | +0.10 (+0.01..+0.18) [4.03] | +0.18 (+0.09..+0.26) [3.71] | +0.19 (+0.11..+0.29) [3.82] |
| own towers at turn 150 | +0.14 (+0.06..+0.22) [5.11] | +0.16 (+0.05..+0.27) [4.28] | +0.19 (+0.09..+0.29) [4.04] |
| games with the second barracks, points | +2.5 (+0.8..+4.5) [94.5] | +5.2 (+1.5..+9.0) [76.5] | +6.8 (+1.5..+12.2) [45.8] |
| the turn of the second barracks | −4.8 (−8.1..−1.7) [113.0] | −4.3 (−8.2..−0.3) [120.1] | −3.9 (−11.1..+3.3) [106.6] |
| income at turn 150 | +0.05 (−0.14..+0.24) [3.59] | +0.14 (−0.08..+0.35) [3.52] | +0.10 (−0.08..+0.29) [2.13] |
| the enemy queen's HP to our knights from turn 50 | +0.45 (−0.74..+1.58) [5.38] | +0.96 (−0.41..+2.33) [14.98] | +1.80 (+0.52..+3.06) [21.47] |
| turns on a walk to a new site | −0.44 (−1.88..+0.99) [47.92] | −1.07 (−2.30..+0.21) [42.16] | +0.68 (−0.32..+1.79) [32.46] |

- **The mechanism moves as predicted, against all three opponents.** The towers stand closer (the third has 1.5-1.6
  own towers within 400 when it is built, v14's 1.1); she stands under three towers by turn 60 in 84-90 % of the games
  (67-78 %); on the contact turns from turn 50 three or more cover her on 78 %, 55 % and 77 % (65 %, 48 %, 65 %), with
  four contact turns a game fewer under one or two; she loses 2.3, 1.7 and 0.7 HP a game less to knights from turn 50,
  every interval below zero, and dies less often (5, 9 and 0 per 400 games against 10, 20 and 2).
- **The ladder does not pay for it; it is slightly faster.** 0.10-0.19 more own towers stand at turns 100 and 150,
  the second barracks (which waits for four towers) is built in more games and four to five turns earlier, the income
  is not lower, and the walks to new sites take as many turns as before. The alternative written before the series (a
  site farther from the midpoint costs the pace) does not hold.
- **The first wave costs the same** (the HP before turn 50: +0.1, −0.2, +0.1, no interval off zero): the second tower
  already stood beside the first in five games of six, and the rule changes the third and the fourth most.

**The dose, all arms** (`sites.txt` has every measure): the share of the contact turns from turn 50 under three or
more / the HP to knights from turn 50 a game / queen deaths per 400 games / own towers at turn 100, against each
opponent:

| arm | against v14 | against `rival` | against `field` |
| --- | --- | --- | --- |
| v14 | 65 % / 5.38 / 10 / 4.03 | 48 % / 6.53 / 20 / 3.71 | 65 % / 1.50 / 2 / 3.82 |
| `SITE_COVER=50` | 71 % / 4.08 / 6 / 4.07 | 51 % / 5.82 / 14 / 3.77 | 73 % / 1.15 / 0 / 3.89 |
| `SITE_COVER=100` | 74 % / 3.82 / 3 / 4.15 | 56 % / 4.05 / 6 / 3.82 | 74 % / 1.19 / 1 / 3.94 |
| `SITE_COVER=200` | 78 % / 2.96 / 7 / 4.10 | 57 % / 4.11 / 6 / 3.84 | 77 % / 1.06 / 1 / 3.98 |
| `SITE_COVER=400` | 79 % / 2.70 / 6 / 4.10 | 57 % / 4.43 / 12 / 3.78 | 78 % / 0.94 / 1 / 3.94 |
| `SITE_COVER=1000` | 81 % / 2.27 / 5 / 4.09 | 58 % / 4.30 / 8 / 3.77 | 78 % / 1.03 / 1 / 3.94 |
| `SITE_NEAR=25` | 73 % / 4.09 / 7 / 4.14 | 52 % / 5.83 / 15 / 3.80 | 74 % / 1.21 / 1 / 3.94 |
| **`SITE_NEAR=50`** | 78 % / 3.04 / 5 / 4.13 | 55 % / 4.88 / 9 / 3.88 | 77 % / 0.81 / 0 / 4.01 |
| `SITE_NEAR=100` | 79 % / 3.63 / 9 / 4.20 | 58 % / 4.75 / 10 / 3.94 | 79 % / 0.92 / 2 / 4.03 |
| `SITE_NEAR=200` | 80 % / 3.30 / 9 / 4.19 | 59 % / 4.83 / 13 / 3.92 | 81 % / 1.12 / 2 / 4.05 |
| `SITE_COVER=100,SITE_NEAR=50` | 79 % / 3.34 / 9 / 4.16 | 58 % / 4.49 / 7 / 3.88 | 78 % / 0.90 / 1 / 3.99 |

The cover rises with the weight in both families and flattens at about 80 % in the mirror and 58 % against `rival`;
no arm has more deaths than v14 against any opponent, and none has fewer towers at turn 100.

## P2 screen (`screen.sh`; summaries `g1.json`, `g2.json`, `g4.json`, `g5.json`; raw reports in `screen/`)

Candidate `current?SITE_NEAR=50` against the control v14 (the local champion and the selected version), PV3 screen
block 8: seeds 1008000000 + 397 i, i < 2000, both seats; `boss` on the first 500 maps (3 threads, 200 ms). 2026-11-10
23:36-23:44, commit 3d4d4d3 with this experiment's uncommitted changes (the reports record the status). The block was
reserved in `docs/data/seed-ledger.json` before the run.

| gate | result |
| --- | --- |
| G0 | **passes**: the candidate had no error, timeout, slow turn or extra line in its 13,000 games (its longest turn 28.2 ms at 8 threads, 20.9 ms beside the boss process) |
| G1 | **passes**: S 0.5425 (95 % CI 0.5292..0.5558) against v14, W/D/L 1828/684/1488; 1731 of 2000 maps differ from v14's mirror (a strategy change) |
| G2 | passes: boss 0.9985 against 0.9985, delta 0.0000 (251 of 500 maps changed; no boss timeout) |
| G4 | **passes**: field 0.9921 against 0.9840, +0.0081 (CI +0.0040..+0.0122) |
| G5 | **passes**: rival 0.8862 against 0.8539, +0.0324 (CI +0.0195..+0.0453) |
| G3 | skipped: the code of `old` is still missing |

The development numbers replicate on the fresh block: S 0.5561 → 0.5425 (the arm was the best of ten on the
development maps, so some of the difference is selection), rival +3.45 → +3.24 pp, field +0.11 → +0.81 pp. By seat
the candidate scores 968/340/692 on seat 0 and 860/344/796 on seat 1 against v14 (W/D/L), the mirror's tilt (v14
against v14 on the block: 850 against 777 wins for player 0).

## Verdict

**`current?SITE_NEAR=50` meets the bar written before the series and passes P2's screen** (G0, G1, G2, G4, G5; G3
skipped): S 0.5425 against v14 on the fresh block, +3.2 pp against `rival`, +0.8 pp against `field`, boss unchanged.
By P2 the passing candidate is the local champion; **the default and a freeze await the user's decision**, and until
then `current` plays as v14 with both flags at 0.

What it does: from the second tower on, the ladder takes the free site by its distance to the ladder's point plus half
its distance to the second-nearest own tower (the nearest while one stands), instead of the distance to the ladder's
point alone. The towers then stand closer to each other; the queen has a place under three towers earlier and stands
under three or more on 55-78 % of the contact turns from turn 50 instead of 48-65 %, loses 0.7-2.3 HP a game less to
knights from turn 50 and dies less often, and the ladder is no slower (0.1-0.2 more towers at turns 100 and 150, the
second barracks four to five turns earlier).

About +0.5 rating points head-to-head at Gold's scale (the logit of 0.5425 is 0.17; the scale 3.00): below the 0.60
upload recommendation, as v10 (0.5764), v12 (0.5716), v13 (0.5958) and v14 (0.5533) were at their final evaluations.
What a default would still need: the freeze (v15), the final evaluation against v14 on evaluation-pv3 block 4, the
artifact with both `procCheck` series; an upload would again be an exception and the user's decision.

`SITE_COVER` is the same effect by another measure: every value meets the bar too (0.541-0.548 against v14, +3.4 to
+4.4 pp against `rival`) and none was screened. It stays in the code at 0.

**The user's decision (2026-11-10, "Да, v15"):** `SITE_NEAR=50` is the `current` default and v15 is frozen (below);
the final evaluation and the artifact follow. S is below the 0.60 upload recommendation, so an upload is an exception
and the user's decision.

## v15 (2026-11-11)

- `Params`: `SITE_NEAR = 50` by default (`SITE_COVER` stays 0).
- `cr.bot.v15.GreedyBot`, `RoutePlanner` and `ApproachPlanner`: copies of `current` at the freeze (the package and class
  names, the frozen header and `name()` changed; `queenSteps()` left out, as in the earlier copies).
- `versions/v15.properties`: v14's snapshot with `SITE_NEAR=50`. `BotFactory`, `LocalGames` (v15 the default opponent),
  `visualizer/local.js`, `docs/visualizer.md` and `.ai/project.md` (the bot specs' ranges v00-v15 and v01-v15; resynced
  into `AGENTS.md` with `.ai/sync.py sync`, the lock updated) know v15.
- Tests: `MainBotTest.withDefaultsCurrentPlaysAsV15` (fixture seeds and PV3 screen block 8's first maps),
  `withV14ParamsCurrentPlaysAsV14` (`SITE_NEAR=0`; the older versions' parameter strings carry the same zero), the chain
  v14 → v15 in `BotFactoryTest`, `LocalGamesTest`'s bot count. In `StrategyParamsTest` the tower-sites test pins v14's
  ladder (`SITE_NEAR=0`) where it asserts v14's choice and asserts the default on a position where it differs, and
  the first-wave anchor's test pins it too (its "anchor on the back edge" case is v12's rule: with the default the
  site beside the tower wins there). The full suite is green (188 tests, 3 corpus tests skipped for want of the arena
  replays in the worktree); the visualizer's node tests pass (18) and the tools' tests (71).
- In the browser (`./gradlew serve` from the worktree, `?view=local`): 18 bots in the lists, v15 among them with its
  description, side 2 defaults to v15, no console error.
- Identity on the 500 development maps (`eval.sh identity`, `replays/bench/exp-2026-11-10-tower-sites/freeze/`):
  `current` against v15 repeats v15's mirror game for game (0 maps changed), and v15 against v14 repeats the screened
  arm's 1000 games (0 differ).
- `docs/bot-architecture.html` is v15's: the overview, the decision flow's ladder node, the pseudocode of the target, the
  target choice (the v15 item), the versions table and the flags lists, the directions.

## Final evaluation (`eval.sh eval`, `rerun.sh`; summaries `eval-g1.json`, `eval-g1-rerun.json`, `eval-g2.json`, `eval-g4.json`, `eval-g4-rerun.json`, `eval-g5.json`; raw reports in `eval/`)

v15 against the selected version v14 on PV3 evaluation block 4: seeds 2004000000 + 397 i, i < 2000, both seats; `boss`
on the first 500 maps (3 threads, 200 ms). 2026-11-11 00:06-00:18. The block was reserved in the ledger before the
run; the next evaluation block is 5.

| gate | result |
| --- | --- |
| G1 | **passes**: S 0.5510 (95 % CI 0.5377..0.5643) against v14, W/D/L 1872/664/1464, 1723 of 2000 maps changed |
| G2 | passes: boss 1.0000 against 1.0000, delta 0.0000 on 500 maps (226 changed; no boss timeout) |
| G4 | passes: field 0.9888 against 0.9849, +0.0039 (CI −0.0004..+0.0081) |
| G5 | **passes**: rival 0.8771 against 0.8520, +0.0251 (CI +0.0119..+0.0383) |
| G3 | skipped (no `old`) |
| G0 | **by quiet repeats.** The first run at 8 threads (00:06-00:09) had 2 slow turns of v15 in the direct series (maps 2004073445 and 2004075033, the longest turn 173.8 ms; v14 had 9 as its opponent) and 9 in its field series (the longest 222.1 ms); v14 had 13 in its own rival series and 1 in its mirror in the same minutes, and v15 none in its rival and boss series. Both series were repeated at 2 threads (`rerun.sh`, 00:16-00:18): the same 4000 games each (`same_games.py`: 0 differ), no slow turn (the longest turns 19.7 and 16.5 ms); `eval-g1-rerun.json` and `eval-g4-rerun.json`: G0 pass, the same S and delta |

The screen's numbers replicate: S 0.5425 → 0.5510, rival +3.24 → +2.51 pp, field +0.81 → +0.39 pp. S is below the
0.60 upload recommendation: an upload is an exception and the user's decision, as v10's (0.5764), v12's (0.5716),
v13's (0.5958) and v14's (0.5533) were. By seat v15 scores 979/351/670 on seat 0 and 893/313/794 on seat 1 against
v14 (W/D/L). About +0.6 rating points head-to-head at Gold's scale (the logit of 0.5510 is 0.20; the scale 3.00).

The slow turns of the first run fell in the minutes when this session read files of the repository beside the series
(the practice is to run nothing else); the bots' own turns are short (v15's mean 0.05-0.11 ms a move, at most 19.7 ms in
the quiet repeats and 43.8 ms beside the boss process).

## Artifact

`./gradlew submit` with no parameters (the defaults are v15): `build/submit/Player.java`, 97,864 characters (limit
100,000), 13 files, sha256 `7a5b28c45a5cf13074219cd33ea55858251f5d2925588a7ec5375a1044fc7a8b`, build `72a6959d` (turn 0:
`TRAIN 7 2 10 6 9 5 9 13`), config `ce9faaa7`; a second build and a clean export of the working tree through a temporary
index rebuilt the same bytes. The kept copy is `artifacts/7a5b28c4/Player.java` in the main checkout (outside Git).
`./gradlew playsAs -Pfile=build/submit/Player.java -Pas=v15`: 6 of 6 games the same as v15's, and the maps tell v15 from
each of the 15 other frozen versions.

| procCheck | games | wins | identical to `current` | timeouts | extra / late / stderr lines | shortest game | max first turn | max later turn |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `-Popp=v01 -Pgames=100 -PseedBase=5000` (00:19:49-00:20:45) | 200 | 200 | 200/200 | 0 | 0 / 0 / 0 | 32 turns | 241.9 ms | 8.6 ms |
| `-Popp=boss -Pgames=100 -PseedBase=6000` (00:20:45-00:22:52) | 200 | 200 | 200/200 | 0 | 0 / 0 / 0 | 48 turns | 203.7 ms | 5.3 ms |

Both series ran with the machine kept awake by the session and nothing beside them. Logs:
`replays/bench/exp-2026-11-10-tower-sites/submit.log`, `proccheck-v01.log` and `proccheck-boss.log`, in the worktree
and copied to the main checkout.

The artifact is not uploaded. The freeze is commit 03a16bc (2026-11-11, on the user's request; a clean export of
the staged tree rebuilt the artifact's bytes before the commit), merged into master the same day. In the main checkout
`./gradlew submit` rebuilds the same bytes from master, and what `tools/arena.py follow v15 --selected v14` does before
the paste, run there without the network, names that file (`docs/DEVLOG.md`, 2026-11-11).

## Limits

- One arm was screened, the one the rule named. `SITE_NEAR=100` and `SITE_COVER=100,SITE_NEAR=50` score 0.5441 and
  0.5504 against v14 on the development maps and +4.7 and +4.9 pp against `rival` (`SITE_NEAR=50`: 0.5561 and
  +3.45 pp): within the noise of the chosen arm in the mirror and higher against `rival`. Which of them the Gold
  field prefers is not known; a screen of either needs its own fresh block.
- Ten arms were compared on the development maps, two of them added after the first sweep. The fresh block's 0.5425
  is the estimate to use, not the development 0.5561.
- The profiles are 400 games an arm and opponent; the screen's gain was not profiled on the fresh block.
- The local panel: the mirror overstates the HP races and `rival` builds giants in most games (PV5,
  `docs/local-screen.md`). v14's rule kept its local size of the cover on the arena, while its HP saving there was not
  established (`docs/data/study-2026-11-10-v14-cover/`); the same may hold here.
- The towers of the defence rule and of the touch rule, the first tower and the sites of the mines and barracks are
  chosen as in v14.

## Checks of the experiment, before the freeze

- `./gradlew test` (2026-11-10, after the last code change and before the screen, and again after the records): 187
  tests, 3 skipped (the corpus tests, for want of the arena replays in the worktree), no failure. The tools' tests
  (`python -B -I -m unittest discover -s tools/tests -p "test_*.py"`): 71, OK.
- With both flags at 0, `./gradlew submit` built an artifact of `current` that played as v14 (the identity series):
  97,863 characters, build `ea82a82e`, config `f0b039e0`, sha256
  `d5a5b911f865dca8d1e340c806ebc99031deabe9da521aeab767ff8e4983b09b`. The two flags take 1,377 of those characters (the
  build before them had 96,486). With `-Pparams=SITE_NEAR=50` the build had 97,875 characters (build `da542ebb`, config
  `7b16e76c`). Neither is a release artifact; v15's is in the Artifact section above.
- Raw reports: `replays/bench/exp-2026-11-10-tower-sites/` in the worktree, copied to the main checkout without the
  profiles' csv files (the sweeps and the screen: 140 reports; then the freeze's identity series, the final
  evaluation with its repeats and the logs).

## Not tried, for a later step

- A heavier weight or both measures together on a fresh block (the first limit above).
- The same measure for the towers the defence rule builds under threat and for a tower on a free site she touches.
- The first tower's site, and where the mines and the barracks stand relative to the towers.
