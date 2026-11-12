package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

class InsightsTest {

    private val day = 86_400_000L
    private val week = 7 * day
    private val now = 3_000 * day

    private fun record(name: String, unit: ResultUnit, points: List<RecordPoint>, lowerIsBetter: Boolean = false) = RecordSummary(
        key = "ex|$name", name = name, displayName = name, unit = unit, isSingleLeg = false, legSide = null,
        isAnti = false, isCompetition = false, category = "Силовые", isIndoor = false, weather = null,
        inventoryLabel = null, levelLabel = "PB", best = points.maxOf { it.value }, bestDate = points.last().date,
        lowerIsBetter = lowerIsBetter, deletableResultId = null, attempts = points.size, history = emptyList(), points = points
    )

    @Test
    fun fit_exactLine() {
        val xs = (0..9).map { it.toDouble() }
        val f = Insights.fit(xs, xs.map { 10 + 0.5 * it })!!
        assertEquals(10.0, f.a, 1e-9)
        assertEquals(0.5, f.b, 1e-9)
        assertEquals(1.0, f.r2, 1e-9)
        assertEquals(0.0, f.margin(20.0), 1e-9)
    }

    @Test
    fun forecast_linearProgress_averagesLastAndLine() {
        // 12 тренировок раз в 5 дней, +5 см каждые 5 дней
        val start = now - 56 * day
        val points = (0 until 12).map { RecordPoint(start + it * 5 * day, 14.0 + it * 0.05, false) }
        val target = now + 14 * day
        val f = Insights.forecast("t", "Тройной", ResultUnit.M, false, points, target, "к старту", now)!!
        val line = 14.0 + 0.01 * ((target - start) / day.toDouble())
        val last = points.last().value
        assertEquals((line + last) / 2, f.value, 1e-6)
        assertEquals(ForecastMethod.OWN_DATA, f.method)
        assertEquals(0.07, f.slopePerWeek, 1e-9)
        assertEquals(Reliability.HIGH, f.reliability)
    }

    @Test
    fun coldStart_fewResults_lastResultOrTypicalGrowth() {
        val points = (0 until 4).map { RecordPoint(now - it * 10 * day, 14.0, false) }
        val plain = Insights.forecast("t", "Тройной", ResultUnit.M, false, points, now + week, "к старту", now)!!
        assertEquals(ForecastMethod.LAST_RESULT, plain.method)
        assertEquals(14.0, plain.value, 1e-9)
        assertEquals(Reliability.LOW, plain.reliability)

        val young = Insights.forecast("t", "Тройной", ResultUnit.M, false, points, now + 365 * day, "к старту", now, growthPerYear = 0.04)!!
        assertEquals(ForecastMethod.TYPICAL_GROWTH, young.method)
        assertEquals(14.0 * 1.04, young.value, 1e-9)
    }

    @Test
    fun coldStart_noResults_usesProfileRecord() {
        val f = Insights.forecast("t", "Тройной", ResultUnit.M, false, emptyList(), now + 365 * day, "к старту", now,
            growthPerYear = 0.015, fallbackLevel = 15.0)!!
        assertEquals(15.0 * 1.015, f.value, 1e-9)
        assertEquals(0, f.points)
        assertNull(Insights.forecast("t", "Тройной", ResultUnit.M, false, emptyList(), now + week, "к старту", now))
    }

    @Test
    fun coldStart_lowerIsBetter_improvesDown() {
        val f = Insights.forecast("t", "Бег 30 м", ResultUnit.SECONDS, true, listOf(RecordPoint(now, 4.0, false)),
            now + 365 * day, "через год", now, growthPerYear = 0.04)!!
        assertEquals(4.0 * 0.96, f.value, 1e-9)
    }

    @Test
    fun typicalGrowth_byAge() {
        assertEquals(0.04, Insights.typicalGrowthPerYear(16), 1e-9)
        assertEquals(0.015, Insights.typicalGrowthPerYear(20), 1e-9)
        assertEquals(0.0, Insights.typicalGrowthPerYear(27), 1e-9)
        assertEquals(0.015, Insights.typicalGrowthPerYear(null), 1e-9)
    }

    @Test
    fun forecast_goalReached() {
        val start = now - 56 * day
        val points = (0 until 12).map { RecordPoint(start + it * 5 * day, 100.0 + it * 2.5, false) }
        val f = Insights.forecast("g", "Присед", ResultUnit.KG, false, points, now + 14 * day, "к сроку", now, goal = 130.0)!!
        assertEquals(true, f.reachesGoal)
        val far = Insights.forecast("g", "Присед", ResultUnit.KG, false, points, now + 14 * day, "к сроку", now, goal = 200.0)!!
        assertEquals(false, far.reachesGoal)
    }

    @Test
    fun influences_findLaggedLink() {
        val pattern = listOf(0.0, 3.0, -2.0, 5.0, 1.0, -4.0, 2.0, 6.0, -3.0, 0.0, 4.0, -1.0, -5.0, 3.0, 2.0, -2.0, 1.0, 5.0, -3.0, 0.0)
        val base = 400L // номер недели
        fun date(w: Int) = (base + w) * week + day
        // присед растёт за сезон, а его колебания через 3 недели повторяются в тройном
        val squat = pattern.indices.map { RecordPoint(date(it), 100.0 + 2 * it + pattern[it], false) }
        val jump = (0 until pattern.size + 3).map { w ->
            RecordPoint(date(w), 14.0 + 0.01 * w + if (w >= 3) 0.05 * pattern[w - 3] else 0.0, false)
        }
        val found = Insights.influences(jump, listOf(record("Присед", ResultUnit.KG, squat)))
        assertEquals(1, found.size)
        assertEquals(3, found[0].lagWeeks)
        assertTrue(found[0].r > 0.9)
    }

    @Test
    fun influences_ignoreCommonTrendOnly() {
        // оба ряда просто растут за сезон — это не связь
        fun date(w: Int) = (400L + w) * week + day
        val squat = (0 until 20).map { RecordPoint(date(it), 100.0 + 2 * it, false) }
        val jump = (0 until 23).map { RecordPoint(date(it), 14.0 + 0.01 * it, false) }
        assertTrue(Insights.influences(jump, listOf(record("Присед", ResultUnit.KG, squat))).isEmpty())
    }

    @Test
    fun anomaly_suddenDrop() {
        val values = listOf(4.10, 4.12, 4.09, 4.11, 4.10, 4.13, 4.08, 4.11, 4.10)
        val points = values.mapIndexed { i, v -> RecordPoint(now - (40 - i * 4) * day, v, false) } +
            RecordPoint(now - day, 4.60, false)
        val advice = Insights.anomalies(listOf(record("Бег 30 м", ResultUnit.SECONDS, points, lowerIsBetter = true)), now)
        assertEquals(1, advice.size)
        assertEquals(AdviceKind.RISK, advice[0].kind)
    }

    @Test
    fun anomaly_improvementIsNotRisk() {
        val values = listOf(4.10, 4.12, 4.09, 4.11, 4.10, 4.13, 4.08, 4.11, 4.10)
        val points = values.mapIndexed { i, v -> RecordPoint(now - (40 - i * 4) * day, v, false) } +
            RecordPoint(now - day, 3.80, false)
        assertTrue(Insights.anomalies(listOf(record("Бег 30 м", ResultUnit.SECONDS, points, lowerIsBetter = true)), now).isEmpty())
    }

    @Test
    fun timeLoad_sharpGrowth() {
        fun t(daysAgo: Int, minutes: Int) = TrainingWithExercises(
            TrainingSession(date = now - daysAgo * day, durationMinutes = minutes), emptyList()
        )
        val trainings = listOf(t(25, 60), t(18, 60), t(11, 60), t(1, 90), t(3, 90), t(5, 90))
        val a = Insights.timeLoad(trainings, now)
        assertNotNull(a)
        assertEquals(AdviceKind.RISK, a!!.kind)
        // ровная нагрузка — без совета
        assertNull(Insights.timeLoad(listOf(t(25, 60), t(18, 60), t(11, 60), t(4, 60)), now))
    }
}
