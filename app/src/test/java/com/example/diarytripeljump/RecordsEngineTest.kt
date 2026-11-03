package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

class RecordsEngineTest {

    private val day = 86_400_000L

    private fun ex(
        id: Long, name: String, value: Double, unit: ResultUnit, dayN: Int,
        inSpikes: Boolean = false, sets: String? = null
    ) = ExerciseResult(
        id = id, exerciseId = 1, exerciseName = name, trainingSessionId = 1, date = dayN * day,
        result = value, resultUnit = unit, setResultsJson = sets, inSpikes = inSpikes
    )

    @Test
    fun spikes_areSeparateRecord() {
        val records = RecordsEngine.build(
            tj = emptyList(),
            ex = listOf(
                ex(1, "Бег 30 м", 4.10, ResultUnit.SECONDS, 1),
                ex(2, "Бег 30 м", 3.85, ResultUnit.SECONDS, 2, inSpikes = true),
                ex(3, "Бег 30 м", 4.05, ResultUnit.SECONDS, 3)
            )
        )
        assertEquals(2, records.size)
        val spikes = records.single { it.inSpikes }
        val shoes = records.single { !it.inSpikes }
        assertEquals(3.85, spikes.best, 1e-9)
        assertEquals(4.05, shoes.best, 1e-9)
        assertTrue(spikes.displayName.endsWith("в шиповках"))
        assertNotEquals(spikes.key, shoes.key)
    }

    @Test
    fun keyWithoutSpikes_isUnchanged_soOldGoalsStillMatch() {
        assertEquals(
            "ex|присед|BOTH|KG",
            RecordsEngine.exerciseRecordKey("Присед", false, null, ResultUnit.KG)
        )
        assertEquals(
            "ex|присед|BOTH|KG|spikes",
            RecordsEngine.exerciseRecordKey("Присед", false, null, ResultUnit.KG, inSpikes = true)
        )
    }

    @Test
    fun manualSpikesRecord_mergesWithTrainingSpikesRecord() {
        val records = RecordsEngine.build(
            tj = listOf(
                TripleJumpResult(
                    id = 10, eventName = "Тройной с 6 шагов", result = 13.9, resultUnit = ResultUnit.M,
                    isCompetition = false, date = 5 * day, inSpikes = true
                )
            ),
            ex = listOf(ex(1, "Тройной с 6 шагов", 13.5, ResultUnit.M, 1, inSpikes = true))
        )
        val r = records.single()
        assertTrue(r.inSpikes)
        assertEquals(13.9, r.best, 1e-9)
        assertEquals(2, r.history.size)
    }

    @Test
    fun timeIsLowerBetter_andHistoryTracksImprovements() {
        val records = RecordsEngine.build(
            tj = emptyList(),
            ex = listOf(
                ex(1, "Бег 60 м", 7.0, ResultUnit.SECONDS, 1),
                ex(2, "Бег 60 м", 7.2, ResultUnit.SECONDS, 2),
                ex(3, "Бег 60 м", 6.9, ResultUnit.SECONDS, 3)
            )
        )
        val r = records.single()
        assertTrue(r.lowerIsBetter)
        assertEquals(6.9, r.best, 1e-9)
        assertEquals(listOf(7.0, 6.9), r.history.map { it.value })
        assertEquals(7.0, r.history.last().previousValue!!, 1e-9)
    }

    @Test
    fun bestOfSets_isUsed() {
        val r = RecordsEngine.build(
            tj = emptyList(),
            ex = listOf(ex(1, "Присед", 100.0, ResultUnit.KG, 1, sets = "90,110,105"))
        ).single()
        assertEquals(110.0, r.best, 1e-9)
    }

    @Test
    fun override_appliesOnlyWhileAutoBestUnchanged() {
        val results = listOf(ex(1, "Присед", 100.0, ResultUnit.KG, 1))
        val key = RecordsEngine.exerciseRecordKey("Присед", false, null, ResultUnit.KG)
        val override = RecordOverride(key, baseBest = 100.0, value = 102.5, date = 2 * day, isIndoor = true, weather = null, notes = null)

        val edited = RecordsEngine.build(emptyList(), results, listOf(override)).single()
        assertTrue(edited.isManuallyEdited)
        assertEquals(102.5, edited.best, 1e-9)

        val newer = RecordsEngine.build(emptyList(), results + ex(2, "Присед", 110.0, ResultUnit.KG, 3), listOf(override)).single()
        assertFalse(newer.isManuallyEdited)
        assertEquals(110.0, newer.best, 1e-9)
    }

    @Test
    fun barbell_weightIsResult_singleRecordAcrossWeights() {
        fun squat(id: Long, kg: Double, day: Int) = ExerciseResult(
            id = id, exerciseId = 1, exerciseName = "Присед", trainingSessionId = 1, date = day * this.day,
            result = kg, resultUnit = ResultUnit.KG, inventoryName = "Штанга", inventoryUnit = ResultUnit.KG, inventoryValue = kg
        )
        val manual = TripleJumpResult(
            id = 5, eventName = "Присед", result = 150.0, resultUnit = ResultUnit.KG, isCompetition = false,
            date = 3 * day, inventoryName = "Штанга", inventoryUnit = ResultUnit.KG, inventoryValue = 150.0
        )
        val r = RecordsEngine.build(listOf(manual), listOf(squat(1, 120.0, 1), squat(2, 140.0, 2))).single()
        assertEquals(150.0, r.best, 1e-9)
        assertEquals("Присед · штанга", r.displayName)
        assertTrue(RecordsEngine.isLoadedImplement(ResultUnit.KG, ResultUnit.KG))
        assertFalse(RecordsEngine.isLoadedImplement(ResultUnit.CM, ResultUnit.CM))
    }

    @Test
    fun shotWeights_areSeparateExercises() {
        fun shot(id: Long, kg: Double, m: Double) = ExerciseResult(
            id = id, exerciseId = 1, exerciseName = "Бросок ядра", trainingSessionId = 1, date = id * day,
            result = m, resultUnit = ResultUnit.M, inventoryName = "Ядро", inventoryUnit = ResultUnit.KG, inventoryValue = kg
        )
        val records = RecordsEngine.build(emptyList(), listOf(shot(1, 5.0, 16.0), shot(2, 7.26, 14.0)))
        assertEquals(2, records.size)
        assertNotEquals(
            RecordsEngine.variantKey("Бросок ядра", false, null, false, "Ядро", ResultUnit.KG, 5.0, ResultUnit.M),
            RecordsEngine.variantKey("Бросок ядра", false, null, false, "Ядро", ResultUnit.KG, 7.26, ResultUnit.M)
        )
    }

    @Test
    fun antiRecord_keepsWorstResult() {
        val r = RecordsEngine.build(
            tj = listOf(
                TripleJumpResult(id = 1, result = 13.0, isAntiRecord = true, date = day),
                TripleJumpResult(id = 2, result = 12.5, isAntiRecord = true, date = 2 * day)
            ),
            ex = emptyList()
        ).single()
        assertTrue(r.isAnti)
        assertEquals(12.5, r.best, 1e-9)
    }
}
