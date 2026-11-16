package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

/** Обзор на вкладке «Аналитика»: радар формы, мишень разбега, карта сезона, подводка, данные виджета. */
class OverviewTest {

    private val day = 86_400_000L
    private val now = startOfDay(20_000 * day) + 12 * 3_600_000L
    private var nextId = 1L

    private fun record(name: String, unit: ResultUnit, points: List<RecordPoint>, lower: Boolean = false) = RecordSummary(
        key = "ex|$name", name = name, displayName = name, unit = unit, isSingleLeg = false, legSide = null,
        isAnti = false, isCompetition = false, category = "Силовые", isIndoor = false, weather = null,
        inventoryLabel = null, levelLabel = "PB", best = if (lower) points.minOf { it.value } else points.maxOf { it.value },
        bestDate = points.last().date, lowerIsBetter = lower, deletableResultId = null, attempts = points.size,
        history = emptyList(), points = points
    )

    private fun training(daysAgo: Int, minutes: Int? = null, results: List<ExerciseResult> = emptyList(), skipped: Boolean = false): TrainingWithExercises {
        val id = nextId++
        val date = now - daysAgo * day
        return TrainingWithExercises(
            TrainingSession(id = id, date = date, durationMinutes = minutes, isSkipped = skipped),
            results.map { it.copy(trainingSessionId = id, date = date) }
        )
    }

    private fun squats(sets: Int, reps: Int) = ExerciseResult(exerciseId = 1, exerciseName = "Присед", trainingSessionId = 0, date = 0,
        result = 100.0, resultUnit = ResultUnit.KG, sets = sets, reps = reps)

    private fun runUp(vararg board: Double) = ExerciseResult(exerciseId = 2, exerciseName = RunUp.NAME, trainingSessionId = 0, date = 0,
        result = 0.0, resultUnit = ResultUnit.CM, runUpJson = RunUp.encode(board.map { RunUpAttempt(boardFeet = it) }))

    @Test
    fun radar_fasterSprint_raisesSpeed() {
        // бег 30 м: месяц назад 4,30, сейчас 4,10 — скорость выросла
        val sprint = record("Бег 30 м", ResultUnit.SECONDS,
            listOf(RecordPoint(now - 40 * day, 4.30, false), RecordPoint(now - 35 * day, 4.25, false), RecordPoint(now - 5 * day, 4.10, false)), lower = true)
        val speed = FormRadar.build(listOf(sprint), emptyList(), emptyList(), emptyList(), now).first { it.title == "Скорость" }
        assertEquals(1.0, speed.now!!, 1e-9)
        assertEquals(0.25, speed.before!!, 1e-9)
    }

    @Test
    fun radar_noData_isNull() {
        val axes = FormRadar.build(emptyList(), emptyList(), emptyList(), emptyList(), now)
        assertEquals(7, axes.size)
        assertTrue(axes.all { it.now == null })
    }

    @Test
    fun runUpTarget_foulsSuggestMovingBack() {
        val v = RunUpTarget.build(listOf(training(3, results = listOf(runUp(1.0, 0.5, 1.0, 0.0, 1.5)))), emptyList(), now)!!
        assertEquals(1, v.hits); assertEquals(4, v.fouls); assertEquals(0, v.shorts)
        assertEquals(1.0, v.move, 1e-9)
        val ok = RunUpTarget.build(listOf(training(3, results = listOf(runUp(0.0, 0.0, -0.2, 0.2)))), emptyList(), now)!!
        assertEquals(0.0, ok.move, 1e-9)
    }

    @Test
    fun seasonMap_levelsAndFuture() {
        val weeks = SeasonMap.build(listOf(training(0, minutes = 120), training(1, minutes = 30), training(2, skipped = true)), emptyList(), emptyList(), now)
        assertEquals(SeasonMap.WEEKS, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        val days = weeks.flatten()
        assertEquals(4, days.first { it.day == startOfDay(now) }.level)
        assertEquals(1, days.first { it.day == startOfDay(now - day) }.level)
        assertTrue(days.first { it.day == startOfDay(now - 2 * day) }.skipped)
        assertTrue(days.filter { it.day > startOfDay(now) }.all { it.level == -1 })
    }

    @Test
    fun taper_baselineAndLastWeek() {
        val start = Competition(id = 1, name = "Первенство", date = now + 10 * day)
        // обычный объём: 3 недели до подводки по 50 повторений через день; последняя неделя — столько же
        val trainings = (5..40 step 2).map { training(it, results = listOf(squats(5, 10))) }
        val v = TaperTimeline.build(trainings, listOf(start), now)!!
        assertEquals(10, v.daysLeft)
        assertEquals(TaperTimeline.TAPER_DAYS + 1, v.days.size)
        assertTrue(v.baselineDaily > 0)
        assertTrue(v.days.last().future)
        assertNull(TaperTimeline.build(trainings, listOf(start.copy(date = now + 40 * day)), now))
    }

    @Test
    fun radar_axesCarryKeysAndSeries_adviceMapsToAxes() {
        val sprint = record("Бег 30 м", ResultUnit.SECONDS, listOf(RecordPoint(now - 5 * day, 4.1, false)), lower = true)
        val axes = FormRadar.build(listOf(sprint), emptyList(), emptyList(), emptyList(), now)
        assertEquals("rec:ex|Бег 30 м", axes.first { it.key == RadarKey.SPEED }.seriesId)
        val advice = listOf(
            Advice("runup-foul-1", AdviceKind.RISK, "Частые заступы", "", ""),
            Advice("load-1", AdviceKind.RISK, "Нагрузка выросла", "", ""),
            Advice("anomaly-x", AdviceKind.RISK, "Резкий спад", "", "", recordKey = "ex|Бег 30 м"),
            Advice("record-1", AdviceKind.GOOD, "Новый рекорд", "", "", recordKey = "ex|Бег 30 м")
        )
        val w = radarWarnings(axes, advice)
        assertEquals("Частые заступы", w[RadarKey.RUNUP]?.title)
        assertEquals("Нагрузка выросла", w[RadarKey.VOLUME]?.title)
        assertEquals("Резкий спад", w[RadarKey.SPEED]?.title)
        assertEquals(3, w.size)
    }

    @Test
    fun radar_onChosenDay_showsFormAsOfThatDay() {
        // в прошлом месяце бег был быстрее, чем сейчас: на выбранный день месяц назад форма лучше
        val sprint = record("Бег 30 м", ResultUnit.SECONDS,
            listOf(RecordPoint(now - 60 * day, 4.4, false), RecordPoint(now - 40 * day, 4.0, false), RecordPoint(now - 3 * day, 4.4, false)), lower = true)
        val today = FormRadar.build(listOf(sprint), emptyList(), emptyList(), emptyList(), now).first { it.key == RadarKey.SPEED }
        val past = FormRadar.build(listOf(sprint), emptyList(), emptyList(), emptyList(), now - 35 * day).first { it.key == RadarKey.SPEED }
        assertEquals(0.0, today.now!!, 1e-9)
        assertEquals(1.0, past.now!!, 1e-9)
    }

    @Test
    fun focusSeries_bringsChartToTopOrAddsOne() {
        val cfg = VisualizationConfig(charts = listOf(
            ChartConfig(id = 1, title = "Вес", series = listOf(SeriesStyle("body:weight", SeriesStyle.AUTO_COLOR))),
            ChartConfig(id = 2, title = "Присед", series = listOf(SeriesStyle("rec:ex|присед", SeriesStyle.AUTO_COLOR, visible = false)))
        ))
        val moved = cfg.focusSeries("rec:ex|присед", "Присед")
        assertEquals(listOf(2L, 1L), moved.charts.map { it.id })
        assertTrue(moved.charts.first().series.single().visible)
        val added = cfg.focusSeries("rec:ex|бег", "Бег 30 м")
        assertEquals(3L, added.charts.first().id)
        assertEquals("Бег 30 м", added.charts.first().title)
        assertEquals(3, added.charts.size)
    }

    @Test
    fun seasonMap_marksComplaintDays() {
        val withNote = TrainingWithExercises(TrainingSession(id = 99, date = now - day, notes = "колено чет побаливает"), emptyList())
        val days = SeasonMap.build(listOf(withNote, training(0, minutes = 60)), emptyList(), emptyList(), now).flatten()
        assertTrue(days.first { it.day == startOfDay(now - day) }.complaint)
        assertFalse(days.first { it.day == startOfDay(now) }.complaint)
    }

    @Test
    fun widgetSnapshot_countsDays() {
        val snap = WidgetSnapshot.of(emptyList(), listOf(Competition(id = 1, name = "Кубок", date = now + 3 * day)), InsightsResult(emptyList(), emptyList()), now)
        assertEquals("Кубок", snap.startName)
        assertEquals(3, snap.daysLeft(now))
        assertNull(snap.daysLeft(now + 5 * day))
    }
}
