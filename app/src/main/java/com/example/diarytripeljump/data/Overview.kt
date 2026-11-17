package com.example.diarytripeljump.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/*
 * Расчёты для обзора на вкладке «Аналитика»: мишень разбега, подводка к старту и данные виджета.
 * Только функции над данными дневника — без интерфейса и базы, поэтому проверяются тестами (OverviewTest).
 */

private const val DAY = 86_400_000L

// ======================================================================= мишень разбега

/**
 * Попытка с разбега: сколько стоп до бруска (плюс — заступ, минус — недоступ).
 * [sourceId] — тренировка («t:id») или старт («c:id»), [sourceLabel] — как её назвать в списке.
 */
data class RunUpShot(
    val date: Long,
    val feet: Double,
    val competition: Boolean,
    val sourceId: String = "",
    val sourceLabel: String = "",
    /** Где лежит попытка — к ней можно оставить комментарий прямо с мишени. */
    val ref: AttemptRef? = null,
    val note: String? = null
)

/** Тренировка или старт, в которых отмечали попадание в брусок. */
data class RunUpSession(val id: String, val label: String, val date: Long, val shots: Int, val competition: Boolean)

data class RunUpView(
    val shots: List<RunUpShot>,
    val hits: Int,
    val fouls: Int,
    val shorts: Int,
    /** Типичное отклонение (медиана): на столько стоп смещено облако попыток. */
    val median: Double,
    /** На сколько перенести отметку: плюс — отнести, минус — поднести; 0 — не трогать. */
    val move: Double
)

/** Чьи попытки показывать на мишени. */
sealed class RunUpScope {
    /** Последняя тренировка или старт с отметками. */
    data object Last : RunUpScope()
    /** Выбранная тренировка или старт. */
    data class Session(val id: String) : RunUpScope()
    /** Последние [days] дней. */
    data class Days(val days: Int) : RunUpScope()
    /** Всё время. */
    data object All : RunUpScope()
    /** Свой период: от [from] до [to] включительно (начала дней). */
    data class Period(val from: Long, val to: Long) : RunUpScope()
}

private fun shortDay(millis: Long) = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))

/** Все попытки с отметкой попадания: из тренировок и стартов, по возрастанию даты. */
fun runUpShots(trainings: List<TrainingWithExercises>, competitions: List<Competition>): List<RunUpShot> =
    (trainings.filter { !it.training.isSkipped }.flatMap { t ->
        val id = "t:${t.training.id}"
        val label = "Тренировка ${shortDay(t.training.date)}"
        t.exerciseResults.filter { it.runUpJson != null }.flatMap { r ->
            val notes = AttemptNotes.decode(r.attemptNotesJson)
            RunUp.decode(r.runUpJson).mapIndexedNotNull { i, a ->
                val key = AttemptNotes.exerciseKey(i)
                a.boardFeet?.let { RunUpShot(r.date, it, false, id, label, AttemptRef(false, r.id, key), notes[key]) }
            }
        }
    } + competitions.flatMap { c ->
        val id = "c:${c.id}"
        val label = "${c.name}, ${shortDay(c.date)}"
        val notes = AttemptNotes.decode(c.attemptNotesJson)
        fun part(json: String?, key: (Int) -> String) = RunUp.decode(json).mapIndexedNotNull { i, a ->
            a.boardFeet?.let { RunUpShot(c.date, it, true, id, label, AttemptRef(true, c.id, key(i)), notes[key(i)]) }
        }
        part(c.qualificationRunUpJson, AttemptNotes::qualificationKey) + part(c.runUpJson, AttemptNotes::finalKey)
    }).sortedBy { it.date }

object RunUpTarget {
    /** Смещение меньше этого (в стопах) — отметку не двигать. */
    const val TOLERANCE = 0.25
    /** Совет по отметке — не меньше чем по стольким попыткам. */
    const val MIN_FOR_ADVICE = 4

    /** Тренировки и старты с отметками, новые сверху. */
    fun sessions(shots: List<RunUpShot>): List<RunUpSession> =
        shots.groupBy { it.sourceId }.map { (id, list) -> RunUpSession(id, list.first().sourceLabel, list.first().date, list.size, list.first().competition) }
            .sortedByDescending { it.date }

    /** Попытки выбранного охвата; null — попыток нет. */
    fun build(shots: List<RunUpShot>, scope: RunUpScope, now: Long): RunUpView? {
        val chosen = when (scope) {
            RunUpScope.Last -> sessions(shots.filter { it.date <= now }).firstOrNull()?.let { s -> shots.filter { it.sourceId == s.id } }.orEmpty()
            is RunUpScope.Session -> shots.filter { it.sourceId == scope.id }
            is RunUpScope.Days -> shots.filter { it.date in (now - scope.days * DAY)..now }
            RunUpScope.All -> shots
            is RunUpScope.Period -> shots.filter { it.date >= startOfDay(scope.from) && it.date < startOfDay(scope.to) + DAY }
        }
        if (chosen.isEmpty()) return null
        val sorted = chosen.map { it.feet }.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        val move = if (chosen.size >= MIN_FOR_ADVICE && abs(median) >= TOLERANCE) Math.round(median * 2) / 2.0 else 0.0
        return RunUpView(chosen, chosen.count { it.feet == 0.0 }, chosen.count { it.feet > 0 }, chosen.count { it.feet < 0 }, median, move)
    }

    /**
     * Где рисовать каждую попытку. По горизонтали — ровно то, что введено: сколько стоп до бруска
     * (0 — брусок, плюс — заступ правее, минус — недоступ левее). По вертикали ничего случайного нет:
     * попытки с одинаковым значением ставятся друг на друга столбиком, от старых к новым,
     * поэтому высота столбика — сколько раз было такое попадание.
     */
    fun stacks(shots: List<RunUpShot>): List<Pair<RunUpShot, Int>> =
        shots.sortedBy { it.date }.groupBy { it.feet }.flatMap { (_, same) -> same.mapIndexed { i, s -> s to i } }

    /** Ширина «пятна» одной попытки на тепловой карте, стоп: меньше точности, с которой отмечают попадание. */
    const val HEAT_SIGMA = 0.25

    /**
     * Тепловая карта, как в футболе: плотность попыток вдоль шкалы от [from] до [to] стоп в [samples] точках.
     * Каждая попытка — гауссово пятно шириной [sigma]; значения нормированы к 1 в самом «горячем» месте.
     */
    fun heat(shots: List<RunUpShot>, from: Double, to: Double, samples: Int, sigma: Double = HEAT_SIGMA): DoubleArray {
        val out = DoubleArray(samples)
        if (shots.isEmpty() || samples < 2) return out
        for (k in 0 until samples) {
            val f = from + (to - from) * k / (samples - 1)
            out[k] = shots.sumOf { s -> val z = (f - s.feet) / sigma; kotlin.math.exp(-0.5 * z * z) }
        }
        val peak = out.max()
        if (peak > 0) for (k in out.indices) out[k] /= peak
        return out
    }
}

// ======================================================================= виджет

/** Что показывает виджет на рабочем столе: ближайший старт, прогноз и рекорд. Сохраняется приложением. */
data class WidgetSnapshot(
    val startName: String?,
    val startDate: Long?,
    val forecast: String?,
    val record: String?
) {
    /** Дней до старта на дату [now]; null — старта нет или он прошёл. */
    fun daysLeft(now: Long): Int? = startDate?.let { Math.round((startOfDay(it) - startOfDay(now)) / DAY.toDouble()).toInt() }?.takeIf { it >= 0 }

    companion object {
        fun of(records: List<RecordSummary>, competitions: List<Competition>, insights: InsightsResult, now: Long): WidgetSnapshot {
            val today = startOfDay(now)
            val next = competitions
                .filter { startOfDay(it.date) >= today && it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && it.status != CompetitionStatus.COMPLETED }
                .minByOrNull { it.date }
            val (comp, train) = RecordInsights.mainJumpSplit(records)
            val best = listOfNotNull(comp, train).maxByOrNull { it.best }
            val f = insights.forecasts.firstOrNull { it.key == "main-jump" }
            return WidgetSnapshot(next?.name, next?.date, f?.let { it.format(it.value) }, best?.let { formatRecordValue(it.best, it.unit) })
        }
    }
}
