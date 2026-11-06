"""Print the seeds of a bench report where the candidate (bot A) had a slow turn, a timeout or an error, one per line.

Usage: python -B -I docs/data/exp-2026-11-06-waves/slow_seeds.py REPORT.json
"""
import json
import sys


def main(path):
    d = json.load(open(path, encoding="utf-8"))
    seeds = set()
    for g in d["games"]:
        a = g["aSeat"]
        if g["slow"][a] or g["timeouts"][a] or g["errors"][a]:
            seeds.add(g["seed"])
    for s in sorted(seeds):
        print(s)


if __name__ == "__main__":
    main(sys.argv[1])
