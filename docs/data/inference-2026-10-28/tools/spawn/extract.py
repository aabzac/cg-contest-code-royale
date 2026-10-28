"""Extract every creep spawn event from CG Code Royale replays.

For each newly registered unit token group we record the entity updates of its frame:
the time-0 commit (state right after `towards`, before collision resolution) and the
final state. Output: JSON lines to stdout.
Usage: python -I extract.py <dir-or-file>... > spawns.jsonl
"""
import json, sys, os, re, math

XR = re.compile(r'(?:^| )x (-?\d+)')
YR = re.compile(r'(?:^| )y (-?\d+)')


def parse_updates(em):
    """entity id -> list of (time, props string) in frame order"""
    out = {}
    for e in em:
        if not e.startswith('U '):
            continue
        sp = e.split(' ', 3)
        if len(sp) < 4:
            continue
        out.setdefault(int(sp[1]), []).append((float(sp[2]), sp[3]))
    return out


def last_xy(updates, cur=None):
    x, y = (cur or (None, None))
    for t, p in updates:
        m = XR.findall(p)
        if m:
            x = int(m[-1])
        m = YR.findall(p)
        if m:
            y = int(m[-1])
    return x, y


def xy_at(updates, time):
    x = y = None
    for t, p in updates:
        if t != time:
            continue
        m = XR.findall(p)
        if m:
            x = int(m[-1])
        m = YR.findall(p)
        if m:
            y = int(m[-1])
    return x, y


def files_of(args):
    for a in args:
        if os.path.isdir(a):
            for root, _, fs in os.walk(a):
                for f in sorted(fs):
                    if re.fullmatch(r'\d+\.json', f):
                        yield os.path.join(root, f)
        else:
            yield a


def process(fn):
    g = json.load(open(fn, encoding='utf-8'))
    gid = g['gameId']
    k = [f for f in g['frames'] if f.get('keyframe')]
    frames = []
    for f in k:
        raw = f['view']
        jj = json.loads(raw.split('\n', 1)[1])
        frames.append(jj.get('frame', jj))
    f0 = frames[0]
    reg0 = f0['tooltips'][0]
    up0 = parse_updates(f0['entitymodule'])
    sites = {}
    for e, v in reg0.items():
        if v.get('type') == 'Site':
            x, y = last_xy(up0.get(int(e), []))
            r = int(f0['tooltips'][1][e][0].split(': ')[1])
            sites[v['id']] = (x, y, r)
    queens = {}
    for e, v in reg0.items():
        if v.get('type') == 'Queen':
            e = int(e)
            circ = ' '.join(p for _, p in up0.get(e - 2, []))
            owner = 1 if 'Unite_Base_Bleu' in circ else 0
            queens[owner] = e
    # running unit state: id -> dict(owner,type,x,y,hp)
    units = {}
    texts = {}
    for owner, e in queens.items():
        x, y = last_xy(up0.get(e, []))
        units[e] = dict(owner=owner, type='Q', x=x, y=y)
    events = []
    for t in range(1, len(frames)):
        fr = frames[t]
        up = parse_updates(fr.get('entitymodule', []))
        tt = fr.get('tooltips') or [{}, {}]
        reg = tt[0] if len(tt) > 0 else {}
        txt = tt[1] if len(tt) > 1 else {}
        prev = {e: dict(u) for e, u in units.items()}
        for e, lines in txt.items():
            texts[int(e)] = lines
        new = []
        for e, v in reg.items():
            ty = v.get('type')
            if ty in ('Site', 'Queen'):
                continue
            e = int(e)
            circ = ' '.join(p for _, p in up.get(e - 2, []))
            owner = 1 if 'Unite_Base_Bleu' in circ else 0
            units[e] = dict(owner=owner, type=ty, x=None, y=None)
            new.append(e)
        for e, u in units.items():
            x, y = last_xy(up.get(e, []), (u['x'], u['y']))
            u['x'], u['y'] = x, y
            hp = texts.get(e - 2)
            if hp and u['type'] != 'Q':
                u['hp'] = int(hp[0].split(': ')[1])
        if not new:
            continue
        new.sort()
        # group by owner (each barracks completion creates count creeps of one type)
        spawned = []
        for e in new:
            x0, y0 = xy_at(up.get(e, []), 0.0)
            spawned.append(dict(id=e, owner=units[e]['owner'], type=units[e]['type'], t0=(x0, y0),
                                fin=(units[e]['x'], units[e]['y'])))
        # alive units before this turn (prev frame end), and at end of this frame
        before = [dict(id=e, **u) for e, u in prev.items() if u['type'] == 'Q' or u.get('hp', 1) > 0]
        after = [dict(id=e, **u) for e, u in units.items() if (u['type'] == 'Q' or u.get('hp', 1) > 0)]
        events.append(dict(game=gid, turn=t, spawned=spawned, before=before, after=after))
    return dict(game=gid, sites=sites, queens=queens, events=events)


if __name__ == '__main__':
    for fn in files_of(sys.argv[1:]):
        try:
            r = process(fn)
        except Exception as ex:
            print('ERR', fn, ex, file=sys.stderr)
            continue
        print(json.dumps(r))
