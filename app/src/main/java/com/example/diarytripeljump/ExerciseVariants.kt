package com.example.diarytripeljump

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.RunUp

/*
 * Упражнение определяется не только названием, но и свойствами: нога, шиповки, снаряд.
 * «Бросок ядра назад 5 кг» и «Бросок ядра назад 7,26 кг» — разные упражнения со своими рекордами,
 * поэтому сравнение идёт по ключу варианта, а на экране свойства выделяются цветом (см. [styledTitle]).
 */

private val SpikesText = Regex("в шиповках", RegexOption.IGNORE_CASE)
private val LegText = Regex("поочерёдно на обеих ногах|на (левой|правой|одной) ноге|(левая|правая|одна) нога", RegexOption.IGNORE_CASE)
private val WordRegex = Regex("\\S+")
private const val START_MARK = "(старт)"

/**
 * Название с цветовым выделением свойств: шиповки — бирюзовым, снаряд — оранжевым, нога — синим.
 * Свойства могут стоять в любом месте названия, поэтому текст разбирается по словам:
 * всё, чего нет в исходном названии [name] и что не нога и не шиповки, относится к снаряду.
 * Подходит и для целей, сохранённых в старом формате через точку.
 */
fun titleWithColors(full: String, name: String, spikes: Color, inventory: Color, leg: Color): AnnotatedString {
    val clean = full.replace("·", " ").replace(Regex("\\s+"), " ").trim()
    val nameWords = name.lowercase().split(Regex("\\s+")).toMutableList()
    val spikesRanges = SpikesText.findAll(clean).map { it.range }.toList()
    val legRanges = LegText.findAll(clean).map { it.range }.toList()
    fun styleOf(color: Color) = SpanStyle(color = color, fontWeight = FontWeight.Medium)
    return buildAnnotatedString {
        append(clean)
        spikesRanges.forEach { addStyle(styleOf(spikes), it.first, it.last + 1) }
        legRanges.forEach { addStyle(styleOf(leg), it.first, it.last + 1) }
        WordRegex.findAll(clean).forEach { word ->
            val marked = (spikesRanges + legRanges).any { word.range.first in it }
            if (!marked && !nameWords.remove(word.value.lowercase()) && word.value != START_MARK) {
                addStyle(styleOf(inventory), word.range.first, word.range.last + 1)
            }
        }
    }
}

@Composable
fun styledTitle(full: String, name: String): AnnotatedString {
    val cs = MaterialTheme.colorScheme
    return remember(full, name, cs.tertiary, cs.secondary, cs.primary) {
        titleWithColors(full, name, spikes = cs.tertiary, inventory = cs.secondary, leg = cs.primary)
    }
}

@Composable
fun Exercise.styledTitle(): AnnotatedString = styledTitle(title(), name)

@Composable
fun ExerciseResultInput.styledTitle(): AnnotatedString = styledTitle(title(), exerciseName)

/** Полное название: «Бросок ядра назад 7,26 кг в шиповках». */
fun Exercise.title(): String = RecordsEngine.exerciseTitle(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, defaultUnit, titleLayout
)

fun Exercise.variantKey(): String = RecordsEngine.variantKey(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, defaultUnit
)

fun ExerciseResultInput.title(): String = RecordsEngine.exerciseTitle(
    exerciseName, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit, titleLayout
)

fun ExerciseResultInput.variantKey(): String = RecordsEngine.variantKey(
    exerciseName, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit
)

fun ExerciseConfig.title(): String = RecordsEngine.exerciseTitle(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit, titleLayout
)

fun ExerciseConfig.variantKey(): String = RecordsEngine.variantKey(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit
)

/** Вес снаряда равен результату (штанга в кг при результате в кг) — отдельно не вводится. */
val ExerciseConfig.isLoadedImplement: Boolean get() = RecordsEngine.isLoadedImplement(inventoryUnit, unit)

val ExerciseConfig.isRunUp: Boolean get() = RunUp.isExercise(name)

/** Выполнение упражнения из справочника в форме тренировки. */
fun Exercise.toInput(): ExerciseResultInput = ExerciseResultInput(
    exerciseId = id,
    exerciseName = name,
    result = 0.0,
    unit = defaultUnit,
    reps = 10,
    isSingleLeg = isSingleLeg,
    legSide = legSide,
    inventoryName = inventoryName,
    inventoryUnit = inventoryUnit,
    inventoryValue = if (RecordsEngine.isLoadedImplement(inventoryUnit, defaultUnit)) null else inventoryValue,
    inSpikes = inSpikes,
    runUp = if (RunUp.isExercise(name)) emptyList() else null,
    titleLayout = titleLayout
)

/** Новое выполнение по параметрам из конструктора; [exerciseId] = 0 — упражнение найдётся по варианту при сохранении. */
fun ExerciseConfig.toInput(exerciseId: Long = 0): ExerciseResultInput = ExerciseResultInput(
    exerciseId = exerciseId,
    exerciseName = name,
    result = 0.0,
    unit = unit,
    reps = 10,
    isSingleLeg = isSingleLeg,
    legSide = if (isSingleLeg) legSide else null,
    inventoryName = inventoryName,
    inventoryUnit = inventoryUnit,
    inventoryValue = if (isLoadedImplement) null else inventoryValue,
    inSpikes = inSpikes,
    runUp = if (isRunUp) emptyList() else null,
    titleLayout = titleLayout
)

/** Упражнение справочника как конфигурация конструктора. */
fun Exercise.toConfig(): ExerciseConfig = ExerciseConfig(
    name = name,
    category = categoryName ?: "Силовое упражнение",
    isSingleLeg = isSingleLeg,
    legSide = legSide,
    inventoryName = inventoryName,
    inventoryUnit = inventoryUnit,
    inventoryValue = inventoryValue,
    inSpikes = inSpikes,
    unit = defaultUnit,
    titleLayout = titleLayout
)

/** Параметры выполнения как конфигурация конструктора (для правки упражнения прямо в тренировке). */
fun ExerciseResultInput.toConfig(category: String): ExerciseConfig = ExerciseConfig(
    name = exerciseName,
    category = category,
    isSingleLeg = isSingleLeg,
    legSide = legSide,
    inventoryName = inventoryName,
    inventoryUnit = inventoryUnit,
    inventoryValue = inventoryValue,
    inSpikes = inSpikes,
    unit = unit,
    titleLayout = titleLayout
)

/** Выполнение с параметрами из конструктора; результаты попыток сохраняются. */
fun ExerciseResultInput.withConfig(cfg: ExerciseConfig, exerciseId: Long = this.exerciseId): ExerciseResultInput = copy(
    exerciseId = exerciseId,
    exerciseName = cfg.name,
    unit = cfg.unit,
    isSingleLeg = cfg.isSingleLeg,
    legSide = if (cfg.isSingleLeg) cfg.legSide else null,
    inventoryName = cfg.inventoryName,
    inventoryUnit = cfg.inventoryUnit,
    inventoryValue = if (cfg.isLoadedImplement) null else cfg.inventoryValue,
    inSpikes = cfg.inSpikes,
    runUp = if (cfg.isRunUp) runUp ?: emptyList() else null,
    titleLayout = cfg.titleLayout
)
