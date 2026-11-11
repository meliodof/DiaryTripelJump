package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test

class InventorySetTest {

    private val plate = InventoryUse("Блин", ResultUnit.KG, 10.0)
    private val hurdle = InventoryUse("Барьер", ResultUnit.CM, 76.0)
    private val rope = InventoryUse("Скакалка", null, null)

    @Test
    fun singleItem_isStoredAsBefore() {
        val f = InventorySet.encode(listOf(hurdle))
        assertEquals(InventoryFields("Барьер", ResultUnit.CM, 76.0), f)
        assertFalse(InventorySet.isPacked(f.name))
        assertEquals(listOf(hurdle), InventorySet.decode(f.name, f.unit, f.value))
    }

    @Test
    fun severalItems_roundTrip() {
        val items = listOf(plate, hurdle, rope)
        val f = InventorySet.encode(items)
        assertTrue(InventorySet.isPacked(f.name))
        // в единице и значении остаётся первый снаряд
        assertEquals(ResultUnit.KG, f.unit)
        assertEquals(10.0, f.value!!, 1e-9)
        assertEquals(items, InventorySet.decode(f.name, f.unit, f.value))
    }

    @Test
    fun empty_meansNoInventory() {
        assertEquals(InventoryFields(null, null, null), InventorySet.encode(emptyList()))
        assertTrue(InventorySet.decode(null, null, null).isEmpty())
        assertTrue(InventorySet.decode("  ", ResultUnit.CM, 1.0).isEmpty())
    }

    @Test
    fun rename_changesOnlyThatItem() {
        val f = InventorySet.encode(listOf(plate, hurdle))
        val renamed = InventorySet.rename(f, "барьер", ResultUnit.CM, "Барьер низкий", ResultUnit.CM)!!
        assertEquals(
            listOf(plate, InventoryUse("Барьер низкий", ResultUnit.CM, 76.0)),
            InventorySet.decode(renamed.name, renamed.unit, renamed.value)
        )
        // другая единица: 76 см не становятся 76 кг
        val unitChanged = InventorySet.rename(f, "Барьер", ResultUnit.CM, "Барьер", ResultUnit.KG)!!
        assertNull(InventorySet.decode(unitChanged.name, unitChanged.unit, unitChanged.value)[1].value)
        // снаряда нет в упражнении
        assertNull(InventorySet.rename(f, "Ядро", ResultUnit.KG, "Ядро", ResultUnit.KG))
    }

    @Test
    fun title_namesEveryItem() {
        val f = InventorySet.encode(listOf(plate, hurdle))
        val title = RecordsEngine.exerciseTitle("Прыжки", false, null, false, f.name, f.unit, f.value, ResultUnit.REPS)
        assertTrue(title, title.contains("10 кг"))
        assertTrue(title, title.contains("76 см"))
        assertTrue(title, title.contains(" и "))
        assertFalse(title, title.contains("\u001F"))
    }

    @Test
    fun differentSets_areDifferentRecords() {
        val one = InventorySet.encode(listOf(hurdle))
        val two = InventorySet.encode(listOf(hurdle, plate))
        val a = RecordsEngine.recordInventory(one.name, one.unit, one.value, ResultUnit.REPS)
        val b = RecordsEngine.recordInventory(two.name, two.unit, two.value, ResultUnit.REPS)
        assertEquals("Барьер 76 см", a)
        assertEquals("Барьер 76 см + Блин 10 кг", b)
        assertNotEquals(
            RecordsEngine.exerciseRecordKey("Прыжки", false, null, ResultUnit.REPS, inventory = a),
            RecordsEngine.exerciseRecordKey("Прыжки", false, null, ResultUnit.REPS, inventory = b)
        )
    }

    @Test
    fun loadedImplement_doesNotSplitRecord() {
        // штанга при результате в кг: её вес и есть результат, рекорд делит только барьер
        val bar = InventoryUse("Штанга", ResultUnit.KG, null)
        val f = InventorySet.encode(listOf(bar, hurdle))
        assertEquals("Барьер 76 см", RecordsEngine.recordInventory(f.name, f.unit, f.value, ResultUnit.KG))
        assertEquals("Штанга + Барьер 76 см", RecordsEngine.variantInventory(f.name, f.unit, f.value, ResultUnit.KG))
    }
}
