/** Read-only catalogue and metrics. CG ranks decide results, including equal-HP games. */
export const LEAGUES = ['wood3', 'wood2', 'wood1', 'bronze', 'silver', 'gold', 'legend'];
export const PAGE_SIZE = 30;
export const RESULT_LABELS = {win: 'Победа', loss: 'Поражение', draw: 'Ничья'};
export const VERSION_LABELS = {legacy: 'Старый бот'};
export const leagueName = value => ({wood3: 'Wood 3', wood2: 'Wood 2', wood1: 'Wood 1', bronze: 'Bronze', silver: 'Silver', gold: 'Gold', legend: 'Legend'})[value] || value;
export const versionName = value => VERSION_LABELS[value] || value;

function localPath(value, prefix) {
  if (typeof value !== 'string') throw new Error('Не указан путь к данным');
  const path = value.replaceAll('\\', '/').replace(/^\/+/, '');
  if (!path.startsWith(prefix) || path.split('/').some(p => p === '..' || p === '.') || /[?#\u0000]/.test(path))
    throw new Error('Некорректный локальный путь: ' + value);
  return '/' + path.replace(/\/+$/, '');
}

async function readJson(fetcher, path) {
  const response = await fetcher(path, {cache: 'no-store'});
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${path}`);
  return response.json();
}

export function gameRow(upload, manifest, game) {
  if (!Number.isSafeInteger(game.gameId) || game.gameId <= 0 || ![0, 1].includes(game.seat) ||
      !Array.isArray(game.ranks) || game.ranks.length !== 2 || !game.ranks.every(Number.isFinite) ||
      !Array.isArray(game.scores) || game.scores.length !== 2 || !game.scores.every(Number.isFinite))
    throw new Error('Некорректная запись игры ' + (game.gameId ?? 'без номера'));
  const seat = game.seat;
  const ourScore = game.scores[seat], opponentScore = game.scores[1 - seat];
  const opponent = {...game.opponent};
  opponent.pseudo = typeof opponent.pseudo === 'string' && opponent.pseudo ? opponent.pseudo : null;
  opponent.ratingAtFetch = Number.isFinite(opponent.ratingAtFetch) ? opponent.ratingAtFetch : null;
  opponent.key = opponent.pseudo || `id:${opponent.agentId ?? game.gameId}`;
  opponent.name = opponent.pseudo || `Агент ${opponent.agentId ?? 'неизвестен'}`;
  return {
    ...game, opponent, version: upload.label, label: upload.label, league: upload.league,
    upload: upload.upload, agent: upload.agent, created: upload.created ?? null,
    file: localPath(manifest.rawDirectory, 'replays/') + '/' + game.gameId + '.json',
    result: game.ranks[seat] < game.ranks[1 - seat] ? 'win' : game.ranks[seat] > game.ranks[1 - seat] ? 'loss' : 'draw',
    ourScore, opponentScore, ourRemoved: ourScore === -1, opponentRemoved: opponentScore === -1,
    scoreTie: ourScore === opponentScore,
    // Frames are not necessarily paired at a timeout; never call this an exact turn count.
    estimatedTurns: Number.isInteger(game.frames) && game.frames >= 1 ? Math.floor((game.frames - 1) / 2) : null,
  };
}

export async function loadCatalog(fetcher = globalThis.fetch) {
  const registry = await readJson(fetcher, '/docs/data/arena-uploads.json');
  if (!Array.isArray(registry.uploads) || (registry.legacyUploads && !Array.isArray(registry.legacyUploads)))
    throw new Error('Реестр загрузок имеет неизвестный формат');
  const uploads = [...registry.uploads, ...(registry.legacyUploads || [])];
  const agents = new Set(), destinations = new Set();
  for (const upload of uploads) {
    const destination = `${upload.label}/${upload.league}/${upload.upload}`;
    if (!Number.isSafeInteger(upload.agent) || !Number.isInteger(upload.upload) || upload.upload < 1 ||
        typeof upload.label !== 'string' || !LEAGUES.includes(upload.league) || agents.has(upload.agent) || destinations.has(destination))
      throw new Error('Повторная или некорректная запись в реестре загрузок');
    agents.add(upload.agent); destinations.add(destination);
  }
  const errors = [], warnings = [], games = [];
  const manifests = await Promise.all(uploads.map(async upload => {
    try {
      const manifest = await readJson(fetcher, localPath(upload.games, 'docs/data/'));
      if (manifest.agentId !== upload.agent || !Array.isArray(manifest.games))
        throw new Error('Манифест принадлежит другому агенту или не содержит списка игр');
      const rows = manifest.games.map(game => gameRow(upload, manifest, game));
      if (new Set(rows.map(row => row.gameId)).size !== rows.length) throw new Error('В манифесте повторяются игры');
      const failures = manifest.failures?.length || manifest.summary?.failures || 0;
      if (failures) warnings.push(`${versionName(upload.label)}, загрузка ${upload.upload}: ${failures} игр не удалось скачать; они не входят в показатели.`);
      return {upload, manifest, rows};
    } catch (error) {
      errors.push({agent: upload.agent, version: upload.label, league: upload.league, upload: upload.upload,
        message: `${versionName(upload.label)} · ${leagueName(upload.league)} · загрузка ${upload.upload}: ${error.message}`});
      return {upload, manifest: null, rows: []};
    }
  }));
  for (const entry of manifests) games.push(...entry.rows);
  return {uploads, games, errors, warnings, manifests};
}

export function defaultState(catalog) {
  const current = catalog.uploads.filter(u => u.label !== 'legacy').at(-1) || catalog.uploads.at(-1);
  return {tab: 'overview', league: current?.league || 'bronze', version: current?.label || 'all', upload: 'all',
    result: 'all', seat: 'all', opponent: '', opponentExact: '', gameId: '', minMeetings: 1,
    sort: 'newest', opponentSort: 'losses', page: 1};
}

export function normalizeState(catalog, input = {}) {
  const defaults = defaultState(catalog), s = {...defaults, ...input};
  const leagues = [...new Set(catalog.uploads.map(u => u.league))];
  if (!leagues.includes(s.league)) s.league = defaults.league;
  const versions = catalog.uploads.filter(u => u.league === s.league).map(u => u.label);
  if (s.version !== 'all' && !versions.includes(s.version)) s.version = 'all';
  s.upload = String(s.upload);
  if (s.upload !== 'all' && !catalog.uploads.some(u => String(u.agent) === s.upload && u.league === s.league &&
      (s.version === 'all' ? u.label !== 'legacy' : u.label === s.version))) s.upload = 'all';
  if (!['overview', 'opponents', 'games'].includes(s.tab)) s.tab = defaults.tab;
  if (!['all', 'win', 'loss', 'draw', 'removed'].includes(s.result)) s.result = 'all';
  s.seat = String(s.seat);
  if (!['all', '0', '1'].includes(s.seat)) s.seat = 'all';
  if (!['newest', 'oldest', 'opponent', 'rating-desc'].includes(s.sort)) s.sort = 'newest';
  if (!['losses', 'loss-rate', 'games'].includes(s.opponentSort)) s.opponentSort = 'losses';
  for (const field of ['opponent', 'opponentExact', 'gameId']) s[field] = typeof s[field] === 'string' ? s[field] : '';
  s.minMeetings = Math.max(1, Math.min(1000, Math.floor(Number(s.minMeetings) || 1)));
  s.page = Math.max(1, Math.floor(Number(s.page) || 1));
  return s;
}

export function selectGames(catalog, input) {
  const s = normalizeState(catalog, input), search = s.opponent.trim().toLocaleLowerCase();
  const games = catalog.games.filter(g => g.league === s.league &&
    (s.version === 'all' ? g.version !== 'legacy' : g.version === s.version) &&
    (s.upload === 'all' || String(g.agent) === s.upload) &&
    (s.result === 'all' || (s.result === 'removed' ? g.ourRemoved || g.opponentRemoved : g.result === s.result)) &&
    (s.seat === 'all' || String(g.seat) === s.seat) &&
    (!s.opponentExact || g.opponent.key === s.opponentExact) &&
    (!search || g.opponent.name.toLocaleLowerCase().includes(search)) &&
    (!s.gameId.trim() || String(g.gameId).includes(s.gameId.trim())));
  return games.sort((a, b) => {
    if (s.sort === 'oldest') return a.gameId - b.gameId || a.agent - b.agent;
    if (s.sort === 'opponent') return a.opponent.name.localeCompare(b.opponent.name) || b.gameId - a.gameId;
    if (s.sort === 'rating-desc') return (b.opponent.ratingAtFetch ?? -Infinity) - (a.opponent.ratingAtFetch ?? -Infinity) || b.gameId - a.gameId;
    return b.gameId - a.gameId || a.agent - b.agent;
  });
}

export function summarize(games) {
  const wins = games.filter(g => g.result === 'win').length, losses = games.filter(g => g.result === 'loss').length;
  return {games: games.length, wins, losses, draws: games.length - wins - losses,
    winRate: games.length ? wins / games.length : null,
    removed: games.filter(g => g.ourRemoved || g.opponentRemoved).length,
    scoreTies: games.filter(g => g.scoreTie).length};
}

export function opponents(games, minMeetings = 1, sort = 'losses') {
  const groups = new Map();
  for (const g of games) {
    if (!groups.has(g.opponent.key)) groups.set(g.opponent.key, {key: g.opponent.key, name: g.opponent.name, rows: [], ids: new Set()});
    const group = groups.get(g.opponent.key);
    group.rows.push(g); if (g.opponent.agentId != null) group.ids.add(g.opponent.agentId);
  }
  return [...groups.values()].map(g => ({...g, ...summarize(g.rows), ids: [...g.ids]})).filter(g => g.games >= minMeetings)
    .sort((a, b) => (sort === 'games' ? b.games - a.games : sort === 'loss-rate' ? b.losses / b.games - a.losses / a.games : b.losses - a.losses) ||
      b.games - a.games || a.name.localeCompare(b.name));
}
