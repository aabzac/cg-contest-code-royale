# Study 2026-11-07: v10 against weaker and stronger opponents, and our queen under enemy tower fire

## Question

After v10's first upload, the user noticed two things:
- the bot loses to several weaker players, while near the top it starts to win, "as if the lower-rated players had a
  strategy that is awkward for us";
- in about ten IDE games against the Silver boss (rating 21.96), the boss won once.

## Data

| Version | Uploads | Agents | Games |
| --- | --- | --- | --- |
| v08 | three Silver uploads | 6843976, 6844095, 6847999 | 250 |
| v10 | two Silver uploads | 6857063, 6857144 | 160 |

Every game was played by the uploaded artifact (`cgAttribute` SAME). The manifests are in
`docs/data/arena-<agent>-games-<date>.json`. The replays are in the main checkout under
`replays/arena/<version>/silver/upload-<NN>/games/`.

## Commands

Run them from PowerShell in the repository root: Git Bash mangles the Windows paths. `<R>` is the main checkout's
`replays\arena`.

The queen's traces (`cr.tools.DeathTrace`), one file per agent:

```
.\gradlew.bat -q deathTrace -Pagent=<agentId> "-Preplays=<R>\<version>\silver\upload-<NN>\games" > replays\study\tower-fire-2026-11-07\trace-<agentId>.txt
```

Per-game features (`cr.tools.CgStudy`), one table per version:

```
.\gradlew.bat -q cgStudy "-Pout=replays\study\tower-fire-2026-11-07\cgstudy-v10.csv" "-Preplays=<R>\v10\silver\upload-01\games <R>\v10\silver\upload-02\games"
.\gradlew.bat -q cgStudy "-Pout=replays\study\tower-fire-2026-11-07\cgstudy-v08.csv" "-Preplays=<R>\v08\silver\upload-03\games <R>\v08\silver\upload-04\games <R>\v08\silver\upload-05\games"
```

The two scripts and their outputs:

```
python -B -I docs/data/study-2026-11-07-tower-fire/towers.py replays/study/tower-fire-2026-11-07 > docs/data/study-2026-11-07-tower-fire/towers.txt
python -B -I docs/data/study-2026-11-07-tower-fire/opponents.py replays/study/tower-fire-2026-11-07/cgstudy-v08.csv replays/study/tower-fire-2026-11-07/cgstudy-v10.csv > docs/data/study-2026-11-07-tower-fire/opponents.txt
```

`towers.py` imports `tools/arena_compare.py`, which fits the logistic model on the registry. Its inputs are gitignored
(`replays/study/`) and can be rebuilt from the replays with the commands above. Their sha256:

| file | sha256 |
| --- | --- |
| trace-6843976.txt | `71919e0c94bbf1eaf463d32aae81bbff3c3b213a5919a8ad20982670eef49b28` |
| trace-6844095.txt | `306aa3042c6badd0e77a228a80a9ec7b938246898df99bb568fc3f2ae1310cf0` |
| trace-6847999.txt | `7cb098dd183120fffa1fce44dc558b91f9ff75bb7df7b97efdfdb4027c14ce50` |
| trace-6857063.txt | `5f4dbbf4f6748a640be84d3aceab870d57c7a867a7664a11317a4ddf08274c16` |
| trace-6857144.txt | `ed26d14ff8115b6a2758879d9a2d13415759a2a6fabc5558245837e3c4fc6681` |
| cgstudy-v08.csv | `8a2d927c827316e2f85b17ab037bcb10cfe8086739c649e4f3f02c5a49c6f421` |
| cgstudy-v10.csv | `0aa32ce6aba6bb82be779176f6f4ab0cc6b52118bf69bbfec09afaadaa968ded` |

## Findings

### 1. Results by the opponent's rating (`towers.txt`)

The expectation comes from the logistic model of `arena_compare.py`: the version's performance rating against the
opponent's score at fetch. The scale is 3.68, Silver's fit on its five uploads.

| Opponents | v08 games | v08 scored | v08 expected | v10 games | v10 scored | v10 expected |
| --- | --- | --- | --- | --- | --- | --- |
| below 19 | 121 | 77 % | 72 % | 111 | 72 % | 75 % |
| 19-21 | 88 | 56 % | 56 % | 34 | 53 % | 55 % |
| 21 and up, and the boss | 41 | 12 of 41 (29 %) | 44 % | 15 | 10 of 15 (67 %) | 43 % |
| of which the boss | 16 | 6 of 16 | 42 % | 4 | 4 of 4 | 42 % |

- **v10 shifted from the weak opponents to the strong.**
  - Against the strongest opponents and the boss, v10 wins far more than its rating predicts: +3.6 points on 15 games.
    This group is small and its opponents differ from v08's. The user's IDE games against the boss (about 9 of 10, not
    saved) point the same way.
  - v08 did the opposite: +6.4 points over its expectation against opponents below 19, −5.8 against the top.
- **The rating does not move.** Most games come against the weaker part of the field (111 of v10's 160 below 19),
  where v10 is slightly below its expectation (−2.8 points, within chance).
- **The user's observation fits.** A version that beats the top but not the bulk of the field more often scores about
  the same. It stays below the boss even though it beats it.

### 2. How v10 loses to opponents below 19 (`opponents.txt`)

Medians at turn 100, us / them:

| | v10 won (78) | v10 lost (31) | v08 won (92) | v08 lost (28) |
| --- | --- | --- | --- | --- |
| income at turn 100 | 3 / 4 | 3 / 6 | 3 / 5 | 3 / 4.5 |
| income at turn 150 | 4 / 3.5 | 3 / 5 | 3 / 5 | 2 / 5 |
| enemy knights on the board at turn 100 | 0 | 3 | 2 | 0.5 |
| our knight barracks at turn 100 | 2 | 1 | 2 | 1 |
| our queen died | 0 | 24 (median turn 142.5) | 1 | 16 (median turn 154.5) |

- Against weaker opponents, v10 loses mostly by the queen's death around turn 140. The winners hold twice our income
  from turn 100 and a standing knight army, with fewer towers than in our won games (4 against 6 built).
- v08's losses look the same.
- On HP at turn 250, v08 lost 35 of 250 games and v10 9 of 160. That is the late hold's purpose, though the opponents
  differ too.

### 3. Enemy tower fire on our queen (`towers.txt`)

The `td` column of `DeathTrace` is the turn's HP loss with the enemy knights removed.

| | v08 | v10 |
| --- | --- | --- |
| HP lost to enemy towers a game (± SE) | 3.2 ± 0.7 | 5.1 ± 1.2 |
| turns under tower fire, with no enemy knight within 250 | 366 of 494 | 313 of 467 |
| the queen's commands on those turns | BUILD TOWER 282, BUILD MINE 168, BUILD BARRACKS-KNIGHT 27, MOVE 17 | BUILD TOWER 257, BUILD MINE 188, BUILD BARRACKS-KNIGHT 22 |
| deaths | 61 | 43 |
| towers' share of the HP lost in the deaths' last 30 turns | 18 % | 31 % |
| deaths with 10+ HP from towers in those turns | 12 | 12 |
| losses on HP at turn 250 where the tower HP covers the margin | 1 of 35 | 1 of 9 |

- The difference between the versions is within the per-game spread: a few games carry most of the tower damage.
- In both versions the queen walks into enemy tower ranges on her own, on BUILD walks, mostly with no enemy knight
  near.

From the code (`cr.bot.v10.GreedyBot`, `RoutePlanner`):
- **The target filter checks only the centre.** A site is no target when an enemy tower's range covers its centre;
  the touch point and the walk are not checked.
- **ROUTE falls back to the straight walk.** When no plan avoids the ranges, `RoutePlanner.plan` returns the straight
  BUILD, so she walks through them.
- **The target is recomputed every turn,** from the midpoint of the queen and home, so it can alternate between two
  sites. In 917802377 (v10 upload 1, against WookieWarlord) the queen stands under two enemy towers from turn 145,
  alternates between sites 20 and 3, and dies at turn 166.

### 4. What the queen was doing under fire (`classes-v10.txt`, `classes-v08.txt`)

`cr.tools.TowerFire` (`./gradlew towerFire -Pagents=<id,id...> -Preplays="<dirs>"`, PowerShell as above; `-Plines=1`
prints every turn, and `-Pagents=dump -Preplays="<game file> <agentId> <from> <to>"` prints a game's board) replays the
games with the recorded commands. It classes each turn the probe without enemy knights loses HP, by:
- the queen's command and its target: a free site, an own tower, mine or barracks;
- whether she touches the target or walks to it;
- whether an enemy tower's range covers the target's centre, and whether a standing point beside it lies outside every
  range;
- whether she starts the turn in a range, and whether an enemy knight is within 250;
- for a free site, its half (nearer our corner or the enemy's) and whether the straight walk to it crosses a range.

| HP to enemy towers | v10 (160 games) | v08 (250 games) |
| --- | --- | --- |
| total | 810 | 805 |
| walking to a free site on the enemy half, the walk through a range | 436 | 426 |
| walking to a free site on our half, the walk through a range | 75 | 97 |
| at or walking to an own tower, with a knight within 250 | 205 | |
| at or walking to an own tower, no knight near | 77 | |

Every v10 class appears in v08 too; the own-tower rows are summed for v10 only. The ladder's walks to the enemy half are
the larger half of the tower damage in both versions.

In 917798608 (v10 upload 1, lost to OhMaley), the whole sequence shows on one board (`-Pagents=dump`):
- Our home is (1920,1000). At turn 160 the ladder wants a sixth tower. The free sites of our half (13, 17, 22) lie in the
  ranges of enemy towers, one of them just built at 19 (852,646). The only target left is site 9 at (193,167), by the
  enemy's corner.
- The queen walks there through three enemy ranges. When enemy knights come, she flees to her own tower 15, which
  stands in enemy tower 19's range, and tops it up there under fire. When they leave, she walks to 9 again.
- She has 97 HP at turn 160 and dies on turn 202.

## Proposed candidates

1. **Keep the queen's walks out of enemy tower ranges.** Skip targets whose touch point or path lies in a range
   instead of checking only the centre, and keep the previous target (hysteresis).
   - It is a defect fix, measurable locally by the HP lost to towers (the local opponents build towers too).
   - Probably a targeted fix by P2's letter.
   - Its arena gain is bounded by the 12 deaths in 160 games where towers took 10+ HP.
   - Tested as `SAFE_LADDER=1` for the ladder's walks to the enemy half (`docs/data/exp-2026-11-07-safe-ladder/`): it
     passes P2 as a targeted fix.
2. **The late economy gap.** Against opponents with twice our income and a standing army, the queen dies around turn
   140. The income candidates of 2026-11-06 did not close it locally (`docs/data/exp-2026-11-06-income/`), and PV3
   records the late game of strong economies as unrepresented by the local panel.
