package com.example.diarytripeljump

import androidx.compose.ui.graphics.Color
import com.example.diarytripeljump.data.AgeGroups
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.RunUpAttempt
import com.example.diarytripeljump.data.RunUpLength
import com.example.diarytripeljump.data.RunUpMeasurement
import com.example.diarytripeljump.data.RunUpMove
import com.example.diarytripeljump.data.SetResults
import com.example.diarytripeljump.data.SportRanks
import com.example.diarytripeljump.data.TitleGrammar
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

    @Test
    fun setResults_detectsHowTheyWereEntered() {
        assertEquals(SetResults.ALL, SetResults.sameValueMode(List(15) { 100.0 }, List(3) { 5 }))
        assertEquals(SetResults.PER_SET, SetResults.sameValueMode(List(5) { 100.0 } + List(3) { 110.0 }, listOf(5, 3)))
        assertEquals(SetResults.EACH, SetResults.sameValueMode(listOf(100.0, 110.0, 120.0, 130.0), listOf(2, 2)))
        assertEquals(SetResults.EACH, SetResults.sameValueMode(listOf(100.0, 100.0), List(3) { 5 }))
        assertEquals(SetResults.EACH, SetResults.sameValueMode(listOf(0.0, 0.0), listOf(2)))
    }

    @Test
    fun setResults_sameBySet_marksEachSetOnItsOwn() {
        val results = listOf(100.0, 100.0, 100.0, 105.0, 110.0, 120.0)
        assertEquals(listOf(true, false, false), SetResults.sameBySet(results, listOf(3, 2, 1)))
        assertEquals(listOf(false, false), SetResults.sameBySet(listOf(0.0, 0.0, 5.0), listOf(2, 1)))
        assertEquals(listOf(false, false), SetResults.sameBySet(listOf(100.0), listOf(2, 2)))
    }

    @Test
    fun setResults_timedSetsHaveOneResultEach() {
        val text: (Double) -> String = { it.toInt().toString() }
        assertEquals("3 × 30 сек", SetResults.label(List(3) { 30 }, timed = true))
        assertEquals("30 + 20 сек", SetResults.label(listOf(30, 20), timed = true))
        assertEquals("2 × 30 сек по 100", SetResults.summary(listOf(100.0, 100.0), listOf(30, 30), text, timed = true))
        assertEquals("2 × 30 сек: 100, 110", SetResults.summary(listOf(100.0, 110.0), listOf(30, 30), text, timed = true))
    }

    @Test
    fun setResults_switchingTimedKeepsEnteredValues() {
        assertEquals(listOf(100.0, 110.0), SetResults.toPerSet(listOf(0.0, 100.0, 110.0, 120.0), listOf(2, 2)))
        assertEquals(listOf(100.0, 100.0, 110.0), SetResults.toPerRep(listOf(100.0, 110.0), listOf(2, 1)))
    }

    @Test
    fun setResults_linesDescribeEverySet() {
        val text: (Double) -> String = { "${it.toInt()} кг" }
        assertEquals(
            listOf("Подход 1: 2 × 80 кг", "Подход 2: 90 кг, 100 кг", "Подход 3: не заполнен"),
            SetResults.setLines(listOf(80.0, 80.0, 90.0, 100.0, 0.0), listOf(2, 2, 1), text)
        )
        assertEquals(listOf("Подход 1: 30 сек, 100 кг"), SetResults.setLines(listOf(100.0), listOf(30), text, timed = true))
        assertEquals(listOf("Подход 1: 5 раз"), SetResults.setLines(emptyList(), listOf(5), text))
    }

    @Test
    fun setResults_summaryShowsMixedSets() {
        val text: (Double) -> String = { it.toInt().toString() }
        assertEquals("3 × 100, 105 · 110", SetResults.summary(listOf(100.0, 100.0, 100.0, 105.0, 110.0), listOf(3, 2), text))
    }

    @Test
    fun setResults_summaryIsShort_withEqualAndDifferentReps() {
        val text: (Double) -> String = { it.toInt().toString() }
        assertEquals("3 × 2 по 100", SetResults.summary(List(6) { 100.0 }, List(3) { 2 }, text))
        assertEquals("2 × 2: 100, 110", SetResults.summary(listOf(100.0, 100.0, 110.0, 110.0), listOf(2, 2), text))
        assertEquals("3 + 1: 100, 110", SetResults.summary(listOf(100.0, 100.0, 100.0, 110.0), listOf(3, 1), text))
        assertEquals("100 · 110", SetResults.summary(listOf(100.0, 110.0), emptyList(), text))
    }

    @Test
    fun setResults_planComesFromStoredRepsOrFromCommonValue() {
        assertEquals(listOf(5, 5, 3), SetResults.repsBySet(3, 5, "5,5,3"))
        assertEquals(listOf(5, 5), SetResults.repsBySet(2, 5, null))
        assertTrue(SetResults.repsBySet(null, 5, null).isEmpty())
        assertEquals("5 + 5 + 3", SetResults.label(listOf(5, 5, 3)))
    }

    private fun title(
        name: String, leg: String? = null, spikes: Boolean = false, inventory: String? = null, value: String = "", layout: String? = null
    ) = TitleGrammar.compose(name, leg != null, leg, spikes, inventory, value, layout)

    @Test
    fun title_declinesInventoryAndAddsPrepositions() {
        assertEquals("Метание ядра", title("Метание", inventory = "Ядро"))
        assertEquals("Метание ядра 5 кг лицом вперёд", title("Метание лицом вперёд", inventory = "Ядро", value = "5 кг"))
        assertEquals("Присед со штангой", title("Присед", inventory = "Штанга"))
        assertEquals("Махи с утяжелителем", title("Махи", inventory = "Утяжелитель"))
        assertEquals("Прыжки через барьер 91 см", title("Прыжки", inventory = "Барьер", value = "91 см"))
        assertEquals("Прыжки на тумбу 60 см", title("Прыжки", inventory = "Тумба", value = "60 см"))
        assertEquals("Бег с набивным мячом", title("Бег", inventory = "Набивной мяч"))
        assertEquals("Выпады с гантелями", title("Выпады", inventory = "Гантели"))
    }

    @Test
    fun title_doesNotRepeatWhatNameAlreadySays() {
        assertEquals("Метание ядра лицом вперёд", title("Метание ядра лицом вперёд", inventory = "Ядро"))
        assertEquals("Через барьеры в шаге 91 см", title("Через барьеры в шаге", inventory = "Барьер", value = "91 см"))
        assertEquals("Махи левой ногой", title("Махи левой ногой", leg = "LEFT"))
        assertEquals("Махи левой ногой на правой ноге", title("Махи левой ногой", leg = "RIGHT"))
        assertEquals("Махи левой ногой поочерёдно на обеих ногах", title("Махи левой ногой", leg = TitleGrammar.ALTERNATE))
    }

    @Test
    fun title_spikesReadAsPhrase_evenWhenNameIsCut() {
        assertEquals("Тройной в шиповках", title("Тройной", spikes = true))
        assertEquals("Бег 30 м в шиповках", title("Бег 30 м в", spikes = true))
        assertEquals("Бег 30 м в шиповках", title("Бег 30 м в шиповках", spikes = true))
    }

    @Test
    fun title_legs_includingAlternate() {
        assertEquals("Прыжки на левой ноге", title("Прыжки", leg = "LEFT"))
        assertEquals("Прыжки поочерёдно на обеих ногах", title("Прыжки", leg = TitleGrammar.ALTERNATE))
    }

    @Test
    fun title_partsGoToChosenPosition() {
        assertEquals("В шиповках бег 30 м", title("Бег 30 м", spikes = true, layout = "AAS"))
        assertEquals("Бег в шиповках 30 м", title("Бег 30 м", spikes = true, layout = "AAM"))
        assertEquals("Бег 30 м в шиповках", title("Бег 30 м", spikes = true, layout = "AAE"))
        assertEquals("На левой ноге прыжки с утяжелителем", title("Прыжки", leg = "LEFT", inventory = "Утяжелитель", layout = "SEA"))
    }

    private fun shown(full: String, name: String) =
        titleWithColors(full, name, Color.Blue, Color.Red, Color.Green).text

    @Test
    fun title_inventoryAndLegStayAsWords_legacyDotsAreRemoved() {
        assertEquals("Бросок ядра 5 кг", shown("Бросок ядра 5 кг", "Бросок"))
        assertEquals("Махи левая нога", shown("Махи · левая нога", "Махи"))
    }
}
