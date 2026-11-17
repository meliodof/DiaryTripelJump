package com.example.diarytripeljump.data

/**
 * Комментарии к отдельным попыткам. Хранятся в самой записи (колонка attemptNotesJson у упражнения и у старта),
 * поэтому переживают редактирование тренировки, где строки упражнений пересоздаются.
 *
 * Ключ попытки: у упражнения — номер подхода (попытки) с нуля, он же номер попытки с разбега; у старта —
 * «a0», «a1»… для попыток финала и «q0», «q1»… для квалификации (попытка с разбега — с тем же номером).
 * Формат: записи через символ-разделитель записей, ключ и текст — через разделитель полей; эти управляющие символы
 * в тексте с клавиатуры не встречаются, поэтому экранирование не нужно.
 */
object AttemptNotes {
    private const val RECORD = '\u001E'
    private const val FIELD = '\u001F'

    fun exerciseKey(index: Int): String = index.toString()
    fun finalKey(index: Int): String = "a$index"
    fun qualificationKey(index: Int): String = "q$index"

    fun decode(text: String?): Map<String, String> =
        text.orEmpty().split(RECORD).mapNotNull { row ->
            val i = row.indexOf(FIELD)
            if (i <= 0) null else row.substring(0, i) to row.substring(i + 1)
        }.filter { it.second.isNotBlank() }.toMap()

    /** Пустые комментарии не хранятся; без комментариев — null, колонка остаётся пустой. */
    fun encode(notes: Map<String, String>): String? =
        notes.entries.filter { it.value.isNotBlank() }.sortedBy { it.key }
            .joinToString(RECORD.toString()) { (k, v) -> k + FIELD + v.trim().replace(RECORD, ' ').replace(FIELD, ' ') }
            .ifEmpty { null }

    /** Новое значение одного комментария; пустой текст удаляет комментарий. */
    fun with(text: String?, key: String, note: String): String? =
        encode(decode(text) + (key to note))

    /** Попытки старта по порядку: «X» — незачёт, пустые поля пропускаются. */
    fun competitionAttempts(text: String?): List<String> =
        text.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/** Где лежит попытка: упражнение тренировки или старт, и ключ попытки внутри записи. */
data class AttemptRef(val competition: Boolean, val ownerId: Long, val key: String)

/** Попытка для окна комментариев: ключ, подпись и уже сохранённый комментарий. */
data class AttemptLine(val key: String, val label: String, val note: String?)

object AttemptLines {
    private fun jump(text: String): String = if (text.equals("x", ignoreCase = true) || text == "Х") "незачёт" else "$text м"

    /** Попытки старта: сначала квалификация, затем финал; разбег той же попытки — в подписи. */
    fun competition(c: Competition): List<AttemptLine> {
        val notes = AttemptNotes.decode(c.attemptNotesJson)
        fun part(attemptsJson: String?, runUpJson: String?, prefix: String, key: (Int) -> String): List<AttemptLine> {
            val jumps = AttemptNotes.competitionAttempts(attemptsJson)
            val runUp = RunUp.decode(runUpJson)
            return (0 until maxOf(jumps.size, runUp.size)).map { i ->
                val parts = listOfNotNull(jumps.getOrNull(i)?.let { jump(it) }, runUp.getOrNull(i)?.takeIf { !it.isEmpty }?.let { RunUp.describe(it) })
                AttemptLine(key(i), "$prefix ${i + 1}" + parts.joinToString(", ").let { if (it.isEmpty()) "" else ": $it" }, notes[key(i)])
            }
        }
        return part(c.qualificationAttemptsJson, c.qualificationRunUpJson, "Квалификация, попытка", AttemptNotes::qualificationKey) +
            part(c.attemptsJson, c.runUpJson, "Попытка", AttemptNotes::finalKey)
    }

    /** Подходы (попытки) упражнения тренировки; без подходов — один итоговый результат. */
    fun exercise(r: ExerciseResult): List<AttemptLine> {
        val notes = AttemptNotes.decode(r.attemptNotesJson)
        val values = r.setResultsJson?.split(",")?.map { it.trim().toDoubleOrNull() }.orEmpty()
        val runUp = RunUp.decode(r.runUpJson)
        val count = maxOf(values.size, runUp.size, if (r.result > 0) 1 else 0)
        val noun = if (runUp.isNotEmpty() || r.sets == null) "Попытка" else "Подход"
        return (0 until count).map { i ->
            val value = (values.getOrNull(i) ?: if (values.isEmpty() && i == 0) r.result else null)?.takeIf { it > 0 }
            val parts = listOfNotNull(value?.let { formatRecordValue(it, r.resultUnit) }, runUp.getOrNull(i)?.let { RunUp.describe(it) })
            val key = AttemptNotes.exerciseKey(i)
            AttemptLine(key, "$noun ${i + 1}" + parts.joinToString(", ").let { if (it.isEmpty()) "" else ": $it" }, notes[key])
        }
    }
}
