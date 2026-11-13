# -*- coding: utf-8 -*-
"""Связь «тренировка → результат» на реальных журналах тренировок (GoldenCheetah OpenData, велоспорт).

Данные приводятся к виду дневника тройного:
  результат (как лучший тройной недели)        — лучшая 5-минутная мощность недели;
  «упражнения» (как присед, бег 30 м …)         — лучшая мощность 10 с, 1 мин, 20 мин за неделю;
  нагрузка (как время тренировок в дневнике)    — часы за неделю и TSS за неделю.
1. Модель связей приложения (Insights.influences) запускается без изменений на каждом спортсмене.
   Контроль: те же ряды со случайным циклическим сдвигом (связи быть не должно).
2. Прогноз результата следующей недели: без нагрузки и с признаками нагрузки («форма — усталость», Банистер).
"""
import json, os, sys, glob, math, datetime, random
from collections import defaultdict
import numpy as np
from sklearn.linear_model import HuberRegressor
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import GroupKFold

DATA, OUT = sys.argv[1], sys.argv[2]
PERMUTATIONS, MAX_LAG, MIN_PAIRS, MIN_R, FDR = 499, 6, 8, 0.3, 0.05


# ------------------------------------------------------------------ порт Insights.influences
def fit(xs, ys):
    n = len(xs); xm = sum(xs) / n; ym = sum(ys) / n
    sxx = sum((x - xm) ** 2 for x in xs)
    if n < 3 or sxx <= 0: return None
    b = sum((x - xm) * (y - ym) for x, y in zip(xs, ys)) / sxx
    return ym - b * xm, b


def detrend(ws, vs):
    f = fit([float(w) for w in ws], vs)
    if not f: m = sum(vs) / len(vs); return [v - m for v in vs]
    return [v - (f[0] + f[1] * w) for w, v in zip(ws, vs)]


def pearson(xs, ys):
    n = len(xs)
    if n < 3: return None
    xm = sum(xs) / n; ym = sum(ys) / n
    sxy = sum((x - xm) * (y - ym) for x, y in zip(xs, ys))
    sxx = sum((x - xm) ** 2 for x in xs); syy = sum((y - ym) ** 2 for y in ys)
    if sxx < 1e-12 or syy < 1e-12: return None
    return sxy / math.sqrt(sxx * syy)


def strongest(ex, tg):
    best = None
    for lag in range(0, MAX_LAG + 1):
        weeks = sorted(w for w in ex if w + lag in tg)
        if len(weeks) < MIN_PAIRS: continue
        r = pearson(detrend(weeks, [ex[w] for w in weeks]), detrend([w + lag for w in weeks], [tg[w + lag] for w in weeks]))
        if r is None: continue
        if best is None or abs(r) > abs(best[1]): best = (lag, r, len(weeks))
    return best


def _resid(x, v):
    x = x - x.mean(); b = (x * (v - v.mean())).sum() / (x * x).sum() if (x * x).sum() > 0 else 0.0
    return v - v.mean() - b * x


def perm_p(ex, tg, obs, seed):
    """То же, что Insights.permutationP, но векторно: значения упражнения перемешиваются по неделям."""
    keys = np.array(sorted(ex)); vals = np.array([ex[k] for k in keys])
    rng = np.random.default_rng(seed)
    P = np.array([rng.permutation(len(vals)) for _ in range(PERMUTATIONS)])   # перестановки × недели
    best = np.zeros(PERMUTATIONS)
    for lag in range(0, MAX_LAG + 1):
        mask = np.array([k + lag in tg for k in keys])
        if mask.sum() < MIN_PAIRS: continue
        wk = keys[mask].astype(float)
        yv = _resid(wk + lag, np.array([tg[k + lag] for k in keys[mask]]))
        X = vals[P][:, mask]                                                  # перемешанные значения в тех же неделях
        xc = wk - wk.mean()
        Xm = X - X.mean(1, keepdims=True)
        b = (Xm * xc).sum(1) / (xc * xc).sum()
        R = Xm - b[:, None] * xc
        num = (R * (yv - yv.mean())).sum(1)
        den = np.sqrt((R * R).sum(1) * ((yv - yv.mean()) ** 2).sum())
        r = np.where(den > 1e-12, num / np.where(den > 1e-12, den, 1), 0)
        best = np.maximum(best, np.abs(r))
    return float((np.sum(best >= abs(obs)) + 1) / (PERMUTATIONS + 1))


def influences(target, exercises, seed=1):
    """target: {неделя: значение}; exercises: {название: {неделя: значение}} → принятые связи после поправки БХ."""
    cand = []
    tested = [k for k, e in exercises.items() if len(e) >= MIN_PAIRS]
    for k in tested:
        s = strongest(exercises[k], target)
        if not s or abs(s[1]) < MIN_R: continue
        cand.append((k, s, perm_p(exercises[k], target, s[1], seed + hash(k) % 1000)))
    m = max(1, len(tested)); cand.sort(key=lambda c: c[2])
    acc = -1
    for i, c in enumerate(cand):
        if c[2] <= (i + 1) / m * FDR: acc = i
    return cand[:acc + 1]


# ------------------------------------------------------------------ загрузка
athletes = []
for fn in sorted(glob.glob(os.path.join(DATA, "*.json"))):
    d = json.load(open(fn, encoding="utf-8"))
    daily = defaultdict(lambda: {"tss": 0.0, "hours": 0.0})
    weekly = defaultdict(dict)
    for r in d["rides"]:
        if (r.get("sport") or "Bike") != "Bike" or not r.get("date"): continue
        try:
            day = datetime.datetime.strptime(r["date"][:10], "%Y/%m/%d").date()
        except Exception:
            try: day = datetime.date.fromisoformat(r["date"][:10])
            except Exception: continue
        o = day.toordinal(); w = o // 7
        secs = r.get("workout_time") or r.get("time_riding") or 0
        daily[o]["hours"] += secs / 3600; daily[o]["tss"] += r.get("coggan_tss") or 0
        for key, name in (("5m_critical_power", "p5m"), ("10s_critical_power", "p10s"),
                          ("1m_critical_power", "p1m"), ("20m_critical_power", "p20m")):
            v = r.get(key)
            if v and v > 30: weekly[w][name] = max(weekly[w].get(name, 0), v)
        weekly[w]["hours"] = weekly[w].get("hours", 0) + secs / 3600
        weekly[w]["tss"] = weekly[w].get("tss", 0) + (r.get("coggan_tss") or 0)
    perf = {w: v["p5m"] for w, v in weekly.items() if "p5m" in v}
    if len(perf) >= 30:
        athletes.append({"id": d["id"], "weekly": weekly, "daily": daily, "perf": perf})
print("спортсменов:", len(athletes))

# ------------------------------------------------------------------ 1. модель связей приложения
EXER = {"p10s": "мощность 10 с (как бег 30 м)", "p1m": "мощность 1 мин", "p20m": "мощность 20 мин",
        "hours": "часы за неделю", "tss": "нагрузка TSS за неделю"}
found = defaultdict(list); found_ctrl = defaultdict(int); tested_n = defaultdict(int)
rnd = random.Random(5)
for a in athletes:
    ex = {k: {w: v[k] for w, v in a["weekly"].items() if k in v and (k not in ("hours", "tss") or v[k] > 0)} for k in EXER}
    ex = {k: e for k, e in ex.items() if len(e) >= MIN_PAIRS}
    for k in ex: tested_n[k] += 1
    for k, s, p in influences(a["perf"], ex):
        found[k].append({"lag": s[0], "r": round(s[1], 2), "p": p})
    # контроль: каждое «упражнение» сдвигается по времени на случайные 20–60 недель (связь разрушается)
    ctrl = {}
    for k, e in ex.items():
        shift = rnd.randint(20, 60) * rnd.choice((-1, 1))
        ctrl[k] = {w + shift: v for w, v in e.items()}
    for k, s, p in influences(a["perf"], ctrl, seed=99):
        found_ctrl[k] += 1

links = {}
for k, name in EXER.items():
    n = tested_n[k]; f = found[k]
    links[name] = {
        "athletes_tested": n, "share_found": round(len(f) / n, 3) if n else None,
        "share_control": round(found_ctrl[k] / n, 3) if n else None,
        "positive": sum(1 for x in f if x["r"] > 0), "negative": sum(1 for x in f if x["r"] < 0),
        "lag_hist": {str(l): sum(1 for x in f if x["lag"] == l) for l in range(0, MAX_LAG + 1)},
        "median_abs_r": float(np.median([abs(x["r"]) for x in f])) if f else None,
    }

# ------------------------------------------------------------------ 2. прогноз с нагрузкой («форма — усталость»)
def ewma(daily, start, end, tau):
    v = 0.0; out = {}
    k = 1 - math.exp(-1 / tau)
    for o in range(start, end + 1):
        v += k * (daily.get(o, {}).get("_load", 0.0) - v); out[o] = v
    return out


rows = []
for a in athletes:
    for load_key in ("tss", "hours"):
        pass
    days = sorted(a["daily"])
    if not days: continue
    for o in days:
        a["daily"][o]["_load"] = a["daily"][o]["tss"]
    ctl_t = ewma(a["daily"], days[0], days[-1] + 7, 42); atl_t = ewma(a["daily"], days[0], days[-1] + 7, 7)
    for o in days:
        a["daily"][o]["_load"] = a["daily"][o]["hours"]
    ctl_h = ewma(a["daily"], days[0], days[-1] + 7, 42); atl_h = ewma(a["daily"], days[0], days[-1] + 7, 7)
    weeks = sorted(a["perf"])
    for i in range(3, len(weeks)):
        w = weeks[i]
        if w - weeks[i - 1] > 4: continue
        prev = [a["perf"][x] for x in weeks[:i]]
        last = prev[-1]; mean3 = sum(prev[-3:]) / 3; best8 = max(a["perf"][x] for x in weeks[:i] if x >= w - 8)
        o = w * 7 - 1   # конец прошлой недели
        if o not in ctl_t: continue
        ctlt, atlt = ctl_t[o], atl_t[o]; ctlh, atlh = ctl_h[o], atl_h[o]
        base = [mean3 / last - 1, best8 / last - 1, math.log1p(w - weeks[i - 1])]
        tss_f = [math.log1p(ctlt), math.log1p(atlt), (ctlt - atlt) / (ctlt + 1)]
        hrs_f = [math.log1p(ctlh * 7), math.log1p(atlh * 7), (ctlh - atlh) / (ctlh + 0.1)]
        rows.append((a["id"], last, a["perf"][w], base, tss_f, hrs_f))

g = np.array([r[0] for r in rows]); last = np.array([r[1] for r in rows]); y = np.array([r[2] for r in rows])
B = np.array([r[3] for r in rows]); T = np.array([r[4] for r in rows]); H = np.array([r[5] for r in rows])
target = y / last - 1


def cv_pred(X):
    p = np.zeros(len(y))
    for tr, te in GroupKFold(n_splits=5).split(X, y, g):
        sc = StandardScaler().fit(X[tr])
        m = HuberRegressor(alpha=1e-3, max_iter=3000).fit(sc.transform(X[tr]), target[tr])
        p[te] = last[te] * (1 + m.predict(sc.transform(X[te])))
    return p


def mape(p): return float(np.mean(np.abs(p - y) / y) * 100)


def boot(p1, p2, n=2000):
    ug = np.unique(g); idx = {k: np.where(g == k)[0] for k in ug}; rr = np.random.default_rng(2); ds = []
    e1 = np.abs(p1 - y) / y; e2 = np.abs(p2 - y) / y
    for _ in range(n):
        ii = np.concatenate([idx[k] for k in rr.choice(ug, len(ug))]); ds.append((e1[ii].mean() - e2[ii].mean()) * 100)
    return [round(float(np.percentile(ds, 2.5)), 3), round(float(np.percentile(ds, 97.5)), 3)]


p_base = cv_pred(B); p_tss = cv_pred(np.hstack([B, T])); p_hrs = cv_pred(np.hstack([B, H]))
forecast = {
    "weeks": int(len(y)), "athletes": int(len(np.unique(g))),
    "naive_%": mape(last), "mean3_%": mape(last * (1 + B[:, 0])),
    "learned_no_load_%": mape(p_base), "learned_plus_tss_%": mape(p_tss), "learned_plus_hours_%": mape(p_hrs),
    "tss_vs_base_ci": boot(p_tss, p_base), "hours_vs_base_ci": boot(p_hrs, p_base),
}
res = {"athletes": len(athletes), "links": links, "forecast": forecast}
json.dump(res, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps(res, ensure_ascii=False, indent=1))
