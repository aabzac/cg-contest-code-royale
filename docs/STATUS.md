# Status

- Date: 2026-10-28. Phase 0 done; phase 1 infrastructure built; **simulator validated**: every
  turn of 234 of 236 replays matches in strict mode; the other 2 differ only in the winner of a tie.
- Game: Code Royale, permanent multiplayer arena (`code-royale`). No deadline.
- Arena entry: `aabzac`, agent 3315948 (Java, submission 17067866, updated 2026-10-18),
  Bronze, local rank 559/2295, score 19.64, placement complete (2026-10-28). Last 10 games: 2 wins.
  Battles + 10 replays saved in `replays/cg/arena-3315948/`. Old code, not from this project.
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. Behaviour vs
  `refs/referee-csj/config/level4/Boss.java` (local `boss` spec) unverified.
- Selected version: none. Uploaded from this project: none. Pending candidate: none (`current` = greedy).
- Simulator: live rules in `cr.core`, listed in `docs/rules/inferred.md`; corpora `replays/cg` (98) and
  `replays/cg2` (138, Bronze/Silver/Gold). Open question: the tie-break at equal scores (CG ranked
  player 0 first in 2/2 games; the simulator scores a draw).
- Benches: numbers measured before the live rules (2026-10-28) used the old engine; current numbers
  are in `docs/BOTS.md`.
- Live Bronze statement captured: `docs/rules/bronze.live.html` (differs from the repo text only in
  knight HP 25 and 250 turns).
