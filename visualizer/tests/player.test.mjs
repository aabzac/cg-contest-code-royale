import test from 'node:test';
import assert from 'node:assert/strict';
import {commandTarget} from '../player.js';

test('MOVE retains its full destination and shows straight prediction separately from the recorded position', () => {
  const from = [100, 200], actual = [125, 235];
  assert.deepEqual(commandTarget('MOVE 400 600\nTRAIN', from, [], actual),
    {to: [400, 600], step: [136, 248], actual: [125, 235]});
  assert.deepEqual(commandTarget('MOVE 130 240', [100, 200], []),
    {to: [130, 240], step: [130, 240], actual: null});
  assert.deepEqual(from, [100, 200]);
  assert.deepEqual(actual, [125, 235]);
});

test('BUILD keeps its full site line without movement circles even when distant or the next position is known', () => {
  const sites = [[7, 400, 600, 70], [9, 120, 225, 60]];
  assert.deepEqual(commandTarget('BUILD 7 MINE', [100, 200], sites, [136, 248]),
    {to: [400, 600], step: null, actual: null});
  assert.deepEqual(commandTarget('BUILD 7 TOWER', [400, 499], sites, [400, 498]),
    {to: [400, 600], step: null, actual: null});
});

test('a zero-distance MOVE still has a straight marker and invalid recorded positions are omitted', () => {
  assert.deepEqual(commandTarget('MOVE 100 200', [100, 200], [], [101, 202]),
    {to: [100, 200], step: [100, 200], actual: [101, 202]});
  for (const actual of [null, [1], [NaN, 200], [100, Infinity]]) {
    assert.deepEqual(commandTarget('MOVE 1500 400', [1800, 800], [], actual),
      {to: [1500, 400], step: [1764, 752], actual: null});
  }
});

test('WAIT and missing destinations do not display either movement circle', () => {
  assert.equal(commandTarget('WAIT', [100, 200], [], [101, 202]), null);
  assert.equal(commandTarget('BUILD 99 MINE', [100, 200], []), null);
  assert.equal(commandTarget('MOVE 400 600', null, []), null);
});
