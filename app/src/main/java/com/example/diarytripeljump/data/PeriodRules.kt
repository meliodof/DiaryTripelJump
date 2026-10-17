package com.example.diarytripeljump.data

/**
 * Правила подготовительных периодов.
 *
 * Периоды могут вкладываться друг в друга, пересекаться, начинаться или заканчиваться
 * в один и тот же день. Нельзя только создавать два периода с одной и той же датой начала.
 */
object PeriodRules {

    /** Период, у которого та же дата начала (с точностью до дня), что и у [startDate]. */
    fun findStartConflict(periods: List<TrainingPeriod>, startDate: Long, excludeId: Long?): TrainingPeriod? {
        val day = startOfDay(startDate)
        return periods.firstOrNull { it.id != excludeId && startOfDay(it.startDate) == day }
    }

    /** Из нескольких периодов, покрывающих день, «главный» — самый короткий (самый вложенный). */
    fun primaryFor(day: Long, periods: List<TrainingPeriod>): TrainingPeriod? =
        containing(day, periods).minWithOrNull(
            compareBy<TrainingPeriod> { it.endDate - it.startDate }.thenByDescending { it.startDate }
        )

    fun containing(day: Long, periods: List<TrainingPeriod>): List<TrainingPeriod> {
        val d = startOfDay(day)
        return periods.filter { d >= startOfDay(it.startDate) && d <= startOfDay(it.endDate) }
    }
}
