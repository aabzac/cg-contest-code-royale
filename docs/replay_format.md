# CG replay format (Code Royale)

`gameResult/findByGameId [gameId, null]` → `frames[]`, `agents[]` (`index` = in-game slot),
`refereeInput` (`seed=N`), `ranks` (0 = winner; equal = draw), `scores` (queen HP, -1 = killed).
Decoder: `cr.tools.CgReplay`.

- `frames[0]` (agentId -1): view `" 0\n{"global":…,"frame":{…}}"`, init state.
- Then per turn: agent 0 frame (stdout, `keyframe=false`), agent 1 frame (stdout,
  `keyframe=true`, view `" N\n{…}"`). Both stdout blocks contain the two output lines.
- View `frame` modules (SDK, not game-specific):
  - `entitymodule`: list of `C <type>` (create) and `U <id> <time> <key> <value>…` updates.
    Keys used: `x`, `y` (int of location), `T` (text), `i` (image).
  - `tooltips`: `[registrations, texts]`. Registrations `{entityId: {type: Site, id}}` /
    `{type: Queen|KNIGHT|ARCHER|GIANT}` (on the unit's token group). Texts are per-frame diffs:
    sites `["Radius: r", "Remaining gold: g", "MINE (+k)" | "TOWER","Range: r","Health: h" |
    "BARRACKS (KNIGHT)"]`; units `["Health: h"]` keyed by the token circle = group id - 2.
  - `anims`: construction/destruction/death events.
- HUD texts (found by init position): gold p0 at (700,1040), p1 at (1020,1040); queen HP p0 at
  (553,1053), p1 at (1771,1053). Unit owner: token circle image `Unite_Base_Rouge` (p0) /
  `Unite_Base_Bleu` (p1).
