"""Categorise plain-turn unit mismatches from a -PdumpPlain JSONL file."""
import json, math, sys, collections

path = sys.argv[1]
recs = [json.loads(l) for l in open(path, encoding='utf-8')]
QR, CR = 30, [20, 25, 40]
W, H = 1920, 1000

def rnd(v):
    return math.floor(v + 0.5)

cat = collections.Counter()
unitcat = collections.Counter()
examples = collections.defaultdict(list)
nbad = 0
for r in recs:
    if r['ok']:
        continue
    nbad += 1
    st = r['start']
    sites = st['s']
    rep, sim = r['rep'], r['sim']
    # count mismatch per owner
    if len(rep[0]) != len(sim[0]) or len(rep[1]) != len(sim[1]):
        cat['count'] += 1
        continue
    # start creeps per owner in order
    sc = [[c for c in st['c'] if c[0] == p] for p in range(2)]
    bad_units = []
    for p in range(2):
        rq, sq = rep[2][p], sim[2][p]
        if rq[0] != rnd(sq[0]) or rq[1] != rnd(sq[1]):
            bad_units.append(('Q', p, None, rq, sq))
        for k in range(len(rep[p])):
            rc, sc_ = rep[p][k], sim[p][k]
            if rc[0] != rnd(sc_[0]) or rc[1] != rnd(sc_[1]):
                bad_units.append(('C', p, k, rc, sc_))
            elif rc[2] != sc_[2]:
                bad_units.append(('HP', p, k, rc, sc_))
    if not bad_units:
        cat['hp-only/none'] += 1
        continue
    kinds = set()
    for (kind, p, k, rv, sv) in bad_units:
        if kind == 'HP':
            kinds.add('hp')
            continue
        err = math.hypot(rv[0] - sv[0], rv[1] - sv[1])
        if kind == 'Q':
            qx, qy = st['q'][p][0], st['q'][p][1]
            # context
            near_enemy_knights = sum(1 for c in st['c'] if c[0] != p and math.hypot(c[2] - qx, c[3] - qy) < 150)
            near_border = min(qx - QR, W - QR - qx, qy - QR, H - QR - qy) < 70
            cmd = r['c%d' % p] or ''
            touching_site = any(math.hypot(s[0] - sv[0], s[1] - sv[1]) < s[2] + QR + 3 for s in sites)
            tag = 'Q:' + ('knights' if near_enemy_knights else 'noknights') + (',border' if near_border else '') + (',site' if touching_site else '') + ',' + cmd.split(' ')[0]
            unitcat[tag] += 1
            examples[tag].append((r['g'], r['t'], p, rv, [round(sv[0], 2), round(sv[1], 2)], round(err, 2)))
            kinds.add('Q')
        else:
            c = sc[p][k]
            typ = c[1]
            cx, cy = c[2], c[3]
            eq = st['q'][1 - p]
            dq = math.hypot(cx - eq[0], cy - eq[1])
            nfriends = sum(1 for o in st['c'] if o is not c and math.hypot(o[2] - cx, o[3] - cy) < CR[o[1]] + CR[typ] + 25)
            near_site = any(math.hypot(s[0] - sv[0], s[1] - sv[1]) < s[2] + CR[typ] + 3 for s in sites)
            near_border = min(sv[0] - CR[typ], W - CR[typ] - sv[0], sv[1] - CR[typ], H - CR[typ] - sv[1]) < 3
            tag = ['K', 'A', 'G'][typ] + ':' + ('nearQ' if dq < 200 else 'farQ') + (',crowd' if nfriends else '') + (',site' if near_site else '') + (',border' if near_border else '')
            unitcat[tag] += 1
            examples[tag].append((r['g'], r['t'], p, k, rv, [round(sv[0], 2), round(sv[1], 2)], round(err, 2)))
            kinds.add(['K', 'A', 'G'][typ])
    cat['+'.join(sorted(kinds))] += 1

print('bad plain turns', nbad)
for k, v in cat.most_common():
    print('  turn kind', k, v)
print('unit categories:')
for k, v in unitcat.most_common():
    print('  ', k, v)
    for e in examples[k][:4]:
        print('       ', e)
