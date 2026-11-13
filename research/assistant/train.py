# -*- coding: utf-8 -*-
"""Обучение моделей помощника «Спросить дневник» и выгрузка весов в приложение.

Признаки (так же в Kotlin, data/TextModel.kt): текст в нижнем регистре, «ё» → «е», всё кроме букв и цифр — пробел;
для каждого слова — само слово, буквенные n-граммы (2–4) слова в рамке «<слово>», пары соседних слов.
Каждый признак — номер FNV-1a(UTF-8) mod DIM («hashing trick»); вектор — единицы в найденных номерах,
нормированные на длину (L2). Модели: логистическая регрессия (вопросы — многоклассовая, заметки — по метке).

Запуск: python train.py <папка ресурсов приложения> <файл с итоговыми цифрами>
"""
import sys, os, re, json, math, random
import numpy as np
from scipy.sparse import csr_matrix
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GroupKFold
from sklearn.metrics import f1_score

sys.path.insert(0, os.path.dirname(__file__))
from dataset import INTENTS, HARD, EXERCISES, LABELS, NOTES, NOTES_HARD

RES, OUT = sys.argv[1], sys.argv[2]
TEST_RES = sys.argv[3] if len(sys.argv) > 3 else RES   # проверочные пары — в тестовые ресурсы, не в приложение
DIM = 4096
DECIMALS = 5
rng = random.Random(42)


# ------------------------------------------------------------------ признаки (features.py — как в приложении)
from features import normalize, vector as _vector


def vector(text):
    return _vector(text, DIM)


def matrix(texts):
    rows, cols, vals = [], [], []
    for r, t in enumerate(texts):
        for c, v in vector(t).items():
            rows.append(r); cols.append(c); vals.append(v)
    return csr_matrix((vals, (rows, cols)), shape=(len(texts), DIM))


def typo(text):
    """Одна случайная опечатка: пропуск, перестановка или повтор буквы."""
    s = list(text)
    letters = [i for i, ch in enumerate(s) if ch.isalpha()]
    if len(letters) < 4:
        return text
    i = rng.choice(letters[1:-1])
    op = rng.choice(["drop", "swap", "dup"])
    if op == "drop":
        del s[i]
    elif op == "swap" and i + 1 < len(s):
        s[i], s[i + 1] = s[i + 1], s[i]
    else:
        s.insert(i, s[i])
    return "".join(s)


# ------------------------------------------------------------------ вопросы
classes = list(INTENTS.keys())
texts, labels, groups = [], [], []
g = 0
for c, phrases in INTENTS.items():
    for p in phrases:
        variants = [p.replace("{ex}", e) for e in rng.sample(EXERCISES, 4)] if "{ex}" in p else [p]
        for v in variants:
            for t in (v, typo(v), "а " + v if rng.random() < 0.3 else v + "?"):
                texts.append(t); labels.append(classes.index(c)); groups.append(g)
        g += 1
y = np.array(labels); G = np.array(groups); X = matrix(texts)


def fit_intents(Xtr, ytr, C):
    return LogisticRegression(C=C, max_iter=3000).fit(Xtr, ytr)


def cv_intents(C):
    pred = np.zeros(len(y), int); proba = np.zeros((len(y), len(classes)))
    for tr, te in GroupKFold(n_splits=5).split(X, y, G):
        m = fit_intents(X[tr], y[tr], C)
        proba[te] = m.predict_proba(X[te]); pred[te] = proba[te].argmax(1)
    return pred, proba


best_C, best_acc = None, -1
for C in (1, 3, 10, 30, 100):
    pred, _ = cv_intents(C)
    acc = float((pred == y).mean())
    if acc > best_acc:
        best_C, best_acc = C, acc
pred_cv, proba_cv = cv_intents(best_C)
intent_model = fit_intents(X, y, best_C)

# порог уверенности: ниже — «не понял» (ответ-подсказка). Подбирается так, чтобы на кросс-проверке
# не терять больше 2 % верно понятых вопросов.
conf = proba_cv.max(1); correct = pred_cv == y
THRESH = 0.0
for t in np.arange(0.05, 0.6, 0.01):
    if (correct & (conf < t)).mean() <= 0.02:
        THRESH = float(round(t, 2))


# ------------------------------------------------------------------ базовая линия: ключевые слова
KEYWORDS = [
    ("FORECAST", ["прогноз", "сколько прыгну", "ожида"]),
    ("NEXT_START", ["когда старт", "ближайш", "до старта", "следующ"]),
    ("READY", ["готов", "подвод", "перед старт", "пик"]),
    ("RUNUP", ["разбег", "заступ", "недоступ", "отметк", "брусок"]),
    ("HEALTH", ["замет", "самочув", "болит", "травм", "жалоб", "сон"]),
    ("LOAD", ["нагрузк", "перегруз", "объем", "отдох", "перетрен"]),
    ("GOALS", ["цел"]),
    ("RECORDS", ["рекорд", "лучш"]),
    ("LINKS", ["влия", "связ", "помога", "зависит"]),
    ("WEEK", ["недел", "месяц", "итог", "сводк"]),
    ("ADVICE", ["совет", "посовет", "улучш", "рекоменд"]),
    ("HELP", ["умеешь", "привет", "помощ", "помоги", "здравств"]),
]


def keyword_intent(text):
    t = normalize(text)
    for c, keys in KEYWORDS:
        if any(k in t for k in keys):
            return c
    if any(e.split()[0][:5] in t for e in EXERCISES):
        return "EXERCISE"
    return "OTHER"


def predict_intent(text, model=None, weights=None):
    if weights is not None:
        W, b = weights
        x = vector(text); z = b.copy()
        for i, v in x.items():
            z += W[:, i] * v
        p = np.exp(z - z.max()); p /= p.sum()
    else:
        p = model.predict_proba(matrix([text]))[0]
    return classes[int(p.argmax())], float(p.max()), p


hard_texts = [(t, c) for c, ts in HARD.items() for t in ts]
hard_pred = [predict_intent(t, intent_model)[0] for t, _ in hard_texts]
hard_acc = float(np.mean([p == c for p, (_, c) in zip(hard_pred, hard_texts)]))
kw_hard = float(np.mean([keyword_intent(t) == c for t, c in hard_texts]))
kw_cv = float(np.mean([keyword_intent(t) == classes[l] for t, l in zip(texts, labels)]))
per_class_hard = {c: float(np.mean([p == c for p, (_, cc) in zip(hard_pred, hard_texts) if cc == c])) for c in classes}
mistakes = [(t, c, p) for (t, c), p in zip(hard_texts, hard_pred) if p != c]

# ------------------------------------------------------------------ заметки
ntexts, nlab, ngroups = [], [], []
for gi, (t, ls) in enumerate(NOTES):
    vec = [1 if l in ls.split(",") else 0 for l in LABELS]
    for v in (t, typo(t), t.capitalize() + "."):
        ntexts.append(v); nlab.append(vec); ngroups.append(gi)
NY = np.array(nlab); NX = matrix(ntexts); NG = np.array(ngroups)


def fit_notes(Xtr, Ytr, C=10):
    return [LogisticRegression(C=C, class_weight="balanced", max_iter=3000).fit(Xtr, Ytr[:, j]) for j in range(len(LABELS))]


NPRED = np.zeros_like(NY)
for tr, te in GroupKFold(n_splits=5).split(NX, NY, NG):
    ms = fit_notes(NX[tr], NY[tr])
    for j, m in enumerate(ms):
        NPRED[te, j] = (m.predict_proba(NX[te])[:, 1] >= 0.5).astype(int)
note_models = fit_notes(NX, NY)

PAIN_RE = re.compile(r"(?<![а-яё])(болит|болят|болело|болела|боль|болью|больно|травм[а-яё]*|тянет|ноет|растяж[а-яё]*|ушиб[а-яё]*|надрыв[а-яё]*)(?![а-яё])")
nh_texts = [t for t, _ in NOTES_HARD]
nh_true = np.array([[1 if l in ls.split(",") else 0 for l in LABELS] for _, ls in NOTES_HARD])
nh_pred = np.array([[int(m.predict_proba(matrix([t]))[0, 1] >= 0.5) for m in note_models] for t in nh_texts])
pain_j = LABELS.index("pain")
regex_pain_hard = np.array([1 if PAIN_RE.search(t.lower()) else 0 for t in nh_texts])
regex_pain_cv = np.array([1 if PAIN_RE.search(t.lower()) else 0 for t in ntexts])

# ------------------------------------------------------------------ выгрузка весов
os.makedirs(RES, exist_ok=True)
Wi = np.round(intent_model.coef_, DECIMALS); bi = np.round(intent_model.intercept_, DECIMALS)
Wn = np.round(np.vstack([m.coef_[0] for m in note_models]), DECIMALS)
bn = np.round(np.array([m.intercept_[0] for m in note_models]), DECIMALS)


def dump(path, kind, names, W, b, extra):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"type {kind}\ndim {DIM}\nclasses {' '.join(names)}\n")
        for k, v in extra.items():
            f.write(f"{k} {v}\n")
        f.write("bias " + " ".join(repr(float(x)) for x in b) + "\n")
        for i in range(DIM):
            col = W[:, i]
            if np.any(col != 0):
                f.write(f"w {i} " + " ".join(repr(float(x)) for x in col) + "\n")


dump(os.path.join(RES, "intent_model.txt"), "softmax", classes, Wi, bi, {"threshold": THRESH})
dump(os.path.join(RES, "notes_model.txt"), "sigmoid", LABELS, Wn, bn, {"threshold": 0.5})

# проверочные пары для теста совпадения Kotlin и Python (по выгруженным, округлённым весам)
os.makedirs(TEST_RES, exist_ok=True)
with open(os.path.join(TEST_RES, "assistant_vectors.txt"), "w", encoding="utf-8", newline="\n") as f:
    for t, _ in hard_texts:
        c, p, _ = predict_intent(t, weights=(Wi, bi))
        f.write(f"intent\t{t}\t{c}\t{p:.6f}\n")
    for t in nh_texts + ["Сделал больше подходов", "больше скорости", "Болит голень", "немного тянет заднюю"]:
        z = Wn @ np.array([vector(t).get(i, 0.0) for i in range(DIM)]) + bn
        probs = 1 / (1 + np.exp(-z))
        f.write(f"notes\t{t}\t" + " ".join(f"{x:.6f}" for x in probs) + "\n")

res = {
    "intents": {
        "classes": classes, "train_examples": len(texts), "base_phrases": int(G.max() + 1), "C": best_C,
        "cv_accuracy": round(best_acc, 4), "cv_macro_f1": round(float(f1_score(y, pred_cv, average="macro")), 4),
        "hard_n": len(hard_texts), "hard_accuracy": round(hard_acc, 4),
        "keywords_hard_accuracy": round(kw_hard, 4), "keywords_train_accuracy": round(kw_cv, 4),
        "threshold": THRESH, "per_class_hard": per_class_hard, "hard_mistakes": mistakes,
    },
    "notes": {
        "labels": LABELS, "train_examples": len(ntexts), "base_notes": len(NOTES),
        "cv_f1": {l: round(float(f1_score(NY[:, j], NPRED[:, j])), 4) for j, l in enumerate(LABELS)},
        "cv_macro_f1": round(float(f1_score(NY, NPRED, average="macro")), 4),
        "hard_n": len(NOTES_HARD),
        "hard_f1": {l: round(float(f1_score(nh_true[:, j], nh_pred[:, j], zero_division=0)), 4) for j, l in enumerate(LABELS)},
        "pain_regex_cv_f1": round(float(f1_score(NY[:, pain_j], regex_pain_cv)), 4),
        "pain_regex_hard_f1": round(float(f1_score(nh_true[:, pain_j], regex_pain_hard)), 4),
        "pain_model_hard_f1": round(float(f1_score(nh_true[:, pain_j], nh_pred[:, pain_j])), 4),
    },
    "model_size_kb": {f: round(os.path.getsize(os.path.join(RES, f)) / 1024, 1) for f in ("intent_model.txt", "notes_model.txt")},
}
json.dump(res, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps(res, ensure_ascii=True, indent=1)[:4000])
