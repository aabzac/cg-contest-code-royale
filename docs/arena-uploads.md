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
   0 (see "Build marker"). Preserve those exact bytes as `artifacts/<first 8 hex of its sha256>/Player.java`
   before another build replaces `build/submit`; see `docs/BOTS.md`, Artifacts.
2. **Before every Submit, save the live entry**: `tools/arena_snapshot.py` and `tools/arena_games.py`.
   CG lists the games of the live agent only: 6823071's list was empty after the re-upload.
3. The user submits. Register the new agent in `docs/data/arena-uploads.json` with its label, league,
   upload number, artifact, games-manifest path and pending placement (no invented score). Follow the placement with `arena_snapshot.py` (every 3 minutes, every minute from
   85 %) until `percentage` is 100 and `inProgress` is false. That first snapshot is the upload's
   **placement score**.
4. Download the games (`arena_games.py`). Check the code with `cgAttribute` against a kept copy of the
   uploaded artifact, `-Pbot=file:artifacts/<first 8 hex of its sha256>/Player.java` (gitignored;
   `build/submit` changes with every build), and check the markers in the manifest. Then complete the
   registry entry with placement and attribution evidence, and its row in `docs/BOTS.md` (Uploads).
   Where everything else goes: `.ai/project.md`, row "Записи после загрузки, эксперимента или решения".
5. **Upload the same configuration again** (normally the same artifact) and repeat 3-4 (upload 2).
6. Decide within the current league with
   `python -I tools/arena_compare.py --candidate <label> --selected <label>`:
   - **Roll back** when the candidate's mean placement score is below the selected version's mean by more
     than **M** and its pooled performance rating is below by more than two standard errors of the
     difference. Rolling back means re-uploading the selected version's artifact, which adds an upload to
     its baseline and to the noise sample.
   - **Early rollback**: when upload 1 alone is below the selected mean by more than 2 M, skip upload 2.
   - **Otherwise** there is no clear sign that the candidate is worse, and it becomes the selected version
     (frozen copy, `docs/BOTS.md`, `docs/STATUS.md`, an entry in `docs/SUMMARY.md`). This does not show that it is not worse: with M ≈ 3,
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

## Replay storage

Our arena captures live under `replays/arena/<label>/<league>/upload-NN/`:
`games/<gameId>.json` holds unchanged CodinGame responses, `snapshots/` holds battle lists and
leaderboard captures, and `checks/` holds retained attribution/validation logs. Upload numbers
come from the registry; re-uploading a build does not create a new version. Keep the number unique
within a label, as required by `arena_compare.py`, including after promotion to another league.
External study/validation corpora remain in `replays/cg/legend-top` and `replays/cg2`.

`docs/data/arena-uploads.json` is the source for agent-to-version/league/upload identity. Its
`legacyUploads` array indexes pre-project captures and is excluded from P1 comparisons. Compact
games manifests stay in `docs/data/`; their `rawDirectory` and `snapshotDirectory` fields locate
the ignored files. Do not derive a version from the current source tree or from a folder name.

Run from the repository root:

```text
python -B -I tools/arena_games.py 6828013
python -B -I tools/arena_snapshot.py 6828013 aabzac bronze docs/data/arena-6828013-presubmit-2026-10-31.json
python -B -I tools/arena_games.py 6828013 --offline --manifest build/arena-6828013-check.json
```

Both downloaders select storage from the registry; the games manifest defaults to its `games`
field. Register a pending upload before downloading its games. The games downloader automatically
merges saved battle lists, existing games and previous manifest IDs, including after replacement
empties the live list. `--saved-list PATH` adds a list from elsewhere; `--registry PATH` selects an
alternate registry. `--offline` makes no requests and reports missing completed games as failures.

Snapshots follow the actual observed agent and league. An unregistered replacement/promotion goes
to `replays/arena/unknown/<league>/agent-<id>/snapshots`; register it before fetching games. Those
saved lists are automatically reused after registration. A known but conflicting turn-0 build marker
puts the replay in that agent's `unknown/.../games` directory and fails the fetch; missing markers
are retained as null (old builds and startup failures). Identity checks still require `cgAttribute`.
Failures produce a nonzero exit status; responses already saved are preserved.

The 2026-10-31 migration and original-to-current path mapping are recorded in
`docs/data/arena-layout-2026-10-31.json`. Historical raw captures, logs and derived CSV bytes are
unchanged; their embedded old paths can be resolved through that mapping.

## Tools

- `tools/arena_snapshot.py`: the leaderboard row (by pseudo) and the live battle list.
- `tools/arena_games.py`: the games of an agent and their manifest, including each game's build marker.
- `./gradlew cgAttribute`: which code played, from the validated replays.
- `tools/arena_compare.py`: checks `docs/data/arena-uploads.json` against the manifests and makes no
  requests. Offline tests: `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`.
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

## Second candidate: v03, early towers (2026-10-30)

v03 is v02 with `INCOME_TARGET=3, MAX_TOWERS=8` (`docs/data/exp-2026-10-30-towers/`). Its P2 final
evaluation gave S 0.815 against v02, so P2 recommends the upload. The agent uploaded it with the user's
permission, the code swapped into the IDE's Play request and checked by sha256.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v03/upload-1 | 6827580 | 23.25, rank 240/2290 | 60 | 61.7 ± 6.3 | 25 of 36 | 12 of 24 | 23.07 | 23.25 ± 0.43 |
| v03/upload-2 | 6828013 | 20.67, rank 428/2290 | 60 | 65.0 ± 6.2 | 25 of 34 | 14 of 26 | 19.48 | 20.22 ± 0.40 |

- **Same code:** `cgAttribute` finds every game identical to `d6266d41` (10,194 and 8,286 turns), with
  the marker `cd1f3d33` in all 120.
- **After upload 1:** 23.25 was +5.44 above v02's mean (17.81), so P1 asked for upload 2.
- **Verdict after two uploads** (logistic scale 1.37): **not clearly worse**, so v03 is selected. Its Bronze
  baseline is 23.25 and 20.67.
  - The mean is 21.96, +4.15 against −M = −2.92.
  - The pooled rating is 21.75 ± 0.34 against v02's 17.68 ± 0.30, +4.06 against −2 SE = −0.90.
  - P1 claims no superiority. Even so, both measures are about 4 points above v02, and the local screen
    predicted that direction.
- **Noise:** v01's three, v02's two and v03's two uploads pool to SD 1.46 on 4 df, **M = 2.92**. That is
  still provisional: the 95 % interval of M is about 1.75-8.4. v03's two placements differ by 2.58.
- **Placement speed:** upload 2 placed in 38 minutes; upload 1 took two hours.

## The selected v03, a third upload (2026-10-31)

The user re-submitted v03 on 2026-10-31 at 01:07 (agent 6829912). Nobody watched the placement. The
first snapshot after it (02:13) shows it complete, and the leaderboard last updated the score at 02:06.
The live entry 6828013 was not saved before the re-submit. Its battle list was empty afterwards, and its
60 placement games were already stored.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v03/upload-1 | 6827580 | 23.25, rank 240/2290 | 60 | 61.7 ± 6.3 | 25 of 36 | 12 of 24 | 23.07 | 23.24 ± 0.41 |
| v03/upload-2 | 6828013 | 20.67, rank 428/2290 | 60 | 65.0 ± 6.2 | 25 of 34 | 14 of 26 | 19.48 | 20.19 ± 0.39 |
| v03/upload-3 | 6829912 | 23.37, rank 233/2289 at 02:13 | 60 | 63.3 ± 6.2 | 20 of 31 | 18 of 29 | 22.72 | 23.22 ± 0.39 |

From `python -I tools/arena_compare.py --candidate v03 --selected v02` (logistic scale 1.30, fitted on
all eight uploads; with it the earlier uploads' ratings move by up to 0.03).
- **Same code:** `cgAttribute` finds every game identical to `d6266d41` (10,047 turns), with the marker
  `cd1f3d33` in all 60. cgValidate: 60/60, no mismatch.
- Two wins are opponents' timeouts on turns 223 and 86. No tie.
- **v03's baseline in Bronze:** mean 22.43, SD 1.53 (3 uploads). Pooled rating 22.35 ± 0.26 (180 games).
  The decision of 2026-10-30 stands: against v02 the mean is +4.62 (−M = −2.81) and the rating +4.68
  (−2 SE = −0.77).
- **Noise:** v01's three, v02's two and v03's three uploads pool to SD 1.41 on 5 df, **M = 2.81**. Still
  provisional: the 95 % interval of M is about 1.75-6.9.
- **Ratings of identical code** are 23.24, 20.19 and 23.22. Their SD is 1.76, against standard errors of
  about 0.40, and Cochran's Q is 40.3 on 2 df at the fitted scale. Upload 2 met weaker opponents (median
  19.48 against 23.07 and 22.72) and won about as often as the other two. As with v01, the rating's
  standard error understates the spread between placements. The policy is unchanged.
- **Placement speed:** 59 minutes.
- **The gap to the boss** (above about 27.1): about 4.7 points from v03's mean, 3.7 from its best
  placement.

## Third candidate: `route` (ROUTE=1), first upload (2026-11-02)

The user pasted the artifact `da914c4c` (`current` with PARAMS `ROUTE=1`, commit eb5dd14; P2 final
evaluation S 0.5856 against v03, below the 0.60 recommendation, uploaded by the user's decision) on
2026-11-01 at 23:56 (agent 6836411), replacing v03 upload 3, which was saved before the paste
(`docs/data/arena-6829912-presubmit-2026-11-01.json`). The placement was watched as the procedure
says (every 3 minutes, every minute from 85 %): the first complete snapshot came at 01:30, 93 minutes
after the paste, with the score last updated at 01:29 (42 snapshots in
`replays/arena/route/bronze/upload-01/snapshots`).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| route/upload-1 | 6836411 | 22.89, rank 263/2288 | 60 | 65.0 ± 6.2 | 20 of 30 | 19 of 30 | 21.33 | 22.39 ± 0.43 |
| v03, three uploads | 6827580, 6828013, 6829912 | mean 22.43, SD 1.53 | 180 | | | | | 22.37 ± 0.27 |

From `python -I tools/arena_compare.py --candidate route --selected v03` (logistic scale 1.40, fitted on
all nine uploads; with it the earlier ratings move by up to 0.04, v03's pooled rating from 22.35 to 22.37).
- **Same code:** `cgAttribute` finds every game identical to `da914c4c` (SAME 60/60, 9,770 turns, every
  compared turn validated by the simulator), with the marker `5c7e3bf0` in all 60.
- Three wins are opponents' timeouts ("failed to provide 2 lines of output in time"): on the first turn
  (915286015, a startup timeout), turn 94 (915286510) and turn 97 (915313197), counted as for v03's games.
  No tie.
- **Against v03's baseline:** the placement score is +0.46 over the mean (22.89 against 22.43); the early
  rollback line is −2M = −5.62, so there is no early rollback. The rating is +0.02 ± 0.51 (22.39 ± 0.43
  against 22.37 ± 0.27). The tool's verdict: upload the candidate again; the P1 decision needs the mean of
  two uploads.
- **Noise:** M stays 2.81 (5 df); one upload of `route` adds no degree of freedom.
- **Shared opponents** (context, tiny samples): against v03/upload-1, 6 players and 7 games, 57.1 % for
  route against 83.3 % for v03 (stratified −21.1 ± 28.1 pp); against v03/upload-3, 5 players and 6 games,
  33.3 % against 66.7 % (−33.3 ± 28.9 pp); against v03/upload-2 one game each, both won.
- **Local prediction:** P2 put the candidate about +0.46 rating points head-to-head against v03 (S 0.5856
  at scale 1.33). The first placement is +0.46 on the mean, which is well inside the placement noise
  (M 2.81) and says nothing yet about the arena effect.
- **Placement speed:** 93 minutes.
- **The gap to the boss** (above about 27.1): about 4.2 points from this placement.

## The `route` candidate, second upload and the P1 verdict (2026-11-02)

The user pasted the same artifact `da914c4c` again on 2026-11-02 at 01:42 (agent 6836589), 13 minutes
after upload 1's placement was complete and saved. The placement was watched as before: the first
complete snapshot came at 03:39, 117 minutes after the paste, with the score last updated at 03:39:27
(55 snapshots in `replays/arena/route/bronze/upload-02/snapshots`).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| route/upload-1 | 6836411 | 22.89, rank 263/2288 | 60 | 65.0 ± 6.2 | 20 of 30 | 19 of 30 | 21.33 | 22.43 ± 0.46 |
| route/upload-2 | 6836589 | 22.10, rank 302/2288 | 60 | 66.7 ± 6.1 | 23 of 31 | 17 of 29 | 20.99 | 21.56 ± 0.46 |
| route, two uploads | | mean 22.50, SD 0.56 | 120 | | | | | 22.01 ± 0.33 |
| v03, three uploads | 6827580, 6828013, 6829912 | mean 22.43, SD 1.53 | 180 | | | | | 22.39 ± 0.28 |

From `python -I tools/arena_compare.py --candidate route --selected v03` (logistic scale 1.51, fitted on
all ten uploads; with it the earlier ratings move by up to 0.09, v03's pooled rating from 22.37 to 22.39).
- **Same code:** `cgAttribute` finds every game of upload 2 identical to `da914c4c` (SAME 60/60, 9,492
  turns, every compared turn validated by the simulator), with the marker `5c7e3bf0` in all 60.
- No opponent was removed by the referee in upload 2, and no tie.
- **Against v03's baseline:** the mean of two placements is +0.07 over v03's mean (22.50 against 22.43;
  the rollback line is −M = −2.61). The pooled rating is −0.38 ± 0.43 (22.01 ± 0.33 against
  22.39 ± 0.28; the rollback line is −2 SE = −0.87). Neither rollback condition holds. **Verdict: not
  clearly worse**, so under P1 `route` (`current?ROUTE=1`, artifact `da914c4c`) is the selected version
  from 2026-11-02, and the next candidate is judged against its two placements. This does not show that
  it is better: a difference up to about M is invisible here, and the rating difference is below zero by
  less than one standard error. Frozen as v04 on 2026-11-02 by the user's decision (ROUTE on by default
  in `current`); the live entry stays `route` upload 2.
- **Noise:** v01's three, v02's two, v03's three and `route`'s two uploads pool to SD 1.30 on 6 df,
  **M = 2.61** (before: 2.81 on 5 df). The 95 % interval of M is about 1.7-5.8. `route`'s two placements
  differ by 0.79.
- **Ratings of identical code** are 22.43 and 21.56 (SE 0.46 each), a difference of 0.87 ± 0.65: within
  the noise, unlike v01's and v03's spreads.
- **Shared opponents** (context, tiny samples): the two `route` uploads share 5 players (6 games), 40.0 %
  against 16.7 %; against v03/upload-1, 5 players and 5 games, 60.0 % for route against 80.0 % for v03
  (stratified −20.0 ± 31.6 pp).
- **Local prediction against the arena:** P2 put the candidate about +0.46 rating points head-to-head
  against v03 (S 0.5856 at scale 1.33); the arena shows +0.07 on the means and −0.38 ± 0.43 on the
  ratings. Both are inside the placement noise, so the arena neither confirms nor contradicts the local
  result; it only rules out a large loss.
- **Placement speed:** 117 minutes (upload 1: 93).
- **The gap to the boss** (above about 27.1): about 4.6 points from the mean of the two placements, 4.2
  from the better one.

## Fourth candidate: v05 (two mines before the barracks), first upload (2026-11-03)

The user pasted the artifact `13b3ec0c` (v05 = `current` with empty PARAMS, MINES_FIRST=2 by default,
commit 496bcc5; P2 screen S 0.7645 and final evaluation S 0.7556 against v04, the recommendation met) on
2026-11-03 at 14:40 (agent 6841317, submission 41622286), replacing `route` upload 2, whose placement
was complete and saved. The discovery snapshot at 14:42 (25 %, 13 wins of 16) went to the unknown
folder and was moved into `replays/arena/v05/bronze/upload-01/snapshots`; the watch (every 3 minutes)
found the placement complete at 14:51, 11 minutes after the paste, with the score last updated at
14:51:21 (5 snapshots, log `placement-watch.log` beside them).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v05/upload-1 | 6841317 | 23.93, rank 144/2289 | 60 | 68.3 ± 6.0 | 22 of 31 | 19 of 29 | 22.70 | 23.60 ± 0.51 |
| route, two uploads | 6836411, 6836589 | mean 22.50, SD 0.56 | 120 | | | | | 22.06 ± 0.35 |

From `python -I tools/arena_compare.py --candidate v05 --selected route` (logistic scale 1.63, fitted on
all eleven uploads; with it the earlier ratings move by up to 0.05, `route`'s pooled rating from 22.01 to
22.06, v03's from 22.39 to 22.42).
- **Same code:** `cgAttribute` finds every game identical to `13b3ec0c` (SAME 60/60, 9,613 turns, every
  compared turn validated by the simulator), with the marker `cc541951` in all 60.
- One win is an opponent removed by the referee (916020840), counted as for the earlier uploads. No tie.
- **Against `route`'s baseline:** the placement score is +1.43 over the mean (23.93 against 22.50); the early
  rollback line is −2M = −5.21, so there is no early rollback. The rating is +1.54 ± 0.62 (23.60 ± 0.51
  against 22.06 ± 0.35). The tool's verdict: upload the candidate again; the P1 decision needs the mean of
  two uploads.
- **Noise:** M stays 2.61 (6 df); one upload of v05 adds no degree of freedom.
- **Shared opponents** (context, tiny samples): against route/upload-1, 4 players and 5 games, 60.0 % for
  v05 against 50.0 % for route (stratified +15.4 ± 34.0 pp); against v03/upload-1, 8 players and 9-10
  games, 55.6 % against 50.0 % (+7.4 ± 23.6 pp).
- **Local prediction:** P2 put the candidate about +1.7 rating points head-to-head against v04 (S 0.7556
  at scale 1.5). The first placement is +1.43 on the mean and +1.54 on the rating: in the direction and
  of the size the local result predicted, though one placement is inside the placement noise (M 2.61).
- **Placement speed:** 11 minutes (the earlier placements took 93-117 minutes).
- **The gap to the boss** (above about 27.1): about 3.2 points from this placement.

## The v05 candidate, second upload and the P1 verdict (2026-11-03)

The user pasted the same artifact `13b3ec0c` again at 14:58 (agent 6841407, submission 41622456),
replacing upload 1, whose placement was complete and saved. The discovery snapshot at 14:58 (15 %, 11 wins
of 12) was moved into `replays/arena/v05/bronze/upload-02/snapshots`; the watch found the placement
complete at 15:11, 13 minutes after the paste, with the score last updated at 15:10:25 (5 snapshots).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v05/upload-1 | 6841317 | 23.93, rank 144/2289 | 60 | 68.3 ± 6.0 | 22 of 31 | 19 of 29 | 22.70 | 23.59 ± 0.51 |
| v05/upload-2 | 6841407 | 26.47, rank 27/2289 | 60 | 68.3 ± 6.0 | 25 of 37 | 16 of 23 | 25.17 | 26.02 ± 0.49 |
| v05, two uploads | | mean 25.20, SD 1.80 | 120 | | | | | 24.90 ± 0.37 |
| route, two uploads | 6836411, 6836589 | mean 22.50, SD 0.56 | 120 | | | | | 22.05 ± 0.35 |

From `python -I tools/arena_compare.py --candidate v05 --selected route` (logistic scale 1.61, fitted on
all twelve uploads).
- **Same code:** `cgAttribute` finds every game of upload 2 identical to `13b3ec0c` (SAME 60/60, 8,897
  turns, every compared turn validated), marker `cc541951` in all 60. No opponent removed, no tie.
- **Against `route`'s baseline:** the mean is +2.70 (25.20 against 22.50) with −M = −2.77; the pooled
  rating is +2.86 ± 0.51 (24.90 ± 0.37 against 22.05 ± 0.35) with −2 SE = −1.01. Neither rollback
  condition holds: **v05 is not clearly worse and becomes the selected version** (P1). The gain is of the
  size M, not above it, so it is the direction of the evidence, not a proof of superiority (the
  rating difference of 5.6 SE says more, with the SE caveat of the noise section).
- **Noise:** M = 2.77 (7 df): v05's two placements differ by 2.54 (SD 1.80), the largest spread after
  v01's and v03's.
- **Same opponents' field?** Upload 2 met stronger opponents (median 25.17 against 22.70 in upload 1) and
  won the same 41 of 60, hence the higher score; the win rate is identical. Shared opponents with the
  route uploads: 2 players and 2 games each, both won by v05 against 50 % for route (context, tiny).
- **Local prediction:** P2 put the candidate about +1.7 rating points head-to-head against v04; the arena
  shows +2.7 on the mean and +2.9 on the rating. The arena agrees with the local direction and exceeds the
  size; a head-to-head number and a field number differ by construction.
- **Placement speed:** 13 minutes.
- **The gap to the boss** (above about 27.1): about 1.9 points from the mean, 0.6 from upload 2 (rank 27;
  `eligibleForPromotion` false in the final snapshot).

## The selected v05, a third upload (2026-11-03)

The user pasted the same artifact `13b3ec0c` a third time at 15:30 (agent 6841579; "I ran one more
series"), replacing upload 2, whose placement was complete and saved. Nobody watched the placement: the
discovery snapshot by pseudo at 15:56 found it complete (score last updated 15:41, 11 minutes after the
paste); its raw responses were moved into `replays/arena/v05/bronze/upload-03/snapshots`.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v05/upload-1 | 6841317 | 23.93, rank 144/2289 | 60 | 68.3 ± 6.0 | 22 of 31 | 19 of 29 | 22.70 | 23.63 ± 0.54 |
| v05/upload-2 | 6841407 | 26.47, rank 27/2289 | 60 | 68.3 ± 6.0 | 25 of 37 | 16 of 23 | 25.17 | 26.07 ± 0.53 |
| v05/upload-3 | 6841579 | 25.77, rank 53/2289 | 60 | 71.7 ± 5.8 | 22 of 30 | 21 of 30 | 23.33 | 24.98 ± 0.55 |
| v05, three uploads | | mean 25.39, SD 1.31 | 180 | | | | | 24.96 ± 0.32 |
| route, two uploads | 6836411, 6836589 | mean 22.50, SD 0.56 | 120 | | | | | 22.09 ± 0.37 |

From `python -I tools/arena_compare.py --candidate v05 --selected route` (logistic scale 1.72, fitted on
all thirteen uploads; with it the earlier ratings move by up to 0.11; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game of upload 3 identical to `13b3ec0c` (SAME 60/60, 8,962
  turns, every compared turn validated), marker `cc541951` in all 60. One win is an opponent removed by
  the referee (916040203, counted like any game). No tie.
- **v05's baseline in Bronze:** mean 25.39, SD 1.31 (3 uploads); pooled rating 24.96 ± 0.32 (180 games).
  The decision of 2026-11-03 stands: against `route` the mean is +2.89 (−M = −2.61) and the rating +2.87
  (−2 SE = −0.97).
- **Noise:** v01's three, v02's two, v03's three, route's two and v05's three uploads pool to SD 1.31 on
  8 df, **M = 2.61** (2.77 before).
- **Ratings of identical code:** 23.63, 26.07 and 24.98, SD 1.22 against standard errors of about 0.54; as
  with v01 and v03, the spread between placements exceeds the rating's standard error.
- **Placement speed:** 11 minutes.
- **The gap to the boss** (above about 27.1): about 1.7 points from v05's mean, 0.6 from its best
  placement. The rollback artifact for the next candidate stays `13b3ec0c`.
