# Statements: provenance

| File | Source | League |
| --- | --- | --- |
| `wood3.live.html` | CG `Puzzle/findProgressByPrettyId ["code-royale", null]`, fetched 2026-10-28 (anonymous view) | Wood 3 (live) |
| `bronze.live.html` | CG `TestSession/startTestSession` `currentQuestion.question.statement`, user aabzac (Bronze) in the built-in browser, 2026-10-28; 18,969 bytes, sha256 `c3479a3d36f12dc458d05df53315c027df7285e008ea43cfc9e6bf55e088bcf4` | Bronze (live; per the statement this is the last rule change, so Silver/Gold/Legend should share it) |
| `wood3.referee-csj.html` | `csj/code-royale@349924a` `config/level1/statement_en.html` | Wood 3 |
| `wood2.referee-csj.html` | same, `level2` | Wood 2 |
| `wood1.referee-csj.html` | same, `level3` | Wood 1 |
| `bronze-plus.referee-csj.html` | same, `level4` | Bronze, Silver, Gold, Legend |

Files are verbatim copies. The repository statements are the 2018-2019 contest texts; they are
not guaranteed to match the live site. Re-capture the live statement of every league reached
(logged in) and diff it against the `referee-csj` copy.

Known live differences:

- Live Bronze vs `bronze-plus.referee-csj.html` (text diff, line wrapping ignored): knight HP
  **25** (repo 30); **250** turns in the victory and draw rules (repo 200); an added "Referee"
  link. Everything else (tower damage wording, turn sequence, collisions, weights, touching < 5,
  concurrent builds, site gold, tower HP) is identical. The statement is not exact about the
  code: it says site radius 60..110 (generator uses 60..90, confirmed by 98 replays) and towers
  deal damage by distance from the firing-range edge, while replays show the distance is taken
  from the site centre. The live code also removes dead creeps only at the end of the turn,
  resolves collisions differently from "at the end of every turn" and caps TRAIN at 30 creeps;
  the statement mentions none of this (see `inferred.md`, validated on 236 replays).

- Wood 3 live text says the game ends after **250** turns; repo statement and `Referee.kt`
  say 200. Replays of Legend games (2026-10-28) confirm 250 turns.
- Live Wood 3 text links the referee as `github.com/csj/team-2` (redirects to `csj/code-royale`).
