# Statements: provenance

| File | Source | League |
| --- | --- | --- |
| `wood3.live.html` | CG `Puzzle/findProgressByPrettyId ["code-royale", null]`, fetched 2026-10-28 (anonymous view) | Wood 3 (live) |
| `wood3.referee-csj.html` | `csj/code-royale@349924a` `config/level1/statement_en.html` | Wood 3 |
| `wood2.referee-csj.html` | same, `level2` | Wood 2 |
| `wood1.referee-csj.html` | same, `level3` | Wood 1 |
| `bronze-plus.referee-csj.html` | same, `level4` | Bronze, Silver, Gold, Legend |

Files are verbatim copies. The repository statements are the 2018-2019 contest texts; they are
not guaranteed to match the live site. Re-capture the live statement of every league reached
(logged in) and diff it against the `referee-csj` copy.

Known live differences:

- Wood 3 live text says the game ends after **250** turns; repo statement and `Referee.kt`
  say 200. Replays of Legend games (2026-10-28) confirm 250 turns.
- Live Wood 3 text links the referee as `github.com/csj/team-2` (redirects to `csj/code-royale`).
