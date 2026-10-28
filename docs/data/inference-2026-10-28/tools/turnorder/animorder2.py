"""Frames where a mine is exhausted (destruction anim at a site whose tooltip now reads
'Remaining gold: 0' without a structure) and creeps die in the same frame: order of the events.
Under the statement order with removal right after the tower phase, deaths caused by towers would be
created before the mine-phase destruction; in the Kotlin order all deaths come last."""
import json, sys, os, glob, collections
sys.stdout.reconfigure(encoding='utf-8')
root = sys.argv[1]
files = sorted(f for f in glob.glob(os.path.join(root, '**', '*.json'), recursive=True) if os.path.basename(f)[:-5].isdigit())
stats = collections.Counter()
for path in files:
    g = json.load(open(path, encoding='utf-8'))
    site_ent = {}  # entity id -> (x, y)
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
                site_ent[int(k)] = None
        # site positions from entity updates
        for e in fm.get('entitymodule', []):
            if not e.startswith('U '):
                continue
            parts = e.split(' ')
            eid = int(parts[1])
            if eid in site_ent and site_ent[eid] is None:
                kv = parts[3:]
                d = {kv[j]: kv[j + 1] for j in range(0, len(kv) - 1, 2)}
                if 'x' in d and 'y' in d:
                    site_ent[eid] = (float(d['x']), float(d['y']))
        prev_texts = dict(texts)
        for k, lines in tx.items():
            texts[int(k)] = lines
        if i == 0:
            continue
        turn += 1
        an = fm.get('anims') or []
        if not any(a['id'] == 'death' for a in an):
            continue
        exhausted = set()
        for k, lines in tx.items():
            k = int(k)
            prev = prev_texts.get(k)
            if k in site_ent and site_ent[k] and len(lines) == 2 and lines[1] == 'Remaining gold: 0'                     and prev is not None and len(prev) == 3 and prev[2].startswith('MINE'):
                exhausted.add(site_ent[k])
        if not exhausted:
            continue
        # creeps that died this frame although they had >= 2 HP before: killed by tower damage
        tower_kills = 0
        for k, lines in tx.items():
            k = int(k)
            if k in site_ent or not lines or not lines[0].startswith('Health: '):
                continue
            if lines[0] == 'Health: 0':
                pv = prev_texts.get(k)
                if pv and pv[0].startswith('Health: ') and int(pv[0][8:]) >= 2:
                    tower_kills += 1
        if tower_kills == 0:
            stats['skipped (no tower kill)'] += 1
            continue
        seq = []
        for a in an:
            loc = (a['params']['x'], a['params']['y'])
            if a['id'] == 'destruction' and loc in exhausted:
                seq.append('EXH')
            elif a['id'] == 'death':
                seq.append('death')
            else:
                seq.append(a['id'])
        if 'EXH' not in seq:
            continue
        key = ' '.join(seq)
        stats['frames'] += 1
        first_death = seq.index('death')
        last_exh = max(j for j, s in enumerate(seq) if s == 'EXH')
        stats['death before exhaustion' if first_death < last_exh else 'all deaths after exhaustion'] += 1
        print(os.path.basename(path), turn, key)
print(dict(stats))
