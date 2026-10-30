import {LEAGUES, PAGE_SIZE, RESULT_LABELS, leagueName, versionName, loadCatalog, defaultState, normalizeState, selectGames, summarize, opponents} from './catalog.js';
export {loadCatalog, defaultState, normalizeState, selectGames};

const escape = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[ch]));
const number = value => value == null ? '—' : Number(value).toLocaleString('ru-RU', {maximumFractionDigits: 2});
const percent = value => value == null ? '—' : `${(value * 100).toLocaleString('ru-RU', {maximumFractionDigits: 1})} %`;
const option = (value, label, selected) => `<option value="${escape(value)}"${String(value) === String(selected) ? ' selected' : ''}>${escape(label)}</option>`;
const button = (label, action, cls = 'link-button') => `<button type="button" class="${cls}" data-action="${escape(action)}">${label}</button>`;
const score = value => value === -1 ? 'снятие' : number(value);
const badge = row => `<span class="badge ${row.result}">${RESULT_LABELS[row.result]}</span>`;

function filters(catalog, s) {
  const leagues = LEAGUES.filter(league => catalog.uploads.some(u => u.league === league));
  const versions = [...new Set(catalog.uploads.filter(u => u.league === s.league).map(u => u.label))];
  const uploads = catalog.uploads.filter(u => u.league === s.league && (s.version === 'all' ? u.label !== 'legacy' : u.label === s.version));
  return `<form class="filter-bar" id="catalog-filters" aria-label="Фильтры игр">
    <label class="filter-field"><span>Версия</span><select name="version">${option('all', 'Все версии проекта', s.version)}${versions.map(v => option(v, versionName(v), s.version)).join('')}</select></label>
    <label class="filter-field"><span>Лига</span><select name="league">${leagues.map(l => option(l, leagueName(l), s.league)).join('')}</select></label>
    <label class="filter-field"><span>Загрузка</span><select name="upload">${option('all', 'Все загрузки', s.upload)}${uploads.map(u => option(u.agent, `${versionName(u.label)} · № ${u.upload}`, s.upload)).join('')}</select></label>
    <label class="filter-field"><span>Результат</span><select name="result">${[['all', 'Все результаты'], ['win', 'Победы'], ['loss', 'Поражения'], ['draw', 'Ничьи'], ['removed', 'Со снятием игрока']].map(([v,l]) => option(v,l,s.result)).join('')}</select></label>
    <label class="filter-field"><span>Сторона</span><select name="seat">${[['all', 'Обе стороны'], ['0', 'Первая · P0'], ['1', 'Вторая · P1']].map(([v,l]) => option(v,l,s.seat)).join('')}</select></label>
    <label class="filter-field filter-search"><span>Соперник</span><input type="search" name="opponent" value="${escape(s.opponent)}" placeholder="Ник игрока" autocomplete="off"></label>
    <label class="filter-field filter-id"><span>Номер игры</span><input type="search" name="gameId" value="${escape(s.gameId)}" inputmode="numeric" placeholder="Например, 913548762"></label>
    <button class="button filter-apply" type="submit">Найти</button>
    <button class="button secondary filter-reset" type="button" data-action="reset">Сбросить</button>
  </form>`;
}

function reportErrors(catalog) {
  if (!catalog.errors.length && !catalog.warnings.length) return '';
  return `<aside class="catalog-alert" role="alert"><strong>${catalog.errors.length ? 'Каталог загружен не полностью' : 'Есть незагруженные игры'}</strong>
    <p>Показатели рассчитаны только по доступным играм.</p><ul>${[...catalog.errors.map(e => e.message), ...catalog.warnings].map(message => `<li>${escape(message)}</li>`).join('')}</ul></aside>`;
}

function metric(title, value, caption, action, variant = '') {
  return `<button type="button" class="metric ${variant}" data-action="${action}"><span class="metric-label">${title}</span><strong>${value}</strong><span class="metric-caption">${caption}</span></button>`;
}

function summaryRow(name, rows, action) {
  const stat = summarize(rows);
  return `<tr><th scope="row">${button(escape(name), action)}</th><td>${stat.games}</td><td class="positive">${stat.wins}</td><td class="negative">${stat.losses}</td><td>${stat.draws}</td><td><span class="rate-number">${percent(stat.winRate)}</span><span class="rate-track" aria-hidden="true"><i style="width:${(stat.winRate || 0) * 100}%"></i></span></td></tr>`;
}

function overview(catalog, s, games) {
  const stat = summarize(games);
  const versions = [...new Set(games.map(g => g.version))];
  const uploads = catalog.uploads.filter(u => games.some(g => g.agent === u.agent));
  const top = opponents(games, 1, 'losses').filter(g => g.losses).slice(0, 5);
  return `<div class="metrics">
    ${metric('Игр в выборке', number(stat.games), 'Открыть список игр', 'all-games')}
    ${metric('Победы', number(stat.wins), 'Посмотреть выигранные игры', 'wins', 'positive')}
    ${metric('Поражения', number(stat.losses), 'Разобрать проигранные игры', 'losses', 'negative')}
    ${metric('Доля побед', percent(stat.winRate), `${stat.draws} ничьих · победы / все игры`, 'all-games')}
  </div>
  <div class="split-grid">
    <section class="panel"><div class="panel-header"><div><h2>Результаты по сторонам</h2><p>Выберите сторону и исход для разбора.</p></div></div>
      <div class="seat-comparison">${[0,1].map(seat => {
        const part = summarize(games.filter(g => g.seat === seat));
        return `<div class="seat-row"><div><strong>${seat === 0 ? 'Первая сторона' : 'Вторая сторона'}</strong><span class="muted">P${seat} · ${part.games} игр</span></div><strong class="seat-rate">${percent(part.winRate)}</strong><div class="seat-results">${button(`${part.wins} побед`, `seat-${seat}-win`)}${button(`${part.losses} поражений`, `seat-${seat}-loss`)}<span class="muted">${part.draws} ничьих</span></div></div>`;
      }).join('')}</div>
    </section>
    <section class="panel"><div class="panel-header"><div><h2>Кому чаще проигрываем</h2><p>По числу поражений в текущей выборке.</p></div>${button('Все соперники →', 'opponents')}</div>
      ${top.length ? `<ol class="opponent-shortlist">${top.map((group, i) => `<li><span class="list-number">${i+1}</span>${button(escape(group.name), 'opponent-' + encodeURIComponent(group.key))}<span><strong>${group.losses}</strong><span class="muted"> / ${group.games} встреч</span></span></li>`).join('')}</ol>` : '<p class="empty-state small">В выбранных играх нет поражений.</p>'}
      <p class="inline-note">Одна встреча ещё не означает устойчиво неудобного соперника.</p>
    </section>
  </div>
  <section class="panel"><div class="panel-header"><div><h2>${versions.length > 1 ? 'Версии и загрузки' : 'Отдельные загрузки'}</h2><p>Только ${escape(leagueName(s.league))}. Состав соперников между загрузками меняется.</p></div></div>
    <div class="table-wrap"><table class="data-table"><thead><tr><th scope="col">Выборка</th><th scope="col">Игры</th><th scope="col">Победы</th><th scope="col">Поражения</th><th scope="col">Ничьи</th><th scope="col">Доля побед</th></tr></thead><tbody>
      ${versions.length > 1 ? versions.map(v => summaryRow(versionName(v), games.filter(g => g.version === v), 'version-' + encodeURIComponent(v))).join('') : ''}
      ${uploads.map(u => summaryRow(`${versionName(u.label)} · загрузка ${u.upload}`, games.filter(g => g.agent === u.agent), 'upload-' + u.agent)).join('')}
    </tbody></table></div>
  </section>
  <p class="inline-note">Исходы — по местам CodinGame, включая игры с равными HP. ${stat.removed ? `Игры со снятием игрока (${stat.removed}) входят в результаты. ` : ''}Рейтинг силы по этим сводкам не вычисляется.</p>`;
}

function opponentsView(s, games) {
  const rows = opponents(games, s.minMeetings, s.opponentSort);
  return `<section class="panel"><div class="panel-header"><div><h2>Соперники</h2><p>Выберите игрока, чтобы открыть все встречи с ним в этой выборке.</p></div>
    <div class="table-tools"><label class="filter-field"><span>Минимум встреч</span><input name="minMeetings" type="number" min="1" max="1000" value="${s.minMeetings}"></label>
    <label class="filter-field"><span>Сортировка</span><select name="opponentSort">${[['losses','Больше поражений'],['loss-rate','Выше доля поражений'],['games','Больше встреч']].map(([v,l]) => option(v,l,s.opponentSort)).join('')}</select></label></div></div>
    <p class="inline-note">Игры группируются по нику соперника; его код мог меняться. ${rows.length} соперников соответствуют минимуму встреч.</p>
    ${rows.length ? `<div class="table-wrap"><table class="data-table"><thead><tr><th scope="col">Соперник</th><th scope="col">Встречи</th><th scope="col">Победы</th><th scope="col">Поражения</th><th scope="col">Ничьи</th><th scope="col">Наша доля побед</th></tr></thead><tbody>
    ${rows.map(group => `<tr><th scope="row">${button(escape(group.name), 'opponent-' + encodeURIComponent(group.key))}${group.ids.length > 1 ? `<span class="cell-note">${group.ids.length} записи на арене</span>` : ''}</th><td>${group.games}</td><td class="positive">${group.wins}</td><td class="negative">${group.losses}</td><td>${group.draws}</td><td>${percent(group.winRate)}</td></tr>`).join('')}</tbody></table></div>` : '<div class="empty-state"><h3>Недостаточно встреч</h3><p>Уменьшите минимальное число встреч или расширьте выборку.</p></div>'}
  </section>`;
}

function gamesView(s, games) {
  const pages = Math.max(1, Math.ceil(games.length / PAGE_SIZE));
  const page = Math.min(s.page, pages), first = (page - 1) * PAGE_SIZE;
  const rows = games.slice(first, first + PAGE_SIZE);
  return `<section class="panel"><div class="panel-header"><div><h2>Игры</h2><p>Откройте реплей и переходите между играми этой выборки.</p></div>
    <label class="filter-field"><span>Сортировка</span><select name="sort">${[['newest','Новые сначала'],['oldest','Старые сначала'],['opponent','По сопернику'],['rating-desc','По рейтингу соперника']].map(([v,l]) => option(v,l,s.sort)).join('')}</select></label></div>
    <div class="table-wrap"><table class="data-table games-table"><thead><tr><th scope="col">Игра</th><th scope="col">Версия</th><th scope="col">Соперник</th><th scope="col">Результат</th><th scope="col">Наши HP / соперника</th><th scope="col">Сторона</th><th scope="col">Длительность</th><th scope="col"><span class="sr-only">Открыть</span></th></tr></thead><tbody>
      ${rows.map(g => `<tr><td><span class="mono">${g.gameId}</span></td><td>${escape(versionName(g.version))}<span class="cell-note">Загрузка ${g.upload}</span></td><th scope="row">${escape(g.opponent.name)}<span class="cell-note">Рейтинг ${number(g.opponent.ratingAtFetch)}</span></th><td>${badge(g)}${g.ourRemoved || g.opponentRemoved ? `<span class="cell-note">Снят ${g.ourRemoved ? 'наш бот' : 'соперник'}</span>` : g.scoreTie ? '<span class="cell-note">Равные HP · места CG</span>' : ''}</td><td class="mono">${score(g.ourScore)} / ${score(g.opponentScore)}</td><td>P${g.seat}</td><td>${g.estimatedTurns == null ? '—' : `≈ ${g.estimatedTurns} ходов`}</td><td><button class="button small" type="button" data-game="${g.agent}:${g.gameId}" aria-label="Смотреть игру ${g.gameId}">Смотреть →</button></td></tr>`).join('')}
    </tbody></table></div>
    <div class="pagination"><span>${first + 1}–${Math.min(first + PAGE_SIZE, games.length)} из ${games.length}</span><div><button class="button secondary" data-page="${page-1}" type="button"${page===1?' disabled':''}>← Назад</button><span>${page} / ${pages}</span><button class="button secondary" data-page="${page+1}" type="button"${page===pages?' disabled':''}>Далее →</button></div></div>
    <p class="inline-note">Рейтинг соперника сохранён на момент скачивания. Длительность приблизительна по числу кадров; точные ходы — в реплее. Снятие игрока не означает отрицательные HP.</p>
  </section>`;
}

export function renderDashboard(container, catalog, input, callbacks) {
  const s = normalizeState(catalog, input), games = selectGames(catalog, s);
  container.innerHTML = `${reportErrors(catalog)}${filters(catalog, s)}
    <div class="dashboard-toolbar"><nav class="tabs" aria-label="Разделы дашборда">${[['overview','Обзор'],['opponents','Соперники'],['games','Игры']].map(([tab,label]) => `<button type="button" class="tab${s.tab===tab?' active':''}" data-tab="${tab}" aria-current="${s.tab===tab?'page':'false'}">${label}</button>`).join('')}</nav>
      <p class="selection-line"><strong>${games.length}</strong> игр · ${escape(leagueName(s.league))}${s.opponentExact ? ` · ${escape(s.opponent)}` : ''}</p></div>
    ${!games.length ? `<section class="empty-state panel"><h2>Нет игр по этим фильтрам</h2><p>Измените условия или вернитесь к исходной выборке.${catalog.errors.length ? ' Некоторые манифесты не удалось загрузить — подробности выше.' : ''}</p>${button('Сбросить фильтры', 'reset', 'button')}</section>` :
      s.tab==='overview' ? overview(catalog,s,games) : s.tab==='opponents' ? opponentsView(s,games) : gamesView(s,games)}`;

  const change = patch => callbacks.onState({...patch, page: patch.page ?? 1});
  const form = container.querySelector('#catalog-filters');
  const searchPatch = () => {
    const opponent = form.elements.opponent.value, gameId = form.elements.gameId.value;
    return {opponent, gameId, opponentExact: opponent === s.opponent ? s.opponentExact : ''};
  };
  form.addEventListener('submit', e => { e.preventDefault(); change(searchPatch()); });
  container.querySelectorAll('select[name],input[type="number"][name]').forEach(control => {
    control.addEventListener('change', () => {
      const key = control.name, patch = {...searchPatch(), [key]: control.value};
      if (key === 'version' || key === 'league') patch.upload = 'all';
      change(patch);
    });
    if (control.tagName === 'INPUT') control.addEventListener('keydown', e => {
      if (e.key === 'Enter') { e.preventDefault(); control.dispatchEvent(new Event('change')); }
    });
  });
  container.querySelectorAll('[data-tab]').forEach(el => el.addEventListener('click', () => change({tab: el.dataset.tab})));
  container.querySelectorAll('[data-page]').forEach(el => el.addEventListener('click', () => change({page: Number(el.dataset.page)})));
  container.querySelectorAll('[data-game]').forEach(el => el.addEventListener('click', () => {
    const row = games.find(g => `${g.agent}:${g.gameId}` === el.dataset.game);
    if (row) callbacks.onOpen(row);
  }));
  container.querySelectorAll('[data-action]').forEach(el => el.addEventListener('click', () => {
    const action = el.dataset.action;
    if (action === 'reset') return callbacks.onState(defaultState(catalog));
    if (action === 'opponents') return change({tab:'opponents'});
    if (action === 'all-games') return change({tab:'games'});
    if (action === 'wins' || action === 'losses') return change({tab:'games',result:action==='wins'?'win':'loss'});
    if (action.startsWith('seat-')) { const [,seat,result] = action.split('-'); return change({tab:'games',seat,result}); }
    if (action.startsWith('version-')) return change({tab:'games',version:decodeURIComponent(action.slice(8)),upload:'all'});
    if (action.startsWith('upload-')) return change({tab:'games',upload:action.slice(7)});
    if (action.startsWith('opponent-')) {
      const key = decodeURIComponent(action.slice(9)), row = games.find(g => g.opponent.key === key);
      return change({tab:'games',opponent:row?.opponent.pseudo || '',opponentExact:key});
    }
  }));
  return games;
}
