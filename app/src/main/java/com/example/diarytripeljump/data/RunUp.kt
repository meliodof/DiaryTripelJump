package com.example.diarytripeljump.data

/**
 * Одна попытка с разбега.
 * [boardFeet]: сколько стоп до бруска — плюс «заступил», минус «не доступил», 0 — попал точно.
 * [moveCm], [moveFeet]: на сколько передвинута отметка разбега — плюс «отнёс», минус «поднёс».
 */
data class RunUpAttempt(
    val boardFeet: Double? = null,
    val moveCm: Double? = null,
    val moveFeet: Double? = null
) {
    val isEmpty: Boolean get() = boardFeet == null && moveCm == null && moveFeet == null
}

/** Изменение отметки разбега за одну тренировку или старт. */
data class RunUpMove(
    val date: Long,
    val deltaCm: Double,
    val deltaFeet: Double,
    val source: String
)

/** Точка истории размера разбега; изменения null у ручного замера. */
data class RunUpHistoryEntry(
    val date: Long,
    val lengthCm: Double?,
    val lengthFeet: Double?,
    val deltaCm: Double?,
    val deltaFeet: Double?,
    val source: String
)

/** Упражнение «По разбегу»: вместо результата — попытки с точностью попадания, в рекорды не попадает. */
object RunUp {
    const val NAME = "По разбегу"

    fun isExercise(name: String?): Boolean = name?.trim()?.startsWith(NAME, ignoreCase = true) == true

    /** Формат хранения: попытки через «|», значения попытки через «;». Пустая строка — попыток ещё нет. */
    fun encode(attempts: List<RunUpAttempt>): String = encodeAll(attempts.filterNot { it.isEmpty })

    /** Сохраняет и пустые попытки: у старта важен номер попытки. */
    fun encodeAll(attempts: List<RunUpAttempt>): String =
        attempts.joinToString("|") { a ->
            listOf(a.boardFeet, a.moveCm, a.moveFeet).joinToString(";") { it?.toString() ?: "" }
        }

    fun decode(text: String?): List<RunUpAttempt> =
        text.orEmpty().split("|").filter { it.isNotBlank() }.map { row ->
            val parts = row.split(";")
            RunUpAttempt(
                boardFeet = parts.getOrNull(0)?.toDoubleOrNull(),
                moveCm = parts.getOrNull(1)?.toDoubleOrNull(),
                moveFeet = parts.getOrNull(2)?.toDoubleOrNull()
            )
        }

    /** «заступил на 1,5 ст., отнёс на 20 см и 0,5 ст.» */
    fun describe(attempt: RunUpAttempt): String {
        val board = attempt.boardFeet?.let { v ->
            when {
                v > 0 -> "заступил на ${formatNumber(v)} ст."
                v < 0 -> "не доступил ${formatNumber(-v)} ст."
                else -> "попал в брусок"
            }
        }
        val amounts = listOfNotNull(
            attempt.moveCm?.takeIf { it != 0.0 }?.let { "${formatNumber(Math.abs(it))} см" },
            attempt.moveFeet?.takeIf { it != 0.0 }?.let { "${formatNumber(Math.abs(it))} ст." }
        )
        val sign = attempt.moveCm?.takeIf { it != 0.0 } ?: attempt.moveFeet?.takeIf { it != 0.0 }
        val move = if (sign == null) null else (if (sign > 0) "отнёс на " else "поднёс на ") + amounts.joinToString(" и ")
        return listOfNotNull(board, move).joinToString(", ")
    }

    /** Суммарный перенос отметки; null — отметку не двигали. */
    fun moveOf(date: Long, attempts: List<RunUpAttempt>, source: String): RunUpMove? {
        val cm = attempts.sumOf { it.moveCm ?: 0.0 }
        val feet = attempts.sumOf { it.moveFeet ?: 0.0 }
        return if (cm == 0.0 && feet == 0.0) null else RunUpMove(date, cm, feet, source)
    }
}

/**
 * Размер разбега вычисляется, а не хранится: последний ручной замер плюс все переносы отметки после него.
 * Поэтому правка или удаление тренировки сразу меняют размер. Сантиметры и стопы считаются отдельно:
 * длина стопы у каждого своя, и пересчитать одно в другое нельзя.
 */
object RunUpLength {
    /** История, новые записи сверху; первая запись — текущий размер. */
    fun build(measurements: List<RunUpMeasurement>, moves: List<RunUpMove>): List<RunUpHistoryEntry> {
        val events = measurements.map { it.date to it } + moves.map { it.date to it }
        var cm: Double? = null
        var feet: Double? = null
        val history = mutableListOf<RunUpHistoryEntry>()
        events.sortedBy { it.first }.forEach { (date, event) ->
            when (event) {
                is RunUpMeasurement -> {
                    cm = event.lengthCm ?: cm
                    feet = event.lengthFeet ?: feet
                    history.add(RunUpHistoryEntry(date, cm, feet, null, null, "Замер"))
                }
                is RunUpMove -> {
                    cm = cm?.plus(event.deltaCm)
                    feet = feet?.plus(event.deltaFeet)
                    if (cm != null || feet != null) {
                        history.add(RunUpHistoryEntry(date, cm, feet, event.deltaCm, event.deltaFeet, event.source))
                    }
                }
            }
        }
        return history.reversed()
    }

    /** «3650 см, 18 ст.» */
    fun label(lengthCm: Double?, lengthFeet: Double?): String = listOfNotNull(
        lengthCm?.let { "${formatNumber(it)} см" },
        lengthFeet?.let { "${formatNumber(it)} ст." }
    ).joinToString(", ")
}
