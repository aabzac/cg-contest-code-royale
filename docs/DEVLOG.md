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
