"""Group spawn events per barracks and print time-0 offsets relative to centre+towards(enemyQ,30)."""
import json, sys, math
from collections import Counter, defaultdict

def resize(vx, vy, n):
    l = vx * vx + vy * vy
    if l < 1e-12:
        return (n, 0.0)
    l = math.sqrt(l)
    return (vx / l * n, vy / l * n)

def towards(p, t, md):
    if (p[0] - t[0]) ** 2 + (p[1] - t[1]) ** 2 < md * md:
        return t
    r = resize(t[0] - p[0], t[1] - p[1], md)
    return (p[0] + r[0], p[1] + r[1])

def groups(game, ev):
    sites = {int(k): v for k, v in game['sites'].items()}
    out = []
    cur = None
    for c in ev['spawned']:
        x, y = c['t0']
        if x is None:
            sid = None
        else:
            sid = min(sites, key=lambda i: (sites[i][0] - x) ** 2 + (sites[i][1] - y) ** 2)
        key = (c['owner'], c['type'], sid)
        if cur is None or cur['key'] != key:
            cur = dict(key=key, creeps=[])
            out.append(cur)
        cur['creeps'].append(c)
    return out

if __name__ == '__main__':
    cnt = Counter()
    per_iter = defaultdict(Counter)
    n = 0
    show = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    for line in open(sys.argv[1], encoding='utf-8'):
        game = json.loads(line)
        sites = {int(k): v for k, v in game['sites'].items()}
        for ev in game['events']:
            after = {u['id']: u for u in ev['after']}
            qpos = {u['owner']: (u['x'], u['y']) for u in ev['after'] if u['type'] == 'Q'}
            qprev = {u['owner']: (u['x'], u['y']) for u in ev['before'] if u['type'] == 'Q'}
            for grp in groups(game, ev):
                owner, ty, sid = grp['key']
                if sid is None:
                    continue
                sx, sy, sr = sites[sid]
                eq = qpos[1 - owner]
                base = towards((sx, sy), eq, 30.0)
                offs = []
                for it, c in enumerate(grp['creeps']):
                    x, y = c['t0']
                    # t0 is truncated (toInt) -> actual in [x, x+1)
                    offs.append((round(x + 0.5 - base[0], 2), round(y + 0.5 - base[1], 2)))
                key = tuple((round(a), round(b)) for a, b in offs)
                cnt[(owner, ty, len(offs), key)] += 1
                for it, o in enumerate(offs):
                    per_iter[(owner, ty, it)][(round(o[0]), round(o[1]))] += 1
                if n < show:
                    print(game['game'], ev['turn'], 'owner', owner, ty, 'site', sid, (sx, sy, sr), 'eq', eq, 'base', (round(base[0], 2), round(base[1], 2)), 'offs', offs)
                n += 1
    print('groups', n)
    for k, v in sorted(cnt.items(), key=lambda kv: -kv[1])[:40]:
        print(v, k)
    print('per iter:')
    for k in sorted(per_iter):
        print(k, per_iter[k].most_common(8))
