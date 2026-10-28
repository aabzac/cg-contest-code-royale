"""Patch verify-movement-alt Rules.train with unit-cap variants selected by system properties:
-DcapTraining=true  count creeps of barracks still training
-DcapSkipEmpty=true no cap check for an empty TRAIN
-DcapFirst=true     cap check before the gold check
"""
import sys

path = sys.argv[1]
src = open(path, 'rb').read().decode('utf-8')
old_start = src.index('        int sum = 0;\r\n        for (int site : sites) sum += Const.CREEP_COST[s.barrType[site]];')
old_end = src.index('        s.gold[p] -= sum;')
new = '''        int sum = 0;
        for (int site : sites) sum += Const.CREEP_COST[s.barrType[site]];
        int units = 0;
        for (int i = 0; i < s.nCreeps; i++) if (s.cOwner[i] == p) units++;
        if (Boolean.getBoolean("capTraining"))
            for (int j = 0; j < s.nSites; j++) if (s.stType[j] == Const.BARRACKS && s.stOwner[j] == p && s.barrTraining[j]) units += Const.CREEP_COUNT[s.barrType[j]];
        for (int site : sites) units += Const.CREEP_COUNT[s.barrType[site]];
        boolean check = !(Boolean.getBoolean("capSkipEmpty") && sites.length == 0);
        String capMsg = check && units > Const.MAX_UNITS ? "Training too many creeps (" + units + " total units requested; " + Const.MAX_UNITS + " maximum)" : null;
        if (Boolean.getBoolean("capFirst") && capMsg != null) return capMsg;
        if (sum > s.gold[p]) return "Training too many creeps (" + sum + " total gold requested)";
        if (capMsg != null) return capMsg;
'''.replace('\n', '\r\n')
src = src[:old_start] + new + src[old_end:]
open(path, 'wb').write(src.encode('utf-8'))
print("patched", path)
