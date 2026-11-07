# Experiment 2026-11-07: SAFE_LADDER, the ladder's targets on the enemy half

## Hypothesis

From the study of 2026-11-07 (`docs/data/study-2026-11-07-tower-fire/`, `cr.tools.TowerFire`): on the arena, v10's queen
lost 436 of the 810 HP enemy towers cost her in 160 Silver games while walking to free sites on the enemy half, through
enemy tower ranges (v08: 426 of 805 in 250 games). The ladder takes the free site nearest the midpoint of the queen and
home and drops a site only when an enemy tower's range covers its centre. With the free sites of our half taken or under
enemy towers, the target is then a site deep in the enemy's base (917798608: site 9 at (193,167) for a queen at
(1333,790) whose home is (1920,1000), through three enemy towers; she died at turn 202).

`SAFE_LADDER` (`Params`, `MainBot.nextSite`, `safeLadder`, `walkUnderEnemyTower`):
- 1: a site on the enemy half (nearer the enemy's corner than ours) is a ladder target only while the straight walk from
  the walker (the queen, or ROUTE's landing point for the next target) to it stays out of every enemy tower's range,
  checked every 20 units until she touches it;
- 2: such a site is never a target;
- 0: as v01-v10.

With no target left, the ladder is complete for that turn and the queen pumps her tower nearest home, as v10 does.
Regression test: `StrategyParamsTest.safeLadderKeepsTheQueenOutOfTheEnemyBase`.

Success metric: P2 against v10, the local champion. A change of fewer than 10 % of the maps against v10's mirror is a
targeted fix (G0, S ≥ 0.500, boss delta ≥ −1 pp, the regression test); otherwise G0-G5. The local panel rarely shows
the defect, so the arena's gain cannot be measured locally; the local series check that nothing else breaks.

## Local measurement

`cr.tools.LocalProfile` prints a new line since 2026-11-07, from the same probe as `DeathTrace`'s `td`: our queen's HP
lost to enemy towers and the turns she walks to a free site on the enemy half through an enemy tower's range. Seeds
1 + 397 k, k < 200, both seats (`./gradlew localProfile -PbotA=<spec> -PbotB=<opponent> -Pmaps=200 -PseedBase=1
-PseedStride=397`):

| botA | opponent | wins | tower HP a game | games with tower fire | far walks a game |
| --- | --- | --- | --- | --- | --- |
| v10 | rival | 78.8 % | 0.3 | 12 of 400 | 0.2 |
| v10 | field | 93.0 % | 0.5 | 21 of 400 | 0.4 |
| v10 | v10 | 48.8 % | 0.9 | 31 of 400 | 0.5 |
| SAFE_LADDER=1 | rival | 79.0 % | 0.2 | 9 of 400 | 0.0 |
| SAFE_LADDER=1 | field | 93.5 % | 0.2 | 15 of 400 | 0.0 |
| SAFE_LADDER=1 | v10 | 48.3 % | 0.4 | 24 of 400 | 0.0 |
| SAFE_LADDER=2 | rival | 80.0 % | 0.2 | 12 of 400 | 0.0 |
| SAFE_LADDER=2 | field | 93.0 % | 0.2 | 13 of 400 | 0.0 |
| SAFE_LADDER=2 | v10 | 48.5 % | 0.3 | 19 of 400 | 0.0 |

Locally the queen loses 0.3-0.9 HP a game to enemy towers, against 5.1 on the arena: the local opponents rarely cover
our half with towers. Both values remove the far walks and halve that loss.

## Development sweep (`sweep.sh`, `sweep_table.py`, `sweep.txt`)

Seeds 1 + 397 k, k < 500, both seats; raw reports in `replays/bench/exp-2026-11-07-safe-ladder/dev/`.

| arm | S vs v10 | maps changed vs the mirror | delta vs rival | delta vs field | failures |
| --- | --- | --- | --- | --- | --- |
| SAFE_LADDER=1 | 0.5025 (0.4972..0.5078) | 37 of 500 | +0.0010 (−0.0042..+0.0062) | +0.0055 (−0.0004..+0.0114) | 0 |
| SAFE_LADDER=2 | 0.5075 (0.4921..0.5229) | 219 of 500 | +0.0095 (−0.0057..+0.0247) | +0.0085 (−0.0007..+0.0177) | 0 |

SAFE_LADDER=1 changes 7.4 % of the maps, a targeted fix. SAFE_LADDER=2 changes 44 %, also forbidding the enemy half
when the walk is clear, which makes it a strategy change with no clear gain. Only SAFE_LADDER=1 goes to the screen, so the
block needs no replication.

## P2 screen (`screen.sh`, `rerun.sh`; PV3 screen block 4)

Seeds 1004000000 + 397 i, i < 2000, both seats; boss on the first 500 maps (3 threads, 200 ms). Raw reports in
`replays/bench/exp-2026-11-07-safe-ladder/screen/`; summaries `g1.json`, `g1-rerun.json`, `g2.json`, `g4.json`, `g5.json`.

| gate | result |
| --- | --- |
| targeted fix: maps changed against v10's mirror | 148 of 2000 (7.4 %), below 10 % |
| targeted fix: S against v10 | 0.5030 (CI 0.5000..0.5060), at least 0.500 |
| G2 boss, delta ≥ −1 pp | 0.0000 (0.999 against 0.999; 1 map of 500 changed) |
| G4 field (as for a strategy candidate) | +0.0000 (CI −0.0021..+0.0021); 201 maps changed |
| G5 rival (as for a strategy candidate) | +0.0013 (CI −0.0018..+0.0043); 142 maps changed |
| G0 | the first run: one slow turn of the candidate (map 1004717379); v10 had one on a neighbouring map of the same series (1004719761) and four at the start of its series against field. The quiet repeat (2 threads) held the same 4000 games with no slow turn on either side; the candidate's longest move 13.1 ms, v10's 32.6 ms |
| G3 | skipped (no `old`) |
| regression test | `StrategyParamsTest.safeLadderKeepsTheQueenOutOfTheEnemyBase` |

SAFE_LADDER=1 passes P2 as a targeted fix. Its S also clears G1's strategy bar, by 0.0000015 on the lower bound. A
targeted fix joins the local champion and needs no upload of its own. The user adopted it ("Да, по умолчанию"): the
default since v11, frozen 2026-11-07 (`cr.bot.v11.GreedyBot`, `versions/v11.properties`).

## The arena games it would change (`arena_diff.py`, `arena_diff.txt`)

`cgAttribute` with the flag on replays v10's 160 Silver games and compares its answers with v10's recorded ones turn by
turn (PowerShell; `<R>` the main checkout's `replays\arena\v10\silver`):

```
.\gradlew.bat -q cgAttribute "-Preplays=<R>\upload-01\games" "-Pbot=current?SAFE_LADDER=1" -Pagent=6857063 > replays\study\tower-fire-2026-11-07\attribute-safe-ladder-upload-01.txt
.\gradlew.bat -q cgAttribute "-Preplays=<R>\upload-02\games" "-Pbot=current?SAFE_LADDER=1" -Pagent=6857144 > replays\study\tower-fire-2026-11-07\attribute-safe-ladder-upload-02.txt
python -B -I docs/data/exp-2026-11-07-safe-ladder/arena_diff.py replays/study/tower-fire-2026-11-07/attribute-safe-ladder-upload-01.txt replays/study/tower-fire-2026-11-07/attribute-safe-ladder-upload-02.txt > docs/data/exp-2026-11-07-safe-ladder/arena_diff.txt
```

The flag changes at least one decision in 31 of the 160 games (19 and 12 per upload). Every game is the same as v10's up
to that turn, so its outcome counts toward the recorded result of v10.

| v10's result | games | HP lost to enemy towers from the first changed turn on | games with that loss | queen deaths |
| --- | --- | --- | --- | --- |
| lost | 20 | 667 | 18 | 17 |
| won | 11 | 32 | 5 | 0 |

- By construction a decision changes only through a site on the enemy half that the straight walk reaches through an
  enemy tower's range, as the ladder's target or as ROUTE's next target.
- In the 20 losses, v10's changed decision is a BUILD MINE in 14: the income below its target again, after a mine was
  lost or exhausted. It is a BUILD TOWER in 2, the forward barracks in 2 (turns 51 and 58) and a MOVE in 2. The flag's
  answer is a BUILD TOWER in 14 (the home tower or a site on our half), a MOVE in 4 and a barracks in 2.
- What the games would have become is unknown. The flag acts on 20 of v10's 52 losses, against 11 of its 108 wins.
