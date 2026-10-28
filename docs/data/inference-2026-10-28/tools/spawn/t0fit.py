"""Test hypotheses for the time-0 (pre-collision) spawn positions."""
import json, sys, math
from collections import Counter, defaultdict
from offsets import groups, towards

O4 = [(1, -1), (-1, 1), (1, 1), (-1, -1)]

def trunc(v):
    return int(v)  # Kotlin Double.toInt(): toward zero

def pred(h, centre, eq, it, owner):
    sx, sy = centre
    if h == 'H1':  # centre + o, then towards
        o = O4[it]
        p = (sx + o[0], sy + o[1])
        return towards(p, eq, 30.0)
    if h == 'H2':  # towards from centre, then + o
        o = O4[it]
        b = towards((sx, sy), eq, 30.0)
        return (b[0] + o[0], b[1] + o[1])
    if h == 'REPO':
        c = -1 if owner == 1 else 1
        p = (sx + c * it, sy + c * it)
        return towards(p, eq, 30.0)

if __name__ == '__main__':
    res = defaultdict(Counter)
    bad_examples = defaultdict(list)
    for line in open(sys.argv[1], encoding='utf-8'):
        game = json.loads(line)
        sites = {int(k): v for k, v in game['sites'].items()}
        for ev in game['events']:
            qa = {u['owner']: (u['x'], u['y']) for u in ev['after'] if u['type'] == 'Q'}
            qb = {u['owner']: (u['x'], u['y']) for u in ev['before'] if u['type'] == 'Q'}
            for grp in groups(game, ev):
                owner, ty, sid = grp['key']
                if sid is None:
                    continue
                sx, sy, sr = sites[sid]
                for qname, qs in (('qAfter', qa), ('qBefore', qb)):
                    eq = qs[1 - owner]
                    for h in ('H1', 'H2', 'REPO'):
                        okg = True
                        for it, c in enumerate(grp['creeps']):
                            p = pred(h, (sx, sy), eq, it, owner)
                            got = (trunc(p[0]), trunc(p[1]))
                            ok = got == tuple(c['t0'])
                            res[(qname, h)]['creep_ok' if ok else 'creep_bad'] += 1
                            if not ok:
                                okg = False
                                if len(bad_examples[(qname, h)]) < 8:
                                    bad_examples[(qname, h)].append((game['game'], ev['turn'], owner, ty, sid, it, (round(p[0], 2), round(p[1], 2)), c['t0'], 'eq', eq))
                        res[(qname, h)]['group_ok' if okg else 'group_bad'] += 1
    for k in sorted(res):
        print(k, dict(res[k]))
    for k in sorted(bad_examples):
        if k[1] == 'REPO':
            continue
        print(k)
        for b in bad_examples[k]:
            print('   ', b)
