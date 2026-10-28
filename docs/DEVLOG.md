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
