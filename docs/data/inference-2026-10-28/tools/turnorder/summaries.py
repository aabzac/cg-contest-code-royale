"""Collect all non-empty frame summaries over a replay corpus, normalised by replacing numbers."""
import json, sys, os, glob, re, collections
sys.stdout.reconfigure(encoding='utf-8')
root = sys.argv[1]
files = sorted(f for f in glob.glob(os.path.join(root, '**', '*.json'), recursive=True) if os.path.basename(f)[:-5].isdigit())
cnt = collections.Counter()
ex = collections.defaultdict(list)
for path in files:
    g = json.load(open(path, encoding='utf-8'))
    turn = 0
    for i, f in enumerate(g['frames']):
        if i == 0:
            continue
        s = f.get('summary')
        if f.get('keyframe'):
            turn += 1
        if s:
            for line in s.strip().split('\n'):
                k = re.sub(r'\d+', '#', line)
                cnt[k] += 1
                if len(ex[k]) < 8:
                    ex[k].append((os.path.basename(path), turn + (0 if f.get('keyframe') else 1), line))
for k, v in cnt.most_common():
    print(v, k)
    for e in ex[k]:
        print('     ', e)
