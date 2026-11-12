package com.example.diarytripeljump.data

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Сигналы демо-дневника за год, заданные так, чтобы на них можно было проверить аналитические модели ([Insights]):
 *  - тройной прыжок растёт за сезон (≈ +0,85 м) — прогноз с трендом;
 *  - колебания приседа (волна ≈ 5 недель) повторяются в тройном через 3 недели — сильная связь;
 *  - колебания бега 30 м (волна ≈ 7 недель) повторяются в тройном через 2 недели — связь слабее;
 *  - остальные упражнения — тренд и независимый шум, связей по ним модели найти не должны.
 * Номер недели [k] — от начала демо-года (0) до сегодняшней недели ([WEEKS]). Значения детерминированы.
 */
object DemoSignals {
    const val WEEKS = 52

    /** Сдвиг, с которым присед и бег отражаются в тройном (в неделях дневника). */
    const val SQUAT_LAG = 3
    const val SPRINT_LAG = 2

    private val rnd = Random(7)
    private fun gauss(sd: Double) = rnd.nextGaussian() * sd

    /** Отклонение приседа от тренда, кг. */
    val squatWave: DoubleArray = DoubleArray(WEEKS + 1) { k -> 6.0 * sin(2 * PI * k / 5) + gauss(1.5) }

    /** Отклонение бега 30 м от тренда, с (плюс — медленнее). */
    val sprintWave: DoubleArray = DoubleArray(WEEKS + 1) { k -> 0.035 * sin(2 * PI * k / 7 + 1.0) + gauss(0.01) }

    private val jumpNoise: DoubleArray = DoubleArray(WEEKS + 1) { gauss(0.03) }

    fun squat(k: Int): Double = 120.0 + 40.0 * k / WEEKS + squatWave[k.coerceIn(0, WEEKS)]

    fun sprint30(k: Int): Double = 4.05 - 0.20 * k / WEEKS + sprintWave[k.coerceIn(0, WEEKS)]

    /** Тройной с полного разбега на тренировке: тренд + отражение приседа и бега + шум. */
    fun jump(k: Int): Double {
        val kk = k.coerceIn(0, WEEKS)
        val squatPart = if (kk >= SQUAT_LAG) 0.012 * squatWave[kk - SQUAT_LAG] else 0.0
        // быстрее бег (минус к времени) — дальше прыжок
        val sprintPart = if (kk >= SPRINT_LAG) -1.6 * sprintWave[kk - SPRINT_LAG] else 0.0
        return 14.40 + 0.85 * kk / WEEKS + squatPart + sprintPart + jumpNoise[kk]
    }

    /** На соревнованиях прыгают чуть дальше, чем на тренировке той же недели. */
    fun meet(k: Int): Double = jump(k) + 0.08
}
