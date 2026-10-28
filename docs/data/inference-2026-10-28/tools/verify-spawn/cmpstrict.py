"""Compare per-game first strict mismatch turns of two CgValidate strict outputs.
Usage: python -I cmpstrict.py <base.strict.txt> <patched.strict.txt> <replays-root>
Games without a MISMATCH line are fully consistent (turn = inf)."""
import os
import re
import sys

base_f, new_f, root = sys.argv[1], sys.argv[2], sys.argv[3]
games = []
for d, _, fs in os.walk(root):
    for f in fs:
        if re.fullmatch(r"\d+\.json", f):
            games.append(f[:-5])
games.sort()


def parse(path):
    r = {}
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = re.match(r"MISMATCH .*?(\d+)\.json \(\d+\): (.*)", line)
            if not m:
                continue
            gid, msg = m.group(1), m.group(2).strip()
            t = re.match(r"t(\d+) ", msg)
            r[gid] = (int(t.group(1)) if t else 10**6, msg[:110])
    return r


b, n = parse(base_f), parse(new_f)
earlier, later, same = [], 0, 0
for g in games:
    tb = b.get(g, (10**9, "OK"))[0]
    tn = n.get(g, (10**9, "OK"))[0]
    if tn < tb:
        earlier.append((g, b.get(g), n.get(g)))
    elif tn > tb:
        later += 1
    else:
        same += 1
print(f"games={len(games)} patched later={later} same={same} earlier={len(earlier)}")
for e in earlier:
    print("EARLIER", e)
print("patched remaining mismatches:")
for g in games:
    if g in n:
        print(" ", g, "base:", b.get(g), "patched:", n[g])
