"""Game endings: number of turns, final HUD queen HP, CG scores and ranks, per replay."""
import json, sys, os, glob, collections
sys.stdout.reconfigure(encoding='utf-8')
root = sys.argv[1]
files = sorted(f for f in glob.glob(os.path.join(root, '**', '*.json'), recursive=True) if os.path.basename(f)[:-5].isdigit())
kinds = collections.Counter()
for path in files:
    g = json.load(open(path, encoding='utf-8'))
    turns = sum(1 for i, f in enumerate(g['frames']) if i > 0 and f.get('keyframe'))
    # last summaries
    last = [f.get('summary') for f in g['frames'][-3:] if f.get('summary')]
    sc, rk = g['scores'], g['ranks']
    if turns == 250:
        kind = '250 turns ' + ('equal scores' if sc[0] == sc[1] else 'different scores')
    else:
        kind = '<250 turns ' + ('a score is 0' if 0 in sc else ('a score is -1' if -1 in sc else 'no zero score'))
    winner_by_score = -1 if sc[0] == sc[1] else (0 if sc[0] > sc[1] else 1)
    winner_by_rank = -1 if rk[0] == rk[1] else (0 if rk[0] < rk[1] else 1)
    agree = winner_by_score == winner_by_rank
    kinds[(kind, 'rank agrees with score' if agree else 'RANK DISAGREES')] += 1
    if not agree or sc[0] == sc[1]:
        print(os.path.basename(path), 'turns', turns, 'scores', sc, 'ranks', rk, last)
for k, v in sorted(kinds.items()):
    print(v, k)
