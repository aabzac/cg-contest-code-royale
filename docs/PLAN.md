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
- [ ] Bronze boss's exact score from an IDE game (`gameResult.agents[].score`); now only above about 27.1
- [ ] A local opponent with archers or giants (24 % of v01's arena opponents build them; both local
  opponents play knights only)
- [x] Study 2026-10-30 (`docs/data/study-2026-10-30/`): we build towers late and few, stronger players
  early and many; early towers win locally. Next: the P2 strategy screen of one arm
- [x] Early towers, P2 strategy track (`docs/data/exp-2026-10-30-towers/`): `INCOME_TARGET=3,MAX_TOWERS=8`
  passed the screen and the final evaluation; frozen as v03, the local champion
- [x] v03 on the arena under P1 (agents 6827580 and 6828013): not clearly worse, so v03 is selected
  (`docs/arena-uploads.md`)
- [ ] Next strength step towards the boss (the gap: `docs/STATUS.md`): candidates from the study
  (a second knight barracks or more knights; queen safety when no tower is near; giants or archers
  against towers)
- [ ] A local opponent with giants (they break towers; 12 % of the arena opponents build them)
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
- [ ] Optional: warm `nextInt` and the println path past their JIT thresholds (bursts up to 20 ms on
  one slow core at turns 24-26, 127, 191)
