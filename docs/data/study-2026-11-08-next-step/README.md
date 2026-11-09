# Study 2026-11-08: v12 in Gold — the loss anatomy, and the next step

## Question

The user asked for an analysis and the single most promising next step ("Можешь сделать анализ и предложить следующий
наиболее перспективный шаг по улучшению бота?"). v12 is the selected version in Gold (placements 19.64 and 21.07, mean
20.36; v11's baseline 17.08). The analysis rests on v12's own 180 Gold games, the Legend corpus, the code and the
experiment history.

## Data

- **Games:** v12's two Gold placements (agents 6860036 and 6860166, artifact `3208b011`, cgAttribute SAME 180/180).
  Replays in the main checkout's `replays/arena/v12/gold/upload-0{1,2}/games`; manifests
  `docs/data/arena-6860036-games-2026-11-07.json`, `docs/data/arena-6860166-games-2026-11-07.json`.
- **Legend corpus:** 88 games of the top-10 Legend agents (2026-10-28), `replays/cg/legend-top`, manifest
  `docs/data/replays-legend-top-2026-10-28.json`; 42 Gold games of other players, `replays/cg2/gold`.
- **Inputs** (gitignored, main checkout `replays/study/gold-v12-2026-11-08/`; run from the worktree root, `<R>` = the main
  checkout's `replays`, written relative as `../../../replays`):
  ```
  ./gradlew -q cgStudy -Pout=replays/study/gold-v12-2026-11-08/features-v12-gold.csv "-Preplays=<R>/arena/v12/gold/upload-01/games <R>/arena/v12/gold/upload-02/games"
  ./gradlew -q cgStudy -Pout=replays/study/gold-v12-2026-11-08/features-v11-gold.csv "-Preplays=<R>/arena/v11/gold/upload-03/games <R>/arena/v11/gold/upload-04/games"
  ./gradlew -q cgStudy -Pout=replays/study/gold-v12-2026-11-08/features-legend.csv -Preplays=<R>/cg/legend-top
  ./gradlew -q cgStudy -Pout=replays/study/gold-v12-2026-11-08/features-cg2-gold.csv -Preplays=<R>/cg2/gold
  ./gradlew -q deathTrace -Pagent=6860036 -Pall=1 -Preplays=<R>/arena/v12/gold/upload-01/games > replays/study/gold-v12-2026-11-08/ours-upload-01.txt
  ./gradlew -q deathTrace -Pagent=6860036 -Pall=1 -Pfoe=1 -Preplays=<R>/arena/v12/gold/upload-01/games > replays/study/gold-v12-2026-11-08/opp-upload-01.txt
  ```
  The same two traces for upload 2 (agent 6860166), and `deathTrace -Pagent=<id> -Pall=1 -Preplays=<R>/cg/legend-top`
  for the ten Legend agents with ten or more games (6719462, 6810526, 1886979, 6394986, 6788158, 6802987, 6802286,
  1886983, 6783909, 6812269) into `legend-<id>.txt`. The WaveTrace summaries of v12 (176 games) and v11 (171) are the
  ones of the meet-back experiment, `replays/study/gold-v12-2026-11-07/waves/` and `replays/study/gold-2026-11-07/waves/`.

  | file | sha256 |
  | --- | --- |
  | features-v12-gold.csv | `9abb612f639c1f3725d904fc84a433b7e614e52235151dde3812932537109bbc` |
  | features-v11-gold.csv | `f3335a689498e74fa4fd6387e854d9b68a600628b57104688c675671b9c9554c` |
  | features-legend.csv | `7570cd5abbaebd22f653bf93d4ba6398c18ea20c7483cc6f4f3822f539a4327c` |
  | features-cg2-gold.csv | `edb59d95a73232440750da6c18eb60c24cfba5e23c80ae18e3929be0633c9596` |
  | ours-upload-01.txt / 02 | `17fd6b11a737ff269e6176dd4d6d7e54a301a212f21511c80b8d9048c78e0a8b` / `0e867b90a9a27720acf2c27d330c5db2b932a023d63e45557490219958ab69b0` |
  | opp-upload-01.txt / 02 | `e2a1f8719e8c50911fb844b6ed12b404039c45d7afe33c8348ec578e1ce9aae9` / `5d3619c1e2ac68cd87774751c2290afdd07d8fe864827f2242990d5a4671ddab` |
- **The Gold ladder:** the public Gold leaderboard at 2026-11-08 18:49 (`docs/data/leaderboard-gold-2026-11-08.json`;
  raw response `replays/cg/leaderboard/gold-20261108-1849.json`, sha256 `6c3d5583…`), fetched with
  `python -I docs/data/inference-2026-10-28/tools/corpus/explore_lb.py <dir> gold`.
- **Script:** `python -B -I docs/data/study-2026-11-08-next-step/next_step.py [--data <main checkout>]` writes
  `next_step.txt`. Every number below that is not attributed to a reader comes from it.
- **Agents** (run `wf_7ae430be-6dc`, the session's standing ultracode setting): seven readers (losses, midgame, seat, first
  wave, opponents, code, history), then four designers with different lenses (targeted, largest gain, structural, the
  arena's view). Their structured results are in `readers/` and `designs/`; the whole run is `workflow-result.json`.
  The two refuters per proposal, the synthesizer and the critic did not run: the user's usage limit cut the run twice.
  In their place, section 8 of `next_step.py` recomputes the two main proposals' claims independently of the readers'
  scripts. The readers' own numbers are marked as theirs below and were not adversarially verified.

## Findings

### 1. Where v12 stands

- Gold, 347 active agents, none eligible for promotion: the top row is 26.54, so the Gold boss stands above it. aabzac
  is at local rank 34 with 21.07; the gap to the top of the league is 5.5 points.
- 180 games: won 109 (73 at the turn limit on HP, 34 by killing the enemy queen, 2 by the referee removing the
  opponent), lost 71 (42 HP races at the limit, 29 queen deaths). 115 of 180 games reach turn 250.
- By the opponent's rating at fetch: below 14: 86 % (7 games); 14-17: 77 % (30); 17-20: 68 % (85); 20-23: 39 % (57);
  the one game against a 25.2 player lost. **Against the 58 opponents rated 20 or more: 24 lost races, 12 deaths, 15 won
  races, 7 kills.** The rating is lost at the top of the field.
- Seat: 67 % on seat 0 (81 games), 56 % on seat 1 (99). The seat reader: the asymmetry is the rules engine's
  (collisions are resolved as player 0's creeps, queen 0, player 1's creeps, queen 1, so a pack of knights stays tight on
  queen 1 and hits her about 1.5× harder; the referee repository's later commit 918c375 fixes it, the live referee does
  not); every arena player shares it, the bot is point-symmetric, our pooled seat effect over 2,070 arena games is
  −0.35 logits (about −1 Gold point) on seat 1, and our queen dies twice as often there (20 of 99 against 9 of 81). Not a
  lever for the rating; a seat-1 defensive bias is a possible later arm (the reader: `DEFENCE_FWD=50` gained +3.8 pp on
  seat-1 development games over v12 and lost on seat 0).

### 2. The HP races are a steady bleed, not one bad phase

Our HP minus theirs in the 42 lost races (medians; means in brackets): t50 −3 (−4.6), t100 −8 (−8.5), t150 −8 (−8.8),
t200 −10 (−12.7), final −13 (−16.3); 10 of 42 lost within 5 HP (four of them by 1 HP, all on seat 1), 18 within 10. In
the 73 won races: t50 +6 (+10.7), t200 +11 (+14.7), final +14.

HP lost to knights per phase (means per game), ours / theirs:

| races | 0-50 | 50-100 | 100-150 | 150-200 | 200-250 |
| --- | --- | --- | --- | --- | --- |
| lost (42) | 10.6 / 6.0 | 4.8 / 1.1 | 1.9 / 1.5 | 4.2 / 1.0 | 3.4 / 0.6 |
| won (73) | 3.5 / 14.2 | 2.8 / 3.9 | 3.6 / 3.1 | 2.1 / 5.2 | 0.8 / 4.4 |

Contact turns (an enemy knight within 250 of the queen), ours / theirs: lost races 13.3/9.9, 13.3/4.7, 8.9/3.4, 9.7/3.1,
7.2/2.7 per phase (52 against 24 over the game); won races 45 against 38.

- **Won races are decided by our first wave:** their queen loses 14.2 HP to our knights in the first 50 turns.
- **Lost races are decided by their waves reaching our queen while ours do not reach theirs:** after turn 50 our knights
  take 1.1, 1.5, 1.0 and 0.6 HP a phase off their queen. The midgame reader: the opponents who win the races are the
  queen-safe ones, whose queen stays within 150 of a tower while our knights are on her (76 % of the episodes against
  55 %); their economy is not larger than the race-losing opponents'.
- **Where our midgame HP goes (section 6e):** contact episodes on turns 50-249 that begin while our queen walks to a site
  cost 2.04 HP each in lost races (200 of 360 episodes, 9.7 HP a game) against 0.97 for episodes met at an own tower
  (130 episodes, 3.0 HP a game); in won races 1.30 against 0.61. Before 197 of those 199 walking contacts a knight was
  already within 500 (median 2 turns of warning). The midgame reader: most of these walks rebuild mines that enemy waves
  tore down (3.7 mine losses per lost race, the rebuilt mine lives a median 18 turns), and in 92 % of the episodes the
  nearest own tower was reachable before the contact.
- **The first wave (the first-wave reader, 115 races):** the wave's size, the merges and the kiting are the same in lost
  and won races; the difference is turns with a knight adjacent to the queen (5.2 against 2.1), and those come from the
  second tower standing after the contact: towers at contact 1.43 against 1.75, the contact at turn 24.5 against 27.3
  while our second tower completes at turn 25 in both groups. The contact turn is the opponent's barracks timing
  (correlation 0.77), not our position any more (322 against 324 from the back edge). Section 8 recomputes the gradient:

  | second tower minus contact | races | lost | first-wave loss (mean) | against 20+ |
  | --- | --- | --- | --- | --- |
  | 3 or more turns before | 32 | 8 (25 %) | 1.4 HP | 4 of 10 lost |
  | 1-2 before | 17 | 3 (18 %) | 3.2 | 1 of 3 |
  | 0-2 after | 39 | 14 (36 %) | 6.9 | 9 of 13 |
  | 3 or more after | 27 | 17 (63 %) | 11.6 | 10 of 13 |

  The first tower gets 6 top-ups (median) before the queen leaves for the second site, 11 turns from the first tower to
  the second; a top-up cap of 400-500 HP while the wave is coming would put the second tower up 2-3 turns earlier.
- **Turns 200-250 when trailing are not a lever** (the midgame reader): in the 44 games trailing at turn 200 our queen
  lost 0.16 HP a game on walks; the rest went at her towers; 40 of the 44 stayed lost. A late hold for the trailing side
  is not worth building.

### 3. The deaths are economic collapses under a standing army

29 deaths at turns 62-246 (median 172). In the last 30 turns the queen loses 30.3 HP to knights and 0.3 to towers,
18.0 of it while kiting; the opponent's income at turn 100 / 150 is 5.5 / 8 against our 3 / 1.5 (medians), with 4
enemy knights alive at turns 100 and 150. The losses reader: she died within 130 of an own tower in 28 of 29; at death the
opponent's income is 2× ours in 23 of 29 and 3× in 20 of 29, our mines are down to one or none in 21 of 29; 9 sieges, 8
attrition deaths, 6 early deaths (5 of them on seat 1). Walking into a visible wave also opens the death sieges (the
midgame reader: 86 of the 95 death episodes began on a walk with the tower reachable).

### 4. The structural gap: the queen's time and the army

Medians, us / them, over the 180 games: income at turn 100 3 / 4-5, max income 5 / 7-8 (8.5 against the 20+ players),
mines built 4 / 6, upgrades 5 / 7-8, knight trainings 9 / 11 (12 against the 20+), tower top-ups 67-97 / 57-60, towers
at turn 200 5-6 / 5 with 453-483 HP each against 486-525.

The queens' turns by what they do (share of all turns):

| | top-up | idle pump | walk to build | kite | MOVE (no knights near) | contact turns | HP per contact turn |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v12 | 27 % | 9 % | 23 % | 19 % | 11 % | 22.5 % | 0.47 |
| opponents 20+ | 21 % | 1 % | 11 % | 9 % | 40 % | 13.7 % | 0.70 |
| opponents <17 | 17 % | 1 % | 15 % | 10 % | 35 % | 17.6 % | 1.01 |
| Legend (168 queen-games) | 16 % | 0 % | 16 % | 15 % | 30 % | 25.6 % | 1.06 |

Legend (88 games): income 6 at turn 100, max income 10, 6 mines, 8 upgrades, 29 top-ups a game, 5 towers at about 470-530
HP, 10 knight trainings in games of 153 turns (median); giants in 18 of 176 rows (two players' style), archers in none;
openings M M BK 83, M M M 33, M BK T 25. The opponents reader: mines and upgrades per 100 turns 4.2 (ours), 7.4 (Gold
20+), 9.9 (Legend; winners 11.9, losers 7.7); the strong mine far from the home corner on the richer central sites (29 %
of their mines within 500 of the corner against our 50 %) and never stop building (last new structure at turn 215
against our 168); tower count is not the gap (everyone holds 4 at turn 100 and 5 at 150-200), and our towers are the
thickest at turn 50-100 (643 HP each against 509-543).

- Our queen spends 36 % of her turns pumping towers (Legend 16 %, the 20+ players 22 %), and per contact turn she is
  the hardest queen to hurt (0.47 HP against 0.70-1.06); the problem is how often knights are on her, not how she steps.
  That also demotes a search or rollout replacement of the kiting step: kiting is not where the gap is.
- The code reader: with two barracks, TRAIN_SYNC holds 80-159 gold on 48 % of the turns (21 % of all turns), and the
  `idle = MIN_VALUE` guard keeps the second barracks idle while the first trains; the losses reader: 23 of the 42 lost
  races end with 80 or more gold unspent, and in 5 losses waves were held while the enemy queen stood at 20 HP or less.
  The designers' sized gain is 2-4 games of 180: a companion flag, not the step.
- The economy itself (income 3 against 5-8) stays the largest structural gap, but every ladder flag for it lost
  locally four times, and the midgame reader's mechanism says why: the walks to mines are where the waves catch her.
  Safety of the walks comes first; the economy is the step after.

### 5. What is small

Enemy tower damage: 114 HP of 4,152 over 180 games (2.7 %), 1.1 turns a game inside an enemy range, almost all while
standing at an own tower that lies inside an enemy tower's range (the known quirk; it decided one lost race); stalls 0;
target oscillation 4 events in 180 games. Giants: 35 of 180 opponents trained them; 4 of the 29 deaths. Archers: 3 games.

### 6. The designers and the checks (section 8 of `next_step.py`)

Four designers converged on two flag-level rules; a third proposal, a rollout controller for the queen with
`cr.core.Rules` (3-5 days), was ranked below them by the lenses that saw the kiting numbers above.

| proposal | mechanism | flips of the 42 lost races if it worked fully (section 8) |
| --- | --- | --- |
| **A. Second tower before the first wave, by a partial first pump** (`WAVE_TOWER_HP`): while fewer than 2 towers stand and the enemy's first wave is coming, top up the first tower only to 400-500 HP, so the queen leaves for the second (anchor) site 2-3 turns earlier | the touch rule's threshold (`MainBot.java` line 121), one conditional | first-wave loss down to the won races' mean: 8; to zero: 13. Of the 10 races lost within 5 HP, 5 carry a first-wave excess of 5 HP or more |
| **B. No ladder walk into a visible wave** (`LADDER_CLEAR`): after the first-wave window and once 2 towers stand, while an enemy knight is within about 500 and none within 250, the ladder's walk to a new site (mine, tower, barracks) is replaced by a walk to the nearest own tower not under an enemy tower, topped up on arrival, with a spell cap | a gate before the ladder branch (`MainBot.play`, rule 5), `clearOfKnights` and `firstWaveWindow` exist | all walking-caught HP on turns 50-249 saved: 9; half: 3. Of the 10 within 5 HP, 4 carry 5 HP or more of it |
| A and B together | | 19 of 42 |

- A is a third variant of a family rejected twice (`WAVE_TOWERS`, `TOPUP_FROM`: the first tower stayed at 200 HP,
  melted, and the queen died twice as often). The partial pump is the new mechanism; the sweep's tripwires are the deaths
  per 1,000 maps, the summed tower HP at turn 50 and the second tower's turn against the contact.
- B is the same family as the rejected early reactions (THREAT_RANGE 350/450, KITE_RANGE 500: "reacting earlier stops
  her mines and walks"). The differences: only the ladder's walk is gated, after the first wave and with two towers up;
  the response is useful work at a covered tower, not a flight; a spell cap bounds the delay.
- Both act on the class of change the arena rewarded beyond the panel (v08, v12: the queen under knights), and all three
  local opponents produce the situations (the first contact at turn 21-25 with one tower; waves across the ladder).

## Recommendation

**The next step: meet every wave at a standing tower — the first wave with two towers (A), the later ones without a walk
into it (B).** Build both as flags on v12, off by default, and run one development sweep on seeds 1 + 397 k (500 maps,
both seats) against the v12 mirror, `field` and `rival`: A alone (`WAVE_TOWER_HP` 400 / 500 / 600, trigger
`knightWaveComing`), B alone (`LADDER_CLEAR` 400 / 500 × spell cap 10 / 20 / none), and the best of each together. Beside
S, record the mechanism measures: the second tower's turn minus the contact, the first-wave loss, towers at contact,
the summed tower HP at turn 50, deaths per 1,000 maps (the tripwire of the rejected family), and the contact episodes
begun while walking with their HP (`LocalProfile` or the DeathTrace classes of `next_step.py`). The arm or bundle that
wins the mirror without losing `field` or `rival` more than 1 pp goes to the P2 screen on screen-pv3 block 6, then the
final evaluation on evaluation-pv3 block 2. If only one may be built first, build A: one conditional, the steeper
gradient, the close losses and the 20+ opponents (11 of 19 won with the second tower before the contact against 11 of
39 after).

**Companion flags for the same artifact, not the step:** the TRAIN_SYNC release at the end of the game and at a kill
shot (enemy queen at 20 HP or less with 80 gold banked); the enemy-range guard on the flight and the idle pump (a
targeted fix, 114 HP in 180 games). **Tooling:** per-seat scores in `bench_pair.py` and the bench summary, so that a
seat-1 gain or loss is visible (the seat shift is 0.2-0.5 logits, larger than most candidate effects).

**Not the next step:** economy flags on the ladder (four failures recorded; the walks' safety first), a late hold for the
trailing side (0.16 HP a game), the rollout controller (kiting is already the most damage-efficient; the gap is the
contacts, not the steps), the seat (the engine's), giants and archers (not the loss driver in Gold).

## What is left open

- The refuters, the synthesizer and the critic of run `wf_7ae430be-6dc` did not run (the usage limit); the checks in
  section 8 stand in for them for proposals A and B only. The readers' other numbers are theirs.
- The flip counts hold the rest of the game fixed and assume the full effect; the realistic gain is a fraction, and the
  first-wave timing is confounded with the opponent's strength (early-barracks opponents are stronger). The development
  sweep decides.
- The economy step after this one (mines far from the corner, no idle pumping after the ladder) is the largest gap and
  the next study, once the walks are safe.
