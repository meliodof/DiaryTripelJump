package com.example.diarytripeljump

import androidx.compose.ui.graphics.Color
import com.example.diarytripeljump.data.AgeGroups
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.RunUpAttempt
import com.example.diarytripeljump.data.RunUpLength
import com.example.diarytripeljump.data.RunUpMeasurement
import com.example.diarytripeljump.data.RunUpMove
import com.example.diarytripeljump.data.SportRanks
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class SportRanksAndRunUpTest {

    private fun yearMillis(year: Int) = Calendar.getInstance().apply { clear(); set(year, 5, 1) }.timeInMillis
    private val male = AgeGroups.GENDER_MALE

    @Test
    fun normIsInclusive_andDependsOnGender() {
        val date = yearMillis(2026)
        assertEquals("I", SportRanks.reached(13.80, male, null, date)?.code)
        assertEquals("II", SportRanks.reached(13.79, male, null, date)?.code)
        assertEquals("MS", SportRanks.reached(13.40, AgeGroups.GENDER_FEMALE, null, date)?.code)
    }

    @Test
    fun allAdultAndYouthRanksArePresent() {
        assertEquals(
            listOf("MSMK", "MS", "KMS", "I", "II", "III", "I_YOUTH", "II_YOUTH", "III_YOUTH"),
            SportRanks.all.map { it.code }
        )
        assertEquals("III_YOUTH", SportRanks.reached(8.40, male, null, yearMillis(2026))?.code)
        assertNull(SportRanks.reached(8.39, male, null, yearMillis(2026)))
    }

    @Test
    fun noRank_withoutGender() {
        val auto = SportRanks.best(listOf(15.0 to yearMillis(2026)), null, null)
        assertNull(auto.rank)
        assertEquals(15.0, auto.bestResult!!, 1e-9)
    }

    @Test
    fun ageLimit_lowersRank() {
        // в 2026 году спортсмену 2013 г. р. исполняется 13 лет: КМС выполняется только с 14
        assertEquals("I", SportRanks.reached(15.0, male, yearMillis(2013), yearMillis(2026))?.code)
    }

    @Test
    fun best_keepsHighestRankEver_andNextGoal() {
        val auto = SportRanks.best(listOf(12.0 to yearMillis(2025), 13.9 to yearMillis(2026)), male, null)
        assertEquals("I", auto.rank?.code)
        assertEquals("KMS", SportRanks.next(auto.rank)?.code)
        assertEquals("III_YOUTH", SportRanks.next(null)?.code)
        assertNull(SportRanks.next(SportRanks.all.first()))
    }

    @Test
    fun runUp_roundTripKeepsSignsAndEmptyValues() {
        val attempts = listOf(
            RunUpAttempt(boardFeet = 1.5, moveCm = 20.0, moveFeet = 0.5),
            RunUpAttempt(boardFeet = -0.5, moveCm = -10.0),
            RunUpAttempt(boardFeet = 0.0)
        )
        assertEquals(attempts, RunUp.decode(RunUp.encode(attempts)))
        assertTrue(RunUp.decode(RunUp.encode(emptyList())).isEmpty())
    }

    @Test
    fun runUp_encodeAll_keepsAttemptNumbers() {
        val attempts = listOf(RunUpAttempt(), RunUpAttempt(boardFeet = 1.0), RunUpAttempt())
        val decoded = RunUp.decode(RunUp.encodeAll(attempts))
        assertEquals(3, decoded.size)
        assertEquals(1.0, decoded[1].boardFeet!!, 1e-9)
    }

    @Test
    fun runUp_describe_usesWordsForDirection() {
        assertTrue(RunUp.describe(RunUpAttempt(boardFeet = 1.0, moveCm = 20.0)).let { it.contains("заступил") && it.contains("отнёс") })
        assertTrue(RunUp.describe(RunUpAttempt(boardFeet = -1.0, moveFeet = -2.0)).let { it.contains("не доступил") && it.contains("поднёс") })
        assertEquals("попал в брусок", RunUp.describe(RunUpAttempt(boardFeet = 0.0)))
    }

    @Test
    fun runUpExercise_isFoundByName() {
        assertTrue(RunUp.isExercise("По разбегу"))
        assertTrue(RunUp.isExercise("по разбегу в шиповках"))
        assertFalse(RunUp.isExercise("Тройной с полного разбега"))
        assertFalse(RunUp.isExercise(null))
    }

    @Test
    fun length_followsMovesAfterMeasurement_inBothUnits() {
        val history = RunUpLength.build(
            listOf(RunUpMeasurement(date = 10, lengthCm = 3600.0, lengthFeet = 18.0)),
            listOf(
                RunUpMove(5, 100.0, 1.0, "до замера"),
                RunUpMove(20, 20.0, 0.0, "Тренировка"),
                RunUpMove(30, -5.0, -0.5, "Старт")
            )
        )
        assertEquals(3, history.size)
        assertEquals(3615.0, history.first().lengthCm!!, 1e-9)
        assertEquals(17.5, history.first().lengthFeet!!, 1e-9)
        assertEquals("Старт", history.first().source)
        assertEquals("Замер", history.last().source)
    }

    @Test
    fun length_newMeasurementReplacesOnlyGivenUnit() {
        val history = RunUpLength.build(
            listOf(
                RunUpMeasurement(date = 1, lengthCm = 3600.0, lengthFeet = 18.0),
                RunUpMeasurement(date = 2, lengthCm = 3700.0)
            ),
            emptyList()
        )
        assertEquals(3700.0, history.first().lengthCm!!, 1e-9)
        assertEquals(18.0, history.first().lengthFeet!!, 1e-9)
    }

    private fun shown(full: String, name: String) =
        titleWithColors(full, name, Color.Blue, Color.Red, Color.Green).text

    @Test
    fun title_spikesAreShownByColorOnly_evenWhenNameHasThem() {
        assertEquals("Бег 30 м", shown("Бег 30 м в шиповках", "Бег 30 м"))
        assertEquals("Бег 30 м", shown("Бег 30 м в в шиповках", "Бег 30 м в"))
        assertEquals("Бег 30 м", shown("Бег 30 м в шиповках в шиповках", "Бег 30 м в шиповках"))
        assertEquals("Бег 30 м", shown("Бег 30 м в шиповках", "Бег 30 м в шиповках"))
    }

    @Test
    fun title_inventoryAndLegStayAsWords_legacyDotsAreRemoved() {
        assertEquals("Бросок ядро 5 кг", shown("Бросок ядро 5 кг", "Бросок"))
        assertEquals("Махи левая нога", shown("Махи · левая нога", "Махи"))
    }
}
