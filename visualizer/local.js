/** Local match setup and history. The server owns the list of runnable bots. */
import {recordedBotLabel} from './bot-label.js';

export const LOCAL_FIELDS = ['bot0', 'bot1', 'seed', 'historyBot', 'flags0', 'flags1'];

function flagObject(value) {
  if (typeof value === 'string') {
    try { value = JSON.parse(value); } catch { value = {}; }
  }
  return value && typeof value === 'object' && !Array.isArray(value)
    ? Object.fromEntries(Object.entries(value).filter(([, enabled]) => typeof enabled === 'boolean')) : {};
}

export function localFlags(data, value) {
  const saved = flagObject(value);
  const definitions = data.bots.find(bot => bot.id === 'current')?.flags || [];
  // A failed catalog request must not lose edits before a retry supplies the definitions.
  if (!definitions.length) return saved;
  return Object.fromEntries(definitions.map(flag => [flag.id,
    Object.hasOwn(saved, flag.id) ? saved[flag.id] : flag.default === true]));
}

export function localHistoryBots(data) {
  const bots = new Map(data.bots.map(bot => [bot.id, {...bot,
    label: bot.id === 'current' ? `${bot.label} · любые настройки` : bot.label}]));
  for (const game of data.games) (game.botIds || []).forEach((id, side) => {
    if (!bots.has(id)) bots.set(id, {id, label: recordedBotLabel(game, side, id)});
    const config = game.botConfigIds?.[side];
    if (config && !bots.has(config)) bots.set(config, {id: config, label: recordedBotLabel(game, side, config)});
  });
  return [...bots.values()];
}

export function localState(data, saved = {}, params = new URLSearchParams()) {
  const state = {...saved};
  for (const key of LOCAL_FIELDS) if (params.has(key)) state[key] = params.get(key);
  const ids = new Set(data.bots.map(bot => bot.id));
  for (const [key, fallback] of [['bot0', 'current'], ['bot1', 'v13']]) {
    const flagsKey = `flags${key.slice(-1)}`;
    if (!ids.size) state[key] ||= data.defaults?.[key] || fallback;
    else if (!ids.has(state[key])) state[key] = ids.has(data.defaults?.[key]) ? data.defaults[key]
      : ids.has(fallback) ? fallback : data.bots[0]?.id || '';
    state[flagsKey] = localFlags(data, state[flagsKey]);
  }
  state.seed = state.seed == null ? '' : String(state.seed);
  // Keep unavailable historical bot IDs selectable, too.
  const historicalIds = new Set(localHistoryBots(data).map(bot => bot.id));
  if (!ids.has(state.historyBot) && !historicalIds.has(state.historyBot)) state.historyBot = 'all';
  return Object.fromEntries(LOCAL_FIELDS.map(key => [key, state[key]]));
}

export function localRunRequest(state) {
  const seed = String(state.seed ?? '').trim();
  if (seed && (!/^\d+$/.test(seed) || !Number.isSafeInteger(Number(seed)) || Number(seed) > 199999))
    throw new Error('Seed должен быть целым числом от 0 до 199999. Пустое поле — случайная карта.');
  if (!state.bot0 || !state.bot1) throw new Error('Выберите ботов для обеих сторон.');
  const request = {bot0: state.bot0, bot1: state.bot1, seed: seed ? Number(seed) : null};
  for (const side of [0, 1]) {
    if (state[`bot${side}`] !== 'current') continue;
    const flags = state[`flags${side}`] ?? {};
    if (!flags || typeof flags !== 'object' || Array.isArray(flags)
      || Object.values(flags).some(enabled => typeof enabled !== 'boolean'))
      throw new Error('Флаги current должны быть включены или выключены.');
    request[`flags${side}`] = {...flags};
  }
  return request;
}

export function swapLocalSides(state) {
  return {...state, bot0: state.bot1, bot1: state.bot0,
    flags0: {...flagObject(state.flags1)}, flags1: {...flagObject(state.flags0)}};
}

export function localSelectionUrl(base, state) {
  const url = new URL(base);
  url.search = '';
  url.searchParams.set('view', 'local');
  for (const key of LOCAL_FIELDS) url.searchParams.set(key,
    key.startsWith('flags') ? JSON.stringify(flagObject(state[key])) : state[key] ?? '');
  return url;
}

export function selectLocalGames(data, state) {
  return data.games.filter(game => !state.historyBot || state.historyBot === 'all'
    || game.botIds?.includes(state.historyBot) || game.botConfigIds?.includes(state.historyBot))
    .sort((a, b) => (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0) || b.file.localeCompare(a.file));
}

export async function localApi(path, options) {
  const response = await fetch(path, options);
  let data;
  try { data = await response.json(); }
  catch { throw new Error('Сервер не вернул данные локальных игр. Запустите актуальную версию через gradlew serve.'); }
  if (!response.ok) throw new Error(data.error || `Запрос не выполнен (${response.status}).`);
  return data;
}

export async function loadLocalData() {
  const results = await Promise.allSettled([localApi('/api/local/bots'), localApi('/api/local/games')]);
  const data = {bots: [], defaults: {}, games: [], warnings: [], botsError: '', historyError: ''};
  const [bots, history] = results;
  if (bots.status === 'fulfilled' && Array.isArray(bots.value.bots)) {
    data.bots = bots.value.bots; data.defaults = bots.value.defaults || {};
  } else data.botsError = bots.status === 'rejected' ? bots.reason.message : 'Некорректный список доступных ботов.';
  if (history.status === 'fulfilled' && Array.isArray(history.value.games)) {
    data.games = history.value.games; data.warnings = history.value.warnings || [];
  } else data.historyError = history.status === 'rejected' ? history.reason.message : 'Некорректная история игр.';
  return data;
}

function escape(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
}

function options(bots, selected, all = false) {
  return (all ? '<option value="all">Все боты</option>' : '') + bots.map(bot =>
    `<option value="${escape(bot.id)}" ${bot.id === selected ? 'selected' : ''}>${escape(bot.label)}</option>`).join('');
}

function date(value) {
  const timestamp = new Date(value);
  return Number.isFinite(timestamp.valueOf()) ? timestamp.toLocaleString('ru-RU', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'}) : 'Дата неизвестна';
}

export function localResult(game) {
  return game.winner === -1 ? 'Ничья' : game.winner === 0 || game.winner === 1 ? `Победа стороны ${game.winner + 1}` : 'Результат неизвестен';
}

function gameErrors(game) {
  return (game.errors || []).filter(Boolean).map(error => typeof error === 'string' ? error : error.message || JSON.stringify(error)).join('; ');
}

/** Callbacks: onForm saves inputs without rerendering, onFilter navigates, onRun launches. */
export function renderLocal(container, data, state, run, {onForm, onFilter, onRun, onOpen, onRetry}) {
  const selected = selectLocalGames(data, state);
  const disabled = run.pending || !data.bots.length ? 'disabled' : '';
  const historicalBots = localHistoryBots(data);
  const definitions = data.bots.find(bot => bot.id === 'current')?.flags || [];
  const description = side => data.bots.find(bot => bot.id === state[`bot${side}`])?.description || '';
  const sideChoice = side => `<div class="local-bot-choice local-side-${side}">
    <label class="filter-field">Сторона ${side + 1}<select name="bot${side}" ${disabled}>${options(data.bots, state[`bot${side}`])}</select><span class="local-bot-description" data-description="${side}">${escape(description(side))}</span></label>
    <fieldset class="local-flags" data-flags="${side}" ${state[`bot${side}`] !== 'current' || !definitions.length ? 'hidden disabled' : disabled}>
      <legend>Настройки current · сторона ${side + 1}</legend>
      ${definitions.map(flag => `<label class="local-flag" title="${escape(flag.description)}"><input type="checkbox" name="flag${side}_${escape(flag.id)}" data-flag="${escape(flag.id)}" ${state[`flags${side}`]?.[flag.id] ? 'checked' : ''}><span>${escape(flag.label)} <code>${escape(flag.id)}</code></span></label>`).join('')}
    </fieldset>
  </div>`;
  container.innerHTML = `<section class="local-page">
    <div class="dashboard-head"><div><h1>Локальные игры</h1><p>Выберите ботов и карту. Сыгранная игра сохранится в истории.</p></div></div>
    <form class="local-launch panel" aria-label="Запуск локальной игры">
      <div class="local-setup">
        ${sideChoice(0)}
        <button class="local-swap" type="button" ${disabled} title="Поменять ботов сторонами" aria-label="Поменять ботов сторонами">⇄</button>
        ${sideChoice(1)}
        <label class="filter-field local-seed">Seed карты<input name="seed" type="number" min="0" max="199999" step="1" placeholder="Случайный" value="${escape(state.seed)}" ${disabled}><span class="local-bot-description">0–199999 · пусто — случайный</span></label>
        <button class="primary local-run" type="submit" ${disabled}>${run.pending ? 'Игра идёт…' : 'Запустить игру'}</button>
      </div>
      <div class="local-run-status ${run.error ? 'is-error' : ''}" role="${run.error ? 'alert' : 'status'}" aria-live="polite">${escape(run.pending ? 'Боты играют. После сохранения откроется реплей. Можно перейти в другой раздел — игра останется в истории.' : run.error || run.message || 'Одна игра на локальном симуляторе. Результат не является оценкой силы ботов.')}</div>
      ${data.botsError ? `<div class="catalog-alert"><p>Запуск недоступен: ${escape(data.botsError)}</p><button type="button" data-retry>Повторить загрузку</button></div>` : ''}
    </form>
    <section class="panel local-history">
      <div class="panel-header"><div><h2>История игр <span class="muted">${data.historyError ? '—' : selected.length}</span></h2><p>Новые игры сверху · переход между реплеями следует этой выборке.</p></div>
        <label class="filter-field">Участник<select name="historyBot" aria-label="Фильтр истории по боту">${options(historicalBots, state.historyBot, true)}</select></label>
      </div>
      ${data.historyError ? `<div class="catalog-alert"><p>Не удалось загрузить историю: ${escape(data.historyError)}</p><button type="button" data-retry>Повторить загрузку</button></div>` : ''}
      ${data.warnings.length ? `<details class="catalog-alert local-history-warnings"><summary>Примечания к истории (${data.warnings.length})</summary><ul>${data.warnings.map(warning => `<li>${escape(warning)}</li>`).join('')}</ul></details>` : ''}
      ${selected.length ? `<div class="table-wrap" data-scroll-key="local-history" tabindex="0" aria-label="Сохранённые локальные игры"><table class="data-table"><thead><tr><th>Когда</th><th>Сторона 1</th><th>Сторона 2</th><th>Seed</th><th>Результат</th><th>HP</th><th>Ходы</th><th><span class="sr-only">Просмотр</span></th></tr></thead><tbody>${selected.map((game, index) => {
        const errors = gameErrors(game);
        const names = [0, 1].map(side => recordedBotLabel(game, side));
        const winnerClass = game.winner === -1 ? 'draw' : game.winner === 0 || game.winner === 1 ? `local-winner-${game.winner}` : 'unknown';
        return `<tr><td><time datetime="${escape(game.createdAt)}">${escape(date(game.createdAt))}</time></td><td class="local-side-0">${escape(names[0])}</td><td class="local-side-1">${escape(names[1])}</td><td class="mono">${escape(game.seed)}</td><td><span class="badge ${winnerClass}">${escape(localResult(game))}</span>${errors ? `<details class="local-game-errors"><summary>Ошибки игры</summary><p>${escape(errors)}</p></details>` : ''}</td><td>${(game.score || []).map(value => Number.isFinite(value) && value >= 0 ? value : '—').join(' : ') || '—'}</td><td>${escape(game.turns ?? '—')}</td><td><button class="small" type="button" data-local-game="${index}" aria-label="Смотреть игру ${escape(names[0])} против ${escape(names[1])}, seed ${escape(game.seed)}">Смотреть</button></td></tr>`;
      }).join('')}</tbody></table></div>` : !data.historyError ? `<div class="empty-state"><h2>${data.games.length ? 'В выборке нет игр' : 'Пока нет локальных игр'}</h2><p>${data.games.length ? 'Выберите другого участника или запустите новую игру.' : 'Выберите ботов выше и запустите первую игру.'}</p></div>` : ''}
    </section>
  </section>`;
  const form = container.querySelector('form');
  const readForm = () => ({bot0: form.elements.bot0.value, bot1: form.elements.bot1.value, seed: form.elements.seed.value,
    ...Object.fromEntries([0, 1].map(side => [`flags${side}`, definitions.length
      ? Object.fromEntries([...form.querySelector(`[data-flags="${side}"]`).querySelectorAll('input[data-flag]')]
        .map(input => [input.dataset.flag, input.checked])) : {...flagObject(state[`flags${side}`])}]))});
  const syncSides = values => {
    for (const side of [0, 1]) {
      form.querySelector(`[data-description="${side}"]`).textContent = data.bots.find(bot => bot.id === values[`bot${side}`])?.description || '';
      const flags = form.querySelector(`[data-flags="${side}"]`);
      flags.hidden = values[`bot${side}`] !== 'current' || !definitions.length;
      flags.disabled = flags.hidden || Boolean(disabled);
    }
  };
  const saveForm = () => {
    const values = readForm(); onForm(values);
    syncSides(values);
  };
  form.addEventListener('input', saveForm);
  form.addEventListener('change', saveForm);
  form.addEventListener('submit', event => {event.preventDefault(); if (form.reportValidity()) onRun(readForm());});
  form.querySelector('.local-swap').addEventListener('click', () => {
    const swapped = swapLocalSides(readForm());
    for (const side of [0, 1]) {
      form.elements[`bot${side}`].value = swapped[`bot${side}`];
      for (const input of form.querySelector(`[data-flags="${side}"]`).querySelectorAll('input[data-flag]'))
        input.checked = swapped[`flags${side}`][input.dataset.flag] === true;
    }
    saveForm();
  });
  container.querySelector('[name="historyBot"]').addEventListener('change', event => onFilter(event.target.value));
  for (const button of container.querySelectorAll('[data-local-game]')) button.addEventListener('click', () => onOpen(selected[Number(button.dataset.localGame)]));
  for (const button of container.querySelectorAll('[data-retry]')) button.addEventListener('click', onRetry);
  return selected;
}
