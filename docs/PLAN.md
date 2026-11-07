# Plan

## Phase 0 — orient
- [x] Statements (live Wood 3, referee levels 1-4), referee source, leaderboard snapshot
- [x] Legend replay corpus (88) + own arena games (10)

## Phase 1 — infrastructure
- [x] Gradle/Java 17 skeleton, `cr.core` port of the referee
- [x] Map generator 1:1 (validated on 98 replays)
- [x] `CgReplay`/`cgValidate` with HUD, site tooltip, unit and summary-warning checks, resync mode
- [x] Bot interface, Params, v00, greedy `current`, CGRunner, FileMerger (`submit`)
- [x] In-process Referee, LocalRunner (`run`), BenchmarkRunner (`bench`), ProcessCheck (`procCheck`)
- [x] Capture live Bronze statement; reconcile constants
- [x] Spawn position rule = replays
- [x] Tower damage formula = replays (distance from the site centre)
- [x] Second corpus `replays/cg2` (138 games, Bronze/Silver/Gold, archers, giants, build conflicts)
- [x] cgValidate strict on 236 replays: every turn matches; only the tie-break differs (2 games)
- [ ] Tie-break at equal scores: collect more tie games
- [x] Freeze v01 (greedy) with parameter snapshot; repo level-4 boss as a process opponent (`ProcessBot`)
- [x] Visualizer for local games (`run -Pout`, `serve`, `visualizer/index.html`)
- [x] Arena dashboard and recorded CG replay playback, with filters and navigation through a selection (`docs/visualizer.md`)
- [x] Launch local games with selectable bot versions; pair-based replay storage, history and player navigation (`docs/visualizer.md`)
- [ ] Replay fetch tool in the repository (the corpus scripts are archived in
  `docs/data/inference-2026-10-28/tools/corpus/`)

## Phase 2 — leagues
- [x] Pre-submission audit of the artifact (protocol, CG runtime, legality, identity):
  `docs/data/presubmit-audit-2026-10-29.json`
- [x] Fixes from the audit: turn-0 warm-up, fallback output, ASCII/LF artifact with checked params,
  procCheck compares the process with in-process `current` and times answers by arrival
- [x] Recheck of the fixed artifact `5bace689` (`docs/data/presubmit-recheck-2026-10-29.json`);
  its tooling findings fixed (procCheck strictness, submit atomicity, tests), artifact unchanged
- [x] Recheck of that tooling (`docs/data/tooling-recheck-2026-10-29.json`): opponent kills, leftover
  and late lines, wrapper children, JVM option variables, FileMerger edges fixed; artifact unchanged
- [x] Review of those fixes (same file, `fixes_review`): rerun command with params, timeout notes,
  env names in any case, swap-in build, tests 47 → 59; artifact unchanged
- [x] Submit v01: the user pasted `5bace689` (agent 6823071, 2026-10-29 08:17; `docs/BOTS.md`, Uploads)
- [x] Which code played: `tools/arena_games.py` and `cgAttribute`; all 60 placement games identical to
  the artifact; tools reviewed (wf_0f5ffee8-bfe) and fixed, tests 59 → 74
- [x] v01 upload 2 (the user re-submitted the same bytes, agent 6823455): every game identical to the
  artifact; the first noise measurement (`docs/arena-uploads.md`)
- [x] Build marker: the artifact prints its build id on turn 0 as a TRAIN of sites 0-15 (referee warning
  only); `arena_games.py` and `cgAttribute` read it; artifact `1e5d1a79…` (build `e5417a83`) checked
- [x] Upload the marked artifact (the user, agent 6824680, 2026-10-29 22:50): v01 upload 3; the marker in
  all 60 games, its turn-0 warning matched by cgValidate, every game identical to `1e5d1a79`
  (`docs/BOTS.md`, `docs/arena-uploads.md`)
- [ ] After each promotion: capture the statement, reconcile the rules, re-run `cgValidate`

## Phase 3 — strength (backlog from the audit)
- [x] Arena step of the promotion policy: P1, two uploads per version (`docs/arena-uploads.md`,
  `tools/arena_compare.py`)
- [x] Local part of the policy: P2, 2026-10-30 (`docs/local-screen.md`, `tools/bench_pair.py`, ledger
  `docs/data/seed-ledger.json`, proxy validation PV1 `docs/data/pv1-2026-10-30/`)
- [ ] `old` opponent: the user puts the code of 3315948 into `refs/old-3315948/Player.java`; then the PV1
  calibration (v01 should score about 0.22 against it) and G3 for later candidates
- [x] Bronze boss's score: 27.63 (agent 1870318's rating at the fetch of v06's placement games, 2026-11-03; three games, all lost); the IDE game is no longer needed for the bound
- [x] A local opponent with the field's balance (2026-11-03, `docs/data/exp-2026-11-03-field-opponent/`):
  `field`, income 8 with 3 mines, 4 towers at 600, giants optional (they cost it 20 pp against v06, so off);
  S 0.255 against v06. Our income flags lose against it too: the economy is not the next lever
- [x] `field` adopted into P2 as gate G4 (2026-11-03, PV2 in `docs/local-screen.md`); archers are still unplayed
  locally
- [x] What the arena's strong players do that v06 does not (2026-11-03, `docs/data/study-2026-11-03-arena-winners/`):
  they MOVE under knights (42 % of contact turns, 36 units a turn) and before contact; a third less HP per
  contact turn; income 8 and 12 waves follow from the free queen turns (33 top-ups against our 93)
- [x] Kiting (2026-11-03, `docs/data/exp-2026-11-03-kite/`): KITE / KITE_RANGE / KITE_COVER, swept against v06
  and `field`, screened under P2 with G4: `KITE=1` S 0.695 against v06, +3.1 pp against `field`, passed; final
  evaluation on block 6 S 0.680, +3.5 pp against `field`, the upload recommendation met; frozen as v07
  (2026-11-03), `submit` and `procCheck` passed (`docs/BOTS.md`, Artifacts)
- [x] v07 on the arena under P1 (agents 6843218 and 6843287): placements 26.42 and 24.64, mean 25.53 against
  v06's 26.24, not clearly worse, so v07 is selected 2026-11-03 by the policy; the local +1.5 did not show
  (`docs/arena-uploads.md`)
- [x] Why the kiting gain did not reach the arena (2026-11-03, `docs/data/study-2026-11-03-v07-arena/`): the
  kiting halves the HP lost per contact turn, but the queen runs out of her towers' cover, the knights chase
  her for 50-130 turns without dying, she tops up half as often (4 towers at 2,364 HP against 5 at 3,180)
  and kills 12 fewer queens; the local opponents stand still under knights and so rewarded it
- [x] Kiting bound to the tower cover (2026-11-03, `docs/data/exp-2026-11-03-kite2/`): KITE=2 with KITE_PULL
  and KITE_MAX; the queen near her towers 87 % of the contact turns; screened under P2 with G4 against v07:
  S 0.666, +13.7 pp against `field`, passed; final evaluation on block 7 S 0.667, +14.8 pp against `field`,
  the upload recommendation met; frozen as v08 (2026-11-03), `submit` and `procCheck` passed (`docs/BOTS.md`,
  Artifacts)
- [x] v08 on the arena under P1 (agents 6843773 and 6843829): placements 27.85 and 28.13, mean 27.99 against
  v07's 25.53, not clearly worse, so v08 is selected 2026-11-03; 4 of 8 against the boss;
  `eligibleForPromotion` true (`docs/arena-uploads.md`)
- [x] Silver: the promotion landed 2026-11-03; the new agent registered as v08's first Silver upload, its
  games fetched and attributed (`docs/arena-uploads.md`, v08 promoted to Silver)
- [x] Silver: a second upload of v08 completes its Silver baseline (P1; `docs/arena-uploads.md`, the Silver baseline)
- [x] Silver: the noise margin M in Silver is borrowed from Bronze by the user's decision (`docs/arena-uploads.md`,
  Silver noise margin)
- [x] Silver: study of v08's 250 Silver games and the local opponents' fingerprints (2026-11-05,
  `docs/data/study-2026-11-05-silver/`)
- [x] Silver: the local proxy renewed as PV3 (2026-11-05, the user's decision): strided seed blocks and the
  panel's coverage recorded (`docs/local-screen.md`, PV3; `docs/data/seed-ledger.json`)
- [x] Candidate from the Silver study: a second tower before the enemy's first wave arrives; loses on the
  development seeds, not screened, flags off (`docs/data/exp-2026-11-05-first-wave/`)
- [x] Candidate from the Silver study: hold an HP lead after turn 200; `LATE_HOLD=200` passed the P2 screen on
  PV3 block 0, the local champion, off by default (`docs/data/exp-2026-11-05-late-hold/`)
- [x] `LATE_HOLD=200` frozen as v09 and made the `current` default by the user's decision, not uploaded
  (`docs/BOTS.md`, v09)
- [x] Arena uploads: one command, `tools/arena.py` (status, follow, fetch), and one layout of `replays/arena`;
  `route` renamed v04 (`docs/arena-uploads.md`, Tools and Replay storage)
- [x] Silver: a third upload of v08 through `tools/arena.py follow`, its first live run (`docs/arena-uploads.md`,
  v08 in Silver, upload 5)
- [x] `tools/arena.py`: name the file to paste; notice a paste the leaderboard does not show yet (the old battle
  list empty); stop when the games carry another build's marker; count the next fetch from the end of the last
  one (`docs/arena-uploads.md`, Tools)
- [x] Late economy (2026-11-03, `docs/data/exp-2026-11-03-late-income/`): INCOME_LATE (the income target after
  the ladder) neutral against v06 (0.505-0.513), with thinner top-ups a loss; off by default, not screened
- [x] Study 2026-10-30 (`docs/data/study-2026-10-30/`): we build towers late and few, stronger players
  early and many; early towers win locally. Next: the P2 strategy screen of one arm
- [x] Early towers, P2 strategy track (`docs/data/exp-2026-10-30-towers/`): `INCOME_TARGET=3,MAX_TOWERS=8`
  passed the screen and the final evaluation; frozen as v03, the local champion
- [x] v03 on the arena under P1 (agents 6827580 and 6828013): not clearly worse, so v03 is selected
  (`docs/arena-uploads.md`); a third upload by the user (6829912) joined its baseline
- [ ] Next strength step towards the boss (the gap: `docs/STATUS.md`): candidates from the study
  (a second knight barracks or more knights; queen safety when no tower is near; giants or archers
  against towers)
- [x] Army direction (2026-11-03, `docs/data/exp-2026-11-03-army/`): flags for a second knight barracks
  gated by the tower count, synchronous waves, the forward placement and the creep cap; swept against v05
  and screened under P2: the second barracks after 4 towers with synchronous waves and 6 towers passed
  (G0-G2); final evaluation S 0.5480 against v05, below the 0.60 recommendation; deferred by the user
  (2026-11-03), the flags stay off, to be combined with a later candidate
- [x] Queen safety (2026-11-03): study `docs/data/study-2026-11-03-queen-safety/` (the queen dies topping up
  one tower; a second tower doubles the kill rate), flags THREAT_TOWERS_NEAR / THREAT_RADIUS / THREAT_RANGE,
  swept against v05 and screened under P2 with the army flags (`docs/data/exp-2026-11-03-safety/`): S 0.642,
  passed; final evaluation on block 5 S 0.652, the upload recommendation met; frozen as v06 (2026-11-03),
  `submit` and `procCheck` passed (`docs/BOTS.md`, Artifacts)
- [x] v06 on the arena under P1 (agents 6842260 and 6842539): placements 27.09 and 25.39, mean 26.24 against
  v05's 25.39, not clearly worse, so v06 is selected 2026-11-03 (`docs/arena-uploads.md`); 1.4 below the boss
- [ ] Queen safety, not tried: kiting between sites under knights; the flight avoiding enemy tower range (262
  HP of 2,335 lost in v05's arena deaths); a tower before the barracks when the enemy barracks trains
- [x] Study 2026-11-03 (`docs/data/study-2026-11-03-losses/`): how v04 loses its 120 placement games,
  and the opening, economy and towers of stronger groups. Gaps: barracks-first opening (everyone
  stronger opens with a mine), income stuck at 3 (they hold 4-7), 6-7 towers at 700 HP (they hold 4 at
  about 450), 57 of 100 queen turns on top-ups (Legend 23), fewer waves
- [x] Strategy parameters as flags of `current` (MINES_FIRST, TOWER_HP, UPGRADE, with INCOME_TARGET and
  MAX_TOWERS), swept against v04 and the opening screened under P2 (`docs/data/exp-2026-11-03-mines-first/`):
  two mines before the barracks passed; the income target, the tower cap, the threshold and the
  upgrade walk lose or do nothing locally
- [x] `current?MINES_FIRST=2`: the final evaluation against v04 on evaluation block 3 passed with the
  upload recommendation met (`docs/data/exp-2026-11-03-mines-first/`)
- [x] `current?MINES_FIRST=2` frozen as v05 (2026-11-03), `submit` and both `procCheck` passed (`docs/BOTS.md`, Artifacts)
- [x] v05 on the arena under P1: placements 23.93 and 26.47, not clearly worse than `route`, selected
  2026-11-03 (`docs/arena-uploads.md`)
- [x] Improve the final approach to the same site for the next target; compare with v03
  (`docs/data/exp-2026-10-31-approach/`)
- [ ] Decide adoption/freeze of the APPROACH candidate (`docs/data/exp-2026-10-31-approach/`)
- [ ] Plan queen routes to the selected site's contact zone: avoid obstacles before getting stuck,
  minimize walking turns while accounting for threats and the next site's approach, and replan
  as the board changes. Use MOVE along the route and BUILD once in contact. First experiment keeps
  site selection unchanged; evaluate movement separately under P2 (`docs/local-screen.md`).
  - [x] Step 0: `cr.tools.LegScan` with the v03 baselines and the APPROACH calibration point
    (`docs/data/exp-2026-11-01-route/`)
  - [x] Step 1: `ROUTE=1`, movement only (heading fan + landing point, replaces UNSTICK and APPROACH):
    P2 screen and final evaluation against v03 passed, below the upload recommendation
    (`docs/data/exp-2026-11-01-route/`); uploaded by the user's decision, two placements under P1
    (22.89 and 22.10), selected 2026-11-02 (`docs/arena-uploads.md`) and frozen as v04 (ROUTE on by default)
  - [ ] Step 2: unit-aware final step
  - [ ] Step 3: `ORDER=k`, target choice by turns, against v04 (the `current` defaults)
  - [ ] Step 4: own towers on the route (ROUTE walks past them, so fewer top-ups; same README), flee
    routing, teardown bonus
- [x] A local opponent with giants, like the strongest Silver players: the rival R0, `field` with kiting, two mines
  first and capped giants (`docs/data/exp-2026-11-06-rival/`)
- [x] PV4: R0 as P2's gate G5, spec `rival` (2026-11-06, the user's decision; `docs/local-screen.md`, PV4)
- [x] The late income against R0 (2026-11-06): even with v09, failed G1; the flags stay off (`docs/data/exp-2026-11-06-income/`)
- [x] An answer to the enemy's giants against R0 (2026-11-06): the tower guard loses, archers gained on the
  development seeds only; not adopted (`docs/data/exp-2026-11-06-giants/`)
- [x] When to release the knight waves (2026-11-06): holding loses; the second knight barracks at the centre passed the P2 screen and is v10, the
  default (the user's decision; `docs/data/exp-2026-11-06-waves/`)
- [x] The final evaluation of v10 against v08 (2026-11-06): S 0.5764, below the 0.60 upload recommendation
  (`docs/data/exp-2026-11-06-waves/`)
- [x] v10 on the arena under P1 (2026-11-07, two Silver uploads): not clearly worse, selected (`docs/arena-uploads.md`)
- [x] Why v10 loses to weaker players and beats stronger ones (2026-11-07, `docs/data/study-2026-11-07-tower-fire/`)
- [x] The ladder's walks into the enemy base through enemy tower ranges (2026-11-07): `SAFE_LADDER=1` passed P2 as a
  targeted fix (`docs/data/exp-2026-11-07-safe-ladder/`)
- [x] Adopt SAFE_LADDER into the local champion: default 1, v11 frozen (2026-11-07, the user's call)
- [ ] The queen's top-ups and flights to own towers under enemy tower fire (same study)
- [ ] Deaths around turn 140 against opponents with twice our income and a standing knight army (same study)
- [x] Organize our arena replays by version, league and upload; downloaders use the registry (`docs/arena-uploads.md`, Replay storage)
- [ ] Find the gap (older item): the 77 lost placement games of v01's three uploads (`replays/arena/v01/bronze/upload-<NN>/games`,
  manifests `docs/data/arena-<agent>-games-2026-10-29.json` for 6823071, 6823455 and 6824680)
- [x] Queen stalls when a site blocks the straight path to a far BUILD target (worst 43 turns): flag
  `UNSTICK` (off by default), tests on two captured positions, `stallScan`; passed P2 as a targeted fix
  on screen block 0 (`docs/data/exp-2026-10-30-unstick/`)
- [x] Adopt UNSTICK into the local champion: default 1, v02 frozen (2026-10-30, the user's call)
- [x] v02 on the arena under P1 (agents 6826276 and 6827094; the user's choice, below P2's S ≥ 0.60
  recommendation): not clearly worse, so v02 is selected, with no gain either: it played as v01 in most
  games (`docs/arena-uploads.md`)
- [ ] Slow slides along a site: UNSTICK with steps under 5 counted as standing changed 13.6 % of the
  development maps (S 0.507 there), a strategy candidate
- [ ] Remaining 1-2 turn stalls on BUILDs towards towers (fleeing, or idling at home)
- [ ] Stalls UNSTICK misses because a unit blocks the queen: the sites-only prediction sees a step, while
  her own knight stops it (v02, 3 games of 4000 on block 400000). Idea: also treat a BUILD that left her
  in place on the previous turn as stuck
- [ ] TRAIN choice respecting the 30-creep cap (only matters with more than one barracks)
- [ ] Seat asymmetry in greedy self-play (player 0 won 187, player 1 110 of 300 games): bot
  tie-breaks or rules? On the arena too: v01 won 63 of 96 games on seat 0 and 40 of 84 on seat 1 over
  its three placements, against fields of similar strength (opponents' median 17.46 and 17.42); adjusted
  for the opponent's score and the upload, seat 1 costs about 1.1 rating points (z ≈ -2.3,
  `docs/data/noise-2026-10-30/noise.txt`)
- [ ] Runner, before the next upload (each changes the artifact): the fallback answers before it
  prints the stack trace; the warm-up bot gets its own Params and `init`; a testable game loop so the
  fallback and the turn deadline get unit tests; optionally count the warm-up cap from JVM start
- [ ] Before any bot simulates with `Rules`: keep the global seat in `InputReader` (the build-conflict
  parity depends on it)
- [x] The architecture page checked against v08's code and rewritten (2026-11-05, `docs/DEVLOG.md`)
- [ ] Code quirks found by that check (`docs/bot-architecture.html`, «Особенности кода»): P2 candidates,
  except the −180° duplicate and the warm-up, which do not change the play (the warm-up can join the runner
  item above)
- [ ] Stale comments and records found by that check (`docs/DEVLOG.md`, 2026-11-05): the javadoc of
  `MainBot` and `kite`, `Params` ranges, `CGRunner.warmUp`, test comments and `CGRunnerTest`'s second
  configuration; the flags table and test names in `docs/BOTS.md`; the marker row of `.ai/project.md`
- [ ] Optional: warm `nextInt` and the println path past their JIT thresholds (bursts up to 20 ms on
  one slow core at turns 24-26, 127, 191)
