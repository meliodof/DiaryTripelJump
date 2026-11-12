package com.example.diarytripeljump.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Аналитические модели дневника, которые работают на телефоне без сети:
 *  - прогноз результата к дате (линейная регрессия с интервалом прогноза);
 *  - связь упражнений с результатом в тройном через несколько недель (корреляция со сдвигом);
 *  - резкий спад относительно своей нормы (робастная оценка по медиане);
 *  - нагрузка по времени тренировок (отношение недели к среднему за месяц).
 * Это обычные статистические модели: у каждого вывода видно, на каких данных и с какой надёжностью он сделан.
 */

/** Насколько можно доверять прогнозу. */
enum class Reliability(val label: String) { HIGH("высокая"), MEDIUM("средняя"), LOW("низкая") }

/** На чём построен прогноз — это объясняется человеку простыми словами. */
enum class ForecastMethod {
    /** Своих результатов достаточно: среднее последнего результата и линии по результатам за год. */
    OWN_DATA,
    /** Своих результатов мало: последний результат (или рекорд из профиля) плюс типичный прирост по возрасту. */
    TYPICAL_GROWTH,
    /** Своих результатов мало и типичного прироста нет: ожидается последний результат. */
    LAST_RESULT
}

/** Прогноз результата к дате [targetDate], если подготовка пойдёт как раньше. */
data class Forecast(
    val key: String,
    val title: String,
    val unit: ResultUnit,
    val lowerIsBetter: Boolean,
    val targetDate: Long,
    val targetLabel: String,
    val value: Double,
    /** Как меняется результат за неделю сейчас; знак — как у значений. */
    val slopePerWeek: Double,
    val points: Int,
    val reliability: Reliability,
    val method: ForecastMethod,
    /** Точки, по которым построен прогноз. */
    val history: List<RecordPoint>,
    /** Цель, к сроку которой построен прогноз; null — прогноз к старту. */
    val goal: Double? = null,
    val recordKey: String? = null,
    /** Прирост в год, заложенный при холодном старте (доля), — для пояснения. */
    val typicalGrowth: Double = 0.0,
    /** Прогноз выбран пользователем (его можно убрать). */
    val custom: Boolean = false,
    /** Своя единица для подписи («ст.»), если её нет среди [ResultUnit]. */
    val unitLabel: String? = null,
    /** Показатель визуализации, по которому пользователь сам выбрал прогноз. */
    val seriesId: String? = null
) {
    /** Значение в единицах показателя: «15,05 м», «3 ст.». */
    fun format(v: Double): String = unitLabel?.let { "${formatNumber(v)} $it" } ?: formatRecordValue(v, unit)

    /** Цель будет достигнута по ожидаемому значению. */
    val reachesGoal: Boolean?
        get() = goal?.let { if (lowerIsBetter) value <= it else value >= it }
}

/** Связь показателя упражнения с результатом в тройном через [lagWeeks] недель. */
data class Influence(
    val key: String,
    val title: String,
    val lagWeeks: Int,
    /** Коэффициент корреляции после удаления общего тренда: плюс — растут вместе, минус — в разные стороны. */
    val r: Double,
    val weeks: Int,
    /** Вероятность получить такую связь случайно (перестановочный тест). */
    val p: Double = 1.0
)

/** Что по исследованиям связано с результатом в тройном — справка и подсказка, что стоит записывать. */
data class KnownLink(
    val title: String,
    val finding: String,
    val source: String,
    /** Части названия упражнения, по которым оно находится в дневнике. */
    val keywords: List<String>
)

/** Всё, что показывает раздел «Советы и прогноз» помимо советов. */
data class InsightsResult(val forecasts: List<Forecast>, val influences: List<Influence>)

object Insights {
    private const val DAY = 86_400_000L
    private const val WEEK = 7 * DAY

    object Limits {
        /** Прогноз строится по последним точкам за полгода до даты прогноза. */
        const val FORECAST_POINTS = 30
        const val FORECAST_WINDOW_DAYS = 180
        const val FORECAST_MIN_POINTS = 6
        const val FORECAST_MIN_SPAN_DAYS = 21
        /** Прогноз дальше, чем на столько недель, без старта не строится. */
        const val DEFAULT_HORIZON_WEEKS = 8

        /**
         * Связь ищется в ту же неделю и со сдвигом до 6 недель; нужно не меньше 8 общих недель.
         * Подтверждается перестановочным тестом ([PERMUTATIONS] перестановок) с долей ложных находок [FDR].
         */
        const val MAX_LAG_WEEKS = 6
        const val MIN_PAIRS = 8
        const val MIN_R = 0.3
        const val PERMUTATIONS = 499
        const val FDR = 0.05

        /** Резкий спад: отклонение по медиане не меньше 3 «типичных разбросов». */
        const val ANOMALY_Z = 3.0
        const val ANOMALY_MIN_POINTS = 8
        const val ANOMALY_FRESH_DAYS = 14

        /** Нагрузка по времени: неделя больше среднего за 4 недели в 1,5 раза. */
        const val ACWR_HIGH = 1.5
        const val ACWR_MIN_TRAININGS = 4
    }

    // =================================================================
    // Прогноз
    // =================================================================

    /** Линейная регрессия y = a + b·x с интервалом прогноза в точке. */
    data class Fit(val a: Double, val b: Double, val n: Int, val s: Double, val xMean: Double, val sxx: Double, val r2: Double) {
        fun at(x: Double) = a + b * x

        /** Половина ширины 95 % интервала прогноза для нового наблюдения в точке [x]. */
        fun margin(x: Double): Double = tQuantile(n - 2) * s * sqrt(1.0 + 1.0 / n + (x - xMean) * (x - xMean) / sxx)
    }

    fun fit(xs: List<Double>, ys: List<Double>): Fit? {
        val n = xs.size
        if (n < 3 || n != ys.size) return null
        val xm = xs.average()
        val ym = ys.average()
        val sxx = xs.sumOf { (it - xm) * (it - xm) }
        if (sxx <= 0.0) return null
        val sxy = xs.indices.sumOf { (xs[it] - xm) * (ys[it] - ym) }
        val b = sxy / sxx
        val a = ym - b * xm
        val sse = xs.indices.sumOf { val e = ys[it] - (a + b * xs[it]); e * e }
        val sst = ys.sumOf { (it - ym) * (it - ym) }
        val s = sqrt(sse / (n - 2))
        val r2 = if (sst > 0) (1 - sse / sst).coerceIn(0.0, 1.0) else 0.0
        return Fit(a, b, n, s, xm, sxx, r2)
    }

    /** Квантиль распределения Стьюдента для 95 % (двусторонний) по числу степеней свободы. */
    fun tQuantile(df: Int): Double {
        val table = doubleArrayOf(12.71, 4.30, 3.18, 2.78, 2.57, 2.45, 2.36, 2.31, 2.26, 2.23,
            2.20, 2.18, 2.16, 2.14, 2.13, 2.12, 2.11, 2.10, 2.09, 2.09)
        return when {
            df < 1 -> table[0]
            df <= table.size -> table[df - 1]
            df <= 30 -> 2.05
            else -> 1.96
        }
    }

    /**
     * Типичный прирост результата в тройном за год по возрасту — для прогноза, когда своих данных мало.
     * По исследованиям юных прыгунов тройным: до 18 лет в среднем +1,5…6,3 % в год, в 18–23 года прирост
     * замедляется, у взрослых результат в среднем держится на месте (−2…+2 %). Возраст неизвестен — как у 18–23 лет.
     */
    fun typicalGrowthPerYear(age: Int?): Double = when {
        age == null -> 0.015
        age < 18 -> 0.04
        age < 24 -> 0.015
        else -> 0.0
    }

    /**
     * Лучший результат каждого дня за полгода до даты прогноза [target] (но не позже [now]), не больше
     * [Limits.FORECAST_POINTS] последних. Окно выбрано проверкой на реальных рядах: год и 9 месяцев от сегодня
     * на части спортсменов прогнозировали хуже, чем «как в прошлый раз».
     */
    private fun daily(points: List<RecordPoint>, lowerIsBetter: Boolean, now: Long, target: Long) = points
        .filter { it.date in (target - Limits.FORECAST_WINDOW_DAYS * DAY)..now }
        .groupBy { it.date / DAY }
        .map { (_, ps) -> if (lowerIsBetter) ps.minBy { it.value } else ps.maxBy { it.value } }
        .sortedBy { it.date }
        .takeLast(Limits.FORECAST_POINTS)

    /**
     * Прогноз ряда [points] к дате [target].
     *
     * Своих результатов достаточно (от [Limits.FORECAST_MIN_POINTS] дней на отрезке от
     * [Limits.FORECAST_MIN_SPAN_DAYS] дней) — среднее двух оценок: последнего результата и линейной регрессии по
     * результатам за полгода до даты прогноза. Скользящая проверка на трёх реальных рядах тройного (TFRRS, 137 стартов
     * трёх спортсменов NCAA): средняя ошибка прогноза следующего старта 0,228 / 0,364 / 0,488 м против
     * 0,236 / 0,396 / 0,492 м у «как на прошлом старте» — лучше у всех троих, в среднем на 4 %.
     *
     * Холодный старт — результатов мало: от последнего результата (или от [fallbackLevel], например рекорда из профиля)
     * с типичным приростом [growthPerYear] за год. Нет ни результатов, ни запасного уровня — null.
     */
    fun forecast(
        key: String, title: String, unit: ResultUnit, lowerIsBetter: Boolean, points: List<RecordPoint>,
        target: Long, targetLabel: String, now: Long, goal: Double? = null, recordKey: String? = null,
        growthPerYear: Double = 0.0, fallbackLevel: Double? = null, custom: Boolean = false
    ): Forecast? {
        val daily = daily(points, lowerIsBetter, now, target).ifEmpty {
            // в окне ничего нет — для холодного старта годится и более старый последний результат
            listOfNotNull(points.filter { it.date <= now }.maxByOrNull { it.date })
        }
        val enough = daily.size >= Limits.FORECAST_MIN_POINTS &&
            (daily.last().date - daily.first().date) / DAY.toDouble() >= Limits.FORECAST_MIN_SPAN_DAYS
        if (enough) {
            val first = daily.first().date
            val spanDays = (daily.last().date - first) / DAY.toDouble()
            val xs = daily.map { (it.date - first) / DAY.toDouble() }
            val f = fit(xs, daily.map { it.value })
            if (f != null) {
                val x0 = (target - first) / DAY.toDouble()
                val value = (daily.last().value + f.at(x0)) / 2
                val horizon = x0 - xs.last()
                val reliability = when {
                    daily.size >= 10 && f.r2 >= 0.5 && horizon <= spanDays * 0.5 -> Reliability.HIGH
                    f.r2 >= 0.25 && horizon <= spanDays -> Reliability.MEDIUM
                    else -> Reliability.LOW
                }
                return Forecast(
                    key = key, title = title, unit = unit, lowerIsBetter = lowerIsBetter, targetDate = target,
                    targetLabel = targetLabel, value = value, slopePerWeek = f.b * 7, points = daily.size,
                    reliability = reliability, method = ForecastMethod.OWN_DATA, history = daily, goal = goal,
                    recordKey = recordKey, custom = custom
                )
            }
        }
        // холодный старт: своих результатов мало
        val base = daily.lastOrNull()?.value ?: fallbackLevel ?: return null
        val from = daily.lastOrNull()?.date ?: now
        val years = ((target - from) / (365.0 * DAY)).coerceAtLeast(0.0)
        val growth = base * growthPerYear * years
        return Forecast(
            key = key, title = title, unit = unit, lowerIsBetter = lowerIsBetter, targetDate = target,
            targetLabel = targetLabel, value = if (lowerIsBetter) base - growth else base + growth,
            slopePerWeek = (if (lowerIsBetter) -1 else 1) * base * growthPerYear / 52, points = daily.size,
            reliability = Reliability.LOW,
            method = if (growthPerYear > 0) ForecastMethod.TYPICAL_GROWTH else ForecastMethod.LAST_RESULT,
            history = daily, goal = goal, recordKey = recordKey, typicalGrowth = growthPerYear, custom = custom
        )
    }

    // =================================================================
    // Связь упражнений с результатом
    // =================================================================

    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        if (n < 3 || n != ys.size) return null
        val xm = xs.average()
        val ym = ys.average()
        val sxy = xs.indices.sumOf { (xs[it] - xm) * (ys[it] - ym) }
        val sxx = xs.sumOf { (it - xm) * (it - xm) }
        val syy = ys.sumOf { (it - ym) * (it - ym) }
        // почти нулевой разброс (ряд без колебаний после удаления тренда) — связи нет, а не случайный шум
        if (sxx < 1e-12 || syy < 1e-12) return null
        return sxy / sqrt(sxx * syy)
    }

    /** Остатки после линейного тренда по номеру недели: общий рост за сезон не должен давать ложную связь. */
    private fun detrend(weeks: List<Long>, values: List<Double>): List<Double> {
        val f = fit(weeks.map { it.toDouble() }, values) ?: return values.map { it - values.average() }
        return weeks.indices.map { values[it] - f.at(weeks[it].toDouble()) }
    }

    /** Лучший результат каждой недели; для «меньше — лучше» знак меняется, чтобы рост всегда означал улучшение. */
    private fun weekly(points: List<RecordPoint>, lowerIsBetter: Boolean): Map<Long, Double> =
        points.groupBy { it.date / WEEK }.mapValues { (_, ps) ->
            if (lowerIsBetter) -ps.minOf { it.value } else ps.maxOf { it.value }
        }

    /** Самая сильная связь упражнения [ex] с тройным [target] среди сдвигов 0…[Limits.MAX_LAG_WEEKS] недель. */
    private fun strongestLag(ex: Map<Long, Double>, target: Map<Long, Double>): Influence? =
        (0..Limits.MAX_LAG_WEEKS).mapNotNull { lag ->
            val weeks = ex.keys.filter { target.containsKey(it + lag) }.sorted()
            if (weeks.size < Limits.MIN_PAIRS) return@mapNotNull null
            val xs = detrend(weeks, weeks.map { ex.getValue(it) })
            val ys = detrend(weeks.map { it + lag }, weeks.map { target.getValue(it + lag) })
            val corr = pearson(xs, ys) ?: return@mapNotNull null
            Influence("", "", lag, corr, weeks.size)
        }.maxByOrNull { abs(it.r) }

    /**
     * Перестановочный тест: как часто случайный порядок недель упражнения даёт связь не слабее найденной,
     * если так же выбирать лучший сдвиг. Учитывает перебор сдвигов, поэтому на чистом шуме ошибается примерно в 5 %
     * случаев (старое правило «|r| ≥ 0,5 и t ≥ 2 по лучшему сдвигу» — в 42 %, см. проверку в документации).
     */
    private fun permutationP(ex: Map<Long, Double>, target: Map<Long, Double>, observed: Double, seed: Int): Double {
        val keys = ex.keys.sorted()
        val values = keys.map { ex.getValue(it) }.toMutableList()
        val rnd = java.util.Random(seed.toLong())
        var hits = 0
        repeat(Limits.PERMUTATIONS) {
            java.util.Collections.shuffle(values, rnd)
            val shuffled = keys.zip(values).toMap()
            val r = strongestLag(shuffled, target)?.r ?: 0.0
            if (abs(r) >= abs(observed)) hits++
        }
        return (hits + 1.0) / (Limits.PERMUTATIONS + 1)
    }

    /**
     * Упражнения, колебания которых совпадают с колебаниями тройного в ту же неделю или через 1–6 недель.
     * Оба ряда очищаются от общего тренда; связь подтверждается перестановочным тестом, а поправка
     * Бенджамини–Хохберга (доля ложных находок [Limits.FDR]) не даёт множеству упражнений породить случайные связи.
     */
    fun influences(jump: List<RecordPoint>, records: List<RecordSummary>): List<Influence> {
        val target = weekly(jump, lowerIsBetter = false)
        if (target.size < Limits.MIN_PAIRS) return emptyList()
        val candidates = records
            .filter { !it.isAnti && !RecordInsights.isMainJump(it) && it.points.size >= Limits.MIN_PAIRS }
            .mapNotNull { r ->
                val ex = weekly(r.points, r.lowerIsBetter)
                val best = strongestLag(ex, target) ?: return@mapNotNull null
                if (abs(best.r) < Limits.MIN_R) return@mapNotNull null
                val p = permutationP(ex, target, best.r, seed = r.key.hashCode())
                Triple(Influence(r.key, r.displayName, best.lagWeeks, best.r, best.weeks), p, r)
            }
        // поправка на число проверенных упражнений: принимаются p ≤ k/m · FDR для наибольшего такого k
        val m = records.count { !it.isAnti && !RecordInsights.isMainJump(it) && it.points.size >= Limits.MIN_PAIRS }.coerceAtLeast(1)
        val sorted = candidates.sortedBy { it.second }
        val accepted = sorted.indices.lastOrNull { k -> sorted[k].second <= (k + 1).toDouble() / m * Limits.FDR } ?: -1
        return sorted.take(accepted + 1)
            .map { (inf, p, _) -> inf.copy(p = p) }
            .sortedByDescending { abs(it.r) }
            .take(5)
    }

    // =================================================================
    // Советы на основе моделей: резкий спад и нагрузка по времени
    // =================================================================

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Последний свежий результат заметно хуже своей нормы (медиана и медианное отклонение предыдущих). */
    fun anomalies(records: List<RecordSummary>, now: Long): List<Advice> =
        records
            .filter { !it.isAnti && it.points.size >= Limits.ANOMALY_MIN_POINTS }
            .mapNotNull { r ->
                val sorted = r.points.sortedBy { it.date }
                val last = sorted.last()
                if (now - last.date > Limits.ANOMALY_FRESH_DAYS * DAY) return@mapNotNull null
                val prev = sorted.dropLast(1).takeLast(20).map { it.value }
                val med = median(prev)
                val mad = median(prev.map { abs(it - med) })
                if (mad <= 0.0) return@mapNotNull null
                val z = 0.6745 * (last.value - med) / mad
                val worse = if (r.lowerIsBetter) z >= Limits.ANOMALY_Z else z <= -Limits.ANOMALY_Z
                if (!worse) return@mapNotNull null
                abs(z) to Advice(
                    id = "anomaly-${r.key}-${last.date}",
                    kind = AdviceKind.RISK,
                    title = "Резкий спад: ${r.displayName}",
                    text = "Последний результат ${formatRecordValue(last.value, r.unit)} при обычных " +
                        "${formatRecordValue(med, r.unit)}. Такой спад часто бывает от усталости или недовосстановления — " +
                        "стоит снизить нагрузку и проверить самочувствие.",
                    because = "Сравнение с медианой ${prev.size} предыдущих результатов: отклонение " +
                        "${String.format(Locale("ru"), "%.1f", abs(z))} типичных разброса.",
                    route = "records", recordKey = r.key
                )
            }
            .sortedByDescending { it.first }
            .take(2)
            .map { it.second }

    /** Отношение времени тренировок за неделю к среднему за 4 недели (острая и хроническая нагрузка). */
    fun timeLoad(trainings: List<TrainingWithExercises>, now: Long): Advice? {
        val timed = trainings.filter {
            !it.training.isSkipped && (it.training.durationMinutes ?: 0) > 0 && it.training.date in (now - 28 * DAY)..now
        }
        if (timed.size < Limits.ACWR_MIN_TRAININGS) return null
        val acute = timed.filter { it.training.date > now - 7 * DAY }.sumOf { it.training.durationMinutes ?: 0 }
        val chronic = timed.sumOf { it.training.durationMinutes ?: 0 } / 4.0
        if (chronic <= 0 || acute < chronic * Limits.ACWR_HIGH) return null
        val ratio = acute / chronic
        return Advice(
            id = "acwr-${now / WEEK}", kind = AdviceKind.RISK,
            title = "Время тренировок выросло в ${String.format(Locale("ru"), "%.1f", ratio)} раза",
            text = "За неделю ${formatTrainingDuration(acute)} тренировок при среднем ${formatTrainingDuration(chronic.roundToInt())} " +
                "в неделю за месяц. Резкий рост нагрузки повышает риск травмы — стоит добавить восстановление.",
            because = "Отношение острой нагрузки (7 дней) к хронической (среднее за 28 дней) по времени начала и окончания тренировок.",
            route = "trainings"
        )
    }

    // =================================================================
    // Сборка для экрана
    // =================================================================

    private fun shortDate(millis: Long) = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

    /** Ближайший старт: дата и подпись прогноза; нет старта — через [weeks] недель. */
    fun targetFor(competitions: List<Competition>, weeks: Int, now: Long): Pair<Long, String> {
        if (weeks <= 0) {
            val next = competitions
                .filter { it.date >= startOfDay(now) && it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }
                .minByOrNull { it.date }
            if (next != null) return next.date to "к старту «${next.name}», ${shortDate(next.date)}"
        }
        val w = if (weeks <= 0) Limits.DEFAULT_HORIZON_WEEKS else weeks
        val date = now + w * WEEK
        return date to "через ${w} ${if (w % 10 == 1 && w % 100 != 11) "неделю" else if (w % 10 in 2..4 && w % 100 !in 12..14) "недели" else "недель"}, ${shortDate(date)}"
    }

    private fun ageAt(birthDate: Long?, now: Long): Int? = birthDate?.let {
        val b = java.util.Calendar.getInstance().apply { timeInMillis = it }
        val n = java.util.Calendar.getInstance().apply { timeInMillis = now }
        n[java.util.Calendar.YEAR] - b[java.util.Calendar.YEAR] -
            if (n[java.util.Calendar.DAY_OF_YEAR] < b[java.util.Calendar.DAY_OF_YEAR]) 1 else 0
    }

    /**
     * Прогноз тройного к ближайшему старту (или на 8 недель вперёд), прогнозы к срокам целей и по показателям,
     * которые выбрал пользователь ([custom]: показатель и через сколько недель, 0 — к ближайшему старту),
     * плюс связи упражнений с результатом в тройном. [birthDate] задаёт типичный прирост для холодного старта,
     * [profilePb] — запасной уровень тройного, если результатов в дневнике ещё нет.
     */
    fun build(
        records: List<RecordSummary>, startPoints: List<RecordPoint>, competitions: List<Competition>,
        goals: List<ExerciseGoal>, birthDate: Long? = null, profilePb: Double? = null,
        custom: List<Pair<AnalyticsSeries, Int>> = emptyList(), now: Long = System.currentTimeMillis()
    ): InsightsResult {
        // главный тройной: лучший тренировочный рекорд (полный разбег, а не «с 6 шагов») и старты
        val (competitionJump, trainingJump) = RecordInsights.mainJumpSplit(records)
        val jumpPoints = (listOfNotNull(trainingJump, competitionJump).flatMap { it.points } + startPoints)
            .distinctBy { it.date to it.value }
        val (target, label) = targetFor(competitions, 0, now)
        val growth = typicalGrowthPerYear(ageAt(birthDate, now))
        val main = forecast(
            "main-jump", "Тройной прыжок", ResultUnit.M, false, jumpPoints, target, label, now,
            growthPerYear = growth, fallbackLevel = profilePb?.takeIf { it > 0 }
        )

        val goalForecasts = goals.mapNotNull { g ->
            val deadline = g.deadline?.takeIf { it > now } ?: return@mapNotNull null
            val r = records.firstOrNull { it.key == g.recordKey } ?: return@mapNotNull null
            forecast(
                "goal-${g.recordKey}", r.displayName, r.unit, r.lowerIsBetter, r.points, deadline,
                "к сроку цели, ${shortDate(deadline)}", now, goal = g.targetValue, recordKey = r.key,
                growthPerYear = if (RecordInsights.isMainJump(r)) growth else 0.0
            )
        }

        val customForecasts = custom.mapNotNull { (series, weeks) ->
            val (t, l) = targetFor(competitions, weeks, now)
            val points = series.samples.mapNotNull { s ->
                val v = if (series.lowerIsBetter) s.values.minOrNull() else s.values.maxOrNull()
                v?.let { RecordPoint(s.date, it, false) }
            }
            forecast(
                "custom-${series.id}-$weeks", series.title, unitOf(series.unit), series.lowerIsBetter, points, t, l, now,
                custom = true
            )?.let { f -> f.copy(seriesId = series.id, unitLabel = series.unit.takeUnless { it in KNOWN_UNITS }) }
        }
        return InsightsResult(listOfNotNull(main) + goalForecasts + customForecasts, influences(jumpPoints, records))
    }

    private val KNOWN_UNITS = setOf("м", "см", "сек", "с", "кг", "раз")

    /** Единица показателя визуализации («м», «кг»…) как [ResultUnit] для подписи значения. */
    private fun unitOf(short: String): ResultUnit = when (short) {
        "м" -> ResultUnit.M
        "см" -> ResultUnit.CM
        "сек", "с" -> ResultUnit.SECONDS
        "кг" -> ResultUnit.KG
        else -> ResultUnit.REPS
    }

    /**
     * Что по исследованиям связано с результатом в тройном. Это сравнения разных спортсменов: кто сильнее в тесте,
     * тот в среднем дальше прыгает. У одного спортсмена от недели к неделе связь бывает заметно слабее.
     */
    val knownLinks: List<KnownLink> = listOf(
        KnownLink(
            "Пятерной прыжок и многоскоки с разбега",
            "У 68 прыгунов тройным (от юношей до взрослых) результат специального пятерного прыжка объяснил 96 % различий в тройном.",
            "Journal of Human Kinetics, тест «Specific Quintuple»",
            listOf("пятерн", "многоскок", "скачк")
        ),
        KnownLink(
            "Скорость бега 30–100 м",
            "Чем быстрее спортсмен бегает 60 и 100 м, тем дальше его тройной (связь очень сильная, но выборка небольшая).",
            "Hudgins и соавт., 2013 (по обзору исследований)",
            listOf("бег 30", "бег 60", "бег 100", "30 м", "60 м", "100 м", "ускорен")
        ),
        KnownLink(
            "Сила ног в приседе на 1 кг веса тела",
            "Присед по отношению к весу тела заметно связан со скоростью на 30 м и высотой прыжка.",
            "Loturco и соавт., 2021; Wagner и соавт., 2023",
            listOf("присед")
        )
    )

    /** Какие упражнения из [knownLinks] спортсмен уже записывает: по названиям упражнений с результатами. */
    fun trackedKnownLinks(exerciseTitles: List<String>): Map<KnownLink, Boolean> =
        knownLinks.associateWith { link ->
            exerciseTitles.any { title -> link.keywords.any { title.contains(it, ignoreCase = true) } }
        }

    /** Как считаются прогноз и связи — для блока «Как считаются советы». */
    val methods: List<Pair<String, String>>
        get() = listOf(
            "Прогноз" to "Когда результатов достаточно (от ${Limits.FORECAST_MIN_POINTS} дней на отрезке от " +
                "${Limits.FORECAST_MIN_SPAN_DAYS} дней), прогноз — среднее двух оценок: последнего результата и прямой линии по " +
                "лучшим результатам дня за полгода до даты прогноза. Проверка на реальных результатах трёх прыгунов тройным " +
                "(137 стартов, студенческие соревнования США, TFRRS): прогноз следующего старта ошибался в среднем на 23, 36 и " +
                "49 см, а «как в прошлый раз» — на 24, 40 и 49 см. Результат на одном старте сильно колеблется, поэтому прогноз " +
                "— ориентир, а не обещание. Надёжность зависит от числа результатов, их ровности и того, насколько далеко дата.",
            "Мало данных" to "Пока результатов мало, прогноз берёт последний результат (или личный рекорд из профиля) и " +
                "добавляет типичный прирост по возрасту: до 18 лет — 4 % в год, в 18–23 года — 1,5 %, взрослым — 0 %. " +
                "Цифры — по исследованиям юных прыгунов тройным (в среднем +1,5…6,3 % в год до 18 лет, у взрослых результат " +
                "в среднем держится на месте). С каждым новым результатом прогноз всё больше опирается на ваши данные.",
            "Связь с результатом" to "Лучшие результаты недели в упражнении и в тройном очищаются от общего роста за сезон, " +
                "затем ищется самое сильное совпадение в ту же неделю или со сдвигом до ${Limits.MAX_LAG_WEEKS} недель (нужно " +
                "от ${Limits.MIN_PAIRS} общих недель). Связь показывается, только если случайный порядок недель редко даёт " +
                "такую же (перестановочный тест, ${Limits.PERMUTATIONS} перестановок) и с поправкой на число упражнений. " +
                "Проверка: на чистом шуме прежнее правило находило ложную связь в 42 % случаев, новое — в 6 %. На реальных " +
                "результатах двух прыгунов (TFRRS) длина и тройной в одну неделю почти не совпадали, и модель ложной связи " +
                "не показала. Совпадение во времени не доказывает причину.",
            "Резкий спад" to "Последний результат за ${Limits.ANOMALY_FRESH_DAYS} дней сравнивается с медианой предыдущих; " +
                "спадом считается отклонение от ${formatNumber(Limits.ANOMALY_Z)} медианных разбросов.",
            "Нагрузка по времени" to "Время тренировок за 7 дней делится на среднее за 4 недели; от " +
                "${formatNumber(Limits.ACWR_HIGH)} — резкий рост нагрузки."
        )
}
