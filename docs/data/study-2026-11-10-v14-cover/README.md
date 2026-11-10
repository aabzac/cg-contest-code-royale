# Study 2026-11-10: what the overlap rule did on the arena (v14's 180 Gold games beside v13's)

## Question

v14 = v13 with `HOLD_OVERLAP=300` and `IDLE_OVERLAP=1` (the queen waits where her towers overlap;
`docs/data/exp-2026-11-10-overlap/`) was selected under P1 on 2026-11-10: placements 23.11 and 22.10 against v13's 21.95
and 21.65, "not clearly worse". The records said that what the rule did on the arena was not measured, and that upload 2
lost 7 of 28 games to opponents rated below 17. The user's "Закоммить и измерь": measure it. No bot code was changed and
no P2 block was used.

## Data

- **Games:** v14's two Gold placements (agents 6868933 and 6869279, artifact `e4c8eeec`, cgAttribute SAME 180/180) and
  v13's two (6868408 and 6868474, artifact `85897d90`). Replays in the main checkout's
  `replays/arena/v14/gold/upload-0{1,2}/games` and `replays/arena/v13/gold/upload-0{1,2}/games`; manifests
  `docs/data/arena-<agent>-games-<date>.json`.
- **The probe:** `docs/data/study-2026-11-09-v13-gold/probes/ArenaProbe.java`, unchanged (its header has the build line).
  From a worktree, `<R>` = `../../../replays`; in Git Bash the class path needs `MSYS_NO_PATHCONV=1`, or the `C:` of the
  gson jar is rewritten:
  ```
  java -cp ... ArenaProbe 6868933,6869279 replays/study/gold-v14-2026-11-10/probe <R>/arena/v14/gold/upload-01/games <R>/arena/v14/gold/upload-02/games
  java -cp ... ArenaProbe 6868408,6868474 replays/study/gold-v14-2026-11-10/probe-v13 <R>/arena/v13/gold/upload-01/games <R>/arena/v13/gold/upload-02/games
  ```
  The second run is the control: its three files are byte for byte those of the v13 study (2026-11-09), so the two
  versions are traced by the same code.

  | file (gitignored; `replays/study/gold-v14-2026-11-10/`, copied to the main checkout) | sha256 |
  | --- | --- |
  | probe/turns.csv (77,130 rows) | `7a0e612a2f440780266f8fd2119282373a4bd1f34a75935a7c969bf64f69f944` |
  | probe/events.csv | `dc0f7f19ff9e09e30cc05bf20c656b06e6c709eef66e32f151d999cf5c21ceb2` |
  | probe/waves.csv | `77adafc6cf2170a09300cc15eb31e6f714601367b055a272afecefb74ae7c784` |
  | probe-v13/turns.csv, events.csv, waves.csv | `b94dd342…bb6a803`, `033cd51d…f22aebca2`, `21791903…ec950cb0` (the v13 study's) |
- **v13's answers on v14's positions:**
  `./gradlew -q cgAttribute -Preplays=<R>/arena/v14/gold/upload-01/games -Pagent=6868933 -Pbot=v13` and the same for
  upload 2 (agent 6869279), saved as `as-v13-upload-0{1,2}.txt` beside the probe files. Exit code 1 is expected: the games
  are not v13's.
- **Scripts** (from the repository root):
  `python -B -I docs/data/study-2026-11-10-v14-cover/cover.py --v14 <probe> --v13 <probe-v13>` → `cover.txt`;
  `python -B -I docs/data/study-2026-11-10-v14-cover/footprint.py <as-v13-upload-01.txt> <as-v13-upload-02.txt>` →
  `footprint.txt`. `cover.py` uses the v13 study's readers and its definitions of a contact turn, the gate's state, the
  cover and an episode (the script's header).
- Revision: commit 63c2c7e, clean but for this directory. Java 17.0.12, Gradle 8.11.1, Windows 11.

## Findings

### 1. The rule acts in most games (`footprint.txt`)

On v14's recorded positions v13 would have given another command in **158 of the 180 games**, on 5,917 of 38,565 turns
(15 %): 37 turns in a game with a difference (median 33). The first difference comes at turn 81 (median; quartiles 59 and
113). It is a MOVE where v13 has BUILD TOWER in 87 games (the spot between towers in place of a top-up) and a BUILD
TOWER on another site in 71 (another tower to stand at). The 22 games without a difference are short ones (median 53
turns).

The rule acts half as often in the games we lose: 18.8 different turns a game (9 % of their turns) against 39.9 (18 %) in
the games we win.

### 2. The queen stands under more towers (`cover.txt` sections 2-5)

| our queen, turns from 50 | v13 | v14 |
| --- | --- | --- |
| contact turns (a knight within 250) with three or more own towers covering her | 39 % | **54 %** |
| ... with four or more | 12 % | 21 % |
| contact turns a game under one or two towers | 21.2 | **15.4** |
| the gate's state (a knight within 500, none within 250): three or more | 53 % | 71 % |
| the first turn of a contact episode: three or more | 49 % | 71 % |
| no knight within 500: three or more | 58 % | 69 % |
| a better-covered spot within 180 of her on a contact turn | 46 % | 40 % |

- The difference in the share under three or more is +14.4 points (95 % interval +6.5..+22.7, games resampled); in the
  turns under one or two towers −5.9 a game (−10.5..−1.5). Both v14 placements are above both of v13's: 57 % and 51 %
  against 36 % and 42 %; 13.6 and 17.2 turns under one or two towers against 21.5 and 21.0.
- In the gate's state v14 moves to a spot on open ground on 18 % of the turns (5.2 a game) and walks to another own
  tower on 22 % (6.4; v13 4.4); the top-ups of a tower at 700 HP or more fall from 49.9 to 36.7 a game, those that add
  10 HP or less from 42.5 to 30.4.
- The local profiles had predicted this size: 53 % → 66 % in the mirror, 35 % → 48 % against `rival`.

### 3. The HP: the direction of the prediction, not established (`cover.txt` sections 2 and 6)

- What a contact turn costs under a given cover did not change: 1.11 / 0.46 / 0.17 / 0.16 HP under 1 / 2 / 3 / 4+
  towers for v13, 1.23 / 0.44 / 0.15 / 0.11 for v14. The rule moves turns along that gradient.
- HP lost to knights on contact turns from turn 50: **15.8 → 12.6 a game, −3.2 (95 % interval −8.1..+1.3)**; under one
  or two towers 12.9 → 9.7 (−7.5..+1.0). The study had sized the effect at 3.4 HP a game. The interval includes zero, and
  the placements overlap (v13 13.9 and 17.7, v14 11.0 and 14.2).
- By 50 turns, ours: 7.2 | 3.3 | 4.6 | 5.9 | 2.0 for v13, 7.2 | 2.1 | 3.3 | 4.3 | 2.9 for v14. The first wave is the
  same; turns 50-199 cost 13.8 → 9.7. The opponents' queens lose 10.7 → 12.9 from turn 50, so the balance from turn 50
  goes from −5.1 to +0.3 HP a game.

### 4. Like for like: the opponent bands (`cover.txt` sections 1 and 7)

| opponents rated | v13: games, won, deaths, lost races | v14: games, won, deaths, lost races | three or more on contact turns, v13 → v14 | our HP to knights from turn 50, v13 → v14 |
| --- | --- | --- | --- | --- |
| below 17 | 13, 100 %, 0, 0 | 32, 78 %, 3, 4 | 65 % → 44 % | 4.7 → 14.1 |
| 17-20 | 45, 69 %, 3, 11 | 45, 82 %, 3, 5 | 44 % → 60 % | 9.7 → 8.7 |
| 20-22 | 77, 58 %, 17, 15 | 62, 65 %, 14, 8 | 40 % → 52 % | 20.2 → 15.8 |
| 22 and up | 45, 40 %, 14, 13 | 41, 44 %, 6, 17 | 27 % → 57 % | 17.5 → 10.9 |

- From 17 up v14 scores more in every band, and the cover rises in every band.
- **Against 22 and up the rule shows most:** three or more towers on 57 % of the contact turns (27 %), 10.9 HP lost from
  turn 50 (17.5), deaths 6 of 41 (14 of 45). The score moves only from 40 % to 44 %: the deaths became lost races (13 →
  17).
- Below 17 the comparison is 13 games against 32, and v14's 32 hold the seven losses of section 8.

### 5. The results (`cover.txt` sections 1 and 8)

- v13: 107 won, 73 lost (39 races, 34 deaths). v14: 120 won, 60 lost (34 races, 26 deaths); the two equal-score games
  with the opponent as player 0 are among the lost races.
- Seat 1: 55 % → 66 % (seat 0: 65 % → 67 %).
- The lost races of v14 were lost by 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 4, 5 HP and more: 12 within 5 HP (v13: 11), 18
  within 9.

### 6. What the rule does not reach (`cover.txt` sections 6 and 8)

- **The deaths are the same games as before.** In v14's 26 deaths the queen has 2.8 own towers on her contact turns,
  1.83 of them cover her and three or more do on 16 % of the turns; in v13's 34 deaths 3.3 towers, 1.86 and 16 %. A death
  costs 48.5 HP on contact turns from turn 50 (v13 50.3), 43.2 of them under one or two towers. Where the line is not
  built there is no overlap to stand in, and the rule acts half as often (section 1).
- **The ladder and the economy are v13's:** own towers at turn 100 3.94 (3.80), at 150 4.51 (4.43); income at turn 100
  2.75 against the opponents' 4.75 (v13: 3.07 against 5.00), at 150 2.87 against 5.42; knights trained from turn 50:
  20.8 against their 36.6 (21.7 against 36.7).
- **Contact turns keep room:** a better-covered spot lies within 180 of her on 40 % of them. The kiting does not count
  the cover by design (`KITE_OVERLAP` lost locally at every weight).

### 7. The tripwire, and episodes begun on open ground (`cover.txt` section 5)

Contact episodes from turn 50 by where they began, a game (HP a game; HP each):

| begun | v13 | v14 |
| --- | --- | --- |
| at an own tower | 6.5 (10.6; 1.61) | 6.2 (7.0; 1.13) |
| on a walk to an own tower | 0.7 (2.7; 3.94) | 1.3 (2.0; 1.53) |
| on open ground | 0.2 (0.7; 4.43) | 0.7 (2.4; 3.40) |
| on a walk to a new site or mine | 0.04 (1.2; 26.8) | 0.03 (0.8; 29.6) |
| all | 7.5 (15.2) | 8.3 (12.2) |

- **The tripwire holds:** walks into a wave towards a new site did not come back (0.03 a game), and the episodes begun
  while she moves cost together 4.7 → 5.2 HP a game, as the local profiles had shown (2.0 → 1.8, 1.7 → 1.9, 0.3 → 0.9).
- **An episode begun on open ground costs three times one begun at a tower** (3.40 against 1.13 HP) at the same cover
  (3.02 and 3.11 towers on the first turn). At equal cover: 6.24 against 2.09 under two towers, 3.00 against 0.81 under
  three, 0.36 against 0.22 under four or more. The knights that reach her there are fresher (their HP sum 22.3 against
  13.0 on the first turn, 1.54 knights against 1.33) and her nearest tower is 175 away (107). 13 of the 125 such
  episodes cost 10 HP or more and hold 328 of their 425 HP.
- **Correction (2026-11-10, with the experiment `docs/data/exp-2026-11-10-open-ground/`): the class is a mixture, and
  the rule's own spots are its cheap part.** By what she did on the turn before the episode (`cover.txt`, the lines
  added to section 5):

  | v14, the turn before was | episodes | a game | HP each | HP a game | own towers covering her on the first turn | the knights' HP sum |
  | --- | --- | --- | --- | --- | --- | --- |
  | a MOVE with a knight within 500: the hold's spot off the towers | 83 | 0.46 | 1.86 | 0.86 | 3.35 | 15.6 |
  | the upgrade of an own mine she touches | 27 | 0.15 | 3.74 | 0.56 | 2.41 | 35.7 |
  | a new structure on the free site she touches | 14 | 0.08 | 10.86 | 0.84 | 2.21 | 32.6 |
  | other | 1 | 0.01 | 18.00 | 0.10 | 3.00 | 75.0 |

  The hold's spots off the towers cost 1.86 HP an episode against 1.13 at a tower: 1.6 times, not three, and 0.86 of
  the class's 2.4 HP a game. The expensive episodes are the touch rule's: she upgrades a mine or builds on a free site
  while a knight is within 500, and fresh knights reach her under two towers. v13 has those too (29 episodes: 0.16 a
  game, 0.71 HP a game). The first reading of this section, that the rule's choice of open ground might be a leak, was
  tested locally and does not hold: without the spots off the towers the cover falls and no HP is saved (the
  experiment's README).

### 8. Upload 2's seven losses to opponents rated below 17 (`cover.txt` sections 9 and 10)

- They are established players, not agents in placement: their ratings were the same on 2026-11-07 and 2026-11-09 (12.5
  to 15.1; VivienBillot, 16.8, is met here for the first time). Earlier Gold versions beat the five met before in 12 of
  14 games.
- Three deaths (turns 135, 218, 226) and four lost races (24:73, 1:3, 7:25, 14:38). In six of the seven the opponent
  out-earned us at turn 100 (income 7, 2, 6, 3, 13, 7 against our 3, 0, 4, 1, 3, 0); in three our income was 0 or 1.
- **Nothing ties them to the rule:** v13 would have played two of the three deaths turn for turn (jamessizeland,
  VivienBillot). In the other five games it differs on 5 to 49 turns, so the rule acted there; whether it cost
  anything in them is not known.
- Against the rating model (the logistic curve at Gold's scale 3.00 and each version's rating) v14 won 25 of 32 games
  below 17 where 29.1 are expected (−2.6 SD; upload 2 alone 21 of 28, −2.9 SD, the largest such shortfall so far). v12
  upload 2 had 21 of 29 (−1.9 SD) and v11 upload 4 55 of 84 (−1.2 SD); v12 over its three uploads 39 of 48 (−0.6 SD),
  v13 13 of 13. Such runs happened before v14 and did not repeat between the uploads of one version.

## What it says about the next step

No decision is taken here; these are the measured openings.
- The rule did what it was built for, and its size on the arena is that of the local profiles. Its ceiling is the tower
  line: the games still lost by death are those with 2.8 towers and an opponent with twice the income. That is the
  economy and the ladder of the v13 study's sections 5, 7 and 8 (the dead turns, a rival whose barracks stand forward),
  and the towers' sites chosen for overlap (`docs/data/exp-2026-11-10-overlap/`, Not tried).
- A narrow follow-up inside the rule: open ground. A flag that keeps the hold to the touch points of own towers, or
  takes open ground only when it adds two towers, is a development sweep on the seeds of the overlap experiment.
  Done 2026-11-10 (`docs/data/exp-2026-11-10-open-ground/`): neither gains (S 0.491 and 0.499 against v14 on 2000
  development maps), the rule stands. What the class points to instead is the touch rule in the gate's state: a mine
  upgrade and a build on a touched free site come before the hold (section 7's correction). Tested too
  (`docs/data/exp-2026-11-10-gate-touch/`): leaving the mine saves the queen's HP and loses the upgrades, level
  overall; a new tower is worth building under the wave.
- Against 22 and up the losses are now races (17 of 23), a third of them within 5 HP.

## Limits

- The two versions met different fields (opponents' median 20.90 and 20.45; v14's upload 2 met 28 opponents below 17,
  v13's two uploads 13). The band table is like for like on 41 to 77 games a cell.
- The intervals resample games within a version. They do not hold the difference between placements that P1's noise
  margin measures; with two placements a version that cannot be estimated here.
- The HP saving is not established at 95 %; the shift in the cover is.
- `cgAttribute -Pbot=v13` counts v13's answers on v14's positions, with v13's memory following the recorded states. It
  is not a game v13 would have played.
- Ratings are those at the fetch.
