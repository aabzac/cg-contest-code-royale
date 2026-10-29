# Bots

Simulator validated 2026-10-28 on 236 replays (`docs/rules/inferred.md`). Numbers marked
"old engine" were measured before the live rules and are not comparable with later ones.

| Id | Description | Params | Evidence |
| --- | --- | --- | --- |
| v00 | WAIT + empty TRAIN (frozen) | — | — |
| v01 | frozen copy of the greedy bot (`cr.bot.v01.GreedyBot`), 2026-10-28 | `versions/v01.properties`: INCOME_TARGET=10, MAX_TOWERS=4, BARRACKS=1, SEED=1, DEBUG=0 | = current at freeze: current vs v01 0.500 (20 maps, 20/0/20; old engine 17/6/17) |
| current | greedy: 1 knight barracks, mines to income 10, up to 4 towers, flee to towers | defaults in `Params` | vs v00 100/100 (50 maps, seeds 1000-1049); vs boss (repo level-4 default AI as a process) 0.950 ± 0.047, 76/0/4 (40 maps, seeds 1000-1039, threads 3, turnMs 200, 0 timeouts; old engine 80/80); artifact `5bace689…` (2026-10-29): procCheck 540/540 games identical to current, 0 timeouts; tooling recheck 1,560/1,562 in 11 runs (the other 2 from one turn-0 stall, not reproduced); fresh seeds boss 50@9000 and v01 50@8000 100/100 each; after each of the two rounds of tooling fixes 400/400 (DEVLOG 2026-10-29) |

External opponents: `boss` = `refs/referee-csj/config/level4/Boss.java` (csj/code-royale@349924a), run as a
process. With 6 threads and 50 ms it timed out 7 times in 80 games (CPU contention), so process
benches need a larger `turnMs` or fewer threads.

## Uploads

| Date | Artifact | Source | League | Agent | Placement |
| --- | --- | --- | --- | --- | --- |
| 2026-10-18 | old code, not in this project | — | Bronze | 3315948, submission 17067866; replaced 2026-10-29 by 6823071 | rank 559/2295, score 19.64 (2026-10-28) |
| 2026-10-29 08:17 | `build/submit/Player.java` sha256 `5bace6897a48b5b50e0f0815e966402bb6ba7ddf9b70286ee29aad71348dd84a`, 56,780 chars, default params | v01 = `current`, commit 80e404c | Bronze | 6823071, submission 41591829 (policy P0) | complete 09:36: rank 736/2296, score 18.24; 60 games, 36 wins (seat 0: 21 of 33, seat 1: 15 of 27); every game identical to the artifact (`cgAttribute`, 7,230 turns); `docs/data/arena-6823071-placement-2026-10-29.json` |
