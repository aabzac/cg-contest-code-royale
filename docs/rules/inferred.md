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
| Knight HP at spawn | 25 | 30 | 8600/8600 knight spawns show "Health: 25"; giants 200 (36/36) |
| Map generation (Bronze+) | as repo | — | sites x/y/radius/gold and queen HP match in 98/98 games |
| Queen start HP (Bronze+) | 25..100 step 5, drawn before the map | — | 98/98 |
| Economy, building, queen movement until first spawn | as repo | — | no HUD/site/unit mismatch before the first creep spawn (1275 turns) |

## Known differences, not yet explained

1. **Spawn positions.** First mismatch in 98/98 games is the first creep spawn: creeps end on
   the barracks boundary (distance r+20+1..2) but at different angles than the repo rule
   (`center + c*(iter,iter)`, `towards(enemy queen, 30)`, `fixCollisions(999)`). Tried and
   rejected: legacy offset `(iter,iter)` (pre-8a63cd9), legacy entity order (pre-918c375),
   collision fix per creep, no `towards`, sign variants. Relative angles are deterministic
   (e.g. owner 1: creeps at about +66°, -38°, +41°, -4° from the queen direction).
2. **Tower damage to creeps** is 1 lower than `3 + int((range - shotDist)/200)` in all 722
   isolated mismatch samples: observed extra damage 0 for range-shot in [200, 289], 1 for
   [400, 487]; no mismatch for [290, 400). Climb distance ≈ 290..300 or a different formula.
   Single-constant changes (MIN=2, CLIMB=290..400) did not reduce mismatches under resync.
3. Queen HP and creep positions after combat (resync mode: 232 queen-HP and ~440 position
   corrections over 98 games) — not analysed yet; partly downstream of 1-2.
4. Archers: none in the corpus; archer constants unverified.
5. Build-conflict parity (`Rules.TURN_BASE`) not observed yet.

The live Bronze statement ("Expert rules") is needed to resolve 1-2 and the unit table.
