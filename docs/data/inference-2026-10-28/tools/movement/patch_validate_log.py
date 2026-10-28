import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
old = """                    if (RESYNC) { res.resynced.merge("gold", 1, Integer::sum); s.gold[p] = Integer.parseInt(hud[p]); }"""
new = """                    if (RESYNC) { res.resynced.merge("gold", 1, Integer::sum); if (Boolean.getBoolean("logResync")) System.out.println("RESYNC g" + g.gameId + " t" + tn + " gold" + p + " replay " + hud[p] + " sim " + s.gold[p]); s.gold[p] = Integer.parseInt(hud[p]); }"""
assert old in s
s = s.replace(old, new)
old2 = """                    if (RESYNC) { res.resynced.merge("queenHp", 1, Integer::sum); s.hp[p] = Integer.parseInt(hud[2 + p]); if (s.score[p] >= 0) s.score[p] = s.hp[p]; }"""
new2 = """                    if (RESYNC) { res.resynced.merge("queenHp", 1, Integer::sum); if (Boolean.getBoolean("logResync")) System.out.println("RESYNC g" + g.gameId + " t" + tn + " queenHp" + p + " replay " + hud[2 + p] + " sim " + s.hp[p]); s.hp[p] = Integer.parseInt(hud[2 + p]); if (s.score[p] >= 0) s.score[p] = s.hp[p]; }"""
assert old2 in s
s = s.replace(old2, new2)
open(p, 'w', encoding='utf-8').write(s)
