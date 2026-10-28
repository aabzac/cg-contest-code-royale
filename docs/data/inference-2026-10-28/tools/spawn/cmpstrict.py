"""Compare first strict-mode divergence per game between two cgValidate MISMATCH logs."""
import re, sys

def load(fn):
    d = {}
    for l in open(fn, encoding='utf-8'):
        m = re.match(r'MISMATCH .*[\\/](\d+)\.json \(\d+\): (t(\d+) )?(.*)', l.rstrip())
        if not m:
            continue
        d[m.group(1)] = (int(m.group(3)) if m.group(3) else 10 ** 6, m.group(4)[:60])
    return d

b = load(sys.argv[1])
f = load(sys.argv[2])
earlier = [(g, b[g], f[g]) for g in f if g in b and f[g][0] < b[g][0]]
print('baseline mismatching games:', len(b), 'final mismatching games:', len(f))
print('games diverging earlier than baseline:', earlier)
for g, v in sorted(f.items()):
    print('final mismatch', g, 'baseline first bad turn', b.get(g, ('-',))[0], '-> final', v)
