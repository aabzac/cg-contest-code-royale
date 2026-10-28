# Live rules inferred from replays

Base: `csj/code-royale@349924a` ported 1:1 into `cr.core` (Kotlin evaluation order, squared
distance comparisons, Kotlin `split`, `Vector2.Zero` assignment ignored), with the live
differences below.

Evidence: `./gradlew cgValidate` on 236 replays, 2026-10-28:

- `replays/cg`: 88 Legend top-10 games and 10 games of our Bronze entry
  (`docs/data/replays-legend-top-2026-10-28.json`);
- `replays/cg2`: 138 games fetched as a second corpus: Bronze 58, Silver 38, Gold 42; archers
  spawn in 12 of them, giants in 27; 2 games contain build conflicts
  (`docs/data/replays-cg2-2026-10-28.json`).

Result in strict mode (no corrections): **97/98 and 137/138 games match on every turn**
(15,374 and 20,438 turns). Units match on 100% of turns, and summary warnings on 113/113 and
3407/3407 turns. In the two remaining games only the winner of a tie differs (see below).

## Confirmed

| Item | Live | Repository | Evidence |
| --- | --- | --- | --- |
| Max turns | 250 | 200 | live statements; 24/88 Legend games end at exactly 250 |
| Knight HP at spawn | 25 | 30 | 8600/8600 knight spawns show "Health: 25"; live Bronze statement table |
| Tower damage distance | from the site centre: `MIN + int((attackRadius - dist(centre))/200)` | from the site edge (`dist - siteRadius`) | creep HP corrections under resync 447 → 1 (98 replays) |
| Collision entity order | each player's creeps (creation order), then that player's queen; sites last. This is the pre-918c375 `players.flatMap { it.allUnits() } + obstacles` | all creeps, then both queens, then sites | plain-turn unit match 89.69% → 100% (cg) and 91.65% → 100% (cg2). It is the only one of 8 natural orders that fits (next best: 98.96% and 97.69%) |
| Spawn position | creep `iter` starts at the site centre + `[(1,-1), (-1,1), (1,1), (-1,-1)][iter]` for both players, then moves 30 towards the enemy queen; one `fixCollisions(999)` per completed barracks | centre + `c*(iter, iter)`, c = -1 for player 1 | replay frames commit the pre-collision position at time 0. Fit: 8528/8636 creeps (cg); knights 8661/8752, archers 49/50, giants 39/40 (cg2). A 0.05 grid around each corner fits worse. Spawn-turn unit match 0.09% → 100% |
| Structure order | towers shoot, then barracks spawn, then mines collect; each group in site order (the statement's sequence) | plain site order, spawning inside the loop | in site order, 226 simulated tower shots hit creeps spawned that turn; none exists in the replays. Tower projectiles aim at positions from before the spawn collisions (814 vs 0) |
| Dead creeps | removed only at the end of the turn: until then they are still tower targets and take part in the spawn collisions | same | contradicts the statement's "dead creeps removed" right after the towers. Replay projectiles hit already-dead creeps; queen positions are exact only with 0-HP creeps in the spawn collisions (e.g. 910380279 t56, 895529937 t39) |
| TRAIN cap | refused (warning, no gold spent) when the player's living creeps plus the requested creeps exceed 30. Creeps in training do not count; the gold check comes first; with more than 30 creeps an empty TRAIN also warns | none | warning "Training too many creeps (N total units requested; 30 maximum)": 22/22 occurrences match N. Variants that count training creeps or check the cap before gold fit worse |
| Build conflict | when both queens build on one site, player 1 builds on even 0-based turns (the statement's "turn 0") and player 0 on odd ones | `removeAt(turn % 2)` with the SDK turn index (1 on the first turn) | 5 conflicts in 2 games, builder read from the colour of the structure image; strict 906051728 passes all 250 turns only with this parity |
| Map generation (Bronze+), queen start HP 25..100 | as repository | — | 236/236 |
| Economy, building, queen movement, creep movement and damage | as repository | — | 0 mismatches on 35,812 turns |
| Archers and giants | as repository: HP 45/200, speed 75/50, archer range 200, damage 2 (10 to giants), giant bust 80 | — | 536 archer and 990 giant plain turns are exact; 286 tower busts by giants |

## Not explained

1. **Tie-break.** Two games end with equal scores: 911493205 (turn 250, 11:11) and 908057307
   (both queens die on turn 72, 0:0). CG ranks player 0 first in both, while the simulator
   calls a draw (`winner 0 vs -1`). A tie-break by seat would fit both; it is not adopted on
   two samples.

## Statement vs live code

The live Bronze statement differs from what runs live in these points:

- **Tower damage**: the statement measures from the edge of the firing range; live measures the
  distance from the site centre.
- **Dead creeps**: the statement's turn sequence removes them right after the towers shoot; live
  removes them at the end of the turn.
- **Collisions**: the statement says collisions are checked "at the end of every turn". Live there
  is one pass per creep substep and one `fixCollisions(999)` per spawning barracks. An extra
  end-of-turn pass drops plain-turn matches to 67-74%.
- **Site radius**: the statement says 60..110; the generator uses 60..90.
- **TRAIN cap**: the statement does not mention it.

## Method

Workflow `code-royale-live-rules` (run `wf_841110c2-70b`) used four investigators (spawn,
movement, turn order, second corpus). Each patch was then checked by a separate adversarial
verifier.

- The spawn, movement and turn-order rules were fitted on `replays/cg` only. Their verifiers
  confirmed them on `replays/cg2`.
- The build-conflict parity comes from `replays/cg2` itself, because `replays/cg` has no conflicts.

The patches were merged into one implementation without switches; the alternatives stay
reproducible from the archived patches. A mutation check reverts each rule alone: every revert
fails at least one unit test and the replay validation.

Patches, verdicts, scripts and outputs: `docs/data/inference-2026-10-28/`.
