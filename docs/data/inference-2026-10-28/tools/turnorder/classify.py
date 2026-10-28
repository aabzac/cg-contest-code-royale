"""Classify RESYNC lines of a cgValidate -PlogResync=true log: hp-only, count-only (extra units, rest
positions equal), or position errors."""
import re, sys, collections
sys.stdout.reconfigure(encoding='utf-8')

line_re = re.compile(r'^RESYNC g(\d+) t(\d+) (\S+) (\S+) replay-only \[(.*?)\] sim-only \[(.*?)\] LOG (.*)$')


def units(s):
    if not s.strip():
        return []
    return [tuple(u.split(' ')) for u in s.split(', ')]


def classify(rep, sim):
    """rep/sim: lists of (owner,type,x,y,hp) differing units. Returns class and details."""
    a = collections.Counter((u[0], u[1], u[2], u[3]) for u in rep)
    b = collections.Counter((u[0], u[1], u[2], u[3]) for u in sim)
    only_a = a - b
    only_b = b - a
    if not only_a and not only_b:
        return 'hp', None
    if not only_a or not only_b:
        return 'count-replay-extra' if only_a else 'count-sim-extra', (dict(only_a), dict(only_b))
    return 'pos', (dict(only_a), dict(only_b))


def main():
    path = sys.argv[1]
    show = sys.argv[2] if len(sys.argv) > 2 else ''
    cnt = collections.Counter()
    for line in open(path, encoding='utf-8', errors='replace'):
        m = line_re.match(line.rstrip('\n'))
        if not m:
            continue
        g, t, kind1, kind2, r, s, log = m.groups()
        c, det = classify(units(r), units(s))
        key = (kind1 + ' ' + kind2, c)
        cnt[key] += 1
        if show and c in show.split(','):
            print(f'g{g} t{t} {kind1} {kind2} {c} rep={r} sim={s} LOG {log[:600]}')
    for k, v in sorted(cnt.items()):
        print(v, k)


main()
