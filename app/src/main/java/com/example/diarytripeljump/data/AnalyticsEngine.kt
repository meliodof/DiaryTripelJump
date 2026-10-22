package com.example.diarytripeljump.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Расчётное ядро модуля аналитики. Не зависит от Android и UI, ничего не хранит:
 * все показатели вычисляются из тренировок, стартов и подготовительных периодов,
 * поэтому всегда соответствуют текущим данным (как и рекорды в [RecordsEngine]).
 *
 * Четыре задачи модуля:
 *  1. динамика тренировочного показателя за период ([dynamics]);
 *  2. сопоставление подготовки с результатом соревнования ([competitionPrep]);
 *  3. анализ и сравнение подготовительных периодов ([periodSummary], [periodComparisonNotes]);
 *  4. сводка по тренировочной и соревновательной деятельности ([competitionSummary], [summaryInsights]).
 *
 * Пороги оценки (±1% — «стабильно», от 5% — «заметно») условные, это справочная оценка, а не диагноз.
 */

/** Границы периода: [start] — начало первого дня, [end] — последняя миллисекунда последнего дня. */
data class AnalysisRange(val start: Long, val end: Long) {
    operator fun contains(t: Long): Boolean = t in start..end
    val days: Int get() = (Math.round((startOfDay(end) - startOfDay(start)) / 86_400_000.0) + 1).toInt()
}

/**
 * Тренировочный показатель: упражнение (с учётом ноги и единицы измерения)
 * либо общий показатель тренировки (интенсивность, длительность).
 */
data class Indicator(
    val key: String,
    val name: String,
    /** Единица измерения упражнения; null — общий показатель со своей подписью [suffix]. */
    val unit: ResultUnit?,
    val suffix: String,
    val lowerIsBetter: Boolean,
    /** Общий показатель: рост не обязательно «лучше», поэтому оценка нейтральная (рост/снижение). */
    val isGeneral: Boolean,
    val sessions: Int
) {
    fun format(value: Double): String =
        if (unit != null) formatRecordValue(value, unit)
        else formatNumber(value) + if (suffix.isBlank()) "" else " $suffix"

    /** Изменение со знаком: «+5 кг», «−0.12 сек». */
    fun formatDelta(delta: Double): String =
        if (unit != null) formatRecordDelta(delta, 0.0, unit)
        else (if (delta >= 0) "+" else "−") + formatNumber(abs(delta)) + if (suffix.isBlank()) "" else " $suffix"
}

data class IndicatorPoint(val date: Long, val value: Double)

enum class Verdict {
    BIG_GAIN, GAIN, STABLE, LOSS, BIG_LOSS, NO_DATA;

    fun label(general: Boolean): String = when (this) {
        BIG_GAIN -> if (general) "Заметный рост" else "Заметное улучшение"
        GAIN -> if (general) "Небольшой рост" else "Небольшое улучшение"
        STABLE -> if (general) "Без существенных изменений" else "Стабильно"
        LOSS -> if (general) "Небольшое снижение" else "Небольшое ухудшение"
        BIG_LOSS -> if (general) "Заметное снижение" else "Заметное ухудшение"
        NO_DATA -> "Недостаточно данных"
    }
}

/** Динамика одного показателя внутри периода. */
data class Dynamics(
    val indicator: Indicator,
    val range: AnalysisRange?,
    val points: List<IndicatorPoint>,
    val first: IndicatorPoint?,
    val last: IndicatorPoint?,
    val best: Double?,
    val average: Double?,
    /** Абсолютное изменение «конец − начало» (со знаком, не зависит от того, что считается лучше). */
    val absChange: Double?,
    /** Относительное изменение, % от значения в начале периода. */
    val relChange: Double?,
    /** Средние первой и последней трети замеров (только при 6+ замерах): сглаживают разовые колебания. */
    val startAvg: Double?,
    val endAvg: Double?,
    val verdict: Verdict,
    /** Изменение в «лучшую» сторону, %: для бега на время снижение значения — это плюс. */
    val improvementPercent: Double?,
    /** 0 — мало замеров, 1 — средне, 2 — достаточно. */
    val confidence: Int,
    val summary: String
) {
    val hasData: Boolean get() = verdict != Verdict.NO_DATA
}

data class Attempt(val distance: Double?, val foul: Boolean)

/** Старт с результатом (если он записан) и разобранными попытками. */
data class CompetitionEntry(
    val competition: Competition,
    val result: Double?,
    val attempts: List<Attempt>
) {
    val date: Long get() = competition.date
    val fouls: Int get() = attempts.count { it.foul }
    val placeNumber: Int?
        get() = competition.placement?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
}

data class LinkObservation(val agree: Int, val total: Int, val text: String)

data class PrepRow(
    val dynamics: Dynamics,
    /** Динамика того же показателя в окне такой же длины перед предыдущим стартом. */
    val previousDynamics: Dynamics?,
    val link: LinkObservation?
)

data class CompetitionPrepAnalysis(
    val entry: CompetitionEntry,
    val previous: CompetitionEntry?,
    val resultChange: Double?,
    val resultChangePercent: Double?,
    val isBestSoFar: Boolean,
    /** Период подготовки (уже обрезан днём перед стартом); null — период пуст. */
    val range: AnalysisRange?,
    val previousRange: AnalysisRange?,
    val trainingsDone: Int,
    val skipped: Int,
    val perWeek: Double?,
    val rows: List<PrepRow>,
    val summary: List<String>
)

enum class PeriodState { UPCOMING, ONGOING, FINISHED }

data class PeriodCompetition(
    val entry: CompetitionEntry,
    /** true — старт проходил внутри периода; false — вскоре после его окончания. */
    val inside: Boolean,
    val daysAfterEnd: Int,
    val changeVsPrevious: Double?
)

data class PeriodSummary(
    val period: TrainingPeriod,
    val state: PeriodState,
    /** Период, ограниченный сегодняшним днём; null — период ещё не начался. */
    val range: AnalysisRange?,
    val trainingsDone: Int,
    val skipped: Int,
    val attendancePercent: Int?,
    val perWeek: Double?,
    val avgIntensity: Double?,
    val totalMinutes: Int,
    val dynamics: List<Dynamics>,
    val competitions: List<PeriodCompetition>
)

data class CompetitionSummary(
    val count: Int,
    val best: CompetitionEntry?,
    val last: CompetitionEntry?,
    val average: Double?,
    /** Последний результат минус первый в выбранном периоде. */
    val changeFirstToLast: Double?,
    val podiums: Int,
    val foulPercent: Int?
)

fun formatPercent(value: Double): String =
    (if (value >= 0) "+" else "−") + String.format(Locale.getDefault(), "%.1f", abs(value)) + "%"

object AnalyticsEngine {

    const val KEY_INTENSITY = "gen|intensity"
    const val KEY_DURATION = "gen|duration"
    private const val DAY_MS = 86_400_000L

    // ───────────────────────────── даты ─────────────────────────────

    fun addDays(millis: Long, n: Int): Long =
        Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

    fun endOfDay(millis: Long): Long = addDays(startOfDay(millis), 1) - 1

    /** Окно подготовки: [days] дней, заканчивающееся накануне даты старта. */
    fun prepRange(competitionDate: Long, days: Int): AnalysisRange {
        val day = startOfDay(competitionDate)
        return AnalysisRange(addDays(day, -days), endOfDay(addDays(day, -1)))
    }

    private fun dayLabel(millis: Long): String = SimpleDateFormat("d MMM", Locale("ru")).format(millis)

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val a = abs(n)
        return when {
            a % 100 in 11..14 -> many
            a % 10 == 1 -> one
            a % 10 in 2..4 -> few
            else -> many
        }
    }

    // ───────────────────────────── показатели ─────────────────────────────

    fun exerciseKey(name: String, isSingleLeg: Boolean, legSide: String?, unit: ResultUnit): String =
        "${name.trim().lowercase()}|$isSingleLeg|$legSide|$unit"

    /** Лучшее значение внутри одного упражнения: среди подходов и итога (для бега — меньшее). */
    fun bestValue(r: ExerciseResult, lowerBetter: Boolean): Double? {
        val c = mutableListOf<Double>()
        r.setResultsJson?.split(",")?.mapNotNullTo(c) { it.trim().toDoubleOrNull()?.takeIf { v -> v > 0 } }
        if (r.result > 0) c.add(r.result)
        if (c.isEmpty()) return null
        return if (lowerBetter) c.min() else c.max()
    }

    /** Список показателей, по которым есть замеры: сначала упражнения (чаще выполняемые — выше), затем общие. */
    fun indicators(trainings: List<TrainingWithExercises>): List<Indicator> {
        class Acc(val name: String, val unit: ResultUnit, val lower: Boolean, var n: Int)

        val map = LinkedHashMap<String, Acc>()
        var intensityN = 0
        var durationN = 0
        trainings.forEach { t ->
            if (t.training.isSkipped) return@forEach
            if ((t.training.intensity ?: 0) > 0) intensityN++
            if ((t.training.durationMinutes ?: 0) > 0) durationN++
            t.exerciseResults.forEach { r ->
                val name = r.exerciseName.trim()
                if (name.isBlank()) return@forEach
                val lower = RecordsEngine.isLowerBetter(name, r.resultUnit)
                if (bestValue(r, lower) == null) return@forEach
                val key = exerciseKey(name, r.isSingleLeg, r.legSide, r.resultUnit)
                map.getOrPut(key) {
                    Acc(name + RecordsEngine.legSuffix(r.isSingleLeg, r.legSide), r.resultUnit, lower, 0)
                }.n++
            }
        }
        val exercises = map.map { (key, a) ->
            Indicator(key, a.name, a.unit, "", a.lower, false, a.n)
        }.sortedWith(compareByDescending<Indicator> { it.sessions }.thenBy { it.name })

        val general = mutableListOf<Indicator>()
        if (intensityN > 0) general.add(Indicator(KEY_INTENSITY, "Интенсивность тренировок", null, "из 10", false, true, intensityN))
        if (durationN > 0) general.add(Indicator(KEY_DURATION, "Длительность тренировок", null, "мин", false, true, durationN))
        return exercises + general
    }

    /** Все замеры показателя по тренировкам, от старых к новым. */
    fun points(indicator: Indicator, trainings: List<TrainingWithExercises>): List<IndicatorPoint> {
        val out = mutableListOf<IndicatorPoint>()
        trainings.forEach { t ->
            if (t.training.isSkipped) return@forEach
            when (indicator.key) {
                KEY_INTENSITY -> t.training.intensity?.takeIf { it > 0 }
                    ?.let { out.add(IndicatorPoint(t.training.date, it.toDouble())) }
                KEY_DURATION -> t.training.durationMinutes?.takeIf { it > 0 }
                    ?.let { out.add(IndicatorPoint(t.training.date, it.toDouble())) }
                else -> t.exerciseResults.forEach { r ->
                    val name = r.exerciseName.trim()
                    if (name.isBlank()) return@forEach
                    if (exerciseKey(name, r.isSingleLeg, r.legSide, r.resultUnit) != indicator.key) return@forEach
                    bestValue(r, indicator.lowerIsBetter)?.let { out.add(IndicatorPoint(r.date, it)) }
                }
            }
        }
        return out.sortedBy { it.date }
    }

    // ───────────────────────────── 1. динамика показателя ─────────────────────────────

    fun dynamics(indicator: Indicator, allPoints: List<IndicatorPoint>, range: AnalysisRange?): Dynamics {
        val pts = allPoints.filter { range == null || it.date in range }.sortedBy { it.date }
        val n = pts.size
        if (n == 0) {
            return Dynamics(
                indicator, range, pts, null, null, null, null, null, null, null, null,
                Verdict.NO_DATA, null, 0, "Нет замеров показателя за выбранный период."
            )
        }
        val first = pts.first()
        val last = pts.last()
        val average = pts.map { it.value }.average()
        val best = if (indicator.lowerIsBetter) pts.minOf { it.value } else pts.maxOf { it.value }
        if (n == 1) {
            return Dynamics(
                indicator, range, pts, first, last, best, average, null, null, null, null,
                Verdict.NO_DATA, null, 0,
                "За период только один замер (${indicator.format(first.value)}, ${dayLabel(first.date)}): для оценки динамики нужно минимум два."
            )
        }

        val sign = if (indicator.lowerIsBetter) -1.0 else 1.0
        val useThirds = n >= 6
        val k = if (useThirds) maxOf(2, n / 3) else 1
        val startAvg = if (useThirds) pts.take(k).map { it.value }.average() else null
        val endAvg = if (useThirds) pts.takeLast(k).map { it.value }.average() else null
        val base = startAvg ?: first.value
        val end = endAvg ?: last.value
        val improvement = if (base != 0.0) sign * (end - base) / abs(base) * 100.0 else 0.0
        val verdict = when {
            improvement >= 5.0 -> Verdict.BIG_GAIN
            improvement >= 1.0 -> Verdict.GAIN
            improvement > -1.0 -> Verdict.STABLE
            improvement > -5.0 -> Verdict.LOSS
            else -> Verdict.BIG_LOSS
        }
        val absChange = last.value - first.value
        val relChange = if (first.value != 0.0) absChange / abs(first.value) * 100.0 else null
        val confidence = when {
            n >= 6 -> 2
            n >= 3 -> 1
            else -> 0
        }

        val sb = StringBuilder()
        sb.append(verdict.label(indicator.isGeneral)).append(": с ")
            .append(indicator.format(first.value)).append(" до ").append(indicator.format(last.value))
            .append(" (").append(indicator.formatDelta(absChange))
        if (relChange != null) sb.append(", ").append(formatPercent(relChange))
        sb.append(").")
        if (useThirds) sb.append(" Оценка сделана по средним значениям первой и последней трети замеров, чтобы сгладить разовые колебания.")
        if (confidence == 0) sb.append(" Замеров мало, оценка ориентировочная.")

        return Dynamics(
            indicator, range, pts, first, last, best, average, absChange, relChange,
            startAvg, endAvg, verdict, improvement, confidence, sb.toString()
        )
    }

    /** Линейный тренд по замерам: значения на первой и последней дате; null, если замеров меньше трёх. */
    fun trendLine(points: List<IndicatorPoint>): Pair<Double, Double>? {
        if (points.size < 3) return null
        val t0 = points.first().date
        val xs = points.map { (it.date - t0) / DAY_MS.toDouble() }
        val ys = points.map { it.value }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den == 0.0) return null
        val slope = num / den
        val intercept = my - slope * mx
        return intercept to (intercept + slope * xs.last())
    }

    // ───────────────────────────── соревнования ─────────────────────────────

    /** Попытки из строки «15.12, 15.31, X, -»: X — фол, «-» — попытка не выполнялась. */
    fun parseAttempts(json: String?): List<Attempt> {
        if (json.isNullOrBlank()) return emptyList()
        val out = mutableListOf<Attempt>()
        json.split(Regex("[;,]")).forEach { raw ->
            val t = raw.trim()
            if (t.isEmpty() || t == "-" || t == "—") return@forEach
            val v = t.toDoubleOrNull()
            when {
                v != null && v > 0 -> out.add(Attempt(v, false))
                t.equals("x", ignoreCase = true) || t.equals("х", ignoreCase = true) || t.startsWith("фол", ignoreCase = true) ->
                    out.add(Attempt(null, true))
            }
        }
        return out
    }

    /** Все состоявшиеся и предстоящие старты (кроме пропущенных и отменённых), от старых к новым. */
    fun competitionEntries(competitions: List<Competition>, results: List<TripleJumpResult>): List<CompetitionEntry> =
        competitions
            .filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }
            .map { c ->
                val attempts = parseAttempts(c.attemptsJson)
                val recorded = results
                    .filter { it.competitionId == c.id && it.result > 0 && !it.isAntiRecord && it.resultUnit == ResultUnit.M }
                    .maxOfOrNull { it.result }
                val result = recorded ?: attempts.mapNotNull { it.distance }.maxOrNull()
                CompetitionEntry(c, result, attempts)
            }
            .sortedBy { it.date }

    /** Предыдущий по дате старт с результатом. */
    fun previousOf(entry: CompetitionEntry, entries: List<CompetitionEntry>): CompetitionEntry? =
        entries.filter { it.result != null && startOfDay(it.date) < startOfDay(entry.date) }.maxByOrNull { it.date }

    fun competitionSummary(entries: List<CompetitionEntry>): CompetitionSummary {
        val withResult = entries.filter { it.result != null }.sortedBy { it.date }
        if (withResult.isEmpty()) return CompetitionSummary(0, null, null, null, null, 0, null)
        val attempts = withResult.sumOf { it.attempts.size }
        val fouls = withResult.sumOf { it.fouls }
        return CompetitionSummary(
            count = withResult.size,
            best = withResult.maxByOrNull { it.result!! },
            last = withResult.last(),
            average = withResult.map { it.result!! }.average(),
            changeFirstToLast = if (withResult.size >= 2) withResult.last().result!! - withResult.first().result!! else null,
            podiums = withResult.count { (it.placeNumber ?: Int.MAX_VALUE) <= 3 },
            foulPercent = if (attempts > 0) fouls * 100 / attempts else null
        )
    }

    // ───────────────────────────── 2. подготовка ↔ результат старта ─────────────────────────────

    /**
     * Сопоставление динамики выбранных показателей в [range] с результатом старта [target].
     * Окно подготовки обрезается днём перед стартом. Для сравнения тот же показатель считается
     * в окне такой же длины перед предыдущим стартом.
     */
    fun competitionPrep(
        target: CompetitionEntry,
        entries: List<CompetitionEntry>,
        trainings: List<TrainingWithExercises>,
        indicators: List<Indicator>,
        range: AnalysisRange
    ): CompetitionPrepAnalysis {
        val withResult = entries.filter { it.result != null }
        val previous = previousOf(target, withResult)
        val result = target.result
        val resultChange = if (result != null && previous?.result != null) result - previous.result else null
        val resultPercent = if (resultChange != null && previous?.result != null && previous.result != 0.0)
            resultChange / previous.result * 100.0 else null
        val isBest = result != null && withResult.filter { startOfDay(it.date) < startOfDay(target.date) }
            .all { it.result!! <= result } && withResult.any { startOfDay(it.date) < startOfDay(target.date) }

        val lastAllowed = endOfDay(addDays(startOfDay(target.date), -1))
        val clipped = AnalysisRange(range.start, minOf(range.end, lastAllowed))
        val effective = if (clipped.start <= clipped.end) clipped else null
        val length = effective?.days ?: range.days.coerceAtLeast(1)
        val prevRange = previous?.let { prepRange(it.date, length) }

        val inWindow = if (effective == null) emptyList() else trainings.filter { it.training.date in effective }
        val done = inWindow.count { !it.training.isSkipped }
        val skipped = inWindow.count { it.training.isSkipped }
        val perWeek = if (effective != null && done > 0) done / (effective.days / 7.0) else null

        val rows = indicators.map { ind ->
            val pts = points(ind, trainings)
            val dyn = dynamics(ind, pts, effective ?: range)
            val prevDyn = prevRange?.let { dynamics(ind, pts, it) }
            PrepRow(dyn, prevDyn, linkObservation(ind, pts, withResult, length))
        }

        val summary = mutableListOf<String>()
        if (result != null) {
            val sb = StringBuilder("Результат ${formatRecordValue(result, ResultUnit.M)}")
            if (previous?.result != null && resultChange != null) {
                sb.append(": ").append(formatRecordDelta(result, previous.result, ResultUnit.M))
                if (resultPercent != null) sb.append(" (").append(formatPercent(resultPercent)).append(")")
                sb.append(" к предыдущему старту «${previous.competition.name}» (${formatRecordValue(previous.result, ResultUnit.M)}).")
            } else {
                sb.append(". Это первый старт с записанным результатом, сравнивать пока не с чем.")
            }
            summary.add(sb.toString())
            if (isBest) summary.add("Это лучший результат среди всех записанных стартов.")
        } else {
            summary.add("Результат этого старта не записан: показана только динамика подготовки.")
        }
        if (effective != null) {
            summary.add(
                "Подготовка: ${dayLabel(effective.start)} — ${dayLabel(effective.end)} (${effective.days} ${plural(effective.days, "день", "дня", "дней")}), " +
                    "$done ${plural(done, "тренировка", "тренировки", "тренировок")}" +
                    (if (perWeek != null) ", ${String.format(Locale.getDefault(), "%.1f", perWeek)} в неделю" else "") +
                    (if (skipped > 0) ", пропусков: $skipped" else "") + "."
            )
        } else {
            summary.add("Выбранный период заканчивается не раньше даты старта: подготовка до старта не определена.")
        }
        val withData = rows.filter { it.dynamics.hasData }
        if (withData.isNotEmpty()) {
            val better = withData.filter { it.dynamics.verdict == Verdict.BIG_GAIN || it.dynamics.verdict == Verdict.GAIN }
            val worse = withData.filter { it.dynamics.verdict == Verdict.LOSS || it.dynamics.verdict == Verdict.BIG_LOSS }
            val flat = withData.filter { it.dynamics.verdict == Verdict.STABLE }
            val parts = mutableListOf<String>()
            if (better.isNotEmpty()) parts.add("выросли/улучшились: " + better.joinToString { it.dynamics.indicator.name })
            if (worse.isNotEmpty()) parts.add("снизились/ухудшились: " + worse.joinToString { it.dynamics.indicator.name })
            if (flat.isNotEmpty()) parts.add("без изменений: " + flat.joinToString { it.dynamics.indicator.name })
            summary.add("Показатели за период подготовки — " + parts.joinToString("; ") + ".")
        }
        return CompetitionPrepAnalysis(
            target, previous, resultChange, resultPercent, isBest, effective, prevRange,
            done, skipped, perWeek, rows, summary
        )
    }

    /**
     * Совпадала ли по всем стартам динамика показателя в окне подготовки с изменением результата
     * относительно предыдущего старта. Нужно минимум два сравнения; вывод — лишь наблюдение.
     */
    fun linkObservation(
        indicator: Indicator,
        points: List<IndicatorPoint>,
        entriesWithResult: List<CompetitionEntry>,
        windowDays: Int
    ): LinkObservation? {
        val sorted = entriesWithResult.filter { it.result != null }.sortedBy { it.date }
        var agree = 0
        var total = 0
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            val prev = sorted[i - 1]
            val dyn = dynamics(indicator, points, prepRange(cur.date, windowDays))
            val imp = dyn.improvementPercent ?: continue
            if (abs(imp) < 1.0) continue
            val delta = cur.result!! - prev.result!!
            if (abs(delta) < 1e-9) continue
            total++
            if ((imp > 0) == (delta > 0)) agree++
        }
        if (total < 2) return null
        return LinkObservation(
            agree, total,
            "В $agree из $total ${plural(total, "сравнения", "сравнений", "сравнений")} динамика показателя совпала с изменением результата на старте. " +
                "Данных мало: это наблюдение, а не доказанная связь."
        )
    }

    // ───────────────────────────── 3. подготовительные периоды ─────────────────────────────

    fun periodSummary(
        period: TrainingPeriod,
        trainings: List<TrainingWithExercises>,
        entries: List<CompetitionEntry>,
        indicators: List<Indicator>,
        today: Long,
        afterDays: Int = 14
    ): PeriodSummary {
        val start = startOfDay(period.startDate)
        val end = startOfDay(period.endDate)
        val todayDay = startOfDay(today)
        val state = when {
            start > todayDay -> PeriodState.UPCOMING
            end < todayDay -> PeriodState.FINISHED
            else -> PeriodState.ONGOING
        }
        val range = if (state == PeriodState.UPCOMING) null
        else AnalysisRange(start, endOfDay(minOf(end, todayDay)))

        val inRange = if (range == null) emptyList() else trainings.filter { it.training.date in range }
        val doneList = inRange.filter { !it.training.isSkipped }
        val done = doneList.size
        val skipped = inRange.size - done
        val intens = doneList.mapNotNull { it.training.intensity?.takeIf { v -> v > 0 } }
        val dynamics = indicators.map { dynamics(it, points(it, trainings), range) }

        val withResult = entries.filter { it.result != null }
        val comps = entries.mapNotNull { e ->
            val day = startOfDay(e.date)
            val inside = day in start..end
            val after = Math.round((day - end) / 86_400_000.0).toInt()
            if (!inside && after !in 1..afterDays) return@mapNotNull null
            val prev = previousOf(e, withResult)
            val change = if (e.result != null && prev?.result != null) e.result - prev.result else null
            PeriodCompetition(e, inside, if (inside) 0 else after, change)
        }.sortedBy { it.entry.date }

        return PeriodSummary(
            period = period,
            state = state,
            range = range,
            trainingsDone = done,
            skipped = skipped,
            attendancePercent = if (done + skipped > 0) done * 100 / (done + skipped) else null,
            perWeek = if (range != null && done > 0) done / (range.days / 7.0) else null,
            avgIntensity = if (intens.isNotEmpty()) intens.average() else null,
            totalMinutes = doneList.sumOf { it.training.durationMinutes ?: 0 },
            dynamics = dynamics,
            competitions = comps
        )
    }

    /** Текстовая сводка по одному периоду. */
    fun periodNarrative(s: PeriodSummary): String {
        if (s.range == null) return "Период ещё не начался."
        val parts = mutableListOf<String>()
        val base = StringBuilder("${s.trainingsDone} ${plural(s.trainingsDone, "тренировка", "тренировки", "тренировок")} за ${s.range.days} ${plural(s.range.days, "день", "дня", "дней")}")
        if (s.perWeek != null) base.append(" (${String.format(Locale.getDefault(), "%.1f", s.perWeek)} в неделю)")
        if (s.attendancePercent != null) base.append(", посещаемость ${s.attendancePercent}%")
        parts.add(base.append(".").toString())
        val withData = s.dynamics.filter { it.hasData }
        if (withData.isNotEmpty()) {
            val better = withData.count { it.verdict == Verdict.BIG_GAIN || it.verdict == Verdict.GAIN }
            val worse = withData.count { it.verdict == Verdict.LOSS || it.verdict == Verdict.BIG_LOSS }
            parts.add("Из ${withData.size} выбранных показателей улучшились или выросли: $better, ухудшились или снизились: $worse.")
        }
        s.competitions.firstOrNull { it.entry.result != null }?.let { c ->
            val when_ = if (c.inside) "Внутри периода состоялся старт" else "Через ${c.daysAfterEnd} ${plural(c.daysAfterEnd, "день", "дня", "дней")} после периода состоялся старт"
            val change = c.changeVsPrevious?.let { ", ${formatRecordDelta(c.entry.result!!, c.entry.result - it, ResultUnit.M)} к предыдущему старту" } ?: ""
            parts.add("$when_ «${c.entry.competition.name}»: ${formatRecordValue(c.entry.result!!, ResultUnit.M)}$change.")
        }
        return parts.joinToString(" ")
    }

    /** Выводы при сравнении нескольких периодов. */
    fun periodComparisonNotes(summaries: List<PeriodSummary>): List<String> {
        val active = summaries.filter { it.range != null && it.trainingsDone > 0 }
        if (active.size < 2) return emptyList()
        val notes = mutableListOf<String>()
        active.filter { it.perWeek != null }.maxByOrNull { it.perWeek!! }?.let {
            notes.add("Самая высокая частота тренировок — «${it.period.name}»: ${String.format(Locale.getDefault(), "%.1f", it.perWeek)} в неделю.")
        }
        active.filter { it.attendancePercent != null }.maxByOrNull { it.attendancePercent!! }?.let {
            notes.add("Лучшая посещаемость — «${it.period.name}»: ${it.attendancePercent}%.")
        }
        val names = active.first().dynamics.indices
        for (i in names) {
            val ind = active.first().dynamics[i].indicator
            val cmp = active.mapNotNull { s -> s.dynamics.getOrNull(i)?.takeIf { it.hasData && it.improvementPercent != null }?.let { s to it } }
            if (cmp.size >= 2) {
                val bestOne = cmp.maxByOrNull { it.second.improvementPercent!! }!!
                notes.add("«${ind.name}» сильнее всего изменился к лучшему в периоде «${bestOne.first.period.name}» (${formatPercent(bestOne.second.improvementPercent!!)}).")
            }
        }
        return notes
    }

    // ───────────────────────────── 4. сводка ─────────────────────────────

    /** Динамика основных показателей (самых частых упражнений), от лучшей к худшей. */
    fun indicatorChanges(
        trainings: List<TrainingWithExercises>,
        range: AnalysisRange?,
        limit: Int = 6
    ): List<Dynamics> =
        indicators(trainings).filter { !it.isGeneral }.take(limit)
            .map { dynamics(it, points(it, trainings), range) }
            .filter { it.hasData }
            .sortedByDescending { it.improvementPercent ?: 0.0 }

    /** Короткие выводы для экрана «Сводка». */
    fun summaryInsights(
        done: Int,
        skipped: Int,
        changes: List<Dynamics>,
        competitions: CompetitionSummary
    ): List<String> {
        val out = mutableListOf<String>()
        if (done + skipped > 0) {
            val att = done * 100 / (done + skipped)
            out.add("Выполнено $done ${plural(done, "тренировка", "тренировки", "тренировок")}, пропусков: $skipped, посещаемость $att%.")
        }
        if (changes.isNotEmpty()) {
            val better = changes.count { it.verdict == Verdict.BIG_GAIN || it.verdict == Verdict.GAIN }
            val worse = changes.count { it.verdict == Verdict.LOSS || it.verdict == Verdict.BIG_LOSS }
            val top = changes.first()
            var s = "Из ${changes.size} основных показателей улучшились: $better, ухудшились: $worse."
            if ((top.improvementPercent ?: 0.0) >= 1.0) s += " Лучшая динамика — «${top.indicator.name}» (${formatPercent(top.relChange ?: 0.0)})."
            val bottom = changes.last()
            if (changes.size > 1 && (bottom.improvementPercent ?: 0.0) <= -1.0) s += " Хуже всего — «${bottom.indicator.name}» (${formatPercent(bottom.relChange ?: 0.0)})."
            out.add(s)
        }
        if (competitions.count > 0) {
            val sb = StringBuilder("Стартов с результатом: ${competitions.count}, лучший — ${formatRecordValue(competitions.best!!.result!!, ResultUnit.M)}")
            val lastResult = competitions.last?.result
            if (competitions.changeFirstToLast != null && lastResult != null) {
                sb.append(", от первого к последнему ${formatRecordDelta(lastResult, lastResult - competitions.changeFirstToLast, ResultUnit.M)}")
            }
            out.add(sb.append(".").toString())
        }
        return out
    }
}
