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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.diarytripeljump.ui.components.Camera3D
import com.example.diarytripeljump.ui.components.V3
import com.example.diarytripeljump.ui.components.box
import com.example.diarytripeljump.ui.components.drawFaces
import com.example.diarytripeljump.ui.components.floorQuad
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
import com.example.diarytripeljump.data.ChartKind
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.diarytripeljump.data.RunUpTarget
import com.example.diarytripeljump.data.RunUpScope
import androidx.compose.material.icons.filled.GpsFixed
import com.example.diarytripeljump.data.runUpShots
import androidx.compose.ui.geometry.Size
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
    onOpenAdvice: () -> Unit = {},
    onOpenRunUp: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val advice by viewModel.allAdvice.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val trainings by viewModel.allTrainingsWithExercises.collectAsState()
    val competitions by viewModel.allCompetitions.collectAsState()
    val now = remember { System.currentTimeMillis() }
    // кратко о разбеге на карточке раздела: последняя тренировка с отметками
    val lastRunUp = remember(trainings, competitions) { RunUpTarget.build(runUpShots(trainings, competitions), RunUpScope.Last, now) }
    val listState = rememberLazyListState()
    AppScreenScaffold(
        title = "Аналитика",
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile,
        onHelp = rememberHelp(HelpTopic.ANALYTICS)
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(inner),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "visualization") {
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
            item(key = "advice") {
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
            item(key = "runup") {
                AppCard(modifier = Modifier.coachTarget("analytics.runup"), onClick = onOpenRunUp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconBadge(icon = Icons.Default.GpsFixed, tint = MaterialTheme.extraColors.gold, size = 48.dp, iconSize = 26.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Попадания на разбеге", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text(
                                lastRunUp?.let { "Последняя тренировка: точно ${it.hits}, заступов ${it.fouls}, недоступов ${it.shorts}" }
                                    ?: "Где попытки с разбега легли относительно бруска",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
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
                        "${chart.kind.label} · ${chart.aggregation.label} · ${chart.mode.label}" + if (chart.smoothing) " · сглаживание" else "",
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
                    else -> MultiSeriesChart(plots, chart.mode, chart.fromZero, chart.byOrder || chart.aggregation == Aggregation.EACH, unitOverride, chart.kind)
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
    unitOverride: String?,
    kind: ChartKind = ChartKind.LINE
) {
    if (kind == ChartKind.THREE_D) {
        Chart3D(plots, mode, unitOverride)
        return
    }
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
                // основание столбцов и заливки: ноль, если он на шкале, иначе низ графика
                val baseY = if (low <= 0.0 && high >= 0.0) y(0.0) else h
                clipRect(left = 0f, right = w * reveal.value) {
                    when (kind) {
                        ChartKind.BAR -> {
                            // столбцы показателей одного дня стоят рядом; ширина — по самому узкому промежутку между датами
                            val xs = plots.flatMap { p -> p.points.map { x(it.date) } }.distinct().sorted()
                            val gap = xs.zipWithNext { a, b -> b - a }.filter { it > 0.5f }.minOrNull() ?: (w - 2 * inset)
                            val slot = (gap * 0.8f).coerceIn(3.dp.toPx(), 36.dp.toPx())
                            val bw = slot / plots.size
                            plots.forEachIndexed { k, plot ->
                                plot.points.forEach { p ->
                                    val left = x(p.date) - slot / 2 + k * bw
                                    val top = minOf(y(p.value), baseY)
                                    drawRect(plot.color, topLeft = Offset(left, top), size = Size(bw * 0.9f, abs(baseY - y(p.value)).coerceAtLeast(1f)))
                                }
                            }
                        }
                        else -> plots.forEach { plot ->
                            val style = plot.style
                            fun trace(path: Path, startAtBase: Boolean) {
                                plot.points.forEachIndexed { i, p ->
                                    when {
                                        i == 0 && startAtBase -> { path.moveTo(x(p.date), baseY); path.lineTo(x(p.date), y(p.value)) }
                                        i == 0 -> path.moveTo(x(p.date), y(p.value))
                                        // ступенька: значение держится до следующей точки — так видно, сколько держался рекорд
                                        kind == ChartKind.STEP -> { path.lineTo(x(p.date), y(plot.points[i - 1].value)); path.lineTo(x(p.date), y(p.value)) }
                                        else -> path.lineTo(x(p.date), y(p.value))
                                    }
                                }
                            }
                            val path = Path().also { trace(it, startAtBase = false) }
                            if (kind == ChartKind.AREA && plot.points.size > 1) {
                                val area = Path().also { trace(it, startAtBase = true) }
                                area.lineTo(x(plot.points.last().date), baseY)
                                area.close()
                                drawPath(area, plot.color.copy(alpha = 0.18f))
                            }
                            if ((kind != ChartKind.LINE || style.style != LineStyle.POINTS) && plot.points.size > 1) {
                                drawPath(path, plot.color, style = Stroke(width = style.width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            if ((kind == ChartKind.LINE && style.style != LineStyle.LINE) || plot.points.size == 1) {
                                plot.points.forEach { p -> drawCircle(plot.color, radius = (style.width + 1.5f).dp.toPx(), center = Offset(x(p.date), y(p.value))) }
                            }
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
            }
        }
    }
}

/**
 * Объёмный график: каждый показатель — ряд столбцов в глубину, высота — значение, по ширине — даты по порядку.
 * Поворачивается пальцем (влево-вправо и наклон), двойное нажатие возвращает исходный вид.
 */
@Composable
private fun Chart3D(plots: List<Plot>, mode: ChartMode, unitOverride: String?) {
    val cs = MaterialTheme.colorScheme
    var yaw by remember { mutableFloatStateOf(-35f) }
    var pitch by remember { mutableFloatStateOf(26f) }
    val grow = remember(plots) { Animatable(0f) }
    LaunchedEffect(plots) { grow.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing)) }
    val measurer = rememberTextMeasurer()
    val dates = remember(plots) { plots.flatMap { p -> p.points.map { it.date } }.distinct().sorted() }
    val positions = remember(dates) { ChartData.positions(dates, byOrder = true) }
    val values = plots.flatMap { p -> p.points.map { it.value } }
    val (low, high) = ChartData.yBounds(values, mode, false)
    val decimals = if (unitOverride != null) 0 else plots.maxOf { ChartData.maxDecimals(it.series.unit) }
    fun label(v: Double): String = when (mode) {
        ChartMode.ABSOLUTE -> ChartData.formatTrim(v, decimals)
        ChartMode.NORMALIZED -> "${ChartData.formatTrim(v, 0)} %"
        ChartMode.CHANGE -> (if (v > 0) "+" else "") + "${ChartData.formatTrim(v, 1)} %"
    }
    val small = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = cs.onSurfaceVariant)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        yaw = (yaw + drag.x * 0.35f).coerceIn(-80f, 80f)
                        pitch = (pitch - drag.y * 0.25f).coerceIn(5f, 80f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { yaw = -35f; pitch = 26f }) }
        ) {
            val wide = 10f
            val tall = 4f
            val step = 1.4f
            val rows = plots.size
            val z0 = -(rows - 1) * step / 2
            val back = z0 + (rows - 1) * step + 0.9f
            val cam = Camera3D(yaw, pitch, 15f + rows * 1.2f, size.minDimension * 1.25f, Offset(size.width / 2, size.height * 0.62f))
            // пол и сетка на задней стенке
            drawFaces(cam, listOf(floorQuad(-wide / 2 - 0.6f, z0 - 1f, wide / 2 + 0.6f, back, 0f, cs.surfaceContainerHighest)), shaded = false)
            repeat(5) { k ->
                val y = tall * k / 4f
                drawLine(cs.outlineVariant, cam.project(V3(-wide / 2 - 0.6f, y, back)), cam.project(V3(wide / 2 + 0.6f, y, back)), strokeWidth = 1f)
            }
            val bw = (wide / dates.size.coerceAtLeast(1) * 0.7f).coerceIn(0.05f, 0.8f)
            // столбцы: дальние рисуются раньше ближних
            val bars = plots.flatMapIndexed { r, plot ->
                val z = z0 + r * step
                plot.points.map { p ->
                    val x = -wide / 2 + (positions[p.date] ?: 0f) * wide
                    val share = if (high - low < 1e-9) 0.5 else (p.value - low) / (high - low)
                    val h = ((0.08 + 0.92 * share) * tall * grow.value).toFloat().coerceAtLeast(0.03f)
                    box(V3(x - bw / 2, 0f, z - 0.45f), V3(x + bw / 2, h, z + 0.45f), plot.color)
                }
            }
            bars.sortedByDescending { faces -> cam.depth(faces.first().center) }.forEach { drawFaces(cam, it) }
            // подписи шкалы на задней стенке
            listOf(0f, 0.5f, 1f).forEach { k ->
                val v = low + (high - low) * ((k - 0.08f) / 0.92f).coerceAtLeast(0f)
                val t = measurer.measure(label(v), small)
                val at = cam.project(V3(wide / 2 + 0.8f, tall * k, back))
                drawText(t, topLeft = Offset(at.x.coerceIn(0f, size.width - t.size.width), (at.y - t.size.height / 2).coerceIn(0f, size.height - t.size.height)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(shortDate(dates.first()), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(shortDate(dates.last()), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
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
                Text("Вид графика", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ChartKind.entries.forEach { k ->
                        ToggleChip(label = k.label, selected = chart.kind == k, onClick = { chart = chart.copy(kind = k) })
                    }
                }
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
                if (chart.aggregation != Aggregation.EACH) {
                    SwitchRow("Точки через равные промежутки", "По порядку, а не по датам", chart.byOrder) {
                        chart = chart.copy(byOrder = it)
                    }
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
