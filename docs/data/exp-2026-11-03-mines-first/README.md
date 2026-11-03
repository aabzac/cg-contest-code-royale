# Experiment 2026-11-03: the opening order (MINES_FIRST), P2 strategy screen against v04

From the loss study `docs/data/study-2026-11-03-losses/`: v04 opens with the knight barracks on
turn 1 in 99 % of its arena games, while every stronger group and 80 % of the opponents who beat it
open with a mine and build the barracks on turn 7-13; our income stays 3, theirs 4-7; we hold 6-7
towers at 700 HP and spend 57 of 100 queen turns on top-ups, they hold 4 at about 450. The user's
two hypotheses: a mine first (no creep threat on the first turns, income always pays), and no need
to pump towers to 700. Starting revision: `191dea4`, clean worktree `claude/sweet-bartik-d9ce19`;
frozen v04, the referee and the harness are unchanged. Local Java 17.0.12, Gradle 8.11.1,
Windows 11, 8 bench threads.

## The parameters (code)

`cr.bot.Params`, read by `MainBot` (`current`); their defaults play as v04 command for command
(`MainBotTest.withDefaultsCurrentPlaysAsV04`, `BotFactoryTest`; every version snapshot records them
at the frozen behaviour):

- `MINES_FIRST` (0): own mines built before the knight barracks. While fewer stand, the next site
  to take is the nearest free gold site (the touch rule builds a mine there with no knight near);
  with no known gold site the ladder goes on as before (the barracks), so the queen never idles.
- `TOWER_HP` (700): the top-up threshold of a touched own tower (was the literal 700).
- `UPGRADE` (0): with 1, while the income is below `INCOME_TARGET`, own mines below their maximum
  are targets too, so the queen walks to the nearest of a free gold site and such a mine.
- `INCOME_TARGET` and `MAX_TOWERS` as before.

The ladder's target choice is now one function, `MainBot.nextSite`, used by the bot and by the
planner's next-target prediction (`ApproachPlanner.nextTarget`), so ROUTE's landing point follows
the new order. Tests: `StrategyParamsTest` (the opening order with 0, 1 and 2 mines first and with
no gold site known; the threshold; the upgrade target and its next-target prediction).

## Development sweep (seeds 10000-10499, 500 maps, both seats, against v04; the v04 mirror on the same maps)

`replays/bench/dev-strategy-2026-11-03/` (gitignored; `sweep-a.sh`, `sweep-b.sh` and the table
tool `sweep_table.py` beside this file; the full table with CIs, seats and failures is
`dev-sweep.txt`). One JVM per arm, `BenchmarkRunner` directly, 8 threads, 50 ms. Stage A, 48 arms
(MINES_FIRST 0/1 × INCOME_TARGET 3/5 × UPGRADE 0/1 × MAX_TOWERS 4/6/8 × TOWER_HP 400/700):

| Change from v04 (the others at v04's values) | S vs v04 | |
| --- | --- | --- |
| none (`mf0-it3-up0-mt8-hp700`) | 0.500, 0 maps changed | the code at its defaults is v04 |
| MINES_FIRST=1 | **0.684** (CI 0.648-0.720) | the largest single effect |
| INCOME_TARGET=5 | 0.290 | locally a loss: the income phase delays the towers |
| MINES_FIRST=1 and INCOME_TARGET=5 | 0.541 | |
| TOWER_HP=400 | 0.293 (with MINES_FIRST=1: 0.492) | thinner towers lose to v04's 700 HP line |
| MAX_TOWERS=4 / 6 | 0.399 / 0.517 (with MINES_FIRST=1: 0.646 / 0.647) | fewer towers lose a little |
| UPGRADE=1 | 0.494; within ±0.005 of UPGRADE=0 in every other pair | no effect |

Stage B, 20 arms around MINES_FIRST (INCOME_TARGET 2/3/4, MAX_TOWERS 8/12, TOWER_HP 600/700/800):

| Arm | S vs v04 (95 % CI) | W/D/L | Mean turns |
| --- | --- | --- | --- |
| MINES_FIRST=2, INCOME_TARGET=2 | 0.768 (0.738-0.799) | 757/23/220 | 231 |
| **MINES_FIRST=2** (INCOME_TARGET=3, the screened arm) | **0.760 (0.729-0.791)** | 749/22/229 | 228 |
| MINES_FIRST=2, INCOME_TARGET=4 | 0.735 | | 222 |
| MINES_FIRST=1, INCOME_TARGET=2 | 0.700 | | 246 |
| MINES_FIRST=1 | 0.684 | | 239 |
| MINES_FIRST=3 (INCOME_TARGET 2/3/4) | 0.673 / 0.673 / 0.657 | | 202 |
| MINES_FIRST=1, TOWER_HP=600 / 800 | 0.616 / 0.474 | | |
| MAX_TOWERS=12 | within ±0.02 of 8 in every pair | | |

Two mines before the barracks is the best opening found; the third mine already costs. The income
target and the tower cap do not matter once the opening is fixed, and the top-up threshold is best
where it is (700; 800 pumps every tower to the maximum and loses). The local opponent (v04, knights
only, a 700 HP tower line) is the caveat for the tower parameters: the arena's stronger players hold
thinner lines against a different field, and only the P1 placements can measure that. Slow turns in
the sweep (0-6 per 1000 games, maxima up to 357 ms) come from fresh JVMs at 8 threads (JIT warm-up)
and count for nothing here; the screen runs through Gradle as the earlier screens did.

## Hypothesis and scope (written before the screen)

- Candidate: `current?MINES_FIRST=2` — two mines, then the knight barracks, then the ladder as v04
  (income 3, 8 towers at 700). One parameter changed from v04.
- Control: frozen `v04` (the selected version and the local champion).
- Expected: S well above 0.60 against v04 (the development estimate 0.76 on a used block; a fresh
  block decides). Δ against `boss` near 0 (v04 beats it in about 99 % of games already).
- Metric: S, the map score over both seats (draw = 1/2), 95 % CI over maps (`tools/bench_pair.py`).
- Gates (strategy track): G0 no candidate failure of any kind; G1 lower CI bound of S above 0.50 on
  the 2000 maps of the fresh screen block; G2 Δ against `boss` on the block's first 500 maps ≥ −2 pp;
  G3 skipped (`refs/old-3315948/Player.java` is absent). Both local opponents play knights only.
- Screen block: **k=5, seeds 210000-211999**, reserved in `docs/data/seed-ledger.json` before any
  game on it. Only this one arm is screened on the block, so no replication is required.
- Process opponent runs: 3 threads, 200 ms. In-process: 8 threads, 50 ms. Sequential, nothing else
  running. No extension of a series on its interim score; no upload and no adoption decision here.
- If it passes: the candidate is the local champion; the final evaluation against v04 on the next
  evaluation block (k=3, 406000-407999) before any upload, and the freeze are the user's decisions.

Commands (`replays/bench/exp-2026-11-03-mines-first/screen.sh`):

```
./gradlew bench "-Pbot1=current?MINES_FIRST=2" -Pbot2=v04 -Pmaps=2000 -Pthreads=8 -PseedBase=210000 -PturnMs=50 -Pout=replays/bench/exp-2026-11-03-mines-first/direct.json
./gradlew bench -Pbot1=v04 -Pbot2=v04 -Pmaps=2000 -Pthreads=8 -PseedBase=210000 -PturnMs=50 -Pout=replays/bench/exp-2026-11-03-mines-first/mirror.json
./gradlew bench "-Pbot1=current?MINES_FIRST=2" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=210000 -PturnMs=200 -Pout=replays/bench/exp-2026-11-03-mines-first/boss-cand.json
./gradlew bench -Pbot1=v04 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=210000 -PturnMs=200 -Pout=replays/bench/exp-2026-11-03-mines-first/boss-ctrl.json
python -I tools/bench_pair.py direct replays/bench/exp-2026-11-03-mines-first/direct.json --mirror replays/bench/exp-2026-11-03-mines-first/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-03-mines-first/g1.json
python -I tools/bench_pair.py common replays/bench/exp-2026-11-03-mines-first/boss-cand.json replays/bench/exp-2026-11-03-mines-first/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-03-mines-first/g2.json
```

Raw reports: `replays/bench/exp-2026-11-03-mines-first/` (gitignored; copied to the main checkout's
`replays/bench/` at the end of the session), bench stdout in the `.log` beside each report.
Summaries `g1.json` and `g2.json` beside this file.

## Screen results (block k=5, seeds 210000-211999)

Summaries `g1.json`, `g2.json` and `g1-rerun.json`; raw reports, logs and `screen.sh` / `recheck.sh`
in `replays/bench/exp-2026-11-03-mines-first/`. Revision 191dea4 with the uncommitted parameter code
(the reports record the diff's sha256; committed right after as the next commit).

| Gate | Result | |
| --- | --- | --- |
| G1, direct against v04, 2000 maps | **S = 0.7645** (95 % CI 0.7492-0.7798, SD 0.349); W/D/L 3018/80/902; by seat 1554/41/405 and 1464/39/497; 2000 of 2000 maps changed against the v04 mirror | pass |
| G2, `boss`, 500 maps | candidate 1.000 (1000/0/0), control 0.990 (990/0/10), Δ = +0.0100 (CI +0.0027..+0.0173) | pass |
| G3, `old` | skipped (no code) | — |
| G0 | first run of the direct series: 5 slow turns of the candidate in 4 games (maps 210258, 210261, 210264, 210265, consecutive maps = one moment of wall-clock time; nothing else of ours was running); 0 in the `boss` series. Recheck: the 8 maps 210258-210265 on one thread, max turn 5.6 ms, no slow turn; a quiet repeat of the whole direct series (`direct-rerun.json`, identical outcomes in all 4000 games, S 0.7645): candidate 0 slow turns, this time 4 slow turns of v04 (max 70.6 ms), whose code is unchanged. The mirror run also had one 51.9 ms turn of v04. | pass on the repeat; the first run's slow turns are host scheduling at 8 threads, not the bot's work (mean turn 0.04-0.07 ms) |

- **Game length:** 227 turns on average against v04 (the mirror 243): more games are decided before the
  limit. Against `boss` the candidate's games last 80 turns against v04's 126: with the economy first it
  kills the Default AI's queen almost twice as fast.
- **Seats:** the candidate wins on both seats, 1554 and 1464 of 2000.
- **No winner's curse seen:** the development estimate was 0.760 on 500 used maps; the fresh block gives 0.7645.
- **Rating terms:** at the arena's logistic scale (about 1.5 in Bronze), S 0.76 head-to-head corresponds to
  about +1.7 rating points against v04 — a head-to-head number, not the field.

Raw report sha256 (first 12 hex digits): `direct.json` 2475195ee995, `mirror.json` 088aa6e03a41,
`boss-cand.json` c5221d3c86cb, `boss-ctrl.json` 7ad5ece2fe1c.

**Verdict:** P2 passed (G0 on the quiet repeat, G1, G2; G3 skipped). `current?MINES_FIRST=2` is the local
champion. The final evaluation against v04 on evaluation block k=3 (406000-407999) before any upload, the
freeze (v05) and the upload are the user's decisions. Caveat for the arena: both local opponents build
knights only and v04 holds the tower line the candidate keeps, so the opening gain is measured against
a tower-heavy field; the study's stronger players open the same way, which supports the direction.
