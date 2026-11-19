package com.example.diarytripeljump

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Где ставится след попытки на 3D-виде «Попадания на разбеге». */
class RunUpPlacementTest {

    @Test
    fun alongTrack_exactlyEnteredFeet() {
        assertEquals(0.13f, toeZ(0.0), 1e-6f)                 // попал — носок на бруске
        assertEquals(0.20f + 0.30f, toeZ(1.0), 1e-6f)         // заступ на стопу — за линией заступа
        assertEquals(-0.45f, toeZ(-1.5), 1e-6f)               // недоступ полторы стопы — до бруска
    }

    @Test
    fun acrossTrack_takeOffLegSideThenAlternating() {
        // поперёк не измеряется: сторона толчковой ноги, одинаковые отклонения — по очереди вправо и влево
        assertEquals(0.10f, lateralX("RIGHT", 0, 0f), 1e-6f)
        assertEquals(-0.10f, lateralX("LEFT", 0, 0f), 1e-6f)
        assertEquals(0f, lateralX(null, 0, 0f), 1e-6f)
        assertEquals(0.075f, lateralX(null, 1, 0f), 1e-6f)
        assertEquals(-0.075f, lateralX(null, 2, 0f), 1e-6f)
        assertEquals(0.15f, lateralX(null, 3, 0f), 1e-6f)
        // много одинаковых попыток не вылезают за брусок
        assertTrue(lateralX("RIGHT", 40, 0.5f) <= 0.61f - 0.06f + 1e-6f)
    }

    @Test
    fun heatColor_coldToHot() {
        val cold = heatColor(0f); val hot = heatColor(1f)
        assertTrue(cold.blue > cold.red)
        assertTrue(hot.red > hot.blue)
    }
}
