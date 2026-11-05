# Experiment 2026-11-05: a second tower before the enemy's first wave (P2 development)

**Hypothesis.** The first candidate of the Silver study (`docs/data/study-2026-11-05-silver/`, What
follows). In Silver the enemy's first wave meets v08 with one own tower in 86 of 250 games. Those games lose
12 HP in the 20 turns from the first contact and score 48 %; with two towers the loss is 5 HP and the score
65 %. v08's second tower comes at turn 25 because the queen first tops the new tower up to 700 HP, about six
turns. A second tower before the wave should cut the first-contact loss and win more HP races.

**Success metric.** On the development seeds: S against v08 above 0.50 and a delta against `field` of at
least −2 pp, as G1 and G4 would ask. Only then a P2 screen on PV3 screen block 0.

## The flags (`current`, off by default; `current` still plays as v08)

- `WAVE_TOWERS=N`: while an enemy knight wave is coming and fewer than N own towers stand, the ladder
  takes towers before its income phase. A wave is coming when an enemy knight barracks trains or enemy
  knights are on the board. The queen then neither upgrades a mine nor tops up a tower she touches
  (`MainBot.knightWaveComing`).
- `TOPUP_FROM=N`: the queen tops up an own tower she touches only once N own towers stand.
- Tests:
  - `StrategyParamsTest.waveTowersBuildTheSecondTowerBeforeTopUpsAndMines`: the first-wave position, with
    the enemy barracks idle or training, the enemy knights out, two towers, and a touched mine;
  - `StrategyParamsTest.topUpFromWaitsForTheTowerCount`;
  - the version identity tests of `MainBotTest` and `BotFactoryTest`. Both flags are written as 0 into
    every version snapshot.

## Development sweep

Seeds 1 + 397 k, k < 500, all 16 starting HPs; each map on both seats. `sweep.sh` (raw reports in
`replays/bench/dev-first-wave-2026-11-05/`, gitignored, main checkout) and `sweep_table.py` → `sweep.txt`.

| Arm | S against v08 (95 % CI) | Δ against `field` (control 0.9315) |
| --- | --- | --- |
| `WAVE_TOWERS=2` | 0.4675 (0.4344-0.5006) | −0.0370 (−0.0608..−0.0132) |
| `WAVE_TOWERS=3` | 0.3135 (0.2817-0.3453) | −0.0940 (−0.1216..−0.0664) |
| `TOPUP_FROM=2` | 0.4620 (0.4292-0.4948) | −0.0335 (−0.0554..−0.0116) |
| `WAVE_TOWERS=2,TOPUP_FROM=2` | 0.4675 (identical games to `WAVE_TOWERS=2`) | −0.0370 |

`WAVE_TOWERS=2` with `TOPUP_FROM=2` plays the same games as `WAVE_TOWERS=2`. In the local games a knight wave
is always coming before the second tower, so the two flags skip the same top-ups.

## What happened (`profiles.txt`: `LocalProfile`, 200 maps 1 + 397 k against v08)

| | v08 | `WAVE_TOWERS=2` | `TOPUP_FROM=2` |
| --- | --- | --- | --- |
| first contact: turn, own towers | 24, 1 | 23, **2** | 23, **2** |
| HP lost in the 20 turns from it, median (mean) | 5 (7.8) | 3 (7.3) | 3 (8.2) |
| HP lost on turns 25-50, median (mean) | 4 (6.8) | 3 (7.0) | 3 (7.7) |
| summed own tower HP at turn 50 | 1,648 | 1,464 | 1,436 |
| our queen's deaths in 400 games | 18 | **36** | **36** |
| wins of 400 | 182 | 167 | 169 |

The mechanism works: both arms meet the first wave with two towers. But the first-contact loss barely moves
on average (mean 7.3 and 8.2 HP against 7.8). The cost comes later:
- the first tower stays at 200 HP and melts;
- the line is weaker at turn 50;
- our queen dies twice as often.

`WAVE_TOWERS=3` also gives up the third mine and the upgrades and loses far more.

## Verdict

Every arm fails the development bar: S below 0.50 against v08, and −3 to −9 pp against `field`, outside
the −2 pp allowance. No P2 screen was run, so no PV3 block is used. The flags stay in the code, off by
default.

The arena's correlation probably does not carry over. The Silver games that met the wave with one tower were
the early waves (first contact at turn 23 against 28), so the tower count stood in for the opponent's timing.
And in Silver a single tower lost more at the first contact (12 HP) than in every local series (5-7 HP). The
Silver opponents' first waves are larger: 7 enemy knights alive at turn 25 in the races we lost, 4 in those
we won. No local opponent sends such a wave, so the proxy cannot test a defence against it. That is a limit
of the panel, as PV3 records for giants. An upload of `WAVE_TOWERS=2` as an exception would rest on the arena
alone; the local evidence is against it.
