import sys, json, math
sys.path.insert(0, '.')
from msim import *
recs = load(sys.argv[1])
g, t = int(sys.argv[2]), int(sys.argv[3])
extra = dict(a.split('=') for a in sys.argv[4:])
kw = {k: (float(v) if '.' in v else (int(v) if v.lstrip('-').isdigit() else (v == 'True' if v in ('True','False') else v))) for k, v in extra.items()}
for r in recs:
    if r['g'] == g and r['t'] == t:
        st = r['start']
        print('cmds', r['c0'], '|', r['c1'])
        print('queens', st['q'])
        for k, c in enumerate(st['c']):
            print('  creep', k, c)
        qs = [(st['q'][p][0], st['q'][p][1]) for p in range(2)]
        for k, s in enumerate(st['s']):
            dmin = min(math.hypot(s[0]-x, s[1]-y) - s[2] for x, y in qs + [(c[2], c[3]) for c in st['c']])
            if dmin < 150:
                print('  site', k, s, 'mind-edge', round(dmin, 1))
        print('replay end', r['rep'])
        print('java end', [[ [round(v,2) for v in c] for c in grp] if i<2 else [[round(v,2) for v in q] for q in grp] for i, grp in enumerate(r['sim'])], 'dead', r.get('dead'))
        o = Opts(**kw)
        q, c = simulate(r, o, trace=True)
        print('model', compare(r, q, c, o))
