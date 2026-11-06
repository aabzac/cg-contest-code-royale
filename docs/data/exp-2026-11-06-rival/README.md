# Experiment 2026-11-06: a rival like the strongest Silver players (a proxy opponent for P2)

**Why.** The Silver study (`docs/data/study-2026-11-05-silver/`) and PV3 left the strongest players' play
unrepresented locally: giants, strong late economies, larger waves. The three local opponents miss them:
- the v08/v09 mirror does not expand;
- `field` expands, but v08 beats it in 93 % of the games;
- `field?GIANTS=1` is weaker still.

Economy or giant candidates were therefore punished or invisible locally. **Goal:** an opponent close to the
strongest four Silver players of v08's placements (the boss and three players rated 20.9-21.7), that orders
our versions as the arena did.

## The target (`target.py` → `target.txt`)

The strongest four against v08, medians:

| | the strongest four | the rest of Silver |
| --- | --- | --- |
| their score against v08 | **70 %** | 29 % |
| turn 100: income, mines, towers (HP), queen HP | 6, 3, 3 (1,916), 42 | 5, 3, 4 (1,818), 39 |
| knight / giant trainings | 11 / 1 (giants in 36 of 55 games) | 12 / 0 (26 of 195) |
| game length median, at the limit | 225, 44 % | 241, 49 % |
| their queen under our knights: contact turns, % near an own tower, HP per contact turn, % MOVE | 26, 77, 1.07, **14** | 36, 64, 1.09, 50 |

Their build:
- the opening is v08's, two mines and a knight barracks (41 of 55 games), with the barracks at turn 12;
- one knight barracks, and a giant barracks in 37 of 55 games, first ordered at turn 84 (quartiles 66-114),
  with 1-2 giants;
- 7 mines and 9 upgrades over the game: their income holds (6.5 at turn 150, v08's falls to 2).

They kite little.

## The opponent: `field` with four new knobs (off by default)

`cr.bot.field.FieldBot` and `FieldParams`:
- `KITE=1`: v08's kiting bound to the towers (`KITE_RANGE`, `KITE_PULL`);
- `MINES_FIRST`: mines before the knight barracks;
- `GIANT_TURN`: the giant barracks not before that turn;
- `GIANTS_MAX`: at most that many giant trainings.

The default `field` plays the same games as before: v08 against `field` on 1 + 397 k, k < 500, shows 0 of 1000
games differing from the first-wave sweep's control, after each change (`runs.txt`). Gate G4 is unchanged.

Tests: `FieldBotTest.kitingStaysUnderTheTowers` and `openingGiantTurnAndGiantCap`.

## Calibration

All runs: v09 against the variant on the development seeds 1 + 397 k (`runs.txt`; raw reports in
`replays/bench/dev-rival-2026-11-06/`, main checkout).

| Variant (`field?…`) | v09's S |
| --- | --- |
| default (as G4) | 0.93 (v08, 500 maps) |
| `KITE=1` | 0.83 (v09's win share, `LocalProfile`, 500 maps) |
| `KITE=1,GIANTS=1` | 0.88 (win share, 500 maps) |
| `KITE=1,MINES_FIRST=2` | 0.81 |
| + giants from turn 80, at most 2 (`GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2`) | 0.80 |
| + `TOWER_HP=700` (**R0**) | **0.706**; 0.703 on 500 maps |
| R0 with `TOWERS` 3 / 5 / 6 | 0.71 / 0.74 / 0.75 |
| R0 with `INCOME` 6 / 10 | 0.76 / 0.71 |
| R0 with `GIANT_TURN` 60 / 100, `GIANTS_MAX` 1 / 3 / 4 | 0.70 / 0.73, 0.73 / 0.75 / 0.77 |
| R0 without giants / without kiting / with `TOWER_HP=800` | 0.76 / 0.84 / 0.88 |
| R0 with `KITE_PULL=200` / `KITE_RANGE=350` | 0.71 / 0.70 |

The ladder plateaus around 0.70. Other balances of the same greedy ladder do not reach the strongest four's
70 % against v08; they must play some other way. R0 is
`field?KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2`.

**R0 next to the target** (`LocalProfile`, R0 against v09 on 500 maps; the target is the four against v08):

| | R0 | the strongest four |
| --- | --- | --- |
| its score | 29 % | 70 % |
| turn 100: income, mines, towers (HP), queen HP | 5, 3, 4 (2,276), 47 | 6, 3, 3 (1,916), 42 |
| knight / giant trainings | 13 / 2 | 11 / 1 |
| game length median, at the limit | 250, 74 % | 225, 44 % |
| under knights: contact turns, % near a tower, HP per contact turn, % MOVE | 44, 80, 0.5, **66** | 26, 77, 1.07, **14** |

Close in the economy, the line and the army. Far in strength, in kiting (it uses ours), and in how many games
go to the limit.

## Validation: the version ladder against R0

500 maps each, on 1 + 397 k. Raw reports `ladder-v05.json` … `ladder-v09.json` (sha256 cacb45c8…, 5df9fdf3…,
898da981…, 7bc41fd4…, a1d37802…).

| Version | S against R0 | step (logit) | the arena's step (Bronze means) | P2's direct S at the time |
| --- | --- | --- | --- | --- |
| v05 | 0.269 | | | |
| v06 | 0.372 | +0.48 | +0.85 (26.24 against 25.39) | 0.652 against v05 |
| v07 | 0.412 | +0.17 | **−0.71** (25.53 against 26.24) | **0.680** against v06 |
| v08 | 0.651 | **+0.98** | **+2.46** (27.99 against 25.53) | 0.667 against v07 |
| v09 | 0.703 | +0.24 | not uploaded | 0.527 against v08 |

R0 orders the three Bronze steps as the arena did: v08's step the largest and v07's the smallest. P2's direct
matches put v07's step first, the one the arena did not confirm. Three steps are a small sample. R0 still
gives v07 a gain where the arena showed none (inside its noise, M about 2.5).

## Verdict and proposal

R0 is the strongest local opponent so far: v09 beats it in 70 % of the games, `field` in 93 %. It is closer
to the strongest Silver players in economy, line, army and giants, and it orders the arena's last three
Bronze steps correctly.

**Proposal (PV4), adopted by the user on 2026-11-06:** R0 joins P2 as gate **G5**: Δ against R0 ≥ −2 pp on the
2000 maps of the block, in-process, as G4. The spec is `rival` (`FieldParams.RIVAL`; `BotFactoryTest` checks
that it plays the spelled-out `field?…` games). The policy text is in `docs/local-screen.md`, PV4.
- What it does not represent, recorded beside PV3's list: the strongest four's strength (they score 70 %
  against v08, R0 29 %), their low kiting, and the share of games decided before the limit.
- Under it the candidates that PV3 left to the arena can be measured locally: our income, an answer to
  giants, the late game.

A side result: v09 gains over v08 against R0 too (+5.2 pp), as against `field` on its screen.
