package com.example.diarytripeljump.data

import java.util.Calendar

/** Возрастная группа в лёгкой атлетике: от U12 до взрослых (мужчины / женщины). */
data class AgeGroup(
    /** Короткий код: «U18», «Мужчины». */
    val code: String,
    /** Расшифровка: «юноши и девушки 16–17 лет». */
    val description: String
)

object AgeGroups {

    /**
     * Группа определяется возрастом, которого спортсмен достигает в текущем календарном году
     * (так принято в лёгкой атлетике): родившийся в декабре выступает в группе своего года рождения весь сезон.
     */
    fun of(birthDateMillis: Long?, gender: String?, now: Long = System.currentTimeMillis()): AgeGroup? {
        if (birthDateMillis == null) return null
        val birthYear = Calendar.getInstance().apply { timeInMillis = birthDateMillis }.get(Calendar.YEAR)
        val year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        return forAgeInYear(year - birthYear, gender)
    }

    fun forAgeInYear(age: Int, gender: String?): AgeGroup {
        val male = gender == GENDER_MALE
        val female = gender == GENDER_FEMALE
        val youth = when {
            male -> "юноши"
            female -> "девушки"
            else -> "юноши и девушки"
        }
        return when {
            age <= 11 -> AgeGroup("U12", "$youth до 12 лет")
            age <= 13 -> AgeGroup("U14", "$youth 12–13 лет")
            age <= 15 -> AgeGroup("U16", "$youth 14–15 лет")
            age <= 17 -> AgeGroup("U18", "$youth 16–17 лет")
            age <= 19 -> AgeGroup(
                "U20",
                when {
                    male -> "юниоры 18–19 лет"
                    female -> "юниорки 18–19 лет"
                    else -> "юниоры и юниорки 18–19 лет"
                }
            )
            age <= 22 -> AgeGroup("U23", "молодёжь 20–22 лет")
            male -> AgeGroup("Мужчины", "взрослые, 23 года и старше")
            female -> AgeGroup("Женщины", "взрослые, 23 года и старше")
            else -> AgeGroup("Взрослые", "23 года и старше · укажите пол в профиле")
        }
    }

    const val GENDER_MALE = "MALE"
    const val GENDER_FEMALE = "FEMALE"
}
