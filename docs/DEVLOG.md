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
