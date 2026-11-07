# Experiment 2026-11-07: a second tower before the first wave, retried over v11 (Gold)

## Hypothesis and what changed before the test

The Gold study (`docs/data/study-2026-11-07-gold/`) proposed this candidate:
- In the HP races v11 lost, the first wave met one own tower (median) and cost 12.5 HP.
- In those it won, it met two towers (median) and cost 5.9.

Before any code, the first wave was looked at again over the same 180 games (`first_wave.py` and `first_wave.txt` in the
study folder). The medians had misled:

| races | games | contact turn | own towers at contact (mean) | enemy knights within 500 | enemy knights alive at t25 | HP lost in the next 20 turns |
| --- | --- | --- | --- | --- | --- | --- |
| lost | 44 | 24 | 1.6 | 3.7 | 4.2 | 12.5 |
| won | 62 | 25 | 1.7 | 3.5 | 4.2 | 5.9 |

- **Lost and won races look alike before the first wave.** The two kinds meet it with the same towers, at the same turn
  and against the same wave.
- **The queen behaves the same during it.** Over the 20 turns from the contact she tops up a tower on about 10 turns and
  kites on about 9.5, about 120 from her nearest tower.
- **Little else differs.**
  - Our tower HP at turn 25: 849 against 890.
  - Our income and first barracks: the same.
  - The opponents' scores: 15.8 against 15.1.
  - The starting HP: 66 against 72.
- **What differs is the outcome.** The enemy knights stay within 250 of the queen on 13.0 of the 20 turns in lost races,
  against 10.7 in won ones.
- **At the same contact timing (turns 22-27), a second tower helps the first contact, but the race barely changes.**
  - The loss is 16.8 HP with one tower (53 games) and 10.0 with two (38).
  - The share of games won is 57 % against 61 %.

So the tower count does not separate the races, and the candidate's premise is weak. The flags of 2026-11-05 are in the
code (`WAVE_TOWERS`, `TOPUP_FROM`), so the local test costs nothing and closes the question on v11.

Success bar (as on 2026-11-05): on the development seeds, S against v11 above 0.50 and a delta against `field` and
`rival` of at least −2 pp. Only then a P2 screen.

## Development sweep (`sweep.sh`, `sweep_table.py`, `sweep.txt`)

Seeds 1 + 397 k, k < 500, both seats; raw reports in `replays/bench/exp-2026-11-07-first-wave-gold/dev/`.

| arm | S vs v11 (95 % CI) | delta vs rival | delta vs field |
| --- | --- | --- | --- |
| `WAVE_TOWERS=2` | 0.4515 (0.4185..0.4845) | −0.0590 (−0.0972..−0.0208) | −0.0295 (−0.0518..−0.0072) |
| `TOPUP_FROM=2` | 0.4470 (0.4142..0.4798) | −0.0725 (−0.1098..−0.0352) | −0.0250 (−0.0461..−0.0039) |

`WAVE_TOWERS=2` had 11 slow turns. They would fail G0, but the arm fails on its score anyway.

## Verdict

Both arms lose on v11 as they lost on v08: S about 0.45, and 2.5-7 pp against `field` and `rival`. The candidate is
rejected on the development seeds. No P2 block is used, and the flags stay in the code, off.

The Gold races are decided at the first wave by something the macro features do not show: the enemy knights stay on
the queen longer, with the same towers, wave and behaviour. The next step for that question is to look at the first
waves of lost races in the replays, not another tower rule.
