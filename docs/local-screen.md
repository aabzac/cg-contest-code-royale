# Local screen: policy P2 (2026-10-30)

Status: **adopted 2026-10-30** by the user, as proposed in that day's session, with the old bot added to
the opponent panel and the boss's exact rating left for later. P2 is the local part of the promotion
policy. The arena step stays P1 (`docs/arena-uploads.md`). A candidate first passes P2, then the artifact
checks (`submit`, both `procCheck` runs), then P1 on the arena. Proxy validation: PV1 (2026-10-30), PV2
(2026-11-03, `field`), **PV3 (2026-11-05, Silver; the user's decision)**: strided seed blocks and a record of
what the panel represents; **PV4 (2026-11-06; the user's decision)**: the rival R0 as gate G5; **PV5 (2026-11-07,
Gold; the user's decision)**: the panel and the gates kept for Gold (below).

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
- **Control**: the version the candidate would replace, which is the local champion (`docs/STATUS.md`).
- **Local champion**: the last version that passed P2. Its configuration becomes the `current` defaults,
  and the next candidates are screened against it.
- **Selected version**: the arena's (P1; `docs/STATUS.md`).

## Opponents and proxy validation PV1-PV5

| Spec | Role | What it is |
| --- | --- | --- |
| the control | G1, direct | the version the candidate would replace |
| `boss` | G2, floor | the referee repository's Default AI (`refs/referee-csj/config/level4/Boss.java`, csj/code-royale@349924a): one knight barracks, mines to income 10, then towers, no evasion. v01 wins 95 % of games against it. It is not a model of the live boss "Princess Boss", which stands above about 27.1 |
| `old` | G3, stronger reference | the code of the replaced entry 3315948 (long-run 19.64 in Bronze), which the user keeps from the IDE at `refs/old-3315948/Player.java` (gitignored). It is compiled into `build/opponents/old` and runs as a process. It is not part of our bot. Pending: the code is not in place yet |
| `field` | G4, the field's balance (since 2026-11-03, PV2) | `cr.bot.field.FieldBot` (in-process, `field?K=v`): a greedy ladder with the arena field's balance — a mine first, 3 mines upgraded to income 8, 4 towers at 600, v06's defence, 17 knight waves; giants optional and off. Its play is not our ladder (income 7 against 3, 4 towers at 600 against 6 at 700). S 0.255 against v06, 0.735 against v03, 60/60 against `boss` (`docs/data/exp-2026-11-03-field-opponent/`). Not the arena's strong players, who beat v06 |
| `rival` | G5, the strongest Silver players' build (since 2026-11-06, PV4) | the rival R0: `FieldBot` with `FieldParams.RIVAL` = `KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2` (in-process, `rival?K=v`): two mines first, income 5, 13 knight and 2 giant trainings, v08's kiting. v09 scores 0.703 against it (`field` 0.93); the versions v05-v09 score 0.27, 0.37, 0.41, 0.65, 0.70 (`docs/data/exp-2026-11-06-rival/`). Far weaker than the strongest Silver players, who score 70 % against v08 |

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

**PV2** (2026-11-03, the user's decision): PV1's opponents plus `field` as a fourth gate. The proxy set
changed, so PV1's fingerprints are kept and `field`'s added (`docs/data/exp-2026-11-03-field-opponent/`):
- `field` holds the field's economy and tower balance (income 7 with 3 mines at turn 100 against the
  opponents who beat v06 at 8 with 3; 4 towers at 2,344 HP against their 1,676; 17 waves against 12) and
  plays knights only, like 33 of the 37 arena opponents who killed v05's queen.
- Our flags against `field` on 200 maps (seeds 11000-11199): the defaults 0.726; INCOME_LATE 6/8
  0.635/0.608; TOWER_HP=500 0.654; MAX_TOWERS=4 0.666; THREAT_TOWERS_NEAR=0 0.632; MINES_FIRST=3 0.759.
  The gate's purpose: a candidate must not lose against the field's balance what it wins against the mirror.
- The arena's strong players (kiting, the timing of waves) are still not represented; v06 beats `field`
  three games in four while they beat v06.
- PV2 lapses as PV1 does.

**PV3** (2026-11-05, Silver, the user's decision on the proposal of `docs/data/study-2026-11-05-silver/`).
PV1/PV2 lapsed with the promotion to Silver on 2026-11-03. The study compared v08's 250 Silver placement
games with local series of v08 against each opponent; PV3 renews the validation for Silver.
- **The maps.** The queens' starting HP is `MapGen`'s first draw (`Random(seed).nextInt(16) * 5 + 25`).
  - Consecutive seeds barely move that draw: every block of the old pools played 4-6 of the 16 values,
    and some development blocks one or two. CG plays all 16 evenly (median 65).
  - The starting HP changes the games: v08 against `field` reaches turn 250 in 21 % of the games at 35 HP
    and in 58 % at CG's spread.
  - Since PV3 a block's maps are strided, seedBase + 397 k. Stride 397 covers all 16 values in every block,
    and the other map features (sites, gold, mine sizes, radii) do not depend on it.
  - The consecutive pools are closed; their verdicts stand.
- **What the panel represents in Silver** (v08's local series at CG's HP spread against the Silver games;
  the table is in the study's `silver.txt`, section 6):
  - The first contact with enemy knights: turn 19-24 locally with one own tower, 23-28 in Silver. Every
    opponent represents it.
  - The mirror: close HP races, which it over-weights. It reaches the limit in 90 % of the games (Silver
    48 %), and 70 % of those are decided by 10 HP or less (Silver 33 %).
  - `field`: the field's economy and its knight waves. It out-expands even the strongest Silver players
    (income 8 against 6) but never kites, and v08 beats it in 93 % of the games.
  - `boss`: a floor. Its games end by turn 50.
- **What the panel does not represent:**
  - giants: 25 % of the Silver games and 65 % of the strongest four players' games; v08's rating against
    giant players is about 2 points lower, p 0.03; `field?GIANTS=1` loses 94 % against v08;
  - the late pressure of strong economies (waves 10-13 against our 6-7).

  A candidate whose effect depends on these is judged by the arena (P1), and its record says so.
- **Calibration** with `old` (PV1) is still pending, and G3 is skipped until its code is in place.
- PV3 lapses as PV1 does.

**PV4** (2026-11-06, the user's decision on the proposal of `docs/data/exp-2026-11-06-rival/`): the rival R0
joins the panel as gate G5.
- **Built for** the play PV3 left unrepresented: the strongest four Silver players of v08's placements. They
  open with two mines, hold income 6, train 11 knight waves and a giant or two (giants in 36 of 55 games), and
  score 70 % against v08.
- **R0** is the `field` ladder with v08's kiting, two mines first, top-ups to 700 and at most two giants from
  turn 80. Other balances of the same ladder plateau near v09 0.70 against them, so R0 is the closest the
  ladder gets.
- **The check:** against R0 the versions v05-v09 score 0.269, 0.372, 0.412, 0.651, 0.703. The steps (in logits
  +0.48, +0.17, +0.98) come in the Bronze arena's order: v06 +0.85, v07 −0.71, v08 +2.46 on the placement means.
  P2's direct matches had put v07's step first. Three steps are a small sample.
- **Not represented**, beside PV3's list:
  - the strongest players' strength: R0 scores 29 % against v09, they score 70 % against v08;
  - their low kiting: 14 % MOVE under knights against R0's 66 %;
  - the share of games decided before the limit: R0 goes to the limit in 74 % of the games, they in 44 %.
- PV4 lapses as PV1 does.

**PV5** (2026-11-07, Gold, the user's decision on the proposal of `docs/data/study-2026-11-07-gold/`).
- **Why it was needed.** CG promoted v11 to Gold on 2026-11-07, and PV3 and PV4 lapsed with the league change.
- **The evidence.** The study compares v11's 180 Gold placement games with local profiles of v11 against each opponent
  (seeds 1 + 397 k):
  - 44 of the 69 losses are HP races at turn 250, decided mostly at the enemy's first wave (turn 24-25);
  - 59 % of the games reach the limit, giants come in 17 %, and v11 scores its logistic expectation in every opponent
    group.
- **The panel and the gates stay as they are** (G0-G5, the seed pools of PV3). What each opponent stands for in Gold:
  - the mirror: the first wave as Gold meets it (turn 24, one own tower) and the HP races, Gold's main loss mode, which
    it over-weights (91 % of games at the limit against 59 %);
  - `field`: Gold's game length (60 % at the limit) with a stronger economy than Gold's and no kiting;
  - `rival`: giants, in 85 % of its games against Gold's 17 %; the late pressure of the strongest players;
  - `boss`: a floor; its games end early.
- **Not represented:**
  - the share of HP races decided by a few HP: half the lost Gold races end within 10 HP, while the mirror's are closer
    still;
  - Gold's mix of opponents of very different strength, from 11.88 to 24.99;
  - the Gold field's midgame pressure (found 2026-11-09 on v13's games: the field's barracks stand nearer our queen than
    the local opponents', and its waves hurt two to seven times more; `docs/data/study-2026-11-09-v13-gold/`, section 8,
    with the proposal of a rival whose barracks stand forward, not decided).
- **Not decided by PV5:** Gold's noise margin for P1. The user chose Bronze's 2.46 on 2026-11-07 (Gold's own M is
  2.97 on 1 df; `docs/arena-uploads.md`, Gold noise margin).
- PV5 lapses as PV1 does.

## Seeds

The map is `MapGen.generate(seed)`: `java.util.Random(seed)`, validated against CG maps. Each map is
played twice with the seats swapped, by fresh bots. The ledger `docs/data/seed-ledger.json` records each
block's use; a used block becomes development data. Since PV3 (2026-11-05) a block's maps are seedBase +
397 k (`./gradlew bench -PseedStride=397`), so that every block plays all 16 starting HPs as CG does.

| Pool | Seeds | Use |
| --- | --- | --- |
| development | every seed below 200000, the blocks used so far, the fixtures; strided series 1 + 397 k (k < 500) cover all starting HPs | free reuse, exploratory evidence |
| screen (PV3) | block k: 1000000000 + 1000000 k + 397 i, i < 2000 | one fresh block per candidate: in-process series play all 2000 maps, process opponents the first 500 (the same base and stride) |
| final evaluation (PV3) | block k: 2000000000 + 1000000 k + 397 i, i < 2000 | once per upload candidate |
| screen, evaluation (closed by PV3) | 200000 + 2000 k .. + 1999 and 400000 + 2000 k .. + 1999 | none: consecutive seeds; the verdicts on them stand |

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
| G4 | `field` (in-process), the 2000 maps of the block: Δ ≥ −2 pp (since 2026-11-03, PV2) |
| G5 | `rival` (in-process), the 2000 maps of the block: Δ ≥ −2 pp (since 2026-11-06, PV4) |

- **Replication** is needed only when several arms were screened on one block: the chosen arm repeats
  G0-G4 on a new screen block.
- A candidate that passes becomes the local champion.
- **Upload recommendation**: when the local champion passes G0-G5 against the selected version on a fresh
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

On 2026-11-06 the user did not adopt a change that passed these gates by the letter. The archers against giants
(`docs/data/exp-2026-11-06-giants/`) changed no map against the mirror, but 1,740 of 2,000 against R0, with no gain
there.

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

For a candidate `current?FLAG=1` against the control v10 on PV3 screen block 0 (seedBase 1000000000,
stride 397; block k starts at 1000000000 + 1000000 k):

```
./gradlew bench "-Pbot1=current?FLAG=1" -Pbot2=v10 -Pmaps=2000 -Pthreads=8 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/direct.json
./gradlew bench -Pbot1=v10 -Pbot2=v10 -Pmaps=2000 -Pthreads=8 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/mirror.json
python -I tools/bench_pair.py direct replays/bench/<exp>/direct.json --mirror replays/bench/<exp>/mirror.json --min-lower 0.5 --out docs/data/<exp>/g1.json
./gradlew bench "-Pbot1=current?FLAG=1" -Pbot2=boss -Pmaps=500 -Pthreads=3 -PturnMs=200 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/boss-cand.json
./gradlew bench -Pbot1=v10 -Pbot2=boss -Pmaps=500 -Pthreads=3 -PturnMs=200 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/boss-ctrl.json
python -I tools/bench_pair.py common replays/bench/<exp>/boss-cand.json replays/bench/<exp>/boss-ctrl.json --min-delta -0.02 --out docs/data/<exp>/g2.json
./gradlew bench "-Pbot1=current?FLAG=1" -Pbot2=field -Pmaps=2000 -Pthreads=8 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/field-cand.json
./gradlew bench -Pbot1=v10 -Pbot2=field -Pmaps=2000 -Pthreads=8 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/field-ctrl.json
python -I tools/bench_pair.py common replays/bench/<exp>/field-cand.json replays/bench/<exp>/field-ctrl.json --min-delta -0.02 --out docs/data/<exp>/g4.json
./gradlew bench "-Pbot1=current?FLAG=1" -Pbot2=rival -Pmaps=2000 -Pthreads=8 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/rival-cand.json
./gradlew bench -Pbot1=v10 -Pbot2=rival -Pmaps=2000 -Pthreads=8 -PseedBase=1000000000 -PseedStride=397 -Pout=replays/bench/<exp>/rival-ctrl.json
python -I tools/bench_pair.py common replays/bench/<exp>/rival-cand.json replays/bench/<exp>/rival-ctrl.json --min-delta -0.02 --out docs/data/<exp>/g5.json
```

`bench_pair.py` refuses to pair reports whose seeds differ in base, stride or count; a report written
before PV3 has no `seedStride` and reads as stride 1. G3 is run the same way with `-Pbot2=old`. For a targeted fix, use `direct ... --min-score 0.5
--max-changed 0.1` and `common ... --min-delta -0.01` against `boss`. `bench_pair.py` exits with 0 when
every gate passes, 1 when one fails, and 2 when the input or the series is invalid. Its tests run with the
other tools' tests: `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`.

## Local results against the arena

Each uploaded candidate is listed here: its local S against the selected version and its P1 outcome.

| Candidate | Local S vs selected (block) | P1 outcome |
| --- | --- | --- |
| v05 (MINES_FIRST=2, two mines before the barracks) | 0.7556 (CI 0.7401-0.7712) against v04, evaluation block 3 (406000-407999); the 0.60 recommendation met | uploads 1 and 2 (2026-11-03): 23.93 and 26.47, mean 25.20 against `route`'s 22.50 (+2.70, M 2.77); pooled rating 24.90 ± 0.37 against 22.05 ± 0.35 (+2.86 ± 0.51). P1: not clearly worse, selected 2026-11-03. Upload 3 (25.77) joined the baseline: mean 25.39 (3), M 2.61. The arena agrees with the local direction (+1.7 head-to-head predicted) and exceeds its size |
| `route` (ROUTE=1, the queen's walk planner) | 0.5856 (CI 0.5701-0.6011) against v03, evaluation block 2 (404000-405999); below the 0.60 recommendation, uploaded by the user's decision | uploads 1 and 2 (2026-11-01/02): 22.89 and 22.10, mean 22.50 against v03's 22.43 (+0.07, M 2.61); pooled rating 22.01 ± 0.33 against 22.39 ± 0.28 (−0.38 ± 0.43). P1: not clearly worse, selected 2026-11-02 and frozen as v04 (`docs/arena-uploads.md`). The local +0.46 rating points head-to-head are inside the arena noise, so the arena neither confirms nor contradicts the local result |
| v03 (early towers) | 0.8146 (CI 0.8015-0.8278) against v02, evaluation block 1 (402000-403999) | +4.15 over v02 (P1 means of two uploads, `docs/arena-uploads.md`): not clearly worse, v03 selected (2026-10-30). The arena agrees with the local direction |
| v02 (UNSTICK) | 0.5032 (CI 0.5005-0.5060) against v01, evaluation block 0 (400000-401999) | +0.05 over v01 (P1 means, `docs/arena-uploads.md`): not clearly worse, v02 selected (2026-10-30). v02 played as v01 in 58 and 56 of 60 games, so the arena saw no effect either way |
| army candidate (`BARRACKS=2,BARRACKS_AFTER_TOWERS=4,TRAIN_SYNC=1,MAX_TOWERS=6`) | 0.5480 (CI 0.5327-0.5633) against v05, evaluation block 4 (408000-409999); the 0.60 recommendation not met (`docs/data/exp-2026-11-03-army/`) | deferred by the user, no upload; folded into the safety candidate |
| v06 (`THREAT_TOWERS_NEAR=2,THREAT_RADIUS=500` with the army flags) | 0.6519 (CI 0.6356-0.6682) against v05, evaluation block 5 (410000-411999); the 0.60 recommendation met (`docs/data/exp-2026-11-03-safety/`) | uploads 1 and 2 (2026-11-03): 27.09 and 25.39, mean 26.24 against v05's 25.39 (+0.85, M 2.59); pooled rating 25.75 ± 0.46 against 25.06 ± 0.35 (+0.69 ± 0.58). P1: not clearly worse, selected 2026-11-03. The arena agrees with the local direction (+1.1 head-to-head predicted) |
| v07 (`KITE=1`, kiting under knights) | 0.6803 (CI 0.6641-0.6964) against v06, evaluation block 6 (412000-413999), +3.5 pp against `field` (G4); the 0.60 recommendation met (`docs/data/exp-2026-11-03-kite/`) | uploads 1 and 2 (2026-11-03): 26.42 and 24.64, mean 25.53 against v06's 26.24 (−0.71, M 2.58); pooled rating 25.34 ± 0.43 against 25.76 ± 0.46 (−0.41 ± 0.63). P1: not clearly worse, selected 2026-11-03. **The arena does not show the local gain**: the first disagreement of this size between P2 and P1 |
| v08 (`KITE=2,KITE_PULL=100`, kiting bound to the towers) | 0.6670 (CI 0.6522-0.6818) against v07, evaluation block 7 (414000-415999), +14.8 pp against `field` (G4); the 0.60 recommendation met (`docs/data/exp-2026-11-03-kite2/`) | uploads 1 and 2 (2026-11-03): 27.85 and 28.13, mean 27.99 against v07's 25.53 (+2.46, M 2.46); pooled rating 27.59 ± 0.50 against 25.42 ± 0.46 (+2.17 ± 0.68). P1: not clearly worse, selected 2026-11-03; 4 of 8 against the boss, `eligibleForPromotion` true. The arena confirms the local direction and size |
| v10 (`BARRACKS_FORWARD=50`, the second knight barracks at the centre, over v09's late hold) | 0.5764 (CI 0.5626-0.5902) against v08, PV3 evaluation block 0 (2000000000 + 397 i), +8.43 pp against `rival`, +1.49 pp against `field`; below the 0.60 recommendation, uploaded as an exception by the user's decision | uploads 1 and 2 (2026-11-07): 19.38 and 21.33, mean 20.35 against v08's Silver mean 20.55 (−0.20, M 2.46); performance 20.56 ± 0.65 against 20.64 ± 0.50 (−0.08 ± 0.82). P1: not clearly worse, selected 2026-11-07; 4 of 4 against the boss, `eligibleForPromotion` false. The arena neither confirms nor refutes the local +1.0, which is inside its noise |
| v11 (`SAFE_LADDER=1`, no ladder walks into the enemy base; a targeted fix over v10) | 0.5030 (CI 0.5000-0.5060) against v10, PV3 screen block 4 (1004000000 + 397 i), 148 of 2000 maps changed against the mirror; a targeted fix has no final evaluation; uploaded as an exception by the user's decision | uploads 1 and 2 (2026-11-07): 20.80 and 21.74, mean 21.27 against v10's Silver mean 20.35 (+0.92, M 2.46); performance 22.02 ± 0.77 against 21.04 ± 0.77 (+0.98 ± 1.09). P1: not clearly worse, selected 2026-11-07; upload 2 ranked 1/200 and was promoted to Gold. Its queen lost 0.6 HP a game to enemy towers, against v10's 5.1: the fix acts on the arena, where the local panel barely shows it. In Gold: uploads 3 (CG's promotion) and 4, 18.13 and 16.03 (ranks 119 and 256 of 351), mean 17.08, v11's Gold baseline |
| v12 (`WAVE_ANCHOR=50,WAVE_UNTIL=30`, the ladder's tower targets kept back in the first wave) | 0.5716 (CI 0.5587-0.5845) against v11, PV3 evaluation block 1 (2001000000 + 397 i); the screen on block 5: 0.5654; field −1.23 pp, rival +1.63 pp; below the 0.60 recommendation, uploaded as an exception by the user's decision | uploads 1 and 2 (2026-11-07, Gold): 19.64 and 21.07, mean 20.36 against v11's Gold mean 17.08 (+3.28, M 2.46); performance 20.38 ± 0.48 against 16.89 ± 0.49 (+3.48 ± 0.69). P1: not clearly worse, selected 2026-11-07. On the arena the first wave is met 287 from the back edge (v11 411) and costs 6.9 HP (v11 11.4): the rule acts as locally. The panel undervalued it: S 0.57 for +3.3 points. A third Gold upload, the paste by mistake of 2026-11-10, scored 20.88 (the mean of three 20.53) |
| v13 (`LADDER_CLEAR=500`, no ladder walk while an enemy knight is within 500 after the first-wave window) | 0.5958 (CI 0.5819-0.6096) against v12, PV3 evaluation block 2 (2002000000 + 397 i); the screen on block 6: 0.5960; field +4.34 pp, rival +6.61 pp, boss 0; G0 by a quiet repeat of the field series (5 slow turns at 8 threads, none at 2 threads, the same games); below the 0.60 recommendation by 0.004 (`docs/data/exp-2026-11-09-meet-waves/`) | uploads 1 and 2 (2026-11-09, Gold, an exception): 21.95 and 21.65, mean 21.80 against v12's Gold mean 20.36 (+1.44, M 2.46); performance 21.75 ± 0.43 against 20.27 ± 0.45 (+1.48 ± 0.62). P1: not clearly worse, selected 2026-11-09. The panel's S 0.596 turned into +1.4 points (v12's 0.572 into +3.3) |
| v14 (`HOLD_OVERLAP=300,IDLE_OVERLAP=1`, the hold and the flight on the spot the most own towers cover within five steps, the idle queen at the best-covered tower) | 0.5533 (CI 0.5404-0.5661) against v13, PV3 evaluation block 3 (2003000000 + 397 i); the screen on block 7: 0.5473; field +1.51 pp, rival +2.45 pp, boss 0 on 499 maps; no failure of v14; below the 0.60 recommendation (`docs/data/exp-2026-11-10-overlap/`) | uploads 1 and 2 (2026-11-10, Gold, an exception): 23.11 and 22.10, mean 22.61 against v13's Gold mean 21.80 (+0.81, M 2.46); performance 22.34 ± 0.51 against 21.84 ± 0.48 (+0.50 ± 0.69). P1: not clearly worse, selected 2026-11-10. The panel's S 0.553 turned into +0.8 points (v13's 0.596 into +1.4, v12's 0.572 into +3.3): the direction agrees, the size is inside the arena's noise. On the arena the rule acts as locally: three or more own towers over the queen on 54 % of her contact turns against v13's 39 % (the mirror's profile 53 % → 66 %; `docs/data/study-2026-11-10-v14-cover/`). The paste of 02:23 that day was v12's file by mistake, v12 upload 3 |
| v15 (`SITE_NEAR=50`, the ladder's tower sites from the second tower on by the distance to the ladder's point plus half the distance to the second-nearest own tower) | 0.5510 (CI 0.5377-0.5643) against v14, PV3 evaluation block 4 (2004000000 + 397 i); the screen on block 8: 0.5425; field +0.39 pp, rival +2.51 pp, boss 0; G0 by quiet repeats of the direct and the field series (2 and 9 slow turns at 8 threads, none at 2 threads, the same games); below the 0.60 recommendation (`docs/data/exp-2026-11-10-tower-sites/`) | uploads 1 and 2 (2026-11-11, Gold, an exception): 21.87 and 21.05, mean 21.46 against v14's Gold mean 22.61 (−1.14, M 2.46); performance 21.31 ± 0.54 against 22.48 ± 0.54 (−1.17 ± 0.76). P1: not clearly worse, selected 2026-11-11. **The arena does not show the local gain**: the panel's S 0.551 turned into −1.1 points, inside the arena's noise and about 1.5 SE of the rating difference (v14's 0.553 into +0.8, v13's 0.596 into +1.4, v12's 0.572 into +3.3). v15 won 121 of its 180 games (v14 120) against a weaker field (opponents' medians 19.98 and 17.74 against 21.18 and 19.79). The second candidate, after v07 (−0.71), whose placement mean fell more than half a point below the version it replaced. **Upload 3 the same evening: 23.76**, the best of our Gold placements, against the strongest field met (opponents' median 22.61); on three placements the mean is 22.23, −0.38 against v14, and the performance 22.15 ± 0.43 against 22.41 ± 0.52 (−0.26 ± 0.68): the arena shows neither the gain nor a loss, and the one artifact's placements span 2.71 points. What the rule did on the arena is measured (`docs/data/study-2026-11-11-v15-sites/`): the sites and the ladder's pace are the local ones, the cover on contact turns and the HP did not move beyond noise |
