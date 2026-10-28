"""Order of structure-phase destruction anims inside one frame: tower deaths (site had a TOWER and
now has none) vs mine exhaustions (site had a MINE and now reads 'Remaining gold: 0' with no
structure), with their site ids. Kotlin acts in site-id order; the statement acts towers, barracks,
then mines."""
import json, sys, os, glob, collections
sys.stdout.reconfigure(encoding='utf-8')
root = sys.argv[1]
files = sorted(f for f in glob.glob(os.path.join(root, '**', '*.json'), recursive=True) if os.path.basename(f)[:-5].isdigit())
stats = collections.Counter()
for path in files:
    g = json.load(open(path, encoding='utf-8'))
    site_ent = {}   # entity -> site id
    site_pos = {}   # entity -> (x, y)
    texts = {}
    turn = 0
    for i, f in enumerate(g['frames']):
        if not f.get('keyframe'):
            continue
        v = f.get('view', '')
        body = v[v.index('\n') + 1:] if '\n' in v else ''
        if not body.strip():
            if i > 0:
                turn += 1
            continue
        fm = json.loads(body)
        fm = fm.get('frame', fm)
        tt = fm.get('tooltips') or [{}, {}]
        reg, tx = (tt + [{}, {}])[:2]
        for k, o in reg.items():
            if o.get('type') == 'Site':
                site_ent[int(k)] = o.get('id')
        for e in fm.get('entitymodule', []):
            if not e.startswith('U '):
                continue
            parts = e.split(' ')
            eid = int(parts[1])
            if eid in site_ent and eid not in site_pos:
                kv = parts[3:]
                d = {kv[j]: kv[j + 1] for j in range(0, len(kv) - 1, 2)}
                if 'x' in d and 'y' in d:
                    site_pos[eid] = (float(d['x']), float(d['y']))
        prev = dict(texts)
        for k, lines in tx.items():
            texts[int(k)] = lines
        if i == 0:
            continue
        turn += 1
        an = fm.get('anims') or []
        kinds = {}
        for k, lines in tx.items():
            k = int(k)
            if k not in site_ent or k not in site_pos:
                continue
            pv = prev.get(k)
            if pv is None or len(lines) != 2:
                continue
            if len(pv) >= 3 and pv[2] == 'TOWER':
                kinds[site_pos[k]] = ('T', site_ent[k])
            elif len(pv) == 3 and pv[2].startswith('MINE') and lines[1] == 'Remaining gold: 0':
                kinds[site_pos[k]] = ('M', site_ent[k])
        seq = []
        for a in an:
            loc = (a['params']['x'], a['params']['y'])
            if a['id'] == 'destruction' and loc in kinds:
                seq.append(kinds[loc])
        ks = [s[0] for s in seq]
        if 'T' in ks and 'M' in ks:
            # did any mine-exhaustion come before a tower death?
            first_m = ks.index('M')
            last_t = max(j for j, s in enumerate(ks) if s == 'T')
            order = 'mine before tower' if first_m < last_t else 'towers before mines'
            site_order = all(seq[j][1] < seq[j + 1][1] for j in range(len(seq) - 1))
            stats[order] += 1
            stats['in site-id order' if site_order else 'not in site-id order'] += 1
            print(os.path.basename(path), turn, seq, order)
print(dict(stats))
