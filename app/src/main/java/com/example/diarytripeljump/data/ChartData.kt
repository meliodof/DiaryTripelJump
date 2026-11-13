package com.example.diarytripeljump.data

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10

/** Группа показателей в списке выбора. */
enum class SeriesGroup(val label: String) {
    START("Старты"),
    EXERCISE("Упражнения"),
    BODY("Вес и рост"),
    RUN_UP("Разбег")
}

/** Результаты одной тренировки или старта: все попытки этого дня. */
data class SeriesSample(val date: Long, val values: List<Double>)

/** Показатель, который можно вывести на график: попытки по датам и единица измерения. */
data class AnalyticsSeries(
    val id: String,
    val title: String,
    val unit: String,
    val group: SeriesGroup,
    val lowerIsBetter: Boolean,
    val samples: List<SeriesSample>
) {
    val attempts: Int get() = samples.sumOf { it.values.size }
    val last: Double? get() = samples.maxByOrNull { it.date }?.values?.lastOrNull()
}

/** Как значения переводятся в высоту на графике. */
enum class ChartMode(val label: String) {
    ABSOLUTE("Значения"),
    NORMALIZED("0–100 %"),
    CHANGE("Прирост, %")
}

/** Что считается точкой графика: каждая попытка, итог тренировки или итог недели. */
enum class Aggregation(val label: String) {
    EACH("Каждая попытка"),
    SESSION_BEST("Лучшая за тренировку"),
    /** Только тренировки, на которых побит прежний лучший результат: как росли рекорды. */
    RECORDS("Рекордные"),
    SESSION_AVG("Средняя за тренировку"),
    WEEK_BEST("Лучшая за неделю"),
    WEEK_AVG("Средняя за неделю"),
    WEEK_COUNT("Попыток за неделю")
}

enum class LineStyle(val label: String) {
    LINE("Линия"),
    POINTS("Точки"),
    BOTH("Линия и точки")
}

/** Оформление одного показателя на графике. */
data class SeriesStyle(
    val id: String,
    val color: Int,
    val style: LineStyle = LineStyle.BOTH,
    val width: Float = 3f,
    val visible: Boolean = true
) {
    companion object {
        /** Цвет не выбран: берётся из темы приложения по порядку показателя. */
        const val AUTO_COLOR = 0
    }
}

/** Один график: свои показатели и свои настройки. */
data class ChartConfig(
    val id: Long,
    val title: String,
    val series: List<SeriesStyle> = emptyList(),
    val mode: ChartMode = ChartMode.ABSOLUTE,
    val aggregation: Aggregation = Aggregation.SESSION_BEST,
    val smoothing: Boolean = false,
    val fromZero: Boolean = false,
    /** Точки через равные промежутки по порядку, а не по датам: попытки одного дня не слипаются. */
    val byOrder: Boolean = false
)

/** Настройки экрана визуализации; сохраняются между запусками. */
data class VisualizationConfig(
    val charts: List<ChartConfig> = listOf(ChartConfig(id = 1, title = "График 1")),
    val range: ChartRange = ChartRange.YEAR
) {
    fun encode(): String = range.name + CHART + charts.joinToString(CHART) { c ->
        listOf(c.id, c.title.replace(Regex("[\u001C-\u001F]"), " "), c.mode.name, c.aggregation.name, c.smoothing, c.fromZero, c.byOrder)
            .joinToString(FIELD) + GROUP + c.series.joinToString(GROUP) { s ->
            listOf(s.id, s.color, s.style.name, s.width, s.visible).joinToString(FIELD)
        }
    }

    companion object {
        private const val FIELD = "\u001F"
        private const val GROUP = "\u001E"
        private const val CHART = "\u001D"

        fun decode(text: String?): VisualizationConfig {
            if (text.isNullOrBlank()) return VisualizationConfig()
            return runCatching {
                val parts = text.split(CHART)
                val charts = parts.drop(1).map { chart ->
                    val groups = chart.split(GROUP)
                    val head = groups.first().split(FIELD)
                    ChartConfig(
                        id = head[0].toLong(),
                        title = head[1],
                        mode = ChartMode.valueOf(head[2]),
                        aggregation = Aggregation.valueOf(head[3]),
                        smoothing = head[4].toBoolean(),
                        fromZero = head[5].toBoolean(),
                        byOrder = head.getOrNull(6)?.toBoolean() ?: false,
                        series = groups.drop(1).filter { it.isNotBlank() }.map { g ->
                            val f = g.split(FIELD)
                            SeriesStyle(f[0], f[1].toInt(), LineStyle.valueOf(f[2]), f[3].toFloat(), f[4].toBoolean())
                        }.distinctBy { it.id }
                    )
                }
                VisualizationConfig(charts = charts.ifEmpty { VisualizationConfig().charts }, range = ChartRange.valueOf(parts[0]))
            }.getOrDefault(VisualizationConfig())
        }
    }
}

/** Точка, готовая к отрисовке: дата и значение после выбранного преобразования. */
data class ChartPoint(val date: Long, val value: Double, val raw: Double)

object ChartData {
    private val NumberRegex = Regex("\\d+(?:[.,]\\d+)?")

    private fun unitShort(unit: ResultUnit): String = when (unit) {
        ResultUnit.M -> "м"
        ResultUnit.CM -> "см"
        ResultUnit.SECONDS -> "сек"
        ResultUnit.KG -> "кг"
        ResultUnit.REPS -> "раз"
    }

    /** Попытки из строки старта «15.12, X, 15.40»: заступы не учитываются. */
    fun attemptsOf(text: String?): List<Double> =
        text?.let { NumberRegex.findAll(it).mapNotNull { m -> m.value.replace(',', '.').toDoubleOrNull() }.filter { v -> v > 0 }.toList() }.orEmpty()

    /** Все попытки упражнения в тренировке; если по подходам не вводили — итоговый результат. */
    fun attemptsOf(result: ExerciseResult): List<Double> {
        val sets = result.setResultsJson?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.filter { it > 0 }.orEmpty()
        return sets.ifEmpty { listOfNotNull(result.result.takeIf { it > 0 }) }
    }

    /** Все показатели дневника: упражнения с попытками, старты, ручные записи, вес, рост и разбег. */
    fun buildSeries(
        exercises: List<ExerciseResult>,
        manual: List<TripleJumpResult>,
        starts: List<SeriesSample>,
        body: List<BodyMeasurement>,
        runUp: List<RunUpHistoryEntry>
    ): List<AnalyticsSeries> {
        class Builder(val title: String, val unit: ResultUnit, val group: SeriesGroup, val lower: Boolean) {
            val samples = mutableListOf<SeriesSample>()
        }
        val byKey = LinkedHashMap<String, Builder>()

        exercises.forEach { r ->
            val name = r.exerciseName.trim()
            val values = attemptsOf(r)
            if (name.isBlank() || values.isEmpty()) return@forEach
            val key = "rec:" + RecordsEngine.exerciseRecordKey(
                name, r.isSingleLeg, r.legSide, r.resultUnit, r.inSpikes,
                RecordsEngine.recordInventory(r.inventoryName, r.inventoryUnit, r.inventoryValue, r.resultUnit)
            )
            byKey.getOrPut(key) {
                Builder(
                    RecordsEngine.exerciseTitle(name, r.isSingleLeg, r.legSide, r.inSpikes, r.inventoryName, r.inventoryUnit, r.inventoryValue, r.resultUnit, r.titleLayout),
                    r.resultUnit, SeriesGroup.EXERCISE, RecordsEngine.isLowerBetter(name, r.resultUnit)
                )
            }.samples.add(SeriesSample(r.date, values))
        }

        manual.filter { !it.isAntiRecord && it.result > 0 }.forEach { r ->
            val name = r.eventName.trim()
            val inv = RecordsEngine.variantInventory(r.inventoryName, r.inventoryUnit, r.inventoryValue, r.resultUnit)
            val recInv = RecordsEngine.recordInventory(r.inventoryName, r.inventoryUnit, r.inventoryValue, r.resultUnit)
            // тренировочные записи идут в тот же показатель, что и упражнение из тренировок
            val key = "rec:" + if (!r.isCompetition) RecordsEngine.exerciseRecordKey(name, r.isSingleLeg, r.legSide, r.resultUnit, r.inSpikes, recInv)
            else RecordsEngine.manualRecordKey(name, false, true, r.resultUnit, r.isSingleLeg, r.legSide, recInv ?: inv, r.inSpikes)
            byKey.getOrPut(key) {
                val title = RecordsEngine.exerciseTitle(name, r.isSingleLeg, r.legSide, r.inSpikes, r.inventoryName, r.inventoryUnit, r.inventoryValue, r.resultUnit)
                Builder(
                    title + if (r.isCompetition) " (старт)" else "", r.resultUnit,
                    if (r.isCompetition) SeriesGroup.START else SeriesGroup.EXERCISE, RecordsEngine.isLowerBetter(name, r.resultUnit)
                )
            }.samples.add(SeriesSample(r.date, listOf(r.result)))
        }

        return buildList {
            if (starts.isNotEmpty()) {
                add(AnalyticsSeries("start:tj", "Тройной прыжок на стартах", "м", SeriesGroup.START, false, starts.sortedBy { it.date }))
            }
            byKey.entries.sortedBy { it.value.title.lowercase() }.forEach { (key, b) ->
                add(AnalyticsSeries(key, b.title, unitShort(b.unit), b.group, b.lower, b.samples.sortedBy { it.date }))
            }
            fun single(id: String, title: String, unit: String, group: SeriesGroup, pick: (BodyMeasurement) -> Double?) {
                body.mapNotNull { m -> pick(m)?.let { SeriesSample(m.date, listOf(it)) } }.takeIf { it.isNotEmpty() }?.let {
                    add(AnalyticsSeries(id, title, unit, group, false, it.sortedBy { s -> s.date }))
                }
            }
            single("body:weight", "Вес", "кг", SeriesGroup.BODY) { it.weightKg }
            single("body:height", "Рост", "см", SeriesGroup.BODY) { it.heightCm }
            runUp.mapNotNull { e -> e.lengthCm?.let { SeriesSample(e.date, listOf(it)) } }.takeIf { it.isNotEmpty() }?.let {
                add(AnalyticsSeries("runup:cm", "Длина разбега", "см", SeriesGroup.RUN_UP, false, it.sortedBy { s -> s.date }))
            }
            runUp.mapNotNull { e -> e.lengthFeet?.let { SeriesSample(e.date, listOf(it)) } }.takeIf { it.isNotEmpty() }?.let {
                add(AnalyticsSeries("runup:feet", "Длина разбега в стопах", "ст.", SeriesGroup.RUN_UP, false, it.sortedBy { s -> s.date }))
            }
        }
    }

    /** Понедельник недели, к которой относится дата. */
    fun weekStart(date: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(date)
        val shift = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_YEAR, -shift)
    }.timeInMillis

    /** Точки графика по выбранному способу: каждая попытка, итог тренировки или недели. */
    fun aggregate(samples: List<SeriesSample>, aggregation: Aggregation, lowerIsBetter: Boolean): List<RecordPoint> {
        fun best(values: List<Double>) = if (lowerIsBetter) values.min() else values.max()
        val filled = samples.filter { it.values.isNotEmpty() }
        return when (aggregation) {
            // попытки одного дня идут друг за другом с шагом в минуту: порядок сохраняется, точки не совпадают
            Aggregation.EACH -> filled.flatMap { s -> s.values.mapIndexed { i, v -> RecordPoint(s.date + i * 60_000L, v, false) } }
            Aggregation.SESSION_BEST -> filled.map { RecordPoint(it.date, best(it.values), false) }
            Aggregation.RECORDS -> {
                var record: Double? = null
                filled.sortedBy { it.date }.mapNotNull { s ->
                    val v = best(s.values)
                    val beaten = record?.let { if (lowerIsBetter) v < it else v > it } ?: true
                    if (beaten) { record = v; RecordPoint(s.date, v, false) } else null
                }
            }
            Aggregation.SESSION_AVG -> filled.map { RecordPoint(it.date, it.values.average(), false) }
            else -> filled.groupBy { weekStart(it.date) }.toSortedMap().map { (week, list) ->
                val values = list.flatMap { it.values }
                val value = when (aggregation) {
                    Aggregation.WEEK_BEST -> best(values)
                    Aggregation.WEEK_AVG -> values.average()
                    else -> values.size.toDouble()
                }
                RecordPoint(week, value, false)
            }
        }.sortedBy { it.date }
    }

    /** Скользящее среднее по трём точкам: сглаживает случайные выбросы. */
    fun smooth(values: List<Double>): List<Double> =
        values.indices.map { i -> values.subList((i - 1).coerceAtLeast(0), (i + 2).coerceAtMost(values.size)).average() }

    /** Точки окна графика после сглаживания и выбранного режима. */
    fun transform(points: List<RecordPoint>, mode: ChartMode, smoothing: Boolean): List<ChartPoint> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.date }
        val raw = sorted.map { it.value }
        val values = if (smoothing) smooth(raw) else raw
        val shown = when (mode) {
            ChartMode.ABSOLUTE -> values
            ChartMode.NORMALIZED -> {
                val low = values.min()
                val span = values.max() - low
                values.map { if (span == 0.0) 50.0 else (it - low) / span * 100 }
            }
            ChartMode.CHANGE -> {
                val first = values.first()
                values.map { if (first == 0.0) 0.0 else (it - first) / abs(first) * 100 }
            }
        }
        return sorted.indices.map { ChartPoint(sorted[it].date, shown[it], raw[it]) }
    }

    /** Границы шкалы: у «0–100 %» всегда полная шкала, у ровной линии — запас вокруг значения. */
    fun yBounds(values: List<Double>, mode: ChartMode, fromZero: Boolean): Pair<Double, Double> {
        if (mode == ChartMode.NORMALIZED) return 0.0 to 100.0
        var low = values.min()
        var high = values.max()
        if (mode == ChartMode.ABSOLUTE && fromZero) low = minOf(0.0, low)
        if (high - low < 1e-9) {
            val pad = maxOf(abs(high) * 0.05, 1.0)
            low -= pad
            high += pad
        }
        return low to high
    }

    /**
     * Положение каждой даты по горизонтали от 0 до 1: пропорционально времени
     * или, при [byOrder], через равные промежутки по порядку.
     */
    fun positions(dates: List<Long>, byOrder: Boolean): Map<Long, Float> {
        val distinct = dates.distinct().sorted()
        if (distinct.size == 1) return mapOf(distinct.first() to 0.5f)
        if (byOrder) return distinct.withIndex().associate { (i, d) -> d to i.toFloat() / distinct.lastIndex }
        val first = distinct.first()
        val span = (distinct.last() - first).toFloat()
        return distinct.associateWith { (it - first) / span }
    }

    /** Самая мелкая цена деления для единицы: сотые секунды, граммы, сотые сантиметра и метра. */
    fun maxDecimals(unit: String): Int = when (unit) {
        "сек" -> 2
        "кг" -> 3
        "см", "м" -> 2
        "ст." -> 1
        else -> 0
    }

    /** Сколько знаков нужно подписям шкалы: чем уже видимый диапазон, тем точнее, но не мельче [maxDecimals]. */
    fun axisDecimals(span: Double, maxDecimals: Int): Int {
        if (span <= 0) return maxDecimals
        return ceil(-log10(span / 4)).toInt().coerceIn(0, maxDecimals)
    }

    fun formatFixed(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

    /** Значение с нужной точностью без лишних нулей: 8,8 сек, 102,5 кг, 13,62 м. */
    fun formatTrim(value: Double, maxDecimals: Int): String {
        val text = formatFixed(value, maxDecimals)
        return if (maxDecimals > 0) text.trimEnd('0').trimEnd('.', ',') else text
    }

    /** Ближайшая по дате точка: для подсказки под пальцем. */
    fun nearest(points: List<ChartPoint>, date: Long): ChartPoint? = points.minByOrNull { abs(it.date - date) }
}
