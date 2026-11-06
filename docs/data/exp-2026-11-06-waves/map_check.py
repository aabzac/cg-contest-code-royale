"""Check the per-map repeats of maprepeat.sh (2026-11-06): every map-<series>-<seed>.json must hold the same two games
(winner, turns, final score) as the screen's first run of that series, with no slow turn, timeout or error of the
candidate (bot A).

Usage: python -B -I docs/data/exp-2026-11-06-waves/map_check.py <raw report directory>
Exits with 0 when every repeat is clean and identical, 1 otherwise.
"""
import glob
import json
import os
import re
import sys


def games(path):
    d = json.load(open(path, encoding="utf-8"))
    return {(g["seed"], g["aSeat"]): g for g in d["games"]}


def main(d):
    first = {}
    ok, n = True, 0
    for path in sorted(glob.glob(os.path.join(d, "map-*.json"))):
        m = re.match(r"map-(.+)-rerun-(\d+)\.json$", os.path.basename(path))
        series, seed = m.group(1), int(m.group(2))
        if series not in first:
            first[series] = games(os.path.join(d, series + ".json"))
        rep = games(path)
        for seat in (0, 1):
            g, h = rep[(seed, seat)], first[series][(seed, seat)]
            same = (g["winner"], g["turns"], list(g["score"])) == (h["winner"], h["turns"], list(h["score"]))
            clean = not (g["slow"][g["aSeat"]] or g["timeouts"][g["aSeat"]] or g["errors"][g["aSeat"]])
            n += 1
            if not (same and clean):
                ok = False
            print("%-12s seed %d seat %d: %s, %s" % (series, seed, seat, "same game" if same else "DIFFERENT game",
                                                   "clean" if clean else "candidate failure %s" % {k: g[k] for k in ("slow", "timeouts", "errors")}))
    print("%d games repeated: %s" % (n, "all identical and clean" if ok else "NOT all identical and clean"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
