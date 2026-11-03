# Experiment 2026-11-03: the army (a second knight barracks, synchronous waves), P2 strategy screen against v05

The direction chosen from `docs/bot-architecture.html` (Направления, first row) on 2026-11-03: 26 of
v05's 38 arena losses are a killed queen, mostly after turn 80, when the opponent's waves come one after
another and ours do not answer; we release 7 waves per game, the stronger groups 9-10, Legend holds two
barracks by turn 100. At income 3 the gold, not the barracks, bounds the waves (80 gold per wave, one
every 27 turns), so a second barracks adds no waves on its own; what it adds is concentration: 160 gold
saved and both barracks released on the same turn give one wave of 8 knights instead of two of 4 five
turns apart, and a tower hits one target a turn. Starting revision: `2fda634`, clean worktree
`claude/bot-improvement-direction-4fe687`; frozen v05, the referee and the harness unchanged. Local Java
17.0.12, Gradle 8.11.1, Windows 11, 8 bench threads.

## The parameters (code)

`cr.bot.Params`, read by `MainBot` (`current`); the defaults play as v05 command for command
(`MainBotTest.withDefaultsCurrentPlaysAsV05`; every version snapshot records the new fields at the
frozen behaviour):

- `BARRACKS` (1, as before): knight barracks kept. With 2 the ladder takes a second one.
- `BARRACKS_AFTER_TOWERS` (0): own towers that must stand before the second and every later barracks;
  until then the ladder goes on (income, towers) and the barracks is inserted before further towers
  once the towers stand. `MainBot.wantsBarracks`, shared by the touch rule, the next-site choice, the
  planner's next-target prediction and its divert rule. 0 means the second barracks right after the
  first, before the income phase.
- `TRAIN_SYNC` (0): with 1, TRAIN only when every own barracks is idle and the gold covers all of
  them, so they release together; with one barracks it plays as 0.
- Always on (no flag): the live cap of 30 creeps alive plus requested (`Const.CREEP_LIMIT`): a wave
  that would exceed it is left out of the line, since the referee refuses the whole line otherwise.
  Unreachable with one knight barracks (4 per 5 turns); the fixture games of v05 are unchanged.

Tests: `StrategyParamsTest` (`secondBarracksWaitsForTheTowers`, `trainSyncReleasesBothBarracksTogether`,
`trainKeepsWithinTheCreepLimit`), the version identity tests of `MainBotTest` and `BotFactoryTest`.

## Development sweep (seeds 10000-10499, 500 maps, both seats, against v05; the v05 mirror on the same maps)

`replays/bench/dev-army-2026-11-03/` (gitignored; `sweep-a.sh`, `sweep-b.sh` and the table tool
`sweep_table.py` beside this file; the full table with CIs, seats and failures is `dev-sweep.txt`).
One JVM per arm, `BenchmarkRunner` directly, 8 threads, 50 ms. Stage A, 12 arms: the defaults and
TRAIN_SYNC alone (identity checks), then BARRACKS=2 with the gate at 0, 2, 4, 6 and 8 towers, each
with and without TRAIN_SYNC.

| Arm (the rest at v05's values) | S vs v05 (95 % CI) | W/D/L | Mean turns | |
| --- | --- | --- | --- | --- |
| defaults; TRAIN_SYNC=1 alone | 0.500, 0 maps changed | 472/56/472 | 212 | the code at its defaults is v05, the creep cap and the sync change nothing with one barracks |
| BARRACKS=2, gate 0 (right after the first), sync 0 / 1 | 0.315 / 0.357 | | 181 | an early second barracks loses badly: it delays the towers and the income |
| gate 2, sync 0 / 1 | 0.401 / 0.492 | | 203 | |
| **gate 4, sync 0 / 1** | 0.458 / **0.548** (0.519-0.578) | 534/29/437 | 207 | the only arm above 0.50; the sync is worth +9 pp here |
| gate 6, sync 0 / 1 | 0.469 / 0.468 | | 210 | |
| gate 8 (after the whole tower line), sync 0 / 1 | 0.488 / 0.477 | | 212 | late: 175 of 500 maps changed |

Slow turns: 1-2 per arm in the two gate-2 arms, with 3 of v05's own in the same runs (unchanged
code), at 8 threads in fresh JVMs — host scheduling, as in the sweep of the opening.

Stage B (around the stage A winner, the same maps) and stage C (the arms above 0.53 replicated on the
second development block, seeds 11000-11499, where the stage A winner fell to 0.508):

| Arm (BARRACKS=2, gate 4, TRAIN_SYNC=1 unless stated) | S, block 10000 | S, block 11000 | |
| --- | --- | --- | --- |
| **MAX_TOWERS=6** | **0.574** (0.544-0.604) | **0.542** (0.514-0.570) | above 0.50 on both blocks, +3 pp over the plain arm on each |
| the plain arm (gate 4, sync) | 0.548 | 0.508 | |
| INCOME_TARGET=4 | 0.539 | 0.510 | |
| BARRACKS_FORWARD=50 (the second barracks near the centre) | 0.541 | 0.486 | forward 30: 0.531, 70: 0.517 on block 10000 |
| gate 3 | 0.511 | 0.508 | gate 5: 0.489 |
| MAX_TOWERS=10; BARRACKS=3; gate 2 or 6 with forward 50 | 0.546; 0.455; 0.395 / 0.476 | | block 10000 only |

What the sweep says: an early second barracks (before the first towers) loses badly, a late one (after
6-8 towers) loses a little, and only the window after 4 towers with synchronous waves gains, by a few
points; the forward placement and a third barracks do not help against v05. The games get shorter
(207 turns against the mirror's 212 on block 10000; 168 against 178 on block 11000): more queens die
before the limit. The one arm consistent on both blocks is the second barracks after 4 towers with
synchronous waves and the tower line capped at 6 (the two sites and the queen's turns the second
barracks takes come out of the last towers). The caveat stands: v05 is knights-only with a 700 HP
tower line, so what a concentrated wave does to the arena's thinner lines is for P1 to measure.

## Hypothesis and scope (written before the screen)

- Candidate: `current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6` — v05's opening
  and economy, a second knight barracks once 4 towers stand, both barracks released together, 6 towers.
- Control: frozen `v05` (the selected version and the local champion).
- Expected: S about 0.55 against v05 (0.574 and 0.542 on the two development blocks; a fresh block
  decides; G1 needs the lower bound above 0.50, so S above about 0.515). Δ against `boss` near 0.
- Metric: S, the map score over both seats (draw = 1/2), 95 % CI over maps (`tools/bench_pair.py`).
- Gates (strategy track): G0 no candidate failure of any kind; G1 lower CI bound of S above 0.50 on
  the 2000 maps of the fresh screen block; G2 Δ against `boss` on the block's first 500 maps ≥ −2 pp;
  G3 skipped (`refs/old-3315948/Player.java` is absent). Both local opponents play knights only.
- Screen block: **k=6, seeds 212000-213999**, reserved in `docs/data/seed-ledger.json` before any
  game on it. Only this one arm is screened on the block, so no replication is required.
- Process opponent runs: 3 threads, 200 ms. In-process: 8 threads, 50 ms. Sequential, nothing else
  running. No extension of a series on its interim score; no upload and no adoption decision here.
- If it passes: the candidate is the local champion; the final evaluation against v05 on the next
  evaluation block (k=4, 408000-409999) before any upload, and the freeze are the user's decisions.

Commands (`replays/bench/exp-2026-11-03-army/screen.sh`, copied beside this file):

```
./gradlew bench "-Pbot1=current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6" -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=212000 -PturnMs=50 -Pout=replays/bench/exp-2026-11-03-army/direct.json
./gradlew bench -Pbot1=v05 -Pbot2=v05 -Pmaps=2000 -Pthreads=8 -PseedBase=212000 -PturnMs=50 -Pout=replays/bench/exp-2026-11-03-army/mirror.json
./gradlew bench "-Pbot1=current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=212000 -PturnMs=200 -Pout=replays/bench/exp-2026-11-03-army/boss-cand.json
./gradlew bench -Pbot1=v05 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=212000 -PturnMs=200 -Pout=replays/bench/exp-2026-11-03-army/boss-ctrl.json
python -I tools/bench_pair.py direct replays/bench/exp-2026-11-03-army/direct.json --mirror replays/bench/exp-2026-11-03-army/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-army/g1.json
python -I tools/bench_pair.py common replays/bench/exp-2026-11-03-army/boss-cand.json replays/bench/exp-2026-11-03-army/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-army/g2.json
```

## Screen results (block k=6, seeds 212000-213999)

Summaries `g1.json`, `g2.json` and `g1-rerun.json`; raw reports, logs, `screen.sh` and `recheck.sh` in
`replays/bench/exp-2026-11-03-army/` (copied to the main checkout's `replays/bench/`). Revision 2fda634
with the uncommitted flag code (the reports record the diff's sha256). Raw report sha256 (first 12 hex
digits): `direct.json` f959a6f4b682, `direct-rerun.json` 3cd156df4d4a, `mirror.json` 60e9e2a4f18d,
`boss-cand.json` c70c1cb64dec, `boss-ctrl.json` 61be09a06c3e, `recheck-1thread.json` 82364b230eda.

| Gate | Result | |
| --- | --- | --- |
| G1, direct against v05, 2000 maps | **S = 0.5473** (95 % CI 0.5331-0.5614, SD 0.323); W/D/L 2113/152/1735; by seat 1281/72/647 and 832/80/1088; 1599 of 2000 maps changed against the v05 mirror | pass |
| G2, `boss`, 500 maps | candidate 0.9985 (998/1/1), control 0.9985 (998/1/1), Δ = 0.0000, 0 maps changed: the games end on turn 39 on average, before the fourth tower, so the flags never act | pass (uninformative) |
| G3, `old` | skipped (no code) | — |
| G0 | first direct run: 4 slow turns of the candidate (maps 213203, 213270, 213280, 213282; max 86.1 ms, mean turn 0.03 ms) and 1 of v05. Recheck: maps 213200-213289 on one thread, max turn 8.8 ms, no slow turn; a quiet repeat of the direct series (`direct-rerun.json`): identical outcomes in all 4000 games, S 0.5473, candidate 0 slow turns, this time 2 of v05 (unchanged code, max 61.5 ms). 0 in the `boss` series | pass on the repeat; the first run's slow turns are host scheduling at 8 threads, as on the screen of v05 |

- **Game length:** 195 turns on average against v05 (the mirror 201); 1605 of 4000 games end before the
  limit. The candidate wins by killing the queen more often, not by the HP race.
- **Seats:** the candidate wins on both seats (1281 and 832 of 2000; in the mirror seat 0 wins 1213 of
  2000, so seat 0 is the stronger seat on this block for v05's play too).
- **Rating terms:** at the arena's logistic scale (1.61 in Bronze), S 0.547 head-to-head is about +0.3
  rating points against v05: a small effect, below the upload recommendation's S ≥ 0.60 (about +0.65).
- **Development estimates held:** 0.574 and 0.542 on the two development blocks, 0.547 on the fresh block.

**Verdict:** P2 passed (G0 on the quiet repeat, G1, G2 uninformative, G3 skipped). The candidate
`current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6` is the local champion by the policy.
The final evaluation on evaluation block k=4 (408000-409999) and the freeze are the user's decisions; with
S about 0.55 the upload recommendation (S ≥ 0.60) is unlikely to be met, so an upload would be the user's
exception, argued by the arena field: the gain comes from killing the queen, and the arena's opponents
hold thinner tower lines than v05's 700 HP line, which is where a concentrated wave of 8 knights should
count for more than against the mirror. Both local opponents play knights only.

## Final evaluation (block k=4, seeds 408000-409999)

On the user's decision ("let's continue") after the screen. The same arms, gates and series as the
screen, on the evaluation block reserved in the ledger before any game on it, plus P2's upload
recommendation S ≥ 0.60. Commands: `eval.sh` beside this file (the screen's with `-PseedBase=408000`,
reports in `replays/bench/exp-2026-11-03-army-eval/`); summaries `eval-g1.json` and `eval-g2.json`. One
run; no extension. Raw report sha256 (first 12 hex digits): `direct.json` 1484fbc960b4, `mirror.json`
3f451b6cc7c4, `boss-cand.json` 0d2812c4e6ab, `boss-ctrl.json` 916ebf3b037f. Revision 2fda634 with the
uncommitted flag code (after the two comment-only edits; the reports record the diff's sha256).

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line in any series (longest candidate turn 30.6 ms against v05, 10.7 ms against `boss`) | pass |
| G1, direct against v05, 2000 maps | **S = 0.5480** (95 % CI 0.5327-0.5633, SD 0.349); W/D/L 2144/96/1760; by seat 1243/47/710 and 901/49/1050; 1948 of 2000 maps changed against the mirror | pass |
| G2, `boss`, 500 maps | candidate 0.9970 (997/0/3), control 0.9980 (998/0/2), Δ = −0.0010 (CI −0.0030..+0.0010); 243 of 500 maps changed: on this block the boss games last 77 turns, so the second barracks acts in half of them | pass |
| G3, `old` | skipped (no code) | — |
| Upload recommendation, S ≥ 0.60 | 0.5480 | **not met** |

- The screen (0.5473) and the evaluation (0.5480) agree; the development blocks gave 0.574 and 0.542.
  The games against v05 last 240 turns on average here (the mirror 242), against 195 on the screen block
  (its mirror 201): the blocks differ in how often v05 kills a queen, the candidate's edge does not.
- **Rating terms:** at the arena's logistic scale (1.72 in Bronze after the third v05 upload), S 0.548
  head-to-head is about +0.33 rating points against v05, inside the arena's noise (M 2.61).

**Verdict:** P2 passed (G0-G2; G3 skipped) and the candidate is the local champion, but the upload
recommendation (S ≥ 0.60, about +0.7 rating points) is not met. By the policy an upload is the user's
exception: the local measure is head-to-head against a knights-only 700 HP tower line, and the
candidate's gain comes from killing the queen, which the arena's thinner lines may reward more, or not.
The freeze (v06), `submit`, both `procCheck` and the upload have not been run.
