# Status

The current state only; replace lines here, do not append history. Where the rest lives: uploads and
artifacts `docs/BOTS.md`, P1 comparisons and the noise margin `docs/arena-uploads.md`, history and
evidence `docs/DEVLOG.md`, the backlog `docs/PLAN.md`, a short Russian summary for the user
`docs/SUMMARY.md`. The history this page held until 2026-10-30: `git show 60f0b59:docs/STATUS.md`.

- Date: 2026-11-03. Phase 3 (strength). Game: Code Royale, permanent multiplayer arena (`code-royale`),
  no deadline. League: Bronze.
- Selected version (arena): v04 = v03 with the queen's walk planner (ROUTE=1: `RoutePlanner`
  replaces UNSTICK and APPROACH), frozen 2026-11-02 (`cr.bot.v04.GreedyBot`, `versions/v04.properties`);
  `current` defaulted to it until v05 (below). Selected under P1 on 2026-11-02 as the `route` configuration, by two placements
  (mean 22.50 against v03's 22.43, "not clearly worse"; `docs/arena-uploads.md`); rollback artifact
  `da914c4c…` (commit eb5dd14, kept in `artifacts/da914c4c/`; `docs/BOTS.md`, Artifacts). Previous: v03
  (`cr.bot.v03.GreedyBot`, early towers), selected 2026-10-30 to 2026-11-02, artifact `d6266d41…`.
- Arena entry: `aabzac`, agent 6841317 = v05 upload 1 (artifact `13b3ec0c…`, empty PARAMS, commit 496bcc5),
  pasted by the user 2026-11-03 14:40 local; placement complete 14:51: score 23.93, rank 144/2289, 60 games,
  41 wins, every game identical to the artifact (`docs/BOTS.md`, Uploads;
  `docs/data/arena-6841317-placement-2026-11-03.json`). It replaced agent 6836589 = `route` upload 2, saved
  (22.10, rank 302/2288; `docs/data/arena-6836589-placement-2026-11-02.json`).
- Candidate under P1: v05. Upload 1: 23.93 against `route`'s mean 22.50 (+1.43; no early rollback, −2M =
  −5.21), rating 23.60 ± 0.51 against 22.06 ± 0.35 (`docs/arena-uploads.md`). The P1 verdict needs the
  second upload of the same artifact (the user's paste); M 2.61 (6 df).
- Candidate judged under P1 (2026-11-02): `current?ROUTE=1`, the queen's walk planner (steps 0-1 of the route
  plan). P2 strategy screen on block 4 passed (S 0.5813 against v03); the final evaluation on block 2
  (404000-405999) passed G0-G2 against v03 with S 0.5856 (CI 0.5701-0.6011), Δ +0.0025 against `boss`, G3
  skipped: below the upload recommendation S ≥ 0.60 (`docs/data/exp-2026-11-01-route/`). Uploaded by the
  user's decision as `route`: placements 22.89 and 22.10 against v03's mean 22.43 (M 2.61), pooled rating
  22.01 ± 0.33 against 22.39 ± 0.28; verdict "not clearly worse", so it is the selected version
  (`docs/arena-uploads.md`); the arena neither confirms nor contradicts the local +0.46 rating points. No
  pending candidate. Frozen as v04 on 2026-11-02 by the user's decision (ROUTE on by default in `current`);
  the next candidate is compared with v04 locally and with `route`'s two placements on the arena. The earlier candidate
  `current?APPROACH=1` (S 0.5245, `docs/data/exp-2026-10-31-approach/`) is superseded by it (ROUTE replaces
  APPROACH when on).
- Local champion and `current` defaults: v05 = v04 with two mines before the knight barracks (MINES_FIRST=2),
  frozen 2026-11-03 by the user's decision (`cr.bot.v05.GreedyBot`, `versions/v05.properties`; artifact
  `13b3ec0c…`, kept in `artifacts/13b3ec0c/`, procCheck 400/400; `docs/BOTS.md`). On the arena since 2026-11-03
  (upload 1 above; the second upload is the user's paste). Its P2 record
  (`docs/data/exp-2026-11-03-mines-first/`): screen block 5 (210000-211999) S 0.7645
  (CI 0.7492-0.7798) against v04, Δ +0.0100 against `boss`, G3 skipped; G0 by the quiet repeat (the first
  run's 5 slow turns on 4 consecutive maps were host noise). From the study 2026-11-03
  (`docs/data/study-2026-11-03-losses/`: every stronger group opens with a mine). The other study
  parameters lose or do nothing locally against v04: income target 5 (0.29), top-up threshold 400
  (0.29), 4-6 towers (0.40-0.52), walking to upgrade mines (no change); the local opponents are
  knights-only and tower-heavy, so the tower parameters stay for the arena to judge. Final evaluation
  on evaluation block 3 (406000-407999), the user's decision: G0-G2 pass, S 0.7556 (CI 0.7401-0.7712)
  against v04, Δ +0.0150 against `boss`, no failure; the upload recommendation S ≥ 0.60 is met. The route
  steps 2-4 are parked (1-2 walking turns per game left).
- Walking diagnostics: `./gradlew legScan` (`cr.tools.LegScan`), baselines in the same README.
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. It stands above about
  27.1: on the Bronze leaderboard of 2026-10-30 08:21 the top rows were 27.75 (placing), 27.14, 27.01 and
  27.00, none eligible for promotion (`docs/data/leaderboard-bronze-2026-10-30.json`). `route` is about 4.6
  points below (the mean of its two placements), v03 about 4.7 (three). The exact score needs an IDE game; the user deferred
  it. The local `boss` spec is the repository's Default AI: a floor, not this boss.
- Waiting on the user: the code of 3315948 in `refs/old-3315948/Player.java` (opponent `old`, gate G3
  of P2).
- Policies: P2 locally (`docs/local-screen.md`), then `submit` and both `procCheck`, then P1 on the arena
  (`docs/arena-uploads.md`, `tools/arena_compare.py`, registry `docs/data/arena-uploads.json`).
- Simulator: live rules in `cr.core`, listed in `docs/rules/inferred.md`; original validation corpora
  `replays/cg/legend-top` (88), `replays/arena/legacy/bronze/upload-01/games` (10) and
  `replays/cg2` (138, Bronze/Silver/Gold). Arena storage: `docs/arena-uploads.md`, Replay storage. Open questions:
  - the tie-break at equal scores: CG ranks player 0 first in every known game (`docs/rules/inferred.md`),
    while the simulator scores a draw;
  - the survivor's score after an opponent's mid-game timeout, one game off by 1 HP.
- Benches: numbers measured before the live rules (2026-10-28) used the old engine; current numbers
  are in `docs/BOTS.md`.
- Live Bronze statement captured: `docs/rules/bronze.live.html` (differs from the repo text only in
  knight HP 25 and 250 turns).
