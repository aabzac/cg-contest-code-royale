"""Print per-turn stdout and summaries of a replay for a turn range: summary.py file from to"""
import json, sys
sys.stdout.reconfigure(encoding='utf-8')
path, a, b = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
g = json.load(open(path, encoding='utf-8'))
turn = 0
cur = []
for i, f in enumerate(g['frames']):
    if i == 0:
        continue
    cur.append(f)
    if f.get('keyframe'):
        turn += 1
        if a <= turn <= b:
            for x in cur:
                print(turn, 'agent', x.get('agentId'), 'stdout', repr(x.get('stdout')), 'summary', repr(x.get('summary')), 'stderr' if x.get('stderr') else '')
        cur = []
print('ranks', g['ranks'], 'scores', g['scores'])
