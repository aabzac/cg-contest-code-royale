"""Mutation check: revert one live rule at a time in a scratch copy, run tests and strict cgValidate.

Usage: python -I mutate.py <repo> <workdir>
"""
import glob
import os
import re
import shutil
import subprocess
import sys

REPO, WORK = sys.argv[1], sys.argv[2]
RULES = 'src/main/java/cr/core/Rules.java'
PHYS = 'src/main/java/cr/core/Physics.java'
CONST = 'src/main/java/cr/core/Const.java'

MUTATIONS = [
    ('none', []),
    ('repo-entity-order', [
        (PHYS,
         "        for (int p = 0; p < 2; p++) {\n"
         "            for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) addCreep(s, i);\n"
         "            add(s.qx[p], s.qy[p], Const.QUEEN_RADIUS, Const.QUEEN_MASS, true);\n"
         "        }\n",
         "        for (int i = 0; i < s.nCreeps; i++) addCreep(s, i);\n"
         "        for (int p = 0; p < 2; p++) add(s.qx[p], s.qy[p], Const.QUEEN_RADIUS, Const.QUEEN_MASS, true);\n"),
        (PHYS,
         "        for (int p = 0; p < 2; p++) {\n"
         "            for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) { s.cx[i] = ex[k]; s.cy[i] = ey[k]; k++; }\n"
         "            s.qx[p] = ex[k]; s.qy[p] = ey[k]; k++;\n"
         "        }\n",
         "        for (int i = 0; i < s.nCreeps; i++, k++) { s.cx[i] = ex[k]; s.cy[i] = ey[k]; }\n"
         "        for (int p = 0; p < 2; p++, k++) { s.qx[p] = ex[k]; s.qy[p] = ey[k]; }\n"),
    ]),
    ('site-order-structures', [
        (RULES,
         "        for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.TOWER) actStructure(s, i);\n"
         "        for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.BARRACKS) actStructure(s, i);\n"
         "        for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.MINE) actStructure(s, i);\n",
         "        for (int i = 0; i < s.nSites; i++) actStructure(s, i);\n"),
    ]),
    ('repo-spawn-offset', [
        (RULES,
         "setCreep(s, k, s.sx[site] + SPAWN_CORNER[iter % 4][0], s.sy[site] + SPAWN_CORNER[iter % 4][1]);",
         "setCreep(s, k, s.sx[site] + (owner == 1 ? -iter : iter), s.sy[site] + (owner == 1 ? -iter : iter));"),
    ]),
    ('no-creep-limit', [
        (CONST, "public static int CREEP_LIMIT = 30;", "public static int CREEP_LIMIT = 999;"),
    ]),
    ('turn-base-1', [
        (RULES, "skip = s.turn % 2;", "skip = (s.turn + 1) % 2;"),
    ]),
    ('dead-removed-after-towers', [
        (RULES,
         "        for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.TOWER) actStructure(s, i);\n",
         "        for (int i = 0; i < s.nSites; i++) if (s.stType[i] == Const.TOWER) actStructure(s, i);\n"
         "        s.removeDeadCreeps();\n"),
    ]),
    ('last-warning-only', [
        (RULES, 'lastWarning[p] = lastWarning[p] == null ? w : lastWarning[p] + "\\n" + w;', 'lastWarning[p] = w;'),
    ]),
    ('tower-damage-from-edge', [
        (RULES,
         "double diff = range - Math.sqrt(Physics.distSq(s.cx[t], s.cy[t], s.sx[i], s.sy[i]));",
         "double diff = range - (Math.sqrt(Physics.distSq(s.cx[t], s.cy[t], s.sx[i], s.sy[i])) - s.sr[i]);"),
        (RULES,
         "double diff = range - Math.sqrt(Physics.distSq(s.qx[enemy], s.qy[enemy], s.sx[i], s.sy[i]));",
         "double diff = range - (Math.sqrt(Physics.distSq(s.qx[enemy], s.qy[enemy], s.sx[i], s.sy[i])) - s.sr[i]);"),
    ]),
]


def copy_repo():
    if os.path.exists(WORK):
        shutil.rmtree(WORK)
    os.makedirs(WORK)
    for name in ['src', 'gradle']:
        shutil.copytree(os.path.join(REPO, name), os.path.join(WORK, name))
    for name in ['build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat']:
        if os.path.exists(os.path.join(REPO, name)):
            shutil.copy2(os.path.join(REPO, name), os.path.join(WORK, name))


def read(p):
    with open(os.path.join(WORK, p), 'rb') as f:
        return f.read().decode('utf-8')


def write(p, s):
    with open(os.path.join(WORK, p), 'wb') as f:
        f.write(s.encode('utf-8'))


def failing_tests():
    out = []
    for x in glob.glob(os.path.join(WORK, 'build/test-results/test/*.xml')):
        s = open(x, encoding='utf-8').read()
        for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', s, re.S):
            if m.group(3) and ('<failure' in m.group(3) or '<error' in m.group(3)):
                out.append(m.group(1))
    return sorted(out)


def gradle(*args):
    cmd = [os.path.join(WORK, 'gradlew.bat')] if os.name == 'nt' else ['./gradlew']
    r = subprocess.run(cmd + list(args), cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0 and 'test' not in args and 'cgValidate' not in args:
        print('gradle failed:', r.stderr[-500:])
    return r


copy_repo()
pristine = {p: read(p) for p in [RULES, PHYS, CONST]}
replays = os.path.join(REPO, 'replays', 'cg').replace('\\', '/')
for name, edits in MUTATIONS:
    for p, s in pristine.items():
        write(p, s)
    for p, old, new in edits:
        s = read(p)
        n = s.replace('\r\n', '\n').count(old)
        if n != 1:
            print(f'{name}: pattern found {n} times in {p}; skipped')
            break
        crlf = '\r\n' in s
        s = s.replace('\r\n', '\n').replace(old, new)
        write(p, s.replace('\n', '\r\n') if crlf else s)
    else:
        shutil.rmtree(os.path.join(WORK, 'build', 'test-results'), ignore_errors=True)
        t = gradle('-q', 'test', '--rerun-tasks')
        fails = failing_tests()
        compile_error = 'error:' in (t.stdout + t.stderr) and not fails
        print(f'== {name}')
        print('   failing tests:', 'COMPILE ERROR' if compile_error else (fails or 'none'))
        for corpus in ['cg', 'cg2']:
            v = gradle('-q', 'cgValidate', '-Preplays=' + replays[:-2] + corpus, '-Pquiet=true')
            for l in v.stdout.splitlines():
                if l.startswith(('games=', 'unit match', 'warnings')):
                    print(f'   {corpus}: {l}')
        sys.stdout.flush()
