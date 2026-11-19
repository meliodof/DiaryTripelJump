package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

/** Тройной прыжок как отдельная категория: варианты, перенос прежних упражнений, главный тройной. */
class TripleJumpTest {

    @Test
    fun variants_twoShortOneFull() {
        assertEquals(listOf(TripleJump.SHORT to false, TripleJump.SHORT to true, TripleJump.FULL to false),
            TripleJump.variants.map { it.name to it.inSpikes })
        assertTrue(TripleJump.isExercise("тройной с полного разбега "))
        assertFalse(TripleJump.isExercise("Тройной прыжок"))
    }

    @Test
    fun legacyNames_mapToVariants() {
        assertEquals(TripleJump.SHORT, TripleJump.canonical("Тройной прыжок с 6 шагов"))
        assertEquals(TripleJump.SHORT, TripleJump.canonical("Тройной с 8 беговых шагов"))
        assertEquals(TripleJump.SHORT, TripleJump.canonical("Тройной с короткого разбега"))
        assertEquals(TripleJump.FULL, TripleJump.canonical("Тройной прыжок с полного разбега"))
        assertEquals(TripleJump.FULL, TripleJump.canonical("Тройной прыжок"))
        // с полного разбега шиповки отдельным вариантом не ведутся
        assertFalse(TripleJump.spikesFor(TripleJump.FULL, true))
        assertTrue(TripleJump.spikesFor(TripleJump.SHORT, true))
    }

    @Test
    fun legacy_onlyTwoLegsMetersNoInventory() {
        assertTrue(TripleJump.isLegacy("Тройной прыжок с 6 шагов", "M", false, null))
        assertFalse(TripleJump.isLegacy("Тройной на одной ноге", "M", true, null))
        assertFalse(TripleJump.isLegacy("Тройной с отягощением", "M", false, "Жилет"))
        assertFalse(TripleJump.isLegacy("Тройной прыжок", "CM", false, null))
        assertFalse(TripleJump.isLegacy(TripleJump.FULL, "M", false, null))
    }

    @Test
    fun mainJump_excludesShortRunUp() {
        fun ex(name: String, v: Double) = ExerciseResult(exerciseId = 1, exerciseName = name, trainingSessionId = 1, date = 1_000,
            result = v, resultUnit = ResultUnit.M)
        val records = RecordsEngine.build(emptyList(), listOf(ex(TripleJump.SHORT, 15.2), ex(TripleJump.FULL, 14.6)))
        val (_, training) = RecordInsights.mainJumpSplit(records)
        assertEquals(14.6, training!!.best, 1e-9)
    }

    @Test
    fun attemptsWithRunUp_alignedForTargetAndNotes() {
        // три попытки: вторая — незачёт с заступом; разбег хранится с номером попытки
        val runUp = listOf(RunUpAttempt(boardFeet = 0.0), RunUpAttempt(boardFeet = 1.0), RunUpAttempt())
        val r = ExerciseResult(id = 4, exerciseId = 1, exerciseName = TripleJump.FULL, trainingSessionId = 2, date = 5_000,
            result = 14.4, resultUnit = ResultUnit.M, sets = 3, reps = 1, setResultsJson = "14.1,0.0,14.4",
            runUpJson = RunUp.encodeAll(runUp))
        val lines = AttemptLines.exercise(r)
        assertEquals("Попытка 2: незачёт, заступил на 1 ст.", lines[1].label)
        assertEquals("Попытка 3: ${formatRecordValue(14.4, ResultUnit.M)}", lines[2].label)
        val shots = runUpShots(listOf(TrainingWithExercises(TrainingSession(id = 2, date = 5_000), listOf(r))), emptyList())
        assertEquals(listOf(0.0, 1.0), shots.map { it.feet })
        assertEquals(AttemptRef(false, 4, "1"), shots[1].ref)
        assertEquals(14.4, RecordsEngine.build(emptyList(), listOf(r)).single().best, 1e-9)
    }
}
