# Study 2026-11-03: v07 on the arena — why the kiting gain did not show

**Question.** P2 put v07 (kiting under knights) at S 0.68-0.69 against v06 and +3 pp against `field`; the
arena gave 26.42 and 24.64 against v06's 27.09 and 25.39 (mean −0.71, inside the noise). What did the
kiting do on the arena, and what did the opponents do to it?

## Data and tools

- **Corpus:** v07's 120 placement games (uploads 1-2, agents 6843218 and 6843287), compared with v06's 120
  (`docs/data/study-2026-11-03-arena-winners/`).
- **Features:** `cgStudy` into `replays/study/features-v07-2026-11-03.csv`; **traces:** `DeathTrace <agent> all`
  for both queens, `replays/study/traces/all-v07-ours.txt` and `all-v07-opp.txt` (the agent ids per game in
  `v07-games.txt`). All gitignored, copied to the main checkout.
- **Script:** `v07.py` (output `v07.txt` beside it; run from the repository root; it imports the winners
  study's loader).

## Findings

| per 120 games | v06 | v07 |
| --- | --- | --- |
| wins | 87 | 83 |
| losses by our queen's death / on HP at the limit | 24 / 9 | 26 / 11 |
| wins by killing their queen | 68 | 56 |
| losses to knights-only opponents / with giants | 25 / 7 | 34 / 2 |
| our HP at turn 100 (median) | 47 | 58 |
| our towers at turn 100 (summed HP) | 5 (3,180) | 4 (2,364) |
| our tower top-ups per game | 73 | 36 |

| under knights (median over games with contact) | v06, losses | v07, losses | v07, wins | opponents who beat v07 |
| --- | --- | --- | --- | --- |
| contact turns per game | 50 | **59** | 25 | 25 |
| HP lost per contact turn | 1.2 | **0.9** | **0.3** | 1.0 |
| % MOVE while in contact | 0 | 75 | 89 | 28 |
| % at an own tower (within 130) while in contact | 90 | **34** | 43 | 77 |
| distance moved per contact turn | 14 | 54 | 50 | 25 |

1. **The kiting works as a mechanism.** Under knights v07 MOVEs on 75-89 % of the turns and loses 0.9 HP
   per contact turn in its losses and 0.3 in its wins (v06: 1.2 and 0.9); its queen has 58 HP at turn 100
   against v06's 47.
2. **But the knights do not die.** v07's queen spends 59 turns in contact in its losses (v06: 50) and only
   34 % of them near a tower (v06: 90 %): she runs, the knights follow, and the towers, which shoot the
   creep nearest to *themselves*, have nothing in range. The chases last 50-130 turns (the losses table in
   `v07.txt`); against v06's synchronous waves of 8 the same rule looked like a cure because v06 stands still
   and its towers kill its own pursuers' targets. The cover condition only binds while she is covered now:
   once a step leaves the cover, every later step is free.
3. **The running queen builds less.** Top-ups fell from 73 to 36 per game and the line from 5 towers at
   3,180 HP to 4 at 2,364; wins by killing the enemy queen fell from 68 to 56 (the kills come from knights
   and the thinner line punishes less). The opponents who beat v07 hold a thicker line than those who beat
   v06 (2,396 HP against 1,676) with 11 waves.
4. **Losses on HP at the limit rose** from 9 to 11: in those games she is chased for 82-107 turns at 0.4-0.7
   HP a turn and ends with less HP than the opponent, who stood at his towers.
5. The arena's winners over v06 moved 36 units a turn and stayed near their towers 64 % of the time: they
   kite *around* the towers, not away from them.

## What follows

The rule to test next: kiting bound to the tower cover and the tower itself — the step must end within an
own tower's range always (no cover: the flight to the nearest tower as v06), and among those steps the
one farthest from the knights with a penalty for the distance from the covering tower, so that the
pursuers stay in its range and die; and a cap on consecutive kiting turns, after which she tops up. The
local proxy cannot judge this (both local opponents stand still under knights and rewarded the version the
arena did not), so a local no-regression check against v06 and `field`, then the arena, decide. The user
chooses between that and returning to v06 (re-uploading `1a9f3e81`, which joins its baseline).
