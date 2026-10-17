package com.example.diarytripeljump

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.ExerciseResult
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.CompetitionStatus
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TrainingPeriod
import com.example.diarytripeljump.data.formatRecordDelta
import com.example.diarytripeljump.data.formatRecordValue
import com.example.diarytripeljump.data.startOfDay
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.AppDatePickerDialog
import com.example.diarytripeljump.ui.components.AppScreenScaffold
import com.example.diarytripeljump.ui.components.DateField
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.SectionHeader
import com.example.diarytripeljump.ui.components.StatTile
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.components.parseHexColor
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.components.rememberTodayStart
import com.example.diarytripeljump.ui.components.screenContentPadding
import com.example.diarytripeljump.ui.theme.NumberLarge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val KIND_ALL = 0
private const val KIND_30 = 1
private const val KIND_90 = 2
private const val KIND_PERIOD = 3
private const val KIND_CUSTOM = 4

/** Границы выбранного периода: [start] — начало первого дня, [end] — последняя миллисекунда последнего дня. */
private data class DateRange(val start: Long, val end: Long) {
    operator fun contains(t: Long) = t in start..end
    val days: Int get() = (Math.round((startOfDay(end) - start) / 86_400_000.0) + 1).toInt()
}

private fun addDays(millis: Long, n: Int): Long =
    Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

private fun endOfDay(millis: Long): Long = addDays(startOfDay(millis), 1) - 1

/** Период той же длины непосредственно перед [r]. */
private fun previousRange(r: DateRange): DateRange {
    val prevEnd = r.start - 1
    val prevStart = startOfDay(addDays(r.start, -r.days))
    return DateRange(prevStart, prevEnd)
}

private data class Bucket(val label: String, val count: Int, val current: Boolean)

private data class ExerciseStat(
    val name: String,
    val unit: ResultUnit,
    val lowerIsBetter: Boolean,
    val sessions: Int,
    val best: Double,
    val first: Double,
    val last: Double
)

/** Лучшее значение внутри одного результата: среди подходов и итога (для бега — меньшее). */
private fun resultValue(r: ExerciseResult, lowerBetter: Boolean): Double? {
    val c = mutableListOf<Double>()
    r.setResultsJson?.split(",")?.mapNotNullTo(c) { it.trim().toDoubleOrNull()?.takeIf { v -> v > 0 } }
    if (r.result > 0) c.add(r.result)
    if (c.isEmpty()) return null
    return if (lowerBetter) c.min() else c.max()
}

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val trainingsList by viewModel.allTrainingsWithExercises.collectAsState()
    val competitionsList by viewModel.allCompetitions.collectAsState()
    val rawResults by viewModel.rawTripleJumpResults.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val periods by viewModel.trainingPeriods.collectAsState()
    val recordEvents by viewModel.recordEvents.collectAsState()
    val pendingPeriod by viewModel.analyticsPeriodRequest.collectAsState()
    val today = rememberTodayStart()

    var kind by rememberSaveable { mutableIntStateOf(KIND_ALL) }
    var periodId by rememberSaveable { mutableLongStateOf(-1L) }
    var customStart by rememberSaveable { mutableLongStateOf(0L) }
    var customEnd by rememberSaveable { mutableLongStateOf(0L) }
    var showCustomDialog by remember { mutableStateOf(false) }

    // Переход из вкладки «Тренировки» → «Аналитика периода»
    LaunchedEffect(pendingPeriod) {
        pendingPeriod?.let {
            kind = KIND_PERIOD
            periodId = it
            viewModel.consumeAnalyticsPeriodRequest()
        }
    }

    val selectedPeriod: TrainingPeriod? = if (kind == KIND_PERIOD) periods.firstOrNull { it.id == periodId } else null
    val effectiveKind = if (kind == KIND_PERIOD && selectedPeriod == null) KIND_ALL else kind

    val range: DateRange? = when (effectiveKind) {
        KIND_30 -> DateRange(startOfDay(addDays(today, -29)), endOfDay(today))
        KIND_90 -> DateRange(startOfDay(addDays(today, -89)), endOfDay(today))
        KIND_PERIOD -> selectedPeriod?.let { DateRange(startOfDay(it.startDate), endOfDay(it.endDate)) }
        KIND_CUSTOM -> if (customStart > 0L && customEnd > 0L) DateRange(startOfDay(minOf(customStart, customEnd)), endOfDay(maxOf(customStart, customEnd))) else null
        else -> null
    }
    val prevRange = remember(range) { range?.let { previousRange(it) } }

    fun inRange(date: Long, r: DateRange?) = r == null || date in r

    fun activeCompetition(status: CompetitionStatus) = status != CompetitionStatus.SKIPPED && status != CompetitionStatus.CANCELLED

    val trainingsIn = remember(trainingsList, range) { trainingsList.filter { inRange(it.training.date, range) } }
    val doneIn = remember(trainingsIn) { trainingsIn.filter { !it.training.isSkipped } }
    val skippedCount = remember(trainingsIn) { trainingsIn.count { it.training.isSkipped } }
    val competitionsIn = remember(competitionsList, range) {
        competitionsList.filter { inRange(it.date, range) && activeCompetition(it.status) }
    }
    val recordsIn = remember(recordEvents, range) { recordEvents.filter { !it.isAnti && inRange(it.date, range) } }

    val prev = remember(trainingsList, competitionsList, recordEvents, prevRange) {
        prevRange?.let { pr ->
            Triple(
                trainingsList.count { !it.training.isSkipped && it.training.date in pr },
                competitionsList.count { it.date in pr && activeCompetition(it.status) },
                recordEvents.count { !it.isAnti && it.date in pr }
            )
        }
    }

    val jumpSeries = remember(rawResults, range) {
        rawResults
            .filter { !it.isAntiRecord && it.isCompetition && it.resultUnit == ResultUnit.M && it.result > 0 && inRange(it.date, range) }
            .sortedBy { it.date }
            .takeLast(12)
    }

    val buckets = remember(doneIn, range, today) { buildBuckets(doneIn.map { it.training.date }, range, today) }

    val exerciseStats = remember(doneIn) {
        data class Acc(val name: String, val unit: ResultUnit, val lower: Boolean, val points: MutableList<Pair<Long, Double>> = mutableListOf())
        val map = LinkedHashMap<String, Acc>()
        doneIn.forEach { t ->
            t.exerciseResults.forEach { r ->
                val name = r.exerciseName.trim()
                if (name.isBlank()) return@forEach
                val lower = RecordsEngine.isLowerBetter(name, r.resultUnit)
                val v = resultValue(r, lower) ?: return@forEach
                val key = "${name.lowercase()}|${r.isSingleLeg}|${r.legSide}|${r.resultUnit}"
                map.getOrPut(key) { Acc(name + RecordsEngine.legSuffix(r.isSingleLeg, r.legSide), r.resultUnit, lower) }
                    .points.add(r.date to v)
            }
        }
        map.values.map { a ->
            val sorted = a.points.sortedBy { it.first }
            ExerciseStat(
                name = a.name, unit = a.unit, lowerIsBetter = a.lower, sessions = sorted.size,
                best = if (a.lower) sorted.minOf { it.second } else sorted.maxOf { it.second },
                first = sorted.first().second, last = sorted.last().second
            )
        }.sortedByDescending { it.sessions }.take(6)
    }

    val hasAnyData = trainingsList.isNotEmpty() || competitionsList.isNotEmpty()
    val hasDataInRange = trainingsIn.isNotEmpty() || competitionsIn.isNotEmpty() || recordsIn.isNotEmpty()
    val dayFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("ru")) }

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
            if (!hasAnyData) {
                item {
                    EmptyState(
                        icon = Icons.Default.Analytics,
                        title = "Пока нет данных",
                        message = "Добавьте тренировки или соревнования — здесь появятся графики и ключевые показатели."
                    )
                }
            } else {
                // --- Выбор периода ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = "Период анализа", subtitle = "Всё время или любой подготовительный период")
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(selected = effectiveKind == KIND_ALL, onClick = { kind = KIND_ALL }, label = { Text("Всё время") })
                            FilterChip(selected = effectiveKind == KIND_30, onClick = { kind = KIND_30 }, label = { Text("30 дней") })
                            FilterChip(selected = effectiveKind == KIND_90, onClick = { kind = KIND_90 }, label = { Text("3 месяца") })
                            periods.sortedByDescending { it.startDate }.forEach { p ->
                                val c = parseHexColor(p.colorHex)
                                FilterChip(
                                    selected = effectiveKind == KIND_PERIOD && periodId == p.id,
                                    onClick = { kind = KIND_PERIOD; periodId = p.id },
                                    label = { Text(p.name) },
                                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(c)) }
                                )
                            }
                            FilterChip(
                                selected = effectiveKind == KIND_CUSTOM,
                                onClick = { showCustomDialog = true },
                                label = {
                                    Text(
                                        if (effectiveKind == KIND_CUSTOM && range != null)
                                            "${dayFmt.format(Date(range.start))} — ${dayFmt.format(Date(range.end))}"
                                        else "Свои даты"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                // --- Карточка выбранного периода ---
                if (range != null) {
                    item {
                        RangeSummaryCard(
                            title = when (effectiveKind) {
                                KIND_30 -> "Последние 30 дней"
                                KIND_90 -> "Последние 3 месяца"
                                KIND_PERIOD -> selectedPeriod?.name ?: "Период"
                                else -> "Свой период"
                            },
                            range = range,
                            color = if (effectiveKind == KIND_PERIOD && selectedPeriod != null) parseHexColor(selectedPeriod.colorHex) else cs.primary,
                            today = today,
                            isPreparationPeriod = effectiveKind == KIND_PERIOD,
                            dayFmt = dayFmt
                        )
                    }
                }

                if (!hasDataInRange) {
                    item {
                        EmptyState(
                            icon = Icons.Default.DateRange,
                            title = "В этом периоде данных нет",
                            message = "Выберите другой период или добавьте тренировки и старты за эти даты."
                        )
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("${doneIn.size}", "Тренировок", Icons.Default.FitnessCenter, cs.primary, Modifier.weight(1f))
                            StatTile("$skippedCount", "Пропусков", Icons.Default.Block, cs.error, Modifier.weight(1f))
                            StatTile("${competitionsIn.size}", "Стартов", Icons.Default.EmojiEvents, cs.tertiary, Modifier.weight(1f))
                        }
                    }
                    item {
                        val total = doneIn.size + skippedCount
                        val attendance = if (total > 0) doneIn.size * 100 / total else 0
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("${recordsIn.size}", "Обновлений рекордов", Icons.Default.MilitaryTech, cs.secondary, Modifier.weight(1f))
                            StatTile("$attendance%", "Посещаемость", Icons.AutoMirrored.Filled.TrendingUp, cs.primary, Modifier.weight(1f))
                        }
                    }

                    if (prev != null && prevRange != null) {
                        item {
                            ComparisonCard(
                                prevLabel = "${dayFmt.format(Date(prevRange.start))} — ${dayFmt.format(Date(prevRange.end))}",
                                rows = listOf(
                                    Triple("Тренировок", doneIn.size, prev.first),
                                    Triple("Стартов", competitionsIn.size, prev.second),
                                    Triple("Обновлений рекордов", recordsIn.size, prev.third)
                                )
                            )
                        }
                    }

                    item { JumpDynamicsCard(series = jumpSeries.map { it.result.toFloat() }, dates = jumpSeries.map { it.date }) }

                    item { LoadCard(buckets = buckets.first, monthly = buckets.second, scopeLabel = if (range == null) "за всё время" else "за период") }

                    if (exerciseStats.isNotEmpty()) {
                        item { ExercisesCard(stats = exerciseStats) }
                    }

                    if (recordsIn.isNotEmpty()) {
                        item { RecordsCard(events = recordsIn.take(6), dayFmt = dayFmt) }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomRangeDialog(
            initialStart = if (customStart > 0L) customStart else startOfDay(addDays(today, -29)),
            initialEnd = if (customEnd > 0L) customEnd else today,
            onDismiss = { showCustomDialog = false },
            onConfirm = { s, e ->
                customStart = minOf(s, e)
                customEnd = maxOf(s, e)
                kind = KIND_CUSTOM
                showCustomDialog = false
            }
        )
    }
}

@Composable
private fun CustomRangeDialog(
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    var start by remember { mutableLongStateOf(initialStart) }
    var end by remember { mutableLongStateOf(initialEnd) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой период", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(label = "С", millis = start, onClick = { pickStart = true }, modifier = Modifier.fillMaxWidth())
                DateField(label = "По", millis = end, onClick = { pickEnd = true }, modifier = Modifier.fillMaxWidth())
                if (end < start) {
                    Text(
                        "Дата окончания раньше начала — даты будут поменяны местами.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(start, end) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
    if (pickStart) AppDatePickerDialog(initialMillis = start, onDismiss = { pickStart = false }, onConfirm = { start = it; pickStart = false })
    if (pickEnd) AppDatePickerDialog(initialMillis = end, onDismiss = { pickEnd = false }, onConfirm = { end = it; pickEnd = false })
}

@Composable
private fun RangeSummaryCard(
    title: String,
    range: DateRange,
    color: Color,
    today: Long,
    isPreparationPeriod: Boolean,
    dayFmt: SimpleDateFormat
) {
    val cs = MaterialTheme.colorScheme
    val total = range.days
    val started = today >= range.start
    val finished = today > range.end
    val passed = if (started && !finished) (Math.round((today - range.start) / 86_400_000.0) + 1).toInt() else 0
    AppCard(containerColor = color.copy(alpha = 0.10f), border = BorderStroke(1.dp, color.copy(alpha = 0.45f))) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                if (isPreparationPeriod) {
                    StatusPill(
                        text = when {
                            !started -> "Впереди"
                            finished -> "Завершён"
                            else -> "Идёт сейчас"
                        },
                        contentColor = if (started && !finished) Color.White else color,
                        containerColor = if (started && !finished) color else color.copy(alpha = 0.16f)
                    )
                }
            }
            Text(
                text = "${dayFmt.format(Date(range.start))} — ${dayFmt.format(Date(range.end))} · $total ${pluralRu(total, "день", "дня", "дней")}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            if (isPreparationPeriod && started && !finished) {
                LinearProgressIndicator(
                    progress = { passed.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = color,
                    trackColor = color.copy(alpha = 0.18f)
                )
                Text("День $passed из $total", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ComparisonCard(prevLabel: String, rows: List<Triple<String, Int, Int>>) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Сравнение с предыдущим периодом", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(prevLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            rows.forEachIndexed { i, (label, now, before) ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                val diff = now - before
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                    Text("$before → $now", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    StatusPill(
                        text = when {
                            diff > 0 -> "+$diff"
                            diff < 0 -> "−${-diff}"
                            else -> "0"
                        },
                        contentColor = when {
                            diff > 0 -> cs.onPrimaryContainer
                            diff < 0 -> cs.onErrorContainer
                            else -> cs.onSurfaceVariant
                        },
                        containerColor = when {
                            diff > 0 -> cs.primaryContainer
                            diff < 0 -> cs.errorContainer
                            else -> cs.surfaceContainerHigh
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExercisesCard(stats: List<ExerciseStat>) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Частые упражнения", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text("Лучший результат и изменение от первого до последнего", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            stats.forEachIndexed { i, st ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(st.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${st.sessions} ${pluralRu(st.sessions, "раз", "раза", "раз")} · лучший ${formatRecordValue(st.best, st.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    if (st.sessions >= 2 && st.first != st.last) {
                        val improved = if (st.lowerIsBetter) st.last < st.first else st.last > st.first
                        StatusPill(
                            text = formatRecordDelta(st.last, st.first, st.unit),
                            contentColor = if (improved) cs.onPrimaryContainer else cs.onErrorContainer,
                            containerColor = if (improved) cs.primaryContainer else cs.errorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsCard(events: List<com.example.diarytripeljump.data.RecordEvent>, dayFmt: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Рекорды за период", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            events.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(dayFmt.format(Date(e.date)) + if (e.isCompetition) " · старт" else " · тренировка", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatRecordValue(e.value, e.unit), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        if (e.previousValue != null) {
                            Text(formatRecordDelta(e.value, e.previousValue, e.unit), style = MaterialTheme.typography.labelSmall, color = cs.primary)
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

                val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale("ru")) }
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


/** Недельные столбики для коротких периодов, месячные — для длинных (и для «всё время»). */
private fun buildBuckets(dates: List<Long>, range: DateRange?, today: Long): Pair<List<Bucket>, Boolean> {
    val from = range?.start ?: (dates.minOrNull() ?: today)
    val to = range?.end ?: today
    val days = (Math.round((startOfDay(to) - startOfDay(from)) / 86_400_000.0) + 1).toInt()
    val monthly = days > 119

    fun bucketStart(millis: Long): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = millis
        if (monthly) set(Calendar.DAY_OF_MONTH, 1) else set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun next(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        if (monthly) add(Calendar.MONTH, 1) else add(Calendar.WEEK_OF_YEAR, 1)
    }.timeInMillis

    val counts = dates.groupingBy { bucketStart(it) }.eachCount()
    val fmt = SimpleDateFormat(if (monthly) "LLL" else "d.MM", Locale("ru"))
    val starts = mutableListOf<Long>()
    var cursor = bucketStart(from)
    var guard = 0
    while (cursor <= to && guard++ < 600) {
        starts.add(cursor)
        cursor = next(cursor)
    }
    val limit = if (monthly) 12 else 17
    val todayBucket = bucketStart(today)
    val result = starts.takeLast(limit).map { st ->
        Bucket(fmt.format(Date(st)), counts[st] ?: 0, st == todayBucket)
    }
    return result to monthly
}

@Composable
private fun LoadCard(buckets: List<Bucket>, monthly: Boolean, scopeLabel: String) {
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
                    text = if (monthly) "Нагрузка по месяцам" else "Нагрузка по неделям",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
                Text(
                    text = "Тренировок $scopeLabel: $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 12) 3.dp else 6.dp)
            ) {
                buckets.forEach { bucket ->
                    val fraction = bucket.count.toFloat() / maxCount
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (bucket.count > 0) bucket.count.toString() else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurface,
                            maxLines = 1
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
                                            bucket.current -> cs.primary
                                            else -> cs.primary.copy(alpha = 0.45f)
                                        }
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bucket.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bucket.current) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (bucket.current) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
