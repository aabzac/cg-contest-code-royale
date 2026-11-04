"""Arena layout migration of 2026-11-04 (gitignored replays/arena, bytes unchanged): one layout for every upload.

Usage: python -B -I docs/data/arena-layout-2026-11-04/migrate.py --root <checkout with replays/> --registry
       <arena-uploads.json> --out <record.json> [--apply]
Without --apply it prints the plan and writes nothing. With it, it moves the files and writes the record.

1. Files at the top of an upload directory (logs, reports and the one-off watch scripts written after the
   2026-10-31 layout, which put .log files in checks/) move to its checks/ directory.
2. Snapshots kept under replays/arena/unknown/<league>/agent-<id> move to that agent's registered upload.
3. The label route becomes v04 (the registry's label for agents 6836411 and 6836589).
Every file's sha256 is taken before and checked after; a name collision stops the run before any move.
Contents digest per upload, as in arena-layout-2026-10-31.json: SHA-256 of UTF-8 lines sorted by file name,
file name + space + sha256 + LF; it stays the same, since moves keep the names.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

RENAMES = {"route": "v04"}
SUBDIRS = ("games", "snapshots", "checks")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def digest(files):
    lines = sorted("%s %s\n" % (Path(p).name, h) for p, h in files.items())
    return hashlib.sha256("".join(lines).encode("utf-8")).hexdigest()


def inventory(directory):
    return {p.relative_to(directory).as_posix(): sha(p) for p in sorted(directory.rglob("*")) if p.is_file()}


def plan(root, registry):
    arena = root / "replays" / "arena"
    moves = []  # (old, new) relative to root
    uploads = sorted(p for p in arena.glob("*/*/upload-*") if p.is_dir() and p.parts[-3] != "unknown")
    for up in uploads:
        label = up.parts[-3]
        target = up.parent.parent.parent / RENAMES.get(label, label) / up.parts[-2] / up.name
        for f in sorted(up.rglob("*")):
            if not f.is_file():
                continue
            rel = f.relative_to(up)
            new = target / (rel if rel.parts[0] in SUBDIRS and len(rel.parts) > 1 else Path("checks") / rel.name)
            if new != f:
                moves.append((f, new))
    entries = registry["uploads"] + registry.get("legacyUploads", [])
    for agent_dir in sorted(arena.glob("unknown/*/agent-*")):
        league, agent = agent_dir.parts[-2], int(agent_dir.name[6:])
        entry = next((u for u in entries if u["agent"] == agent and u["league"] == league), None)
        if entry is None:
            raise SystemExit("no registered upload for %s" % agent_dir)
        target = arena / entry["label"] / league / ("upload-%02d" % entry["upload"])
        for f in sorted(agent_dir.rglob("*")):
            if f.is_file():
                rel = f.relative_to(agent_dir)
                if rel.parts[0] != "snapshots":
                    raise SystemExit("only snapshots are adopted, not %s" % f)
                moves.append((f, target / rel))
    news = [n for _, n in moves]
    if len(set(news)) != len(news):
        raise SystemExit("two files would land on one path")
    for old, new in moves:
        if new.exists() and new not in [o for o, _ in moves]:
            raise SystemExit("%s exists already" % new)
    return uploads, moves


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", required=True)
    parser.add_argument("--registry", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    registry = json.loads(Path(args.registry).read_text(encoding="utf-8"))
    arena = root / "replays" / "arena"
    before = inventory(arena)
    uploads, moves = plan(root, registry)
    hashes = {old: before[old.relative_to(arena).as_posix()] for old, _ in moves}
    for old, new in moves:
        print("%s -> %s" % (old.relative_to(root).as_posix(), new.relative_to(root).as_posix()))
    print("%d files to move, %d files under replays/arena" % (len(moves), len(before)))
    if not args.apply:
        return 0
    revision = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    dirty = subprocess.run(["git", "-C", str(root), "status", "--porcelain"], capture_output=True, text=True).stdout.strip()
    per_upload = []
    for up in uploads + sorted(arena.glob("unknown/*/agent-*")):
        files = {k: v for k, v in before.items() if k.startswith(up.relative_to(arena).as_posix() + "/")}
        per_upload.append({"old": up.relative_to(root).as_posix(), "files": len(files),
                           "bytes": sum((arena / k).stat().st_size for k in files), "contentInventorySha256": digest(files)})
    for old, new in moves:
        new.parent.mkdir(parents=True, exist_ok=True)
        old.replace(new)
    for d in sorted((p for p in arena.rglob("*") if p.is_dir()), key=lambda p: len(p.parts), reverse=True):
        if not any(d.iterdir()):
            d.rmdir()
    after = inventory(arena)
    if sorted(after.values()) != sorted(before.values()):
        raise SystemExit("the contents changed")
    for old, new in moves:
        if after.get(new.relative_to(arena).as_posix()) != hashes[old]:
            raise SystemExit("%s did not arrive intact" % new)
    targets = {}
    for up in per_upload:
        parts = up["old"].split("/")
        if parts[2] == "unknown":
            continue
        new = "/".join(parts[:2] + [RENAMES.get(parts[2], parts[2])] + parts[3:])
        files = {k: v for k, v in after.items() if k.startswith(new[len("replays/arena/"):] + "/")}
        up.update(new=new, filesAfter=len(files), contentInventorySha256After=digest(files))
        targets[new] = up
    record = {
        "date": "2026-11-04", "revisionBefore": revision, "workingTreeBefore": "clean" if not dirty else dirty,
        "what": "Arena layout migration: every upload keeps games/, snapshots/ and checks/ only; the label route is "
                "renamed v04; snapshots left under unknown/ join their registered upload. Every file's SHA-256 checked "
                "before and after; no network calls. Script: docs/data/arena-layout-2026-11-04/migrate.py.",
        "layout": "replays/arena/<label>/<league>/upload-NN/{games,snapshots,checks}",
        "mapping": "A file at the top of an upload directory -> checks/<filename>; unknown/<league>/agent-<id>/snapshots/"
                   "<path> -> the agent's registered upload snapshots/<path>; replays/arena/route -> replays/arena/v04.",
        "labelRenames": [{"old": "route", "new": "v04", "agents": [6836411, 6836589]}],
        "digestDefinition": "SHA-256 of UTF-8 lines sorted by filename: filename + space + sha256 + LF.",
        "filesUnderReplaysArena": len(after),
        "uploads": per_upload,
        "moves": [{"old": o.relative_to(root).as_posix(), "new": n.relative_to(root).as_posix(), "sha256": hashes[o]}
                  for o, n in moves],
    }
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(record, f, indent=1)
        f.write("\n")
    print("moved %d files; record %s" % (len(moves), out.as_posix()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
