# Plan

## Phase 0 — orient
- [x] Statements (live Wood 3, referee levels 1-4), referee source, leaderboard snapshot
- [x] Legend replay corpus (88) + own arena games (10)

## Phase 1 — infrastructure
- [x] Gradle/Java 17 skeleton, `cr.core` port of the referee
- [x] Map generator 1:1 (validated on 98 replays)
- [x] `CgReplay`/`cgValidate` with HUD, site tooltip and unit checks, resync mode
- [x] Bot interface, Params, v00, greedy `current`, CGRunner, FileMerger (`submit`)
- [x] In-process Referee, LocalRunner (`run`), BenchmarkRunner (`bench`), ProcessCheck (`procCheck`)
- [ ] Capture live Bronze statement; reconcile constants
- [ ] Spawn position rule = replays
- [x] Tower damage formula = replays (distance from the site centre)
- [ ] cgValidate 100% on the corpus (strict mode)
- [x] Freeze v01 (greedy) with parameter snapshot; repo level-4 boss as a process opponent (`ProcessBot`)
- [x] Visualizer for local games (`run -Pout`, `serve`, `visualizer/index.html`)
- [ ] `cgRender`: export CG replays to the visualizer format

## Phase 2 — leagues
- [ ] Submit verified v01 (user pastes `build/submit/Player.java`), follow placement
