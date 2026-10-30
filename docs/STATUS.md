# Status

The current state only; replace lines here, do not append history. Where the rest lives: uploads and
artifacts `docs/BOTS.md`, P1 comparisons and the noise margin `docs/arena-uploads.md`, history and
evidence `docs/DEVLOG.md`, the backlog `docs/PLAN.md`, a short Russian summary for the user
`docs/SUMMARY.md`. The history this page held until 2026-10-30: `git show 60f0b59:docs/STATUS.md`.

- Date: 2026-10-30. Phase 3 (strength). Game: Code Royale, permanent multiplayer arena (`code-royale`),
  no deadline. League: Bronze.
- Selected version and local champion: v03 = v02 with early towers (mines only to income 3, up to 8
  towers), frozen 2026-10-30 (`cr.bot.v03.GreedyBot`, `versions/v03.properties`); `current` defaults to
  it. Selected under P1 on 2026-10-30 (`docs/arena-uploads.md`); rollback artifact `d6266d41…` (commit
  095d1eb; `docs/BOTS.md`, Artifacts).
- Arena entry: `aabzac`, agent 6828013 = v03 upload 2 (`docs/BOTS.md`, Uploads).
- Pending candidate: none. Next: a strength step towards the boss (`docs/PLAN.md`, phase 3).
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. It stands above about
  27.1: on the Bronze leaderboard of 2026-10-30 08:21 the top rows were 27.75 (placing), 27.14, 27.01 and
  27.00, none eligible for promotion (`docs/data/leaderboard-bronze-2026-10-30.json`). v03 is about 5
  points below (the mean of its two placements). The exact score needs an IDE game; the user deferred
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
