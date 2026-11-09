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

## 2026-11-03 — v08 upload 1: 27.85, rank 1 among the players, 3 of 7 against the boss; P1 needs the second upload

The user pasted `5c16d925` ("I pasted it, watch"). `discover-and-watch.sh` (`replays/arena/v08/bronze/upload-01/`):
the new row at 22:55 (agent 6843773, creationTime 22:54:56, 16 %); registered as v08 upload 1 (pending), the
discovery snapshots moved into `upload-01/snapshots`; the watch: 38 % 26.75 at 22:58, 61 % 28.04 (local rank 1)
at 23:02, 83 % 27.54, complete at 23:08:06 (score last updated 23:06:26): score 27.85, local rank 1 of 2291,
60 games, 42 wins (seat 0: 21 of 28, seat 1: 21 of 32), `eligibleForPromotion` false. The user asked whether
the games were being fetched, since a promotion mid-placement would move the live list: `fetch-loop.sh`
fetched the finished games every minute from 23:03 (42 of 43 at once), all 60 by 23:07, before the placement
completed; the game ids stay in the saved battle lists and `gameResult/findByGameId` serves them by id in
any case. `arena_games.py 6843773`: 60 games, marker 3f19aa6e in all 60, 0 failures, 0 ties
(`docs/data/arena-6843773-games-2026-11-03.json`). `cgAttribute -Pbot=file:artifacts/5c16d925/Player.java`:
SAME 60/60, 9,459 turns. Seven games against the boss (agent 1870318, rating 28.08 at the first and 27.90 at
the last), 3 won (916267498 59:0, 916269344 36:21, 916269626 72:22). `arena_compare.py --candidate v08
--selected v07` (`arena-compare.json` beside the games): scale 1.95 on eighteen uploads; v08 27.85,
performance 27.64 ± 0.60 (opponents' median 26.75, 70.0 % wins); v07 mean 25.53, 25.34 ± 0.43; M 2.58;
verdict "upload the candidate again" (+2.32 against 2M 5.16). Records: `docs/arena-uploads.md` (new
section), the registry, BOTS (Uploads, Artifacts), STATUS (entry, candidate, the boss), SUMMARY,
`docs/local-screen.md` (v08 row). Raw arena files copied to the main checkout. Not committed (the user's
call); upload 2 of the same artifact is the user's paste.

## 2026-11-03 — v08 upload 2: 28.13, rank 1, eligibleForPromotion true; P1 verdict: v08 selected

The user pasted `5c16d925` again ("I pasted it, watch"). `discover-and-watch.sh` (`upload-02/`): the new row at
23:14 (agent 6843829, creationTime 23:12:37, 31 %); registered as v08 upload 2 (pending), 6843773 marked
replaced; `fetch-loop.sh` fetched the finished games every minute (all 60 by 23:23); the watch: 58 % 24.46 at
23:17, 83 % 26.96 (rank 3) at 23:20, complete at 23:23:49 (score last updated 23:22:46): score 28.13, local
rank 1 of 2291, 60 games, 48 wins (seat 0: 30 of 35, seat 1: 18 of 25), `eligibleForPromotion` true.
`arena_games.py 6843829`: 60 games, marker 3f19aa6e in all 60, 0 failures, 0 ties; `cgAttribute` against the
kept copy: SAME 60/60, 9,087 turns; one opponent removed by the referee (916274351); one boss game
(916275323, rating 27.81), won 25:0. `arena_compare.py --candidate v08 --selected v07` (`arena-compare.json`
beside the games): scale 2.13 on nineteen uploads; v08 placements 27.85 and 28.13, mean 27.99, SD 0.20,
rating 27.59 ± 0.50 (opponents' median 23.93 in upload 2, 80.0 % wins); v07 mean 25.53, 25.42 ± 0.46; M
2.46 (11 df); verdict "not clearly worse" (mean +2.46 against −2.46; rating +2.17 against −1.36): v08 is the
selected version. A discovery snapshot at 23:24 still showed the row in Bronze with the flag true: the
promotion to Silver is CG's next step; by the policy the Silver agent is the next upload with its own
league and v08's baseline is rebuilt there. Records: `docs/arena-uploads.md` (new section), the registry,
BOTS (versions, uploads, artifacts), STATUS (selected version, entry, candidate, the boss), SUMMARY (the
"now" block rewritten), PLAN (the Silver item), `docs/local-screen.md` (v08 row), the architecture page (the
v08 numbers and pills), the local-games labels. Raw arena files copied to the main checkout. Not committed
(the user's call).

## 2026-11-04 — v08 in Silver: upload 3 (CG's promotion), placement 20.70, rank 2/200; 80 games fetched

The user: "we moved up to Silver, fetch the games for the current bot". Discovery
`arena_snapshot.py 6843829 aabzac silver <scratch path>` at 00:04: the row of aabzac in Silver (division 4) with
agent 6843976 (creationTime 2026-11-03 23:46:20, score last updated 00:02:36), percentage 100, inProgress
false, score 20.70, local rank 2 of 200, global 461, `eligibleForPromotion` false; 80 battles, all done, all
with our submission 41627919 — the same id as in upload 2's saved Bronze battle list, so the promotion kept
the submission. Registered as v08 upload 3 (league silver), upload 2 marked replaced; the discovery files
moved from `replays/arena/unknown/silver/agent-6843976/snapshots` into `replays/arena/v08/silver/upload-03/snapshots`;
`arena_snapshot.py 6843976 aabzac silver docs/data/arena-6843976-placement-2026-11-04.json` at 00:05 (the
same values). `arena_games.py 6843976`: 80 games, 53 wins, marker 3f19aa6e in all 80, 0 failures, 0 score
ties (`docs/data/arena-6843976-games-2026-11-04.json`). `cgAttribute -Preplays=replays/arena/v08/silver/upload-03/games
-Pagent=6843976 -Pbot=file:<main checkout>/artifacts/5c16d925/Player.java`: SAME 80/80, 15,934 turns, every
compared turn validated (`cgAttribute.log` beside the games). Seats 27 of 40 and 26 of 40; one opponent
removed by the referee (916284417); 50 opponents, median rating 18.73 at the fetch; the Silver boss is agent
1876720 (no pseudo, rating 21.87), 10 games, 4 won; 40 of the 80 games went to turn 250 (v08's Bronze
placements: 18 and 13 of 60). `arena_compare.py` (`arena-compare.json` beside the games): Silver scale 3.81
on this one upload, performance 20.88 ± 0.95; the Bronze report unchanged. P1: v08's first Silver
placement; the Silver baseline needs a second upload of `5c16d925` (the user's paste), and PV1/PV2 lapse with
the league change. Records: the registry, `docs/arena-uploads.md` (new section), BOTS (versions, uploads,
artifacts), STATUS (date and league, selected version, entry, the Silver boss), SUMMARY, PLAN (the Silver
item). The worktree's raw files `replays/arena/v08/silver/` copied to the main checkout. Committed with upload 4's
records in one commit (the user's request).

## 2026-11-04 — v08 upload 4 (Silver): 20.74, rank 3/200; the Silver baseline 20.72; the Silver M of one pair

The user re-pasted `5c16d925` ("re-uploaded once more; when it ends fetch another series, then commit in one
commit"). Discovery `arena_snapshot.py 6843976 aabzac silver <scratch path>` at 00:16: agent 6844095
(creationTime 00:14:42, submission 41628436), 21 %; registered as v08 upload 4 (league silver, pending),
upload 3 marked replaced; the discovery files moved into `replays/arena/v08/silver/upload-04/snapshots`.
`watch.sh` (beside the games; `placement-watch.log`): 23 % 18.66 (rank 29) at 00:17, 38 % 19.10, 55 % 19.69,
72 % 20.52, 88 % 21.30 (rank 1) at 00:29, 95 % 21.05, complete at 00:31:47 (score last updated 00:31:24):
score 20.74, local rank 3 of 200, global 462, `eligibleForPromotion` false. `inProgress` was false in every
snapshot of this placement; completion was read from percentage 100. `fetch-loop.sh` (`fetch-loop.log`):
`arena_games.py 6844095` every minute from 00:17, all 80 by 00:32: 80 games, 50 wins, marker 3f19aa6e in all
80, 0 failures, one score tie (`docs/data/arena-6844095-games-2026-11-04.json`). `cgAttribute` against the
kept copy: SAME 80/80, 16,757 turns, every compared turn validated (`cgAttribute.log`). Seats 29 of 45 and 21
of 35; two opponents removed by the referee (916295252, 916295352); 916297591 ended 28:28 on turn 250 and CG
ranked player 0 (the opponent) first, the sixth such game (`docs/rules/inferred.md`, now six); Silver boss
1876720 (21.87-21.97) 3 games, 1 won (916299584); 39 opponents, median 19.05; 40 of 80 games to turn 250.
`arena_compare.py --candidate v08 --selected v08` (`arena-compare.json` beside the games): Silver scale 3.58;
upload 3 20.74 ± 0.89, upload 4 20.90 ± 0.85; v08 mean 20.72, SD 0.03, pooled rating 20.82 ± 0.62; M 0.06
(1 df); the Bronze report unchanged. The baseline is complete; the Silver M from one pair is not a usable
margin (Bronze 2.46 on 11 df), so how to set it is left to the user (`docs/arena-uploads.md`, STATUS, PLAN).
Records: the registry, `docs/arena-uploads.md` (new section), BOTS (versions, uploads, artifacts), STATUS,
SUMMARY, PLAN, `docs/rules/inferred.md`. Raw files `replays/arena/v08/silver/upload-04/` copied to the main
checkout. Committed with the upload 3 records in one commit on the user's request.

## 2026-11-04 — Silver noise margin: Bronze's M 2.46 (the user's decision); `noiseMargins` in the registry

The user answered the open question of upload 4 ("M from Bronze, 2.46"). Until then `arena_compare.py` took M
from the league's own pairs only, so a Silver verdict would have used 0.06.

What changed:
- The registry's `noiseMargins` maps a league to a source league, the M recorded at the decision and the
  date. Entry: `noiseMargins.silver` = from bronze, M 2.46, decided 2026-11-04.
- The tool:
  - sets `noise.usedM` (the verdict's M) and `noise.borrowed`, and keeps the league's own `noise.M`;
  - prints both;
  - refuses (exit 2) when the source M is more than 0.005 from the recorded value, the source is not another
    known league, the source has no M, or the rule lacks M or decided.
- Test `test_a_league_can_borrow_the_noise_margin_of_another`: the own M rolls back early, the borrowed M asks
  for another upload; plus the refusals.

Checks:
- `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`: 45 OK.
- `node --test visualizer/tests/catalog.test.mjs`: 6 pass (the visualizer reads the uploads lists only).
- `python -B -I tools/arena_compare.py --candidate v08 --selected v08`:
  - Bronze unchanged (M 2.4644, 11 df, from 19 uploads of 8 versions);
  - Silver "M = 2 SD = 0.06; verdicts use M = 2.46 from bronze", not clearly worse against −2.46;
  - the report is in the scratchpad only; the `arena-compare.json` files beside the games stay as written.

Records:
- policy: `.ai/project.md` (the noise row, then sync to AGENTS.md); `docs/arena-uploads.md` (item 7 and a new
  section, Silver noise margin);
- STATUS (the Silver noise margin line), PLAN, SUMMARY.

## 2026-11-04 — `tools/arena.py`: one command for an upload; one layout of `replays/arena`; `route` → v04

The user asked for one tool they can run themselves ("structured and understandable") and pointed at the
mixed upload folders: `checks/` in v01-v03 only, no v04 but `route`, `*.sh` scripts in some. Asked about the
rename, they chose v04.

The tool:
- `tools/arena.py` has three commands:
  - `status`: the leaderboard row of `aabzac` and the registered upload it is; writes nothing;
  - `follow <label> [--artifact] [--selected] [--wait]`: save the live entry, wait for the new agent,
    register it, follow the placement and fetch games every minute, record the placement, fetch the rest,
    cgAttribute, comparison;
  - `fetch [--agent] [--no-attribute]`.
- Registration:
  - the label's next upload number in the league of the leaderboard row;
  - the submission from the agent's battle list;
  - `replaced` on the previous entry;
  - "CG's promotion" when the submission equals the previous entry's;
  - the artifact identity reused for known bytes, else from the new Gradle task `artifactInfo`
    (`cr.submit.ArtifactInfo`, a JSON line from `Build.of/config/params`; not shipped);
  - the kept copy written to `artifacts/<8 hex>/`;
  - a label's other configuration refused.
- A league change of the row during the placement:
  - becomes the label's next upload in the new league;
  - the old one gets `promotedTo` and `placement.lastPartial`.

  An agent change in the same league stops the command (exit 3). The same command resumes after an
  interruption.
- The registry is re-read and checked (`arena_paths.check_registry`, split out of `registry_entries`)
  before each write, which keeps its format and line endings.
- `arena_snapshot.snapshot` is split into `take` (the requests and raw files) and `write_summary`, so the
  watch writes a summary only for the agent it follows.
- Progress goes to the console and to the upload's `checks/arena.log`, with the captured output of the
  tools it ran.

Checks:
- Tests `tools/tests/test_arena_follow.py` (a simulated arena on a fake clock, a fake Gradle):
  - a paste during the follow;
  - a promotion during the placement;
  - another paste;
  - resume after an interruption;
  - another configuration under a label;
  - `status`.
- `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`: 51 OK.
- `./gradlew test --tests cr.submit.ArtifactInfoTest --tests cr.submit.BuildTest`: passed.
- `arena.gradle(["-q", "artifactInfo", "-Pfile=<main>/artifacts/5c16d925/Player.java"])` from Python on
  Windows printed sha256 `5c16d925…`, build `3f19aa6e`, config `c9dad766`, as in the registry.
- `python -B -I tools/arena.py status` live: agent 6844095 in Silver, 100 %, score 20.53, rank 5 of 201;
  registered as v08 upload 4, 80 games saved.

The layout (`docs/data/arena-layout-2026-11-04/`, `migrate.py` and `record.json`):
- Dry run, then `--apply` on the main checkout's `replays/arena` (main at 6722a27, clean). It moved 374
  files:
  - 47 top-level files of upload directories (logs, reports, `*.sh`) into `checks/`;
  - the 2 snapshots of `unknown/bronze/agent-6829912` into v03 upload 3;
  - 325 files from `route/` to `v04/`.
- The 2,738 file hashes are the same set before and after; every upload's digest is unchanged except
  v03 upload 3 (66 → 68 files).
- Checked afterwards: every upload directory holds `games`, `snapshots` and `checks` only, and all 1,310
  games of every manifest are at the manifest's `rawDirectory` with their sha256.

The rename in the tracked files:
- the registry's label v04 with `formerLabel` for agents 6836411 and 6836589;
- `replays/arena/route/` → `replays/arena/v04/` in their two games manifests and two placement summaries;
- the path in `CgStudyTest`;
- the two rows in `docs/BOTS.md` (Uploads);
- `arena_compare.py` now prints `label v04 (config 347b908d): placement 22.89 22.10, mean 22.50`.

Older DEVLOG entries, `docs/arena-uploads.md` sections and `docs/data/study-2026-11-03-losses/README.md`
keep `route` and the old file locations (the record maps them). Committed and fast-forwarded into master
on the user's request; until then the main checkout's registry still said `route` while its folder was
already `v04`.

Records: `.ai/project.md` (the row "Загрузка на арену одной командой" and the placement watch row),
`docs/arena-uploads.md` (procedure, Replay storage, Tools), PLAN, SUMMARY.

## 2026-11-04 — CgAttributeTest: v01's parameters from its snapshot (the test had failed since v06)

After the merge, the full suite in the main checkout, which has the replays, gave 147 of 148.
`CgAttributeTest.realPlacementGamesAreAttributedToTheBotThatPlayedThem` failed. It checks that
`current?<v01's params>` replays v01's placement games. Its hand-written subset
(`INCOME_TARGET=10,MAX_TOWERS=4,UNSTICK=0,ROUTE=0,MINES_FIRST=0`) was last updated at the v05 freeze.
v06-v08 then changed current's defaults: BARRACKS=2, BARRACKS_AFTER_TOWERS=4, TRAIN_SYNC=1,
THREAT_TOWERS_NEAR=2, THREAT_RADIUS=500, KITE=2 and KITE_PULL=100. The worktrees, without replays, skip
the test, so nobody saw it fail.

Evidence (main checkout):
- `cgAttribute` of v01 upload 1, all 60 games: SAME 4 of 60 with the old subset;
- SAME 60 of 60 with those flags at v01's values.

Fix:
- The test reads `PARAMS` from `src/main/resources/cr/bot/versions/v01.properties`, the snapshot that every
  new flag already records at v01's behaviour, so it cannot go stale again.
- With the two games copied into the worktree (and removed afterwards):
  `./gradlew test --tests cr.tools.CgAttributeTest` gave 8 of 8, the real-games case passed, not skipped.
- After the merge (master at 9a04817), the full suite in the main checkout passed 148 of 148, none
  skipped; the tools' tests passed too.

## 2026-11-04 — v08 upload 5 (Silver): 20.21, rank 6/201; the first run of `tools/arena.py follow`

The user re-uploaded v08 ("what if I run the current version again"). Asked about the order, they started
`python -B -I tools/arena.py follow v08` in the main checkout before the paste. Main's
`build/submit/Player.java` was v03's `d6266d41`, so the file to paste was the kept copy
`artifacts/5c16d925/Player.java`.

The run (`replays/arena/v08/silver/upload-04/checks/arena.log` until the registration, then
`upload-05/checks/arena.log`):
- 22:50, with `TimeoutError` retries from the leaderboard service: the live entry v08 upload 4 saved, a
  snapshot and 90 games. 10 of them were played after its placement (3 won). Then "paste the code".
- The user pasted after 22:56. `status` and a read of the old agent's battle list (both read-only, from
  this session): at 23:04 the leaderboard still showed 6844095 at 100 %; at 23:05 its battle list was
  empty, so the paste had reached CG.
- 23:06:49: the leaderboard showed agent 6847999 (created 22:59:16, already 43 %). The command fetched upload 4
  once more (nothing new), saved a snapshot of the new agent and registered it as v08 upload 5:
  submission 41637774, the kept copy's identity reused (same bytes), upload 4 `replaced`.
- Snapshots: 43 % 18.96 (rank 13) at 23:06; 65 % 19.18 at 23:11; 76 % 19.29 at 23:14; 86 % 20.02 (rank 6)
  from 23:17:53 to 23:23:53, with all 80 games finished from 23:18:53; 100 % at 23:24:50, score 20.21, local
  rank 6 of 201, global 465, `eligibleForPromotion` false.
- Fetches every minute (the first one, of 37 games, took five minutes under the timeouts). All 80 games had
  marker `3f19aa6e`, no failures; the manifest is `docs/data/arena-6847999-games-2026-11-04.json`.
- `cgAttribute` against the kept copy: SAME 80/80, 15,296 turns.
- `arena_compare.py`, without a verdict:
  - upload 5 at 60.0 % won and performance 20.27 ± 0.73;
  - v08 in Silver: placements 20.70, 20.74 and 20.21, mean 20.55, pooled 20.40 ± 0.42 over 250 games
    (scale 3.08);
  - noise: Silver's own M 0.59 (2 df); the verdicts use Bronze's 2.46.
- No failure and SAME 80/80, which is exit 0 by the tool's rule. The run took 35 minutes (22:50-23:25).

Details:
- Seats 28 of 43 and 20 of 37; 39 opponents, median 18.97; no opponent removed; 38 of 80 games to turn 250.
- The Silver boss (1876720, 21.87-21.99): 3 games, 1 won (916722076).
- 916719390: both queens die on turn 146, 0:0. The game result's ranks are [0, 1] with us as player 0,
  counted as a win.
- That game is why the snapshots' battle-list count says 47 wins and the manifest 48. The battle list gives
  both players position 0 there, and a check of every saved list shows the same for all five of our
  equal-score games (`docs/rules/inferred.md`).

Records:
- the registry: notes added to upload 5's placement and upload 4's `replaced`, by hand;
- BOTS (the v08 row, uploads 4 and 5, the artifact row), STATUS, SUMMARY, PLAN, `docs/arena-uploads.md` (new
  section), `docs/rules/inferred.md`.

The tool-written files of the main checkout were copied into this branch to commit. Seen in the run and left
for later (PLAN):
- the paste prompt does not name the file;
- it does not notice a paste the leaderboard has not listed yet;
- the next fetch counts from the start of the previous one.

## 2026-11-04 — `tools/arena.py follow`: the file to paste, a paste CG has not listed, the build checked before the registration

The user asked for the improvements left open after upload 5 right away.

Changes to `follow`:
- **The artifact first.** It is chosen, identified and checked against the label's configuration before
  the live entry is saved, so a wrong file or label stops before the paste.
- **The prompt** names the file to paste with its sha256 and build id. When `build/submit/Player.java`
  holds other bytes, it adds "not build/submit/Player.java, which holds other bytes (<sha>, <label>'s
  artifact)". At upload 5 main's `build/submit` was v03's.
- **A paste CG has not listed.**
  - If the save finds the live agent's battle list empty, the command says "CG has a paste already" and
    asks for no paste.
  - While waiting, it reads the old agent's battle list every minute. An empty list means "CG has the
    paste; waiting for it to list the new agent (do not paste again)" and gives the wait its full length
    again.
- **The build before the registration** (`check_build`). The new agent's first finished games are read,
  at most three, and the first one with a marker decides; a first finished game is waited for up to 10
  minutes. Another build stops the command: nothing is registered, and the message names the build's
  upload when the registry knows it. The replays stay under `unknown/`, where `arena_games` takes them
  after a registration. Empty `unknown/` directories are pruned after each fetch.
- **The watch** stops on games with another build's marker (`arena_games` failures "differs from
  registered build"). It counts the next fetch from the end of the last one, so a slow fetch is no longer
  followed at once by another.
- **Resume.** A complete entry without `attribution` (a follow stopped after the placement) resumes under
  its own label, as before; the docs now say so.

Tests in `tools/tests/test_arena_follow.py`, six new:
- a re-upload names the kept copy and warns about `build/submit`;
- a paste the leaderboard does not show yet is noticed;
- started after such a paste, nothing asks to paste;
- another build stops before the registration, the registry unchanged and the replay kept;
- the next fetch counts from the end: checked to fail with the old schedule on a copy;
- another build's games stop the watch.

`python -B -I -m unittest discover -s tools/tests -p "test_*.py"`: 57 OK.

Records: `.ai/project.md` (the row, then sync), `docs/arena-uploads.md` (Tools), PLAN, SUMMARY.

## 2026-11-05 — The architecture page checked against v08's code and rewritten

The user asked for an analysis of the current bot and for `docs/bot-architecture.html` to be brought in
line with it in every part (the pseudo-code, the target choice, the queen's movement), with what the bot no
longer uses removed or moved to the background. The bot's code has not changed since the v08 freeze
(da6384a); the page last changed in 20e2b8b, before the promotion to Silver. No code changed now.

Method: two read-only workflows over the page, the code (v08 defaults) and the records.
- **Audit:** 9 auditors by section group, each group's findings checked by a skeptic. 165 findings: 129
  confirmed, 35 partly (the correction adjusted), 1 refuted. By kind: 8 wrong, 57 imprecise, 33 stale,
  24 missing, 43 code quirks.
- **Review of the rewritten page:** 6 reviewers (651 claims checked) and 6 skeptics. 24 errors reported, 23
  kept and fixed (the refuted one was reworded for clarity anyway).
- Raw outputs, main checkout, gitignored: `replays/study/audit-2026-11-05-architecture/audit-findings.json`
  (sha256 41433eb9f907391f452b6f62c8e3b04684b64fec00c8a837ccaa7c1a1495fef8) and `review-errors.json`
  (c287f8fdf52740b8b88c51f1e6cbaeac637bb28f420cccd83672705e3a7af0ca).

What changed on the page:
- **State:** league Silver; v08's three Silver placements in a fact tile and in a Silver column of the
  versions table.
- **Rule order:** the lead and the flow give the order of `MainBot.play`: defence, kiting, touch rules,
  flight, the ladder via ROUTE, the idle home tower. The defence edge now covers "no eligible site", and
  TRAIN is drawn apart from the queen's command.
- **"Easy to miss":**
  - under threat the kiting now pre-empts the touch builds (stale since v07);
  - the ladder is recomputed every turn, so lost mines reopen their step;
  - a far BUILD is only a walk;
  - a double touch gives `touched` = −1.
- **Runner:** the six warm-up copies (with v08 the knight copy hits the defence rule) and v08's marker
  `TRAIN 3 15 1 9 10 10 6 14`, plus the SLOW timing.
- **Pseudo-code at v08's values:** the kiting threshold is ≥ 5 and the heading origin is stated. The target
  search follows the code: the two-mines step comes before the ladder check. Site gold and the player's gold
  are told apart, and the TRAIN cap is shown.
- **Target choice:** the filter of each ladder step. Unseen sites count as gold sites (225). The target is not
  stored, and the BUILD type has two uses.
- **"Движение королевы" (new):** which commands move the queen. Only the ladder BUILD goes through ROUTE; the
  defence, flight and idle walks go straight.
- **Defence:** the tower damage is 3 + ⌊(range − d)/200⌋, so a top-up raises it by a third, not a quarter.
  Creeps also lose 1 HP a turn. Towers are counted by centre distance, and the cap is not checked.
- **Kiting:** rewritten for KITE=2 first; v07's rule and study moved to the history.
- **ROUTE:**
  - the clean-walk test: each step's segment against range + 2, and the straight bound;
  - B and the thresholds of its quiet-step check;
  - 23 headings, 180° searched twice;
  - the landing ring r+33 and the same-B filter;
  - the plain BUILD fallback, which always applies when the queen stands in an enemy range.
- **Versions:** the table gains defence, kiting and walk columns. UNSTICK and KITE_COVER are marked inert in
  v08. The top-up sentence now says "worse than 700", not "loses to v04".
- **File map:** the planners' roles; `queenStep` serves ROUTE and kiting; rows for `cr/runner`,
  `cr/referee`, `cr/tools` and `FileMerger`.
- **Gaps:** the enemy-tower claim is corrected (ROUTE does avoid the range on the ladder walk). The turn-250
  HP race and the enemy queen are added. The top-up numbers carry their version.
- **"Особенности кода" (new):** nine behaviours that look unintended (below).
- **Directions:**
  - open ones first: the PV re-check in Silver, the HP race, untried queen safety, archers and giants, route
    steps 2-4;
  - the closed ones in their own table;
  - the base is v08, and PV lapsed in Silver.
- **History (collapsed):** UNSTICK, APPROACH, v07's kiting, v05's arena profile.

Code quirks put on the page, none fixed:
- the flight and the idle tower can choose an own tower whose centre is in an enemy tower's range, which the
  touch rule skips (`MainBot.java:106-108, 137`);
- the straight walk's unreachable B (`nextTurns` −1) ranks as "B within a turn" (`RoutePlanner.java:82-93,
  172-177`);
- a 2-3 MOVE plan sends its last prefix target, not the first (`RoutePlanner.java:105, 120-129`);
- the hysteresis compares headings exactly against a fan rebuilt from the new bearing
  (`RoutePlanner.java:97-101, 180`);
- +180° and −180° are searched twice (`RoutePlanner.java:99-101`; UNSTICK `MainBot.java:307-309`);
- `diverts()` lacks the MINES_FIRST branch (`RoutePlanner.java:240-243`);
- an exhausted mine out of sight keeps its last gold (`InputReader.java:73`);
- the mine phase with no known gold site returns −1 (`MainBot.java:275-278`);
- the warm-up reaches neither the kiting loop nor the flight (`CGRunner.java:102-104`).

Two fixes would not change the play: dropping the −180° duplicate (the last heading, never chosen) and
the warm-up (the runner only; a new artifact needs `submit` and both `procCheck`). The others change the
play and would be P2 candidates.

Stale comments and records found and not changed (comments do not reach the artifact):
- **Bot comments:**
  - `MainBot`'s class javadoc (no defence or kiting; "Trains whenever affordable"; ROUTE as the option) and
    `kite`'s javadoc (KITE=1 only);
  - `Params` comments: short version ranges (THREAT_RANGE, INCOME_LATE, APPROACH, TOWER_HP, UPGRADE) and
    "a quarter" at THREAT_TOWERS_NEAR;
  - `CGRunner.warmUp`'s "the flight from a knight (MOVE)";
  - `ApproachPlanner.nextTarget`'s "an unknown mine gives no next target" (InputReader never stores −1, and
    `InputReader.goldKnown` is never read).
- **Tests:**
  - `CGRunnerTest` runs "" and "ROUTE=1", the same configuration since v04 (its comment still says
    "UNSTICK with the defaults");
  - `StrategyParamsTest`'s javadoc links the absent `withDefaultsCurrentPlaysAsV05`;
  - `RoutePlannerTest.V02` sets MAX_TOWERS twice (8 wins);
  - the `HORIZON_SLACK` comment of `theSearchIgnoresTheClock` is out of date.
- **Records:**
  - `docs/BOTS.md`'s flags table gives the defaults the flags had when introduced (BARRACKS 1 and 0,
    THREAT_TOWERS_NEAR 0 and 400, TRAIN_SYNC 0, KITE 0) and cites absent `withDefaultsCurrentPlaysAsV02…V07`;
  - its `current` row ends "(= v05)";
  - `.ai/project.md` (the marker row) says a repeated site was never seen live, but v08's marker repeats
    site 10 in all its games.

Checks: the page opened in the built-in browser (a static snapshot). Both mermaid diagrams render as
flowcharts, there are no console messages, and all 33 internal links resolve. At 375 px there is no
horizontal scroll. Dark mode was not checked: the static snapshot always renders light, and the colour
tokens are unchanged. Docs only: no code changed and no tests run.

Records: SUMMARY, PLAN.

## 2026-11-05 — Study of v08's Silver games and of the local proxy against the Silver field

The user's "start the study", after the session's recommendation: a study of v08's 250 Silver games that
also re-checks the local proxy, whose validation (PV1/PV2) lapsed with the promotion. The quick pass the
same day (`cgStudy` only, scratch scripts) had suggested two things. The HP races are decided on turns
25-50. Giants are no special weakness once the opponents' ratings are accounted for (logistic scale 1.33).
The first held up only in part, the second not at all (below). Record: `docs/data/study-2026-11-05-silver/`
(README, `silver.py` → `silver.txt`, `start_hp.py` → `start_hp.txt`, `local-profiles.txt`).

**Inputs** (gitignored, main checkout; the commands are in the README):
- `replays/study/features-v08-silver-2026-11-05.csv` (sha256 8a2d927c827316e2f85b17ab037bcb10cfe8086739c649e4f3f02c5a49c6f421)
  and `features-v08-bronze-2026-11-05.csv` (ff28ef5d591050a456639074a3f6cbab704d0749d48156241638a021f7130ec0);
- `replays/study/traces/all-v08s-ours.txt` (a5f645fa7dfe868ccb85bf677eebd3495efbe7f2e9757d98921da24b61c3c0cd)
  and `all-v08s-opp.txt` (ffc38dbe3499452d29218d320d0142edd1efab8bfcb9323a68125cd15230cf9a), with the per-upload
  pieces beside them;
- `replays/bench/study-2026-11-05-silver/`: the bench reports `v08-vs-v08.json` (5ad0a9ee…), `v08-vs-field.json`
  (e85d7a8a…), `v08-vs-field-giants.json` (7db705c3…), `v08-vs-boss.json` (29e96e5d…), and the run logs
  `runs.log` (2e179b86…) and `runs-strided.log` (701902d9…). Development seeds 150000-150499 and 1 + 397k, k < 500.
  The ledger is not touched.
- The study ran in a worktree; `refs/referee-csj/config/level4/Boss.java` was copied there (sha256 a1a3f043…,
  identical).

**Tool changes** (the bot, `current` and the artifact unchanged; `cr/tools` is not shipped):
- `cr.tools.DeathTrace`: appended columns `kd` and `td` split each turn's HP loss by source, from two probe
  copies of the turn: enemy tower ranges set to 0, or enemy knights removed. The split misses the loss on 20
  of 4,944 turns with a loss. A `foe` flag traces the other seat, and the header ends with the traced agent.
  The parsers of the 2026-11-03 studies match without an end anchor, so they still read the new lines.
- `cr.tools.LocalProfile`:
  - **bug fixed.** `median()` filtered on "the last column" as the game length. That stopped being true in
    da6384a (2026-11-03), when the four under-knights columns were appended: from then on the turn-50/100/150
    medians, the waves and the game length were filtered on the MOVE share. Named column indices now.
  - The contact-turns median keeps its meaning (games with contact), and the records after da6384a quote only
    the under-knights line (`docs/data/exp-2026-11-03-kite2/`), which never used the filter. So no recorded
    number changes.
  - With stride 1 the fixed tool printed this study's first profile again, line for line.
  - New output: the first contact, the HP lost on turns 25-50, the games at the limit, the close ones, and the
    maps' starting HP. An optional seed stride.
- `build.gradle`: tasks `deathTrace` (`-Pagent -Pall -Pfoe -Preplays`, stdout) and `localProfile`
  (`-PbotA -PbotB -Pmaps -PseedBase -PseedStride`).
- `.ai/project.md` is not updated with the two tasks: its edit needs the kit's synchronizer, not run here.

**Results** (the README has the tables):
- **Outcomes.** 250 games: 153 won, 95 lost (61 deaths, median turn 166; 34 on HP at the limit), 2 draws.
  48 % reach the limit (Bronze 26 %). The starting HP sets how a game ends (28 % at the limit at 25-40 HP,
  68 % at 85-100), not the score (60-64 % in every band).
- **Damage.** Knights 7,083 of the 7,897 HP our queen lost, enemy towers 805.
- **The first wave.** First contact at turn 23-28; our second tower stands at turn 25 in every group.
  - One own tower then (86 games): 12 HP in 20 turns, score 48 %.
  - Two (132 games): 5 HP, 65 %. Three or more (28): 0 HP, 82 %.
  - The races we win build their lead there (+10.3 HP on turns 25-50; 70 of 85 ahead after turn 50).
  - The Silver boss: one tower at its first wave in 12 of 16 games; 6 of the 10 losses decided on turns 25-50.
- **The late game.** Races lost: the lead falls most on turns 200-250 (−6.8 mean).
  - 10 of the 34 were led after turn 200; there the queen lost 278 HP to knights, walking to build, topping
    up or kiting.
  - 21 of the 34 were lost by 10 HP or less.
- **Giants.** 62 of 250 games; score 40 % against 69 %.
  - The logistic scale fitted on these games is 3.16; Bronze's 1.33 fits far worse (log-likelihood −167.6
    against −154.6).
  - With 3.16 the performance rating is 19.02 ± 0.84 against giant players and 20.96 ± 0.52 against the rest;
    LR 4.66, 1 df, p 0.03, the scale re-fitted under each model.
  - With 1.33 the two are equal: that was the quick pass's claim, now withdrawn.
  - Against giants our first tower falls around turn 138, and the queen dies late (turn 196).
- **The strongest four** (the boss and three players at 20.9-21.7): 16.5 of 55.
  - Per contact turn our queen loses less (0.5 HP against their 0.8-1.1).
  - After turn 100 their knights reach her on 32 turns against 9 for ours near theirs. Income 6 against
    3, waves 10-13.5 against 6-7 (the boss: 10 against 11).
- **The proxy.** `MapGen`'s first draw is the starting HP, `Random(seed).nextInt(16)`.
  - Consecutive seeds barely move it, so local blocks play 1-6 of the 16 HP values. The study's block: 484 of
    500 at 35; the `field` calibration: all 200 at 35; P2's screen and evaluation blocks: 4-6 values each.
  - CG: all 16, median 65. The formula matches the start of all 250 replayed games.
  - With spread seeds (stride 397) the mirror reaches the limit in 90 % of the games, 70 % of those close
    (Silver 48 %, 33 %); `field` 58 % (21 % on the 35-HP block); `boss` 0 %.
  - The first contact is local-like everywhere (turn 19-24, one tower, 5-7 HP).
  - No local opponent plays giants as the field does (`field?GIANTS=1` loses 94 %). `field` out-expands the
    strongest four (income 8, 18 waves) but never kites.

**What follows** (the README; the user's decisions):
- PV3 proposed:
  - P2's series spread over the starting HP, with a seed stride in `bench`, `bench_pair.py` and the ledger
    (new blocks in their own seed range);
  - the panel kept, with what each opponent stands for recorded;
  - giants and the late game of strong economies recorded as unrepresented, judged by the arena.
- Candidates in order:
  1. a second tower before the enemy's first wave;
  2. holding an HP lead after turn 200;
  3. a giants response, once a local giant opponent exists;
  4. the economy against the strongest.

Checks: `./gradlew compileJava` and `./gradlew test` (148 tests, 0 failures, 3 skipped for want of the arena
replays in the worktree: CgAttributeTest, CgStudyTest and CgViewReplayTest's corpus tests). The page's tags
balance, and in the built-in browser (a static snapshot) the directions table renders with no console
messages. No commit (not asked).

Records: STATUS, SUMMARY, PLAN, the architecture page's directions.

## 2026-11-05 — PV3 adopted: P2's blocks move to strided seeds; the panel's coverage recorded

The user's decision on the Silver study's proposal ("Да, принять PV3"). PV3 renews P2's proxy validation
for Silver (PV1/PV2 lapsed with the promotion). It has two parts:
- the screen and evaluation blocks move to strided seeds, so every block plays all 16 starting HPs;
- the record of what each opponent represents in Silver, and what none of them does (giants, the late game
  of strong economies), whose candidates the arena judges.

Policy text: `docs/local-screen.md` (status, PV3 section, seeds table, commands) and `.ai/project.md`
(P2 section and the promotion table). `AGENTS.md` and `.ai/kit.lock.json` were regenerated by
`python -B -I .ai/sync.py sync . --source C:/Disk_D/Programming/CG/cg-agent-kit`. `sync` keeps the locked kit
commit f4a5bd63, which is also the local kit's HEAD. Only the `AGENTS.md` digest changed in the lock, and
`check` reports OK.

**Tooling:**
- `cr.runner.BenchmarkRunner`: an optional eighth argument `seedStride` (`./gradlew bench -PseedStride=397`).
  - The maps are seedBase + stride k; a stride below 1 is refused.
  - The report records `seedStride`, and the summary line prints the seeds when the stride is not 1.
  - Without it nothing changes.
- `tools/bench_pair.py`:
  - a report's seeds are seedBase + seedStride k; a report written before PV3 has no `seedStride` and reads
    as stride 1;
  - a non-positive or non-integer stride is invalid;
  - paired reports must agree in base, stride, count and turnMs;
  - the summary's inputs carry the stride.
- Tests:
  - `tools/tests/test_bench_pair.py`: strided pairing in both modes, a stride that does not match the games,
    a stride mismatch between paired reports, the default stride of an old report;
  - `cr.runner.BenchmarkRunnerTest` (new): the strided seeds and the recorded stride, consecutive seeds
    without a stride, a stride of 0 refused.

**The pools** (`docs/data/seed-ledger.json`; the 18 used blocks unchanged):
- `screen-pv3`, block k = 1000000000 + 1000000 k + 397 i, i < 2000; `evaluation-pv3`, block k =
  2000000000 + 1000000 k + 397 i. Both next at k = 0; every seed stays below 2^31 for k < 140.
- The consecutive pools `screen` and `evaluation` are closed; their verdicts stand.
- Checked with `docs/data/study-2026-11-05-silver/start_hp.py`'s formula: every block k < 20 of both pools
  plays all 16 starting HPs, 110-143 maps each of 2000 and 23-41 each of the first 500, which the process
  opponents play.
- The other map features do not depend on the stride. A throwaway MapGen check (scratchpad, not kept)
  compared four seed sets: the 1,400 CG seeds of the manifests, the old screen block 0, 1 + 397 k and PV3
  screen block 0.
  - The same in all four: 24 sites, gold 253 per site, max mine size 2.56-2.57, radius 74.9-75.1, queen 0
    at x 221-223.
  - Only the mean starting HP differs: 62.9 on CG, 69.6 on the old block, 62.4 strided.

Checks:
- `./gradlew test`: 151 tests, 0 failures, 3 skipped (the corpus tests that need the arena replays, absent
  from the worktree).
- `python -B -I -m unittest discover -s tools/tests -p "test_*.py"`: 59 OK.
- The visualizer's node tests: 18 of 18.
- End to end on development seeds 1 + 397 k, k < 20 (`replays/bench/pv3-check-2026-11-05/`, main checkout):
  `current` against v08 and the v08 mirror through `bench -PseedStride=397`, then `bench_pair.py direct
  --mirror`. It reads "seeds 1 + 397 k, k < 20", S 0.5000, 0 changed maps, pass: `current` still plays as v08.

No candidate was screened, so no PV3 block is used yet.

## 2026-11-05 — The first-wave tower (WAVE_TOWERS, TOPUP_FROM): loses on the development seeds, not screened

The user's "Да, начинай": the Silver study's first candidate (`docs/data/study-2026-11-05-silver/`). Hypothesis:
a second own tower before the enemy's first knight wave cuts the first-contact HP loss and wins more HP races.
In Silver one tower meets the wave in 86 of 250 games, with 12 HP lost in 20 turns and a score of 48 %; two
towers lose 5 HP and score 65 %. v08's second tower comes at turn 25, after about six top-ups of the first.
Record: `docs/data/exp-2026-11-05-first-wave/` (README, `sweep.sh`, `sweep_table.py` → `sweep.txt`,
`profiles.txt`).

**Code** (`current`, off by default, so `current` still plays as v08):
- `Params.WAVE_TOWERS=N`. While an enemy knight wave is coming (`MainBot.knightWaveComing`: an enemy knight
  barracks trains or enemy knights are on the board) and fewer than N own towers stand, the ladder sees
  the income target as met, so towers come before the income phase. The queen neither upgrades a touched
  mine nor tops up a touched tower.
- `Params.TOPUP_FROM=N`: the touch top-up waits until N own towers stand.
- Both are written as 0 into the snapshots v01-v08 (`BotFactoryTest` requires every field there).
- Tests: `StrategyParamsTest.waveTowersBuildTheSecondTowerBeforeTopUpsAndMines` and
  `topUpFromWaitsForTheTowerCount`. `StrategyParamsTest`, `MainBotTest` (the identity of `current` with
  v08 and the older snapshots) and `BotFactoryTest` pass.

**Development sweep**: seeds 1 + 397 k, k < 500, 8 threads, 50 ms. Raw reports in
`replays/bench/dev-first-wave-2026-11-05/` (main checkout, gitignored):
- ctrl-field 0b6a9e1a…, wave_towers-2-v08 93874d66…, wave_towers-2-field 1d569e5f…, wave_towers-3-v08 6b00cd7c…,
  wave_towers-3-field c72c4293…, topup_from-2-v08 a3282731…, topup_from-2-field 068c8d6d…;
- the combined arm: …-v08 a086c980…, …-field 013773bd….

| Arm | S against v08 | Δ against `field` |
| --- | --- | --- |
| `WAVE_TOWERS=2` | 0.4675 (0.4344-0.5006) | −0.0370 |
| `WAVE_TOWERS=3` | 0.3135 (0.2817-0.3453) | −0.0940 |
| `TOPUP_FROM=2` | 0.4620 (0.4292-0.4948) | −0.0335 |
| both (2, 2) | the same games as `WAVE_TOWERS=2` | |

`v08` against `field` scores 0.9315 here. The arms had 1-8 slow turns in these series, but no gate is judged
on development seeds.

**Profiles** (`LocalProfile`, 200 maps 1 + 397 k against v08):
- The arms meet the first wave with two towers (v08: one), at turn 23.
- The 20-turn loss: median 3 against 5 HP, but mean 7.3 and 8.2 against 7.8.
- At turn 50 the arms' towers hold 1,464 and 1,436 HP against v08's 1,648.
- The queen dies in 36 games of 400 against 18.

**Verdict:** every arm fails the development bar (S < 0.50, Δ < −2 pp against `field`); no screen, no PV3
block used, the flags stay off.
- The arena correlation probably stands in for the opponent's timing: the one-tower games met the wave
  earlier (turn 23 against 28).
- In Silver a single tower loses more at the first contact (12 HP) than in any local series (5-7 HP); the
  Silver races lost at the limit faced 7 enemy knights at turn 25, those won 4.
- No local opponent sends such a wave. This is a limit of the panel beside giants (the README says so; PV3's
  record is left as it is until the user decides).

Records: BOTS (flags table), STATUS, SUMMARY, PLAN, the architecture page (directions, parameters), the
Silver study's README (the candidate's outcome).

## 2026-11-05 — Holding an HP lead late (LATE_HOLD=200): passes the P2 screen on PV3 block 0, S 0.527 against v08

The user's "Да, начинай": the Silver study's second candidate. Evidence from the traces of the 10 races lost
at the limit from a lead after turn 200:
- In the last 50 turns the queen lost 292 HP: kiting 111, topping up under knights 94, walking to a tower
  under knights 73, the rest building in place or walking to a mine.
- In seven of the ten the ladder reopened after turn 200 (mines ran dry) and walked her out to a mine with
  no knight near. The others walked to tower sites.

Record: `docs/data/exp-2026-11-05-late-hold/`: README, `sweep.sh`, `sweep_table.py` → `sweep.txt`, `screen.sh`,
`rerun.sh`, `same_games.py`, and the `bench_pair.py` summaries `g1*.json`, `g2-rerun.json`, `g4*.json`.

**Code** (`current`, off by default):
- `Params.LATE_HOLD=T`: from turn T, while `s.hp[0] > s.hp[1]` (the limit's score), the ladder has no target,
  so the idle rule builds the own tower nearest home. The defence builds only on a touched site.
- `LATE_HOLD=0` is written into the snapshots v01-v08.
- Test: `StrategyParamsTest.lateHoldKeepsTheQueenHomeWhenAheadLate`; the identity tests pass.

**Development sweep** (1 + 397 k, k < 500; raw reports in `replays/bench/dev-late-hold-2026-11-05/`):
- T = 100: 0.4830 against v08, +2.15 pp against `field`;
- T = 150: 0.5310, +2.25 pp;
- T = 200: 0.5310 (0.5190-0.5430), +1.25 pp;
- T = 225: 0.5110, +0.40 pp.

200 went to the screen alone: it ties 150 against v08 with the narrower interval, changes the least, and aims
at the arena's leads lost after turn 200.

**Screen** on PV3 screen block 0 (1000000000 + 397 k; the ledger's first `screen-pv3` entry). Raw reports in
`replays/bench/exp-2026-11-05-late-hold/` (main checkout, gitignored), sha256:

| report | sha256 |
| --- | --- |
| `direct.json` | cb204b67… |
| `direct-rerun.json` | 0db2dfd9… |
| `mirror.json` | e2c7e82d… |
| `boss-cand.json` | 749661d8… |
| `boss-cand-rerun.json` | 2d843be5… |
| `boss-ctrl.json` | f58ffd74… |
| `field-cand.json` | 1eb4e035… |
| `field-cand-rerun.json` | 3e6abc76… |
| `field-cand-rerun2.json` | 174b8244… |
| `field-cand-rerun3.json` | d681bdb1… |
| `field-ctrl.json` | e669e20b… |

Results:
- **G1:** S 0.5269 (CI 0.5208-0.5329), 665 of 2000 maps changed against the mirror (a strategy candidate).
- **G2:** Δ 0.0000 against `boss` (1000 of 1000 each).
- **G4:** Δ +0.0146 (CI +0.0105..+0.0187) against `field`: 0.9275 against 0.9129.
- **G3:** skipped (no `old`).
- **G0** by quiet repeats; every repeat played the same games (`same_games.py`: 0 of 4000 differ, apart from
  the six boss timeouts).
  - First runs: the candidate had 3 slow turns in the direct series and 1 against `field`. v08 had 5 in the
    direct series and 7 in its mirror. They fell on runs of neighbouring maps.
  - Boss: 6 boss timeouts made the first series invalid (more than 1 % of the maps).
  - Repeats: direct at 8 threads was clean. Field had 2 slow turns at 8 threads and 18 at 4, none at 2
    (max 9.6 ms). Boss at 2 threads was clean.
  - The host is an Intel Core Ultra 7 155U laptop (two performance and ten efficiency cores, an IDE and a
    browser open). Threads on the efficiency cores stall; 2 threads do not.

**Verdict:** passes the screen; the local champion by the policy.
- S < 0.60, so no upload recommendation: about +0.14 rating points at the Bronze scale, +0.34 at Silver's,
  inside M = 2.46.
- PV3: the mirror over-weights close HP races, so `field`'s +1.5 pp is the more representative gain.
- `current`'s defaults are unchanged (LATE_HOLD=0). Whether it becomes the default, the final evaluation, a
  freeze and any upload are the user's decisions.

Checks:
- `./gradlew test`: 154 tests, 0 failures, 3 skipped (the corpus tests, no arena replays in the worktree).
- The tool tests: OK.
- `./gradlew submit` with all three new flags off: 80,245 characters of 100,000.

Records: BOTS (flags table), STATUS, SUMMARY, PLAN, the architecture page, the ledger, the Silver study's README.

## 2026-11-05 — Freeze v09: the late hold (LATE_HOLD=200) as the local champion and the current default, not uploaded

The user's answer to "make LATE_HOLD=200 the default of current without an upload": "Да, по умолчанию". The
defaults of `current` always equal a frozen snapshot (`BotFactoryTest`), so the default comes as a freeze. P2
screen: `docs/data/exp-2026-11-05-late-hold/`.

- **Code:**
  - `cr.bot.v09.GreedyBot` with its own `RoutePlanner` and `ApproachPlanner` is a copy of current: the package,
    the class name, `name()` "v09", and no queen-step counter, as in the earlier frozen copies.
  - `versions/v09.properties` is v08's snapshot with LATE_HOLD=200, and `Params.LATE_HOLD` defaults to 200.
  - `BotFactory` creates and describes v09.
  - The local-games catalog lists v09 as the frozen local champion, v08 as the arena's selected version, and
    v09 as the default second side (`LocalGames`, `visualizer/local.js`).
- **Tests:**
  - `MainBotTest.withDefaultsCurrentPlaysAsV09` checks the fixture seeds and PV3 screen block 0's first maps.
  - `withV08ParamsCurrentPlaysAsV08` runs `current?LATE_HOLD=0`.
  - The older identity constants (V02, V05, V06, V07) gained LATE_HOLD=0.
  - `BotFactoryTest` checks the chain v08 → v09 and that the live defaults are v09's.
  - `LocalGamesTest`: 12 choices, default v09.
  - `StrategyParamsTest.lateHoldKeepsTheQueenHomeWhenAheadLate` sets v08's rule explicitly and checks the new
    default.
- **Results:** 155 Java tests, 0 failures, 3 skipped (the corpus tests); the visualizer's node tests 18 of 18;
  the tool tests OK.
- **Artifact:** `./gradlew submit` with empty PARAMS: 80,247 characters, now v09's play. It is not kept and not
  checked by `procCheck`, since there is no upload.
- **Not done:** no final evaluation (the `evaluation-pv3` pool is untouched), no upload. v08 stays the selected
  version and the rollback artifact stays `5c16d925`. The next P2 candidates are screened against v09.
- **Records:**
  - BOTS: the v09 row, the flags text (defaults play as v09), and the stale "(= v05)" of the `current` row
    corrected;
  - STATUS, SUMMARY and PLAN;
  - the architecture page: the versions table and the parameters;
  - `docs/visualizer.md` and `.ai/project.md` (v00-v09; `AGENTS.md` resynced at the locked kit commit);
  - the experiment's README.

## 2026-11-06 — A rival like the strongest Silver players: `field` with kiting, two mines first and capped giants (R0)

The user's "Да, начинай" to the proposed next step: a local opponent that plays like the strongest Silver
players, so that economy and giant candidates can be measured. Record: `docs/data/exp-2026-11-06-rival/`:
- `README.md`;
- `target.py` → `target.txt`;
- the scripts `calib1.sh`, `grid1.sh`, `grid2.sh`, `grid3.sh`, `ladder.sh`;
- their outputs, in `runs.txt`.

**Target:** the four agents rated 20.5+ in v08's Silver games against v08:
- their score 70 %;
- turn 100: income 6, 3 mines, 3 towers at 1,916 HP;
- 11 knight trainings, 1 giant (giants in 36 of 55 games, the giant barracks ordered at turn 84);
- the opening M M BK;
- under our knights, only 14 % MOVE and 1.07 HP per contact turn.

**Code** (not shipped; `cr.bot.field`). New `FieldParams` knobs, all off by default:
- `KITE=1` with `KITE_RANGE`, `KITE_PULL`: v08's kiting (`FieldBot.kite`, a copy of `MainBot.kite` with KITE=2);
- `MINES_FIRST`;
- `GIANT_TURN`;
- `GIANTS_MAX`, a counter of the giant trainings the bot ordered.

v08 against the default `field` on 1 + 397 k, k < 500, plays the same 1000 games as the first-wave sweep's
control, after the kiting and after the other knobs (`same_games.py`: 0 differ). Tests:
`FieldBotTest.kitingStaysUnderTheTowers` and `openingGiantTurnAndGiantCap` (`FieldBotTest` 4 of 4).

**Calibration** (v09 against the variant on 1 + 397 k; raw reports in `replays/bench/dev-rival-2026-11-06/`, main
checkout, gitignored):
- kiting alone lifts the ladder from 0.93 to 0.83;
- two mines first and giants from turn 80, at most 2: 0.80;
- with TOWER_HP=700 (R0): **0.706**, 0.703 on 500 maps.

Around R0 every knob was the same or worse (0.70-0.88): 3/5/6 towers, income 6 or 10, the giant turn 60/100, the
cap 1/3/4, no giants (0.76), no kiting (0.84), TOWER_HP=800 (0.88), the kiting pull and range. The ladder
plateaus near 0.70, far from the strongest four's 70 % against v08.

**R0's profile** against v09 (500 maps) next to the target:
- turn 100: income 5, 3 mines, 4 towers at 2,276 HP;
- 13 knight and 2 giant trainings;
- 74 % of the games at the limit (target 44 %);
- under knights 66 % MOVE (target 14 %) and 0.5 HP per contact turn (target 1.07).

**Validation, the version ladder against R0** (500 maps each; `ladder-v05.json` … `ladder-v09.json`, sha256 cacb45c8,
5df9fdf3, 898da981, 7bc41fd4, a1d37802): v05 0.269, v06 0.372, v07 0.412, v08 0.651, v09 0.703.
- The logit steps: v06 +0.48, v07 +0.17, v08 +0.98, v09 +0.24.
- They give the Bronze arena's order of steps (v06 +0.85, v07 −0.71, v08 +2.46 on the means). P2's direct S at
  the time put v07's step first (0.680 against v06).
- Three steps are a small sample, and R0 still gives v07 a gain where the arena showed none.

**Proposal (PV4, the user's decision):** R0 as P2's gate G5, Δ ≥ −2 pp on the block's 2000 maps, in-process. What
it does not represent is recorded in the README: the strongest players' strength, their low kiting, the
games decided before the limit. Policy text unchanged until the user decides.

Records: BOTS (the opponents), STATUS, SUMMARY, PLAN, the architecture page (giants), the README.

## 2026-11-06 — PV4 adopted: the rival R0 as P2's gate G5 (spec `rival`)

The user's decision on the proposal of `docs/data/exp-2026-11-06-rival/` ("Да, принять PV4").

**Gate G5:** Δ against R0 ≥ −2 pp on the 2000 maps of the block, in-process (8 threads, 50 ms), as G4.
- P2 now has gates G0-G5, and the upload recommendation asks for G0-G5 on the evaluation block.
- PV4 lapses as PV1 does.
- What R0 does not represent is recorded with it: the strongest players' strength (R0 29 % against v09, they 70 %
  against v08), their low kiting (14 % MOVE against R0's 66 %), and the games decided before the limit (R0 at the
  limit in 74 % of the games, they 44 %).

**The spec `rival`:**
- `BotFactory.create`/`describe` build `FieldBot` with `FieldParams.RIVAL`
  (`KITE=1,MINES_FIRST=2,TOWER_HP=700,GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2`) before any overrides, so a report records the
  full configuration as "rival …".
- `BotFactoryTest.rivalIsTheFieldBotWithTheRivalParameters` checks the description and that three games equal those of
  the spelled-out `field?…` spec.

**Policy text:**
- `docs/local-screen.md`: the status, the opponents table, the PV4 section, the gates table, the upload
  recommendation, and the commands (the example's control is now v09, the local champion);
- `.ai/project.md`: the P2 section and the promotion table, with `AGENTS.md` resynced at the locked kit commit
  f4a5bd63 (`check` OK). The bench row's list of specs that take parameters is corrected: it named only `current`
  and `v01`, while v01-v09, `field` and now `rival` take them (`submit`, `boss`, `old` and `v00` refuse them).

The verdicts before PV4 stand; v09 against R0 on the development seeds was +5.2 pp over v08's (the rival experiment).

Records: BOTS (the opponents), STATUS, SUMMARY, PLAN, the rival README, the architecture page.

Checks: `./gradlew test` 158 tests, 0 failures, 3 skipped; `tools/tests` OK; the visualizer's node tests 18 passed;
the architecture page's tags balance; `.ai/sync.py check` OK. Nothing committed.

## 2026-11-06 — The late income against the rival R0: four development sweeps, five towers with the safe late income screened

The user's choice after PV4 ("Да, начинай": the income first, the giants next). Everything is in
`docs/data/exp-2026-11-06-income/` (README, `sweep.sh`, `sweep_table.py`, `sweep.txt`, `profile.sh`, `profile1.txt`,
`screen.sh`). Raw reports are in the main checkout: `replays/bench/dev-income-2026-11-06/` (68 development series)
and `replays/bench/exp-2026-11-06-income/` (the screen).

**Starting point:** commit 7e5e346 with the uncommitted rival and PV4 changes; each report records the status and the
sha256 of the changes.

**The profile tool.** `cr.tools.LocalProfile` gains the turn-200 line and an economy line: own mines built, upgrades,
mines torn down (gold left on the site) or depleted, the gold mined. v09 against R0 mines 723 gold a game with 4.6
mines built; R0 mines 1,204 with 8.1, and still loses 70 % of the games.

**The hypothesis and criteria** were written into the README before the first sweep. An arm is a candidate if, on the
development seeds 1 + 397 k (500 maps), it gains ≥ +2 pp against R0, scores S ≥ 0.50 against v09 and loses at most
2 pp against `field`. The best such arm goes to the P2 screen on `screen-pv3` k = 1.

**The sweeps** (22 arms, each against v09, R0 and `field`; the tables are in the README):
1. `INCOME_LATE` 4-8: +1.3..+1.9 pp against R0, S 0.478-0.485 against v09, ≈ 0 against `field`. `UPGRADE=1` changes
   19 of 500 maps (the touch rule already fills a new mine).
   - The profile: the income rises 5-8 %, about one wave. In the mirror our queen dies 63 times per 1000 games
     instead of 47.
   - The late phase starts when the ladder is complete, and the ladder has made our half's sites towers.
2. Safety conditions on the late walks, new flags off by default: `LATE_COVER`, `LATE_HALF`, `LATE_CLEAR`.
   `LATE_CLEAR=1000` brings the mirror to 0.498 and keeps +1.95 pp against R0.
3. The tower-mine balance and our own giants. New flags `GIANTS`, `GIANT_TURN` and `GIANTS_MAX`, off by default, play
   as R0's giants.
   - `MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=1000`: S 0.5090 against v09, +3.35 pp against R0, −0.20 pp against
     `field`, the first arm to meet the criteria.
   - Four towers lose the mirror (0.467), and mines before the towers lose (target 4: 0.481; target 5: 0.402).
   - Our giants lose heavily: 0.379 against v09, −21.6 pp against R0, −9.5 pp against `field`.
4. The neighbourhood of five towers.
   - Five towers alone lose (0.471, −4.0 pp against R0).
   - Without `LATE_CLEAR` the income loses the mirror (0.462).
   - Targets 4 and 8 with `LATE_CLEAR=1000` score 0.463 and 0.495; `LATE_CLEAR=600` scores 0.5035 and +3.65 pp.
   - So the mirror stays near even across the family, and the best arm's 0.509 is in part the pick's luck.
   - The pick rule, stated before sweep 4: the highest S against v09 among the arms meeting the criteria.

**Code** (all new flags off, `current` plays as v09):
- `Params`: `LATE_COVER`, `LATE_HALF`, `LATE_CLEAR`, `GIANTS`, `GIANT_TURN`, `GIANTS_MAX`;
- `MainBot`:
  - `lateSite` takes the conditions;
  - new `clearOfKnights`;
  - the giant barracks before the ladder's next site and on a touched free site;
  - `train` trains the giant barracks first and saves for it; the knights' sync counts only knight barracks, which
    changes nothing without a giant barracks;
- the snapshots v01-v09 record the six fields at 0;
- tests `StrategyParamsTest.lateIncomeSafetyConditions` and `giantsBarracksAndTraining`.

**The P2 screen** (`screen.sh`), PV3 screen block 1 (1001000000 + 397 i, i < 2000), candidate
`current?MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=1000` against v09:
- G1 **fails**: S 0.5016 (CI 0.4898-0.5135), 1,769 of 2,000 maps changed against the mirror.
- G4 passes: +0.31 pp against `field`.
- G5 passes: +1.79 pp (CI +0.48..+3.10) against R0, half the development estimate.
- G2 not established. The candidate scored 999 of 1,000 against `boss`, but `boss` timed out on 48 of 500 maps, a run
  of consecutive seeds (host load), so the series is invalid.
- G0 not established: 5 slow candidate turns at 8 threads, on neighbouring maps and on both sides; v09's mirror and
  R0 control had them too.
- Neither is repeated, because G1 decides.
- The verdict: not the local champion, v09 stays, the flags stay off. Recorded in `docs/data/seed-ledger.json`
  (`screen-pv3` next at k = 2).

About 0.3 rating points against R0-like opponents with Silver's scale: the economy as this ladder can express it is not
the lever. The next direction (README): an answer to the enemy's giants, measured against R0.

## 2026-11-06 — An answer to the enemy's giants against R0: archers screened as a targeted fix

The user's choice after the income experiment ("Да, начинай"). Everything is in `docs/data/exp-2026-11-06-giants/`:
README, `sweep.sh`, `sweep_table.py`, `sweep.txt`, `profile-archers-rival.txt`, `screen.sh`. Raw reports are in the
main checkout: `replays/bench/dev-giants-2026-11-06/` (31 development series) and
`replays/bench/exp-2026-11-06-giants/` (the screen).

**The problem.** `LocalProfile` gains a giants line (giants out, own towers lost while a giant touched them, the other
towers lost, the first giant at an own tower) and an archer count. Against R0, v09 meets 1.7 giants a game in 884 of
1000 games. They knock down 3.8 of its towers a game (other causes, 1.0) from turn 151. Our queen dies 135 times per
1000 games, against 62 when R0 trains no giants, and v09's wins fall from 74.8 % to 69.9 %.

**The answers**, new flags off by default (`Params`, `MainBot`; snapshots v01-v09 record them at 0):
- `GIANT_GUARD=D`: the queen tops up the tower a giant attacks.
- `ARCHERS=1` with `ARCHER_RANGE=R`: an archer barracks and archers before knights while a giant is out.
- `MainBot` also gains `walkTo` (the walk to a BUILD target, shared by the late income, giant and archer barracks
  rules), `guardTower` and `giantNearTowers`. `train` trains the special barracks first and syncs the knights over
  knight barracks only.
- Tests: `StrategyParamsTest.giantGuardTopsUpTheAttackedTower` and `archersAgainstGiants`.

**Hypothesis and criteria** were written into the README before the sweep: Δ against R0 ≥ +2 pp, S against v09
≥ 0.50, Δ against `field` ≥ −2 pp, on 1 + 397 k (500 maps); `field?GIANTS=1` as a check of generality.

The answers act only on enemy giants. Every arm scores exactly 0.5000 against v09 and plays v09's games against
`field`. By P2's letter the screen is therefore a targeted fix (fewer than 10 % of the maps changed against the
control's mirror): G0, S ≥ 0.500, Δ against `boss` ≥ −1 pp, a regression test. G5 against R0 is reported as the
evidence of benefit.

**The sweep** (7 arms):
- the guard loses at every radius: D 150/300/500 → −1.8/−3.3/−2.5 pp against R0;
- archers answering any giant, or one within 1000 of our towers, gain +3.05 pp against R0 and cost 1.3-1.45 pp against
  `field?GIANTS=1`, inside their intervals; within 600 they gain +1.75;
- archers with the guard: +0.9 pp against R0.

The tie between `ARCHER_RANGE=0` and `1000` was broken by the smaller loss against `field?GIANTS=1`, a rule set after
the tie appeared. The pick is `current?ARCHERS=1,ARCHER_RANGE=1000`. Its profile against R0:
- own towers lost to giants: 2.0 a game (v09 3.8);
- our queen's deaths: 80 of 1000 games (v09 135);
- enemy queens killed: 97 (v09 131);
- knight waves 8, and 1.2 archer pairs a game.

**The P2 screen** (`screen.sh`), PV3 screen block 2 (1002000000 + 397 i, i < 2000), candidate
`current?ARCHERS=1,ARCHER_RANGE=1000` against v09:
- the direct match: S 0.5000, 0 of 2,000 maps changed against the mirror, G0 clean;
- `field`: the same games as v09's;
- R0: +0.36 pp (CI -0.88..+1.60), 1,740 maps changed;
- `field?GIANTS=1`: -0.99 pp (CI -1.79..-0.19);
- boss delta 0.0000 with v09's games (passes the targeted fix's -1 pp);
- no slow turn, timeout or error in any series.

The development gain of +3.05 pp did not replicate. By P2's letter this is a targeted fix: it changes no map
against the control's mirror. In substance it changes 87 % of the games against R0 and gains nothing there.
Not adopted, by the user's decision against P2's letter ("Да, не принимать"); the flags stay off. The README records the policy gap: the changed maps are counted
against the mirror only. Recorded in `docs/data/seed-ledger.json` (`screen-pv3` next at k = 3).

## 2026-11-06 — When to release the knight waves: holding loses, the forward second barracks screened

The user's choice after the giants experiment ("Да, начинай": hold a wave until the enemy queen leaves her towers).
Everything is in `docs/data/exp-2026-11-06-waves/`: README, `wavescan.txt`, `sweep.sh`, `sweep_table.py`, `sweep.txt`,
`screen.sh`. Raw reports are in the main checkout: `replays/bench/dev-waves-2026-11-06/` (23 development series) and
`replays/bench/exp-2026-11-06-waves/` (the screen).

**The idea as proposed does not work.** `LocalProfile` gains a waves line. From turn 30 on, the enemy queen stands
outside all her towers' ranges on 0.9 % of the turns against R0 and 0.2 % in the mirror, and the shares at our
releases are the same. Tower ranges of 360-510 cover almost every move she makes.

**What a wave depends on.** `cr.tools.WaveScan` is new (`./gradlew waveScan`). It measures, for each of our waves from
turn 30, the HP the enemy queen loses in the next 35 turns on turns with our knights within 250. That damage falls
with her distance from the releasing barracks at release: against R0 8.2 HP below 600, 2.8 at 1000-1200, 1.8 at
1200-1500; against v09 3.8 and 1.6. That is a correlation, so the sweep tests it.

**Code** (off by default, `current` plays as v09):
- `RELEASE_NEAR=R` with `RELEASE_HOLD=T` in `MainBot.train`: a ready knight release waits until the enemy queen is
  within R of a knight barracks, at most T turns (`enemyQueenNearKnightBarracks`, `readySince`);
- the snapshots v01-v09 record both at 0;
- test `StrategyParamsTest.releaseWaitsForTheEnemyQueen`.

**Hypothesis and criteria** were written into the README before the sweep: S ≥ 0.52 against v09, Δ ≥ −2 pp against
R0 and against `field`, on 1 + 397 k (500 maps).

**The sweep** (7 arms):
- every hold loses heavily: R/T 800/15, 800/30, 1000/15, 1000/30, 600/30 → 0.332, 0.296, 0.377, 0.346, 0.254 against
  v09, from −6.9 to −19.8 pp against R0;
- the existing `BARRACKS_FORWARD`, the second knight barracks toward the enemy: 35 → 0.503 and +4.5 pp against R0;
  50 → 0.5425 (CI 0.515-0.570), +5.35 pp against R0, −0.75 pp against `field`;
- the pick, the only arm that meets the criteria: `current?BARRACKS_FORWARD=50`.

**The P2 screen** (`screen.sh`), PV3 screen block 3 (1003000000 + 397 i, i < 2000), candidate
`current?BARRACKS_FORWARD=50` against v09:
- G1 passes: S 0.5426 (CI 0.5293-0.5560), 1,837 of 2,000 maps changed against the mirror;
- G2 passes: Δ 0.0000 against `boss` (1.000 both);
- G4 passes: +0.81 pp against `field`;
- G5 passes: +3.09 pp (CI +1.56..+4.61) against R0;
- G0 by repeats:
  - the first run had slow candidate turns in 6, 6 and 11 games, and v09's own mirror in 22 and 21;
  - `rerun.sh` with 2 threads held the same 12,000 games, with 3, 4 and 3 slow games left on other maps (stalls up to
    330 ms, v09 one of 560 ms);
  - `maprepeat.sh` replayed those 8 maps one at a time in 1 thread: the same 16 games, clean (`map_check.py`, new,
    with `slow_seeds.py`).
- G3 skipped.

The candidate passes: the local champion by P2, about +0.5 rating points head-to-head (logit 0.171 times Silver's
scale 3.16). The freeze, the default, the
final evaluation against v08 and any upload are the user's decisions. A profile corrects the README's first account of
the holds: a hold costs about one wave of ten, and it halves what a knight training takes from the enemy queen (0.8
against 1.5 HP). Recorded in `docs/data/seed-ledger.json` (`screen-pv3` next at k = 4).

## 2026-11-06 — Freeze v10: the second knight barracks at the centre as the local champion and the current default

The user's decision on the waves screen ("Да, по умолчанию"), as for v09: the default without an upload.
- `cr.bot.v10` is a frozen copy of `current` (`GreedyBot`, `RoutePlanner`, `ApproachPlanner`). It carries the off flags of
  the income, giants and waves experiments.
- `versions/v10.properties` is v09's snapshot with BARRACKS_FORWARD=50, and `Params` defaults to it.
- `BotFactory`, the local-games catalog (`LocalGames`, `visualizer/local.js`: v10 is the default second side) and
  `.ai/project.md` and `docs/visualizer.md` (v00-v10) know v10. `AGENTS.md` is resynced (`check` OK).
- Tests:
  - `MainBotTest.withDefaultsCurrentPlaysAsV10` (fixture seeds and PV3 screen block 3's maps) and
    `withV09ParamsCurrentPlaysAsV09` (the former defaults test);
  - the older identity constants carry BARRACKS_FORWARD=0;
  - `BotFactoryTest` chains v09 to v10, and the live defaults equal v10;
  - `LocalGamesTest` counts 13 choices.
- The architecture page:
  - its rules describe v08, the selected version, and stay;
  - the versions section, the table (a v10 row) and the identity-test list are updated;
  - the stale "the values are the defaults, v08" now says the values are v08's snapshot.
- Records: BOTS (the v10 row, the v09 row, `current`, the BARRACKS_FORWARD flag), STATUS, SUMMARY, PLAN (the final
  evaluation of v10 against v08 is open), the waves README.

v08 stays the selected version on the arena. There is no final evaluation and no artifact check yet. The trial
`submit` of the screen built 86,098 characters.

## 2026-11-06 — v10's final evaluation against v08: S 0.5764, below the 0.60 upload recommendation

The user's request ("Да, запускай"), under P2: the local champion against the selected version on a fresh final
evaluation block. PV3 evaluation block 0 (2000000000 + 397 i, i < 2000), the first of that pool. The scripts are
`eval.sh` and `eval-rerun.sh` in `docs/data/exp-2026-11-06-waves/`. The raw reports are in the main checkout's
`replays/bench/exp-2026-11-06-waves-eval/`.
- G1: S 0.5764 (CI 0.5626-0.5902) against v08, 1,866 of 2,000 maps changed against v08's mirror;
- G2: G2 passes: boss delta 0.0000 (1.000 against 1.000);
- G4: G4 passes: field delta +0.0149 (CI +0.0063..+0.0234);
- G5: G5 passes: rival delta +0.0843 (CI +0.0683..+0.1002);
- G0: G0 by quiet repeats: v10's three series repeated with 2 threads held the same 12000 games without a slow turn;
- G3 skipped.

G0-G2, G4 and G5 pass (G3 skipped): v10 passes the final evaluation against v08. The upload recommendation (S ≥ 0.60) is not met: even the upper bound, 0.5902, is below it.
- With Silver's scale 3.16 the gain is about +1.0 rating point head-to-head; with Bronze's 1.33, +0.4.
- Against R0 the gain over v08 is large, +8.4 pp: the late hold (+5.2 pp in the rival experiment) and the forward
  barracks (+3.1 on the screen) add up.
- The bench's slow turns are host stalls of up to 560 ms on both sides, the frozen controls included; the average
  turn takes 0.05-0.08 ms. The CG time limits of an artifact are `procCheck`'s to check, before any upload.
- An upload of v10 would be the user's exception, as `route`'s was at 0.5856. Recorded in the seed ledger
  (`evaluation-pv3` next at k = 1).

## 2026-11-07 — v10's artifact for an upload as an exception

The user's decision after the final evaluation ("Да, готовь"): prepare v10 for the arena, although S 0.5764
against v08 is below the 0.60 upload recommendation.
- `./gradlew submit` with empty PARAMS (v10's defaults): `21665009d7b3db69ccd12fe808d203286c6a2f8a3b0ef0da1da683c7267d7543`, 86,080 characters, build `8c3b0d9a`,
  config `ca7e6562`. A second build gave the same bytes.
- `procCheck`: procCheck vs v01 100 maps @5000: 200/200 identical to current, 199 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 333.3 ms, later 3.5 ms; vs boss 100 maps @6000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 341.1 ms, later 7.1 ms (logs `replays/bench/exp-2026-11-06-waves-eval/proccheck-v10-*.log`).
- The kept copy: `artifacts/21665009/Player.java` in the main checkout.
- The commit and the fast-forward merge into master: the user's request ("Да, влей в master").
  - A clean export of the staged tree (`git write-tree`, `git archive`) ran `./gradlew submit` and rebuilt the same
    bytes before the commit; the commit carries the same sources.
  - After the merge `./gradlew submit` in the main checkout builds them too, so its `build/submit` holds the file
    to paste.
- Next: from the main checkout, `python -B -I tools/arena.py follow v10 --selected v08` before the paste.
- The selected version stays v08 until P1 judges two placements of v10.

## 2026-11-07 — v10 upload 1 in Silver: 19.38, rank 6/201; P1 asks for the second upload

The user ran `python -B -I tools/arena.py follow v10 --selected v08` in the main checkout and pasted `21665009` ("Прогнал").
The tool:
- saved v08 upload 5 (80 games, none after its placement);
- registered agent 6857063 (submission 41655689) as v10 upload 1 when its first game showed build `8c3b0d9a`;
- followed the placement to its end at 00:56, 19 minutes after the paste;
- fetched the 80 games and ran `cgAttribute`: SAME 80/80, 14,998 turns, marker `8c3b0d9a` in all 80;
- gave P1's verdict: "upload the candidate again".

Its files: `replays/arena/v10/silver/upload-01/` (games, snapshots, `checks/arena.log`, `checks/cgAttribute.log`,
`checks/arena-compare.json`), `docs/data/arena-6857063-games-2026-11-07.json`,
`docs/data/arena-6857063-placement-2026-11-07.json` and the registry entry.

**The placement:**
- score 19.38, local rank 6/201 (global 465), `eligibleForPromotion` false;
- 54 wins in 80 (seat 0: 26 of 35, seat 1: 28 of 45);
- performance rating 19.30 ± 0.81, with Silver's logistic scale 3.22 fitted on the four uploads.

Two notes:
- in 917798617 the referee removed the opponent;
- 917804072 ends at turn 250 at 7:7. CG's game result ranks us (player 0) first, while the four saved battle lists
  give both players position 0. It is the eighth equal-score game, added to `docs/rules/inferred.md`.

**Against v08:**
- v08's three Silver placements have a mean of 20.55 and a performance of 20.46 ± 0.44; v10's placement is −1.17
  below that mean.
- 2M = 4.93 with the borrowed M 2.46, so there is no early rollback (the threshold is 15.62).
- The second upload of the same artifact decides.

Why the score sits below v08's while the wins rise (54 of 80 against 48-53):
- v10 met weaker opponents: median 17.21 against 18.73-19.19.
- It played no game against the Silver boss (v08 met it 3-10 times).
- It had 6 games against players rated 20.5 or more (1 won); v08 had 13-24 (3-8 won).
- The scale did not drift: the 28 opponents both versions met moved by a median of −0.08 between the fetches of
  2026-11-04 and 2026-11-07.

P2 predicted about +1.0 rating point head-to-head. The first placement is −1.2, inside the arena's noise.

Records: BOTS (Uploads, the artifact's uploads), STATUS (the arena entry, the pending P1 candidate),
`docs/arena-uploads.md` (the eighth candidate), `docs/local-screen.md` (local against arena), `docs/rules/inferred.md`
(the eighth tie), SUMMARY. Not committed.

## 2026-11-07 — v10 upload 2 in Silver: 21.33, rank 2/201; P1 selects v10

The user pasted the same `21665009` again ("Запустил вторую. …") with `python -B -I tools/arena.py follow v10 --selected
v08` running in the main checkout. The tool:
- saw the new agent 6857144 at 01:05:22 (created 01:04:47, submission 41655831, 12 % played) and saved what was left of
  v10 upload 1 (80 games, none after its placement);
- registered it as v10 upload 2 at 01:05:29, when its first game showed build `8c3b0d9a`;
- followed the placement to its end at 01:22:37, 18 minutes after the paste;
- fetched the 80 games and ran `cgAttribute`: SAME 80/80, 13,445 turns, marker `8c3b0d9a` in all 80;
- gave P1's verdict: "not clearly worse".

Its files: `replays/arena/v10/silver/upload-02/` (games, snapshots, `checks/arena.log`, `checks/cgAttribute.log`,
`checks/arena-compare.json`), `docs/data/arena-6857144-games-2026-11-07.json`,
`docs/data/arena-6857144-placement-2026-11-07.json` and the registry entry.

**The placement:**
- score 21.33, local rank 2/201 (global 461), `eligibleForPromotion` false;
- 54 wins in 80 (seat 0: 25 of 39, seat 1: 29 of 41), 4 of them against the Silver boss (all its games);
- opponents' median 18.97; performance 21.45 ± 0.91 with Silver's scale 3.68, now fitted on five uploads.

Two notes:
- in 917807402 the referee removed the opponent (score −1);
- 917810075 ends at turn 250 at 7:7. CG's game result ranks us (player 0) first; all 15 saved battle lists give both
  players position 0. It is the ninth equal-score game (`docs/rules/inferred.md`).

**P1 (`docs/arena-uploads.md`):**
- v10's placements 19.38 and 21.33 (mean 20.35) against v08's 20.55: −0.20 against −M = −2.46. M is borrowed from
  Bronze; Silver's own M is 1.66 on 3 df.
- Performance 20.56 ± 0.65 against 20.64 ± 0.50: −0.08 against −2 SE = −1.63.
- Neither rollback condition holds: **v10 is the selected version**. Its artifact `21665009` is the rollback artifact for
  the next candidate, whose early rollback is below 15.43.
- P2's +1.0 head-to-head is inside the arena's noise.

**Where the records live:**
- This session runs in the worktree `claude/bot-improvement-next-steps-4b8f4e`. Its script first wrote the upload 1
  records into the main checkout, next to the files the follow tool writes there. A harness rule then stopped this
  session's edits of the main checkout.
- All the records are now in this worktree's branch: upload 1's, upload 2's, and the follow tool's data files (the
  registry, the four new manifests, the re-fetched manifest of v08 upload 5).
- The main checkout keeps its uncommitted copies until the branch is merged, a subset of the branch's changes. They have
  to be dropped there before the merge.

Records:
- BOTS: Uploads, the versions table, the artifact's uploads, the line "defaults play as v10".
- STATUS, `docs/arena-uploads.md`, `docs/local-screen.md`, `docs/rules/inferred.md`, PLAN, SUMMARY.
- `docs/bot-architecture.html` describes v10, the selected version: header, flowchart, pseudocode, target choice,
  versions, limits and directions, with the late hold and the second barracks at the centre read from
  `cr.bot.v10.GreedyBot`. The flowchart renders without a Mermaid error.

Not committed.

## 2026-11-07 — Study: v10 against weaker and stronger opponents, its losses, the queen under enemy tower fire

The user's observation after upload 1: the bot loses to some weaker players and wins near the top. In about ten IDE
games against the Silver boss (21.96), the boss won once. The study is `docs/data/study-2026-11-07-tower-fire/`
(README, `towers.py`, `opponents.py` and their outputs). The traces and cgStudy tables it reads are in
`replays/study/tower-fire-2026-11-07/` (gitignored).

**By the opponent's score.** The logistic model of `arena_compare.py`, scale 3.68:
- Against opponents below 19, v10 scores 72 % where its rating predicts 75 %; v08 scored 77 % against 72 %.
- Against those at 21 and up and the boss, v10 wins 10 of 15 against 43 %; v08 won 12 of 41 against 44 %.
- So v10 gives up a little against the weaker players and gains a lot against the stronger ones. Most games come against
  the weaker, so the rating stays.

**Losses to weaker opponents** (cgStudy):
- 24 of v10's 31 losses to opponents below 19 are deaths, median turn 142.
- The winners hold income 6 at turn 100 to our 3 and knights on the board (3 against 0). They build fewer towers and
  top up less.
- v08's 28 such losses look the same: 16 deaths, income 5 against our 2 at turn 150.
- On HP at turn 250, v08 lost 35 games of 250 and v10 9 of 160.

**Enemy tower fire** (`cr.tools.DeathTrace`, the `td` column):
- v10's queen loses 5.1 ± 1.2 HP a game to enemy towers, v08's 3.2 ± 0.7. The difference is within the per-game spread.
- Of v10's 467 such turns, 313 had no enemy knight within 250.
- The commands on those turns were BUILD TOWER (257), BUILD MINE (188) and BUILD BARRACKS-KNIGHT (22).
- In 12 of v10's 43 deaths, towers took 10+ HP in the last 30 turns: 31 % of those turns' HP, against v08's 18 %.
- In 1 of the 9 losses on HP at turn 250, the HP lost to towers covers the margin.

**From the code** (`cr.bot.v10.GreedyBot`, `RoutePlanner`):
- A site is no target when an enemy tower's range covers its centre; the touch point and the walk are not checked.
- When no clean plan avoids the ranges, `RoutePlanner.plan` returns the straight BUILD. Example: 917802377, from turn 145
  under two enemy towers, targets alternating between sites 20 and 3, dead at turn 166.

Proposed candidates:
- Keep the queen's walks out of enemy tower ranges: check the touch point and the path, and add target hysteresis. A
  targeted fix, measurable locally by the HP lost to towers.
- The late economy gap against opponents with twice our income, which the income candidates of 2026-11-06 did not close
  locally (`docs/data/exp-2026-11-06-income/`).

## 2026-11-07 — SAFE_LADDER: no ladder walks into the enemy base; passes P2 as a targeted fix

The user's "Да, начинай" for the candidate the study of the same day proposed: keep the queen's walks out of enemy tower
ranges. Experiment `docs/data/exp-2026-11-07-safe-ladder/` (README, scripts, summaries).

**Diagnosis** (`cr.tools.TowerFire`, gradle task `towerFire`; outputs `classes-v10.txt` and `classes-v08.txt` in the
study folder):
- The tool replays the arena games and classes every turn on which the probe without enemy knights loses HP. A class is
  the command and its target, touching or walking, the target's centre and sides against the ranges, the queen's start,
  knights near, the target's half and the straight walk.
- 436 of v10's 810 tower HP (v08: 426 of 805) came on walks to free sites on the enemy half through enemy ranges.
- The rest: 75 on walks to our half; 282 at or walking to own towers (205 with knights near).
- Example 917798608: our half's free sites lie in enemy ranges, so the ladder's sixth tower targets site 9 by the
  enemy's corner. The queen alternates between walking there and topping up her own tower 15, which stands in an enemy
  range, until she dies.
- `-Pagents=dump` prints a game's board and the queen's path over a turn window.

**Local measure.** `cr.tools.LocalProfile` gets a line from the same probe: the HP our queen loses to enemy towers a game,
and the turns she walks to a free site on the enemy half through an enemy range. v10 on seeds 1 + 397 k, k < 200, loses
0.3, 0.5 and 0.9 HP a game against rival, field and v10, against 5.1 on the arena.

**The flag `SAFE_LADDER`:**
- `Params`, default 0. `MainBot.nextSite` now takes the walker's position (the queen, or ROUTE's landing point via
  `ApproachPlanner.nextTarget`) and computes the midpoint itself; the same doubles, so the defaults play as before.
- `safeLadder`: a site on the enemy half (nearer the enemy's corner) needs, with 1, a straight walk outside every enemy
  tower range (`walkUnderEnemyTower`, every 20 units until touching); with 2 it never qualifies.
- With no target left, the existing branch pumps the tower nearest home.
- The snapshots v01-v10 gained `SAFE_LADDER=0`. The test is `StrategyParamsTest.safeLadderKeepsTheQueenOutOfTheEnemyBase`.
- `StrategyParamsTest`, `MainBotTest` (v10 equivalence) and `BotFactoryTest` pass.

**Development sweep** (`sweep.sh`, seeds 1 + 397 k, k < 500):
- SAFE_LADDER=1: S 0.5025 against v10, 37 maps changed against the mirror, rival +0.0010, field +0.0055.
- SAFE_LADDER=2: S 0.5075, 219 maps changed, rival +0.0095, field +0.0085.
- Only 1 went to the screen: a targeted fix. Arm 2 changes 44 % of the maps for no clear gain.

**P2 screen on PV3 screen block 4** (`screen.sh`, `rerun.sh`; seeds 1004000000 + 397 i, i < 2000):
- 148 of 2000 maps changed against the mirror (7.4 %, a targeted fix); S 0.5030 (CI 0.5000-0.5060).
- G2 boss delta 0.0000; field delta 0.0000 (CI -0.0021..+0.0021); rival delta +0.0013 (CI -0.0018..+0.0043).
- G0:
  - In the first run the candidate had one slow turn (map 1004717379), and v10 one on a neighbouring map of the same
    series.
  - The quiet repeat at 2 threads held the same 4000 games with no slow turn on either side. The candidate's longest
    move was 13.1 ms.
- G3 skipped. The flag passes P2 as a targeted fix.

**On v10's arena games** (`cgAttribute -Pbot=current?SAFE_LADDER=1`, `arena_diff.py`):
- The flag changes a decision in 31 of 160 games: 20 losses (17 queen deaths, 667 tower HP after the changed turn) and
  11 wins (32 tower HP).
- The changed decision is mostly a BUILD MINE on the enemy half, 14 of the 20 losses: the income below its target again.
- The counterfactual outcomes are unknown.

**Records:** BOTS (the flag's row), STATUS, SUMMARY, PLAN, the seed ledger (screen-pv3 block 4, next 5), the architecture
page (an off flag). The raw reports are in this worktree's `replays/bench/exp-2026-11-07-safe-ladder/` (gitignored; to
copy to the main checkout's `replays/bench/` before the worktree goes).

The flag stays off: adopting a targeted fix into the local champion (and freezing v11) is the user's decision. Not
committed.

## 2026-11-07 — v11: SAFE_LADDER=1 by default (the user's decision)

The user adopted the targeted fix ("Да, по умолчанию"):
- `Params.SAFE_LADDER = 1`, documented as the default since v11.
- `cr.bot.v11` (`GreedyBot`, `RoutePlanner`, `ApproachPlanner`) is a frozen copy of current: the package, the class and
  name "v11", no `queenSteps`, a header javadoc.
- `versions/v11.properties` is v10's snapshot with SAFE_LADDER=1.
- `BotFactory`, `LocalGames` (choice v11, default bot1 v11, its resource list) and `visualizer/local.js` know v11.
- `.ai/project.md` and `docs/visualizer.md` name v00-v11. `AGENTS.md` and `.ai/kit.lock.json` were regenerated with
  `python -B -I .ai/sync.py sync . --source C:/Disk_D/Programming/CG/cg-agent-kit` at the locked kit f4a5bd63; `check` OK.

Tests:
- `MainBotTest`: the constants V02 and V05-V09 gained SAFE_LADDER=0, and V10 = "SAFE_LADDER=0,".
  `withV10ParamsCurrentPlaysAsV10` replaces the defaults' v10 test. `withDefaultsCurrentPlaysAsV11` plays the fixture
  seeds and PV3 screen block 4's first maps.
- `BotFactoryTest`: the chain v10 → v11 and the defaults equal v11.
- `LocalGamesTest`: 14 choices, default v11.
- `StrategyParamsTest.safeLadderKeepsTheQueenOutOfTheEnemyBase`: its v10 case now names SAFE_LADDER=0.
- Results: 166 Java tests, 3 corpus tests skipped for want of the arena replays in the worktree; visualizer 18 of 18.

Records:
- BOTS: the v11 row, the v10 row, current, the flag's default.
- STATUS (local champion v11; v10 stays the selected version), SUMMARY, PLAN, the experiment's verdict.
- The architecture page: it still describes v10, the selected version. Its header names current = v11, the target
  choice and the pseudocode note v11's rule, and the versions table gains v11.

The selected version stays v10. A targeted fix needs no upload of its own. An upload of v11 would be the user's
exception: S 0.5030 against v10, far below 0.60, and only the arena can show the effect. Not committed.

## 2026-11-07 — v11's artifact for an upload as an exception

The user's decision after the freeze ("Да, готовь"): prepare v11 for the arena, although a targeted fix needs no
upload of its own and S 0.5030 against v10 is far below the 0.60 recommendation. Only the arena can show its effect.
- `./gradlew submit` with empty PARAMS (v11's defaults): `cf71d566d41f77394502c11174496c39e2f94d4b2c4a0c27071f3283b450dff9`, 86,973 characters, build `cf3b2e46`
  (turn 0: `TRAIN 12 15 3 11 2 14 4 6`), config `bc23e79d`. A second build gave the same bytes.
- Reproducibility before any commit:
  - a temporary index (`GIT_INDEX_FILE`, `git read-tree HEAD`, `git add -A`, `git write-tree`: tree d87439fa) took
    the working tree as it stands, leaving the real index alone;
  - `git archive` of that tree, built with `./gradlew -p <export> submit`, gave the same bytes.
- `procCheck`: procCheck vs v01 100 maps @5000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 372.0 ms, later 7.6 ms; vs boss 100 maps @6000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 370.6 ms, later 4.8 ms (logs `replays/bench/exp-2026-11-07-safe-ladder/proccheck-v11-*.log`).
- The kept copy: `artifacts/cf71d566/Player.java` in this worktree, gitignored. It goes to the main checkout's
  `artifacts/` with the merge, where `tools/arena.py` looks for it.
- The commit and the fast-forward merge into master: the user's request ("Да, влей в master").
  - The commit's sources equal the exported tree's, so the clean export stands for the commit.
  - The main checkout's uncommitted copies of the upload records were dropped before the merge. Its data files were
    byte-identical to the branch's, and the document lines the branch lacked were the ones it had superseded.
  - The artifact copy, the raw bench reports (`replays/bench/exp-2026-11-07-safe-ladder/`) and the study's traces
    (`replays/study/tower-fire-2026-11-07/`), all gitignored, were copied to the main checkout.
  - `./gradlew submit` in the main checkout builds the same bytes into `build/submit/Player.java`.
- Next: from the main checkout, `python -B -I tools/arena.py follow v11 --selected v10` before the paste.
- The selected version stays v10 until P1 judges two placements of v11.

## 2026-11-07 — v11 upload 1 in Silver: 20.80, rank 2/200; the tower fire nearly gone; P1 asks for the second upload

The user ran `python -B -I tools/arena.py follow v11 --selected v10` in the main checkout and pasted `cf71d566`
("закончилась серия"). The tool:
- saved v10 upload 2;
- saw the new agent 6857351 at 02:42:56 (created 02:42:05, submission 41656151, 12 % played);
- registered it as v11 upload 1 at 02:43:02, when its first game showed build `cf3b2e46`;
- followed the placement to its end at 02:59:08, 17 minutes after the paste;
- fetched the 80 games and ran `cgAttribute`: SAME 80/80, 15,831 turns, marker `cf3b2e46` in all 80;
- gave P1's verdict: "upload the candidate again".

Its files are in the main checkout: `replays/arena/v11/silver/upload-01/` (games, snapshots, `checks/`), the manifests
`docs/data/arena-6857351-games-2026-11-07.json` and `docs/data/arena-6857351-placement-2026-11-07.json`, the registry
entry, and a re-fetched `docs/data/arena-6857144-games-2026-11-07.json`. The four tracked files were copied into this
worktree, where the records are written (the main checkout keeps the same changes uncommitted until the next merge).

**The placement:**
- score 20.80, local rank 2/200 (global 462), `eligibleForPromotion` false;
- 52 wins in 80 (seat 0: 26 of 40, seat 1: 26 of 40); opponents' median 19.53; 10 games against the Silver boss,
  5 won;
- performance 21.58 ± 1.04 with Silver's scale 4.25, now fitted on six uploads.

Notes:
- the referee removed the opponent in 917832868 (score −1);
- 917833240 (77:77, we were player 1, a loss) and 917834882 (23:23 against the boss, we were player 0, a win) end at
  turn 250. All their saved battle lists (20 and 9) give both players position 0. They are the tenth and eleventh
  equal-score games (`docs/rules/inferred.md`).

**The fix on the arena** (`cr.tools.DeathTrace` and `cr.tools.TowerFire` on the 80 games; the study's `towers.py`
now includes v11):
- the queen lost 41 HP to enemy towers in 3 games, 0.5 ± 0.5 a game, against v10's 5.1 ± 1.2 and v08's 3.2 ± 0.7;
- she made no walk to a free site on the enemy half;
- 15 deaths (v10: 43 in 160), none with 10+ HP from towers (v10: 12);
- 13 games lost on HP at turn 250 (v10: 9 in 160).
- By the opponent's score, v11 scores about its expectation in every group: below 19, 81 % against 80 %; 19-21, 56 %
  against 58 %; 21 and up with the boss, 7 of 13 against 51 %.

**Against v10 (P1):**
- +0.45 on the mean (20.80 against 20.35), with 2M = 4.93, so no early rollback (below 15.43).
- The second upload of the same artifact decides.
- The performance rating, 21.58 ± 1.04 against 20.94 ± 0.74, is context until then.

Records:
- BOTS (Uploads; the artifact's uploads), STATUS, `docs/arena-uploads.md` (the ninth candidate), `docs/local-screen.md`
  (the v11 row) and `docs/rules/inferred.md` (eleven ties);
- SUMMARY, the study's section 5 (its results table now at the scale 4.25) and the experiment's arena section.

Not committed.

## 2026-11-07 — v11 upload 2 in Silver: 21.74, rank 1/200; P1 selects v11; promoted to Gold

The user pasted the kept copy again ("закончилась вторая. Прошли серебро") with
`python -B -I tools/arena.py follow v11 --selected v10` running in the main checkout. The tool:
- saved v11 upload 1 (80 games);
- named `artifacts/cf71d566/Player.java` to paste at 03:03:07;
- saw the new agent 6857388 at 03:04:14 (created 03:03:39, submission 41656217, 12 % played);
- registered it at 03:04:19 when its first game showed build `cf3b2e46`;
- followed the placement to 03:20:26;
- fetched the 80 games and ran `cgAttribute`: SAME 80/80, 14,820 turns;
- gave P1's verdict: "not clearly worse".

The four tracked files it wrote into the main checkout (the registry and the manifests) were copied into this worktree.

**The placement:**
- score 21.74, local rank 1/200 (global 461), `eligibleForPromotion` true;
- 56 wins in 80 (seat 0: 33 of 46, seat 1: 23 of 34); opponents' median 18.64; 2 games against the Silver boss, 1 won;
- no equal-score game; the referee removed one opponent (917836644).

**P1:** v11's placements are 20.80 and 21.74 (mean 21.27) against v10's 19.38 and 21.33 (mean 20.35).
- Mean: +0.92 against −M = −2.46 (M borrowed from Bronze; Silver's own M is 1.59 on 4 df).
- Performance: 22.02 ± 0.77 against 21.04 ± 0.77 at Silver's scale 4.41, now fitted on seven uploads; +0.98 against
  −2 SE = −2.17.
- **v11 is the selected version**; `cf71d566` is the rollback artifact.

**The fix over both uploads** (the study's `towers.py` with v11's 160 games; `cr.tools.TowerFire`):
- the queen lost 98 HP to enemy towers in 9 games, 0.6 ± 0.3 a game (v10: 5.1 ± 1.2; about 3.6 SE apart);
- she made no walk to the enemy half;
- 32 deaths, none with 10+ HP from towers (v10: 43, 12 of them); 20 games lost on HP at turn 250 (v10: 9).
- By the opponent's score, v11 plays to its expectation in every group: below 19, 75 % against 77 %; 19-21, 63 % against
  60 %; 21 and up with the boss, 9 of 18 against 53 %.

**The promotion:**
- Upload 2 ranked first, above the Silver boss. The user reports the promotion to Gold, while the leaderboard still
  listed the agent in Silver at 03:21.
- CG's promotion makes a new agent in Gold, with the same submission, as with v08 in Silver. Its placement is v11's first
  Gold upload; `python -B -I tools/arena.py follow v11` registers it once it plays.
- Per P1, scores compare within a league and v11's Gold baseline needs two placements there.
- PV3 and PV4 lapse with the league change, so local candidates are developed only, not promoted, until a validation for
  Gold.
- Gold has no noise margin; borrowing Bronze's, as Silver did, is the user's call.

**Records:**
- BOTS: Uploads, the versions table, the artifact's uploads.
- STATUS (league, selected version, arena entry), SUMMARY, `docs/arena-uploads.md`, `docs/local-screen.md`.
- The study and the experiment READMEs.
- `.ai/project.md`: the current league, with `AGENTS.md` resynced.
- The architecture page now describes v11, the selected version.

Committed with the records of v11's first upload and merged into master by fast-forward on the user's request
("Да, влей в master"). The main checkout's uncommitted copies of the follow tool's files, byte-identical to the
branch's, were dropped first. The study's new traces were copied to its `replays/study/` (gitignored).

## 2026-11-07 — v11 in Gold: the promoted agent's placement, 18.13, rank 119/351

CG promoted v11 upload 2 to Gold. The promotion made agent 6857463 at 03:48:10 (the same submission 41656217), whose
Gold placement played unwatched. The user ran `python -B -I tools/arena.py follow v11` in the main checkout at 11:27
("сохранил"). The tool:
- saved what was left of upload 2;
- registered the Gold agent as v11 upload 3 ("CG's promotion of v11 upload 2"), when its first game showed build
  `cf3b2e46`;
- took its first snapshot at 100 %, and fetched the 90 games: `cgAttribute` SAME 90/90, 18,587 turns.

The leaderboard last updated the score at 04:08:53, after the placement's 90 games, so 18.13 is the placement's own
score; the entry is marked unwatched. The four tracked files it wrote into the main checkout (the registry, the
re-fetched upload 2 manifest, the new manifests) were copied into this worktree.

**The placement:**
- score 18.13, local rank 119/351 (global 230), `eligibleForPromotion` false;
- 56 wins in 90 (seat 0: 30 of 48, seat 1: 26 of 42); opponents' median 16.78 (from 11.88 to 24.99); no game against
  the Gold boss;
- Gold's scale 3.83 from this one upload; performance 18.28 ± 0.86.
- 917850788 (95:95, turn 250, we were player 0) is the twelfth equal-score game: CG's result ranks us first, and its
  three saved battle lists give both players position 0.

**How v11 loses in Gold** (`DeathTrace`, `TowerFire`; the study's `towers.py` now prints a Gold section):
- 20 of the 34 losses are HP races at turn 250 and 14 are deaths;
- enemy towers cost the queen 20 HP in 4 games (0.2 a game), all at own towers, none in a death;
- in Silver v11 lost 20 of 160 games at the limit; in Gold the HP race is its main loss mode.

**P1 in Gold:**
- the first of the two placements v11's Gold baseline needs;
- the second is a new upload of the kept copy `artifacts/cf71d566/Player.java`;
- Gold has no noise margin (0 df). Whether Gold borrows Bronze's M, as Silver does, is the user's decision.

**Records:**
- BOTS (Uploads, the artifact's uploads), STATUS (arena entry, waiting list), SUMMARY;
- `docs/arena-uploads.md`, `docs/rules/inferred.md` (twelve ties), `docs/local-screen.md`, the study (Gold section,
  `classes-v11-gold.txt`), PLAN.

Not committed.

## 2026-11-07 — v11 upload 4 in Gold: 16.03, rank 256/351; the Gold baseline; the study of the Gold games

The user pasted the kept copy again with `python -B -I tools/arena.py follow v11` running ("загрузил ещё одну серию.
можно разобрать игры с учётом двух"). The tool:
- saved upload 3 and named the file at 11:56:03, then saw the old agent's battle list empty at 11:58:16;
- registered agent 6858567 (created 11:58:02, submission 41658370) as v11 upload 4 at 11:58:43;
- followed the placement from 1 % to 12:18:43;
- ran `cgAttribute`: SAME 90/90, 17,378 turns.

The tracked files it wrote into the main checkout were copied into this worktree.

**The placement:**
- score 16.03, local rank 256/351 (global 367), `eligibleForPromotion` false;
- 55 wins in 90 (seat 0: 29 of 46, seat 1: 26 of 44), 5 of them by the referee's removal of the opponent;
- opponents' median 15.03; no game against the Gold boss;
- 917985146 (35:35, we were player 1, a loss) and 917987888 (68:68, player 0, a win) are the thirteenth and fourteenth
  equal-score games; their saved battle lists (19 and 10) give both players position 0.

**v11's Gold baseline:**
- placements 18.13 and 16.03, mean 17.08; performance 16.92 ± 0.50 over 180 games at Gold's scale 3.10;
- the two differ by 2.10 with the same code;
- Gold's own M is 2.97 on 1 df. Which M Gold uses (its own, or Bronze's 2.46 as Silver borrows) is the user's decision.

**The study of the 180 Gold games** (`docs/data/study-2026-11-07-gold/`: README, `gold.py`, `gold.txt`, the local
profiles):
- **Losses:** 44 of the 69 are HP races at turn 250 and 25 are deaths. In the deaths, towers take 0.4 HP of the last 30
  turns.
- **The first wave decides the races:**
  - in the races we lose, the first enemy knight comes within 250 at turn 24 against one tower, and the next 20 turns
    cost us 12.5 HP and the enemy 5.1;
  - in those we win, the first wave meets two towers (5.9 against 8.7);
  - after turn 50 the gap barely moves, and the last 50 turns widen a lost race (6.5 against 2.8, while behind at turn
    200 in 41 of 44).
- **The opponents:** giants in 17 % of the games, not more often in our losses. v11 scores its logistic expectation in
  every opponent group.
- **The local panel** (seeds 1 + 397 k, k < 200):
  - the mirror meets the first wave as in Gold (turn 24, one tower) and goes to the limit in 91 % of games (Gold 59 %);
  - `field` 60 %; `rival` builds giants in 85 % of its games; `boss` ends early.
- **The proposal (PV5, the user's decision):** keep the panel and the gates and record what each opponent stands for in
  Gold. The main candidate is the second tower before the first wave, retried from v11.

Records:
- BOTS (Uploads, the artifact's uploads), STATUS (the Gold baseline, the arena entry, the study, the waiting list);
- SUMMARY, `docs/arena-uploads.md`, `docs/rules/inferred.md` (fourteen ties), `docs/local-screen.md`, PLAN;
- the architecture page.

Not committed.

## 2026-11-07 — PV5: the local panel for Gold

The user adopted the Gold study's proposal ("Да, принять PV5"):
- `docs/local-screen.md` gains the PV5 section: the panel and the gates G0-G5 and PV3's seed pools are kept, with the
  record of what each opponent stands for in Gold and what none of them does;
- `.ai/project.md` names PV5 in the P2 section and the proxy row; `AGENTS.md` is resynced;
- STATUS, SUMMARY, PLAN, the study's README and the architecture page follow.

P2 candidates can be promoted again. Gold's noise margin for P1 stays the user's decision: its own M 2.97 on 1 df, or
Bronze's 2.46 as Silver borrows.

Not committed.

## 2026-11-07 — Gold's noise margin: Bronze's 2.46

The user's decision ("Bronze 2.46"): Gold's P1 verdicts borrow Bronze's M, as Silver's do.
- The registry gains `noiseMargins.gold` (from bronze, M 2.46, decided 2026-11-07).
- `python -B -I tools/arena_compare.py` prints for Gold: "verdicts use M = 2.46 from bronze (the user's decision of
  2026-11-07; this league's own M is context)". Gold's own M is 2.97 on 1 df; the run exits 0.
- The next Gold candidate rolls back if its mean is below v11's 17.08 by more than 2.46 and its rating below 16.92 by
  more than 2 SE; early below 12.15.

Records: `docs/arena-uploads.md` (Gold noise margin), STATUS, SUMMARY, PLAN, `docs/local-screen.md` (PV5's open
point), the Gold study's README, `.ai/project.md` (the noise row; `AGENTS.md` resynced), the architecture page.

Committed together with the records of v11's two Gold placements, the Gold study and PV5, and merged into master
by fast-forward on the user's request ("Сначала влей в master"). The main checkout's uncommitted copies of the
follow tool's files, byte-identical to the branch's, were dropped first; the Gold study's inputs and the new traces
were copied to its `replays/study/` (gitignored).

## 2026-11-07 — The second tower before the first wave, retried over v11: rejected; a correction to the Gold study

The user's "Да, начинай" for the Gold study's first candidate. The first wave was looked at again before any code
(`docs/data/study-2026-11-07-gold/first_wave.py`, `first_wave.txt`), and the study's premise did not hold.
- **The medians had misled.** The study had our towers at the first contact as one in lost races and two in won ones.
  The means are 1.6 and 1.7.
- **The rest is the same too:**
  - the contact turn (24 and 25), the enemy knights within 500 (3.7 and 3.5) and alive at turn 25 (4.2 and 4.2);
  - the queen's 20 turns (about 10 top-ups and 9.5 kiting moves, about 120 from her tower);
  - our tower HP at turn 25 (849 and 890), our income and barracks timing.
- **What differs:**
  - the enemy knights stay within 250 of her on 13.0 of the 20 turns against 10.7, and the loss is 12.5 against 5.9;
  - the opponents score 15.8 against 15.1, and the starting HP is 66 against 72.
- At the same contact timing (turns 22-27), two towers cut the first-contact loss from 16.8 to 10.0 HP. The share of
  games won only moves from 57 % to 61 %.

**The local test** of the 2026-11-05 flags over v11 (`docs/data/exp-2026-11-07-first-wave-gold/`, seeds 1 + 397 k,
k < 500):
- `WAVE_TOWERS=2`: S 0.4515 (CI 0.4185..0.4845) against v11, rival −5.9 pp, field −3.0 pp;
- `TOPUP_FROM=2`: S 0.4470, rival −7.3 pp, field −2.5 pp.
- Both fail the development bar. The candidate is rejected before a screen, and no PV3 block is used.

**Corrections** in the Gold study's README (finding 2, candidate 1), STATUS, SUMMARY and the architecture page. The
earlier DEVLOG entry and the commit message of ecac692 keep the median reading, and this entry corrects them.

Not committed.

## 2026-11-07 — The first wave in the Gold replays: forward contacts and merged waves

The user's "Да, разбирай": look at the replays for what decides the first wave in the Gold HP races v11 loses. Study
section 6 of `docs/data/study-2026-11-07-gold/README.md`.

**Tool:** `cr.tools.WaveTrace`, the gradle task `waveTrace`.
- It replays each game with the recorded commands and writes the first wave turn by turn, from 3 turns before the first
  contact to 29 after.
- Per turn: our queen's position, HP, loss by source, command and step; the own towers covering her, each with HP,
  range and distance; the enemy knights within 250 and 500, with their HP and how many stand inside our tower ranges;
  our towers' damage and kills (from a probe without our towers); the enemy queen's distance and the spawns.
- It also writes a summary of window metrics per game.
- 171 Gold games have a contact. The output is gitignored in `replays/study/gold-2026-11-07/waves/`, with sha256 of
  `summary.csv` 65f5c722…

**Method:** a workflow of 18 agents (the session runs under the user's standing ultracode setting).
- Six readers took about 18 races each and wrote a narrative per game with patterns and hypotheses.
- A consolidator kept nine measurable hypotheses.
- Nine verifiers computed them over all 106 races, with bootstrap CIs, rank tests, strata of contact turn and towers,
  and refutation attempts.
- A synthesizer ranked the survivors, and a skeptic recomputed the claims and challenged them.
- The whole result is `wave_workflow.json` in the study folder. `wave_mechanisms.py` recomputes the headline numbers
  independently (`wave_mechanisms.txt`).

**Findings** (lost against won races; the first-wave loss to knights is 12.5 against 5.9 HP):
- **Where the queen meets the wave.** She is 444 from our back edge in lost races against 380 (CI +15..+121). In the
  forward half the loss is 15.8 against 5.6; in the back half 6.2 against 6.0.
- **Merged waves.** 19 of 44 lost races against 7 of 62 won (odds ratio about 7 in every stratum), 20 of the 26 in the
  forward half.
  - The loss from the merge on is 7.5 against 0.6, the whole gap. Unmerged races lose 6.7 against 5.7.
  - On the merge turn the queen moved toward the enemy in 10 of 19 lost merges (skeptic, p 0.016).
- **Standing at an own tower with a knight in reach**, when `kite()` finds no step: 3.6 against 0.8 HP. At the same
  crowd it costs 2.6 HP a turn against 1.25 for a kite step. It is fragile: three races hold 45 %.
- **Refuted or weak:** the defence walk as such, the kite step re-firing the defence rule, pinned BUILDs, kiting bound
  to one tower, site shields, the board edge.

**Candidates:**
- A. Meet the first wave back: while an enemy wave is coming or on her, no forward defence sites, ladder targets or kite
  steps.
- B. Never stand with a knight within 155.
- C. Hysteresis on the defence radius.

Before any of them, local games must be checked for forward contacts and merges, since the panel may not produce them.

Records: the study README (section 6), STATUS, SUMMARY, PLAN. Not committed.

## 2026-11-07 — Meet the first wave back: the ladder's anchor to turn 30 passes P2

The user's "Да, начинай" to candidate A of the first-wave study. The record is
`docs/data/exp-2026-11-07-meet-back/README.md`, which has the hypothesis and bar written before the comparisons, the
probes, the design, both sweeps, the screen and the verdict.

**Local check first.** `LocalProfile` measures the first wave as the Gold study did: the contact, the queen's distance
from her back edge, the 20-turn loss to knights and merged waves.
- The mirror and the rival reproduce Gold's pattern.
  - The contact is at 409 and 423 (median), against 411 in Gold.
  - Merged waves come in 24 % of the games.
  - The loss is 10.7 against 5.0 beyond and within 406.
- `gold_reference.py` applies the same measures to the study's WaveTrace files.

**Probes.** `THREAT_RADIUS=350` loses badly (S 0.39) and `KITE_PULL=200` changes nothing. Neither moves the contact.

**Design.** A workflow of four agents: three designers (the ladder, the defence rule and kiting, the build order) and a
judge. It is saved as `design_workflow.json`.
- The place of the contact is set by the ladder's pull toward the home corner before any threat rule acts.
- Three flags, all off by default:
  - `WAVE_ANCHOR` with `WAVE_UNTIL`: the tower target nearest the point at the walker's height, A % of the way back to
    her edge, in the first-wave window, from the second tower on.
  - `DEFENCE_FWD`, with `DEFENCE_UNTIL` added after sweep 1: the defence tower not ahead of the queen.
  - `STAND_REACH`: a forced kite step instead of standing with a knight in reach.

**Implementation.**
- `MainBot.nextSite` and `firstWaveWindow`, the defence rule and `kite()`.
- Three tests in `StrategyParamsTest`, mirrored to the right-side home.
- The four new keys entered every version snapshot with their off values.
- A review workflow (four lenses, refuters per finding) found no defect in the bot. Its findings were acted on:
  - test cases that did not discriminate (the scope, A=50 against A=100, the window's owner and type checks, the exact
    forced step, the mirrored side);
  - the profile's held-target measure, which counted the contact turn.
- `LocalProfile` also gained the HP flow by phase (both queens, knights and towers) and the mechanism measures.
- A new tool, `cr.tools.LocalTrace` (`./gradlew localTrace`), prints local games turn by turn in DeathTrace's columns.
- Full suite: 169 tests pass, 3 skipped.

**Sweeps** on the development seeds 1 + 397 k, k < 500, with `sweep.sh` and `profile.sh`.
- Sweep 1: the anchor halves the first-wave loss.
  - Every arm except `STAND_REACH` still loses 2-6 pp to field. `DEFENCE_FWD=150` loses exactly 2.0 pp, on the bar's
    edge, with a small first-wave effect.
  - In the mirror the anchor at 50 meets the wave at 305 instead of 409, with 48 merged waves instead of 95 and a
    first-wave loss of 4.2 instead of 7.8.
  - With the defence limit the loss falls to 3.2 and the merged waves to 31.
  - The anchor at 50 also loses 3 pp to the rival.
  - The HP flow shows the cost after turn 50. Against field our queen loses 7.3-9.7 HP to knights over turns 50-249,
    against v11's 5.3.
- Sweep 2: the anchor's window decides it.
  - Windows to 25-35 score 0.552-0.559 against v11 and −1.35 to +0.3 pp against field. The window to 40 loses 2.3 pp
    and the window for the whole game scores 0.489.
  - The defence limit costs field 3-3.5 pp even when it ends at turn 60.
  - The window to 30 is chosen, the middle of the plateau.

**P2 screen** of `current?WAVE_ANCHOR=50,WAVE_UNTIL=30` against v11 on PV3 screen block 5, seeds 1005000000 + 397 i:
`C="current?WAVE_ANCHOR=50,WAVE_UNTIL=30" bash docs/data/exp-2026-11-07-meet-back/screen.sh`.
- S 0.5654 (CI 0.5523..0.5784), 1320 of 2000 maps changed.
- boss delta 0.0000, field −0.0149 (CI −0.0227..−0.0071), rival +0.0135 (CI −0.0013..+0.0283).
- The candidate had no failure in its 13000 games. G3 is skipped.
- G0-G2, G4 and G5 pass.

The block is recorded in `docs/data/seed-ledger.json`; the next screen block is 6. The candidate is the new local
champion by P2. Whether it becomes the default (v12) and whether it is uploaded are the user's decisions. S is below
0.60, and a final evaluation against v11 would come first.

Records: the experiment README, STATUS, SUMMARY, PLAN, the seed ledger. Not committed.

## 2026-11-07 — v12: the first-wave anchor becomes the default

The user's "Да, v12" after the meet-back screen.
- `Params`: WAVE_ANCHOR=50 and WAVE_UNTIL=30 are the defaults.
- `cr.bot.v12` is a frozen copy of current (GreedyBot, RoutePlanner, ApproachPlanner, made as v11 was).
- `versions/v12.properties` is v11's snapshot with the anchor.
- BotFactory, LocalGames (v12 the default opponent; v11 relabelled the selected version) and `visualizer/local.js` know
  v12.
- `.ai/project.md` and `docs/visualizer.md` name v00-v12; AGENTS.md is resynced.

Tests:
- `MainBotTest.withDefaultsCurrentPlaysAsV12` covers the fixture seeds and block 5's maps, and
  `withV11ParamsCurrentPlaysAsV11` covers block 4's. The V02/V05-V10 strings name WAVE_ANCHOR=0.
- `BotFactoryTest` checks the v11 → v12 chain and the live defaults equal to v12.
- `LocalGamesTest` checks 15 choices with v12 the default.
- `StrategyParamsTest` has a default case in `waveAnchorKeepsTheTowerTargetsBack`. `waveTowersBuildTheSecondTowerBeforeTopUpsAndMines`
  names WAVE_ANCHOR=0, since it tests the old WAVE_TOWERS rule on v11's ladder.

Checks:
- Java: 170 tests, 3 corpus tests skipped.
- Tools: 59 OK. Visualizer: 18 of 18.
- `./gradlew bench -Pbot1=v12 -Pbot2=v11 -Pmaps=500 -PseedBase=1 -PseedStride=397`: 0.559, as the candidate in the sweep
  (0.5590).

v11 stays the selected version and the arena's; v12 is not uploaded. Not committed.

## 2026-11-07 — v12 prepared for the arena: the final evaluation and the artifact

The user's "Да, готовь".

**Final evaluation** of v12 against the selected version v11 on PV3 evaluation block 1, seeds 2001000000 + 397 i.
Command: `bash docs/data/exp-2026-11-07-meet-back/eval.sh`. Raw reports: `replays/bench/exp-2026-11-07-meet-back/eval/`.
- S 0.5716 (CI 0.5587..0.5845); 1299 of 2000 maps changed.
- boss delta 0.0000; field −0.0123 (CI −0.0196..−0.0049); rival +0.0163 (CI +0.0022..+0.0303).
- v12 had no failure. G0-G2, G4 and G5 pass; G3 is skipped.
- S is below the 0.60 recommendation, so an upload would be an exception.
- The block is recorded in the seed ledger; the next evaluation block is 2.

**Artifact**, `./gradlew submit` with empty PARAMS:
- `3208b011415504ac2776a72159e9d79fc92d7d3101bd7070b8eb282ec4b30271`, 88,517 characters;
- build `58d513ae` (turn 0: `TRAIN 5 8 13 5 1 3 10 14`), config `7b8e0525`.

**Reproducibility, before any commit.**
- A second build gave the same bytes.
- A temporary index took the working tree as it stands (`GIT_INDEX_FILE`, `git read-tree HEAD`, `git add -A`,
  `git write-tree`: tree 355b8617). `git archive` of that tree, built with `./gradlew -p <export> submit`, gave the same
  bytes.

**procCheck:** procCheck vs v01 100 maps @5000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 264.8 ms, later 6.7 ms; vs boss 100 maps @6000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 298.1 ms, later 7.7 ms.
- v12's 121 ms turn in the boss bench (3 threads, 200 ms budget) did not recur: at CG's 50 ms its later turns stay at
  most 7.7 ms.

**Kept copy:** `artifacts/3208b011/Player.java` in this worktree, gitignored. It goes to the main checkout with the merge.

**Next.** On the user's decision: the commit and the merge into master, then from the main checkout
`python -B -I tools/arena.py follow v12 --selected v11` before the paste. v11 stays the selected version until P1 judges
two Gold placements of v12. Not committed.

## 2026-11-07 — v12's two Gold uploads: v12 selected under P1

The user's "Сделал 2 загрузки". Both ran with `python -B -I tools/arena.py follow v12 --selected v11` in the main checkout.
- Upload 1 is agent 6860036: placement 19.64, rank 88 of 348.
- Upload 2 is agent 6860166: placement 21.07, rank 34 of 348.
- `cgAttribute`: SAME 90/90 in each, against `artifacts/3208b011/Player.java`. The marker `58d513ae` is in all 180 games.
- The follow tool's data files were copied from the main checkout: the registry, the four new manifests and the
  re-fetched manifest of v11 upload 4.

**P1** (upload 2's `checks/arena-compare.json`):
- Mean: 20.36 against v11's Gold 17.08, +3.28 against −M = −2.46.
- Performance: 20.38 ± 0.48 against 16.89 ± 0.49 at Gold's refitted scale 3.04, +3.48 ± 0.69.
- v12 is not clearly worse, and in fact clearly better: **v12 is the selected version**. Its artifact `3208b011` is the
  rollback artifact.
- Gold's own M is now 2.54 on 2 df. The verdicts keep Bronze's 2.46, by the user's decision.

**The rule on the arena.**
- `./gradlew waveTrace -Pagents=6860036,6860166 -Pout=replays/study/gold-v12-2026-11-07/waves
  -Preplays="../../../replays/arena/v12/gold/upload-01/games ../../../replays/arena/v12/gold/upload-02/games"`: 176 games
  with a contact.
- `gold_reference.py` gives the first wave met at 287 from the back edge (median; v11 411), 37 contacts beyond 406
  (v11 88), a loss of 6.9 HP (v11 11.4) and 45 merged waves (v11 69).
- `wave_mechanisms.py` gives the HP races:
  - the distance no longer separates lost from won (322 against 324);
  - lost races still lose 10.2 HP to the first wave against 3.4, in the back half too;
  - merged waves mark 12 of 42 lost races.

**Equal scores.** 918143672 (75:75) and 918166983 (51:51) are the fifteenth and sixteenth equal-score games. Both rank
player 0, which was us, first, and the battle lists give a draw.

**Local against arena.** P2's S 0.5716 turned into +3.3 points. The panel undervalues this kind of change, as it
undervalued v08's.

Records: `docs/arena-uploads.md` (the section and the table), BOTS (Uploads, Artifacts, versions), STATUS, SUMMARY,
`docs/local-screen.md` (v12's row), `docs/rules/inferred.md` and the architecture page (now v12's). Not committed.

## 2026-11-08 — Study of v12's 180 Gold games: the loss anatomy and the next step

The user's "Можешь сделать анализ и предложить следующий наиболее перспективный шаг по улучшению бота?". Study
`docs/data/study-2026-11-08-next-step/` (README, `next_step.py` → `next_step.txt`, `readers/`, `designs/`,
`workflow-result.json`); the Gold ladder `docs/data/leaderboard-gold-2026-11-08.json`.

**Data.** cgStudy over v12's two Gold uploads, v11's two, the Legend corpus (88 games) and cg2's Gold games; DeathTrace of
both queens in v12's 180 games and of ten Legend players; the meet-back WaveTrace summaries; all manifests and bench
reports for the seat. Inputs in the main checkout's `replays/study/gold-v12-2026-11-08/` (sha256 in the README). The
public Gold leaderboard (347 agents, none eligible, top 26.54; aabzac 34th with 21.07) fetched with the corpus tools'
`explore_lb.py`; raw `replays/cg/leaderboard/gold-20261108-1849.json`.

**Method.** A workflow (`wf_7ae430be-6dc`, 29 agents planned): seven readers (losses, midgame, seat, first wave,
opponents, code, history) and four designers returned; the two refuters per proposal, the synthesizer and the critic
failed on the user's 5-hour usage limit, twice. Section 8 of `next_step.py` recomputes the two main proposals' claims on
the 42 lost races instead; the readers' other numbers are theirs and marked so.

**Findings.** 42 lost races and 29 deaths of 71 losses; 39 % against the 58 opponents rated 20+. The races: a steady
bleed (their knights on our queen 52 turns a game against 24; ours take 1 HP a phase off their queen after turn 50).
First wave: lost races meet it before the second tower stands (contact 24.5, tower 25.2); second tower 3+ turns before
the contact: 25 % lost and 1.4 HP, 3+ after: 63 % and 11.6 HP. Midgame: 200 of 360 contact episodes on turns 50-249
begin while the queen walks to a site (2.04 HP each against 0.97 at a tower; 197 of 199 with a knight already within
500). Deaths: economic collapses (opponent income 8 at turn 150 against our 1.5). Our queen pumps towers 36 % of her
turns (Legend 16 %) but loses the least per contact turn (0.47 against 0.70-1.06). Seat: the engine's collision order
(player 0's creeps, queen 0, player 1's creeps, queen 1) costs seat 1 about 1 Gold point for everyone; our bot is
point-symmetric.

**Recommendation.** "Meet every wave at a standing tower": `WAVE_TOWER_HP` (the first tower pumped to 400-500 while the
first wave comes) and `LADDER_CLEAR` (no ladder walk while an enemy knight is within ~500 after the first-wave window);
each flips 8-9 of the 42 lost races at full effect, both 19. One development sweep with the deaths tripwire, then the P2
screen on screen-pv3 block 6. Not the step: economy flags, a trailing-side hold, a rollout controller, the seat.

Records: the study README, SUMMARY (Сейчас and a decision entry), PLAN (phase 3), STATUS (the next step). Not committed.

## 2026-11-09 — Meet every wave at a tower: LADDER_CLEAR=500 passes P2; the first-wave cap does not

The user's "Да, делай" on the study's recommendation. Experiment `docs/data/exp-2026-11-09-meet-waves/` (README with the
hypothesis and the bar written first, `sweep.sh`/`sweep_table.py` → `sweep.txt`, `profile.sh`/`profile_table.py` →
`profiles.txt`, `screen.sh` → `g1.json`, `g2.json`, `g4.json`, `g4-rerun.json`, `g5.json`, `eval.sh` prepared). Raw reports
in the main checkout's `replays/bench/exp-2026-11-09-meet-waves/` (`dev/`, `profiles/`, `screen/`, the logs).

**Code** (`current`, off by default; `current` plays as v12, `MainBotTest.withDefaultsCurrentPlaysAsV12`):
- `WAVE_TOWER_HP=H`: with fewer than 2 own towers and an enemy knight wave coming, the touched tower is topped up only
  while its HP is below H (the touch rule's threshold, `MainBot.play`).
- `LADDER_CLEAR=R`, `LADDER_CLEAR_MAX=N`: after the first-wave window and with 2 own towers, while an enemy knight is within
  R and none within THREAT_RANGE, the ladder's walk to a new site (and INCOME_LATE's, and the idle walk home) becomes a
  walk to the nearest own tower outside the enemy towers' ranges (a top-up when touched); a spell lasts while a knight
  stays within R and ends after N holds (0: no cap). A gate before the ladder branch; `clearRun` like `kiteRun`.
- `cr.tools.LocalProfile`: two new lines, the second tower's turn minus the first contact, and the midgame contact
  episodes (turns 50-249) begun on a walk / at an own tower / otherwise with their HP to knights (`midgame`).
- Tests: `StrategyParamsTest.waveTowerHpCapsTheFirstTowersPumpWhileAWaveComes`,
  `ladderClearHoldsTheQueenAtHerTowerWhileAKnightIsNear`; the snapshots `versions/v01-v12.properties` list the three new
  parameters at 0 (`BotFactoryTest.frozenSnapshotIsIndependentOfLiveDefaults`). Full suite green in the worktree (27 s).
  The worktree got a copy of `refs/referee-csj/config/level4/Boss.java` for the boss series.

**Development sweeps** (seeds 1 + 397 k, k < 500, both seats; v12 controls 0.7610 against rival, 0.9390 against field):
- `WAVE_TOWER_HP` 400 / 500 / 600: S 0.474 / 0.5025 / 0.499 against v12, rival −3.1 / −1.3 / −0.9 pp, field −2.6 / −1.8 /
  −0.1 pp. The profile at 500: the second tower stands 2 turns earlier relative to the contact, the first-wave loss falls
  4.4 → 3.3 HP, the mirror's deaths halve (21 → 9), the summed tower HP at turn 50 is 4-7 % lower; no score. Off.
- `LADDER_CLEAR` 400 / 500 / 600 / 700: S 0.580 / 0.6025 / 0.605 / 0.572; rival +5.7 / +6.65 / +4.95 / +4.55 pp; field +2.4 /
  +3.3 / +3.6 / +2.6 pp; `LADDER_CLEAR_MAX=15` changes nothing. The candidate: 500, the middle of the plateau.
- Profiles (200 maps): the episodes begun on a walk fall 3.3 → 0.6 (mirror), 4.5 → 0.8 (rival), 8.0 → 1.0 (field) a game;
  our queen's knight damage on turns 50-249 8.7 → 5.7, 12.8 → 9.3, 6.8 → 1.8; deaths 21 → 18, 39 → 30, 13 → 3 per 400
  games; towers, income and gold mined unchanged; the enemy queen loses 0.5-2.7 HP less to our knights.

**P2 screen** of `current?LADDER_CLEAR=500` against v12 on PV3 screen block 6 (seeds 1006000000 + 397 i; the ledger's
next block is 7): G1 S 0.5960 (CI 0.5817..0.6103), W/D/L 2202/364/1434, 1931 of 2000 maps changed; G2 boss 0.9990 against
0.9990; G4 field 0.9690 against 0.9180, +0.0510 (CI +0.0414..+0.0606); G5 rival 0.8140 against 0.7392, +0.0747
(CI +0.0588..+0.0907); G3 skipped. G0: one slow turn in the first field run at 8 threads (52.7 ms; v12's own mirror had a
64.1 ms turn); the series repeated at 2 threads held the same 4000 games (`same_games.py`) with no slow turn, so G0 passes
by the quiet repeat as in the waves experiment. **Verdict: pass; the local champion by P2.** The default, a freeze as v13,
the final evaluation on evaluation block 2 and an upload are the user's decisions.

Records: the experiment README, the seed ledger (block 6 used, next 7), BOTS (flags), STATUS, SUMMARY, PLAN. Not
committed.

## 2026-11-09 — Freeze v13: no ladder walk into a visible wave (LADDER_CLEAR=500); the final evaluation and the artifact

The user's "Заморозим v13".
- `Params.LADDER_CLEAR` defaults to 500. `cr.bot.v13.GreedyBot`, `RoutePlanner` and `ApproachPlanner` are copies of
  `current` at the freeze (package, class names, the frozen header, `name()`; no `queenSteps()`); `versions/v13.properties`
  is v12's snapshot with LADDER_CLEAR=500. `BotFactory`, `LocalGames` (v13 the default opponent, 16 bots),
  `visualizer/local.js`, `docs/visualizer.md` and `.ai/project.md` (v00-v13; AGENTS.md resynced) know v13.
- Tests: `MainBotTest.withDefaultsCurrentPlaysAsV13` (fixture seeds and PV3 screen block 6's first maps) and
  `withV12ParamsCurrentPlaysAsV12`; the V02-V11 constants add `LADDER_CLEAR=0`; `BotFactoryTest`'s chain v12 → v13 and the
  live defaults = v13; `LocalGamesTest`'s bot count 16; `StrategyParamsTest.ladderClearHolds…` sets `LADDER_CLEAR=0` for its
  v12 baseline. Full suite green: 173 tests, 3 corpus tests skipped (no arena replays in the worktree).
- `docs/bot-architecture.html` rewritten for v13: the header, the decision flow (a step 2b node), the pseudocode, the
  target-choice list, the versions table and paragraph, the files map, the gaps and the directions row.

**Final evaluation** (`eval.sh`, PV3 evaluation block 2, seeds 2002000000 + 397 i; the ledger's next evaluation block is 3):
G1 S 0.5958 (CI 0.5819..0.6096) against v12, W/D/L 2190/386/1424, 1942 of 2000 maps changed; G2 boss 1.0000 against
1.0000; G4 field 0.9673 against 0.9239, +0.0434 (CI +0.0342..+0.0526); G5 rival 0.8170 against 0.7509, +0.0661
(CI +0.0505..+0.0817); G3 skipped. G0: 5 slow turns in the first field run at 8 threads (93.3 ms; v12's own field control
had 2 with 100.1 ms, its mirror 1); the series repeated at 2 threads held the same 4000 games (`same_games.py`) without a
slow turn (5.7 ms), so G0 passes by the quiet repeat (`eval-g4-rerun.json`). The screen replicates (0.5960 → 0.5958,
field +5.1 → +4.3 pp, rival +7.5 → +6.6 pp). S is 0.004 below the 0.60 upload recommendation: an upload is an exception.

**Artifact.** `./gradlew submit` with empty PARAMS: `85897d90c377ada9354eb5238081aa16c3bfb6c6de3bca4a88ae089d6bc9cddd`,
89,252 chars, build `a19a87ca` (turn 0: `TRAIN 10 1 9 10 8 7 12 10`), config `0c433665`; a second build gave the same
bytes; the kept copy `artifacts/85897d90/Player.java` in the main checkout. procCheck vs v01 100 maps @5000: 200/200
identical to current, 200 wins, 0 timeouts, no extra, late or stderr lines, max first turn 199.7 ms, later 8.0 ms; vs boss
100 maps @6000: 200/200, 200 wins, 0 timeouts, max first 148.0 ms, later 6.1 ms. Raw reports and logs in the main
checkout's `replays/bench/exp-2026-11-09-meet-waves/` (`eval/`, `submit.log`, `proccheck-*.log`).

Records: the experiment README (v13, Final evaluation, Artifact), BOTS (versions, Artifacts, flags), STATUS, SUMMARY,
PLAN, the seed ledger (evaluation block 2 used, next 3), `docs/local-screen.md` (v13's row), the architecture page. Not
committed; not uploaded (the user's decision: `tools/arena.py follow v13 --selected v12`, then the paste, twice).

## 2026-11-09 — v13 upload 1 in Gold: 21.95, rank 18/348; the follow's stop and its fix

The user's "Можешь запустить загрузку?" while away from the computer: neither browser had a CodinGame session (the
built-in pane showed the guest IDE, Claude in Chrome was not connected), so the paste stayed the user's. Back at the
computer the user played one IDE game against the boss, pasted the kept copy `artifacts/85897d90/Player.java` and ran
`python -B -I tools/arena.py follow v13 --selected v12` from the main checkout.
- **The stop.** For a new label the command defaults to the main checkout's `build/submit/Player.java`; v13 had been
  built in the worktree, so that file still held v12's `3208b011`. The command saved v12 upload 2 (21:03:38, 90 games,
  marker `58d513ae`), saw agent 6868408 at 21:06:35 and stopped at 21:06:41: "plays build a19a87ca, not build 58d513ae
  of the artifact 3208b011". The agent's build was v13's: the paste was right, the default wrong.
- **The fix.** `follow v13 --selected v12 --artifact artifacts/85897d90/Player.java` at 21:09:40 resumed from step 4:
  game 919171235 carried build `a19a87ca`, the agent was registered as v13 upload 1 at 21:09:45 and followed (snapshots
  at 21, 33, 44, 57, 68, 81, 92, 95 and 100 %; the games fetched every minute).
- **Placement** complete at 21:29:49: local rank 18/348 (global 132), score 21.95, `eligibleForPromotion` false;
  90 games, 52 wins (seat 0: 27 of 41, seat 1: 25 of 49); the referee removed the opponent in 919171244 (Aa123456, a
  turn-2 timeout); 919175620 ended 70:70 at turn 250 with us as player 0, ranked a win (the seventeenth equal-score
  game); 59 of 90 games reached turn 250. Opponents' median 21.81 (v12's placements 19.5-19.7): 11 games against players
  rated 23 or more (5 won), 53 against 20-23 (28), 21 against 17-20 (14), 4 below 17 (4).
- **Code:** `cgAttribute` against the kept copy: SAME 90/90, 19,341 turns; marker `a19a87ca` in all 90.
- **P1:** performance 22.20 ± 0.67 (Gold scale 2.99 on five uploads); 21.95 against v12's mean 20.36 is +1.59, inside
  M 2.46: no early rollback (that needs a score below 15.43); the second upload decides. The follow for it was started
  at 21:31:08 and names the kept copy to paste.
- Files: `replays/arena/v13/gold/upload-01/{games,snapshots,checks}` in the main checkout;
  `docs/data/arena-6868408-placement-2026-11-09.json`, `docs/data/arena-6868408-games-2026-11-09.json`, the registry entry
  and the re-fetched manifest of v12 upload 2, copied into the worktree.

Records: BOTS (Uploads), `docs/arena-uploads.md` (the v13 section), STATUS, SUMMARY, PLAN, `docs/local-screen.md`. The
memory note on `tools/arena.py` gained the `--artifact` lesson. Not committed.

## 2026-11-09 — v13 upload 2 in Gold: 21.65, rank 22/348; P1 verdict: v13 selected

The user's "Вставил" (the second paste of the kept copy). `python -B -I tools/arena.py follow v13 --selected v12`, started
at 21:31:08 (the kept copy is the default for a known label), saved upload 1 at 21:31:13, saw agent 6868474 at 21:35:15
(10 % played), checked game 919179259 (build `a19a87ca`) and registered it as v13 upload 2 at 21:35:20.
- **Placement** (snapshots at 10, 22, 34, 50, 64, 81, 92 and 100 %): complete at 21:54:29, local rank 22/348 (global
  136), score 21.65, `eligibleForPromotion` false; 90 games, 55 wins (seat 0: 25 of 39, seat 1: 30 of 51); no referee
  removal, no equal score; 58 of 90 games reached turn 250; opponents' median 20.44 (58 games against 20-23, 30 points;
  24 against 17-20, 17; 8 below 17, 8).
- **Code:** `cgAttribute` against the kept copy: SAME 90/90, 18,930 turns; marker `a19a87ca` in all 90.
- **P1 against v12** (upload 2's `checks/arena-compare.json`; Gold's scale refitted at 2.71 on six uploads):
  - mean 21.80 (21.95, 21.65; SD 0.21) against 20.36, +1.44 against −M = −2.46;
  - performance 21.75 ± 0.43 against 20.27 ± 0.45, +1.48 against −2 SE = −1.23;
  - neither rollback condition holds: **v13 is the selected version**; `85897d90` is the rollback artifact for the next
    candidate. Gold's own M is 2.09 on 3 df; the verdicts keep Bronze's 2.46 (the user's decision of 2026-11-07).
- **Local against arena:** S 0.5958 → +1.44 points (v12: 0.5716 → +3.28).
- Files: `replays/arena/v13/gold/upload-02/{games,snapshots,checks}` in the main checkout;
  `docs/data/arena-6868474-placement-2026-11-09.json`, `docs/data/arena-6868474-games-2026-11-09.json`, the registry and
  the re-fetched manifest of upload 1, copied into the worktree. The equal-score game 919175620 of upload 1 is the
  seventeenth in `docs/rules/inferred.md`.

Records: BOTS (versions, Uploads, Artifacts), `docs/arena-uploads.md` (upload 2 and the verdict), STATUS (the selected
version), SUMMARY, PLAN, `docs/local-screen.md`, `docs/rules/inferred.md`, the architecture page (v13 selected), the
experiment README (On the arena). Not committed: the commit of the freeze and the records, then the merge into master,
are the user's.
