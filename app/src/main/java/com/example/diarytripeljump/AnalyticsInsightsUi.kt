package com.example.diarytripeljump

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.AnalysisRange
import com.example.diarytripeljump.data.AnalyticsEngine
import com.example.diarytripeljump.data.Competition
import com.example.diarytripeljump.data.CompetitionEntry
import com.example.diarytripeljump.data.CompetitionPhase
import com.example.diarytripeljump.data.CompetitionPrepAnalysis
import com.example.diarytripeljump.data.CompetitionSummary
import com.example.diarytripeljump.data.Dynamics
import com.example.diarytripeljump.data.Indicator
import com.example.diarytripeljump.data.PeriodState
import com.example.diarytripeljump.data.PeriodSummary
import com.example.diarytripeljump.data.PrepRow
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TrainingPeriod
import com.example.diarytripeljump.data.TrainingWithExercises
import com.example.diarytripeljump.data.TripleJumpResult
import com.example.diarytripeljump.data.Verdict
import com.example.diarytripeljump.data.formatPercent
import com.example.diarytripeljump.data.formatRecordDelta
import com.example.diarytripeljump.data.formatRecordValue
import com.example.diarytripeljump.data.phase
import com.example.diarytripeljump.data.startOfDay
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.SectionHeader
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.components.parseHexColor
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.theme.NumberLarge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/*
 * Интерфейс модуля аналитики (вкладки «Динамика», «Старт», «Периоды» + карточки для «Сводки»).
 * Все расчёты — в data/AnalyticsEngine.kt, здесь только выбор параметров и отображение.
 */

private fun fmt1(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

// ───────────────────────────── общие элементы ─────────────────────────────

/** Цвета оценки: для упражнений рост — «хорошо», для общих показателей (нагрузка) — нейтрально. */
@Composable
private fun verdictColors(v: Verdict, general: Boolean): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (v) {
        Verdict.BIG_GAIN, Verdict.GAIN ->
            if (general) cs.tertiaryContainer to cs.onTertiaryContainer else cs.primaryContainer to cs.onPrimaryContainer
        Verdict.LOSS, Verdict.BIG_LOSS ->
            if (general) cs.secondaryContainer to cs.onSecondaryContainer else cs.errorContainer to cs.onErrorContainer
        else -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }
}

@Composable
private fun VerdictPill(d: Dynamics, modifier: Modifier = Modifier) {
    val (bg, fg) = verdictColors(d.verdict, d.indicator.isGeneral)
    StatusPill(text = d.verdict.label(d.indicator.isGeneral), contentColor = fg, containerColor = bg, modifier = modifier)
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = cs.surfaceContainerLow) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Поле с выпадающим списком (одиночный выбор). */
@Composable
private fun PickerField(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = cs.surfaceContainerLow,
            border = BorderStroke(1.dp, cs.outlineVariant)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = cs.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}

/** Выбранные показатели (мультивыбор). Пока пользователь ничего не менял — берутся самые частые упражнения. */
internal class KeySelection(val keys: Set<String>, val toggle: (String) -> Unit)

@Composable
private fun rememberKeySelection(indicators: List<Indicator>, defaultCount: Int): KeySelection {
    var raw by rememberSaveable { mutableStateOf<String?>(null) }
    val valid = indicators.map { it.key }.toSet()
    val keys: Set<String> = raw?.split("\n")?.filter { it.isNotBlank() && it in valid }?.toSet()
        ?: indicators.filter { !it.isGeneral }.take(defaultCount).map { it.key }.toSet()
    return KeySelection(keys) { k ->
        val next = if (k in keys) keys - k else keys + k
        raw = next.joinToString("\n")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndicatorChips(indicators: List<Indicator>, selection: KeySelection) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        indicators.forEach { ind ->
            FilterChip(
                selected = ind.key in selection.keys,
                onClick = { selection.toggle(ind.key) },
                label = { Text(ind.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

// ───────────────────────────── выбор периода ─────────────────────────────

private const val RS_ALL = 0
private const val RS_30 = 1
private const val RS_90 = 2
private const val RS_PERIOD = 3
private const val RS_CUSTOM = 4

internal class RangeSelection(
    private val kindState: MutableIntState,
    private val periodState: MutableLongState,
    private val startState: MutableLongState,
    private val endState: MutableLongState
) {
    var kind: Int
        get() = kindState.intValue
        set(v) { kindState.intValue = v }
    var periodId: Long
        get() = periodState.longValue
        set(v) { periodState.longValue = v }
    var customStart: Long
        get() = startState.longValue
        set(v) { startState.longValue = v }
    var customEnd: Long
        get() = endState.longValue
        set(v) { endState.longValue = v }

    fun selectedPeriod(periods: List<TrainingPeriod>): TrainingPeriod? =
        if (kind == RS_PERIOD) periods.firstOrNull { it.id == periodId } else null

    fun effectiveKind(periods: List<TrainingPeriod>): Int =
        if (kind == RS_PERIOD && selectedPeriod(periods) == null) RS_ALL else kind

    /** null — весь период наблюдений. */
    fun range(today: Long, periods: List<TrainingPeriod>): AnalysisRange? = when (effectiveKind(periods)) {
        RS_30 -> AnalysisRange(startOfDay(AnalyticsEngine.addDays(today, -29)), AnalyticsEngine.endOfDay(today))
        RS_90 -> AnalysisRange(startOfDay(AnalyticsEngine.addDays(today, -89)), AnalyticsEngine.endOfDay(today))
        RS_PERIOD -> selectedPeriod(periods)?.let { AnalysisRange(startOfDay(it.startDate), AnalyticsEngine.endOfDay(it.endDate)) }
        RS_CUSTOM -> if (customStart > 0L && customEnd > 0L)
            AnalysisRange(startOfDay(minOf(customStart, customEnd)), AnalyticsEngine.endOfDay(maxOf(customStart, customEnd)))
        else null
        else -> null
    }
}

@Composable
private fun rememberRangeSelection(): RangeSelection {
    val kind = rememberSaveable { mutableIntStateOf(RS_ALL) }
    val period = rememberSaveable { mutableLongStateOf(-1L) }
    val start = rememberSaveable { mutableLongStateOf(0L) }
    val end = rememberSaveable { mutableLongStateOf(0L) }
    return remember { RangeSelection(kind, period, start, end) }
}

@Composable
private fun RangeChips(sel: RangeSelection, periods: List<TrainingPeriod>, today: Long, dayFmt: SimpleDateFormat) {
    var showDialog by remember { mutableStateOf(false) }
    val kind = sel.effectiveKind(periods)
    val range = sel.range(today, periods)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = kind == RS_ALL, onClick = { sel.kind = RS_ALL }, label = { Text("Всё время") })
        FilterChip(selected = kind == RS_30, onClick = { sel.kind = RS_30 }, label = { Text("30 дней") })
        FilterChip(selected = kind == RS_90, onClick = { sel.kind = RS_90 }, label = { Text("3 месяца") })
        periods.sortedByDescending { it.startDate }.forEach { p ->
            FilterChip(
                selected = kind == RS_PERIOD && sel.periodId == p.id,
                onClick = { sel.kind = RS_PERIOD; sel.periodId = p.id },
                label = { Text(p.name) },
                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(p.colorHex))) }
            )
        }
        FilterChip(
            selected = kind == RS_CUSTOM,
            onClick = { showDialog = true },
            label = {
                Text(
                    if (kind == RS_CUSTOM && range != null) "${dayFmt.format(Date(range.start))} — ${dayFmt.format(Date(range.end))}" else "Свои даты"
                )
            },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
    }
    if (showDialog) {
        CustomRangeDialog(
            initialStart = if (sel.customStart > 0L) sel.customStart else startOfDay(AnalyticsEngine.addDays(today, -29)),
            initialEnd = if (sel.customEnd > 0L) sel.customEnd else today,
            onDismiss = { showDialog = false },
            onConfirm = { s, e ->
                sel.customStart = minOf(s, e)
                sel.customEnd = maxOf(s, e)
                sel.kind = RS_CUSTOM
                showDialog = false
            }
        )
    }
}

// ───────────────────────────── график динамики ─────────────────────────────

/**
 * Линия показателя с точками по датам (расстояние между точками пропорционально времени),
 * линейным трендом и (опционально) вертикальной отметкой старта.
 */
@Composable
private fun DynamicsChart(
    points: List<com.example.diarytripeljump.data.IndicatorPoint>,
    modifier: Modifier = Modifier,
    markerDate: Long? = null,
    xStart: Long? = null,
    height: Dp = 140.dp
) {
    val cs = MaterialTheme.colorScheme
    val lineColor = cs.primary
    val startColor = cs.secondary
    val gridColor = cs.outlineVariant
    val trendColor = cs.onSurfaceVariant
    val markerColor = cs.tertiary
    val trend = remember(points) { AnalyticsEngine.trendLine(points) }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val t0 = minOf(xStart ?: points.first().date, points.first().date)
        val t1 = maxOf(markerDate ?: points.last().date, points.last().date)
        val span = (t1 - t0).coerceAtLeast(1L).toFloat()
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val pad = (if (maxV > minV) maxV - minV else maxOf(abs(maxV) * 0.05, 1.0)) * 0.15
        val lo = (minV - pad).toFloat()
        val vr = ((maxV + pad).toFloat() - lo).coerceAtLeast(1e-6f)
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        fun x(t: Long) = left + (t - t0) / span * (right - left)
        fun y(v: Double) = bottom - ((v.toFloat() - lo) / vr) * (bottom - top)
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))

        repeat(3) { i ->
            val gy = top + (bottom - top) * i / 2f
            drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
        }
        if (markerDate != null) {
            drawLine(markerColor, Offset(x(markerDate), top), Offset(x(markerDate), bottom), strokeWidth = 2.dp.toPx(), pathEffect = dash)
        }
        if (trend != null) {
            drawLine(
                trendColor.copy(alpha = 0.7f),
                Offset(x(points.first().date), y(trend.first)),
                Offset(x(points.last().date), y(trend.second)),
                strokeWidth = 2.dp.toPx(), pathEffect = dash
            )
        }
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p.date), y(p.value)) else path.lineTo(x(p.date), y(p.value))
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, p ->
            val edge = i == 0 || i == points.lastIndex
            drawCircle(
                color = if (i == 0) startColor else lineColor,
                radius = if (edge) 6.dp.toPx() else 3.5.dp.toPx(),
                center = Offset(x(p.date), y(p.value))
            )
        }
    }
}

@Composable
private fun ChartLegend(hasTrend: Boolean, hasMarker: Boolean) {
    val cs = MaterialTheme.colorScheme
    @Composable
    fun item(color: Color, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(cs.secondary, "начало")
        item(cs.primary, "конец")
        if (hasTrend) item(cs.onSurfaceVariant, "тренд")
        if (hasMarker) item(cs.tertiary, "старт")
    }
}

private fun confidenceLabel(c: Int) = when (c) {
    2 -> "достаточная"
    1 -> "средняя"
    else -> "низкая"
}

// ───────────────────────────── вкладка «Динамика» ─────────────────────────────

@Composable
internal fun DynamicsTab(
    trainings: List<TrainingWithExercises>,
    periods: List<TrainingPeriod>,
    today: Long,
    dayFmt: SimpleDateFormat
) {
    val indicators = remember(trainings) { AnalyticsEngine.indicators(trainings) }
    var selectedKey by rememberSaveable { mutableStateOf("") }
    val sel = rememberRangeSelection()
    val indicator = indicators.firstOrNull { it.key == selectedKey } ?: indicators.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (indicator == null) {
            EmptyState(
                icon = Icons.Default.Analytics,
                title = "Нет показателей для анализа",
                message = "Запишите тренировки с результатами упражнений — здесь появится их динамика."
            )
            return@Column
        }
        SectionHeader(title = "Динамика показателя", subtitle = "Сравнение начала и конца выбранного периода")
        PickerField(
            label = "Показатель",
            value = indicator.name,
            options = indicators.map { if (it.isGeneral) it.name else "${it.name} (${it.sessions})" },
            onSelect = { selectedKey = indicators[it].key }
        )
        RangeChips(sel, periods, today, dayFmt)

        val range = sel.range(today, periods)
        val d = remember(indicator, trainings, range) {
            AnalyticsEngine.dynamics(indicator, AnalyticsEngine.points(indicator, trainings), range)
        }
        DynamicsDetailCard(d, dayFmt)
    }
}

@Composable
private fun DynamicsDetailCard(d: Dynamics, dayFmt: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    val ind = d.indicator
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ind.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                VerdictPill(d)
            }
            if (d.first != null && d.last != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBox("Начало периода", ind.format(d.first.value), Modifier.weight(1f), sub = dayFmt.format(Date(d.first.date)))
                    MetricBox("Конец периода", ind.format(d.last.value), Modifier.weight(1f), sub = dayFmt.format(Date(d.last.date)))
                }
            }
            if (d.hasData && d.absChange != null) {
                val accent = verdictColors(d.verdict, ind.isGeneral).second
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBox("Абсолютное изменение", ind.formatDelta(d.absChange), Modifier.weight(1f), valueColor = accent)
                    MetricBox("Относительное изменение", d.relChange?.let { formatPercent(it) } ?: "—", Modifier.weight(1f), valueColor = accent)
                }
            }
            if (d.startAvg != null && d.endAvg != null) {
                Text(
                    "Среднее по первой трети замеров: ${ind.format(d.startAvg)} → по последней трети: ${ind.format(d.endAvg)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (d.points.size >= 2) {
                DynamicsChart(points = d.points)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dayFmt.format(Date(d.points.first().date)), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(dayFmt.format(Date(d.points.last().date)), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                ChartLegend(hasTrend = d.points.size >= 3, hasMarker = false)
            }
            Text(d.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
            if (d.points.isNotEmpty() && d.best != null && d.average != null) {
                HorizontalDivider(color = cs.outlineVariant)
                Text(
                    "Замеров: ${d.points.size} · лучший: ${ind.format(d.best)} · среднее: ${ind.format(d.average)}" +
                        if (d.hasData) " · надёжность оценки: ${confidenceLabel(d.confidence)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────────── вкладка «Старт» ─────────────────────────────

private val PREP_WEEKS = listOf(2, 4, 8, 12)
private const val PREP_PERIOD = 4
private const val PREP_CUSTOM = 5

@Composable
internal fun CompetitionTab(
    trainings: List<TrainingWithExercises>,
    competitions: List<Competition>,
    results: List<TripleJumpResult>,
    periods: List<TrainingPeriod>,
    today: Long,
    dayFmt: SimpleDateFormat
) {
    val entries = remember(competitions, results) { AnalyticsEngine.competitionEntries(competitions, results) }
    val indicators = remember(trainings) { AnalyticsEngine.indicators(trainings) }
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var prepKind by rememberSaveable { mutableIntStateOf(1) }
    var prepPeriodId by rememberSaveable { mutableLongStateOf(-1L) }
    var customStart by rememberSaveable { mutableLongStateOf(0L) }
    var customEnd by rememberSaveable { mutableLongStateOf(0L) }
    var showDialog by remember { mutableStateOf(false) }
    val selection = rememberKeySelection(indicators, defaultCount = 3)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.EmojiEvents,
                title = "Нет соревнований",
                message = "Добавьте старт на вкладке «Старты» — здесь можно будет сопоставить подготовку с результатом."
            )
            return@Column
        }
        val ordered = remember(entries) { entries.sortedByDescending { it.date } }
        val target: CompetitionEntry = ordered.firstOrNull { it.competition.id == selectedId }
            ?: ordered.firstOrNull { it.result != null }
            ?: ordered.first()

        SectionHeader(title = "Подготовка и результат старта", subtitle = "Как менялись показатели перед соревнованием")
        PickerField(
            label = "Соревнование",
            value = "${dayFmt.format(Date(target.date))} · ${target.competition.name}",
            options = ordered.map { e ->
                "${dayFmt.format(Date(e.date))} · ${e.competition.name}" + (e.result?.let { " · ${formatRecordValue(it, ResultUnit.M)}" } ?: "")
            },
            onSelect = { selectedId = ordered[it].competition.id }
        )

        // Период подготовки: N недель до старта, подготовительный период или свои даты
        val applicablePeriods = remember(periods, target) {
            periods.filter { startOfDay(it.startDate) < startOfDay(target.date) }.sortedByDescending { it.startDate }
        }
        val chosenPeriod = if (prepKind == PREP_PERIOD) applicablePeriods.firstOrNull { it.id == prepPeriodId } else null
        val effectiveKind = if (prepKind == PREP_PERIOD && chosenPeriod == null) 1 else prepKind
        val range: AnalysisRange = when {
            effectiveKind in PREP_WEEKS.indices -> AnalyticsEngine.prepRange(target.date, PREP_WEEKS[effectiveKind] * 7)
            effectiveKind == PREP_PERIOD && chosenPeriod != null ->
                AnalysisRange(startOfDay(chosenPeriod.startDate), AnalyticsEngine.endOfDay(chosenPeriod.endDate))
            effectiveKind == PREP_CUSTOM && customStart > 0L && customEnd > 0L ->
                AnalysisRange(startOfDay(minOf(customStart, customEnd)), AnalyticsEngine.endOfDay(maxOf(customStart, customEnd)))
            else -> AnalyticsEngine.prepRange(target.date, 28)
        }

        Text("Период подготовки", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PREP_WEEKS.forEachIndexed { i, w ->
                FilterChip(
                    selected = effectiveKind == i,
                    onClick = { prepKind = i },
                    label = { Text("$w ${pluralRu(w, "неделя", "недели", "недель")}") }
                )
            }
            applicablePeriods.forEach { p ->
                FilterChip(
                    selected = effectiveKind == PREP_PERIOD && prepPeriodId == p.id,
                    onClick = { prepKind = PREP_PERIOD; prepPeriodId = p.id },
                    label = { Text(p.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(p.colorHex))) }
                )
            }
            FilterChip(
                selected = effectiveKind == PREP_CUSTOM,
                onClick = { showDialog = true },
                label = {
                    Text(if (effectiveKind == PREP_CUSTOM) "${dayFmt.format(Date(range.start))} — ${dayFmt.format(Date(range.end))}" else "Свои даты")
                },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Text("Тренировочные показатели", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (indicators.isEmpty()) {
            Text("Пока нет тренировочных показателей для сопоставления.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IndicatorChips(indicators, selection)
        }

        val chosen = remember(indicators, selection.keys) { indicators.filter { it.key in selection.keys } }
        val analysis = remember(target, entries, trainings, chosen, range) {
            AnalyticsEngine.competitionPrep(target, entries, trainings, chosen, range)
        }

        CompetitionResultCard(analysis, today, dayFmt)
        if (chosen.isEmpty()) {
            Text(
                "Выберите хотя бы один показатель, чтобы увидеть его динамику перед стартом.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        analysis.rows.forEach { row -> PrepRowCard(row, analysis, dayFmt) }
        if (analysis.summary.isNotEmpty()) SummaryLinesCard("Выводы", analysis.summary)

        if (showDialog) {
            CustomRangeDialog(
                initialStart = if (customStart > 0L) customStart else range.start,
                initialEnd = if (customEnd > 0L) customEnd else range.end,
                onDismiss = { showDialog = false },
                onConfirm = { s, e ->
                    customStart = minOf(s, e)
                    customEnd = maxOf(s, e)
                    prepKind = PREP_CUSTOM
                    showDialog = false
                }
            )
        }
    }
}

@Composable
private fun CompetitionResultCard(a: CompetitionPrepAnalysis, today: Long, dayFmt: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    val e = a.entry
    val c = e.competition
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(
                        listOfNotNull(dayFmt.format(Date(c.date)), c.city?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                val (label, bg, fg) = when (c.phase(today)) {
                    CompetitionPhase.UPCOMING -> Triple("Предстоит", cs.tertiaryContainer, cs.onTertiaryContainer)
                    CompetitionPhase.CURRENT -> Triple("Сегодня", cs.primary, cs.onPrimary)
                    CompetitionPhase.PAST -> Triple("Прошёл", cs.surfaceContainerHigh, cs.onSurfaceVariant)
                }
                StatusPill(text = label, contentColor = fg, containerColor = bg)
            }
            if (e.result != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatRecordValue(e.result, ResultUnit.M), style = NumberLarge, color = cs.onSurface)
                    c.placement?.takeIf { it.isNotBlank() }?.let {
                        StatusPill(text = it, contentColor = cs.onSecondaryContainer, containerColor = cs.secondaryContainer)
                    }
                    if (a.previous?.result != null && a.resultChange != null) {
                        val better = a.resultChange > 0
                        StatusPill(
                            text = formatRecordDelta(e.result, a.previous.result, ResultUnit.M),
                            contentColor = if (better) cs.onPrimaryContainer else cs.onErrorContainer,
                            containerColor = if (better) cs.primaryContainer else cs.errorContainer
                        )
                    }
                }
            } else {
                Text("Результат не записан", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            if (e.attempts.isNotEmpty()) {
                val best = e.attempts.mapNotNull { it.distance }.maxOrNull()
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.attempts.forEach { at ->
                        when {
                            at.foul -> StatusPill("X", cs.onErrorContainer, cs.errorContainer)
                            at.distance == best -> StatusPill(fmtAttempt(at.distance), cs.onPrimaryContainer, cs.primaryContainer)
                            else -> StatusPill(fmtAttempt(at.distance), cs.onSurfaceVariant, cs.surfaceContainerHigh)
                        }
                    }
                }
            }
        }
    }
}

private fun fmtAttempt(d: Double?): String = if (d == null) "X" else String.format(Locale.getDefault(), "%.2f", d)

@Composable
private fun PrepRowCard(row: PrepRow, a: CompetitionPrepAnalysis, dayFmt: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    val d = row.dynamics
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.indicator.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface, modifier = Modifier.weight(1f))
                VerdictPill(d)
            }
            if (d.hasData && d.first != null && d.last != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBox("В начале подготовки", d.indicator.format(d.first.value), Modifier.weight(1f))
                    MetricBox("Перед стартом", d.indicator.format(d.last.value), Modifier.weight(1f))
                    MetricBox("Изменение", d.relChange?.let { formatPercent(it) } ?: "—", Modifier.weight(1f), sub = d.absChange?.let { d.indicator.formatDelta(it) },
                        valueColor = verdictColors(d.verdict, d.indicator.isGeneral).second)
                }
            }
            if (d.points.size >= 2) {
                DynamicsChart(points = d.points, markerDate = a.entry.date, xStart = a.range?.start, height = 110.dp)
                ChartLegend(hasTrend = d.points.size >= 3, hasMarker = true)
            }
            Text(d.summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            row.previousDynamics?.let { p ->
                val text = if (p.hasData && p.relChange != null)
                    "К предыдущему старту «${a.previous?.competition?.name ?: ""}» в окне такой же длины: ${p.verdict.label(p.indicator.isGeneral).lowercase()} (${formatPercent(p.relChange)})."
                else "В окне такой же длины перед предыдущим стартом замеров для сравнения недостаточно."
                Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            row.link?.let { Text(it.text, style = MaterialTheme.typography.bodySmall, color = cs.onSurface) }
        }
    }
}

@Composable
private fun SummaryLinesCard(title: String, lines: List<String>) {
    val cs = MaterialTheme.colorScheme
    AppCard(containerColor = cs.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(cs.primary))
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                }
            }
            Text(
                "Справочная оценка по вашим записям, не заменяет мнение тренера.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

// ───────────────────────────── вкладка «Периоды» ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodsTab(
    trainings: List<TrainingWithExercises>,
    competitions: List<Competition>,
    results: List<TripleJumpResult>,
    periods: List<TrainingPeriod>,
    today: Long,
    dayFmt: SimpleDateFormat
) {
    val indicators = remember(trainings) { AnalyticsEngine.indicators(trainings) }
    val selection = rememberKeySelection(indicators, defaultCount = 2)
    var rawPeriods by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (periods.isEmpty()) {
            EmptyState(
                icon = Icons.Default.DateRange,
                title = "Нет подготовительных периодов",
                message = "Создайте период на вкладке «Тренировки» — здесь появятся его показатели и сравнение с другими периодами."
            )
            return@Column
        }
        val sortedPeriods = remember(periods) { periods.sortedBy { it.startDate } }
        val validIds = sortedPeriods.map { it.id }.toSet()
        val selectedIds: Set<Long> = rawPeriods?.split(",")?.mapNotNull { it.toLongOrNull() }?.filter { it in validIds }?.toSet()
            ?: validIds

        SectionHeader(title = "Подготовительные периоды", subtitle = "Показатели внутри периода и сравнение периодов между собой")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            sortedPeriods.forEach { p ->
                FilterChip(
                    selected = p.id in selectedIds,
                    onClick = {
                        val next = if (p.id in selectedIds) selectedIds - p.id else selectedIds + p.id
                        rawPeriods = next.joinToString(",")
                    },
                    label = { Text(p.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(p.colorHex))) }
                )
            }
        }
        Text("Тренировочные показатели", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (indicators.isEmpty()) {
            Text("Пока нет тренировочных показателей для сравнения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IndicatorChips(indicators, selection)
        }

        val chosen = remember(indicators, selection.keys) { indicators.filter { it.key in selection.keys } }
        val entries = remember(competitions, results) { AnalyticsEngine.competitionEntries(competitions, results) }
        val summaries = remember(sortedPeriods, selectedIds, trainings, entries, chosen, today) {
            sortedPeriods.filter { it.id in selectedIds }
                .map { AnalyticsEngine.periodSummary(it, trainings, entries, chosen, today) }
        }

        if (summaries.isEmpty()) {
            Text("Выберите хотя бы один период.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (summaries.size >= 2) PeriodComparisonCard(summaries, chosen)
        summaries.forEach { PeriodAnalysisCard(it, dayFmt) }
    }
}

@Composable
private fun PeriodAnalysisCard(s: PeriodSummary, dayFmt: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    val color = parseHexColor(s.period.colorHex)
    val totalDays = (Math.round((startOfDay(s.period.endDate) - startOfDay(s.period.startDate)) / 86_400_000.0) + 1).toInt()
    AppCard(containerColor = color.copy(alpha = 0.08f), border = BorderStroke(1.dp, color.copy(alpha = 0.40f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.period.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(
                        "${dayFmt.format(Date(s.period.startDate))} — ${dayFmt.format(Date(s.period.endDate))} · $totalDays ${pluralRu(totalDays, "день", "дня", "дней")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = when (s.state) {
                        PeriodState.UPCOMING -> "Впереди"
                        PeriodState.ONGOING -> "Идёт сейчас"
                        PeriodState.FINISHED -> "Завершён"
                    },
                    contentColor = if (s.state == PeriodState.ONGOING) Color.White else color,
                    containerColor = if (s.state == PeriodState.ONGOING) color else color.copy(alpha = 0.16f)
                )
            }
            if (s.range == null) {
                Text("Период ещё не начался — данных пока нет.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox("Тренировок", "${s.trainingsDone}", Modifier.weight(1f), sub = if (s.skipped > 0) "пропусков: ${s.skipped}" else null)
                    MetricBox("В неделю", s.perWeek?.let { fmt1(it) } ?: "—", Modifier.weight(1f))
                    MetricBox("Посещаемость", s.attendancePercent?.let { "$it%" } ?: "—", Modifier.weight(1f))
                }
                if (s.avgIntensity != null || s.totalMinutes > 0) {
                    Text(
                        listOfNotNull(
                            s.avgIntensity?.let { "средняя интенсивность ${fmt1(it)} из 10" },
                            if (s.totalMinutes > 0) "всего ${s.totalMinutes / 60} ч ${s.totalMinutes % 60} мин" else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (s.dynamics.isNotEmpty()) {
                    HorizontalDivider(color = cs.outlineVariant)
                    Text("Изменение показателей внутри периода", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    s.dynamics.forEach { DynamicsRow(it) }
                }
                if (s.competitions.isNotEmpty()) {
                    HorizontalDivider(color = cs.outlineVariant)
                    Text("Контекст соревнований", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    s.competitions.forEach { pc ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pc.entry.competition.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
                                Text(
                                    if (pc.inside) "внутри периода" else "через ${pc.daysAfterEnd} ${pluralRu(pc.daysAfterEnd, "день", "дня", "дней")} после периода",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            pc.entry.result?.let { r ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatRecordValue(r, ResultUnit.M), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
                                    pc.changeVsPrevious?.let { ch ->
                                        Text(
                                            formatRecordDelta(r, r - ch, ResultUnit.M),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ch >= 0) cs.primary else cs.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(AnalyticsEngine.periodNarrative(s), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DynamicsRow(d: Dynamics) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.indicator.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (d.hasData && d.first != null && d.last != null) "${d.indicator.format(d.first.value)} → ${d.indicator.format(d.last.value)}" else d.summary,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        if (d.hasData && d.absChange != null) {
            val (bg, fg) = verdictColors(d.verdict, d.indicator.isGeneral)
            StatusPill(
                text = d.indicator.formatDelta(d.absChange) + (d.relChange?.let { " (${formatPercent(it)})" } ?: ""),
                contentColor = fg,
                containerColor = bg
            )
        }
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, valueText: String, color: Color, labelColor: Color? = null) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor ?: cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(92.dp))
        Box(modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape).background(cs.surfaceContainerHigh)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        }
        Text(valueText, style = MaterialTheme.typography.labelMedium, color = cs.onSurface, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(78.dp))
    }
}

@Composable
private fun PeriodComparisonCard(summaries: List<PeriodSummary>, indicators: List<Indicator>) {
    val cs = MaterialTheme.colorScheme
    val active = summaries.filter { it.range != null }
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Сравнение периодов", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text("Выбранные периоды рядом: нагрузка и изменение показателей", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (active.isNotEmpty()) {
                Text("Тренировок в неделю", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                val maxWeek = active.maxOf { it.perWeek ?: 0.0 }.coerceAtLeast(0.1)
                active.forEach { s ->
                    BarRow(s.period.name, ((s.perWeek ?: 0.0) / maxWeek).toFloat(), s.perWeek?.let { fmt1(it) } ?: "—", parseHexColor(s.period.colorHex))
                }
                Text("Посещаемость", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                active.forEach { s ->
                    BarRow(s.period.name, (s.attendancePercent ?: 0) / 100f, s.attendancePercent?.let { "$it%" } ?: "—", parseHexColor(s.period.colorHex))
                }
            }
            indicators.forEachIndexed { i, ind ->
                val rows = summaries.mapNotNull { s -> s.dynamics.getOrNull(i)?.takeIf { it.hasData && it.improvementPercent != null }?.let { s to it } }
                if (rows.isNotEmpty()) {
                    Text(
                        if (ind.isGeneral) "«${ind.name}»: изменение за период" else "«${ind.name}»: улучшение за период",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant
                    )
                    val maxAbs = rows.maxOf { abs(it.second.improvementPercent!!) }.coerceAtLeast(0.1)
                    rows.forEach { (s, d) ->
                        val imp = d.improvementPercent!!
                        val good = if (ind.isGeneral) cs.tertiary else cs.primary
                        val bad = if (ind.isGeneral) cs.secondary else cs.error
                        BarRow(
                            label = s.period.name,
                            fraction = (abs(imp) / maxAbs).toFloat(),
                            valueText = d.relChange?.let { formatPercent(it) } ?: "—",
                            color = if (imp >= 0) good else bad
                        )
                    }
                }
            }
            val withResults = summaries.filter { s -> s.competitions.any { it.entry.result != null } }
            if (withResults.isNotEmpty()) {
                Text("Результат на старте рядом с периодом", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                withResults.forEach { s ->
                    val pc = s.competitions.first { it.entry.result != null }
                    BarRow(
                        label = s.period.name,
                        fraction = 1f,
                        valueText = formatRecordValue(pc.entry.result!!, ResultUnit.M),
                        color = parseHexColor(s.period.colorHex)
                    )
                }
            }
            val notes = AnalyticsEngine.periodComparisonNotes(summaries)
            if (notes.isNotEmpty()) {
                HorizontalDivider(color = cs.outlineVariant)
                notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurface) }
            }
        }
    }
}

// ───────────────────────────── карточки для вкладки «Сводка» ─────────────────────────────

@Composable
internal fun InsightsCard(lines: List<String>) {
    if (lines.isEmpty()) return
    SummaryLinesCard("Главное за период", lines)
}

@Composable
internal fun IndicatorChangesCard(changes: List<Dynamics>) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Изменение основных показателей", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text("Самые частые упражнения: от первого замера до последнего", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            changes.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                DynamicsRow(d)
            }
        }
    }
}

@Composable
internal fun CompetitionSummaryCard(s: CompetitionSummary, dayFmt: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Соревновательная деятельность", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("Стартов", "${s.count}", Modifier.weight(1f), sub = if (s.podiums > 0) "призовых: ${s.podiums}" else null)
                MetricBox("Лучший", s.best?.result?.let { formatRecordValue(it, ResultUnit.M) } ?: "—", Modifier.weight(1f), sub = s.best?.let { dayFmt.format(Date(it.date)) })
                MetricBox("Средний", s.average?.let { formatRecordValue(it, ResultUnit.M) } ?: "—", Modifier.weight(1f))
            }
            val last = s.last
            val lastResult = last?.result
            if (last != null && lastResult != null) {
                val change = s.changeFirstToLast
                Text(
                    "Последний старт: «${last.competition.name}» — ${formatRecordValue(lastResult, ResultUnit.M)}" +
                        (if (change != null) ". От первого старта периода: ${formatRecordDelta(lastResult, lastResult - change, ResultUnit.M)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (s.foulPercent != null) {
                Text("Доля фолов среди попыток: ${s.foulPercent}%", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}
