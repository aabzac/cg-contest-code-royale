# Dev log

## 2026-10-28 — phase 0
Statements, referee `csj/code-royale@349924a`, leaderboard and 88 Legend replays. Live max
turns 250 vs repo 200 (confirmed by replays).

## 2026-10-28 — phase 1: referee port and replay validation
Hypothesis: the public referee equals the live game except the turn limit.
Result: rejected. `cgValidate` (98 replays): map/economy/building/queen moves match up to the
first creep spawn (1275 turns, 0 mismatches before it); knights spawn with 25 HP (8600/8600,
repo 30) → adopted. Spawn positions and tower-vs-creep damage still differ; legacy variants
of two 2018 community fixes (entity order, spawn offset) and several spawn variants rejected
(same first mismatch in 98/98). Resync mode (copy replay units/HUD and continue): 4/98 games
fully consistent; corrections: queenHp 232, units hp 447, pos 161, poshp 278, count 78,
spawn 410. Tower damage samples (722): live = sim - 1 for range-shot in [200,289] and
[400,487]. Decision: keep repo formula until the live Bronze statement is available; do not
trust combat benches. Commands: `./gradlew cgValidate -Pquiet=true [-Presync=true]`.

Greedy `current` vs v00: 100/100 wins (50 maps × 2 seats, seeds 1000-1049); procCheck of the
merged artifact (54,375 chars): 0 timeouts, first turn ≤165 ms, later ≤10.5 ms.

## 2026-10-28 — tower damage, opponents, visualizer
Hypothesis: the live tower damage counts distance from the site centre (repo: from the edge).
Evidence: 722 isolated samples were 1 lower exactly where `int((range - dist + r)/200)` and
`int((range - dist)/200)` differ. Resync over 98 replays: creep HP corrections 447 -> 1, fully
consistent games 4 -> 33, plain-turn unit match 89.69%; queen HP corrections 382 -> 319 with the
same rule for queen shots. Decision: adopted as default (`Rules.TOWER_FROM_CENTER`,
`TOWER_QUEEN_FROM_CENTER`). Rejected: global TOUCHING_DELTA 8/10/12/15 (site checks diverge
earlier, matched turns 9472 -> 5375..3736).
Built `ProcessBot` (external programs in run/bench/procCheck), `boss`/`bossN` specs, frozen
`v01`, local replay export and a canvas visualizer served by `./gradlew serve`.
Workflow `code-royale-live-rules` (run wf_841110c2-70b) started for spawn placement,
movement/collisions, turn order/HP and a second corpus `replays/cg2`.

## 2026-10-28 — live rules from replays: simulator validated
Hypothesis: the remaining mismatches come from a few structural differences in the live
referee, not from constants. The workflow (4 investigators, 3 adversarial verifiers, run
wf_841110c2-70b) tested this on `replays/cg` and then on a new corpus `replays/cg2`.

Result: confirmed. Adopted, each with a test:
- collision entity order: each player's creeps, then that player's queen, sites last;
- spawn corner offsets `(1,-1), (-1,1), (1,1), (-1,-1)`;
- structures act by type: towers, then barracks, then mines;
- TRAIN capped at 30 creeps;
- build-conflict parity counted from turn 0.

A retained Kotlin behaviour that contradicts the statement now has tests: dead creeps are
removed only at the end of the turn. The tower-damage rule from the previous entry got its
first unit test. The four patches were merged into one implementation. Removed: the inference switches, the trace
log and the `-P` flags `legacyOrder`, `legacySpawn`, `turnBase`, `knightHp`, `spawnOffset`,
`spawnFixEach`, `spawnNoTowards`, `towerFromCenter`, `towerQueenFromCenter`, `traceGame`,
`traceTurn`. `cgValidate` now also compares summary warnings (multiple per player per turn) and
counts the turns of games that differ only at the end.

Strict validation, no corrections:
- `replays/cg` (98): 97 games consistent, 15,374/15,374 turns; units plain 13424/13424, spawn
  1950/1950; warnings 113/113. Before: 0 games, first mismatch at the first spawn in 98/98;
  resync plain 89.69%, spawn 0.09%.
- `replays/cg2` (138; Bronze 58, Silver 38, Gold 42): 137 games, 20,438/20,438 turns; plain
  18330/18330, spawn 2104/2104; warnings 3407/3407. Before: 3 games, the 1-turn ones.
- Remaining: 911493205 (11:11) and 908057307 (0:0) are ranked [0,1] by CG; the simulator scores
  a draw. The tie-break rule is open.

Mutation check of the merged code: each rule was reverted alone. Every revert fails its unit
test and at least one corpus (`docs/data/inference-2026-10-28/mutation-check.txt`).

Rejected; details in `docs/data/inference-2026-10-28/workflow-result.json`:
- spawn offsets `c*iter` and `(iter,iter)`, no `towards`, a collision fix per creep, one
  collision pass after all barracks;
- spawning inside the structure loop or after dead-creep removal;
- an extra global `fixCollisions` after the creep phase or at the end of the turn (plain 67-74%);
- 2, 3 or 999 collision passes per substep; mass, speed, radius and gap variants;
- dead creeps: towers skipping 0-HP creeps, removal after each shot or after the towers;
- aging: knights aging before attacking, newborn creeps aging;
- queens tearing down structures right after builds;
- the cap counting creeps in training or checked before gold.

Engine change, so benches before this entry are not comparable. Re-run on this code:
- current vs v00: 100/100 (50 maps, seeds 1000-1049).
- current vs v01: 0.500, 20/0/20 (20 maps; before 17/6/17). In this mirror player 0 won 12 maps
  and player 1 won 8, too few to claim a seat advantage.
- current vs boss: 0.950 ± 0.047, 76/0/4 (40 maps, seeds 1000-1039, threads 3, turnMs 200, 0
  errors, boss max 106 ms). All four losses were as player 1: seeds 1008, 1023, 1028, 1038.
  Before: 80/80.
- `procCheck` (`Player.java` 54,064 chars, sha256 `b267ba0d…`): 10/10 vs boss, 0 timeouts,
  first turn ≤ 177 ms, later turns ≤ 15.4 ms.

Commands: `./gradlew cgValidate -Preplays=replays/cg2 [-Presync=true]`; `./gradlew test` (22 tests).

## 2026-10-29 — phase 2: pre-upload audit of the v01 artifact
Hypothesis: the merged artifact of `current` (= v01, `Player.java` sha256 `b267ba0d…`) is safe
to upload: protocol, CG runtime, legality and identity. Workflow `cr-presubmit-audit` (4
auditors, 2 adversarial verifiers, run wf_77cc6540-949); evidence
`docs/data/presubmit-audit-2026-10-29.json`.

Result: no blocker. The bot read the live input of 236 replays exactly; 71,624 answers gave 0
kills and 0 referee warnings. CG runs Java 21; the artifact compiles from Java 11, uses only
java.base and about 1.5 MB of heap per game. One confirmed medium finding, RT-1: the first use of
three lambdas, of `Physics` and `Const` and of two string-concatenation shapes fell on turn 1 and
on the first MOVE. Cost: 6-8 ms p50 on all cores; 12-33 ms p50 (max 39.5 ms) on one slow core;
with JBR 25 the first MOVE took 28.9 ms p50 and over 50 ms in 2 of 45 games. 85% of maps touch a
site on turn 0.

Corrections to the previous entry:
- Identity: `b267ba0d` came from the CRLF working tree. `git archive` of 9e78ac4 gives
  `a5a721f3…` with core.autocrlf=true and `c61dc8ac…` with false.
- Seats: greedy self-play is not balanced. Player 0 won 187, player 1 110, 3 draws on seeds
  1-300 (z ≈ 4.5); player 0 won 686 of 988 decided games on seeds 10000-10499.

Fixes, giving artifact `5bace689…` (56,780 chars):
- turn-0 warm-up: a throw-away `MainBot` plays four altered copies of the first state (touched
  site, nearest site, flight from a knight, own-tower fallback with TRAIN) within 300 ms of the
  turn start;
- WAIT/TRAIN if `play` throws; `InputReader` sets `turn` and the turn start; the SLOW message is
  built with StringBuilder;
- `submit` writes ASCII with LF whatever the checkout, validates params and checks before writing;
- `procCheck` times answers by arrival, refuses late lines and replays every game with in-process
  `current`, whose command lines must match; the v01 snapshot test checks every field.
`procCheck`: 140/140 games identical to `current`, 0 timeouts, 0 kills; first turn ≤ 464 ms,
later turns ≤ 3.1 ms vs v01 (25 games) and ≤ 8.5 ms vs boss (25 games).

Recheck of `5bace689` (workflow `cr-presubmit-recheck`, run wf_3d6c6c04-483; evidence and the
decision per finding in `docs/data/presubmit-recheck-2026-10-29.json`): no blocker, play unchanged.
- Latency, old vs new artifact, paired: on the slowest core turn 1 went from 33.9 to 0.75 ms p50
  (max 48.9 to 1.13, 120 games); with JBR 25 the first MOVE from 46.3 to 0.47 ms p50. The new
  artifact had 0 answers over 25 ms after turn 0; later turns did not change. Turn 0 grew by 5-46
  ms p50: the warm-up took 25, 67 and 140 ms p50 on all cores, an E-core and the LP E-core, at most
  177 ms. All 890 paired games gave the same lines, length and winner.
- Scale: `procCheck` 400/400 identical (v01 100 games, boss 50, current 50), 0 timeouts; 760
  games through the audit's legality harness with 0 warnings and 0 kills; 20,000 fuzz answers
  byte-identical to the old artifact. The old artifact's two timeouts from the audit (3078/0,
  4020/0) are gone.
- The low findings were in the tooling. `procCheck` passed with 0 of 0 games compared and could
  not see a bot exception behind the fallback. A non-ASCII digit in params deleted the classes and
  kept the old `Player.java`. Six changed behaviours had no test, and leftover output lines were
  accepted. Fixed without changing the artifact (the rebuild is byte-identical):
  - `procCheck` fails on a game it cannot compare, on extra output lines and on stderr other than
    SLOW;
  - `submit` checks params against the literal, turns only CRLF into LF, sorts files by name and
    leaves no artifact after a failure;
  - 7 new tests, 35 in all.
- Left for before the next upload, because each changes the artifact and none affects v01: the
  fallback answering before its stack trace, the warm-up bot's own Params and init, and unit tests
  for the fallback and the turn start.

Recheck of that tooling (workflow `cr-tooling-recheck`, run wf_e9950365-a63; evidence and the
decision per finding in `docs/data/tooling-recheck-2026-10-29.json`): no blocker, artifact unchanged
(rebuilt byte-identical in fresh copies).
- Strict `procCheck` on `5bace689`: v01 200/200 (seeds 5000-5099), boss 200/200 (6000-6099), current
  100/100, v00 50/50, each with 0 extra and 0 stderr lines. Over 11 runs 1,560 of 1,562 artifact
  games were identical. The other 2 were in one extra run on fresh seeds (boss, 20000-20149): the boss
  timed out in one game, and in the next the artifact missed the 1000 ms first turn (1001.2 ms, with
  no SLOW report of its own). Those seeds passed 3 reruns and the whole set 2 more runs (SCALE-1, read
  as a machine stall). `bench current boss` gave identical per-game rows with the old and the new
  ProcessBot (56/0/4).
- Findings, all in the tooling:
  - medium R2-M1: `procCheck` passed when the opponent was killed for invalid output on turn 0;
  - run and bench dropped extra output lines, while CG reads a leftover line as the next answer;
  - a late answer was reported as extra output;
  - close() left a `cmd /c` wrapper's child running;
  - `JDK_JAVA_OPTIONS` failed `procCheck` through the launcher's stderr note;
  - `submit` did not handle a locked class file, a non-ASCII file name or a quote in `-Pparams`
    (Windows splits the argument);
  - the verdict logic had no tests.
- Fixed, artifact unchanged:
  - a game where the opponent was killed is not compared;
  - leftover lines stay queued and are counted (`extraLines` in run, bench and `procCheck`), and late
    lines are counted apart from them;
  - close() kills the program's descendants;
  - programs start without the JVM option variables;
  - `submit` checks the merged text and the PARAMS marker, renames the old artifact aside in one step
    and takes one argument;
  - `procCheck` prints a rerun command on timeouts, and its verdict is unit-tested.
  Tests: 35 → 47.
- After the fixes: `submit` gives `5bace689`; `procCheck` v01 100@5000 200/200 (99 wins, shortest game
  30 turns), boss 100@6000 200/200 (178 wins, shortest 53), 0 timeouts. Negative controls fail as
  intended: garbage opponent (notCompared=4), extra line (desync, killed on turn 6 in run 3/3),
  80 ms answer (lateLines=4 with the SLOW report), locked class file (old artifact whole), split
  params. `JDK_JAVA_OPTIONS` now passes, and a wrapper closes in about 530 ms with no child left.

Review of those fixes (workflow `cr-tooling-fixes-review`, run wf_4b31fcad-e21; `fixes_review` in the
same evidence file): no blocker.
- Well-behaved process bots are unchanged: `bench current boss` gave the same 60 per-game rows as the
  pre-fix tree, and `run submit v01 -Pseed=5` the same 472 move lines. Fresh seeds passed: boss
  50@9000 and v01 50@8000, 100/100 each.
- Findings:
  - medium F1: the printed rerun command left out `-Pparams`, so it rebuilt a different artifact;
  - after a long stall the note said "no SLOW report", although close() had killed the artifact
    before it could report;
  - the JVM option variables were removed in upper case only;
  - 20 of 45 mutations survived;
  - a test ran `FileMerger.main` in the project;
  - a failed build removed the rollback artifact.
- Fixed, artifact unchanged:
  - the rerun line carries the artifact's params, and `procCheck` prints the artifact's sha256 first;
  - after a missed deadline close() waits 2 s, so a late artifact still answers and reports, and a
    kill at close gives "no conclusion";
  - the variables are removed in any case;
  - `submit` builds in `build/submit.new` and swaps it in only after the compile, so a failure keeps
    the old artifact (this replaces the previous round's "no artifact after a failure"), and the
    build is tested in a temporary directory;
  - the rollback source is the commit plus `./gradlew submit`, not `build/submit`.
  Tests: 47 → 59. The review's mutation list, moved to the new code with 9 new mutants, is now
  caught 51 of 54 (the full run caught 49, then two tests were strengthened). The 3 survivors cannot
  be caught:
  - `passed()` ignoring timeouts is equivalent, since every timeout is also a kill;
  - a plain move instead of ATOMIC_MOVE is the same rename on one volume;
  - the restore after a failed second rename would need fault injection.
- After the fixes: `submit` gives `5bace689`; `procCheck` v01 100@5000 and boss 100@6000 200/200 each
  (99 and 178 wins), 0 timeouts. Controls: the rerun line of a `-Pparams=MAX_TOWERS=3` check rebuilds
  the same artifact; answers late by 80 ms and by 2 s show the SLOW report, and one late by 5 s gives
  "no conclusion".

Decision: policy P0, upload `5bace689` as the first agent from this project.

## 2026-10-29 — v01 on the arena as agent 6823071

- Upload: the user pasted `build/submit/Player.java` sha256 `5bace689…` (commit 80e404c, default
  params). CG created agent 6823071 (submission 41591829) in Bronze at 08:17 (leaderboard
  `creationTime`), replacing the old entry 3315948 under policy P0.
- Placement, followed with `tools/arena_snapshot.py` every 3 minutes and every minute from 85 %
  (36 snapshots, `docs/data/arena-6823071-placement-2026-10-29.json`):
  - 20 % at 08:21: rank 1175/2296, score 15.12;
  - complete at 09:36 (`percentage` 100, `inProgress` false): rank 736/2296, score 18.24, 60 games,
    36 wins (seat 0: 21 of 33, seat 1: 15 of 27); no promotion.
  - In the same league the old entry stood at 559/2295 with 19.64 on 2026-10-28. That is one placement
    against an older rating in a changing pool: an observation, not a verdict, and no policy decides
    on it (P0 replaced the entry by the user's decision).
- Which code played. Two new tools:
  - `tools/arena_games.py` downloads an agent's finished games unchanged and writes a manifest
    (`docs/data/arena-6823071-games-2026-10-29.json`);
  - `cgAttribute` (`cr.tools.CgAttribute` with `cr.referee.Follow`) replays each game through
    CgValidate, then on the validated turns compares the bot's two lines with the agent's recorded
    stdout, while the game advances with the recorded commands of both players.

  Results on all 60 placement games:
  - cgValidate strict 60/60, 7,230 turns;
  - the artifact `5bace689`, compiled from its hashed bytes: SAME in 60 of 60, 7,230 turns;
  - current and v01: SAME in 60 of 60;
  - controls: v00 is DIFF in 60 of 60; every opponent seat is DIFF in 60 of 60 (48 from turn 0);
    the old agent's 10 games are DIFF in 10 of 10.

  So agent 6823071 behaved as `5bace689` on every decision of its placement. This is behaviour on the
  positions reached: the identity of the pasted file rests on the user's upload.
- Review of the tools (wf_0f5ffee8-bfe: 2 reviewers, 6 skeptics). The claim held. The tools had gaps,
  none affecting it:
  - high: a game counted as SAME when the comparison stopped early; a bot going silent at turn 30
    passed with exit code 0;
  - medium: `submit?K=v` ran the unchanged artifact;
  - medium: a stale unfinished row in a saved battle list could hide a downloaded game;
  - medium: test gaps (14 of 34 one-edit mutants survived);
  - low: the printed hash was not tied to the classes that ran; partly validated games passed;
    `arena_games.py` aborted on a malformed game and did not pause after errors; no reason was given
    when the record outlasts the simulated game.
- Fixed:
  - per-game verdicts SAME, DIFF, PARTIAL and ERROR, with exit code 0 only when every game is SAME;
    a missing answer is a difference;
  - BotFactory refuses parameters for submit, boss and v00;
  - a Java bot (`submit`, `file:<path>`) is compiled from the bytes whose sha256 is printed;
  - battle lists merge by "finished wins", a stored file counts as finished, and the live list is
    saved;
  - `-Preplays` and `-Pagent` are required, and a pseudo of several agents is an error.

  Tests 59 → 74, including a real-game test on two placement games, skipped without the replays.
  21 one-edit mutants of the new code: 20 caught, and the 21st after an added test.

Decision: none under P0; v01 stays live. The next step is the user's: keep it and start phase 3, or
roll back to the old code they kept.

Decision (the user, after the placement): keep v01; no rollback.

## 2026-10-29 — v01 uploaded again; policy P1; build marker

**Upload 2.** The user kept v01 and re-submitted the same `build/submit/Player.java` (`5bace689…`) at
14:03: agent 6823455, submission 41593274, Bronze. The placement was not watched. The first snapshot, at
20:51, found it complete: score 16.08 (leaderboard update 15:51; 60 games, none after the placement),
rank 1067/2294 at 20:51, 31 wins (seat 0: 20 of 30, seat 1: 11 of 30).
- 6823071's battle list is empty now, as Back Track King saw after a replacement. Its 60 placement games
  were saved at 09:36; games it played after its placement, if any, are lost.
- `tools/arena_games.py`: 60 games, `docs/data/arena-6823455-games-2026-10-29.json`. cgValidate: 60/60
  games, 6,413 turns, warnings exact 1,335/1,335. `cgAttribute -Pbot=submit` (then `5bace689`): SAME in
  60/60 (6,413 turns); v00: DIFF in 60/60.

**Noise.** The same bytes placed at 18.24 and 16.08 (ranks 736 and about 1067): SD 1.53, M = 2 SD ≈ 3.05
CG points (one pair). `tools/arena_compare.py` gives performance ratings of 18.16 ± 0.38 and 16.78 ± 0.37
(logistic scale 1.32, fitted on 120 games): 1.38 ± 0.53 apart for identical code, 2.6 standard errors at
that scale (about 2.3 with the weakly identified scale profiled out, see the review below). The rating
follows the placement score, and its standard error understates the spread between placements. The
opponents' median scores were 17.82 and 16.81, the win rates 60.0 and 51.7 %. Only 2 opponents were shared.

**Policy P1.** The user pointed to Back Track King's two-series rule (its `docs/arena-uploads.md`, adopted
there 2026-10-27). Adopted for the arena step in `docs/arena-uploads.md`: two uploads per version; roll
back when the mean placement score is below the selected version's by more than M and the pooled rating
below by more than 2 SE; roll back early when upload 1 is below by more than 2 M; otherwise there is no
clear sign that the candidate is worse (not a proof that it is not). Registry
`docs/data/arena-uploads.json`, tool `tools/arena_compare.py`. The local part (proxy, screen) remains for
phase 3. With M ≈ 3 the arena step only catches large regressions.

**Build marker.** The user asked for the bot's version in a first-turn message. Code Royale has no
message: the referee kills on a token after WAIT or MOVE (after BUILD unless the build warns first), and a
TRAIN token must be an existing site id. A TRAIN of a site without barracks only warns: the local replays
hold 352 such warnings in 21 games, all from single-site TRAINs, none fatal (corrected from "80 in 12",
which left out the 6823455 games). In 906359074 and 906522694 a turn-0 TRAIN of the site being built on
got that warning, so no barracks exist when the turn-0 TRAIN is processed. The first-offending-site order
is seen live only in the 69 per-site warnings on multi-site TRAINs (all `Barracks N is training`); it
matches the repo referee and our `Rules`. So the artifact prints its build id on turn 0 as
`TRAIN d1 … d8`, each hex digit a site id 0-15. Bronze maps have 24 sites; under 16 there is no marker.
An 8-site TRAIN of barracks was seen live (912219998, 17 turns, warnings only). Eight sites without
barracks, or a repeated site, have not been: the first marked upload is their check.
- `FileMerger` writes the build id into `CGRunner.BUILD`: the first 8 hex digits of the sha256 of the
  artifact text with an empty literal (`cr.submit.Build`). `submit` prints it; `procCheck` and
  `cgAttribute` check it against the content. After the reviews, a config id too: the merged code from
  `class Player {` to the runner section plus PARAMS as written, shared by builds that differ only in the
  runner (`5bace689` and `1e5d1a79`: `4194117d`).
- `CGRunner` replaces an exactly empty turn-0 TRAIN line with the marker, also after the fallback.
- `procCheck` requires exactly the marker where in-process `current` printed an empty turn-0 TRAIN.
  `Follow` and `cgAttribute` excuse a recorded marker for a bot with an empty TRAIN there and require it
  exactly from a bot that prints one; they print the markers per game and in the summary.
  `arena_games.py` records each game's marker.
- Tests 74 → 84, then 89 after the review: marker encoding and decoding, build and config id checks, a
  real process game with the marker on turn 0 only, the referee warning in `Rules`, the procCheck and
  Follow rules, a real placement game with and without the marker. Python: `tools/test_arena_tools.py`,
  12 offline tests.
- New artifact: sha256 `1e5d1a79…`, 57,884 chars, build `e5417a83` (turn 0: `TRAIN 14 5 4 1 7 10 8 3`),
  default params. procCheck vs v01, 100 maps @5000: 200/200 identical to current, 99 wins, 0 timeouts,
  max first turn 124 ms, later 2.8 ms. vs boss, 100 maps @6000: 200/200, 178 wins, 0 timeouts, max first
  turn 219 ms, later 9.6 ms.

**Review** (workflow wf_ea643ec7-3d7). Four reviewers covered marker safety, the build id, comparison
semantics and the statistics and policy. A refuting verifier then checked every finding. Of 26 findings,
22 were confirmed, all low or medium; none could cost games on CG. The 4 refuted ones: a CRLF copy
failing the byte-exact build id (by design), the recorded attribution string (it pins the artifact by
sha256), and two wording points. Fixes:
- `arena_compare.py` checks the registry, with exit code 2 on any problem:
  - one config id per label (new `Build.config`);
  - each manifest's agentId and its games' markers against the entry;
  - unique uploads and agents; leagues in lower case.
- A placement ended by a promotion (`promotedTo`) has no placement score. A promoted candidate is kept and
  the baseline is rebuilt in the new league. A missing score or rating no longer crashes the tool.
- `FileMerger` runs `Build.of` and `Build.config` before writing, so a second BUILD literal no longer
  replaces the old artifact.
- `Follow` and `cgAttribute` read the recorded marker from the record itself: it is reported also when
  turn 0 is not compared or the game fails later.
- `Build.of` returns "" for an artifact without a literal (built before the marker), and `describe`
  prints "invalid (…)" for a bad one.
- Tests now pin both conditions of the marker excuse (both one-term mutants caught), a game whose turn 0
  is not validated still reports its marker, and the config id is tested. Javadocs corrected.
- Docs corrected:
  - the live evidence (352 in 21, single-site; the 8-site form not seen live);
  - the rating's role (pooled: second rollback condition; per upload: context);
  - "no clear sign that it is worse" instead of "clearly not worse";
  - the 2.6 SE caveat (scale 95 % interval about 0.76-2.81, profiled z about 2.3);
  - the old entry's dates (created 2020-11-23, last score update 2026-10-17, UTC);
  - P1 as "the same configuration", and the promotion rule.

**Second check** (workflow wf_0774530d-666). Two checkers covered the code and the docs, and another agent
tried to refute each finding. Of 13 findings, 10 were confirmed (8 distinct, low or medium) and 3
refuted. Fixes:
- The config id no longer hashes the import header, which the runner's imports join, so a runner-only
  import keeps it. It hashes PARAMS as written (documented). For both v01 artifacts it is now `4194117d`;
  the first definition gave `9471ad53`.
- `arena_compare.py`:
  - a game without a marker (an agent that missed turn 0) is listed, not refused; with a build, at least
    one game must carry it;
  - a candidate with an upload still placing gets "pending";
  - the league must be a league name (null and typos refused), and agent and upload must be numbers;
  - a manifest that does not load is a registry problem (exit 2), not a traceback.
- `FollowTest` forces the bot's empty turn-5 TRAIN, so the turn guard stays pinned whatever `current`
  does.
- Docs: the live evidence (the per-site warnings on multi-site lines are all "is training", and those
  lines also drew 381 creep warnings; an 8-site barracks TRAIN was seen live), and the old entry's dates
  in UTC, in BOTS too.

After the fixes the artifact is unchanged: `1e5d1a79`, build `e5417a83`, config `4194117d`. A short
procCheck vs v01 (5 maps) gave 10/10. `cgAttribute` of upload 2 against the kept copy
`replays/artifacts/5bace689/Player.java` (gitignored) gave SAME in 60/60, build none, config `4194117d`,
markers none in all 60. Tests: Java 89, Python 12.

Decision: v01 stays selected, with its Bronze baseline of two uploads (mean 17.16). The marked artifact
plays as v01 and is ready as its upload 3; uploading it is the user's call.
