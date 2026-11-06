# Experiment 2026-11-06: the late income against the rival R0 (P2 candidate)

**Why.** The strongest Silver players out-produce v08: income 6 against 3, and 7 mines and 9 upgrades a game.
Their income holds at 6.5 at turn 150 (`docs/data/study-2026-11-05-silver/`, `docs/data/exp-2026-11-06-rival/`).
`INCOME_LATE`, the income target after the ladder (`docs/data/exp-2026-11-03-late-income/`), was neutral against v06
and lost 9-12 pp against `field`. The local opponents could not show what an economy buys. R0 (gate G5 since PV4)
mines like the strongest four. The user's choice of 2026-11-06, after PV4: the income first, the giants next.

**Starting point.**
- Revision: commit `7e5e346` (v09 frozen) plus the uncommitted rival and PV4 changes of the worktree
  `claude/bot-improvement-next-steps-4b8f4e`. Each bench report records the commit, the status and the sha256 of the
  changes.
- Frozen v09, the referee and the harness are unchanged.
- `cr.tools.LocalProfile` gains the turn-200 line and the economy over the game: own mines built, upgrades, mines torn
  down (gold left on the site) or depleted, the gold mined. Diagnostics only.

**v09 against R0** (`LocalProfile`, 500 maps on 1 + 397 k, both seats; medians over the games):

| | v09 | R0 |
| --- | --- | --- |
| income at turns 50 / 100 / 150 / 200 | 4 / 3 / 4 / 3 | 5 / 5 / 6 / 6 |
| mines built, upgrades over the game | 4, 5 | 8, 13 |
| own mines torn down, depleted | 1, 2 | 2, 4 |
| gold mined | 725 | 1,266 |
| knight / giant waves | 9 / 0 | 13 / 2 |

v09 mines about 57 % of R0's gold and still wins 70 % of the games.

**Hypothesis and metric (stated before the sweep).** Against R0, a higher income after the ladder pays. An arm of
`INCOME_LATE` (4, 5, 6, 8) or `UPGRADE=1` (own mines below their maximum as targets of the ladder's mine phase) is a
candidate if, on the development seeds 1 + 397 k, k < 500, all three hold:
- Δ against R0 ≥ +2 pp;
- S against v09 ≥ 0.50;
- Δ against `field` ≥ −2 pp.

The best such arm goes to the P2 screen (G0-G5) on the fresh block `screen-pv3` k = 1. With none, the flags stay off.

## Sweep 1: the late income (`sweep.sh`, `sweep_table.py` → `sweep.txt`, which holds all four sweeps)

Raw reports: `replays/bench/dev-income-2026-11-06/` in the main checkout. 500 maps per series, 8 threads, 50 ms.
v09 itself scores 0.7025 against R0 and 0.9440 against `field` on these maps.

| Arm | S vs v09 (95 % CI) | Δ vs R0 | Δ vs `field` | maps changed vs R0 |
| --- | --- | --- | --- | --- |
| `INCOME_LATE=4` | 0.4850 (0.4735-0.4965) | +1.25 pp (+0.12..+2.38) | −0.15 pp | 111 |
| `INCOME_LATE=5` | 0.4815 (0.4679-0.4951) | +1.65 pp (+0.23..+3.07) | −0.25 pp | 206 |
| `INCOME_LATE=6` | 0.4775 (0.4621-0.4929) | +1.80 pp (+0.16..+3.44) | −0.20 pp | 251 |
| `INCOME_LATE=8` | 0.4790 (0.4621-0.4959) | +1.85 pp (+0.18..+3.52) | −0.10 pp | 280 |
| `UPGRADE=1` | 0.4995 (0.4985-0.5005) | −0.30 pp | +0.10 pp | 19 |
| `UPGRADE=1,INCOME_LATE=6` | 0.4775 (0.4621-0.4929) | +1.70 pp | −0.10 pp | 256 |

- The late income gains against R0 and loses the mirror, and `field` does not notice it.
- Against v06's `field` it lost 9-12 pp on 2026-11-03; the kiting of v07-v08 and the late hold of v09 changed that.
- `UPGRADE=1` does almost nothing: the touch rule already upgrades a mine the queen has just built until it is full.

**What it buys** (`profile.sh` → `profile1.txt`, `INCOME_LATE=6`, 500 maps, both seats):

| | v09 against R0 | +`INCOME_LATE=6` against R0 | v09 against v09 | +`INCOME_LATE=6` against v09 |
| --- | --- | --- | --- | --- |
| gold mined (mean) | 723 | 761 | 784 | 845 |
| mines built, upgrades (means) | 4.6, 6.3 | 4.8, 6.8 | 4.7, 6.9 | 5.1, 7.8 |
| knight waves (median) | 9 | 10 | 10 | 11 |
| our queen's deaths, enemy queens killed (of 1000 games) | 135, 131 | 123, 152 | 47, 47 | 63, 56 |

The income rises by 5-8 %, about one wave a game. The late phase starts only when the ladder is complete
(2 mines, 2 barracks, 6 towers), and by then our half's sites are towers. The free gold sites left are far away,
and the trips there cost our queen: in the mirror 16 more deaths per 1000 games.

## Sweep 2: safety conditions on the late trips

New parameters, off by default (`Params`, `MainBot.lateSite`, `MainBot.clearOfKnights`):
- `LATE_COVER=1`: only sites an own tower covers;
- `LATE_HALF=1`: only sites nearer home than the enemy's corner;
- `LATE_CLEAR=R`: no walk while an enemy knight is within R of the queen.

| Arm (with `INCOME_LATE=6`) | S vs v09 (95 % CI) | Δ vs R0 (95 % CI) | Δ vs `field` |
| --- | --- | --- | --- |
| `LATE_COVER=1` | 0.4825 (0.4680-0.4970) | +1.85 pp (+0.25..+3.45) | +0.00 pp |
| `LATE_HALF=1` | 0.4910 (0.4773-0.5047) | +1.25 pp (−0.29..+2.79) | +0.15 pp |
| `LATE_CLEAR=600` | 0.4905 (0.4761-0.5049) | +2.05 pp (+0.41..+3.69) | −0.30 pp |
| `LATE_CLEAR=1000` | 0.4980 (0.4844-0.5116) | +1.95 pp (+0.35..+3.55) | −0.30 pp |
| `LATE_COVER=1,LATE_CLEAR=600` | 0.4970 (0.4836-0.5104) | +2.05 pp (+0.45..+3.65) | −0.15 pp |

Waiting for knights to clear brings the mirror back to even and keeps the gain against R0. No arm reaches S ≥ 0.50
against v09, so none meets the criteria stated above. P2's G1 needs more than even: the lower bound above 0.50.

## Sweep 3: sites for mines, and giants to spend the gold on

Two ideas from sweeps 1-2:
- The late phase finds no free gold sites because the ladder has made our half's sites towers. A lower tower cap
  leaves sites for mines, and a higher income target builds them before the towers.
- Extra gold buys knights that the mirror's six towers grind down, while R0's two giants cost v09 6 pp. So new
  parameters `GIANTS`, `GIANT_TURN` and `GIANTS_MAX`, off by default, as R0 has them.

`GIANTS` in `MainBot`:
- from `GIANT_TURN` on, while the enemy holds a tower and giants are left, the queen builds a giant barracks before
  the ladder's next site;
- the giants train first, and the knights wait while the barracks saves for 140 gold.

| Arm | S vs v09 (95 % CI) | Δ vs R0 (95 % CI) | Δ vs `field` (95 % CI) | maps changed vs R0 |
| --- | --- | --- | --- | --- |
| `MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=1000` | **0.5090** (0.4857-0.5323) | **+3.35 pp** (+0.80..+5.90) | −0.20 pp (−1.16..+0.76) | 447 |
| `MAX_TOWERS=4,INCOME_LATE=6,LATE_CLEAR=1000` | 0.4665 (0.4392-0.4938) | +2.65 pp (−0.34..+5.64) | −0.05 pp | 477 |
| `INCOME_TARGET=4,INCOME_LATE=6,LATE_CLEAR=1000` | 0.4810 (0.4593-0.5027) | −0.10 pp | −0.50 pp | 341 |
| `INCOME_TARGET=5,INCOME_LATE=6,LATE_CLEAR=1000` | 0.4020 (0.3737-0.4303) | −3.90 pp | −2.30 pp | 430 |
| `GIANTS=1,GIANT_TURN=80,GIANTS_MAX=2` | 0.3790 (0.3518-0.4062) | −21.55 pp | −9.50 pp | 481 |
| the same with `INCOME_LATE=6,LATE_CLEAR=1000` | 0.3950 (0.3665-0.4235) | −20.55 pp | −9.40 pp | 481 |

- **Five towers with the safe late income** meet all three criteria stated above: S 0.509 against v09, +3.35 pp
  against R0, −0.20 pp against `field`. The criteria were stated for sweep 1's arms. This arm is the 12th tried on
  these seeds, so its numbers carry the selection; the fresh screen block decides.
- Four towers lose the mirror, and mines before the towers lose everywhere, more so with a higher target.
- **Our own giants lose heavily**: 21 pp against R0 and 10 pp against `field`. They cost 280 gold of a budget of about
  750, and the knights wait while the barracks saves. The flags stay off. Giants that pay need a different economy,
  not a flag on top of ours.

**The pick for the screen (stated before sweep 4):** sweep 4 tries the neighbourhood of five towers, including five
towers without the late income. Among the arms of sweeps 1-4 that meet the three criteria, the screen takes the one
with the highest S against v09, because G1 is the binding gate.

## Sweep 4: the neighbourhood of five towers

| Arm | S vs v09 (95 % CI) | Δ vs R0 (95 % CI) | Δ vs `field` |
| --- | --- | --- | --- |
| `MAX_TOWERS=5` | 0.4710 (0.4472-0.4948) | −4.00 pp (−6.87..−1.13) | −0.20 pp |
| `MAX_TOWERS=5,INCOME_LATE=6` | 0.4615 (0.4383-0.4847) | +3.45 pp (+0.85..+6.05) | −0.05 pp |
| `MAX_TOWERS=5,INCOME_LATE=4,LATE_CLEAR=1000` | 0.4630 (0.4390-0.4870) | +0.40 pp | −0.10 pp |
| `MAX_TOWERS=5,INCOME_LATE=8,LATE_CLEAR=1000` | 0.4950 (0.4713-0.5187) | +2.95 pp (+0.40..+5.50) | −0.20 pp |
| `MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=600` | 0.5035 (0.4801-0.5269) | +3.65 pp (+1.02..+6.28) | +0.05 pp |

- Five towers alone lose everywhere: the gain needs the freed site to become a mine.
- The income without waiting for the knights to clear loses the mirror, as with six towers.
- Against R0 the family gains +3..+3.7 pp with income targets 6-8; the mirror stays near even, 0.463-0.509 across
  neighbouring arms, so the best arm's 0.509 is in part the luck of the pick.

**The pick:** two arms meet the criteria, `LATE_CLEAR=1000` (S 0.5090) and `LATE_CLEAR=600` (0.5035). By the rule
stated before sweep 4, the screen takes `current?MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=1000`. Twenty-two arms were
tried on these seeds.

## The P2 screen on PV3 screen block 1 (`screen.sh`; summaries `g1.json` … `g5.json`)

Candidate `current?MAX_TOWERS=5,INCOME_LATE=6,LATE_CLEAR=1000` against the control v09. Seeds 1001000000 + 397 i,
i < 2000; `boss` plays the first 500 maps (3 threads, 200 ms). Raw reports: `replays/bench/exp-2026-11-06-income/`
in the main checkout.

| Gate | Result | |
| --- | --- | --- |
| G1, direct against v09 | S **0.5016** (CI 0.4898-0.5135), 1,769 of 2,000 maps changed against the mirror | **fails**: the lower bound is below 0.50 |
| G4, against `field` | Δ +0.31 pp (−0.22..+0.85): 0.9301 against v09's 0.9270 | passes |
| G5, against R0 | Δ **+1.79 pp** (+0.48..+3.10): 0.7251 against v09's 0.7073 | passes |
| G2, against `boss` | the candidate scored 999 of 1,000 games, but `boss` failed (timeouts) on 48 of the 500 maps, a run of consecutive seeds | **not established**: more than 1 % of the maps dropped makes the series invalid |
| G0 | 5 slow candidate turns at 8 threads in the `field` and R0 series, on neighbouring maps and on both sides; v09's mirror and R0 control had them too | **not established**; in-process slow turns do not change the games |

G2 and G0 are not repeated: G1 decides the screen whatever they show.

**What the candidate buys** (`profile2.txt`, the development seeds; v09's numbers from `profile1.txt`):

| | against R0: v09 / candidate | against v09: v09 / candidate |
| --- | --- | --- |
| gold mined (mean) | 723 / 790 (+9 %) | 784 / 914 (+17 %) |
| knight waves (median) | 9 / 10 | 10 / 12 |
| our queen's deaths, enemy queens killed (of 1000 games) | 135, 131 / 116, 171 | 47, 47 / 53, 61 |

## Verdict

- **G1 fails on the fresh block.** The candidate plays v09 even: 0.5016, CI 0.4898-0.5135. P2 needs the lower bound
  above 0.50, so the candidate is not the local champion, and v09 stays.
- **The gain against R0 replicates at about half its development size:** +1.79 pp (CI +0.48..+3.10) against +3.35 pp
  on the development seeds, the selection of the pick.
  - With Silver's logistic scale 3.16 that is about 0.3 rating points against R0-like opponents.
  - That is far inside the arena's noise (M 2.46).
- **All new flags stay off.** The code keeps them with their tests: `LATE_COVER`, `LATE_HALF`, `LATE_CLEAR`, `GIANTS`,
  `GIANT_TURN`, `GIANTS_MAX`.
- **What the economy can do with this ladder.**
  - The greedy ladder spends our half's sites on towers. The late income adds 5-8 % of gold.
  - Five towers free a site, but lose without the late income. Mines before the towers lose.
  - Our own giants cost the knights the gold they need.
  - A real economy needs a different balance of sites, not a flag on the ladder (as concluded on 2026-11-03).

**Next:** an answer to the enemy's giants, measured against R0, whose two giants cost v09 6 pp
(`docs/data/exp-2026-11-06-rival/`). Two options: defend the tower a giant attacks (a top-up adds 100 HP and a giant
busts 80 a turn), or archers (10 damage a turn to a giant).
