# Bots

Simulator validated 2026-10-28 on 236 replays (`docs/rules/inferred.md`). Numbers marked
"old engine" were measured before the live rules and are not comparable with later ones.

| Id | Description | Params | Evidence |
| --- | --- | --- | --- |
| v00 | WAIT + empty TRAIN (frozen) | — | — |
| v01 | frozen copy of the greedy bot (`cr.bot.v01.GreedyBot`), 2026-10-28 | `versions/v01.properties`: INCOME_TARGET=10, MAX_TOWERS=4, BARRACKS=1 | = current at freeze: current vs v01 0.500 (20 maps, 20/0/20; old engine 17/6/17) |
| current | greedy: 1 knight barracks, mines to income 10, up to 4 towers, flee to towers | defaults in `Params` | vs v00 100/100 (50 maps, seeds 1000-1049); vs boss (repo level-4 default AI as a process) 0.950 ± 0.047, 76/0/4 (40 maps, seeds 1000-1039, threads 3, turnMs 200, 0 timeouts; old engine 80/80); procCheck 10/10 vs boss, 0 timeouts, first turn ≤ 177 ms, later ≤ 15.4 ms |

External opponents: `boss` = `refs/referee-csj/config/level4/Boss.java` (csj/code-royale@349924a), run as a
process. With 6 threads and 50 ms it timed out 7 times in 80 games (CPU contention), so process
benches need a larger `turnMs` or fewer threads.
