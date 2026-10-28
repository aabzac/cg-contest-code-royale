# Live rules inferred from replays

Base: `csj/code-royale@349924a` ported 1:1 into `cr.core` (Kotlin evaluation order, squared
distance comparisons, Kotlin `split`, `Vector2.Zero` assignment ignored, dead creeps kept in
lists until the end of the turn, spawning inside the structure loop).
Evidence: `./gradlew cgValidate` on 98 replays (88 Legend top-10 + 10 own Bronze games,
manifests in `docs/data/`), 2026-10-28.

## Confirmed

| Item | Live | Repository | Evidence |
| --- | --- | --- | --- |
| Max turns | 250 | 200 | live Wood 3 statement; 24/88 Legend games end at exactly 250 |
| Knight HP at spawn | 25 | 30 | 8600/8600 knight spawns show "Health: 25"; giants 200 (36/36); live Bronze statement table |
| Tower damage distance | from the site centre: `MIN + int((attackRadius - dist(centre))/200)` | from the site edge (`dist - siteRadius`) | resync, 98 replays: creep HP corrections 447 → 1, fully consistent games 4 → 33, plain-turn unit match 89.69%; queen HP corrections 382 → 319 when applied to queen shots too |
| Map generation (Bronze+) | as repo | — | sites x/y/radius/gold and queen HP match in 98/98 games |
| Queen start HP (Bronze+) | 25..100 step 5, drawn before the map | — | 98/98 |
| Economy, building, queen movement until first spawn | as repo | — | no HUD/site/unit mismatch before the first creep spawn (1275 turns) |

The repository's Bronze statement says towers deal "3 base damage at the edge of their firing
range, +1 for every 200 units closer"; the live code measures that from the site centre. The
statement's "Expert Rules" turn sequence (aging after structures, dead creeps removed after
tower shots) differs from the Kotlin code and is being checked against the replays.

## Known differences, not yet explained

1. **Spawn positions.** First mismatch in 98/98 games is the first creep spawn (resync: 1/1159
   spawn turns exact): creeps end on the barracks boundary (distance r+20+1..2) but at
   different angles than the repo rule (`center + c*(iter,iter)`, `towards(enemy queen, 30)`,
   `fixCollisions(999)`). Tried and rejected: legacy offset `(iter,iter)` (pre-8a63cd9),
   legacy entity order (pre-918c375), collision fix per creep, no `towards`, sign variants.
   Relative angles are deterministic (e.g. owner 1: creeps at about +66°, -38°, +41°, -4° from
   the queen direction).
2. **Movement/collisions on plain turns**: 10.3% of non-spawn turns differ after resync, e.g.
   a queen pushed by a knight ends 1 unit further in the replay.
3. Queen HP (319 corrections under resync) — largely downstream of 1-2.
4. Archers: none in the corpus; archer constants unverified.
5. Build-conflict parity (`Rules.TURN_BASE`) not observed yet. The live statement says "On
   even-numbered turns (this includes the first turn: turn 0), player 2 gets to build", i.e.
   `TURN_BASE = 0`; the code default is 1 (SDK turn numbering) until a replay shows a conflict.
