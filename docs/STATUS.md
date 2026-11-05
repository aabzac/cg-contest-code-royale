# Status

The current state only; replace lines here, do not append history. Where the rest lives: uploads and
artifacts `docs/BOTS.md`, P1 comparisons and the noise margin `docs/arena-uploads.md`, history and
evidence `docs/DEVLOG.md`, the backlog `docs/PLAN.md`, a short Russian summary for the user
`docs/SUMMARY.md`. The history this page held until 2026-10-30: `git show 60f0b59:docs/STATUS.md`.

- Date: 2026-11-05. Phase 3 (strength). Game: Code Royale, permanent multiplayer arena (`code-royale`),
  no deadline. League: Silver since 2026-11-03 23:46 (CG promoted v08 upload 2); the Bronze scores stay
  Bronze-only. PV1/PV2 lapsed with the league change; **PV3** (2026-11-05, the user's decision on the Silver
  study's proposal, `docs/data/study-2026-11-05-silver/`) renews the proxy for Silver. P2's blocks are now
  strided (`screen-pv3` and `evaluation-pv3` in `docs/data/seed-ledger.json`, both next at k = 0), so every
  block plays all 16 starting HPs. Giants and the late game of strong economies are recorded as
  unrepresented, and candidates that depend on them are judged by the arena (`docs/local-screen.md`, PV3).
  P2 candidates can be promoted again.
- Selected version, local champion and `current` defaults: v08 = v07 with the kiting bound to the towers
  (KITE=2, KITE_PULL=100), frozen 2026-11-03 (`cr.bot.v08.GreedyBot`, `versions/v08.properties`; P2
  `docs/data/exp-2026-11-03-kite2/`: S 0.6670 against v07 and +14.8 pp against `field` on the evaluation
  block). Selected under P1 on 2026-11-03 by two placements (mean 27.99 against v07's 25.53, "not clearly
  worse", +2.46 against −M −2.46; `docs/arena-uploads.md`); promoted to Silver after upload 2. Silver
  baseline (2026-11-04): placements 20.70, 20.74 and 20.21, mean 20.55, pooled rating 20.40 ± 0.42 (250 games). Artifact `5c16d925…` (79,348 chars, build `3f19aa6e`, config `c9dad766`; the freeze commit; kept copy
  `artifacts/5c16d925/Player.java`), procCheck 200/200 against v01 and against `boss`, no timeouts
  (`docs/BOTS.md`, Artifacts). The rollback artifact for the next candidate is `5c16d925`; v07's `4c7b0d04` stays kept.
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
- Arena entry: `aabzac`, agent 6847999 = v08 upload 5, **Silver** (the same artifact `5c16d925…`), pasted by
  the user 2026-11-04 22:59 local and followed by `tools/arena.py follow v08`; placement complete 23:24: score
  20.21, local rank 6/201 (global 465), `eligibleForPromotion` false, 80 games, 48 wins, 1 of 3 against the
  Silver boss, every game identical to the artifact (`docs/BOTS.md`, Uploads;
  `docs/data/arena-6847999-placement-2026-11-04.json`). It replaced agent 6844095 = v08 upload 4 (20.74, rank
  3), whose 90 games were saved before the paste.
- Silver noise margin: **M = 2.46, borrowed from Bronze** (11 df) by the user's decision of 2026-11-04, until
  they decide otherwise. Silver's own M, 0.59 on 2 df (v08's three Silver placements, SD 0.30), is printed as
  context (`noiseMargins` in the registry; `docs/arena-uploads.md`, Silver noise margin). A Silver candidate
  rolls back early below 15.62.
- Silver boss: agent 1876720 (no pseudo), rating **21.87-21.99** during the fetches of 2026-11-04 00:05-23:24
  (the games manifests of v08 uploads 3-5); v08 won 6 of its 16 games against it and stands about 1.4 below
  it (mean 20.55). The strongest other player met: mesavitae, 21.12-21.35. About half of v08's Silver
  placement games (40, 40 and 38 of 80) went to turn 250.
- How v08 plays in Silver (the study of 2026-11-05, `docs/data/study-2026-11-05-silver/`; its README has the
  numbers):
  - most HP races are decided at the enemy's first wave (turn 23-28), which our second tower (turn 25) often
    misses, and the boss games above all;
  - 10 of 34 lost races were led after turn 200;
  - giant players beat us about 2 rating points more than their ratings predict (p 0.03 with Silver's own
    logistic scale, 3.16);
  - the strongest four out-produce us (income 6 against 3, waves 10-13.5 against 6-7).
  Next candidates proposed there: a second tower before the first wave, then holding an HP lead late.
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
- Waiting on the user: the choice of the next candidate (the study proposes the first-wave tower); the code
  of 3315948 in `refs/old-3315948/Player.java` (opponent `old`, gate G3 of P2).
- Policies: P2 locally with gates G0-G4 (`docs/local-screen.md`; PV3 since 2026-11-05), then `submit` and both `procCheck`, then P1 on the arena
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
