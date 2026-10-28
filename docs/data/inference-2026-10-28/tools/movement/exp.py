"""Run hypothesis variants of the Python movement model over plain turns in parallel.
Usage: python exp.py file.jsonl 'name:k=v,k=v' 'name2:...'"""
import sys, json, multiprocessing as mp
sys.path.insert(0, '.')
import msim

RECS = None


def init(path):
    global RECS
    RECS = msim.load(path)


def parse_kw(spec):
    kw = {}
    for kv in spec.split(','):
        if not kv:
            continue
        k, v = kv.split('=')
        if v in ('True', 'False'):
            kw[k] = v == 'True'
        else:
            try:
                kw[k] = int(v)
            except ValueError:
                try:
                    kw[k] = float(v)
                except ValueError:
                    kw[k] = v
    return kw


def work(args):
    spec, lo, hi = args
    o = msim.Opts(**parse_kw(spec))
    res = []
    for r in RECS[lo:hi]:
        q, c = msim.simulate(r, o)
        ok, bad = msim.compare(r, q, c, o)
        nb = sum(1 for b in bad if b[0] in ('Q', 'C'))
        res.append((r['g'], r['t'], ok, nb))
    return res


if __name__ == '__main__':
    path = sys.argv[1]
    specs = sys.argv[2:]
    n = sum(1 for _ in open(path, encoding='utf-8'))
    chunks = 28
    step = (n + chunks - 1) // chunks
    with mp.Pool(14, initializer=init, initargs=(path,)) as pool:
        base = None
        for full in specs:
            name, _, spec = full.partition(':')
            out = []
            for part in pool.map(work, [(spec, i, min(n, i + step)) for i in range(0, n, step)]):
                out.extend(part)
            ok = sum(1 for x in out if x[2])
            units_bad = sum(x[3] for x in out)
            line = '%-28s ok %d/%d  bad-units %d' % (name, ok, len(out), units_bad)
            if base is None:
                base = {(g, t): k for g, t, k, _ in out}
            else:
                gained = sum(1 for g, t, k, _ in out if k and not base[(g, t)])
                lost = sum(1 for g, t, k, _ in out if not k and base[(g, t)])
                line += '  gained %d lost %d' % (gained, lost)
            print(line, flush=True)
