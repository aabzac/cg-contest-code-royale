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
- [ ] Submit verified v01 (user pastes `build/submit/Player.java`), follow placement
