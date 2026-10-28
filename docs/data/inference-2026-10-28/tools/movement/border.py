import json, math, collections
recs = [json.loads(l) for l in open('base_plain.jsonl', encoding='utf-8')]
CR=[20,25,40]
rep_out = collections.Counter(); sim_out = collections.Counter()
ex = []
for r in recs:
    st = r['start']
    dead = set(r['dead'])
    for p in range(2):
        surv = [c for k, c in enumerate(st['c']) if c[0]==p and k not in dead]
        for c, rc, sc in zip(surv, r['rep'][p], r['sim'][p]):
            rad = CR[c[1]]
            for (v, lo, hi, nm) in ((rc[0], rad, 1920-rad, 'x'), (rc[1], rad, 1000-rad, 'y')):
                if v < lo: rep_out[lo - v] += 1
                if v > hi: rep_out[v - hi] += 1
            for v, lo, hi in ((sc[0], rad, 1920-rad), (sc[1], rad, 1000-rad)):
                if v < lo - 0.5: sim_out[round(lo - v)] += 1
                if v > hi + 0.5: sim_out[round(v - hi)] += 1
        q = r['rep'][2][p]; sq = r['sim'][2][p]
        for v, lo, hi in ((q[0], 30, 1890), (q[1], 30, 970)):
            if v < lo or v > hi:
                ex.append((r['g'], r['t'], p, q, [round(sq[0],2), round(sq[1],2)], r['ok']))
print('replay creep beyond-border depth histogram', sorted(rep_out.items()))
print('sim creep beyond-border depth histogram', sorted(sim_out.items()))
print('queen beyond border', len(ex))
for e in ex[:40]: print(' ', e)
