# Local screen: policy P2 (2026-10-30)

Status: **adopted 2026-10-30** by the user, as proposed in that day's session, with the old bot added to
the opponent panel and the boss's exact rating left for later. P2 is the local part of the promotion
policy. The arena step stays P1 (`docs/arena-uploads.md`). A candidate first passes P2, then the artifact
checks (`submit`, both `procCheck` runs), then P1 on the arena.

## Why

- Arena noise: M = 3.01 points (P1, 2 df, provisional). The arena catches only large regressions, so an
  improvement has to be shown locally.
- League target. On the public Bronze leaderboard (2026-10-30 08:21, 2291 players) the top rows stood at
  27.75 (still placing, 33 %), then 27.14, 27.01 and 27.00. No row was eligible for promotion, so the boss
  stands above about 27.1. v01's pooled rating is 17.92, a gap of about 9 points. The boss's exact score
  needs an IDE game against it (`gameResult.agents[].score`); that is left for later.
  Snapshot: `docs/data/leaderboard-bronze-2026-10-30.json`.
- Local games are cheap. 1,000 in-process games take about 9 s; against a process opponent (3 threads,
  200 ms) 120 games take about 14 s.
- Every bot is deterministic: our bots run with SEED=1, and `Boss.java` has no Random. A rerun on the
  same seeds repeats the same games and adds no evidence.

## Roles

- **Candidate**: `current` with its flag on. Unrelated flags are off.
- **Control**: the version the candidate would replace, which is the local champion. Now v01.
- **Local champion**: the last version that passed P2. Its configuration becomes the `current` defaults,
  and the next candidates are screened against it.
- **Selected version**: the arena's (P1). Now v01.

## Opponents and proxy validation PV1

| Spec | Role | What it is |
| --- | --- | --- |
| the control | G1, direct | the version the candidate would replace |
| `boss` | G2, floor | the referee repository's Default AI (`refs/referee-csj/config/level4/Boss.java`, csj/code-royale@349924a): one knight barracks, mines to income 10, then towers, no evasion. v01 wins 95 % of games against it. It is not a model of the live boss "Princess Boss", which stands above about 27.1 |
| `old` | G3, stronger reference | the code of the replaced entry 3315948 (long-run 19.64 in Bronze), which the user keeps from the IDE at `refs/old-3315948/Player.java` (gitignored). It is compiled into `build/opponents/old` and runs as a process. It is not part of our bot. Pending: the code is not in place yet |

Process opponents run with `-Pthreads=3 -PturnMs=200`, which avoids timeouts from CPU contention.

**PV1** (2026-10-30). Script and output: `docs/data/pv1-2026-10-30/pv1.py` and `pv1.txt`.
- Arena, v01's 180 placement games:
  - Opponents built knight barracks in 96 % of games, archer barracks in 17 %, giant barracks in 12 %
    (archer or giant: 24 %), towers in 92 % and mines in 94 %.
  - v01 won 54 % of the 129 games against knight-only opponents.
  - Games lasted 110.4 turns on average (median 86.5), and 13 % reached the 250-turn limit.
- Local:
  - v01 against `boss`: 81.8 turns, none at the limit (60 maps).
  - The near-mirror `current?MAX_TOWERS=3` against v01: 151.7 turns, 11 % at the limit (500 maps). In 439
    of its 500 maps each bot won one game, so the seat decides most maps there.
- Both local opponents play knights only, so the arena's archer and giant players are not represented.
- Calibration with `old`: from the arena ratings v01 should score about 0.22 against it (17.92 against
  19.64 at the logistic scale 1.33). If the local score differs by more than 0.10, local play and the arena
  order this pair differently, and P2 is reviewed before the next decision.
- PV1 lapses on a league change, when a proxy changes, or when P1 rolls back a candidate that passed P2.
  Until it is renewed, candidates are developed but not promoted.

## Seeds

The map is `MapGen.generate(seed)`: `java.util.Random(seed)`, validated against CG maps. Each map is
played twice with the seats swapped, by fresh bots. The ledger `docs/data/seed-ledger.json` records each
block's use; a used block becomes development data.

| Pool | Seeds | Use |
| --- | --- | --- |
| development | every seed below 200000, the blocks used so far, the fixtures | free reuse, exploratory evidence |
| screen | block k: 200000 + 2000 k .. + 1999 | one fresh block per candidate: in-process series play all 2000 maps, process opponents the first 500 |
| final evaluation | block k: 400000 + 2000 k .. + 1999 | once per upload candidate |

## Metric

- **Map score**: botA's mean over the map's two games (win 1, draw 1/2, loss 0).
- **S** (direct): the candidate's mean map score against the control. The 95 % interval is
  S ± 1.96 SD / √maps, with maps as the samples, as the bench prints it.
- **Δ** (common opponent): the mean over maps of the candidate's map score minus the control's, both
  against the same opponent on the same maps, with the interval over maps.
- Context only: at the arena's logistic scale, S corresponds to about 1.33 · ln(S / (1 − S)) rating points
  against the control (S = 0.55: +0.27, S = 0.60: +0.54). A head-to-head score is not the field.

## Gates for a strategy candidate

| Gate | Rule |
| --- | --- |
| G0 | The candidate has no error, timeout, slow turn or extra line in any series |
| G1 | Direct, the 2000 maps of a fresh screen block: the lower 95 % bound of S is above 0.50 |
| G2 | `boss`, 500 maps of the block: Δ ≥ −2 pp |
| G3 | `old` (once present), 500 maps of the block: Δ ≥ −2 pp. Until then G3 is skipped, and the record says so |

- **Replication** is needed only when several arms were screened on one block: the chosen arm repeats
  G0-G3 on a new screen block.
- A candidate that passes becomes the local champion.
- **Upload recommendation**: when the local champion passes G0-G3 against the selected version on a fresh
  final-evaluation block, with S ≥ 0.60. The user decides whether to upload. The upload then follows the
  artifact checks and P1.

## Targeted fix

A targeted fix is a change whose games differ from the control's mirror (the control against itself on
the same maps) on fewer than 10 % of the block's maps. It needs:
- a regression test of its mechanism;
- G0;
- S ≥ 0.500 as a point estimate, with no interval requirement;
- Δ ≥ −1 pp against `boss`.

It joins the local champion and needs no upload of its own.

## Draws, failures, sample size

- A draw counts 1/2 and is reported. The simulator scores a tie as a draw; CG's tie-break is unresolved
  (it ranked player 0 first in the 2 known games).
- Any failure of the candidate fails G0, whatever the score.
- If the opponent or the control fails in a game (an error, a timeout or extra lines), the map is dropped
  from both arms. More than 1 % of the maps dropped makes the series invalid; it is then rerun with fewer
  threads or a larger turnMs.
- Series sizes are fixed: no early stop, no extension. In-process series run with `-Pthreads=8
  -PturnMs=50`.

## Records

- Raw reports go to `replays/bench/<experiment>/` (gitignored, reproducible from the commit and the seeds).
  A report holds every game, the specs with their full effective configuration (`BotFactory.describe`:
  parameters, or a program's source sha256), the commit with its status, the sha256 of the tracked diff
  and of each untracked file, the threads, turnMs and the Java version.
- Summaries go to `docs/data/exp-<date>-<name>/`: the `bench_pair.py` outputs (`--out`), with the
  experiment's commands.
- Each experiment gets a DEVLOG entry with the hypothesis, the arms, the gates and the verdict, and its
  blocks are entered in the ledger.

## Commands

For a candidate `current?FLAG=1` against the control v01 on screen block 0 (seedBase 200000):

```
./gradlew bench "-Pbot1=current?FLAG=1" -Pbot2=v01 -Pmaps=2000 -Pthreads=8 -PseedBase=200000 -Pout=replays/bench/<exp>/direct.json
./gradlew bench -Pbot1=v01 -Pbot2=v01 -Pmaps=2000 -Pthreads=8 -PseedBase=200000 -Pout=replays/bench/<exp>/mirror.json
python -I tools/bench_pair.py direct replays/bench/<exp>/direct.json --mirror replays/bench/<exp>/mirror.json --min-lower 0.5 --out docs/data/<exp>/g1.json
./gradlew bench "-Pbot1=current?FLAG=1" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PturnMs=200 -PseedBase=200000 -Pout=replays/bench/<exp>/boss-cand.json
./gradlew bench -Pbot1=v01 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PturnMs=200 -PseedBase=200000 -Pout=replays/bench/<exp>/boss-ctrl.json
python -I tools/bench_pair.py common replays/bench/<exp>/boss-cand.json replays/bench/<exp>/boss-ctrl.json --min-delta -0.02 --out docs/data/<exp>/g2.json
```

G3 is run the same way with `-Pbot2=old`. For a targeted fix, use `direct ... --min-score 0.5
--max-changed 0.1` and `common ... --min-delta -0.01` against `boss`. `bench_pair.py` exits with 0 when
every gate passes, 1 when one fails, and 2 when the input or the series is invalid. Its tests run with the
other tools' tests: `python -B -I -m unittest discover -s tools -p "test_*.py"`.

## Local results against the arena

Each uploaded candidate is listed here: its local S against the selected version and its P1 outcome.

| Candidate | Local S vs selected (block) | P1 outcome |
| --- | --- | --- |
| none yet | | |
