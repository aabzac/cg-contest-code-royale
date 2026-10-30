# Experiment 2026-10-30: v02 final evaluation (P2) and its artifact, before arena upload 1

- **What**: the local champion v02 (v01 with UNSTICK, frozen at a3e48fc) against the selected version v01.
  It is the P2 record of an upload candidate. The user chose to upload v02 although P2 recommends an
  upload only from S ≥ 0.60.
- **Pool**: final-evaluation block 0, seeds 400000-401999, fresh. Direct series use all 2000 maps; `boss`
  series use 400000-400499.
- **Code**: commit a3e48fc, clean tree.

## Commands

```
./gradlew bench -Pbot1=v02 -Pbot2=v01 -Pmaps=2000 -Pthreads=8 -PseedBase=400000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-30-v02-eval/direct.json
./gradlew bench -Pbot1=v01 -Pbot2=v01 -Pmaps=2000 -Pthreads=8 -PseedBase=400000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-30-v02-eval/mirror.json
./gradlew bench -Pbot1=v02 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=400000 -PturnMs=200 -Pout=replays/bench/exp-2026-10-30-v02-eval/boss-cand.json
./gradlew bench -Pbot1=v01 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=400000 -PturnMs=200 -Pout=replays/bench/exp-2026-10-30-v02-eval/boss-ctrl.json
./gradlew stallScan -Pbot=v02 -Popp=v01 -Pmaps=2000 -PseedBase=400000
./gradlew stallScan -Pbot=v01 -Popp=v01 -Pmaps=2000 -PseedBase=400000
python -I tools/bench_pair.py direct replays/bench/exp-2026-10-30-v02-eval/direct.json --mirror replays/bench/exp-2026-10-30-v02-eval/mirror.json --min-lower 0.5 --out docs/data/exp-2026-10-30-v02-eval/direct.json
python -I tools/bench_pair.py common replays/bench/exp-2026-10-30-v02-eval/boss-cand.json replays/bench/exp-2026-10-30-v02-eval/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-10-30-v02-eval/boss.json
./gradlew procCheck -Popp=v01 -Pgames=100 -PseedBase=5000
./gradlew procCheck -Popp=boss -Pgames=100 -PseedBase=6000
```

Raw files are in `replays/bench/exp-2026-10-30-v02-eval/` (gitignored):

| File | sha256 |
| --- | --- |
| `direct.json` | `8d7b4524…` |
| `mirror.json` | `aab89420…` |
| `boss-cand.json` | `1618485d…` |
| `boss-ctrl.json` | `d77bcda8…` |
| `run.log` | `5a55363c…` |
| `proccheck.log` | `7e5fe524…` |

## Results

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line; longest turn 11.2 ms against v01, 21.4 ms against `boss` | pass |
| G1, direct against v01 | S = 0.5032 (95 % CI 0.5005-0.5060, SD 0.062); W/D/L 1993/40/1967; 110 of 2000 maps changed (5.5 %) | pass |
| G2, `boss` | candidate 0.927, control 0.926, Δ = +0.0010 (CI −0.0010..0.0030) | pass |
| G3, `old` | skipped: the code of 3315948 is not in `refs/old-3315948/` yet | — |
| Upload recommendation, S ≥ 0.60 | 0.5032 | not met; the user uploads anyway |

Mechanism, from `stallScan` on the 2000 maps:

| | stall turns | streaks | games with 10+ |
| --- | --- | --- | --- |
| v01 | 464 | 97 | 15 (6 not won) |
| v02 | 74 | 38 | 3 (1 not won) |

v02's three long streaks (seed 400238 on both seats, 400512 on seat 0, turns 10-21) are early BUILDs to a
mine. There the queen's own knight stands next to her (distance 52, radii 50). The sites-only prediction
(`Physics.queenStep`) sees a 12-unit step, but the knight stops it, so UNSTICK does not fire. This is
recorded as a later idea: decide that the queen is stuck from what happened on the previous turn too, not
only from the prediction.

## Artifact

`./gradlew submit` from a3e48fc gave `build/submit/Player.java`:
- sha256 `527244968dbe3d44abeea2706cc129dff166e341d29351a9f8fe8de18da965a9`, 64,230 chars, PARAMS `""`;
- effective parameters INCOME_TARGET=10, MAX_TOWERS=4, BARRACKS=1, UNSTICK=1, SEED=1, DEBUG=0;
- build `a2c71dbb`, config `173dd43b`;
- kept copy `replays/artifacts/52724496/Player.java`.

procCheck:

| Opponent | Games | Identical to current | Timeouts | Kills | Extra / late / stderr lines | Longest first turn | Longest later turn |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v01 (100 maps from seed 5000) | 200 | 200 | 0 | 0 | 0 | 158.5 ms | 3.4 ms |
| `boss` (100 maps from seed 6000) | 200 | 200 | 0 | 0 | 0 | 184.2 ms | 3.5 ms |

The turn-0 marker of build `a2c71dbb` is `TRAIN 10 2 12 7 1 13 11 11`, which repeats site 11. A repeated
site has not been seen live yet. In the repo referee (`Referee.kt:178-189`):
- each site is checked in order, and the first site without barracks throws `PlayerInputWarning`; on
  turn 0 that is site 10;
- the duplicate check comes after the per-site checks and is itself a `PlayerInputWarning`.

So the line can only warn. This upload is the first live check.
