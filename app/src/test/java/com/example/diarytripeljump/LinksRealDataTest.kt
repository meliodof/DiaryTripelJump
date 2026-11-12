package com.example.diarytripeljump

import com.example.diarytripeljump.data.Insights
import com.example.diarytripeljump.data.RecordPoint
import com.example.diarytripeljump.data.RecordSummary
import com.example.diarytripeljump.data.ResultUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Random

/**
 * Проверка поиска связей «упражнение → тройной».
 *
 * 1. Реальные данные: у двух прыгунов (TFRRS: tfrrs.org/athletes/8667434 — Justin Davis, tfrrs.org/athletes/7978401 —
 *    Velecia Williams) на одних соревнованиях есть и длина, и тройной. От недели к неделе они почти не совпадают
 *    (коэффициенты связи от −0,39 до 0,4 в зависимости от сдвига, перестановочный p = 0,35 и 0,93), поэтому модель
 *    не должна показывать связь.
 * 2. Чистый шум: упражнение, никак не связанное с тройным, не должно давать «связь» чаще, чем примерно в 5–10 % случаев.
 *    Прежнее правило (|r| ≥ 0,5 и t ≥ 2 по лучшему сдвигу) давало ложную связь в 42 % случаев.
 */
class LinksRealDataTest {

    private val day = 86_400_000L
    private val week = 7 * day

    private fun record(name: String, unit: ResultUnit, points: List<RecordPoint>) = RecordSummary(
        key = "ex|$name", name = name, displayName = name, unit = unit, isSingleLeg = false, legSide = null,
        isAnti = false, isCompetition = false, category = "Прыжковые", isIndoor = false, weather = null,
        inventoryLabel = null, levelLabel = "PB", best = points.maxOf { it.value }, bestDate = points.last().date,
        lowerIsBetter = false, deletableResultId = null, attempts = points.size, history = emptyList(), points = points
    )

    private fun parse(raw: String, event: String): List<RecordPoint> = raw.trim().lines()
        .map { it.trim().split(" ") }
        .filter { it[1] == event }
        .map { (d, _, v) -> RecordPoint(LocalDate.parse(d).toEpochDay() * day, v.toDouble(), true) }

    private fun noLink(raw: String) {
        val found = Insights.influences(parse(raw, "TJ"), listOf(record("Прыжок в длину", ResultUnit.M, parse(raw, "LJ"))))
        assertTrue("ложная связь: $found", found.isEmpty())
    }

    @Test
    fun davis_longJumpDoesNotMoveWithTripleWeekToWeek() = noLink(DAVIS)

    @Test
    fun williams_longJumpDoesNotMoveWithTripleWeekToWeek() = noLink(WILLIAMS)

    @Test
    fun noise_rarelyGivesLinks() {
        val rnd = Random(11)
        val runs = 60
        var falseLinks = 0
        repeat(runs) {
            val jumpWeeks = (0 until 52).shuffled(rnd).take(26)
            val exWeeks = (0 until 52).shuffled(rnd).take(30)
            val base = 2_900L * week + day
            val jump = jumpWeeks.map { w -> RecordPoint(base + w * week, 14 + 0.01 * w + rnd.nextGaussian() * 0.2, false) }
            val ex = exWeeks.map { w -> RecordPoint(base + w * week, 100 + 0.5 * w + rnd.nextGaussian() * 3, false) }
            if (Insights.influences(jump, listOf(record("Выпады", ResultUnit.KG, ex))).isNotEmpty()) falseLinks++
        }
        assertTrue("ложных связей $falseLinks из $runs", falseLinks <= runs * 0.12)
    }

    private companion object {
        val DAVIS = """
            2023-12-01 LJ 6.55
            2023-12-01 TJ 14.33
            2024-01-14 LJ 6.84
            2024-01-14 TJ 13.95
            2024-01-19 TJ 14.69
            2024-02-10 LJ 6.77
            2024-02-23 LJ 7.23
            2024-02-23 TJ 13.89
            2024-03-08 LJ 6.96
            2024-03-08 TJ 14.37
            2024-03-22 LJ 7.14
            2024-03-22 TJ 13.17
            2024-03-29 LJ 7.08
            2024-04-06 TJ 13.73
            2024-04-13 LJ 6.95
            2024-04-13 TJ 13.74
            2024-04-19 LJ 6.94
            2024-04-26 LJ 7.03
            2024-04-26 TJ 14.31
            2024-05-09 LJ 7.10
            2024-05-09 TJ 14.01
            2024-05-15 LJ 7.08
            2024-12-07 LJ 6.98
            2024-12-07 TJ 14.59
            2025-01-11 LJ 7.12
            2025-01-17 TJ 14.90
            2025-01-31 LJ 7.06
            2025-01-31 TJ 14.45
            2025-02-14 LJ 7.09
            2025-02-27 LJ 7.08
            2025-02-27 TJ 14.54
            2025-03-21 LJ 7.05
            2025-03-27 LJ 6.72
            2025-03-27 TJ 14.41
            2025-04-18 TJ 14.16
            2025-05-02 LJ 7.11
            2025-05-15 LJ 7.13
            2025-05-15 TJ 14.69
            2025-12-06 TJ 14.73
            2026-01-16 LJ 6.91
            2026-01-16 TJ 15.09
            2026-01-30 LJ 6.97
            2026-01-30 TJ 14.32
            2026-02-27 LJ 7.04
            2026-02-27 TJ 14.62
            2026-03-26 LJ 6.81
            2026-04-10 TJ 14.48
            2026-04-17 LJ 6.96
            2026-04-17 TJ 14.45
            2026-05-14 LJ 7.53
            2026-05-14 TJ 14.46
            2026-05-27 LJ 7.32
        """

        val WILLIAMS = """
            2022-01-14 LJ 6.01
            2022-01-14 TJ 12.67
            2022-01-28 TJ 12.52
            2022-02-11 LJ 6.28
            2022-02-11 TJ 12.26
            2022-02-25 LJ 6.21
            2022-02-25 TJ 12.41
            2022-03-23 LJ 6.04
            2022-04-22 LJ 5.96
            2022-05-13 LJ 6.42
            2022-05-13 TJ 12.44
            2022-05-25 LJ 6.22
            2022-06-08 LJ 6.12
            2023-02-10 LJ 5.65
            2023-03-29 LJ 6.02
            2023-04-06 LJ 6.36
            2023-04-21 TJ 12.59
            2023-04-26 TJ 12.61
            2023-05-06 TJ 12.88
            2023-05-12 LJ 5.84
            2023-05-12 TJ 12.68
            2023-05-24 LJ 6.37
            2023-05-24 TJ 12.65
            2023-06-07 LJ 6.15
            2024-01-12 TJ 12.89
            2024-01-20 LJ 6.11
            2024-02-02 TJ 12.53
            2024-02-09 LJ 6.07
            2024-02-09 TJ 12.16
            2024-02-16 LJ 6.28
            2024-02-23 LJ 5.90
            2024-02-23 TJ 12.65
            2024-04-05 LJ 6.33
            2024-04-19 LJ 6.39
            2024-04-19 TJ 12.25
            2024-05-10 LJ 6.14
            2024-05-10 TJ 12.38
            2024-05-22 LJ 6.56
            2024-06-05 LJ 6.41
            2025-01-17 TJ 12.31
            2025-01-25 LJ 6.21
            2025-01-31 LJ 6.21
            2025-01-31 TJ 13.27
            2025-02-14 LJ 6.29
            2025-02-14 TJ 13.35
            2025-02-28 LJ 6.09
            2025-02-28 TJ 13.13
            2025-04-04 LJ 6.13
            2025-04-04 TJ 12.86
            2025-04-11 LJ 6.07
            2025-04-11 TJ 12.92
            2025-04-18 TJ 12.39
            2025-05-03 LJ 6.28
            2025-05-09 LJ 6.14
            2025-05-16 LJ 6.12
            2025-05-16 TJ 12.53
            2025-05-28 LJ 6.11
            2025-05-28 TJ 13.19
        """
    }
}
