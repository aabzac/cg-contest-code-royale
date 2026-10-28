"""Spawn turns: compare creep HP lists and pre-existing creep / queen positions (replay vs sim)
for two dumps on their common spawn turns.

usage: python spawnhp.py <dumpA.jsonl> <dumpB.jsonl>
"""
import json
import sys


def jround(v):
    # Java Math.round for the doubles stored before rounding
    import math
    return int(math.floor(v + 0.5))


def load(path):
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            d = json.loads(line)
            if d['spawn']:
                out[(d['g'], d['t'])] = d
    return out


def stats(d):
    rep, sim = d['rep'], d['sim']
    start = d['start']
    dead = d['dead']
    res = {}
    counts_ok = all(len(rep[p]) == len(sim[p]) for p in range(2))
    res['count'] = counts_ok
    res['hp'] = counts_ok and all([c[2] for c in rep[p]] == [c[2] for c in sim[p]] for p in range(2))
    # pre-existing creeps that survive: start creeps of owner p minus dead ones (dead ordinals < start count)
    oldpos = True
    old_total = 0
    for p in range(2):
        n_start = sum(1 for c in start['c'] if c[0] == p)
        n_dead_old = sum(1 for o, k in dead if o == p and k < n_start)
        n_old = n_start - n_dead_old
        old_total += n_old
        if len(rep[p]) < n_old or len(sim[p]) < n_old:
            oldpos = False
            continue
        for k in range(n_old):
            r, s = rep[p][k], sim[p][k]
            if int(r[0]) != jround(s[0]) or int(r[1]) != jround(s[1]):
                oldpos = False
    res['oldpos'] = oldpos
    res['old_total'] = old_total
    q = rep[2]
    res['queens'] = all(int(q[p][0]) == jround(sim[2][p][0]) and int(q[p][1]) == jround(sim[2][p][1]) for p in range(2))
    return res


a, b = load(sys.argv[1]), load(sys.argv[2])
common = sorted(set(a) & set(b))
same_start = [k for k in common if json.dumps(a[k]['start']) == json.dumps(b[k]['start'])]
print(f"spawn turns A {len(a)} B {len(b)} common {len(common)} identical start {len(same_start)}")
for name, dd in (('A', a), ('B', b)):
    st = [stats(dd[k]) for k in same_start]
    print(f"{name}: count ok {sum(s['count'] for s in st)}  hp lists ok {sum(s['hp'] for s in st)}  "
          f"old-creep positions ok {sum(s['oldpos'] for s in st)}  queens ok {sum(s['queens'] for s in st)}  of {len(st)}")
ga = [k for k in same_start if stats(b[k])['hp'] and not stats(a[k])['hp']]
la = [k for k in same_start if stats(a[k])['hp'] and not stats(b[k])['hp']]
print("hp gained", len(ga), ga[:10])
print("hp lost", len(la), la[:10])
