# Status

- Date: 2026-10-29. Phase 2 done: v01 is on the arena as agent 6823071, placement complete.
- Game: Code Royale, permanent multiplayer arena (`code-royale`). No deadline.
- Arena entry: `aabzac`, agent 6823071 (Java, submission 41591829, created 2026-10-29 08:17), Bronze,
  local rank 736/2296, score 18.24, placement complete at 09:36: 60 games, 36 wins
  (`docs/data/arena-6823071-placement-2026-10-29.json`). Every placement game is identical to
  artifact `5bace689…` (`cgAttribute`, 7,230 turns; games in `docs/data/arena-6823071-games-2026-10-29.json`).
- Replaced entry: agent 3315948 (old code, not from this project; rank 559/2295, score 19.64 on
  2026-10-28, `docs/data/arena-3315948-presubmit-2026-10-28.json`); the user keeps its code from the IDE
  for a rollback.
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. Behaviour vs
  `refs/referee-csj/config/level4/Boss.java` (local `boss` spec) unverified.
- Selected version: v01 (= `current`, greedy) under policy P0, the user's decision to replace the old
  entry, without a comparison with it. Uploaded: v01 as `build/submit/Player.java` sha256 `5bace689…`
  (56,780 chars, default params; `./gradlew submit` at commit 80e404c rebuilds it byte for byte).
  Pending candidate: none. Pre-upload evidence: `docs/data/presubmit-audit-2026-10-29.json`,
  `docs/data/presubmit-recheck-2026-10-29.json`, `docs/data/tooling-recheck-2026-10-29.json`,
  DEVLOG 2026-10-29; uploads in `docs/BOTS.md`.
- Simulator: live rules in `cr.core`, listed in `docs/rules/inferred.md`; corpora `replays/cg` (98) and
  `replays/cg2` (138, Bronze/Silver/Gold). Open question: the tie-break at equal scores (CG ranked
  player 0 first in 2/2 games; the simulator scores a draw).
- Benches: numbers measured before the live rules (2026-10-28) used the old engine; current numbers
  are in `docs/BOTS.md`.
- Live Bronze statement captured: `docs/rules/bronze.live.html` (differs from the repo text only in
  knight HP 25 and 250 turns).
