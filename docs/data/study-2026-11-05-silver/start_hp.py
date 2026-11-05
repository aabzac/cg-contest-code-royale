"""The queens' starting HP of local maps against CG maps (study 2026-11-05).

MapGen.generate(seed, level >= 4) draws the starting HP first: new java.util.Random(seed).nextInt(16) * 5 + 25. For a
power-of-two bound nextInt takes the top bits of the first LCG output, and consecutive seeds move those bits only every
few hundred seeds, so a block of consecutive seeds plays nearly one starting HP. CG's seeds are spread out.
Usage (from the repository root): python -B -I docs/data/study-2026-11-05-silver/start_hp.py
"""
import collections
import glob
import json
import os
import statistics as st

MULT, ADD, MASK = 0x5DEECE66D, 0xB, (1 << 48) - 1
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))


def start_hp(seed):
    """MapGen's first draw for level >= 4: sample(rnd, 5, 20) * 5 = (nextInt(16) + 5) * 5."""
    s = (seed ^ MULT) & MASK
    s = (s * MULT + ADD) & MASK
    bits31 = s >> (48 - 31)
    return ((16 * bits31) >> 31) * 5 + 25


def describe(label, seeds):
    hp = [start_hp(s) for s in seeds]
    c = collections.Counter(hp)
    top = ", ".join("%d: %d" % kv for kv in sorted(c.items(), key=lambda kv: -kv[1])[:4])
    print("%-44s %5d maps, distinct starting HP %2d, median %5.1f, most common %s" % (label, len(hp), len(c), st.median(hp), top))


def main():
    print("Local blocks (consecutive seeds)")
    describe("development 10000-10499", range(10000, 10500))
    describe("development 11000-11199 (field calibration)", range(11000, 11200))
    describe("development 110000-110299 (legScan)", range(110000, 110300))
    describe("this study 150000-150499", range(150000, 150500))
    for k in range(10):
        describe("screen block %d (%d-%d)" % (k, 200000 + 2000 * k, 200000 + 2000 * k + 1999), range(200000 + 2000 * k, 200000 + 2000 * (k + 1)))
    for k in range(8):
        describe("evaluation block %d (%d-%d)" % (k, 400000 + 2000 * k, 400000 + 2000 * k + 1999), range(400000 + 2000 * k, 400000 + 2000 * (k + 1)))
    seeds = []
    for path in sorted(glob.glob(os.path.join(REPO, "docs", "data", "arena-*-games-*.json"))):
        for g in json.load(open(path, encoding="utf-8")).get("games", []):
            if g.get("seed") not in (None, ""):
                seeds.append(int(g["seed"]))
    describe("CG games in docs/data manifests", sorted(set(seeds)))


if __name__ == "__main__":
    main()
