package com.example.diarytripeljump

import com.example.diarytripeljump.data.TrainingSession
import com.example.diarytripeljump.data.TrainingStartTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Привычное время начала тренировки: берётся из уже записанных тренировок. */
class TrainingStartTimeTest {

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute)
    }.timeInMillis

    private var nextId = 1L
    private fun session(day: Int, hour: Int, minute: Int, skipped: Boolean = false) =
        TrainingSession(id = nextId++, date = at(day, hour, minute), isSkipped = skipped)

    @Test
    fun noTrainings_noSuggestion() {
        assertTrue(TrainingStartTime.suggest(emptyList(), at(18, 0, 0)).isEmpty())
    }

    @Test
    fun sameWeekdayComesFirst_thenMostFrequent() {
        // 2, 9 и 16 ноября 2026 — понедельники, 3, 4, 5 — вторник, среда, четверг
        val sessions = listOf(
            session(2, 18, 0), session(9, 18, 2), session(16, 17, 58),   // по понедельникам в 18:00
            session(3, 10, 30), session(4, 10, 30), session(5, 10, 31)   // в остальные дни в 10:30
        )
        assertEquals(listOf(18 * 60, 10 * 60 + 30), TrainingStartTime.suggest(sessions, at(23, 12, 0)))
        // во вторник понедельничное время уступает
        assertEquals(10 * 60 + 30, TrainingStartTime.suggest(sessions, at(24, 12, 0)).first())
    }

    @Test
    fun skippedDaysAreIgnored_andLabelIsClock() {
        val sessions = listOf(session(2, 7, 0, skipped = true), session(9, 16, 45))
        assertEquals(listOf(16 * 60 + 45), TrainingStartTime.suggest(sessions, at(23, 12, 0)))
        assertEquals("16:45", TrainingStartTime.label(16 * 60 + 45))
        assertEquals("9:05", TrainingStartTime.label(9 * 60 + 5))
    }
}
