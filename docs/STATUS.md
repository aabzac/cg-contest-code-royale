# Status

- Date: 2026-10-29. Phase 2: the v01 upload candidate is built and verified; the user pastes it
  into the CG IDE.
- Game: Code Royale, permanent multiplayer arena (`code-royale`). No deadline.
- Arena entry: `aabzac`, agent 3315948 (Java, submission 17067866, updated 2026-10-18),
  Bronze, local rank 559/2295, score 19.64, placement complete (2026-10-28 snapshot
  `docs/data/arena-3315948-presubmit-2026-10-28.json`). Last 10 games: 2 wins. Old code, not from
  this project; the user keeps a copy from the IDE for a rollback.
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. Behaviour vs
  `refs/referee-csj/config/level4/Boss.java` (local `boss` spec) unverified.
- Selected version: none. Uploaded from this project: none yet. Pending candidate: v01 (= `current`,
  greedy) as `build/submit/Player.java` sha256 `5bace689…` (56,780 chars, default params), policy P0
  (replaces the old entry). Evidence: `docs/data/presubmit-audit-2026-10-29.json`,
  `docs/data/presubmit-recheck-2026-10-29.json`, `docs/data/tooling-recheck-2026-10-29.json`,
  DEVLOG 2026-10-29; uploads in `docs/BOTS.md`.
- Simulator: live rules in `cr.core`, listed in `docs/rules/inferred.md`; corpora `replays/cg` (98) and
  `replays/cg2` (138, Bronze/Silver/Gold). Open question: the tie-break at equal scores (CG ranked
  player 0 first in 2/2 games; the simulator scores a draw).
- Benches: numbers measured before the live rules (2026-10-28) used the old engine; current numbers
  are in `docs/BOTS.md`.
- Live Bronze statement captured: `docs/rules/bronze.live.html` (differs from the repo text only in
  knight HP 25 and 250 turns).
