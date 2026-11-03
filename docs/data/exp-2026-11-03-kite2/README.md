# Experiment 2026-11-03: kiting bound to the towers (KITE=2), P2 strategy screen with G4

From the study `docs/data/study-2026-11-03-v07-arena/`: v07's kiting halves the HP lost per contact turn
on the arena but the queen runs out of her towers' cover (34 % of the contact turns near a tower against
v06's 90 %), the knights chase her for 50-130 turns without dying, the top-ups halve and the kills of the
enemy queen fall; the arena gave v07 −0.71 against v06 where P2 had promised +1.5. The direction chosen
by the user (2026-11-03): kiting that stays under the towers. Starting revision: `8c1e43d`, worktree
`claude/bot-improvement-direction-4fe687` with the uncommitted records of the two uploads; frozen v06 and
v07, `field`, the referee and the harness unchanged. Local Java 17.0.12, 8 bench threads.

## The parameters (code)

`cr.bot.Params`, read by `MainBot` (`current`); the defaults play as v07 command for command
(`MainBotTest.withDefaultsCurrentPlaysAsV07`; the snapshots record the new fields):

- `KITE=2`: as KITE=1 (24 headings, the predicted step, out of enemy tower range), but every step must
  end within an own tower's range whatever the queen's cover now, and the score is the distance to the
  nearest knight minus `KITE_PULL` percent of the distance to the nearest own tower; with no step under
  cover (no tower in reach) nothing is kited and v06's flight to the nearest tower applies.
- `KITE_PULL` (50): the pull towards the tower in the score; 100 weighs a unit of distance from the
  tower like a unit of distance from the knights.
- `KITE_MAX` (0): with N > 0, after N consecutive kiting turns the other rules get a turn (the top-up at
  the tower she stands by).
- `cr.tools.LocalProfile` now prints the behaviour under knights of local games in the arena studies'
  terms: contact turns, % near an own tower, HP lost per contact turn, % MOVE.

Tests: `StrategyParamsTest.boundKitingStaysUnderTheTowersAndFleesOutOfReach` (the covered step, the flight
out of reach where KITE=1 would run, the cap), the version identity tests of `MainBotTest` and
`BotFactoryTest`.

## Development sweep (seeds 10000-10499, 500 maps, both seats; `replays/bench/dev-kite2-2026-11-03/`)

`sweep-a.sh`, `sweep-b.sh` and `sweep_table.py` beside this file; the full table `dev-sweep.txt`. Against v06
(the mirror and the v06-vs-`field` control 0.718 are the kiting sweep's on these maps) and against `field`.

| Arm (KITE=2) | S vs v06 | S vs `field` (control 0.718) | v07's rule (KITE=1) for comparison |
| --- | --- | --- | --- |
| pull 30, cap 0 / 8 | 0.739 / 0.740 | 0.854 / 0.853 | 0.681 / 0.762 |
| pull 50, cap 0 / 8 | 0.737 / 0.736 | 0.871 / 0.875 | |
| **pull 100**, cap 0 / 8 | **0.785** / 0.782 | **0.915** / 0.916 | |
| pull 200 | 0.790 | 0.912 | |

The behaviour under knights (`LocalProfile`, 50 maps against v06; the arena numbers of the studies for
comparison):

| | contact turns | % near an own tower | HP lost per contact turn | % MOVE |
| --- | --- | --- | --- | --- |
| v06 (KITE=0), local | 35 | 91 | 0.8 | 0 |
| v07 (KITE=1), local | 45 | 39 | 0.4 | 70 |
| KITE=2, pull 50 | 43 | 67 | 0.5 | 71 |
| **KITE=2, pull 100** | 40 | **87** | 0.4 | 65 |
| KITE=2, pull 200 | 41 | 89 | 0.4 | 66 |
| v07 on the arena, its losses | 59 | 34 | 0.9 | 75 |
| the arena's winners over v06 | 36 | 64 | 1.0 | 42 |

What the sweep says: the bound kiting keeps the queen near her towers (87 % of the contact turns at pull
100, where v07's rule left her there 39 % locally and 34 % on the arena) while keeping the halved HP loss
per contact turn, and the contact turns fall back towards v06's; the cap adds nothing. Locally it beats v06
by 28 pp and `field` by 20 pp over the control, far above v07's rule (18 and 4 pp). The local proxy
over-rewarded v07's rule once (both local opponents stand still under knights), so these numbers are a
no-regression check and a check of the mechanism, not a prediction of the arena; the mechanism that
failed on the arena (leaving the cover) is what KITE=2 removes by construction.

Stage B, replicated on seeds 11000-11499 (the kiting sweep's `r-mirror` and `rf-control` 0.718 there), and
against v07, the selected version and the control of the screen (the v07 mirror on block 10000):

| Arm (KITE=2) | S vs v06, block 11000 | S vs `field`, block 11000 | S vs v07, block 10000 / 11000 |
| --- | --- | --- | --- |
| **pull 100** | **0.808** | **0.907** (+18.9 pp) | **0.689 / 0.658** |
| pull 200 | 0.801 | 0.921 | 0.681 / — |

Pull 100 and 200 are within noise of each other on every opponent; 100 is the candidate (the milder pull).

## Hypothesis and scope (written before the screen)

- Candidate: `current?KITE=2,KITE_PULL=100` — v07 with the kiting bound to the towers (every step under an
  own tower's range, the pull to the nearest tower weighed like the distance from the knights, no cap).
- Control: frozen `v07` (the selected version and the local champion).
- Expected: S about 0.67 against v07 (0.689 and 0.658 on the two development blocks; a fresh block
  decides); Δ against `boss` near 0; Δ against `field` about +15 pp (G4; v07 beats `field` at about 0.75).
- Metric: S, the map score over both seats (draw = 1/2), 95 % CI over maps (`tools/bench_pair.py`).
- Gates (strategy track, PV2): G0 no candidate failure of any kind; G1 lower CI bound of S above 0.50 on
  the 2000 maps of the fresh screen block; G2 Δ against `boss` on the block's first 500 maps ≥ −2 pp;
  G3 skipped (`refs/old-3315948/Player.java` is absent); G4 Δ against `field` on the block's 2000 maps
  ≥ −2 pp.
- Screen block: **k=9, seeds 218000-219999**, reserved in `docs/data/seed-ledger.json` before any
  game on it. Only this one arm is screened on the block, so no replication is required.
- Process opponent runs: 3 threads, 200 ms. In-process: 8 threads, 50 ms. Sequential, nothing else
  running. No extension of a series on its interim score; no upload and no adoption decision here.
- Caveat written down in advance: the local proxy over-rewarded v07's kiting (both local opponents stand
  still under knights); the screen checks no regression and the mechanism (the queen near her towers), the
  arena decides the version.
- If it passes: the candidate is the local champion; the final evaluation against v07 on the next
  evaluation block (k=7, 414000-415999) before any upload, and the freeze (v08) are the user's decisions.

Commands: `screen.sh` beside this file (the reports in `replays/bench/exp-2026-11-03-kite2/`).

## Screen results (block k=9, seeds 218000-219999)

Summaries `g1.json`, `g1-rerun.json`, `g2.json` and `g4.json`; raw reports, logs, `screen.sh` and `recheck.sh`
in `replays/bench/exp-2026-11-03-kite2/` (copied to the main checkout's `replays/bench/`). Revision 8c1e43d
with the uncommitted KITE=2 code (the reports record the diff's sha256). Raw report sha256 (first 12 hex
digits): `direct.json` 6bc30bb36ba8, `direct-rerun.json` 89ff9880a6bc, `mirror.json` 0dda374ae08d,
`field-cand.json` 37e7228e088c, `field-ctrl.json` 0678e9e9a63c, `boss-cand.json` 69761414bb3f,
`boss-ctrl.json` e20f65348e75, `recheck-1thread.json` 910c20950d33.

| Gate | Result | |
| --- | --- | --- |
| G0 | first direct run: 7 slow turns of the candidate on maps 218042 and 218081-218092 (max 132 ms, mean turn 0.08 ms) and 7 of v07 in the same run (max 94 ms, unchanged code). Recheck: maps 218040-218099 on one thread, max turn 17.0 ms, no slow turn; a quiet repeat of the direct series: identical outcomes in all 4000 games, S 0.6663, 0 slow turns on either side. The `field` and `boss` series clean for the candidate (`field` itself had one slow turn) | pass on the repeat; host scheduling at 8 threads, as on the earlier screens |
| G1, direct against v07, 2000 maps | **S = 0.6663** (95 % CI 0.6516-0.6809, SD 0.335); W/D/L 2571/188/1241; by seat 1308/94/598 and 1263/94/643; 2000 of 2000 maps changed | pass |
| G2, `boss`, 500 maps | candidate 1.000 (1000/0/0), control 0.999 (999/0/1), Δ = +0.0010 | pass |
| G3, `old` | skipped (no code) | — |
| G4, `field`, 2000 maps | candidate 0.9204 (3672/19/309), control 0.7835 (3118/32/850), **Δ = +0.1369** (CI +0.1225..+0.1513) | pass |

- **Game length:** 223 turns on average against v07 (the mirror 220); against `field` 185 (the control 184).
- **Seats:** the candidate wins on both seats, 1308 and 1263 of 2000 against v07.
- **Development estimates held:** 0.689 and 0.658 on the two development blocks, 0.666 on the fresh block;
  against `field` +19 pp on the blocks, +14 pp here.
- **Rating terms:** at the arena's logistic scale (1.97 in Bronze), S 0.666 head-to-head against v07 is
  about +1.4 rating points. The caveat written before the screen stands: the local proxy over-rewarded v07's
  kiting; here the mechanism check is the point (the queen near her towers 87 % of the contact turns
  against v07's 39 %, `LocalProfile`), and the arena decides.

**Verdict:** P2 passed (G0 on the quiet repeat, G1, G2, G4; G3 skipped). The candidate
`current?KITE=2,KITE_PULL=100` is the local champion. The final evaluation on evaluation block k=7
(414000-415999) before any upload, the freeze (v08) and the upload are the user's decisions.

## Final evaluation (block k=7, seeds 414000-415999; written before the run)

On the user's decision after the screen. The same arms, gates and series as the screen (G0-G2, G4; G3
skipped), on the evaluation block reserved in the ledger before any game on it, plus P2's upload
recommendation S ≥ 0.60. Commands: `eval.sh` beside this file (the screen's with `-PseedBase=414000`,
reports in `replays/bench/exp-2026-11-03-kite2-eval/`); summaries `eval-g1.json`, `eval-g2.json`,
`eval-g4.json`. One run; no extension; no upload decision here.

### Results

Summaries `eval-g1.json`, `eval-g1-rerun.json`, `eval-g2.json`, `eval-g4.json` and `eval-g4-rerun.json`; raw
reports, logs, `eval.sh` and `recheck-eval.sh` in `replays/bench/exp-2026-11-03-kite2-eval/` (sha256 first 12 hex
digits: `direct.json` 410d17230fad, `direct-rerun.json` 4b7a532b1788, `mirror.json` a9abc9bbeabf,
`field-cand.json` cb697257d05c, `field-cand-rerun.json` 71a377fbe7c9, `field-ctrl.json` 869a0700fda8,
`boss-cand.json` 88829ce06b0e, `boss-ctrl.json` 2ef579bc90ba, `recheck-1thread-v07.json` 90729eb42ca1,
`recheck-1thread-field.json` 7533766a2701). Revision 8c1e43d with the uncommitted KITE=2 code.

| Gate | Result | |
| --- | --- | --- |
| G0 | first runs: one slow candidate turn of 50.1 ms on map 414083 against v07 (v07 itself had 4, max 91.6 ms, unchanged code) and one of 50.6 ms on map 414137 against `field`. Recheck: the two regions on one thread, max turn 5.4 and 13.6 ms, no slow turn; quiet repeats of both series: identical outcomes in all 4000 games of each, 0 slow turns on either side | pass on the repeats; host scheduling at 8 threads, the two turns at the threshold |
| G1, direct against v07, 2000 maps | **S = 0.6670** (95 % CI 0.6522-0.6818, SD 0.338); W/D/L 2562/212/1226; by seat 1313/107/580 and 1249/105/646; 2000 of 2000 maps changed | pass |
| G2, `boss`, 500 maps | candidate 1.000 (996/0/0), control 1.000 (996/0/0), Δ = 0.0000; maps 414135 and 414136 dropped (the `boss` process timed out in 4 games of the control series, 0.4 % of the maps, the series valid) | pass |
| G3, `old` | skipped (no code) | — |
| G4, `field`, 2000 maps | candidate 0.9153 (3649/24/327), control 0.7669 (3049/37/914), **Δ = +0.1484** (CI +0.1331..+0.1637) | pass |
| Upload recommendation, S ≥ 0.60 | 0.6670 | **met** |

- The screen (0.6663) and the evaluation (0.6670) agree; against `field` +13.7 and +14.8 pp. The games
  against v07 last 242 turns on average (the mirror 241).
- **Rating terms:** at the arena's logistic scale (1.97 in Bronze), S 0.667 head-to-head against v07 is about
  +1.4 rating points; against v06 the development blocks gave 0.79-0.81, about +2.6. The caveat stands: the
  local proxy over-rewarded v07's kiting, so these are a no-regression check and a mechanism check; the
  arena decides the version.

**Verdict:** P2 passed with the upload recommendation met (G0 on the repeats, G1, G2, G4; G3 skipped).
`current?KITE=2,KITE_PULL=100` is the local champion; the freeze as v08 and the upload under P1 (against
v07's baseline, mean 25.53) are the user's decisions. Before any paste: `./gradlew submit` and both
`procCheck` series.
