package com.example.diarytripeljump

import com.example.diarytripeljump.data.TitleGrammar
import org.junit.Assert.*
import org.junit.Test

/** Название продолжается после свойств: «Прыжки» + в шиповках + «с разбега». */
class TitleContinuationTest {

    @Test
    fun propertiesGoAfterGivenWords() {
        val layout = "${TitleGrammar.AUTO}${TitleGrammar.AUTO}${TitleGrammar.afterWords(1)}"
        assertEquals(
            "Прыжки в шиповках с разбега",
            TitleGrammar.compose("Прыжки с разбега", false, null, true, null, "", layout)
        )
    }

    @Test
    fun legAndSpikesKeepOrderInOnePlace() {
        val split = TitleGrammar.afterWords(2)
        assertEquals(
            "Прыжки вверх на левой ноге в шиповках с места",
            TitleGrammar.compose("Прыжки вверх с места", true, "LEFT", true, null, "", "$split${TitleGrammar.AUTO}$split")
        )
    }

    @Test
    fun splitIsReadBack() {
        assertEquals(2, TitleGrammar.continuationSplit("2A2"))
        assertNull(TitleGrammar.continuationSplit("AAE"))
        assertNull(TitleGrammar.continuationSplit(null))
    }

    @Test
    fun oldLayoutsUnchanged() {
        assertEquals("Прыжки в шиповках", TitleGrammar.compose("Прыжки", false, null, true, null, "", null))
        assertEquals(
            "В шиповках прыжки",
            TitleGrammar.compose("Прыжки", false, null, true, null, "", "AA${TitleGrammar.START}")
        )
    }
}
