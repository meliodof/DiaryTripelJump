package com.example.diarytripeljump

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.RunUpAttempt
import com.example.diarytripeljump.data.RunUpScope
import com.example.diarytripeljump.data.RunUpShot
import com.example.diarytripeljump.data.RunUpTarget
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.data.runUpShots
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.AppDatePickerDialog
import com.example.diarytripeljump.ui.components.ChartWindow
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.pinchZoom
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.theme.extraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

private fun dayLabel(millis: Long) = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

/** Точек вдоль шкалы на тепловой карте. */
private const val HEAT_SAMPLES = 480

/** Цвет тепловой карты, как в футболе: синий — редко, зелёный, жёлтый, красный — чаще всего. */
private fun heatColor(d: Float): Color {
    val stops = listOf(0f to Color(0xFF2E7DFF), 0.35f to Color(0xFF22C55E), 0.65f to Color(0xFFFACC15), 1f to Color(0xFFEF4444))
    val v = d.coerceIn(0f, 1f)
    val i = stops.indexOfLast { it.first <= v }.coerceIn(0, stops.size - 2)
    val (a, ca) = stops[i]
    val (b, cb) = stops[i + 1]
    return lerp(ca, cb, ((v - a) / (b - a)).coerceIn(0f, 1f))
}

/**
 * Раздел «Мишень разбега». Каждая попытка — точка на шкале в стопах: ровно столько, сколько введено в попытке
 * (0 — брусок, плюс — заступ, минус — недоступ). Одинаковые значения ставятся столбиком. Масштаб — двумя пальцами
 * или лупами, ползунок двигает приближённую шкалу; нажатие на точку показывает, что это за попытка.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunUpScreen(viewModel: DiaryViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gold = MaterialTheme.extraColors.gold
    val trainings by viewModel.allTrainingsWithExercises.collectAsState()
    val competitions by viewModel.allCompetitions.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val shots = remember(trainings, competitions) { runUpShots(trainings, competitions) }
    val sessions = remember(shots) { RunUpTarget.sessions(shots) }
    var scope by remember { mutableStateOf<RunUpScope>(RunUpScope.Last) }
    var choosingSession by remember { mutableStateOf(false) }
    var pickingFrom by remember { mutableStateOf(false) }
    var periodFrom by remember { mutableStateOf<Long?>(null) }
    var picked by remember(scope) { mutableStateOf<RunUpShot?>(null) }
    var heatMode by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<RunUpShot?>(null) }
    val view = remember(shots, scope) { RunUpTarget.build(shots, scope, now) }
    val window = remember(scope) { ChartWindow(maxZoom = 16f) }
    val appear = remember(scope) { Animatable(0f) }
    LaunchedEffect(scope) { appear.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val measurer = rememberTextMeasurer()

    val scopeLabel = when (val s = scope) {
        RunUpScope.Last -> sessions.firstOrNull()?.label ?: "последняя тренировка"
        is RunUpScope.Session -> sessions.firstOrNull { it.id == s.id }?.label ?: "выбранная тренировка"
        is RunUpScope.Days -> if (s.days <= 31) "за месяц" else "за 3 месяца"
        RunUpScope.All -> "за всё время"
        is RunUpScope.Period -> "${dayLabel(s.from)} — ${dayLabel(s.to)}"
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Мишень разбега", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpIconButton(onClick = rememberHelp(HelpTopic.F_RUNUP_TARGET), id = "runup.help")
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "scope") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToggleChip("Последняя", scope == RunUpScope.Last, { scope = RunUpScope.Last })
                    ToggleChip("Тренировка…", scope is RunUpScope.Session, { choosingSession = true })
                    ToggleChip("Месяц", scope == RunUpScope.Days(30), { scope = RunUpScope.Days(30) })
                    ToggleChip("3 месяца", scope == RunUpScope.Days(91), { scope = RunUpScope.Days(91) })
                    ToggleChip("Всё время", scope == RunUpScope.All, { scope = RunUpScope.All })
                    ToggleChip("Период…", scope is RunUpScope.Period, { pickingFrom = true })
                }
            }
            item(key = "target") {
                AppCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            scopeLabel + (view?.let { " · ${it.shots.size} ${pluralRu(it.shots.size, "попытка", "попытки", "попыток")}" } ?: ""),
                            style = MaterialTheme.typography.titleSmall, color = cs.onSurface
                        )
                        if (view == null) {
                            Text(
                                if (shots.isEmpty()) "Попыток с отметкой попадания в брусок пока нет." else "В этом охвате попыток с отметкой нет.",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                            )
                            return@Column
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ToggleChip("Точки", !heatMode, { heatMode = false })
                            ToggleChip("Тепловая карта", heatMode, { heatMode = true; picked = null })
                        }
                        val placed = remember(view) { RunUpTarget.stacks(view.shots) }
                        // шкала: от −span до +span стоп, не меньше ±2 стоп, кратно половине стопы
                        val span = remember(view) { max(2.0, ceil((view.shots.maxOf { abs(it.feet) } + 0.5) * 2) / 2) }
                        val heat = remember(view, span) { RunUpTarget.heat(view.shots, -span, span, HEAT_SAMPLES) }
                        val small = TextStyle(fontSize = 10.sp, color = cs.onSurfaceVariant)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .pinchZoom(window, inset = 12.dp, onZoom = { picked = null })
                                .pointerInput(placed, window.zoom, window.start, heatMode) {
                                    detectTapGestures(onDoubleTap = { window.reset(); picked = null }) { tap ->
                                        if (heatMode) return@detectTapGestures
                                        val inset = 12.dp.toPx()
                                        val w = size.width - 2 * inset
                                        val base = size.height - 30.dp.toPx()
                                        val r = 7.dp.toPx()
                                        val maxStack = (placed.maxOfOrNull { it.second } ?: 0) + 1
                                        val stepY = minOf(2 * r + 3.dp.toPx(), (base - 20.dp.toPx()) / maxStack)
                                        val hit = placed.minByOrNull { (s, k) ->
                                            val x = inset + window.screen(((s.feet + span) / (2 * span)).toFloat()) * w
                                            val y = base - r - k * stepY
                                            hypot(x - tap.x, y - tap.y)
                                        }
                                        picked = hit?.takeIf { (s, k) ->
                                            val x = inset + window.screen(((s.feet + span) / (2 * span)).toFloat()) * w
                                            hypot(x - tap.x, base - r - k * stepY - tap.y) < 3 * r
                                        }?.first
                                    }
                                }
                        ) {
                            val inset = 12.dp.toPx()
                            val w = size.width - 2 * inset
                            val base = size.height - 30.dp.toPx()
                            fun x(feet: Double) = inset + window.screen(((feet + span) / (2 * span)).toFloat()) * w
                            clipRect(left = 0f, right = size.width) {
                                // зоны: недоступ слева, заступ справа
                                drawRect(cs.tertiary.copy(alpha = 0.10f), topLeft = Offset(0f, 0f), size = Size((x(-0.33)).coerceIn(0f, size.width), base))
                                val foulLeft = x(0.33).coerceIn(0f, size.width)
                                drawRect(cs.error.copy(alpha = 0.10f), topLeft = Offset(foulLeft, 0f), size = Size(size.width - foulLeft, base))
                                // брусок: 0 стоп, ширина бруска ≈ 20 см ≈ 0,66 стопы
                                val bl = x(-0.33)
                                drawRect(gold.copy(alpha = if (heatMode) 0.35f else 0.85f), topLeft = Offset(bl, 0f), size = Size(x(0.33) - bl, base))
                                // тепловая карта: столбец на каждую точку шкалы, цвет и высота пятна — от плотности попыток
                                if (heatMode) {
                                    val mid = base / 2
                                    for (k in 0 until HEAT_SAMPLES - 1) {
                                        val d = heat[k].toFloat() * appear.value
                                        if (d < 0.03f) continue
                                        val x0 = x(-span + 2 * span * k / (HEAT_SAMPLES - 1))
                                        val x1 = x(-span + 2 * span * (k + 1) / (HEAT_SAMPLES - 1))
                                        if (x1 < 0f || x0 > size.width) continue
                                        val color = heatColor(d)
                                        val half = base * (0.18f + 0.32f * d)
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to color.copy(alpha = 0f), 0.5f to color.copy(alpha = 0.25f + 0.6f * d), 1f to color.copy(alpha = 0f),
                                                startY = mid - half, endY = mid + half
                                            ),
                                            topLeft = Offset(x0, mid - half), size = Size(x1 - x0 + 0.8f, 2 * half)
                                        )
                                    }
                                }
                                // деления: через стопу, при приближении — через полстопы
                                val step = if (window.zoom >= 2.5f) 0.5 else 1.0
                                var f = -span
                                while (f <= span + 1e-9) {
                                    val px = x(f)
                                    if (px in -1f..size.width + 1f) {
                                        drawLine(cs.outline, Offset(px, base), Offset(px, base + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
                                        val text = if (f == 0.0) "0" else (if (f > 0) "+" else "−") + formatNumber(abs(f))
                                        val t = measurer.measure(text, small)
                                        drawText(t, topLeft = Offset((px - t.size.width / 2).coerceIn(0f, size.width - t.size.width), base + 8.dp.toPx()))
                                    }
                                    f += step
                                }
                                drawLine(cs.outline, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
                                // медиана — типичное отклонение
                                val mx = x(view.median)
                                drawLine(cs.onSurface, Offset(mx, 0f), Offset(mx, base), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                                // точки: по горизонтали — введённые стопы, одинаковые значения — столбиком
                                val r = 7.dp.toPx()
                                val maxStack = (placed.maxOfOrNull { it.second } ?: 0) + 1
                                val stepY = minOf(2 * r + 3.dp.toPx(), (base - 20.dp.toPx()) / maxStack)
                                if (!heatMode) placed.forEach { (s, k) ->
                                    val c = Offset(x(s.feet), base - r - k * stepY * appear.value)
                                    val color = when {
                                        s.feet == 0.0 -> cs.primary
                                        s.feet > 0 -> cs.error
                                        else -> cs.tertiary
                                    }
                                    drawCircle(color, radius = r, center = c)
                                    if (s.competition) drawCircle(gold, radius = r + 2.5.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                                    if (s == picked) drawCircle(cs.onSurface, radius = r + 5.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                                }
                            }
                            val lb = measurer.measure("брусок", small.copy(color = gold, fontWeight = FontWeight.Bold))
                            val bx = x(0.0)
                            if (bx in 0f..size.width) drawText(lb, topLeft = Offset((bx - lb.size.width / 2).coerceIn(0f, size.width - lb.size.width), 2.dp.toPx()))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { window.zoomBy(0.5f, focus = 0.5f); picked = null }, enabled = window.isZoomed) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Отдалить")
                            }
                            Text(window.label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            IconButton(onClick = { window.zoomBy(2f, focus = 0.5f); picked = null }, enabled = window.zoom < 16f) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить")
                            }
                            if (window.isZoomed) {
                                Slider(value = window.slider, onValueChange = { window.slider = it; picked = null }, modifier = Modifier.weight(1f))
                            }
                        }
                        picked?.let { s ->
                            Text(
                                "${dayLabel(s.date)} · ${s.sourceLabel.substringBefore(',').removePrefix("Тренировка ").let { if (s.competition) it else "тренировка" }} · " +
                                    RunUp.describe(RunUpAttempt(boardFeet = s.feet)),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                            )
                            if (!s.note.isNullOrBlank()) {
                                Text("«${s.note}»", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                            if (s.ref != null) {
                                TextButton(onClick = { editingNote = s }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (s.note.isNullOrBlank()) "Комментарий к попытке" else "Изменить комментарий")
                                }
                            }
                        }
                        if (heatMode) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("реже", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                Box(
                                    modifier = Modifier.width(140.dp).height(10.dp).clip(CircleShape)
                                        .background(Brush.horizontalGradient((0..10).map { heatColor(it / 10f) }))
                                )
                                Text("чаще", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Legend(cs.primary, "точно ${view.hits}")
                                Legend(cs.error, "заступ ${view.fouls}")
                                Legend(cs.tertiary, "недоступ ${view.shorts}")
                            }
                        }
                        Text(
                            when {
                                view.move > 0 -> "Обычно заступ на ${formatNumber(abs(view.median))} ст. Отнесите отметку примерно на ${formatNumber(view.move)} ст."
                                view.move < 0 -> "Обычно недоступ ${formatNumber(abs(view.median))} ст. Поднесите отметку примерно на ${formatNumber(-view.move)} ст."
                                view.shots.size < RunUpTarget.MIN_FOR_ADVICE -> "Попыток мало для совета по отметке."
                                else -> "Попытки вокруг бруска — отметку можно не трогать."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                        )
                    }
                }
            }
        }
    }

    if (choosingSession) {
        AlertDialog(
            onDismissRequest = { choosingSession = false },
            title = { Text("Тренировка или старт") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.id }) { s ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { scope = RunUpScope.Session(s.id); choosingSession = false }.padding(vertical = 10.dp)) {
                            Text(s.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (s.competition) FontWeight.Medium else FontWeight.Normal)
                            Text("${s.shots} ${pluralRu(s.shots, "попытка", "попытки", "попыток")} с отметкой", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingSession = false }) { Text("Закрыть") } }
        )
    }
    editingNote?.let { s ->
        var text by remember(s) { mutableStateOf(s.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Комментарий к попытке") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${dayLabel(s.date)} · ${RunUp.describe(RunUpAttempt(boardFeet = s.feet))}",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(300) },
                        placeholder = { Text("Например: сбился на последних шагах") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    s.ref?.let { viewModel.saveAttemptNote(it, text.trim()) }
                    picked = s.copy(note = text.trim().ifEmpty { null })
                    editingNote = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Отмена") } }
        )
    }
    if (pickingFrom) {
        AppDatePickerDialog(initialMillis = now - 30 * 86_400_000L, onDismiss = { pickingFrom = false }) { from ->
            pickingFrom = false
            periodFrom = from
        }
    }
    periodFrom?.let { from ->
        AppDatePickerDialog(initialMillis = now, onDismiss = { periodFrom = null }) { to ->
            scope = RunUpScope.Period(minOf(from, to), maxOf(from, to))
            periodFrom = null
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
