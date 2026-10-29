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
- [ ] `cgRender`: export CG replays to the visualizer format
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
- [x] Submit v01: the user pasted `5bace689` (agent 6823071, 2026-10-29 08:17); placement complete at
  09:36, rank 736/2296, score 18.24, 60 games, 36 wins (`docs/data/arena-6823071-placement-2026-10-29.json`)
- [x] Which code played: `tools/arena_games.py` and `cgAttribute`; all 60 placement games identical to
  the artifact; tools reviewed (wf_0f5ffee8-bfe) and fixed, tests 59 → 74
- [x] v01 upload 2 (the user re-submitted the same bytes, agent 6823455): 16.08 after 18.24, every game
  identical to the artifact; first noise measurement, M ≈ 3.05 (`docs/arena-uploads.md`)
- [x] Build marker: the artifact prints its build id on turn 0 as a TRAIN of sites 0-15 (referee warning
  only); `arena_games.py` and `cgAttribute` read it; artifact `1e5d1a79…` (build `e5417a83`) checked
- [ ] Upload the marked artifact (the user): v01 upload 3, the third noise sample and the first live check
  of the marker (its warning in cgValidate, the markers in the manifest)
- [ ] After each promotion: capture the statement, reconcile the rules, re-run `cgValidate`

## Phase 3 — strength (backlog from the audit)
- [x] Arena step of the promotion policy: P1, two uploads per version (`docs/arena-uploads.md`,
  `tools/arena_compare.py`)
- [ ] Local part of the policy (proxy, screen, replication), set before the first comparison
- [ ] Find the gap: the 24 lost placement games (`replays/cg/arena-6823071/games`, manifest in
  `docs/data/arena-6823071-games-2026-10-29.json`); v01 placed 736/2296 (18.24), the old code stood at
  559/2295 (19.64)
- [ ] Queen stalls when a site blocks the straight path to a far BUILD target (worst 43 turns)
- [ ] TRAIN choice respecting the 30-creep cap (only matters with more than one barracks)
- [ ] Seat asymmetry in greedy self-play (player 0 won 187, player 1 110 of 300 games): bot
  tie-breaks or rules?
- [ ] Runner, before the next upload (each changes the artifact): the fallback answers before it
  prints the stack trace; the warm-up bot gets its own Params and `init`; a testable game loop so the
  fallback and the turn deadline get unit tests; optionally count the warm-up cap from JVM start
- [ ] Before any bot simulates with `Rules`: keep the global seat in `InputReader` (the build-conflict
  parity depends on it)
- [ ] Optional: warm `nextInt` and the println path past their JIT thresholds (bursts up to 20 ms on
  one slow core at turns 24-26, 127, 191)
