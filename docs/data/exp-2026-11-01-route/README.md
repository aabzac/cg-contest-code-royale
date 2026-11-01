# ROUTE=1, a walk planner for the queen: experiment 2026-11-01

Steps 0 and 1 of the queen-route plan (`docs/data/study-2026-11-01-queen-routes/README.md`):
the LegScan tool with its baselines and the second calibration point, then the `ROUTE=1` planner
screened under P2 (`docs/local-screen.md`, strategy track). Starting revision: `8fbe079`, clean
worktree `claude/sweet-bartik-d9ce19`; frozen v03, the referee and the harness are unchanged.
Local Java 17.0.12, Gradle 8.11.1, Windows 11, 8 bench threads.

## Hypothesis and scope (written before the screen)

A walk planner that predicts the straight BUILD walk with the sites only and, when the walk would
slide along a site, stall, or land badly for the next target, searches a fan of 24 headings with
1-3 MOVEs plus the landing point of the next target, saves walking turns (the study's pool: slides
3-4 turns per game, the landing point about 1 turn over APPROACH's last step) and so reaches the
rule ladder earlier. At the study's exchange rate of about 0.01 S per saved walking turn the
expected direct score is S 0.53-0.58 against v03. The target rule, the build priorities and the
training are unchanged: this is movement only.

- Candidate: `current?ROUTE=1` (UNSTICK and APPROACH are replaced by the planner when it is on).
  Default `ROUTE=0`: `current` plays as v03 command for command (`MainBotTest.withDefaultsCurrentPlaysAsV03`).
- Control: frozen `v03` (income 3, 8 towers, one knight barracks, UNSTICK on).
- Metric: S, the map score over both seats (draw = 1/2), 95 % CI over maps (`tools/bench_pair.py`).
- Gates (strategy track, the approach experiment changed 93.6 % of maps): G0 no candidate failure
  of any kind; G1 lower CI bound of S above 0.50 on the 2000 maps of a fresh screen block;
  G2 Δ against `boss` on the block's first 500 maps ≥ -2 pp; G3 skipped (`refs/old-3315948/Player.java`
  is absent). Both local opponents play knights only.
- Second metric: LegScan on the same block for both arms (walking turns, excess, slides, abandoned
  legs by reason, landing potential, ladder completion, play() time, queenStep calls per turn).
- Development: seeds 10000-10499 (500 maps), at most three recorded iterations, the control's mirror
  on the same maps for the changed-map count; the stall fixtures, a slide fixture from seed 110135
  turn 144 and the APPROACH landing case as tests.
- Screen block: the ledger's next block was k=3 (206000-207999), but the unmerged branch
  `codex/queen-routing` had already played its own ROUTE candidate on it (`docs/data/exp-2026-10-31-route`
  there; raw reports `replays/bench/exp-2026-10-31-route` in the main checkout). That block is
  recorded as used and this experiment reserved **k=4, seeds 208000-209999** before any game on it.
- Process opponent runs: 3 threads, 200 ms. In-process: 8 threads, 50 ms. Sequential, nothing else
  running. No extension of a series on its interim score; no upload, no adoption decision here.

Raw reports: `replays/bench/exp-2026-11-01-route/` (gitignored; copied to the main checkout's
`replays/bench/` at the end of the session). Bench stdout is in the `.log` beside each report; the
iteration sources with their sha256 in `source-it<N>/`; LegScan summaries and per-game CSVs in
`legscan/<series>/`. Compact summaries (`bench_pair.py --out`, LegScan summaries) live beside this file.

## Step 0: LegScan and its baselines

`cr.tools.LegScan` (`./gradlew legScan -Pbot=<spec> -Popp=<spec> -Pmaps=N -PseedBase=S [-Pthreads=8] [-Pout=<dir>]`)
plays every map on both seats (a mirror once per map) and reports per queen-game: turn classes,
walking turns, route legs (arrived at a free site, no flee) with their excess over the straight
bound floor((d - (r+35)) / 60) + 1, mid-leg slide turns (displacement below 55 not touching the
target), stall turns, failed final steps (ring within 60 before the turn, not touching after) with a
unit near, abandoned legs to free sites by reason (flee, own-tower top-up, retarget, a build on a
site touched on the way, other), builds on free sites touched on the way, the landing potential
(last step and whole walk: turns to the next route target saved by the best of 72 sampled landings,
sites only), the ladder completion turn, the bot's longest play() after turn 0 and its queenStep
calls per turn (bots that count them: `current`; frozen versions report "not counted").
A leg is a maximal run of walking turns with one target; MOVE turns take the target of the BUILD
that follows them, or of the site they arrive at. Test: `LegScanTest` pins seeds 110135 (the study's
worst leg: 12 turns, bound 6, 7 slide turns) and 110098 (16 turns, bound 3, 13 failed final steps
with a unit near). The tool's revision: `legscan/revision-step0.txt` in the raw directory.

Baselines (development seeds 110000-110299, 300 maps, both seats):

| Series | Walking | Route legs | Excess | Slides | Final fails | Abandoned (flee / top-up / retarget / touched site / other) | Landing potential (last / full) | Ladder complete, mean turn | Max play() |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v03 mirror | 92.18 | 12.42 | 3.77 | 4.86 | 0.77 | 9.57 (2.03 / 4.66 / 1.86 / 0.30 / 0.73) | 3.66 / 4.61 | 74.3 %, 156.4 | 1.9 ms |
| v03 vs v01 | 79.22 | 11.46 | 2.64 | 3.36 | 0.80 | 9.34 (3.83 / 3.20 / 1.63 / 0.25 / 0.43) | 3.09 / 3.88 | 61.7 %, 150.8 | 7.6 ms |
| current mirror (= v03, counts calls) | 92.18 | 12.42 | 3.77 | 4.86 | 0.77 | as the v03 mirror | 3.66 / 4.61 | 74.3 %, 156.4 | 39.0 ms (8 threads; mean 0.5 calls per turn, max 1225) |

The numbers reproduce the study's (walking 92.2, excess 5.0 there with a leg definition that spans
retargets; here legs end at a retarget, so the excess is split between the leg classes). The
39 ms maximum of the `current` mirror against 1.9 ms of the same code as `v03` shows that the
maximum play() time at 8 threads is scheduling noise, not the bot's work.

Second calibration point, screen block 2 (seeds 204000-205999, 2000 maps), where `current?APPROACH=1`
scored S 0.5245 (CI 0.5111-0.5379) against v03 (`docs/data/exp-2026-10-31-approach`):

| Series | Walking | Excess | Slides | Final fails | Abandoned (top-up / touched site) | Landing potential (last / full) | Ladder complete, mean turn | Calls per turn (mean / max) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v03 mirror | 92.93 | 3.60 | 4.61 | 0.80 | 9.57 (4.63 / 0.29) | 3.82 / 4.78 | 68.5 %, 160.5 | not counted |
| current?APPROACH=1 vs v03 | 90.88 | 3.16 | 4.33 | 0.75 | 9.46 (4.56 / 0.30) | 1.92 / 2.80 | 73.1 %, 157.5 | 6.3 / 2563 |

APPROACH saved 2.05 walking turns per game for +0.0245 S: 0.012 S per saved walking turn, against
the study's 0.01 from seeds 10000-10199 (2.5 turns saved). The landing potential of the last step
fell from 3.82 to 1.92: APPROACH realizes half of it (its guards near units and the strict
same-next-target rule keep the rest). The builds on free sites touched on the way did not change
on this block (0.29 against 0.30), unlike the study's shadow count.

## Step 1: ROUTE=1

`cr.bot.current.RoutePlanner`, called by `MainBot` instead of UNSTICK and APPROACH for the BUILD
towards the next site to take (the target rule is untouched):

1. Predict the straight BUILD walk with the sites only (`Physics.queenStep`, 5 passes, 25-turn
   horizon). It is clean when it takes no more turns than the straight bound (no slide), never
   stands still, crosses no enemy tower's range (`safeSegment`), ends no turn on a free site the
   touch rule would build at, and lands touching A alone (`touchesOnly`). Own towers and mines are
   not avoided: a touch tops them up or upgrades them as in v03 (step 4 of the plan decides that policy).
2. The next target B is `ApproachPlanner.nextTarget` at the straight landing, computed only when
   the step is quiet (APPROACH's unit guards) and the enemy queen cannot reach A within two turns.
   A clean walk whose landing is at most one turn from B stands: the BUILD is returned.
3. Otherwise the search: the straight start and every prefix of 1-3 MOVEs on 24 headings (15-degree
   steps either side of the straight line) are followed by the straight walk and by the landing
   walk: the ring point of A at r+33 nearest to B, clamped to the arc reachable in the same number
   of turns, walked to with MOVEs (the last one lands exactly on the point, no push) and verified
   with the step prediction. A turn ending on a free wanted site costs one turn more; a stall, an
   enemy tower's range, a landing touching another site or a landing from which the bot's next
   target would not be B disqualify the plan.
4. Plans are compared by turns to touch A, then turns from the landing to B (straight walk, sites
   only), then the previous turn's heading (hysteresis), then the fixed search order. The search
   spends at most 3000 step predictions and stops with the best complete plan found so far: no
   clock, no Random, so a position gives the same command every time (`RoutePlannerTest.plansAreDeterministic`,
   `theSearchIgnoresTheClock`).
5. The first command of the best plan is returned: the BUILD (straight), a MOVE 200 ahead on the
   heading, or the MOVE to the landing point.

`CGRunner.warmUp` gained two states (a walk blocked by a site, a clean walk with a next site to
land for), so the planner's code is loaded and compiled in the 1000 ms first turn (`CGRunnerTest`).
Tests (`RoutePlannerTest`): the two stall fixtures reached in no more turns than UNSTICK (7 against
7 head-on, 9 against 10 in the wedge), the slide fixture of seed 110135 turn 144 (v03 10 turns in
the sites-only replay, ROUTE at most 7), a clean walk kept as a BUILD, the APPROACH landing case
under the real rules (A built on turn 2 in both arms, B on turn 6 instead of 7), the flee rule
unchanged with a knight near, determinism and the clock independence; `MainBotTest` keeps the
default-off equivalence with v03 (ROUTE=0 recorded in every version snapshot).

### Development iterations (seeds 10000-10499, 500 maps; the v03 mirror on the same maps is `dev-mirror.json`)

| Iteration | Change | S vs v03 (95 % CI), W/D/L | Changed maps | Walking | Excess | Slides | Top-ups | Ladder complete, mean turn | Max play(), calls per turn (mean / max) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v03 mirror (reference) | — | 0.5 | — | 91.56 | 3.55 | 4.53 | 4.41 | 67.6 %, 161.3 | 16.7 ms, not counted |
| 1 (`source-it1/`) | as designed, plus: own towers below 700 HP and own mines below their maximum count as diverting sites (a turn ending on them costs one turn), so the planner routes around them; no slide trigger | 0.5315 (0.4994-0.5636), 507/49/444 | 498 of 500 | 89.20 | 3.37 | 2.72 | 1.08 | 70.4 %, 148.6 | 19.8 ms, 44.8 / 1265 |
| 2 (`source-it2/`, the screened code) | only free wanted sites divert (own towers and mines are walked past as in v03, their touch rule unchanged); the straight walk also counts as not clean when it takes more turns than the straight bound (the slide trigger of the design, which iteration 1 lacked: slides along own barracks, enemy structures and full towers were never searched around) | 0.5825 (0.5502-0.6148), 558/49/393 | 498 of 500 | 85.97 | 1.40 | 1.85 | 2.91 | 72.5 %, 149.5 | 39.0 ms, 41.3 / 1489 |

Iteration 1 had one slow turn (157 ms, seed 10167) and the v03 mirror two (214 ms): both series ran
while two single games were played beside them for the traces, so the maxima are host noise; the
screen ran alone. Iteration 2 had no failure. The third allowed iteration was not used: the second
is at the top of the expected range, and the screen decides. Summaries: `dev-direct-it1.json`,
`dev-direct-it2.json`; LegScan summaries `legscan/dev-10000-*`.

## Screen (block k=4, seeds 208000-209999)

Commands, recorded before the runs:

```powershell
./gradlew.bat bench "-Pbot1=current?ROUTE=1" "-Pbot2=v03" "-Pmaps=2000" "-Pthreads=8" "-PseedBase=208000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-11-01-route/direct.json"
./gradlew.bat bench "-Pbot1=v03" "-Pbot2=v03" "-Pmaps=2000" "-Pthreads=8" "-PseedBase=208000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-11-01-route/mirror.json"
python -B -I tools/bench_pair.py direct replays/bench/exp-2026-11-01-route/direct.json --mirror replays/bench/exp-2026-11-01-route/mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-01-route/g1.json
./gradlew.bat bench "-Pbot1=current?ROUTE=1" "-Pbot2=boss" "-Pmaps=500" "-Pthreads=3" "-PseedBase=208000" "-PturnMs=200" "-Pout=replays/bench/exp-2026-11-01-route/boss-cand.json"
./gradlew.bat bench "-Pbot1=v03" "-Pbot2=boss" "-Pmaps=500" "-Pthreads=3" "-PseedBase=208000" "-PturnMs=200" "-Pout=replays/bench/exp-2026-11-01-route/boss-ctrl.json"
python -B -I tools/bench_pair.py common replays/bench/exp-2026-11-01-route/boss-cand.json replays/bench/exp-2026-11-01-route/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-01-route/g2.json
./gradlew.bat legScan "-Pbot=current?ROUTE=1" "-Popp=v03" "-Pmaps=2000" "-PseedBase=208000" "-Pthreads=8" "-Pout=replays/bench/exp-2026-11-01-route/legscan/block4-208000"
./gradlew.bat legScan "-Pbot=v03" "-Popp=v03" "-Pmaps=2000" "-PseedBase=208000" "-Pthreads=8" "-Pout=replays/bench/exp-2026-11-01-route/legscan/block4-208000"
```

The `boss` spec needs `refs/referee-csj/config/level4/Boss.java`; the worktree holds a byte-identical
copy of the main checkout's file (sha256 `a1a3f043…`, printed in the reports).

## Results

### G1, direct (2000 maps, 4000 games)

`g1.json`: S = 0.5813 (95 % CI 0.5657-0.5968, SD 0.356), 2203/244/1553 W/D/L, no map dropped; the
candidate has no error, timeout, slow turn or extra line (bench maximum 26.0 ms). By seat:
seat 0 1172/120/708, seat 1 1031/124/845 (v03's seat asymmetry stays: its
mirror has 1033 wins on seat 0 against 794 on seat 1). 1990 of 2000 maps differ from the v03 mirror:
the strategy track. **G0 and G1 pass.**

### G2, `boss` (first 500 maps, 1000 games per arm)

`g2.json`: candidate 982/0/18, v03 983/0/17 W/D/L; Δ = -0.0010 (paired-map 95 % CI -0.0100..+0.0080),
no map dropped, no failure on either side. **G2 passes** (Δ ≥ -0.02). The Default AI is a
floor, not Princess Boss. G3 is skipped: `refs/old-3315948/Player.java` is absent.

### LegScan on the block (2000 maps, 4000 queen-games each)

| Series | Walking (MOVE / BUILD) | Route legs | Excess | Slides | Final fails (unit) | Abandoned (flee / top-up / retarget / touched site / other) | Free-site builds on the way | Landing potential (last / full) | Ladder complete, mean turn | Max play(), calls per turn |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v03 mirror | 92.75 (1.19 / 91.56) | 12.44 | 3.63 | 4.65 | 0.76 (0.73) | 9.58 (2.16 / 4.58 / 1.77 / 0.3 / 0.77) | 0.24 | 3.81 / 4.74 | 69.9 %, 162.8 | 13.99 ms, not counted |
| current?ROUTE=1 vs v03 | 86.86 (19.73 / 67.13) | 12.72 | 1.47 | 1.97 | 0.68 (0.67) | 7.17 (1.82 / 2.96 / 1.15 / 0.06 / 1.18) | 0.03 | 1.85 / 2.2 | 73.3 %, 151.6 | 53.15 ms, mean 41.7, max 1703 |

ROUTE walks 5.89 turns less per game than v03's mirror and completes the ladder
11.2 turns earlier; the excess over the straight bound falls from 3.63 to
1.47 and the slide turns from 4.65 to 1.97. Against the two calibration points
(APPROACH: 2.05 turns for +0.0245 S on block 2, 2.5 turns for +0.041 S on seeds 10000-10199)
this is 0.0138 S per saved walking turn: the exchange rate holds at about 0.01-0.02 per turn,
and the realized saving is larger than the study's 3-4 turn slide pool because the planner also
removes most abandoned legs (own-tower slides that ended in a top-up, 4.58 → 2.96 per game).
The builds on free sites touched on the way did not increase (0.24 → 0.03), the risk the
study named. The landing potential of the last step falls from 3.81 to 1.85: part of it
stays behind the unit guards and the same-next-target rule.

Time: the planner spends 41.7 step predictions per turn on average, at most 1703 (the cap is 3000),
so its work is well under a millisecond. The 53.15 ms maximum of the LegScan replay is one turn of
one game at 8 threads; the bench series of the same games, the evidence for G0, had at most
26.0 ms, and the v03 baselines show the same spread (1.9 against 39.0 ms for identical code). The
turn time on CG's single core is only shown by `procCheck` and a placement.

### Verdict

`current?ROUTE=1` passes the P2 strategy screen: G0, G1 (S 0.5813, lower bound 0.5657 > 0.50) and
G2 (Δ -0.0010); G3 skipped while `old` is absent. The flag stays off by default; adoption as
the local champion (the P2 default-switch step), the final-evaluation block and any upload are
decisions for the user. The screen is one block with one arm (no replication needed under P2).
What the screen does not show: strength against archers and giants (both local opponents play
knights only), the turn time on CG's single slow core (`procCheck` and a placement would), and
whether the lost own-tower top-ups cost anything on the arena.

### Final checks

Run after the screen, in this session, on the screened sources plus the two fixes below
(log `replays/bench/exp-2026-11-01-route/final-checks.log`, copied with the whole directory to
the main checkout):

| Check | Outcome |
| --- | --- |
| `./gradlew test` | 127 tests, 0 failures, 2 skipped (`CgAttributeTest`, `CgViewReplayTest`: arena replays are absent in the worktree), 35 s |
| `./gradlew submit -Pparams=ROUTE=1` | 90 050 of 100 000 characters, 13 files, build id 5c7e3bf0, config id 347b908d, sha256 da914c4c6e0d…; no upload |
| `./gradlew procCheck -Pparams=ROUTE=1 -Popp=v03 -Pgames=20 -PseedBase=150000` | 40 games, sameAsCurrent 40/40, no timeouts, extra, late or stderr lines; maxFirst 293.8 ms, maxLater 3.0 ms |

This is a smoke check of the packaging, not the pre-upload pair of 100-game `procCheck` runs
against `v01` and `boss` that `.ai/project.md` requires before a paste.

Two defects around `Params.ROUTE` surfaced only here, after the screen; neither touches the
planner or the screened games:

- `LocalGamesTest` expected the local-match server's two-flag catalog and a 400 on a `ROUTE`
  launch request. The server advertises every flag whose parameter exists, so with `ROUTE`
  present it launched the test's throwing stub, the exchange was never answered, and the first
  run waited two hours on that request (`final-checks-attempt1-hung.log`; found with `jstack` on
  the test worker). The test now expects three flags (`route0`/`route1` config ids, eight
  combinations, the `current [UNSTICK=1, ROUTE=1]` label) and times every request out after
  60 s. `.ai/project.md` (local-match row) and `docs/visualizer.md` list the flag.
- `Bot.java`'s javadoc `{@link cr.core.Physics#queenStep}` violated the merger's ban on
  qualified project names, failing `FileMergerTest` and `submit`
  (`final-checks-attempt2-merger.log`); it is plain text now.

## Final evaluation (block k=2, seeds 404000-405999)

Written before the runs. The user asked for the P2 final evaluation on 2026-11-01, after the
screen and the final checks. Candidate `current?ROUTE=1` with the bot sources exactly as screened
(`source-it2/`; `revision-eval.txt` records the revision and the dirty state at the start of the
runs). Control: the selected version v03, which was also the screen's control. Gates as in the
screen: G0 no candidate failure in any series; G1 lower 95 % bound of S above 0.50 on the block's
2000 maps; G2 Δ against `boss` on the first 500 maps ≥ -2 pp; G3 skipped (`old` absent). The
upload recommendation of P2 needs S ≥ 0.60; the decision is the user's. Expectation from the
screen: S near 0.58 (screen CI 0.5657-0.5968), so the recommendation threshold is more likely
missed than met, and this block is played once whatever comes out. LegScan on both arms repeats
the walking numbers on fresh maps. The in-process series run alone on the machine (8 threads,
50 ms), the `boss` series with 3 threads and 200 ms.

```powershell
./gradlew.bat bench "-Pbot1=current?ROUTE=1" "-Pbot2=v03" "-Pmaps=2000" "-Pthreads=8" "-PseedBase=404000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-11-01-route/eval-direct.json"
./gradlew.bat bench "-Pbot1=v03" "-Pbot2=v03" "-Pmaps=2000" "-Pthreads=8" "-PseedBase=404000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-11-01-route/eval-mirror.json"
python -B -I tools/bench_pair.py direct replays/bench/exp-2026-11-01-route/eval-direct.json --mirror replays/bench/exp-2026-11-01-route/eval-mirror.json --min-lower 0.5 --out docs/data/exp-2026-11-01-route/eval-g1.json
./gradlew.bat bench "-Pbot1=current?ROUTE=1" "-Pbot2=boss" "-Pmaps=500" "-Pthreads=3" "-PseedBase=404000" "-PturnMs=200" "-Pout=replays/bench/exp-2026-11-01-route/eval-boss-cand.json"
./gradlew.bat bench "-Pbot1=v03" "-Pbot2=boss" "-Pmaps=500" "-Pthreads=3" "-PseedBase=404000" "-PturnMs=200" "-Pout=replays/bench/exp-2026-11-01-route/eval-boss-ctrl.json"
python -B -I tools/bench_pair.py common replays/bench/exp-2026-11-01-route/eval-boss-cand.json replays/bench/exp-2026-11-01-route/eval-boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-11-01-route/eval-g2.json
./gradlew.bat legScan "-Pbot=current?ROUTE=1" "-Popp=v03" "-Pmaps=2000" "-PseedBase=404000" "-Pthreads=8" "-Pout=replays/bench/exp-2026-11-01-route/legscan/eval-404000"
./gradlew.bat legScan "-Pbot=v03" "-Popp=v03" "-Pmaps=2000" "-PseedBase=404000" "-Pthreads=8" "-Pout=replays/bench/exp-2026-11-01-route/legscan/eval-404000"
```

Raw reports and logs: `replays/bench/exp-2026-11-01-route/eval-*.json|log`, `eval-run.log`,
`legscan/eval-404000/`; summaries `eval-g1.json`, `eval-g2.json` and the LegScan summaries in
`legscan/` beside this file.

### Results (runs 21:20-21:28 UTC, `eval-run.log`)

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line in any series; time per move max 35.4 ms against v03 (8 threads) and 57.9 ms against `boss` (3 threads; v03's own arm 58.3 ms, so the external process's host noise, not the planner) | pass |
| G1, direct against v03, 2000 maps | S = 0.5856 (95 % CI 0.5701-0.6011, SD 0.353); W/D/L 2216/253/1531; by seat 1184/123/693 and 1032/130/838; 1987 of 2000 maps differ from the v03 mirror (1805/390/1805, seat 0 won 1015) | pass |
| G2, `boss`, 500 maps | candidate 0.9900 (990/0/10), control 0.9875 (987/1/12), Δ = +0.0025 (95 % CI -0.0065..+0.0115) | pass |
| G3, `old` | skipped (`refs/old-3315948/Player.java` absent) | — |
| Upload recommendation, S ≥ 0.60 | 0.5856: the point estimate is below the line, the interval's upper bound (0.6011) reaches it | not met |

The screen's S 0.5813 is replicated within its interval. Rating terms: at the arena's logistic
scale S = 0.586 against v03 is about +0.46 points in a head-to-head, not the field (v03 stands
about 4.7 below the Bronze boss). Under P2 the user decides: the candidate passes the gates
against the selected version and stays below the recommendation line, as v02 did (0.5032, uploaded
anyway). An upload would follow `submit`, both 100-game `procCheck` runs and P1.

LegScan on the block (2000 maps, 4000 queen-games per arm; summaries
`legscan/eval-404000-*-summary.txt` beside this file, per-game CSVs in the raw directory):

| Per queen-game | v03 mirror | `current?ROUTE=1` vs v03 |
| --- | --- | --- |
| walking turns | 92.85 | 86.71 |
| excess over the straight bound | 3.72 | 1.50 |
| mid-leg slide turns | 4.76 | 2.07 |
| failed final steps | 0.73 | 0.67 |
| abandoned legs (turns) | 9.62 (27.07) | 7.24 (20.04) |
| of them own-tower top-ups | 4.67 | 3.01 |
| builds on a free site touched on the way | 0.23 | 0.03 |
| landing potential, last step / whole walk | 3.82 / 4.73 | 1.92 / 2.22 |
| ladder complete | 69.1 % at turn 160.9 | 73.8 % at turn 150.8 |
| queenStep calls per turn | not counted | mean 42.2, max 1614 (cap 3000) |
| max play() after turn 0 (8 threads) | 25.53 ms | 35.56 ms |

The walking numbers repeat the screen's (walking 86.86 → 86.71, excess 1.47 → 1.50, top-ups
2.96 → 3.01). Exchange rate on this block: 6.14 saved walking turns for +0.0856 S, 0.0139 S per
turn (screen 0.0138).

Raw report sha256 (first 12 hex digits): `eval-direct.json` d084dc9bcb01, `eval-mirror.json`
562cf050f10c, `eval-boss-cand.json` 19b4d120e31e, `eval-boss-ctrl.json` 635f912f197b. Revision
and dirty state at the start: `revision-eval.txt` (commit 8fbe079, bot sources byte-identical to
`source-it2/`). The block is used once; the ledger's next evaluation block is k=3.
