import json, glob, collections, re, sys
cnt = collections.Counter()
ex = collections.defaultdict(list)
for f in glob.glob('C:/Disk_D/Programming/CG/cg-contest-code-royale/replays/cg/*/*.json'):
    if not re.search(r'\d+\.json$', f):
        continue
    g = json.load(open(f, encoding='utf-8'))
    if not isinstance(g, dict) or 'frames' not in g:
        continue
    for i, fr in enumerate(g['frames']):
        s = fr.get('summary')
        if not s:
            continue
        for line in s.split('\n'):
            if not line.strip():
                continue
            key = re.sub(r'\d+', '#', line)
            cnt[key] += 1
            if len(ex[key]) < 3:
                ex[key].append((g['gameId'], (i + 1) // 2, line))
for k, v in cnt.most_common():
    print(v, k, ex[k][:2])
