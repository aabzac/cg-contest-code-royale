# Experiment 2026-10-30: UNSTICK (queen stalls), P2 targeted-fix screen

Written before the screen's results were seen.

- **Hypothesis**: with `UNSTICK=1` the queen no longer stands still when the BUILD towards the next site
  to take is blocked (a site straight ahead, or two sites wedging her). Removing these stalls does not
  cost games.
- **Track**: P2 targeted fix (`docs/local-screen.md`). The gates:
  - G0;
  - S ≥ 0.500 against the control;
  - Δ ≥ −1 pp against `boss`;
  - fewer than 10 % of the maps changed from the control's mirror;
  - the regression tests `MainBotTest` and `StallScanTest`.
- **Mechanism metric** (context, not a gate): stall streaks of the candidate against v01 on the same block
  (`stallScan`).
- **Arms**:
  - candidate `current?UNSTICK=1`;
  - control `v01`, the local champion;
  - opponents: the control (direct) and `boss`.
- **Pool**: screen block 0, seeds 200000-201999. Direct series use all 2000 maps; `boss` series use
  200000-200499.
- **Code**: commit e53a53b with the uncommitted phase-3 changes. Each report records the status, the diff's
  sha256 and the untracked files' sha256.
- **Development before the screen**: seeds 10000-10499 only (`replays/bench/dev-unstick-2026-10-30`). The
  variants tried are listed in `docs/DEVLOG.md` (2026-10-30). The screened arm is the last one:
  - MIN_STEP 2;
  - only the BUILD towards the next site to take;
  - plans of up to 6 detour moves within 25 turns;
  - a step that ends touching the target counts as progress.

## Commands

```
./gradlew bench "-Pbot1=current?UNSTICK=1" -Pbot2=v01 -Pmaps=2000 -Pthreads=8 -PseedBase=200000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-30-unstick/direct.json
./gradlew bench -Pbot1=v01 -Pbot2=v01 -Pmaps=2000 -Pthreads=8 -PseedBase=200000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-30-unstick/mirror.json
./gradlew bench "-Pbot1=current?UNSTICK=1" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=200000 -PturnMs=200 -Pout=replays/bench/exp-2026-10-30-unstick/boss-cand.json
./gradlew bench -Pbot1=v01 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=200000 -PturnMs=200 -Pout=replays/bench/exp-2026-10-30-unstick/boss-ctrl.json
./gradlew stallScan -Pbot=current -Popp=v01 -Pmaps=2000 -PseedBase=200000
./gradlew stallScan "-Pbot=current?UNSTICK=1" -Popp=v01 -Pmaps=2000 -PseedBase=200000
python -I tools/bench_pair.py direct replays/bench/exp-2026-10-30-unstick/direct.json --mirror replays/bench/exp-2026-10-30-unstick/mirror.json --min-score 0.5 --max-changed 0.1 --out docs/data/exp-2026-10-30-unstick/direct.json
python -I tools/bench_pair.py common replays/bench/exp-2026-10-30-unstick/boss-cand.json replays/bench/exp-2026-10-30-unstick/boss-ctrl.json --min-delta -0.01 --out docs/data/exp-2026-10-30-unstick/boss.json
```

The raw reports and the run log (`run.log`, with the stall scans) are in
`replays/bench/exp-2026-10-30-unstick/` (gitignored). Their sha256 are in the summaries; `run.log` is
`0ca5efcf…`.

## Results

All four reports carry commit e53a53b with the same tracked diff (sha256 `d5bac528…`). Summaries:
`direct.json` and `boss.json` in this folder.

| Series | Result | Gate |
| --- | --- | --- |
| Direct: candidate against v01, 2000 maps | S = 0.5046 (95 % CI 0.5018-0.5075, SD 0.065); W/D/L 2002/33/1965; by seat 1291/16/693 and 711/17/1272 | S ≥ 0.500: pass |
| Changed from the v01 mirror | 146 of 2000 maps (7.3 %) | < 10 %: pass, so a targeted fix |
| `boss`, 500 maps | candidate 0.9190, control 0.9190, Δ = 0.0000 (CI −0.0028..0.0028); 8 maps changed | Δ ≥ −1 pp: pass |
| G0 | no error, timeout, slow turn or extra line; longest turn 5.4 ms against v01, 8.8 ms against `boss` | pass |
| Dropped maps | none | — |

Mechanism, from `stallScan` on the 2000 maps (4000 games):

| | stall turns | streaks | 1 | 2-4 | 5-9 | 10-19 | 20-39 | 40+ | games with 10+ |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| current (= v01) | 739 | 144 | 57 | 34 | 27 | 20 | 6 | 0 | 14 (5 not won) |
| current?UNSTICK=1 | 53 | 49 | 45 | 4 | 0 | 0 | 0 | 0 | 0 |

- v01's longest streak on the block was 25 turns: seed 201022, seat 0, turns 51-75, on `BUILD 14 MINE`.
- The candidate's remaining streaks last 1-2 turns. They are BUILDs towards towers (fleeing, or idling at
  home, which UNSTICK leaves alone), plus a few single turns on a MINE target.

Context only, not this track's gate: the lower bound 0.5018 also clears G1 of a strategy candidate.

**Verdict**: pass (P2, targeted fix). Under P2 the fix joins the local champion; how it joins (UNSTICK=1
as the `current` default, a frozen v02) is for the user to decide.

After the screen, only a comment of `MainBot` changed (`MIN_STEP`). Rerunning the direct series on seeds
200000-200199 gave all 400 games identical to the screened report.
