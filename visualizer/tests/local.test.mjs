import test from 'node:test';
import assert from 'node:assert/strict';
import {recordedBotLabel} from '../bot-label.js';
import {localState, localFlags, localRunRequest, localSelectionUrl, selectLocalGames, localResult,
  localHistoryBots, swapLocalSides} from '../local.js';

const defaults = {UNSTICK: true, APPROACH: false, ROUTE: false};
const approach = {...defaults, APPROACH: true};
const route = {...defaults, ROUTE: true};
const routeId = 'current-unstick1-approach0-route1';

const data = {
  bots: [{id: 'current', label: 'current', flags: [
    {id: 'UNSTICK', label: 'Обход застреваний', default: true},
    {id: 'APPROACH', label: 'Подход к площадке', default: false},
    {id: 'ROUTE', label: 'Планирование пути', default: false}
  ]}, {id: 'v03', label: 'v03', flags: []}],
  defaults: {bot0: 'current', bot1: 'v03'},
  games: [
    {file: '/replays/local/b.json', createdAt: '2026-10-31T12:00:00Z', botIds: ['v03', 'current'], winner: 0},
    {file: '/replays/local/a.json', createdAt: '2026-10-31T11:00:00Z', botIds: ['current-approach', 'v03'], bots: ['current + APPROACH', 'v03'], winner: -1},
    {file: '/replays/local/c.json', createdAt: '2026-10-31T13:00:00Z', botIds: ['retired', 'v03'], winner: 1},
    {file: '/replays/local/d.json', createdAt: '2026-10-31T14:00:00Z', botIds: ['current', 'v03'],
      botConfigIds: [routeId, 'v03'], botFlags: [route, {}], bots: ['current [UNSTICK=1, APPROACH=0, ROUTE=1]', 'v03'], winner: 0}
  ]
};

test('URL form values override saved inputs and round-trip both sides including false flags and empty seed', () => {
  const params = new URLSearchParams({bot0: 'current', seed: '', flags0: JSON.stringify({UNSTICK: false, APPROACH: true, ROUTE: false})});
  const state = localState(data, {bot0: 'v03', bot1: 'current', seed: '120', historyBot: 'retired', flags0: route, flags1: route}, params);
  assert.deepEqual(state, {bot0: 'current', bot1: 'current', seed: '', historyBot: 'retired',
    flags0: {UNSTICK: false, APPROACH: true, ROUTE: false}, flags1: route});
  const url = localSelectionUrl('http://localhost:8765/visualizer/?version=v01&game=123&turn=50', state);
  assert.equal(url.searchParams.get('view'), 'local');
  assert.equal(url.searchParams.has('game'), false);
  assert.equal(url.searchParams.has('turn'), false);
  assert.deepEqual(localState(data, {}, url.searchParams), state);
});

test('current defaults come from the server and side settings are independent', () => {
  const state = localState(data);
  assert.deepEqual(state.flags0, defaults);
  assert.deepEqual(state.flags1, defaults);
  state.flags0.ROUTE = true;
  assert.equal(state.flags1.ROUTE, false);
  const changedServer = {...data, bots: [{...data.bots[0], flags: data.bots[0].flags.map(flag => ({...flag, default: true}))}, data.bots[1]]};
  assert.deepEqual(localState(changedServer, {flags0: {APPROACH: false}}).flags0,
    {UNSTICK: true, APPROACH: false, ROUTE: true});
  assert.deepEqual(localFlags(data, {UNSTICK: false, ROUTE: 'true', unknown: true}),
    {UNSTICK: false, APPROACH: false, ROUTE: false});
  assert.deepEqual(localFlags(data, '{broken'), defaults);
});

test('a two-flag catalog drops stale ROUTE from launch state and URL without relabeling saved ROUTE games', () => {
  const catalog = {...data, bots: [{...data.bots[0], flags: data.bots[0].flags.filter(flag => flag.id !== 'ROUTE')}, data.bots[1]]};
  const historyBefore = structuredClone(catalog.games);
  const state = localState(catalog, {bot0: 'current', bot1: 'current', flags0: route, flags1: route},
    new URLSearchParams({flags0: JSON.stringify({UNSTICK: false, APPROACH: true, ROUTE: true}), historyBot: routeId}));
  assert.deepEqual(state.flags0, {UNSTICK: false, APPROACH: true});
  assert.deepEqual(state.flags1, {UNSTICK: true, APPROACH: false});
  assert.deepEqual(localRunRequest(state), {bot0: 'current', bot1: 'current', seed: null,
    flags0: state.flags0, flags1: state.flags1});
  const url = localSelectionUrl('http://localhost:8765/visualizer/', state);
  for (const side of [0, 1]) assert.equal(Object.hasOwn(JSON.parse(url.searchParams.get(`flags${side}`)), 'ROUTE'), false);
  assert.deepEqual(localState(catalog, {}, url.searchParams), state);

  const [saved] = selectLocalGames(catalog, state);
  assert.equal(saved.botConfigIds[0], routeId);
  assert.equal(recordedBotLabel(saved, 0), 'current [UNSTICK=1, ROUTE=1]');
  assert.equal(localHistoryBots(catalog).find(bot => bot.id === routeId).label, 'current [UNSTICK=1, ROUTE=1]');
  assert.equal(recordedBotLabel({...saved, botFlags: undefined}, 0), saved.bots[0]);
  assert.deepEqual(catalog.games, historyBefore);
});

test('unknown runnable bot falls back, historical configurations remain available, and failed catalog keeps edits', () => {
  const state = localState(data, {bot0: 'proc:whatever', historyBot: 'retired'});
  assert.equal(state.bot0, 'current');
  assert.equal(state.historyBot, 'retired');
  assert.equal(localState(data, {historyBot: routeId}).historyBot, routeId);
  assert.equal(localState(data, {historyBot: 'missing'}).historyBot, 'all');
  const retired = localState(data, {}, new URLSearchParams('bot0=current-approach'));
  assert.equal(retired.bot0, 'current');
  assert.deepEqual(retired.flags0, defaults);
  const failed = localState({bots: [], games: []}, {bot0: 'current', seed: '0', flags0: route});
  assert.equal(failed.bot0, 'current');
  assert.deepEqual(failed.flags0, route);
});

test('requests send explicit current flags but never send stale current flags to a frozen bot', () => {
  const state = localState(data, {bot0: 'current', bot1: 'current', flags0: route, flags1: approach, seed: '0'});
  assert.deepEqual(localRunRequest(state), {bot0: 'current', bot1: 'current', seed: 0, flags0: route, flags1: approach});
  const frozen = localState(data, {...state, bot0: 'v03'});
  assert.deepEqual(frozen.flags0, route, 'remembered edits survive switching away');
  assert.deepEqual(localRunRequest(frozen), {bot0: 'v03', bot1: 'current', seed: 0, flags1: approach});
  assert.deepEqual(localRunRequest(localState(data, {...frozen, bot0: 'current'})).flags0, route);
  assert.throws(() => localRunRequest({...state, flags0: {ROUTE: 'false'}}));
});

test('swapping sides exchanges complete configurations, copies flag maps and retains seed/filter', () => {
  const state = localState(data, {bot0: 'current', bot1: 'v03', flags0: route, flags1: approach, seed: '37', historyBot: routeId});
  const swapped = swapLocalSides(state);
  assert.equal(swapped.bot0, 'v03');
  assert.equal(swapped.bot1, 'current');
  assert.deepEqual(swapped.flags0, approach);
  assert.deepEqual(swapped.flags1, route);
  assert.equal(swapped.seed, '37');
  assert.equal(swapped.historyBot, routeId);
  assert.deepEqual(swapLocalSides(swapped), state);
  swapped.flags1.ROUTE = false;
  assert.equal(state.flags0.ROUTE, true);
});

test('development seeds preserve zero and reject evaluation seeds, decimals, and unsafe numbers', () => {
  const request = seed => localRunRequest({bot0: 'current', bot1: 'v03', seed});
  assert.equal(request('').seed, null);
  assert.equal(request('0').seed, 0);
  assert.equal(request('199999').seed, 199999);
  for (const seed of ['200000', '-1', '1.2', '1e4', 'Infinity', '9007199254740992']) assert.throws(() => request(seed));
});

test('history filters recorded ids exactly, displays enabled saved flags and preserves replay ordering', () => {
  const original = data.games.map(game => game.file);
  assert.deepEqual(selectLocalGames(data, {historyBot: 'all'}).map(game => game.file), [original[3], original[2], original[0], original[1]]);
  assert.deepEqual(selectLocalGames(data, {historyBot: 'current'}).map(game => game.file), [original[3], original[0]]);
  assert.deepEqual(selectLocalGames(data, {historyBot: routeId}).map(game => game.file), [original[3]]);
  assert.deepEqual(selectLocalGames(data, {historyBot: 'current-approach'}).map(game => game.file), [original[1]]);
  const choices = localHistoryBots(data);
  assert.equal(choices.find(bot => bot.id === routeId).label, 'current [UNSTICK=1, ROUTE=1]');
  assert.equal(recordedBotLabel({botIds: ['current'], botFlags: [defaults]}, 0), 'current [UNSTICK=1]');
  assert.equal(recordedBotLabel({botIds: ['current'], botFlags: [{UNSTICK: false, APPROACH: false, ROUTE: false}]}, 0), 'current');
  assert.equal(recordedBotLabel({botIds: ['saved-bot'], bots: ['Saved original name']}, 0), 'Saved original name');
  assert.equal(choices.find(bot => bot.id === 'current-approach').label, 'current + APPROACH');
  assert.equal(choices.filter(bot => bot.id === 'v03').length, 1);
  assert.deepEqual(data.games.map(game => game.file), original);
  assert.equal(localResult(data.games[0]), 'Победа стороны 1');
  assert.equal(localResult(data.games[1]), 'Ничья');
  assert.equal(localResult({winner: null}), 'Результат неизвестен');
});
