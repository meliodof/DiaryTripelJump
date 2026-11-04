package com.example.diarytripeljump.data

import java.util.Calendar

/** Спортивное звание или разряд с нормой в тройном прыжке (метры, не менее). */
data class SportRank(
    val code: String,
    val shortName: String,
    val title: String,
    val maleNorm: Double,
    val femaleNorm: Double,
    /** С какого возраста выполняется норма: возраст должен быть достигнут в календарный год соревнований. */
    val minAge: Int
)

/** Разряд, определённый по лучшим результатам; [rank] = null — ни одна норма пока не выполнена. */
data class AutoRank(
    val rank: SportRank?,
    val bestResult: Double?,
    val bestDate: Long?
)

/**
 * Нормы ЕВСК по лёгкой атлетике, дисциплина «прыжок тройной»: приложение № 16 к приказу Минспорта России
 * от 10.03.2026 № 201. Норма выполняется на соревнованиях при попутном ветре не более 2 м/с,
 * а звание или разряд присваивает уполномоченная организация — приложение показывает выполненную норму.
 */
object SportRanks {
    /** От высшего к низшему. */
    val all = listOf(
        SportRank("MSMK", "МСМК", "Мастер спорта международного класса", 16.90, 14.40, 16),
        SportRank("MS", "МС", "Мастер спорта", 15.90, 13.40, 15),
        SportRank("KMS", "КМС", "Кандидат в мастера спорта", 14.80, 12.30, 14),
        SportRank("I", "I разряд", "I спортивный разряд", 13.80, 11.50, 10),
        SportRank("II", "II разряд", "II спортивный разряд", 12.80, 10.70, 10),
        SportRank("III", "III разряд", "III спортивный разряд", 11.70, 9.90, 10),
        SportRank("I_YOUTH", "I юн.", "I юношеский разряд", 10.60, 9.30, 10),
        SportRank("II_YOUTH", "II юн.", "II юношеский разряд", 9.50, 8.70, 10),
        SportRank("III_YOUTH", "III юн.", "III юношеский разряд", 8.40, 8.10, 10)
    )

    fun byCode(code: String?): SportRank? = all.firstOrNull { it.code == code }

    /** Норма для пола; null — пол в профиле не указан. */
    fun norm(rank: SportRank, gender: String?): Double? = when (gender) {
        AgeGroups.GENDER_MALE -> rank.maleNorm
        AgeGroups.GENDER_FEMALE -> rank.femaleNorm
        else -> null
    }

    private fun yearOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }[Calendar.YEAR]

    /** Высший разряд, норму которого покрывает результат с учётом возраста в год соревнований. */
    fun reached(result: Double, gender: String?, birthDate: Long?, resultDate: Long): SportRank? {
        val age = birthDate?.let { yearOf(resultDate) - yearOf(it) }
        return all.firstOrNull { rank ->
            val limit = norm(rank, gender) ?: return null
            result >= limit && (age == null || age >= rank.minAge)
        }
    }

    /** Разряд по всем результатам [samples] (результат и дата): высший, какой когда-либо был выполнен. */
    fun best(samples: List<Pair<Double, Long>>, gender: String?, birthDate: Long?): AutoRank {
        val top = samples.maxByOrNull { it.first }
        val rank = samples.mapNotNull { (result, date) -> reached(result, gender, birthDate, date) }
            .minByOrNull { all.indexOf(it) }
        return AutoRank(rank, top?.first, top?.second)
    }

    /** Следующая цель: разряд выше текущего; null — выше некуда. */
    fun next(rank: SportRank?): SportRank? =
        if (rank == null) all.last() else all.getOrNull(all.indexOf(rank) - 1)
}
