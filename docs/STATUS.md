# Status

The current state only; replace lines here, do not append history. Where the rest lives: uploads and
artifacts `docs/BOTS.md`, P1 comparisons and the noise margin `docs/arena-uploads.md`, history and
evidence `docs/DEVLOG.md`, the backlog `docs/PLAN.md`, a short Russian summary for the user
`docs/SUMMARY.md`. The history this page held until 2026-10-30: `git show 60f0b59:docs/STATUS.md`.

- Date: 2026-11-11. Phase 3 (strength). Game: Code Royale, permanent multiplayer arena (`code-royale`),
  no deadline. League: **Gold** since 2026-11-07 (CG promoted v11 upload 2 after its Silver placement, 21.74, rank
  1/200; CG's Gold agent 6857463 was made at 03:48). PV3 and PV4 lapsed with this league change; **PV5**
  (2026-11-07, the user's decision on `docs/data/study-2026-11-07-gold/`) renews the proxy for Gold with the panel and
  gates unchanged, so P2 candidates can be promoted again. v11's Gold baseline:
  18.13 and 16.03 (ranks 119 and 256 of 351), mean 17.08, performance 16.92 ± 0.50 (Gold's scale 3.10). Gold's
  verdicts use Bronze's M 2.46 (the user's decision of 2026-11-07); its own, 2.97 on 1 df, is context. Before: Silver from 2026-11-03 23:46 (CG promoted v08 upload 2); the Bronze
  and Silver scores stay in their leagues. PV1/PV2 lapsed with the change to Silver; **PV3** (2026-11-05, the user's decision on the Silver
  study's proposal, `docs/data/study-2026-11-05-silver/`) renews the proxy for Silver. P2's blocks are now
  strided (`screen-pv3` and `evaluation-pv3` in `docs/data/seed-ledger.json`, which holds the next blocks), so every
  block plays all 16 starting HPs. Giants and the late game of strong economies are recorded as
  unrepresented, and candidates that depend on them are judged by the arena (`docs/local-screen.md`, PV3).
  **PV4** (2026-11-06, the user's decision) adds the rival R0 as gate G5 (spec `rival`). P2's gates are
  G0-G5, kept for Gold by PV5 (2026-11-07).
- Local champion and `current` defaults: **v15** = v14 with the ladder's tower sites chosen beside the towers that
  stand (SITE_NEAR=50: from the second tower on, the free site with the smallest distance to the ladder's point plus
  half its distance to the second-nearest own tower, the nearest while one stands; the first tower, the mines, the
  barracks, the touch rule and the defence rule's towers as in v14), frozen 2026-11-11 by the user's decision of
  2026-11-10 ("Да, v15"; `cr.bot.v15.GreedyBot`, `versions/v15.properties`; `MainBotTest.withDefaultsCurrentPlaysAsV15`).
  P2 strategy screen on PV3 screen block 8: S 0.5425 (CI 0.5292..0.5558) against v14, boss delta 0.0000, field +0.0081,
  rival +0.0324, no candidate failure, G3 skipped (`docs/data/exp-2026-11-10-tower-sites/`). Final evaluation against
  v14 on PV3 evaluation block 4: S 0.5510 (CI 0.5377..0.5643), boss 0.0000, field +0.0039, rival +0.0251, G0 by quiet
  repeats of the direct and the field series (2 and 9 slow turns at 8 threads, none at 2 threads, the same games);
  below the 0.60 recommendation, so an upload is an exception. Artifact `7a5b28c4…` (97,864 chars, build `72a6959d`,
  config `ce9faaa7`; a second build and a clean export of the working tree through a temporary index rebuilt the same
  bytes; kept copy `artifacts/7a5b28c4/Player.java`), procCheck 200/200 against v01 and against `boss`, later turns at
  most 8.6 ms; it plays as v15 and is told from each of the other 15 versions (`docs/BOTS.md`, Artifacts). Commit
  03a16bc, merged into master on 2026-11-11 (a clean export of its staged tree rebuilt the bytes before the commit, and
  `./gradlew submit` in the main checkout rebuilds them from master). Uploaded twice as an exception (2026-11-11:
  agents 6871160 and 6871202, placements 21.87 and 21.05; `docs/arena-uploads.md`). Also the selected version since
  2026-11-11 (below). The next P2 candidates are screened against v15. The artifact holds the
  flags that are off; 2,136 characters of the 100,000 are left.
- Previous local champion: v14 (2026-11-10 to 2026-11-11) = v13 with the queen standing where her towers overlap
  (HOLD_OVERLAP=300: in v13's hold, an enemy knight within 500 and none within 250, and in the flight she takes the spot
  within five steps that the most own towers cover instead of her nearest own tower, a tower's touch point before open
  ground on a tie, v13's command when nothing is better covered; IDLE_OVERLAP=1: idle, she waits at the own tower with
  the most own towers over its centre), frozen 2026-11-10 by the user's decision ("Да, v14"; `cr.bot.v14.GreedyBot`,
  `versions/v14.properties`; `MainBotTest.withV14ParamsCurrentPlaysAsV14`). P2 strategy screen on PV3 screen block 7:
  S 0.5473 (CI 0.5347..0.5598) against v13, boss delta 0.0000, field +0.0161, rival +0.0275, G0 by a quiet repeat, G3
  skipped (`docs/data/exp-2026-11-10-overlap/`). Final evaluation against v13 on PV3 evaluation block 3: S 0.5533
  (CI 0.5404..0.5661), boss 0.0000 on 499 maps, field +0.0151, rival +0.0245, no failure of v14 in 13,000 games; below
  the 0.60 recommendation, so an upload is an exception. Artifact `e4c8eeec…` (95,590 chars, build `62ad918b`, config
  `337e3891`; a second build and a clean export of the working tree through a temporary index (tree d2e1a1b0) rebuilt the same bytes;
  kept copy `artifacts/e4c8eeec/Player.java`), procCheck 200/200 against `boss` and, on the repeat, against v01, later
  turns at most 8.1 ms; the first run against v01 lost one game to the laptop's Modern Standby (a 63.7 s turn;
  `docs/BOTS.md`, Artifacts). Commit 931975a, merged into master on 2026-11-10 (a clean export of its staged tree rebuilt
  the bytes before the commit). Uploaded twice as an exception (2026-11-10: agents 6868933 and 6869279, placements 23.11
  and 22.10; `docs/arena-uploads.md`). Also the selected version since 2026-11-10 (below).
- Previous local champion: v13 (2026-11-09 to 2026-11-10) = v12 with no ladder walk into a visible wave (LADDER_CLEAR=500: after
  the first-wave window and with two own towers, while an enemy knight is within 500 of the queen and none within 250,
  the walk to the ladder's next site becomes a walk to her nearest own tower outside the enemy towers' ranges, a top-up
  when touched), frozen 2026-11-09 by the user's decision ("Заморозим v13"; `cr.bot.v13.GreedyBot`,
  `versions/v13.properties`; `MainBotTest.withV13ParamsCurrentPlaysAsV13`). P2 strategy screen on PV3 screen block 6:
  S 0.5960 (CI 0.5817..0.6103) against v12, boss delta 0.0000, field +0.0510, rival +0.0747, G0 by a quiet repeat, G3
  skipped (`docs/data/exp-2026-11-09-meet-waves/`). Final evaluation against v12 on PV3 evaluation block 2: S 0.5958
  (CI 0.5819..0.6096), boss 0.0000, field +0.0434, rival +0.0661, G0 by a quiet repeat; below the 0.60 recommendation by
  0.004, so an upload is an exception. Artifact `85897d90…` (89,252 chars, build `a19a87ca`, config `0c433665`; a second
  build gave the same bytes; kept copy `artifacts/85897d90/Player.java`), procCheck 200/200 against v01 and against
  `boss`, later turns at most 8.0 ms (`docs/BOTS.md`, Artifacts). Commit 75f4bf9, merged into master on 2026-11-09. Uploaded twice as an exception
  (2026-11-09: agents 6868408 and 6868474, placements 21.95 and 21.65; `docs/arena-uploads.md`). Also the selected
  version 2026-11-09 to 2026-11-10 (below).
- Previous local champion: v12 (2026-11-07 to 2026-11-09) = v11 with the ladder's tower targets kept back in the first wave
  (WAVE_ANCHOR=50, WAVE_UNTIL=30: until turn 30, while an enemy knight barracks or knight is out, from the second tower
  on, the free site nearest the point at the queen's height halfway back to her edge), frozen 2026-11-07 by the user's
  decision ("Да, v12"; `cr.bot.v12.GreedyBot`, `versions/v12.properties`; `MainBotTest.withV12ParamsCurrentPlaysAsV12`).
  P2 strategy screen on PV3 screen block 5: S 0.5654 (CI 0.5523..0.5784) against v11, boss delta 0.0000, field −0.0149,
  rival +0.0135, no candidate failure, G3 skipped (`docs/data/exp-2026-11-07-meet-back/`). Final evaluation against
  v11 on PV3 evaluation block 1: S 0.5716 (CI 0.5587..0.5845), boss 0.0000, field −0.0123, rival +0.0163, no failure;
  below the 0.60 recommendation. Artifact `3208b011…` (88,517 chars, build `58d513ae`, config `7b8e0525`; kept copy
  `artifacts/3208b011/Player.java`), procCheck 200/200 against v01 and against `boss`, later turns at most 7.7 ms
  (`docs/BOTS.md`, Artifacts); commit 08fbc49, merged into master. Also the selected version 2026-11-07 to 2026-11-09 (below).
- Previous local champion: v11 (2026-11-07) = v10 with the ladder's sites on the enemy half only while the
  straight walk to them stays out of every enemy tower's range (SAFE_LADDER=1), frozen 2026-11-07 by the user's decision
  ("the default"; `cr.bot.v11.GreedyBot`, `versions/v11.properties`; `MainBotTest.withV11ParamsCurrentPlaysAsV11`). P2 as
  a targeted fix on PV3 screen block 4 (`docs/data/exp-2026-11-07-safe-ladder/`). Also the selected version since
  2026-11-07 (below).
- Previous local champion: v10 (2026-11-06 to 2026-11-07) = v09 with the second knight barracks at the centre of the board
  (BARRACKS_FORWARD=50), frozen 2026-11-06 by the user's decision ("the default"; `cr.bot.v10.GreedyBot`,
  `versions/v10.properties`; `MainBotTest.withV10ParamsCurrentPlaysAsV10`). P2 screen on PV3 block 3: S 0.5426 against
  v09, +3.09 pp against R0 (`docs/data/exp-2026-11-06-waves/`). The selected version on 2026-11-07, until v11.
- Previous local champion: v09 (2026-11-05 to 2026-11-06) = v08 with the late hold (LATE_HOLD=200: from turn 200, ahead on
  HP, no walk to the ladder's next site), frozen 2026-11-05 by the user's decision ("the default without an
  upload"; `cr.bot.v09.GreedyBot`, `versions/v09.properties`; `MainBotTest.withV09ParamsCurrentPlaysAsV09`). P2
  screen on PV3 block 0: S 0.5269 against v08, +1.46 pp against `field` (`docs/data/exp-2026-11-05-late-hold/`).
  Not uploaded, no final evaluation, no artifact checked.
- Selected version (the arena's): **v15** since 2026-11-11, by P1 on two Gold placements, 21.87 and 21.05 (mean 21.46,
  SD 0.58), against v14's Gold 23.11 and 22.10 (mean 22.61): "not clearly worse" (−1.14 against −M −2.46; performance
  21.31 ± 0.54 against 22.48 ± 0.54 at Gold's refitted scale 3.25, −1.17 against −2 SE −1.54; `docs/arena-uploads.md`).
  **The arena does not show the local gain**: both differences are negative, inside the margin and about 1.5 SE of
  the rating difference. v15 won 121 of its 180 games (v14 120) and lost them the same way (25 deaths and 34 lost
  races against 26 and 34), against a weaker field (opponents' medians 19.98 and 17.74 against 21.18 and 19.79).
  Uploaded as an exception (S 0.5510 against v14 on the evaluation block). Artifact `7a5b28c4…` (97,864 chars, build
  `72a6959d`, config `ce9faaa7`; commit 03a16bc; kept copy `artifacts/7a5b28c4/Player.java`). The rollback artifact.
  The live agent is 6871202 (upload 2; the arena entry, below). Its code was identical to the artifact in all 180
  games. On the arena the rule does to the board what it does locally (the third tower is built with 1.55 own towers
  within 400, v14 1.13; she stands under three by turn 60 in 72 % of the games, v14 62 %; the ladder is no slower), but
  the share of her contact turns under three or more did not move (53.5 % against 53.7 %; locally +8 to +13 points)
  and the HP it saves, 1.1 a game, is not established; two thirds of the knights' damage falls before turn 50 or
  where two towers or fewer stand, outside the rule's reach (v15 in Gold, below).
- Previous selected version: v14 (2026-11-10 to 2026-11-11), by P1 on two Gold placements, 23.11 and 22.10 (mean 22.61,
  SD 0.71), against v13's Gold 21.95 and 21.65 (mean 21.80): "not clearly worse" (+0.81 against −M −2.46; performance
  22.34 ± 0.51 against 21.84 ± 0.48 at Gold's scale of that day, 3.00, +0.50 against −2 SE −1.39;
  `docs/arena-uploads.md`). Uploaded as an exception (S 0.5533 against v13 on the evaluation block). Artifact
  `e4c8eeec…` (95,590 chars, build `62ad918b`, config `337e3891`; commit 931975a; kept copy
  `artifacts/e4c8eeec/Player.java`), the rollback artifact until v15's selection. Agents 6868933 and 6869279; its code
  was identical to the artifact in all 180 games. On the arena the rule put the queen under three or more own towers on 54 % of her contact turns (v13 39 %);
  the HP it saves, 3.2 a game, is not established (v14 in Gold, below).
- Previous selected version: v13 (2026-11-09 to 2026-11-10), by P1 on two Gold placements, 21.95 and 21.65 (mean 21.80,
  SD 0.21), against v12's Gold 19.64 and 21.07 (mean 20.36): "not clearly worse" (+1.44 against −M −2.46; performance
  21.75 ± 0.43 against 20.27 ± 0.45 at Gold's refitted scale 2.71, +1.48 against −2 SE −1.23; `docs/arena-uploads.md`).
  Uploaded as an exception (S 0.5958 against v12 on the evaluation block). Artifact `85897d90…` (89,252 chars, build
  `a19a87ca`, config `0c433665`; commit 75f4bf9; kept copy `artifacts/85897d90/Player.java`); kept.
- Previous selected version: v12 (2026-11-07 to 2026-11-09), by P1 on two Gold placements, 19.64 and 21.07 (mean 20.36,
  SD 1.01), against v11's Gold 18.13 and 16.03 (mean 17.08): "not clearly worse" (+3.28 against −M −2.46; performance
  20.38 ± 0.48 against 16.89 ± 0.49 at Gold's scale 3.04, +3.48 against −2 SE −1.38; `docs/arena-uploads.md`). Uploaded
  as an exception (S 0.5716 against v11 on the evaluation block). Artifact `3208b011…` (88,517 chars, build `58d513ae`,
  config `7b8e0525`; commit 08fbc49; kept copy `artifacts/3208b011/Player.java`). On the arena the
  first wave is met 287 from the back edge (v11 411) and costs 6.9 HP (v11 11.4). A third Gold placement, the paste by
  mistake of 2026-11-10 02:23 in place of v14's file: agent 6868868, 20.88 (the mean of three 20.53;
  `docs/arena-uploads.md`; `tools/arena.py follow` has found a new label's file by its play since that day).
- Previous selected version: v11 (2026-11-07), by P1 on two Silver placements, 20.80 and 21.74 (mean
  21.27, SD 0.66), against v10's 19.38 and 21.33 (mean 20.35): "not clearly worse" (+0.92 against −M −2.46;
  performance 22.02 ± 0.77 against 21.04 ± 0.77 at Silver's scale 4.41, +0.98 against −2 SE −2.17;
  `docs/arena-uploads.md`). Uploaded as an exception (a targeted fix, S 0.5030 against v10). Artifact `cf71d566…`
  (86,973 chars, build `cf3b2e46`, config `bc23e79d`; commit 1dd24ba; kept copy `artifacts/cf71d566/Player.java`),
  procCheck 200/200 against v01 and against `boss` (`docs/BOTS.md`, Artifacts); kept. Upload 2 was promoted to Gold;
  v11's Gold baseline: 18.13 and 16.03, mean 17.08.
- Previous selected version: v10 (2026-11-07), by P1 on two Silver placements, 19.38 and 21.33 (mean
  20.35, SD 1.38), against v08's 20.70, 20.74 and 20.21 (mean 20.55): "not clearly worse" (−0.20 against −M −2.46;
  performance 20.56 ± 0.65 against 20.64 ± 0.50, −0.08 against −2 SE −1.63; `docs/arena-uploads.md`). Uploaded as an
  exception (S 0.5764 against v08 on the evaluation block, below 0.60). Artifact `21665009…` (86,080 chars, build
  `8c3b0d9a`, config `ca7e6562`; commit bac2788; kept copy `artifacts/21665009/Player.java`), procCheck 200/200
  against v01 and against `boss` (`docs/BOTS.md`, Artifacts); kept.
- Previous selected version: v08 = v07 with the kiting bound to the towers
  (KITE=2, KITE_PULL=100), frozen 2026-11-03 (`cr.bot.v08.GreedyBot`, `versions/v08.properties`; P2
  `docs/data/exp-2026-11-03-kite2/`: S 0.6670 against v07 and +14.8 pp against `field` on the evaluation
  block). Selected under P1 on 2026-11-03 by two placements (mean 27.99 against v07's 25.53, "not clearly
  worse", +2.46 against −M −2.46; `docs/arena-uploads.md`); promoted to Silver after upload 2. Silver
  baseline (2026-11-04): placements 20.70, 20.74 and 20.21, mean 20.55, pooled rating 20.40 ± 0.42 (250 games). Artifact `5c16d925…` (79,348 chars, build `3f19aa6e`, config `c9dad766`; the freeze commit; kept copy
  `artifacts/5c16d925/Player.java`), procCheck 200/200 against v01 and against `boss`, no timeouts
  (`docs/BOTS.md`, Artifacts); kept, like v07's `4c7b0d04`.
- Previous selected version: v07 = v06 with kiting under knights (KITE=1 at
  the contact range 250, under own tower cover), frozen 2026-11-03 (`cr.bot.v07.GreedyBot`,
  `versions/v07.properties`; P2 `docs/data/exp-2026-11-03-kite/`: S 0.6803 against v06 and +3.5 pp against
  `field` on the evaluation block). Selected under P1 on 2026-11-03 by two placements (mean 25.53 against
  v06's 26.24, "not clearly worse": −0.71 against −M −2.58; the local +1.5 did not show on the arena,
  `docs/arena-uploads.md`). Artifact `4c7b0d04…` (78,622 chars without comments, build `b6b5f0f7`, config
  `c1fcf458`; commit 8c1e43d; kept copy `artifacts/4c7b0d04/Player.java`), procCheck 200/200 against v01 and
  against `boss`, no timeouts (`docs/BOTS.md`, Artifacts). The rollback artifact for the next candidate is
  `4c7b0d04`; v06's `1a9f3e81` stays kept.
- Previous selected version: v06 = v05 with a second tower near the queen
  under threat (THREAT_TOWERS_NEAR=2 within 500) and the army flags (a second knight barracks after 4 towers,
  synchronous waves, 6 towers), frozen 2026-11-03 (`cr.bot.v06.GreedyBot`, `versions/v06.properties`; P2
  `docs/data/exp-2026-11-03-safety/`: S 0.6519 against v05 on the evaluation block). Selected under P1 on
  2026-11-03 by two placements (mean 26.24 against v05's 25.39, "not clearly worse"; `docs/arena-uploads.md`).
  Artifact `1a9f3e81…` (97,797 chars, build `0bf54983`, config `0ff55c62`; commit 5034ca7; kept copy
  `artifacts/1a9f3e81/Player.java`), procCheck 200/200 against v01 and against `boss`, no timeouts
  (`docs/BOTS.md`, Artifacts). The rollback artifact for the next candidate is `1a9f3e81`.
- Previous selected version: v05 = v04 with two mines before the knight barracks (MINES_FIRST=2),
  frozen 2026-11-03 (`cr.bot.v05.GreedyBot`, `versions/v05.properties`). Selected
  under P1 on 2026-11-03 by two placements (mean 25.20 against `route`'s 22.50, "not clearly worse";
  `docs/arena-uploads.md`); artifact `13b3ec0c…` (commit 496bcc5, kept in `artifacts/13b3ec0c/`;
  `docs/BOTS.md`, Artifacts). The rollback artifact for the next candidate is `13b3ec0c`. Previous: v04 (the
  `route` configuration, artifact `da914c4c…`), selected 2026-11-02 to 2026-11-03; v03, 2026-10-30 to 2026-11-02.
- Arena entry: `aabzac`, agent 6871202 = **v15 upload 2**, **Gold**, pasted 2026-11-11 01:18 local: the main checkout's
  `build/submit/Player.java`, byte for byte the kept copy `artifacts/7a5b28c4/Player.java`, named by `tools/arena.py
  follow v15 --selected v14`. The arena entry is the selected version.
  - The placement completed at 01:38: score 21.05, local rank 34/351 (global 148), `eligibleForPromotion` false.
  - 90 games, 63 wins; opponents' median 17.74. Every game is identical to the artifact (`docs/BOTS.md`, Uploads;
    `docs/data/arena-6871202-placement-2026-11-11.json`).
  - It replaced agent 6871160 = v15 upload 1 (21.87, rank 19/351; 00:54), which replaced 6869279 = v14 upload 2 (22.10,
    rank 18/349). v14 upload 1's 23.11 (rank 9/348) is the best Gold placement so far.
- v11 in Gold (the study of 2026-11-07, `docs/data/study-2026-11-07-gold/`; its README has the numbers):
  - 44 of the 69 losses in 180 games are HP races at turn 250, 25 are deaths (towers 0.4 HP of a death's last 30
    turns);
  - the races are decided at the first wave, turn 24-25: in those we lose it costs 12.5 HP against the enemy's 5.1,
    in those we win 5.9 against 8.7, with the same towers (1.6 and 1.7 on average), wave and queen behaviour;
  - giants come in 17 % of the games and do not mark our losses; v11 scores its expectation by opponent group;
  - the local panel against the field: the mirror meets the first wave as in Gold (turn 24, one tower) and goes to the
    limit in 91 % (Gold 59 %); `field` 60 %; `rival`'s giants 85 % of its games.
- Noise margins: **M = 2.46, borrowed from Bronze** (11 df) in Silver (the user's decision of 2026-11-04) and in
  **Gold** (2026-11-07), until they decide otherwise. The leagues' own M are printed as context: Silver 1.59 on 4 df
  (v08's, v10's and v11's Silver placements), Gold 1.69 on 6 df (v11's pair, v12's three and the pairs of v13, v14 and v15). See `noiseMargins` in the registry and
  `docs/arena-uploads.md` (Silver and Gold noise margins). A Gold candidate rolls back early below 16.53 (v15's Gold
  mean 21.46 less 2M = 4.93).
- Silver boss: agent 1876720 (no pseudo), rating **21.87-21.99** during the fetches of 2026-11-04 00:05-23:24
  (the games manifests of v08 uploads 3-5), 21.84 falling to 21.54 during v10's second placement (2026-11-07
  01:05-01:22), 21.96 when the user looked on 2026-11-07. v08 won 6 of its 16 games against it, v10 all 4 (and
  about 9 of 10 IDE games, the user's count), v11 5 of 10 (its score 21.38-21.74 at those fetches); v10's mean 20.35 is about 1.2-1.6 below it. The strongest other
  player met: mesavitae, 21.12-21.35. About half of v08's Silver placement games (40, 40 and 38 of 80) went to
  turn 250.
- How v08 plays in Silver (the study of 2026-11-05, `docs/data/study-2026-11-05-silver/`; its README has the
  numbers):
  - most HP races are decided at the enemy's first wave (turn 23-28), which our second tower (turn 25) often
    misses, and the boss games above all;
  - 10 of 34 lost races were led after turn 200;
  - giant players beat us about 2 rating points more than their ratings predict (p 0.03 with Silver's own
    logistic scale, 3.16);
  - the strongest four out-produce us (income 6 against 3, waves 10-13.5 against 6-7).
  Candidates from it (2026-11-05):
  - a second tower before the first wave lost on the development seeds (`WAVE_TOWERS`, `TOPUP_FROM`, off;
    `docs/data/exp-2026-11-05-first-wave/`);
  - holding an HP lead late passed the P2 screen on PV3 block 0 (`docs/data/exp-2026-11-05-late-hold/`).
- v09's screen (P2, 2026-11-05, `current?LATE_HOLD=200` before the freeze): S 0.5269 (CI 0.5208-0.5329)
  against v08, Δ 0.0000 against `boss`, Δ +0.0146 against `field`, G0 by quiet repeats (`-Pthreads=2` for the
  in-process repeats on this laptop's hybrid cores), G3 skipped. S < 0.60, so no upload recommendation.
- Candidate judged under P1 (2026-11-07): v11. Placements 20.80 and 21.74 (mean 21.27, SD 0.66) against v10's 20.35;
  performance 22.02 ± 0.77 against 21.04 ± 0.77 (scale 4.41, seven uploads); M 2.46 (borrowed). Neither rollback
  condition holds (+0.92 against −2.46; +0.98 against −2.17), so v11 is the selected version. Over its 160 games its
  queen lost 0.6 ± 0.3 HP a game to enemy towers (v10: 5.1 ± 1.2), and none of its 32 deaths took 10+ HP from them.
  Upload 2 was promoted to Gold. No pending candidate.
- Candidate judged under P1 (2026-11-07): v10. Placements 19.38 and 21.33 (mean 20.35, SD 1.38) against v08's
  20.55; performance 20.56 ± 0.65 against 20.64 ± 0.50 (Silver scale 3.68, fitted on the five uploads); M 2.46
  (borrowed). Neither rollback condition holds (−0.20 against −2.46; −0.08 against −1.63), so v10 is the selected
  version. P2's +1.0 head-to-head is inside the arena's noise: neither confirmed nor refuted. No pending candidate.
- Candidate judged under P1 (2026-11-03): v08. Placements 27.85 and 28.13 (mean 27.99, SD 0.20) against
  v07's 25.53; pooled rating 27.59 ± 0.50 against 25.42 ± 0.46 (scale 2.13); M 2.46 (11 df). Neither
  rollback condition holds (+2.46 against −2.46; +2.17 against −1.36), so v08 is the selected version. The
  arena confirms the local direction and size (P2 +1.4 head-to-head). No pending candidate.
- Candidate judged under P1 (2026-11-03): v07. Placements 26.42 and 24.64 (mean 25.53, SD 1.26) against
  v06's 26.24; pooled rating 25.34 ± 0.43 against 25.76 ± 0.46 (scale 1.97); M 2.58 (10 df). Neither
  rollback condition holds (−0.71 against −2.58; −0.41 against −1.26), so v07 is the selected version by
  the policy. The arena does not confirm the local gain (P2 +1.5 head-to-head): the first such disagreement,
  noted under PV2 in `docs/arena-uploads.md`. No pending candidate.
- Why (2026-11-03, `docs/data/study-2026-11-03-v07-arena/`): on the arena the kiting halves the HP lost per
  contact turn (0.9 and 0.3 against v06's 1.2 and 0.9) but the queen leaves her towers' cover (34 % of the
  contact turns near a tower against 90 %), the knights chase her for 50-130 turns without dying, the
  top-ups halve (36 against 73 per game; 4 towers at 2,364 HP against 5 at 3,180) and the kills of the enemy
  queen fall from 68 to 56.
- Candidate frozen as v08 (2026-11-03, kiting bound to the towers, the user's choice after the v07 study):
  `current?KITE=2,KITE_PULL=100` — every kiting step within an own tower's range, the pull to the nearest
  tower weighed like the distance from the knights (`MainBot.kite`, `docs/data/exp-2026-11-03-kite2/`). The
  queen stays near her towers 87 % of the contact turns locally (v07's rule 39 %). P2 screen on block 9
  (218000-219999) passed: S 0.6663 (CI 0.6516-0.6809) against v07, Δ +0.0010 against `boss`, Δ +0.1369
  against `field`, G0 on the quiet repeat; the local champion. Final evaluation on evaluation block 7
  (414000-415999), the user's decision: G0 on the repeats, S 0.6670 (CI 0.6522-0.6818) against v07, Δ 0.0000
  against `boss`, Δ +0.1484 against `field`; the upload recommendation S ≥ 0.60 is met. The local proxy
  over-rewarded v07's kiting, so the arena decides this one. Frozen as v08 on the user's decision; `submit`
  and both `procCheck` series: `docs/BOTS.md` (Artifacts).
- Candidate judged under P1 (2026-11-03): v06. Placements 27.09 and 25.39 (mean 26.24, SD 1.20) against
  v05's 25.39; pooled rating 25.75 ± 0.46 against 25.06 ± 0.35 (scale 1.95); M 2.59 (9 df). Neither
  rollback condition holds, so v06 is the selected version. The arena agrees with the local direction and
  size (P2: S 0.65 against v05, about +1.1 head-to-head; the arena +0.85 on the mean). No pending candidate.
- Late economy (2026-11-03, after v06's selection; `docs/data/exp-2026-11-03-late-income/`): the profile of
  v06's 120 arena games (87 wins; 24 deaths, 3 before turn 80; the opponents who beat it hold income 8 and 12
  waves against our 3 and 6, while our queen spends 74 turns on top-ups) led to `INCOME_LATE` (the income
  target after the ladder, upgrades and new mines out of the top-up turns). Development sweep against v06:
  4-6 → 0.505-0.513, 8 → 0.485, with TOWER_HP 500-600 → 0.36-0.43. Neutral locally, not screened, off by
  default. The economy's value against the arena field needs a local opponent with the field's balance.
- Field-balance opponent (2026-11-03, the user's choice; `docs/data/exp-2026-11-03-field-opponent/`): `field`
  (`cr.bot.field.FieldBot`, income 7-8 with 3 mines, 4 towers at 600, 17 knight waves; giants optional, off:
  they cost 20 pp). S 0.255 against v06, 0.735 against v03, 60/60 against `boss`. Against it our parked flags
  lose more than against v06 (INCOME_LATE −9..−12 pp, TOWER_HP=500 −7, MAX_TOWERS=4 −6, the defence off −9).
  Adopted into P2 as gate G4 (Δ against `field` on the block's 2000 maps ≥ −2 pp; PV2, `docs/local-screen.md`),
  the user's decision 2026-11-03.
  `cr.tools.LocalProfile` prints a bot's local profile in cgStudy's terms.
- v07's local record (2026-11-03, kiting; the user's choice after the field opponent): `current?KITE=1` — under
  knights within 250 the queen MOVEs away from them under her towers' cover instead of standing at a tower
  topping it up. Study `docs/data/study-2026-11-03-arena-winners/` (the arena winners over v06 MOVE on 42 % of
  their turns under knights and lose a third less HP per contact turn; v06 never moves). P2 screen on block 8
  (216000-217999) passed with G4: S 0.6946 (CI 0.6784-0.7109) against v06, Δ +0.0015 against `boss`, Δ +0.0309
  against `field`, G0 clean (`docs/data/exp-2026-11-03-kite/`); the local champion, about +1.6 rating points
  head-to-head. Final evaluation on evaluation block 6 (412000-413999), the user's decision: G0 on the quiet
  repeat (4 slow turns on consecutive maps in the first run were host noise), G1 S 0.6803 (CI 0.6641-0.6964)
  against v06, Δ +0.0010 against `boss`, Δ +0.0352 against `field`; the upload recommendation S ≥ 0.60 is met.
  Frozen as v07 on the user's decision; `submit` and both `procCheck` series: `docs/BOTS.md` (Artifacts).
  Dropped in the sweep: KITE_RANGE 500
  (−12 pp against `field`). The artifact merger strips comments since 2026-11-03 (the code had grown past
  100,000 characters with them; about 79,000 without).
- Candidate judged under P1 (2026-11-03): v05. Placements 23.93 and 26.47 (mean 25.20, SD 1.80) against
  `route`'s 22.50; pooled rating 24.90 ± 0.37 against 22.05 ± 0.35 (scale 1.61); M 2.77 (7 df). Neither
  rollback condition holds, so v05 is the selected version. With the third upload (25.77) the verdict
  stands: mean +2.89 against −M −2.61, rating +2.87 against −2 SE −0.97. The arena agrees with the local direction
  (P2: S 0.76 against v04, about +1.7 head-to-head) and exceeds its size. The earlier
  judgements (`route` 2026-11-02, v03 2026-10-30) are in `docs/arena-uploads.md`.
- Deferred candidate (2026-11-03, the army direction of `docs/bot-architecture.html`):
  `current?BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6` (a second knight barracks once 4
  towers stand, both released together, 6 towers). P2 screen on block 6 (212000-213999) passed: S 0.5473
  (CI 0.5331-0.5614) against v05, Δ 0.0000 against `boss` (its games end before the flags act), G0 on the
  quiet repeat, G3 skipped (`docs/data/exp-2026-11-03-army/`); the local champion by the policy. About +0.3
  rating points head-to-head, below the upload recommendation (S ≥ 0.60). Final evaluation on evaluation
  block 4 (408000-409999), the user's decision: G0-G2 pass, S 0.5480 (CI 0.5327-0.5633) against v05, Δ
  −0.0010 against `boss`, no failure; S < 0.60, so the upload recommendation is not met and an upload would
  be the user's exception. The user's decision (2026-11-03, evening): deferred, no upload, the flags stay in
  the code off by default (the recommendation: +0.3 is inside the arena's noise, M 2.61, and the arena cannot
  measure it; later it may join a safety candidate to reach 0.60). The local champion stays v05. Flags
  tried and dropped in the sweep: an early or late second barracks, a third barracks, the forward placement.
- v06's local record (2026-11-03, the queen's safety, the user's choice after the army):
  `current?THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500,BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6`
  (under threat a second tower within 500 of the queen, plus the deferred army flags). Study
  `docs/data/study-2026-11-03-queen-safety/` (1029 of 1103 hit turns in v05's arena deaths spent at one tower
  topping it up; `cr.tools.DeathTrace`). P2 screen on block 7 (214000-215999) passed: S 0.6420 (CI
  0.6261-0.6579) against v05, Δ +0.0030 against `boss`, G0 clean, G3 skipped
  (`docs/data/exp-2026-11-03-safety/`); the local champion. Final evaluation on evaluation block 5
  (410000-411999), the user's decision: G0-G2 pass, S 0.6519 (CI 0.6356-0.6682) against v05, Δ 0.0000 against
  `boss`, no failure; the upload recommendation S ≥ 0.60 is met (about +1.1 rating points head-to-head).
  Frozen as v06 on the user's decision; `submit` and both `procCheck` series: `docs/BOTS.md` (Artifacts). The safety flag alone: 0.635 and 0.605 on the two development blocks (not screened).
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
  session 2026-10-28; its battles are not listed by `findLastBattlesByAgentId`. First measured 2026-11-03:
  v06's placement games include three against agent 1870318 (no pseudo), rating **27.63** at the fetch
  (`docs/data/arena-6842260-games-2026-11-03.json`), all lost; on 2026-11-03 23:00 its rating was 28.08 falling
  to 27.90 during v08's placement (`docs/data/arena-6843773-games-2026-11-03.json`), and v08 won 3 of its 7
  games against it; v08's first placement 27.85 is 0.05 below it, v07's mean 25.53 about 2.4 below. Earlier bound: above about 27.1 (the Bronze leaderboard of
  2026-10-30, `docs/data/leaderboard-bronze-2026-10-30.json`). The local `boss` spec is the repository's Default AI: a floor, not this boss.
- Rival opponent (2026-11-06, `docs/data/exp-2026-11-06-rival/`):
  R0 = `field?KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2`, the closest the ladder
  gets to the strongest four Silver players.
  - v09 scores 0.703 against it (`field` 0.93). Against R0 the versions v05-v09 score 0.27, 0.37, 0.41, 0.65 and
    0.70, the Bronze arena's order of steps.
  - Gate G5 of P2 since PV4 (2026-11-06; spec `rival`, `docs/local-screen.md`).
- Income candidate (2026-11-06, `docs/data/exp-2026-11-06-income/`): the late income gains 1-2 pp against R0 and
  loses the v09 mirror (our half's sites are towers when the ladder ends, and the walks to far mines kill the
  queen). Five towers with the late income and no walk while enemy knights are within 1000
  (`current?MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=1000`) failed the P2 screen on PV3 screen block 1: S 0.5016
  (CI 0.4898-0.5135) against v09, G1 fails; +1.79 pp against R0, +0.31 pp against `field`, G2 not established (boss timeouts on 48 of the 500 maps of the candidate's series, none in v09's). Our own
  giants lose 21 pp against R0. All new flags stay off; v09 stays the local champion.
- Giants answer (2026-11-06, `docs/data/exp-2026-11-06-giants/`): R0's giants knock down 3.8 of v09's towers a game
  and double its queen's deaths. Topping up the attacked tower (`GIANT_GUARD`) loses 1.8-3.3 pp against R0.
  Archers (`ARCHERS=1,ARCHER_RANGE=1000`) gained +3.05 pp on the development seeds. On PV3 screen block 2 they are
  +0.36 pp (CI -0.88..+1.60) against R0 and -0.99 pp against `field?GIANTS=1`, with v09's games against v09 and
  `field` (a targeted fix by P2's letter), boss delta 0.0000 with v09's games (passes the targeted fix's -1 pp). Not adopted by the user's decision, against P2's letter (a passing targeted fix joins
  the local champion): no gain on fresh seeds; the flags stay off.
- Knight waves (2026-11-06, `docs/data/exp-2026-11-06-waves/`): holding a ready wave until the enemy queen comes
  near our barracks loses 6.9-19.8 pp against R0 (the queen almost never leaves her towers' ranges, and a held wave
  takes half the HP). The second knight barracks at the centre (`current?BARRACKS_FORWARD=50`) on PV3 screen
  block 3: S 0.5426 (CI 0.5293-0.5560) against v09, +3.09 pp against R0, +0.81 pp against `field`, G2 boss delta 0.0000 (1.000 against 1.000),
  G0 by repeats (the 2-thread repeats held the same 12000 games, and the 8 maps still slow there, repeated one at a time in 1 thread, were identical and clean); passes the P2 screen; frozen as v10, the default (the user's decision).
- v10's final evaluation (2026-11-06, PV3 evaluation block 0): S 0.5764 (CI 0.5626-0.5902) against v08; G2 passes: boss delta 0.0000 (1.000 against 1.000); G4 passes: field delta +0.0149 (CI +0.0063..+0.0234); G5 passes: rival delta +0.0843 (CI +0.0683..+0.1002); G0 by quiet repeats: v10's three series repeated with 2 threads held the same 12000 games without a slow turn. G0-G2, G4 and G5 pass (G3 skipped): v10 passes the final evaluation against v08; S < 0.60, so no upload recommendation (an upload would be the user's
  exception).
- v10's artifact (2026-11-07): `21665009…`, 86,080 characters, build `8c3b0d9a`, config `ca7e6562`; procCheck vs v01 100 maps @5000: 200/200 identical to current, 199 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 333.3 ms, later 3.5 ms; vs boss 100 maps @6000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 341.1 ms, later 7.1 ms (logs `replays/bench/exp-2026-11-06-waves-eval/proccheck-v10-*.log`).
  The kept copy is `artifacts/21665009/Player.java`. Committed and merged into master (bac2788; a clean export
  rebuilds the bytes); uploaded 2026-11-07 as v10 uploads 1 and 2 (upload 2 is the arena entry above).
- v10's Silver games (the study of 2026-11-07, `docs/data/study-2026-11-07-tower-fire/`; its README has the numbers):
  - against opponents below 19 it wins about what its rating predicts (72 % against 75 %; v08 77 % against 72 %),
    against those at 21 and up and the boss 10 of 15 where the model expects 43 % (v08 12 of 41);
  - 24 of its 31 losses to opponents below 19 are deaths (median turn 142) against opponents with income 6 at turn
    100 to our 3 and knights on the board, as in v08's losses;
  - its queen loses 5.1 ± 1.2 HP a game to enemy towers (v08 3.2 ± 0.7), mostly on walks to BUILD TOWER and BUILD
    MINE targets with no enemy knight within 250; 12 of its 43 deaths took 10+ HP from towers in their last 30 turns.
- SAFE_LADDER (2026-11-07, the user's "Да, начинай"; `docs/data/exp-2026-11-07-safe-ladder/`): a site on the enemy
  half is a ladder target only while the straight walk to it stays out of every enemy tower's range.
  - P2 on PV3 screen block 4 as a targeted fix: 148 of 2000 maps changed against v10's mirror, S 0.5030 (CI
    0.5000-0.5060), boss delta 0.0000, field 0.0000, rival +0.0013, G0 by the quiet repeat. It passes; the default
    since v11 (the user's decision).
  - Locally the defect is rare (0.3-0.9 HP a game to enemy towers, 5.1 on the arena). On v10's 160 arena games the
    flag changes a decision in 31: 20 losses with 17 queen deaths and 667 tower HP after the change, 11 wins.
- v11's artifact (2026-11-07): `cf71d566…`, 86,973 characters, build `cf3b2e46`, config `bc23e79d`; procCheck vs v01 100 maps @5000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 372.0 ms, later 7.6 ms; vs boss 100 maps @6000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 370.6 ms, later 4.8 ms.
  A second build and a clean export of the working tree rebuilt the same bytes. Committed and merged into master on
  2026-11-07; the kept copy is `artifacts/cf71d566/Player.java` in the main checkout. Uploaded 2026-11-07 as v11
  uploads 1 and 2 (upload 2 is the arena entry above).
- The second tower before the first wave, retried over v11 (2026-11-07, `docs/data/exp-2026-11-07-first-wave-gold/`):
  `WAVE_TOWERS=2` and `TOPUP_FROM=2` score 0.45 against v11 on the development seeds and lose 2.5-7 pp against
  `field` and `rival`. Rejected before a screen; the flags stay off.
- v13 in Gold (the study of 2026-11-09, `docs/data/study-2026-11-09-v13-gold/`; its README has the numbers):
  - 73 losses in 180 games: 39 HP races and 34 deaths; against opponents rated 20 or more, 31 deaths and 28 lost races;
  - the gate moved the midgame contact damage from the walks to the towers (15.5 → 15.7 HP a game against a stronger
    field) and slowed the ladder (3.8 towers at turn 100, v12 4.2); v13 wins turns 0-50 by 7 HP and loses turns 100-200
    by 5;
  - the HP a contact turn costs our queen falls with the own towers covering her (1.11 under one, 0.46 under two, 0.17
    under three); 27 of the 31.6 HP a lost game costs in midgame contacts go under one or two, and a better-covered spot
    is within three steps on about half of those turns; the local panel shows the same gradient;
  - 42.5 of our turns a game top up a tower that is already full; the opponents train 1.7 times our knights from turn
    50 (income 5 against 3) at the same HP a knight; five economy arms of existing flags give nothing over v13;
  - the local opponents release their midgame waves from 1,230-1,260 away, the Gold field from 990, and the field's
    waves hurt two to seven times more.
- v14 in Gold (the measurement of 2026-11-10, `docs/data/study-2026-11-10-v14-cover/`; its README has the numbers):
  - v13 would have commanded otherwise in 158 of the 180 games, on 15 % of the turns, and half as often in the games
    we lose;
  - three or more own towers cover the queen on 54 % of her contact turns from turn 50 (v13 39 %; +14.4 points, 95 %
    interval +6.5..+22.7) and 15.4 contact turns a game pass under one or two towers (21.2); the HP lost on contact
    turns falls from 15.8 to 12.6 a game, which is not established (−8.1..+1.3);
  - against opponents rated 22 or more: 57 % under three or more (27 %), deaths 6 of 41 (14 of 45), lost races 17
    (13);
  - the 26 deaths (v13 34) are the games without a tower line: 2.8 own towers on the contact turns, an opponent with
    twice the income; the ladder, the income and the knights trained are v13's;
  - an episode begun on open ground costs 3.40 HP against 1.13 at a tower, but the class is a mixture (corrected with
    the open-ground experiment): the hold's own spots off the towers cost 1.86 HP an episode (0.86 HP a game), the
    queen caught at a mine upgrade or at a build on a free site she touches 3.7 and 10.9 (1.4 HP a game; v13 0.71);
  - upload 2's seven losses to opponents rated below 17 are not tied to the rule (v13 would have played two of the
    three deaths turn for turn; in six the opponent out-earned us at turn 100); v12's and v11's uploads had such runs
    too.
- v15 in Gold (the measurement of 2026-11-11, `docs/data/study-2026-11-11-v15-sites/`; its README has the numbers):
  - v14 would have commanded otherwise in 161 of the 180 games, on 2.6 % of the turns, first at turn 31 (median): a
    tower on another site or the walk to a tower's site aimed elsewhere;
  - the sites are the local ones: the third tower is built with 1.55 own towers within 400 (v14 1.13; +0.43, 95 %
    interval +0.29..+0.55; locally 1.50-1.59), and the ladder is no slower (the third tower on turn 51 as before, the
    fourth on 70 against 75, the second barracks in 72 % of the games against 67 %);
  - she stands under three or more by turn 60 in 72 % of the games (62 %; +10.0, +0.6..+19.4), but the share of her
    contact turns from turn 50 under three or more is 53.5 % against 53.7 % (−0.3, −9.2..+8.5; locally +8 to +13
    points): the turns with three towers standing and no covered spot within 300 halve (2.1 → 1.1 a game) and the
    turns under four or more rise (7.2 → 8.8), as locally, while the turns under exactly three fall (10.9 → 8.8) and
    those with two towers or fewer standing do not (6.9 → 8.1);
  - the HP lost to knights from turn 50 is 11.5 a game against 12.6 (−1.1, −5.4..+3.3), not established; deaths 25
    against 26, lost races 34 and 34; no band of opponents and not the 53 shared opponents show a difference;
  - of the 19.5 HP a game the queen loses to knights, 8.0 fall before turn 50 and 5.1 where two own towers or fewer
    stand: two thirds are outside the reach of the hold and of the sites; 22 of the 34 lost races are behind at turn
    50, in both versions;
  - upload 2 again lost to opponents rated below 17 (9 of 37; 11 of 48 over both uploads, −1.9 SD of the rating
    model): seven deaths, six of them to an opponent with more income at turn 100, and four races.
- The open-ground experiment (2026-11-10, the user's "Да, проверь"; `docs/data/exp-2026-11-10-open-ground/`): not
  adopted.
  - `OVERLAP_TOUCH=9` (the hold and the flight at the towers' touch points only) scores 0.4909 (0.4802..0.5016)
    against v14 on 2000 development maps, −0.7 pp against `rival`, −0.4 pp against `field`; `OVERLAP_TOUCH=1` (a spot
    off the towers only for two more towers) 0.4994 (0.4889..0.5098), −0.0 and −0.3 pp;
  - without the spots off the towers the contact turns under three or more own towers fall by 3-5 points and the HP
    lost to knights from turn 50 does not fall; a spot that adds two towers is worth taking (+0.85 pp against v14,
    the interval above zero), one that adds a single tower changes nothing measurable;
  - the flag is in `current`, off; no screen was run.
- The gate-touch experiment (2026-11-10, the user's "Да, проверь"; `docs/data/exp-2026-11-10-gate-touch/`): not
  adopted.
  - `CLEAR_TOUCH` puts the gate's hold before the touch rule's upgrade of a touched mine (1), new mine or barracks
    on a touched free site (2) and new tower there (4). On 2000 development maps against v14: 1 scores 0.5034
    (0.4977..0.5090), 3 scores 0.5066 (0.4991..0.5142), 2 is level (0.5000), 4 loses (0.4904, −1.6 pp against
    `rival`), 7 scores 0.4960;
  - with 1 the episodes begun at a mine upgrade vanish and the queen loses 0.66 HP a game less from turn 50 in the
    mirror, but 0.5-0.8 upgrades a game are never made, the income at turn 150 is 0.1-0.25 lower and the enemy queen
    loses 0.35-0.8 HP a game less to our knights;
  - a new tower on the touched site is worth building with the knights within 500: set aside, 0.1-0.2 fewer own
    towers stand at turn 150;
  - the flag is in `current`, off; no screen was run.
- The tower-sites experiment (2026-11-10, the task of the session that keeps the plan;
  `docs/data/exp-2026-11-10-tower-sites/`): the bar is met and the P2 screen passed; **by the user's decision
  `SITE_NEAR=50` is the default, frozen as v15** (the champion's block at the top of this page).
  - v14's ladder already clusters the towers (the second stands within 400 of the first in five games of six), which
    left the third and the fourth tower to the choice of the site;
  - two flags on the ladder's tower-phase target, from the second tower on: `SITE_NEAR=P` adds P % of the distance to
    the second-nearest own tower, `SITE_COVER=W` takes W off for each own tower covering the site, up to two. On 2000
    development maps all ten arms score 0.537-0.556 against v14 and +1.5 to +5.6 pp against `rival`; the heavier the
    weight, the lower against v14 and the higher against `rival`; the two measures act alike;
  - `SITE_NEAR=50`, the highest against v14 (0.5561), in the profiles: three or more own towers over the queen on
    78 / 55 / 77 % of her contact turns from turn 50 against v14 / `rival` / `field` (v14 65 / 48 / 65 %), 2.3 / 1.7 /
    0.7 HP a game less to knights from turn 50, fewer deaths (5 / 9 / 0 per 400 games against 10 / 20 / 2), and the
    ladder no slower (0.1-0.2 more towers at turns 100 and 150, the second barracks four to five turns earlier);
  - the screen on screen-pv3 block 8: S 0.5425 (0.5292..0.5558) against v14, rival +3.2 pp, field +0.8 pp, boss
    unchanged, no failure. `SITE_COVER` and the heavier arms were not screened (`SITE_NEAR=100` and both flags
    together score 0.544 and 0.550 against v14 and +4.7 and +4.9 pp against `rival` on the development maps);
  - the final evaluation of v15 against v14 on evaluation-pv3 block 4: S 0.5510 (0.5377..0.5643), rival +2.5 pp,
    field +0.4 pp, boss unchanged;
  - on the arena (two Gold uploads, 2026-11-11): 21.87 and 21.05, mean 21.46 against v14's 22.61; selected by P1,
    with no sign of the local gain (the selected version's block above);
  - `SITE_COVER` is in `current`, off. The next screen block is 9, the next evaluation block 5.
- Waiting on the user:
  - nothing P1 requires: v15 is the selected version. The arena gives no sign of its local gain (mean −1.14 and
    performance −1.17 ± 0.76 against v14, inside the margin), and the measurement of its 180 games (v15 in Gold,
    above) finds no mechanism for a loss of that size and none for a gain. Whether v14 goes back on the arena is
    open to the user: a decision outside P1's rollback rule;
  - the next step. Candidates on record: the economy with the dead turns, a rival whose barracks stand
    forward (`docs/data/exp-2026-11-10-overlap/`, Not tried; the study's sections 7 and 8), and what the tower-sites
    experiment left untried (a heavier weight on a fresh block, the same measure for the defence rule's and the touch
    rule's towers). The measurement of v15's games sizes them: the first 50 turns hold 8.0 of the 19.5 HP a game the
    queen loses to knights and the turns with two own towers or fewer standing 5.1, while the class the sites act on
    holds 1.0. The two narrow follow-ups of the measurement of v14's games are tested and not adopted (the
    open-ground and gate-touch experiments above). The artifact has 2,136 characters left of its 100,000: the flags
    that are off take room a later candidate may need. The Gold ladder on
    2026-11-08: 347 agents, none eligible, top 26.54 (`docs/data/leaderboard-gold-2026-11-08.json`); v15's mean is 5.1
    below it (v14's 3.9);
  - the code of 3315948 in `refs/old-3315948/Player.java` (opponent `old`, gate G3 of P2).
- Policies: P2 locally with gates G0-G5 (`docs/local-screen.md`; PV4 since 2026-11-06), then `submit` and both `procCheck`, then P1 on the arena
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
