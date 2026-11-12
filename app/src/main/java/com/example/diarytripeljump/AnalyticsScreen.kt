package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.coachTarget
import com.example.diarytripeljump.ui.components.LocalConfirm
import com.example.diarytripeljump.data.HelpTopic
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.Aggregation
import com.example.diarytripeljump.data.AnalyticsSeries
import com.example.diarytripeljump.data.ChartConfig
import com.example.diarytripeljump.data.ChartData
import com.example.diarytripeljump.data.ChartMode
import com.example.diarytripeljump.data.ChartPoint
import com.example.diarytripeljump.data.ChartRange
import com.example.diarytripeljump.data.LineStyle
import com.example.diarytripeljump.data.RecordInsights
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.SeriesGroup
import com.example.diarytripeljump.data.SeriesStyle
import com.example.diarytripeljump.data.VisualizationConfig
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.data.AdviceKind
import androidx.compose.material.icons.filled.Lightbulb
import com.example.diarytripeljump.ui.components.ChartWindow
import com.example.diarytripeljump.ui.components.pinchZoom
import com.example.diarytripeljump.ui.components.AppScreenScaffold
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.SegmentedControl
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.screenContentPadding
import com.example.diarytripeljump.ui.theme.extraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Цвета на выбор; по умолчанию показатель берёт цвет из темы приложения. */
private val SeriesColors = listOf(
    0xFF3B82F6, 0xFFF97316, 0xFF10B981, 0xFFE11D48, 0xFF8B5CF6, 0xFFEAB308,
    0xFF06B6D4, 0xFFEC4899, 0xFF84CC16, 0xFF64748B, 0xFFB45309, 0xFF14B8A6
).map { Color(it) }

/** Новый график без своего названия: при первом показателе получает его название. */
private const val DEFAULT_TITLE_PREFIX = "График"

private fun shortDate(millis: Long): String = SimpleDateFormat("d MMM yy", Locale("ru")).format(Date(millis))

/** Цвета темы по порядку показателя: так графики следуют выбранной расцветке. */
@Composable
private fun themeColors(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return listOf(cs.primary, cs.secondary, cs.tertiary, MaterialTheme.extraColors.gold, cs.error)
}

private fun colorOf(style: SeriesStyle, index: Int, theme: List<Color>): Color =
    if (style.color == SeriesStyle.AUTO_COLOR) theme[index % theme.size] else Color(style.color)

/** Линия на графике: показатель, его оформление, готовые точки и цвет. */
private class Plot(val series: AnalyticsSeries, val style: SeriesStyle, val points: List<ChartPoint>, val color: Color)

/** Вкладка «Аналитика»: разделы модуля. Первый раздел — визуализация показателей. */
@Composable
fun AnalyticsScreen(
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit,
    onOpenVisualization: () -> Unit,
    onOpenAdvice: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val advice by viewModel.allAdvice.collectAsState()
    val insights by viewModel.insights.collectAsState()
    AppScreenScaffold(
        title = "Аналитика",
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile,
        onHelp = rememberHelp(HelpTopic.ANALYTICS)
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(inner),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppCard(modifier = Modifier.coachTarget("analytics.visualization"), onClick = onOpenVisualization) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconBadge(icon = Icons.AutoMirrored.Filled.ShowChart, tint = cs.primary, size = 48.dp, iconSize = 26.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Визуализация", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text(
                                "Попытки, итоги тренировок и недель, вес и разбег на графиках со своими настройками",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                val risks = advice.count { it.kind == AdviceKind.RISK }
                AppCard(modifier = Modifier.coachTarget("analytics.advice"), onClick = onOpenAdvice) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconBadge(icon = Icons.Default.Lightbulb, tint = cs.tertiary, size = 48.dp, iconSize = 26.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Советы и прогноз", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            // прогноз тройного — главное, что спортсмен хочет увидеть сразу
                            insights.forecasts.firstOrNull { it.key == "main-jump" }?.let { f ->
                                Text(
                                    "Тройной ${f.targetLabel.substringBefore(',')}: ≈ ${com.example.diarytripeljump.data.formatRecordValue(f.value, f.unit)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.primary
                                )
                            }
                            Text(
                                when {
                                    advice.isEmpty() -> "Прогноз результата, что влияет на прыжок, советы по разбегу, целям и нагрузке"
                                    risks > 0 -> "${advice.size} ${pluralRu(advice.size, "совет", "совета", "советов")}, важных: $risks"
                                    else -> "${advice.size} ${pluralRu(advice.size, "совет", "совета", "советов")} по данным дневника"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (risks > 0) cs.error else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Визуализация: графики с одним показателем по умолчанию, у каждого свой масштаб и способ подсчёта. */
@Composable
fun VisualizationScreen(viewModel: DiaryViewModel, onBack: () -> Unit) {
    val confirm = LocalConfirm.current
    val allSeries by viewModel.analyticsSeries.collectAsState()
    val config by viewModel.visualization.collectAsState()
    val favoriteExercises by viewModel.favoriteExercises.collectAsState()
    // null — окно закрыто, NEW_CHART — новый график, иначе id графика, в который добавляется показатель
    var pickerFor by remember { mutableStateOf<Long?>(null) }
    var editingStyle by remember { mutableStateOf<Pair<Long, SeriesStyle>?>(null) }
    var editingChart by remember { mutableStateOf<ChartConfig?>(null) }
    val byId = remember(allSeries) { allSeries.associateBy { it.id } }
    val favorites = remember(favoriteExercises) {
        favoriteExercises.map { ex ->
            "rec:" + RecordsEngine.exerciseRecordKey(
                ex.name.trim(), ex.isSingleLeg, ex.legSide, ex.defaultUnit, ex.inSpikes,
                RecordsEngine.recordInventory(ex.inventoryName, ex.inventoryUnit, ex.inventoryValue, ex.defaultUnit)
            )
        }.toSet()
    }
    val now = remember { System.currentTimeMillis() }

    fun update(change: VisualizationConfig.() -> VisualizationConfig) = viewModel.updateVisualization { it.change() }
    fun updateChart(id: Long, change: ChartConfig.() -> ChartConfig) =
        update { copy(charts = charts.map { if (it.id == id) it.change() else it }) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(text = "Визуализация", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpIconButton(onClick = rememberHelp(HelpTopic.F_VISUALIZATION), id = "viz.help")
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SegmentedControl(
                    modifier = Modifier.coachTarget("viz.range"),
                    options = ChartRange.entries.map { it.label },
                    selectedIndex = config.range.ordinal,
                    onSelect = { i -> update { copy(range = ChartRange.entries[i]) } }
                )
            }
            items(config.charts, key = { "chart:" + it.id }) { chart ->
                ChartCard(
                    chart = chart,
                    byId = byId,
                    range = config.range,
                    now = now,
                    onSettings = { editingChart = chart },
                    onAddSeries = { pickerFor = chart.id },
                    onToggle = { id -> updateChart(chart.id) { copy(series = series.map { if (it.id == id) it.copy(visible = !it.visible) else it }) } },
                    onEditStyle = { editingStyle = chart.id to it }
                )
            }
            item {
                OutlinedButton(onClick = { pickerFor = NEW_CHART }, modifier = Modifier.fillMaxWidth().coachTarget("viz.add")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить график")
                }
            }
        }
    }

    pickerFor?.let { target ->
        val chart = config.charts.firstOrNull { it.id == target }
        if (target == NEW_CHART) {
            // новый график: один показатель, название — как у показателя
            SeriesPicker(
                all = allSeries,
                chosen = emptySet(),
                favorites = favorites,
                onToggle = { id ->
                    val title = byId[id]?.title ?: "$DEFAULT_TITLE_PREFIX ${config.charts.size + 1}"
                    update {
                        val newId = (charts.maxOfOrNull { it.id } ?: 0) + 1
                        copy(charts = charts + ChartConfig(id = newId, title = title, series = listOf(SeriesStyle(id, SeriesStyle.AUTO_COLOR))))
                    }
                    pickerFor = null
                },
                onDismiss = { pickerFor = null }
            )
        } else if (chart != null) {
            SeriesPicker(
                all = allSeries,
                chosen = chart.series.map { it.id }.toSet(),
                favorites = favorites,
                onToggle = { id ->
                    updateChart(target) {
                        if (series.any { it.id == id }) copy(series = series.filterNot { it.id == id })
                        else copy(
                            series = series + SeriesStyle(id, SeriesStyle.AUTO_COLOR),
                            // график без своего названия называется по первому показателю
                            title = if (series.isEmpty() && title.startsWith(DEFAULT_TITLE_PREFIX)) byId[id]?.title ?: title else title
                        )
                    }
                },
                onDismiss = { pickerFor = null }
            )
        }
    }

    editingStyle?.let { (chartId, style) ->
        SeriesStyleDialog(
            title = byId[style.id]?.title ?: "",
            initial = style,
            onDismiss = { editingStyle = null },
            onSave = { changed ->
                updateChart(chartId) { copy(series = series.map { if (it.id == changed.id) changed else it }) }
                editingStyle = null
            },
            onRemove = {
                confirm.delete("показатель с графика", "«${byId[style.id]?.title ?: ""}» исчезнет с этого графика. Данные не изменятся.") {
                    updateChart(chartId) { copy(series = series.filterNot { it.id == style.id }) }
                    editingStyle = null
                }
            }
        )
    }

    editingChart?.let { chart ->
        ChartSettingsDialog(
            initial = chart,
            canDelete = config.charts.size > 1,
            onDismiss = { editingChart = null },
            onSave = { changed ->
                updateChart(chart.id) { changed }
                editingChart = null
            },
            onDelete = {
                confirm.delete("график", "График «${chart.title}» будет удалён. Данные не изменятся.") {
                    update { copy(charts = charts.filterNot { it.id == chart.id }) }
                    editingChart = null
                }
            }
        )
    }
}

private const val NEW_CHART = -1L

/** Карточка одного графика: заголовок, график, масштаб и показатели. */
@Composable
private fun ChartCard(
    chart: ChartConfig,
    byId: Map<String, AnalyticsSeries>,
    range: ChartRange,
    now: Long,
    onSettings: () -> Unit,
    onAddSeries: () -> Unit,
    onToggle: (String) -> Unit,
    onEditStyle: (SeriesStyle) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val theme = themeColors()
    val chosen = chart.series.filter { it.id in byId }
    val plots = remember(chart, byId, range, theme) {
        chosen.mapIndexedNotNull { index, style ->
            if (!style.visible) return@mapIndexedNotNull null
            val series = byId.getValue(style.id)
            val points = RecordInsights.inRange(ChartData.aggregate(series.samples, chart.aggregation, series.lowerIsBetter), range, now)
            Plot(series, style, ChartData.transform(points, chart.mode, chart.smoothing), colorOf(style, index, theme))
                .takeIf { it.points.isNotEmpty() }
        }
    }
    val unitOverride = if (chart.aggregation == Aggregation.WEEK_COUNT) "попыток" else null

    AppCard(modifier = Modifier.coachTarget("viz.chart")) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(chart.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${chart.aggregation.label} · ${chart.mode.label}" + if (chart.smoothing) " · сглаживание" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, contentDescription = "Настроить график", tint = cs.onSurfaceVariant) }
            }
            Column(modifier = Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    chosen.isEmpty() -> Text(
                        "Добавьте показатель: упражнение, старты, вес или разбег",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                    plots.isEmpty() -> Text(
                        "За этот период данных нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                    else -> MultiSeriesChart(plots, chart.mode, chart.fromZero, chart.byOrder || chart.aggregation == Aggregation.EACH, unitOverride)
                }
                // при одном показателе название уже в заголовке, отдельная строка нужна только для нескольких
                chosen.forEachIndexed { index, style ->
                    val series = byId.getValue(style.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEditStyle(style) }
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colorOf(style, index, theme)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (chosen.size > 1 || series.title != chart.title) {
                                Text(series.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                listOfNotNull(series.last?.let { "последний ${ChartData.formatTrim(it, ChartData.maxDecimals(series.unit))} ${series.unit}" }, "${series.attempts} попыток").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onToggle(style.id) }) {
                            Icon(
                                if (style.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (style.visible) "Скрыть" else "Показать",
                                tint = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                TextButton(onClick = onAddSeries) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Показатель")
                }
            }
        }
    }
}

/**
 * График нескольких показателей: сетка, подсказка со значениями под пальцем и масштаб.
 * Кнопки лупы приближают график, ползунок под ним двигает видимое окно.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSeriesChart(
    plots: List<Plot>,
    mode: ChartMode,
    fromZero: Boolean,
    byOrder: Boolean,
    unitOverride: String?
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reveal = remember(plots) { Animatable(0f) }
    LaunchedEffect(plots) { reveal.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }
    var scrub by remember(plots) { mutableStateOf<Float?>(null) }
    // масштаб: двумя пальцами, как фотографию, или лупами под графиком
    val window = remember { ChartWindow() }

    val positions = remember(plots, byOrder) { ChartData.positions(plots.flatMap { p -> p.points.map { it.date } }, byOrder) }
    fun screenOf(date: Long): Float = window.screen(positions[date] ?: 0f)
    // шкала по точкам, которые видны в окне: при приближении график растягивается и по высоте
    val inWindow = plots.flatMap { p -> p.points.filter { screenOf(it.date) in -0.001f..1.001f } }.ifEmpty { plots.flatMap { it.points } }
    val (low, high) = ChartData.yBounds(inWindow.map { it.value }, mode, fromZero)
    val span = high - low
    val pad = 0.08
    // цена деления растёт с приближением: до сотых секунды, граммов и сотых сантиметра
    val unitDecimals = if (unitOverride != null) 0 else plots.maxOf { ChartData.maxDecimals(it.series.unit) }
    val decimals = ChartData.axisDecimals(span, if (mode == ChartMode.ABSOLUTE) unitDecimals else 1)

    fun valueLabel(v: Double): String = when (mode) {
        ChartMode.ABSOLUTE -> ChartData.formatFixed(v, decimals)
        ChartMode.NORMALIZED -> "${ChartData.formatFixed(v, decimals)} %"
        ChartMode.CHANGE -> (if (v > 0) "+" else "") + "${ChartData.formatFixed(v, decimals)} %"
    }
    fun nearest(points: List<ChartPoint>, screen: Float): ChartPoint? = points.minByOrNull { abs(screenOf(it.date) - screen) }

    val dates = positions.keys.sorted()
    val windowDates = dates.filter { screenOf(it) in -0.001f..1.001f }.ifEmpty { dates }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val picked = scrub?.let { s -> plots.mapNotNull { p -> nearest(p.points, s)?.let { p to it } } }
            if (!picked.isNullOrEmpty()) {
                Column {
                    Text(shortDate(picked.first().second.date), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        picked.forEach { (plot, point) ->
                            Text(
                                "${ChartData.formatTrim(point.raw, if (unitOverride != null) 0 else ChartData.maxDecimals(plot.series.unit))} ${unitOverride ?: plot.series.unit}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = plot.color
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Проведите пальцем по графику, чтобы увидеть значения",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
        Row {
            Column(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.SpaceBetween) {
                // подписи на уровне линий сетки
                listOf(1.0, 0.75, 0.5, 0.25, 0.0).forEach { share ->
                    val v = low + span * (share - pad) / (1 - 2 * pad)
                    Text(valueLabel(v), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(200.dp)
                    .pinchZoom(window, inset = 10.dp, onZoom = { scrub = null })
                    .pointerInput(plots) {
                        val inset = 10.dp.toPx()
                        detectTapGestures(
                            // двойное нажатие возвращает весь график
                            onDoubleTap = { window.reset(); scrub = null }
                        ) { offset ->
                            scrub = ((offset.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    .pointerInput(plots) {
                        val inset = 10.dp.toPx()
                        // только горизонтальное движение: вертикальное остаётся прокруткой экрана
                        detectHorizontalDragGestures { change, _ ->
                            scrub = ((change.position.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                        }
                    }
            ) {
                // отступ по краям: крайние точки рисуются целиком
                val inset = 10.dp.toPx()
                val w = size.width
                val h = size.height
                fun x(date: Long) = inset + screenOf(date) * (w - 2 * inset)
                fun y(v: Double) = h - ((pad + (v - low) / span * (1 - 2 * pad)) * h).toFloat()

                repeat(5) { i ->
                    val gy = h * i / 4f
                    drawLine(cs.outlineVariant, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                if (mode == ChartMode.CHANGE && low < 0 && high > 0) {
                    drawLine(cs.outline, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.5f)
                }

                // при приближении всё, что за краями окна, обрезается
                clipRect(left = 0f, right = w * reveal.value) {
                    plots.forEach { plot ->
                        val style = plot.style
                        if (style.style != LineStyle.POINTS && plot.points.size > 1) {
                            val path = Path()
                            plot.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.date), y(p.value)) else path.lineTo(x(p.date), y(p.value)) }
                            drawPath(path, plot.color, style = Stroke(width = style.width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        if (style.style != LineStyle.LINE || plot.points.size == 1) {
                            plot.points.forEach { p -> drawCircle(plot.color, radius = (style.width + 1.5f).dp.toPx(), center = Offset(x(p.date), y(p.value))) }
                        }
                    }
                }

                scrub?.let { s ->
                    val sx = inset + s * (w - 2 * inset)
                    drawLine(cs.onSurfaceVariant, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
                    plots.forEach { plot ->
                        nearest(plot.points, s)?.let { p ->
                            val c = Offset(x(p.date), y(p.value))
                            drawCircle(cs.surface, radius = 7.dp.toPx(), center = c)
                            drawCircle(plot.color, radius = 4.5.dp.toPx(), center = c)
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 40.dp)) {
            Text(shortDate(windowDates.first()), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(shortDate(windowDates.last()), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        // масштаб: пальцы или лупы меняют приближение, ползунок двигает окно по времени
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { window.zoomBy(0.5f, focus = 1f); scrub = null }, enabled = window.isZoomed) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Отдалить")
            }
            Text(window.label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            IconButton(onClick = { window.zoomBy(2f, focus = 1f); scrub = null }, enabled = window.zoom < 64f) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить")
            }
            if (window.isZoomed) {
                Slider(
                    value = window.slider,
                    onValueChange = { window.slider = it; scrub = null },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    "Раздвиньте двумя пальцами, чтобы приблизить",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
        }
    }
}

/** Настройки графика: название, что считать точкой, шкала, ось и сглаживание. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartSettingsDialog(
    initial: ChartConfig,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (ChartConfig) -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var chart by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_CHART_SETTINGS) { Text("Настройки графика") } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = chart.title,
                    onValueChange = { chart = chart.copy(title = it.take(60)) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Что показывать", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Aggregation.entries.forEach { a ->
                        ToggleChip(label = a.label, selected = chart.aggregation == a, onClick = { chart = chart.copy(aggregation = a) })
                    }
                }
                Text("Шкала", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                SegmentedControl(
                    options = ChartMode.entries.map { it.label },
                    selectedIndex = chart.mode.ordinal,
                    onSelect = { chart = chart.copy(mode = ChartMode.entries[it]) }
                )
                Text(
                    text = when (chart.mode) {
                        ChartMode.ABSOLUTE -> "Значения как есть: удобно для показателей в одних единицах"
                        ChartMode.NORMALIZED -> "Каждый показатель растянут от своего минимума до максимума"
                        ChartMode.CHANGE -> "Прирост от первой точки периода: сравнимы разные единицы"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                if (chart.aggregation != Aggregation.EACH) {
                    SwitchRow("Точки через равные промежутки", "По порядку, а не по датам: перерывы не растягивают график", chart.byOrder) {
                        chart = chart.copy(byOrder = it)
                    }
                } else {
                    Text(
                        "Попытки показываются по порядку, чтобы попытки одного дня не сливались в столбик",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                SwitchRow("Сглаживание", "Среднее по трём соседним точкам", chart.smoothing) { chart = chart.copy(smoothing = it) }
                if (chart.mode == ChartMode.ABSOLUTE) {
                    SwitchRow("Шкала от нуля", "Иначе шкала подстраивается под данные", chart.fromZero) { chart = chart.copy(fromZero = it) }
                }
                if (canDelete) {
                    HorizontalDivider(color = cs.outlineVariant)
                    TextButton(onClick = onDelete) { Text("Удалить график", color = cs.error) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(chart.copy(title = chart.title.ifBlank { initial.title })) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Выбор показателей: поиск, вид, избранное и сортировка по алфавиту. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesPicker(
    all: List<AnalyticsSeries>,
    chosen: Set<String>,
    favorites: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<SeriesGroup?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var ascending by remember { mutableStateOf(true) }
    ExercisePickerScreen(title = "Показатели", doneLabel = "Готово", onDismiss = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск показателя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ToggleChip(label = "Все", selected = group == null, onClick = { group = null })
            SeriesGroup.entries.forEach { g ->
                ToggleChip(label = g.label, selected = group == g, onClick = { group = if (group == g) null else g })
            }
            ToggleChip(label = "Избранное", selected = onlyFavorites, onClick = { onlyFavorites = !onlyFavorites })
            ToggleChip(label = if (ascending) "А → Я" else "Я → А", selected = false, onClick = { ascending = !ascending })
        }
        val filtered = all
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            .filter { group == null || it.group == group }
            .filter { !onlyFavorites || it.id in favorites }
            .let { list -> if (ascending) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.title.lowercase() } }
        if (filtered.isEmpty()) {
            Text(
                if (onlyFavorites) "Среди избранных упражнений ничего нет. Отметьте упражнения звёздочкой в каталоге." else "Ничего не найдено",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
        filtered.forEach { s ->
            val selected = s.id in chosen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) cs.primaryContainer else cs.surfaceContainerLow)
                    .clickable { onToggle(s.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyMedium, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
                    Text(
                        "${s.group.label} · ${s.samples.size} дней · ${s.attempts} попыток",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (selected) Icon(Icons.Default.Check, contentDescription = "Выбрано", tint = cs.onPrimaryContainer)
            }
        }
    }
}

/** Настройка показателя: цвет (по умолчанию из темы), вид линии и толщина. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesStyleDialog(
    title: String,
    initial: SeriesStyle,
    onDismiss: () -> Unit,
    onSave: (SeriesStyle) -> Unit,
    onRemove: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var style by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_CHART_SETTINGS) { Text(title, style = MaterialTheme.typography.titleMedium) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Цвет", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // первый вариант — цвет темы: меняется вместе с расцветкой приложения
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cs.primaryContainer)
                            .border(3.dp, if (style.color == SeriesStyle.AUTO_COLOR) cs.onSurface else Color.Transparent, CircleShape)
                            .clickable { style = style.copy(color = SeriesStyle.AUTO_COLOR) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Цвет темы", tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    SeriesColors.forEach { color ->
                        val selected = style.color == color.toArgb()
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(3.dp, if (selected) cs.onSurface else Color.Transparent, CircleShape)
                                .clickable { style = style.copy(color = color.toArgb()) }
                        )
                    }
                }
                Text("Вид", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                SegmentedControl(
                    options = LineStyle.entries.map { it.label },
                    selectedIndex = style.style.ordinal,
                    onSelect = { style = style.copy(style = LineStyle.entries[it]) }
                )
                Text("Толщина: ${style.width.toInt()}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Slider(value = style.width, onValueChange = { style = style.copy(width = it) }, valueRange = 1f..6f, steps = 4)
                HorizontalDivider(color = cs.outlineVariant)
                TextButton(onClick = onRemove) { Text("Убрать с графика", color = cs.error) }
            }
        },
        confirmButton = { Button(onClick = { onSave(style) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
