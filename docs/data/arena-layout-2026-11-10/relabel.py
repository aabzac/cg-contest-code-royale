"""The relabel of 2026-11-10 (gitignored replays/arena, bytes unchanged): the upload of agent 6868868, registered as
v14 upload 1 after a paste of v12's artifact, becomes v12 upload 3.

`tools/arena.py follow v14`, before it looked for a new label's file by its play, named the main checkout's
build/submit/Player.java for the paste. That file held v12's bytes (sha256 3208b011...): the agent played v12's code
(cgAttribute SAME 90 of 90) under the label v14. One artifact has one label, so the upload is v12's next one.

Usage, each step from the root of the checkout it changes:
  python -B -I docs/data/arena-layout-2026-11-10/relabel.py records --source <checkout where the tool wrote>
      In the branch. Takes the registry and the three manifests the tool wrote in the source checkout, gives the
      upload its label (formerLabel keeps the old one, as for route -> v04) and its directory in the manifests.
      Refuses unless the source registry is this checkout's plus that one upload.
  python -B -I docs/data/arena-layout-2026-11-10/relabel.py plan --source <checkout with replays/> --out <record.json>
      The inventory of the upload's directory (sha256 of every file) and the moves, written to the record.
  python -B -I docs/data/arena-layout-2026-11-10/relabel.py move --record <record.json>
      In the checkout with replays/, after the merge. Checks every file against the record, renames the directory,
      checks again and removes the empty directories of the old label. A collision stops it before any move.
Contents digest, as in arena-layout-2026-11-04: SHA-256 of UTF-8 lines sorted by file name, file name + space +
sha256 + LF; it stays the same, since the move keeps the names.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tools"))
import arena  # noqa: E402
import arena_paths  # noqa: E402

AGENT, BEFORE = 6868868, 6868474
FORMER, LABEL = ("v14", 1), "v12"
REGISTRY = "docs/data/arena-uploads.json"
LIVE_BEFORE = "docs/data/arena-6868474-games-2026-11-09.json"
MANIFESTS = {"docs/data/arena-6868868-games-2026-11-10.json": 33, "docs/data/arena-6868868-placement-2026-11-10.json": 1}
OLD = "replays/arena/v14/gold/upload-01"
REPLACED = "2026-11-10T02:23:11+02:00 by %s upload %d"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def digest(files):
    lines = sorted("%s %s\n" % (Path(p).name, h) for p, h in files.items())
    return hashlib.sha256("".join(lines).encode("utf-8")).hexdigest()


def inventory(directory):
    return {p.relative_to(directory).as_posix(): sha(p) for p in sorted(directory.rglob("*")) if p.is_file()}


def records(source):
    ours = json.loads(Path(REGISTRY).read_text(encoding="utf-8"))
    theirs = json.loads((source / REGISTRY).read_text(encoding="utf-8"))
    rest = lambda r: {k: v for k, v in r.items() if k != "uploads"}
    if rest(ours) != rest(theirs) or len(theirs["uploads"]) != len(ours["uploads"]) + 1:
        raise SystemExit("the source registry is not this one plus one upload")
    for a, b in zip(ours["uploads"], theirs["uploads"]):
        same = {k: v for k, v in b.items() if k != "replaced"} == a and "replaced" not in a if a["agent"] == BEFORE else a == b
        if not same:
            raise SystemExit("the source registry differs at agent %d" % a["agent"])
    entry = theirs["uploads"][-1]
    before = next(u for u in theirs["uploads"] if u["agent"] == BEFORE)
    if (entry["label"], entry["upload"], entry["agent"]) != FORMER + (AGENT,) or before["replaced"] != REPLACED % FORMER:
        raise SystemExit("not the upload this script is about: %s" % arena.name(entry))
    first = arena.owner(entry["artifact"]["sha256"], ours["uploads"])
    if first is None or first["label"] != LABEL:
        raise SystemExit("the artifact is not %s's" % LABEL)
    number = 1 + max(u["upload"] for u in ours["uploads"] if u["label"] == LABEL)
    entry["label"], entry["upload"] = LABEL, number
    entry["formerLabel"] = (
        "v14, upload 1 (2026-11-10, for some hours: a paste of v12's artifact by mistake; `tools/arena.py follow v14`, "
        "before it looked for a new label's file by its play, named the main checkout's build/submit/Player.java, "
        "which held v12's bytes, and registered the paste under v14; docs/data/arena-layout-2026-11-10/record.json)")
    before["replaced"] = REPLACED % (LABEL, number)
    new = arena_paths.upload_dir(entry).as_posix()
    arena.write_registry(REGISTRY, theirs)
    Path(LIVE_BEFORE).write_bytes((source / LIVE_BEFORE).read_bytes())
    for name, count in MANIFESTS.items():
        text = (source / name).read_bytes().decode("utf-8")
        if text.count(OLD) != count or "v14" in text.replace(OLD, ""):
            raise SystemExit("unexpected paths in %s" % name)
        Path(name).write_bytes(text.replace(OLD, new).encode("utf-8"))
    print("%s upload %d (agent %d): %s -> %s; %d + %d paths rewritten in the manifests" % (
        (LABEL, number, AGENT, OLD, new) + tuple(MANIFESTS.values())))


def plan(source, out):
    entry = next(u for u in json.loads(Path(REGISTRY).read_text(encoding="utf-8"))["uploads"] if u["agent"] == AGENT)
    new = arena_paths.upload_dir(entry).as_posix()
    files = inventory(source / OLD)
    if not files or (source / new).exists():
        raise SystemExit("nothing at %s, or %s exists" % (OLD, new))
    games = json.loads(Path(next(iter(MANIFESTS))).read_text(encoding="utf-8"))
    missing = [g["gameId"] for g in games["games"] if files.get("games/%d.json" % g["gameId"]) != g["sha256"]]
    if missing:
        raise SystemExit("the games manifest and the directory disagree: %s" % missing[:5])
    record = {
        "date": "2026-11-10",
        "what": "The upload of agent %d was registered as v14 upload 1 after a paste of v12's artifact by mistake (sha256 "
                "3208b011..., build 58d513ae; cgAttribute SAME 90 of 90). It is v12's upload %d: the registry entry, the "
                "two manifests and the directory under replays/arena take that label. Every file's SHA-256 is checked "
                "before and after the move; no network calls. Script: docs/data/arena-layout-2026-11-10/relabel.py."
                % (AGENT, entry["upload"]),
        "agent": AGENT,
        "formerLabel": {"label": FORMER[0], "upload": FORMER[1]},
        "label": {"label": entry["label"], "upload": entry["upload"]},
        "registry": ["the entry of agent %d: label %s -> %s, upload %d -> %d, formerLabel added" % (
            AGENT, FORMER[0], entry["label"], FORMER[1], entry["upload"]),
            "the entry of agent %d (v13 upload 2): replaced \"%s\" -> \"%s\"" % (
                BEFORE, REPLACED % FORMER, REPLACED % (entry["label"], entry["upload"]))],
        "manifests": {name: "%d occurrences of %s -> %s" % (count, OLD, new) for name, count in MANIFESTS.items()},
        "old": OLD,
        "new": new,
        "files": len(files),
        "bytes": sum((source / OLD / f).stat().st_size for f in files),
        "digestDefinition": "SHA-256 of UTF-8 lines sorted by filename: filename + space + sha256 + LF.",
        "contentInventorySha256": digest(files),
        "keptAsWritten": "checks/arena.log, checks/cgAttribute.log and checks/arena-compare.json were written while the "
                         "upload carried the label v14 and name it so (arena-compare.json holds a P1 verdict of the label "
                         "v14 against v13, which is about v12's code); their bytes are unchanged.",
        "moves": [{"old": "%s/%s" % (OLD, f), "new": "%s/%s" % (new, f), "sha256": h} for f, h in files.items()],
    }
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(record, f, indent=1)
        f.write("\n")
    print("%s: %d files, %d bytes, digest %s; %d games agree with the manifest" % (
        out, record["files"], record["bytes"], record["contentInventorySha256"][:16], len(games["games"])))


def move(record_path):
    record = json.loads(Path(record_path).read_text(encoding="utf-8"))
    old, new = Path(record["old"]), Path(record["new"])
    expected = {m["old"][len(record["old"]) + 1:]: m["sha256"] for m in record["moves"]}
    if new.exists():
        raise SystemExit("%s exists: nothing moved" % new)
    if inventory(old) != expected:
        raise SystemExit("%s does not hold the record's files: nothing moved" % old)
    new.parent.mkdir(parents=True, exist_ok=True)
    old.rename(new)
    after = inventory(new)
    if after != expected or digest(after) != record["contentInventorySha256"]:
        raise SystemExit("the files at %s differ from the record after the move" % new)
    for empty in (old.parent, old.parent.parent):
        empty.rmdir()  # the old label has no other upload; a directory with anything left stops here
    print("moved %s -> %s: %d files, digest %s, the same before and after" % (old, new, len(after), digest(after)[:16]))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="step", required=True)
    sub.add_parser("records").add_argument("--source", required=True, type=Path)
    p = sub.add_parser("plan")
    p.add_argument("--source", required=True, type=Path)
    p.add_argument("--out", required=True)
    sub.add_parser("move").add_argument("--record", required=True)
    args = parser.parse_args()
    if args.step == "records":
        records(args.source)
    elif args.step == "plan":
        plan(args.source, args.out)
    else:
        move(args.record)


if __name__ == "__main__":
    main()
