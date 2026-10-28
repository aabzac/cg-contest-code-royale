"""Choose games from saved battle lists and download them unchanged into <root>/<league>/<gameId>.json.

Usage:
  python -I -X utf8 fetch_games.py probe <lbdir> <battlesdir> <agents.json> <root> <existing_root> <selection.json>
      one game per selected agent (newest eligible), recorded in selection.json
  python -I -X utf8 fetch_games.py focus <lbdir> <battlesdir> <root> <existing_root> <selection.json> <league> <agentId> <n> <reason>
      up to n more eligible games of one agent (its battle list is fetched if missing); optional
      last argument overrides MAX_PER_OPP

Eligible: done, gameId >= MIN_ID, not in <existing_root> (the fixed corpus) or already chosen,
opponent not known to be in another league, at most MAX_PER_OPP games per opponent pseudo per
league. Downloads are resumable by game id; a response without frames is recorded as a failure.
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cgapi  # noqa: E402

MIN_ID = 880_000_000
MAX_PER_OPP = 2


def league_map(lbdir):
    m = {}
    for lg in ("bronze", "silver", "gold", "legend"):
        with open(os.path.join(lbdir, "leaderboard-league-%s.json" % lg), encoding="utf-8") as f:
            for u in json.load(f)["users"]:
                m[u["agentId"]] = lg
    return m


def existing_ids(root):
    return {int(os.path.basename(p)[:-5]) for p in glob.glob(os.path.join(root, "**", "*.json"), recursive=True)
            if os.path.basename(p)[:-5].isdigit()}


def load_sel(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return {"games": [], "failures": [], "calls": []}


def save_sel(path, sel):
    sel["calls"] = sel.get("calls", []) + cgapi.LOG
    cgapi.LOG.clear()
    with open(path, "w", encoding="utf-8") as f:
        json.dump(sel, f, indent=1)


def battles(bdir, agent):
    path = os.path.join(bdir, "battles-%d.json" % agent)
    if not os.path.exists(path):
        raw = cgapi.call_raw("gamesPlayersRanking/findLastBattlesByAgentId", [agent, None])
        with open(path, "wb") as f:
            f.write(raw)
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def download(root, league, gid):
    """Returns (ok, info). Stores the raw bytes unchanged."""
    d = os.path.join(root, league)
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, "%d.json" % gid)
    if os.path.exists(path):
        return True, "cached"
    raw = cgapi.call_raw("gameResult/findByGameId", [gid, None])
    try:
        g = json.loads(raw.decode("utf-8"))
    except Exception as e:
        return False, "bad json: %r" % e
    if not isinstance(g, dict) or not g.get("frames") or g.get("gameId") != gid or len(g.get("agents", [])) != 2:
        return False, "incomplete response (%d bytes): %s" % (len(raw), raw[:200])
    with open(path + ".part", "wb") as f:
        f.write(raw)
    os.replace(path + ".part", path)
    return True, "%d bytes" % len(raw)


def eligible(bl, me, lg, lmap, taken, opp_count):
    for b in bl:
        gid = b["gameId"]
        if not b.get("done") or gid < MIN_ID or gid in taken:
            continue
        opps = [p for p in b["players"] if p["playerAgentId"] != me]
        if len(opps) != 1 or len(b["players"]) != 2:
            continue
        o = opps[0]
        olg = lmap.get(o["playerAgentId"])
        if olg is not None and olg != lg:
            continue
        if opp_count.get((lg, o["nickname"]), 0) >= MAX_PER_OPP:
            continue
        yield b, o, olg


def take(sel, root, lg, b, o, olg, src, reason, taken, opp_count):
    gid = b["gameId"]
    ok, info = download(root, lg, gid)
    rec = {"gameId": gid, "league": lg, "sourceAgent": src, "reason": reason,
           "opponent": {"pseudo": o["nickname"], "agentId": o["playerAgentId"], "leagueNow": olg or "unlisted"},
           "battlePlayers": [{"nickname": p["nickname"], "playerAgentId": p["playerAgentId"], "position": p["position"]} for p in b["players"]]}
    taken.add(gid)
    if ok:
        sel["games"].append(rec)
        opp_count[(lg, o["nickname"])] = opp_count.get((lg, o["nickname"]), 0) + 1
    else:
        rec["error"] = info
        sel["failures"].append(rec)
    print("%-6s %d %-8s vs %-20s %s" % (lg, gid, reason, o["nickname"], info))
    return ok


def counts(sel):
    oc = {}
    for g in sel["games"]:
        k = (g["league"], g["opponent"]["pseudo"])
        oc[k] = oc.get(k, 0) + 1
    return oc


def main():
    mode = sys.argv[1]
    if mode == "probe":
        lbdir, bdir, agents_json, root, ex_root, sel_path = sys.argv[2:8]
        lmap = league_map(lbdir)
        sel = load_sel(sel_path)
        taken = existing_ids(ex_root) | {g["gameId"] for g in sel["games"]} | {g["gameId"] for g in sel["failures"]}
        opp_count = counts(sel)
        done_src = {(g["sourceAgent"]["agentId"]) for g in sel["games"]}
        with open(agents_json, encoding="utf-8") as f:
            agents = json.load(f)["agents"]
        for a in agents:
            if a["agentId"] in done_src:
                continue
            bl = battles(bdir, a["agentId"])
            for b, o, olg in eligible(bl, a["agentId"], a["league"], lmap, taken, opp_count):
                src = {"agentId": a["agentId"], "pseudo": a["pseudo"], "localRank": a["localRank"], "why": a["why"]}
                if take(sel, root, a["league"], b, o, olg, src, "probe", taken, opp_count):
                    break
            save_sel(sel_path, sel)
    elif mode == "focus":
        lbdir, bdir, root, ex_root, sel_path, lg, agent, n, reason = sys.argv[2:11]
        agent, n = int(agent), int(n)
        global MAX_PER_OPP
        if len(sys.argv) > 11:
            MAX_PER_OPP = int(sys.argv[11])
        lmap = league_map(lbdir)
        sel = load_sel(sel_path)
        taken = existing_ids(ex_root) | {g["gameId"] for g in sel["games"]} | {g["gameId"] for g in sel["failures"]}
        opp_count = counts(sel)
        bl = battles(bdir, agent)
        me = None
        for b in bl:
            for p in b["players"]:
                if p["playerAgentId"] == agent:
                    me = p["nickname"]
        got = 0
        for b, o, olg in eligible(bl, agent, lg, lmap, taken, opp_count):
            if got >= n:
                break
            src = {"agentId": agent, "pseudo": me, "localRank": None, "why": "focus"}
            if take(sel, root, lg, b, o, olg, src, reason, taken, opp_count):
                got += 1
        save_sel(sel_path, sel)
        print("focus %s %d: %d games" % (lg, agent, got))
    elif mode == "ids":
        # ids <lbdir> <battlesdir> <root> <existing_root> <selection.json> <league> <agentId> <reason> <gameId...>
        lbdir, bdir, root, ex_root, sel_path, lg, agent, reason = sys.argv[2:10]
        agent = int(agent)
        lmap = league_map(lbdir)
        sel = load_sel(sel_path)
        taken = existing_ids(ex_root) | {g["gameId"] for g in sel["games"]} | {g["gameId"] for g in sel["failures"]}
        opp_count = counts(sel)
        bl = {b["gameId"]: b for b in battles(bdir, agent)}
        for gid in map(int, sys.argv[10:]):
            b = bl.get(gid)
            if b is None or gid in taken or not b.get("done"):
                print("skip", gid, "not in list / taken / not done")
                continue
            me = next(p for p in b["players"] if p["playerAgentId"] == agent)
            o = next(p for p in b["players"] if p["playerAgentId"] != agent)
            olg = lmap.get(o["playerAgentId"])
            if olg is not None and olg != lg:
                print("skip", gid, "opponent in", olg)
                continue
            src = {"agentId": agent, "pseudo": me["nickname"], "localRank": None, "why": "focus"}
            take(sel, root, lg, b, o, olg, src, reason, taken, opp_count)
        save_sel(sel_path, sel)


main()
