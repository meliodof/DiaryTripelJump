package com.example.diarytripeljump

import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.RecordsEngine

/*
 * Упражнение определяется не только названием, но и свойствами: нога, шиповки, снаряд.
 * «Бросок ядра назад · ядро 5 кг» и «Бросок ядра назад · ядро 7,26 кг» — разные упражнения со своими рекордами,
 * поэтому в списках и формах показывается полное название, а сравнение идёт по ключу варианта.
 */

/** Полное название: «Бросок ядра назад · ядро 7,26 кг · в шиповках». */
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
    inSpikes = inSpikes
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
    inSpikes = inSpikes
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
    inSpikes = cfg.inSpikes
)
