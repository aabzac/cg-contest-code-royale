"""Compare per-turn exact-match flags of two CgValidate dumps on their common (game, turn) set.

usage: python common.py <dumpA.jsonl> <dumpB.jsonl> [list-limit]
"""
import json
import sys


def load(path):
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            d = json.loads(line)
            out[(d['g'], d['t'])] = d
    return out


a = load(sys.argv[1])
b = load(sys.argv[2])
limit = int(sys.argv[3]) if len(sys.argv) > 3 else 15
common = sorted(set(a) & set(b))
print(f"A turns {len(a)}  B turns {len(b)}  common {len(common)}")
for kind in (False, True):
    keys = [k for k in common if a[k]['spawn'] == kind]
    same_start = sum(1 for k in keys if json.dumps(a[k]['start']) == json.dumps(b[k]['start']))
    a_ok = sum(1 for k in keys if a[k]['ok'])
    b_ok = sum(1 for k in keys if b[k]['ok'])
    gained = [k for k in keys if b[k]['ok'] and not a[k]['ok']]
    lost = [k for k in keys if a[k]['ok'] and not b[k]['ok']]
    spawn_mismatch = sum(1 for k in keys if a[k]['spawn'] != b[k]['spawn'])
    print(f"{'spawn' if kind else 'plain'}: common {len(keys)} identical start {same_start} "
          f"A exact {a_ok}  B exact {b_ok}  gained {len(gained)}  lost {len(lost)}  spawnflag-diff {spawn_mismatch}")
    if lost:
        print("  lost:", lost[:limit])
    if not kind:
        print("  gained sample:", gained[:limit])
only_a = sorted(set(a) - set(b))
only_b = sorted(set(b) - set(a))
print(f"only in A: {len(only_a)} (exact {sum(1 for k in only_a if a[k]['ok'])}); "
      f"only in B: {len(only_b)} (exact {sum(1 for k in only_b if b[k]['ok'])}, plain {sum(1 for k in only_b if not b[k]['spawn'])}, "
      f"plain exact {sum(1 for k in only_b if not b[k]['spawn'] and b[k]['ok'])})")
