# Experiment 2026-11-03: a local opponent with the arena field's balance (`field`)

Why: the two income experiments (`exp-2026-11-03-mines-first` INCOME_TARGET=5 against v04,
`exp-2026-11-03-late-income` INCOME_LATE against v06) lose or do nothing against our own mirror, while
the arena opponents who beat v06 hold income 8 at turn 100, 3 mines, 4 towers at about 1,700 HP in all and
12 knight waves (`exp-2026-11-03-late-income/profile.txt`); giants appear in 7 of v06's 33 losses. Both
local opponents of P2 (the control and `boss`) play our ladder or the Default AI: knights only, tower-heavy.
The user chose a local opponent with the field's balance (2026-11-03). Starting revision: `3eef83c` plus
the uncommitted late-income flag; local Java 17.0.12, 8 bench threads.

## The bot (code)

`cr.bot.field.FieldBot` (spec `field`, parameters `FieldParams`, spec syntax `field?K=v`; not shipped,
outside the artifact's packages). A greedy ladder of its own, not a copy of any player: a mine first,
the knight barracks, a tower, mines up to MINES (3), towers up to TOWERS (4), a giant barracks once the
enemy holds GIANT_TOWERS towers (GIANTS=1 only), then mine upgrades and new gold sites up to INCOME (8);
idle: the tower nearest home. v06's defence (a second tower within THREAT_RADIUS under threat) and flight;
the touch rule upgrades mines, tops up towers below TOWER_HP and builds what the plan wants; a straight
BUILD walk with a MOVE around whatever leaves the queen standing (no route planner). Training: giants
first while the enemy has towers (the knights wait for the 140 gold), then knights, within the creep cap.
`cr.tools.LocalProfile <botA> <botB> <maps> <seedBase>` prints a bot's local profile in cgStudy's terms.
Tests: `FieldBotTest` (legal play against v06 on 10 maps and both seats with and without giants, the
ladder, the giant barracks, the touch rule, the training order); `LocalGamesTest` (the catalog lists it).

## Calibration against v06 (seeds 10000-10099, 100 maps, both seats; `replays/bench/dev-field-2026-11-03/c*.json`)

| `field?…` | S vs v06 | Profile (`LocalProfile`, 50 maps) |
| --- | --- | --- |
| first cut (GIANTS=1, TOWER_HP=450) | 0.060 | income 6 at turn 50, 4 towers at 2,040 HP at turn 100, 6 knight and 5 giant waves, 38 queen deaths of 100 games |
| GIANTS=0 | 0.193 | |
| TOWER_HP=600 (giants on) / TOWERS=5 / INCOME=10 / GIANT_TOWERS=4 | 0.045 / 0.028 / 0.070 / 0.065 | the giants cost the knight waves whatever else changes |
| **GIANTS=0, TOWER_HP=600** (the defaults now) | **0.255** | income 7 with 3 mines at turn 100, 4 towers at 2,344 HP (585 each), 17 knight waves, 20 queen deaths, kills v06's queen 6 times |
| GIANTS=0 with TOWERS=5 / 3; TOWER_HP=600 with TOWERS=5 | 0.223 / 0.237 / 0.198 | |
| GIANTS=0, TOWER_HP=600 with THREAT_TOWERS_NEAR=3 / MINES=2 / MINES=4 / THREAT_RADIUS=700 / INCOME=12 | 0.230 / 0.250 / 0.240 / 0.260 / 0.278 | within 100-map noise (± 0.07) of the defaults |
| GIANTS=0, TOWER_HP=800 | 0.025 | pumping towers to the maximum wastes the queen |

The defaults beat `boss` 60 of 60 and v03 0.735 (100 maps): a floor well above the Default AI, a quarter
of the games against v06, and a different balance from ours (income 7 against 3, 4 towers at 600 against
6 at 700, 17 waves against 11). What it is not: the arena's strong players, who kite and interleave, and who
beat v06 while this bot loses to it three times in four.

## What our parked flags do against it (seeds 11000-11199, 200 maps, both seats; `d-*.json`)

| `current?…` against `field` | S | against the control's 0.726 |
| --- | --- | --- |
| defaults (v06) | 0.726 | — |
| INCOME_LATE=6 / 8 | 0.635 / 0.608 | −9 / −12 pp: worse than against v06 (where they were neutral) |
| INCOME_TARGET=5 | 0.699 | −3 pp |
| TOWER_HP=500 | 0.654 | −7 pp |
| MAX_TOWERS=4 | 0.666 | −6 pp |
| MINES_FIRST=3 | 0.759 | +3 pp (games 87 turns: its queen dies to our early knights) |
| THREAT_TOWERS_NEAR=0 | 0.632 | −9 pp: the defence of v06 holds against this style too |
| BARRACKS=1,TRAIN_SYNC=0,MAX_TOWERS=8 (the army flags off) | 0.714 | −1 pp |

What it says: against an opponent that out-earns us and sends 17 waves, our early aggression and the
tower line win, and every flag that trades early strength for income loses more, not less. The economy
is not the lever for the next step on this evidence; the field's strong players beat v06 with something
this greedy ladder does not reproduce (kiting, timing), which the arena replays of v06's losses can show.

**Verdict:** `field` is available as a third local opponent (`./gradlew bench -Pbot2=field`, the local-games
catalog). The user adopted it into P2 on 2026-11-03 as gate G4 (Δ against `field` on the block's 2000 maps
≥ −2 pp; proxy validation PV2 in `docs/local-screen.md`); the two income flags stay off.
