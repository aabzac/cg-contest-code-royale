# Status

- Date: 2026-10-28. Phase 0 (orientation) done; phase 1 (infrastructure) not started.
- Game: Code Royale, permanent multiplayer arena. No live contest deadline.
- Arena entry / league / boss: unknown (to be provided by the user).
- Selected version: none. Uploaded agent: none. Pending candidate: none.
- Rules: `docs/rules/` (Wood 3 live + all 4 referee statement levels). Referee
  `csj/code-royale@349924a` in `refs/referee-csj/` (gitignored). Live max turns = 250 (repo: 200).
- Leaderboard 2026-10-28: 6302 players; Legend 108, Gold 343, Silver 194, Bronze 2295,
  Wood 1/2/3 1370/1229/769. Top 50: C++ 21, Java 19, Rust 4. Snapshot `docs/data/leaderboard-2026-10-28.json`.
- Corpus: 88 replays of top-10 Legend agents, `replays/cg/legend-top/`; manifest
  `docs/data/replays-legend-top-2026-10-28.json` (gameId, seed, sha256, slots, ranks, turns).
  Replay views use the SDK entity module (not a game-specific event format): state must be
  rebuilt by simulation from seed + both players' stdout.
