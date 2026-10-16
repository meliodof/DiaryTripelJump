package com.example.diarytripeljump.data

import java.util.Calendar

/** Фаза соревнования относительно сегодняшнего дня. */
enum class CompetitionPhase { UPCOMING, CURRENT, PAST }

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Автоматическое определение раздела по дате:
 * будущая дата — «Предстоящие», сегодня — «Текущие», со следующего дня — «Прошедшие».
 * Пропущенные и отменённые старты всегда попадают в «Прошедшие».
 */
fun Competition.phase(todayStart: Long = startOfDay(System.currentTimeMillis())): CompetitionPhase {
    if (status == CompetitionStatus.SKIPPED || status == CompetitionStatus.CANCELLED) return CompetitionPhase.PAST
    val day = startOfDay(date)
    return when {
        day > todayStart -> CompetitionPhase.UPCOMING
        day == todayStart -> CompetitionPhase.CURRENT
        else -> CompetitionPhase.PAST
    }
}
