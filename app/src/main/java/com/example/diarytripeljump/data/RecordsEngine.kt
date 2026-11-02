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
    val isManuallyEdited: Boolean = false,
    /** Рекорд в шиповках ведётся отдельно от результата в обычной обуви. */
    val inSpikes: Boolean = false
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

    /** Код ноги для ключа рекорда. */
    fun sideCode(isSingleLeg: Boolean, legSide: String?): String =
        if (isSingleLeg) (legSide ?: "ANY") else "BOTH"

    /** Суффикс ключа для шиповок; без шиповок ключ прежний, поэтому старые цели и правки рекордов не теряются. */
    private fun spikesCode(inSpikes: Boolean): String = if (inSpikes) "|spikes" else ""

    /** Ключ рекорда упражнения из тренировок; по нему же хранятся цели. */
    fun exerciseRecordKey(name: String, isSingleLeg: Boolean, legSide: String?, unit: ResultUnit, inSpikes: Boolean = false): String =
        "ex|${name.trim().lowercase()}|${sideCode(isSingleLeg, legSide)}|${unit.name}" + spikesCode(inSpikes)

    /** Ключ записи, добавленной вручную (старт, антирекорд или запись с инвентарём). */
    fun manualRecordKey(
        name: String, isAnti: Boolean, isCompetition: Boolean, unit: ResultUnit,
        isSingleLeg: Boolean, legSide: String?, inventory: String?, inSpikes: Boolean = false
    ): String =
        "tj|${name.trim().lowercase()}|$isAnti|$isCompetition|${unit.name}|${sideCode(isSingleLeg, legSide)}|${inventory?.lowercase() ?: ""}" +
            spikesCode(inSpikes)

    fun spikesSuffix(inSpikes: Boolean): String = if (inSpikes) " · в шиповках" else ""

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
            val inSpikes: Boolean,
            val samples: MutableList<Sample> = mutableListOf()
        )

        val groups = LinkedHashMap<String, Group>()

        // Упражнения из тренировок
        ex.forEach { res ->
            val name = res.exerciseName.trim()
            if (name.isBlank()) return@forEach
            val lower = isLowerBetter(name, res.resultUnit)
            val value = bestOfExercise(res, lower) ?: return@forEach
            val key = exerciseRecordKey(name, res.isSingleLeg, res.legSide, res.resultUnit, res.inSpikes)
            val g = groups.getOrPut(key) {
                Group(key, name, res.resultUnit, res.isSingleLeg, res.legSide, false, false, res.inSpikes)
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
            val inv = inventoryLabel(r.inventoryName, r.inventoryUnit, r.inventoryValue)
            // «тренировочные» записи без инвентаря сливаются с упражнениями; с инвентарём — отдельный рекорд
            val merged = !r.isCompetition && !r.isAntiRecord && inv == null
            val key = if (merged) exerciseRecordKey(name, r.isSingleLeg, r.legSide, r.resultUnit, r.inSpikes)
            else manualRecordKey(name, r.isAntiRecord, r.isCompetition, r.resultUnit, r.isSingleLeg, r.legSide, inv, r.inSpikes)
            val g = groups.getOrPut(key) {
                Group(key, name, r.resultUnit, r.isSingleLeg, r.legSide, r.isAntiRecord, r.isCompetition, r.inSpikes)
            }
            g.samples.add(
                Sample(
                    date = r.date, value = r.result, tjId = r.id, isIndoor = r.isIndoor,
                    weather = r.weatherConditions, inventory = inv, level = r.recordLevel,
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
                displayName = g.name + legSuffix(g.isSingleLeg, g.legSide) + spikesSuffix(g.inSpikes),
                inSpikes = g.inSpikes,
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

/** Прогресс по цели относительно текущего рекорда. */
data class GoalProgress(
    val goal: ExerciseGoal,
    val current: Double?,
    /** 0..1 */
    val fraction: Float,
    val achieved: Boolean,
    /** Сколько осталось до цели (в единицах упражнения), null если цель достигнута или нет результатов. */
    val remaining: Double?
)

fun computeGoalProgress(goal: ExerciseGoal, current: Double?): GoalProgress {
    if (current == null || current <= 0.0) return GoalProgress(goal, null, 0f, false, null)
    val achieved = if (goal.lowerIsBetter) current <= goal.targetValue else current >= goal.targetValue
    val fraction = when {
        achieved -> 1f
        goal.lowerIsBetter -> (goal.targetValue / current).toFloat()
        else -> (current / goal.targetValue).toFloat()
    }.coerceIn(0f, 1f)
    val remaining = if (achieved) null else Math.abs(goal.targetValue - current)
    return GoalProgress(goal, current, fraction, achieved, remaining)
}

/** Значение без знака для остатка до цели: «0.26 м», «5 кг». */
fun formatGoalRemaining(value: Double, unit: ResultUnit): String = formatRecordValue(value, unit)
