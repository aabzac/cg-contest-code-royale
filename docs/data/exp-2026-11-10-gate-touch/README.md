# Experiment 2026-11-10: the hold before the touch rule's upgrades and builds (Gold, over v14)

## Hypothesis and success bar (written before the comparisons)

The open-ground experiment (`docs/data/exp-2026-11-10-open-ground/`) took apart the contact episodes from turn 50
that begin away from the towers. The expensive ones are not the hold's spots: they begin while the queen upgrades an
own mine she touches, or builds on a free site she touches, with an enemy knight already within 500.

| v14 against | begun at a mine upgrade: a game / HP a game / HP each | begun at a new structure | begun at an own tower, HP each |
| --- | --- | --- | --- |
| v14 | 0.19 / 0.59 / 3.09 | 0.06 / 0.45 / 7.46 | 0.44 |
| `rival` | 0.25 / 0.50 / 1.97 | 0.12 / 0.71 / 6.17 | 0.49 |
| `field` | 0.25 / 0.19 / 0.77 | 0.03 / 0.05 / 1.58 | 0.09 |
| the arena (Gold) | 0.15 / 0.56 / 3.74 | 0.08 / 0.84 / 10.86 | 1.13 |

They are met under about two own towers by fresh knights. In the code the touch rule comes before the gate's hold
(`LADDER_CLEAR`): the hold replaces a walk to a new site, but a mine she touches is upgraded and a free site she touches
is built on while the knights close from 500 to 250. `OVERLAP_FIRST` (rejected in the overlap experiment) was a
different rule: it moved her from a top-up or an upgrade only to a better-covered spot within reach, and it took the
tower top-ups too.

The user's decision of 2026-11-10 ("Да, проверь"): build the flag and test it locally.

The flag, off by default (`current` plays as v14; `MainBotTest.withDefaultsCurrentPlaysAsV14`):
- `CLEAR_TOUCH=B`, a sum of 1, 2 and 4. In the gate's state (after the first-wave window, two own towers, an enemy
  knight within `LADDER_CLEAR` and none within `THREAT_RANGE`) the hold comes before the touch rule's
  - 1: upgrade of an own mine she touches;
  - 2: new mine or barracks on a free site she touches;
  - 4: new tower on a free site she touches.

  The hold then acts as if she touched nothing: the best-covered spot within five steps, or her nearest own tower. When
  it leaves her standing where she is, or finds no tower to go to, the touch rule's build stands, since it costs her
  nothing. The top-up of a tower she touches is not changed.

The arms: `CLEAR_TOUCH=1` (the main one: the mine upgrades are the most frequent of these episodes and stay several
turns), `2`, `4`, `3` and `7`, each against v14 (direct), `rival` and `field`.

**Hypothesis.** On the development seeds, both seats:
- the episodes begun at a mine upgrade (arm 1) or at a new structure (arms 2 and 4) fall, and the HP they cost;
- the HP the queen loses to knights from turn 50 falls, and she does not die more often;
- S against v14 rises above 0.50, with deltas against `rival` and `field` of at least −1 pp.

**The cost, which the same series show:** every skipped upgrade is income lost for the rest of the game (she upgrades
a mine only when she happens to touch it), and a skipped build is a second walk to the site. If that outweighs the HP,
the income at turns 100-150, the towers standing and the knight releases fall, and S against v14 does not rise.

**Success bar** (the open-ground experiment's): on the four development sets pooled (seeds 1, 3, 5 and 7 + 397 k,
k < 500; 2000 maps), for the arm taken further: S against v14 with its lower 95 % bound above 0.50, deltas against
`rival` and `field` of at least −1 pp, deaths per 400 profile games not above v14's, and the mechanism moving as
predicted (the HP of the episodes it targets falls, the HP to knights from turn 50 does not rise). Only then a P2
screen on a fresh block: screen-pv3 block 8, seeds 1008000000 + 397 i, used once, for the single arm chosen here.
Otherwise the flag stays in the code, off.

## Tools

- The open-ground experiment's `sweep.sh` and `sweep_table.py` with this experiment's directories
  (`OUT=replays/bench/exp-2026-11-10-gate-touch/dev-<base> BASE=<base> bash docs/data/exp-2026-11-10-open-ground/sweep.sh
  ARM ...`; `sweep_table.py replays/bench/exp-2026-11-10-gate-touch`): the arms against v14 (direct), `rival` and
  `field` with v14's control series, a line for every development set and one for the sets pooled
  (`tools/bench_pair.py`), and the arms against each other. Raw reports are gitignored; `sweep.txt` is the table.
- The open-ground experiment's `profile.sh` (`OUT=replays/bench/exp-2026-11-10-gate-touch/profiles`): the study's probe
  in its local mode (`docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java`), 200 maps 1 + 397 k, both seats, of
  v14 and of each arm against v14, `rival` and `field` (v14's three profiles are those of the open-ground experiment,
  copied). `touch.py` reads them (`touch.txt`): the episodes begun at an upgrade or a build, the gate's turns that go to
  them, the income, the structures and the releases, and the arm minus v14 on the same maps with bootstrap intervals.
- Code: `cr.bot.Params.CLEAR_TOUCH` (0 by default) and `MainBot.play` (the touch rule's build is set aside in the
  gate's state and restored when the hold leaves her standing or does not apply; `gateOpen`).
- Tests: `StrategyParamsTest.clearTouchPutsTheHoldBeforeUpgradesAndBuilds`; the version identity
  `MainBotTest.withDefaultsCurrentPlaysAsV14`; the frozen snapshots `versions/v01-v14.properties` list the parameter at 0
  (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults`).

## Development sweep (`sweep.txt`, raw reports in `dev-1`, `dev-3`, `dev-5`, `dev-7`)

Seeds 1, 3, 5 and 7 + 397 k, k < 500, both seats, in-process (8 threads, 50 ms), 21:26-21:41; each arm against v14
(direct), `rival` and `field`, with v14's own series as the controls, run again for this experiment (the open-ground
experiment's control reports name one parameter fewer, and `bench_pair.py` refuses the pair). Commit 63c2c7e with the
uncommitted flags and records (the reports carry the status). Before the sweep `current` at its defaults repeated
v14's mirror game for game on the 500 maps of the first set (`identity-current-v14.json`, `bench_pair.py direct`: 0 maps
changed, no failure).

| arm | S vs v14, the four sets | pooled, 2000 maps (95 % CI) | maps changed against v14's mirror | delta vs rival, pooled | delta vs field, pooled |
| --- | --- | --- | --- | --- | --- |
| `CLEAR_TOUCH=1`, the mine upgrade | 0.5000, 0.5130, 0.4990, 0.5015 | **0.5034 (0.4977..0.5090)** | 511 | +0.0045 (−0.0019..+0.0109) | +0.0005 (−0.0018..+0.0028) |
| `CLEAR_TOUCH=2`, a new mine or barracks | 0.4995, 0.5040, 0.4980, 0.4985 | 0.5000 (0.4934..0.5066) | 585 | −0.0009 (−0.0078..+0.0061) | −0.0010 (−0.0036..+0.0016) |
| `CLEAR_TOUCH=4`, a new tower | 0.4850, 0.4930, 0.4935, 0.4900 | **0.4904 (0.4824..0.4983)** | 729 | −0.0164 (−0.0244..−0.0084) | −0.0035 (−0.0061..−0.0009) |
| `CLEAR_TOUCH=3`, 1 and 2 | 0.5030, 0.5155, 0.5025, 0.5055 | **0.5066 (0.4991..0.5142)** | 773 | +0.0009 (−0.0069..+0.0086) | −0.0009 (−0.0040..+0.0023) |
| `CLEAR_TOUCH=7`, all three | 0.4900, 0.5090, 0.4910, 0.4940 | 0.4960 (0.4862..0.5058) | 1180 | −0.0158 (−0.0261..−0.0054) | −0.0054 (−0.0093..−0.0015) |

- **No arm meets the bar.** The two best, the mine upgrade alone and with the new mine or barracks, score 0.5034 and
  0.5066 against v14 with lower bounds of 0.4977 and 0.4991, and are level against `rival` and `field`.
- **The hold before a new tower loses**: 0.4904 against v14 (the interval below 0.50), −1.6 pp against `rival` and
  −0.35 pp against `field`, both intervals below zero; the bundle of all three loses as much against `rival` and
  `field`. A tower on the free site she touches is worth its turn with the knights within 500.
- **The mine upgrade is the only part with a plus**, and a small one: +0.34 pp against v14 alone (not established) and
  +0.66 pp (+0.16..+1.16) on top of the new mine or barracks (`CLEAR_TOUCH=3` minus `CLEAR_TOUCH=2` on the same maps),
  with +0.45 and +0.18 pp against `rival` and nothing against `field`.
- **A new mine or barracks set aside changes nothing** (0.5000; 3 minus 1: +0.32 pp against v14, −0.36 pp against
  `rival`, neither established).
- The single bits and the pair act on 26-39 % of the maps (511-773 of 2000 changed against the mirror), all three bits
  on 59 %.
- Slow turns at 8 threads: at most 8 in a series, in the arms' series and in v14's controls alike. Host noise.

## Profiles (`touch.txt`; the probe's csv files are not kept: `profile.sh` rebuilds them)

`ArenaProbe local`, 200 maps 1 + 397 k, both seats (400 games), against v14, `rival` and `field`, of the arms 1, 3 and
4 (2 and 7 were not profiled). The arm minus v14 on the same maps, with the 95 % interval of a bootstrap over maps;
v14's value in brackets.

**`CLEAR_TOUCH=1`: the HP is saved as predicted, and paid for in upgrades.**

| a game | against v14 | against `rival` | against `field` |
| --- | --- | --- | --- |
| episodes begun at a mine upgrade | 0.19 → 0.01 | 0.26 → 0.00 | 0.25 → 0.00 |
| their HP | −0.55 (−0.82..−0.30) [0.59] | −0.49 (−0.84..−0.22) [0.50] | −0.19 (−0.29..−0.11) [0.19] |
| HP to knights from turn 50 | −0.66 (−1.39..−0.12) [5.38] | −0.39 (−1.15..+0.33) [6.53] | −0.14 (−0.40..+0.11) [1.50] |
| queen deaths per 400 games | −2 (−6..+1) [10] | −2 (−7..+2) [20] | 0 (−3..+3) [2] |
| mine upgrades | −0.53 (−0.73..−0.35) [7.54] | −0.76 (−0.94..−0.58) [6.08] | −0.84 (−1.00..−0.68) [4.04] |
| income at turn 150 | −0.08 (−0.16..−0.01) [3.59] | −0.13 (−0.21..−0.06) [3.52] | −0.25 (−0.35..−0.16) [2.13] |
| knight releases | −0.06 (−0.12..−0.00) [8.36] | −0.06 (−0.13..+0.01) [8.04] | −0.18 (−0.28..−0.09) [7.00] |
| the enemy queen's HP to our knights from turn 50 | −0.35 (−0.84..+0.03) [5.38] | −0.75 (−1.48..−0.08) [14.98] | −0.82 (−1.52..−0.18) [21.47] |

- **The mechanism moves as predicted.** The episodes begun at a mine upgrade vanish with all of their HP, and in the
  mirror the HP she loses to knights from turn 50 falls by 0.66 a game, the interval below zero (not established
  against `rival` and `field`). She dies no more often.
- **The cost is as large.** Half an upgrade to 0.8 of one a game is never made (she upgrades a mine only when she
  happens to touch it), the income at turn 150 is 0.1-0.25 lower, and the enemy queen loses 0.35-0.8 HP a game less to
  our knights, established against `rival` and `field`. What our queen keeps, theirs keeps too.
- Towers, mines and barracks at turn 150 are v14's.

**`CLEAR_TOUCH=3`** adds the new mine or barracks: against `rival` the episodes begun at a new structure cost 0.29 HP a
game less (−0.59..−0.02), with 0.10 fewer own towers and 0.04 fewer barracks at turn 150; in the mirror nothing moves
beyond arm 1 (HP from turn 50 −0.78, −1.54..−0.16).

**`CLEAR_TOUCH=4`** shows why the new tower must be built: 0.15, 0.09 and 0.20 fewer own towers at turn 150 against
the three opponents (every interval below zero), the queen's HP from turn 50 +0.34 and +0.71 in the mirror and against
`rival` (not established), and the enemy queen's −0.73 (−1.29..−0.28) in the mirror; the profile's score is −0.036
(−0.063..−0.010) against v14.

## Verdict

**Not adopted.** No arm meets the bar (a lower 95 % bound of S above 0.50 on the 2000 pooled maps): 0.4977 for the mine
upgrade, 0.4991 with the new mine or barracks, the others level or losing. No P2 screen follows and screen-pv3 block 8
stays unused. `CLEAR_TOUCH` stays in the code at 0.

What the experiment settles:
- **The leak is real in HP and not free to close.** Leaving the mine when the knights come within 500 saves the queen
  what the measurement attributed to those episodes (0.5 HP a game in the mirror), and the upgrades she then never
  makes cost the same on the other side of the board. The net is +0.3 to +0.7 pp in the mirror, below what 2000 maps
  can establish and, at Gold's scale, under a tenth of a rating point.
- **A new tower on the touched site is worth building under an approaching wave**; walking away from it costs a point
  against v14 and 1.6 against `rival`.
- **The income shows its price locally.** A tenth to a quarter of a gold a turn less at turn 150 is 0.35-0.8 HP a game
  less damage to the enemy queen.

## Limits

- Development seeds only; several arms on the same maps. The contrast that excludes zero (arm 3 over arm 2) is one of
  several looked at.
- The profiles are 400 games an arm and opponent; the arms 2 and 7 have series only.
- Not tried: the upgrade put off rather than dropped (she returns to the mine once no knight is within 500). It would
  keep the income; what it could add is bounded by the half point above.

## Checks

- `./gradlew test`: 186 tests, 3 skipped (the corpus tests, for want of the arena replays in the worktree), no failure.
- `./gradlew submit` builds the artifact of `current` with both new flags in it: 96,486 characters (limit 100,000),
  build `4a7b89cd`, config `1c638437`, sha256 `2f983ba2e5dd9246dd152b14610b5a2ee72beb5a04a6c1c0d6c528bca4c56a68`. It plays
  as v14 (the identity series) and is not a candidate. `CLEAR_TOUCH` takes 931 of those characters (the build before it
  had 95,555).
