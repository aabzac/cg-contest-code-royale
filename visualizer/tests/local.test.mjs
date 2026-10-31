import test from 'node:test';
import assert from 'node:assert/strict';
import {localState, localRunRequest, localSelectionUrl, selectLocalGames, localResult} from '../local.js';

const data = {
  bots: [{id: 'current', label: 'current'}, {id: 'v03', label: 'v03'}, {id: 'current-approach', label: 'current + approach'}],
  defaults: {bot0: 'current', bot1: 'v03'},
  games: [
    {file: '/replays/local/b.json', createdAt: '2026-10-31T12:00:00Z', botIds: ['v03', 'current'], winner: 0},
    {file: '/replays/local/a.json', createdAt: '2026-10-31T11:00:00Z', botIds: ['current-approach', 'v03'], winner: -1},
    {file: '/replays/local/c.json', createdAt: '2026-10-31T13:00:00Z', botIds: ['retired', 'v03'], winner: 1}
  ]
};

test('URL form values override saved inputs, including clearing a previously fixed seed', () => {
  const state = localState(data, {bot0: 'v03', bot1: 'current', seed: '120', historyBot: 'retired'}, new URLSearchParams('bot0=current-approach&seed='));
  assert.deepEqual(state, {bot0: 'current-approach', bot1: 'current', seed: '', historyBot: 'retired'});
  const url = localSelectionUrl('http://localhost:8765/visualizer/?version=v01&game=123&turn=50', state);
  assert.equal(url.searchParams.get('view'), 'local');
  assert.equal(url.searchParams.has('game'), false);
  assert.equal(url.searchParams.has('turn'), false);
  assert.deepEqual(localState(data, {}, url.searchParams), state);
});

test('unknown runnable bot falls back to server defaults, historical filters remain available', () => {
  assert.deepEqual(localState(data, {bot0: 'proc:whatever', historyBot: 'retired'}), {bot0: 'current', bot1: 'v03', seed: '', historyBot: 'retired'});
  assert.equal(localState(data, {historyBot: 'missing'}).historyBot, 'all');
  // A failed bot request must not discard settings needed for a retry.
  assert.equal(localState({bots: [], games: []}, {bot0: 'current-approach', seed: '0'}).bot0, 'current-approach');
});

test('development seeds preserve zero and reject evaluation seeds, decimals, and unsafe numbers', () => {
  const request = seed => localRunRequest({bot0: 'current', bot1: 'v03', seed});
  assert.equal(request('').seed, null);
  assert.equal(request('0').seed, 0);
  assert.equal(request('199999').seed, 199999);
  for (const seed of ['200000', '-1', '1.2', '1e4', 'Infinity', '9007199254740992']) assert.throws(() => request(seed));
});

test('local replay sequence uses the full filtered history in newest-first order without mutating input', () => {
  const original = data.games.map(game => game.file);
  assert.deepEqual(selectLocalGames(data, {historyBot: 'all'}).map(game => game.file), [original[2], original[0], original[1]]);
  assert.deepEqual(selectLocalGames(data, {historyBot: 'current'}).map(game => game.file), [original[0]]);
  assert.deepEqual(selectLocalGames(data, {historyBot: 'v03'}).map(game => game.file), [original[2], original[0], original[1]]);
  assert.deepEqual(data.games.map(game => game.file), original);
  assert.equal(localResult(data.games[0]), 'Победа стороны 1');
  assert.equal(localResult(data.games[1]), 'Ничья');
  assert.equal(localResult({winner: null}), 'Результат неизвестен');
});
