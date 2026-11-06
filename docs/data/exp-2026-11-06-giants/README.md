# Experiment 2026-11-06: an answer to the enemy's giants, against the rival R0 (P2 candidate)

**Why.** The Silver study (`docs/data/study-2026-11-05-silver/`) found giant players beat v08 by about 2 rating
points more than their ratings predict. Against giants our first tower falls around turn 138, and our queen dies
late with 3 towers left. R0 (gate G5 since PV4) trains at most two giants from turn 80, and they cost v09 about
5 pp (`docs/data/exp-2026-11-06-rival/`). The user's choice of 2026-11-06, after the income experiment
(`docs/data/exp-2026-11-06-income/`).

**Starting point.**
- Revision: commit `7e5e346` plus the uncommitted rival, PV4 and income changes of the worktree
  `claude/bot-improvement-next-steps-4b8f4e`; each bench report records the status and the sha256 of the changes.
- Frozen v09, the referee and the harness are unchanged.
- `cr.tools.LocalProfile` gains a giants line: enemy giants that came out, own towers lost while a giant touched them,
  the other towers lost, and the first turn a giant touched an own tower. Diagnostics only.

**The problem** (`LocalProfile`, v09 on both seats, 500 maps on 1 + 397 k):

| | v09 against R0 | v09 against R0 without giants (`rival?GIANTS=0`) |
| --- | --- | --- |
| v09's wins | 69.9 % | 74.8 % |
| our queen's deaths, enemy queens killed (of 1000 games) | 135, 131 | 62, 100 |
| enemy giants | in 884 games, 1.7 a game | none |
| own towers lost to giants, to other causes (means, in games with giants) | 3.8, 1.0 | - |
| first giant at an own tower (median) | turn 151 | - |

The giants knock down most of our line, and our queen dies twice as often.

**The answers** (`Params`, `MainBot`, off by default):
- `GIANT_GUARD=D`: with no knight threat, while an enemy giant stands within D of an own tower's centre, the queen walks
  to that tower and tops it up. A top-up adds 100 HP and a giant busts 80 a turn, so one giant cannot break a tower she
  pumps while the towers shoot it.
- `ARCHERS=1` with `ARCHER_RANGE=R`: while an enemy giant stands within R of an own tower (0: anywhere), an archer
  barracks on the free site nearest the midpoint of the queen and home, and archers before knights. Two archers cost
  100 gold, deal 10 a turn each to a giant (200 HP) and live 45 turns.

**Hypothesis and criteria (stated before the sweep).** An answer to giants pays against the opponents that train
them. On the development seeds 1 + 397 k, k < 500, an arm is a candidate if all three hold:
- Δ against R0 ≥ +2 pp;
- S against v09 ≥ 0.50;
- Δ against `field` ≥ −2 pp.

`field?GIANTS=1` (the field-balance opponent with giants) is a second giant opponent, reported as a check of
generality.

The answers act only when enemy giants are out. v09 and `field` train none, so in the mirror and against `field` the
candidate should play v09's games exactly. By P2's letter (`docs/local-screen.md`) that makes it a **targeted fix**:
fewer than 10 % of the maps changed against the control's mirror. A targeted fix needs:
- G0;
- S ≥ 0.500 against the control;
- Δ against `boss` ≥ −1 pp;
- a regression test.

The screen on the fresh block `screen-pv3` k = 2 runs the full panel anyway, R0 included. Among the arms that meet the
criteria, it takes the one with the largest Δ against R0. The policy says nothing of G5 for a targeted fix, so the
screen reports it as the evidence of benefit.

**A tie-break, added during the sweep** (before the last arm, `ARCHERS=1,GIANT_GUARD=300`, finished):
`ARCHERS=1` and `ARCHERS=1,ARCHER_RANGE=1000` tie at +3.05 pp against R0. The rule above does not settle this. On a
tie, the screen takes the arm with the smaller loss against `field?GIANTS=1`, the second giant opponent. This rule
was set after the tie appeared.

## The sweep (`sweep.sh`, `sweep_table.py` → `sweep.txt`)

Raw reports: `replays/bench/dev-giants-2026-11-06/` in the main checkout. 500 maps per series, 8 threads, 50 ms.
v09 scores 0.7025 against R0, 0.9440 against `field` and 0.9495 against `field?GIANTS=1` on these maps. Every arm
scores exactly 0.5000 against v09 and plays v09's games against `field`: neither trains giants.

| Arm | Δ vs R0 (95 % CI) | Δ vs `field?GIANTS=1` (95 % CI) | maps changed vs R0 |
| --- | --- | --- | --- |
| `GIANT_GUARD=150` | −1.80 pp (−4.34..+0.74) | −0.40 pp | 429 |
| `GIANT_GUARD=300` | −3.30 pp (−6.00..−0.60) | −1.20 pp | 439 |
| `GIANT_GUARD=500` | −2.50 pp (−5.24..+0.24) | −4.80 pp (−6.56..−3.04) | 443 |
| `ARCHERS=1` | **+3.05 pp** (+0.40..+5.70) | −1.45 pp (−3.27..+0.37) | 438 |
| `ARCHERS=1,ARCHER_RANGE=600` | +1.75 pp (−0.75..+4.25) | −0.75 pp | 429 |
| `ARCHERS=1,ARCHER_RANGE=1000` | **+3.05 pp** (+0.45..+5.65) | −1.30 pp (−3.09..+0.49) | 436 |
| `ARCHERS=1,GIANT_GUARD=300` | +0.90 pp | −3.35 pp (−4.99..−1.71) | 444 |

- **The tower guard loses at every radius**, and it drags the archers down as well.
  - It saves some towers: with `GIANT_GUARD=300`, 2.7 lost to giants per game in a check on 100 maps, against v09's
    3.8 on 500.
  - It still loses games. Why was not traced.
- **Archers gain against R0** when they answer any giant or one within 1000 of our towers. Answering only giants
  within 600 gains less (+1.75 pp).
- **Against `field?GIANTS=1` the archers cost 1-1.5 pp**, inside their intervals. That opponent trains giants early
  and without a cap, and v09 already beats it 95 %.
- **The pick (the tie-break above):** `current?ARCHERS=1,ARCHER_RANGE=1000`. Seven arms were tried on these seeds.

**What the archers buy** (`profile-archers-rival.txt`, against R0, the development seeds):

| | v09 | `ARCHERS=1,ARCHER_RANGE=1000` |
| --- | --- | --- |
| wins | 69.9 % | 72.4 % |
| own towers lost to giants, to other causes (means, games with giants) | 3.8, 1.0 | 2.0, 1.2 |
| our queen's deaths, enemy queens killed (of 1000 games) | 135, 131 | 80, 97 |
| knight waves (median), archer pairs (mean) | 9, 0 | 8, 1.2 |
| games at the 250-turn limit | 74 % | 82 % |

The archers save half the towers the giants took and cut our queen's deaths by 40 %. The gold they cost is one knight
wave, so we kill R0's queen less often, and more games go to the limit.

## The P2 screen on PV3 screen block 2 (`screen.sh`; summaries `fix.json`, `g1.json`, `g2.json`, `g4.json`, `g5.json`, `fieldg.json`)

Candidate `current?ARCHERS=1,ARCHER_RANGE=1000` against the control v09. Seeds 1002000000 + 397 i, i < 2000; `boss`
plays the first 500 maps (3 threads, 200 ms). Raw reports: `replays/bench/exp-2026-11-06-giants/` in the main
checkout. No series had a slow turn, a timeout or an error of either side.

| Series | Result | |
| --- | --- | --- |
| direct against v09, with the mirror | S **0.5000**, 0 of 2,000 maps changed, G0 clean | the targeted fix's conditions on the direct match hold; G1 cannot pass (0.500 is no gain) |
| against `field` (G4) | Δ 0.00 pp, the same games as v09's | passes |
| against R0 (G5) | Δ **+0.36 pp** (−0.88..+1.60): 0.7140 against v09's 0.7104, 1,740 of 2,000 maps changed | passes the gate, but shows no gain |
| against `field?GIANTS=1` | Δ **−0.99 pp** (−1.79..−0.19): 0.9395 against 0.9494 | a small, significant loss |
| against `boss` (G2, the targeted fix's −1 pp) | boss delta 0.0000 with v09's games (passes the targeted fix's -1 pp) | |

## Verdict

- **The development gain did not replicate.** Against R0 the archers are +0.36 pp on the fresh block (CI −0.88..+1.60),
  against +3.05 pp on the development seeds. That is the pick's luck among seven arms, two of them tied. Against
  `field?GIANTS=1` they lose 0.99 pp, a small but significant loss.
- **By P2's letter the candidate is a targeted fix.** It changes no map against v09's mirror, scores 0.500 and has
  a regression test, and G2 passes too (v09's games against `boss`). So by the letter it "joins the local champion".
- **In substance it is not targeted.** It changes 1,740 of 2,000 maps against R0, and it shows no gain where it
  acts. **Not adopted: the user's decision of 2026-11-06 on this recommendation, against P2's letter.** The flags stay off with their tests: `ARCHERS`, `ARCHER_RANGE`,
  `GIANT_GUARD`.
- **A gap in the policy:** the targeted fix counts changed maps against the control's mirror only. A rule that
  acts only on an opponent's units passes as targeted however much it changes against them. Counting the
  changed maps against every panel opponent would close this gap. That is the user's decision.
- **What the profile showed still stands** on the development seeds: the archers halve the towers lost to
  giants and cut our queen's deaths by 40 %. They pay for it with a knight wave and with fewer kills of the
  enemy queen, and the score does not move.
