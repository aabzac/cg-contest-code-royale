# Status

- Date: 2026-10-30. Phase 2 done; v01 has three arena uploads in Bronze, the P1 baseline.
- Game: Code Royale, permanent multiplayer arena (`code-royale`). No deadline.
- Arena entry: `aabzac`, agent 6824680 (Java, submission 41596829, created 2026-10-29 22:50): v01 upload 3,
  the marked artifact `1e5d1a79…` (build `e5417a83`, config `4194117d`, commit 2641aa9). Bronze, placement
  complete 2026-10-30 00:47: score 18.98, rank 654/2294, 60 games, 36 wins. Every game carries the marker
  and is identical to the artifact (`cgAttribute`, 6,222 turns;
  `docs/data/arena-6824680-placement-2026-10-29.json`).
- Earlier entries (battle lists empty since replaced): 6823455 (v01 upload 2, 14:03-22:50): placement
  16.08, rank 1067/2294 at 20:51, 31 wins of 60 (3 by opponent timeout on turn 0). 6823071 (v01 upload 1, 08:17-14:03): placement 18.24,
  rank 736/2296, 36 wins of 60. Their games: `docs/data/arena-6823071-games-2026-10-29.json`,
  `docs/data/arena-6823455-games-2026-10-29.json`. 3315948 (old code, not from this project; rank
  559/2295, score 19.64 on 2026-10-28, `docs/data/arena-3315948-presubmit-2026-10-28.json`): the user
  keeps its code from the IDE.
- Policy: P1 since 2026-10-29 for the arena step (`docs/arena-uploads.md`, `tools/arena_compare.py`,
  registry `docs/data/arena-uploads.json`): two uploads per version, decision on the means within a
  league, M = 2 × the SD between uploads of one configuration. Bronze M = 3.01 from v01's three uploads
  (2 df, provisional: 95 % interval 1.6-19). The local screen before an upload is not set yet (phase 3).
- Selected version: v01 (= `current`, greedy), baseline in Bronze 18.24, 16.08 and 18.98, mean 17.77.
  Live: `1e5d1a79…` (commit 2641aa9 rebuilds it byte for byte; kept copy
  `replays/artifacts/1e5d1a79/Player.java`). Pending strategy candidate: none.
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. Behaviour vs
  `refs/referee-csj/config/level4/Boss.java` (local `boss` spec) unverified.
- Simulator: live rules in `cr.core`, listed in `docs/rules/inferred.md`; corpora `replays/cg` (98) and
  `replays/cg2` (138, Bronze/Silver/Gold). Open question: the tie-break at equal scores (CG ranked
  player 0 first in 2/2 games; the simulator scores a draw).
- Benches: numbers measured before the live rules (2026-10-28) used the old engine; current numbers
  are in `docs/BOTS.md`.
- Live Bronze statement captured: `docs/rules/bronze.live.html` (differs from the repo text only in
  knight HP 25 and 250 turns).
