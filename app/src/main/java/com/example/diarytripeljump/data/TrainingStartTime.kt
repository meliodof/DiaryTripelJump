package com.example.diarytripeljump.data

import java.util.Calendar

/**
 * Время начала тренировки, которое предлагается новой тренировке. Отдельно не хранится: берётся из начала
 * уже записанных тренировок, поэтому запоминается само и меняется вместе с расписанием.
 *
 * Сначала — время, в которое чаще всего начинались тренировки в тот же день недели, затем — чаще всего
 * вообще; при равенстве — то, что было позже. Пропущенные тренировки не учитываются, время округляется до 5 минут.
 */
object TrainingStartTime {
    /** Сколько последних тренировок учитывается: старое расписание не мешает новому. */
    const val RECENT = 40
    private const val STEP = 5

    fun minuteOfDay(millis: Long): Int = Calendar.getInstance().run {
        timeInMillis = millis
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }

    private fun weekday(millis: Long): Int = Calendar.getInstance().run { timeInMillis = millis; get(Calendar.DAY_OF_WEEK) }

    private fun rounded(minute: Int): Int = ((minute + STEP / 2) / STEP * STEP).coerceAtMost(24 * 60 - STEP)

    /**
     * До [limit] вариантов времени начала (минуты от полуночи) для тренировки в день [date], лучший — первым.
     * Пусто, если тренировок ещё не было.
     */
    fun suggest(sessions: List<TrainingSession>, date: Long, limit: Int = 3): List<Int> {
        val recent = sessions.filter { !it.isSkipped }.sortedByDescending { it.date }.take(RECENT)
        if (recent.isEmpty()) return emptyList()
        val day = weekday(date)
        data class Stat(var sameDay: Int = 0, var all: Int = 0, var last: Long = 0)
        val stats = linkedMapOf<Int, Stat>()
        recent.forEach { s ->
            val st = stats.getOrPut(rounded(minuteOfDay(s.date))) { Stat() }
            st.all++
            if (weekday(s.date) == day) st.sameDay++
            st.last = maxOf(st.last, s.date)
        }
        return stats.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Stat>> { it.value.sameDay }
                .thenByDescending { it.value.all }
                .thenByDescending { it.value.last })
            .take(limit)
            .map { it.key }
    }

    fun label(minute: Int): String = "%d:%02d".format(minute / 60, minute % 60)
}
