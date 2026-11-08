package com.example.diarytripeljump.data

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/** Период графика на экране рекорда. */
enum class ChartRange(val label: String, val days: Int?) {
    MONTH("Месяц", 31),
    QUARTER("3 месяца", 92),
    YEAR("Год", 365),
    ALL("Всё время", null)
}

/** Выводы из результатов упражнения: сезон, последняя форма, темп до цели. */
object RecordInsights {
    private const val DAY_MS = 86_400_000L

    private fun yearOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }[Calendar.YEAR]

    fun inRange(points: List<RecordPoint>, range: ChartRange, now: Long = System.currentTimeMillis()): List<RecordPoint> {
        val days = range.days ?: return points
        val from = now - days * DAY_MS
        return points.filter { it.date >= from }
    }

    fun seasonBest(points: List<RecordPoint>, lowerIsBetter: Boolean, now: Long = System.currentTimeMillis()): Double? {
        val season = points.filter { yearOf(it.date) == yearOf(now) }.map { it.value }
        return if (lowerIsBetter) season.minOrNull() else season.maxOrNull()
    }

    fun latest(points: List<RecordPoint>): RecordPoint? = points.maxByOrNull { it.date }

    /** Разница между средним трёх последних и трёх предыдущих результатов; плюс — форма лучше. */
    fun trend(points: List<RecordPoint>, lowerIsBetter: Boolean): Double? {
        if (points.size < 6) return null
        val sorted = points.sortedBy { it.date }.map { it.value }
        val recent = sorted.takeLast(3).average()
        val before = sorted.dropLast(3).takeLast(3).average()
        return if (lowerIsBetter) before - recent else recent - before
    }

    fun daysHeld(bestDate: Long, now: Long = System.currentTimeMillis()): Int =
        ((now - bestDate) / DAY_MS).toInt().coerceAtLeast(0)

    /** Сколько полных недель осталось до срока; null — срока нет или он прошёл. */
    fun weeksLeft(deadline: Long?, now: Long = System.currentTimeMillis()): Int? {
        if (deadline == null || deadline <= now) return null
        return ceil((deadline - now) / (7.0 * DAY_MS)).toInt().coerceAtLeast(1)
    }

    /** На сколько нужно улучшаться в неделю, чтобы успеть к сроку. */
    fun weeklyPace(current: Double, target: Double, deadline: Long?, now: Long = System.currentTimeMillis()): Double? {
        val weeks = weeksLeft(deadline, now) ?: return null
        return abs(target - current) / weeks
    }

    /** Темп понятными единицами: сантиметры вместо долей метра. */
    fun formatPace(pace: Double, unit: ResultUnit): String = when (unit) {
        // поправка убирает погрешность дробей: 0,05 м должны остаться 5 см, а не стать 6
        ResultUnit.M -> "${ceil(pace * 100 - 1e-6).toInt().coerceAtLeast(1)} см"
        ResultUnit.CM -> "${ceil(pace).toInt().coerceAtLeast(1)} см"
        ResultUnit.SECONDS -> String.format(Locale.getDefault(), "%.2f сек", pace.coerceAtLeast(0.01))
        ResultUnit.KG -> String.format(Locale.getDefault(), "%.1f кг", pace.coerceAtLeast(0.1))
        ResultUnit.REPS -> "${ceil(pace).toInt().coerceAtLeast(1)} раз"
    }

    /** Рекорд в самом тройном прыжке: без снаряда, на двух ногах. */
    fun isMainJump(r: RecordSummary): Boolean =
        !r.isAnti && r.unit == ResultUnit.M && !r.isSingleLeg && r.inventoryName.isNullOrBlank() && r.name.contains("тройной", ignoreCase = true)

    /** Лучшие рекорды в тройном отдельно на соревнованиях и на тренировках. */
    fun mainJumpSplit(records: List<RecordSummary>): Pair<RecordSummary?, RecordSummary?> {
        val jumps = records.filter { isMainJump(it) }
        return jumps.filter { it.isCompetition }.maxByOrNull { it.best } to jumps.filter { !it.isCompetition }.maxByOrNull { it.best }
    }

    /** Рекорд, улучшенный с прошлого обновления; без прошлого состояния праздновать нечего. */
    fun newRecord(known: Map<String, Double>?, records: List<RecordSummary>): RecordSummary? {
        if (known == null) return null
        return records.firstOrNull { r ->
            val old = known[r.key]
            old != null && !r.isAnti && !r.isManuallyEdited && (if (r.lowerIsBetter) r.best < old else r.best > old)
        }
    }
}
