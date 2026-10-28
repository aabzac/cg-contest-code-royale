# Dev log

## 2026-10-28 — phase 0
Statements, referee `csj/code-royale@349924a`, leaderboard and 88 Legend replays. Live max
turns 250 vs repo 200 (confirmed by replays).

## 2026-10-28 — phase 1: referee port and replay validation
Hypothesis: the public referee equals the live game except the turn limit.
Result: rejected. `cgValidate` (98 replays): map/economy/building/queen moves match up to the
first creep spawn (1275 turns, 0 mismatches before it); knights spawn with 25 HP (8600/8600,
repo 30) → adopted. Spawn positions and tower-vs-creep damage still differ; legacy variants
of two 2018 community fixes (entity order, spawn offset) and several spawn variants rejected
(same first mismatch in 98/98). Resync mode (copy replay units/HUD and continue): 4/98 games
fully consistent; corrections: queenHp 232, units hp 447, pos 161, poshp 278, count 78,
spawn 410. Tower damage samples (722): live = sim - 1 for range-shot in [200,289] and
[400,487]. Decision: keep repo formula until the live Bronze statement is available; do not
trust combat benches. Commands: `./gradlew cgValidate -Pquiet=true [-Presync=true]`.

Greedy `current` vs v00: 100/100 wins (50 maps × 2 seats, seeds 1000-1049); procCheck of the
merged artifact (54,375 chars): 0 timeouts, first turn ≤165 ms, later ≤10.5 ms.

## 2026-10-28 — tower damage, opponents, visualizer
Hypothesis: the live tower damage counts distance from the site centre (repo: from the edge).
Evidence: 722 isolated samples were 1 lower exactly where `int((range - dist + r)/200)` and
`int((range - dist)/200)` differ. Resync over 98 replays: creep HP corrections 447 -> 1, fully
consistent games 4 -> 33, plain-turn unit match 89.69%; queen HP corrections 382 -> 319 with the
same rule for queen shots. Decision: adopted as default (`Rules.TOWER_FROM_CENTER`,
`TOWER_QUEEN_FROM_CENTER`). Rejected: global TOUCHING_DELTA 8/10/12/15 (site checks diverge
earlier, matched turns 9472 -> 5375..3736).
Built `ProcessBot` (external programs in run/bench/procCheck), `boss`/`bossN` specs, frozen
`v01`, local replay export and a canvas visualizer served by `./gradlew serve`.
Workflow `code-royale-live-rules` (run wf_841110c2-70b) started for spawn placement,
movement/collisions, turn order/HP and a second corpus `replays/cg2`.

## 2026-10-28 — live rules from replays: simulator validated
Hypothesis: the remaining mismatches come from a few structural differences in the live
referee, not from constants. The workflow (4 investigators, 3 adversarial verifiers, run
wf_841110c2-70b) tested this on `replays/cg` and then on a new corpus `replays/cg2`.

Result: confirmed. Adopted, each with a test:
- collision entity order: each player's creeps, then that player's queen, sites last;
- spawn corner offsets `(1,-1), (-1,1), (1,1), (-1,-1)`;
- structures act by type: towers, then barracks, then mines;
- TRAIN capped at 30 creeps;
- build-conflict parity counted from turn 0.

A retained Kotlin behaviour that contradicts the statement now has tests: dead creeps are
removed only at the end of the turn. The tower-damage rule from the previous entry got its
first unit test. The four patches were merged into one implementation. Removed: the inference switches, the trace
log and the `-P` flags `legacyOrder`, `legacySpawn`, `turnBase`, `knightHp`, `spawnOffset`,
`spawnFixEach`, `spawnNoTowards`, `towerFromCenter`, `towerQueenFromCenter`, `traceGame`,
`traceTurn`. `cgValidate` now also compares summary warnings (multiple per player per turn) and
counts the turns of games that differ only at the end.

Strict validation, no corrections:
- `replays/cg` (98): 97 games consistent, 15,374/15,374 turns; units plain 13424/13424, spawn
  1950/1950; warnings 113/113. Before: 0 games, first mismatch at the first spawn in 98/98;
  resync plain 89.69%, spawn 0.09%.
- `replays/cg2` (138; Bronze 58, Silver 38, Gold 42): 137 games, 20,438/20,438 turns; plain
  18330/18330, spawn 2104/2104; warnings 3407/3407. Before: 3 games, the 1-turn ones.
- Remaining: 911493205 (11:11) and 908057307 (0:0) are ranked [0,1] by CG; the simulator scores
  a draw. The tie-break rule is open.

Mutation check of the merged code: each rule was reverted alone. Every revert fails its unit
test and at least one corpus (`docs/data/inference-2026-10-28/mutation-check.txt`).

Rejected; details in `docs/data/inference-2026-10-28/workflow-result.json`:
- spawn offsets `c*iter` and `(iter,iter)`, no `towards`, a collision fix per creep, one
  collision pass after all barracks;
- spawning inside the structure loop or after dead-creep removal;
- an extra global `fixCollisions` after the creep phase or at the end of the turn (plain 67-74%);
- 2, 3 or 999 collision passes per substep; mass, speed, radius and gap variants;
- dead creeps: towers skipping 0-HP creeps, removal after each shot or after the towers;
- aging: knights aging before attacking, newborn creeps aging;
- queens tearing down structures right after builds;
- the cap counting creeps in training or checked before gold.

Engine change, so benches before this entry are not comparable. Re-run on this code:
- current vs v00: 100/100 (50 maps, seeds 1000-1049).
- current vs v01: 0.500, 20/0/20 (20 maps; before 17/6/17). In this mirror player 0 won 12 maps
  and player 1 won 8, too few to claim a seat advantage.
- current vs boss: 0.950 ± 0.047, 76/0/4 (40 maps, seeds 1000-1039, threads 3, turnMs 200, 0
  errors, boss max 106 ms). All four losses were as player 1: seeds 1008, 1023, 1028, 1038.
  Before: 80/80.
- `procCheck` (`Player.java` 54,064 chars, sha256 `b267ba0d…`): 10/10 vs boss, 0 timeouts,
  first turn ≤ 177 ms, later turns ≤ 15.4 ms.

Commands: `./gradlew cgValidate -Preplays=replays/cg2 [-Presync=true]`; `./gradlew test` (22 tests).
