package com.example.diarytripeljump.data

import java.util.Locale

/** Одна точка истории рекорда: момент, когда рекорд был установлен или улучшен. */
data class RecordHistoryEntry(
    val date: Long,
    val value: Double,
    /** Предыдущий рекорд; null — это первый результат в данном упражнении. */
    val previousValue: Double?,
    val isCompetition: Boolean,
    val inventoryLabel: String? = null
)

/** Текущий рекорд по упражнению/виду вместе с полной историей его обновлений. */
data class RecordSummary(
    val key: String,
    val name: String,
    val displayName: String,
    val unit: ResultUnit,
    val isSingleLeg: Boolean,
    val legSide: String?,
    val isAnti: Boolean,
    val isCompetition: Boolean,
    val category: String,
    val isIndoor: Boolean,
    val weather: String?,
    val inventoryLabel: String?,
    val levelLabel: String,
    val best: Double,
    val bestDate: Long,
    val lowerIsBetter: Boolean,
    /** id строки triple_jump_results, которую можно удалить; null — рекорд берётся из тренировки. */
    val deletableResultId: Long?,
    val attempts: Int,
    val history: List<RecordHistoryEntry>,
    val notes: String? = null,
    /** Значение, вычисленное автоматически (до ручной правки). */
    val autoBest: Double = best,
    val isManuallyEdited: Boolean = false
)

/** Событие «рекорд обновлён» для ленты на главном экране. */
data class RecordEvent(
    val key: String,
    val displayName: String,
    val unit: ResultUnit,
    val lowerIsBetter: Boolean,
    val value: Double,
    val previousValue: Double?,
    val date: Long,
    val isCompetition: Boolean,
    val isAnti: Boolean
)

/**
 * Единая логика рекордов. Рекорды не хранятся отдельно, а вычисляются из результатов:
 *  - упражнения из тренировок (учитываются нога и единица измерения);
 *  - записи, добавленные вручную на вкладке «Рекорды» (старты и антирекорды).
 * Поэтому при добавлении, изменении или удалении тренировки рекорды и их история
 * всегда соответствуют данным, а новый результат автоматически создаёт или обновляет рекорд.
 */
object RecordsEngine {

    private data class Sample(
        val date: Long,
        val value: Double,
        val tjId: Long?,
        val isIndoor: Boolean,
        val weather: String?,
        val inventory: String?,
        val level: String?,
        val category: String,
        val isCompetition: Boolean,
        val notes: String? = null
    )

    /** Для бега на время лучший результат — меньший. Для остальных единиц — больший. */
    fun isLowerBetter(name: String, unit: ResultUnit): Boolean {
        if (unit != ResultUnit.SECONDS) return false
        val n = name.lowercase()
        return n.contains("бег") || n.contains("спринт") || n.contains("ускор") ||
            Regex("\\d+\\s*м").containsMatchIn(n)
    }

    fun inferCategory(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("бег") || n.contains("30 м") || n.contains("спринт") -> "Беговые"
            n.contains("присед") || n.contains("штанг") || n.contains("жим") || n.contains("тяга") -> "Силовые"
            else -> "Прыжковые"
        }
    }

    fun legSuffix(isSingleLeg: Boolean, legSide: String?): String = when {
        !isSingleLeg -> ""
        legSide == "LEFT" -> " · левая нога"
        legSide == "RIGHT" -> " · правая нога"
        else -> " · одна нога"
    }

    fun inventoryLabel(name: String?, unit: ResultUnit?, value: Double?): String? {
        if (name.isNullOrBlank()) return null
        val unitText = when (unit) {
            ResultUnit.CM -> "см"
            ResultUnit.KG -> "кг"
            else -> ""
        }
        return if (value != null && value > 0) "$name ${formatNumber(value)} $unitText".trim() else name
    }

    /** Лучшее значение внутри одного упражнения: среди результатов подходов и итогового значения. */
    private fun bestOfExercise(res: ExerciseResult, lowerBetter: Boolean): Double? {
        val candidates = mutableListOf<Double>()
        res.setResultsJson?.split(",")?.mapNotNullTo(candidates) { it.trim().toDoubleOrNull()?.takeIf { v -> v > 0 } }
        if (res.result > 0) candidates.add(res.result)
        if (candidates.isEmpty()) return null
        return if (lowerBetter) candidates.min() else candidates.max()
    }

    fun build(
        tj: List<TripleJumpResult>,
        ex: List<ExerciseResult>,
        overrides: List<RecordOverride> = emptyList()
    ): List<RecordSummary> {
        data class Group(
            val key: String,
            val name: String,
            val unit: ResultUnit,
            val isSingleLeg: Boolean,
            val legSide: String?,
            val isAnti: Boolean,
            val isCompetition: Boolean,
            val samples: MutableList<Sample> = mutableListOf()
        )

        val groups = LinkedHashMap<String, Group>()

        // Упражнения из тренировок
        ex.forEach { res ->
            val name = res.exerciseName.trim()
            if (name.isBlank()) return@forEach
            val lower = isLowerBetter(name, res.resultUnit)
            val value = bestOfExercise(res, lower) ?: return@forEach
            val side = if (res.isSingleLeg) (res.legSide ?: "ANY") else "BOTH"
            val key = "ex|${name.lowercase()}|$side|${res.resultUnit.name}"
            val g = groups.getOrPut(key) {
                Group(key, name, res.resultUnit, res.isSingleLeg, res.legSide, false, false)
            }
            g.samples.add(
                Sample(
                    date = res.date, value = value, tjId = null, isIndoor = false, weather = null,
                    inventory = inventoryLabel(res.inventoryName, res.inventoryUnit, res.inventoryValue),
                    level = null, category = inferCategory(name), isCompetition = false
                )
            )
        }

        // Записи, добавленные вручную
        tj.forEach { r ->
            if (r.result <= 0) return@forEach
            val name = r.eventName.trim()
            val merged = !r.isCompetition && !r.isAntiRecord // «тренировочные» записи сливаются с упражнениями
            val key = if (merged) "ex|${name.lowercase()}|BOTH|${r.resultUnit.name}"
            else "tj|${name.lowercase()}|${r.isAntiRecord}|${r.isCompetition}|${r.resultUnit.name}"
            val g = groups.getOrPut(key) {
                Group(key, name, r.resultUnit, false, null, r.isAntiRecord, r.isCompetition)
            }
            g.samples.add(
                Sample(
                    date = r.date, value = r.result, tjId = r.id, isIndoor = r.isIndoor,
                    weather = r.weatherConditions, inventory = null, level = r.recordLevel,
                    category = r.exerciseCategory, isCompetition = r.isCompetition, notes = r.notes
                )
            )
        }

        return groups.values.mapNotNull { g ->
            if (g.samples.isEmpty()) return@mapNotNull null
            // у антирекорда «лучшим» считается худший результат
            val lowerBetter = isLowerBetter(g.name, g.unit) xor g.isAnti
            val sorted = g.samples.sortedWith(compareBy<Sample> { it.date }.thenBy { if (lowerBetter) it.value else -it.value })

            val chain = mutableListOf<Pair<Sample, Double?>>()
            var running: Double? = null
            var bestSample: Sample? = null
            sorted.forEach { s ->
                val better = running == null || (if (lowerBetter) s.value < running!! else s.value > running!!)
                if (better) {
                    chain.add(s to running)
                    running = s.value
                    bestSample = s
                }
            }
            val best = bestSample!!
            val base = RecordSummary(
                key = g.key,
                name = g.name,
                displayName = g.name + legSuffix(g.isSingleLeg, g.legSide),
                unit = g.unit,
                isSingleLeg = g.isSingleLeg,
                legSide = g.legSide,
                isAnti = g.isAnti,
                isCompetition = g.isCompetition,
                category = best.category,
                isIndoor = best.isIndoor,
                weather = best.weather,
                inventoryLabel = best.inventory,
                levelLabel = best.level ?: "PB",
                best = best.value,
                bestDate = best.date,
                lowerIsBetter = lowerBetter,
                deletableResultId = best.tjId,
                attempts = g.samples.size,
                history = chain.map { (s, prev) ->
                    RecordHistoryEntry(s.date, s.value, prev, s.isCompetition || g.isCompetition, s.inventory)
                },
                notes = best.notes
            )

            // Ручная правка рекорда из тренировок действует, пока рекорд не обновился новым результатом
            val override = overrides.find { it.recordKey == base.key }
            if (override != null && base.deletableResultId == null && Math.abs(override.baseBest - base.best) < 1e-9) {
                val fixedHistory = if (base.history.isEmpty()) base.history else
                    base.history.dropLast(1) + base.history.last().copy(value = override.value, date = override.date)
                base.copy(
                    best = override.value,
                    bestDate = override.date,
                    isIndoor = override.isIndoor,
                    weather = override.weather,
                    notes = override.notes,
                    history = fixedHistory,
                    isManuallyEdited = true
                )
            } else base
        }.sortedByDescending { it.bestDate }
    }

    /** Все обновления рекордов, новые сверху. */
    fun events(records: List<RecordSummary>): List<RecordEvent> =
        records.flatMap { r ->
            r.history.map { h ->
                RecordEvent(
                    key = r.key, displayName = r.displayName, unit = r.unit, lowerIsBetter = r.lowerIsBetter,
                    value = h.value, previousValue = h.previousValue, date = h.date,
                    isCompetition = h.isCompetition, isAnti = r.isAnti
                )
            }
        }.sortedByDescending { it.date }
}

fun formatNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString()
    else String.format(Locale.getDefault(), "%.1f", value)

fun formatRecordValue(value: Double, unit: ResultUnit): String = when (unit) {
    ResultUnit.KG -> "${formatNumber(value)} кг"
    ResultUnit.CM -> "${formatNumber(value)} см"
    ResultUnit.M -> String.format(Locale.getDefault(), "%.2f м", value)
    ResultUnit.REPS -> "${value.toInt()} раз"
    ResultUnit.SECONDS -> String.format(Locale.getDefault(), "%.2f сек", value)
}

/** Разница с прошлым рекордом: «+5 кг», «−0.12 сек». */
fun formatRecordDelta(value: Double, previous: Double, unit: ResultUnit): String {
    val diff = value - previous
    val sign = if (diff >= 0) "+" else "−"
    val abs = Math.abs(diff)
    val body = when (unit) {
        ResultUnit.M -> String.format(Locale.getDefault(), "%.2f м", abs)
        ResultUnit.SECONDS -> String.format(Locale.getDefault(), "%.2f сек", abs)
        ResultUnit.KG -> "${formatNumber(abs)} кг"
        ResultUnit.CM -> "${formatNumber(abs)} см"
        ResultUnit.REPS -> "${abs.toInt()} раз"
    }
    return sign + body
}
