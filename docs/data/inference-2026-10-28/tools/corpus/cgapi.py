"""Paced client for the public CodinGame services (POST JSON array bodies).

Every call waits at least MIN_GAP seconds after the previous one and retries politely on
errors (exponential backoff). Returns the raw response bytes; callers store them unchanged.
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "https://www.codingame.com/services/"
MIN_GAP = 0.8
_last = [0.0]
LOG = []


def call_raw(service, body, retries=5, timeout=60):
    data = json.dumps(body).encode("utf-8")
    delay = 2.0
    for attempt in range(retries + 1):
        wait = _last[0] + MIN_GAP - time.time()
        if wait > 0:
            time.sleep(wait)
        _last[0] = time.time()
        req = urllib.request.Request(BASE + service, data=data, method="POST",
                                     headers={"Content-Type": "application/json;charset=UTF-8",
                                              "Accept": "application/json",
                                              "User-Agent": "cr-replay-corpus/1.0 (research; paced)"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
                LOG.append({"t": time.time(), "service": service, "body": body, "status": resp.status, "bytes": len(raw), "attempt": attempt})
                return raw
        except urllib.error.HTTPError as e:
            msg = "HTTP %d" % e.code
            try:
                msg += " " + e.read()[:300].decode("utf-8", "replace")
            except Exception:
                pass
        except Exception as e:  # network errors, timeouts
            msg = repr(e)
        LOG.append({"t": time.time(), "service": service, "body": body, "error": msg, "attempt": attempt})
        print("  retry %d %s %s: %s" % (attempt, service, json.dumps(body)[:80], msg), file=sys.stderr)
        if attempt < retries:
            time.sleep(delay)
            delay = min(delay * 2, 60)
    raise RuntimeError("giving up on %s %s" % (service, body))


def call(service, body, **kw):
    return json.loads(call_raw(service, body, **kw).decode("utf-8"))
