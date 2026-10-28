import json, sys
sys.stdout.reconfigure(encoding='utf-8')

path, turns = sys.argv[1], [int(x) for x in sys.argv[2].split(',')]
g = json.load(open(path, encoding='utf-8'))
fr = g['frames']
nid = 0
turn = 0
for i, f in enumerate(fr):
    v = f.get('view', '')
    if not f.get('keyframe'):
        continue
    body = v[v.index('\n') + 1:] if '\n' in v else ''
    if not body.strip():
        turn += 1
        continue
    j = json.loads(body)
    fm = j.get('frame', j)
    em = fm.get('entitymodule', [])
    creates = [e for e in em if e.startswith('C ')]
    first = nid + 1
    nid += len(creates)
    if turn in turns:
        print('turn', turn, 'frame', i, 'creates', creates, 'ids', first, '..', nid)
        for e in em:
            if not e.startswith('C '):
                print('   ', e)
        print('  anims', fm.get('anims'))
        print('  tooltips', fm.get('tooltips'))
    turn += 1
