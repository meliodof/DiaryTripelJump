package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.coachTarget
import com.example.diarytripeljump.data.HelpTopic
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.diarytripeljump.ui.components.ChartWindow
import com.example.diarytripeljump.ui.components.pinchZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diarytripeljump.data.ChartRange
import com.example.diarytripeljump.data.ExerciseGoal
import com.example.diarytripeljump.data.RecordInsights
import com.example.diarytripeljump.data.RecordPoint
import com.example.diarytripeljump.data.RecordSummary
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.computeGoalProgress
import com.example.diarytripeljump.data.formatRecordDelta
import com.example.diarytripeljump.data.formatRecordValue
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.SegmentedControl
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.theme.NumberHero
import com.example.diarytripeljump.ui.theme.NumberLarge
import com.example.diarytripeljump.ui.theme.NumberMedium
import com.example.diarytripeljump.ui.theme.extraColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

private fun fullDate(millis: Long): String = SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(millis))

/** Экран рекорда: главное число, график всех результатов, темп до цели и история обновлений. */
@Composable
fun RecordDetailScreen(
    record: RecordSummary,
    goal: ExerciseGoal?,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onSetGoal: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
                    Text(
                        text = styledTitle(record.displayName, record.name),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    HelpIconButton(onClick = rememberHelp(HelpTopic.F_RECORD_DETAIL), id = "detail.help")
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Редактировать") }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.coachTarget("detail.hero")) { RecordHero(record, goal) }
                    Box(modifier = Modifier.coachTarget("detail.chart")) { ChartCard(record, goal) }
                    if (!record.isAnti) Box(modifier = Modifier.coachTarget("detail.goal")) { GoalCard(record, goal, onSetGoal) }
                    FactsRow(record)
                    HistoryCard(record)
                }
            }
        }
    }
}

/** Значение плавно набирается от нуля: рекорд ощущается, а не просто показывается. */
@Composable
private fun countUp(target: Double): Double {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    return if (value.isRunning || value.value == 0f) value.value.toDouble() else target
}

@Composable
private fun RecordHero(record: RecordSummary, goal: ExerciseGoal?) {
    val extra = MaterialTheme.extraColors
    val onHero = extra.onHero
    val points = record.points
    val season = RecordInsights.seasonBest(points, record.lowerIsBetter)
    val latest = RecordInsights.latest(points)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)), MaterialTheme.shapes.extraLarge)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (record.isAnti) "АНТИ-РЕКОРД" else "ЛИЧНЫЙ РЕКОРД",
                    style = MaterialTheme.typography.labelMedium,
                    color = onHero.copy(alpha = 0.85f)
                )
                Text(text = fullDate(record.bestDate), style = MaterialTheme.typography.labelMedium, color = onHero.copy(alpha = 0.75f))
            }
            Text(text = formatRecordValue(countUp(record.best), record.unit), style = NumberHero, color = onHero, maxLines = 1)
            HorizontalDivider(color = onHero.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth()) {
                HeroStat("СЕЗОН", season?.let { formatRecordValue(it, record.unit) } ?: "—", Modifier.weight(1f))
                HeroStat("ПОСЛЕДНИЙ", latest?.let { formatRecordValue(it.value, record.unit) } ?: "—", Modifier.weight(1f))
                HeroStat("ЦЕЛЬ", goal?.let { formatRecordValue(it.targetValue, record.unit) } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val onHero = MaterialTheme.extraColors.onHero
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = onHero.copy(alpha = 0.7f), maxLines = 1)
        Text(text = value, style = NumberMedium, color = onHero, maxLines = 1)
    }
}

@Composable
private fun ChartCard(record: RecordSummary, goal: ExerciseGoal?) {
    val cs = MaterialTheme.colorScheme
    var range by remember { mutableStateOf(ChartRange.ALL) }
    val points = remember(record.points, range) { RecordInsights.inRange(record.points, range).sortedBy { it.date } }
    var picked by remember(points) { mutableStateOf<RecordPoint?>(null) }
    val shown = picked ?: points.lastOrNull()

    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shown?.let { formatRecordValue(it.value, record.unit) } ?: "—",
                        style = NumberLarge,
                        color = cs.onSurface
                    )
                    Text(
                        text = shown?.let { fullDate(it.date) + if (it.isCompetition) " · старт" else " · тренировка" } ?: "Нет результатов",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Text(
                    text = "${points.size} ${pluralRu(points.size, "результат", "результата", "результатов")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
            if (points.isEmpty()) {
                Text(
                    text = "За этот период результатов нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                )
            } else {
                ProgressChart(
                    points = points,
                    lowerIsBetter = record.lowerIsBetter,
                    target = goal?.targetValue,
                    picked = picked,
                    onPick = { picked = it }
                )
            }
            SegmentedControl(
                options = ChartRange.entries.map { it.label },
                selectedIndex = range.ordinal,
                onSelect = { range = ChartRange.entries[it] }
            )
        }
    }
}

/** Линия рисуется слева направо; палец на графике показывает значение любой точки. */
@Composable
private fun ProgressChart(
    points: List<RecordPoint>,
    lowerIsBetter: Boolean,
    target: Double?,
    picked: RecordPoint?,
    onPick: (RecordPoint?) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val gold = MaterialTheme.extraColors.gold
    val haptics = LocalHapticFeedback.current
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing)) }

    // масштаб двумя пальцами, как у фотографии; двойное нажатие возвращает весь период
    val window = remember(points) { ChartWindow() }
    val first = points.first().date
    val spanTime = (points.last().date - first).coerceAtLeast(1L)
    fun positionOf(p: RecordPoint): Float = if (points.size == 1) 0.5f else (p.date - first).toFloat() / spanTime

    // шкала по видимым точкам: при приближении график растягивается и по высоте
    val inWindow = points.filter { window.screen(positionOf(it)) in -0.001f..1.001f }.ifEmpty { points }
    val values = inWindow.map { it.value } + listOfNotNull(target)
    val low = values.min()
    val high = values.max()
    val span = (high - low).takeIf { it > 0 } ?: (abs(high) * 0.1).coerceAtLeast(1.0)

    fun xOf(p: RecordPoint, width: Float): Float = window.screen(positionOf(p)) * width

    fun yOf(value: Double, height: Float): Float {
        val share = ((value - low) / span).toFloat()
        // выше на графике всегда лучше, даже если лучший результат меньше по числу
        val up = if (lowerIsBetter) 1f - share else share
        return height - (0.1f + up * 0.8f) * height
    }

    fun nearest(x: Float, width: Float): RecordPoint = points.minBy { abs(xOf(it, width) - x) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .pinchZoom(window, onZoom = { onPick(null) })
            .pointerInput(points) {
                detectTapGestures(onDoubleTap = { window.reset(); onPick(null) }) { offset ->
                    onPick(nearest(offset.x, size.width.toFloat()))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            .pointerInput(points) {
                var last: RecordPoint? = null
                // только горизонтальное движение: вертикальное остаётся прокруткой экрана
                detectHorizontalDragGestures(onDragEnd = { onPick(null) }, onDragCancel = { onPick(null) }) { change, _ ->
                    val point = nearest(change.position.x, size.width.toFloat())
                    if (point != last) {
                        last = point
                        onPick(point)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val line = Path()
        points.forEachIndexed { i, p ->
            val x = xOf(p, w)
            val y = yOf(p.value, h)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(xOf(points.last(), w), h)
            lineTo(xOf(points.first(), w), h)
            close()
        }

        if (target != null) {
            val y = yOf(target, h)
            drawLine(
                color = gold,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            )
        }

        clipRect(right = w * reveal.value) {
            drawPath(area, Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, cs.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { p ->
                val center = Offset(xOf(p, w), yOf(p.value, h))
                drawCircle(if (p.isCompetition) gold else cs.primary, radius = 3.5.dp.toPx(), center = center)
            }
        }

        picked?.let { p ->
            val x = xOf(p, w)
            val center = Offset(x, yOf(p.value, h))
            drawLine(cs.outline, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            drawCircle(cs.surface, radius = 8.dp.toPx(), center = center)
            drawCircle(cs.primary, radius = 5.dp.toPx(), center = center)
        }
    }
    if (points.size > 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (window.isZoomed) "Масштаб ${window.label}" else "Раздвиньте двумя пальцами, чтобы приблизить",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (window.isZoomed) {
                TextButton(onClick = { window.reset(); onPick(null) }) { Text("Весь период") }
            }
        }
    }
    }
}

@Composable
private fun GoalCard(record: RecordSummary, goal: ExerciseGoal?, onSetGoal: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon = Icons.Default.Flag, tint = extra.gold, size = 38.dp, iconSize = 20.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Цель", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(
                        text = goal?.let { formatRecordValue(it.targetValue, record.unit) } ?: "Не поставлена",
                        style = NumberMedium,
                        color = cs.onSurface
                    )
                }
                if (goal != null && computeGoalProgress(goal, record.best).achieved) {
                    StatusPill("Достигнута", extra.onGoldContainer, extra.goldContainer)
                }
            }
            if (goal != null) {
                val progress = computeGoalProgress(goal, record.best)
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                val remaining = progress.remaining
                if (remaining != null) {
                    val weeks = RecordInsights.weeksLeft(goal.deadline)
                    val pace = RecordInsights.weeklyPace(record.best, goal.targetValue, goal.deadline)
                    Text(
                        text = if (weeks != null && pace != null) {
                            "Осталось ${formatRecordValue(remaining, record.unit)}: $weeks ${pluralRu(weeks, "неделя", "недели", "недель")}, " +
                                "примерно ${RecordInsights.formatPace(pace, record.unit)} в неделю"
                        } else "Осталось ${formatRecordValue(remaining, record.unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface
                    )
                }
            }
            if (onSetGoal != null) {
                FilledTonalButton(onClick = onSetGoal, modifier = Modifier.fillMaxWidth()) {
                    Text(if (goal == null) "Поставить цель" else "Изменить цель")
                }
            }
        }
    }
}

@Composable
private fun FactsRow(record: RecordSummary) {
    val trend = RecordInsights.trend(record.points, record.lowerIsBetter)
    val days = RecordInsights.daysHeld(record.bestDate)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FactTile(record.attempts.toString(), "результатов", Modifier.weight(1f))
        FactTile(days.toString(), "${pluralRu(days, "день", "дня", "дней")} держится", Modifier.weight(1f))
        FactTile(
            value = trend?.let { (if (it >= 0) "+" else "−") + formatRecordValue(abs(it), record.unit) } ?: "—",
            label = "форма",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FactTile(value: String, label: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = value, style = NumberMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HistoryCard(record: RecordSummary) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    val entries = record.history.reversed()
    Text(text = "История рекорда", style = MaterialTheme.typography.titleMedium, color = cs.onBackground)
    AppCard {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = if (index == 0) Icons.Default.EmojiEvents else Icons.Default.History,
                    tint = if (index == 0) extra.gold else cs.onSurfaceVariant,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = formatRecordValue(entry.value, record.unit), style = NumberMedium, color = cs.onSurface)
                    Text(
                        text = buildList {
                            add(fullDate(entry.date))
                            add(if (entry.isCompetition) "старт" else "тренировка")
                            if (!entry.inventoryLabel.isNullOrBlank()) add(entry.inventoryLabel)
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (entry.previousValue != null) {
                    StatusPill(
                        text = formatRecordDelta(entry.value, entry.previousValue, record.unit),
                        contentColor = cs.onPrimaryContainer,
                        containerColor = cs.primaryContainer
                    )
                }
            }
            if (index < entries.lastIndex) {
                HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(start = 64.dp))
            }
        }
    }
}

/** Маленький график без осей: динамика одним взглядом. */
@Composable
fun Sparkline(values: List<Double>, lowerIsBetter: Boolean, color: Color, modifier: Modifier = Modifier) {
    val reveal = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { reveal.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }
    Canvas(modifier = modifier.clipToBounds()) {
        if (values.size < 2) return@Canvas
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0 } ?: 1.0
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / values.lastIndex * size.width
            val share = ((v - low) / span).toFloat()
            val y = size.height - (0.1f + (if (lowerIsBetter) 1f - share else share) * 0.8f) * size.height
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        clipRect(right = size.width * reveal.value) {
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private class Confetti(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val size: Float, val color: Color)

/** Новый рекорд: конфетти, вибрация и разница с прошлым результатом. Нажатие закрывает окно. */
@Composable
fun RecordCelebration(record: RecordSummary, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    val haptics = LocalHapticFeedback.current
    val pieces = remember {
        val colors = listOf(extra.gold, cs.primary, cs.secondary, cs.tertiary, Color.White)
        List(110) {
            Confetti(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.35f, speed = 0.7f + Random.nextFloat() * 0.6f,
                drift = (Random.nextFloat() - 0.5f) * 0.25f, spin = Random.nextFloat() * 900f,
                size = 6f + Random.nextFloat() * 10f, color = colors.random()
            )
        }
    }
    val time = remember { Animatable(0f) }
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    LaunchedEffect(Unit) {
        time.animateTo(1f, tween(durationMillis = 3000, easing = LinearEasing))
        delay(600)
        onDismiss()
    }
    val previous = record.history.lastOrNull()?.previousValue

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                pieces.forEach { p ->
                    val t = ((time.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (t > 0f) {
                        val center = Offset(
                            (p.x + p.drift * t) * size.width,
                            -40f + t * p.speed * (size.height + 80f)
                        )
                        rotate(degrees = p.spin * t, pivot = center) {
                            drawRect(
                                color = p.color.copy(alpha = (1.3f - t).coerceIn(0f, 1f)),
                                topLeft = Offset(center.x - p.size / 2, center.y - p.size / 3),
                                size = Size(p.size, p.size * 0.6f)
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .scale(pop.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = extra.gold, modifier = Modifier.size(72.dp))
                Text(text = "НОВЫЙ РЕКОРД", style = MaterialTheme.typography.titleMedium, color = extra.gold)
                Text(text = formatRecordValue(record.best, record.unit), style = NumberHero, color = Color.White)
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (previous != null) {
                    Text(
                        text = formatRecordDelta(record.best, previous, record.unit),
                        style = NumberMedium,
                        color = extra.gold
                    )
                }
            }
        }
    }
}

/** Единицы для подсказки на главной: тройной прыжок считается в метрах. */
fun RecordSummary.isMainJump(): Boolean = RecordInsights.isMainJump(this)
