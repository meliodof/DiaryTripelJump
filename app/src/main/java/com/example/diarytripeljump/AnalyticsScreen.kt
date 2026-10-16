package com.example.diarytripeljump

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TripleJumpResult
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
    val preparationPeriods by viewModel.allPreparationPeriods.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()

    var selectedPeriodIndex by remember { mutableIntStateOf(0) } // 0 = Всё время

    val selectedPeriod = remember(selectedPeriodIndex, preparationPeriods) {
        if (selectedPeriodIndex > 0 && selectedPeriodIndex <= preparationPeriods.size) {
            preparationPeriods[selectedPeriodIndex - 1]
        } else null
    }

    val filteredTrainings = remember(trainingsList, selectedPeriod) {
        if (selectedPeriod == null) trainingsList
        else trainingsList.filter { it.training.date >= selectedPeriod.startDate && it.training.date <= selectedPeriod.endDate }
    }

    val filteredCompetitions = remember(competitionsList, selectedPeriod) {
        if (selectedPeriod == null) competitionsList
        else competitionsList.filter { it.date >= selectedPeriod.startDate && it.date <= selectedPeriod.endDate }
    }

    val filteredRawResults = remember(rawResults, selectedPeriod) {
        if (selectedPeriod == null) rawResults
        else rawResults.filter { it.date >= selectedPeriod.startDate && it.date <= selectedPeriod.endDate }
    }

    val hasData = filteredTrainings.isNotEmpty() || filteredCompetitions.isNotEmpty()

    val doneTrainings = remember(filteredTrainings) { filteredTrainings.count { !it.training.isSkipped } }
    val skippedTrainings = remember(filteredTrainings) { filteredTrainings.count { it.training.isSkipped } }

    val jumpSeries = remember(filteredRawResults) {
        filteredRawResults
            .filter { !it.isAntiRecord && it.isCompetition && it.resultUnit == ResultUnit.M && it.result > 0 }
            .sortedBy { it.date }
            .takeLast(12)
    }

    val weekly = remember(filteredTrainings) { buildWeeklyBuckets(filteredTrainings.filter { !it.training.isSkipped }.map { it.training.date }) }

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
            // Filter Selector: "Всё время" vs Preparation Periods!
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ФИЛЬТР ПЕРИОДА:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedPeriodIndex == 0,
                            onClick = { selectedPeriodIndex = 0 },
                            label = { Text("Всё время", fontSize = 11.sp) }
                        )

                        preparationPeriods.forEachIndexed { index, period ->
                            FilterChip(
                                selected = selectedPeriodIndex == index + 1,
                                onClick = { selectedPeriodIndex = index + 1 },
                                label = { Text("🚩 ${period.name ?: "Период"}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            if (!hasData) {
                item {
                    EmptyState(
                        icon = Icons.Default.Analytics,
                        title = "Нет данных за этот период",
                        message = "В выбpанном периоде нет проведенных тренировок или соревнований."
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
                            value = "${filteredCompetitions.size}",
                            label = "Стартов",
                            icon = Icons.Default.EmojiEvents,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (jumpSeries.size >= 2) {
                    item {
                        AppCard {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SectionHeader(
                                    title = "Динамика тройного прыжка",
                                    subtitle = "Результаты официальных соревнований"
                                )
                                JumpLineChart(series = jumpSeries)
                            }
                        }
                    }
                }

                item {
                    AppCard {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SectionHeader(
                                title = "Активность по неделям",
                                subtitle = "За последние 6 недель"
                            )
                            WeeklyActivityChart(buckets = weekly)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpLineChart(series: List<TripleJumpResult>) {
    val cs = MaterialTheme.colorScheme
    val linePrimary = cs.primary
    val values = series.map { it.result.toFloat() }
    val minVal = (values.minOrNull() ?: 12f) - 0.2f
    val maxVal = (values.maxOrNull() ?: 16f) + 0.2f

    val range = if (maxVal - minVal < 0.1f) 1f else maxVal - minVal
    val lastResult = series.lastOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (lastResult != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f м", lastResult.result),
                        style = NumberLarge,
                        color = cs.onSurface
                    )
                    Text(
                        text = "Последний результат · ${formatShortDate(lastResult.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }

                val bestVal = series.maxOf { it.result }
                if (lastResult.result >= bestVal) {
                    StatusPill("Пик формы", cs.onPrimaryContainer, cs.primaryContainer, icon = Icons.AutoMirrored.Filled.TrendingUp)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val w = size.width
            val h = size.height
            val step = if (values.size > 1) w / (values.size - 1) else w

            val points = values.mapIndexed { index, v ->
                val x = index * step
                val norm = (v - minVal) / range
                val y = h - (norm * (h - 24.dp.toPx())) - 12.dp.toPx()
                Offset(x, y)
            }

            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x, p.y)
                else path.lineTo(p.x, p.y)
            }

            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.last().x, h)
                lineTo(points.first().x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(linePrimary.copy(alpha = 0.28f), linePrimary.copy(alpha = 0.0f))
                )
            )

            drawPath(
                path = path,
                color = linePrimary,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            points.forEach { p ->
                drawCircle(color = cs.surface, radius = 5.dp.toPx(), center = p)
                drawCircle(color = linePrimary, radius = 3.dp.toPx(), center = p)
            }
        }
    }
}

@Composable
private fun WeeklyActivityChart(buckets: List<WeeklyBucket>) {
    val cs = MaterialTheme.colorScheme
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEach { b ->
            val fraction = b.count.toFloat() / maxCount
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (b.count > 0) "${b.count}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (b.count > 0) cs.primary else cs.surfaceContainerHigh)
                )

                Text(
                    text = b.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private data class WeeklyBucket(val label: String, val count: Int)

private fun buildWeeklyBuckets(timestamps: List<Long>): List<WeeklyBucket> {
    val buckets = mutableListOf<WeeklyBucket>()
    val cal = Calendar.getInstance()

    for (w in 5 downTo 0) {
        val weekCal = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, -w)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMs = weekCal.timeInMillis
        val endMs = startMs + 7 * 86_400_000L

        val cnt = timestamps.count { it in startMs until endMs }
        val label = if (w == 0) "Тек." else "${w}н назад"
        buckets.add(WeeklyBucket(label, cnt))
    }
    return buckets
}

private fun formatShortDate(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val day = cal[Calendar.DAY_OF_MONTH]
    val month = when (cal[Calendar.MONTH]) {
        Calendar.JANUARY -> "янв"
        Calendar.FEBRUARY -> "фев"
        Calendar.MARCH -> "мар"
        Calendar.APRIL -> "апр"
        Calendar.MAY -> "май"
        Calendar.JUNE -> "июн"
        Calendar.JULY -> "июл"
        Calendar.AUGUST -> "авг"
        Calendar.SEPTEMBER -> "сен"
        Calendar.OCTOBER -> "окт"
        Calendar.NOVEMBER -> "ноя"
        Calendar.DECEMBER -> "дек"
        else -> ""
    }
    return "$day $month"
}
