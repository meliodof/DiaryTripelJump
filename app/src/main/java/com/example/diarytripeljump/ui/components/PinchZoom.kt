package com.example.diarytripeljump.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Видимая часть графика по горизонтали: доли от 0 до 1 всей длины графика.
 * Приближение растягивает окно, сдвиг двигает его; точка под пальцами при этом остаётся на месте.
 */
@Stable
class ChartWindow(private val maxZoom: Float = 64f) {
    var zoom by mutableFloatStateOf(1f)
        private set
    var start by mutableFloatStateOf(0f)
        private set

    /** Какая доля графика видна. */
    val visible: Float get() = 1f / zoom

    val isZoomed: Boolean get() = zoom > 1.001f

    /** Положение точки на экране (0 — левый край окна, 1 — правый) по её положению на всём графике. */
    fun screen(position: Float): Float = (position - start) / visible

    /**
     * Приближение в [factor] раз вокруг [focus] (доля ширины окна) со сдвигом на [pan] ширины окна.
     * Пальцы раздвигают — график растёт, ведут вправо — график едет вправо.
     */
    fun zoomBy(factor: Float, focus: Float, pan: Float = 0f) {
        val anchor = start + focus * visible
        zoom = (zoom * factor).coerceIn(1f, maxZoom)
        start = (anchor - focus * visible - pan * visible).coerceIn(0f, (1f - visible).coerceAtLeast(0f))
    }

    /** Ползунок под графиком: 0 — начало, 1 — конец. */
    var slider: Float
        get() = if (1f - visible < 1e-4f) 1f else start / (1f - visible)
        set(value) {
            start = value.coerceIn(0f, 1f) * (1f - visible).coerceAtLeast(0f)
        }

    fun reset() {
        zoom = 1f
        start = 0f
    }

    /** «×2,5» для подписи под графиком. */
    val label: String
        get() = "×" + ((zoom * 10).roundToInt() / 10f).toString().removeSuffix(".0").replace('.', ',')
}

/**
 * Масштаб двумя пальцами, как у фотографии. Жест перехватывается раньше остальных, поэтому
 * одним пальцем по графику по-прежнему смотрят значения, а вертикальным движением прокручивают экран.
 * [inset] — отступ области точек от краёв холста.
 */
fun Modifier.pinchZoom(window: ChartWindow, inset: Dp = 0.dp, onZoom: () -> Unit = {}): Modifier =
    pointerInput(window) {
        val insetPx = inset.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val width = (size.width - 2 * insetPx).coerceAtLeast(1f)
                    val focus = ((event.calculateCentroid(useCurrent = true).x - insetPx) / width).coerceIn(0f, 1f)
                    window.zoomBy(event.calculateZoom(), focus, event.calculatePan().x / width)
                    onZoom()
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
