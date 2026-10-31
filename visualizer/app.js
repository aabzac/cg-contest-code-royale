import {loadCatalog, defaultState, normalizeState, selectGames, renderDashboard} from './dashboard.js';
import {mountPlayer} from './player.js';
import {localState, localRunRequest, localSelectionUrl, selectLocalGames, loadLocalData, localApi, renderLocal} from './local.js';

const content = document.querySelector('#content');
const refresh = document.querySelector('#refresh');
const navLinks = [...document.querySelectorAll('[data-view]')];
let catalog = null, catalogLoad = null, localData = null, localLoad = null;
let disposePlayer = null, renderId = 0, navigationId = 0;
const cache = new Map();
const localRun = {pending: false, error: '', message: ''};
const filterKeys = ['tab','league','version','upload','result','seat','opponent','opponentExact','gameId','minMeetings','sort','opponentSort','page'];
const localStorageKey = 'code-royale.local-form';
let savedLocal = readStored(localStorageKey, {});
let lastArenaUrl = '/visualizer/';
history.scrollRestoration = 'manual';

function readStored(key, fallback) {
  try { return JSON.parse(sessionStorage.getItem(key)) || fallback; } catch { return fallback; }
}

function rememberLocal(state) {
  savedLocal = state;
  try { sessionStorage.setItem(localStorageKey, JSON.stringify(state)); } catch { /* URL still preserves the form. */ }
}

function isLocal() { return new URLSearchParams(location.search).get('view') === 'local'; }

function currentLocalState() {
  return localState(localData || {bots: [], games: []}, savedLocal, new URLSearchParams(location.search));
}

function localUrl(state = currentLocalState()) { return localSelectionUrl(location.href, state); }

function stateFromUrl() {
  const params = new URLSearchParams(location.search), state = defaultState(catalog);
  for (const key of filterKeys) if (params.has(key)) state[key] = params.get(key);
  return normalizeState(catalog, state);
}

function selectionUrl(state) {
  const url = new URL(location.href);
  url.search = '';
  const defaults = defaultState(catalog);
  for (const key of filterKeys) {
    if (state[key] !== undefined && state[key] !== null && state[key] !== '' && String(state[key]) !== String(defaults[key])) url.searchParams.set(key, state[key]);
  }
  url.searchParams.set('version', state.version);
  url.searchParams.set('league', state.league);
  return url;
}

function rememberScroll() {
  const regions = Object.fromEntries([...content.querySelectorAll('[data-scroll-key]')]
    .map(node => [node.dataset.scrollKey, {top: node.scrollTop, left: node.scrollLeft}]));
  history.replaceState({...history.state, scrollY: window.scrollY, regions}, '', location.href);
}

function restoreScroll(token) {
  requestAnimationFrame(() => {
    if (token !== renderId) return;
    window.scrollTo({top: history.state?.scrollY || 0});
    for (const node of content.querySelectorAll('[data-scroll-key]')) {
      const saved = history.state?.regions?.[node.dataset.scrollKey];
      if (saved) node.scrollTo({top: saved.top || 0, left: saved.left || 0});
    }
  });
}

function navigate(url, replace = false, data = {}) {
  if (!replace) rememberScroll();
  navigationId++;
  history[replace ? 'replaceState' : 'pushState'](data, '', url);
  render();
}

function changeSelection(patch) {
  const state = normalizeState(catalog, {...stateFromUrl(), ...patch});
  navigate(selectionUrl(state), false, {scrollY: window.scrollY});
}

function changeLocalForm(patch) {
  const state = {...currentLocalState(), ...patch};
  rememberLocal(state);
  history.replaceState(history.state, '', localUrl(state));
}

function openGame(row, adjacent = false) {
  const url = selectionUrl(stateFromUrl());
  url.searchParams.set('game', row.gameId);
  url.searchParams.set('agent', row.agent);
  const data = adjacent ? {...history.state} : {fromSelection: true, returnUrl: selectionUrl(stateFromUrl()).href, returnScroll: window.scrollY};
  navigate(url, adjacent, data);
  window.scrollTo({top: 0});
}

function openLocalGame(row, adjacent = false) {
  const url = localUrl();
  url.searchParams.set('file', row.file);
  const data = adjacent ? {...history.state} : {fromSelection: true, returnUrl: localUrl().href, returnScroll: window.scrollY};
  navigate(url, adjacent, data);
  window.scrollTo({top: 0});
}

function backToSelection() {
  if (history.state?.fromSelection) history.back();
  else if (isLocal()) navigate(localUrl(), true, {scrollY: history.state?.returnScroll || 0});
  else if (catalog) navigate(selectionUrl(stateFromUrl()), true, {scrollY: history.state?.returnScroll || 0});
  else navigate('/visualizer/', true);
}

function notice(title, message, retry) {
  content.replaceChildren();
  const section = document.createElement('section'); section.className = 'error-state';
  const heading = document.createElement('h1'); heading.textContent = title;
  const detail = document.createElement('p'); detail.textContent = message;
  section.append(heading, detail);
  if (retry) { const button = document.createElement('button'); button.textContent = 'Попробовать ещё раз'; button.onclick = retry; section.append(button); }
  const back = document.createElement('button'); back.textContent = isLocal() ? 'К локальным играм' : 'К коллекции игр'; back.onclick = backToSelection; section.append(back);
  content.append(section);
}

async function ensureCatalog() {
  if (catalog) return catalog;
  if (!catalogLoad) catalogLoad = loadCatalog().then(data => (catalog = data)).finally(() => { catalogLoad = null; });
  return catalogLoad;
}

async function ensureLocal() {
  if (localData) return localData;
  if (!localLoad) localLoad = loadLocalData().then(data => (localData = data)).finally(() => { localLoad = null; });
  return localLoad;
}

async function getReplay(file) {
  if (cache.has(file)) return cache.get(file);
  const response = await fetch('/api/replay?file=' + encodeURIComponent(file));
  let replay;
  try { replay = await response.json(); } catch { throw new Error('Сервер не вернул данные реплея. Запустите актуальную версию через gradlew serve.'); }
  if (!response.ok) throw new Error(response.status === 404 ? `Файл реплея отсутствует в локальной коллекции: ${file}.` : replay.error || `Не удалось открыть файл (${response.status}).`);
  if (!Array.isArray(replay.frames) || !replay.frames.length) throw new Error('В реплее нет кадров для просмотра.');
  cache.set(file, replay);
  if (cache.size > 4) cache.delete(cache.keys().next().value);
  return replay;
}

async function launchLocal(patch) {
  if (localRun.pending) return;
  changeLocalForm(patch);
  let request;
  try { request = localRunRequest(currentLocalState()); }
  catch (error) { localRun.error = error.message; render(); return; }
  const origin = navigationId;
  localRun.pending = true; localRun.error = ''; localRun.message = '';
  rememberScroll(); render();
  try {
    const row = await localApi('/api/local/run', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request)});
    if (!row.file) throw new Error('Игра завершена, но сервер не вернул путь реплея. Обновите историю.');
    // An in-flight history refresh may predate the new match. Merge after it finishes.
    if (!localData) await ensureLocal();
    localData.games = [row, ...localData.games.filter(game => game.file !== row.file)];
    localRun.message = 'Игра сохранена в истории.';
    localRun.pending = false;
    if (origin === navigationId && isLocal() && !new URLSearchParams(location.search).has('file')) {
      // A newly launched pair may not match the active history filter.
      if (!selectLocalGames(localData, currentLocalState()).some(game => game.file === row.file)) changeLocalForm({historyBot: 'all'});
      if (origin === navigationId && isLocal() && !new URLSearchParams(location.search).has('file')) openLocalGame(row);
    } else if (isLocal() && !new URLSearchParams(location.search).has('file')) { rememberScroll(); render(); }
  } catch (error) {
    localRun.pending = false; localRun.error = error.message;
    if (isLocal() && !new URLSearchParams(location.search).has('file')) { rememberScroll(); render(); }
  }
}

function updateNavigation(local) {
  for (const link of navLinks) {
    const active = (link.dataset.view === 'local') === local;
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
    link.href = link.dataset.view === 'local' ? localSelectionUrl(location.href, savedLocal).href : lastArenaUrl;
  }
  refresh.title = local ? 'Перечитать список ботов и историю локальных игр' : 'Перечитать сохранённые манифесты';
  document.querySelector('#footer-context').textContent = local ? 'Локальный симулятор · история запусков' : 'Результаты — по рангам CodinGame';
}

async function render() {
  const token = ++renderId;
  disposePlayer?.(); disposePlayer = null;
  const params = new URLSearchParams(location.search), local = isLocal();
  updateNavigation(local);
  refresh.disabled = true;
  content.innerHTML = '<div class="loading-state" role="status">Открываем коллекцию игр…</div>';
  try {
    if (local) await ensureLocal();
    else if (!params.has('file')) await ensureCatalog();
    if (token !== renderId) return;
    if (!local && catalog) lastArenaUrl = selectionUrl(stateFromUrl()).href;
    if (params.has('file') || params.has('game')) {
      let game = null, selected = [], index = -1;
      let file = params.get('file');
      if (local) {
        selected = selectLocalGames(localData, currentLocalState());
        index = selected.findIndex(row => row.file === file);
      } else if (!file) {
        selected = selectGames(catalog, stateFromUrl());
        game = catalog.games.find(row => String(row.gameId) === params.get('game') && (!params.has('agent') || String(row.agent) === params.get('agent')));
        if (!game) { notice('Игра не найдена', 'Такой игры нет в загруженных манифестах. Проверьте фильтры или обновите коллекцию.', reload); return; }
        file = game.file;
        index = selected.findIndex(row => row.gameId === game.gameId && row.agent === game.agent);
      }
      content.innerHTML = '<div class="loading-state" role="status">Загружаем записанную игру…</div>';
      try {
        const replay = await getReplay(file);
        if (token !== renderId) return;
        document.title = `Code Royale · ${game ? 'Игра ' + game.gameId : local ? 'Локальная игра' : 'Просмотр реплея'}`;
        disposePlayer = mountPlayer(content, replay, {
          game, index, total: selected.length,
          backLabel: local ? '← К локальным играм' : undefined,
          initialTurn: Math.max(0, Number(params.get('turn')) || 0),
          onBack: backToSelection,
          onPrevious: index > 0 ? () => local ? openLocalGame(selected[index - 1], true) : openGame(selected[index - 1], true) : null,
          onNext: index >= 0 && index + 1 < selected.length ? () => local ? openLocalGame(selected[index + 1], true) : openGame(selected[index + 1], true) : null,
          onTurn: turn => {
            const url = new URL(location.href);
            if (turn) url.searchParams.set('turn', turn); else url.searchParams.delete('turn');
            history.replaceState(history.state, '', url);
          }
        });
      } catch (error) {
        if (token !== renderId) return;
        notice('Не удалось открыть реплей', error.message + ' Список игр и выбранные фильтры сохранены.', () => render());
      }
    } else if (local) {
      document.title = 'Code Royale · Локальные игры';
      const state = currentLocalState(); rememberLocal(state);
      history.replaceState(history.state, '', localUrl(state));
      renderLocal(content, localData, state, localRun, {
        onForm: changeLocalForm, onRun: launchLocal, onOpen: openLocalGame, onRetry: reload,
        onFilter: historyBot => {
          const next = {...currentLocalState(), historyBot}; rememberLocal(next); navigate(localUrl(next));
        }
      });
      restoreScroll(token);
    } else {
      document.title = 'Code Royale · Арена';
      lastArenaUrl = selectionUrl(stateFromUrl()).href;
      updateNavigation(false);
      renderDashboard(content, catalog, stateFromUrl(), {onState: changeSelection, onOpen: row => openGame(row)});
      content.insertAdjacentHTML('afterbegin', '<div class="dashboard-head"><div><h1>Арена</h1><p>Результаты сохранённых игр и разбор реплеев.</p></div></div>');
      restoreScroll(token);
    }
  } catch (error) {
    if (token === renderId) notice('Коллекция недоступна', error.message, reload);
  } finally { if (token === renderId) refresh.disabled = false; }
}

function reload() {
  rememberScroll();
  cache.clear();
  if (isLocal()) localData = null; else catalog = null;
  render();
}

for (const link of navLinks) link.addEventListener('click', event => {
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
  event.preventDefault();
  navigate(link.dataset.view === 'local' ? localSelectionUrl(location.href, savedLocal) : lastArenaUrl);
});
refresh.addEventListener('click', reload);
window.addEventListener('popstate', () => { navigationId++; render(); });
window.addEventListener('pagehide', event => { if (!event.persisted) disposePlayer?.(); });
render();
