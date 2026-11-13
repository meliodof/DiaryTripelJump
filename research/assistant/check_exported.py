# -*- coding: utf-8 -*-
"""Проверка выгруженных моделей на фразах из тестов приложения (тот же расчёт, что в Kotlin)."""
import sys, os, math
sys.path.insert(0, os.path.dirname(__file__))
from features import vector  # noqa: E402


def load(path):
    m = {"w": {}}
    for line in open(path, encoding="utf-8"):
        p = line.split()
        if not p: continue
        if p[0] == "classes": m["classes"] = p[1:]
        elif p[0] == "threshold": m["threshold"] = float(p[1])
        elif p[0] == "type": m["type"] = p[1]
        elif p[0] == "dim": m["dim"] = int(p[1])
        elif p[0] == "bias": m["bias"] = [float(x) for x in p[1:]]
        elif p[0] == "w": m["w"][int(p[1])] = [float(x) for x in p[2:]]
    return m


def predict(m, text):
    z = list(m["bias"])
    for i, v in vector(text, m["dim"]).items():
        if i in m["w"]:
            z = [a + b * v for a, b in zip(z, m["w"][i])]
    if m["type"] == "sigmoid":
        return [1 / (1 + math.exp(-x)) for x in z]
    mx = max(z); e = [math.exp(x - mx) for x in z]; s = sum(e)
    return [x / s for x in e]


res = sys.argv[1]
im = load(os.path.join(res, "intent_model.txt")); nm = load(os.path.join(res, "notes_model.txt"))
for q in ["итоги недели", "сколько я прыгну на старте", "как мой присед", "какая погода завтра", "когда ближайший старт",
          "что в заметках", "что с бегом 30", "Какой прогноз на старт?", "Итоги недели", "Как мой присед?",
          "Что влияет на тройной?", "Готов ли я к старту?", "Не перегружаюсь ли я?", "Что в заметках?", "Успею ли к цели?"]:
    p = predict(im, q); b = max(range(len(p)), key=lambda i: p[i])
    print(f"{q!r:40} -> {im['classes'][b]:10} {p[b]:.3f}{'  (ниже порога)' if p[b] < im['threshold'] else ''}")
for n in ["колено ноет", "колено чет побаливает", "неприятно в стопе", "все хорошо", "не выспался совсем"]:
    p = predict(nm, n)
    print(f"{n!r:30}", [l for l, x in zip(nm["classes"], p) if x >= nm["threshold"]])
