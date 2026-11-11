package com.example.diarytripeljump.data

/** Один снаряд в упражнении: название, единица (см, кг или без единицы) и вес или размер. */
data class InventoryUse(val name: String, val unit: ResultUnit?, val value: Double?)

/** Снаряды упражнения в трёх полях базы: inventoryName, inventoryUnit, inventoryValue. */
data class InventoryFields(val name: String?, val unit: ResultUnit?, val value: Double?)

/**
 * Несколько снарядов в одном упражнении (например, блин 10 кг и барьер 76 см).
 *
 * В базе у упражнения, результата и рекорда по-прежнему три поля снаряда. Один снаряд хранится в них
 * как раньше. Несколько — списком в поле названия через служебные разделители, а в единице и значении
 * остаётся первый снаряд. Так схема базы и резервные копии не меняются, старые записи читаются без
 * изменений, а список переносится везде, где копируются эти поля (тренировка → рекорд → правка рекорда).
 * Разбирать поля нужно только через [decode].
 */
object InventorySet {
    private const val ITEM = "\u001F"
    private const val FIELD = "\u001E"

    fun isPacked(name: String?): Boolean = name?.contains(ITEM) == true

    fun decode(name: String?, unit: ResultUnit?, value: Double?): List<InventoryUse> {
        if (name.isNullOrBlank()) return emptyList()
        if (!isPacked(name)) return listOf(InventoryUse(name.trim(), unit, value))
        return name.split(ITEM).mapNotNull { part ->
            val fields = part.split(FIELD)
            val itemName = fields.getOrNull(0)?.trim().orEmpty()
            if (itemName.isEmpty()) return@mapNotNull null
            InventoryUse(
                name = itemName,
                unit = fields.getOrNull(1)?.let { u -> ResultUnit.entries.find { it.name == u } },
                value = fields.getOrNull(2)?.toDoubleOrNull()
            )
        }
    }

    fun encode(items: List<InventoryUse>): InventoryFields {
        val clean = items.filter { it.name.isNotBlank() }
        return when (clean.size) {
            0 -> InventoryFields(null, null, null)
            1 -> clean[0].let { InventoryFields(it.name.trim(), it.unit, it.value) }
            else -> InventoryFields(
                clean.joinToString(ITEM) { "${it.name.trim()}$FIELD${it.unit?.name.orEmpty()}$FIELD${it.value ?: ""}" },
                clean[0].unit,
                clean[0].value
            )
        }
    }

    /**
     * Переименование снаряда из списка инвентаря в полях упражнения; null — этого снаряда в упражнении нет.
     * Если сменилась единица, значение сбрасывается: 91 см не становятся 91 кг.
     */
    fun rename(fields: InventoryFields, oldName: String, oldUnit: ResultUnit?, newName: String, newUnit: ResultUnit?): InventoryFields? {
        val items = decode(fields.name, fields.unit, fields.value)
        if (items.none { it.name.equals(oldName, ignoreCase = true) && it.unit == oldUnit }) return null
        return encode(items.map {
            if (it.name.equals(oldName, ignoreCase = true) && it.unit == oldUnit) {
                InventoryUse(newName, newUnit, if (newUnit == oldUnit) it.value else null)
            } else it
        })
    }
}
