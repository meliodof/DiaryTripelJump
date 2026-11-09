package com.example.diarytripeljump.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.TourStep
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Подсказки поверх интерфейса: затемнение, вырез вокруг элемента, стрелка и карточка с текстом.
 * Элементы отмечаются [coachTarget]; их положение хранится в обычной карте, а не в состоянии,
 * поэтому прокрутка не вызывает лишней работы, пока подсказки закрыты.
 */
class CoachController {
    internal val targets = HashMap<String, Rect>()
    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    /** true — перед каждым шагом нужно открыть экран его раздела (обучение и подсказки из настроек). */
    var navigates by mutableStateOf(false)
        private set
    private var onStep: (Int) -> Unit = {}
    private var onFinish: () -> Unit = {}

    val current: TourStep? get() = steps.getOrNull(index)

    fun show(list: List<TourStep>, startAt: Int = 0, navigates: Boolean = false, onStep: (Int) -> Unit = {}, onFinish: () -> Unit = {}) {
        if (list.isEmpty()) return
        this.onStep = onStep
        this.onFinish = onFinish
        this.navigates = navigates
        index = startAt.coerceIn(0, list.lastIndex)
        steps = list
    }

    fun next() {
        if (index < steps.lastIndex) {
            index++
            onStep(index)
        } else finish()
    }

    fun back() {
        if (index > 0) {
            index--
            onStep(index)
        }
    }

    fun finish() {
        steps = emptyList()
        index = 0
        onFinish()
    }
}

val LocalCoach = staticCompositionLocalOf { CoachController() }

/** Отмечает элемент, который подсказка может подсветить. */
fun Modifier.coachTarget(id: String): Modifier = composed {
    val coach = LocalCoach.current
    DisposableEffect(id) { onDispose { coach.targets.remove(id) } }
    // координаты экрана: элемент может быть в окне поверх экрана, а подсказка — в другом окне
    onGloballyPositioned { coach.targets[id] = Rect(it.positionOnScreen(), it.size.toSize()) }
}

/** Слой подсказки поверх всего экрана. Нажатия проходят только внутрь подсвеченного выреза. */
@Composable
fun CoachOverlay(coach: CoachController) {
    val tour = coach.current ?: return
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var target by remember { mutableStateOf<Rect?>(null) }
    var card by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    // положение элемента читается каждый кадр, пока открыта подсказка: экран мог прокрутиться или смениться
    LaunchedEffect(tour) {
        target = null
        val next = coach.steps.getOrNull(coach.index + 1)?.step?.target
        // окно уже было открыто (вернулись «Назад») — ждём, пока его закроют и откроют снова
        var wasOpen = next != null && coach.targets.containsKey(next)
        while (true) {
            withFrameNanos { }
            target = tour.step.target?.let { coach.targets[it] }?.translate(-origin)
            // шаг «нажмите»: как только открылось окно следующего шага, обучение переходит к нему
            val open = next != null && coach.targets.containsKey(next)
            if (!open) wasOpen = false
            if (tour.step.tap && open && !wasOpen) {
                coach.next()
                break
            }
        }
    }
    val pulse by rememberInfiniteTransition(label = "coachPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "coachPulseValue"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val padPx = with(density) { 8.dp.toPx() }
        val gapPx = with(density) { 36.dp.toPx() }
        val hole = target
            ?.let { Rect(it.left - padPx, it.top - padPx, it.right + padPx, it.bottom + padPx) }
            ?.takeIf { it.bottom > 0 && it.top < height }
        val cardBelow = hole == null || hole.center.y < height * 0.5f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                hole?.let { addRoundRect(RoundRect(it, CornerRadius(16.dp.toPx()))) }
            }
            drawPath(path, Color.Black.copy(alpha = 0.72f))
            hole?.let {
                val grow = 4.dp.toPx() * pulse
                drawRoundRect(
                    color = cs.primary,
                    topLeft = Offset(it.left - grow, it.top - grow),
                    size = androidx.compose.ui.geometry.Size(it.width + 2 * grow, it.height + 2 * grow),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            // стрелка от карточки к подсвеченному элементу
            val c = card
            if (hole != null && c != null) {
                val start = if (cardBelow) Offset(c.center.x, c.top) else Offset(c.center.x, c.bottom)
                val end = if (cardBelow) Offset(hole.center.x, hole.bottom + 6.dp.toPx()) else Offset(hole.center.x, hole.top - 6.dp.toPx())
                drawLine(Color.White, start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                val angle = atan2(end.y - start.y, end.x - start.x)
                val head = 14.dp.toPx()
                val spread = 0.5f
                val left = Offset(end.x - head * cos(angle - spread), end.y - head * sin(angle - spread))
                val right = Offset(end.x - head * cos(angle + spread), end.y - head * sin(angle + spread))
                drawPath(Path().apply { moveTo(end.x, end.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, Color.White)
            }
        }

        // вне выреза нажатия не проходят к интерфейсу, внутри — проходят
        val blockers = if (hole == null) listOf(Rect(0f, 0f, width, height)) else listOf(
            Rect(0f, 0f, width, hole.top.coerceAtLeast(0f)),
            Rect(0f, hole.bottom, width, height),
            Rect(0f, hole.top, hole.left.coerceAtLeast(0f), hole.bottom),
            Rect(hole.right, hole.top, width, hole.bottom)
        )
        blockers.filter { it.width > 0 && it.height > 0 }.forEach { r ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                    .pointerInput(Unit) { detectTapGestures { } }
            )
        }

        val cardModifier = when {
            hole == null -> Modifier.align(Alignment.Center)
            cardBelow -> Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (hole.bottom + gapPx).roundToInt()) }
            else -> Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -(height - hole.top + gapPx).roundToInt()) }
        }
        Surface(
            modifier = cardModifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .onGloballyPositioned { card = it.boundsInRoot() },
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tour.topic.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.weight(1f))
                    if (coach.steps.size > 1) {
                        Text("${coach.index + 1} из ${coach.steps.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
                Text(tour.step.title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                Text(tour.step.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (tour.step.tap && hole != null) {
                    Text("Нажмите на подсвеченную кнопку или «Далее»", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (coach.index < coach.steps.lastIndex) {
                        TextButton(onClick = { coach.finish() }) { Text("Пропустить") }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (coach.index > 0) TextButton(onClick = { coach.back() }) { Text("Назад") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { coach.next() }) { Text(if (coach.index < coach.steps.lastIndex) "Далее" else "Готово") }
                }
            }
        }
    }
}

/** Открыть настройки: даёт главный экран, а кнопка шестерёнки берёт его на любом экране. */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Подсказки там, где они нужны: поверх экрана или, для открытого окна, в собственном окне над ним —
 * иначе окно закрыло бы подсветку.
 */
@Composable
fun CoachLayer(coach: CoachController) {
    val tour = coach.current ?: return
    if (tour.topic.dialog) {
        Dialog(
            onDismissRequest = { coach.finish() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) { CoachOverlay(coach) }
    } else {
        CoachOverlay(coach)
    }
}
