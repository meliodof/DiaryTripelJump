package com.example.diarytripeljump

import com.example.diarytripeljump.data.ChartRange
import com.example.diarytripeljump.data.RecordInsights
import com.example.diarytripeljump.data.RecordPoint
import com.example.diarytripeljump.data.RecordSummary
import com.example.diarytripeljump.data.ResultUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RecordInsightsTest {
    private val day = 86_400_000L
    private fun date(year: Int, month: Int = 5, dayOfMonth: Int = 1) =
        Calendar.getInstance().apply { clear(); set(year, month, dayOfMonth) }.timeInMillis

    private fun point(value: Double, date: Long) = RecordPoint(date, value, false)

    private fun record(best: Double, lower: Boolean = false, anti: Boolean = false, edited: Boolean = false) = RecordSummary(
        key = "k", name = "Тройной прыжок", displayName = "Тройной прыжок", unit = ResultUnit.M, isSingleLeg = false, legSide = null,
        isAnti = anti, isCompetition = false, category = "Прыжковые", isIndoor = false, weather = null, inventoryLabel = null,
        levelLabel = "PB", best = best, bestDate = 0, lowerIsBetter = lower, deletableResultId = null, attempts = 1,
        history = emptyList(), isManuallyEdited = edited
    )

    @Test
    fun seasonBest_usesOnlyCurrentYear() {
        val points = listOf(point(15.0, date(2025)), point(14.0, date(2026)), point(14.5, date(2026, 6)))
        assertEquals(14.5, RecordInsights.seasonBest(points, false, date(2026, 8))!!, 1e-9)
        assertEquals(14.0, RecordInsights.seasonBest(points, true, date(2026, 8))!!, 1e-9)
        assertNull(RecordInsights.seasonBest(points, false, date(2027)))
    }

    @Test
    fun range_cutsOldPoints() {
        val now = date(2026, 8)
        val points = listOf(point(1.0, now - 400 * day), point(2.0, now - 100 * day), point(3.0, now - 10 * day))
        assertEquals(1, RecordInsights.inRange(points, ChartRange.QUARTER, now).size)
        assertEquals(2, RecordInsights.inRange(points, ChartRange.YEAR, now).size)
        assertEquals(3, RecordInsights.inRange(points, ChartRange.ALL, now).size)
    }

    @Test
    fun trend_needsSixResultsAndRespectsDirection() {
        val points = listOf(10.0, 10.0, 10.0, 11.0, 11.0, 11.0).mapIndexed { i, v -> point(v, i * day) }
        assertEquals(1.0, RecordInsights.trend(points, false)!!, 1e-9)
        assertEquals(-1.0, RecordInsights.trend(points, true)!!, 1e-9)
        assertNull(RecordInsights.trend(points.take(5), false))
    }

    @Test
    fun pace_splitsRemainderByWeeks() {
        val now = date(2026)
        assertEquals(4, RecordInsights.weeksLeft(now + 28 * day, now))
        assertEquals(0.05, RecordInsights.weeklyPace(15.0, 15.2, now + 28 * day, now)!!, 1e-9)
        assertNull(RecordInsights.weeklyPace(15.0, 15.2, null, now))
        assertNull(RecordInsights.weeksLeft(now - day, now))
        assertEquals("5 см", RecordInsights.formatPace(0.05, ResultUnit.M))
    }

    @Test
    fun mainJump_splitsCompetitionAndTraining() {
        val competition = record(14.11).copy(key = "c", isCompetition = true)
        val training = record(14.21).copy(key = "t")
        val withBar = record(15.0).copy(key = "b", inventoryName = "Утяжелитель")
        val (c, t) = RecordInsights.mainJumpSplit(listOf(competition, training, withBar))
        assertEquals("c", c?.key)
        assertEquals("t", t?.key)
    }

    @Test
    fun hiddenRecord_returnsOnlyAfterImprovement() {
        val hidden = mapOf("k" to 14.0)
        assertEquals(false, RecordInsights.isVisible(record(14.0), hidden))
        assertEquals(true, RecordInsights.isVisible(record(14.2), hidden))
        assertEquals(true, RecordInsights.isVisible(record(10.0), emptyMap()))
        assertEquals(true, RecordInsights.isVisible(record(10.7, lower = true), mapOf("k" to 10.8)))
    }

    @Test
    fun newRecord_onlyForRealImprovement() {
        assertNull(RecordInsights.newRecord(null, listOf(record(15.0))))
        assertNull(RecordInsights.newRecord(emptyMap(), listOf(record(15.0))))
        assertNull(RecordInsights.newRecord(mapOf("k" to 15.0), listOf(record(15.0))))
        assertNotNull(RecordInsights.newRecord(mapOf("k" to 14.0), listOf(record(15.0))))
        assertNotNull(RecordInsights.newRecord(mapOf("k" to 11.0), listOf(record(10.8, lower = true))))
        assertNull(RecordInsights.newRecord(mapOf("k" to 14.0), listOf(record(15.0, anti = true))))
        assertNull(RecordInsights.newRecord(mapOf("k" to 14.0), listOf(record(15.0, edited = true))))
    }
}
