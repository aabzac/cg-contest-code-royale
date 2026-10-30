# Study 2026-10-30: where v01/v02 lose, and what stronger players do

**Question.** v01/v02 sit at about 17.8 in Bronze, while the boss stands above about 27.1. Which part of
their play loses games, and what do stronger players do differently?

## Data and tool

- **Tool:** `./gradlew cgStudy -Pout=<csv> -Preplays="<dirs or files>"` (`cr.tools.CgStudy`). It replays
  each recorded CG game through `Rules` from its seed with both players' recorded commands, as
  cgValidate does. It writes one row per game and seat:
  - the result;
  - at turns 25, 50, 75, 100, 150 and 200: income, mines, towers, barracks by type, creeps alive by
    type, queen HP and gold;
  - the first barracks, mine and tower turns, income, mean banked gold, structures built and lost, and
    trainings started by type.
- **Corpora** (526 games, 1052 rows):
  - our 300 placement games: v01 uploads 1-3 and v02 uploads 1-2;
  - `replays/cg2`: 58 Bronze, 38 Silver and 42 Gold games, manifest
    `docs/data/replays-cg2-2026-10-28.json`;
  - `replays/cg/legend-top`: 88 games.
- **Command:** `./gradlew cgStudy -Pout=build/study/features.csv "-Preplays=replays/cg2
  replays/cg/arena-6823071/games replays/cg/arena-6823455/games replays/cg/arena-6824680/games
  replays/cg/arena-6826276/games replays/cg/arena-6827094/games replays/cg/legend-top"`. The output is
  kept as `replays/study/features-2026-10-30.csv` (gitignored, sha256 `2fe77d5c…`).
- **Scripts here:** `groups.py` gives medians by group (output `groups-raw.txt`); `towers.py` gives our
  results against towers and how we lose (output `towers.txt`).

## Findings

**1. We build towers late and few; everyone else builds them early.** Medians:

| group | first barracks | first tower | towers at 50 | most towers | income at 50 |
| --- | --- | --- | --- | --- | --- |
| v01/v02 | 1 | 31 | 1 | 2 | 7 |
| their opponents | 6 | 18 | 3 | 3 | 4 |
| Bronze, rating ≥ 24 (cg2) | 7 | 14.5 | 2 | 4 | 4.5 |
| Silver and Gold | 10-12 | 17-18 | 3 | 4-5 | 4-5 |
| Legend | 13 | 17 | 3 | 5 | 7 |

Our bot builds one knight barracks on turn 1 and mines up to income 10. Only then does it build
towers.

**2. Our towers go with our wins.** Our 300 placement games (`towers.txt`):

| Our towers at turn 50 | games | we win |
| --- | --- | --- |
| 0 | 72 | 32 % |
| 1 | 126 | 59 % |
| 2 | 59 | 83 % |

- By the most towers we ever held: 0 → 33 %, 1 → 47 %, 2 → 63 %, 3+ → 74 %.
- Our win rate falls with the opponent's towers at turn 50: 0 → 89 %, 3+ → 48 %.

**3. How we lose.**
- 105 of 124 losses are a killed queen, at a median turn of 80 (quartiles 56-104); 19 are on HP at turn
  250.
- In losses our queen has 38 HP at turn 50, 29 at 75 and 22 at 100; in wins 51, 47 and 41.
- The opponent keeps 4 knights alive at turns 50-75 in our losses, and 1-2 in our wins.

These are correlations: a game where we are pressed early also leaves no time for towers.

## Local test (development pool only)

Raw reports are in `replays/bench/dev-towers-2026-10-30/` (gitignored); `dev-sweep.txt` lists them all.
The arms are existing parameters of `current`.

- **Against v02** (500 maps, 100000-100499): a lower income target before towers and a higher tower
  cap help monotonically:
  - `INCOME_TARGET=8`: 0.525;
  - `INCOME_TARGET=4`: 0.670;
  - `INCOME_TARGET=3,MAX_TOWERS=8`: 0.782;
  - `INCOME_TARGET=2,MAX_TOWERS=6`: 0.797 (± 0.028).
- **Other profiles**, Δ against v02 on the same maps:
  - v01 with 2 barracks: +13 to +14 pp;
  - a tower-heavy v01 (`INCOME_TARGET=4,MAX_TOWERS=8`): +35 to +44 pp;
  - 2 barracks with `INCOME_TARGET=4`: +24 pp.
- **`boss`** (150 maps): 0.977 and 0.987 against 0.920.
- **Seat:** the tower arms also win more on seat 1 (143-146 of 150 against `boss`, v02 128).

Caveats:
- Every local opponent plays knights only. Giants, which break towers, were built by 12 % of v01's
  arena opponents, and none plays locally.
- `old` (G3) is still missing. Twelve arms shared the development block, so their numbers carry a
  winner's curse: the P2 screen on a fresh block is the honest estimate.

## Candidates

1. **Early towers** (`INCOME_TARGET` 2-3, `MAX_TOWERS` 6-8), parameters only. It has the strongest
   evidence, both on the arena and locally. Risk: giants.
2. **A second knight barracks or more knights.** In our wins we keep more knights alive (7 against 4 at
   turn 75), and Legend players keep 2 knight barracks. The evidence is weaker; test after 1.
3. **Queen safety:** we lose most games to a killed queen. Early towers already cover part of this; a
   flee rule for when no tower exists is a smaller follow-up.
