package com.example.diarytripeljump.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/*
 * Расчёты для обзора на вкладке «Аналитика»: радар формы, мишень разбега, карта сезона и таймлайн подводки.
 * Только функции над данными дневника — без интерфейса и базы, поэтому проверяются тестами (OverviewTest).
 */

private const val DAY = 86_400_000L

// ======================================================================= радар формы

/**
 * Ось радара: [now] и [before] — от 0 до 1, где 0 — худшее, 1 — лучшее значение в истории спортсмена;
 * null — за окно данных нет. [detail] — текущее значение словами.
 */
data class RadarAxis(
    val title: String,
    val now: Double?,
    val before: Double?,
    val detail: String?,
    /** Постоянный ключ оси: по нему ось связана с советами и с другими карточками обзора. */
    val key: String = "",
    /** Показатель визуализации, который стоит за осью: «показать на графике». */
    val seriesId: String? = null
)

/** Ключи осей радара. */
object RadarKey {
    const val SPEED = "speed"
    const val STRENGTH = "strength"
    const val JUMPS = "jumps"
    const val TRIPLE = "triple"
    const val RUNUP = "runup"
    const val STABILITY = "stability"
    const val VOLUME = "volume"
}

object FormRadar {
    /** Окно, за которое берётся лучший результат: 4 недели «сейчас» и 4 недели месяц назад. */
    const val WINDOW_DAYS = 28
    /** Разброс тройного в окне, при котором стабильность считается нулевой. */
    const val CV_ZERO = 0.06

    private val SPEED = listOf("бег")
    private val STRENGTH = listOf("присед", "становая", "тяга")
    private val JUMPS = listOf("пятерн", "многоскок", "скачк", "десятерн", "длин", "с места")

    fun build(
        records: List<RecordSummary>,
        trainings: List<TrainingWithExercises>,
        competitions: List<Competition>,
        body: List<BodyMeasurement>,
        now: Long
    ): List<RadarAxis> {
        val before = now - WINDOW_DAYS * DAY
        val (comp, train) = RecordInsights.mainJumpSplit(records)
        val jumpPoints = (listOfNotNull(comp, train).flatMap { it.points })
        val axes = mutableListOf<RadarAxis>()

        val speed = pick(records, now) { it.unit == ResultUnit.SECONDS && it.lowerIsBetter && it.name.lowercase().let { n -> SPEED.any { k -> k in n } } }
        axes += recordAxis("Скорость", speed, now, before).copy(key = RadarKey.SPEED, seriesId = speed?.let { "rec:" + it.key })

        val strength = pick(records, now) { it.unit == ResultUnit.KG && !it.lowerIsBetter && it.name.lowercase().let { n -> STRENGTH.any { k -> k in n } } }
        val weights = body.filter { it.weightKg != null }.sortedBy { it.date }
        if (strength != null && weights.isNotEmpty()) {
            // сила на килограмм веса тела: так рост мышечной массы не выдаётся за рост силы
            fun weightAt(d: Long) = (weights.lastOrNull { it.date <= d } ?: weights.first()).weightKg!!
            val rel = strength.points.map { it.copy(value = it.value / weightAt(it.date)) }
            axes += pointsAxis("Сила", rel, false, now, before) { "${formatNumber(Math.round(it * 100) / 100.0)} на кг · ${strength.displayName}" }.copy(key = RadarKey.STRENGTH, seriesId = "rec:" + strength.key)
        } else {
            axes += recordAxis("Сила", strength, now, before).copy(key = RadarKey.STRENGTH, seriesId = strength?.let { "rec:" + it.key })
        }

        val jumps = pick(records, now) { it.unit == ResultUnit.M && !RecordInsights.isMainJump(it) && it.name.lowercase().let { n -> JUMPS.any { k -> k in n } } }
        axes += recordAxis("Прыгучесть", jumps, now, before).copy(key = RadarKey.JUMPS, seriesId = jumps?.let { "rec:" + it.key })

        val tripleSeries = (train ?: comp)?.let { "rec:" + it.key }
        axes += pointsAxis("Тройной", jumpPoints, false, now, before) { formatRecordValue(it, ResultUnit.M) }.copy(key = RadarKey.TRIPLE, seriesId = tripleSeries)

        val shots = runUpShots(trainings, competitions)
        fun hitShare(t: Long): Double? {
            val inWindow = shots.filter { it.date in (t - WINDOW_DAYS * DAY + 1)..t }
            return if (inWindow.size < 3) null else inWindow.count { it.feet == 0.0 }.toDouble() / inWindow.size
        }
        val hitNow = hitShare(now)
        axes += RadarAxis("Разбег", hitNow, hitShare(before), hitNow?.let { "в брусок ${Math.round(it * 100)} %" }, RadarKey.RUNUP)

        fun stability(t: Long): Double? {
            val v = jumpPoints.filter { it.date in (t - WINDOW_DAYS * DAY + 1)..t }.map { it.value }
            if (v.size < 3) return null
            val mean = v.average()
            val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / (v.size - 1))
            return (1 - sd / mean / CV_ZERO).coerceIn(0.0, 1.0)
        }
        val stNow = stability(now)
        axes += RadarAxis("Стабильность", stNow, stability(before), stNow?.let { "разброс тройного ${formatNumber(Math.round((1 - it) * CV_ZERO * 1000) / 10.0)} %" }, RadarKey.STABILITY, tripleSeries)

        fun volume(t: Long): Double? {
            val weeks = (0 until 52).map { w -> AdviceEngine.volume(trainings, t - (w + 1) * 7 * DAY, t - w * 7 * DAY) }
            val max = weeks.maxOrNull() ?: 0
            if (max == 0) return null
            return weeks.take(4).average() / max
        }
        val volNow = volume(now)
        axes += RadarAxis("Объём", volNow, volume(before), volNow?.let { "${Math.round(it * 100)} % от самой нагруженной недели" }, RadarKey.VOLUME)
        return axes
    }

    /** Упражнение группы, которое чаще всего делали последние 3 месяца. */
    private fun pick(records: List<RecordSummary>, now: Long, filter: (RecordSummary) -> Boolean): RecordSummary? =
        records.filter { !it.isAnti && it.points.isNotEmpty() && filter(it) }
            .maxByOrNull { r -> r.points.count { it.date >= now - 90 * DAY } * 1000 + r.points.size }

    private fun recordAxis(title: String, r: RecordSummary?, now: Long, before: Long): RadarAxis =
        if (r == null) RadarAxis(title, null, null, null)
        else pointsAxis(title, r.points, r.lowerIsBetter, now, before) { "${formatRecordValue(it, r.unit)} · ${r.displayName}" }

    /** Лучший результат окна на шкале «худший — лучший» за всю историю спортсмена. */
    fun pointsAxis(title: String, points: List<RecordPoint>, lowerIsBetter: Boolean, now: Long, before: Long, label: (Double) -> String): RadarAxis {
        val history = points.filter { it.date <= now }
        if (history.isEmpty()) return RadarAxis(title, null, null, null)
        val lo = history.minOf { it.value }
        val hi = history.maxOf { it.value }
        fun best(t: Long): Double? = history.filter { it.date in (t - WINDOW_DAYS * DAY + 1)..t }.map { it.value }
            .let { v -> if (v.isEmpty()) null else if (lowerIsBetter) v.min() else v.max() }
        fun score(v: Double?): Double? = v?.let {
            if (hi - lo < 1e-9) 0.5 else ((it - lo) / (hi - lo)).let { s -> if (lowerIsBetter) 1 - s else s }
        }
        val bestNow = best(now)
        return RadarAxis(title, score(bestNow), score(best(before)), bestNow?.let(label))
    }
}

/**
 * Какие советы относятся к каким осям радара: заступы — к разбегу, рост нагрузки и перерыв — к объёму,
 * застой — к тройному, резкий спад и отставание от цели — к оси того же упражнения.
 */
fun radarWarnings(axes: List<RadarAxis>, advice: List<Advice>): Map<String, Advice> {
    val out = LinkedHashMap<String, Advice>()
    advice.filter { it.kind != AdviceKind.GOOD }.forEach { a ->
        val key = when {
            a.id.startsWith("runup-") -> RadarKey.RUNUP
            a.id.startsWith("load-") || a.id.startsWith("acwr-") || a.id.startsWith("break-") -> RadarKey.VOLUME
            a.id.startsWith("plateau-") -> RadarKey.TRIPLE
            a.recordKey != null -> axes.firstOrNull { it.seriesId == "rec:" + a.recordKey }?.key
            else -> null
        }
        if (key != null && key !in out) out[key] = a
    }
    return out
}

// ======================================================================= мишень разбега

/** Попытка с разбега: сколько стоп до бруска (плюс — заступ, минус — недоступ). */
data class RunUpShot(val date: Long, val feet: Double, val competition: Boolean)

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

/** Все попытки с отметкой попадания: из тренировок и стартов. */
fun runUpShots(trainings: List<TrainingWithExercises>, competitions: List<Competition>): List<RunUpShot> =
    (trainings.flatMap { t ->
        t.exerciseResults.filter { it.runUpJson != null }.flatMap { r -> RunUp.decode(r.runUpJson).mapNotNull { a -> a.boardFeet?.let { RunUpShot(r.date, it, false) } } }
    } + competitions.flatMap { c ->
        (RunUp.decode(c.qualificationRunUpJson) + RunUp.decode(c.runUpJson)).mapNotNull { a -> a.boardFeet?.let { RunUpShot(c.date, it, true) } }
    }).sortedBy { it.date }

object RunUpTarget {
    const val DAYS = 60
    const val LIMIT = 40
    /** Смещение меньше этого (в стопах) — отметку не двигать. */
    const val TOLERANCE = 0.25

    fun build(trainings: List<TrainingWithExercises>, competitions: List<Competition>, now: Long): RunUpView? {
        val shots = runUpShots(trainings, competitions).filter { it.date in (now - DAYS * DAY)..now }.takeLast(LIMIT)
        if (shots.isEmpty()) return null
        val sorted = shots.map { it.feet }.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        val move = if (shots.size >= 4 && abs(median) >= TOLERANCE) Math.round(median * 2) / 2.0 else 0.0
        return RunUpView(shots, shots.count { it.feet == 0.0 }, shots.count { it.feet > 0 }, shots.count { it.feet < 0 }, median, move)
    }
}

// ======================================================================= карта сезона

/** День на карте сезона. [level]: −1 — будущее, 0 — без тренировки, 1…4 — нагрузка от малой до большой. */
data class SeasonDay(
    val day: Long,
    val level: Int,
    val minutes: Int,
    val volume: Int,
    val skipped: Boolean,
    val start: Competition?,
    val record: Boolean,
    /** В заметках дня жалоба на боль или болезнь — по своей модели разбора заметок ([NoteTagger]). */
    val complaint: Boolean = false
)

object SeasonMap {
    const val WEEKS = 26

    /** Недели (с понедельника) от старых к новым, в каждой 7 дней. */
    fun build(
        trainings: List<TrainingWithExercises>,
        competitions: List<Competition>,
        records: List<RecordSummary>,
        now: Long,
        weeks: Int = WEEKS
    ): List<List<SeasonDay>> {
        val firstMonday = ChartData.weekStart(now) - (weeks - 1) * 7 * DAY
        val today = startOfDay(now)
        val byDay = trainings.groupBy { startOfDay(it.training.date) }
        val startDays = competitions.filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }
            .associateBy { startOfDay(it.date) }
        val recordDays = records.filter { !it.isAnti }.flatMap { r -> r.history.filter { it.previousValue != null }.map { startOfDay(it.date) } }.toSet()
        // мера нагрузки дня: время тренировок, если его отмечают, иначе объём в повторениях
        val useMinutes = trainings.any { (it.training.durationMinutes ?: 0) > 0 && it.training.date >= firstMonday }
        val days = (0 until weeks * 7).map { i ->
            // календарный день: прибавление суток через startOfDay выдерживает переход на летнее время
            val day = startOfDay(firstMonday + i * DAY + DAY / 2)
            val list = byDay[day].orEmpty()
            val done = list.filter { !it.training.isSkipped }
            val minutes = done.sumOf { it.training.durationMinutes ?: 0 }
            val volume = AdviceEngine.volume(list, day, day + DAY)
            Triple(day, done, Pair(minutes, volume)) to list.any { it.training.isSkipped }
        }
        val max = days.maxOfOrNull { (t, _) -> if (useMinutes) t.third.first else t.third.second } ?: 0
        val cells = days.map { (t, skipped) ->
            val (day, done, mv) = t
            val v = if (useMinutes) mv.first else mv.second
            val level = when {
                day > today -> -1
                v > 0 && max > 0 -> ceil(4.0 * v / max).toInt().coerceIn(1, 4)
                done.isNotEmpty() -> 1
                else -> 0
            }
            val notes = byDay[day].orEmpty().flatMap { t -> listOfNotNull(t.training.notes) + t.exerciseResults.mapNotNull { it.notes } }
            val complaint = day <= today && notes.any { n -> NoteTagger.tags(n).let { NoteTag.PAIN in it || NoteTag.ILLNESS in it } }
            SeasonDay(day, level, mv.first, mv.second, skipped && done.isEmpty(), startDays[day], day in recordDays, complaint)
        }
        return cells.chunked(7)
    }
}

// ======================================================================= подводка к старту

data class TaperDay(val day: Long, val volume: Int, val future: Boolean)

data class TaperView(
    val start: Competition,
    val daysLeft: Int,
    /** 14 дней до старта и день старта. */
    val days: List<TaperDay>,
    /** Обычный объём в день за 3 недели до подводки; 0 — данных нет. */
    val baselineDaily: Double,
    /** Объём последних 7 дней в долях от обычного; null — сравнивать не с чем. */
    val lastWeekShare: Double?
)

object TaperTimeline {
    /** За сколько дней до старта показывать подводку. */
    const val HORIZON_DAYS = 21
    const val TAPER_DAYS = 14
    /** Рекомендуемый объём в подводке: 40–60 % обычного (Bosquet и соавт., 2007). */
    const val LOW = 0.4
    const val HIGH = 0.6

    fun build(trainings: List<TrainingWithExercises>, competitions: List<Competition>, now: Long): TaperView? {
        val today = startOfDay(now)
        val start = competitions
            .filter { startOfDay(it.date) >= today && it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && it.status != CompetitionStatus.COMPLETED }
            .minByOrNull { it.date } ?: return null
        val startDay = startOfDay(start.date)
        val daysLeft = Math.round((startDay - today) / DAY.toDouble()).toInt()
        if (daysLeft > HORIZON_DAYS) return null
        val firstDay = startDay - TAPER_DAYS * DAY
        val days = (0..TAPER_DAYS).map { i ->
            val d = startOfDay(firstDay + i * DAY + DAY / 2)
            TaperDay(d, if (d > today) 0 else AdviceEngine.volume(trainings, d, d + DAY), d > today)
        }
        val baseline = AdviceEngine.volume(trainings, firstDay - 21 * DAY, firstDay) / 21.0
        val lastWeek = AdviceEngine.volume(trainings, now - 7 * DAY, now + 1)
        return TaperView(start, daysLeft, days, baseline, if (baseline > 0) lastWeek / (baseline * 7) else null)
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
