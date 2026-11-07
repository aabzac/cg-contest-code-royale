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
Steps 2-4 are one command since 2026-11-04: `python -B -I tools/arena.py follow <label>`, started just
before the paste (Tools, below). The steps say what it does; the tools it runs stay usable alone.

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
   `tools/arena.py` fills the registry entry (placement and attribution); the rows and records for people
   stay to be written.
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
   By the user's decision a league may use another league's M instead (`noiseMargins` in the registry):
   adopted 2026-11-04 for Silver, which uses Bronze's M (below, Silver noise margin).
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

Our arena captures live under `replays/arena/<label>/<league>/upload-NN/`, and an upload directory
holds these three and nothing else:
- `games/<gameId>.json`: unchanged CodinGame responses;
- `snapshots/`: battle lists and leaderboard captures, raw;
- `checks/`: logs and reports, `arena.log` of `tools/arena.py` (with the output of the tools it ran),
  `cgAttribute.log`, `arena-compare.json`; uploads before 2026-11-04 also keep there the logs of their
  placements and, from v05 on, the one-off watch scripts (`*.sh`) that wrote them.

Upload numbers come from the registry; re-uploading a build does not create a new version. Keep the
number unique within a label, as required by `arena_compare.py`, including after promotion to another
league (v08's Silver uploads are 3 and 4).
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

The 2026-11-04 migration (`docs/data/arena-layout-2026-11-04/`: `migrate.py` and `record.json` with
every move and its sha256) restored that layout:
- the files written at the top of upload directories since v04 moved to `checks/`;
- the two snapshots left in `unknown/bronze/agent-6829912` joined v03 upload 3;
- the label `route` became **v04**: agents 6836411 and 6836589, uploaded as `current?ROUTE=1` and
  frozen later as v04 (they play as v04 command for command). The registry keeps the old name as
  `formerLabel`, and the two manifests point to `replays/arena/v04/`.

Older text here and in `docs/DEVLOG.md` keeps the name `route` and the old file locations; the record
maps them.

## Tools

- `tools/arena.py`: one command for an upload, from the repository root.
  - `status` shows the leaderboard row of `aabzac` and the registered upload it belongs to; it writes
    nothing.
  - `follow <label> [--selected <label>]`, started just before the paste:
    1. checks the artifact before anything else: the label's kept copy, or `build/submit/Player.java` for
       a new label (`./gradlew artifactInfo -Pfile=...` for new bytes); another configuration under the
       label is refused;
    2. saves the live entry;
    3. names the file to paste, with its sha256 and build id, and warns when `build/submit/Player.java`
       holds other bytes. It then waits for the new agent. When the old agent's battle list is empty, the
       paste has reached CG and the leaderboard lags (7 minutes at upload 5): it says so instead of asking
       for another paste;
    4. reads the new agent's first finished game: another build's turn-0 marker stops the command before
       the registration, with the replay kept under `unknown/`;
    5. registers the agent and keeps the artifact in `artifacts/`;
    6. follows the placement at P1's cadence while fetching the finished games a minute after the last
       fetch ended, stops on games with another build's marker, and records the placement score;
    7. runs `cgAttribute` and records its result, then prints the label's comparison (with
       `--selected`, the P1 verdict).

    A promotion during the placement becomes the next upload in the new league. Interrupted, the same
    command resumes. A complete entry without `attribution` (a follow stopped after the placement)
    resumes under its own label instead of starting a re-upload.
  - `fetch [--agent ID]` fetches the games again and reruns `cgAttribute`.

  Progress goes to the console and to the upload's `checks/arena.log`. Offline tests:
  `tools/tests/test_arena_follow.py`.
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

## Fifth candidate: v06 (a second tower under threat, the army flags), first upload (2026-11-03)

The user pasted `1a9f3e81` at 17:39 (agent 6842260, submission 41624586; "I pasted it, watch"), replacing
v05's upload 3, whose placement was complete and saved. The discovery snapshot at 17:40 (18 %, 12 wins of
13) was moved into `replays/arena/v06/bronze/upload-01/snapshots`; the watch (`placement-watch.sh`, `.log`
there) found the placement complete at 17:55, 16 minutes after the paste, with the score last updated at
17:54 (7 snapshots).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v06/upload-1 | 6842260 | 27.09, rank 1/2290 (local rank among the players) | 60 | 71.7 ± 5.8 | 22 of 31 | 21 of 29 | 25.55 | 26.79 ± 0.60 |
| v05, three uploads | 6841317, 6841407, 6841579 | mean 25.39, SD 1.31 | 180 | | | | | 25.02 ± 0.34 |

From `python -I tools/arena_compare.py --candidate v06 --selected v05` (logistic scale 1.86, fitted on all
fourteen uploads; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game identical to `1a9f3e81` (SAME 60/60), marker `0bf54983` in all
  60. Three wins are opponents removed by the referee (916100796, 916101800, 916108462; counted like any
  game). No tie.
- **The Bronze boss in the games:** three games against agent 1870318 (no pseudo; rating 27.63 at the fetch,
  above every player), all lost (916106598 0:20, 916113842 0:50, 916114160 0:11). That rating is the first
  measurement of the boss's score the project has (`docs/STATUS.md` had "above about 27.1"); v06's 27.09 is
  rank 1 among the players and 0.5 below the boss, `eligibleForPromotion` false.
- **Against v05's baseline:** +1.70 on the mean (27.09 against 25.39) with 2M = 5.22, so no early rollback;
  the verdict needs the second upload of the same artifact. The performance rating 26.79 ± 0.60 against
  25.02 ± 0.34 (+1.77 ± 0.69) is context until then.
- **Local prediction:** P2 put v06 about +1.1 rating points head-to-head against v05; the first placement
  shows +1.7 on the mean. One placement inside the arena's noise (M 2.61).
- **Placement speed:** 16 minutes.

## The v06 candidate, second upload and the P1 verdict (2026-11-03)

The user pasted the same artifact `1a9f3e81` again at 18:02 (agent 6842539), replacing upload 1, whose
placement was complete and saved. The watch snapshotted every minute from 18:03 (15 %), but read a stale
summary: `arena_snapshot.py` keeps a summary file that belongs to another agent and writes the new one
beside the raw responses, so the loop kept seeing 6842260 while the raw snapshots of 6842539 went to
`replays/arena/unknown/…` (moved into `upload-02/snapshots` afterwards). The first raw snapshot with the
placement complete is 18:16:46 (score last updated 18:16:00), 14 minutes after the paste.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v06/upload-1 | 6842260 | 27.09, rank 1/2290 | 60 | 71.7 ± 5.8 | 22 of 31 | 21 of 29 | 25.55 | 26.84 ± 0.62 |
| v06/upload-2 | 6842539 | 25.39, rank 77/2290 | 60 | 73.3 ± 5.7 | 23 of 32 | 21 of 28 | 22.25 | 24.48 ± 0.61 |
| v06, two uploads | | mean 26.24, SD 1.20 | 120 | | | | | 25.75 ± 0.46 |
| v05, three uploads | 6841317, 6841407, 6841579 | mean 25.39, SD 1.31 | 180 | | | | | 25.06 ± 0.35 |

From `python -I tools/arena_compare.py --candidate v06 --selected v05` (logistic scale 1.95, fitted on
all fifteen uploads; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game of upload 2 identical to `1a9f3e81` (SAME 60/60), marker
  `0bf54983` in all 60. One win is an opponent removed by the referee (916125229). No tie.
- **Against v05's baseline:** the mean is +0.85 (26.24 against 25.39) with −M = −2.59; the pooled rating is
  +0.69 ± 0.58 (25.75 ± 0.46 against 25.06 ± 0.35) with −2 SE = −1.16. Neither rollback condition holds:
  **v06 is not clearly worse and becomes the selected version** (P1). The gain is a third of M: the direction
  of the evidence, not a proof of superiority.
- **Noise:** M = 2.59 (9 df): v06's two placements differ by 1.70 (SD 1.20).
- **Same opponents' field?** Upload 2 met weaker opponents (median 22.25 against 25.55 in upload 1) and won
  one game more (44 of 60); its score is 1.70 lower. The win rates, 71.7 and 73.3 %, are the highest of the
  project's fifteen uploads.
- **Local prediction:** P2 put v06 about +1.1 rating points head-to-head against v05; the arena shows +0.85 on
  the mean and +0.7 on the rating. The arena agrees with the local direction and size.
- **Placement speed:** 14 minutes.
- **The gap to the boss** (27.63, measured in upload 1's games): 1.4 points from v06's mean, 0.5 from its
  best placement. The rollback artifact for the next candidate is now v06's `1a9f3e81`.

## Sixth candidate: v07 (kiting under knights), first upload (2026-11-03)

The user pasted `4c7b0d04` at 20:45 (agent 6843218; "I pasted it, watch"), replacing v06's upload 2, whose
placement was complete and saved. The discovery used a fresh summary file per attempt (the lesson of
v06's upload 2) and found the new row at 20:47 (18 %, 14 wins of 14); the watch found the placement
complete at 20:58, 13 minutes after the paste, with the score last updated at 20:57 (6 snapshots;
`placement-watch.log` in `replays/arena/v07/bronze/upload-01/`).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v07/upload-1 | 6843218 | 26.42, rank 32/2290 | 60 | 68.3 ± 6.0 | 21 of 29 | 20 of 31 | 24.95 | 26.14 ± 0.56 |
| v06, two uploads | 6842260, 6842539 | mean 26.24, SD 1.20 | 120 | | | | | 25.71 ± 0.45 |

From `python -I tools/arena_compare.py --candidate v07 --selected v06` (logistic scale 1.89, fitted on
all sixteen uploads; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game identical to `4c7b0d04` (SAME 60/60, 9,301 turns, every
  compared turn validated), marker `b6b5f0f7` in all 60. Two wins are opponents removed by the referee
  (916208555, 916208563; counted like any game). No tie. No game against the boss this time.
- **Against v06's baseline:** +0.18 on the mean (26.42 against 26.24) with 2M = 5.18, so no early rollback;
  the verdict needs the second upload of the same artifact. The performance rating 26.14 ± 0.56 against
  25.71 ± 0.45 (+0.43 ± 0.72) is context until then.
- **Local prediction:** P2 put v07 about +1.5 rating points head-to-head against v06; the first placement
  shows +0.2 on the mean, inside the arena's noise (M 2.59). One placement says little either way.
- **Placement speed:** 13 minutes.
- **The gap to the boss** (27.63): 1.2 points from this placement.

## The v07 candidate, second upload and the P1 verdict (2026-11-03)

The user pasted the same artifact `4c7b0d04` again at 21:03 (agent 6843287) and said so at 21:30, when the
discovery snapshot found the placement already complete (score last updated 21:14; nobody watched it).
Upload 1's placement was complete and saved before the paste.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v07/upload-1 | 6843218 | 26.42, rank 32/2290 | 60 | 68.3 ± 6.0 | 21 of 29 | 20 of 31 | 24.95 | 26.18 ± 0.59 |
| v07/upload-2 | 6843287 | 24.64, rank 108/2290 | 60 | 70.0 ± 5.9 | 21 of 30 | 21 of 30 | 23.14 | 24.33 ± 0.61 |
| v07, two uploads | | mean 25.53, SD 1.26 | 120 | | | | | 25.34 ± 0.43 |
| v06, two uploads | 6842260, 6842539 | mean 26.24, SD 1.20 | 120 | | | | | 25.76 ± 0.46 |

From `python -I tools/arena_compare.py --candidate v07 --selected v06` (logistic scale 1.97, fitted on
all seventeen uploads; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game of upload 2 identical to `4c7b0d04` (SAME 60/60, 9,615
  turns, every compared turn validated), marker `b6b5f0f7` in all 60. Three wins are opponents removed by
  the referee (916214881, 916214882, 916217955). No tie, no boss game.
- **Against v06's baseline:** the mean is −0.71 (25.53 against 26.24) with −M = −2.58; the pooled rating is
  −0.41 ± 0.63 (25.34 ± 0.43 against 25.76 ± 0.46) with −2 SE = −1.26. Neither rollback condition holds:
  **by P1 v07 is not clearly worse and becomes the selected version.** The sign is negative on both
  measures, inside the noise: the arena shows no gain where the local proxy predicted +1.5 rating points
  (P2: S 0.68-0.69 against v06 and +3 pp against `field`). The policy's known weakness applies here in full:
  a candidate up to about M worse passes.
- **Proxy disagreement (PV2 note):** this is the first candidate whose local gain (the largest since v03)
  did not show on the arena at all. Possible reasons, none measured: the arena's winners kite better than
  our one-step rule and punish it; the arena field has archers and giants, against which the kiting queen
  leaves her towers; two placements of 60 games cannot see +1.5 at M 2.58 anyway. The local opponents
  (the mirror and `field`) are both knights-only ladders that stand still under knights.
- **Noise:** M = 2.58 (10 df); v07's two placements differ by 1.78.
- **Placement speed:** about 11 minutes (the score last updated 11 minutes after the creation time).
- **The gap to the boss** (27.63): 2.1 points from v07's mean, 1.4 from v06's. The rollback artifact for
  the next candidate is v07's `4c7b0d04` by the policy; v06's `1a9f3e81` stays kept.

## Seventh candidate: v08 (kiting bound to the towers), first upload (2026-11-03)

The user pasted `5c16d925` at 22:54 (agent 6843773; "I pasted it, watch"), replacing v07's upload 2, whose
placement was complete and saved. The discovery found the new row at 22:55 (16 %); the watch found the
placement complete at 23:08, 13 minutes after the paste (5 snapshots; `placement-watch.log` in
`replays/arena/v08/bronze/upload-01/`). The games were fetched during the placement (`fetch-loop.sh`, every
minute), all 60 before it completed, against the risk of a promotion moving the live list away (the user's
point: the score passed the boss's mid-placement).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v08/upload-1 | 6843773 | 27.85, rank 1/2291 (local rank among the players) | 60 | 70.0 ± 5.9 | 21 of 28 | 21 of 32 | 26.75 | 27.64 ± 0.60 |
| v07, two uploads | 6843218, 6843287 | mean 25.53, SD 1.26 | 120 | | | | | 25.34 ± 0.43 |

From `python -I tools/arena_compare.py --candidate v08 --selected v07` (logistic scale 1.95, fitted on
all eighteen uploads; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game identical to `5c16d925` (SAME 60/60, 9,459 turns, every
  compared turn validated), marker `3f19aa6e` in all 60. No opponent removed, no tie.
- **The Bronze boss, seven games:** agent 1870318, rating 28.08 at the first of them and 27.90 at the last
  (it fell during our placement); v08 won 3 of the 7 (916267498 59:0, 916269344 36:21, 916269626 72:22) where
  v06 lost all 3 of its boss games. The placement ended at 27.85 against the boss's 27.90:
  `eligibleForPromotion` false by that margin.
- **Against v07's baseline:** +2.32 on the mean (27.85 against 25.53) with 2M = 5.16, so no early rollback;
  the verdict needs the second upload of the same artifact. The performance rating 27.64 ± 0.60 against
  25.34 ± 0.43 (+2.30 ± 0.74) is context until then. The opponents' median 26.75 is the highest of any
  upload: the field at the top of Bronze.
- **Local prediction:** P2 put v08 about +1.4 rating points head-to-head against v07 (with the caveat that
  the proxy had over-rewarded v07's kiting); the first placement shows +2.3 on the mean, one placement
  inside the arena's noise (M 2.58), in the direction the local measure predicted this time.
- **Placement speed:** 13 minutes.

## The v08 candidate, second upload and the P1 verdict (2026-11-03): v08 selected, promotion flagged

The user pasted the same artifact `5c16d925` again at 23:12 (agent 6843829; "I pasted it, watch"),
replacing upload 1, whose placement was complete and saved. The discovery found the row at 23:14 (31 %);
the games were fetched every minute during the placement (all 60 by 23:23); the watch found the placement
complete at 23:23, 11 minutes after the paste, with `eligibleForPromotion` **true**.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v08/upload-1 | 6843773 | 27.85, rank 1/2291 | 60 | 70.0 ± 5.9 | 21 of 28 | 21 of 32 | 26.75 | 27.73 ± 0.65 |
| v08/upload-2 | 6843829 | 28.13, rank 1/2291 | 60 | 80.0 ± 5.2 | 30 of 35 | 18 of 25 | 23.93 | 27.39 ± 0.76 |
| v08, two uploads | | mean 27.99, SD 0.20 | 120 | | | | | 27.59 ± 0.50 |
| v07, two uploads | 6843218, 6843287 | mean 25.53, SD 1.26 | 120 | | | | | 25.42 ± 0.46 |

From `python -I tools/arena_compare.py --candidate v08 --selected v07` (logistic scale 2.13, fitted on
all nineteen uploads; `arena-compare.json` beside the games).
- **Same code:** `cgAttribute` finds every game of upload 2 identical to `5c16d925` (SAME 60/60, 9,087
  turns, every compared turn validated), marker `3f19aa6e` in all 60. One win is an opponent removed by
  the referee (916274351). One game against the boss (916275323, rating 27.81), won 25:0.
- **Against v07's baseline:** the mean is +2.46 (27.99 against 25.53) with −M = −2.46; the pooled rating is
  +2.17 ± 0.68 (27.59 ± 0.50 against 25.42 ± 0.46) with −2 SE = −1.36. Neither rollback condition holds:
  **v08 is not clearly worse and becomes the selected version** (P1). This time the gain equals M on the
  mean and is three standard errors on the rating: the direction and the size of the local prediction
  (+1.4 against v07) are both confirmed, after v07's local gain had not shown.
- **Noise:** M = 2.46 (11 df); v08's two placements differ by 0.28, the tightest pair of the project.
- **The boss:** 3 of 7 in upload 1 and 1 of 1 in upload 2 against agent 1870318 (rating 27.81-28.08 over the
  evening); v08's mean 27.99 is at its level. 80 % wins in upload 2 is the highest of any upload.
- **Promotion:** `eligibleForPromotion` true at the first complete snapshot; the live row was still in
  Bronze at 23:24. By the policy a promotion creates a new agent in Silver whose placement is the next
  upload, recorded with its league; the Bronze scores above are complete placements and stay. In Silver
  the selected version's baseline is built anew by two uploads there before the next candidate is judged.
- **Placement speed:** 11 minutes.

## v08 promoted to Silver: upload 3, the first Silver placement (2026-11-04)

CG promoted v08 upload 2 after its completed Bronze placement: a new agent 6843976 in Silver, created
2026-11-03 23:46, with the same submission 41627919 as upload 2's battle list (no paste). Upload 2 keeps its
Bronze score (28.13); the promotion did not interrupt a placement, so no `promotedTo`. Nobody watched: on the
user's request ("we moved up to Silver, fetch the games") the discovery snapshot at 00:04 found the Silver
placement complete (score last updated 00:02, about 16 minutes after the agent was created).

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v08/upload-3 (Silver) | 6843976 | 20.70, rank 2/200 | 80 | 66.2 ± 5.3 | 27 of 40 | 26 of 40 | 18.73 | 20.88 ± 0.95 |

From `python -I tools/arena_compare.py` (Silver: logistic scale 3.81, fitted on this one upload only, so the
rating's SE is rough; `arena-compare.json` beside the games). The Bronze tables above are unchanged.
- **Same code:** `cgAttribute` finds every game identical to `5c16d925` (SAME 80/80, 15,934 turns, every
  compared turn validated: the simulator holds on Silver games too), marker `3f19aa6e` in all 80. One win is
  an opponent removed by the referee (916284417).
- **The Silver boss:** agent 1876720 (no pseudo), rating 21.87 at the fetch; 10 of the 80 games, 4 won
  (916287859, 916288639, 916290151, 916290814). v08 is 1.17 below it; local rank 2 leaves one player above.
- **The field:** 50 opponents, median 18.73 at the fetch; 40 of the 80 games went to turn 250 (v08's Bronze
  placements: 18 and 13 of 60), so half the Silver games are decided on the queens' HP.
- **Placement size:** 80 games in the battle list at completion, against 60 in every Bronze placement.
- **P1 in Silver:** v08 has one Silver placement; its baseline needs a second Silver upload of the same
  configuration before a candidate is judged here, and M has no Silver estimate yet. PV1/PV2 lapse with the
  league change (`docs/local-screen.md`): the local proxy is to be re-checked against Silver games.

## v08 in Silver, upload 4: the Silver baseline (2026-11-04)

The user pasted the same artifact `5c16d925` at 00:14 (agent 6844095, submission 41628436; "re-uploaded once
more"), replacing upload 3, whose placement was complete and saved. The discovery found the row at 00:16
(21 %); `watch.sh` took snapshots every 3 minutes, every minute from 85 %, and `fetch-loop.sh` fetched the
finished games every minute (all 80 by 00:32). The placement was complete at 00:31, 17 minutes after the
paste: 18.66 at 23 %, 19.69 (rank 4) at 55 %, 21.30 (rank 1) at 88 %, 20.74 (rank 3) at the end.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v08/upload-3 | 6843976 | 20.70, rank 2/200 | 80 | 66.2 ± 5.3 | 27 of 40 | 26 of 40 | 18.73 | 20.74 ± 0.89 |
| v08/upload-4 | 6844095 | 20.74, rank 3/200 | 80 | 62.5 ± 5.4 | 29 of 45 | 21 of 35 | 19.05 | 20.90 ± 0.85 |
| v08, two Silver uploads | | mean 20.72, SD 0.03 | 160 | | | | | 20.82 ± 0.62 |

From `python -I tools/arena_compare.py --candidate v08 --selected v08` (Silver: logistic scale 3.58, fitted
on these two uploads; `arena-compare.json` beside upload 4's games). The Bronze tables are unchanged.
- **Same code:** `cgAttribute` finds every game identical to `5c16d925` (SAME 80/80, 16,757 turns, every
  compared turn validated), marker `3f19aa6e` in all 80. Two wins are opponents removed by the referee
  (916295252, 916295352).
- **Equal scores:** 916297591 ended 28:28 on turn 250 and CG ranked player 0 (the opponent) first, the sixth
  such game (`docs/rules/inferred.md`); it counts as a loss.
- **The Silver boss:** 3 games against agent 1876720 (rating 21.87-21.97 during the fetches), 1 won
  (916299584); with upload 3, 5 of 13. v08's mean 20.72 is about 1.2 below it.
- **The baseline:** v08 now has the two Silver placements P1 needs; the next candidate is judged in Silver
  against the mean 20.72 and the pooled rating 20.82 ± 0.62.
- **Noise:** the two placements differ by 0.04, so the Silver M is 0.06 on 1 df. That is one pair, not a
  noise estimate: in Bronze the pooled M was 2.46 on 11 df and v08's own pair differed by 0.28. A rollback
  test against M = 0.06 would roll back almost any candidate that is not better. Leagues are not pooled
  (P1), so until Silver has more pairs, how to set M there is the user's decision.
- **Placement size:** 80 games again (Bronze 60); placement speed 17 minutes.

## Silver noise margin: Bronze's M (2026-11-04)

The user's answer to the open question above ("M from Bronze, 2.46"): until they decide otherwise, P1
verdicts in Silver use Bronze's pooled M, 2.46 (SD 1.23 on 11 df), instead of Silver's own 0.06 (1 df).
The registry records it as `noiseMargins.silver` (from bronze, M 2.46, decided 2026-11-04).
`tools/arena_compare.py` then uses Bronze's M for the Silver verdict and prints Silver's own M beside it as
context. It refuses (exit code 2) if Bronze's M moves more than 0.005 from 2.46, since the decision was taken
for that value. This account gets no more Bronze uploads, so in practice the value is fixed.
- **The next candidate in Silver** is judged against v08's mean 20.72 and pooled rating 20.82 ± 0.62:
  - rollback: the candidate's mean is below by more than 2.46 and its rating below by more than 2 SE of the
    difference;
  - early rollback: its first placement is below 15.79 (20.72 − 2M, 2M = 4.93).
  As in Bronze, a candidate up to about 2.5 points worse passes.
- **An assumption:** that Bronze's spread carries over to Silver. Silver placements have 80 games (Bronze 60)
  and a different field. v08's Silver pair differed by 0.04 and its Bronze pair by 0.28, both well inside
  2.46. Silver's own M is re-estimated with every Silver pair and printed; switching to it is the user's
  decision.
- **The report:** `python -I tools/arena_compare.py --candidate v08 --selected v08` now prints for Silver
  "M = 2 SD = 0.06; verdicts use M = 2.46 from bronze" and "not clearly worse" against −M = −2.46. The Bronze
  report is unchanged.

## v08 in Silver, upload 5: the first run of `tools/arena.py` (2026-11-04)

The user started `python -B -I tools/arena.py follow v08` in the main checkout at 22:50. When the command
said so, they pasted the kept copy `artifacts/5c16d925/Player.java`; `build/submit/Player.java` there held
v03's `d6266d41` at the time. The command then:
- saved upload 4: a snapshot and its 90 games, 10 of them played after its placement;
- waited, then registered agent 6847999 (created 22:59:16, submission 41637774) as v08 upload 5;
- followed the placement while fetching the games every minute;
- recorded the placement and `cgAttribute`.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v08/upload-3 | 6843976 | 20.70, rank 2/200 | 80 | 66.2 ± 5.3 | 27 of 40 | 26 of 40 | 18.73 | 20.46 ± 0.78 |
| v08/upload-4 | 6844095 | 20.74, rank 3/200 | 90 | 58.9 ± 5.2 | 31 of 54 | 22 of 36 | 19.19 | 20.47 ± 0.68 |
| v08/upload-5 | 6847999 | 20.21, rank 6/201 | 80 | 60.0 ± 5.5 | 28 of 43 | 20 of 37 | 18.97 | 20.27 ± 0.73 |
| v08, three Silver uploads | | mean 20.55, SD 0.30 | 250 | | | | | 20.40 ± 0.42 |

The figures are from `arena_compare.py` as `tools/arena.py` ran it, without a verdict (Silver: logistic
scale 3.08, fitted on the three uploads; upload 5's `checks/arena-compare.json`). Upload 4's row now
counts 90 games: the 10 played after its placement (3 won) were saved before the paste, as P1's step 2
asks, and its rating moved from 20.90 ± 0.85 to 20.47 ± 0.68. The Bronze tables are unchanged.
- **Same code:** `cgAttribute` finds every game identical to `5c16d925` (SAME 80/80, 15,296 turns), marker
  `3f19aa6e` in all 80; the referee removed no opponent.
- **Equal scores:** in 916719390 both queens die on turn 146 (0:0). CG's game result ranks player 0, us,
  first, which the manifest counts as a win; the battle list gives both players position 0
  (`docs/rules/inferred.md`, now seven games).
- **The Silver boss:** 3 games, 1 won (916722076); with uploads 3 and 4, 6 of 16. Its rating was
  21.87-21.99 at the fetches; v08's mean 20.55 is about 1.4 below it.
- **The baseline:** three Silver placements, mean 20.55 (20.72 on two), pooled rating 20.40 ± 0.42. The
  next candidate in Silver rolls back early below 15.62 (20.55 − 2 × 2.46).
- **Noise:** Silver's own M is 0.59 on 2 df (SD 0.30; upload 5 is 0.5 below the first two). By the user's
  decision the verdicts keep Bronze's 2.46 (Silver noise margin, above).
- **The tool's first run:**
  - CG created the agent at 22:59:16 but listed it on the leaderboard only at 23:06:49, already at 43 %,
    while its API timed out (`TimeoutError` lines with retries). The command kept polling every 20 s and
    went on. The old agent's battle list was already empty at 23:05, which shows the paste had reached CG.
  - The first fetch after the registration took five minutes (37 games, slow responses), so the next
    snapshot came at 23:11:53 rather than about 23:10.
  - Once two fetches ran six seconds apart: the next fetch time counts from the start of the previous
    one.
  - The percentage stayed at 86 % from 23:17:53 to 23:23:53 with all 80 games finished. The completion
    snapshot at 23:24:50 gave 20.21 (20.02 at 86 %).
  - The prompt "paste the code" also shows after a paste that CG has not listed yet, and it does not name
    the file to paste. These are left for later (`docs/PLAN.md`).

## Eighth candidate: v10 (the second knight barracks at the centre), first upload in Silver (2026-11-07)

v10 is v09 (the late hold, never uploaded) with BARRACKS_FORWARD=50. Its final evaluation against v08 gave S 0.5764
(`docs/data/exp-2026-11-06-waves/`), below P2's 0.60 upload recommendation, and the user chose to upload it as an
exception ("Да, готовь"). The user ran `python -B -I tools/arena.py follow v10 --selected v08` in the main checkout at
00:34 and pasted `21665009`, which is `build/submit/Player.java` there, rebuilt from master bac2788. The command:
- saved v08 upload 5 (80 games, none played after its placement);
- saw the new agent 6857063 at 00:37:47 (created 00:37:28, submission 41655689);
- registered it as v10 upload 1 at 00:37:52, once its first game showed build `8c3b0d9a`;
- followed the placement to its end at 00:56, 19 minutes after the paste, fetching the games;
- ran `cgAttribute` and the P1 comparison.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v10/upload-1 | 6857063 | 19.38, rank 6/201 | 80 | 67.5 ± 5.2 | 26 of 35 | 28 of 45 | 17.21 | 19.30 ± 0.81 |
| v08, three Silver uploads | | mean 20.55, SD 0.30 | 250 | | | | 18.73-19.19 | 20.46 ± 0.44 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it: Silver's logistic scale 3.22, fitted on the four
uploads; upload 1's `checks/arena-compare.json`.
- **Same code:** `cgAttribute` finds every game identical to `21665009` (SAME 80/80, 14,998 turns), marker `8c3b0d9a`
  in all 80.
- **The referee removed one opponent:** in 917798617 (nicolegrimpeur) the opponent's score is −1, a win for us. CG and
  these numbers count it as an ordinary win.
- **Equal scores:** 917804072 against Reynadan ends at turn 250 at 7:7.
  - CG's game result ranks player 0, us, first. The four saved battle lists that hold the finished game give both
    players position 0.
  - It is the eighth such game (`docs/rules/inferred.md`).
- **Weaker opponents:**
  - The opponents' median is 17.21, against 18.73-19.19 in v08's three uploads.
  - There was no game against the Silver boss; v08 met it 3-10 times.
  - Only 6 games came against players rated 20.5 or more (1 won); v08 had 13-24 (3-8 won).
  - So more wins came at a lower score: the placement drew from another part of the field.
  - The scale did not drift: the 28 opponents both versions met moved by a median of −0.08 between the fetches of
    2026-11-04 and 2026-11-07.
- **Against v08's baseline:**
  - −1.17 on the mean (19.38 against 20.55) with 2M = 4.93, so no early rollback (below 15.62).
  - The verdict needs the second upload of the same artifact.
  - The performance rating, 19.30 ± 0.81 against 20.46 ± 0.44 (−1.16 ± 0.92), is context until then.
- **Local prediction:** P2's final evaluation put v10 about +1.0 rating point head-to-head over v08, and +8.4 pp
  against R0. The first placement is −1.2: one placement, inside the arena's noise (M 2.46).
- **Placement speed:** 19 minutes.

## The v10 candidate, second upload and the P1 verdict (2026-11-07): v10 selected

The user pasted the same `21665009` again ("Запустил вторую"), with `python -B -I tools/arena.py follow v10 --selected
v08` running in the main checkout. The command:
- saw the new agent 6857144 at 01:05:22 (created 01:04:47, submission 41655831; 12 % of the placement played) and saved
  what was left of upload 1 (80 games, none after its placement);
- registered it as v10 upload 2 at 01:05:29, once its first game showed build `8c3b0d9a`;
- followed the placement to its end at 01:22:37, 18 minutes after the paste;
- fetched the games, ran `cgAttribute` and gave P1's verdict.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v10/upload-1 | 6857063 | 19.38, rank 6/201 | 80 | 67.5 ± 5.2 | 26 of 35 | 28 of 45 | 17.21 | 19.59 ± 0.92 |
| v10/upload-2 | 6857144 | 21.33, rank 2/201 | 80 | 67.5 ± 5.2 | 25 of 39 | 29 of 41 | 18.97 | 21.45 ± 0.91 |
| v10, both | | mean 20.35, SD 1.38 | 160 | | | | | 20.56 ± 0.65 |
| v08, three Silver uploads | | mean 20.55, SD 0.30 | 250 | | | | 18.73-19.19 | 20.64 ± 0.50 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it (upload 2's `checks/arena-compare.json`). Silver's
logistic scale is 3.68, fitted on the five uploads (3.22 on four), so the performance ratings of upload 1 and v08 differ
from the previous section's.
- **Same code:** `cgAttribute` finds every game identical to `21665009` (SAME 80/80, 13,445 turns), marker `8c3b0d9a`
  in all 80.
- **The referee removed one opponent:** in 917807402 (RAAAT) the opponent's score is −1, a win for us, counted as an
  ordinary win.
- **Equal scores:** 917810075 against godlikegod ends at turn 250 at 7:7.
  - CG's game result ranks player 0, us, first. All 15 saved battle lists that hold the finished game give both
    players position 0.
  - It is the ninth such game (`docs/rules/inferred.md`).
- **The boss:** 4 games against the Silver boss (agent 1876720), all won; its score at the fetches fell from 21.84 to
  21.54. v08 won 6 of its 16. The opponents' median, 18.97, is v08's (18.73-19.19); upload 1's was 17.21.
- **P1 verdict against v08:**
  - M is 2.46, borrowed from Bronze. Silver's own M is now 1.66 on 3 df (pooled SD 0.83, v08's three placements and
    v10's two), printed as context.
  - Mean: 20.35 against 20.55, −0.20 against −M = −2.46.
  - Performance: 20.56 ± 0.65 against 20.64 ± 0.50, −0.08 against −2 SE = −1.63.
  - Neither rollback condition holds, so v10 is not clearly worse and becomes the **selected version** (2026-11-07).
- **The next candidate in Silver** is judged against v10's mean 20.35 and rating 20.56 ± 0.65:
  - rollback: the candidate's mean is below by more than 2.46 and its rating below by more than 2 SE of the difference;
  - early rollback: its first placement is below 15.43 (2M = 4.93).
  The rollback artifact is `21665009` (`docs/BOTS.md`, Artifacts).
- **Local prediction:** P2's final evaluation put v10 about +1.0 rating point head-to-head over v08 (S 0.5764). The
  arena gives −0.20 on the mean and −0.08 on the rating, inside its noise: it neither confirms nor refutes the step.
- **Spread:** the two placements of the same code differ by 1.95. Upload 1 met weaker opponents and no boss.
- **Where v10 differs from v08** (`docs/data/study-2026-11-07-tower-fire/`):
  - against opponents at 21 and up and the boss, v10 won 10 of 15 where the logistic model expects 43 %; v08 won
    12 of 41 (expected 44 %);
  - against opponents below 19, v10 won 72 % (expected 75 %) and v08 77 % (expected 72 %).
  The rating is the same because most games come against the weaker part of the field.
- **Promotion:** `eligibleForPromotion` false after both placements. Upload 2 ranks 2 of 201, below the boss.
- **Placement speed:** 18 minutes.

## Ninth candidate: v11 (no ladder walks into the enemy base), first upload in Silver (2026-11-07)

v11 is v10 with SAFE_LADDER=1, a P2 targeted fix (`docs/data/exp-2026-11-07-safe-ladder/`). Its local effect is far
below the 0.60 upload recommendation (S 0.5030 against v10), and only the arena can show it; the user chose to upload it
as an exception ("Да, готовь"). The user ran `python -B -I tools/arena.py follow v11 --selected v10` in the main checkout
and pasted `cf71d566`, which is `build/submit/Player.java` there, rebuilt from master 1dd24ba. The command:
- saved v10 upload 2;
- saw the new agent 6857351 at 02:42:56 (created 02:42:05, submission 41656151; 12 % of the placement played);
- registered it as v11 upload 1 at 02:43:02, once its first game showed build `cf3b2e46`;
- followed the placement to its end at 02:59:08, 17 minutes after the paste;
- fetched the games, ran `cgAttribute` and the P1 comparison.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v11/upload-1 | 6857351 | 20.80, rank 2/200 | 80 | 65.0 ± 5.3 | 26 of 40 | 26 of 40 | 19.53 | 21.58 ± 1.04 |
| v10, two Silver uploads | | mean 20.35, SD 1.38 | 160 | | | | 17.21-18.97 | 20.94 ± 0.74 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it (upload 1's `checks/arena-compare.json`). Silver's
logistic scale is 4.25, fitted on the six uploads (3.68 on five), so the performance ratings differ from the previous
sections'.
- **Same code:** `cgAttribute` finds every game identical to `cf71d566` (SAME 80/80, 15,831 turns), marker `cf3b2e46`
  in all 80.
- **The referee removed one opponent:** in 917832868 (knacky) the opponent's score is −1, a win for us, counted as an
  ordinary win.
- **Equal scores:** two games end at turn 250 with equal scores.
  - 917833240 against MartinQc, 77:77; we were player 1, so CG's game result makes it a loss.
  - 917834882 against the Silver boss, 23:23; we were player 0, so it is a win.
  - All their saved battle lists (20 and 9) give both players position 0. They are the tenth and eleventh such games
    (`docs/rules/inferred.md`).
- **Opponents:** median 19.53, against v10's 17.21 and 18.97. Ten games came against the Silver boss, 5 won; its score
  was 21.38-21.74 at the fetches.
- **Enemy tower fire** (the study of 2026-11-07: `towers.txt` and `classes-v11.txt` in `docs/data/study-2026-11-07-tower-fire/`):
  - 41 HP in 3 of the 80 games, 0.5 ± 0.5 a game, against v10's 5.1 ± 1.2;
  - no walk to a site on the enemy half;
  - none of the 15 deaths took 10+ HP from towers (v10: 12 of 43);
  - 13 games lost on HP at turn 250 (v10: 9 of 160).
- **Against v10's baseline:** +0.45 on the mean (20.80 against 20.35) with 2M = 4.93, so no early rollback (below
  15.43). The second upload of the same artifact decides. The performance rating, 21.58 ± 1.04 against 20.94 ± 0.74,
  is context until then.
- **Placement speed:** 17 minutes.

## The v11 candidate, second upload and the P1 verdict (2026-11-07): v11 selected, promoted to Gold

The user pasted the kept copy `artifacts/cf71d566/Player.java` again ("закончилась вторая. Прошли серебро"), with
`python -B -I tools/arena.py follow v11 --selected v10` running in the main checkout. The command:
- saved v11 upload 1 (80 games, none after its placement) and named the file to paste at 03:03:07;
- saw the new agent 6857388 at 03:04:14 (created 03:03:39, submission 41656217; 12 % played);
- registered it as v11 upload 2 at 03:04:19, once its first game showed build `cf3b2e46`;
- followed the placement to its end at 03:20:26, 17 minutes after the paste;
- fetched the games, ran `cgAttribute` and gave P1's verdict.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v11/upload-1 | 6857351 | 20.80, rank 2/200 | 80 | 65.0 ± 5.3 | 26 of 40 | 26 of 40 | 19.53 | 21.67 ± 1.07 |
| v11/upload-2 | 6857388 | 21.74, rank 1/200 | 80 | 70.0 ± 5.1 | 33 of 46 | 23 of 34 | 18.64 | 22.38 ± 1.10 |
| v11, both | | mean 21.27, SD 0.66 | 160 | | | | | 22.02 ± 0.77 |
| v10, two Silver uploads | | mean 20.35, SD 1.38 | 160 | | | | 17.21-18.97 | 21.04 ± 0.77 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it (upload 2's `checks/arena-compare.json`). Silver's
logistic scale is 4.41, fitted on the seven uploads.
- **Same code:** `cgAttribute` finds every game identical to `cf71d566` (SAME 80/80, 14,820 turns), marker `cf3b2e46`
  in all 80.
- **The referee removed one opponent:** in 917836644 (Swedgy) the opponent's score is −1, an ordinary win.
- **No equal-score game.** Two games came against the Silver boss, 1 won.
- **P1 verdict against v10:**
  - M is 2.46, borrowed from Bronze. Silver's own M is 1.59 on 4 df (pooled SD 0.79), printed as context.
  - Mean: 21.27 against 20.35, +0.92 against −M = −2.46.
  - Performance: 22.02 ± 0.77 against 21.04 ± 0.77, +0.98 against −2 SE = −2.17.
  - Neither rollback condition holds: **v11 is the selected version** (2026-11-07). Its artifact `cf71d566` is the
    rollback artifact.
- **The arena's view of the fix** (the study's `towers.txt` over v11's 160 games):
  - the queen lost 0.6 ± 0.3 HP a game to enemy towers, against v10's 5.1 ± 1.2;
  - 32 deaths, none with 10+ HP from towers (v10: 43, 12 of them);
  - 20 games lost on HP at turn 250 (v10: 9).
  P2 measured the fix as a targeted fix with S 0.5030 against v10. On the arena v11's mean is +0.92 over v10's and its
  rating +0.98, both inside the arena's noise, so the size of its gain is not established.
- **Promotion:**
  - Upload 2 ranked 1 of 200, above the Silver boss, with `eligibleForPromotion` true; the user reports the promotion to
    Gold.
  - The leaderboard still listed the agent in Silver at 03:21. CG's promotion creates a new agent in Gold (the same
    submission), whose placement is v11's first Gold upload. `python -B -I tools/arena.py follow v11` registers it once
    it plays there.
  - Upload 2's Silver placement was complete, so it keeps its score; no `promotedTo` is needed for the verdict.
- **Gold:**
  - Scores compare within a league, so v11's Gold baseline needs two placements there: the promoted agent's and one more
    upload of `cf71d566`.
  - Gold has no noise margin yet. Whether it borrows Bronze's 2.46, as Silver does, is the user's decision.
  - P2's proxy validation (PV3, PV4) lapses with the league change. Local candidates are developed only, not promoted,
    until it is renewed for Gold.
- **Placement speed:** 17 minutes.

## v11 in Gold: upload 3, the first Gold placement (2026-11-07)

CG promoted v11 upload 2 to Gold. The promotion made agent 6857463 at 03:48:10, with the same submission 41656217 and
no paste, and it played its Gold placement unwatched. The user ran `python -B -I tools/arena.py follow v11` from the
main checkout at 11:27 ("сохранил"), which:
- saved what was left of v11 upload 2 in Silver (its live battle list was empty, the 80 games were already kept);
- registered the Gold agent as v11 upload 3 at 11:27:44 ("CG's promotion of v11 upload 2"), once its first game showed
  build `cf3b2e46`;
- took the first snapshot at 100 %. The leaderboard last updated the score at 04:08:53, after the 90 placement games and
  with no game since, so the recorded score is the placement's. The entry has `watched` false;
- fetched the 90 games and ran `cgAttribute`.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v11/upload-3 (Gold) | 6857463 | 18.13, rank 119/351 | 90 | 62.2 ± 5.1 | 30 of 48 | 26 of 42 | 16.78 | 18.28 ± 0.86 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it (upload 3's `checks/arena-compare.json`). Gold's
logistic scale is 3.83, fitted on this one upload; Gold scores do not compare with Silver's.
- **Same code:** SAME 90/90 (18,587 turns), marker `cf3b2e46` in all 90.
- **Equal scores:** 917850788 against Snef ends at turn 250 at 95:95.
  - We were player 0, and CG's game result ranks us first.
  - All three saved battle lists give both players position 0.
  - It is the twelfth such game (`docs/rules/inferred.md`).
- **No game against the Gold boss.** The opponents ranged from 11.88 to 24.99.
- **How the games are lost** (the study's `towers.txt`, Gold section):
  - 34 of the 90 games are losses: 20 on HP at turn 250 and 14 by the queen's death;
  - enemy towers cost the queen 0.2 HP a game, none in the deaths.
  - In Silver, v11 lost 20 of 160 on HP at the limit; in Gold the HP race is the larger loss mode.
- **P1 in Gold:**
  - This is the first of the two Gold placements v11's baseline needs; the second is a new upload of `cf71d566`.
  - Gold has no noise margin: with one placement, Gold's own M has no degrees of freedom. Borrowing Bronze's 2.46, as
    Silver does, is the user's decision.

## v11 in Gold, upload 4: the Gold baseline (2026-11-07)

The user pasted the kept copy `artifacts/cf71d566/Player.java` again ("загрузил ещё одну серию"), with
`python -B -I tools/arena.py follow v11` running in the main checkout. The command:
- saved v11 upload 3;
- named the file to paste at 11:56:03, and found the old agent's battle list empty at 11:58:16, which shows the paste
  had arrived;
- saw the new agent 6858567 at 11:58:38 (created 11:58:02, submission 41658370) and registered it as v11 upload 4 at
  11:58:43, once its first game showed build `cf3b2e46`;
- followed the placement from 1 % to its end at 12:18:43, and ran `cgAttribute`.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v11/upload-3 (Gold) | 6857463 | 18.13, rank 119/351 | 90 | 62.2 ± 5.1 | 30 of 48 | 26 of 42 | 16.78 | 17.94 ± 0.70 |
| v11/upload-4 (Gold) | 6858567 | 16.03, rank 256/351 | 90 | 61.1 ± 5.1 | 29 of 46 | 26 of 44 | 15.03 | 15.81 ± 0.71 |
| v11 in Gold, both | | mean 17.08, SD 1.49 | 180 | | | | | 16.92 ± 0.50 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it (upload 4's `checks/arena-compare.json`). Gold's
logistic scale is 3.10, fitted on the two Gold uploads.
- **Same code:** SAME 90/90 (17,378 turns), marker `cf3b2e46` in all 90.
- **The referee removed the opponent in five games**, all wins for us: 917982549 and 917984118 (BeeGee), 917982652
  (Zenoscave), 917985878 and 917986565 (jfaixo). CG and these numbers count them as ordinary wins.
- **Equal scores:** two games end at turn 250 with equal scores.
  - 917985146 against rush123321, 35:35. We were player 1, so it counts as a loss.
  - 917987888 against TheMonsterGBA, 68:68. We were player 0, so it counts as a win.
  - Their saved battle lists (19 and 10) give both players position 0. They are the thirteenth and fourteenth such
    games (`docs/rules/inferred.md`).
- **The spread:** the two placements of the same code differ by 2.10 points and their performance ratings by 2.13
  (about 2.1 SE of the difference). It is the same over-dispersion as v01's three Bronze uploads.
- **v11's Gold baseline:** mean 17.08, performance 16.92 ± 0.50 over 180 games.
- **Gold's noise margin:** Gold's own M is 2.97 on 1 df, from this pair; `arena_compare.py` uses it, as Gold has no
  borrowed margin. Silver borrowed Bronze's 2.46 by the user's decision. Which M Gold uses is the user's decision.
- **The study of these 180 games:** `docs/data/study-2026-11-07-gold/`. 44 of the 69 losses are HP races at turn 250,
  decided mostly at the first wave.
- **Placement speed:** 21 minutes from the paste.

## Gold noise margin: Bronze's M (2026-11-07)

The user's decision ("Bronze 2.46"): P1 verdicts in Gold use Bronze's pooled M, 2.46 (SD 1.23 on 11 df), as Silver's do,
until they decide otherwise.
- Gold's own M is 2.97 on 1 df, from v11's two Gold placements (18.13, 16.03). It is printed beside the verdict as
  context.
- The registry records it as `noiseMargins.gold` (from bronze, M 2.46, decided 2026-11-07). `tools/arena_compare.py`
  then prints "verdicts use M = 2.46 from bronze" for Gold. Like Silver's, the rule refuses (exit code 2) if Bronze's M
  moves more than 0.005 from 2.46.
- **The next candidate in Gold** is judged against v11's Gold mean 17.08 and rating 16.92 ± 0.50 (scale 3.10):
  - rollback: the candidate's mean is below by more than 2.46 and its rating below by more than 2 SE of the difference;
  - early rollback: its first placement is below 12.15 (17.08 − 2M, 2M = 4.93).

## Tenth candidate: v12 (meeting the first wave back), two Gold uploads and the P1 verdict (2026-11-07): v12 selected

The user pasted the kept copy `artifacts/3208b011/Player.java` twice ("Сделал 2 загрузки"), each time with
`python -B -I tools/arena.py follow v12 --selected v11` running in the main checkout.
- **Upload 1.**
  - The command saved v11 upload 4 at 19:03:05. Its battle list was already empty: the paste had arrived.
  - It saw the new agent 6860036 at 19:03:33 (created 19:02:47, submission 41661436, 10 % played) and registered it at
    19:03:38, once game 918143669 showed build `58d513ae`.
  - The placement completed at 19:25:46.
- **Upload 2.**
  - The command saved upload 1 at 19:37:46 and named the file to paste at 19:37:51.
  - It saw agent 6860166 at 19:38:35 (created 19:37:59, submission 41661731, 6 %) and registered it at 19:38:41.
  - The placement completed at 20:02:41.

| upload | agent | placement score | games | win % | seat 0 | seat 1 | opp. median | performance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v12/upload-1 (Gold) | 6860036 | 19.64, rank 88/348 | 90 | 54.4 ± 5.2 | 29 of 46 | 20 of 44 | 19.72 | 19.93 ± 0.66 |
| v12/upload-2 (Gold) | 6860166 | 21.07, rank 34/348 | 90 | 66.7 ± 5.0 | 25 of 35 | 35 of 55 | 19.51 | 20.90 ± 0.72 |
| v12 in Gold, both | | mean 20.36, SD 1.01 | 180 | | | | | 20.38 ± 0.48 |
| v11 in Gold, uploads 3 and 4 | | mean 17.08, SD 1.48 | 180 | | | | 15.03-16.78 | 16.89 ± 0.49 |

The figures come from `arena_compare.py` as `tools/arena.py` ran it (upload 2's `checks/arena-compare.json`). Gold's
logistic scale is 3.04, refitted on the four Gold uploads; v11's rating moves from 16.92 to 16.89 with it.
- **Same code:** `cgAttribute` finds every game identical to `3208b011`: SAME 90/90 in each upload, 18,948 and 19,304
  turns. The marker `58d513ae` is in all 180.
- **The referee removed the opponent** in two games of upload 1, 918143680 and 918144045, ordinary wins.
- **Equal scores:** one game in each upload, both at turn 250 with us as player 0, so both rank as wins:
  - 918143672 against Snef, 75:75;
  - 918166983 against Dje54, 51:51.
  - Their saved battle lists (33 and 12) give both players position 0. They are the fifteenth and sixteenth such games
    (`docs/rules/inferred.md`).
- **P1 verdict against v11.** M is 2.46, borrowed from Bronze. Gold's own M is now 2.54 on 2 df (pooled SD 1.27),
  printed as context.
  - Mean: 20.36 against 17.08, +3.28 against −M = −2.46.
  - Performance: 20.38 ± 0.48 against 16.89 ± 0.49, +3.48 ± 0.69 against −2 SE = −1.38.
  - Neither rollback condition holds: **v12 is the selected version** (2026-11-07). Its artifact `3208b011` is the
    rollback artifact for the next candidate.
  - The difference is larger than M and about 5 SE of the rating difference. On the arena v12 is better than v11, not
    just "not clearly worse".
- **The arena's view of the rule.** WaveTrace over v12's 180 games against v11's, the games with a first contact
  (`gold_reference.py`, `wave_mechanisms.py`; traces in `replays/study/gold-v12-2026-11-07/waves/`, gitignored,
  `summary.csv` sha256 3842893b…):

  | | v11 (171 games) | v12 (176 games) |
  | --- | --- | --- |
  | distance from the back edge at the contact (median) | 411 | 287 |
  | contacts beyond 406 | 88 | 37 |
  | first-wave loss to knights (mean) | 11.4 HP | 6.9 HP |
  | merged waves | 69 | 45 |

  In the HP races the distance no longer separates lost from won (322 against 324; v11 444 against 380). Merged waves
  mark 12 of 42 lost races (v11 19 of 44). The first wave still costs more in lost races, 10.2 HP against 3.4, in the
  back half too.
- **Local against arena:** P2's final evaluation gave S 0.5716 against v11, below the 0.60 recommendation, and the
  arena moved +3.3 points. The local panel undervalued this change, like v08's.
- **Placement speed:** 22 and 24 minutes from the agent's creation.
