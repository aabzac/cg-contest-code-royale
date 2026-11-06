# Experiment 2026-11-06: when to release the knight waves (P2 candidate)

**Why.** In the Silver study (`docs/data/study-2026-11-05-silver/`), after turn 100 the strongest players' knights
reach our queen far more often than ours reach theirs: 32 contact turns against 9. Our knights always walk at the
enemy queen; the bot chooses only when to train them. Today v09 releases both knight barracks together as soon as the
gold covers both (`TRAIN_SYNC=1`). The user's choice of 2026-11-06: hold a wave until the enemy queen leaves her
towers.

**Starting point.**
- Revision: commit `7e5e346` plus the uncommitted rival, PV4, income and giants changes of the worktree
  `claude/bot-improvement-next-steps-4b8f4e`; each bench report records the status and the sha256 of the changes.
- Frozen v09, the referee and the harness are unchanged.

**The idea as proposed does not work.** `LocalProfile` gains a waves line (diagnostics only). From turn 30 on, the
enemy queen stands outside all her towers' ranges on 0.9 % of the turns against R0 and 0.2 % in the mirror. At our
releases the shares are 1.0 % and 0.2 %. Tower ranges of 360-510 cover almost every move she makes, so a hold until she
is uncovered would be a delay for nothing.

**What a wave does depend on** (`cr.tools.WaveScan`, new, `./gradlew waveScan`; v09 on both seats, 500 maps on
1 + 397 k, → `wavescan.txt`). A wave is the knight barracks that start training on one turn, from turn 30. Its damage
is the HP the enemy queen loses in the next 35 turns on turns with our knights within 250 of her.

| Enemy queen's distance to the releasing barracks | share of waves, against R0 | damage against R0 | damage against v09 |
| --- | --- | --- | --- |
| below 600 | 5.5 % | 8.2 HP | 3.8 HP |
| 600-800 | 10.2 % | 5.2 | 2.0 |
| 800-1000 | 21.3 % | 3.6 | 1.8 |
| 1000-1200 | 34.3 % | 2.8 | 1.6 |
| 1200-1500 | 27.2 % | 1.8 | 1.0 |
| all | | 3.2 | 1.5 |

A wave released while the enemy queen is near our barracks does 2-4 times the damage. That is a correlation: she is
then out of her base, so the timing may not cause it.

**The candidate rules** (`Params`, `MainBot.train`, off by default):
- `RELEASE_NEAR=R` with `RELEASE_HOLD=T`: a ready knight release (both barracks idle and the gold for both, as
  `TRAIN_SYNC=1` has it) waits until the enemy queen is within R of one of our knight barracks, at most T turns.
- For comparison, the existing `BARRACKS_FORWARD=P` moves the second knight barracks toward the enemy, which shortens
  every wave's walk. It was dropped against v05 in the army sweep of 2026-11-03.

**Hypothesis and criteria (stated before the sweep).** Waiting for the enemy queen to come near makes the waves hit
harder than it costs in delay. On the development seeds 1 + 397 k, k < 500, an arm is a candidate if all three hold:
- S ≥ 0.52 against v09: a direct gain large enough for G1's lower bound on 2000 maps;
- Δ ≥ −2 pp against R0;
- Δ ≥ −2 pp against `field`.

Among the arms that meet them, the screen on the fresh block `screen-pv3` k = 3 takes the one with the highest S
against v09. With none, the flags stay off.

## The sweep (`sweep.sh`, `sweep_table.py` → `sweep.txt`)

Raw reports: `replays/bench/dev-waves-2026-11-06/` in the main checkout. 500 maps per series, 8 threads, 50 ms.
v09 scores 0.7025 against R0 and 0.9440 against `field` on these maps.

| Arm | S vs v09 (95 % CI) | Δ vs R0 (95 % CI) | Δ vs `field` |
| --- | --- | --- | --- |
| `RELEASE_NEAR=800,RELEASE_HOLD=15` | 0.3320 (0.3001-0.3639) | −12.00 pp | −7.00 pp |
| `RELEASE_NEAR=800,RELEASE_HOLD=30` | 0.2955 (0.2629-0.3281) | −16.45 pp | −7.65 pp |
| `RELEASE_NEAR=1000,RELEASE_HOLD=15` | 0.3765 (0.3429-0.4101) | −7.80 pp | −2.65 pp |
| `RELEASE_NEAR=1000,RELEASE_HOLD=30` | 0.3455 (0.3122-0.3788) | −6.85 pp | −4.45 pp |
| `RELEASE_NEAR=600,RELEASE_HOLD=30` | 0.2535 (0.2229-0.2841) | −19.80 pp | −10.95 pp |
| `BARRACKS_FORWARD=35` | 0.5030 (0.4796-0.5264) | +4.50 pp (+1.74..+7.26) | −0.45 pp |
| `BARRACKS_FORWARD=50` | **0.5425** (0.5152-0.5698) | **+5.35 pp** (+2.23..+8.47) | −0.75 pp |

- **Every hold loses heavily.** The loss grows with the hold and shrinks with the radius, that is, with how rarely the
  hold applies.
  - A hold costs about one wave of ten (medians 9 against 10, `profile.txt`, 200 maps against v09).
  - It also halves what a knight training takes from the enemy queen: 0.8 HP against v09's 1.5. Why was not traced.
  - So the WaveScan correlation does not show a moment our release can choose.
- **Shorter walks pay.** With the second knight barracks at the centre of the board (`BARRACKS_FORWARD=50`), every wave
  from it starts about half the board nearer the enemy. It is the only arm that meets all three criteria. On
  2026-11-03 against v05 the same flag scored 0.541 and 0.486 on two blocks of consecutive seeds, so its sign was
  unsettled then.
- **The pick:** `current?BARRACKS_FORWARD=50`, the only arm that meets the criteria. Seven arms were tried on these
  seeds.

## The P2 screen on PV3 screen block 3 (`screen.sh`, `rerun.sh`; summaries `g1.json` … `g5.json` and the reruns)

Candidate `current?BARRACKS_FORWARD=50` against the control v09. Seeds 1003000000 + 397 i, i < 2000; `boss` plays the
first 500 maps (3 threads, 200 ms). Raw reports: `replays/bench/exp-2026-11-06-waves/` in the main checkout.

| Gate | First run | |
| --- | --- | --- |
| G1, direct against v09 | S **0.5426** (CI 0.5293-0.5560), 1,837 of 2,000 maps changed against the mirror | passes: the lower bound is above 0.50 |
| G4, against `field` | Δ +0.81 pp (−0.03..+1.66): 0.9301 against v09's 0.9220 | passes |
| G5, against R0 | Δ **+3.09 pp** (+1.56..+4.61): 0.7302 against v09's 0.6994 | passes |
| G2, against `boss` | Δ 0.00 pp: 1.000 against v09's 1.000, no failure of either side | passes |
| G0, first run | slow candidate turns in 6 direct, 6 `field` and 11 R0 games, on runs of neighbouring maps; v09's mirror had 22 and 21 | fails: host noise, so the candidate's three series are repeated with 2 threads |
| G0, the repeats | `rerun.sh` with 2 threads: the same 12,000 games as the first run, with slow candidate turns left in 3, 4 and 3 games on other maps (stalls up to 330 ms; v09 had one of 560 ms). `maprepeat.sh`: those 8 maps one at a time in 1 thread, the same 16 games, clean (`map_check.py`) | **passes**: every game of the three series has a clean run with the same outcome |

G3 is skipped (no `old`).

**Verdict: the candidate passes the P2 screen.**
- The direct gain replicated exactly: 0.5425 on the development seeds, 0.5426 on the fresh block.
- The gain against R0 shrank from +5.35 to +3.09 pp and held.
- `current?BARRACKS_FORWARD=50` is the local champion by P2. Freezing it as v10, making it the default, the final
  evaluation against the selected v08 and any upload are the user's decisions.
- About +0.5 rating points head-to-head with Silver's logistic scale 3.16: the logit of 0.5426 is 0.171. The upload
  recommendation asks S ≥ 0.60 against the selected version, v08, on the evaluation block (the final evaluation
  below).

**What it changes** (`profile.txt`, 200 maps against v09):
- from turn 30 the enemy queen stands 860 from our nearest knight barracks on average, against 1,038;
- a knight training takes 1.6 HP from her, against 1.5;
- we kill the enemy queen in 27 of 400 games against 16, and lose ours in 19 against 16.

A trial `./gradlew submit -Pparams=BARRACKS_FORWARD=50` builds and compiles: 86,098 characters of the 100,000 limit.
No `procCheck` was run and nothing was uploaded. `build/submit` now holds this trial build, and the arena's artifacts
stay kept in `artifacts/`.

**The user's decision (2026-11-06, "Да, по умолчанию"):** frozen as v10 and the default of `current`.
- `cr.bot.v10.GreedyBot` and `versions/v10.properties` (v09's snapshot with BARRACKS_FORWARD=50).
- `MainBotTest.withDefaultsCurrentPlaysAsV10` and `withV09ParamsCurrentPlaysAsV09`.
- Not uploaded, and no final evaluation against v08.

## The final evaluation of v10 against v08 (`eval.sh`, `eval-rerun.sh`; summaries `eval-g*.json`)

v10, the local champion, against v08, the selected version on the arena. PV3 evaluation block 0: seeds 2000000000 + 397
i, i < 2000, used once; `boss` plays the first 500 maps (3 threads, 200 ms). Raw reports:
`replays/bench/exp-2026-11-06-waves-eval/` in the main checkout.

| Gate | Result |
| --- | --- |
| G1, direct against v08 | S 0.5764 (CI 0.5626-0.5902) against v08 |
| the upload recommendation, S ≥ 0.60 | **not met**: even the upper bound is below 0.60 |
| G2, against `boss` | G2 passes: boss delta 0.0000 (1.000 against 1.000) |
| G4, against `field` | G4 passes: field delta +0.0149 (CI +0.0063..+0.0234) |
| G5, against R0 | G5 passes: rival delta +0.0843 (CI +0.0683..+0.1002) |
| G0 | G0 by quiet repeats: v10's three series repeated with 2 threads held the same 12000 games without a slow turn |

**Verdict:** G0-G2, G4 and G5 pass (G3 skipped): v10 passes the final evaluation against v08.
- About +1.0 rating point over v08 head-to-head with Silver's logistic scale 3.16; the logit of 0.5764 is 0.308.
- With Bronze's 1.33, which set P2's 0.60 threshold at about +0.5, it is +0.4.
- An upload would be the user's exception, as `route`'s was at 0.5856.
- Under P1 a gain of this size is inside the arena's noise (M 2.46).
