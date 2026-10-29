# Status

- Date: 2026-10-29. Phase 2 done; v01 has two arena uploads in Bronze, the P1 baseline.
- Game: Code Royale, permanent multiplayer arena (`code-royale`). No deadline.
- Arena entry: `aabzac`, agent 6823455 (Java, submission 41593274, created 2026-10-29 14:03): v01 upload 2,
  the same artifact `5bace689…` as upload 1. Bronze, placement complete by 15:51: score 16.08, rank
  1067/2294 at 20:51, 60 games, 31 wins. Every game is identical to the artifact (`cgAttribute`, 6,413
  turns; `docs/data/arena-6823455-games-2026-10-29.json`).
- Earlier entries: 6823071 (v01 upload 1, 08:17-14:03): placement 18.24, rank 736/2296, 36 wins of 60
  (`docs/data/arena-6823071-placement-2026-10-29.json`); its battle list is empty since the re-upload.
  3315948 (old code, not from this project; rank 559/2295, score 19.64 on 2026-10-28,
  `docs/data/arena-3315948-presubmit-2026-10-28.json`): the user keeps its code from the IDE.
- Policy: P1 since 2026-10-29 for the arena step (`docs/arena-uploads.md`, `tools/arena_compare.py`,
  registry `docs/data/arena-uploads.json`): two uploads per version, decision on the means within a
  league, M = 2 × the SD between uploads of one configuration. Bronze M ≈ 3.05 from v01's pair
  (provisional). The local screen before an upload is not set yet (phase 3).
- Selected version: v01 (= `current`, greedy), baseline in Bronze 18.24 and 16.08, mean 17.16. Uploaded:
  v01 as `5bace689…` (commit 80e404c rebuilds it byte for byte). Ready to upload: v01 with the build
  marker, `build/submit/Player.java` sha256 `1e5d1a79…`, build `e5417a83`, config `4194117d` (shared with `5bace689`); it plays as `current`
  (procCheck 200/200 vs v01 and vs boss) and builds from the commit that adds the marker (2026-10-29). Pending strategy candidate: none.
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
