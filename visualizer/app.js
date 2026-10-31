import {loadCatalog, defaultState, normalizeState, selectGames, renderDashboard} from './dashboard.js';
import {mountPlayer} from './player.js';

const content = document.querySelector('#content');
const refresh = document.querySelector('#refresh');
let catalog = null, disposePlayer = null, renderId = 0;
const cache = new Map();
history.scrollRestoration = 'manual';
const filterKeys = ['tab','league','version','upload','result','seat','opponent','opponentExact','gameId','minMeetings','sort','opponentSort','page'];

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

function navigate(url, replace = false, data = {}) {
  if (!replace) rememberScroll();
  history[replace ? 'replaceState' : 'pushState'](data, '', url);
  render();
}

function changeSelection(patch) {
  const state = normalizeState(catalog, {...stateFromUrl(), ...patch});
  navigate(selectionUrl(state), false, {scrollY: window.scrollY});
}

function openGame(row, adjacent = false) {
  const url = selectionUrl(stateFromUrl());
  url.searchParams.set('game', row.gameId);
  url.searchParams.set('agent', row.agent);
  const data = adjacent ? {...history.state} : {fromSelection: true, returnUrl: selectionUrl(stateFromUrl()).href, returnScroll: window.scrollY};
  navigate(url, adjacent, data);
  window.scrollTo({top: 0});
}

function backToSelection() {
  if (history.state?.fromSelection) history.back();
  else if (catalog) navigate(selectionUrl(stateFromUrl()), true, {scrollY: history.state?.returnScroll || 0});
  else {
    history.replaceState({}, '', '/visualizer/');
    start();
  }
}

function notice(title, message, retry) {
  content.replaceChildren();
  const section = document.createElement('section'); section.className = 'error-state';
  const heading = document.createElement('h1'); heading.textContent = title;
  const detail = document.createElement('p'); detail.textContent = message;
  section.append(heading, detail);
  if (retry) { const b = document.createElement('button'); b.textContent = 'Попробовать ещё раз'; b.onclick = retry; section.append(b); }
  const back = document.createElement('button'); back.textContent = 'К коллекции игр'; back.onclick = backToSelection; section.append(back);
  content.append(section);
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

async function render() {
  const token = ++renderId;
  disposePlayer?.(); disposePlayer = null;
  const params = new URLSearchParams(location.search);
  if (params.has('file') || params.has('game')) {
    let game = null, selected = [], index = -1;
    let file = params.get('file');
    if (!file) {
      if (!catalog) return;
      selected = selectGames(catalog, stateFromUrl());
      game = catalog.games.find(row => String(row.gameId) === params.get('game') && (!params.has('agent') || String(row.agent) === params.get('agent')));
      if (!game) { notice('Игра не найдена', 'Такой игры нет в загруженных манифестах. Проверьте фильтры или обновите коллекцию.', start); return; }
      file = game.file;
      index = selected.findIndex(row => row.gameId === game.gameId && row.agent === game.agent);
    }
    content.innerHTML = '<div class="loading-state" role="status">Загружаем записанную игру…</div>';
    try {
      const replay = await getReplay(file);
      if (token !== renderId) return;
      document.title = `Code Royale · ${game ? 'Игра ' + game.gameId : 'Просмотр реплея'}`;
      disposePlayer = mountPlayer(content, replay, {
        game, index, total: selected.length,
        initialTurn: Math.max(0, Number(params.get('turn')) || 0),
        onBack: backToSelection,
        onPrevious: index > 0 ? () => openGame(selected[index - 1], true) : null,
        onNext: index >= 0 && index + 1 < selected.length ? () => openGame(selected[index + 1], true) : null,
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
  } else if (catalog) {
    document.title = 'Code Royale · Арена';
    renderDashboard(content, catalog, stateFromUrl(), {onState: changeSelection, onOpen: row => openGame(row)});
    content.insertAdjacentHTML('afterbegin', '<div class="dashboard-head"><div><h1>Арена</h1><p>Результаты сохранённых игр и разбор реплеев.</p></div></div>');
    requestAnimationFrame(() => {
      if (token !== renderId) return;
      window.scrollTo({top: history.state?.scrollY || 0});
      for (const node of content.querySelectorAll('[data-scroll-key]')) {
        const saved = history.state?.regions?.[node.dataset.scrollKey];
        if (saved) node.scrollTo({top: saved.top || 0, left: saved.left || 0});
      }
    });
  }
}

async function start() {
  refresh.disabled = true;
  const directFile = new URLSearchParams(location.search).has('file');
  if (directFile) render();
  try {
    catalog = await loadCatalog();
    cache.clear();
    if (!directFile) await render();
  } catch (error) {
    if (!directFile) notice('Коллекция недоступна', error.message, start);
  } finally { refresh.disabled = false; }
}

refresh.addEventListener('click', () => { cache.clear(); start(); });
window.addEventListener('popstate', () => render());
window.addEventListener('pagehide', event => { if (!event.persisted) disposePlayer?.(); });
start();
