"""Scan replays for the order of anim events (construction/destruction/death) inside a frame."""
import json, sys, os, glob, collections
sys.stdout.reconfigure(encoding='utf-8')

root = sys.argv[1]
files = sorted(glob.glob(os.path.join(root, '**', '*.json'), recursive=True))
files = [f for f in files if os.path.basename(f)[:-5].isdigit()]
patterns = collections.Counter()
examples = collections.defaultdict(list)
for path in files:
    g = json.load(open(path, encoding='utf-8'))
    turn = 0
    for i, f in enumerate(g['frames']):
        if not f.get('keyframe'):
            continue
        v = f.get('view', '')
        body = v[v.index('\n') + 1:] if '\n' in v else ''
        if i == 0:
            continue
        turn += 1
        if not body.strip():
            continue
        fm = json.loads(body)
        fm = fm.get('frame', fm)
        an = fm.get('anims') or []
        seq = [a['id'] for a in an]
        # compress runs
        comp = []
        for s in seq:
            if not comp or comp[-1] != s:
                comp.append(s)
        key = '>'.join(comp)
        patterns[key] += 1
        if len(examples[key]) < 5:
            examples[key].append((os.path.basename(path), turn, [(a['id'], a['t'], round(a['params']['x'], 3), round(a['params']['y'], 3)) for a in an]))
for k, v in patterns.most_common():
    print(v, k)
    if 'death' in k and ('destruction' in k):
        for e in examples[k]:
            print('    ', e)
