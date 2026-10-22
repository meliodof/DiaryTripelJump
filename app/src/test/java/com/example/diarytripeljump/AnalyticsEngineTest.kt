package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

class AnalyticsEngineTest {

    private val day = 86_400_000L
    private val today = startOfDay(System.currentTimeMillis())
    private fun at(offset: Int) = AnalyticsEngine.addDays(today, offset) + 12 * 3_600_000L

    private fun training(offset: Int, id: Long, name: String, value: Double, unit: ResultUnit, intensity: Int = 7) =
        TrainingWithExercises(
            TrainingSession(id = id, date = at(offset), intensity = intensity, durationMinutes = 90),
            listOf(ExerciseResult(exerciseId = 1, exerciseName = name, trainingSessionId = id, date = at(offset), result = value, resultUnit = unit))
        )

    private fun squat(startOffset: Int, step: Int, values: List<Double>) =
        values.mapIndexed { i, v -> training(startOffset + i * step, 100L + startOffset + i, "Присед", v, ResultUnit.KG) }

    private fun indicatorOf(ts: List<TrainingWithExercises>, name: String) =
        AnalyticsEngine.indicators(ts).first { it.name == name }

    @Test
    fun growth_isBigGain_withAbsoluteAndRelativeChange() {
        val ts = squat(-70, 7, listOf(100.0, 102.5, 105.0, 107.5, 110.0, 115.0, 117.5, 120.0))
        val ind = indicatorOf(ts, "Присед")
        val d = AnalyticsEngine.dynamics(ind, AnalyticsEngine.points(ind, ts), null)
        assertEquals(Verdict.BIG_GAIN, d.verdict)
        assertEquals(20.0, d.absChange!!, 1e-9)
        assertEquals(20.0, d.relChange!!, 1e-9)
        assertNotNull(d.startAvg)
        assertEquals(2, d.confidence)
    }

    @Test
    fun timeIndicator_decreaseIsImprovement() {
        val ts = listOf(4.20, 4.15, 4.10, 4.05, 4.02, 4.00).mapIndexed { i, v ->
            training(-60 + i * 10, 200L + i, "Бег 30 м", v, ResultUnit.SECONDS)
        }
        val ind = indicatorOf(ts, "Бег 30 м")
        assertTrue(ind.lowerIsBetter)
        val d = AnalyticsEngine.dynamics(ind, AnalyticsEngine.points(ind, ts), null)
        // 6 замеров: сравниваются средние первой и последней трети (4.175 → 4.01 с, около 4%)
        assertEquals(Verdict.GAIN, d.verdict)
        assertTrue(d.absChange!! < 0)
        assertTrue(d.improvementPercent!! > 0)
    }

    @Test
    fun stableAndDecline() {
        val flat = squat(-40, 5, listOf(100.0, 100.0, 100.5, 100.0))
        val ind = indicatorOf(flat, "Присед")
        assertEquals(Verdict.STABLE, AnalyticsEngine.dynamics(ind, AnalyticsEngine.points(ind, flat), null).verdict)
        val down = squat(-40, 5, listOf(100.0, 98.0, 95.0, 92.0))
        assertEquals(Verdict.BIG_LOSS, AnalyticsEngine.dynamics(ind, AnalyticsEngine.points(ind, down), null).verdict)
    }

    @Test
    fun rangeFilter_andInsufficientData() {
        val ts = squat(-70, 7, listOf(100.0, 105.0, 110.0, 115.0))
        val ind = indicatorOf(ts, "Присед")
        val pts = AnalyticsEngine.points(ind, ts)
        val one = AnalysisRange(at(-71), at(-65))
        val d = AnalyticsEngine.dynamics(ind, pts, one)
        assertEquals(Verdict.NO_DATA, d.verdict)
        assertEquals(1, d.points.size)
        assertEquals(Verdict.NO_DATA, AnalyticsEngine.dynamics(ind, pts, AnalysisRange(at(10), at(20))).verdict)
    }

    @Test
    fun generalIndicators_areNeutral() {
        val ts = squat(-40, 7, listOf(100.0, 100.0, 100.0)).mapIndexed { i, t ->
            t.copy(training = t.training.copy(intensity = 5 + i * 2))
        }
        val ind = AnalyticsEngine.indicators(ts).first { it.key == AnalyticsEngine.KEY_INTENSITY }
        assertTrue(ind.isGeneral)
        val d = AnalyticsEngine.dynamics(ind, AnalyticsEngine.points(ind, ts), null)
        assertEquals("Заметный рост", d.verdict.label(true))
    }

    @Test
    fun parseAttempts_handlesFoulsAndBlanks() {
        val a = AnalyticsEngine.parseAttempts("15.12, 15.31, X, -, 15.40, -")
        assertEquals(4, a.size)
        assertEquals(1, a.count { it.foul })
        assertEquals(15.40, a.mapNotNull { it.distance }.max(), 1e-9)
        assertTrue(AnalyticsEngine.parseAttempts(null).isEmpty())
    }

    private fun comp(id: Long, name: String, offset: Int, attempts: String?, place: String?) = Competition(
        id = id, name = name, date = at(offset), attemptsJson = attempts, placement = place,
        status = if (offset < 0) CompetitionStatus.COMPLETED else CompetitionStatus.UPCOMING
    )

    @Test
    fun competitionPrep_comparesWithPreviousStart() {
        val ts = squat(-110, 5, (0 until 22).map { 100.0 + it * 1.5 })
        val comps = listOf(
            comp(1, "Первый", -80, "15.10, 15.20, X", "3 место"),
            comp(2, "Второй", -50, "15.30, 15.45, 15.50", "2 место"),
            comp(3, "Третий", -20, "15.60, X, 15.74", "1 место")
        )
        val entries = AnalyticsEngine.competitionEntries(comps, emptyList())
        assertEquals(15.74, entries.last().result!!, 1e-9)
        val ind = indicatorOf(ts, "Присед")
        val a = AnalyticsEngine.competitionPrep(entries.last(), entries, ts, listOf(ind), AnalyticsEngine.prepRange(comps[2].date, 28))
        assertEquals("Второй", a.previous!!.competition.name)
        assertEquals(0.24, a.resultChange!!, 1e-9)
        assertTrue(a.isBestSoFar)
        assertTrue(a.range!!.end < comps[2].date)
        assertTrue(a.trainingsDone > 0)
        val row = a.rows.single()
        assertTrue(row.dynamics.hasData)
        assertNotNull(row.previousDynamics)
        assertNotNull(row.link)
        assertEquals(2, row.link!!.total)
        assertEquals(2, row.link!!.agree)
    }

    @Test
    fun competitionPrep_clipsRangeAtCompetitionDay() {
        val ts = squat(-40, 5, listOf(100.0, 105.0, 110.0))
        val c = comp(1, "Старт", -10, "15.00", null)
        val entries = AnalyticsEngine.competitionEntries(listOf(c), emptyList())
        val ind = indicatorOf(ts, "Присед")
        val a = AnalyticsEngine.competitionPrep(entries[0], entries, ts, listOf(ind), AnalysisRange(at(-40), at(5)))
        assertTrue(a.range!!.end < c.date)
        assertNull(a.previous)
        assertNull(a.resultChange)
    }

    @Test
    fun periodSummary_countsTrainingsAndLinksCompetition() {
        val ts = squat(-60, 5, (0 until 12).map { 100.0 + it * 2 }) +
            TrainingWithExercises(TrainingSession(id = 999, date = at(-58), isSkipped = true), emptyList())
        val period = TrainingPeriod(id = 1, name = "База", startDate = at(-62), endDate = at(-10), colorHex = "#2E7D6B")
        val entries = AnalyticsEngine.competitionEntries(listOf(comp(1, "Финал", -5, "15.50", "1 место")), emptyList())
        val ind = indicatorOf(ts, "Присед")
        val s = AnalyticsEngine.periodSummary(period, ts, entries, listOf(ind), today)
        assertEquals(PeriodState.FINISHED, s.state)
        assertEquals(11, s.trainingsDone)
        assertEquals(1, s.skipped)
        assertEquals(1, s.competitions.size)
        assertFalse(s.competitions[0].inside)
        assertEquals(5, s.competitions[0].daysAfterEnd)
        assertTrue(s.dynamics[0].hasData)
        val upcoming = AnalyticsEngine.periodSummary(period.copy(startDate = at(5), endDate = at(30)), ts, entries, listOf(ind), today)
        assertEquals(PeriodState.UPCOMING, upcoming.state)
        assertNull(upcoming.range)
    }

    @Test
    fun competitionSummary_aggregates() {
        val entries = AnalyticsEngine.competitionEntries(listOf(
            comp(1, "A", -80, "15.10, X, 15.20", "3 место"),
            comp(2, "B", -40, "15.30, 15.45, X", "5 место")
        ), emptyList())
        val s = AnalyticsEngine.competitionSummary(entries)
        assertEquals(2, s.count)
        assertEquals(1, s.podiums)
        assertEquals(0.25, s.changeFirstToLast!!, 1e-9)
        assertEquals(33, s.foulPercent)
    }
}
