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

1. **Tie-break.** Eighteen games end with equal scores:
   - 911493205 (turn 250, 11:11);
   - 908057307 (both queens die on turn 72, 0:0);
   - 913398184 (v02 upload 1, 2026-10-30, both queens die on turn 38, 0:0);
   - 913453290 (v02 upload 2, 2026-10-30, turn 250, 50:50);
   - 913522461 (v03 upload 1, 2026-10-30, turn 250, 50:50; player 0 was the opponent);
   - 916297591 (v08 upload 4, Silver, 2026-11-04, turn 250, 28:28; player 0 was the opponent);
   - 916719390 (v08 upload 5, Silver, 2026-11-04, both queens die on turn 146, 0:0; player 0 was us);
   - 917804072 (v10 upload 1, Silver, 2026-11-07, turn 250, 7:7; player 0 was us);
   - 917810075 (v10 upload 2, Silver, 2026-11-07, turn 250, 7:7; player 0 was us);
   - 917833240 (v11 upload 1, Silver, 2026-11-07, turn 250, 77:77; player 0 was the opponent);
   - 917834882 (v11 upload 1, Silver, 2026-11-07, turn 250, 23:23, against the Silver boss; player 0 was us);
   - 917850788 (v11 upload 3, Gold, 2026-11-07, turn 250, 95:95; player 0 was us);
   - 917985146 (v11 upload 4, Gold, 2026-11-07, turn 250, 35:35; player 0 was the opponent);
   - 917987888 (v11 upload 4, Gold, 2026-11-07, turn 250, 68:68; player 0 was us);
   - 918143672 (v12 upload 1, Gold, 2026-11-07, turn 250, 75:75; player 0 was us);
   - 918166983 (v12 upload 2, Gold, 2026-11-07, turn 250, 51:51; player 0 was us);
   - 919175620 (v13 upload 1, Gold, 2026-11-09, turn 250, 70:70; player 0 was us);
   - 919249780 (v12 upload 3, Gold, 2026-11-10, turn 250, 31:31; player 0 was us).

   The game result (`gameResult/findByGameId`) ranks player 0 first in all eighteen, while the simulator
   calls a draw (`winner 0 vs -1`). A tie-break by seat would fit all eighteen; it is not adopted on eighteen
   samples. No game of v01's 180 placement games had equal scores.

   The battle list (`findLastBattlesByAgentId`) says otherwise: it gives both players position 0, a draw,
   in each of our sixteen such games with saved lists. That is every saved list that holds the finished game:
   80, 48, 24, 16, 27, 4, 15, 20, 9, 3, 19, 10, 33, 12, 12 and 21 lists for 913398184, 913453290, 913522461, 916297591,
   916719390, 917804072, 917810075, 917833240, 917834882, 917850788, 917985146, 917987888, 918143672, 918166983,
   919175620 and 919249780. Which of the
   two CG's rating uses is not known. The manifests and `arena_compare.py` count by the game result's
   ranks, so such a game is a win for player 0. The battle-list summaries of `arena_snapshot.py` count it as
   neither: 47 wins against the manifest's 48 in v08 upload 5.
2. **Score after a mid-game timeout.** In 913520691 (v03 upload 1) the opponent timed out on turn
   115. Every turn matches, but CG scored the surviving queen 52 and the simulator 53. CG seems to
   finish the timeout's turn, so creeps still act, before it scores; the simulator ends the game when
   the kill happens (`Rules.finishKilled`). Turn-0 timeouts showed no difference, since no creeps exist
   then. The ranks agree. The rule is not changed on one sample. Two later mid-game timeouts of the
   opponent, 913807642 (turn 223) and 913829899 (turn 86, both v03 upload 3), score as the simulator
   does. Whether a finished turn could have changed the score there was not checked, so they neither
   confirm nor refute the explanation.

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
