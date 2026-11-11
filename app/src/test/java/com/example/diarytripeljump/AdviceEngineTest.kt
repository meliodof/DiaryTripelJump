package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

class AdviceEngineTest {

    private val day = 86_400_000L
    private val now = 1_000 * day + 12 * 3_600_000L

    private var nextId = 1L

    private fun training(
        daysAgo: Int,
        results: List<ExerciseResult> = emptyList(),
        notes: String? = null,
        skipped: Boolean = false,
        reason: String? = null
    ): TrainingWithExercises {
        val id = nextId++
        val date = now - daysAgo * day
        return TrainingWithExercises(
            TrainingSession(id = id, date = date, notes = notes, isSkipped = skipped, skipReason = reason),
            results.map { it.copy(trainingSessionId = id, date = date) }
        )
    }

    private fun squats(sets: Int, reps: Int, notes: String? = null) = ExerciseResult(
        exerciseId = 1, exerciseName = "Присед", trainingSessionId = 0, date = 0,
        result = 100.0, resultUnit = ResultUnit.KG, sets = sets, reps = reps, notes = notes
    )

    private fun runUp(vararg board: Double) = ExerciseResult(
        exerciseId = 2, exerciseName = RunUp.NAME, trainingSessionId = 0, date = 0,
        result = 0.0, resultUnit = ResultUnit.CM, runUpJson = RunUp.encode(board.map { RunUpAttempt(boardFeet = it) })
    )

    private fun build(
        trainings: List<TrainingWithExercises> = emptyList(),
        competitions: List<Competition> = emptyList(),
        body: List<BodyMeasurement> = emptyList()
    ) = AdviceEngine.build(AdviceInput(trainings, competitions, emptyList(), emptyList(), body, now))

    @Test
    fun emptyDiary_noAdvice() {
        assertTrue(build().isEmpty())
    }

    @Test
    fun frequentFouls_adviseMovingMarkBack() {
        val advice = build(listOf(training(3, listOf(runUp(0.5, 0.5, 1.0, 0.0, 0.5)))))
        val a = advice.single { it.id.startsWith("runup-foul") }
        assertEquals(AdviceKind.RISK, a.kind)
        assertTrue(a.text, a.text.contains("4 из 5"))
    }

    @Test
    fun accurateRunUp_isGoodNews() {
        val a = build(listOf(training(3, listOf(runUp(0.0, 0.0, 0.0, 0.0, 0.5))))).single { it.id.startsWith("runup") }
        assertEquals(AdviceKind.GOOD, a.kind)
    }

    @Test
    fun oldRunUp_isIgnored() {
        assertTrue(build(listOf(training(60, listOf(runUp(1.0, 1.0, 1.0, 1.0))))).none { it.id.startsWith("runup") })
    }

    @Test
    fun loadJump_isRisk() {
        val trainings = listOf(
            training(2, listOf(squats(10, 10))),          // эта неделя: 100
            training(9, listOf(squats(5, 10))),           // прошлые недели: по 50
            training(16, listOf(squats(5, 10))),
            training(23, listOf(squats(5, 10)))
        )
        val a = build(trainings).single { it.id.startsWith("load") }
        assertEquals("Нагрузка выросла на 100%", a.title)
    }

    @Test
    fun steadyLoad_noAdvice() {
        val trainings = (0..3).map { w -> training(2 + w * 7, listOf(squats(5, 10))) }
        assertTrue(build(trainings).none { it.id.startsWith("load") })
    }

    @Test
    fun painInRecentNotes_isRisk() {
        val trainings = listOf(
            training(1, notes = "Болит голень"),
            training(3, listOf(squats(3, 5, notes = "немного тянет заднюю"))),
            training(5)
        )
        val a = build(trainings).single { it.id.startsWith("pain") }
        assertTrue(a.text, a.text.contains("2 из 3"))
    }

    @Test
    fun wordMore_isNotPain() {
        val trainings = listOf(training(1, notes = "Сделал больше подходов"), training(2, notes = "больше скорости"), training(3))
        assertTrue(build(trainings).none { it.id.startsWith("pain") })
    }

    @Test
    fun manySkips_namesReason() {
        val trainings = listOf(
            training(2, skipped = true, reason = "Болезнь"),
            training(5, skipped = true, reason = "болезнь"),
            training(9, skipped = true, reason = "Учёба")
        )
        val a = build(trainings).single { it.id.startsWith("skips") }
        assertTrue(a.text, a.text.contains("болезнь"))
    }

    @Test
    fun competitionSoon_withoutTaper_isRisk() {
        val trainings = listOf(
            training(2, listOf(squats(10, 10))),
            training(9, listOf(squats(5, 10))),
            training(16, listOf(squats(5, 10)))
        )
        val start = Competition(id = 7, name = "Чемпионат", date = now + 5 * day)
        val a = build(trainings, listOf(start)).single { it.id.startsWith("taper") }
        assertEquals(AdviceKind.RISK, a.kind)
        assertTrue(a.title.contains("Чемпионат"))
    }

    @Test
    fun farCompetition_noTaperAdvice() {
        val start = Competition(id = 7, name = "Чемпионат", date = now + 30 * day)
        assertTrue(build(competitions = listOf(start)).none { it.id.startsWith("taper") })
    }

    @Test
    fun weightChange_isNoticed() {
        val body = listOf(
            BodyMeasurement(id = 1, date = now - 40 * day, weightKg = 75.0),
            BodyMeasurement(id = 2, date = now - 1 * day, weightKg = 72.0)
        )
        val a = build(body = body).single { it.id.startsWith("weight") }
        assertEquals("Вес −3 кг за месяц", a.title)
    }

    @Test
    fun risksComeFirst() {
        val trainings = listOf(
            training(1, listOf(runUp(0.0, 0.0, 0.0, 0.0)), notes = "болит колено"),
            training(2, notes = "боль в стопе")
        )
        val kinds = build(trainings).map { it.kind }
        assertEquals(kinds.sortedBy { it.ordinal }, kinds)
        assertEquals(AdviceKind.RISK, kinds.first())
    }
}
