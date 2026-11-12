package com.example.diarytripeljump

import com.example.diarytripeljump.data.formatTrainingDuration
import com.example.diarytripeljump.data.trainingTimeRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TrainingTimeTest {

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 12, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun duration_inWords() {
        assertEquals("45 мин", formatTrainingDuration(45))
        assertEquals("2 ч", formatTrainingDuration(120))
        assertEquals("1 ч 30 мин", formatTrainingDuration(90))
    }

    @Test
    fun range_withEnd() {
        assertEquals("10:00–11:30 (1 ч 30 мин)", trainingTimeRange(at(10, 0), 90))
    }

    @Test
    fun range_withoutEnd_showsStartOnly() {
        assertEquals("18:15", trainingTimeRange(at(18, 15), null))
        // пропуски хранили длительность 0 — это тоже «не указано»
        assertEquals("18:15", trainingTimeRange(at(18, 15), 0))
    }
}
