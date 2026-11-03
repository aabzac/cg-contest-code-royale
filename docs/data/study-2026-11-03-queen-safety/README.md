# Study 2026-11-03: how v05's queen loses HP and dies (the queen's safety direction)

**Question.** The direction chosen after the army experiment (`docs/data/exp-2026-11-03-army/`): the
architecture page lists the queen's safety as the fourth direction (early deaths with one tower by turn
25; the threat is only knights within 250; the flight goes to the nearest tower however far). What does
the queen actually do while she is being hit, and what kills her?

## Data and tools

- **Corpus:** v05's 180 placement games (uploads 1-3, agents 6841317, 6841407, 6841579;
  `replays/arena/v05/bronze/upload-0*/games`).
- **Features:** `./gradlew cgStudy -Pout=replays/study/features-v05-3up-2026-11-03.csv -Preplays="…"`
  (sha256 `d4e56593ec03ff70…`, gitignored, copied to the main checkout).
- **Traces:** `cr.tools.DeathTrace <agentId> [all] <replays…>` (new, 2026-11-03): the game replayed
  through `Rules` with the recorded commands, one line per turn from the queen's first HP loss (or turn 0
  with `all`): HP and its loss, gold, position, the command, the touched site, own towers and the nearest
  one's distance, enemy knights within 250 and 500 and the nearest, enemy archers and giants within 300,
  enemy towers covering her. `replays/study/traces/all-v05.txt` (all 180 games, sha256
  `f36d8f9c3215cc44…`) and one file per early death in `replays/study/traces/<gameId>.txt`.
- **Script:** `deaths.py` (output `deaths.txt` beside it; run from the repository root).

## Findings

1. **How the 55 losses end.** 37 by the queen's death (median turn 103; 12 before turn 80), 18 on HP at
   the 250-turn limit. The killers are knights: 2,062 of the 2,335 HP lost in the games she died in; enemy
   towers took 262 (11 %), mostly on walks through their range. 33 of the 37 killers played knights only.
2. **The queen's starting HP is random in Bronze** (`MapGen`: 25, 30, … 100, the same for both queens;
   the repository's referee samples it for level 4 and above). v05 wins *more* with a low start (76 % at
   25-40 HP, 61 % at 85-100): the race is symmetric and our early knights hurt them more than theirs hurt
   us. The early deaths concentrate at low HP (9 of 12 at 25-60) because a 35 HP queen survives 14 turns of
   contact, but the start HP is not the lever.
3. **What she does while hit.** In the games she died in she was hit on 1,103 turns; on 1,029 of them she
   stood at her own tower (within 130 of its centre), and on 858 she was topping that tower up (the other
   245: walking to the nearest tower). The median number of knights on her is 3; a knight deals 1 HP a turn
   each.
4. **Why that loses.** A tower shoots the enemy creep nearest to *the tower* once a turn for 3 HP at 200 HP
   of tower (range 262) and 4 HP at 700 (range 477; `Rules.actStructure`): a knight (25 HP) dies in 7-9
   turns. Four knights at one tower take about 30 turns to clear and deal 4+3+2+1 ≈ 60-70 HP meanwhile. The
   top-up raises the damage from 3 to 4 (and the range), a second tower adds 3 more: it doubles the kill
   rate, the top-up adds a quarter. At the first contact she had one tower in 10 of the 37 deaths, two in 18.
5. **What the winners do.** The opponents' traces in the same games (916027972 BartWelling, 916044828
   morph1max; `DeathTrace <their agentId> all`): under the same knights they walk to the next free site and
   build another tower there, under fire (two towers by turn 32, three by turn 29), and move between
   sites; they end with 12-15 HP where we die. The study of 2026-11-03 saw the same in the aggregate:
   stronger players hold 4 towers at about 450 HP, we 6 at 700.
6. **Enemy towers.** 262 HP in deaths and 139 in survivals: the flight to the nearest own tower and the
   ladder's walks do not avoid enemy tower range (only the target site is checked; ROUTE's plans avoid it
   for the walk to the next site, not for the flight).

## What follows (flags of `current`, experiment `docs/data/exp-2026-11-03-safety/`)

- `THREAT_TOWERS_NEAR=N`, `THREAT_RADIUS=R`: under threat, with fewer than N own towers within R of the
  queen, build a new tower on the nearest free site within R (not under an enemy tower) instead of
  topping up or walking to the nearest tower.
- `THREAT_RANGE`: the knights' distance that counts as a threat (250 in v05).
- Not tried here: kiting between sites, the flight avoiding enemy tower range, a tower before the barracks
  when the enemy barracks already trains.
