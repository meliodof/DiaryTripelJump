package com.example.diarytripeljump.data

import java.util.Calendar

/**
 * Единые проверки ввода: одни и те же правила действуют в тренировках, рекордах и конструкторе упражнения.
 * Каждая проверка возвращает текст ошибки или null, если значение подходит.
 */
object EntryRules {

    /** Оставляет цифры и одну точку; запятая считается точкой. */
    fun cleanNumber(text: String): String {
        val digits = text.replace(',', '.').filter { it.isDigit() || it == '.' }
        val dot = digits.indexOf('.')
        return if (dot < 0) digits else digits.substring(0, dot + 1) + digits.substring(dot + 1).replace(".", "")
    }

    /** Верхняя граница значения: защита от опечатки, а не спортивный норматив. */
    fun maxValue(unit: ResultUnit): Double = when (unit) {
        ResultUnit.M -> 1_000.0
        ResultUnit.CM -> 5_000.0
        ResultUnit.SECONDS -> 36_000.0
        ResultUnit.KG -> 1_000.0
        ResultUnit.REPS -> 10_000.0
    }

    private fun unitText(unit: ResultUnit): String = when (unit) {
        ResultUnit.M -> "м"
        ResultUnit.CM -> "см"
        ResultUnit.SECONDS -> "сек"
        ResultUnit.KG -> "кг"
        ResultUnit.REPS -> "раз"
    }

    fun resultError(value: Double?, unit: ResultUnit): String? = when {
        value == null || value <= 0.0 -> "Введите число больше нуля"
        value > maxValue(unit) -> "Значение не может быть больше ${formatNumber(maxValue(unit))} ${unitText(unit)}"
        else -> null
    }

    fun resultError(text: String, unit: ResultUnit): String? = resultError(text.toDoubleOrNull(), unit)

    /** Вес или размер снаряда необязателен, но введённое значение должно быть верным. */
    fun inventoryValueError(text: String, unit: ResultUnit?): String? =
        if (text.isBlank()) null else resultError(text, unit ?: ResultUnit.CM)

    /** Результат нельзя показать в будущем; тренировку без результатов можно запланировать. */
    fun dateError(date: Long, hasResult: Boolean, now: Long = System.currentTimeMillis()): String? {
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return if (hasResult && date >= tomorrow) "Дата результата не может быть в будущем" else null
    }

    /** Для разбега ведутся попытки и длина разбега, а не рекорд. */
    fun recordExerciseError(name: String): String? =
        if (RunUp.isExercise(name)) "Для разбега рекорд не ведётся, выберите другое упражнение" else null

    fun inventoryNameError(name: String, unit: ResultUnit?, existing: List<InventoryItem>, editing: InventoryItem? = null): String? {
        val clean = name.trim()
        return when {
            clean.isBlank() -> "Введите название"
            existing.any { it.id != editing?.id && it.unit == unit && it.name.trim().equals(clean, ignoreCase = true) } ->
                "Такой инвентарь уже есть"
            else -> null
        }
    }
}
