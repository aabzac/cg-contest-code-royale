"""Grid-search each iter's pre-towards offset against the time-0 (truncated) positions."""
import json, sys
from collections import defaultdict
from offsets import groups, towards

data = defaultdict(list)  # iter -> list of (centre, eq, t0)
for line in open(sys.argv[1], encoding='utf-8'):
    game = json.loads(line)
    sites = {int(k): v for k, v in game['sites'].items()}
    for ev in game['events']:
        qa = {u['owner']: (u['x'], u['y']) for u in ev['after'] if u['type'] == 'Q'}
        for grp in groups(game, ev):
            owner, ty, sid = grp['key']
            if sid is None:
                continue
            sx, sy, sr = sites[sid]
            for it, c in enumerate(grp['creeps']):
                data[it].append(((sx, sy), qa[1 - owner], tuple(c['t0'])))

step = 0.05
for it in sorted(data):
    best = []
    for ix in range(-60, 61):
        for iy in range(-60, 61):
            ox, oy = ix * step, iy * step
            n = 0
            for (sx, sy), eq, t0 in data[it]:
                p = towards((sx + ox, sy + oy), eq, 30.0)
                if (int(p[0]), int(p[1])) == t0:
                    n += 1
            best.append((n, round(ox, 2), round(oy, 2)))
    best.sort(reverse=True)
    top = best[0][0]
    region = [b for b in best if b[0] >= top - 0]
    xs = [b[1] for b in region]; ys = [b[2] for b in region]
    print('iter', it, 'samples', len(data[it]), 'best', best[:3], 'max-region x[%.2f,%.2f] y[%.2f,%.2f] size %d' % (min(xs), max(xs), min(ys), max(ys), len(region)))
