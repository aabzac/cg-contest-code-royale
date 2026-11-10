# Experiment 2026-11-10: the hold at the towers only, without open ground (Gold, over v14)

## Hypothesis and success bar (written before the comparisons)

The measurement of v14's 180 Gold games (`docs/data/study-2026-11-10-v14-cover/`, section 7) found one place inside the
overlap rule to test further. A contact episode from turn 50 that began on open ground (the queen neither at an own
tower nor walking to a site) cost 3.40 HP against 1.13 for one begun at an own tower, at the same cover on its first
turn (3.02 and 3.11 towers); the knights that reached her there were fresher (their HP sum 22.3 against 13.0). Such
episodes are 0.7 a game in v14 (0.2 in v13) and cost 2.4 HP a game. The finding is observational: the positions in which
the rule picks open ground may be the harder ones.

In the code open ground is two of the three kinds of spot `overlapSpot` searches: the place she stands on and the
predicted positions on 24 headings; the third is the touch point of an own tower. v14 gives a tie to a touch point, so
it takes a spot off the towers whenever one more own tower covers it.

The user's decision of 2026-11-10 ("Да, проверь"): build the flag and test it locally.

The flag, off by default (`current` plays as v14; `MainBotTest.withDefaultsCurrentPlaysAsV14`):
- `OVERLAP_TOUCH=N`: a spot off the towers is taken only when it has more than N covering own towers above the best of
  the touch points within reach and the end of v13's walk. 0 is v14 (one more is enough); 1 asks for two more; 9 never
  takes one, so the hold and the flight stand at the towers' touch points only (the best covered one within five steps
  when it beats the end of v13's walk, else v13's command).

The arms: `OVERLAP_TOUCH=1` and `OVERLAP_TOUCH=9`, each against v14 (direct), `rival` and `field`.

**Hypothesis.** On the development seeds, both seats:
- the episodes begun on open ground fall towards v13's count, and the HP they cost with them;
- the HP the queen loses to knights from turn 50 falls, and she does not die more often;
- S against v14 rises above 0.50, with deltas against `rival` and `field` of at least −1 pp.

**The alternative**, which the same series can show: the spots off the towers are where the rule's gain comes from. Then
without them the share of contact turns under three or more towers falls back towards v13's (66 % → 53 % in the mirror's
profile of the overlap experiment) and S against v14 falls below 0.50.

**Success bar.** The expected effect is small (the open-ground episodes cost 2.4 HP a game on the arena, of 12.6 on
contact turns), so the decision is read on four development sets pooled: seeds 1, 3, 5 and 7 + 397 k, k < 500, 2000 maps
(the first two are the sets of the overlap experiment). For the arm taken further: S against v14 with its lower 95 %
bound above 0.50 on the pooled maps, deltas against `rival` and `field` of at least −1 pp, deaths per 400 profile games
not above v14's, and the mechanism moving as predicted (the HP of the episodes begun on open ground falls and the HP to
knights from turn 50 does not rise). Only then a P2 screen on a fresh block: screen-pv3 block 8, seeds
1008000000 + 397 i, used once. Otherwise the flag stays in the code, off, and the rule of v14 stands as it is.

## Tools

- `sweep.sh`, `sweep_table.py`: the arms against v14 (direct), `rival` and `field` with v14's control series, one
  development set a call (`BASE`); the table gives S with the changed maps and the deltas with their intervals for
  every set and for the sets pooled (`tools/bench_pair.py`; the maps of the sets are disjoint, so the pooled interval is
  taken over all their maps). Raw reports in `replays/bench/exp-2026-11-10-open-ground/dev-<base>/` (gitignored);
  `sweep.txt` is the table.
- `profile.sh`: the study's probe in its local mode (`docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java`), 200
  maps 1 + 397 k, both seats, of v14 and of each arm against v14, `rival` and `field`. `profile_table.py` of the
  overlap experiment reads them (`profiles.txt`); `begun.py` adds the episodes by where they began, with the arena
  study's definitions (`begun.txt`).
- Code: `cr.bot.Params.OVERLAP_TOUCH` (0 by default) and `MainBot.overlapSpot`, which now keeps the best of the towers'
  touch points and the best spot off the towers apart and compares them at the end.
- Tests: `StrategyParamsTest.overlapTouchKeepsTheHoldAtTheTowers`; the version identity
  `MainBotTest.withDefaultsCurrentPlaysAsV14`; the frozen snapshots `versions/v01-v14.properties` list the parameter at 0
  (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults`).

## Development sweep (`sweep.txt`, raw reports in `dev-1`, `dev-3`, `dev-5`, `dev-7`)

Seeds 1, 3, 5 and 7 + 397 k, k < 500, both seats, in-process (8 threads, 50 ms); each arm against v14 (direct), `rival`
and `field`, with v14's own series as the controls (v14 scores 0.8260-0.8610 against `rival` and 0.9805-0.9905 against
`field` on the four sets). Commit 63c2c7e with the uncommitted flag and records (the reports carry the status). Before
the sweep `current` at its defaults repeated v14's mirror game for game on the 500 maps of the first set
(`identity-current-v14.json`, `bench_pair.py direct`: 0 maps changed).

| arm | S vs v14, the four sets | pooled, 2000 maps (95 % CI) | maps changed against v14's mirror | delta vs rival, pooled | delta vs field, pooled |
| --- | --- | --- | --- | --- | --- |
| `OVERLAP_TOUCH=9`, the touch points only | 0.4935, 0.4830, 0.5030, 0.4840 | **0.4909 (0.4802..0.5016)** | 1602 | −0.0070 (−0.0178..+0.0038) | −0.0040 (−0.0080..−0.0000) |
| `OVERLAP_TOUCH=1`, open ground for two more towers | 0.4990, 0.4995, 0.5030, 0.4960 | **0.4994 (0.4889..0.5098)** | 1557 | −0.0004 (−0.0110..+0.0102) | −0.0026 (−0.0060..+0.0007) |

- **Neither arm gains.** The hold at the towers only loses about a point against v14, on three sets of the four, with
  small minuses against both other opponents; with open ground kept for two more towers the play is level with v14
  against all three.
- **The flag changes most games** (four maps in five against the mirror), so the level result is not a flag that
  rarely acts.
- **The arms against each other:** `OVERLAP_TOUCH=1` scores +0.0085 (+0.0022..+0.0148) above `OVERLAP_TOUCH=9` against
  v14 and +0.0066 (+0.0012..+0.0120) against `rival` on the same maps (+0.0014 against `field`). A spot off the towers
  that adds two towers or more is worth taking; one that adds a single tower changes nothing that the series can see.
- Slow turns of the arms (0-30 a series; v14 has them in its own control series too, and the burst of 18 and 30 fell
  on two series written within 15 seconds) are host noise at 8 threads and count for nothing in a development sweep:
  the games do not depend on the clock.

## Profiles (`begun.txt`, `paired.txt`, `profiles.txt`; the probe's csv files are not kept: `profile.sh` rebuilds them)

`ArenaProbe local`, 200 maps 1 + 397 k, both seats (400 games), against v14, `rival` and `field`.

**The local opponents show what the arena showed.** Contact episodes from turn 50 of v14, by where they began:

| v14 against | begun on open ground: a game / HP a game / HP each | begun at an own tower: a game / HP each | own towers covering her on the first turn, open ground / tower | knights' HP on it |
| --- | --- | --- | --- | --- |
| v14 | 0.70 / 1.61 / 2.30 | 5.51 / 0.44 | 3.04 / 3.46 | 20.4 / 12.4 |
| `rival` | 0.80 / 1.71 / 2.13 | 7.56 / 0.49 | 2.53 / 2.91 | 14.7 / 10.5 |
| `field` | 0.87 / 0.43 / 0.49 | 9.58 / 0.09 | 2.87 / 3.11 | 11.6 / 8.8 |
| the arena (Gold, the measurement) | 0.7 / 2.4 / 3.40 | 6.2 / 1.13 | 3.02 / 3.11 | 22.3 / 13.0 |

In the gate's state from turn 50 v14 spends 5.4 turns a game in the mirror on a MOVE to a spot off the towers (5.2 on
the arena), 6.7 on a walk to an own tower and 16.4 topping up the tower it touches.

**The class is a mixture, and the rule's own spots are its cheap part.** The same episodes by what the queen did on the
turn before them (`begun.txt`; the arena's lines were added to the measurement's `cover.txt`, section 5, with this
experiment), as episodes a game / HP a game / HP each:

| v14 against | a MOVE with a knight within 500: the hold's spot off the towers | the upgrade of an own mine she touches | a new structure on the free site she touches | an episode begun at an own tower, HP each |
| --- | --- | --- | --- | --- |
| v14 | 0.44 / 0.49 / 1.11 | 0.19 / 0.59 / 3.09 | 0.06 / 0.45 / 7.46 | 0.44 |
| `rival` | 0.43 / 0.50 / 1.15 | 0.25 / 0.50 / 1.97 | 0.12 / 0.71 / 6.17 | 0.49 |
| `field` | 0.59 / 0.19 / 0.32 | 0.25 / 0.19 / 0.77 | 0.03 / 0.05 / 1.58 | 0.09 |
| the arena (Gold) | 0.46 / 0.86 / 1.86 | 0.15 / 0.56 / 3.74 | 0.08 / 0.84 / 10.86 | 1.13 |

- The hold's own spots off the towers are half to two thirds of the class and about a third of its HP: 1.1 HP an
  episode in the mirror and against `rival` and 1.86 on the arena, under 3.0-3.6 own towers. That is 2.3-2.5 times an
  episode begun at a tower locally and 1.6 times on the arena, not three.
- The expensive episodes are those the touch rule begins: she upgrades a mine or builds on a free site she touches
  while a knight is within 500, under 1.6-2.4 towers, and the knights reach her fresh (HP sum 27-40 in the mirror, 33-36
  on the arena, against 12-16 at a tower or at the hold's spot). They are a third to a half of the class and hold
  1.0-1.2 HP a game in the mirror and against `rival` and 1.4 on the arena. v13 has them too (0.16 a game and 0.71 HP
  a game on the arena), and the flag of this experiment does not touch them.

**What the arms change** (the arm minus v14 on the same maps, 95 % interval of a bootstrap over maps; v14's value in
brackets), for `OVERLAP_TOUCH=9`:

| a game | against v14 | against `rival` | against `field` |
| --- | --- | --- | --- |
| episodes begun on open ground | −0.49 (−0.59..−0.40) [0.70] | −0.48 (−0.58..−0.40) [0.80] | −0.57 (−0.70..−0.44) [0.87] |
| their HP | −0.73 (−1.29..−0.19) [1.61] | −0.60 (−1.32..+0.08) [1.71] | −0.23 (−0.44..−0.05) [0.43] |
| HP of the episodes begun at or on a walk to an own tower | +0.23 (−0.56..+1.04) [3.34] | +0.81 (−0.35..+2.09) [4.59] | +0.14 (−0.30..+0.58) [1.06] |
| HP to knights from turn 50 | −0.42 (−1.37..+0.54) [5.38] | +0.30 (−0.99..+1.55) [6.53] | −0.10 (−0.57..+0.39) [1.50] |
| contact turns under one or two own towers | +0.90 (+0.05..+1.77) [8.73] | +1.09 (−0.22..+2.47) [17.51] | +2.00 (+0.84..+3.18) [10.65] |
| share of contact turns under three or more, points | −2.8 (−5.5..−0.2) [65.0] | −2.7 (−5.1..−0.5) [47.6] | −4.6 (−7.5..−1.7) [64.7] |
| queen deaths per 400 games | −2 (−7..+3) [10] | +3 (−5..+12) [20] | 0 (−4..+4) [2] |

- **The first prediction holds:** the episodes begun on open ground fall to 0.21-0.32 a game and cost 0.2-0.7 HP a
  game less. The MOVEs of the gate's state go to walks to an own tower (6.7 → 8.2 a game in the mirror) and to top-ups
  (16.4 → 19.7).
- **The second does not:** the HP she loses to knights from turn 50 does not fall (−0.4, +0.3, −0.1, every interval
  across zero). Part or all of what the open-ground episodes save, those begun at the towers take back (+0.2, +0.8 and
  +0.1 HP a game: not established one by one, of one sign against all three opponents).
- **The cover falls**, as the alternative said, by 3-5 points of the contact turns under three or more own towers, with
  one to two contact turns a game more under one or two. It does not fall back to v13's: 62 % in the mirror, between
  v13's 53 % and v14's 65 % (v13's from the overlap experiment's profile of v13 against v13 on the same maps). The
  towers' touch points carry most of the rule; the spots off the towers add the rest.
- **Deaths** are level (−2, +3, 0 per 400 games, inside the noise).
- **The open-ground episodes that remain** (0.21, 0.32 and 0.30 a game, at 4.25, 3.48 and 0.64 HP each) are the touch
  rule's: the mine upgrades and the new structures of the table above, as many and as costly as in v14 (0.17 and 0.04 a
  game in the mirror, 0.40 and 0.49 HP a game). The episodes the flag removes are the cheap ones, 0.44 a game at
  1.11 HP each in the mirror, and she then meets the same knights at a tower under fewer towers.
- `OVERLAP_TOUCH=1` moves the same way and less: the open-ground episodes fall as far (−0.44, −0.48, −0.54 a game;
  of v14's 5.4 off-tower turns a game in the mirror 0.7 stay, so nearly nine in ten are for one more tower), the cover
  by 1-2 points (not established), the HP from turn 50 by −0.4, +0.6 and −0.3 (not established).

## Verdict

**Not adopted.** Neither arm meets the bar: S against v14 is 0.4909 (0.4802..0.5016) for the touch points only and
0.4994 (0.4889..0.5098) with open ground kept for two more towers, where the bar asked for a lower bound above 0.50;
the HP lost to knights from turn 50 does not fall. No P2 screen follows and screen-pv3 block 8 stays unused.
`OVERLAP_TOUCH` stays in the code at 0 and v14's rule stands as it is.

What the experiment settles about the measurement's finding:
- **The finding was read too broadly.** "An episode begun on open ground costs three times one begun at a tower" is
  true of the class, locally as on the arena, but the class mixed the hold's spots off the towers with the queen caught
  upgrading a mine or building on a free site. The hold's own spots cost 1.86 HP an episode on the arena against 1.13
  at a tower, and hold 0.86 of the class's 2.4 HP a game. The measurement's README is corrected (its section 7).
- **The rule's choice of a spot off the towers is not a leak.** Taking the spots away lowers the cover and saves no
  HP. The choice is right where the spot adds two towers or more (+0.85 pp against v14 for taking them, the interval
  above zero) and makes no measurable difference where it adds one.
- **What the class points to instead** is the touch rule in the gate's state: the upgrade of a touched mine and a
  build on a touched free site come before the hold. On the arena 0.4 of the gate's turns a game go to a mine upgrade
  and about as many to a new structure (the measurement's `cover.txt`, section 3), and the episodes begun there cost
  1.4 HP a game (0.71 in v13). Not tested here: a flag that puts the hold before those two would also cost the
  upgrades and structures it skips. Tested next (`docs/data/exp-2026-11-10-gate-touch/`): the HP is saved and the
  cost is as large; not adopted.

## Limits

- The local opponents punish the hold's spots off the towers less than the Gold field: 0.5 HP a game in the mirror and
  against `rival`, 0.86 on the arena. The arena could judge the flag differently; nothing local gives a reason to
  spend an upload on it.
- The profiles are 400 games an arm and opponent: differences of half an HP a game are inside their noise, which is why
  the decision is read on S over 2000 maps.
- Development seeds only; no fresh block was used.

## Checks

- `./gradlew test`: 185 tests, 3 skipped (the corpus tests, for want of the arena replays in the worktree), no failure.
- `./gradlew submit` builds the artifact of `current` with the flag in it: 95,555 characters (limit 100,000), build
  `0f66bcd4`, config `1fe785e2`, sha256 `aef954a7d124ba6be2877651ea4dc1082fd43fb99518a501a38a619bf618a1f9`. It plays as
  v14 (the identity series above) and is not a candidate; v14's artifact stays `artifacts/e4c8eeec/Player.java` in the
  main checkout. The worktree's `build/submit/Player.java` now holds this build, not v14's bytes.
