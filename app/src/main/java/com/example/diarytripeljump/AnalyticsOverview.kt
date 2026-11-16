package com.example.diarytripeljump

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.diarytripeljump.data.Advice
import com.example.diarytripeljump.data.Forecast
import com.example.diarytripeljump.data.RadarAxis
import com.example.diarytripeljump.data.RadarKey
import com.example.diarytripeljump.data.startOfDay
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.data.RunUpView
import com.example.diarytripeljump.data.SeasonDay
import com.example.diarytripeljump.data.TaperTimeline
import com.example.diarytripeljump.data.TaperView
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.data.formatTrainingDuration
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.theme.extraColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private fun dayLabel(millis: Long) = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

@Composable
private fun CardTitle(title: String, subtitle: String?) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LegendDot(color: Color, text: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape)
                .background(if (ring) Color.Transparent else color)
                .then(if (ring) Modifier.background(color.copy(alpha = 0.25f)) else Modifier)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================================================================= радар формы

/** Радар формы: контур «сейчас» поверх контура «месяц назад»; нажатие на ось показывает её значение. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormRadarCard(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    /** Советы, относящиеся к осям: ось отмечается красной точкой. */
    warnings: Map<String, Advice> = emptyMap(),
    /** «на 12 ноября», если в обзоре выбран день; null — сегодня. */
    asOfLabel: String? = null,
    onShowChart: (String) -> Unit = {},
    onJump: (String) -> Unit = {},
    onOpenAdvice: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val n = axes.size
    val known = axes.count { it.now != null }

    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Радар формы", (asOfLabel?.let { "Форма $it: " } ?: "") + "лучшее за 4 недели на шкале вашей истории")
            if (known == 0) {
                Text("Пока мало данных: запишите бег, присед, прыжковые упражнения и тройной.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                return@Column
            }
            val labelStyle = TextStyle(fontSize = 11.sp, color = cs.onSurfaceVariant)
            Canvas(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.15f).pointerInput(axes) {
                    detectTapGestures { p ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val ang = Math.atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())
                        // ближайшая ось по углу (первая ось смотрит вверх)
                        picked = (0 until n).minByOrNull { i ->
                            val a = -PI / 2 + 2 * PI * i / n
                            abs(Math.atan2(sin(ang - a), cos(ang - a)))
                        }
                    }
                }
            ) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f - 30.dp.toPx()
                fun pt(i: Int, v: Double): Offset {
                    val a = -PI / 2 + 2 * PI * i / n
                    return Offset(c.x + (r * v * cos(a)).toFloat(), c.y + (r * v * sin(a)).toFloat())
                }
                // сетка: три кольца и оси
                listOf(1 / 3.0, 2 / 3.0, 1.0).forEach { k ->
                    val ring = Path()
                    (0..n).forEach { i -> val q = pt(i % n, k); if (i == 0) ring.moveTo(q.x, q.y) else ring.lineTo(q.x, q.y) }
                    drawPath(ring, cs.outlineVariant, style = Stroke(1.dp.toPx()))
                }
                (0 until n).forEach { i -> drawLine(cs.outlineVariant, c, pt(i, 1.0), strokeWidth = 1.dp.toPx()) }
                fun shape(values: List<Double?>, scale: Float): Path = Path().apply {
                    values.forEachIndexed { i, v -> val q = pt(i, (v ?: 0.0) * scale); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
                    close()
                }
                // месяц назад — пунктир, сейчас — заливка
                if (axes.any { it.before != null }) {
                    drawPath(shape(axes.map { it.before }, grow.value), cs.onSurfaceVariant,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                }
                val now = shape(axes.map { it.now }, grow.value)
                drawPath(now, cs.primary.copy(alpha = 0.22f))
                drawPath(now, cs.primary, style = Stroke(2.5.dp.toPx()))
                axes.forEachIndexed { i, a ->
                    a.now?.let { v -> drawCircle(cs.primary, radius = if (picked == i) 6.dp.toPx() else 3.5.dp.toPx(), center = pt(i, v * grow.value)) }
                    // подпись оси снаружи кольца
                    val text = measurer.measure(a.title, labelStyle.copy(
                        color = if (a.now == null) cs.outline else if (picked == i) cs.primary else cs.onSurfaceVariant,
                        fontWeight = if (picked == i) FontWeight.Bold else FontWeight.Normal
                    ))
                    val q = pt(i, 1.0)
                    val dx = q.x - c.x
                    val x = when {
                        dx > 4 -> q.x + 6.dp.toPx()
                        dx < -4 -> q.x - text.size.width - 6.dp.toPx()
                        else -> q.x - text.size.width / 2f
                    }
                    val y = when {
                        q.y < c.y - r * 0.9f -> q.y - text.size.height - 4.dp.toPx()
                        q.y > c.y + r * 0.9f -> q.y + 4.dp.toPx()
                        else -> q.y - text.size.height / 2f
                    }
                    val tl = Offset(x.coerceIn(0f, size.width - text.size.width), y.coerceIn(0f, size.height - text.size.height))
                    drawText(text, topLeft = tl)
                    // совет по этой оси — красная точка у подписи
                    if (a.key in warnings) drawCircle(cs.error, radius = 3.5.dp.toPx(), center = Offset(tl.x + text.size.width + 5.dp.toPx(), tl.y + 4.dp.toPx()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(cs.primary, "сейчас")
                LegendDot(cs.onSurfaceVariant, "месяц назад")
            }
            val sel = picked?.let { axes.getOrNull(it) }
            if (sel != null) {
                Text(
                    "${sel.title}: " + (sel.detail ?: "нет данных за 4 недели") +
                        (if (sel.now != null && sel.before != null) changeWord(sel.now - sel.before) else ""),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                )
                warnings[sel.key]?.let { w ->
                    Text("Совет: ${w.title}", style = MaterialTheme.typography.bodyMedium, color = cs.error)
                }
                // переходы: ось связана с графиком, мишенью, картой сезона и прогнозом
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sel.seriesId?.let { id -> ToggleChip(label = "На графике", selected = false, onClick = { onShowChart(id) }) }
                    when (sel.key) {
                        RadarKey.RUNUP -> ToggleChip(label = "Мишень разбега", selected = false, onClick = { onJump(RadarKey.RUNUP) })
                        RadarKey.VOLUME -> ToggleChip(label = "Карта сезона", selected = false, onClick = { onJump(RadarKey.VOLUME) })
                        RadarKey.TRIPLE, RadarKey.STABILITY -> ToggleChip(label = "Прогноз", selected = false, onClick = onOpenAdvice)
                    }
                    if (sel.key in warnings && sel.key != RadarKey.TRIPLE && sel.key != RadarKey.STABILITY) {
                        ToggleChip(label = "К совету", selected = false, onClick = onOpenAdvice)
                    }
                }
            } else {
                val diffs = axes.mapNotNull { a -> if (a.now != null && a.before != null) a.title to a.now - a.before else null }
                val up = diffs.filter { it.second > 0.05 }.sortedByDescending { it.second }.map { it.first }
                val down = diffs.filter { it.second < -0.05 }.sortedBy { it.second }.map { it.first }
                Text(
                    listOfNotNull(
                        up.takeIf { it.isNotEmpty() }?.let { "Выросло: ${it.joinToString().lowercase()}" },
                        down.takeIf { it.isNotEmpty() }?.let { "Просело: ${it.joinToString().lowercase()}" }
                    ).joinToString(". ").ifEmpty { "За месяц форма без заметных изменений" } +
                        (if (warnings.isNotEmpty()) ". Красная точка — есть совет" else ""),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
    }
}

private fun changeWord(d: Double) = when {
    d > 0.05 -> " · лучше, чем месяц назад"
    d < -0.05 -> " · хуже, чем месяц назад"
    else -> " · как месяц назад"
}

// ======================================================================= мишень разбега

/** Мишень разбега: брусок посередине, заступы справа, недоступы слева, стрелка — куда перенести отметку. */
@Composable
fun RunUpTargetCard(view: RunUpView?, modifier: Modifier = Modifier, focusDay: Long? = null) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val dayShots = view?.shots?.count { focusDay != null && startOfDay(it.date) == focusDay } ?: 0
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Мишень разбега", view?.let {
                "${it.shots.size} ${pluralRu(it.shots.size, "попытка", "попытки", "попыток")} с отметкой за 2 месяца" +
                    (focusDay?.let { d -> " до ${dayLabel(d)}" + if (dayShots > 0) ", в этот день — $dayShots" else "" } ?: "")
            })
            if (view == null) {
                Text("Отмечайте в попытках с разбега, попали ли в брусок, — здесь появится мишень и совет по отметке.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                return@Column
            }
            val gold = MaterialTheme.extraColors.gold
            val small = TextStyle(fontSize = 11.sp, color = cs.onSurfaceVariant)
            Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                val w = size.width
                val h = size.height
                val span = max(2.0, view.shots.maxOf { abs(it.feet) } + 0.5)
                fun x(f: Double) = (w / 2 + f / span * (w / 2 - 12.dp.toPx())).toFloat()
                val laneTop = 18.dp.toPx()
                val laneBottom = h - 22.dp.toPx()
                // дорожка и зона отталкивания
                drawRoundRect(cs.surfaceContainerHighest, topLeft = Offset(0f, laneTop), size = Size(w, laneBottom - laneTop), cornerRadius = CornerRadius(10.dp.toPx()))
                // деления через стопу
                var f = -Math.floor(span)
                while (f <= span) {
                    drawLine(cs.outlineVariant, Offset(x(f), laneBottom - 6.dp.toPx()), Offset(x(f), laneBottom), strokeWidth = 1.dp.toPx())
                    f += 1.0
                }
                // брусок
                drawRect(gold, topLeft = Offset(x(0.0) - 3.dp.toPx(), laneTop), size = Size(6.dp.toPx(), laneBottom - laneTop))
                // попытки: точки разнесены по высоте, чтобы не сливались
                view.shots.forEachIndexed { i, s ->
                    val y = laneTop + (laneBottom - laneTop) * (0.18f + 0.64f * ((i * 37 % 100) / 100f))
                    val color = when {
                        s.feet == 0.0 -> cs.primary
                        s.feet > 0 -> cs.error
                        else -> cs.tertiary
                    }
                    val cx = x(0.0) + (x(s.feet) - x(0.0)) * appear.value
                    if (s.competition) drawCircle(color, radius = 6.dp.toPx(), center = Offset(cx, y), style = Stroke(2.dp.toPx()))
                    else drawCircle(color, radius = 5.dp.toPx(), center = Offset(cx, y))
                    // попытки выбранного в обзоре дня
                    if (focusDay != null && startOfDay(s.date) == focusDay) drawCircle(cs.onSurface, radius = 9.dp.toPx(), center = Offset(cx, y), style = Stroke(2.dp.toPx()))
                }
                // типичное отклонение и куда перенести отметку
                val mx = x(view.median)
                drawLine(cs.onSurface, Offset(mx, laneTop - 4.dp.toPx()), Offset(mx, laneBottom + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                val lbl = measurer.measure("недоступ", small)
                drawText(lbl, topLeft = Offset(4.dp.toPx(), h - lbl.size.height))
                val lbr = measurer.measure("заступ", small)
                drawText(lbr, topLeft = Offset(w - lbr.size.width - 4.dp.toPx(), h - lbr.size.height))
                val lbb = measurer.measure("брусок", small.copy(color = gold))
                drawText(lbb, topLeft = Offset(x(0.0) - lbb.size.width / 2f, h - lbb.size.height))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(cs.primary, "точно ${view.hits}")
                LegendDot(cs.error, "заступ ${view.fouls}")
                LegendDot(cs.tertiary, "недоступ ${view.shorts}")
            }
            Text(
                when {
                    view.move > 0 -> "Обычно заступ на ${formatNumber(abs(view.median))} ст. Отнесите отметку примерно на ${formatNumber(view.move)} ст."
                    view.move < 0 -> "Обычно недоступ ${formatNumber(abs(view.median))} ст. Поднесите отметку примерно на ${formatNumber(-view.move)} ст."
                    view.shots.size < 4 -> "Попыток пока мало для совета по отметке."
                    else -> "Облако попыток вокруг бруска — отметку можно не трогать."
                },
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
            )
            Text("Кружок — попытка на старте, пунктир — типичное отклонение.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

// ======================================================================= карта сезона

/** Тепловая карта: неделя — столбец, день — клетка; цвет — нагрузка, рамка — старт, точка — рекорд. */
@Composable
fun SeasonMapCard(
    weeks: List<List<SeasonDay>>,
    modifier: Modifier = Modifier,
    /** Выбранный в обзоре день: он же выбран на подводке, мишени и радаре. */
    focusDay: Long? = null,
    onPick: (Long?) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val gold = MaterialTheme.extraColors.gold
    val measurer = rememberTextMeasurer()
    val days = weeks.flatten()
    val picked = focusDay?.let { f -> days.firstOrNull { it.day == f } }
    val trained = days.count { it.level > 0 }
    val starts = days.count { it.start != null && it.level >= 0 }
    val records = days.count { it.record }
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Карта сезона", "Полгода: $trained ${pluralRu(trained, "день", "дня", "дней")} с тренировками, " +
                "$starts ${pluralRu(starts, "старт", "старта", "стартов")}, $records ${pluralRu(records, "рекорд", "рекорда", "рекордов")}")
            val small = TextStyle(fontSize = 10.sp, color = cs.onSurfaceVariant)
            val dayNames = listOf("пн", "", "ср", "", "пт", "", "вс")
            Canvas(
                modifier = Modifier.fillMaxWidth().height(150.dp).pointerInput(weeks, focusDay) {
                    detectTapGestures { p ->
                        val left = 22.dp.toPx(); val top = 16.dp.toPx()
                        val cell = minOf((size.width - left) / weeks.size, (size.height - top) / 7f)
                        val col = ((p.x - left) / cell).toInt(); val row = ((p.y - top) / cell).toInt()
                        val d = weeks.getOrNull(col)?.getOrNull(row)?.takeIf { it.level >= 0 }
                        // повторное нажатие на тот же день снимает выбор
                        onPick(if (d == null || d.day == focusDay) null else d.day)
                    }
                }
            ) {
                val left = 22.dp.toPx(); val top = 16.dp.toPx()
                val cell = minOf((size.width - left) / weeks.size, (size.height - top) / 7f)
                val gap = cell * 0.14f
                dayNames.forEachIndexed { r, t ->
                    if (t.isNotEmpty()) {
                        val m = measurer.measure(t, small)
                        drawText(m, topLeft = Offset(0f, top + r * cell + (cell - m.size.height) / 2))
                    }
                }
                var lastMonth = -1
                weeks.forEachIndexed { c, week ->
                    val month = Calendar.getInstance().apply { timeInMillis = week.first().day }[Calendar.MONTH]
                    if (month != lastMonth) {
                        lastMonth = month
                        val name = SimpleDateFormat("LLL", Locale("ru")).format(Date(week.first().day)).take(3)
                        drawText(measurer.measure(name, small), topLeft = Offset(left + c * cell, 0f))
                    }
                    week.forEachIndexed { r, d ->
                        val tl = Offset(left + c * cell + gap / 2, top + r * cell + gap / 2)
                        val sz = Size(cell - gap, cell - gap)
                        val color = when (d.level) {
                            -1 -> Color.Transparent
                            0 -> cs.surfaceContainerHighest
                            else -> cs.primary.copy(alpha = 0.25f + 0.1875f * d.level)
                        }
                        drawRoundRect(color, topLeft = tl, size = sz, cornerRadius = CornerRadius(cell * 0.22f))
                        if (d.skipped) drawLine(cs.outline, tl, Offset(tl.x + sz.width, tl.y + sz.height), strokeWidth = 1.dp.toPx())
                        if (d.start != null && d.level >= 0) drawRoundRect(gold, topLeft = tl, size = sz, cornerRadius = CornerRadius(cell * 0.22f), style = Stroke(1.6.dp.toPx()))
                        if (d.record) drawCircle(gold, radius = cell * 0.16f, center = Offset(tl.x + sz.width / 2, tl.y + sz.height / 2))
                        // жалоба в заметках — по модели разбора заметок
                        if (d.complaint) drawCircle(cs.error, radius = cell * 0.13f, center = Offset(tl.x + sz.width - cell * 0.13f, tl.y + cell * 0.13f))
                        if (picked?.day == d.day) drawRoundRect(cs.onSurface, topLeft = tl, size = sz, cornerRadius = CornerRadius(cell * 0.22f), style = Stroke(1.6.dp.toPx()))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("меньше", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                (1..4).forEach { l -> Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.25f + 0.1875f * l))) }
                Text("больше", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                LegendDot(gold, "старт · рекорд")
                LegendDot(cs.error, "жалоба")
            }
            picked?.let { d ->
                Text(
                    dayLabel(d.day) + ": " + listOfNotNull(
                        d.start?.let { "старт «${it.name}»" },
                        if (d.minutes > 0) formatTrainingDuration(d.minutes) else null,
                        if (d.volume > 0) "${d.volume} повторений" else null,
                        if (d.record) "новый рекорд" else null,
                        if (d.complaint) "жалоба в заметках" else null,
                        if (d.skipped) "пропуск" else null
                    ).ifEmpty { listOf("без тренировки") }.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                )
            }
        }
    }
}

// ======================================================================= подводка

/** Подводка к старту: обратный отсчёт, объём по дням и коридор рекомендуемого снижения. */
@Composable
fun TaperCard(
    view: TaperView,
    forecast: Forecast?,
    modifier: Modifier = Modifier,
    focusDay: Long? = null,
    onPick: (Long?) -> Unit = {},
    onOpenForecast: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val good = MaterialTheme.extraColors.gold
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle("Подводка: ${view.start.name}", dayLabel(view.start.date) + (forecast?.let { " · прогноз ${it.format(it.value)}" } ?: ""))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (view.daysLeft == 0) "сегодня" else "${view.daysLeft}",
                        style = if (view.daysLeft == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = cs.primary)
                    if (view.daysLeft > 0) Text(pluralRu(view.daysLeft, "день", "дня", "дней"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            val small = TextStyle(fontSize = 10.sp, color = cs.onSurfaceVariant)
            Canvas(modifier = Modifier.fillMaxWidth().height(130.dp).pointerInput(view, focusDay) {
                // нажатие на прошедший день выбирает его во всём обзоре
                detectTapGestures { p ->
                    val i = (p.x / (size.width / view.days.size)).toInt().coerceIn(0, view.days.lastIndex)
                    val d = view.days[i]
                    onPick(if (d.future || d.day == focusDay) null else d.day)
                }
            }) {
                val w = size.width
                val h = size.height - 16.dp.toPx()
                val n = view.days.size
                val slot = w / n
                val maxV = max(view.days.maxOf { it.volume }.toDouble(), view.baselineDaily * 1.2).coerceAtLeast(1.0)
                fun y(v: Double) = (h - v / maxV * h * 0.92).toFloat()
                if (view.baselineDaily > 0) {
                    // коридор подводки: 40–60 % обычного объёма
                    drawRect(good.copy(alpha = 0.16f), topLeft = Offset(0f, y(view.baselineDaily * TaperTimeline.HIGH)),
                        size = Size(slot * (n - 1), y(view.baselineDaily * TaperTimeline.LOW) - y(view.baselineDaily * TaperTimeline.HIGH)))
                    drawLine(cs.onSurfaceVariant, Offset(0f, y(view.baselineDaily)), Offset(w, y(view.baselineDaily)), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                }
                view.days.forEachIndexed { i, d ->
                    val left = i * slot + slot * 0.18f
                    val bw = slot * 0.64f
                    if (i == n - 1) {
                        drawCircle(cs.primary, radius = bw * 0.45f, center = Offset(left + bw / 2, h - bw * 0.5f))
                    } else if (d.future) {
                        drawRoundRect(cs.outlineVariant, topLeft = Offset(left, h - 6.dp.toPx()), size = Size(bw, 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
                    } else if (d.volume > 0) {
                        val top = y(d.volume * grow.value.toDouble())
                        val inZone = view.baselineDaily > 0 && d.volume <= view.baselineDaily * TaperTimeline.HIGH
                        drawRoundRect(if (inZone) good else cs.primary, topLeft = Offset(left, top), size = Size(bw, h - top), cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                    if (d.day == focusDay) {
                        drawRoundRect(cs.onSurface, topLeft = Offset(left - 2.dp.toPx(), 0f), size = Size(bw + 4.dp.toPx(), h),
                            cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                }
                val l1 = measurer.measure("−14", small); drawText(l1, topLeft = Offset(0f, size.height - l1.size.height))
                val l2 = measurer.measure("−7", small); drawText(l2, topLeft = Offset(slot * 7, size.height - l2.size.height))
                val l3 = measurer.measure("старт", small); drawText(l3, topLeft = Offset(w - l3.size.width, size.height - l3.size.height))
            }
            val share = view.lastWeekShare
            Text(
                when {
                    view.daysLeft == 0 -> "Старт сегодня: разминка как обычно, без новых упражнений."
                    view.baselineDaily <= 0 -> "Записывайте подходы и повторения — покажу, снижается ли объём перед стартом."
                    share == null -> "Объём за последнюю неделю сравнить пока не с чем."
                    share > TaperTimeline.HIGH + 0.15 -> "Объём за неделю — ${Math.round(share * 100)} % от обычного. Перед стартом его обычно снижают до 40–60 %, сохраняя скорость."
                    share < TaperTimeline.LOW - 0.15 -> "Объём за неделю — ${Math.round(share * 100)} % от обычного: нагрузку снизили сильно, держите скоростную работу."
                    else -> "Объём за неделю — ${Math.round(share * 100)} % от обычного: подводка идёт по плану."
                },
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
            )
            Text("Пунктир — обычный объём, полоса — коридор 40–60 % для подводки.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            if (forecast != null) ToggleChip(label = "Прогноз на старт", selected = false, onClick = onOpenForecast)
        }
    }
}
