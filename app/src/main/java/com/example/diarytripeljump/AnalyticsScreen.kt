package com.example.diarytripeljump

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.AppScreenScaffold
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.SectionHeader
import com.example.diarytripeljump.ui.components.StatTile
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.components.screenContentPadding
import com.example.diarytripeljump.ui.theme.NumberLarge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {}
) {
    val trainingsList by viewModel.allTrainingsWithExercises.collectAsState()
    val competitionsList by viewModel.allCompetitions.collectAsState()
    val rawResults by viewModel.rawTripleJumpResults.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()

    val hasData = trainingsList.isNotEmpty() || competitionsList.isNotEmpty()

    val doneTrainings = remember(trainingsList) { trainingsList.count { !it.training.isSkipped } }
    val skippedTrainings = remember(trainingsList) { trainingsList.count { it.training.isSkipped } }

    // Результаты в метрах на соревнованиях — для графика динамики (от старых к новым, последние 12)
    val jumpSeries = remember(rawResults) {
        rawResults
            .filter { !it.isAntiRecord && it.isCompetition && it.resultUnit == ResultUnit.M && it.result > 0 }
            .sortedBy { it.date }
            .takeLast(12)
    }

    val weekly = remember(trainingsList) { buildWeeklyBuckets(trainingsList.filter { !it.training.isSkipped }.map { it.training.date }) }

    AppScreenScaffold(
        modifier = modifier,
        title = "Аналитика",
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(inner),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasData) {
                item {
                    EmptyState(
                        icon = Icons.Default.Analytics,
                        title = "Пока нет данных",
                        message = "Добавьте тренировки или соревнования — здесь появятся графики и ключевые показатели."
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = "$doneTrainings",
                            label = "Тренировок",
                            icon = Icons.Default.FitnessCenter,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "$skippedTrainings",
                            label = "Пропусков",
                            icon = Icons.Default.Block,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "${competitionsList.size}",
                            label = "Стартов",
                            icon = Icons.Default.EmojiEvents,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item { JumpDynamicsCard(series = jumpSeries.map { it.result.toFloat() }, dates = jumpSeries.map { it.date }) }

                item { WeeklyTrainingsCard(buckets = weekly) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = "Рекомендации по технике")
                        AppCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                RecommendationItem(
                                    title = "Сохранение жёсткости во 2-й фазе",
                                    desc = "Удерживать ритм отталкивания в фазе Step (Шаг) для сохранения горизонтальной скорости."
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                RecommendationItem(
                                    title = "Стабильность разбега",
                                    desc = "Удерживать темп на последних 4 шагах перед бруском отталкивания без подбора шага."
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** График динамики результатов в тройном прыжке. */
@Composable
private fun JumpDynamicsCard(series: List<Float>, dates: List<Long>) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Динамика результатов",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = "Тройной прыжок, соревнования",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (series.size >= 2) {
                    val delta = series.last() - series.first()
                    val positive = delta >= 0f
                    StatusPill(
                        text = (if (positive) "+" else "−") + String.format(Locale.getDefault(), "%.2f м", kotlin.math.abs(delta)),
                        contentColor = if (positive) cs.onPrimaryContainer else cs.onErrorContainer,
                        containerColor = if (positive) cs.primaryContainer else cs.errorContainer,
                        icon = if (positive) Icons.AutoMirrored.Filled.TrendingUp else null
                    )
                }
            }

            if (series.size >= 2) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f", series.last()),
                        style = NumberLarge,
                        color = cs.onSurface
                    )
                    Text(
                        text = "м · последний результат",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                LineChart(
                    values = series,
                    lineColor = cs.primary,
                    fillColor = cs.primary.copy(alpha = 0.22f),
                    gridColor = cs.outlineVariant,
                    dotCoreColor = cs.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = fmt.format(Date(dates.first())), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(text = fmt.format(Date(dates.last())), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            } else {
                Text(
                    text = "Для графика нужно минимум два результата с соревнований. Добавьте их на вкладке «Рекорды».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LineChart(
    values: List<Float>,
    lineColor: Color,
    fillColor: Color,
    gridColor: Color,
    dotCoreColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val minV = values.min()
        val maxV = values.max()
        val range = if (maxV - minV > 0.0001f) maxV - minV else 1f

        val padTop = 12.dp.toPx()
        val padBottom = 12.dp.toPx()
        val padH = 8.dp.toPx()
        val chartW = size.width - 2 * padH
        val chartH = size.height - padTop - padBottom

        fun px(i: Int): Float = padH + chartW * i / (values.size - 1)
        fun py(v: Float): Float = padTop + chartH * (1f - (v - minV) / range)

        // Сетка
        for (i in 0..2) {
            val y = padTop + chartH * i / 2f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val linePath = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) linePath.moveTo(px(i), py(v)) else linePath.lineTo(px(i), py(v))
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(px(values.lastIndex), size.height - padBottom)
            lineTo(px(0), size.height - padBottom)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = padTop,
                endY = size.height
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        values.forEachIndexed { i, v ->
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(px(i), py(v)))
            drawCircle(color = dotCoreColor, radius = 2.5.dp.toPx(), center = Offset(px(i), py(v)))
        }
    }
}

private data class WeekBucket(val label: String, val count: Int)

/** Количество выполненных тренировок по неделям, последние 8 недель (включая текущую). */
private fun buildWeeklyBuckets(trainingDates: List<Long>): List<WeekBucket> {
    fun weekStart(millis: Long): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = millis
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val counts = trainingDates.groupingBy { weekStart(it) }.eachCount()
    val labelFormat = SimpleDateFormat("d.MM", Locale.forLanguageTag("ru"))

    val result = mutableListOf<WeekBucket>()
    var cursor = weekStart(System.currentTimeMillis())
    repeat(8) {
        result.add(0, WeekBucket(labelFormat.format(Date(cursor)), counts[cursor] ?: 0))
        val prev = Calendar.getInstance().apply { timeInMillis = cursor; add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
        cursor = weekStart(prev)
    }
    return result
}

@Composable
private fun WeeklyTrainingsCard(buckets: List<WeekBucket>) {
    val cs = MaterialTheme.colorScheme
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val total = buckets.sumOf { it.count }

    AppCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Нагрузка по неделям",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
                Text(
                    text = "Тренировок за 8 недель: $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                buckets.forEachIndexed { index, bucket ->
                    val isCurrent = index == buckets.lastIndex
                    val fraction = bucket.count.toFloat() / maxCount
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (bucket.count > 0) bucket.count.toString() else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(if (bucket.count > 0) fraction.coerceAtLeast(0.08f) else 0.04f)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(
                                        when {
                                            bucket.count == 0 -> cs.surfaceContainerHigh
                                            isCurrent -> cs.primary
                                            else -> cs.primary.copy(alpha = 0.45f)
                                        }
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bucket.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationItem(
    title: String,
    desc: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(
            icon = Icons.Default.Lightbulb,
            tint = MaterialTheme.colorScheme.secondary,
            size = 36.dp,
            iconSize = 20.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
