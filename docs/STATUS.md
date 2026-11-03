# Status

The current state only; replace lines here, do not append history. Where the rest lives: uploads and
artifacts `docs/BOTS.md`, P1 comparisons and the noise margin `docs/arena-uploads.md`, history and
evidence `docs/DEVLOG.md`, the backlog `docs/PLAN.md`, a short Russian summary for the user
`docs/SUMMARY.md`. The history this page held until 2026-10-30: `git show 60f0b59:docs/STATUS.md`.

- Date: 2026-11-03. Phase 3 (strength). Game: Code Royale, permanent multiplayer arena (`code-royale`),
  no deadline. League: Bronze.
- Selected version, local champion and `current` defaults: v05 = v04 with two mines before the knight
  barracks (MINES_FIRST=2), frozen 2026-11-03 (`cr.bot.v05.GreedyBot`, `versions/v05.properties`). Selected
  under P1 on 2026-11-03 by two placements (mean 25.20 against `route`'s 22.50, "not clearly worse";
  `docs/arena-uploads.md`); artifact `13b3ec0c…` (commit 496bcc5, kept in `artifacts/13b3ec0c/`;
  `docs/BOTS.md`, Artifacts). The rollback artifact for the next candidate is `13b3ec0c`. Previous: v04 (the
  `route` configuration, artifact `da914c4c…`), selected 2026-11-02 to 2026-11-03; v03, 2026-10-30 to 2026-11-02.
- Arena entry: `aabzac`, agent 6841407 = v05 upload 2 (the same artifact `13b3ec0c…` as upload 1), pasted by the
  user 2026-11-03 14:58 local; placement complete 15:11: score 26.47, rank 27/2289, 60 games, 41 wins, every
  game identical to the artifact (`docs/BOTS.md`, Uploads; `docs/data/arena-6841407-placement-2026-11-03.json`).
  It replaced agent 6841317 = v05 upload 1, saved (23.93, rank 144/2289;
  `docs/data/arena-6841317-placement-2026-11-03.json`).
- Candidate judged under P1 (2026-11-03): v05. Placements 23.93 and 26.47 (mean 25.20, SD 1.80) against
  `route`'s 22.50; pooled rating 24.90 ± 0.37 against 22.05 ± 0.35 (scale 1.61); M 2.77 (7 df). Neither
  rollback condition holds, so v05 is the selected version. The arena agrees with the local direction
  (P2: S 0.76 against v04, about +1.7 head-to-head) and exceeds its size. No pending candidate. The earlier
  judgements (`route` 2026-11-02, v03 2026-10-30) are in `docs/arena-uploads.md`.
- v05's local record (procCheck 400/400 of the artifact; P2
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
