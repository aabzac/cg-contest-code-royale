# Live rules inference, 2026-10-28

Evidence for the live rules in `docs/rules/inferred.md`. It comes from the workflow
`code-royale-live-rules` (run `wf_841110c2-70b`): 7 agents, about 2.25M subagent tokens, 99 minutes.

1. Four investigators each worked in their own copy of the project:
   - spawn placement;
   - movement and collisions;
   - turn order and HP;
   - a second replay corpus.
2. The spawn, movement and turn-order patches were each checked by an independent adversarial
   verifier, including on the new corpus `replays/cg2`, which the investigators did not use to fit.

All three verdicts were `confirmed`. The corpus agent's patch had no verifier of its own; the
movement and turn-order verifiers cross-checked its two rules:

- **Build-conflict parity**: `turnBase=0` fixes the conflict game 906051728.
- **Creep cap**: confirmed, but its check order was rejected. It checks the cap before gold, and
  in game 912219998 the replay shows the gold warning on all 32 turns where both checks fail.

The confirmed rules were then merged by hand into one implementation without switches (the
commit that adds this directory).

## Files

| Path | Content |
| --- | --- |
| `workflow-result.json` | Raw workflow result. Per item: conclusion, evidence, rejected hypotheses, remaining issues and the verifier's verdict (metrics, reasoning, problems) |
| `patches/spawn.patch` | Spawn agent: corner offsets, spawn timing switch, legacy entity order, diagnostics, 2 tests |
| `patches/movement.patch` | Movement agent: legacy entity order, towers first, 30-unit cap, plain/spawn dumps, warning counter, 5 tests |
| `patches/turnorder.patch` | Turn-order agent: structures by type, creep limit, tower-shot / knight-hit / death decoding, 2 tests |
| `patches/turnorder_exp.patch` | Turn-order agent: every alternative it tested as a `-P` switch (rejected variants) |
| `patches/verify-turnorder-alts.patch` | Turn-order verifier: alternative rules (`-PverifyAlt`), warning check, spawn corners; applies on top of `turnorder.patch` |
| `patches/corpus.patch` | Corpus agent: build-conflict parity `TURN_BASE = 0`, creep cap checked before gold (rejected order), 2 tests |
| `tools/spawn`, `tools/movement`, `tools/turnorder`, `tools/corpus` | Investigators' analysis scripts as used. The corpus tools fetched `replays/cg2` through the public CG services, paced at least 0.8 s apart |
| `tools/verify-*` | Verifiers' scripts (time-0 offset check, site order, strict comparison, ablation runners) |
| `tools/mutate.py` | Mutation check of the merged code (below) |
| `mutation-check.txt` | Its output |

The scripts contain the paths of the session scratchpad they ran in (`.../scratchpad/wf/...`).
Run them with `python -I` after adjusting the paths. The heavy per-turn dumps (about 300 MB) are
not kept; the patches' `-PdumpPlain`/`-PdumpSpawn` switches regenerate them.

## Reproducing the experiments

The patches were made against the source tree of commit `f53933f`. Their `build.gradle` hunks
only add property names to the `cgValidate` pass-through list, and they no longer apply to that
file. Example:

```bash
git worktree add ../cr-spawn f53933f
cd ../cr-spawn
git apply -p1 --exclude=build.gradle ../cg-contest-code-royale/docs/data/inference-2026-10-28/patches/spawn.patch
```

Then add the patch's property names (`spawnTiming`, `endFix`, `logNew`, ...) to the list in
`build.gradle` and run, for example, `./gradlew cgValidate -Preplays=<abs>/replays/cg -Presync=true
-PspawnOffset=0 -PspawnTiming=0 -PlegacyOrder=false`. The exact command for every number is in
the `evidence` and `metrics` fields of `workflow-result.json`.

## Merged implementation (this commit)

`./gradlew cgValidate -Preplays=replays/cg` and `-Preplays=replays/cg2`, strict and resync give
identical results:

| Corpus | Games consistent | Turns matched | Units plain / spawn turns | Warning turns | Resync corrections |
| --- | --- | --- | --- | --- | --- |
| `replays/cg` (98) | 97 | 15,374 / 15,374 | 13424/13424, 1950/1950 | 113/113 | none |
| `replays/cg2` (138) | 137 | 20,438 / 20,438 | 18330/18330, 2104/2104 | 3407/3407 | none |

In each corpus one game fails, and only on `winner 0 vs -1`: equal scores, which CG ranks for
player 0 (911493205, 908057307).

Mutation check: `python -I tools/mutate.py <repo> <scratch dir>` copies the repository, reverts
one rule at a time, then runs `./gradlew test` and strict `cgValidate` on both corpora.
`mutation-check.txt` shows that every revert fails at least one unit test and at least one corpus.
