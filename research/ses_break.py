# -*- coding: utf-8 -*-
"""Сглаживание с повышенным весом результата после перерыва (межсезонье): подбор на половине спортсменов."""
import json, glob, os, sys, datetime, itertools
import numpy as np

series = []
for fn in sorted(glob.glob(os.path.join(sys.argv[1], "*.json"))):
    d = json.load(open(fn, encoding="utf-8")); best = {}
    for r in d["tj"]:
        o = datetime.date.fromisoformat(r["date"]).toordinal(); best[o] = max(best.get(o, 0), r["mark"])
    series.append(sorted(best.items()))


def errs(pts, a, a_break, gap_days):
    lvl = pts[0][1]; e = []
    for i in range(1, len(pts)):
        gap = pts[i][0] - pts[i - 1][0]
        if gap > 400: lvl = pts[i][1]; continue
        e.append(abs(lvl - pts[i][1]))
        w = a_break if gap > gap_days else a
        lvl = w * pts[i][1] + (1 - w) * lvl
    return e


def score(ids, *p): return float(np.mean(sum((errs(series[i], *p) for i in ids), [])))


rng = np.random.default_rng(0); idx = rng.permutation(len(series)); A, B = idx[:75], idx[75:]
grid = [(a, ab, g) for a in (0.3, 0.4) for ab in (0.3, 0.5, 0.6, 0.7, 0.8) for g in (45, 60, 90)]
for name, (tr, te) in {"A->B": (A, B), "B->A": (B, A)}.items():
    best = min(grid, key=lambda p: score(tr, *p))
    print(name, best, "test", round(score(te, *best), 4), "plain0.3", round(score(te, 0.3, 0.3, 60), 4))
print("all plain", round(score(range(len(series)), 0.3, 0.3, 60), 4), "all break0.6/60", round(score(range(len(series)), 0.3, 0.6, 60), 4))
