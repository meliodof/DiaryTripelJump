# -*- coding: utf-8 -*-
"""Признаки текста — так же, как в приложении (data/TextModel.kt, объект TextFeatures)."""
import re, math


def normalize(text):
    t = text.lower().replace("ё", "е")
    return re.sub(r"[^a-z0-9а-я]+", " ", t).strip()


def fnv(s):
    """FNV-1a, 32 бита, по байтам UTF-8."""
    h = 2166136261
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * 16777619) & 0xFFFFFFFF
    return h


def feature_keys(text):
    """Слова, буквенные n-граммы (2–4) слова в рамке «<слово>», пары соседних слов."""
    words = normalize(text).split()
    keys = []
    for w in words:
        keys.append("w:" + w)
        framed = "<" + w + ">"
        for n in (2, 3, 4):
            for i in range(len(framed) - n + 1):
                keys.append("c:" + framed[i:i + n])
    for a, b in zip(words, words[1:]):
        keys.append("b:" + a + " " + b)
    return keys


def vector(text, dim):
    """Единицы в номерах признаков (FNV mod dim), нормированные на длину вектора."""
    idx = sorted({fnv(k) % dim for k in feature_keys(text)})
    if not idx:
        return {}
    v = 1.0 / math.sqrt(len(idx))
    return {i: v for i in idx}
