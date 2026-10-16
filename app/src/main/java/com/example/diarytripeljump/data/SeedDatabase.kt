package com.example.diarytripeljump.data

import kotlinx.coroutines.flow.first
import java.util.Calendar

class SeedDatabase(private val repository: TripleJumpRepository) {

    suspend fun seedInitialData() {
        if (repository.getAllCompetitions().first().isNotEmpty()) {
            return
        }

        // 1. Профиль атлета
        val birthCalendar = Calendar.getInstance().apply { set(2000, Calendar.JANUARY, 15) }
        val profile = AthleteProfile(
            id = 1,
            firstName = "Алексей",
            lastName = "Иванов",
            nickname = "@jumper_alex",
            greetingPreference = "NAME",
            specialization = "Тройной прыжок",
            birthDate = birthCalendar.timeInMillis,
            height = 185.0,
            weight = 75.0,
            customPbMeters = 15.52,
            notes = "КМС по легкой атлетике"
        )
        repository.insertProfile(profile)

        // 2. Базовые упражнения в справочник
        val initialExercises = listOf(
            Exercise(name = "Приседания со штангой", categoryName = "Силовое упражнение", defaultUnit = ResultUnit.KG),
            Exercise(name = "Бег 30 м с/х", categoryName = "Беговое упражнение", defaultUnit = ResultUnit.SECONDS),
            Exercise(name = "Бег 60 м", categoryName = "Беговое упражнение", defaultUnit = ResultUnit.SECONDS),
            Exercise(name = "Бег 150 м", categoryName = "Беговое упражнение", defaultUnit = ResultUnit.SECONDS),
            Exercise(name = "Тройной прыжок с короткого разбега", categoryName = "Упражнение на технику тройного прыжка", defaultUnit = ResultUnit.M),
            Exercise(name = "Прыжки с ноги на ногу (многоскоки)", categoryName = "Прыжковое упражнение", defaultUnit = ResultUnit.M, isSingleLeg = true),
            Exercise(name = "Спрыгивания с тумбы с последующим прыжком", categoryName = "Прыжковое упражнение", defaultUnit = ResultUnit.CM),
            Exercise(name = "Выпады со штангой", categoryName = "Силовое упражнение", defaultUnit = ResultUnit.KG, isSingleLeg = true),
            Exercise(name = "Подъем штанги на грудь", categoryName = "Силовое упражнение", defaultUnit = ResultUnit.KG),
            Exercise(name = "Пресс / Планка с отягощением", categoryName = "Упражнение ОФП", defaultUnit = ResultUnit.SECONDS)
        )

        initialExercises.forEach { ex ->
            repository.insertExercise(ex)
        }

        // 3. Подготовительные периоды
        val calPeriodStart = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 1) }
        val calPeriodEnd = Calendar.getInstance().apply { set(2025, Calendar.SEPTEMBER, 30) }
        repository.insertPreparationPeriod(
            PreparationPeriod(
                name = "Базовый мезоцикл",
                startDate = calPeriodStart.timeInMillis,
                endDate = calPeriodEnd.timeInMillis,
                colorHex = "#1E4D3B"
            )
        )

        // 4. Соревнования за 3 месяца (Август, Сентябрь, Октябрь 2025)
        val cal = Calendar.getInstance()

        // Август: Прошлое соревнование
        cal.set(2025, Calendar.AUGUST, 15, 12, 0)
        repository.insertCompetition(
            Competition(
                name = "Кубок Области по легкой атлетике",
                country = "Россия",
                city = "Краснодар",
                streetAndNumber = "ул. Железнодорожная, д. 49",
                location = "Россия, Краснодар, ул. Железнодорожная, д. 49",
                address = "Россия, Краснодар, ул. Железнодорожная, д. 49",
                date = cal.timeInMillis,
                eventTime = "12:00",
                hasQualification = true,
                qualificationStandard = "14.80 м",
                didNotQualifyForFinal = false,
                placement = "2-е место",
                attemptsJson = "14.60, 14.85, 15.02, X, 15.10, 15.15",
                eventType = "Тройной прыжок",
                status = CompetitionStatus.COMPLETED
            )
        )

        // Октябрь: Сегодняшнее соревнование
        val nowCal = Calendar.getInstance()
        repository.insertCompetition(
            Competition(
                name = "Чемпионат России (Финал сезона)",
                country = "Россия",
                city = "Москва",
                streetAndNumber = "Стадион Лужники",
                location = "Россия, Москва, Стадион Лужники",
                address = "Россия, Москва, Стадион Лужники",
                date = nowCal.timeInMillis,
                eventTime = "14:00",
                hasQualification = true,
                qualificationStandard = "15.00 м",
                didNotQualifyForFinal = false,
                placement = "1-е место",
                attemptsJson = "15.10, 15.25, 15.40, X, 15.35, 15.52",
                eventType = "Тройной прыжок",
                status = CompetitionStatus.ONGOING
            )
        )

        // 5. Тренировки за 3 месяца
        cal.set(2025, Calendar.AUGUST, 5, 10, 0)
        val session1 = repository.insertTrainingSession(
            TrainingSession(
                date = cal.timeInMillis,
                trainingType = TrainingType.STRENGTH,
                intensity = 8,
                durationMinutes = 90,
                notes = "Базовая силовая подготовка ног"
            )
        )
        repository.insertExerciseResult(
            ExerciseResult(
                exerciseId = 1,
                exerciseName = "Приседания со штангой",
                trainingSessionId = session1,
                date = cal.timeInMillis,
                result = 130.0,
                resultUnit = ResultUnit.KG,
                sets = 4,
                reps = 6,
                setResultsJson = "120,125,130,130"
            )
        )

        cal.set(2025, Calendar.SEPTEMBER, 3, 11, 0)
        val session2 = repository.insertTrainingSession(
            TrainingSession(
                date = cal.timeInMillis,
                trainingType = TrainingType.CONDITIONING,
                intensity = 8,
                durationMinutes = 60,
                notes = "Скоростная работа на манеже"
            )
        )
        repository.insertExerciseResult(
            ExerciseResult(
                exerciseId = 3,
                exerciseName = "Бег 60 м",
                trainingSessionId = session2,
                date = cal.timeInMillis,
                result = 7.05,
                resultUnit = ResultUnit.SECONDS,
                sets = 3,
                reps = 3,
                setResultsJson = "7.15,7.08,7.05"
            )
        )

        // 6. Рекорды
        repository.insertTripleJumpResult(
            TripleJumpResult(
                eventName = "Тройной прыжок",
                result = 15.52,
                resultUnit = ResultUnit.M,
                isIndoor = false,
                isAntiRecord = false,
                recordLevel = "PB",
                exerciseCategory = "Прыжковые",
                isCompetition = true,
                weatherConditions = "Ясно, +24°C",
                notes = "Рекорд на Чемпионате России"
            )
        )

        repository.insertTripleJumpResult(
            TripleJumpResult(
                eventName = "Тройной прыжок",
                result = 13.90,
                resultUnit = ResultUnit.M,
                isIndoor = false,
                isAntiRecord = true,
                recordLevel = "PB",
                exerciseCategory = "Прыжковые",
                isCompetition = true,
                weatherConditions = "Дождь, +12°C",
                notes = "Худший результат сезона (дождь)"
            )
        )
    }
}
