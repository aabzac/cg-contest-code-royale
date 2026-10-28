"""Gives every file of a tree the line-ending style of the same file in the base tree (new files: LF).
Usage: fix_eol.py <base dir> <tree dir>"""
import os, sys

base, tree = sys.argv[1], sys.argv[2]
for root, _, files in os.walk(os.path.join(tree, 'src')):
    for name in files:
        p = os.path.join(root, name)
        rel = os.path.relpath(p, tree)
        data = open(p, 'rb').read()
        b = os.path.join(base, rel)
        crlf = os.path.exists(b) and b'\r\n' in open(b, 'rb').read()
        norm = data.replace(b'\r\n', b'\n')
        out = norm.replace(b'\n', b'\r\n') if crlf else norm
        if out != data:
            open(p, 'wb').write(out)
            print('fixed', rel, 'CRLF' if crlf else 'LF')
for rel in ['build.gradle']:
    p, b = os.path.join(tree, rel), os.path.join(base, rel)
    data = open(p, 'rb').read()
    crlf = b'\r\n' in open(b, 'rb').read()
    norm = data.replace(b'\r\n', b'\n')
    out = norm.replace(b'\n', b'\r\n') if crlf else norm
    if out != data:
        open(p, 'wb').write(out)
        print('fixed', rel, 'CRLF' if crlf else 'LF')
