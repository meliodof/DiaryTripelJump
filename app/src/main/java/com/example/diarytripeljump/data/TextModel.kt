package com.example.diarytripeljump.data

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Свои текстовые модели дневника: логистическая регрессия на буквенных n-граммах.
 * Обучаются заранее в Python (research/assistant/train.py) на собственных примерах, веса лежат в ресурсах
 * приложения (resources/ai). Признаки считаются здесь точно так же, как при обучении, — это проверяет
 * тест AssistantModelTest по контрольным парам из Python.
 */
object TextFeatures {
    private val NonWord = Regex("[^a-z0-9а-я]+")

    /** Нижний регистр, «ё» → «е», всё кроме букв и цифр — пробел. */
    fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').replace(NonWord, " ").trim()

    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /** Слова, буквенные n-граммы (2–4) слова в рамке «<слово>» и пары соседних слов. */
    fun keys(text: String): List<String> {
        val ws = words(text)
        val keys = mutableListOf<String>()
        for (w in ws) {
            keys += "w:$w"
            val framed = "<$w>"
            for (n in 2..4) for (i in 0..framed.length - n) keys += "c:" + framed.substring(i, i + n)
        }
        for (i in 0 until ws.size - 1) keys += "b:${ws[i]} ${ws[i + 1]}"
        return keys
    }

    /** FNV-1a (32 бита) по байтам UTF-8 — номер признака не зависит от платформы. */
    fun fnv(s: String): Long {
        var h = 0x811C9DC5.toInt()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toInt() and 0xFF)
            h *= 16777619
        }
        return h.toLong() and 0xFFFFFFFFL
    }

    /** Номера признаков и общее значение каждого (единицы, нормированные на длину вектора). */
    fun vector(text: String, dim: Int): Pair<IntArray, Double> {
        val idx = keys(text).map { (fnv(it) % dim).toInt() }.toSortedSet().toIntArray()
        return idx to if (idx.isEmpty()) 0.0 else 1.0 / sqrt(idx.size.toDouble())
    }
}

/**
 * Линейная модель над [TextFeatures]: softmax по классам (один класс) или сигмоида по каждой метке (несколько меток).
 * Формат файла: строки «type», «dim», «classes», необязательный «threshold», «bias», затем «w номер веса…».
 */
class LinearTextModel(
    val classes: List<String>,
    private val dim: Int,
    private val multiLabel: Boolean,
    val threshold: Double,
    private val bias: DoubleArray,
    private val weights: Map<Int, DoubleArray>
) {
    /** Вероятность каждого класса (или каждой метки). */
    fun predict(text: String): DoubleArray {
        val (idx, value) = TextFeatures.vector(text, dim)
        val z = bias.copyOf()
        for (i in idx) weights[i]?.let { w -> for (c in z.indices) z[c] += w[c] * value }
        return if (multiLabel) {
            DoubleArray(z.size) { 1.0 / (1.0 + exp(-z[it])) }
        } else {
            val max = z.max()
            val e = DoubleArray(z.size) { exp(z[it] - max) }
            val sum = e.sum()
            DoubleArray(e.size) { e[it] / sum }
        }
    }

    companion object {
        fun parse(lines: Sequence<String>): LinearTextModel {
            var type = "softmax"
            var dim = 0
            var classes = emptyList<String>()
            var threshold = 0.5
            var bias = DoubleArray(0)
            val weights = HashMap<Int, DoubleArray>()
            for (line in lines) {
                val parts = line.trim().split(' ')
                when (parts.firstOrNull()) {
                    "type" -> type = parts[1]
                    "dim" -> dim = parts[1].toInt()
                    "classes" -> classes = parts.drop(1)
                    "threshold" -> threshold = parts[1].toDouble()
                    "bias" -> bias = parts.drop(1).map { it.toDouble() }.toDoubleArray()
                    "w" -> weights[parts[1].toInt()] = parts.drop(2).map { it.toDouble() }.toDoubleArray()
                }
            }
            require(dim > 0 && classes.isNotEmpty() && bias.size == classes.size) { "повреждённый файл модели" }
            return LinearTextModel(classes, dim, type == "sigmoid", threshold, bias, weights)
        }

        /** Модель из ресурсов приложения; null, если файла нет или он повреждён (тогда работают правила). */
        fun fromResource(path: String): LinearTextModel? = runCatching {
            LinearTextModel::class.java.getResourceAsStream(path)!!.bufferedReader(Charsets.UTF_8).use { parse(it.lineSequence()) }
        }.getOrNull()
    }
}

/** Что сказано в заметке: боль, усталость, плохой сон, хорошее самочувствие, техника, болезнь. */
enum class NoteTag(val key: String, val label: String) {
    PAIN("pain", "боль"),
    FATIGUE("fatigue", "усталость"),
    SLEEP("sleep", "плохой сон"),
    GOOD("good", "хорошее самочувствие"),
    TECHNIQUE("technique", "техника"),
    ILLNESS("illness", "болезнь")
}

/** Разбор заметок своей моделью; если модель не загрузилась — меток нет (советы работают по ключевым словам). */
object NoteTagger {
    private val model: LinearTextModel? by lazy { LinearTextModel.fromResource("/ai/notes_model.txt") }

    val available: Boolean get() = model != null

    fun tags(note: String): Set<NoteTag> {
        val m = model ?: return emptySet()
        if (note.isBlank()) return emptySet()
        val p = m.predict(note)
        return NoteTag.entries.filterIndexed { _, tag ->
            val i = m.classes.indexOf(tag.key)
            i >= 0 && p[i] >= m.threshold
        }.toSet()
    }
}
