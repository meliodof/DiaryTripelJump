package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** Демо-данные устроены так, что модели находят в них заложенное: тренд тройного и связь приседа с прыжком. */
class DemoSignalsTest {

    private val day = 86_400_000L
    private val week = 7 * day
    // понедельник: присед в понедельник, бег 30 м в четверг, тройной в субботу — как в плане недели демо
    private val firstMonday = 2_900L * week + 4 * day

    private fun record(name: String, unit: ResultUnit, points: List<RecordPoint>, lowerIsBetter: Boolean = false) = RecordSummary(
        key = "ex|$name", name = name, displayName = name, unit = unit, isSingleLeg = false, legSide = null,
        isAnti = false, isCompetition = false, category = "Силовые", isIndoor = false, weather = null,
        inventoryLabel = null, levelLabel = "PB", best = points.maxOf { it.value }, bestDate = points.last().date,
        lowerIsBetter = lowerIsBetter, deletableResultId = null, attempts = points.size, history = emptyList(), points = points
    )

    private val weeks = 0..DemoSignals.WEEKS
    private val squat = weeks.map { RecordPoint(firstMonday + it * week, DemoSignals.squat(it), false) }
    private val sprint = weeks.map { RecordPoint(firstMonday + it * week + 3 * day, DemoSignals.sprint30(it), false) }
    private val jump = weeks.map { RecordPoint(firstMonday + it * week + 5 * day, DemoSignals.jump(it), false) }

    @Test
    fun jump_growsOverSeason() {
        val now = jump.last().date + day
        val f = Insights.forecast("t", "Тройной", ResultUnit.M, false, jump, now + 21 * day, "к старту", now)!!
        assertTrue("наклон ${f.slopePerWeek}", f.slopePerWeek > 0)
        assertEquals(ForecastMethod.OWN_DATA, f.method)
    }

    @Test
    fun squat_isLinkedToJump_noiseIsNot() {
        val rnd = Random(1)
        val noise = weeks.map { RecordPoint(firstMonday + it * week + day, 60.0 + 0.3 * it + rnd.nextGaussian() * 2, false) }
        val found = Insights.influences(
            jump,
            listOf(
                record("Приседания со штангой", ResultUnit.KG, squat),
                record("Бег 30 м", ResultUnit.SECONDS, sprint, lowerIsBetter = true),
                record("Выпады со штангой", ResultUnit.KG, noise)
            )
        )
        val squatLink = found.firstOrNull { it.title == "Приседания со штангой" }
        assertNotNull("связь приседа не найдена: $found", squatLink)
        assertTrue(squatLink!!.r > 0.6)
        // присед в понедельник, а прыжок в субботу: по календарным неделям сдвиг 3–4 недели
        assertTrue(squatLink.lagWeeks in DemoSignals.SQUAT_LAG..DemoSignals.SQUAT_LAG + 1)
        assertTrue("лишняя связь: $found", found.none { it.title == "Выпады со штангой" })
    }
}
