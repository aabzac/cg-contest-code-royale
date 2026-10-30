# Arena uploads: two placements per version (policy P1, 2026-10-29)

Status: **adopted 2026-10-29** at the user's suggestion, after they re-uploaded v01 to see the noise.
Taken from Back Track King's `docs/arena-uploads.md` (adopted there 2026-10-27, repository
`summer-challenge-2026-back-track-king` at `07a708c`), with this project's tools. It sets the arena
step of the promotion policy. The local screen a candidate passes before its first upload is policy P2
(`docs/local-screen.md`, adopted 2026-10-30).

## Why

- A placement here is 60 games against the Bronze field. The same artifact (`5bace689`) placed at
  **18.24** (rank 736/2296) and **16.08** (rank 1067/2294): 2.16 points apart. One upload cannot tell a
  candidate from noise. A third upload of the same configuration (`1e5d1a79`, with the build marker)
  placed at **18.98** (rank 654/2294).
- The score at the end of placement depends on which opponents the matchmaker serves and in which order.
  The three uploads met fields with median scores 17.82, 16.81 and 18.52 and won 60.0 %, 51.7 % and
  60.0 % of their games.

## Procedure

1. The candidate's artifact passes `submit` and both `procCheck` runs (`.ai/project.md`). Its sha256,
   build id and config id are recorded (`submit` prints them). The artifact prints the build id on turn
   0 (see "Build marker").
2. **Before every Submit, save the live entry**: `tools/arena_snapshot.py` and `tools/arena_games.py`.
   CG lists the games of the live agent only: 6823071's list was empty after the re-upload.
3. The user submits. Follow the placement with `arena_snapshot.py` (every 3 minutes, every minute from
   85 %) until `percentage` is 100 and `inProgress` is false. That first snapshot is the upload's
   **placement score**.
4. Download the games (`arena_games.py`). Check the code with `cgAttribute` against a kept copy of the
   uploaded artifact, `-Pbot=file:replays/artifacts/<first 8 hex of its sha256>/Player.java` (gitignored;
   `build/submit` changes with every build), and check the markers in the manifest. Then add the upload to
   `docs/data/arena-uploads.json` with its build and config ids.
5. **Upload the same configuration again** (normally the same artifact) and repeat 3-4 (upload 2).
6. Decide within the current league with
   `python -I tools/arena_compare.py --candidate <label> --selected <label>`:
   - **Roll back** when the candidate's mean placement score is below the selected version's mean by more
     than **M** and its pooled performance rating is below by more than two standard errors of the
     difference. Rolling back means re-uploading the selected version's artifact, which adds an upload to
     its baseline and to the noise sample.
   - **Early rollback**: when upload 1 alone is below the selected mean by more than 2 M, skip upload 2.
   - **Otherwise** there is no clear sign that the candidate is worse, and it becomes the selected version
     (frozen copy, `docs/BOTS.md`, `docs/STATUS.md`). This does not show that it is not worse: with M ≈ 3,
     a candidate up to about 3 points worse passes.
7. **M = 2 × the SD of placement scores between uploads of one configuration**, pooled over the versions
   with two or more placement scores in the league. With two uploads per version the SD of a difference
   of means is about one SD, so M is about two standard errors. It is re-estimated with every upload.
8. **Promotion during a placement.** CG then replays the placement in the new league as a new agent. The
   old-league upload gets `promotedTo` and no placement score: its last partial score is context, and its
   games count for the ratings only. A promoted candidate is kept, since it passed the league's boss. The
   selected version's baseline is then rebuilt in the new league, from the promoted agent's placement and
   further uploads there.
9. **Leagues.** Scores compare within one league only. After a promotion the selected version needs two
   placement scores in the new league before the next candidate is judged there.
10. Win rate, opponents' median score, each upload's own performance rating and shared opponents are
    context. The pooled rating per version is the second condition of a rollback (item 6). The rating
    corrects for the field only in part (below).

The selected version needs two placement scores in the league before a candidate is judged against it.
v01 has three in Bronze.

**Configuration.** A label holds one configuration: the bot sources and the params. Its config id is the
first 8 hex digits of the sha256 of the merged code from `class Player {` to the runner section, plus the
PARAMS value as written (`cr.submit.Build.config`). The import header does not count, because the
runner's imports join it. Builds that differ only in the runner share the id. Params spelled differently
(`""` and the defaults written out) do not. A runner change
can still change play (the fallback, say), so such a build also passes `procCheck`, which compares its
play with in-process `current`. v01's artifacts `5bace689` and `1e5d1a79` (with the marker) share config
`4194117d`. `arena_compare.py` refuses a registry whose label mixes config ids, whose manifest belongs to
another agent or does not load, whose league is not a league name, or whose games carry a marker other
than the entry's build, or none of them carries that build. Games without a marker (no turn-0 answer)
are listed, not refused. A candidate with an upload still placing gets "pending".

## Tools

- `tools/arena_snapshot.py`: the leaderboard row (by pseudo) and the live battle list.
- `tools/arena_games.py`: the games of an agent and their manifest, including each game's build marker.
- `./gradlew cgAttribute`: which code played, from the validated replays.
- `tools/arena_compare.py`: checks `docs/data/arena-uploads.json` against the manifests and makes no
  requests. Offline tests: `python -B -I -m unittest discover -s tools -p "test_*.py"`.
  - Per upload: placement score, record and seats, the opponents' median score, and the performance
    rating. The rating is the opponent score at which the upload would score 50 %, from a logistic fit
    against the opponents' scores at download time. The scale is shared by the league's uploads (profile
    likelihood; 2.0 until 60 games, or when every group meets one opponent score). The standard error
    comes from the Fisher information and treats the scale as known.
  - Per upload, the games where the referee removed a player (game score -1: a timeout or invalid
    output), by side. CG counts them like any game, and so does every figure.
  - Per version: the mean and SD of its placement scores and the pooled rating.
  - Per league: M, and the stratified head-to-head on shared opponents.

## Build marker

Code Royale has no message channel. The referee kills a player for a token after WAIT or MOVE, and after
BUILD unless the build warns first. A token on the TRAIN line that is not a site id also kills. The
artifact instead prints its build id on turn 0 as its TRAIN line, one site id 0-15 per hex digit. For
build `e5417a83` the line is `TRAIN 14 5 4 1 7 10 8 3`. No barracks exist on turn 0, so the referee only
warns `Cannot spawn from 14: not a barracks` and trains nothing, while the replay keeps the line in the
agent's stdout. Bronze maps have 24 sites. The runner prints no marker below 16 sites, so every printed
site id exists.

Live evidence before the first marked upload (356 local games, 2026-10-29):
- The local replays held 352 `Cannot spawn from N: not a barracks` warnings in 21 games, all from
  single-site TRAIN lines and none fatal; 16 of them were on turn 0.
- In 906359074 and 906522694 a player sent `BUILD s BARRACKS-KNIGHT` and `TRAIN s` on turn 0. The TRAIN
  got this warning, and the same TRAIN trained on turn 1. So no barracks exist when the turn-0 TRAIN is
  processed.
- The per-site warnings on multi-site TRAIN lines were all `Barracks N is training`: 69, of which 65 name
  the first site and 4 a later one where the earlier sites could train. So the referee checks the sites one
  by one, in order. Those lines also drew 381 `Training too many creeps` warnings.
- An 8-site TRAIN line was seen live: 17 turns of `TRAIN 3 4 10 13 14 16 20 22` in 912219998 (Silver), all
  of them barracks, with gold and creep-cap warnings and no kill.
- No live TRAIN line had several sites without barracks, and none has repeated a site yet. A build id with
  a repeated hex digit repeats a site id. In the repo referee the per-site check warns on the first id
  before the duplicate check runs, so this should still be a warning.

The first marked upload was the live check of eight sites without barracks (agent 6824680, v01 upload 3,
2026-10-29). A repeated site is still unchecked (below).
- All 60 placement games carry `TRAIN 14 5 4 1 7 10 8 3` on turn 0 (`arena_games.py`: markers
  `e5417a83` 60 of 60).
- Each game drew exactly one warning for it, `Cannot spawn from 14: not a barracks`. The seven later
  sites drew none, so the referee stops at the first site without barracks. No game ended on turn 0; the
  shortest lasted 39 turns. In 2 games the queen's turn-0 BUILD targeted site 14 itself; the TRAIN still
  only warned.
- `cgValidate` matched every warning (2,121 of 2,121 turns with a warning, 6,222 turns). `cgAttribute`
  against the kept copy gave SAME in 60 of 60.
- Apart from the marker the agent drew no warning in these games. The opponents drew 226 single-site
  `not a barracks` warnings and one more `Barracks N is training` on a multi-site line (`TRAIN 5 16` in
  913236684, turn 50).

A repeated site is still not seen live: build `e5417a83` has eight distinct hex digits. In the repo
referee the duplicate check runs only after every site has passed the barracks check. On turn 0 no site
has barracks, and live the check stops at the first site, so a repeated site should never reach the
duplicate check.

- The build id is the first 8 hex digits of the sha256 of the artifact with an empty BUILD literal.
  `FileMerger` writes and prints it, after checking that the artifact has no second literal.
  `procCheck` and `cgAttribute` check it against the artifact's content.
- An artifact built before the marker (`5bace689`, say) has no BUILD literal and no build id.
- `arena_games.py` records each game's marker. `cgAttribute` prints the marker per game, also when turn
  0 is not compared, and in its summary.
- The marker is self-reported identity: it names the build the agent says it is. A bot without a marker
  (in-process `current`, or a build before the marker) is excused the recorded marker, so `cgAttribute`'s
  SAME shows that the play matches, and the markers name the build.

## First noise measurement (2026-10-29)

`python -I tools/arena_compare.py --candidate v01 --selected v01` on `docs/data/arena-uploads.json`:

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v01/upload-1 | 6823071 | 18.24, rank 736/2296 | 60 | 60.0 ± 6.3 | 21 of 33 | 15 of 27 | 17.82 | 18.16 ± 0.38 |
| v01/upload-2 | 6823455 | 16.08, rank 1067/2294 at 20:51 | 60 | 51.7 ± 6.5 | 20 of 30 | 11 of 30 | 16.81 | 16.78 ± 0.37 |

- Same bytes: `cgAttribute` finds every game of both uploads identical to `5bace689` (7,230 and 6,413
  turns).
- **SD 1.53, M ≈ 3.05 CG points** (one pair, one degree of freedom: provisional).
- The performance ratings differ by 1.38 ± 0.53 at the fitted logistic scale 1.32. That is 2.6 standard
  errors for identical code. The scale is weakly identified: its 95 % profile interval is about
  0.76-2.81, and at scale 2.0 the gap is 2.0 standard errors. A likelihood-ratio test with the scale
  profiled out gives z ≈ 2.3 (review, 2026-10-29). Either way, the rating's standard error understates the
  spread between placements. It is the weaker condition of a rollback: it can prevent one, never cause
  one.
- Shared opponents: 2 players with one game each. The tool's +100 ± 50 pp means nothing at this size, so
  this is context only.
- Upload 2's placement was not watched. Its first completed snapshot is from 20:51. The score is its
  placement-end score: the leaderboard last updated it at 15:51, and no game followed the 60. The rank
  is from 20:51.
- The old entry 3315948 (agent created 2020-11-23 22:01 UTC, last score update 2026-10-17 23:46 UTC)
  stood at 19.64, rank 559, on 2026-10-28. That is a long-run rating, not a placement score, so it is compared with neither upload.

Consequence: with M ≈ 3 points the arena step catches only large regressions. A candidate's improvement
has to be shown locally first (phase 3). More uploads per version lower the noise of a mean (SD/√k) and
refine M.

## Noise after three uploads (2026-10-30)

The user uploaded v01 a third time, as the marked artifact `1e5d1a79` (same config `4194117d`). The table,
the mean, SD and M, the pooled rating and the shared opponents come from
`python -I tools/arena_compare.py --candidate v01 --selected v01`. The other figures come from
`python -B -I docs/data/noise-2026-10-30/noise.py`, run from the repository root (output
`docs/data/noise-2026-10-30/noise.txt`). It uses the same logistic model:

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v01/upload-1 | 6823071 | 18.24, rank 736/2296 | 60 | 60.0 ± 6.3 | 21 of 33 | 15 of 27 | 17.82 | 18.16 ± 0.39 |
| v01/upload-2 | 6823455 | 16.08, rank 1067/2294 at 20:51 | 60 | 51.7 ± 6.5 | 20 of 30 | 11 of 30 | 16.81 | 16.78 ± 0.37 |
| v01/upload-3 | 6824680 | 18.98, rank 654/2294 | 60 | 60.0 ± 6.3 | 22 of 33 | 14 of 27 | 18.52 | 18.93 ± 0.41 |

- Same configuration: `cgAttribute` finds every game identical to the uploaded artifact (7,230, 6,413 and
  6,222 turns).
- Upload 2's 31 wins include 3 games where the referee removed the opponent for not answering turn 0
  (913110374, 913114190, 913119798; game score -1). CG counts them, and so do the figures here. No game of
  the three uploads removed v01. `arena_compare.py` now lists such games per upload and side.
- **Mean 17.77, SD 1.51 (2 df), M = 3.01.** The pair alone gave 1.53 and 3.05. Still provisional: with
  2 df the 95 % interval of the SD is 0.78-9.47, so that of M is 1.6-19.
- Pooled rating 17.92 ± 0.23 (180 games), at the logistic scale 1.33 fitted on all three. The scale's 95 %
  profile interval is 0.88-2.24.
- The three ratings of identical code spread more than their standard errors allow. Their SD is 1.09,
  2.8 times the mean standard error (0.37-0.41); that ratio is a 2-df figure too.
  - A likelihood-ratio test of one rating for all 180 games against one per upload, with the scale fitted
    in each model, gives 11.1 on 2 df (p ≈ 0.004).
  - Cochran's Q gives 15.9 on 2 df at the fitted scale and 8.7 at scale 2.0.
  - Upload 3, the new data, against the pooled rating of uploads 1-2 (17.46 ± 0.27): z ≈ 3.0.
  - Without the 3 forfeits: upload 2 won 28 of 57, its rating is 16.67, and the likelihood ratio is 12.2
    (p ≈ 0.002).
  - Matchups with individual opponents cannot produce this. Each upload met 56-59 different opponents,
    so such effects are independent from game to game and stay within the standard errors. The cause has
    to be shared by the games of one upload (the field or the conditions of its time window), or the
    spread is chance, which the tests make unlikely.
- Consequence for P1: the rating's standard error understates the spread between placements, so the
  second rollback condition (rating below by more than two standard errors) is easy to meet, and in
  practice a rollback rests on M. The policy is unchanged. Scaling the rating's standard error by the
  observed overdispersion would make the condition informative; adopting that is a policy decision.
- Seats over the 180 games: seat 0 won 63 of 96 (65.6 %), seat 1 40 of 84 (47.6 %); two-proportion
  z ≈ 2.5. The opponents' median scores were 17.46 and 17.42. A logistic fit with an intercept per upload
  and the opponent's score gives seat 1 -0.795 ± 0.344 (z ≈ -2.3): seat 1 lowers the rating by about 1.1
  points. Adding the game's order within its placement gives -0.815 ± 0.347. Without the 3 forfeits: 61
  of 94 and 39 of 83.
  Greedy self-play leans the same way (player 0 won 187, player 1 110 of 300). This is a phase 3 item in
  `docs/PLAN.md`.
- Shared opponents: 2-3 players per pair of uploads, context only.

## First candidate: v02, two uploads (2026-10-30)

v02 is v01 with the targeted fix UNSTICK (`docs/local-screen.md`). Its P2 final evaluation gave S 0.5032
against v01. The user chose to upload it anyway, although P2 recommends an upload from S ≥ 0.60 only.
The agent submitted both uploads in the desktop app's built-in browser, each with the user's explicit
permission, and checked CG's saved IDE code by sha256 against the artifact.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v02/upload-1 | 6826276 | 17.23, rank 891/2289 | 60 | 61.7 ± 6.3 | 19 of 30 | 18 of 30 | 16.02 | 16.74 ± 0.43 |
| v02/upload-2 | 6827094 | 18.40, rank 715/2289 | 60 | 60.0 ± 6.3 | 19 of 26 | 17 of 34 | 18.00 | 18.39 ± 0.39 |

- **Same code:** `cgAttribute` finds every game identical to `52724496` (5,983 and 7,084 turns), with the
  marker `a2c71dbb` in all 120.
  - In-process v01 differs in 2 and in 4 games, so UNSTICK changed the play in 6 of 120.
  - The marker repeats site 11, and live it only warned.
  - Opponents' turn-0 timeouts: 1 and 2 games. v02 was never removed.
- **Ties:** one tie game in each placement, 0:0 and 50:50. CG ranked player 0 first in both, as in the
  two earlier known ties.
- **Verdict** (`python -I tools/arena_compare.py --candidate v02 --selected v01`, logistic scale 1.35):
  **not clearly worse**.
  - The mean is 17.81 against 17.77, +0.05 against −M.
  - The pooled rating is 17.68 ± 0.29 against 17.92 ± 0.23, −0.24 against −2 SE = −0.75.
  - v02 becomes the selected version, with its Bronze baseline these two uploads.
- **Noise:** v01's three uploads and v02's two pool to SD 1.32 on 3 df, **M = 2.64**. That is still
  provisional: the 95 % interval of M is about 1.5-9.8. Since v02 played as v01 in 114 of 120 games, its
  pair is in effect more samples of v01's spread.
- **Matchmaking:** after upload 1's first 10 games, CG scheduled no battle for about 26 minutes (10:01 to
  10:27), then resumed. A re-submit would not have helped, so the user waited.
