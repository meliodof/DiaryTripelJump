package com.example.diarytripeljump.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Длительность тренировки словами: «45 мин», «2 ч», «1 ч 30 мин». */
fun formatTrainingDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m мин"
        m == 0 -> "$h ч"
        else -> "$h ч $m мин"
    }
}

/**
 * Время тренировки для списка: «10:00–11:30 (1 ч 30 мин)», а без окончания — только «10:00».
 * Окончание хранится длительностью: при переносе тренировки на другой день или время оно сдвигается вместе с началом.
 */
fun trainingTimeRange(start: Long, durationMinutes: Int?): String {
    val fmt = SimpleDateFormat("HH:mm", Locale("ru"))
    val begin = fmt.format(Date(start))
    val duration = durationMinutes?.takeIf { it > 0 } ?: return begin
    return "$begin–${fmt.format(Date(start + duration * 60_000L))} (${formatTrainingDuration(duration)})"
}
