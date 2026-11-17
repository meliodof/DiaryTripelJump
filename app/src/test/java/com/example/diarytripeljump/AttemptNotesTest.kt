package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

/** Комментарии к отдельным попыткам: хранение в записи, подписи попыток, привязка точек мишени. */
class AttemptNotesTest {

    @Test
    fun encodeDecode_roundTrip_andEmptyRemoved() {
        val text = AttemptNotes.encode(mapOf("0" to "смазал толчок", "2" to "  ", "a1" to "ветер в лицо; X"))
        assertEquals(mapOf("0" to "смазал толчок", "a1" to "ветер в лицо; X"), AttemptNotes.decode(text))
        assertNull(AttemptNotes.encode(mapOf("0" to "")))
        assertTrue(AttemptNotes.decode(null).isEmpty())
        // один комментарий меняется, пустой — удаляется
        val changed = AttemptNotes.with(text, "0", "")
        assertEquals(mapOf("a1" to "ветер в лицо; X"), AttemptNotes.decode(changed))
    }

    @Test
    fun competitionLines_qualificationThenFinal_withRunUp() {
        val c = Competition(
            id = 5, name = "Кубок", attemptsJson = "14.10, X, 14.32",
            runUpJson = RunUp.encodeAll(listOf(RunUpAttempt(), RunUpAttempt(boardFeet = 1.0))),
            hasQualification = true, qualificationAttemptsJson = "13.90",
            attemptNotesJson = AttemptNotes.encode(mapOf(AttemptNotes.finalKey(1) to "заступ"))
        )
        val lines = AttemptLines.competition(c)
        assertEquals(listOf("q0", "a0", "a1", "a2"), lines.map { it.key })
        assertEquals("Квалификация, попытка 1: 13.90 м", lines[0].label)
        assertEquals("Попытка 2: незачёт, заступил на 1 ст.", lines[2].label)
        assertEquals("заступ", lines[2].note)
    }

    @Test
    fun exerciseLines_setsAndSingleResult() {
        val sets = ExerciseResult(id = 1, exerciseId = 1, exerciseName = "Присед", trainingSessionId = 1, date = 0,
            result = 120.0, resultUnit = ResultUnit.KG, sets = 3, reps = 5, setResultsJson = "100,110,120",
            attemptNotesJson = AttemptNotes.encode(mapOf("2" to "тяжело")))
        val lines = AttemptLines.exercise(sets)
        assertEquals(3, lines.size)
        assertEquals("Подход 3: 120 кг", lines[2].label)
        assertEquals("тяжело", lines[2].note)
        val single = sets.copy(sets = null, setResultsJson = null, attemptNotesJson = null)
        assertEquals(listOf("Попытка 1: 120 кг"), AttemptLines.exercise(single).map { it.label })
    }

    @Test
    fun runUpShots_carryAttemptRefAndNote() {
        val r = ExerciseResult(id = 9, exerciseId = 2, exerciseName = RunUp.NAME, trainingSessionId = 3, date = 1_000,
            result = 0.0, resultUnit = ResultUnit.CM,
            runUpJson = RunUp.encode(listOf(RunUpAttempt(boardFeet = 0.0), RunUpAttempt(boardFeet = 1.5))),
            attemptNotesJson = AttemptNotes.encode(mapOf("1" to "поздно начал")))
        val t = TrainingWithExercises(TrainingSession(id = 3, date = 1_000), listOf(r))
        val shots = runUpShots(listOf(t), emptyList())
        assertEquals(AttemptRef(false, 9, "1"), shots[1].ref)
        assertEquals("поздно начал", shots[1].note)
        assertNull(shots[0].note)
    }
}
