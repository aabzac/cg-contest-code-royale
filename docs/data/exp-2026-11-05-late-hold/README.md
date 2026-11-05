# Experiment 2026-11-05: holding an HP lead late (LATE_HOLD, P2)

**Hypothesis.** The second candidate of the Silver study (`docs/data/study-2026-11-05-silver/`, What
follows). 10 of the 34 races v08 lost at the limit it led after turn 200. In the last 50 turns of those ten the
queen lost 10-73 HP each, all but 12 of 292 to knights:
- kiting, 111 HP;
- topping up a tower under knights, 94;
- walking to a tower under knights, 73.

After turn 200 the ladder often reopens: mines run dry, and towers fall to giants. Then the queen walks out to
build: in seven of the ten games to a mine, with no knight near. If a queen ahead on HP stays home instead,
she should keep more of her lead.

**Success metric:** P2 on a fresh PV3 screen block: G0, G1 (the lower 95 % bound of S against v08 above
0.50), G2 (Δ against `boss` ≥ −2 pp), G4 (Δ against `field` ≥ −2 pp); G3 skipped (no `old`). PV3 warns that the
mirror over-weights close HP races, so the arena judges this candidate (P1).

## The flag (`current`, off by default; `current` still plays as v08)

`LATE_HOLD=T`: from turn T on, while our queen has more HP than the enemy's (the score at the limit):
- no walk to the ladder's next site: she builds her own tower nearest home instead (the idle rule);
- the defence under threat builds only on a site she touches.

Kiting, the flight and the touch rules stay. The bot reads the turn from `InputReader` and both queens' HP from
the input.

Tests: `StrategyParamsTest.lateHoldKeepsTheQueenHomeWhenAheadLate` checks four cases: ahead late, before T,
level on HP, and the defence under threat. The version identity tests pass (`LATE_HOLD=0` in every snapshot).

## Development sweep

Seeds 1 + 397 k, k < 500, all 16 starting HPs. `sweep.sh` (raw reports in
`replays/bench/dev-late-hold-2026-11-05/`, main checkout) and `sweep_table.py` → `sweep.txt`.

| Arm | S against v08 (95 % CI) | Δ against `field` (control 0.9315) | limit games against v08: won / lost |
| --- | --- | --- | --- |
| `LATE_HOLD=100` | 0.4830 (0.4603-0.5057) | +0.0215 | 396 / 413 |
| `LATE_HOLD=150` | 0.5310 (0.5127-0.5493) | +0.0225 | 440 / 373 |
| **`LATE_HOLD=200`** | 0.5310 (0.5190-0.5430) | +0.0125 | 435 / 375 |
| `LATE_HOLD=225` | 0.5110 (0.5044-0.5176) | +0.0040 | 415 / 394 |

From turn 100 the hold costs: a queen that stops rebuilding that early loses the mirror. 150 and 200 tie
against v08. 200 has the narrower interval, changes the least play and aims at the arena's evidence (the
leads lost after turn 200). It went to the screen as the only arm, so the screen needs no replication. 150's
larger gain against `field` stays a development result.

## P2 screen (PV3 screen block 0)

Seeds 1000000000 + 397 k, k < 2000 (process opponents the first 500); ledger entry `screen-pv3` k = 0.
- First runs: `screen.sh`.
- Quiet repeats: `rerun.sh`, plus two more field runs with `-Pthreads=4` and `-Pthreads=2`.
- Raw reports: `replays/bench/exp-2026-11-05-late-hold/` (main checkout, gitignored); sha256 in `docs/DEVLOG.md`.
- Summaries: `g1*.json`, `g2-rerun.json`, `g4*.json` beside this file.

| Gate | Series | Result |
| --- | --- | --- |
| G1 | direct against v08, 2000 maps | S **0.5269** (CI 0.5208-0.5329), pass; 665 of 2000 maps changed against v08's mirror, so a strategy candidate, not a targeted fix |
| G2 | against `boss`, 500 maps | Δ 0.0000 (both 1000 of 1000), pass, on the 2-thread repeat |
| G3 | `old` | skipped: no code |
| G4 | against `field`, 2000 maps | Δ **+0.0146** (CI +0.0105..+0.0187), pass: 0.9275 against v08's 0.9129 |
| G0 | every candidate series | pass on the quiet repeats (below) |

**G0 and the repeats.** The first runs had the candidate's slow turns:
- direct: 3 (max 66.7 ms);
- field: 1 (max 201 ms).

Both came on runs of neighbouring maps, and v08 had them too:
- 5 in the direct series;
- 3 and 4 in its own mirror (max 155 ms).

The first boss series lost 6 maps to boss timeouts, five of them consecutive. That is more than 1 %, so the
series was invalid.

The repeats play the same games: `same_games.py` finds 0 of 4000 differing, except the six boss timeout
games. Only the timing changed:
- the direct repeat at 8 threads was clean (max 49.3 ms);
- field had 2 slow turns at 8 threads and 18 at 4, but none at 2 (max 9.6 ms);
- the boss repeat at 2 threads had no boss timeout and no slow turn.

The host is a laptop CPU with two performance cores and ten efficiency cores (Intel Core Ultra 7 155U), with
an IDE and a browser open. With many bench threads, some land on efficiency cores and stall for tens of
milliseconds. Two threads stay fast.

## Verdict

LATE_HOLD=200 passes P2's screen (G0-G2 and G4; G3 skipped) and is the local champion by the policy.
- **Below the upload recommendation.** S 0.527 against v08 is far below 0.60. That is about +0.14 rating
  points at Bronze's logistic scale, +0.34 at Silver's 3.16, inside the arena's noise (M = 2.46), so two
  uploads could not see it.
- **The proxy overrates it.** PV3 records that the mirror over-weights close HP races, and the gain against
  `field` (+1.5 pp) is the more representative number.
- **The user decides:** making it the `current` default (so later candidates build on it, as v06 took in the
  army flags), the final evaluation, a freeze, and any upload.

**The user's decision (2026-11-05):** the default without an upload. Frozen as v09 (`cr.bot.v09.GreedyBot`,
`versions/v09.properties`); `current` plays as v09 (`MainBotTest.withDefaultsCurrentPlaysAsV09`). No final evaluation
block was used, and v08 stays the selected version.
