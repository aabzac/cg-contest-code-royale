"""For turns with several spawn groups, check whether creation order follows site id order."""
import json, sys
from collections import Counter
from offsets import groups

cnt = Counter()
for line in open(sys.argv[1], encoding='utf-8'):
    game = json.loads(line)
    for ev in game['events']:
        gs = groups(game, ev)
        if len(gs) < 2:
            continue
        sids = [g['key'][2] for g in gs]
        owners = [g['key'][0] for g in gs]
        kind = 'site-order' if sids == sorted(sids) else 'NOT-site-order'
        mixed = 'mixed-owners' if len(set(owners)) > 1 else 'same-owner'
        cnt[(kind, mixed)] += 1
        if kind != 'site-order' or cnt[(kind, mixed)] <= 3:
            print(game['game'], ev['turn'], [(g['key'], [c['id'] for c in g['creeps']]) for g in gs])
print(cnt)
