package com.example.diarytripeljump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.Advice
import com.example.diarytripeljump.data.AdviceEngine
import com.example.diarytripeljump.data.AdviceKind
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.coachTarget
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableIntStateOf
import com.example.diarytripeljump.data.AnalyticsSeries
import com.example.diarytripeljump.data.ForecastMethod
import com.example.diarytripeljump.data.KnownLink
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.ui.components.pluralRu
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import com.example.diarytripeljump.data.Forecast
import com.example.diarytripeljump.data.Influence
import com.example.diarytripeljump.data.Insights
import com.example.diarytripeljump.data.RecordInsights
import com.example.diarytripeljump.data.Reliability
import com.example.diarytripeljump.data.formatRecordValue
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.theme.extraColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
}

/** Как меняется результат сейчас — обычными словами: «растёт примерно на 3 см в неделю». */
private fun changeText(f: Forecast): String {
    val improving = if (f.lowerIsBetter) f.slopePerWeek < 0 else f.slopePerWeek > 0
    val pace = RecordInsights.formatPace(abs(f.slopePerWeek), f.unit)
    val tiny = abs(f.slopePerWeek) < 1e-9 || (f.unit == ResultUnit.M && abs(f.slopePerWeek) < 0.005)
    return when {
        tiny -> "Сейчас результат держится примерно на одном уровне."
        improving -> "Сейчас результат улучшается примерно на $pace в неделю."
        else -> "Сейчас результат ухудшается примерно на $pace в неделю."
    }
}

/** Откуда взялся прогноз — одной фразой, без терминов. */
private fun basisText(f: Forecast): String = when (f.method) {
    ForecastMethod.OWN_DATA ->
        "Прогноз по вашим последним ${f.points} ${pluralRu(f.points, "дню", "дням", "дням")} с результатами: свежие весят больше, " +
            "а один случайный результат прогноз не сбивает. ${changeText(f)}"
    ForecastMethod.TYPICAL_GROWTH ->
        (if (f.points == 0) "Своих результатов в дневнике пока нет, поэтому прогноз — от рекорда в профиле"
        else "Своих результатов пока мало (${f.points}), поэтому прогноз — от последнего результата") +
            " с обычным приростом для вашего возраста (около ${(f.typicalGrowth * 100).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale("ru"), "%.1f", it) }} % в год)."
    ForecastMethod.LAST_RESULT ->
        "Своих результатов пока мало (${f.points}), поэтому ожидается примерно последний результат. Чем больше записей, тем точнее прогноз."
}

/**
 * Прогноз простыми словами: сколько ожидаем, к какой дате, насколько можно доверять и откуда это взялось.
 * На графике — результаты и ожидаемая точка; пунктир золотом — цель.
 */
@Composable
private fun ForecastCard(forecast: Forecast, onOpen: (() -> Unit)?, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val gold = MaterialTheme.extraColors.gold
    val reliabilityColor = when (forecast.reliability) {
        Reliability.HIGH -> cs.primary
        Reliability.MEDIUM -> cs.tertiary
        Reliability.LOW -> cs.error
    }
    AppCard(modifier = modifier, onClick = onOpen) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(forecast.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text(forecast.targetLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Убрать прогноз", tint = cs.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "≈ ${forecast.format(forecast.value)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
                StatusPill(
                    text = "надёжность ${forecast.reliability.label}",
                    contentColor = reliabilityColor,
                    containerColor = reliabilityColor.copy(alpha = 0.12f)
                )
            }
            forecast.goal?.let { goal ->
                val reaches = forecast.reachesGoal == true
                Text(
                    "Цель ${forecast.format(goal)} — " + if (reaches) "по прогнозу успеваешь" else "по прогнозу не успеваешь",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (reaches) cs.primary else cs.error
                )
            }
            if (forecast.history.isNotEmpty()) ForecastChart(forecast, gold)
            Text(basisText(forecast), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ForecastChart(f: Forecast, gold: androidx.compose.ui.graphics.Color) {
    val cs = MaterialTheme.colorScheme
    val first = f.history.first().date
    val span = (f.targetDate - first).coerceAtLeast(1L).toFloat()
    val values = f.history.map { it.value } + listOf(f.value) + listOfNotNull(f.goal)
    val lo = values.min()
    val hi = values.max()
    val range = (hi - lo).takeIf { it > 0 } ?: 1.0
    val last = f.history.last()
    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun x(date: Long) = pad + (date - first) / span * w
        fun y(v: Double) = pad + h - ((v - lo) / range * h).toFloat()
        f.goal?.let { g ->
            drawLine(gold, Offset(pad, y(g)), Offset(pad + w, y(g)), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        f.history.forEach { p -> drawCircle(cs.primary, radius = 3.5.dp.toPx(), center = Offset(x(p.date), y(p.value))) }
        // от последнего результата к ожидаемому
        drawLine(
            cs.tertiary, Offset(x(last.date), y(last.value)), Offset(x(f.targetDate), y(f.value)),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
        drawCircle(cs.surface, radius = 7.dp.toPx(), center = Offset(x(f.targetDate), y(f.value)))
        drawCircle(cs.tertiary, radius = 5.dp.toPx(), center = Offset(x(f.targetDate), y(f.value)))
    }
}

/** Выбор показателя и срока для своего прогноза. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastPickerDialog(series: List<AnalyticsSeries>, onDismiss: () -> Unit, onPick: (String, Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AnalyticsSeries?>(null) }
    var weeks by remember { mutableIntStateOf(0) }
    val shown = series.filter { it.samples.isNotEmpty() && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Прогноз по показателю") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Поиск: присед, бег, вес…") }, modifier = Modifier.fillMaxWidth()
                )
                Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    shown.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = s }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected?.id == s.id, onClick = { selected = s })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                                Text(
                                    "${s.samples.size} ${pluralRu(s.samples.size, "запись", "записи", "записей")}, ${s.unit}",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (shown.isEmpty()) Text("Ничего не найдено", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("К какому сроку", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "К ближайшему старту", 4 to "Через 4 недели", 8 to "Через 8 недель", 12 to "Через 12 недель").forEach { (w, label) ->
                        ToggleChip(label = label, selected = weeks == w, onClick = { weeks = w })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = selected != null, onClick = { selected?.let { onPick(it.id, weeks) } }) { Text("Показать прогноз") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Связь упражнения с тройным: направление, сдвиг в неделях и сила связи полоской. */
@Composable
private fun InfluenceRow(inf: Influence, onOpen: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val positive = inf.r > 0
    val color = if (positive) cs.primary else cs.error
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val strength = when {
            abs(inf.r) >= 0.7 -> "сильная"
            abs(inf.r) >= 0.5 -> "заметная"
            else -> "слабая"
        }
        val lagText = if (inf.lagWeeks == 0) "в ту же неделю" else "через ${inf.lagWeeks} нед."
        val chance = if (inf.p <= 0.01) "реже 1 раза из 100" else "примерно 1 раз из ${(1 / inf.p).roundToInt()}"
        Text(inf.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
        Text(
            (if (positive) "Когда это упражнение улучшается, тройной тоже растёт" else "Когда это упражнение улучшается, тройной снижается") +
                " — $lagText.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.weight(1f).height(8.dp)) {
                drawRoundRect(cs.surfaceContainerHigh, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                drawRoundRect(
                    color,
                    size = size.copy(width = size.width * abs(inf.r).toFloat()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("связь $strength", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Text(
            "По ${inf.weeks} неделям. Случайно такое совпадение бывает $chance.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

/** Что по исследованиям связано с тройным и записывает ли это спортсмен. */
@Composable
private fun KnownLinksCard(links: Map<KnownLink, Boolean>) {
    val cs = MaterialTheme.colorScheme
    AppCard(containerColor = cs.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("По исследованиям", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            links.forEach { (link, tracked) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(link.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface, modifier = Modifier.weight(1f))
                        StatusPill(
                            text = if (tracked) "есть в дневнике" else "не записываете",
                            contentColor = if (tracked) cs.primary else cs.onSurfaceVariant,
                            containerColor = if (tracked) cs.primary.copy(alpha = 0.12f) else cs.surfaceContainerHigh
                        )
                    }
                    Text(link.finding, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(link.source, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = cs.onSurfaceVariant)
                }
            }
            Text(
                "Это сравнение разных спортсменов. У одного спортсмена от недели к неделе связь бывает слабее — поэтому выше " +
                    "показаны только связи, подтверждённые вашими данными. Записывайте эти упражнения, чтобы их можно было проверить.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = cs.onSurfaceVariant
            )
        }
    }
}

private val AdviceKind.groupTitle: String
    get() = when (this) {
        AdviceKind.RISK -> "Важно"
        AdviceKind.CHANCE -> "Можно улучшить"
        AdviceKind.GOOD -> "Хорошие новости"
    }

/**
 * Раздел аналитики «Советы и прогноз»: прогноз результата к старту и срокам целей, связи упражнений с тройным
 * и все советы по данным дневника с тем, на чём они основаны. Скрытые с главной советы здесь остаются.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdviceScreen(viewModel: DiaryViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit, onOpenAssistant: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val insights by viewModel.insights.collectAsState()
    val all by viewModel.allAdvice.collectAsState()
    val dismissed by viewModel.dismissedAdvice.collectAsState()
    val records by viewModel.records.collectAsState()
    val goals by viewModel.goals.collectAsState()
    var filter by rememberSaveable { mutableStateOf<AdviceKind?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var openedRecord by remember { mutableStateOf<String?>(null) }
    var showForecastPicker by remember { mutableStateOf(false) }
    val allSeries by viewModel.analyticsSeries.collectAsState()

    val shown = all.filter { filter == null || it.kind == filter }

    fun open(a: Advice) {
        val key = a.recordKey
        if (key != null && records.any { it.key == key }) openedRecord = key else a.route?.let(onNavigate)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(text = "Советы и прогноз", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpIconButton(onClick = rememberHelp(HelpTopic.F_ADVICE), id = "advice.help")
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Вопрос к дневнику своими словами ---
            item(key = "assistant") {
                AppCard(onClick = onOpenAssistant, modifier = Modifier.fillMaxWidth().coachTarget("advice.assistant")) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = Icons.Default.QuestionAnswer, tint = cs.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Спросить дневник", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "«Итоги недели», «как мой присед», «готов ли я к старту» — ответ из ваших данных",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- Прогноз ---
            item(key = "forecast:title") { SectionTitle("Прогноз") }
            if (insights.forecasts.none { !it.custom }) {
                item(key = "forecast:empty") {
                    Text(
                        "Добавьте хотя бы один результат в тройном или личный рекорд в профиле — появится прогноз к старту.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            items(insights.forecasts, key = { "forecast:${it.key}" }) { f ->
                ForecastCard(
                    forecast = f,
                    onOpen = f.recordKey?.takeIf { k -> records.any { it.key == k } }?.let { k -> { openedRecord = k } },
                    onRemove = f.seriesId?.let { id -> { viewModel.removeForecast(id) } },
                    modifier = if (f == insights.forecasts.first()) Modifier.coachTarget("advice.forecast") else Modifier
                )
            }
            item(key = "forecast:add") {
                OutlinedButton(onClick = { showForecastPicker = true }, modifier = Modifier.fillMaxWidth().coachTarget("advice.forecastAdd")) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Прогноз по другому показателю")
                }
            }

            // --- Что связано с результатом: по своим данным и по исследованиям ---
            item(key = "influence:title") { SectionTitle("Что связано с результатом в тройном") }
            item(key = "influence:list") {
                AppCard(modifier = Modifier.coachTarget("advice.links")) {
                    if (insights.influences.isEmpty()) {
                        Text(
                            "По вашим данным надёжных связей пока нет. Для проверки нужно хотя бы ${Insights.Limits.MIN_PAIRS} недель, " +
                                "в которые есть и упражнение, и тройной.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    insights.influences.forEachIndexed { i, inf ->
                        InfluenceRow(inf, onOpen = if (records.any { it.key == inf.key }) { { openedRecord = inf.key } } else null)
                        if (i < insights.influences.lastIndex) HorizontalDivider(color = cs.outlineVariant)
                    }
                    if (insights.influences.isNotEmpty()) {
                        Text(
                            "Совпадение во времени не доказывает причину: это подсказка, что стоит обсудить с тренером.",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        )
                    }
                }
            }
            item(key = "influence:known") {
                KnownLinksCard(Insights.trackedKnownLinks(records.map { it.displayName }))
            }

            // --- Советы ---
            item(key = "advice:title") { SectionTitle("Советы") }
            item {
                FlowRow(
                    modifier = Modifier.coachTarget("advice.filters"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ToggleChip(label = "Все · ${all.size}", selected = filter == null, onClick = { filter = null })
                    AdviceKind.entries.forEach { kind ->
                        val count = all.count { it.kind == kind }
                        if (count > 0) {
                            ToggleChip(label = "${kind.groupTitle} · $count", selected = filter == kind, onClick = { filter = kind })
                        }
                    }
                }
            }

            if (all.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Lightbulb,
                        title = "Пока советов нет",
                        message = "Советы появятся, когда в дневнике будет больше тренировок, попыток разбега, целей и стартов."
                    )
                }
            }

            AdviceKind.entries.forEach { kind ->
                val group = shown.filter { it.kind == kind }
                if (group.isNotEmpty()) {
                    item(key = "group:$kind") {
                        Text(
                            kind.groupTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(group, key = { "advice:${it.id}" }) { a ->
                        AdviceCard(
                            advice = a,
                            hidden = a.id in dismissed,
                            onOpen = { open(a) },
                            onHide = { viewModel.dismissAdvice(a) },
                            onRestore = { viewModel.restoreAdvice(a) },
                            modifier = if (a == shown.first()) Modifier.coachTarget("advice.card") else Modifier
                        )
                    }
                }
            }

            // Как считаются советы: правила и пороги
            item(key = "rules") {
                AppCard(modifier = Modifier.coachTarget("advice.rules")) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = cs.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Как считаются прогноз и советы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showRules = !showRules }) {
                            Icon(if (showRules) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Показать правила")
                        }
                    }
                    if (showRules) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            (Insights.methods + AdviceEngine.rules).forEach { (title, text) ->
                                Column {
                                    Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
                                    Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = cs.outlineVariant)
                            Text(
                                "Прогноз и советы — подсказки по цифрам вашего дневника, а не указания тренера или врача.",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForecastPicker) {
        ForecastPickerDialog(
            series = allSeries,
            onDismiss = { showForecastPicker = false },
            onPick = { id, weeks ->
                viewModel.addForecast(id, weeks)
                showForecastPicker = false
            }
        )
    }

    openedRecord?.let { key ->
        records.find { it.key == key }?.let { record ->
            RecordDetailScreen(
                record = record,
                goal = goals.firstOrNull { it.recordKey == key },
                onDismiss = { openedRecord = null }
            )
        }
    }
}

/** Совет целиком: текст, данные, на которых он основан, и действия. */
@Composable
private fun AdviceCard(
    advice: Advice,
    hidden: Boolean,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = adviceLook(advice.kind)
    AppCard(modifier = modifier.alpha(if (hidden) 0.6f else 1f), onClick = if (advice.route != null) onOpen else null) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            IconBadge(icon = icon, tint = tint, size = 44.dp, iconSize = 22.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(advice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(advice.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text(
                    "Почему: ${advice.because}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = cs.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (advice.route != null) {
                        OutlinedButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                            Text(if (advice.recordKey != null) "Открыть рекорд" else "Открыть раздел")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (hidden) {
                        TextButton(onClick = onRestore) { Text("Вернуть на главную") }
                    } else {
                        TextButton(onClick = onHide) { Text("Скрыть с главной") }
                    }
                }
            }
        }
    }
}
