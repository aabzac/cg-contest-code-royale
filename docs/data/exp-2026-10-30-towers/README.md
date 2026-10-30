# Experiment 2026-10-30: early towers (INCOME_TARGET=3, MAX_TOWERS=8), P2 strategy screen

Written before the screen's results were seen.

- **Hypothesis**: building towers early beats v02. The candidate mines only to income 3 before it turns
  to towers, and keeps up to 8 towers. The study `docs/data/study-2026-10-30/` motivates it: we build
  towers late and few, and our arena wins go with our towers.
- **Track**: P2 strategy candidate (`docs/local-screen.md`). The gates:
  - G0: no failure of the candidate;
  - G1: direct against the control, lower 95 % bound of S > 0.50;
  - G2: against `boss`, Δ ≥ −2 pp;
  - G3: against `old`, skipped, because the code of 3315948 is not in `refs/old-3315948/` (recorded).
- **Replication**: only this one arm is screened on the block, so none is required. The arm was chosen
  from 12 on the development pool, so the screen is the first fresh estimate.
- **Arms**:
  - candidate `current?INCOME_TARGET=3,MAX_TOWERS=8` (v02's code with these two parameters);
  - control `v02`, the local champion and the selected version;
  - opponents: the control (direct) and `boss`.
- **Pool**: screen block 1, seeds 202000-203999. Direct series use all 2000 maps; `boss` series use
  202000-202499.
- **If it passes**: the final evaluation against v02 on evaluation block 1 (402000-403999), with the same
  gates plus P2's upload recommendation S ≥ 0.60.
- **Code**: commit 5889248 with the uncommitted study tool (`cr.tools.CgStudy`, `build.gradle`) and docs.
  The bot's code is unchanged since a3e48fc.

## Commands

```
./gradlew bench "-Pbot1=current?INCOME_TARGET=3,MAX_TOWERS=8" -Pbot2=v02 -Pmaps=2000 -Pthreads=8 -PseedBase=202000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-30-towers/direct.json
./gradlew bench -Pbot1=v02 -Pbot2=v02 -Pmaps=2000 -Pthreads=8 -PseedBase=202000 -PturnMs=50 -Pout=replays/bench/exp-2026-10-30-towers/mirror.json
./gradlew bench "-Pbot1=current?INCOME_TARGET=3,MAX_TOWERS=8" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=202000 -PturnMs=200 -Pout=replays/bench/exp-2026-10-30-towers/boss-cand.json
./gradlew bench -Pbot1=v02 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PseedBase=202000 -PturnMs=200 -Pout=replays/bench/exp-2026-10-30-towers/boss-ctrl.json
python -I tools/bench_pair.py direct replays/bench/exp-2026-10-30-towers/direct.json --mirror replays/bench/exp-2026-10-30-towers/mirror.json --min-lower 0.5 --out docs/data/exp-2026-10-30-towers/g1.json
python -I tools/bench_pair.py common replays/bench/exp-2026-10-30-towers/boss-cand.json replays/bench/exp-2026-10-30-towers/boss-ctrl.json --min-delta -0.02 --out docs/data/exp-2026-10-30-towers/g2.json
```

## Screen results (block 1)

Summaries: `g1.json` and `g2.json`. Raw reports and `run.log` are in
`replays/bench/exp-2026-10-30-towers/` (gitignored).

| Gate | Result | |
| --- | --- | --- |
| G0 | no error, timeout, slow turn or extra line; longest turn 10.1 ms against v02, 3.5 ms against `boss` | pass |
| G1, direct against v02, 2000 maps | S = 0.7969 (95 % CI 0.7834-0.8103, SD 0.306); W/D/L 3170/35/795; by seat 1665/17/318 and 1505/18/477; 1996 of 2000 maps changed | pass |
| G2, `boss`, 500 maps | candidate 0.993, control 0.916, Δ = +0.0770 (CI +0.0596..+0.0944) | pass |
| G3, `old` | skipped (no code) | — |

- **Game length:** 226 turns on average against v02, against 171 in the v02 mirror. Many games end on HP at
  turn 250.
- **Control's slow turn:** one in-process turn of the control took 65 ms in the mirror run. It is not
  the candidate's and no failure for the gates.
- **No winner's curse seen:** the development estimate was 0.782 on 500 maps; the fresh block gives
  0.797.

**Verdict**: the screen passes. The candidate is the local champion, pending the final evaluation
against v02 on block 402000-403999.

## Final evaluation (block 402000-403999)

Commands are those above with `-PseedBase=402000` and the reports in
`replays/bench/exp-2026-10-30-towers-eval/`. Summaries: `eval-g1.json` and `eval-g2.json`.

| Gate | Result | |
| --- | --- | --- |
| G0 | no failure; longest turn 4.4 ms against v02, 12.6 ms against `boss` | pass |
| G1, direct against v02, 2000 maps | S = 0.8146 (95 % CI 0.8015-0.8278, SD 0.300); W/D/L 3243/31/726; by seat 1692/13/295 and 1551/18/431 | pass |
| G2, `boss`, 500 maps | candidate 0.991, control 0.943, Δ ≈ +0.048 | pass |
| G3, `old` | skipped | — |
| Upload recommendation, S ≥ 0.60 | 0.8146 | met |

- **Rating terms:** at the arena's logistic scale, S = 0.815 against v02 corresponds to about +2.0
  points in a head-to-head. That is a head-to-head score, not the field.
- **Seats:** the candidate wins on both seats, 1692 and 1551 of 2000. The v02 mirror's seat 0 won 1440 of
  2000 maps' seat-0 games.

Raw report sha256 (first 12 hex digits):

| Series | Screen block | Final-evaluation block |
| --- | --- | --- |
| `direct.json` | `19121c96dafb` | `6eff8b40482e` |
| `mirror.json` | `f0a06ad36eba` | `b2a3c7fa1e72` |
| `boss-cand.json` | `2fca2a36e6f8` | `438deff0e2c5` |
| `boss-ctrl.json` | `8daf3519bbe4` | `66cb38efe266` |
| `run.log` | `87af288813f4` | `56296323ae80` |

**Verdict**: P2 passed, with the upload recommendation met. The candidate is the new local champion
(frozen as v03); the upload is the user's decision.

## v03 artifact

`./gradlew submit` with the v03 defaults gives `build/submit/Player.java`:
- sha256 `d6266d41b6562158146d24a191742ecb0a9c10242cae6b04910933c397007524`, 64,300 chars, PARAMS `""`;
- effective INCOME_TARGET=3, MAX_TOWERS=8, BARRACKS=1, UNSTICK=1, SEED=1, DEBUG=0;
- build `cd1f3d33`, config `12070467`;
- kept copy `replays/artifacts/d6266d41/Player.java`.

procCheck (log `replays/bench/exp-2026-10-30-towers-eval/proccheck-v03.log`):

| Opponent | Games | Identical to current | Timeouts | Kills | Extra / late / stderr lines | Longest first turn | Longest later turn |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v01 (100 maps from seed 5000) | 200 | 200 | 0 | 0 | 0 | 218.7 ms | 3.5 ms |
| `boss` (100 maps from seed 6000) | 200 | 200 | 0 | 0 | 0 | 213.4 ms | 3.5 ms |

The turn-0 marker is `TRAIN 12 13 1 15 3 13 3 3`. It repeats sites 13 and 3, which only warns: v02's
120 arena games showed a repeated site live.
