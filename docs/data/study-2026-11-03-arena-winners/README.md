# Study 2026-11-03: what the arena opponents who beat v06 do that v06 does not

**Question.** The economy experiments lose or do nothing against our mirror and against `field`, while
the arena opponents who beat v06 hold income 8. What else distinguishes them? Traces of both queens in
v06's 120 placement games, compared in four groups: v06 in its 33 losses and 87 wins, the opponents who
beat it and those who lost.

## Data and tools

- **Corpus:** v06's 120 placement games (uploads 1-2, agents 6842260 and 6842539).
- **Traces:** `cr.tools.DeathTrace <agent> all <game>` for every turn of our queen
  (`replays/study/traces/all-v06-ours.txt`) and of each opponent's queen (`all-v06-opp.txt`; the agent ids
  per game in `v06-games.txt`). Features: `replays/study/features-v06-2026-11-03.csv` (`cgStudy`). All
  gitignored, copied to the main checkout.
- **Script:** `winners.py` (output `winners.txt` beside it; run from the repository root).

## Findings (medians over games)

| under knights (contact = a knight within 250) | v06, losses | v06, wins | winners over v06 | losers to v06 |
| --- | --- | --- | --- | --- |
| contact turns per game | 50 | 20 | 36 | 34 |
| HP lost per contact turn | 1.2 | 0.9 | 1.0 | 1.4 |
| % of contact turns with MOVE | 0 | 0 | **42** | 26 |
| % of contact turns topping up a tower | 77 | 74 | 28 | 25 |
| % of contact turns at an own tower (within 130) | 90 | 84 | 64 | 61 |
| distance moved per contact turn | 14 | 17 | **36** | 32 |
| % MOVE when knights are 250-500 away (before contact) | 8 | 6 | **44** | 12 |
| % MOVE over the whole game | 8.5 | 9 | 36 | 24 |

| economy and towers at turn 100 | v06, losses | v06, wins | winners | losers |
| --- | --- | --- | --- | --- |
| income (mines) | 3 (2) | 3 (2) | **8 (3)** | 4 (2) |
| towers (summed HP) | 5 (3,108) | 5 (3,240) | 4 (1,676) | 4 (1,440) |
| knight waves per game | 7 | 6 | **12** | 7 |
| tower top-ups per game | 93 | 62 | 33 | 24 |
| mine upgrades per game | 4 | 3 | 11 | 6 |
| barracks built | 2 | 2 | 1 (13 of 33), 2 (14) | 1 (52 of 86) |

1. **We lose when we are pinned.** In its losses v06's queen spends 50 turns in contact with knights (20 in
   its wins), 90 % of them at a tower, 77 % topping it up; she never MOVEs under knights (the only MOVEs
   are the route planner's).
2. **The winners move.** Under knights they MOVE on 42 % of the turns and cover 36 units a turn (ours 14);
   when knights are still 250-500 away they already MOVE on 44 % of the turns (ours 8 %). The losers among
   the opponents move half as much. Moving costs them a third less HP per contact turn (1.0 against the
   losers' 1.4), with fewer knights on them at once (2.2 against 2.7).
3. **The winners earn and attack more, and pump less:** income 8 with 3 mines and 11 upgrades, 12 waves,
   33 top-ups; the losers among the opponents hold 4 towers at 1,440 HP too, so the thin line alone is not
   it. The economy follows from the queen's free turns: 33 top-ups against our 93.
4. Enemy towers took no HP from either side in these games (the trace counts towers covering the queen
   while no knight is near).

## What follows

The next flag of `current`: kiting under knights (`docs/data/exp-2026-11-03-kite/`): when knights are
near and the defence rule has nothing to build, MOVE away from the knights' centre, staying within own
tower cover, instead of standing at the tower topping it up; and the same before contact, when knights are
250-500 away. Both local opponents play knights only, so the effect is measurable locally (P2 with G4).
