# Experiment 2026-11-07: meet the first wave back (Gold, over v11)

## Hypothesis and success bar (written before the comparisons)

The replay study of the first wave (`docs/data/study-2026-11-07-gold/`, section 6) found what decides the HP races v11
loses in Gold:
- **Where the queen meets the enemy's first knight wave.** Lost races meet it 444 from our back edge, won ones 380. Beyond
  406 the wave costs 15.8 HP in lost races and 5.6 in won ones; within 406 it costs 6.2 and 6.0.
- **Merged waves.** A second enemy group arrives while the first is on her in 19 of 44 lost races and 7 of 62 won ones,
  20 of the 26 in the forward half. They carry the whole gap.
- In the costliest race (917851391, 58 HP in 20 turns) both show up:
  - a kite step carries her past 500 from her second tower, so the defence rule fires;
  - the defence rule walks her 230 toward the enemy to a new tower, through five knights;
  - she then stands at that tower topping it up, with ten knights on her for eight turns.

**Local check before any code** (`profile.sh`, `profile_table.py`, `profiles.txt`; v11 against each opponent on the
development seeds 1 + 397 k, k < 200, both seats):

| v11 vs | contact turn | distance at contact (median) | beyond 406 | merged waves | loss: merged / unmerged | loss: beyond / within 406 |
| --- | --- | --- | --- | --- | --- | --- |
| v11 (mirror) | 24 | 409 | 201 of 400 | 95 | 16.9 / 5.0 | 10.7 / 5.0 |
| rival | 25 | 423 | 206 of 400 | 97 | 15.7 / 4.6 | 9.7 / 4.8 |
| field | 21 | 360 | 160 of 400 | 4 | 15.0 / 7.3 | 8.4 / 6.7 |
| Gold games (171 with a contact) | 25 | 411 | 88 of 171 | 69 | 18.5 / 6.7 | 14.4 / 8.3 |

The Gold row comes from `gold_reference.py`, which applies LocalProfile's measures to the study's WaveTrace files of v11's
two placements: all games with a contact, not only the HP races. The mirror and the rival reproduce Gold's pattern, so
the candidate can be tested locally. They merge less often than Gold (24 % of games against 40 %) and lose less to the
first wave (7.8 and 7.3 HP against 11.4).

**Hypothesis.** While the enemy's first wave comes, keep the queen nearer her back edge: no forward ladder targets, no
defence walk toward the enemy, no forward kite step. If so:
- the median distance at contact falls below 400;
- there are fewer merged waves and a lower mean first-wave loss against the mirror and the rival;
- the HP races improve.

The candidate flags are off by default (= v11).

**Success bar** (as for the earlier first-wave candidates):
- on the development seeds 1 + 397 k, k < 500, S against v11 above 0.50;
- a delta against `rival` and `field` of at least −2 pp;
- the first-wave measures moving as predicted.

Only then a P2 screen on a fresh block: screen-pv3 block 5, seeds 1005000000 + 397 i.

## Probes with existing parameters

Two existing parameters were run first, through `sweep.sh` and `profile.sh`, while the rules were being designed. They
are not candidates. They test whether the existing levers move the first wave.

The raw reports are in `replays/bench/exp-2026-11-07-meet-back/probes/` and `profiles-probes/`. Their v11 controls were
made before the four new parameters entered the version snapshots, so `bench_pair` would refuse to mix them with the
sweep's reports, and they stay apart. The table comes from
`python -B -I docs/data/exp-2026-11-07-meet-back/sweep_table.py replays/bench/exp-2026-11-07-meet-back/probes`.
- `THREAT_RADIUS=350`, against 500, shortens the defence rule's reach.
- `KITE_PULL=200`, against 100, pulls the kite steps harder toward the own towers.

| arm | S vs v11 (500 maps) | delta vs rival | delta vs field | first-wave loss, mirror / rival | merged waves, mirror / rival | queen deaths in the mirror profile |
| --- | --- | --- | --- | --- | --- | --- |
| v11 | — | — | — | 7.8 / 7.3 | 95 / 97 | 18 |
| `THREAT_RADIUS=350` | 0.3925 (0.3652..0.4198) | −0.1025 | −0.0325 | 8.7 / 8.2 | 102 / 102 | 29 |
| `KITE_PULL=200` | 0.5035 (0.4779..0.5291) | −0.0080 | −0.0025 | 8.0 / 7.4 | 101 / 103 | 24 |

Neither moves the distance at contact: 409 in the mirror, as v11, since both act only after the contact.
- A shorter defence reach loses badly. The second tower that the defence rule builds within 500 of the queen is
  worth keeping, so a rule that stops forward defence walks must not stop the tower itself.
- A harder pull toward the towers changes nothing.

`KITE_PULL=200` had 12 slow turns in 11 games at 8 threads. They were not repeated, since a probe is not a candidate.

## Design

A workflow of four agents designed the rules: three designers (the ladder; the defence rule and kiting; the build order)
and a judge. The proposals and the verdict are in `design_workflow.json`. The agents' Python reconstructions (ports of
MapGen and of `nextSite` over the Gold WaveTrace files) were scratch work and are not kept. Their numbers are estimates,
which this experiment's profiles check.

**What sets the place of the contact.**
- The ladder, before any threat rule acts:
  - In the forward half of the Gold races, the new tower the queen was walking to at the contact lay more than 500 from
    her back edge in 27 of 29 lost races and 22 of 24 won ones.
  - 25 of those 27 targets were chosen after the enemy's first knight training, which comes at turn 11.5 (median).
  - The probes above agree: neither moves the place.
- The cause is the pull toward the home corner. v11's target is the free site nearest the midpoint of the queen and the
  home corner. Once the corner's sites are taken, that pulls her forward along the home edge.

**The flags** (all off by default when designed, so `current` played as v11; since v12 the defaults are
`WAVE_ANCHOR=50` and `WAVE_UNTIL=30`):
- `WAVE_ANCHOR=A` with `WAVE_UNTIL=T` (default 50).
  - While an enemy knight barracks stands or an enemy knight is on the board, before turn T, from the second tower on, the
    ladder's tower target is the free site nearest the point at the walker's height, A % of the way back to her edge.
  - The mines, the first barracks, the forward barracks of v10 and the first tower stay as in v11.
  - The judge's estimates for the mirror:
    - A = 50: the median distance at contact about 330-350, 110-130 games beyond 406, 75-85 merged waves, a first-wave
      loss of 7.0-7.4.
    - A = 100: about 300-330, 90-110, 70-80 and 6.8-7.2.
  - The loss and merge estimates are upper bounds.
- `DEFENCE_FWD=F`.
  - The defence rule builds only on a free site less than F ahead of the queen toward the enemy's side, or on the site she
    touches. With no such site it builds nothing.
  - It does not move the contact. The estimate is 0.5-1 HP less first-wave loss locally.
  - The risk: about one Gold defence episode in five goes without its second tower, and `THREAT_RADIUS=350` shows how
    much that tower is worth.
- `STAND_REACH=R`.
  - When no kite step gains and the nearest knight is within R, the queen takes the best-scoring kite step that moves her
    40 or more, instead of standing at her tower topping it up.
  - This is the study's candidate B, secondary here; the local signal is expected to be small.

**Rejected.**
- `WAVE_FRONT` and `WAVE_BACK`: cut the ladder's targets at a distance from the back edge. They are dominated by the
  anchor, which moves the contact as far with a tenth of the extra walk and never leaves the ladder without a target.
- `WAVE_STAY`: hold before the contact. It turns the second tower into a defence build under the knights, as
  `WAVE_TOWERS` did.
- `WAVE_AWAY`: park at a back tower as the wave arrives. The evidence is weak, it overlaps the anchor, and it parks her
  where the study saw standing cost the most.
- `KITE_FWD`: penalise forward kite steps. Kiting is already net backward (−108 to −115 over the 20 turns), and a forward
  step costs nothing measurable on the next turn. So the hypothesis's kite clause is not tested.

## Development sweep 1 (`sweep.txt`, `profiles.txt`, `outcomes.txt`, `profiles-flow.txt`)

Seeds 1 + 397 k, both seats: the benches cover 500 maps, the profiles 200.
- Raw reports: `replays/bench/exp-2026-11-07-meet-back/dev/`, `profiles/`, `profiles-flow/`.
- Tables: `sweep_table.py`, `profile_table.py`, `outcomes.py`.
- Every arm sets `WAVE_UNTIL=50` when it sets `WAVE_ANCHOR`.

| arm | S vs v11 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `WAVE_ANCHOR=50` | 0.5115 (0.4809..0.5421) | −0.0315 (−0.0641..+0.0011) | −0.0560 (−0.0789..−0.0331) |
| `WAVE_ANCHOR=100` | 0.5050 (0.4723..0.5377) | −0.0575 (−0.0926..−0.0224) | −0.0515 (−0.0754..−0.0276) |
| `DEFENCE_FWD=50` | 0.5480 (0.5253..0.5707) | +0.0265 (−0.0007..+0.0537) | −0.0315 (−0.0535..−0.0095) |
| `DEFENCE_FWD=150` | 0.5150 (0.4976..0.5324) | +0.0020 (−0.0186..+0.0226) | −0.0200 (−0.0372..−0.0028) |
| `STAND_REACH=155` | 0.4895 (0.4736..0.5054) | −0.0195 (−0.0390..−0.0000) | +0.0010 (−0.0093..+0.0113) |
| `WAVE_ANCHOR=50,DEFENCE_FWD=50` | 0.5635 (0.5313..0.5957) | +0.0085 (−0.0257..+0.0427) | −0.0410 (−0.0644..−0.0176) |
| the same with `STAND_REACH=155` | 0.5645 (0.5319..0.5971) | +0.0030 (−0.0312..+0.0372) | −0.0385 (−0.0612..−0.0158) |

Failures: `WAVE_ANCHOR=100` had 2 slow turns and `STAND_REACH=155` had 1. The other arms had none.

**The mechanism works as designed, and more strongly than estimated.** Mirror profiles:

| arm | distance at contact (median) | beyond 406 | merged waves | first-wave loss | HP lost on turns 25-50 | queen deaths |
| --- | --- | --- | --- | --- | --- | --- |
| v11 | 409 | 201 | 95 | 7.8 | 6.8 | 18 |
| `WAVE_ANCHOR=50` | 305 | 92 | 48 | 4.2 | 3.6 | 15 |
| `WAVE_ANCHOR=100` | 274 | 58 | 53 | 4.4 | 3.8 | 18 |
| `DEFENCE_FWD=50` | 409 | 201 | 71 | 6.1 | 5.5 | 17 |
| `WAVE_ANCHOR=50,DEFENCE_FWD=50` | 305 | 92 | 31 | 3.2 | 2.8 | 11 |

Against the rival the same arms cut the first-wave loss from 7.3 to 3.9 (A = 50), 4.1 (A = 100), 5.7 (the defence
limit) and 2.8 (both). `STAND_REACH=155` removes the
standing turns but leaves the loss as it was (7.9).

**No arm passes the bar clearly.**
- The anchors and the defence limit at 50 lose 3-6 pp to field. The anchor also loses 3-6 pp to the rival.
- `DEFENCE_FWD=150` sits on the bar's edge.
  - field −2.00 pp (CI −3.72..−0.28), rival +0.20, S 0.515 (CI 0.498..0.532).
  - Its first-wave effect is small: a loss of 7.4 against 7.8, and the contact unchanged.
  - It was not taken further.
- `STAND_REACH=155` is level with field but scores 0.49 against v11 and loses 2 pp to the rival.
- Against field the queen dies more often. Per 1000 games:

  | | v11 | `DEFENCE_FWD=50` | `WAVE_ANCHOR=50` | both |
  | --- | --- | --- | --- | --- |
  | our queen dies | 21 | 37 | 56 | 41 |
  | HP races lost at turn 250 | 37 | 52 | 57 | 56 |

- The HP flow by phase (`profiles-flow.txt`) shows where the first-wave gain goes. Against field, all three arms save
  1.3-2.7 HP in turns 0-49.
  - Our queen then loses more to the enemy knights in turns 50-249: 7.3, 9.7 and 8.5 HP against v11's 5.3.
  - Our knights take less off the field queen: 23.3-24.0 HP against 25.1.
- Against the rival, the anchor loses 14.1 HP after turn 50, against v11's 9.5; `DEFENCE_FWD=50` loses 9.6.
- The rules help the first wave and cost later.
  - The anchor's towers stand deeper, so the midgame is fought from a different base.
  - `DEFENCE_FWD` without an end also gives up later forward defence towers.

## Development sweep 2 (`sweep2.txt`, `profiles2.txt`)

The hypothesis from sweep 1: the cost after turn 50 comes from the rules acting, or their towers standing, beyond the
first wave. `DEFENCE_UNTIL=T` (new, default 0 = the whole game) ends `DEFENCE_FWD` at turn T. The arms:
- the defence limit to turn 60 or 100;
- the anchor to turn 35;
- a milder anchor (`A=25`);
- the anchor for the whole game (`WAVE_UNTIL=0`, a diagnostic);
- the anchor to turn 35 with the defence limit to turn 60.

Raw reports are in `dev2/` and `profiles2/`, with their own v11 controls. `DEFENCE_UNTIL` entered the version snapshots
after sweep 1, so the two sweeps' reports are not mixed.

| arm | S vs v11 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `DEFENCE_FWD=50,DEFENCE_UNTIL=60` | 0.5385 (0.5174..0.5596) | +0.0025 (−0.0222..+0.0272) | −0.0350 (−0.0553..−0.0147) |
| `DEFENCE_FWD=50,DEFENCE_UNTIL=100` | 0.5430 (0.5207..0.5653) | +0.0070 (−0.0181..+0.0321) | −0.0315 (−0.0521..−0.0109) |
| `WAVE_ANCHOR=50,WAVE_UNTIL=35` | 0.5520 (0.5250..0.5790) | +0.0065 (−0.0228..+0.0358) | −0.0135 (−0.0313..+0.0043) |
| `WAVE_ANCHOR=25,WAVE_UNTIL=50` | 0.5285 (0.4980..0.5590) | −0.0095 (−0.0402..+0.0212) | −0.0505 (−0.0721..−0.0289) |
| `WAVE_ANCHOR=50,WAVE_UNTIL=0` | 0.4890 (0.4560..0.5220) | −0.0085 (−0.0430..+0.0260) | −0.0490 (−0.0716..−0.0264) |
| anchor to turn 35, defence limit to turn 60 | 0.5845 (0.5551..0.6139) | +0.0255 (−0.0062..+0.0572) | −0.0280 (−0.0501..−0.0059) |

Failures: `DEFENCE_UNTIL=100` had 8 slow turns; the other arms had none.

- **The anchor to turn 35 passes the development bar.**
  - It moves the first wave as the anchor to turn 50 did: in the mirror, a contact at 305 (median), 48 merged waves
    and a first-wave loss of 4.2.
  - It keeps v11's choice for the towers after turn 35. Those towers cost the anchor to turn 50 its midgame.
  - Against field it loses 7.5 HP after turn 50, against v11's 5.3 and the anchor to turn 50's 9.7.
  - It scores 0.552 against v11, +0.65 pp against the rival and −1.35 pp against field.
- **The anchor for the whole game loses** (S 0.489). A deep base for the whole game is worse still.
- **The defence limit costs field 3-3.5 pp even when it ends at turn 60.** Its cost against field is decided in the first
  wave, not later. With the anchor it makes the strongest arm against v11 (0.5845), but it fails field. It is dropped.

The anchor's neighbours, same seeds and controls:

| arm | S vs v11 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `WAVE_ANCHOR=50,WAVE_UNTIL=30` | 0.5590 (0.5335..0.5845) | +0.0040 (−0.0241..+0.0321) | −0.0030 (−0.0178..+0.0118) |
| `WAVE_ANCHOR=50,WAVE_UNTIL=40` | 0.5440 (0.5161..0.5719) | +0.0125 (−0.0171..+0.0421) | −0.0230 (−0.0423..−0.0037) |
| `WAVE_ANCHOR=75,WAVE_UNTIL=35` | 0.5515 (0.5228..0.5802) | −0.0020 (−0.0324..+0.0284) | −0.0085 (−0.0276..+0.0106) |
| `WAVE_ANCHOR=50,WAVE_UNTIL=25` | 0.5575 (0.5332..0.5818) | −0.0040 (−0.0329..+0.0249) | +0.0030 (−0.0107..+0.0167) |

`WAVE_UNTIL=30` had 11 slow turns at 8 threads, `WAVE_UNTIL=25` had 5.

- The shorter the window, the less the anchor costs against field, with S level at 0.54-0.56. The towers it gains are the
  ones chosen before the first contact, at turn 21-25 (median).
- The trend flattens between 25 and 30.
- At 25 the first wave is helped a little less. Mirror profiles (`profiles2.txt`):

  | window to | contact (median) | merged waves | first-wave loss |
  | --- | --- | --- | --- |
  | 25 | 310 | 57 | 4.8 |
  | 30 | 305 | 48 | 4.3 |
  | 35 | 305 | 48 | 4.2 |

**The candidate: `WAVE_ANCHOR=50,WAVE_UNTIL=30`**, the middle of the plateau. It is screened alone on a fresh block,
since several windows were compared on the development seeds.

## P2 screen (`screen.sh`, `g1.json`, `g2.json`, `g4.json`, `g5.json`)

Candidate `current?WAVE_ANCHOR=50,WAVE_UNTIL=30` against the control v11 (the local champion and the selected version),
PV3 screen block 5: seeds 1005000000 + 397 i, i < 2000, both seats; boss on the first 500 maps (3 threads, 200 ms).
Commit ecac692 with this experiment's uncommitted changes (the reports record the status). Raw reports in
`replays/bench/exp-2026-11-07-meet-back/screen/`.
- **G0 passes:** the candidate had no error, timeout, slow turn or extra line in any of its 13000 games (4000 each
  against v11, field and the rival, 1000 against boss). v11 had one slow
  turn in the mirror and one against field; a control's failure does not count for G0.
- **G1 passes:** S = 0.5654 (95 % CI 0.5523..0.5784) against v11; W/D/L 2140/243/1617. 1320 of 2000 maps differ from
  v11's mirror, so this is a strategy change, not a targeted fix.
- **G2 passes:** boss 1.000 against 1.000, delta 0.0000.
- **G4 passes:** field 0.9137 against 0.9286, delta −0.0149 (CI −0.0227..−0.0071), within −2 pp.
- **G5 passes:** rival 0.7465 against 0.7330, delta +0.0135 (CI −0.0013..+0.0283).
- G3 is skipped: the code of `old` is still missing.

## Verdict

`WAVE_ANCHOR=50,WAVE_UNTIL=30` passes P2. It meets the enemy's first wave nearer the back edge: in the mirror
(development profiles) it meets the wave 305 from the back edge instead of 409, with 48 merged waves instead of 95 and
a first-wave loss of 4.3 HP instead of 7.8. It wins 0.565 against v11 on the fresh block.

The cost is against field: −1.5 pp, inside the gate. The anchor's towers after the first wave cost the midgame against
the strong economies; the window to turn 30 keeps that cost small.

Rejected on the development seeds:
- `DEFENCE_FWD=50`, with or without an end, costs field 3-3.5 pp, although it wins the mirror (S 0.54-0.55).
  `DEFENCE_FWD=150` costs 2.0 pp at a small first-wave effect.
- `STAND_REACH`: it removes the standing turns without lowering the loss.
- The anchor with longer windows, for the whole game, or at 25 %.
The flags stay in the code, off.

By P2 a passing candidate becomes the local champion. By the user's decision ("Да, v12", 2026-11-07) it is
`current`'s default and the frozen v12.

## Final evaluation (`eval.sh`, `eval-g1.json`, `eval-g2.json`, `eval-g4.json`, `eval-g5.json`)

The user's "Да, готовь": v12 against the selected version v11 on PV3 evaluation block 1, seeds 2001000000 + 397 i,
i < 2000, both seats. boss plays the first 500 maps (3 threads, 200 ms). Raw reports are in
`replays/bench/exp-2026-11-07-meet-back/eval/`.

| gate | result |
| --- | --- |
| G0 | passes: v12 had no error, timeout, slow turn or extra line in its 13000 games (4000 each against v11, field and the rival, 1000 against boss) |
| G1 | passes: S 0.5716 (95 % CI 0.5587..0.5845) against v11, W/D/L 2156/261/1583, 1299 of 2000 maps differ from v11's mirror |
| G2 | passes: boss 0.9990 against 0.9990, delta 0.0000 |
| G4 | passes: field 0.9244 against 0.9366, delta −0.0123 (CI −0.0196..−0.0049) |
| G5 | passes: rival 0.7516 against 0.7354, delta +0.0163 (CI +0.0022..+0.0303) |
| G3 | skipped |

- The screen's numbers replicate on the fresh block: S 0.565 and 0.572, field −1.5 and −1.2 pp, rival +1.4 and +1.6 pp.
- S is below 0.60, so P2 does not recommend the upload. Uploading v12 is an exception and the user's decision.
- v12's longest turn against boss was 121 ms, in a series at 3 threads with a 200 ms budget, against v11's 17.5 ms. It is
  not a failure there; procCheck measures the artifact at CG's 50 ms.

## Artifact

`./gradlew submit` with empty PARAMS (v12's defaults):
- `3208b011415504ac2776a72159e9d79fc92d7d3101bd7070b8eb282ec4b30271`, 88,517 characters;
- build `58d513ae` (turn 0: `TRAIN 5 8 13 5 1 3 10 14`), config `7b8e0525`.

A second build and a clean export of the working tree gave the same bytes. The export went through a temporary index
(tree 355b8617), so the real index was left alone. procCheck vs v01 100 maps @5000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 264.8 ms, later 6.7 ms; vs boss 100 maps @6000: 200/200 identical to current, 200 wins, 0 timeouts, 0 kills, no extra, late or stderr lines, max first turn 298.1 ms, later 7.7 ms. The kept copy is `artifacts/3208b011/Player.java`
(gitignored).
