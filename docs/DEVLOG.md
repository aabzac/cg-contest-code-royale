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
  Follow rules, a real placement game with and without the marker. Python: `tools/tests/test_arena_tools.py`,
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

## 2026-10-30 — v01 upload 3: the build marker live; noise over three placements

**Upload 3.** The user submitted the marked artifact `1e5d1a79…` (build `e5417a83`, config `4194117d`,
commit 2641aa9) on 2026-10-29 at 22:50: agent 6824680, submission 41596829, Bronze.
- Before the Submit, 6823455's leaderboard row and its 60 games were saved at 22:48
  (`docs/data/arena-6823455-presubmit-2026-10-29.json`, `docs/data/arena-6823455-games-2026-10-29.json`).
  Its battle list was empty at 00:52, as after the first replacement.
- The placement was followed: a snapshot every 3 minutes, every minute from 85 %. One interval was 7.7
  minutes and no snapshot failed. It completed at 00:47:44: score **18.98**, rank 654/2294, 60 games,
  36 wins (seat 0: 22 of 33, seat 1: 14 of 27). During the placement the score ranged from 13.36 (18 %)
  to 20.15 (81 %).
- Placement record `docs/data/arena-6824680-placement-2026-10-29.json`: every snapshot, the first
  completed one, and the attribution with its controls. Games manifest
  `docs/data/arena-6824680-games-2026-10-29.json`. Snapshots and raw responses are in
  `replays/arena/v01/bronze/upload-03/snapshots` (gitignored). Registry entry: v01 upload 3.

**The marker live.** This was the first live check of eight sites without barracks:
- All 60 games carry `TRAIN 14 5 4 1 7 10 8 3` on turn 0.
- Each drew exactly one warning, `Cannot spawn from 14: not a barracks`, and none for the seven later
  sites: the referee stops at the first site without barracks.
- No game ended on turn 0; the shortest lasted 39 turns. In 2 games the queen's turn-0 BUILD targeted
  site 14 itself, and the TRAIN still only warned. Three games had a second turn-0 warning, from the
  opponent's own TRAIN.
- Apart from the marker, v01 drew no line in the referee's summaries in any of the 180 games of the three
  uploads.
- cgValidate: 60/60 games, 6,222 turns, units 100 %, warnings exact 2,121/2,121.
- `cgAttribute` against the kept copy `replays/artifacts/1e5d1a79/Player.java`: SAME in 60/60, 6,222 turns,
  markers `{e5417a83=60}`.
- Controls on the same games:
  - the unmarked v01 artifact `5bace689` and in-process `current`: SAME in 60/60 (a bot without a marker
    is excused the recorded one);
  - `MAX_TOWERS=3`: DIFF in 18 of 60, `INCOME_TARGET=11`: DIFF in 21 of 60. A changed parameter shows in
    part of the games, not in every game.

A repeated site is still not seen live, since `e5417a83` has distinct digits. In the repo referee the
duplicate check only runs after every site has passed the barracks check, which no site passes on turn 0.

**Noise over three placements.** Sources: `tools/arena_compare.py --candidate v01 --selected v01` (table,
mean, SD, M, pooled rating) and `python -B -I docs/data/noise-2026-10-30/noise.py` for the rest (output
`noise.txt` beside it). Details are in `docs/arena-uploads.md`.
- Placement scores of one configuration: 18.24, 16.08, 18.98. Mean 17.77, SD 1.51 (2 df), **M = 3.01**
  (3.05 from the pair). M is still provisional: its 95 % interval is 1.6-19. The opponents' medians were
  17.82, 16.81 and 18.52, the win rates 60.0, 51.7 and 60.0 %.
- Upload 2's 31 wins include 3 opponent timeouts on turn 0 (913110374, 913114190, 913119798; game
  score -1), counted by CG and by every figure here. v01 itself was never removed.
- Performance ratings: 18.16 ± 0.39, 16.78 ± 0.37 and 18.93 ± 0.41; pooled 17.92 ± 0.23 (180 games).
  The logistic scale is 1.33, with a profile interval of 0.88-2.24.
- The ratings of identical code are overdispersed: their SD is 1.09, 2.8 times their mean standard error.
  - A likelihood-ratio test of one rating against one per upload, with the scale fitted in each model,
    gives 11.1 on 2 df (p ≈ 0.004); 12.2 without the 3 forfeits.
  - Cochran's Q gives 15.9 on 2 df at the fitted scale and 8.7 at scale 2.0.
  - Upload 3 against the pooled rating of uploads 1-2: z ≈ 3.0.
  - The cause has to be shared by the games of one upload (the field or the conditions of its time
    window), or the spread is chance, which the tests make unlikely. Matchups with individual opponents
    cannot produce it, since each upload met 56-59 different opponents.
  - So the second rollback condition of P1 (rating below by more than 2 SE) is easy to meet, and a
    rollback rests on M in practice. P1 is unchanged: scaling the rating's SE by the observed
    overdispersion is a policy decision for the user.
- Seats over the 180 games: seat 0 won 63 of 96 (65.6 %), seat 1 40 of 84 (47.6 %), against opponents'
  medians of 17.46 and 17.42.
  - Adjusted for the opponent's score and the upload, seat 1 costs about 1.1 rating points (logit
    -0.795 ± 0.344, z ≈ -2.3).
  - Greedy self-play leans the same way (187 to 110 of 300). This is added to the PLAN item on seat
    asymmetry.

**Review** (workflow wf_44073cb6-368). Three checkers covered the numbers and references, the
statistics, and the marker evidence with cross-document consistency. A refuting verifier then checked
each list. Of 13 findings, 9 were confirmed (5 distinct, all low) and 4 refuted:
- twice, that 6823455's empty battle list has no evidence: it was checked live at 00:52, only not saved;
- that M must stay labelled provisional: its main statements give its df (adopted anyway, with its
  interval);
- that the docs miss the TRAIN kill rule: it is documented.

Fixes:
- The seat medians were upper-middle values (17.49 and 17.43); they are now medians (17.46 and 17.42).
- The pre-upload marker evidence is dated (356 local games, 2026-10-29) in `docs/arena-uploads.md` and
  `.ai/project.md`, and the upload is called the check of eight sites without barracks only.
- Upload 2's 3 forfeits are reported. `arena_compare.py` now lists the games where the referee removed a
  player, by side (test added: Python 12 → 13).
- "Matchups as a candidate cause" was wrong and is replaced.
- The figures that `arena_compare.py` does not compute are archived with their script in
  `docs/data/noise-2026-10-30/`.
- M's 95 % interval is stated, and "provisional" is back.

**Second check** (workflow wf_c02ef4ed-7ae). Two checkers covered the code and the archived script, and
the docs after the fixes, each followed by a refuting verifier.
- They confirmed `removed()` on both seats and on manifests without scores, and found the JSON report
  backward compatible. The new test catches swapped sides, a missing agent side and a dropped print line
  (mutants run on copies). `noise.txt` reproduced byte for byte.
- Of 9 findings, 2 were confirmed, both low:
  - the test's "both queens alive" game had a score of 0, which is a dead queen;
  - the "56-59 different opponents" behind the matchup argument came from no archived output.
- Fixed:
  - the fixture now has live queens, removed players ranked last and a second removal of the agent; the
    test also checks the printed ids, the points and the rated games;
  - `noise.py` prints the distinct opponents per upload (58, 56 and 59 of 60) and the frame count of each
    removed game (3: turn 0).
- Taken from the refuted ones as cheap improvements:
  - `noise.py` picks the three uploads by agent id, so a later v01 upload does not break the documented
    command;
  - it prints the seat effect as a signed rating shift (-1.07 points);
  - the seat coefficients are quoted with three decimals (the source's precision);
  - the shared-cause sentence keeps "or chance" in DEVLOG and project.md;
  - M gets "(2 df, provisional)" in BOTS, PLAN and project.md's state;
  - STATUS names upload 2's forfeits.
- Not taken: repeating the forfeits next to every count that includes upload 2's wins (they are CG's
  counts, and the convention is stated where the figures are reported).

Decision: v01 stays selected. Its Bronze baseline is three uploads, mean 17.77, and the live entry is
upload 3 (`1e5d1a79`, which plays as v01). The policy is unchanged.

## 2026-10-30 — phase 3: local screen P2 adopted

The user chose to start phase 3 with the local part of the promotion policy, then the queen-stall
candidate. They approved the proposal as made: policy P2, `docs/local-screen.md`. The old bot joins the
opponent panel as `old`, and the Bronze boss's exact rating is left for later.

**Pilot** on development seeds, before the proposal:
- `./gradlew bench "-Pbot1=current?MAX_TOWERS=3" -Pbot2=v01 -Pmaps=500 -Pthreads=8 -PseedBase=1000 -PturnMs=50`:
  - score 0.486 ± 0.015, W/D/L 485/2/513; the candidate won 316 games on seat 0 and 169 on seat 1;
  - 1,000 games took 8.8 s, Gradle included;
  - in 439 of the 500 maps each bot won one game; the map score's SD is 0.172.
- `./gradlew bench -Pbot1=v01 -Pbot2=boss -Pmaps=60 -Pthreads=3 -PseedBase=1000 -PturnMs=200`: score 0.950 ±
  0.038, W/D/L 114/0/6, 0 timeouts; 120 games took 13.9 s.
- The raw reports are in `replays/bench/pilot-2026-10-30/` (sha256 `f18df820…` and `6351f301…`).
- Every bot is deterministic: ours run with SEED=1, and `Boss.java` has no Random. A rerun repeats the games.

**Target.**
- Source: the public Bronze leaderboard at 08:21 (`Leaderboards/getFilteredPuzzleLeaderboard`, LEAGUE
  bronze, active), 2291 players.
- The top rows stood at 27.75 (an agent created at 08:01, 33 % placed), then 27.14, 27.01 and 27.00. No
  row was eligible for promotion. aabzac 6824680 was at local rank 651 with 18.98.
- So the boss stands above about 27.1, about 9 points over v01's pooled rating of 17.92.
- Snapshot: `docs/data/leaderboard-bronze-2026-10-30.json`; the raw response is in `replays/cg/leaderboard/`
  (sha256 `d96b4b87…`).

**The local `boss` is not the live boss.** `refs/referee-csj/config/level4/Boss.java` is the "Default AI":
- one knight barracks on the site nearest the queen, mines to income 10, then towers;
- TRAIN whenever it has 80 gold, and no evasion.

v01 beats it in 95 % of games. If the live boss played like this, it would rate far below v01, while it
stands above 27.1. So `boss` is a floor in P2.

**Proxy validation PV1** (`docs/data/pv1-2026-10-30/pv1.py`, output `pv1.txt`):
- Arena, v01's 180 placement games:
  - opponents built knight barracks in 96 % of games, archers in 17 %, giants in 12 % (either: 24 %),
    towers in 92 % and mines in 94 %;
  - v01 won 70 of 129 games against knight-only opponents (54 %), 16 of 22 against archer and knight
    players, 6 of 12 against giant and knight players, and 3 of 9 against players with all three;
  - games lasted 110.4 turns on average (median 86.5), and 13 % reached the limit.
- Local:
  - v01 against `boss`: 81.8 turns, none at the limit;
  - the near-mirror above: 151.7 turns, 11 % at the limit.
- Both local opponents play knights only. A local opponent with archers or giants is a PLAN item.

**P2 in short:**
- Fresh blocks of 2000 maps: screen `200000 + 2000·k`, final evaluation `400000 + 2000·k`
  (`docs/data/seed-ledger.json`).
- Gates:
  - G0: no failure of the candidate.
  - G1: S against the control, lower 95 % bound > 0.50.
  - G2: Δ against `boss` ≥ −2 pp on 500 maps.
  - G3: Δ against `old` ≥ −2 pp on 500 maps, once its code is in place.
  - A targeted fix (fewer than 10 % of the maps changed) needs G0, S ≥ 0.500, Δ ≥ −1 pp against `boss` and
    a regression test.
- Replication only when several arms shared a block. Upload recommendation at S ≥ 0.60 against the
  selected version on a final-evaluation block.
- Calibration with `old`: from the arena ratings, v01 should score about 0.22 against it.

**Tools:**
- `BenchmarkRunner`:
  - counts slow turns and timeouts per game;
  - the report records both specs' `BotFactory.describe` (full parameters, or a program source's sha256),
    the commit with its status, the sha256 of the tracked diff and of each untracked file, the threads and
    the Java version;
  - `-Pout=<path>` sets the report path.
- `BotFactory`: the spec `old` compiles `refs/old-3315948/Player.java` into `build/opponents/old`.
- `tools/bench_pair.py`: direct and common modes, the gates as options, dropped maps, and maps changed
  from the control's mirror. It has 10 offline tests; Python tests went from 13 to 23, Java tests to 90.
- Smoke run on 20 development maps: `current?MAX_TOWERS=3` against v01, with the v01 mirror. S 0.500;
  12 of 20 maps changed, so it is a broad change; G0 passed.

## 2026-10-30 — first candidate: UNSTICK (queen stalls), passed P2 as a targeted fix

**The bug.** A BUILD towards a site the queen does not touch walks her straight at the site's centre
(`Rules`: `moveQueen` to the site). A site on that line pushes her straight back, and the rounding of
positions keeps the equilibrium. The audit's scanner was not committed, so `cr.tools.StallScan`
(`./gradlew stallScan`) rebuilds it:
- A stall turn is a BUILD to a site the queen does not touch that leaves her where she was.
- On seeds 10000-10499 (current against v01, 1000 games) it reproduces the audit exactly: 240 stall
  turns, 44 streaks [1:18, 2-4:10, 5-9:9, 10-19:5, 20-39:1, 40+:1], and 5 games with 10+ turns, 3 of them
  not won.

Two shapes:
- **Head-on**: the blocking site's centre lies exactly on the line, as in seeds 10477, 10206, 10035 and
  10062. The worst case is 10477, seat 1, turns 51-93: 43 turns pressed against her own barracks on site
  20, on `BUILD 12 MINE`.
- **Wedge**: in seed 10394 the queen at 1551,710 touches sites 13 and 8. Her line to site 3 runs between
  them through a 27-unit gap; she is 60 wide.

**The flag.** `Params.UNSTICK` is 0 by default; the v01 snapshot gains `UNSTICK=0`, which `GreedyBot`
does not read. With 1, `MainBot.unstick` handles the BUILD towards the next site to take:
- If the straight step, predicted with the sites only, moves the queen less than 2 and does not make her
  touch the site, she detours.
- The prediction is `Physics.queenStep`: the step, 5 collision passes and the rounding, with only the
  sites she can reach loaded.
- The detour plan: a heading turned from the straight line in 15-degree steps, 1-6 MOVEs on it, then the
  straight walk. The plan that touches the site in the fewest turns (within 25) wins, and its MOVEs go on
  over the next turns.
- Fleeing to a tower is left alone.

**Development** on seeds 10000-10499 only, each variant against v01 with the v01 mirror (500 maps):

| Variant | Changed maps | S | Note |
| --- | --- | --- | --- |
| trigger: the step gains < 1 towards the site; one detour step | 142 (28.4 %) | 0.498 | sliding along a site gains little too |
| trigger: the predicted position does not change | 11 (2.2 %) | 0.500 | the wedge still 4 turns: creeps make the queen wobble by a unit, and one detour step led back into the wedge |
| planner, steps < 5 count as standing, every BUILD | 324 (64.8 %) | 0.501 | fleeing to a tower moves little on purpose |
| planner only on the next site to take; plan kept on consecutive turns | 157 (31.4 %) | 0.506 | longest turn 29 ms |
| a step that ends touching the site counts as progress | 68 (13.6 %) | 0.507 | the queen at exactly r + 35 counted as stuck |
| `queenStep` loads only reachable sites | 68 (13.6 %) | 0.507 | same games, longest turn 3.7 ms |
| steps < 2 count as standing (**screened**) | 22 (4.4 %) | 0.500 | stall turns 9, all on BUILDs towards towers |

The step-5 variant also speeds up slow slides. It is a broad change, kept as a later idea.

**Tests** (Java 90 → 96):
- `RulesTest.queenStepPredictsTheRules`: `queenStep` lands where `Rules.resolve` puts a lone queen, over
  more than 1000 random walks on three maps.
- `MainBotTest`:
  - the two captured positions, as the exact input text seat 1 read
    (`src/test/resources/cr/bot/current/`): with UNSTICK=0 the bot sends the stalling BUILD and never
    arrives; with 1 it MOVEs and touches the site within 10 and 15 turns;
  - a free walk keeps the BUILD;
  - with no flag on, current plays v01's commands on 10 seeds, on both seats.
- `StallScanTest`: the whole games of seeds 10477 and 10394. Streaks of 43 and 17 turns without the flag,
  at most 2 with it.

**Screen** (P2, targeted-fix track, fresh block 0 = seeds 200000-201999;
`docs/data/exp-2026-10-30-unstick/`, hypothesis and commands written before the run):
- Direct, against v01, 2000 maps: S = 0.5046 (95 % CI 0.5018-0.5075), W/D/L 2002/33/1965. 146 of 2000 maps
  changed (7.3 %).
- Against `boss`, 500 maps: Δ = 0.0000 (CI −0.0028..0.0028).
- G0: no failure; longest turn 5.4 ms.
- Every gate passes. The lower bound would clear G1 of a strategy candidate too (context only).
- Mechanism on the block, 4000 games:
  - stall turns 739 → 53; streaks 144 → 49, the longest 25 → 2;
  - games with a 10+ streak 14 → 0 (5 of the 14 not won).
- After the screen only a comment of `MainBot` changed. 400 of 400 rerun games were identical.

Decision: UNSTICK passes P2 as a targeted fix. Under P2 it joins the local champion. Whether it becomes
the `current` default and v02 is frozen is the user's call, so the default stays 0 for now.

## 2026-10-30 — v02 frozen; the user sends it to the arena

The user asked for a commit and for v02 on the arena. Commit 0606c2c holds P2 and UNSTICK, still off.

- v02 = `cr.bot.v02.GreedyBot`, a copy of `MainBot` at 0606c2c (only the class name and the header differ).
  Its snapshot `versions/v02.properties` is v01's with UNSTICK=1.
- `Params.UNSTICK` now defaults to 1, so `current` plays as v02. `current?UNSTICK=0` plays as v01.
- Tests (Java 96 → 97):
  - `MainBotTest.withDefaultsCurrentPlaysAsV02`: current and v02 give identical replays on 8 seeds with
    detours, on both seats, against v01 and v02;
  - the v01 equivalence test now uses `current?UNSTICK=0`;
  - `BotFactoryTest` checks the v02 snapshot.
- P2 recommends an upload only when the local champion scores S ≥ 0.60 against the selected version on a
  final-evaluation block. v02 is at about 0.505, so the arena cannot show its gain (M ≈ 3). The user
  chose to test it there anyway; under P1 a decision needs two uploads.

**Final evaluation** (P2, final-evaluation block 0 = seeds 400000-401999, commit a3e48fc;
`docs/data/exp-2026-10-30-v02-eval/`):
- G0 passed; G1 v02 against v01 S = 0.5032 (CI 0.5005-0.5060, 110 of 2000 maps changed); G2 Δ = +0.0010
  against `boss`.
- G3 skipped (no `old`). The upload recommendation (S ≥ 0.60) is not met.
- Stall scan on the block:
  - v01: 464 stall turns, 15 games with 10+;
  - v02: 74 stall turns, 3 games with 10+.
- v02's three long stalls are early BUILDs to a mine where her own knight stands against the queen. The
  sites-only prediction sees a step, so UNSTICK does not fire. A later idea (PLAN): judge being stuck
  from the previous turn too.

**Artifact**: `./gradlew submit` from a3e48fc gives `527244968dbe…`.
- 64,230 chars, build `a2c71dbb`, config `173dd43b`; kept copy `replays/artifacts/52724496/Player.java`.
- procCheck against v01 (100 maps from 5000) and against `boss` (100 maps from 6000): 200/200 identical to
  current each, 0 timeouts, 0 kills, 0 extra/late/stderr lines. The longest first turn was 158.5/184.2 ms,
  the longest later turn 3.4/3.5 ms.
- The marker `TRAIN 10 2 12 7 1 13 11 11` repeats site 11, which has not been seen live. In the repo
  referee (`Referee.kt:178-189`) the per-site check comes first and warns at site 10 on turn 0. The
  duplicate check after it is a warning too. Upload 1 is the live check.

**Before the Submit**: the live entry 6824680 was saved at 09:32 (18.98, rank 651/2291, 60 battles,
`docs/data/arena-6824680-presubmit-2026-10-30.json`). It played no game after its placement, and all 60
games are stored (`replays/arena/v01/bronze/upload-03/games`, manifest `docs/data/arena-6824680-games-2026-10-29.json`).
The watcher still saw 60 battles at 09:55.

**The upload, by the agent with the user's explicit permission.** The user was on a phone and asked for
the upload without their help. In the desktop app's built-in browser they were signed in to CodinGame.
The user allowed, in words, swapping the code in the IDE's Play request for v02, checking it by sha256
and pressing Submit. This was a one-time permission; the standing rule stays that the user uploads.
- **Getting the text in:**
  - CG's page blocks requests to http addresses (CSP `connect-src data: https: wss:`), and the built-in
    browser opens no tab from page script. So the text came through `window.name`: a relay page on
    127.0.0.1 set it, the same tab then navigated to the IDE, and the page read it there.
  - On the IDE page its sha256 was `527244968dbe…`. `window.name` was cleared right after.
- **The editor:** Monaco ignores synthetic input: neither Ctrl+V nor a synthetic paste event changed it.
- **Play:** a page hook replaced the code in the IDE's own `TestSession/play` request (one field) with
  v02, and the IDE played against Princess Boss.
  - On turn 0 the bot printed `TRAIN 10 2 12 7 1 13 11 11`. The referee only warned `Cannot spawn from
    10: not a barracks` and the game went on: the first live repeated marker site, as `Referee.kt`
    predicted.
  - The play response was cut off in the tool output, so the boss's score was not read.
- **Checks:**
  - CG's saved IDE code (`TestSession/startTestSession`, `answer.code`) then had sha256 `527244968dbe…`.
  - After a reload the editor held it (1486 lines).
- **Submit:** the IDE's button is now labelled "Test in arena" (class `submit`); CG asked "You did not
  test your answer" and the agent confirmed. Submission 41598763 (`TestSession/submit`) at 09:58:49;
  the saved code is still `527244968dbe…`. Agent 6826276, Bronze, placement started: 10 battles queued.
- **Before the upload, the IDE's code was not the v01 artifact byte for byte:** 57,884 chars with the
  final newline replaced by a space (`}\n\n} `). That is upload 3's IDE text; the play matched
  `1e5d1a79` (`cgAttribute` SAME 60/60) and the difference is whitespace.

## 2026-10-30 — v02 upload 1 placed 17.23; P1 asks for upload 2

**Placement** (agent 6826276, Bronze; `docs/data/arena-6826276-placement-2026-10-30.json`):
- Followed by snapshots every 3 minutes, every minute from 85 %. The first 10 games were done by
  10:00:46 (4 wins, 9.02).
- Then CG scheduled no battle until about 10:27. The user thought of re-submitting; the 10 games showed
  nothing wrong (marker in all, no referee removal of v02), so they waited, and the games resumed.
- Complete at 12:50:07: score **17.23**, rank 891/2289, 60 games, 37 wins (seat 0: 19 of 30, seat 1: 18
  of 30).
- One win is an opponent's turn-0 timeout (913406622, score −1). v02 was never removed.

**Which code played** (manifest `docs/data/arena-6826276-games-2026-10-30.json`):
- cgValidate: 59/60 games, every one of 5,983 turns matches, warnings exact 1164/1164.
  - The repeated-site marker's turn-0 warning (`Cannot spawn from 10`) is matched.
  - The one mismatch is the winner of game 913398184: both queens died on turn 38 (0:0), and CG ranked
    player 0 first while the simulator calls a draw. That is the third known tie game, and CG put
    player 0 first in all three (`docs/rules/inferred.md`).
- `cgAttribute` against the kept copy `replays/artifacts/52724496/Player.java`: SAME in 60/60, 5,983
  turns, marker `a2c71dbb` in 60/60.
- Control, in-process v01: DIFF in 2 of 60 (913407360 from turn 20, 913424791 from turn 167). UNSTICK
  changed the play in only those two games.
- The first manifest run also merged the battle lists saved before the Submit. Those hold 6824680's
  games, so it listed 60 failures ("agent 6826276 not among the players"). It was rerun with the new
  agent's lists only (10:01 on): 60 games, 0 failures.

**P1** (`python -I tools/arena_compare.py --candidate v02 --selected v01`, registry entry v02 upload 1):
- v02 upload 1: 17.23, win rate 61.7 %, opponents' median 16.02, performance 16.75 ± 0.43 (logistic scale
  refitted to 1.36).
- v01: mean 17.77, pooled performance 17.92 ± 0.24. M = 3.01 (pooled over v01 only).
- Verdict: **upload the candidate again**. Upload 1 is 0.54 below the selected mean, far from early
  rollback (2M = 6.03). A decision needs upload 2.
- Context: v02 played as v01 in 58 of 60 games, so the gap in score and rating against v01's uploads is
  placement noise. It is one more sample of the spread between placements of near-identical code.

Local against arena (`docs/local-screen.md`): v02 locally S 0.5032 against v01 (evaluation block 0);
arena upload 1 17.23 against v01's mean 17.77, P1 pending.

## 2026-10-30 — v02 upload 2 placed 18.40; P1: not clearly worse, v02 selected

The user allowed the re-upload ("Да" to re-uploading the same artifact in the IDE).
- Before the Submit, 6826276 was saved at 13:11:22 (17.23, rank 891, 60 battles, all already in its
  manifest; `docs/data/arena-6826276-presubmit-2026-10-30.json`).
- The IDE still held v02: its saved code had sha256 `527244968dbe…`, and after a reload the editor showed
  1486 lines. No code swap was needed.
- "Test in arena", then Yes: submission 41599649 at 13:12:18, agent 6827094.

**Placement** (`docs/data/arena-6827094-placement-2026-10-30.json`, followed as before):
- Complete at 15:16:06: score **18.40**, rank 715/2289, 60 games.
- 36 wins by CG ranks (seat 0: 19 of 26, seat 1: 17 of 34). One of them is game 913453290, 50:50 HP at
  turn 250, where CG ranked player 0 (v02) first; the battle list counts 35.
- Two wins are opponents' turn-0 timeouts (913449112, 913469680). v02 was never removed.

**Which code played** (manifest `docs/data/arena-6827094-games-2026-10-30.json`, 0 failures):
- cgValidate: 59/60, all 7,084 turns, warnings exact 1101/1101. The one mismatch is the winner of the
  tie game 913453290: the fourth known tie, and the fourth ranked player 0 first (`docs/rules/inferred.md`).
- `cgAttribute` against `replays/artifacts/52724496/Player.java`: SAME 60/60, 7,084 turns, marker
  `a2c71dbb` 60/60.
- Control, in-process v01: DIFF in 4 of 60. UNSTICK changed the play in games 913452154 (turn 61),
  913453290 (turn 167), 913457400 (turn 64) and 913463667 (turn 88).

**P1** (`python -I tools/arena_compare.py --candidate v02 --selected v01`, logistic scale 1.35):

| label | uploads | placement | mean | SD | pooled performance |
| --- | --- | --- | --- | --- | --- |
| v01 | 3 | 18.24, 16.08, 18.98 | 17.77 | 1.51 | 17.92 ± 0.23 (180 games) |
| v02 | 2 | 17.23, 18.40 | 17.81 | 0.83 | 17.68 ± 0.29 (120 games) |

- Noise: pooled SD 1.32 on 3 df, **M = 2.64** (95 % interval about 1.5-9.8; provisional).
- Verdict: **not clearly worse**. The mean is +0.05 against −M = −2.64, and the pooled rating −0.24
  against −2 SE = −0.75.
- Under P1, v02 becomes the selected version. Its Bronze baseline is these two uploads (mean 17.81).
- This shows no gain. v02 played as v01 in 58 and 56 of its 60 games, and the arena cannot see a fix of
  this size: its two placements are in effect two more samples of v01's noise.
- Rollback artifact: a clean export of a3e48fc rebuilds `527244968dbe…` byte for byte (`./gradlew
  submit`, 2026-10-30). The kept copy is `replays/artifacts/52724496/Player.java`.

## 2026-10-30 — study: we lose for lack of early towers

The user chose to study the stronger players' play and our losses (`docs/data/study-2026-10-30/`).
- **Tool:** `cr.tools.CgStudy` (`./gradlew cgStudy`) replays recorded games through `Rules` and samples
  per-player features.
- **Data:** our 300 placement games, 138 games of `replays/cg2` and 88 Legend games.

Findings:
- **Towers.** We build one knight barracks on turn 1 and towers only after income 10: first tower at a
  median turn of 31, most towers 2. Our opponents, top-Bronze, Silver, Gold and Legend players build
  their first tower around turn 15-19 and hold 3-5.
- **Our towers and our results.** By our towers at turn 50 we win 32 % (0 towers, 72 games), 59 % (1)
  and 83 % (2). By the opponent's towers at turn 50 our win rate falls from 89 % (0) to 48 % (3+).
- **Losses.** 105 of 124 are a killed queen, at a median turn of 80. In losses our queen has 22 HP by
  turn 100, in wins 41.

**Development test** (pool 100000-100499, existing parameters):
- **Against v02:** `INCOME_TARGET=2,MAX_TOWERS=6` 0.797 ± 0.028, `INCOME_TARGET=3,MAX_TOWERS=8` 0.782.
  Lowering only the income target gives 0.670 (target 4) and 0.525 (target 8); raising only the tower
  cap gives 0.506.
- **The same arms, Δ against v02 on the same maps:**
  - v01 with 2 barracks: +13 to +14 pp;
  - a tower-heavy v01: +35 to +44 pp;
  - 2 barracks with income target 4: +24 pp.
- **`boss`:** 0.98-0.99 against 0.92.
- **Caveats:**
  - no local opponent uses giants, which break towers (12 % of v01's arena opponents build them);
  - `old` is still missing;
  - twelve arms shared the development block (a winner's curse).

The candidates are proposed to the user (early towers first).

## 2026-10-30 — early towers pass P2 (S 0.80-0.81 against v02); v03 frozen

The user chose to screen `INCOME_TARGET=3,MAX_TOWERS=8` alone (one arm, so no replication). The
hypothesis and gates were written before the run (`docs/data/exp-2026-10-30-towers/README.md`).
- **Screen** on block 1 (seeds 202000-203999), control v02:
  - G0 passed;
  - G1: S = 0.7969 (CI 0.7834-0.8103), W/D/L 3170/35/795;
  - G2: `boss` 0.993 against v02's 0.916, Δ +7.7 pp;
  - G3 skipped (no `old`).
  - The development estimate on 500 maps was 0.782, so no winner's curse shows. Games against v02 last
    226 turns on average.
- **Final evaluation** on evaluation block 1 (402000-403999):
  - S = 0.8146 (CI 0.8015-0.8278), W/D/L 3243/31/726, both seats won (1692 and 1551 of 2000);
  - `boss` 0.991 against 0.943;
  - G0 passed.
  - The upload recommendation S ≥ 0.60 is met: about +2 rating points against v02 head-to-head at the
    arena's logistic scale, which is not the field.
- **v03 frozen** as the local champion:
  - `cr.bot.v03.GreedyBot` is a copy of `MainBot`; `versions/v03.properties` has INCOME_TARGET=3 and
    MAX_TOWERS=8, and these are the `Params` defaults now.
  - Tests pin v01's and v02's parameters where they test those versions: `MainBotTest`, `StallScanTest`,
    and `CgAttributeTest`, which builds its artifact with v01's parameters.
  - `MainBotTest.withDefaultsCurrentPlaysAsV03` checks that current plays as v03. Java tests 97 → 98.
- **Not covered locally:** giants against towers. No local opponent uses them; 12 % of the arena
  opponents build them.

## 2026-10-30 — v03 upload 1 placed 23.25 (+5.4 over v02)

The user chose to commit and upload v03 (option "а": commit, save the live entry, swap the code in the
IDE's Play request, check sha256, submit, follow).
- **Before the upload:**
  - commit 095d1eb; a clean export rebuilds `d6266d41…` byte for byte;
  - 6827094 saved at 16:41:06 (18.40, rank 715, all 60 games in its manifest,
    `docs/data/arena-6827094-presubmit-2026-10-30.json`).
- **In the built-in browser:**
  - the v03 text reached the IDE page through `window.name` from the relay page, sha256 `d6266d41…`, and
    `window.name` was cleared;
  - the hook swapped it into `TestSession/play`. The IDE game against Princess Boss printed `TRAIN 12 13
    1 15 3 13 3 3` on turn 0 and drew only `Cannot spawn from 12: not a barracks`;
  - CG's saved code was `d6266d41…`, and after a reload the editor held `INCOME_TARGET = 3`;
  - "Test in arena", then Yes: submission 41600876, agent 6827580, created 16:43:22.
- **Placement** (`docs/data/arena-6827580-placement-2026-10-30.json`): complete at 18:46:52, score
  **23.25**, rank 240/2290.
  - 60 games, 37 wins (seat 0: 25 of 36, seat 1: 12 of 24). One win is an opponent's timeout on turn 115.
  - The opponents were much stronger as v03 climbed: their median score was 23.07, against 16-18.5 for
    the earlier uploads. Games lasted 160.5 turns at the median.
- **Which code played** (manifest `docs/data/arena-6827580-games-2026-10-30.json`, 0 failures):
  - cgValidate: 58/60, all 10,194 turns, warnings exact 1641/1641. Two mismatches:
    - the tie 913522461 (50:50 at turn 250, player 0, the opponent, ranked first);
    - the opponent's mid-game timeout in 913520691, where CG scored our queen 52 and the simulator 53
      (`docs/rules/inferred.md`, items 1-2).
  - `cgAttribute` against `replays/artifacts/d6266d41/Player.java`: SAME 60/60, marker `cd1f3d33` 60/60.
  - In-process v02 differs in all 60.
- **P1** (`python -I tools/arena_compare.py --candidate v03 --selected v02`):
  - v03 upload 1: 23.25, performance rating 23.25 ± 0.43;
  - v02: mean 17.81, rating 17.68 ± 0.30.
  - Verdict: **upload the candidate again**; a decision needs upload 2. Upload 1 is +5.44 above v02's
    mean, twice M (2.64).
- **Local against arena:** S 0.815 against v02 locally (about +2 rating points head-to-head) and +5.4
  placement points on the arena. The field gained more than the head-to-head suggested.
- **The gap to the boss** (above about 27.1) shrinks from about 9.3 to about 3.9 points.

## 2026-10-30 — v03 upload 2 placed 20.67; P1: v03 selected (+4.15 over v02)

The user allowed the re-upload ("Да").
- 6827580 was saved at 18:52:57 (23.25, rank 240, all 60 games in its manifest;
  `docs/data/arena-6827580-presubmit-2026-10-30.json`).
- The IDE held v03: saved code `d6266d41…`, and after a reload the editor had `INCOME_TARGET = 3`.
- "Test in arena", then Yes: submission 41601594, agent 6828013, created 18:53:45.

**Placement** (`docs/data/arena-6828013-placement-2026-10-30.json`):
- Complete at 19:31:43, after only 38 minutes: score **20.67**, rank 428/2290.
- 60 games, 39 wins (seat 0: 25 of 34, seat 1: 14 of 26). The opponents' median was 19.48 (23.07 for
  upload 1).
- No removed player and no tie.

**Which code played** (manifest `docs/data/arena-6828013-games-2026-10-30.json`, 0 failures):
- cgValidate: 60/60 games, all 8,286 turns, warnings exact 1509/1509, no mismatch.
- `cgAttribute` against `replays/artifacts/d6266d41/Player.java`: SAME 60/60, marker `cd1f3d33` 60/60.
- In-process v02 differs in all 60.

**P1** (`python -I tools/arena_compare.py --candidate v03 --selected v02`, logistic scale 1.37):

| label | uploads | placement | mean | SD | pooled performance |
| --- | --- | --- | --- | --- | --- |
| v02 | 2 | 17.23, 18.40 | 17.81 | 0.83 | 17.68 ± 0.30 |
| v03 | 2 | 23.25, 20.67 | 21.96 | 1.82 | 21.75 ± 0.34 |

- Verdict: **not clearly worse**: the mean is +4.15 against −M, and the rating +4.06 against −2 SE.
  v03 becomes the selected version, with its Bronze baseline these two uploads (mean 21.96).
- P1 makes no claim of superiority. Still, both measures put v03 far above v02: the ratings differ by
  4.06 with an SE of the difference of about 0.45. The local screen predicted this direction.
- Noise over v01 (3), v02 (2) and v03 (2): pooled SD 1.46 on 4 df, **M = 2.92** (95 % interval about
  1.75-8.4; provisional). v03's two placements differ by 2.58 points.
- Rollback artifact: commit 095d1eb rebuilds `d6266d41…` byte for byte (clean export, 2026-10-30). The
  kept copy is `replays/artifacts/d6266d41/Player.java`.
- The gap to the boss (above about 27.1): about 5.1 points from v03's mean, about 3.9 from its better
  placement.

## 2026-10-30 — records: a summary for the user, one place per fact

The user found the commits after an upload or an experiment too long and chose to keep a short summary
for themselves apart from the agents' records. An upload commit had 3,400-3,800 lines: about 97 %
pretty-printed JSON evidence, plus 100-130 lines of text over 8-9 files that repeated the same numbers.
- `docs/SUMMARY.md`, in Russian: the state now, then 3-5 lines per decision, without hashes or ids.
- One place per fact (`.ai/project.md`, row "Записи после загрузки, эксперимента или решения"): uploads
  in `docs/BOTS.md` and `docs/data/arena-uploads.json`, P1 and M in `docs/arena-uploads.md`, the work and
  its evidence in this log, `docs/STATUS.md` the current state only, `docs/PLAN.md` ticks with pointers.
- Removed duplicates: the history of replaced agents in STATUS; the current-state section of
  `.ai/project.md` (10.6 KB that every session loaded through `AGENTS.md`) and its rows that changed with
  each upload (upload lists, M history, rollback artifacts, tie games); numbers in PLAN items and in
  `docs/local-screen.md`. The old texts: `git show 60f0b59:docs/STATUS.md`, `git show 60f0b59:.ai/project.md`.
- Check: of the 194 numbers, ids, hashes, times and paths in the removed lines, 192 appear in the
  remaining docs. The snapshot of 3315948 went into its BOTS row; `presubmit-*` is a pattern whose files
  PLAN names. The JSON evidence keeps its format (one value per line).

## 2026-10-30 — the bot architecture: the HTML page only

The user kept `docs/bot-architecture.html` (Russian, with diagrams) as the only description of the bot
and dropped its English twin `docs/bot-architecture.md` (`git show 148fa07:docs/bot-architecture.md`).
What only the Markdown had moved into the page: the UNSTICK and version-reproduction test names, the
`Params` snapshot path, the SLOW threshold (limit minus 10 ms), the last seen site values being kept,
and two UNSTICK cases (already touching the site; builds on a touched site left alone). Turn times stay
where they were: the 2026-10-28 process run in this log, the procCheck maxima per artifact in
`docs/BOTS.md`. Pointers changed in `.ai/project.md` and `docs/BOTS.md`.

## 2026-10-31 — arena replay directories and automatic download destinations

The user chose version → league → upload and authorized moving existing captures and updating the
downloaders. Before changes: `d44a016257ce0c3a2629e299a77fb9c5a9e6d7da`, clean working tree.
The layout and commands now live in `docs/arena-uploads.md`, Replay storage; the registry remains
the source of upload identity. No bot, simulator or artifact changed, and no platform request was made.

- Moved every file in the old `replays/cg/arena-*` directories, with SHA-256 checks before and after;
  removed only the emptied directories. Compact inventory hashes, mapping and exceptions:
  `docs/data/arena-layout-2026-10-31.json`. The one-off local preflight/move script and detailed plan
  are `build/migrate_arena.py` and `build/arena-migration-before.json` (ignored).
- Some historical pre-submit captures were filed under the next upload even though the responses
  still named its predecessor. Reassigned these by the actual agent in the raw response, preserving
  the bytes; the mapping records every exception. Also corrected the v03 upload-2 placement report's
  existing snapshots-directory typo, which had pointed at v02 upload 2.
- Added a manifest and `legacyUploads` entry for the pre-project bot. It stays outside P1 comparisons.
  Updated manifest paths and active callers, including the real-replay test fixture and PV1 reader.
  Archived inference scripts and feature CSV bytes retain their historical paths; their READMEs point
  to the mapping. Project commands were regenerated from `.ai/project.md` using the locked local kit.
- Downloaders resolve directories through the registry, merge saved lists/cache/manifest IDs and
  keep failed completed-game IDs for retry. They preserve conflicting markers under `unknown`,
  preserve rejected list responses outside automatic discovery, and route snapshots by the observed
  agent. A replacement agent cannot overwrite an existing predecessor's compact snapshot.

Validation:
- `python -B -I tools/arena_games.py <agent> --offline --manifest build/arena-layout-check-<agent>.json`
  for every registered and legacy upload: all saved games recovered, zero failures, original
  per-game records unchanged (the oldest manifest's absent marker fields compare as null).
- All files checked against the pre-move SHA-256 inventory; all recorded saved-list paths resolve.
- `python -B -I tools/arena_compare.py --candidate v03 --selected v02 --out build/arena-compare-after-layout.json`:
  complete JSON report identical to `build/arena-compare-before-layout.json`.
- `python -B -I docs/data/pv1-2026-10-30/pv1.py`: all output lines identical to the pre-move run and
  the preserved `pv1.txt`.
- `./gradlew.bat test --tests cr.tools.CgAttributeTest`: all eight tests passed, none skipped;
  the relocated real replay is still exercised. Full simulator/strength runs were unnecessary:
  executable game behavior and replay bytes did not change.
- Python downloader regressions: `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`: all 44 passed;
  cover empty live lists, recovery after failed download, absent markers, conflicting markers,
  rejected responses followed by a successful retry, unknown/promoted agents and snapshot preservation.
- `python -B -I .ai/sync.py sync . --source ../cg-agent-kit`, its `check` command and `git diff --check` passed.

Follow-up at the user's request: moved the three `tools/test_*.py` files into `tools/tests`,
adjusted their imports for `python -I` and updated discovery commands. All 44 tests passed with
`python -B -I -m unittest discover -s tools/tests -p "test_*.py"`. The first sandboxed run could not
access its default temporary directories; the successful run set TEMP and TMP to `build/test-tmp`
for that process only. No test behavior changed.

## 2026-10-31 — arena dashboard and recorded replay viewer

Implemented the user's first stage: overview, opponents, filtered games and replay navigation
within the same selection. Before changes: `902a3d029d415b2c30711b534286f2d449da78a3`, clean tree.
Usage, data semantics and limitations are maintained in `docs/visualizer.md`.

The catalog reads the upload registry and manifests; an individual raw replay is loaded on demand
through `/api/replay`. `CgViewReplay` decodes recorded SDK entities and tooltips without running a bot
or simulator. Local replay links remain supported. URL state preserves filters, ordering, page,
game and turn; returning from a game restores the list's scroll position. The server stays on
loopback and rejects paths outside the repository. Updated the launcher to use the Gradle runtime
classpath and regenerated project instructions from the locked local kit.

Validation:
- `node --test visualizer/tests/catalog.test.mjs`: 6 passed. Includes real manifests, rank-based
  outcomes, equal scores, disqualifications, league/legacy separation, opponent filters and partial data.
- `./gradlew.bat test --tests cr.tools.CgViewReplayTest --tests cr.tools.ServeTest`: 4 passed,
  none skipped. All 430 saved arena games decoded with no ownership warnings; commands and outcomes
  matched their sources, including 7 disqualifications and 3 equal-score games. Synthetic fixtures
  exercise building ownership changes, deaths, training, missing commands and local JSON handling;
  HTTP checks cover MIME types, missing files and path containment.
- Browser checks at 1440 px and 390 px: overview, opponent drilldown, empty search, combined
  loss/side filters, next/previous replay, list pagination, direct local playback, seeking and reload.
  Returning from game 31 of a 420-game selection restored page 2 and its scroll position.
  Simulated missing manifest, missing registry and missing raw replay retained usable navigation
  and explicit error/partial-data messages. A persisted pagehide/pageshow event check confirmed
  playback controls remain active after the lifecycle cleanup fix.
- Syntax and whitespace checks passed. Local QA captures are ignored under `build/`; browser
  automation output is ignored under `.playwright-mcp/`.

No platform requests, replay-byte changes, bot changes or simulator changes. Strength benchmarks
and full simulator validation were not run because this change only reads existing recorded games.

## 2026-10-31 — v03 upload 3 (the user's re-submit) placed 23.37

The user re-submitted v03 at 01:07 and asked for its games. Before changes: 2cb1007, clean tree.

- **The replaced entry 6828013 was not saved before the re-submit.** Afterwards,
  `python -B -I tools/arena_games.py 6828013 --manifest build/arena-6828013-recheck-2026-10-31.json`
  found its live list empty and all 60 placement games stored (marker `cd1f3d33` in 60).
- **The new agent.** `python -B -I tools/arena_snapshot.py 6828013 aabzac bronze build/arena-probe-2026-10-31.json`
  followed the pseudo to agent 6829912 (submission 41603803, created 01:07:26).
  - At 02:13:37 the placement was complete: score **23.37**, rank 233/2289, 60 battles, 38 wins.
  - The leaderboard last updated the score at 02:06:47.
  - The raw responses stay in `replays/arena/unknown/bronze/agent-6829912/snapshots`, where the
    downloader finds them. The summary is `docs/data/arena-6829912-placement-2026-10-31.json`.
- **Registered** as v03 upload 3, then `python -B -I tools/arena_games.py 6829912`: 60 games, 0 failures,
  marker `cd1f3d33` in all 60, no tie. Manifest `docs/data/arena-6829912-games-2026-10-31.json`. Games
  lasted 154.5 turns at the median, and 18 of 60 reached the limit.
- **Which code played** (logs in `replays/arena/v03/bronze/upload-03/checks/`):
  - `./gradlew cgValidate -Preplays=replays/arena/v03/bronze/upload-03/games`: 60/60, 10,047 turns,
    warnings exact 2907/2907, no mismatch;
  - `./gradlew cgAttribute -Preplays=replays/arena/v03/bronze/upload-03/games -Pagent=6829912
    -Pbot=file:replays/artifacts/d6266d41/Player.java`: SAME 60/60.
- **Opponent timeouts.** Two wins are opponents' timeouts on turns 223 (913807642) and 86 (913829899).
  The simulator's final scores match CG's in both (`docs/rules/inferred.md`, item 2).
- **P1.** `python -B -I tools/arena_compare.py --candidate v03 --selected v02 --out build/arena-compare-2026-10-31.json`
  (scale 1.30):
  - v03: mean 22.43 (SD 1.53), pooled rating 22.35 ± 0.26; the decision of 2026-10-30 stands;
  - M = 2.81 on 5 df;
  - v03's three ratings spread far beyond their standard errors (Cochran's Q 40.3 on 2 df);
  - details in `docs/arena-uploads.md`.
- The gap to the boss is about 4.7 points.
- **A test pinned the corpus size.** `visualizer/tests/catalog.test.mjs` expected 430 games, so every new
  upload broke it. It now takes the expected counts from the registry's manifests and pins only v03's
  first two uploads (60 games each, 37 and 39 wins).
- **Checks:**
  - `node --test visualizer/tests/catalog.test.mjs`: 6 passed;
  - `./gradlew test --tests cr.tools.CgViewReplayTest --tests cr.tools.ServeTest`: 4 passed, none
    skipped; the decoder read all 490 saved games;
  - `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`: 44 passed.

## 2026-10-31 — compact dashboard and replay layout

The user requested less empty space, smaller player cards with prominent nicknames, and desktop
views that fit the window with scrolling inside long blocks. Starting revision: `5f524ba`, clean tree.

Replay controls and the board now share the main column; the sidebar combines player cards,
commands, object details and recorded messages. Removed nickname repetition from the heading.
The canvas fits its available rectangle while preserving its actual aspect ratio for hit-testing;
its ResizeObserver is disconnected on disposal. Long commands/messages and the sidebar scroll
independently, including by keyboard without triggering replay shortcuts.

The desktop dashboard uses denser filters and three overview panels; tables scroll internally with
sticky headers. History now preserves each named panel's scroll position. Narrow screens retain
the document flow. Usage remains in `docs/visualizer.md`.

Validation:
- Browser: replay fits without document overflow at 1512×742, 1366×768, 1100×650 and 900×600.
  All three dashboard tabs fit at 1366×768 and 1100×650; 390×844 mobile has no horizontal page overflow.
- Seeking, keyboard steps, playback of a local replay and canvas object selection work after resizing.
  From page 2, game 39 of the project selection, next-game then back restored the table at scrollTop 400.
- Injected long nickname, TRAIN, summary and expanded warnings stayed in scrollable sidebar blocks.
  Missing-manifest and empty-filter views remained usable within the desktop window.
- `node --test visualizer/tests/catalog.test.mjs`: 6 passed; syntax checks for changed JavaScript and
  `git diff --check` passed. No backend, simulator or strength checks: executable game behavior and
  replay decoding are unchanged. Reused the already running local server; no server process started.

Follow-up: replaced playback/pause and previous/next-turn text with inline SVG icons and hover
shortcuts; accessible button names remain. Browser checks confirmed icon/state toggling, stepping,
the disabled start boundary and mobile width. JavaScript syntax and diff whitespace checks passed.


## 2026-10-31 — same-site final approach, compared with v03

Hypothesis: change only the final landing point at the already selected empty site, keeping its
construction turn and favoring the same predicted next site by integer walking turns. Candidate
`current?APPROACH=1`, control frozen v03; default remains 0. No referee or harness change.

One implementation was frozen before development, then tested under the P2 strategy gates on a
reserved fresh screen block. G0–G2 pass; G3 is skipped because the old source is absent. The task's
predeclared scope defers adoption/freeze of a new champion; selected/default v03 is unchanged.
No final-evaluation block or arena upload was run. Numerical results, exact commands, seeds,
source patch/inventory, timing, packaging check and caveats have one home:
`docs/data/exp-2026-10-31-approach/README.md` and its JSON evidence. Raw records, logs and source
archive are in `replays/bench/exp-2026-10-31-approach/`. The initial selected build directory was
restored after checking the candidate; retained artifact identities are in `docs/BOTS.md`.

Validation covers contact/build timing, the complete fixture mine-upgrade phase, safety fallbacks,
unchanged training and observations, default-v03 equivalence, snapshots and single-file merging.
Two local replays illustrate the approach. No simulation revalidation: the engine is unchanged.


## 2026-10-31 — preserved submissions moved out of replays

The user requested a clearer location for saved bot builds. Canonical retained submission sources
now live under the root `artifacts/`; their purpose and identity catalog remain in `docs/BOTS.md`.
The old-to-new mapping and exact per-file SHA-256/size inventory are recorded in
`docs/data/artifacts-layout-2026-10-31.json`. All five files moved byte-for-byte; no duplicate old
canonical directory remains. `.gitignore` continues to exclude these local source copies.

Updated active paths in the upload registry, procedure, project rules and experiment kept-copy
locators. The project instructions were regenerated with
`python -B -I .ai/sync.py sync . --source ../cg-agent-kit` at the same locked kit revision.
No executable code has a hardcoded dependency on the old directory: `cgAttribute` accepts a
`file:<path>` argument. Historical commands in this log, captured placement command/stdout records
and hashed experiment archives retain their original text; apply the mapping when replaying an old
command. Experiment-local build backups stay with their evidence.

Checks: all saved source hashes/sizes and current literal file references verified; edited JSON is
unchanged except for path replacements (allowing Git checkout line endings); historical placement
records unchanged; all five files ignored by Git; synchronizer `check` and `git diff --check` passed.
No build or games rerun: executable sources, artifact bytes and bot behavior are unchanged.


## 2026-10-31 — local games in the visualizer

Starting revision: `a6b3df70ce304bc5d4df9febd24072884fbcb2e4`; the preceding artifact-directory
move was uncommitted and retained. The user chose ordered bot-pair folders for local replay storage.
Usage, supported choices, seed range, persistence and runtime limitations have one home in
`docs/visualizer.md`, section «Локальные игры».

Added a local page with version selection, side swap, seed input, launch status, filtered history
and replay navigation. `Serve` delegates the allowlisted local APIs to `LocalGames`, using the
existing in-process `Referee`. Replays are written atomically and include effective parameters,
outcome/errors, frames and a server-runtime snapshot. History is read from saved files, skipping
frame payloads; malformed entries produce warnings. Changed compiled classes require restarting
the server. The snapshot describes the running build; it is not a source-code attestation.
POST validation bounds input and checks loopback authority/origin; one game runs at a time.

Validation (JDK 17, Windows):
- `node --test visualizer/tests/catalog.test.mjs visualizer/tests/local.test.mjs`: 10 passed.
- `./gradlew.bat test --tests cr.tools.CgViewReplayTest --tests cr.tools.ServeTest --tests cr.tools.LocalGamesTest`:
  replay decoding and existing server checks passed. Initial failures exposed JSON null metadata
  lost by response serialization (fixed) and Windows junction cleanup in the new test (fixed).
  Final `./gradlew.bat test --tests cr.tools.LocalGamesTest`: all 5 passed, none skipped. Across
  the final runs all 9 relevant Java checks passed, including real launch/save/reload and junction
  containment; no production changes followed those checks.
- Browser on a preview server: three actual launches on development seeds 110002/110003, including
  swapped sides. Saved replay playback, turn stepping, next-game/back, filtering and browser reload
  work. A completed launch does not redirect after leaving for the arena; launching outside the
  history filter makes the new game accessible. Arena loss-filter selection survives playback/back.
- Desktop 1366×768 and 1100×650 fit without document overflow; long history scrolls inside its
  panel. Mobile 390×844 has no horizontal overflow. Injected bot-error metadata is visible in the
  player; no browser console errors. JavaScript syntax and diff whitespace checks passed.
- Stopped the preview server and restarted the identified project server on port 8765; the local
  page lists all three saved matches after restart. Regenerated project instructions with the
  pinned kit; synchronizer `check` passed. The selected `build/submit/Player.java` hash is unchanged.

No bot, simulator, referee, version-parameter or submission-artifact changes. These were interface
and persistence checks, not strength measurements; no comparison series or arena upload was run.

Follow-up at the user's request: removed the UUID from new local replay filenames. UTC timestamp
with milliseconds and seed form the usual name; a short counter is added only on collision.
Temporary writes own a separate unique `.part` file, and publication never replaces an earlier
replay. Existing replay names remain usable. `./gradlew.bat test --tests cr.tools.LocalGamesTest`:
6 passed, including forced same-timestamp/same-seed preservation. Project sync/check passed.

Visualizer follow-up: investigated command-target lines for APPROACH. MOVE endpoints already used
recorded coordinates and the previous queen position; BUILD correctly used the site's centre.
The reached MOVE marker was hidden under the current queen. Added an outer dashed MOVE ring and
label drawn after units, plus a checkbox tooltip explaining frame timing and command targets.
Browser check on saved seed 24203, frame 37: line (1672,578) → (1681,545), exactly the recorded
command; the new ring remains visible around the queen at the endpoint. Previous frame is BUILD 5;
turn stepping and hiding all target marks work. Screenshot inspected; JavaScript syntax and diff
checks passed. No gameplay/backend changes or new matches.

The user queued broader queen-route planning as future work before committing the current tooling
changes. Scope and evaluation pointer are in `docs/PLAN.md`, phase 3; no route-planner code or
strategy changes were made for this decision.


## 2026-10-31 — show the next recorded command in the player

Starting from clean `6ef9309`, changed command targets to use the next frame's recorded commands,
originating from the current queen positions. This matches both local and CG frame semantics.
Cards retain the current frame's commands as primary text, with the upcoming commands below in
gray. At the initial state, current commands say «Начальное состояние»; at the last frame, the
upcoming row says «Конец записи» and targets disappear. Frame summaries stay with their recorded
state and explicitly name the completed turn. Targets default to enabled; the field background
is muted green with a subdued grid.

Browser validation compared actual canvas line endpoints and sidebar text with recorded data:
local seed 110002 at frames 0, 52, 53 and 250; CG game 913838775 at frames 0, 5 and 104. Checked
default visibility, checkbox hiding, stepping and playback/pause. Inspected the screenshot at
1512×820. JavaScript syntax and diff checks passed. No backend/build, simulation or new match run;
this changes only the presentation of existing recordings.

Follow-up checks cover both command rows on local seed 1000 at frames 0, 46, 47 and 250 and CG
game 913838775 frame 5. Inspected the lighter field at 1512×820; desktop 1100×650 still has no
document overflow and mobile 390×844 has no horizontal overflow.

Investigated the apparently distant MOVE in seed 1000 at frame 46: (613,361) targets (666,534),
about 181 units away, but the next recorded queen position is (629,418), about 59 units travelled,
and frame 48 builds tower #20. The target is APPROACH's 120-degree sample around site #20, not an
UNSTICK detour. Static geometry predicts 2 walking turns to the next site #4 instead of 3 from
the ordinary approach. MOVE coordinates specify direction/target, not the next frame's position.
`ApproachPlanner` explicitly simulates each candidate with `queenStep` and rejects it unless the
predicted landing touches only the original site; the sample coordinate itself need not be
reachable in one turn. Prediction includes speed, static-site collisions and rounding; nearby
unit/spawn guards limit, but do not remove, uncertainty from dynamic interactions.

Final palette: muted green field `#48634f`, subtle grid and light side-colored targets/labels.
Tower zones have translucent fills and thicker, higher-opacity outlines. All zone fills precede
all outlines, keeping overlapping zones from washing out another tower's boundary. Inspected
seed-1000 frame 46 with multiple intersecting ranges at 1512×820 and 1100×650; range toggling
works and the compact view has no page overflow. Syntax/diff checks passed.

Playback speeds now read 0.5x, 1x, 2x, 5x, 10x and 20x, retaining the old normal interval of
250 ms as 1x. The visible speed label is removed; its accessible name and tooltip remain.
During playback, onTurn/history updates are coalesced to 250 ms; pause, manual seeks and the final
frame report the exact turn immediately. Disposal suppresses notifications to preserve navigation.
Browser validation checked all six configured intervals, changes during playback, default 1x,
pause/seek/end URL accuracy and leaving an active player. A 250-turn replay at 20x made 13 history
updates and ended at the correct turn. No new games or backend changes; syntax/diff checks passed.

## 2026-10-31 — session visualizer changes isolated from strategy

Created `codex/visualizer-session` from master `2864cb8`. Extracted only the UI, local-game
API/persistence, tests and documentation from session commits `5dcd0fe` and `d162359`.
This includes independent current flags, canonical saved configurations and history filters,
enabled-only labels, and the predicted/recorded queen circles for MOVE. Bot, simulator,
referee and version parameter files are identical to master; strategy experiment records
were not transferred. The source queen-routing branch and its running server were preserved.

The sole adaptation is the server flag catalog: intersect the explicit UI allowlist with
parameters present in this build. Master provides UNSTICK and APPROACH; ROUTE is optional
and is not offered or accepted without the implementation. Historical recorded ROUTE flags
remain readable. UI tests cover stale URL/settings cleanup without rewriting saved history.

Validation in the isolated checkout:
- `node --test visualizer/tests/catalog.test.mjs visualizer/tests/local.test.mjs visualizer/tests/player.test.mjs`:
  18 passed.
- `./gradlew.bat test --tests cr.tools.CgViewReplayTest --tests cr.tools.ServeTest --tests cr.tools.LocalGamesTest`:
  12 passed, one optional corpus test skipped because this checkout has no ignored arena corpus.
- Offline pinned-kit sync/check and `git diff --check` passed. No bot strength comparison.


## 2026-11-01 — queen routes, steps 0 and 1: LegScan and the ROUTE=1 planner (P2 screen passed)

The task from the study session (`docs/data/study-2026-11-01-queen-routes/README.md`, plan steps 0
and 1): a tool that measures where the queen's walking turns go, its baselines and a second
calibration point of saved turns against S; then the `ROUTE=1` walk planner, screened under P2 on
a fresh block. Record with every number, the iteration sources' hashes and the commands:
`docs/data/exp-2026-11-01-route/README.md`. Worktree `claude/sweet-bartik-d9ce19` from `8fbe079`.

Step 0. `cr.tools.LegScan` (`./gradlew legScan`, `LegScanTest` on seeds 110135 and 110098): per
queen-game the walking turns, route legs with their excess over the straight bound, mid-leg slides,
failed final steps, abandoned legs by reason, builds on free sites touched on the way, the landing
potential, the ladder turn, the longest play() and the queenStep calls per turn (`Physics.calls`,
`Bot.queenSteps()`; frozen versions do not count). Baselines on 110000-110299: the v03 mirror walks
92.2 turns per game (excess 3.8, slides 4.9, abandoned 9.6 of which 4.7 own-tower top-ups, ladder
complete in 74 % at turn 156), v03 against v01 79.2. Calibration on block 2 (204000-205999):
`current?APPROACH=1` walks 90.9 against the mirror's 92.9 for its S 0.5245, about 0.012 S per saved
walking turn, as the study's 0.01 from seeds 10000-10199.

Step 1. `cr.bot.current.RoutePlanner` (`Params.ROUTE`, default 0; every version snapshot records
`ROUTE=0`; `MainBotTest.withDefaultsCurrentPlaysAsV03` stays green): the straight BUILD walk is
predicted with the sites only; when it slides (more turns than the bound), stalls, ends a turn on a
free wanted site, lands touching two sites, or when the next target is two or more turns from its
landing, a fan of 24 headings with 1-3 MOVEs plus the analytic landing point (the ring point at
r+33 nearest the next target, clamped to the reachable arc, verified by a simulated walk) is
searched; plans are ordered by turns to A, turns from the landing to B, the previous heading, the
search order; at most 3000 step predictions per turn, no clock, no Random; APPROACH's unit guards
and a contested-site guard switch the landing part off. With the flag on it replaces UNSTICK and
APPROACH. `CGRunner.warmUp` plays a blocked walk and a landing case on turn 0. Tests: the two stall
fixtures (7 and 9 turns against UNSTICK's 7 and 10 in the sites-only replay), a captured slide
fixture (seed 110135 turn 144: v03 10 turns, ROUTE at most 7), the APPROACH landing case under the
real rules (B built a turn earlier), the flee rule, determinism, clock independence.

Development (seeds 10000-10499, 500 maps, two of three allowed iterations): iteration 1 treated
own towers below 700 HP and unfinished mines as sites to route around and lacked the slide trigger:
S 0.5315 (CI 0.4994-0.5636), top-ups 4.4 → 1.1 per game. Iteration 2 (the screened code) walks past
own towers and mines as v03 does and searches whenever the straight walk loses a turn to a
collision: S 0.5825 (CI 0.5502-0.6148), walking 91.6 → 86.0, excess 3.55 → 1.40, slides 4.5 → 1.9,
ladder complete at turn 149.5 instead of 161.3, no failure.

Screen (block k=4, seeds 208000-209999; block 3 had been played by the unmerged branch
`codex/queen-routing` for its own ROUTE candidate, S 0.5179, so it is recorded as used in the ledger):
direct S = 0.5813 (CI 0.5657-0.5968), 2203/244/1553, 1990 of 2000 maps changed, no candidate failure
(max 26.0 ms at 8 threads); `boss` on the first 500 maps: Δ = -0.0010 (CI -0.0100..+0.0080), candidate
982/0/18 against v03's 983/0/17. G0, G1 and G2 pass; G3 skipped (`old` absent). LegScan on
the block: walking 92.75 → 86.86, excess 3.63 → 1.47, slides 4.65 → 1.97, abandoned legs
9.58 → 7.17 (top-ups 4.58 → 2.96), free-site builds on the way 0.24 → 0.03, ladder complete
69.9 % at 162.8 → 73.3 % at 151.6; mean 41.7, max 1703 queenStep calls per turn (cap 3000). The
LegScan replay of the same games measured one play() of 53.15 ms at 8 threads where the bench
series, which is the G0 evidence, had at most 26.0 ms: host scheduling, not the planner's work
(the v03 arm shows the same spread, 1.9 against 39 ms for identical code on the baselines).
Exchange rate on this block: 0.0138 S per saved walking turn.

Decision: none here. The flag stays off by default; the user decides adoption (freeze as the local
champion), the final-evaluation block and an upload. Nothing was uploaded or committed. Checks run
at the end (README, "Final checks"): `./gradlew test` 127 tests, 0 failures, 2 skipped (arena
replays absent in the worktree), 35 s; `./gradlew submit -Pparams=ROUTE=1` 90 050 chars, build id
5c7e3bf0, config id 347b908d; `procCheck -Pparams=ROUTE=1 -Popp=v03 -Pgames=20 -PseedBase=150000`
sameAsCurrent 40/40, no timeouts, maxLater 3.0 ms (a packaging smoke check, not the pre-upload
100-game pair); the kit synchronizer after the `.ai/project.md` rows for `legScan` and the
local-match flags. The first test run hung for two hours: `LocalGamesTest` expected a 400 on a
`ROUTE` launch request, the server (which advertises every flag whose parameter exists) accepted
it and ran the test's throwing stub, and the unanswered request had no client timeout. The test
now expects the three-flag catalog and times requests out after 60 s; `docs/visualizer.md` lists
the flag. `Bot.java`'s javadoc link `{@link cr.core.Physics#queenStep}` broke the merger's ban on
qualified project names (`FileMergerTest`, `submit`) and is plain text now. Neither fix touches
the planner or the screened games.
Raw reports of this experiment were copied from the worktree to the main checkout's
`replays/bench/exp-2026-11-01-route/`; the worktree's `refs/` holds a byte-identical copy of
`Boss.java` for the `boss` spec. Hygiene learned: two single games played beside a bench series
gave 157-214 ms "slow" turns at 8 threads (both arms); the screen ran alone and had none.


## 2026-11-01 — final evaluation of `current?ROUTE=1` against v03 on block 404000-405999 (P2)

The user asked for the P2 final evaluation after the screen and the final checks. Block k=2 of the
evaluation pool (seeds 404000-405999) was reserved in the ledger and the plan written in the README
before the runs; the bot sources were byte-identical to the screened snapshot (`source-it2/`,
`replays/bench/exp-2026-11-01-route/revision-eval.txt`). Series as in the screen with
`-PseedBase=404000`: direct and the v03 mirror on 2000 maps (8 threads, 50 ms), both arms against
`boss` on the first 500 maps (3 threads, 200 ms), LegScan on both arms; `eval-run.log` and the
reports `eval-*.json` in the raw directory (copied to the main checkout), summaries `eval-g1.json`,
`eval-g2.json` and the LegScan summaries beside the README (section "Final evaluation").

Result: S = 0.5856 (95 % CI 0.5701-0.6011), 2216/253/1531, 1987 of 2000 maps changed, no candidate
failure (max 35.4 ms per move at 8 threads); against `boss` candidate 0.9900, control 0.9875,
Δ = +0.0025 (CI -0.0065..+0.0115). G0, G1 and G2 pass; G3 skipped (`old` absent). The upload
recommendation S ≥ 0.60 is not met by the point estimate (the interval reaches 0.6011). The
screen's 0.5813 is replicated. LegScan repeats the screen's walking numbers: walking 92.85 → 86.71
turns per game, excess 3.72 → 1.50, slides 4.76 → 2.07, top-ups 4.67 → 3.01, ladder 69.1 % at
160.9 → 73.8 % at 150.8; 42.2 queenStep calls per turn, max 1614. Both `boss` arms show one move
near 58 ms (the external process's host noise, v03 included). Rating terms: about +0.46 points
head-to-head against v03.

Decision: none here. The candidate passes the P2 gates against the selected version and stays
below the recommendation line, as v02 did; adoption and an upload (then `submit`, both 100-game
`procCheck` runs and P1) are the user's call. Nothing uploaded or committed. The ledger marks
block 2 of the evaluation pool used; the next evaluation block is k=3.

Upload preparation (the user's decision to upload despite S < 0.60, 2026-11-01): `./gradlew submit
-Pparams=ROUTE=1` → `build/submit/Player.java` sha256
da914c4c6e0ddae56bdb3f6daba9becff066b5cae69aaa97e441c01656cfb368, 90,050 chars, build id 5c7e3bf0,
config id 347b908d, kept as `artifacts/da914c4c/Player.java` in the worktree and the main checkout.
`procCheck -Popp=v01 -Pgames=100 -PseedBase=5000`: 200 games, 180 wins, sameAsCurrent 200/200,
0 timeouts, max first turn 262.6 ms, later 4.0 ms. `-Popp=boss -Pgames=100 -PseedBase=6000`: 200 games,
199 wins, sameAsCurrent 198/200, 0 artifact timeouts, max first turn 522.4 ms, later 8.1 ms; seed 6045
was not compared on either seat because the `boss` process was killed by a timeout (turn 4 in the
in-process run, turn 0 in the artifact run); the quiet rerun of that seed (`-Pgames=1 -PseedBase=6045`)
gave 2/2 identical, no timeouts, max first turn 184.0 ms. Both results are kept
(`replays/bench/exp-2026-11-01-route/upload-prep.log`). The live entry was saved before the paste:
`tools/arena_snapshot.py 6829912` → `docs/data/arena-6829912-presubmit-2026-11-01.json` (score 23.37,
rank 232 of 2288, placement complete) and `tools/arena_games.py 6829912` (60 games, all marker
cd1f3d33, the list merged into the manifest). Next: the user pastes the artifact; the new agent is
registered as pending in `docs/data/arena-uploads.json` and its placement followed under P1.

Committed on the user's request: eb5dd14 (code, tests and records of this entry). A clean export
of that commit (`git archive`) rebuilt the artifact byte for byte with `submit -Pparams=ROUTE=1`;
the artifact's row in `docs/BOTS.md` (Artifacts) follows in the next commit.

The user pasted the artifact on 2026-11-01 23:56 local: agent 6836411 (submission 41613612) replaced
6829912. Registered as `route` upload 1 (pending) in `docs/data/arena-uploads.json`; the discovery
snapshot of 23:59 (18 %, 11 games, 9 wins) had gone to the unknown folder and was moved into
`replays/arena/route/bronze/upload-01/snapshots`. The placement is followed by `arena_snapshot.py`
every 3 minutes (every minute from 85 %) into `docs/data/arena-6836411-placement-2026-11-01.json`
until the first snapshot with percentage 100 and inProgress false (the placement score under P1).

Placement complete on 2026-11-02 at 01:30 local (the first snapshot with percentage 100 and inProgress
false; the leaderboard's updateTime 01:29:48; 93 minutes after the paste; 42 snapshots in
`replays/arena/route/bronze/upload-01/snapshots`, log `placement-watch.log` beside them): score 22.89,
rank 263 of 2288, 60 games, 39 wins (seat 0: 20 of 30, seat 1: 19 of 30). `arena_games.py 6836411`:
60 games fetched, marker 5c7e3bf0 in all 60, no failures (`docs/data/arena-6836411-games-2026-11-01.json`,
raw `replays/arena/route/bronze/upload-01/games`). Three wins are opponents' removals ("failed to
provide 2 lines of output in time"): 915286015 on the first turn, 915286510 on turn 94, 915313197 on
turn 97 (counted like v03's games: (frames − 1) / 2). `cgAttribute
-Preplays=replays/arena/route/bronze/upload-01/games -Pagent=6836411
-Pbot=file:artifacts/da914c4c/Player.java`: SAME 60/60, 9,770 turns compared and validated, markers
{5c7e3bf0: 60} (`cgAttribute.log` beside the games). `arena_compare.py --candidate route --selected v03
--out replays/arena/route/bronze/upload-01/arena-compare.json`: scale 1.40 on nine uploads; route
22.39 ± 0.43 (opponents' median 21.33), v03 pooled 22.37 ± 0.27, mean 22.43, M 2.81 unchanged; verdict
"upload the candidate again" (22.89 against the mean, 2M = 5.62). Recorded: the registry (placement,
attribution), `docs/BOTS.md` (Uploads, Artifacts), STATUS, SUMMARY, `docs/local-screen.md` (the P1 row),
PLAN and a section in `docs/arena-uploads.md`. The raw upload folder and the experiment's bench
folder are copied to the main checkout. Open: the second upload of the same artifact (the user),
then the P1 verdict; adoption.

The user pasted the same artifact again on 2026-11-02 at 01:42 local (the second upload under P1):
agent 6836589 (submission 41613945) replaced 6836411, whose placement was complete and saved.
Registered as `route` upload 2 (pending) in `docs/data/arena-uploads.json`; the discovery snapshot of
01:43 (10 %, 10 games, 7 wins) had gone to the unknown folder and was moved into
`replays/arena/route/bronze/upload-02/snapshots`. The placement is followed as before into
`docs/data/arena-6836589-placement-2026-11-02.json` (log `placement-watch.log` beside the snapshots).

Placement of upload 2 complete on 2026-11-02 at 03:39 local (the first snapshot with percentage 100 and
inProgress false; the leaderboard's updateTime 03:39:27; 117 minutes after the paste; 55 snapshots in
`replays/arena/route/bronze/upload-02/snapshots`, log `placement-watch.log` beside them): score 22.10,
rank 302 of 2288, 60 games, 40 wins (seat 0: 23 of 31, seat 1: 17 of 29), no opponent removed, no tie.
`arena_games.py 6836589`: 60 games fetched, marker 5c7e3bf0 in all 60, no failures
(`docs/data/arena-6836589-games-2026-11-02.json`, raw `replays/arena/route/bronze/upload-02/games`).
`cgAttribute -Preplays=replays/arena/route/bronze/upload-02/games -Pagent=6836589
-Pbot=file:artifacts/da914c4c/Player.java`: SAME 60/60, 9,492 turns compared and validated, markers
{5c7e3bf0: 60} (`cgAttribute.log` beside the games). `arena_compare.py --candidate route --selected v03
--out replays/arena/route/bronze/upload-02/arena-compare.json`: scale 1.51 on ten uploads; route mean
22.50, SD 0.56, pooled rating 22.01 ± 0.33 (opponents' median 20.99 in upload 2); v03 mean 22.43, pooled
22.39 ± 0.28; M 2.61 (pooled SD 1.30, 6 df); verdict "not clearly worse" (mean +0.07 against −M = −2.61;
performance −0.38 against −2 SE = −0.87). Under P1 `route` is the selected version from 2026-11-02; a
frozen version with ROUTE on by default is the user's decision, and `current` keeps ROUTE=0. Recorded:
the registry (placement, attribution), `docs/BOTS.md` (Uploads, Artifacts, the flag row, the v03 row),
STATUS, SUMMARY, `docs/local-screen.md` (the P1 row), PLAN and a section in `docs/arena-uploads.md`.
The raw upload folder is copied to the main checkout.

v04 frozen on 2026-11-02 by the user's decision ("Да, заморозить v04"): `cr.bot.v04.GreedyBot` is a copy of
`cr.bot.current.MainBot` at b740cf0 (bot sources unchanged since eb5dd14) with its own copies of
`RoutePlanner` and `ApproachPlanner`, the package and class renamed, `name()` "v04" and, as in the other
frozen copies, no step-prediction counter (LegScan reports frozen versions as not counted);
`versions/v04.properties` = v03's snapshot with ROUTE=1. `Params.ROUTE` defaults to 1, so `current` plays
as v04; v01-v03 keep ROUTE=0 in their snapshots. `BotFactory` and the local-games catalog (`LocalGames`,
default opponent v04, `visualizer/local.js` fallback) know v04. Tests: `MainBotTest.withDefaultsCurrentPlaysAsV04`
(seeds 100000, 110135, 110098, 10394, 208000-208002, 404000, both seats against v01 and v02),
`withV03ParamsCurrentPlaysAsV03` now runs `current?ROUTE=0`; `BotFactoryTest` pins the live defaults to v04's
snapshot; the UNSTICK, APPROACH, stall-scan, attribution and seed-110098 LegScan tests pin ROUTE=0, since the
planner replaces UNSTICK and APPROACH; `LocalGamesTest` expects six bots and `route1` in the default
configuration ids. `./gradlew test`: 128 tests, 0 failures, 1 skipped (the arena-replay attribution test, replays absent in the worktree), 27 s; node tests 18/18. `cgAttribute -Preplays=replays/arena/route/bronze/upload-02/games
-Pagent=6836589 -Pbot=v04`: SAME 60/60, 9,492 turns (`cgAttribute-v04.log` beside the games). `./gradlew submit`
with empty PARAMS: sha256 ba2f8c2af1e924fca206cee2f346454cece899da11062b7dd2011e6cd482f362, 90,239 chars, build 30ba51a6, config 4f4efb38; `procCheck -Popp=v03 -Pgames=20
-PseedBase=7000`: 40/40 identical to current, 20 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 215.4 ms, later 2.5 ms. The artifact is a packaging check only: not kept and not uploaded, the live
arena entry stays `route` upload 2 (`da914c4c`, the same play). Records: `docs/BOTS.md` (versions, flags,
artifacts), STATUS, SUMMARY, PLAN, `docs/local-screen.md` (roles), `docs/arena-uploads.md`, `docs/visualizer.md`,
`docs/bot-architecture.html` (ROUTE section, versions table, file map), `.ai/project.md` (local-match row) with
the kit sync.

Study 2026-11-03 (`docs/data/study-2026-11-03-losses/`), on the user's choice to start with "how we
lose" before changing the build strategy. `CgStudy` extended (new trailing columns: deathTurn,
firstTowerLost, towerTopUps, mineUpgrades, opening, thp_<t>; `CgStudyTest` pins them on game 915331914,
skipped without the replay). `cgStudy` over v04's 120 placement games, v03's 180, cg2 and legend-top
(526 games, `replays/study/features-2026-11-03.csv`, sha256 dd108fba…, copied to the main checkout; the
first run failed because MSYS rewrote the absolute Windows paths in `-Preplays`, relative paths work).
`losses.py` → `losses.txt`. Findings: 41 losses = 26 killed queens (median turn 114, 7 before 80) + 15
HP races at the limit; giants in 12 games (58 % won) are not the bulk; we open with the barracks on turn 1
while every stronger group and 80 % of the opponents who beat us open with a mine and build the barracks
on turn 7-13; our income stays 3 (they hold 4-7), we hold 6-7 towers at 700 HP (they hold 4 at about
450) and spend 57 of 100 queen turns on top-ups (Legend 23, the opponents who beat us 39); 7 waves per
game against 9-10. Top-ups show no benefit (more in losses than in wins). Candidates: opening order,
income target 5-6, tower cap 4-5 with a 400-500 top-up threshold, as flags screened against v04. The
route steps 2-4 are parked. Records: PLAN, STATUS, SUMMARY, `.ai/project.md` (cgStudy row, kit sync).

## 2026-11-03 — strategy parameters: two mines before the barracks pass the P2 screen (S 0.7645 against v04)

On the user's go-ahead after the loss study. `Params` gained `MINES_FIRST` (own mines before the knight
barracks), `TOWER_HP` (the top-up threshold, the former literal 700) and `UPGRADE` (own mines below their
maximum as targets while the income is below its target); the ladder's target choice is one function,
`MainBot.nextSite`, shared with the planner's next-target prediction (`ApproachPlanner.nextTarget`), so
ROUTE's landing follows the new order. Every version snapshot records the new fields at the frozen values;
`current` at its defaults plays as v04 (`MainBotTest`, `BotFactoryTest`); `StrategyParamsTest` pins the
opening order, the no-gold fallback, the threshold and the upgrade target.

Development sweep on seeds 10000-10499 against v04 (68 arms, `BenchmarkRunner` directly, one JVM per arm;
`docs/data/exp-2026-11-03-mines-first/dev-sweep.txt`, raw `replays/bench/dev-strategy-2026-11-03/`):
MINES_FIRST 1 → 0.684, 2 → 0.760, 3 → 0.673; INCOME_TARGET 5 → 0.290 (with mines first 0.54); TOWER_HP 400
→ 0.293, 600 → 0.616, 800 → 0.474; MAX_TOWERS 4/6 → 0.40/0.52 (with mines first 0.65); UPGRADE no effect;
INCOME_TARGET 2-4 and MAX_TOWERS 8/12 indistinguishable once the opening is fixed. The candidate is one
parameter, `current?MINES_FIRST=2`.

P2 screen, block 5 (210000-211999, reserved before the first game; hypothesis and commands in the README
before the run): G1 S 0.7645 (CI 0.7492-0.7798), W/D/L 3018/80/902, both seats won, 2000 of 2000 maps
changed; G2 Δ +0.0100 against `boss` (1.000 against 0.990; games 80 turns against 126); G3 skipped. G0:
the first direct run had 5 slow turns of the candidate in 4 games on consecutive maps 210258-210265 (one
moment of wall-clock time, mean turn 0.04 ms); the 8 maps on one thread gave a 5.6 ms maximum and the
quiet repeat of the whole series (identical outcomes in all 4000 games) 0 slow turns of the candidate and
4 of v04 (unchanged code, max 70.6 ms), the mirror one 51.9 ms turn of v04: host scheduling at 8 threads,
recorded as such (`g1.json`, `g1-rerun.json`). Verdict: pass; the local champion is
`current?MINES_FIRST=2`. The final evaluation on evaluation block 3, the freeze as v05 and the upload are
the user's decisions. Raw reports copied to the main checkout's `replays/bench/`. Records: README, ledger,
STATUS, PLAN, SUMMARY, BOTS (flags, current).

## 2026-11-03 — final evaluation of MINES_FIRST=2 against v04: S 0.7556, upload recommended

On the user's "yes, evaluate". Evaluation block 3 (406000-407999) reserved in the ledger and the plan
written in the README before the run; the screen's commands with `-PseedBase=406000`
(`replays/bench/exp-2026-11-03-mines-first-eval/eval.sh`, copied to the main checkout). G0: no failure in
any series (longest candidate turn 27.4 ms; neither the mirror nor the control had a slow turn this time).
G1: S 0.7556 (CI 0.7401-0.7712), W/D/L 2974/97/929, by seat 1532/51/417 and 1442/46/512, 2000 of 2000 maps
changed. G2: 0.999 against 0.984, Δ +0.0150 (CI +0.0057..+0.0243); games against `boss` 64 turns against
v04's 93. G3 skipped. The recommendation S ≥ 0.60 is met. Summaries `eval-g1.json`, `eval-g2.json`.
Verdict: pass; the freeze as v05 and the upload are the user's decisions. Records: README, ledger, STATUS,
PLAN, SUMMARY, BOTS (flags).

v05 frozen on 2026-11-03 by the user's decision ("Да, заморозить v05"): `cr.bot.v05.GreedyBot` is a copy of
`cr.bot.current.MainBot` at 3413374 (bot sources as at e4938ea) with its own `RoutePlanner` and
`ApproachPlanner` (the `MainBot.nextSite` reference renamed), the package and class renamed, `name()` "v05"
and no step-prediction counter; `versions/v05.properties` = v04's snapshot with MINES_FIRST=2.
`Params.MINES_FIRST` defaults to 2, so `current` plays as v05; v01-v04 keep MINES_FIRST=0 in their snapshots.
`BotFactory` and the local-games catalog (`LocalGames`, default opponent v05, `visualizer/local.js` fallback)
know v05. Tests: `MainBotTest.withDefaultsCurrentPlaysAsV05` (seeds 100000, 110135, 10394, 10477,
210000-210002, 406000, both seats against v01 and v02), `withV04ParamsCurrentPlaysAsV04` now runs
`current?MINES_FIRST=0`; `BotFactoryTest` pins the live defaults to v05's snapshot and v05 = v04 with
MINES_FIRST=2; the route, approach, stall-scan, attribution, LegScan-110098 and strategy tests pin
MINES_FIRST=0 where their fixtures assume the barracks first (the landing case of `RoutePlannerTest`
changes with two mines first: B is built on turn 9, not 7); `LocalGamesTest` expects seven bots and v05 as
the default opponent. Full suite: 134 tests, 0 failures, 1 skipped (the arena replay of CgStudyTest is absent in the worktree). `./gradlew submit` with empty PARAMS: sha256 13b3ec0c6cd324befd0e166c2c39a890bff1c7b2516a47654d59d01a7a129dc1, 92,522 chars, build
cc541951 (turn 0: `TRAIN 12 12 5 4 1 9 5 1`), config 8ec996a5; kept as `artifacts/13b3ec0c/Player.java` in the main
checkout. `procCheck -Popp=v01 -Pgames=100 -PseedBase=5000`: 200/200 identical to current, 198 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 235.1 ms, later 2.2 ms; `-Popp=boss -Pgames=100
-PseedBase=6000`: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 376.2 ms, later 3.8 ms (logs `replays/bench/exp-2026-11-03-mines-first-eval/proccheck-v05-*.log`).
Not uploaded: the paste into the IDE and the two P1 placements are the user's. Records: `docs/BOTS.md`
(versions, flags, artifacts), STATUS, SUMMARY, PLAN, `docs/local-screen.md` (control v05),
`docs/visualizer.md`, `docs/bot-architecture.html` (ladder, pseudo-code, versions table, file map),
`.ai/project.md` (local-match row) with the kit sync.

## 2026-11-03 — v05 upload 1: 23.93, the project's best placement; P1 needs the second upload

The user pasted `13b3ec0c` at 14:40 local ("Вставил, следи"): agent 6841317 (submission 41622286) replaced
6836589 (`route` upload 2, complete and saved). The discovery snapshot by nickname (14:42: 25 %, 13 wins
of 16) landed in the unknown folder and was moved into `replays/arena/v05/bronze/upload-01/snapshots`;
registered as v05 upload 1 (pending) in `docs/data/arena-uploads.json`; watched every 3 minutes
(`placement-watch.sh` and `.log` beside the snapshots). Complete at 14:51 (leaderboard updateTime
14:51:21, 11 minutes after the paste; 5 snapshots): score 23.93, local rank 144 of 2289 (global 801),
60 games, 41 wins (seat 0: 22 of 31, seat 1: 19 of 29), one opponent removed by the referee (916020840),
no tie; `docs/data/arena-6841317-placement-2026-11-03.json`. `arena_games.py 6841317`: 60 games, marker
cc541951 in all 60, no failures (`docs/data/arena-6841317-games-2026-11-03.json`, raw
`replays/arena/v05/bronze/upload-01/games`). `cgAttribute -Pbot=file:artifacts/13b3ec0c/Player.java`:
SAME 60/60, 9,613 turns compared and validated (`cgAttribute.log` beside the games). `arena_compare.py
--candidate v05 --selected route` (`arena-compare.json` beside the games): scale 1.63 on eleven uploads;
v05 23.93, rating 23.60 ± 0.51, opponents' median 22.70; `route` mean 22.50, rating 22.06 ± 0.35; M 2.61
(6 df); verdict "upload the candidate again" (+1.43 on the mean, −2M = −5.21). Raw arena files copied to
the main checkout. Records: `docs/arena-uploads.md` (new section), BOTS (Uploads, Artifacts), STATUS,
SUMMARY, PLAN, `docs/local-screen.md` (v05 row).

## 2026-11-03 — v05 upload 2: 26.47, rank 27; P1 verdict: v05 selected

The user pasted `13b3ec0c` again at 14:58 ("Вставил, следи"): agent 6841407 (submission 41622456)
replaced 6841317 (complete and saved). Discovery snapshot by nickname at 14:58 (15 %, 11 of 12) moved
into `replays/arena/v05/bronze/upload-02/snapshots`; registered as v05 upload 2 (pending); watched every
3 minutes (`placement-watch.sh`, `.log`). Complete at 15:11 (leaderboard updateTime 15:10:25, 13 minutes
after the paste; 5 snapshots): score 26.47, local rank 27 of 2289 (global 684), 60 games, 41 wins (seat 0:
25 of 37, seat 1: 16 of 23), no opponent removed, no tie, `eligibleForPromotion` false;
`docs/data/arena-6841407-placement-2026-11-03.json`. `arena_games.py 6841407`: 60 games, marker cc541951 in
all 60, no failures (`docs/data/arena-6841407-games-2026-11-03.json`). `cgAttribute
-Pbot=file:artifacts/13b3ec0c/Player.java`: SAME 60/60, 8,897 turns (`cgAttribute.log` beside the games).
`arena_compare.py --candidate v05 --selected route` (`arena-compare.json` beside the games): scale 1.61 on
twelve uploads; v05 placements 23.93 and 26.47, mean 25.20, SD 1.80, rating 24.90 ± 0.37 (opponents'
median 25.17 in upload 2); `route` mean 22.50, rating 22.05 ± 0.35; M 2.77 (7 df); verdict "not clearly
worse" (mean +2.70 against −M −2.77; rating +2.86 against −2 SE −1.01): v05 is the selected version. Raw
arena files copied to the main checkout. Records: `docs/arena-uploads.md` (new section), the registry
(attributions), BOTS (versions, uploads, artifacts), STATUS, SUMMARY, PLAN, `docs/local-screen.md`
(roles, v05 row), `docs/bot-architecture.html` (versions table).

Architecture page brought to v05 (the user's request, 2026-11-03): `docs/bot-architecture.html` now
states the two-mines opening in the lead, the flow and the "easy to miss" list (the barracks on turn 9,
the first tower on turn 14: medians of v05's 120 arena games, `./gradlew cgStudy` over
`replays/arena/v05/bronze/upload-0*/games` into `replays/study/features-v05-2026-11-03.csv`, gitignored,
copied to the main checkout), the pseudo-code at v05's values, the ROUTE test name, two new gaps
(economy at income 3, 55 of 100 queen turns on top-ups) and a new section "Направления": seven
directions with the data behind each and how to test it (army, economy for the arena field, thinner
towers, queen safety, the HP race, archers/giants, route steps 2-4), plus v05's arena profile against its
opponents. Checked in the built-in browser (renders, no console errors).

## 2026-11-03 — Army direction: a second knight barracks with synchronous waves; P2 screen passed, S 0.547 against v05

The user chose the first direction of `docs/bot-architecture.html` (Направления): the army. Reasoning
recorded in `docs/data/exp-2026-11-03-army/README.md`: at income 3 the gold bounds the waves (one of 4
knights every 27 turns), so a second barracks adds concentration, not waves. Code (worktree
`claude/bot-improvement-direction-4fe687`, from 2fda634): `Params.BARRACKS_AFTER_TOWERS` (own towers
before the second and later barracks), `TRAIN_SYNC` (TRAIN only when every own barracks is idle and
affordable), `BARRACKS_FORWARD` (the second barracks' site nearest to a point along home → the enemy's
corner); `MainBot.wantsBarracks` shared by the touch rule, `nextSite` (now takes the home corner),
`ApproachPlanner.nextTarget` and `RoutePlanner.diverts`; `MainBot.train` keeps every wave within
`Const.CREEP_LIMIT` (always on; v05's fixture games unchanged). The five version snapshots record the
new fields at the frozen behaviour (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults` demands
every field). Tests: `StrategyParamsTest` (the gate, the forward site, the sync, the cap); the suite
green, including the identity tests of v01-v05.

Development sweep against v05 (`replays/bench/dev-army-2026-11-03/`, 500 maps per arm, both seats, one JVM
per arm, 8 threads, 50 ms; `sweep-a/b/c.sh` and `dev-sweep.txt` beside the README): stage A on seeds
10000-10499, 12 arms: the defaults and TRAIN_SYNC alone 0.500 with 0 maps changed; BARRACKS=2 with the
gate at 0 / 2 / 4 / 6 / 8 towers, sync off → 0.315 / 0.401 / 0.458 / 0.469 / 0.488, sync on → 0.357 /
0.492 / 0.548 / 0.468 / 0.477. Stage B around gate 4 with sync: MAX_TOWERS=6 0.574, 10 0.546,
INCOME_TARGET=4 0.539, forward 30 / 50 / 70 → 0.531 / 0.541 / 0.517, gate 3 0.511, gate 5 0.489,
BARRACKS=3 0.455. Stage C, the arms above 0.53 on seeds 11000-11499: gate 4 + sync 0.508 (the stage A
winner did not replicate), with MAX_TOWERS=6 0.542, INCOME_TARGET=4 0.510, forward 50 0.486, gate 3
0.508; the v05 mirror there 0.500. Chosen on the two blocks: `BARRACKS=2,BARRACKS_AFTER_TOWERS=4,
TRAIN_SYNC=1,MAX_TOWERS=6`. Slow turns in the sweep (1-2 per arm in three arms, with v05's own) are
host scheduling at 8 threads, as in the sweep of the opening.

P2 screen on block 6 (212000-213999, reserved in the ledger before the run; `screen.sh`): G1 S 0.5473
(CI 0.5331-0.5614), W/D/L 2113/152/1735, seats 1281/72/647 and 832/80/1088, 1599 of 2000 maps changed
against the mirror; G2 both arms 0.9985 against `boss` with 0 maps changed (the boss games end on turn 39,
before the fourth tower), Δ 0.0000; G3 skipped. G0: 4 slow candidate turns in the first direct run (maps
213203-213282, max 86 ms) and 1 of v05; `recheck.sh`: the 90 maps 213200-213289 on one thread with no
slow turn (max 8.8 ms) and a quiet repeat of the direct series with identical outcomes in all 4000 games
(0 candidate slow turns, 2 of v05) → pass. Summaries `g1.json`, `g1-rerun.json`, `g2.json`. Mean game
195 turns (the mirror 201). About +0.3 rating points head-to-head at scale 1.61: below the upload
recommendation (S ≥ 0.60). Records: README, ledger, BOTS (flags), STATUS, SUMMARY, PLAN. Not done: the
final evaluation (block 4, 408000-409999), the freeze, `submit`, `procCheck`, the architecture page (the
selected version is unchanged). Raw reports copied to the main checkout's `replays/bench/`. The worktree
has `refs/referee-csj/config/level4/Boss.java` (sha256 a1a3f043…) and `build/gson.jar` copied in for the
bench. After the screen two comment-only edits: the class comment of `MainBot` and the `Params` comment of
`BARRACKS_AFTER_TOWERS` made ASCII (`FileMergerTest` rejects a non-ASCII character in the bot sources); the
reports' diff sha256 names the code before them, the compiled behaviour is the same. Full `./gradlew test` green.

## 2026-11-03 — v05 upload 3 (25.77) and the army candidate's final evaluation (S 0.548, below the 0.60 recommendation)

The user ("let's continue; I ran one more series"): the discovery snapshot by pseudo
(`arena_snapshot.py 6841407 aabzac bronze`) found the live row at agent 6841579 (creationTime 15:30:55,
score last updated 15:41:22), placement complete at the first snapshot (15:56: percentage 100, inProgress
false, score 25.77, local rank 53/2289, 60 games, 43 wins). Registered as v05 upload 3 (pending, then
completed) in `docs/data/arena-uploads.json`; the raw responses moved from `replays/arena/unknown/bronze/
agent-6841579/snapshots` into `replays/arena/v05/bronze/upload-03/snapshots`, the summary renamed to
`docs/data/arena-6841579-placement-2026-11-03.json` and refreshed by a second snapshot. `arena_games.py
6841579`: 60 games, marker cc541951 in all 60, 0 failures, 0 ties (`docs/data/arena-6841579-games-2026-11-03.json`).
`cgAttribute -Preplays=replays/arena/v05/bronze/upload-03/games -Pagent=6841579
-Pbot=file:artifacts/13b3ec0c/Player.java`: SAME 60/60, 8,962 turns compared and validated (`cgAttribute.log`
beside the games; the kept copy was copied into the worktree from the main checkout). `arena_compare.py
--candidate v05 --selected route` (`arena-compare.json` beside the games): scale 1.72 on thirteen uploads;
v05 placements 23.93, 26.47, 25.77, mean 25.39, SD 1.31, rating 24.96 ± 0.32 (opponents' median 23.33 in
upload 3, 71.7 % wins, seats 22 of 30 and 21 of 30, one opponent removed: 916040203); route 22.50 / 22.09 ±
0.37; M 2.61 (8 df); verdict unchanged, "not clearly worse" (mean +2.89 against −2.61; rating +2.87 against
−0.97). Records: `docs/arena-uploads.md` (new section), the registry, BOTS (Uploads, Artifacts), STATUS,
SUMMARY, `docs/local-screen.md` (v05 row), the architecture page (the two v05 numbers). Raw arena files
copied to the main checkout.

Final evaluation of the army candidate (the user's "let's continue"), evaluation block 4 (408000-409999)
reserved in the ledger before the run, `eval.sh` (the screen's commands with `-PseedBase=408000`, reports
in `replays/bench/exp-2026-11-03-army-eval/`, run while the arena download ran in Python, before
`cgAttribute`): G0 clean in every series (max candidate turn 30.6 ms); G1 S 0.5480 (CI 0.5327-0.5633),
W/D/L 2144/96/1760, seats 1243/47/710 and 901/49/1050, 1948 of 2000 maps changed; G2 0.9970 against 0.9980,
Δ −0.0010 (CI −0.0030..+0.0010), 243 of 500 maps changed (the boss games last 77 turns on this block, so the
second barracks acts); G3 skipped. Summaries `eval-g1.json`, `eval-g2.json`. S < 0.60: the upload
recommendation is not met; the candidate stays the local champion, the freeze and any upload are the
user's decisions. Records: README (evaluation section), ledger (verdict), STATUS, SUMMARY, PLAN unchanged
(the item already says the decisions are the user's), `docs/local-screen.md` (a candidate row). Raw
reports copied to the main checkout's `replays/bench/`.

Decision (the user, 2026-11-03, on the recommendation): the army candidate is deferred, no freeze and no
upload; the flags stay in `current` off by default, to be combined with a later candidate. Next: the
queen's safety. Committed with these records.

## 2026-11-03 — The queen's safety: a second tower under threat; study, flags, P2 screen passed (S 0.642 with the army flags)

The user's "next" after deferring the army candidate. Study `docs/data/study-2026-11-03-queen-safety/`
(README, `deaths.py`, `deaths.txt`): `cgStudy` over v05's 180 arena games (uploads 1-3) into
`replays/study/features-v05-3up-2026-11-03.csv`, and a new tool `cr.tools.DeathTrace` (one line per turn
of a recorded game replayed through `Rules`: HP and loss, command, touched site, own towers, enemy knights
within 250/500, enemy towers covering her) over all 180 games into `replays/study/traces/all-v05.txt` (both
gitignored, sha256 in the README, copied to the main checkout). Findings: 37 of 55 losses by the queen's
death (12 before turn 80), 18 on HP; knights took 2,062 of 2,335 HP in the deaths, enemy towers 262; on 1,029
of the 1,103 hit turns she stood at one own tower, topping it up on 858; a tower deals 3 HP a turn at 200 HP
and 4 at 700 (`Rules.actStructure`), so a second tower doubles the kill rate; the opponents' queens in the
same games (traced by their agent ids) build the next towers under fire and survive. The queen's starting HP
in Bronze is random 25-100 (`MapGen`, both queens equal); v05 wins 76 % at 25-40 and 61 % at 85-100, so the
start is not the lever.

Code (from 78d2c4d): `Params.THREAT_RANGE` (the former literal 250), `THREAT_TOWERS_NEAR` and
`THREAT_RADIUS`; in `MainBot.play`, before the touch rule: under threat with fewer than N own towers within R,
`BUILD` a tower on the nearest free site within R not under an enemy tower. The snapshots record the fields;
`StrategyParamsTest.threatBuildsASecondTowerNearby`; `cr.bot.*` green.

Development sweep against v05 (`replays/bench/dev-safety-2026-11-03/`, 500 maps per arm, both seats, one JVM
per arm, 8 threads, 50 ms; `sweep-a.sh`, `sweep-b.sh`, `dev-sweep.txt` beside the README; the v05 mirrors
copied from the army sweep): seeds 10000-10499: N=2 at radius 300/400/500 → 0.631/0.621/0.635, N=3 →
0.585/0.576/0.587, THREAT_RANGE 350/450 alone → 0.471/0.409, with N=2 → 0.616/0.611; radius 600 0.634,
THREAT_RANGE 200 0.652, MAX_TOWERS=6 0.641, N=1 0.499 (6 maps changed), N=2 radius 500 with the army flags
0.680. Seeds 11000-11499: N=2 at 300/400/500 → 0.599/0.587/0.605, N=3 at 500 0.573, N=2 at 500 with the army
flags 0.629. Chosen: the combination (the same sign on both blocks, +3.5 pp over safety alone pooled).

P2 screen on block 7 (214000-215999, reserved in the ledger before the run; `screen.sh`): G0 clean in every
series (max candidate turn 21.6 ms; v05 one slow turn of 60 ms in the direct series); G1 S 0.6420 (CI
0.6261-0.6579), W/D/L 2525/86/1389, seats 1395/40/565 and 1130/46/824, 1960 of 2000 maps changed; G2 1.000
against 0.997 on `boss`, Δ +0.0030, 497 of 500 maps changed; G3 skipped. Summaries `g1.json`, `g2.json`.
About +1.0 rating point head-to-head at scale 1.72. Records: README, ledger, BOTS (flags), STATUS, SUMMARY,
PLAN. Not done: the final evaluation (block 5, 410000-411999), the freeze, `submit`, `procCheck`, the
architecture page. Raw reports copied to the main checkout's `replays/bench/`.

Final evaluation of the safety+army candidate (the user's "yes"), evaluation block 5 (410000-411999)
reserved in the ledger before the run, `eval.sh` (the screen's commands with `-PseedBase=410000`, reports
in `replays/bench/exp-2026-11-03-safety-eval/`): G0 clean in every series (max candidate turn 41.2 ms);
G1 S 0.6519 (CI 0.6356-0.6682), W/D/L 2566/83/1351, seats 1395/36/569 and 1171/47/782, 1997 of 2000 maps
changed; G2 1.000 against 1.000 on `boss`, Δ 0.0000, 488 of 500 maps changed; G3 skipped. Summaries
`eval-g1.json`, `eval-g2.json`. S ≥ 0.60: the upload recommendation is met. Records: README (results),
ledger, STATUS, SUMMARY, PLAN, `docs/local-screen.md` (the candidate row). Not done: the freeze (v06),
`submit`, `procCheck`, the architecture page, the commit. Raw reports copied to the main checkout.

## 2026-11-03 — Freeze v06: a second tower under threat and the army flags as the defaults; artifact 1a9f3e81

The user's decision after the final evaluation. `cr.bot.v06.GreedyBot` with its own `RoutePlanner` and
`ApproachPlanner` is a copy of `current` (the `queenSteps` diagnostic dropped, as in the earlier copies);
`versions/v06.properties` is v05's snapshot with MAX_TOWERS=6, BARRACKS=2, BARRACKS_AFTER_TOWERS=4,
TRAIN_SYNC=1, THREAT_TOWERS_NEAR=2, THREAT_RADIUS=500; `Params` defaults to those values, so `current` plays
as v06 (`MainBotTest.withDefaultsCurrentPlaysAsV06` on the fixture seeds, 214000-214002 and 410000;
`withV05ParamsCurrentPlaysAsV05` with the V05 pins). `BotFactory` and the local-games catalog know v06 (the
default opponent; v05 relabelled as the arena's selected version), `visualizer/local.js` falls back to v06.
The route, approach, stall-scan, LegScan and strategy tests pin v05's values where their fixtures assume one
barracks, 8 towers or the flight to the nearest tower. Full `./gradlew test`: 20 classes, 0 failures; the
visualizer's node tests 18/18.

Artifact with empty PARAMS: sha256 `1a9f3e81151548881d9ad3b2d5f03a5fbb2de25513517657ab8fd4d55e3f517f`,
97,797 chars (limit 100,000), build `0bf54983`, config `0ff55c62`. procCheck vs v01 100 maps @5000: 200/200
identical to current, 194 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 169.0 ms,
later 2.1 ms; vs boss 100 maps @6000: 200/200 identical, 200 wins, 0 timeouts, max first turn 246.5 ms, later
3.2 ms (`replays/bench/exp-2026-11-03-safety-eval/proccheck-v06-*.log`, copied to the main checkout). Kept
copy `artifacts/1a9f3e81/Player.java` (worktree and main checkout). Records: BOTS (versions, flags, artifacts),
STATUS, SUMMARY, PLAN, visualizer.md, bot-architecture.html (v06: the defence section, the flow, the
pseudo-code, TRAIN, the versions table, the gaps and the directions), `.ai/project.md` (v00–v06) with the kit
sync. Not uploaded: the paste and the P1 placements are the user's; register the new agent as v06 upload 1
(pending) before downloading its games.

## 2026-11-03 — v06 upload 1: 27.09, rank 1 among the players; the Bronze boss measured at 27.63

The user pasted `1a9f3e81` ("I pasted it, watch"). Discovery snapshot by pseudo at 17:40
(`arena_snapshot.py 6841579 aabzac bronze`): agent 6842260 (creationTime 17:39:25), 18 %, 12 wins of 13;
raw responses moved into `replays/arena/v06/bronze/upload-01/snapshots`, the summary renamed to
`docs/data/arena-6842260-placement-2026-11-03.json`; registered as v06 upload 1 (pending) in the registry,
v05 upload 3 marked replaced. Watch (`placement-watch.sh`, `.log` beside the snapshots): 7 snapshots, 21 % at
17:41 → 45 % 24.67 → 63 % 26.48 → 76 % 26.14 → 93 % 27.43 (local rank 1) → 98 % 26.91 → 100 % at 17:55
(score last updated 17:54:06): score 27.09, local rank 1 of 2290, global rank 658, 60 games, 43 wins (seat 0:
22 of 31, seat 1: 21 of 29), `eligibleForPromotion` false. `arena_games.py 6842260`: 60 games, marker
0bf54983 in all 60, 0 failures, 0 ties (`docs/data/arena-6842260-games-2026-11-03.json`). `cgAttribute
-Pbot=file:artifacts/1a9f3e81/Player.java`: SAME 60/60 (`cgAttribute.log` beside the games). Three games
against agent 1870318 (no pseudo, rating 27.63 at the fetch: the Bronze boss), all lost (916106598 0:20,
916113842 0:50, 916114160 0:11): the first measurement of the boss's score; three wins by opponents removed
by the referee (916100796, 916101800, 916108462). `arena_compare.py --candidate v06 --selected v05`
(`arena-compare.json` beside the games): scale 1.86 on fourteen uploads; v06 27.09, performance 26.79 ± 0.60
(opponents' median 25.55, 71.7 % wins); v05 mean 25.39, 25.02 ± 0.34; M 2.61; verdict "upload the candidate
again" (+1.70 against 2M 5.22). Records: `docs/arena-uploads.md` (new section), the registry, BOTS (Uploads,
Artifacts), STATUS (entry, candidate, the boss), SUMMARY, PLAN (the boss item), `docs/local-screen.md`
(v06 row). Raw arena files copied to the main checkout. Not committed (the user's call); upload 2 of the same
artifact is the user's paste.

## 2026-11-03 — v06 upload 2: 25.39, rank 77; P1 verdict: v06 selected

The user pasted `1a9f3e81` again ("I pasted it, watch"). The discovery snapshot at 18:02 still showed
6842260 with an empty battle list (the row lags the paste by a minute); `discover-and-watch.sh`
(`upload-02/`) snapshotted every minute, but read a stale summary: `arena_snapshot.py` keeps a summary file
that belongs to another agent and writes the new one beside the raw responses, so the loop printed 6842260
for 20 minutes while the raw snapshots of the new agent 6842539 (creationTime 18:02:10) accumulated under
`replays/arena/unknown/bronze/agent-6842539/snapshots` (66 files, moved into `upload-02/snapshots`); the
loop was stopped by hand at 18:25. From the raw leaderboard snapshots: 15 % at 18:03, 43 % at 18:07, 70 % at
18:11, 95 % at 18:15, complete at 18:16:46 (score last updated 18:16:00): score 25.39, local rank 77 of
2290, 60 games, 44 wins (seat 0: 23 of 32, seat 1: 21 of 28). Lesson for the watch: start the discovery
with a fresh summary path per snapshot, or pass the new agent id once the row changes. Registered as v06
upload 2 (the registry; 6842260 marked replaced), a fresh snapshot by the new id written to
`docs/data/arena-6842539-placement-2026-11-03.json`. `arena_games.py 6842539`: 60 games, marker 0bf54983 in
all 60, 0 failures, 0 ties (`docs/data/arena-6842539-games-2026-11-03.json`). `cgAttribute
-Pbot=file:artifacts/1a9f3e81/Player.java`: SAME 60/60. `arena_compare.py --candidate v06 --selected v05`
(`arena-compare.json` beside the games): scale 1.95 on fifteen uploads; v06 placements 27.09 and 25.39,
mean 26.24, SD 1.20, rating 25.75 ± 0.46 (opponents' median 22.25 in upload 2, 73.3 % wins, one opponent
removed: 916125229); v05 mean 25.39, 25.06 ± 0.35; M 2.59 (9 df); verdict "not clearly worse" (mean +0.85
against −2.59; rating +0.69 against −1.16): v06 is the selected version. Records: `docs/arena-uploads.md`
(new section), the registry, BOTS (versions, uploads, artifacts), STATUS, SUMMARY, PLAN, `docs/local-screen.md`
(v06 row), the architecture page (the v06 numbers and pills), the local-games labels. Raw arena files copied
to the main checkout. Not committed (the user's call).

## 2026-11-03 — Merge to master; the late-economy flag: neutral against v06, off

Master fast-forwarded to 3eef83c (the three commits of the day) from the main checkout; the worktree stays.
The user's "next": the profile of v06's 120 arena games (`docs/data/exp-2026-11-03-late-income/profile.py`,
`profile.txt`; `cgStudy` into `replays/study/features-v06-2026-11-03.csv`, `DeathTrace` into
`replays/study/traces/all-v06.txt`, both copied to the main checkout): 87 wins, 33 losses (24 deaths, median
turn 136, 3 before turn 80; 9 on HP); the opponents who beat v06: income 8 at turn 100, 12 knight waves, 4
towers at 1,676 HP, 3 mines; ours: income 3, 6 waves, 5 towers at 3,180 HP, 74 top-ups; giants in 7 of the
33 losses. Code: `Params.INCOME_LATE` (the income target after the ladder; `MainBot.lateSite`: the nearest
free gold site or own mine below its maximum; the touch rule builds a mine on a free gold site in that
phase); the snapshots record it; `StrategyParamsTest.lateIncomeRaisesTheIncomeAfterTheLadder`; `cr.bot.*`
green with the v06 identity. Development sweep against v06 (`replays/bench/dev-late-income-2026-11-03/`,
seeds 10000-10499, 500 maps, both seats, the v06 mirrors on 10000 and 11000): INCOME_LATE 4/5/6/8 →
0.505/0.513/0.508/0.485; 6 with TOWER_HP 600/500 → 0.428/0.368; 8 with 500 → 0.360; 1-2 slow turns in
three arms with v06's own (host). A traced game (seed 10001) shows the late phase starved by the threat
rules after turn 120. Verdict: neutral, not screened, the flag off by default; the economy's value against
the field needs a local opponent with the field's balance (PLAN). Records: README, STATUS, SUMMARY, PLAN.
Raw reports copied to the main checkout.

## 2026-11-03 — A local opponent with the field's balance: `field`, calibrated; the income flags lose against it too

The user's choice after the late-income result. Code: `cr.bot.field.FieldBot` and `FieldParams` (spec
`field?K=v`; the package is outside the artifact's), `BotFactory` create/describe, the local-games catalog
(9 bots), `cr.tools.LocalProfile` (a bot's local profile in cgStudy's terms from the referee's frames).
Tests: `FieldBotTest` (legal play on 10 maps and both seats against v06 with and without giants; the ladder,
the giant barracks, the touch rule, the training order with the knights waiting for the giants' gold),
`LocalGamesTest` (9 bots). Full `./gradlew test`: 21 classes, 0 failures.

Calibration against v06 (`replays/bench/dev-field-2026-11-03/`, 100 maps each, both seats, seeds 10000-10099;
`LocalProfile` on 50 maps): the first cut (giants, TOWER_HP 450) 0.060 with 6 knight and 5 giant waves and 38
queen deaths of 100; GIANTS=0 0.193; GIANTS=0 with TOWER_HP=600 **0.255** (income 7 with 3 mines at turn 100,
4 towers at 2,344 HP, 17 knight waves, 20 deaths) — the defaults; TOWERS 3/5, MINES 2/4, THREAT_TOWERS_NEAR 3,
THREAT_RADIUS 700, INCOME 12 within ±0.03 of it; TOWER_HP=800 0.025; every giants-on arm 0.03-0.07. Against
`boss` 60/60 (30 maps), against v03 0.735 (100 maps). Our flags against `field` (200 maps, seeds
11000-11199): defaults 0.726; INCOME_LATE 6/8 0.635/0.608; INCOME_TARGET=5 0.699; TOWER_HP=500 0.654;
MAX_TOWERS=4 0.666; MINES_FIRST=3 0.759; THREAT_TOWERS_NEAR=0 0.632; the army flags off 0.714. Records:
`docs/data/exp-2026-11-03-field-opponent/README.md`, BOTS (opponents), STATUS, SUMMARY, PLAN (the backlog
item closed, two new), `.ai/project.md` (specs) with the kit sync. Raw reports copied to the main checkout.
Not adopted into P2 (the user's decision); not committed.
Adopted (the user, 2026-11-03): `field` is a P2 opponent, gate G4 (Δ against `field` on the block's 2000
maps ≥ −2 pp); proxy validation PV2 recorded in `docs/local-screen.md` with `field`'s fingerprints;
`.ai/project.md` (opponents, gates, the proxy and reserve rows) with the kit sync. Committed.

## 2026-11-03 — Study of the arena winners (they move under knights); kiting flag KITE; P2 screen with G4 passed, S 0.695

The user's "trace study". `DeathTrace <agent> all` for every turn of both queens in v06's 120 placement games
(`replays/study/traces/all-v06-ours.txt`, `all-v06-opp.txt`, the agent ids per game in `v06-games.txt`;
copied to the main checkout); `docs/data/study-2026-11-03-arena-winners/winners.py` (`winners.txt`):
under knights the opponents who beat v06 MOVE on 42 % of their turns and cover 36 units a turn, MOVE on 44 %
of the turns while knights are 250-500 away, lose 1.0 HP per contact turn against the losers' 1.4 and ours
1.2, with 2.2 knights on them against 2.7; v06 never MOVEs under knights (90 % of its contact turns at a
tower, 77 % topping up; 50 contact turns in its losses, 20 in its wins). Winners: income 8 with 3 mines and
11 upgrades, 12 waves, 33 top-ups (ours 93 in losses); the losers among the opponents hold 4 thin towers
too, so the thin line alone is not it.

Code (from 64e61fe): `Params.KITE`, `KITE_RANGE`, `KITE_COVER`; `MainBot.kite` (24 headings, the predicted
step farthest from the nearest knight, out of enemy tower range, under own cover when covered now) before
the touch rule; the snapshots record the fields; `StrategyParamsTest.kiteMovesAwayFromKnightsUnderTowerCover`;
`cr.bot.*` green with the v06 identity. Development sweep (`replays/bench/dev-kite-2026-11-03/`, 500 maps per
arm, both seats, seeds 10000-10499 and 11000-11499, against v06 and against `field` with the v06-vs-`field`
control 0.718 on both blocks): range 250 → 0.681/0.709 vs v06, 0.762/0.809 vs `field`; range 400 →
0.682/0.745, 0.717/0.762; range 500 → 0.720/0.739, 0.598 vs `field` on block 10000; cover 0 within noise.
Chosen: `KITE=1` (range 250, cover 1).

P2 screen on block 8 (216000-217999, reserved before the run; `screen.sh` with the G4 series: candidate and
v06 against `field` on the 2000 maps): G0 clean in every series (no slow turn at all; max candidate turn
36.9 ms); G1 S 0.6946 (CI 0.6784-0.7109), W/D/L 2752/53/1195, seats 1444/27/529 and 1308/26/666, 2000 of
2000 maps changed; G2 0.998 against 0.997 on `boss`, Δ +0.0015; G4 0.7466 against 0.7157 on `field`, Δ +0.0309
(CI +0.0120..+0.0497); G3 skipped. Summaries `g1.json`, `g2.json`, `g4.json`. About +1.6 rating points
head-to-head at scale 1.95. Records: the study README, the experiment README, ledger, BOTS (flags KITE and
INCOME_LATE), STATUS, SUMMARY, PLAN. Not done: the final evaluation (block 6, 412000-413999), the freeze
(v07), `submit`, `procCheck`, the architecture page, the commit. Raw reports copied to the main checkout.
The full suite then failed in `FileMergerTest`: the merged artifact had grown to 103,518 characters (limit
100,000) with the javadocs of the new flags. `FileMerger.merge` now leaves out whole-line comments (line,
javadoc and block-comment lines; the comment prefix of a "/* live-tunable */ code" line is dropped and the
code kept) and blank lines: 78,622 characters for the same code. `FileMergerTest.theConfigIdFollowsTheBotNotTheRunner`
changes code instead of a comment to move the config id. The artifacts of v01-v06 are unchanged (they are
rebuilt from their commits with the merger of the time); from the next build on, build and config ids are
computed on the stripped text. Full suite 21 classes, 0 failures; `.ai/project.md` (the submit row) with
the kit sync.

Final evaluation of the kiting candidate (the user's "yes"), evaluation block 6 (412000-413999) reserved
in the ledger before the run, `eval.sh` (the screen's series with `-PseedBase=412000`, reports in
`replays/bench/exp-2026-11-03-kite-eval/`): G1 S 0.6803 (CI 0.6641-0.6964), W/D/L 2692/58/1250, seats
1399/30/571 and 1293/28/679, 2000 of 2000 maps changed; G2 1.000 against 0.999 on `boss`, Δ +0.0010; G4
0.7675 against 0.7322 on `field`, Δ +0.0352 (CI +0.0163..+0.0542), 2000 of 2000 changed; G3 skipped. G0: 4
slow candidate turns in the first direct run (maps 412270-412280, max 73.8 ms), with 1 of v06 there and 4
of v06 in the mirror (max 106 ms, unchanged code); `recheck.sh`: the 30 maps 412260-412289 on one thread
with no slow turn (max 8.8 ms) and a quiet repeat of the direct series with identical outcomes in all 4000
games (0 candidate slow turns, 1 of v06) → pass. Summaries `eval-g1.json`, `eval-g1-rerun.json`,
`eval-g2.json`, `eval-g4.json`. S ≥ 0.60: the upload recommendation is met (about +1.5 rating points at
scale 1.95). Records: README (results), ledger, STATUS, SUMMARY, PLAN, `docs/local-screen.md` (the candidate
row). Not done: the freeze (v07), `submit`, `procCheck`, the architecture page, the commit. Raw reports
copied to the main checkout.

## 2026-11-03 — Freeze v07: kiting under knights as the default; artifact 4c7b0d04

The user's decision after the final evaluation. `cr.bot.v07.GreedyBot` with its own `RoutePlanner` and
`ApproachPlanner` is a copy of `current` (the `queenSteps` diagnostic dropped, as in the earlier copies);
`versions/v07.properties` is v06's snapshot with KITE=1; `Params.KITE` defaults to 1, so `current` plays as
v07 (`MainBotTest.withDefaultsCurrentPlaysAsV07` on the fixture seeds, 216000-216002 and 412000;
`withV06ParamsCurrentPlaysAsV06` with KITE=0). `BotFactory` and the local-games catalog know v07 (the default
opponent; v06 relabelled as the arena's selected version; 10 bots), `visualizer/local.js` falls back to v07.
The tests whose fixtures assume no kiting pin KITE=0 (the route, approach, stall-scan, LegScan and strategy
tests, the defence test). Full `./gradlew test`: 21 classes, 0 failures.

Artifact with empty PARAMS, the first built without comment and blank lines: sha256
`4c7b0d04aebdd27f6a9a6f07049f250e3035ba30311f98bde126173078a7a8e8`, 78,622 chars (limit 100,000), build
`b6b5f0f7`, config `c1fcf458`. procCheck vs v01 100 maps @5000: 200/200 identical to current, 198 wins, 0
timeouts, 0 kills, no extra, late or stderr lines, max first turn 372.0 ms, later 7.8 ms; vs boss 100 maps
@6000: 200/200 identical, 200 wins, 0 timeouts, max first turn 302.4 ms, later 10.5 ms
(`replays/bench/exp-2026-11-03-kite-eval/proccheck-v07-*.log`, copied to the main checkout). Kept copy
`artifacts/4c7b0d04/Player.java` (worktree and main checkout). Records: BOTS (versions, flags, artifacts),
STATUS, SUMMARY, PLAN, visualizer.md, bot-architecture.html (v07: the kiting section, the flow, the
pseudo-code, the versions table, the gaps and the directions), `.ai/project.md` (v00–v07) with the kit sync.
Not uploaded: the paste and the P1 placements are the user's; register the new agent as v07 upload 1
(pending) before downloading its games.

## 2026-11-03 — v07 upload 1: 26.42, rank 32; P1 needs the second upload

The user pasted `4c7b0d04` ("I pasted it, watch"). `discover-and-watch.sh` (`replays/arena/v07/bronze/upload-01/`)
with a fresh discovery file per attempt (the lesson of v06's upload 2): the new row at 20:47 (agent 6843218,
creationTime 20:45:49, 18 %, 14 wins of 14); registered as v07 upload 1 (pending), the discovery snapshots
moved into `upload-01/snapshots`; the watch by the new id: 45 % 25.25 at 20:50, 66 % 24.79, 88 % 26.13, 96 %
26.03, complete at 20:58:24 (score last updated 20:57:28): score 26.42, local rank 32 of 2290, 60 games,
41 wins (seat 0: 21 of 29, seat 1: 20 of 31), `eligibleForPromotion` false. `arena_games.py 6843218`: 60
games, marker b6b5f0f7 in all 60, 0 failures, 0 ties; `cgAttribute -Pbot=file:artifacts/4c7b0d04/Player.java`:
SAME 60/60, 9,301 turns. Two wins by opponents removed by the referee (916208555, 916208563); no boss game.
`arena_compare.py --candidate v07 --selected v06` (`arena-compare.json` beside the games): scale 1.89 on
sixteen uploads; v07 26.42, performance 26.14 ± 0.56 (opponents' median 24.95, 68.3 % wins); v06 mean 26.24,
25.71 ± 0.45; M 2.59; verdict "upload the candidate again" (+0.18 against 2M 5.18). Records:
`docs/arena-uploads.md` (new section), the registry, BOTS (Uploads, Artifacts), STATUS, SUMMARY,
`docs/local-screen.md` (v07 row). Raw arena files copied to the main checkout. Not committed (the user's call);
upload 2 of the same artifact is the user's paste.

## 2026-11-03 — v07 upload 2: 24.64, rank 108; P1 verdict: v07 selected by the policy, the local gain absent on the arena

The user pasted `4c7b0d04` again at 21:03 and said so at 21:30 ("ran it"); `discover-and-watch.sh`
(`upload-02/`) found the new row at once (agent 6843287, creationTime 21:03:58) with the placement
complete (score last updated 21:14:54; not watched): score 24.64, local rank 108 of 2290, 60 games, 42 wins
(seat 0: 21 of 30, seat 1: 21 of 30). Registered as v07 upload 2, 6843218 marked replaced. `arena_games.py
6843287`: 60 games, marker b6b5f0f7 in all 60, 0 failures, 0 ties; `cgAttribute` against the kept copy:
SAME 60/60, 9,615 turns; three opponents removed by the referee (916214881, 916214882, 916217955); no boss
game. `arena_compare.py --candidate v07 --selected v06` (`arena-compare.json` beside the games): scale 1.97
on seventeen uploads; v07 placements 26.42 and 24.64, mean 25.53, SD 1.26, rating 25.34 ± 0.43 (opponents'
median 23.14 in upload 2, 70.0 % wins); v06 mean 26.24, 25.76 ± 0.46; M 2.58 (10 df); verdict "not clearly
worse" (mean −0.71 against −2.58; rating −0.41 against −1.26): v07 is the selected version by the policy.
The arena does not show the local gain (P2 +1.5 head-to-head): recorded as a PV2 note in
`docs/arena-uploads.md` and a PLAN item (study v07's 120 games). Records: `docs/arena-uploads.md` (new
section), the registry, BOTS (versions, uploads, artifacts), STATUS, SUMMARY, PLAN, `docs/local-screen.md`
(v07 row), the architecture page (the v07 numbers and pills), the local-games labels. Raw arena files
copied to the main checkout. Not committed (the user's call).

## 2026-11-03 — Study of v07's arena games: the kiting leaves the towers' cover

The user's "study v07's games". `cgStudy` into `replays/study/features-v07-2026-11-03.csv`, `DeathTrace
<agent> all` for both queens (`replays/study/traces/all-v07-ours.txt`, `all-v07-opp.txt`, `v07-games.txt`;
copied to the main checkout); `docs/data/study-2026-11-03-v07-arena/v07.py` (`v07.txt`), which imports the
winners study's loader. Against v06's 120 games: wins 83 (87), deaths 26 (24), HP losses 11 (9), kills of
the enemy queen 56 (68); under knights v07 MOVEs on 75-89 % of the turns, loses 0.9 / 0.3 HP per contact
turn in losses / wins (v06 1.2 / 0.9), HP 58 at turn 100 (47); but 59 contact turns in its losses (50), 34 %
of them near a tower (90 %), top-ups 36 per game (73), 4 towers at 2,364 HP (5 at 3,180); chases of
50-130 turns in the loss list. Diagnosis: the cover condition binds only while covered, so the queen runs
out of range and the towers, which shoot the creep nearest to themselves, kill nothing; the local opponents
stand still under knights, which is why P2 rewarded it. Records: the study README, PLAN (the item closed,
the next one), STATUS, SUMMARY. Not committed.

## 2026-11-03 — Kiting bound to the towers (KITE=2): sweep, profiles, P2 screen against v07 passed (S 0.666)

The user's "kiting near the towers". Code (from 8c1e43d): `Params.KITE` 2 (bound), `KITE_PULL`, `KITE_MAX`;
`MainBot.kite`: with KITE=2 every step must be covered by an own tower, the score is the knight distance
minus pull × the tower distance (the standing position scored the same way, +5 to move), no covered step
means null (v06's flight); `kiteRun` counts consecutive kiting turns for the cap. `cr.tools.LocalProfile`
prints the behaviour under knights (contact turns, % near an own tower within 130, HP lost per contact turn,
% MOVE) from the referee's frames. Snapshots record the fields; `StrategyParamsTest.boundKitingStaysUnderTheTowersAndFleesOutOfReach`;
`cr.bot.*` green with the v07 identity.

Development sweep (`replays/bench/dev-kite2-2026-11-03/`, 500 maps per arm, both seats; `sweep-a.sh`,
`sweep-b.sh`, `dev-sweep.txt`): block 10000 against v06 / `field` (control 0.718): pull 30 → 0.739 / 0.854,
50 → 0.737 / 0.871, 100 → 0.785 / 0.915, 200 → 0.790 / 0.912, the cap 8 within ±0.005; block 11000: pull
100 → 0.808 / 0.907, 200 → 0.801 / 0.921; against v07 (the v07 mirror 0.500): pull 100 → 0.689 / 0.658 on
the two blocks, 200 → 0.681. `LocalProfile` (50 maps against v06): KITE=0 contact 35 turns, near a tower
91 %, 0.8 HP per contact turn, MOVE 0 %; KITE=1 45 / 39 % / 0.4 / 70 %; KITE=2 pull 50 43 / 67 % / 0.5 /
71 %; pull 100 40 / 87 % / 0.4 / 65 %; pull 200 41 / 89 % / 0.4 / 66 %. Chosen: `KITE=2,KITE_PULL=100`.

P2 screen on block 9 (218000-219999, reserved before the run; `screen.sh` against v07 with the `field`
series): G1 S 0.6663 (CI 0.6516-0.6809), W/D/L 2571/188/1241, seats 1308/94/598 and 1263/94/643, 2000 of
2000 maps changed; G2 1.000 against 0.999 on `boss`, Δ +0.0010; G4 0.9204 against 0.7835 on `field`, Δ
+0.1369 (CI +0.1225..+0.1513); G3 skipped. G0: 7 slow candidate turns in the first direct run (maps 218042,
218081-218092, max 132 ms) with 7 of v07 in the same run (max 94 ms); `recheck.sh`: the 60 maps 218040-218099
on one thread with no slow turn (max 17 ms) and a quiet repeat with identical outcomes in all 4000 games
and no slow turn on either side → pass. Summaries `g1.json`, `g1-rerun.json`, `g2.json`, `g4.json`. About
+1.4 rating points head-to-head against v07 at scale 1.97, with the caveat written before the screen that
the arena decides. Records: README, ledger, BOTS (the KITE=2 row), STATUS, SUMMARY, PLAN. Not done: the
final evaluation (block 7, 414000-415999), the freeze (v08), `submit`, `procCheck`, the commit. Raw reports
copied to the main checkout.

Final evaluation of the bound-kiting candidate (the user's "yes"), evaluation block 7 (414000-415999)
reserved in the ledger before the run, `eval.sh` (the screen's series with `-PseedBase=414000`, reports in
`replays/bench/exp-2026-11-03-kite2-eval/`): G1 S 0.6670 (CI 0.6522-0.6818), W/D/L 2562/212/1226, seats
1313/107/580 and 1249/105/646, 2000 of 2000 maps changed; G2 1.000 against 1.000 on `boss` with maps 414135
and 414136 dropped (4 timeouts of the `boss` process in the control series, 0.4 %), Δ 0.0000; G4 0.9153
against 0.7669 on `field`, Δ +0.1484 (CI +0.1331..+0.1637); G3 skipped. G0: one 50.1 ms candidate turn on
map 414083 against v07 (v07 had 4 slow turns there, max 91.6 ms) and one 50.6 ms turn on map 414137 against
`field`; `recheck-eval.sh`: both regions on one thread with no slow turn (max 5.4 and 13.6 ms), quiet repeats
of both series with identical outcomes in all 4000 games each and no slow turn on either side → pass.
Summaries `eval-g1.json`, `eval-g1-rerun.json`, `eval-g2.json`, `eval-g4.json`, `eval-g4-rerun.json`. S ≥ 0.60:
the upload recommendation is met (about +1.4 rating points head-to-head against v07 at scale 1.97). Records:
README (results), ledger, STATUS, SUMMARY, PLAN, `docs/local-screen.md` (the candidate row). Not done: the
freeze (v08), `submit`, `procCheck`, the architecture page, the commit. Raw reports copied to the main checkout.

## 2026-11-03 — Freeze v08: the kiting bound to the towers as the default; artifact 5c16d925

The user's decision after the final evaluation. `cr.bot.v08.GreedyBot` with its own `RoutePlanner` and
`ApproachPlanner` is a copy of `current` (the `queenSteps` diagnostic dropped, as in the earlier copies);
`versions/v08.properties` is v07's snapshot with KITE=2, KITE_PULL=100; `Params` defaults to those values, so
`current` plays as v08 (`MainBotTest.withDefaultsCurrentPlaysAsV08` on the fixture seeds, 218000-218002 and
414000; `withV07ParamsCurrentPlaysAsV07` with KITE=1, KITE_PULL=50). `BotFactory` and the local-games
catalog know v08 (the default opponent; v07 relabelled as the arena's selected version; 11 bots),
`visualizer/local.js` falls back to v08. Full `./gradlew test`: 21 classes, 0 failures.

Artifact with empty PARAMS: sha256 `5c16d925b0c4e20746484cbdfb7826e82b4bc1ed8dd9e1e3e1a5b8954b917df6`, 79,348 chars (limit
100,000), build `3f19aa6e`, config `c9dad766`. procCheck vs v01 100 maps @5000: 200/200 identical to current, 198
wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 545.1 ms, later 5.2 ms; vs boss 100
maps @6000: 200/200 identical, 200 wins, 0 timeouts, max first turn 475.7 ms, later 5.5 ms
(`replays/bench/exp-2026-11-03-kite2-eval/proccheck-v08-*.log`, copied to the main checkout). Kept copy
`artifacts/5c16d925/Player.java` (worktree and main checkout). Records: BOTS (versions, flags, artifacts),
STATUS, SUMMARY, PLAN, visualizer.md, bot-architecture.html (v08: the kiting section, the flow, the
pseudo-code, the versions table, the gaps and the directions), `.ai/project.md` (v00–v08) with the kit sync.
Not uploaded: the paste and the P1 placements are the user's; register the new agent as v08 upload 1
(pending) before downloading its games.
