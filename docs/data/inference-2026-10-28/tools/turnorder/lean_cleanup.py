"""Small style/doc cleanups on the lean tree, preserving each file's line endings."""
import sys

ROOT = sys.argv[1]


def edit(rel, pairs):
    p = ROOT + '/' + rel
    raw = open(p, 'rb').read()
    crlf = b'\r\n' in raw
    s = raw.decode('utf-8').replace('\r\n', '\n')
    for old, new in pairs:
        if s.count(old) != 1:
            raise SystemExit('anchor not unique/missing in %s: %r' % (rel, old[:80]))
        s = s.replace(old, new)
    if crlf:
        s = s.replace('\n', '\r\n')
    open(p, 'wb').write(s.encode('utf-8'))


edit('src/main/java/cr/tools/CgValidate.java', [
    ("""    }


    /**
     * Replay events decoded""", """    }

    /**
     * Replay events decoded"""),
    ("""        return res;
    }
    /**
     * Classifies a turn""", """        return res;
    }

    /**
     * Classifies a turn"""),
    ("""            if (alive + req + inTraining > 30 || sum.contains("$" + p + ": [WARNING] Training too many"))""",
     """            if (alive + req + inTraining > Math.max(Const.CREEP_LIMIT, 0) || sum.contains("$" + p + ": [WARNING] Training too many"))"""),
])

edit('src/main/java/cr/core/Const.java', [
    ("""     * 15 such warnings in 98 replays (2026-10-28), N = creeps at turn start + requested in all 9
     * reached by resync; creeps still in training do not count (7 accepted requests over 30 with
     * them). Resync gold corrections 17 -> 10, the rest being mines destroyed only in one side.
     * The order against the gold check is unobserved (both only reject). 0 disables.""",
     """     * 15 such warnings in 98 replays (2026-10-28); N = creeps at turn start + requested in all 9
     * reached by resync; creeps still in training do not count (7 accepted requests exceed 30 only
     * with them). Resync with resyncCount: gold corrections 17 -> 10 (the other 10 are mines torn
     * down on one side only, after creep position errors). The order against the gold check is
     * unobserved (both only reject). 0 disables."""),
])
print('ok')
