package com.example.diarytripeljump

import com.example.diarytripeljump.data.EntryRules
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.ResultUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class EntryRulesTest {

    @Test
    fun cleanNumber_keepsOneDotAndTreatsCommaAsDot() {
        assertEquals("15.32", EntryRules.cleanNumber("15,32"))
        assertEquals("1.23", EntryRules.cleanNumber("1.2.3"))
        assertEquals("12", EntryRules.cleanNumber("1a2"))
    }

    @Test
    fun result_mustBePositiveAndWithinLimit() {
        assertNull(EntryRules.resultError(15.32, ResultUnit.M))
        assertNull(EntryRules.resultError(1_000.0, ResultUnit.KG))
        assertNotNull(EntryRules.resultError(0.0, ResultUnit.M))
        assertNotNull(EntryRules.resultError(null as Double?, ResultUnit.M))
        assertNotNull(EntryRules.resultError(1_000.5, ResultUnit.KG))
        assertNotNull(EntryRules.resultError("абв", ResultUnit.M))
    }

    @Test
    fun inventoryValue_isOptionalButChecked() {
        assertNull(EntryRules.inventoryValueError("", ResultUnit.KG))
        assertNull(EntryRules.inventoryValueError("7.26", ResultUnit.KG))
        assertNotNull(EntryRules.inventoryValueError("0", ResultUnit.CM))
        assertNotNull(EntryRules.inventoryValueError("6000", null))
    }

    @Test
    fun futureDate_isForbiddenOnlyForResults() {
        val now = Calendar.getInstance().apply { clear(); set(2026, 5, 10, 12, 0) }.timeInMillis
        val laterToday = Calendar.getInstance().apply { clear(); set(2026, 5, 10, 23, 0) }.timeInMillis
        val tomorrow = Calendar.getInstance().apply { clear(); set(2026, 5, 11, 0, 0) }.timeInMillis
        assertNull(EntryRules.dateError(laterToday, hasResult = true, now = now))
        assertNotNull(EntryRules.dateError(tomorrow, hasResult = true, now = now))
        assertNull(EntryRules.dateError(tomorrow, hasResult = false, now = now))
    }

    @Test
    fun runUp_hasNoRecord() {
        assertNotNull(EntryRules.recordExerciseError("По разбегу"))
        assertNull(EntryRules.recordExerciseError("Тройной прыжок"))
    }

    @Test
    fun inventoryName_mustBeFilledAndUnique() {
        val barrier = InventoryItem(id = 1, name = "Барьер", unit = ResultUnit.CM)
        val items = listOf(barrier, InventoryItem(id = 2, name = "Ядро", unit = ResultUnit.KG))
        assertNotNull(EntryRules.inventoryNameError("  ", ResultUnit.CM, items))
        assertNotNull(EntryRules.inventoryNameError("барьер", ResultUnit.CM, items))
        assertNull(EntryRules.inventoryNameError("Барьер", ResultUnit.KG, items))
        assertNull(EntryRules.inventoryNameError("барьер", ResultUnit.CM, items, editing = barrier))
        assertNotNull(EntryRules.inventoryNameError("Ядро", ResultUnit.KG, items, editing = barrier))
        // инвентарь без единицы — отдельная запись
        assertNull(EntryRules.inventoryNameError("Барьер", null, items))
        assertNotNull(EntryRules.inventoryNameError("скакалка", null, items + InventoryItem(id = 3, name = "Скакалка")))
    }
}
