# Experiment 2026-11-03: the queen's safety (a second tower under threat), P2 strategy screen against v05

From the study `docs/data/study-2026-11-03-queen-safety/`: in the arena games v05's queen died in, she
was hit on 1,103 turns and on 1,029 of them stood at one own tower, topping it up on 858; a tower deals 3
HP a turn to the nearest knight at 200 HP of tower and 4 at 700, so the top-up adds a quarter to the kill
rate and a second tower doubles it; the opponents who killed her built their next towers under fire. The
direction chosen by the user after the army experiment. Starting revision: `78d2c4d`, clean worktree
`claude/bot-improvement-direction-4fe687`; frozen v05, the referee and the harness unchanged. Local Java
17.0.12, Gradle 8.11.1, Windows 11, 8 bench threads.

## The parameters (code)

`cr.bot.Params`, read by `MainBot` (`current`); the defaults play as v05 command for command
(`MainBotTest.withDefaultsCurrentPlaysAsV05`; every version snapshot records the new fields at the
frozen behaviour):

- `THREAT_RANGE` (250): enemy knights closer than this to the queen are the threat (the former literal).
- `THREAT_TOWERS_NEAR` (0) with `THREAT_RADIUS` (400): under threat, with fewer than N own towers within
  R of the queen, she builds a new tower on the nearest free site within R that is not under an enemy
  tower (`BUILD site TOWER`: the walk, then the build), before the touch rule, so it overrides the
  top-up of the tower she touches and the flight to her nearest tower. With N towers near, v05's flight.
  The towers so built count in the ladder's tower cap.

Tests: `StrategyParamsTest.threatBuildsASecondTowerNearby` (the top-up at 0, the new tower at N=2, the
radius, N=1, two towers near, the threat range), the version identity tests of `MainBotTest` and
`BotFactoryTest`.

## Development sweep (seeds 10000-10499, 500 maps, both seats, against v05; the v05 mirror on the same maps)

`replays/bench/dev-safety-2026-11-03/` (gitignored; `sweep-a.sh`, `sweep-b.sh` and the table tool
`sweep_table.py` beside this file; the full table with CIs, seats and failures is `dev-sweep.txt`). One
JVM per arm, `BenchmarkRunner` directly, 8 threads, 50 ms. The mirror is the army sweep's on the same maps.
Stage A, 12 arms:

| Arm (the rest at v05's values) | S vs v05 (95 % CI) | W/D/L | Mean turns | |
| --- | --- | --- | --- | --- |
| defaults (THREAT_TOWERS_NEAR=0) | 0.500, 0 maps changed | 472/56/472 | 212 | the code at its defaults is v05 |
| **THREAT_TOWERS_NEAR=2**, radius 300 / 400 / 500 | **0.631 / 0.621 / 0.635** (0.612-0.658 at 500) | 611/48/341 at 500 | 214 | the largest effect of any flag so far |
| THREAT_TOWERS_NEAR=3, radius 300 / 400 / 500 | 0.585 / 0.576 / 0.587 | | 212-213 | a third tower under threat costs more than it saves |
| THREAT_RANGE=350 / 450 alone | 0.471 / 0.409 | | 204-209 | reacting earlier stops her mines and walks more; loses |
| THREAT_TOWERS_NEAR=2, radius 400, THREAT_RANGE 350 / 450 | 0.616 / 0.611 | | 213 | the range gives nothing on top |
| THREAT_TOWERS_NEAR=3, radius 400, THREAT_RANGE 350 | 0.559 | | 210 | |

Slow turns: 0-2 per arm scattered over the arms, with v05's own in the same runs (unchanged code), at 8
threads in fresh JVMs: host scheduling, as in the earlier sweeps.

Stage B: the N=2 arms replicated on the second development block (seeds 11000-11499; the v05 mirror there
is the army sweep's `r-mirror.json`), and on block 10000 N=1, radius 600, THREAT_RANGE 200, the tower cap 6
and the deferred army flags on top:

| Arm (THREAT_TOWERS_NEAR=2 unless stated) | S, block 10000 | S, block 11000 | |
| --- | --- | --- | --- |
| radius 300 / 400 / 500 | 0.631 / 0.621 / 0.635 | 0.599 / 0.587 / **0.605** | above 0.58 on both blocks at every radius; 500 best on both |
| radius 600; THREAT_RANGE=200; MAX_TOWERS=6 (radius 500) | 0.634; 0.652; 0.641 | | within noise of radius 500 |
| N=3, radius 500 | 0.587 | 0.573 | |
| N=1, radius 500 | 0.499 (6 maps changed) | | a tower within 500 is nearly always there |
| **radius 500 with the army flags** (`BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6`) | **0.680** (0.647-0.713) | **0.629** (0.597-0.662) | +4.5 and +2.4 pp over the safety flag alone, the same sign on both blocks |

What the sweep says: one more tower near the queen when knights are on her is worth about 12 pp against
v05 (0.62 pooled over the two blocks), the same at every radius from 300 to 600; a third tower, a wider
threat range or a lower one add nothing. The deferred army candidate (`docs/data/exp-2026-11-03-army/`,
+5 pp alone) stacks on top (+3.5 pp over safety alone, pooled), as the recommendation of 2026-11-03
foresaw: together about 0.655 on the two blocks, above the 0.60 upload recommendation.

## Hypothesis and scope (written before the screen)

- Candidate: `current?THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6`
  — v05's opening and economy; under threat a second tower within 500 of the queen; a second knight
  barracks once 4 towers stand, both released together; 6 towers in the ladder (the threat towers count).
- Control: frozen `v05` (the selected version and the local champion).
- Expected: S about 0.65 against v05 (0.680 and 0.629 on the two development blocks; a fresh block
  decides). Δ against `boss` near 0 (v05 beats it in about 99.8 % of games already).
- Metric: S, the map score over both seats (draw = 1/2), 95 % CI over maps (`tools/bench_pair.py`).
- Gates (strategy track): G0 no candidate failure of any kind; G1 lower CI bound of S above 0.50 on
  the 2000 maps of the fresh screen block; G2 Δ against `boss` on the block's first 500 maps ≥ −2 pp;
  G3 skipped (`refs/old-3315948/Player.java` is absent). Both local opponents play knights only, like
  33 of the 37 arena opponents who killed v05's queen.
- Screen block: **k=7, seeds 214000-215999**, reserved in `docs/data/seed-ledger.json` before any
  game on it. Only this one arm is screened on the block, so no replication is required; the safety
  flag alone is recorded on the two development blocks (0.635, 0.605) and not screened.
- Process opponent runs: 3 threads, 200 ms. In-process: 8 threads, 50 ms. Sequential, nothing else
  running. No extension of a series on its interim score; no upload and no adoption decision here.
- If it passes: the candidate is the local champion; the final evaluation against v05 on the next
  evaluation block (k=5, 410000-411999) before any upload, and the freeze are the user's decisions.

Commands (`screen.sh` beside this file; the reports in `replays/bench/exp-2026-11-03-safety/`):

```
./gradlew bench "-Pbot1=current?THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6" -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=214000 -PturnMs=50 -Pout=replays/bench/exp-2026-11-03-safety/direct.json
./gradlew bench -Pbot1=v05 -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=214000 -PturnMs=50 -Pout=replays/bench/exp-2026-11-03-safety/mirror.json
./gradlew bench "-Pbot1=current?…the same…" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=214000 -PturnMs=200 -Pout=replays/bench/exp-2026-11-03-safety/boss-cand.json
./gradlew bench -Pbot1=v05 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=214000 -PturnMs=200 -Pout=replays/bench/exp-2026-11-03-safety/boss-ctrl.json
python -I tools/bench_pair.py direct replays/bench/exp-2026-11-03-safety/direct.json --mirror replays/bench/exp-2026-11-03-safety/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-safety/g1.json
python -I tools/bench_pair.py common replays/bench/exp-2026-11-03-safety/boss-cand.json replays/bench/exp-2026-11-03-safety/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-safety/g2.json
```

## Screen results (block k=7, seeds 214000-215999)

Summaries `g1.json` and `g2.json`; raw reports, logs and `screen.sh` in `replays/bench/exp-2026-11-03-safety/`
(copied to the main checkout's `replays/bench/`). Revision 78d2c4d with the uncommitted safety code (the
reports record the diff's sha256). Raw report sha256 (first 12 hex digits): `direct.json` eb1ee7f64f68,
`mirror.json` 2e49efcbe888, `boss-cand.json` 5a6a7969584a, `boss-ctrl.json` f7d3420a801a.

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line of the candidate in any series (longest turn 21.6 ms against v05, 11.7 ms against `boss`; v05 had one slow turn of 60 ms in the direct series, unchanged code) | pass |
| G1, direct against v05, 2000 maps | **S = 0.6420** (95 % CI 0.6261-0.6579, SD 0.363); W/D/L 2525/86/1389; by seat 1395/40/565 and 1130/46/824; 1960 of 2000 maps changed against the v05 mirror | pass |
| G2, `boss`, 500 maps | candidate 1.000 (1000/0/0), control 0.997 (997/0/3), Δ = +0.0030 (CI −0.0004..+0.0064); 497 of 500 maps changed | pass |
| G3, `old` | skipped (no code) | — |

- **Game length:** 197 turns on average against v05 (the mirror 203).
- **Seats:** the candidate wins on both seats, 1395 and 1130 of 2000.
- **Development estimates held:** 0.680 and 0.629 on the two development blocks, 0.642 on the fresh block.
- **Rating terms:** at the arena's logistic scale (1.72 in Bronze), S 0.642 head-to-head is about +1.0
  rating point against v05, a third of the arena's noise margin M (2.61), about three times the deferred army
  candidate's gain. A head-to-head number against a knights-only opponent, not the field.

**Verdict:** P2 passed (G0, G1, G2; G3 skipped). The candidate
`current?THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6`
is the local champion. The final evaluation on evaluation block k=5 (410000-411999) before any upload, the
freeze (v06) and the upload are the user's decisions; at S about 0.64 the upload recommendation (S ≥ 0.60)
would be met. Caveat: the gain is measured against a knights-only opponent with a 700 HP tower line; the
arena's killers of v05's queen were knights-only in 33 of 37 games, which supports the safety part.

## Final evaluation (block k=5, seeds 410000-411999; written before the run)

On the user's decision after the screen. The same arms, gates and series as the screen, on the
evaluation block reserved in the ledger before any game on it, plus P2's upload recommendation S ≥ 0.60.
Commands: `eval.sh` beside this file (the screen's with `-PseedBase=410000`, reports in
`replays/bench/exp-2026-11-03-safety-eval/`); summaries `eval-g1.json` and `eval-g2.json`. One run; no
extension; no upload decision here.

### Results

Summaries `eval-g1.json` and `eval-g2.json`; raw reports and logs in `replays/bench/exp-2026-11-03-safety-eval/`
(sha256 first 12 hex digits: `direct.json` d72ad7648df0, `mirror.json` 2d07ff0d33ac, `boss-cand.json`
f8086a993f70, `boss-ctrl.json` 395151aff4f4). Revision 78d2c4d with the uncommitted safety code.

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line in any series (longest candidate turn 41.2 ms against v05, 7.2 ms against `boss`; the mirror and the control had none either) | pass |
| G1, direct against v05, 2000 maps | **S = 0.6519** (95 % CI 0.6356-0.6682, SD 0.372); W/D/L 2566/83/1351; by seat 1395/36/569 and 1171/47/782; 1997 of 2000 maps changed | pass |
| G2, `boss`, 500 maps | candidate 1.000 (1000/0/0), control 1.000 (1000/0/0), Δ = 0.0000; 488 of 500 maps changed | pass |
| G3, `old` | skipped (no code) | — |
| Upload recommendation, S ≥ 0.60 | 0.6519 | **met** |

- The screen (0.6420) and the evaluation (0.6519) agree within their intervals; the games against v05 last
  230 turns on average here (the mirror 233).
- **Rating terms:** at the arena's logistic scale (1.72 in Bronze), S 0.652 head-to-head is about +1.1
  rating points against v05. A head-to-head number against a knights-only opponent, not the field.

**Verdict:** P2 passed with the upload recommendation met. The candidate is the local champion; the freeze as
v06 and the upload under P1 are the user's decisions. Before any paste: `./gradlew submit` (with the
candidate's `-Pparams`, or with empty PARAMS once the defaults are the candidate's) and both `procCheck`
series.
