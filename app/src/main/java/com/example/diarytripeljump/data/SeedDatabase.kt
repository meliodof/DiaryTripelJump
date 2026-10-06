package com.example.diarytripeljump.data

import kotlinx.coroutines.flow.first
import java.util.Calendar

class SeedDatabase(private val repository: TripleJumpRepository) {

    suspend fun seedInitialData() {
        if (repository.getAllCompetitions().first().isNotEmpty()) {
            return
        }

        // 1. Профиль атлета
        val birthCalendar = Calendar.getInstance()
        birthCalendar.set(2000, Calendar.JANUARY, 15)
        val profile = AthleteProfile(
            id = 1,
            name = "Алексей Иванов",
            specialization = "Тройной прыжок",
            birthDate = birthCalendar.timeInMillis,
            height = 185.0,
            weight = 75.0,
            notes = "Спортсмен с 2018 года"
        )
        repository.insertProfile(profile)

        // 2. Соревнования
        val currentTime = System.currentTimeMillis()

        val competitions = listOf(
            Competition(
                name = "Чемпионат России",
                location = "Стадион Лужники, Москва",
                date = currentTime + (6 * 24 * 60 * 60 * 1000L),
                eventType = "Тройной прыжок",
                status = CompetitionStatus.UPCOMING,
                notes = "Главный старт сезона"
            ),
            Competition(
                name = "Кубок Регионов",
                location = "Манеж",
                date = currentTime + (30 * 24 * 60 * 60 * 1000L),
                eventType = "Тройной прыжок",
                status = CompetitionStatus.UPCOMING,
                notes = "Отборочный этап"
            )
        )

        competitions.forEach { competition ->
            repository.insertCompetition(competition)
        }
    }
}
