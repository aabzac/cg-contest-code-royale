# Same-site final approach: experiment 2026-10-31

Predeclared before match results. Starting revision: `2adaa509eb8e70f2b9d3e34b1d4b93f869e91374`, clean tree.
Referee, harness and frozen v03 remain at that revision; local Java 17 / Gradle 8.11.1.

## Hypothesis and scope

Use unused travel distance in the final movement turn toward the already selected empty site A.
Both the baseline BUILD and the candidate MOVE must reach A this turn, allowing construction on
the next turn. Among valid contact points, accept only a strict improvement in integer travel turns
to the same predicted next useful site B. Keep v03's construction priorities and training unchanged.

The predictor uses known site geometry and `Physics.queenStep`, including collision resolution and
rounding. It does not run a full game simulation or use hidden resources/opponent commands.
Conservative tower/mobile-unit guards restrict the optimization to quiet approaches. This is a
bounded movement prediction, not a universal guarantee against future opponent interventions.
Existing UNSTICK detours and immediate building actions are untouched.

- Candidate: `current?APPROACH=1`; default `APPROACH=0` preserves v03.
- Control: frozen `v03`, income 3, towers 8, one knight barracks, UNSTICK enabled.
- Primary metric: map score S, each map played on both seats, draw = half a point;
  95% CI = mean +/- 1.96 SD / sqrt(number of maps), using `tools/bench_pair.py`.
- Mechanism checks: unchanged target A and construction turn in fixtures/recorded-state diagnostics,
  strict predicted next-trip saving, ordinary behavior with the flag off, runtime and legal output.

## Fixed development budget and confirmation

1. Unit/mechanism checks and a 20-map smoke run on seeds 110000-110019.
2. Development: 500 maps, seeds 110000-110499, both seats, candidate vs v03 and a v03 mirror.
   At most three implementation iterations on this development pool; record each, including failures.
   Fixing a mechanical or safety error before an iteration is not evidence of strength.
3. Freeze one working candidate. If the development comparison is clearly negative (upper CI < 0.5),
   stop and record rejection; otherwise use one fresh P2 screen block, reserving it in the seed ledger
   before play. The next available block at start is k=2, seeds 204000-205999 (2000 maps).
4. Compare to the mirror to classify scope. Under P2, fewer than 10% of maps changed permits the
   targeted-fix track: S >= 0.500, boss delta >= -0.01 and a regression test. Otherwise use the strategy
   track: lower CI > 0.50 and boss delta >= -0.02. G0 (zero candidate errors/timeouts/slow turns/extra
   lines) applies throughout. More than 1% maps excluded for control/opponent failures invalidates a run.
5. If direct confirmation passes, test candidate and control against the repository `boss` on the first
   500 maps of that block. `old` is unavailable: G3 is skipped explicitly. Both available opponents use
   knights; results do not establish performance against archer/giant opponents or an arena rating gain.

In-process: 8 threads, 50 ms. Process opponent: 3 threads, 200 ms. No concurrent heavy runs.
No extension of the fixed sample based on an interim score. No final upload-evaluation block or live
upload is planned in this task. Preserve the candidate patch and new sources with hashes before runs.
Keep selected/default v03 pending a separate adoption decision.

## Commands and evidence

Raw reports: `replays/bench/exp-2026-10-31-approach/` (ignored). Compact comparisons and source inventory
live beside this file. Exact executed commands, iterations, checks and conclusions will be recorded
below after the fixed runs.

```powershell
./gradlew.bat bench "-Pbot1=current?APPROACH=1" -Pbot2=v03 -Pmaps=500 -Pthreads=8 -PseedBase=110000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-31-approach/dev-direct.json
./gradlew.bat bench -Pbot1=v03 -Pbot2=v03 -Pmaps=500 -Pthreads=8 -PseedBase=110000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-31-approach/dev-mirror.json
python -B -I tools/bench_pair.py direct replays/bench/exp-2026-10-31-approach/dev-direct.json --mirror replays/bench/exp-2026-10-31-approach/dev-mirror.json --out docs/data/exp-2026-10-31-approach/dev-direct.json
```

## Execution: one frozen implementation

Only iteration 1 was evaluated; no tuning followed the development result. `source-iteration1.json`
identifies the exact source bytes and the ignored reconstruction directory: start at the recorded
commit, apply `tracked.patch`, then restore the archived new source files. Frozen v03's Java source,
the referee and the harness were not edited. Every version's parameter snapshot now explicitly has
`APPROACH=0`; this satisfies the complete-snapshot check without changing old-version behavior.
The test file later gained one mine-upgrade timeline case; executable production sources stayed fixed.

On this Windows host quote the complete Gradle property argument, including `-Pout=...json`.
An initial unquoted smoke invocation failed during Gradle argument parsing (`Task '.json' not found`),
before any games. All actual runs used quoted properties and sequential execution. The standalone
diagnostic's first sandboxed Java/javac processes made no progress and were cancelled; the successful
compile/run used the same JDK outside that sandbox. No match result was discarded for these setup errors.

Additional executed commands (same quoting also applied to the development commands above):

```powershell
./gradlew.bat bench "-Pbot1=current?APPROACH=1" "-Pbot2=v03" "-Pmaps=20" "-Pthreads=8" "-PseedBase=110000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-10-31-approach/smoke.json"
javac -cp build/classes/java/main -d build/probe build/ApproachProbe.java
java -cp "build/classes/java/main;build/resources/main;build/probe" cr.bot.current.ApproachProbe 500 110000 docs/data/exp-2026-10-31-approach/mechanism.json
./gradlew.bat bench "-Pbot1=current?APPROACH=1" "-Pbot2=v03" "-Pmaps=2000" "-Pthreads=8" "-PseedBase=204000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-10-31-approach/direct.json"
./gradlew.bat bench "-Pbot1=v03" "-Pbot2=v03" "-Pmaps=2000" "-Pthreads=8" "-PseedBase=204000" "-PturnMs=50" "-Pout=replays/bench/exp-2026-10-31-approach/mirror.json"
python -B -I tools/bench_pair.py direct replays/bench/exp-2026-10-31-approach/direct.json --mirror replays/bench/exp-2026-10-31-approach/mirror.json --min-lower 0.5 --out docs/data/exp-2026-10-31-approach/g1.json
```

Bench stdout, including maximum turn time, is saved in the matching `.log` beside each raw JSON;
each command was piped to `Tee-Object -FilePath <log>`, followed by `exit $LASTEXITCODE`.
The diagnostic source is archived at `source-iteration1/build/ApproachProbe.java` under the raw directory.

## Direct comparison and mechanism

- Development: `dev-direct.json`, 500 maps / 1000 games, S = 0.5280, CI 0.4991–0.5569;
  495 wins / 66 draws / 439 losses. No dropped maps or candidate failures. Outcome-changed maps
  against the v03 mirror: 466/500 (93.2%). This chooses the strategy track, not the targeted-fix track.
- Fresh screen k=2 (reserved before play): `g1.json`, 2000 maps / 4000 games, S = 0.5245,
  CI 0.5111–0.5379; 1971 wins / 254 draws / 1775 losses. G0 and G1 pass. No dropped maps;
  no errors, timeouts, slow turns or extra output from either side. Candidate by seat W/D/L:
  1117/127/756 and 854/127/1019. Outcome-changed maps: 1872/2000 (93.6%).
- The result is small but positive against v03, with an existing seat asymmetry. These are point
  fractions with draws worth half, not a 52.45% win rate. The paired-map confidence interval, not
  an interval treating 4000 games as independent, is the declared test.
- Maximum candidate turn in direct screen: 38.2 ms at 8 threads, under the local 50 ms budget.
  Single-thread shadow diagnostic (`mechanism.json`) has subsequent-turn p95 0.003 ms,
  p99 0.093 ms, max 1.5085 ms; first-turn max 1.2526 ms. These include JVM/OS effects and
  exclude diagnostic copying/checking overhead. Percentiles cover all candidate decisions,
  mostly inactive APPROACH turns, not just planner activations; they do not certify platform latency.

The shadow diagnostic observes v03 mirror games and never executes the candidate's command.
On 242844 observations it changed 3046 queen commands in 960/1000 games (495/500 maps), and
changed no TRAIN command. Every change predicted exclusive contact with the same A this turn,
the same next B, and a strict integer walking-turn saving: 3508 predicted turns in total.
No input-state mutation, illegal command or diagnostic failure occurred. Predictions reuse the
planner's static geometry and are not independent proof of actual future savings. Actual Rules
timelines are covered by tests, including first construction and the fixture's mine upgrades 1→2→3.

Even with an unchanged first construction time, later combat, resource depletion, newly revealed
sites or changes during tower/mine upgrades can alter the next actual target. The strength series
therefore matters: improving one geometric leg does not guarantee every game's outcome improves.
The forecast walks straight toward B; it does not rerun MainBot on intermediate positions. Even in
a static quiet map, opportunistic construction at a touched site or ordinary per-turn target reselection
can change the actual next leg. The shadow's saved-turn count is a prediction, not a realized-game total.

## Boss, packaging and final decision

```powershell
./gradlew.bat bench "-Pbot1=current?APPROACH=1" "-Pbot2=boss" "-Pmaps=500" "-Pthreads=3" "-PseedBase=204000" "-PturnMs=200" "-Pout=replays/bench/exp-2026-10-31-approach/boss-cand.json"
./gradlew.bat bench "-Pbot1=v03" "-Pbot2=boss" "-Pmaps=500" "-Pthreads=3" "-PseedBase=204000" "-PturnMs=200" "-Pout=replays/bench/exp-2026-10-31-approach/boss-ctrl.json"
python -B -I tools/bench_pair.py common replays/bench/exp-2026-10-31-approach/boss-cand.json replays/bench/exp-2026-10-31-approach/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-10-31-approach/g2.json
./gradlew.bat test --tests cr.bot.current.ApproachPlannerTest --tests cr.bot.current.MainBotTest --tests cr.bot.BotFactoryTest --tests cr.submit.FileMergerTest
./gradlew.bat procCheck "-Pparams=APPROACH=1" "-Popp=v03" "-Pgames=20" "-PseedBase=130000"
./gradlew.bat run "-Pbot0=current?APPROACH=1" "-Pbot1=v03" "-Pseed=110002" "-Pout=build/replays/local/approach-110002.json"
./gradlew.bat run "-Pbot0=v03" "-Pbot1=v03" "-Pseed=110002" "-Pout=build/replays/local/approach-control-110002.json"
```

`g2.json`: 500 maps / 1000 games per arm, no dropped maps. Candidate 984/0/16 W/D/L,
control 985/0/15. Delta −0.0010, paired-map CI −0.0096..0.0076. G2 passes the predeclared
−0.02 regression guard. Both sides have zero errors, timeouts, slow turns or extra lines.
Maximum candidate turn is 29.0 ms, control 34.3 ms; process-opponent maximum 65.6 / 56.6 ms
under the declared 200 ms budget. This opponent is the repository's Default AI, not Princess Boss.

`checks.json`: 33 tests passed (7 approach, 6 MainBot, 5 factory, 15 merger). The added mine
fixture reaches rates 1/2/3 at turns 2/3/4 in both arms and builds the same next tower at 8 rather
than 9. The barracks fixture builds A at 2 in both and B at 6 rather than 7. Existing default-v03
equivalence and parameter snapshot checks pass. The test XML and final test source are archived
beside the raw reports; the original six-test source remains in the iteration-1 archive.

The exact merged candidate was checked in 40 games (20 development maps, both seats):
40/40 command-equivalent to in-process `current?APPROACH=1`, no timeout, kill, unexpected stderr,
extra or late line. Maximum first answer 205.3 ms, subsequent answer 6.6 ms. Artifact identity and
kept source: `docs/BOTS.md`, Artifacts; machine-readable details: `checks.json`. This was a
packaging smoke check, not the full pre-upload regimen. The original selected-v03 `build/submit`
directory was saved before it and restored afterwards, including its compiled classes.

Two example replays are available in `build/replays/local/` and archived with hashes in
`replay-examples.json`. On this development example, both arms build mine #2 at frame 3;
candidate reaches/builds #20 sooner. The full-game building times (9 vs 22) also reflect subsequent
unit collisions and divergent play, not a thirteen-turn geometric forecast. This is an illustration,
not another strength sample. Open either file through the existing local visualizer, starting at frame 2.

**Decision:** the single frozen candidate passes the available P2 strategy screen G0–G2; G3 is
explicitly skipped because `refs/old-3315948/Player.java` is absent. Retain the experimental flag,
default 0. Adoption/freeze as the new local champion is deferred to a separate decision: this task
was predeclared as implementation and comparison, so the usual P2 default-switch step is not applied
here. v03 remains the selected/default version and last adopted frozen champion.

No fresh final-evaluation block, full pre-upload process checks or arena upload was run. The observed
screen score is below the policy's 0.60 upload-recommendation threshold; it does not establish that
a final evaluation would pass. The two available opponent types both use knights, so the small local
gain does not establish strength against archers/giants or an arena-rating improvement.
