# Study 2026-11-07: v11 in Gold — how its games are lost, and the local proxy for Gold

## Question

v11 was promoted to Gold after its second Silver placement. The user asked for its Gold games to be studied once
both Gold placements were in ("можно разобрать игры с учётом двух"):
- how v11 loses in Gold;
- what the Gold field does;
- whether the local panel still stands for it. P2's proxy validation (PV3, PV4) lapsed with the promotion.

## Data

- **Games:** v11's two Gold placements, both played by the artifact `cf71d566` (cgAttribute SAME 180/180).
  - Upload 3: agent 6857463, CG's promotion, 18.13, rank 119/351.
  - Upload 4: agent 6858567, a new paste, 16.03, rank 256/351.
  - 180 games in all. The replays are in the main checkout's `replays/arena/v11/gold/upload-0{3,4}/games`.
- **Inputs:** gitignored, in `replays/study/gold-2026-11-07/`. Run from PowerShell in the repository root; `<R>` is the
  main checkout's `replays\arena\v11\gold`.
  ```
  .\gradlew.bat -q cgStudy "-Pout=replays\study\gold-2026-11-07\features-v11-gold.csv" "-Preplays=<R>\upload-03\games <R>\upload-04\games"
  .\gradlew.bat -q deathTrace -Pagent=6857463 -Pall=1 "-Preplays=<R>\upload-03\games" > replays\study\gold-2026-11-07\ours-upload-03.txt
  .\gradlew.bat -q deathTrace -Pagent=6857463 -Pall=1 -Pfoe=1 "-Preplays=<R>\upload-03\games" > replays\study\gold-2026-11-07\opp-upload-03.txt
  ```
  For upload 4 (agent 6858567), the same two traces.
- **Local profiles:** `./gradlew -q localProfile -PbotA=v11 -PbotB=<opponent> -Pmaps=200 -PseedBase=1 -PseedStride=397`,
  against `v11`, `field` and `rival`, and 100 maps against `boss`; the output files are `local-v11-*.txt` beside this
  README.
- **Script:** `python -B -I docs/data/study-2026-11-07-gold/gold.py` (from a worktree, add `--data <main checkout>`)
  writes `gold.txt`. It imports `tools/arena_compare.py` for Gold's logistic fit.

## Findings

Means and medians as `gold.txt` labels them.

### 1. How the games end

| v11's result | how | games |
| --- | --- | --- |
| lost | on HP at turn 250 (1 at equal scores, ranked by player 0) | 44 |
| lost | our queen died (median turn 134) | 25 |
| won | on HP at turn 250 (2 at equal scores) | 62 |
| won | the enemy queen died (median turn 150) | 44 |
| won | the referee removed the opponent | 5 |

106 of the 180 games (59 %) reach turn 250. **The HP race is Gold's main loss mode: 44 of the 69 losses.** In the 25
deaths, knights take 21.9 HP of the last 30 turns' loss and towers 0.4, so the tower fix of v11 holds here too.

### 2. The HP races are decided at the first wave

Our HP minus the enemy's, by turn (medians; means in brackets):

| races | t25 | t50 | t100 | t150 | t200 | t250 | ahead at t200 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| lost (44) | 0 (−0.9) | −5 (−5.8) | −6 (−7.4) | −6 (−8.7) | −8 (−9.2) | −12 (−13.5) | 3 |
| won (62) | 0 (+0.4) | +4 (+7.3) | +6 (+9.3) | +9 (+11.8) | +11 (+16.5) | +14 (+20.5) | 55 |

- **The first wave decides most of the race.**
  - The first enemy knight comes within 250 of our queen at turn 24-25 in both groups.
  - In the races we lose, she has one tower then (median), and in the next 20 turns she loses 12.5 HP to knights while
    the enemy loses 5.1 to ours.
  - In the races we win, she has two towers, and the exchange turns: 5.9 against 8.7.
  - After turn 50 the gap barely moves until turn 200.
- **The last 50 turns widen a lost race.** In lost races we lose 6.5 HP to knights on turns 200-250 against the enemy's
  2.8. We are behind at turn 200 in 41 of the 44, where the late hold (from turn 200, only when ahead) does nothing.
- **The margins are small.** The median lost race ends 12 HP down, and 21 of the 44 within 10 HP.
- The Silver study of 2026-11-05 found the same for v08: the first wave at turn 23-28, met with one tower.

### 3. The opponents

Medians, us / them:

| | lost (69) | won (111) |
| --- | --- | --- |
| income at turn 100 | 3 / 4 | 3 / 4 |
| towers at turn 200 | 6 / 5 | 6 / 5 |
| own tower HP at turn 200 | 2,400 / 2,556 | 2,712 / 2,272 |
| queen HP at turn 100 | 43.5 / 43.5 | 58 / 42 |
| queen HP at turn 200 | 37 / 46 | 58 / 35 |
| tower top-ups | 79 / 58 | 90 / 51 |

- The economy and the towers look alike in wins and losses; the HP does not.
- Giants come in 30 of the 180 games (17 %): 13 of the 69 losses (19 %) and 17 of the 111 wins (15 %). Archers appear in
  one game. Giants are not what loses Gold games, unlike Silver, where giant players beat v08 by about 2 rating points.

### 4. Results by the opponent's score

The model is `arena_compare.py`'s, with Gold's scale 3.10 fitted on the two Gold uploads; v11's performance is 16.92.

| opponents | games | scored | expected |
| --- | --- | --- | --- |
| below 14 | 44 | 77 % | 82 % |
| 14-17 | 88 | 64 % | 63 % |
| 17-20 | 44 | 48 % | 44 % |
| 20 and above | 4 | 0 of 4 | 19 % |

v11 scores about what its rating predicts in each group: no sign of Silver's pattern.

### 5. The local panel against the Gold field

| | Gold (v11's 180 games) | mirror v11 | `field` | `rival` (R0) | `boss` |
| --- | --- | --- | --- | --- | --- |
| games reaching turn 250 | 59 % | 91 % | 60 % | 76 % | 0 % |
| the first contact: turn, own towers | 24-25, 1-2 | 24, 1 | 21, 1 | 25, 1 | 19, 1 |
| HP lost in the 20 turns after it (mean) | 12.5 lost races, 5.9 won | 7.8 | 7.4 | 7.3 | 6.9 |
| enemy giants | 17 % of the games | 0 | 0 | 85 % | 0 |
| v11's score | 62 % | 48 % | 94 % | 79 % | 100 % |

- **The mirror** meets the first wave as in Gold, at turn 24 with one tower, and over-weights the HP races (91 % at the
  limit against Gold's 59 %). It is the panel's model of Gold's main loss mode.
- **`field`** matches Gold's game length (60 % at the limit), with a stronger economy and no kiting.
- **`rival`** builds giants in 85 % of the games, five times Gold's rate; it stands for the strongest players' late
  pressure, which Gold's field shows little of.
- **`boss`** stays a floor: its games end early (median turn 50), and v11 wins all 200.

## What follows

**A proposal for the Gold validation (PV5).** The user adopted it as proposed on 2026-11-07; the policy text is in
`docs/local-screen.md` (PV5).
- **Keep the panel and its gates as they are** (G0-G5; P2's seed pools stay). Record what each opponent stands for in
  Gold:
  - the mirror: the first wave and the HP races, Gold's main loss mode, which it over-weights;
  - `field`: Gold's game length and an economy stronger than Gold's;
  - `rival`: giants, five times as often as in Gold;
  - `boss`: a floor.
- **Gold's noise margin, the user's decision:**
  - v11's two Gold placements differ by 2.10 with the same code, so Gold's own M is 2.97 on 1 df;
  - Silver borrowed Bronze's 2.46 (11 df);
  - borrowing it again, or using Gold's own M once more placements come, are the options.
  - The user chose Bronze's 2.46 on 2026-11-07 (`docs/arena-uploads.md`, Gold noise margin).

**Candidates, in the order the evidence supports:**
1. **A second tower before the first wave.** In the HP races we lose, the first wave meets one tower and costs 12.5 HP;
   in those we win, it meets two and costs 5.9. The 2026-11-05 version (`WAVE_TOWERS`, `TOPUP_FROM`, off) lost on
   the development seeds against v08, with the queen dying twice as often. A retry would start from v11, with the mirror
   as the measure of the first wave, and has to answer why the second tower cost HP before.
2. **The last 50 turns of a race we trail.** We lose 6.5 HP to knights on turns 200-250 while behind in 41 of the 44
   lost races. The late hold acts only when ahead; a rule for the trailing side is open (more knights, or no
   exposure).
