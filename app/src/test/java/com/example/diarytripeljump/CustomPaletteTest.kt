package com.example.diarytripeljump

import androidx.compose.ui.graphics.Color
import com.example.diarytripeljump.data.CustomPalette
import com.example.diarytripeljump.ui.theme.contrast
import com.example.diarytripeljump.ui.theme.readableOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPaletteTest {

    @Test
    fun palette_survivesSaving() {
        val palette = CustomPalette(-1, -16777216, 123456)
        assertEquals(palette, CustomPalette.decode(palette.encode()))
    }

    @Test
    fun brokenSavedValue_isIgnored() {
        assertNull(CustomPalette.decode(null))
        assertNull(CustomPalette.decode("1,2"))
        assertNull(CustomPalette.decode("a,b,c"))
    }

    @Test
    fun paleColor_becomesReadable() {
        assertTrue(contrast(readableOn(Color(0xFFFFF59D), Color.White), Color.White) >= 3f)
        assertTrue(contrast(readableOn(Color(0xFF1A237E), Color(0xFF171922)), Color(0xFF171922)) >= 3f)
    }

    @Test
    fun readableColor_staysAsChosen() {
        assertEquals(Color(0xFF2A4DB0), readableOn(Color(0xFF2A4DB0), Color.White))
    }
}
