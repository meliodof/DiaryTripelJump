package com.example.diarytripeljump

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.RunUp

/*
 * Упражнение определяется не только названием, но и свойствами: нога, шиповки, снаряд.
 * «Бросок ядра назад ядро 5 кг» и «Бросок ядра назад ядро 7,26 кг» — разные упражнения со своими рекордами,
 * поэтому сравнение идёт по ключу варианта, а на экране свойства выделяются цветом (см. [styledTitle]).
 */

private val LegRegex = Regex("(левая|правая|одна) нога")
private const val SPIKES_WORDS = "в шиповках"
private const val START_MARK = "(старт)"
/** Слова про шиповки в самом названии: «Бег 30 м в шиповках» и обрезанное «Бег 30 м в». */
private val NameSpikesRegex = Regex("\\s+в\\s+шип\\S*$", RegexOption.IGNORE_CASE)
private val NameSpikesTailRegex = Regex("\\s+в(\\s+шип\\S*)?$", RegexOption.IGNORE_CASE)

/**
 * Название упражнения с цветовым выделением вместо перечисления через точку:
 * шиповки — название синим, снаряд — оранжевым, нога — зелёным.
 * Разбирает полное название [full], поэтому подходит и для целей, сохранённых в старом формате через точку.
 */
fun titleWithColors(full: String, name: String, spikes: Color, inventory: Color, leg: Color): AnnotatedString {
    val base = name.trim()
    val clean = full.replace("·", " ").trim()
    if (base.isEmpty() || !clean.startsWith(base, ignoreCase = true)) return AnnotatedString(clean)
    var rest = clean.substring(base.length)
    val inSpikes = rest.contains(SPIKES_WORDS) || NameSpikesRegex.containsMatchIn(base)
    rest = rest.replace(SPIKES_WORDS, " ")
    // шиповки показаны цветом, поэтому хвост про них в названии не нужен
    val shownName = if (inSpikes) base.replace(NameSpikesTailRegex, "") else base
    val isStart = rest.contains(START_MARK)
    rest = rest.replace(START_MARK, " ")
    val legText = LegRegex.find(rest)?.value
    if (legText != null) rest = rest.replace(legText, " ")
    val inventoryText = rest.trim().replace(Regex("\\s+"), " ")
    return buildAnnotatedString {
        if (inSpikes) withStyle(SpanStyle(color = spikes)) { append(shownName) } else append(shownName)
        if (legText != null) {
            append(" ")
            withStyle(SpanStyle(color = leg, fontWeight = FontWeight.Medium)) { append(legText) }
        }
        if (inventoryText.isNotEmpty()) {
            append(" ")
            withStyle(SpanStyle(color = inventory, fontWeight = FontWeight.Medium)) { append(inventoryText) }
        }
        if (isStart) append(" $START_MARK")
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

/** Полное название: «Бросок ядра назад ядро 7,26 кг в шиповках». */
fun Exercise.title(): String = RecordsEngine.exerciseTitle(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, defaultUnit
)

fun Exercise.variantKey(): String = RecordsEngine.variantKey(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, defaultUnit
)

fun ExerciseResultInput.title(): String = RecordsEngine.exerciseTitle(
    exerciseName, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit
)

fun ExerciseResultInput.variantKey(): String = RecordsEngine.variantKey(
    exerciseName, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit
)

fun ExerciseConfig.title(): String = RecordsEngine.exerciseTitle(
    name, isSingleLeg, legSide, inSpikes, inventoryName, inventoryUnit, inventoryValue, unit
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
    runUp = if (RunUp.isExercise(name)) emptyList() else null
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
    runUp = if (isRunUp) emptyList() else null
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
    unit = unit
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
    runUp = if (cfg.isRunUp) runUp ?: emptyList() else null
)
