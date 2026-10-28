import json, math, sys
def rnd(v): return math.floor(v + 0.5)
for path in sys.argv[1:]:
    tot = {}
    for l in open(path, encoding='utf-8'):
        r = json.loads(l)
        if not r.get('spawn'):
            continue
        st = r['start']
        by_owner = [[k for k, c in enumerate(st['c']) if c[0] == p] for p in range(2)]
        dead = {by_owner[o][k] for o, k in r['dead']} if r['dead'] and isinstance(r['dead'][0], list) else set(r['dead'])
        newpos = []
        rows = []
        for p in range(2):
            pre = [k for k, c in enumerate(st['c']) if c[0] == p]
            surv = [k for k in pre if k not in dead]
            rep = r['rep'][p]; sim = r['sim'][p]
            if len(rep) < len(surv) or len(sim) < len(surv):
                continue
            newpos += [(c[0], c[1]) for c in rep[len(surv):]]
            for j, k in enumerate(surv):
                rows.append((rep[j][0], rep[j][1], sim[j][0], sim[j][1]))
            q = r['rep'][2][p]; sq = r['sim'][2][p]
            rows.append((q[0], q[1], sq[0], sq[1]))
        for (rx, ry, sx, sy) in rows:
            d = min([math.hypot(rx - x, ry - y) for x, y in newpos] or [9999])
            band = 'near<150' if d < 150 else ('mid<300' if d < 300 else 'far>=300')
            ok = rx == rnd(sx) and ry == rnd(sy)
            a, b = tot.get(band, (0, 0))
            tot[band] = (a + ok, b + 1)
    print(path, {k: '%d/%d (%.2f%%)' % (a, b, 100.0 * a / b) for k, (a, b) in sorted(tot.items())})
