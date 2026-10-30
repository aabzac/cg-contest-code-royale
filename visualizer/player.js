/** Recorded arena/local replay playback. This module never runs a bot or a simulator. */
const SIDE = ['#ef8176', '#7db6f5'];
const UNIT = ['Рыцарь', 'Лучник', 'Гигант'];
const LETTER = ['Р', 'Л', 'Г'];
const RADIUS = [20, 25, 40];
const WIDTH = 1920, HEIGHT = 1000;

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function value(number) {
  return Number.isFinite(number) && number >= 0 ? String(number) : '—';
}

function position(pair) {
  return Array.isArray(pair) && Number.isFinite(pair[0]) && Number.isFinite(pair[1]);
}

function boundedIndex(requested, count) {
  const index = Number(requested);
  return Number.isFinite(index) ? Math.max(0, Math.min(count - 1, Math.trunc(index))) : 0;
}

function barracksStatus(countdown, training, compact = false) {
  if (Number.isFinite(countdown) && countdown >= 0)
    return countdown === 0 ? 'готов' : compact ? `${countdown} х.` : `до выпуска: ${countdown} ходов`;
  if (training === true) return compact ? 'обучение' : 'идёт обучение';
  if (training === false) return 'готов';
  return null;
}

function outcome(replay, game) {
  if (['win', 'loss', 'draw'].includes(game?.result)) return game.result;
  if (game?.seat === 0 || game?.seat === 1) {
    const ranks = replay.ranks;
    if (Array.isArray(ranks) && ranks.length === 2) {
      return ranks[game.seat] < ranks[1 - game.seat] ? 'win'
        : ranks[game.seat] > ranks[1 - game.seat] ? 'loss' : 'draw';
    }
    if (Number.isInteger(replay.winner)) return replay.winner < 0 ? 'draw' : replay.winner === game.seat ? 'win' : 'loss';
  }
  return null;
}

/**
 * Mount one replay. initialTurn and onTurn use the frame index, 0 = initial state.
 * Returns a cleanup function which stops playback and removes every event listener.
 */
export function mountPlayer(container, replay, options = {}) {
  const {game = null, index = -1, total = 0, initialTurn = 0,
    onBack, onPrevious, onNext, onTurn} = options;
  if (!Array.isArray(replay.frames) || !replay.frames.length) throw new Error('В реплее нет кадров для просмотра.');
  const frames = replay.frames;
  const sites = Array.isArray(replay.sites) ? replay.sites : [];
  const controller = new AbortController();
  const listen = (node, event, callback) => node.addEventListener(event, callback, {signal: controller.signal});
  const root = element('section', 'player');
  let frameIndex = boundedIndex(initialTurn, frames.length);
  let timer = null, disposed = false, selected = null;
  function button(label, action, className = '') {
    const node = element('button', className, label);
    node.type = 'button';
    if (action) listen(node, 'click', action);
    return node;
  }

  const topbar = element('div', 'player-topbar');
  const back = button(index >= 0 ? '← К выборке' : '← К дашборду', () => {pause(); onBack?.();});
  back.disabled = !onBack;
  const collection = element('span', 'player-collection', index >= 0 && total ? `Игра ${index + 1} из ${total}` : 'Отдельный реплей');
  const previousGame = button('← Предыдущая игра', () => {pause(); onPrevious?.();});
  previousGame.disabled = !onPrevious || index <= 0;
  const nextGame = button('Следующая игра →', () => {pause(); onNext?.();});
  nextGame.disabled = !onNext || index < 0 || index >= total - 1;
  topbar.append(back, collection, previousGame, nextGame);
  root.append(topbar);

  const names = [0, 1].map(side => replay.bots?.[side] || `Игрок ${side + 1}`);
  const heading = element('div', 'player-heading');
  const titleBlock = element('div');
  titleBlock.append(element('h1', 'player-title', `${names[0]} × ${names[1]}`));
  const meta = [];
  if (game?.label || game?.version) meta.push(game.label || game.version);
  if (game?.league) meta.push(game.league[0].toUpperCase() + game.league.slice(1));
  if (game?.upload != null) meta.push(`Загрузка ${game.upload}`);
  if (replay.gameId ?? game?.gameId) meta.push(`Игра ${replay.gameId ?? game.gameId}`);
  if (replay.seed != null) meta.push(`Seed ${replay.seed}`);
  if (game?.ourRemoved) meta.push('Наш бот снят с игры');
  if (game?.opponentRemoved) meta.push('Соперник снят с игры');
  titleBlock.append(element('p', 'player-meta', meta.join(' · ')));
  heading.append(titleBlock);
  const result = outcome(replay, game);
  if (result) heading.append(element('span', `player-result ${result}`, {win: 'Победа', loss: 'Поражение', draw: 'Ничья'}[result]));
  else if (Number.isInteger(replay.winner)) heading.append(element('span', 'player-result', replay.winner < 0 ? 'Ничья' : `Победил игрок ${replay.winner + 1}`));
  root.append(heading);

  const scoreboard = element('div', 'player-scoreboard');
  const cards = [0, 1].map(side => {
    const card = element('div', `player-scorecard side-${side}`);
    const ours = side === game?.seat;
    card.append(element('div', 'player-side-label', `Сторона ${side + 1}${game ? (ours ? ' · Наш бот' : ' · Соперник') : ''}`));
    card.append(element('strong', 'player-name', names[side]));
    const stats = element('div', 'player-stats');
    card.append(stats);
    scoreboard.append(card);
    return stats;
  });
  root.append(scoreboard);

  const canvas = element('canvas', 'player-board');
  canvas.width = WIDTH; canvas.height = HEIGHT;
  canvas.setAttribute('role', 'img');
  canvas.setAttribute('aria-label', 'Поле игры Code Royale. Характеристики игроков и команды приведены под полем.');
  canvas.textContent = 'Ваш браузер не поддерживает отображение игрового поля.';
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Браузер не поддерживает рисование игрового поля.');
  root.append(canvas);

  const controls = element('div', 'player-controls');
  const play = button('Воспроизвести', toggle, 'primary');
  play.setAttribute('aria-label', 'Воспроизвести реплей');
  const previous = button('← Ход', () => {pause(); step(-1);});
  const next = button('Ход →', () => {pause(); step(1);});
  const timeline = element('input', 'player-timeline');
  timeline.type = 'range'; timeline.min = '0'; timeline.max = String(frames.length - 1); timeline.step = '1';
  timeline.setAttribute('aria-label', 'Ход реплея');
  const turnLabel = element('span', 'player-turn');
  const jumpLabel = element('label', 'player-jump-label', 'Перейти: ');
  const jump = element('input', 'player-jump');
  jump.type = 'number'; jump.min = '0'; jump.max = String(frames.length - 1); jump.step = '1';
  jump.setAttribute('aria-label', 'Номер хода, 0 — старт');
  jumpLabel.append(jump);
  const speedLabel = element('label', 'player-speed-label', 'Скорость ');
  const speed = element('select', 'player-speed');
  speed.setAttribute('aria-label', 'Скорость воспроизведения');
  for (const [milliseconds, label] of [[600, 'Медленно'], [250, 'Обычно'], [80, 'Быстро']]) {
    const option = element('option', '', label); option.value = String(milliseconds); option.selected = milliseconds === 250; speed.append(option);
  }
  speedLabel.append(speed);
  controls.append(play, previous, next, timeline, turnLabel, jumpLabel, speedLabel);
  root.append(controls);

  const displayOptions = element('div', 'player-display-options');
  function checkbox(text, checked) {
    const label = element('label');
    const input = element('input'); input.type = 'checkbox'; input.checked = checked;
    label.append(input, document.createTextNode(` ${text}`));
    displayOptions.append(label);
    listen(input, 'change', draw);
    return input;
  }
  const showRanges = checkbox('Радиусы башен', true);
  const showTargets = checkbox('Цели команд', false);
  displayOptions.append(element('span', 'player-help', '← → — по ходам · пробел — пауза · нажмите на объект для подробностей'));
  root.append(displayOptions);

  const inspector = element('div', 'player-inspector');
  inspector.setAttribute('aria-live', 'polite');
  inspector.textContent = 'Выберите сайт, королеву или отряд на поле.';
  root.append(inspector);
  const commands = element('div', 'player-commands');
  const commandViews = [0, 1].map(side => {
    const block = element('section', `player-command side-${side}`);
    block.append(element('h2', '', `Команды · ${names[side]}`));
    const pre = element('pre'); block.append(pre); commands.append(block); return pre;
  });
  root.append(commands);
  const summary = element('div', 'player-summary');
  root.append(summary);
  const warnings = (replay.warnings || []).filter(Boolean);
  if (warnings.length) {
    const details = element('details', 'player-warnings');
    details.append(element('summary', '', 'Примечания к записи'));
    const list = element('ul');
    for (const warning of warnings) list.append(element('li', '', String(warning)));
    details.append(list); root.append(details);
  }
  container.replaceChildren(root);

  function circle(x, y, radius, fill, stroke = null, lineWidth = 1) {
    if (![x, y, radius].every(Number.isFinite) || radius <= 0) return;
    ctx.beginPath(); ctx.arc(x, y, radius, 0, Math.PI * 2);
    if (fill) {ctx.fillStyle = fill; ctx.fill();}
    if (stroke) {ctx.strokeStyle = stroke; ctx.lineWidth = lineWidth; ctx.stroke();}
  }

  function label(text, x, y, size = 20, color = '#e9f0ed', bold = false) {
    ctx.font = `${bold ? '600 ' : ''}${size}px system-ui, sans-serif`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillStyle = color;
    ctx.fillText(text, x, y);
  }

  function field(frame) {
    ctx.clearRect(0, 0, WIDTH, HEIGHT);
    ctx.fillStyle = '#152c2a'; ctx.fillRect(0, 0, WIDTH, HEIGHT);
    ctx.strokeStyle = '#1c3532'; ctx.lineWidth = 1;
    for (let x = 80; x < WIDTH; x += 80) {ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, HEIGHT); ctx.stroke();}
    for (let y = 80; y < HEIGHT; y += 80) {ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(WIDTH, y); ctx.stroke();}
    ctx.strokeStyle = '#38514a'; ctx.lineWidth = 2; ctx.strokeRect(1, 1, WIDTH - 2, HEIGHT - 2);

    if (showRanges.checked) sites.forEach(([, x, y], i) => {
      const [type, owner, , range] = frame.structures?.[i] || [];
      if (type !== 1 || !Number.isFinite(range) || range <= 0) return;
      circle(x, y, range, owner === 0 ? '#ef81760b' : '#7db6f50b', owner === 0 ? '#ef817655' : '#7db6f555', 2);
    });

    sites.forEach(([id, x, y, radius], i) => {
      const [type = -1, owner = -1, p1, p2] = frame.structures?.[i] || [];
      const fill = type === 0 ? '#75623b' : type === 1 ? '#53646a' : type === 2 ? '#68545a' : '#30433b';
      circle(x, y, radius, fill, owner === 0 || owner === 1 ? SIDE[owner] : '#5d6a5b', owner >= 0 ? 6 : 2);
      if (selected?.kind === 'site' && selected.index === i) circle(x, y, radius + 7, null, '#fff2b3', 3);
      const title = type === 0 ? `+${value(p1)}` : type === 1 ? value(p1) : type === 2 ? `Б · ${LETTER[p2] || '?'}` : '·';
      label(title, x, y - 11, type < 0 ? 28 : 25, '#f3f2df', true);
      label(`#${id}`, x, y + 19, 17, '#c5cec2');
      if (type === 2) {
        const status = barracksStatus(p1, frame.training?.[i], true);
        if (status) label(status, x, y + 42, 16, '#e4d9cf');
      }
    });

    if (showTargets.checked && frameIndex > 0) {
      for (const side of [0, 1]) {
        const first = String(frame.cmds?.[side] || '').split('\n')[0];
        const move = /^MOVE\s+(-?\d+)\s+(-?\d+)/.exec(first);
        const build = /^BUILD\s+(\d+)/.exec(first);
        const targetSite = build ? sites.find(site => site[0] === Number(build[1])) : null;
        const to = move ? [Number(move[1]), Number(move[2])] : targetSite?.slice(1, 3);
        const from = frames[frameIndex - 1].queens?.[side];
        if (!position(from) || !position(to)) continue;
        ctx.save(); ctx.strokeStyle = SIDE[side]; ctx.lineWidth = 3; ctx.setLineDash([10, 8]);
        ctx.beginPath(); ctx.moveTo(from[0], from[1]); ctx.lineTo(to[0], to[1]); ctx.stroke(); ctx.restore();
        circle(to[0], to[1], 9, null, SIDE[side], 3);
      }
    }

    (frame.creeps || []).forEach(([owner, type, x, y, hp], i) => {
      const radius = RADIUS[type] || 20;
      circle(x, y, radius, SIDE[owner] || '#a5b4ad', '#12201c', 2);
      if (selected?.kind === 'creep' && selected.index === i) circle(x, y, radius + 6, null, '#fff2b3', 3);
      label(LETTER[type] || '?', x, y, 19, '#182b28', true);
    });
    (frame.queens || []).forEach((queen, side) => {
      if (!position(queen)) return;
      const [x, y] = queen;
      circle(x, y, 32, SIDE[side], '#eef3e8', 3);
      if (selected?.kind === 'queen' && selected.index === side) circle(x, y, 39, null, '#fff2b3', 3);
      label(value(frame.hp?.[side]), x, y, 22, '#192c29', true);
      label('К', x, y - 48, 21, SIDE[side], true);
    });
  }

  function inspect(frame) {
    if (!selected) {inspector.textContent = 'Выберите сайт, королеву или отряд на поле.'; return;}
    const sideLabel = owner => owner === 0 || owner === 1 ? `Сторона ${owner + 1} · ${names[owner]}` : 'Нейтральный';
    if (selected.kind === 'site') {
      const [id, , , radius] = sites[selected.index];
      const [type = -1, owner = -1, p1, p2, gold] = frame.structures?.[selected.index] || [];
      const detail = type === 0 ? `Шахта · доход ${value(p1)}` : type === 1 ? `Башня · HP ${value(p1)} · радиус атаки ${value(p2)}`
        : type === 2 ? `Барак · ${UNIT[p2] || 'тип неизвестен'} · ${barracksStatus(p1, frame.training?.[selected.index]) || 'состояние обучения неизвестно'}` : 'Пустой сайт';
      inspector.textContent = `Сайт #${id} · ${sideLabel(owner)} · ${detail} · золото ${value(gold)} · радиус ${radius}`;
    } else if (selected.kind === 'queen') {
      const side = selected.index, q = frame.queens?.[side];
      inspector.textContent = `Королева · ${sideLabel(side)} · HP ${value(frame.hp?.[side])}${position(q) ? ` · (${q[0]}, ${q[1]})` : ''}`;
    } else {
      const creep = frame.creeps?.[selected.index];
      if (!creep) {selected = null; inspect(frame); return;}
      const [owner, type, x, y, hp] = creep;
      inspector.textContent = `${UNIT[type] || 'Отряд'} · ${sideLabel(owner)} · HP ${value(hp)} · (${x}, ${y})`;
    }
  }

  function draw() {
    if (disposed) return;
    const frame = frames[frameIndex];
    field(frame); inspect(frame);
    const turnText = frameIndex === 0 ? 'Старт' : `Ход ${frame.turn ?? frameIndex}`;
    turnLabel.textContent = `${turnText} / ${frames.at(-1).turn ?? frames.length - 1}`;
    timeline.value = String(frameIndex); jump.value = String(frameIndex);
    timeline.setAttribute('aria-valuetext', turnText);
    previous.disabled = frameIndex === 0; next.disabled = frameIndex === frames.length - 1;
    play.disabled = frames.length < 2;
    canvas.setAttribute('aria-label', `${turnText}. ${names[0]}: HP ${value(frame.hp?.[0])}, золото ${value(frame.gold?.[0])}. ${names[1]}: HP ${value(frame.hp?.[1])}, золото ${value(frame.gold?.[1])}.`);
    for (const side of [0, 1]) {
      const count = (frame.creeps || []).filter(unit => unit[0] === side).length;
      cards[side].textContent = `HP ${value(frame.hp?.[side])}   ·   Золото ${value(frame.gold?.[side])}   ·   Войска ${count}`;
      commandViews[side].textContent = frameIndex === 0 ? 'Начальное состояние' : (frame.cmds?.[side] || 'Команда не записана');
    }
    summary.hidden = !frame.summary;
    summary.textContent = frame.summary ? `Сообщение игры: ${frame.summary}` : '';
  }

  function pause() {
    if (timer !== null) clearInterval(timer);
    timer = null;
    play.textContent = 'Воспроизвести'; play.setAttribute('aria-label', 'Воспроизвести реплей');
    play.setAttribute('aria-pressed', 'false');
  }

  function seek(nextIndex) {
    const nextFrame = boundedIndex(nextIndex, frames.length);
    if (nextFrame !== frameIndex && selected?.kind === 'creep') selected = null;
    const changed = nextFrame !== frameIndex;
    frameIndex = nextFrame; draw();
    if (frameIndex === frames.length - 1) pause();
    if (changed) onTurn?.(frameIndex);
  }

  function step(direction) {seek(frameIndex + direction);}

  function start() {
    if (frames.length < 2 || disposed) return;
    if (frameIndex === frames.length - 1) seek(0);
    play.textContent = 'Пауза'; play.setAttribute('aria-label', 'Приостановить реплей');
    play.setAttribute('aria-pressed', 'true');
    timer = setInterval(() => step(1), Number(speed.value));
  }

  function toggle() {if (timer !== null) pause(); else start();}
  listen(timeline, 'input', () => {pause(); seek(timeline.value);});
  listen(jump, 'change', () => {pause(); seek(jump.value);});
  listen(speed, 'change', () => {if (timer !== null) {pause(); start();}});
  listen(document, 'keydown', event => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.target.closest?.('input,select,textarea,button,a,[contenteditable="true"]')) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault(); pause(); step(event.key === 'ArrowLeft' ? -1 : 1);
    } else if (event.code === 'Space') {event.preventDefault(); toggle();}
  });
  listen(document, 'visibilitychange', () => {if (document.hidden) pause();});
  listen(canvas, 'click', event => {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const x = (event.clientX - rect.left) * WIDTH / rect.width, y = (event.clientY - rect.top) * HEIGHT / rect.height;
    const frame = frames[frameIndex];
    const hits = [];
    (frame.queens || []).forEach((q, i) => {if (position(q) && Math.hypot(q[0] - x, q[1] - y) <= 36) hits.push({kind: 'queen', index: i});});
    (frame.creeps || []).forEach((unit, i) => {if (Math.hypot(unit[2] - x, unit[3] - y) <= (RADIUS[unit[1]] || 20) + 3) hits.push({kind: 'creep', index: i});});
    sites.forEach((site, i) => {if (Math.hypot(site[1] - x, site[2] - y) <= site[3]) hits.push({kind: 'site', index: i});});
    selected = hits[0] || null; draw();
  });
  draw();
  return () => {pause(); disposed = true; controller.abort();};
}
