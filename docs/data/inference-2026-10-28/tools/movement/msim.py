"""Python model of the movement part of one Code Royale turn (queen actions + creep phase),
faithful to cr.core.Rules/Physics, with switches for live-rule hypotheses."""
import json, math, sys, collections

W, H = 1920, 1000
QR, QM, QS = 30, 10000, 60
CR = [20, 25, 40]
CM = [400, 900, 2000]
CS = [100, 75, 50]
RANGE = [0, 200, 0]
OBST_GAP = 90


class Opts:
    def __init__(self, **kw):
        self.passes = 1           # collision passes per substep
        self.end_fix = 0          # extra fixCollisions passes after creep phase
        self.gap_unit = 1.0
        self.gap_site = 1.0       # gap added to a unit pushed by a site
        self.knight_off = 3.0
        self.steps = 5
        self.queen_in_loop = False
        self.mass_q = QM
        self.round_mode = 'round'
        self.clamp_first = True   # clamp before pair loop (referee) else after
        self.both_gap = True
        self.knight_always = False
        self.pre_fix = 0
        self.order = 'repo'
        self.cm = None   # creep masses override 'k/a/g'
        self.cs = None   # creep speeds override
        self.cr = None   # creep radii override
        self.move_order = 'type'  # type (stable sort by creep type) | list | type_desc | owner1first
        self.post_fix_each = 0
        self.__dict__.update(kw)


def norm(vx, vy, ln):
    l2 = vx * vx + vy * vy
    if l2 < 1e-12:
        return ln, 0.0
    l = math.sqrt(l2)
    return vx / l * ln, vy / l * ln


def towards(px, py, tx, ty, d):
    if (px - tx) ** 2 + (py - ty) ** 2 < d * d:
        return tx, ty
    rx, ry = norm(tx - px, ty - py, d)
    return px + rx, py + ry


class Ent:
    __slots__ = ('x', 'y', 'r', 'm', 'unit', 'kind', 'owner', 'typ', 'hp', 'idx')

    def __init__(self, x, y, r, m, unit, kind, owner=-1, typ=-1, hp=0, idx=-1):
        self.x, self.y, self.r, self.m, self.unit = x, y, r, m, unit
        self.kind, self.owner, self.typ, self.hp, self.idx = kind, owner, typ, hp, idx


def setloc(e, x, y):
    if e.unit and x == 0.0 and y == 0.0 and math.copysign(1, x) > 0 and math.copysign(1, y) > 0:
        return
    e.x, e.y = x, y


def collision_check(ents, o, acceptable_gap=0.0):
    changed = False
    n = len(ents)
    for i in range(n):
        a = ents[i]
        if o.clamp_first:
            cd = OBST_GAP + a.r if a.m == 0 else a.r
            setloc(a, min(max(a.x, cd), W - cd), min(max(a.y, cd), H - cd))
        for j in range(n):
            if j == i:
                continue
            b = ents[j]
            dist = math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2)
            overlap = a.r + b.r + acceptable_gap - dist
            if overlap <= 1e-6:
                continue
            if a.m == 0 and b.m == 0:
                d1, d2 = 0.5, 0.5
            elif a.m == 0:
                d1, d2 = 0.0, 1.0
            elif b.m == 0:
                d1, d2 = 1.0, 0.0
            else:
                d1 = b.m / (a.m + b.m)
                d2 = a.m / (a.m + b.m)
            vx, vy = b.x - a.x, b.y - a.y
            if a.m == 0 and b.m == 0:
                gap = 20.0
            elif a.m == 0 or b.m == 0:
                gap = o.gap_site
            else:
                gap = o.gap_unit
            rx, ry = norm(vx, vy, d1 * overlap + (0.0 if (a.m == 0 and b.m > 0) else gap))
            setloc(a, a.x - rx, a.y - ry)
            rx, ry = norm(vx, vy, d2 * overlap + (0.0 if (b.m == 0 and a.m > 0) else gap))
            setloc(b, b.x + rx, b.y + ry)
            changed = True
        if not o.clamp_first:
            cd = OBST_GAP + a.r if a.m == 0 else a.r
            setloc(a, min(max(a.x, cd), W - cd), min(max(a.y, cd), H - cd))
    return changed


def fix_collisions(ents, o, n):
    for _ in range(n):
        if not collision_check(ents, o):
            return


def parse_cmd(c):
    if c is None:
        return ('WAIT',)
    t = c.strip().split(' ')
    if t[0] == 'MOVE':
        return ('MOVE', int(t[1]), int(t[2]))
    if t[0] == 'BUILD':
        return ('BUILD', int(t[1]), t[2] if len(t) > 2 else '')
    return ('WAIT',)


def simulate(rec, o, trace=False):
    global CM, CS, CR
    CM0, CS0, CR0 = CM, CS, CR
    if o.cm: CM = [int(v) for v in o.cm.split('/')]
    if o.cs: CS = [int(v) for v in o.cs.split('/')]
    if o.cr: CR = [int(v) for v in o.cr.split('/')]
    try:
        return _simulate(rec, o, trace)
    finally:
        CM, CS, CR = CM0, CS0, CR0


def _simulate(rec, o, trace=False):
    st = rec['start']
    queens = [Ent(q[0], q[1], QR, o.mass_q, True, 'Q', owner=p, hp=q[2]) for p, q in enumerate(st['q'])]
    creeps = []
    for k, c in enumerate(st['c']):
        creeps.append(Ent(c[2], c[3], CR[c[1]], CM[c[1]], True, 'C', owner=c[0], typ=c[1], hp=c[4], idx=k))
    sites = [Ent(s[0], s[1], s[2], 0, False, 'S', owner=s[4], typ=s[3], idx=k) for k, s in enumerate(st['s'])]
    if o.order == 'repo':
        ents = creeps + queens + sites
    elif o.order == 'legacy':
        ents = [c for c in creeps if c.owner == 0] + [queens[0]] + [c for c in creeps if c.owner == 1] + [queens[1]] + sites
    elif o.order == 'qfirst':
        ents = queens + creeps + sites
    elif o.order == 'sfirst':
        ents = sites + creeps + queens
    elif o.order == 'sqc':
        ents = sites + queens + creeps
    # queen actions
    qtarget = [None, None]
    for p in range(2):
        cmd = parse_cmd(rec['c%d' % p])
        q = queens[p]
        tgt = None
        if cmd[0] == 'MOVE':
            tgt = (cmd[1], cmd[2])
        elif cmd[0] == 'BUILD':
            s = sites[cmd[1]]
            d2 = (s.x - q.x) ** 2 + (s.y - q.y) ** 2
            if not d2 < (QR + s.r + 5) ** 2:
                tgt = (s.x, s.y)
            elif not (s.typ != -1 and s.owner == 1 - p):
                kind = cmd[2].split('-')[0]
                nt = {'MINE': 0, 'TOWER': 1, 'BARRACKS': 2}.get(kind)
                if nt is not None:
                    s.typ, s.owner = nt, p
        qtarget[p] = tgt
        if tgt is not None and not o.queen_in_loop:
            x, y = towards(q.x, q.y, tgt[0], tgt[1], QS)
            setloc(q, x, y)
    if trace:
        print('  after queens', [(round(q.x, 3), round(q.y, 3)) for q in queens])
    if o.pre_fix:
        fix_collisions(ents, o, o.pre_fix)
    if o.move_order == 'type':
        order = sorted(creeps, key=lambda c: c.typ)
    elif o.move_order == 'list':
        order = list(creeps)
    elif o.move_order == 'type_desc':
        order = sorted(creeps, key=lambda c: -c.typ)
    elif o.move_order == 'owner1first':
        order = sorted(creeps, key=lambda c: (c.typ, -c.owner))
    fr = 1.0 / o.steps
    for step in range(o.steps):
        if o.queen_in_loop:
            for p in range(2):
                if qtarget[p] is not None:
                    q = queens[p]
                    x, y = towards(q.x, q.y, qtarget[p][0], qtarget[p][1], QS * fr)
                    setloc(q, x, y)
        for c in order:
            move_creep(c, creeps, queens, sites, o, fr)
        fix_collisions(ents, o, o.passes)
        if trace:
            print('  substep', step, [(round(q.x, 3), round(q.y, 3)) for q in queens], [(c.idx, round(c.x, 3), round(c.y, 3)) for c in creeps])
    if o.end_fix:
        fix_collisions(ents, o, o.end_fix)
    return queens, creeps


def move_creep(c, creeps, queens, sites, o, fr):
    sp = CS[c.typ] * fr
    if c.typ == 2:
        best, bd = None, 0
        for s in sites:
            if s.typ != 1 or s.owner != 1 - c.owner:
                continue
            d = (s.x - c.x) ** 2 + (s.y - c.y) ** 2
            if best is None or d < bd:
                best, bd = s, d
        if best is not None:
            x, y = towards(c.x, c.y, best.x, best.y, sp)
            setloc(c, x, y)
        return
    if c.typ == 0:
        t = queens[1 - c.owner]
        tr = QR
    else:
        t, bd = None, 0
        for e in creeps:
            if e.owner == c.owner:
                continue
            d = (e.x - c.x) ** 2 + (e.y - c.y) ** 2
            if t is None or d < bd:
                t, bd = e, d
        if t is None:
            t = queens[c.owner]
            tr = QR
        else:
            tr = t.r
    d2 = (c.x - t.x) ** 2 + (c.y - t.y) ** 2
    reach = c.r + tr + RANGE[c.typ]
    if d2 > reach * reach or (o.knight_always and c.typ == 0):
        ox, oy = norm(c.x - t.x, c.y - t.y, o.knight_off)
        x, y = towards(c.x, c.y, t.x + ox, t.y + oy, sp)
        setloc(c, x, y)


def rnd(v, mode='round'):
    if mode == 'round':
        return math.floor(v + 0.5)
    return int(v)


def dead_set(rec):
    """Start-state creep indices of the dead creeps ([owner, ordinal] pairs, or old global indices)."""
    d = rec.get('dead', [])
    if d and isinstance(d[0], list):
        st = rec['start']['c']
        by_owner = [[k for k, c in enumerate(st) if c[0] == p] for p in range(2)]
        return {by_owner[o][k] for o, k in d if k < len(by_owner[o])}
    return set(d)


def compare(rec, queens, creeps, o):
    """(ok, bad list): unit positions (rounded) vs the replay; creeps matched by order of survivors."""
    rep = rec['rep']
    dead = dead_set(rec)
    bad = []
    for p in range(2):
        q = queens[p]
        rq = rep[2][p]
        if rq[0] != rnd(q.x, o.round_mode) or rq[1] != rnd(q.y, o.round_mode):
            bad.append(('Q', p, None, rq, (round(q.x, 3), round(q.y, 3))))
        surv = [c for c in creeps if c.owner == p and c.idx not in dead]
        rc = rep[p]
        if len(surv) != len(rc):
            bad.append(('count', p, None, None, None))
            continue
        for k, c in enumerate(surv):
            if rc[k][0] != rnd(c.x, o.round_mode) or rc[k][1] != rnd(c.y, o.round_mode):
                bad.append(('C', p, c.idx, rc[k], (round(c.x, 3), round(c.y, 3))))
    return not bad, bad


def java_check(rec, queens, creeps):
    """Max abs difference between this model and the Java raw end positions."""
    dead = dead_set(rec)
    m = 0.0
    for p in range(2):
        jq = rec['sim'][2][p]
        m = max(m, abs(jq[0] - queens[p].x), abs(jq[1] - queens[p].y))
        surv = [c for c in creeps if c.owner == p and c.idx not in dead]
        js = rec['sim'][p]
        if len(js) != len(surv):
            return float('inf')
        for c, j in zip(surv, js):
            m = max(m, abs(j[0] - c.x), abs(j[1] - c.y))
    return m


def load(path):
    return [json.loads(l) for l in open(path, encoding='utf-8')]


if __name__ == '__main__':
    recs = load(sys.argv[1])
    kw = {}
    for a in sys.argv[2:]:
        k, v = a.split('=')
        kw[k] = v
    o = Opts(**kw)
    ok = 0
    worst = 0
    for r in recs:
        q, c = simulate(r, o)
        good, _ = compare(r, q, c, o)
        ok += good
        jc = java_check(r, q, c)
        if jc > 1e-6:
            worst += 1
            if worst < 5:
                print('java diff', r['g'], r['t'], jc)
    print('ok', ok, 'of', len(recs), 'java-model diffs', worst)
