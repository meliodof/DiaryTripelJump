# -*- coding: utf-8 -*-
"""Проверка прогноза на большом наборе реальных рядов и сравнение с обучаемыми моделями.

A. Прогноз следующего старта (TFRRS — истории NCAA; World Athletics — старты текущего сезона, возраст известен).
B. Холодный старт: прогноз лучшего результата следующего сезона по возрасту (World Athletics, прогрессия по сезонам).
Обучаемые модели оцениваются перекрёстной проверкой по спортсменам (спортсмен целиком либо в обучении, либо в проверке).
"""
import json, os, sys, glob, datetime, math, random
from collections import defaultdict
import numpy as np
from sklearn.linear_model import Ridge
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.model_selection import GroupKFold

WA_DIR, TF_DIR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
MON = {m: i for i, m in enumerate("JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split(), 1)}


def is_champ(name):
    """Главный старт сезона: чемпионаты, отборы, финалы."""
    n = name.lower()
    return any(k in n for k in ("championship", "ncaa", "trials", "final", "olympic", "world", "european",
                                "conference", "big ten", "big 12", "sec ", "acc ", "pac-12", "nationals"))


def wa_date(s):
    d, m, y = s.split()
    return datetime.date(int(y), MON[m], int(d))


# ------------------------------------------------------------------ загрузка
series = []      # (источник, id, [(ordinal, mark)], birth ordinal | None)
progress = []    # (id, birth_year, {season: best})
for fn in glob.glob(os.path.join(WA_DIR, "*.json")):
    d = json.load(open(fn, encoding="utf-8"))
    birth = None
    try:
        birth = wa_date(d["birthDate"]) if d.get("birthDate") else None
    except Exception:
        pass
    sb = {}
    for p in d.get("progression") or []:
        if p.get("discipline") != "Triple Jump":
            continue
        for r in p["results"]:
            try:
                season, mark = int(r["season"]), float(r["mark"])
            except Exception:
                continue
            if r.get("notLegal"):
                continue
            sb[season] = max(sb.get(season, 0), mark)
    if birth and len(sb) >= 2:
        progress.append((d["slug"], birth.year, sb))
    pts = []
    for e in (d.get("resultsByYear") or {}).get("resultsByEvent") or []:
        if e.get("discipline") != "Triple Jump":
            continue
        for r in e["results"]:
            try:
                if r.get("notLegal") and r.get("wind") is None:
                    continue
                w = float(r["wind"]) if r.get("wind") not in (None, "") else None
                comp = (r.get("competition") or "") + " " + (r.get("venue") or "")
                pts.append((wa_date(r["date"]).toordinal(), float(r["mark"]),
                            {"wind": w, "indoor": "(i)" in comp, "champ": is_champ(r.get("competition") or "")}))
            except Exception:
                pass
    if len(pts) >= 4:
        series.append(("WA", d["slug"], pts, birth.toordinal() if birth else None))
for fn in glob.glob(os.path.join(TF_DIR, "*.json")):
    d = json.load(open(fn, encoding="utf-8"))
    pts = [(datetime.date.fromisoformat(r["date"]).toordinal(), r["mark"],
            {"wind": r.get("wind"), "indoor": bool(r.get("indoor")), "champ": is_champ(r.get("meet", ""))})
           for r in d["tj"]]
    series.append(("TFRRS", d["id"], pts, None))


def daily_best(pts):
    best = {}
    for day, v, meta in pts:
        if day not in best or v > best[day][0]:
            best[day] = (v, meta)
    return sorted((day, v, meta) for day, (v, meta) in best.items())


# по литературе: попутный ветер 2 м/с даёт в тройном ≈ +0,10 м (Moinat и соавт., 2018) — ≈ 0,051 м на 1 м/с
WIND_PER_MS = 0.051
CORRECT_WIND = os.environ.get("CORRECT_WIND", "1") == "1"


def corrected(v, meta):
    if CORRECT_WIND and meta["wind"] is not None and not meta["indoor"]:
        return v - WIND_PER_MS * meta["wind"]
    return v


full = [(src, sid, daily_best(p), b) for src, sid, p, b in series]
series = [(src, sid, [(d, v) for d, v, _ in p], b) for src, sid, p, b in full]
metas = {(src, sid): {d: m for d, _, m in p} for src, sid, p, b in full}
corr_series = {(src, sid): [(d, corrected(v, m)) for d, v, m in p] for src, sid, p, b in full}
# убрать дубликаты спортсменов не требуется: TFRRS и WA — разные выборки (WA — текущий сезон)


# ------------------------------------------------------------------ текущая модель приложения
def ols(xs, ys):
    n = len(xs); xm = sum(xs) / n; ym = sum(ys) / n
    sxx = sum((x - xm) ** 2 for x in xs)
    if sxx <= 0: return None
    b = sum((x - xm) * (y - ym) for x, y in zip(xs, ys)) / sxx
    return ym - b * xm, b


def growth_by_age(age):
    if age is None: return 0.015
    if age < 18: return 0.04
    if age < 24: return 0.015
    return 0.0


def app_forecast(hist, target, age):
    """Порт Insights.forecast: hist — [(day, mark)] до даты прогноза."""
    window = [(d, v) for d, v in hist if target - 180 <= d < target][-30:]
    if not window:
        window = hist[-1:]
    if len(window) >= 6 and window[-1][0] - window[0][0] >= 21:
        first = window[0][0]
        f = ols([d - first for d, _ in window], [v for _, v in window])
        if f:
            return (window[-1][1] + f[0] + f[1] * (target - first)) / 2
    base_day, base = window[-1]
    return base * (1 + growth_by_age(age) * max(0, target - base_day) / 365)


# ------------------------------------------------------------------ признаки для обучаемых моделей
FEATURES = ["mean3", "best180", "median180", "ols", "app", "log_days", "n180", "age"]


def features(hist, target, age):
    last_d, last = hist[-1]
    w = [v for d, v in hist if target - 180 <= d < target] or [last]
    mean3 = sum(v for _, v in hist[-3:]) / len(hist[-3:])
    window = [(d, v) for d, v in hist if target - 180 <= d < target][-30:]
    o = last
    if len(window) >= 3 and window[-1][0] - window[0][0] > 0:
        first = window[0][0]
        f = ols([d - first for d, _ in window], [v for _, v in window])
        if f:
            o = f[0] + f[1] * (target - first)
            o = min(max(o, last - 1.5), last + 1.5)
    return [mean3 - last, max(w) - last, float(np.median(w)) - last, o - last,
            app_forecast(hist, target, age) - last,
            math.log1p(target - last_d), min(len(w), 30), age if age is not None else 21.0]


LIT_FEATURES = ["wind_mean3", "wind_ols", "tgt_indoor", "tgt_champ", "season_day", "last_indoor"]


def season_start(hist, target):
    """Начало текущего сезона: первый старт после перерыва больше 45 дней."""
    start = target
    for d, _ in reversed(hist):
        if start - d > 45:
            break
        start = d
    return start


def lit_features(hist_c, hist, target, meta_t, meta_last):
    """Признаки из тренерской литературы: результаты с поправкой на ветер, зал/стадион, главный старт, фаза сезона."""
    f = features(hist_c, target, None)
    last = hist[-1][1]
    shift = hist_c[-1][1] - last
    return [f[0] + shift, f[3] + shift, float(meta_t["indoor"]), float(meta_t["champ"]),
            min(target - season_start(hist, target), 200) / 30.0, float(meta_last["indoor"])]


rows = []  # (group, src, last, y, feats, app, age, n_hist, lit)
for src, sid, pts, birth in series:
    pc = corr_series[(src, sid)]; mt = metas[(src, sid)]
    for i in range(1, len(pts)):
        target, y = pts[i]
        hist = pts[:i]
        if target - hist[-1][0] > 400:   # перерыв больше года — другой сезон жизни, пропускаем
            continue
        age = (target - birth) / 365.25 if birth else None
        rows.append((f"{src}:{sid}", src, hist[-1][1], y, features(hist, target, age),
                     app_forecast(hist, target, age), age, len(hist),
                     lit_features(pc[:i], hist, target, mt[target], mt[hist[-1][0]])))

# естественный разброс результата у одного спортсмена внутри сезона (по Hopkins, 2005, у тройного ≈ 1,7 %)
cvs = []
for src_, sid_, pts_, b_ in series:
    seasons = defaultdict(list)
    for d_, v_ in pts_:
        seasons[datetime.date.fromordinal(d_).year].append(math.log(v_))
    for vs in seasons.values():
        if len(vs) >= 4:
            cvs.append(float(np.std(vs, ddof=1)))
EMP_CV = float(np.median(cvs)) if cvs else None

groups = np.array([r[0] for r in rows])
last = np.array([r[2] for r in rows]); y = np.array([r[3] for r in rows])
X = np.array([r[4] for r in rows]); app = np.array([r[5] for r in rows])
ages = np.array([r[6] if r[6] is not None else np.nan for r in rows])
nh = np.array([r[7] for r in rows]); src = np.array([r[1] for r in rows])
mean3 = last + X[:, 0]

pred_ridge = np.zeros(len(rows)); pred_gbm = np.zeros(len(rows))
gkf = GroupKFold(n_splits=5)
for tr, te in gkf.split(X, y, groups):
    rm = Ridge(alpha=1.0).fit(X[tr], (y - last)[tr])
    pred_ridge[te] = last[te] + rm.predict(X[te])
    gm = HistGradientBoostingRegressor(max_depth=3, max_iter=200, learning_rate=0.05,
                                       min_samples_leaf=30, random_state=0).fit(X[tr], (y - last)[tr])
    pred_gbm[te] = last[te] + gm.predict(X[te])
final_ridge = Ridge(alpha=1.0).fit(X, y - last)

# обучаемая модель с устойчивой к выбросам функцией потерь (метрика — средняя абсолютная ошибка)
from sklearn.linear_model import HuberRegressor
from sklearn.preprocessing import StandardScaler
pred_huber = np.zeros(len(rows))
for tr, te in gkf.split(X, y, groups):
    sc = StandardScaler().fit(X[tr])
    hm = HuberRegressor(epsilon=1.35, alpha=1e-3, max_iter=2000).fit(sc.transform(X[tr]), (y - last)[tr])
    pred_huber[te] = last[te] + hm.predict(sc.transform(X[te]))
sc_full = StandardScaler().fit(X)
huber_full = HuberRegressor(epsilon=1.35, alpha=1e-3, max_iter=2000).fit(sc_full.transform(X), y - last)
# коэффициенты в исходных единицах признаков — для переноса в приложение
huber_coef = huber_full.coef_ / sc_full.scale_
huber_intercept = huber_full.intercept_ - float((huber_full.coef_ * sc_full.mean_ / sc_full.scale_).sum())

# экспоненциальное сглаживание: вес alpha подбирается на обучающих спортсменах
def ses_all(alpha):
    p = np.zeros(len(rows)); k = 0
    for s_, sid_, pts_, birth_ in series:
        lvl = None
        for i_ in range(1, len(pts_)):
            v_prev = pts_[i_ - 1][1]
            lvl = v_prev if lvl is None else alpha * v_prev + (1 - alpha) * lvl
            target_, _ = pts_[i_]
            if target_ - pts_[i_ - 1][0] > 400:
                continue
            p[k] = lvl; k += 1
    return p
XL = np.hstack([X, np.array([r[8] for r in rows])])
pred_lit = np.zeros(len(rows))
for tr, te in gkf.split(XL, y, groups):
    sc = StandardScaler().fit(XL[tr])
    hm = HuberRegressor(epsilon=1.35, alpha=1e-3, max_iter=3000).fit(sc.transform(XL[tr]), (y - last)[tr])
    pred_lit[te] = last[te] + hm.predict(sc.transform(XL[te]))
sc_l = StandardScaler().fit(XL)
lit_full = HuberRegressor(epsilon=1.35, alpha=1e-3, max_iter=3000).fit(sc_l.transform(XL), y - last)
lit_coef = lit_full.coef_ / sc_l.scale_
lit_intercept = lit_full.intercept_ - float((lit_full.coef_ * sc_l.mean_ / sc_l.scale_).sum())

ses_grid = {a: ses_all(a) for a in (0.2, 0.3, 0.4, 0.5, 0.6, 0.8)}
pred_ses = np.zeros(len(rows)); ses_alphas = []
for tr, te in gkf.split(X, y, groups):
    best_a = min(ses_grid, key=lambda a: np.mean(np.abs(ses_grid[a][tr] - y[tr])))
    ses_alphas.append(best_a); pred_ses[te] = ses_grid[best_a][te]

out = {"A": {}, "B": {}}


def mae(p, mask=None):
    m = np.ones(len(y), bool) if mask is None else mask
    return float(np.mean(np.abs(p[m] - y[m]))), int(m.sum())


def boot_diff(p1, p2, mask, n=2000):
    """Доверительный интервал разности MAE (p1 − p2) бутстрепом по спортсменам."""
    g = groups[mask]; e1 = np.abs(p1[mask] - y[mask]); e2 = np.abs(p2[mask] - y[mask])
    ug = np.unique(g); idx = {k: np.where(g == k)[0] for k in ug}
    rnd = np.random.default_rng(1); diffs = []
    for _ in range(n):
        pick = rnd.choice(ug, len(ug))
        ii = np.concatenate([idx[k] for k in pick])
        diffs.append(e1[ii].mean() - e2[ii].mean())
    return float(np.percentile(diffs, 2.5)), float(np.percentile(diffs, 97.5))


subsets = {
    "все": np.ones(len(y), bool),
    "от 6 прошлых стартов": nh >= 6,
    "меньше 6 прошлых стартов": nh < 6,
    "TFRRS (NCAA)": src == "TFRRS",
    "World Athletics, сезон 2026": src == "WA",
    "моложе 20 лет": ages < 20,
    "20–23 года": (ages >= 20) & (ages < 24),
    "24 года и старше": ages >= 24,
}
for name, m in subsets.items():
    if m.sum() < 20:
        continue
    out["A"][name] = {
        "n": int(m.sum()), "athletes": int(len(np.unique(groups[m]))),
        "naive": mae(last, m)[0], "mean3": mae(mean3, m)[0], "app": mae(app, m)[0],
        "ridge": mae(pred_ridge, m)[0], "gbm": mae(pred_gbm, m)[0],
        "huber": mae(pred_huber, m)[0], "ses": mae(pred_ses, m)[0], "huber_lit": mae(pred_lit, m)[0],
        "lit_vs_huber_ci": boot_diff(pred_lit, pred_huber, m),
        "huber_vs_app_ci": boot_diff(pred_huber, app, m), "huber_vs_naive_ci": boot_diff(pred_huber, last, m),
        "huber_vs_mean3_ci": boot_diff(pred_huber, mean3, m), "app_vs_naive_ci": boot_diff(app, last, m),
    }
out["A"]["ridge_coef"] = dict(zip(FEATURES, map(float, final_ridge.coef_)))
out["A"]["ridge_intercept"] = float(final_ridge.intercept_)
out["A"]["huber_coef"] = dict(zip(FEATURES, map(float, huber_coef)))
out["A"]["huber_intercept"] = float(huber_intercept)
out["A"]["ses_alphas"] = ses_alphas
out["A"]["lit_coef"] = dict(zip(FEATURES + LIT_FEATURES, map(float, lit_coef)))
out["A"]["lit_intercept"] = float(lit_intercept)
out["A"]["empirical_cv"] = EMP_CV
out["A"]["share_champ"] = float(np.mean(XL[:, len(FEATURES) + 3]))
out["A"]["share_indoor"] = float(np.mean(XL[:, len(FEATURES) + 2]))
# ошибка на главных стартах: недооценивает ли модель «пик к чемпионату»
champ = XL[:, len(FEATURES) + 3] == 1
out["A"]["champ_bias"] = {"n": int(champ.sum()),
                          "mean_y_minus_last": float(np.mean((y - last)[champ])) if champ.any() else None,
                          "mean_y_minus_last_other": float(np.mean((y - last)[~champ]))}
out["A"]["athletes_total"] = int(len(np.unique(groups)))

# ------------------------------------------------------------------ B. сезоны и возраст
pairs = []  # (id, age, sb, next_sb)
for sid, by, sb in progress:
    for s in sorted(sb):
        if s + 1 in sb:
            age = s - by
            if 13 <= age <= 40:
                pairs.append((sid, age, sb[s], sb[s + 1]))
if len(pairs) < 20:
    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps(out, ensure_ascii=False, indent=1)); sys.exit(0)
pg = np.array([p[0] for p in pairs]); pa = np.array([p[1] for p in pairs])
ps = np.array([p[2] for p in pairs]); pn = np.array([p[3] for p in pairs])
rule = np.array([s * (1 + growth_by_age(a)) for a, s in zip(pa, ps)])
learned = np.zeros(len(pairs))


def age_curve(ages_, rel):
    """Средний относительный прирост по возрасту со сглаживанием по соседним возрастам."""
    cur = {}
    for a in range(13, 41):
        w = np.exp(-0.5 * ((ages_ - a) / 1.0) ** 2)
        cur[a] = float((w * rel).sum() / w.sum()) if w.sum() > 1e-6 else 0.0
    return cur


for tr, te in GroupKFold(n_splits=5).split(pa, pn, pg):
    cur = age_curve(pa[tr], (pn[tr] - ps[tr]) / ps[tr])
    learned[te] = ps[te] * (1 + np.array([cur[int(a)] for a in pa[te]]))
full_curve = age_curve(pa, (pn - ps) / ps)


def bmae(p, m): return float(np.mean(np.abs(p[m] - pn[m])))


for name, m in {"все": np.ones(len(pairs), bool), "до 18 лет": pa < 18, "18–23 года": (pa >= 18) & (pa < 24),
                "24 года и старше": pa >= 24}.items():
    if m.sum() < 10:
        continue
    out["B"][name] = {"n": int(m.sum()), "naive": bmae(ps, m), "rule": bmae(rule, m), "learned": bmae(learned, m)}
out["B"]["curve"] = {str(a): round(v, 4) for a, v in full_curve.items()}
out["B"]["athletes"] = int(len(set(pg)))
out["B"]["mean_growth_by_age"] = {str(a): round(float(np.mean((pn[pa == a] - ps[pa == a]) / ps[pa == a])), 4)
                                  for a in range(14, 36) if (pa == a).sum() >= 5}
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps(out, ensure_ascii=False, indent=1))
