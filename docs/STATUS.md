# Status

- Date: 2026-10-30. Phase 2 done. Phase 3: local screen P2 adopted; v02 (v01 + the targeted fix
  UNSTICK) selected, then v03 (early towers) selected under P1 after two arena uploads in Bronze
  (23.25 and 20.67, about +4 points over v02).
- Game: Code Royale, permanent multiplayer arena (`code-royale`). No deadline.
- Arena entry: `aabzac`, agent 6828013 (Java, submission 41601594, created 2026-10-30 18:53): v03 upload 2.
  - Artifact `d6266d41…` (build `cd1f3d33`, config `12070467`, commit 095d1eb), submitted by the agent in
    the built-in browser with the user's permission.
  - Bronze, placement complete 19:31: score 20.67, rank 428/2290, 60 games, 39 wins (seat 0: 25 of 34,
    seat 1: 14 of 26), opponents' median 19.48.
  - Every game is identical to the artifact (`cgAttribute`, 8,286 turns; cgValidate 60/60 with no
    mismatch; `docs/data/arena-6828013-placement-2026-10-30.json`).
- Replaced 2026-10-30 18:53: agent 6827580 (submission 41600876, created 16:43), v03 upload 1, the same
  artifact: placement 23.25, rank 240/2290, 37 wins of 60 against opponents with a median of 23.07; every
  game identical to the artifact (10,194 turns; `docs/data/arena-6827580-placement-2026-10-30.json`); saved
  before the Submit (`docs/data/arena-6827580-presubmit-2026-10-30.json`).
- Replaced 2026-10-30 16:43: agent 6827094 (submission 41599649, created 13:12), v02 upload 2, artifact
  `527244968dbe…`: placement 18.40, rank 715/2289, 36 wins of 60 by CG ranks; every game identical to the
  artifact (7,084 turns; `docs/data/arena-6827094-placement-2026-10-30.json`); saved before the Submit
  (`docs/data/arena-6827094-presubmit-2026-10-30.json`).
- Replaced 2026-10-30 13:12: agent 6826276 (submission 41598763, created 09:58), v02 upload 1, the same
  artifact: placement 17.23, rank 891/2289, 37 wins of 60; every game identical to the artifact (5,983
  turns; `docs/data/arena-6826276-placement-2026-10-30.json`); saved before the Submit
  (`docs/data/arena-6826276-presubmit-2026-10-30.json`).
- Replaced 2026-10-30 09:58: agent 6824680 (submission 41596829, created 2026-10-29 22:50), v01 upload 3,
  the marked artifact `1e5d1a79…` (build `e5417a83`, config `4194117d`, commit 2641aa9). Placement
  complete 2026-10-30 00:47: score 18.98, rank 654/2294, 60 games, 36 wins. Every game carries the marker
  and is identical to the artifact (`cgAttribute`, 6,222 turns;
  `docs/data/arena-6824680-placement-2026-10-29.json`); saved before the Submit
  (`docs/data/arena-6824680-presubmit-2026-10-30.json`).
- Earlier entries (battle lists empty since replaced): 6823455 (v01 upload 2, 14:03-22:50): placement
  16.08, rank 1067/2294 at 20:51, 31 wins of 60 (3 by opponent timeout on turn 0). 6823071 (v01 upload 1, 08:17-14:03): placement 18.24,
  rank 736/2296, 36 wins of 60. Their games: `docs/data/arena-6823071-games-2026-10-29.json`,
  `docs/data/arena-6823455-games-2026-10-29.json`. 3315948 (old code, not from this project; rank
  559/2295, score 19.64 on 2026-10-28, `docs/data/arena-3315948-presubmit-2026-10-28.json`): the user
  keeps its code from the IDE.
- Policy: P1 since 2026-10-29 for the arena step (`docs/arena-uploads.md`, `tools/arena_compare.py`,
  registry `docs/data/arena-uploads.json`): two uploads per version, decision on the means within a
  league, M = 2 × the SD between uploads of one configuration. Bronze M = 2.92 from v01's three, v02's
  two and v03's two uploads (4 df, provisional: 95 % interval about 1.75-8.4). The local screen before
  an upload is P2 since 2026-10-30 (`docs/local-screen.md`, `tools/bench_pair.py`, ledger
  `docs/data/seed-ledger.json`): fresh 2000-map blocks, the candidate against the control (G1: lower 95 %
  bound > 0.50), against `boss` (G2) and `old` (G3, waiting for the code of 3315948 in
  `refs/old-3315948/Player.java`); targeted fixes need G0, S ≥ 0.500 and Δ ≥ -1 pp against `boss`.
- Selected version and local champion: v03 = v02 with early towers (mines only to income 3, up to 8
  towers), frozen 2026-10-30 (`cr.bot.v03.GreedyBot`, `versions/v03.properties`); `current` defaults to it.
  - From the study `docs/data/study-2026-10-30/`.
  - P2: S 0.797 (screen) and 0.815 (final evaluation) against v02 (`docs/data/exp-2026-10-30-towers/`).
  - P1 verdict on 2026-10-30: not clearly worse than v02. The placement mean is 21.96 against 17.81, and
    the pooled rating 21.75 ± 0.34 against 17.68 ± 0.30.
  - Baseline in Bronze: 23.25 and 20.67.
  - Live: `d6266d41…` (a clean export of 095d1eb rebuilds it byte for byte; kept copy
    `replays/artifacts/d6266d41/Player.java`).
  - Pending strategy candidate: none.
- Previous selected versions: v02 (17.23 and 18.40, mean 17.81; `527244968dbe…`, a clean export of a3e48fc
  rebuilds it; kept copy `replays/artifacts/52724496/Player.java`) and v01 (18.24, 16.08 and 18.98, mean
  17.77; `1e5d1a79…`, commit 2641aa9; kept copy `replays/artifacts/1e5d1a79/Player.java`).
- Bronze boss: "Princess Boss" (arenabossId 175, agentInitialId 1870317, division 219), from the IDE
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. It stands above about
  27.1: on the Bronze leaderboard of 2026-10-30 08:21 the top rows were 27.75 (placing), 27.14, 27.01 and
  27.00, none eligible for promotion (`docs/data/leaderboard-bronze-2026-10-30.json`). v03 is at about 22
  (mean of two placements), so the gap is about 5 points. The exact score needs an IDE game. The local
  `boss` spec is the repository's Default AI: a floor, not this boss.
- Simulator: live rules in `cr.core`, listed in `docs/rules/inferred.md`; corpora `replays/cg` (98) and
  `replays/cg2` (138, Bronze/Silver/Gold). Open questions:
  - the tie-break at equal scores: CG ranked player 0 first in 5/5 games, three of them in this phase's
    placements, while the simulator scores a draw;
  - the survivor's score after an opponent's mid-game timeout, one game off by 1 HP.
- Benches: numbers measured before the live rules (2026-10-28) used the old engine; current numbers
  are in `docs/BOTS.md`.
- Live Bronze statement captured: `docs/rules/bronze.live.html` (differs from the repo text only in
  knight HP 25 and 250 turns).
