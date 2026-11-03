# Experiment 2026-11-03: the income after the ladder (INCOME_LATE), P2 strategy screen against v06

From the profile of v06's 120 arena games (`profile.py`, `profile.txt` beside this file; the tools of
`docs/data/study-2026-11-03-queen-safety/`): 87 wins; of the 33 losses 24 are the queen's death (median turn
136, only 3 before turn 80: the second tower under threat closed the early deaths) and 9 the HP race. The
opponents who beat v06 hold income 8 at turn 100 with 3 mines against our 3 with 2, release 12 knight waves
against our 6, and keep 4 towers at 1,676 HP against our 5 at 3,180; our queen spends 74 turns a game on
top-ups. The earlier income experiment (`docs/data/exp-2026-11-03-mines-first/`, INCOME_TARGET=5: 0.29
against v04) raised the income *before* the towers and delayed the line; this one raises it *after* the
ladder, out of the top-up turns. The direction chosen by the user after v06's selection (2026-11-03).
Starting revision: `3eef83c`, clean worktree `claude/bot-improvement-direction-4fe687`; frozen v06, the
referee and the harness unchanged. Local Java 17.0.12, Gradle 8.11.1, Windows 11, 8 bench threads.

## The parameters (code)

`cr.bot.Params`, read by `MainBot` (`current`); the defaults play as v06 command for command
(`MainBotTest.withDefaultsCurrentPlaysAsV06`; every version snapshot records the new field at the frozen
behaviour):

- `INCOME_LATE` (0): the income target once the ladder is complete. Below it, with no knight near, the
  queen walks to the nearest of a free gold site (a new mine) and an own mine below its maximum (an
  upgrade) to the midpoint of herself and home, and builds there (`MainBot.lateSite`; ROUTE plans the walk
  as for a ladder site); the touch rule builds a mine on a free gold site in that phase too. With the
  target met, or a knight near, v06's idle behaviour (pump the home tower) and flight.
- `TOWER_HP` as before (the top-up threshold of a touched tower), swept together with it.

Tests: `StrategyParamsTest.lateIncomeRaisesTheIncomeAfterTheLadder`, the version identity tests of
`MainBotTest` and `BotFactoryTest`.

## Development sweep (seeds 10000-10499, 500 maps, both seats, against v06; the v06 mirror on the same maps)

`replays/bench/dev-late-income-2026-11-03/` (gitignored; `sweep-a.sh` and `sweep_table.py` beside this
file; the full table is `dev-sweep.txt`). One JVM per arm, `BenchmarkRunner` directly, 8 threads, 50 ms.

| Arm (the rest at v06's values) | S vs v06 (95 % CI) | Maps changed | |
| --- | --- | --- | --- |
| defaults (INCOME_LATE=0) | 0.500 | 0 | the code at its defaults is v06 |
| INCOME_LATE=4 / 5 / 6 | 0.505 / 0.513 (0.494-0.532) / 0.508 | 126 / 221 / 268 | within noise of 0.50 |
| INCOME_LATE=8 | 0.485 | 308 | |
| INCOME_LATE=6 with TOWER_HP=600 / 500; 8 with 500 | 0.428 / 0.368 / 0.360 | 498 | thinner top-ups lose, as against v04 |

What the sweep says: raising the income after the ladder is neutral against v06 (at most +1 pp, inside the
500-map interval), and buying the queen's time with a lower top-up threshold loses 7-14 pp. A game trace
(`./gradlew run "-Pbot0=current?INCOME_LATE=6" -Pbot1=v06 -Pseed=10001 -Pprint=1`) shows why the phase
gets little time: v06's ladder (two mines, two barracks, six towers) runs to about turn 120, and from then
on the enemy's synchronous waves keep knights within 250 of the queen most of the time, so the threat
rules (the flight, the top-up at the tower she fled to) hold her for 20-40 turns at a stretch between
short mine trips. The arena opponents who beat v06 with income 8 also hold 4 towers at 1,700 HP and 12
waves: a different balance, not a flag on top of ours. Both local opponents are our own code, so the
economy's value against the field stays unmeasured locally (the same caveat as `exp-2026-11-03-mines-first`).

**Verdict:** no candidate; `INCOME_LATE` stays in the code off by default (a free gold site at the queen's
feet in the late phase is still a mine with it on). Not screened. Next, if the economy is to be measured
at all: a local opponent that plays the field's balance (thinner towers, more mines, giants) — the backlog
item of `docs/PLAN.md`.
