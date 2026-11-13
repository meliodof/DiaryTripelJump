# -*- coding: utf-8 -*-
"""Сглаживание уровня (α) и затухающий тренд Холта (β, φ) по дням: подбор на половине спортсменов, проверка на другой."""
import json, glob, os, sys, datetime, itertools
import numpy as np

TF = sys.argv[1]
series = []
for fn in sorted(glob.glob(os.path.join(TF, "*.json"))):
    d = json.load(open(fn, encoding="utf-8"))
    best = {}
    for r in d["tj"]:
        o = datetime.date.fromisoformat(r["date"]).toordinal()
        best[o] = max(best.get(o, 0), r["mark"])
    series.append(sorted(best.items()))


def run(pts, a, b, phi):
    """Уровень и тренд в метрах за день; прогноз = уровень + тренд·(1−φ^h)/(1−φ)·…, h — дни до старта (не больше 60)."""
    lvl, tr, prev = pts[0][1], 0.0, pts[0][0]
    errs, naive = [], []
    for i in range(1, len(pts)):
        day, v = pts[i]
        gap = day - prev
        if gap > 400:
            lvl, tr, prev = v, 0.0, day; continue
        h = min(gap, 60)
        damp = sum(phi ** k for k in range(1, h + 1)) if b > 0 else 0
        f = lvl + tr * damp
        errs.append(abs(f - v)); naive.append(abs(pts[i - 1][1] - v))
        # обновление
        new_lvl = a * v + (1 - a) * (lvl + tr * damp)
        if b > 0:
            tr = b * (new_lvl - lvl) / max(gap, 1) + (1 - b) * tr * phi ** h
        lvl, prev = new_lvl, day
    return errs, naive


rng = np.random.default_rng(0)
idx = rng.permutation(len(series)); A, B = idx[: len(idx) // 2], idx[len(idx) // 2:]


def score(ids, a, b, phi):
    e = []
    for i in ids: e += run(series[i], a, b, phi)[0]
    return float(np.mean(e))


grid = list(itertools.product([0.2, 0.3, 0.4], [0.0, 0.05, 0.1, 0.2], [0.9, 0.97, 0.99]))
res = {}
for part, (tr, te) in {"A→B": (A, B), "B→A": (B, A)}.items():
    best = min(grid, key=lambda g: score(tr, *g))
    ses = min([g for g in grid if g[1] == 0.0], key=lambda g: score(tr, *g))
    nv = float(np.mean(sum((run(series[i], 0.3, 0, 0.9)[1] for i in te), [])))
    res[part] = {"best": best, "holt_test": score(te, *best), "ses": ses, "ses_test": score(te, *ses), "naive_test": nv}
print(json.dumps(res, indent=1))
