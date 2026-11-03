# Experiment 2026-11-03: kiting under knights (KITE), P2 strategy screen against v06 with G4 (`field`)

From the study `docs/data/study-2026-11-03-arena-winners/`: the arena opponents who beat v06 MOVE on
42 % of their turns under knights and cover 36 units a turn (v06: 0 % and 14), start moving when knights are
still 250-500 away (44 % against our 8 %), and lose a third less HP per contact turn than the opponents who
lost; v06 in its losses spends 50 turns in contact, 90 % of them at a tower topping it up. The direction
chosen by the user after the field opponent (2026-11-03). Starting revision: `64e61fe`, clean worktree
`claude/bot-improvement-direction-4fe687`; frozen v06, `field`, the referee and the harness unchanged.
Local Java 17.0.12, Gradle 8.11.1, Windows 11, 8 bench threads.

## The parameters (code)

`cr.bot.Params`, read by `MainBot` (`current`); the defaults play as v06 command for command
(`MainBotTest.withDefaultsCurrentPlaysAsV06`; every version snapshot records the new fields at the frozen
behaviour):

- `KITE` (0): with 1, when enemy knights are within `KITE_RANGE` and the defence rule has nothing to build
  (`MainBot.kite`, before the touch rule), the queen MOVEs 200 ahead on the one of 24 headings whose
  predicted step (`Physics.queenStep`, the sites only) leaves her farthest from the nearest knight, at
  least 5 farther than now; the step must move her, end out of enemy tower range and, with `KITE_COVER=1`
  and an own tower covering her now, under an own tower's cover; ties go to the step ending nearer her
  nearest own tower. When no step gains, v06's rules (the top-up, the flight) apply.
- `KITE_RANGE` (250): the knights' distance that starts the kiting; 400-500 start before contact.
- `KITE_COVER` (1): the cover condition above.

Tests: `StrategyParamsTest.kiteMovesAwayFromKnightsUnderTowerCover`, the version identity tests of
`MainBotTest` and `BotFactoryTest`.

## Development sweep (seeds 10000-10499, 500 maps, both seats; `replays/bench/dev-kite-2026-11-03/`)

`sweep-a.sh`, `sweep-b.sh` and `sweep_table.py` beside this file; the full table `dev-sweep.txt`. One JVM per
arm, `BenchmarkRunner` directly, 8 threads, 50 ms. Against v06 (the mirror is the late-income sweep's) and
against `field` (the control: v06 against `field` on the same maps).

| Arm | S vs v06 | S vs `field` (control 0.718) | |
| --- | --- | --- | --- |
| defaults (KITE=0) | 0.500 | 0.718 | |
| **KITE=1, range 250** | **0.681** (0.650-0.712) | **0.762** (+4.4 pp) | gains against both |
| KITE=1, range 250, cover 0 | 0.667 | | |
| KITE=1, range 400 / cover 0 | 0.682 / 0.689 | 0.717 (0 pp) | |
| KITE=1, range 500 / cover 0 | 0.720 / 0.670 | **0.598 (−12 pp)** | the best against the mirror, the worst against the field's balance: starting to run at 500 leaves the ladder and the mines to the faster-earning opponent |

Slow turns: 1-4 per arm in half the arms, with v06's own in the same runs (unchanged code), at 8 threads
in fresh JVMs: host scheduling as in the earlier sweeps (the kiting adds at most 24 step predictions a turn).

Stage B, the arms replicated on seeds 11000-11499 (the v06 mirror is the late-income sweep's `r-mirror`;
the `field` control there 0.718 again):

| Arm | S vs v06, block 11000 | S vs `field`, block 11000 (control 0.718) | pooled over the two blocks |
| --- | --- | --- | --- |
| **KITE=1, range 250** | **0.709** | **0.809** (+9.1 pp) | v06 0.695; `field` +6.8 pp |
| KITE=1, range 250, cover 0 | 0.703 | 0.808 (+9.0 pp) | within noise of cover 1 |
| KITE=1, range 400 | 0.745 | 0.762 (+4.4 pp) | v06 0.714; `field` +2.2 pp |
| KITE=1, range 500 | 0.739 | (block 10000: −12 pp) | the gain against the mirror does not carry to the field's balance |

What the sweep says: kiting is worth about +20 pp against v06 and +7 pp against `field` at the contact
range (250), on both blocks and with or without the cover condition; starting earlier gains a little more
against the mirror and loses against `field`, so the contact range is the candidate and the cover
condition stays (it keeps the queen under her towers, which is where the knights die).

## Hypothesis and scope (written before the screen)

- Candidate: `current?KITE=1` — v06 with kiting at the contact range (KITE_RANGE=250, KITE_COVER=1).
- Control: frozen `v06` (the selected version and the local champion).
- Expected: S about 0.70 against v06 (0.681 and 0.709 on the two development blocks; a fresh block
  decides); Δ against `boss` near 0; Δ against `field` about +7 pp (G4).
- Metric: S, the map score over both seats (draw = 1/2), 95 % CI over maps (`tools/bench_pair.py`).
- Gates (strategy track, PV2): G0 no candidate failure of any kind; G1 lower CI bound of S above 0.50 on
  the 2000 maps of the fresh screen block; G2 Δ against `boss` on the block's first 500 maps ≥ −2 pp;
  G3 skipped (`refs/old-3315948/Player.java` is absent); G4 Δ against `field` on the block's 2000 maps
  ≥ −2 pp.
- Screen block: **k=8, seeds 216000-217999**, reserved in `docs/data/seed-ledger.json` before any
  game on it. Only this one arm is screened on the block, so no replication is required.
- Process opponent runs: 3 threads, 200 ms. In-process: 8 threads, 50 ms. Sequential, nothing else
  running. No extension of a series on its interim score; no upload and no adoption decision here.
- If it passes: the candidate is the local champion; the final evaluation against v06 on the next
  evaluation block (k=6, 412000-413999) before any upload, and the freeze (v07) are the user's decisions.

Commands: `screen.sh` beside this file (the reports in `replays/bench/exp-2026-11-03-kite/`).

## Screen results (block k=8, seeds 216000-217999)

Summaries `g1.json`, `g2.json` and `g4.json`; raw reports, logs and `screen.sh` in
`replays/bench/exp-2026-11-03-kite/` (copied to the main checkout's `replays/bench/`). Revision 64e61fe with
the uncommitted kiting code (the reports record the diff's sha256). Raw report sha256 (first 12 hex digits):
`direct.json` e64b5ef765a1, `mirror.json` 42aa76c8be86, `field-cand.json` 072787286fa9, `field-ctrl.json`
8d805a4bb486, `boss-cand.json` 7918ef74f80c, `boss-ctrl.json` 95965a4ccf3c.

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line of the candidate in any series (longest turn 36.9 ms against `field`, 25.2 against v06, 34.8 against `boss`; mean 0.07 ms) | pass |
| G1, direct against v06, 2000 maps | **S = 0.6946** (95 % CI 0.6784-0.7109, SD 0.371); W/D/L 2752/53/1195; by seat 1444/27/529 and 1308/26/666; 2000 of 2000 maps changed | pass |
| G2, `boss`, 500 maps | candidate 0.998 (998/0/2), control 0.997 (996/1/3), Δ = +0.0015 (CI −0.0007..+0.0037) | pass |
| G3, `old` | skipped (no code) | — |
| G4, `field`, 2000 maps | candidate 0.7466 (2974/25/1001), control 0.7157 (2851/24/1125), **Δ = +0.0309** (CI +0.0120..+0.0497); 1999 of 2000 maps changed | pass |

- **Game length:** 226 turns on average against v06 (the mirror 226); against `field` 212 (the control 200).
- **Seats:** the candidate wins on both seats, 1444 and 1308 of 2000 against v06.
- **Development estimates held:** 0.681 and 0.709 on the two development blocks, 0.695 on the fresh block;
  against `field` +4.4 and +9.1 pp, +3.1 on the fresh block.
- **Rating terms:** at the arena's logistic scale (1.95 in Bronze), S 0.695 head-to-head is about +1.6
  rating points against v06, the largest local gain of the project after the early towers (v03) and the
  two-mines opening (v05).

**Verdict:** P2 passed (G0, G1, G2, G4; G3 skipped). The candidate `current?KITE=1` is the local champion.
The final evaluation on evaluation block k=6 (412000-413999) before any upload, the freeze (v07) and the
upload are the user's decisions; at S about 0.70 the upload recommendation (S ≥ 0.60) would be met.

## Final evaluation (block k=6, seeds 412000-413999; written before the run)

On the user's decision after the screen. The same arms, gates and series as the screen (G0-G2, G4; G3
skipped), on the evaluation block reserved in the ledger before any game on it, plus P2's upload
recommendation S ≥ 0.60. Commands: `eval.sh` beside this file (the screen's with `-PseedBase=412000`,
reports in `replays/bench/exp-2026-11-03-kite-eval/`); summaries `eval-g1.json`, `eval-g2.json`,
`eval-g4.json`. One run; no extension; no upload decision here.

### Results

Summaries `eval-g1.json`, `eval-g1-rerun.json`, `eval-g2.json` and `eval-g4.json`; raw reports, logs, `eval.sh`
and `recheck.sh` in `replays/bench/exp-2026-11-03-kite-eval/` (sha256 first 12 hex digits: `direct.json`
7db58dac58a0, `direct-rerun.json` f6641a2d7caa, `mirror.json` cd8679af0863, `field-cand.json` 1c8153984003,
`field-ctrl.json` 82c0c15a4bd8, `boss-cand.json` 8ebb1e9b085a, `boss-ctrl.json` 7579fe9388cc,
`recheck-1thread.json` 23b85519b291). Revision 64e61fe with the uncommitted kiting code and the comment-stripping
merger (the reports record the diff's sha256).

| Gate | Result | |
| --- | --- | --- |
| G0 | first direct run: 4 slow turns of the candidate on maps 412270, 412271, 412275, 412280 (consecutive maps = one moment of wall-clock time; max 73.8 ms, mean turn 0.05 ms), with 1 of v06 there and 4 of v06 in the mirror (max 106 ms, unchanged code). Recheck: maps 412260-412289 on one thread, max turn 8.8 ms, no slow turn; a quiet repeat of the direct series: identical outcomes in all 4000 games, S 0.6803, candidate 0 slow turns (1 of v06). The `field` and `boss` series clean (max candidate turn 30.5 ms against `field`) | pass on the repeat; the first run's slow turns are host scheduling at 8 threads, as on the screens of v05 and the army candidate |
| G1, direct against v06, 2000 maps | **S = 0.6803** (95 % CI 0.6641-0.6964, SD 0.368); W/D/L 2692/58/1250; by seat 1399/30/571 and 1293/28/679; 2000 of 2000 maps changed | pass |
| G2, `boss`, 500 maps | candidate 1.000 (1000/0/0), control 0.999 (999/0/1), Δ = +0.0010 | pass |
| G3, `old` | skipped (no code) | — |
| G4, `field`, 2000 maps | candidate 0.7675 (3053/34/913), control 0.7322 (2910/38/1052), **Δ = +0.0352** (CI +0.0163..+0.0542) | pass |
| Upload recommendation, S ≥ 0.60 | 0.6803 | **met** |

- The screen (0.6946) and the evaluation (0.6803) agree within their intervals; against `field` +3.1 and
  +3.5 pp. The games against v06 last 243 turns on average here (the mirror 243).
- **Rating terms:** at the arena's logistic scale (1.95 in Bronze), S 0.680 head-to-head is about +1.5
  rating points against v06.

**Verdict:** P2 passed with the upload recommendation met (G0 on the quiet repeat, G1, G2, G4; G3 skipped).
`current?KITE=1` is the local champion; the freeze as v07 and the upload under P1 are the user's decisions.
Before any paste: `./gradlew submit` (with `-Pparams=KITE=1`, or with empty PARAMS once the default is 1)
and both `procCheck` series; the merger now strips comments, so the artifact is about 79,000 characters.
