"""Resolve arena storage from the upload registry, never from a caller-selected raw directory."""
import json
from pathlib import Path
import re

DEFAULT_REGISTRY = "docs/data/arena-uploads.json"
LEAGUES = ("wood3", "wood2", "wood1", "bronze", "silver", "gold", "legend")


def upload_dir(entry):
    label, league, number = entry.get("label"), entry.get("league"), entry.get("upload")
    if not isinstance(label, str) or not re.fullmatch(r"[a-z][a-z0-9_-]*", label) or label == "unknown":
        raise ValueError("invalid upload label: %r" % label)
    if league not in LEAGUES:
        raise ValueError("invalid upload league: %r" % league)
    if type(number) is not int or number < 1:
        raise ValueError("upload number must be a positive integer: %r" % number)
    return Path("replays/arena") / label / league / ("upload-%02d" % number)


def registry_entries(registry_path=DEFAULT_REGISTRY):
    with open(registry_path, encoding="utf-8") as f:
        registry = json.load(f)
    entries = registry["uploads"] + registry.get("legacyUploads", [])
    agents, destinations = set(), set()
    for entry in entries:
        agent = entry.get("agent")
        if type(agent) is not int or agent < 1 or agent in agents:
            raise ValueError("invalid or duplicate registry agent: %r" % agent)
        destination = upload_dir(entry)
        if destination in destinations:
            raise ValueError("two agents share an upload directory: %s" % destination)
        agents.add(agent)
        destinations.add(destination)
    return entries


def upload_for(agent, registry_path=DEFAULT_REGISTRY):
    entry = next((u for u in registry_entries(registry_path) if u["agent"] == agent), None)
    if entry is None:
        raise ValueError("agent %d is not registered in %s; add its pending upload with label, league, "
                         "upload, agent, artifact and games manifest before downloading" % (agent, registry_path))
    return entry


def unknown_dir(agent, league):
    if type(agent) is not int or agent < 1 or league not in LEAGUES:
        raise ValueError("invalid unknown agent/league: %r / %r" % (agent, league))
    return Path("replays/arena/unknown") / league / ("agent-%d" % agent)


def decode_battles(raw, agent):
    """Only validated lists may be published with the auto-discovered battles prefix."""
    rows = json.loads(raw)
    if not isinstance(rows, list):
        raise ValueError("battle response is not a list")
    for row in rows:
        if not isinstance(row, dict) or type(row.get("gameId")) is not int or row["gameId"] < 1:
            raise ValueError("invalid battle row: %r" % row)
        players = row.get("players")
        if not isinstance(players, list) or not all(isinstance(p, dict) for p in players) or not any(
                p.get("playerAgentId") == agent for p in players):
            raise ValueError("battle %d does not contain agent %d" % (row["gameId"], agent))
    return rows


def write_raw(path, raw):
    """Preserve response bytes; refuse a collision instead of overwriting evidence."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != raw:
            raise ValueError("different response already stored at %s" % path)
        return
    temporary = path.with_suffix(path.suffix + ".part")
    temporary.write_bytes(raw)
    temporary.replace(path)
