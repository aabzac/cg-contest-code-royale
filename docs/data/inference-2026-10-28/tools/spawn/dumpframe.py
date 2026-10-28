import json, sys, re

# Usage: python dumpframe.py <replay.json> <turn>   (turn = keyframe index, 0 = init)
fn, turn = sys.argv[1], int(sys.argv[2])
g = json.load(open(fn, encoding='utf-8'))
k = [f for f in g['frames'] if f.get('keyframe')]
f = k[turn]
raw = f['view']
print('header:', raw.split('\n', 1)[0])
jj = json.loads(raw.split('\n', 1)[1])
fj = jj.get('frame', jj)
print('modules:', list(fj.keys()))
em = fj.get('entitymodule', [])
for e in em:
    print(e)
print('tooltips:', json.dumps(fj.get('tooltips'))[:3000])
print('anims:', json.dumps(fj.get('anims'))[:2000] if 'anims' in fj else None)
