# Status

The current state only; replace lines here, do not append history. Where the rest lives: uploads and
artifacts `docs/BOTS.md`, P1 comparisons and the noise margin `docs/arena-uploads.md`, history and
evidence `docs/DEVLOG.md`, the backlog `docs/PLAN.md`, a short Russian summary for the user
`docs/SUMMARY.md`. The history this page held until 2026-10-30: `git show 60f0b59:docs/STATUS.md`.

- Date: 2026-11-02. Phase 3 (strength). Game: Code Royale, permanent multiplayer arena (`code-royale`),
  no deadline. League: Bronze.
- Selected/default version and last adopted frozen champion: v03 = v02 with early towers (mines only to income 3, up to 8
  towers), frozen 2026-10-30 (`cr.bot.v03.GreedyBot`, `versions/v03.properties`); `current` defaults to
  it. Selected under P1 on 2026-10-30 (`docs/arena-uploads.md`); rollback artifact `d6266d41…` (commit
  095d1eb; `docs/BOTS.md`, Artifacts).
- Arena entry: `aabzac`, agent 6836411 = `route` upload 1 (artifact `da914c4c…`, `current` with PARAMS
  `ROUTE=1`, commit eb5dd14), pasted by the user 2026-11-01 23:56 local; placement complete 2026-11-02
  01:30: score 22.89, rank 263/2288, 60 games, 39 wins, every game identical to the artifact
  (`docs/BOTS.md`, Uploads; `docs/data/arena-6836411-placement-2026-11-01.json`). It replaced agent
  6829912 = v03 upload 3, saved before the paste (`docs/data/arena-6829912-presubmit-2026-11-01.json`).
- Pending candidate: `current?ROUTE=1`, the queen's walk planner (steps 0-1 of the route plan). P2
  strategy screen on block 4 passed (S 0.5813 against v03); the final evaluation on block 2
  (404000-405999) passed G0-G2 against the selected v03 with S 0.5856 (CI 0.5701-0.6011), Δ +0.0025
  against `boss`, G3 skipped: below the upload recommendation S ≥ 0.60 (`docs/data/exp-2026-11-01-route/`).
  Uploaded by the user's decision as `route` (artifact `da914c4c…`, `docs/BOTS.md` Artifacts, kept in
  `artifacts/da914c4c/`): under P1 its first placement scored 22.89 against v03's mean 22.43 (M 2.81), no
  early rollback, rating 22.39 ± 0.43 against 22.37 ± 0.27 (`docs/arena-uploads.md`). Next: a second upload
  of the same artifact (the user pastes `artifacts/da914c4c/Player.java`; `submit -Pparams=ROUTE=1` from
  commit eb5dd14 rebuilds it), then the P1 verdict; adoption (a frozen version with ROUTE on by default) is
  the user's decision. Off by default in `current`. The earlier candidate `current?APPROACH=1` (S 0.5245,
  `docs/data/exp-2026-10-31-approach/`) is superseded by it (ROUTE replaces APPROACH when on).
- Walking diagnostics: `./gradlew legScan` (`cr.tools.LegScan`), baselines in the same README.
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. It stands above about
  27.1: on the Bronze leaderboard of 2026-10-30 08:21 the top rows were 27.75 (placing), 27.14, 27.01 and
  27.00, none eligible for promotion (`docs/data/leaderboard-bronze-2026-10-30.json`). v03 is about 4.7
  points below (the mean of its three placements). The exact score needs an IDE game; the user deferred
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
