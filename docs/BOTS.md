# Bots

Simulator validated 2026-10-28 on 236 replays (`docs/rules/inferred.md`). Numbers marked
"old engine" were measured before the live rules and are not comparable with later ones.

| Id | Description | Params | Evidence |
| --- | --- | --- | --- |
| v00 | WAIT + empty TRAIN (frozen) | — | — |
| v01 | frozen copy of the greedy bot (`cr.bot.v01.GreedyBot`), 2026-10-28 | `versions/v01.properties`: INCOME_TARGET=10, MAX_TOWERS=4, BARRACKS=1, SEED=1, DEBUG=0 | = current at freeze: current vs v01 0.500 (20 maps, 20/0/20; old engine 17/6/17) |
| current | greedy: 1 knight barracks, mines to income 10, up to 4 towers, flee to towers | defaults in `Params` | vs v00 100/100 (50 maps, seeds 1000-1049); vs boss (repo level-4 default AI as a process) 0.950 ± 0.047, 76/0/4 (40 maps, seeds 1000-1039, threads 3, turnMs 200, 0 timeouts; old engine 80/80); artifact `5bace689…` (2026-10-29): procCheck 540/540 games identical to current, 0 timeouts; tooling recheck 1,560/1,562 in 11 runs (the other 2 from one turn-0 stall, not reproduced); fresh seeds boss 50@9000 and v01 50@8000 100/100 each; after each of the two rounds of tooling fixes 400/400 (DEVLOG 2026-10-29) |

Flags of `current` (off by default, so the defaults play as v01; `MainBotTest` checks it on 10 seeds):

| Flag | What | Evidence |
| --- | --- | --- |
| `UNSTICK=1` | detour when the BUILD towards the next site to take would leave the queen standing | P2 targeted fix, screen block 0 (seeds 200000-201999), 2026-10-30: S 0.5046 (CI 0.5018-0.5075) against v01, Δ 0.0000 against `boss`, 146 of 2000 maps changed; stall turns 739 → 53 (`docs/data/exp-2026-10-30-unstick/`) |

External opponents:
- `boss` = `refs/referee-csj/config/level4/Boss.java` (csj/code-royale@349924a), run as a process: the Default
  AI, a floor (P2). With 6 threads and 50 ms it timed out 7 times in 80 games (CPU contention), so process
  benches need a larger `turnMs` or fewer threads.
- `old` = the code of entry 3315948, kept by the user at `refs/old-3315948/Player.java` (not in place yet).

## Uploads

| Date | Artifact | Source | League | Agent | Placement |
| --- | --- | --- | --- | --- | --- |
| 2020-11-23 (agent created; last score update 2026-10-17 23:46 UTC) | old code, not in this project | — | Bronze | 3315948, submission 17067866; replaced 2026-10-29 by 6823071 | rank 559/2295, score 19.64 (2026-10-28) |
| 2026-10-29 08:17 | `build/submit/Player.java` sha256 `5bace6897a48b5b50e0f0815e966402bb6ba7ddf9b70286ee29aad71348dd84a`, 56,780 chars, default params | v01 = `current`, commit 80e404c | Bronze | 6823071, submission 41591829 (policy P0); replaced 14:03 by 6823455 | complete 09:36: rank 736/2296, score 18.24; 60 games, 36 wins (seat 0: 21 of 33, seat 1: 15 of 27); every game identical to the artifact (`cgAttribute`, 7,230 turns); `docs/data/arena-6823071-placement-2026-10-29.json` |
| 2026-10-29 14:03 | the same `5bace689…`, re-submitted by the user | v01, upload 2 | Bronze | 6823455, submission 41593274 (policy P1); replaced 22:50 by 6824680 | not watched; complete by 15:51 (last update): score 16.08, rank 1067/2294 at 20:51; 60 games, 31 wins (seat 0: 20 of 30, seat 1: 11 of 30; 3 of them opponent timeouts on turn 0); every game identical to the artifact (`cgAttribute`, 6,413 turns). Same bytes 2.16 points below upload 1: SD 1.53, M ≈ 3.05 (`docs/arena-uploads.md`) |
| 2026-10-29 22:50 | `build/submit/Player.java` sha256 `1e5d1a79f113347a00a68e51172602bf44c23d56670ebf85db0c99592c6741fd`, 57,884 chars, default params, build `e5417a83` | v01 with the build marker, upload 3; commit 2641aa9 | Bronze | 6824680, submission 41596829 (policy P1) | complete 2026-10-30 00:47: rank 654/2294, score 18.98; 60 games, 36 wins (seat 0: 22 of 33, seat 1: 14 of 27); marker `e5417a83` in all 60, the turn-0 warning matched by cgValidate; every game identical to the artifact (`cgAttribute`, 6,222 turns); `docs/data/arena-6824680-placement-2026-10-29.json`. Three uploads of v01: mean 17.77, SD 1.51, M = 3.01 (2 df, provisional) |

Registry for `tools/arena_compare.py`: `docs/data/arena-uploads.json`.

## Artifacts

Kept copies (gitignored): `replays/artifacts/<first 8 hex of the sha256>/Player.java`. The config id (`Build.config`) names the bot and its params (the code outside the runner section and the import header, plus PARAMS as written); builds that differ only in the runner share it.

| Built | Artifact | Build id | Config id | Source | Checks | Uploads |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-10-29 | `5bace6897a48b5b50e0f0815e966402bb6ba7ddf9b70286ee29aad71348dd84a`, 56,780 chars | none (no marker) | `4194117d` | v01 = `current`, default params; commit 80e404c (8f8ca09 builds the same bytes) | see the `current` row; `cgAttribute` of upload 2 against the kept copy: SAME 60/60 | 6823071, 6823455 |
| 2026-10-29 21:06 | `1e5d1a79f113347a00a68e51172602bf44c23d56670ebf85db0c99592c6741fd`, 57,884 chars | `e5417a83` (turn 0: `TRAIN 14 5 4 1 7 10 8 3`) | `4194117d` | v01 = `current` with the build marker, default params; commit 2641aa9 (a clean export rebuilds the bytes) | procCheck vs v01 100 maps @5000 and vs boss 100 maps @6000: 200/200 identical to current each (the marker in place of the empty turn-0 TRAIN), 0 timeouts, 0 kills, max first turn 124 / 219 ms, max later 2.8 / 9.6 ms; `cgAttribute` of upload 3 against the kept copy: SAME 60/60, marker in 60/60 | 6824680 |
