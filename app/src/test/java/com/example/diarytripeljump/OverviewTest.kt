package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

/** Обзор на вкладке «Аналитика»: попадания на разбеге с выбором охвата и столбиками одинаковых значений, данные виджета. */
class OverviewTest {

    private val day = 86_400_000L
    private val now = startOfDay(20_000 * day) + 12 * 3_600_000L
    private var nextId = 1L

    private fun training(daysAgo: Int, results: List<ExerciseResult> = emptyList(), skipped: Boolean = false): TrainingWithExercises {
        val id = nextId++
        val date = now - daysAgo * day
        return TrainingWithExercises(
            TrainingSession(id = id, date = date, isSkipped = skipped),
            results.map { it.copy(trainingSessionId = id, date = date) }
        )
    }

    private fun squats(sets: Int, reps: Int) = ExerciseResult(exerciseId = 1, exerciseName = "Присед", trainingSessionId = 0, date = 0,
        result = 100.0, resultUnit = ResultUnit.KG, sets = sets, reps = reps)

    private fun runUp(vararg board: Double) = ExerciseResult(exerciseId = 2, exerciseName = RunUp.NAME, trainingSessionId = 0, date = 0,
        result = 0.0, resultUnit = ResultUnit.CM, runUpJson = RunUp.encode(board.map { RunUpAttempt(boardFeet = it) }))

    // три тренировки с разбегом: 3, 20 и 100 дней назад, плюс старт 10 дней назад
    private val trainings = listOf(
        training(3, listOf(runUp(1.0, 0.5, 1.0, 0.0, 1.5))),
        training(20, listOf(runUp(0.0, 0.0, -0.2, 0.2))),
        training(100, listOf(runUp(-1.0, -1.5, -1.0, -0.5)))
    )
    private val start = Competition(id = 7, name = "Первенство", date = now - 10 * day, runUpJson = RunUp.encodeAll(listOf(RunUpAttempt(boardFeet = 0.5))))
    private val shots = runUpShots(trainings, listOf(start))

    @Test
    fun runUp_sessionsNewestFirst() {
        val s = RunUpTarget.sessions(shots)
        assertEquals(4, s.size)
        assertEquals(trainings[0].training.id.let { "t:$it" }, s.first().id)
        assertTrue(s.any { it.id == "c:7" && it.competition && it.label.startsWith("Первенство") })
    }

    @Test
    fun runUp_lastSession_foulsSuggestMovingBack() {
        val v = RunUpTarget.build(shots, RunUpScope.Last, now)!!
        assertEquals(5, v.shots.size)
        assertEquals(1, v.hits); assertEquals(4, v.fouls); assertEquals(0, v.shorts)
        assertEquals(1.0, v.move, 1e-9)
    }

    @Test
    fun runUp_chosenSession_onTarget() {
        val id = "t:${trainings[1].training.id}"
        val v = RunUpTarget.build(shots, RunUpScope.Session(id), now)!!
        assertEquals(4, v.shots.size)
        assertEquals(0.0, v.move, 1e-9)
    }

    @Test
    fun runUp_daysAllAndPeriod() {
        // месяц: две тренировки и старт; всё время — все попытки; период 90–110 дней назад — только старая тренировка
        assertEquals(5 + 4 + 1, RunUpTarget.build(shots, RunUpScope.Days(30), now)!!.shots.size)
        assertEquals(14, RunUpTarget.build(shots, RunUpScope.All, now)!!.shots.size)
        val old = RunUpTarget.build(shots, RunUpScope.Period(now - 110 * day, now - 90 * day), now)!!
        assertEquals(4, old.shorts)
        assertEquals(-1.0, old.move, 1e-9)
        assertNull(RunUpTarget.build(shots, RunUpScope.Period(now - 60 * day, now - 50 * day), now))
    }

    @Test
    fun runUp_equalFeetStackOldestFirst() {
        // 0 ст. встречается дважды 20 дней назад и один раз 3 дня назад: столбик из трёх точек, свежая — сверху
        val all = RunUpTarget.build(shots, RunUpScope.All, now)!!.shots
        val zero = RunUpTarget.stacks(all).filter { it.first.feet == 0.0 }
        assertEquals(listOf(0, 1, 2), zero.map { it.second }.sorted())
        assertEquals(2, zero.first { it.first.date == now - 3 * day }.second)
        // значение по горизонтали — ровно введённые стопы, без сдвигов
        assertTrue(RunUpTarget.stacks(all).all { (s, _) -> all.any { it.feet == s.feet } })
        assertEquals(0, RunUpTarget.stacks(all).first { it.first.feet == 1.5 }.second)
    }
    @Test
    fun heat_peaksWhereAttemptsCluster() {
        // все время: три попытки на 0 и по одной—две на 1 стопе — самое «горячее» место у бруска
        val all = RunUpTarget.build(shots, RunUpScope.All, now)!!.shots
        val heat = RunUpTarget.heat(all, -2.0, 2.0, 401)
        assertEquals(1.0, heat.max(), 1e-9)
        val hottest = -2.0 + 4.0 * heat.indices.maxBy { heat[it] } / 400
        assertTrue("пик у бруска: $hottest", kotlin.math.abs(hottest) < 0.3)
        assertTrue(heat.first() < 0.2)
        assertTrue(RunUpTarget.heat(emptyList(), -2.0, 2.0, 10).all { it == 0.0 })
    }

    @Test
    fun widgetSnapshot_countsDays() {
        val snap = WidgetSnapshot.of(emptyList(), listOf(Competition(id = 1, name = "Кубок", date = now + 3 * day)), InsightsResult(emptyList(), emptyList()), now)
        assertEquals("Кубок", snap.startName)
        assertEquals(3, snap.daysLeft(now))
        assertNull(snap.daysLeft(now + 5 * day))
    }
}
