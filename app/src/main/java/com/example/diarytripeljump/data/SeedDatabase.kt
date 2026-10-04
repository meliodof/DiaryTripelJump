package com.example.diarytripeljump.data

import kotlinx.coroutines.flow.first
import java.util.Calendar

class SeedDatabase(private val repository: TripleJumpRepository) {

    suspend fun seedInitialData() {
        // Если база уже заполнена упражнениями, не дублируем данные
        if (repository.getAllExercises().first().isNotEmpty()) {
            return
        }

        // 1. Создаём профиль спортсмена
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

        // 2. Создаём список уникальных упражнений
        val exerciseList = listOf(
            // Приседания & Силовые
            Exercise(name = "Приседания со штангой", category = ExerciseCategory.SQUAT, isFavorite = true, defaultUnit = ResultUnit.KG),
            Exercise(name = "Выпады с гантелями", category = ExerciseCategory.LUNGE, isFavorite = true, defaultUnit = ResultUnit.KG),
            Exercise(name = "Румынская тяга", category = ExerciseCategory.DEADLIFT, isFavorite = true, defaultUnit = ResultUnit.KG),
            Exercise(name = "Взрывной рывок", category = ExerciseCategory.POWER_CLEAN, isFavorite = false, defaultUnit = ResultUnit.KG),

            // Прыжковые упражнения
            Exercise(name = "Тройной прыжок с места", category = ExerciseCategory.JUMP, isFavorite = true, defaultUnit = ResultUnit.M),
            Exercise(name = "Прыжки на тумбу", category = ExerciseCategory.JUMP_BOX, isFavorite = true, defaultUnit = ResultUnit.CM),
            Exercise(name = "Прыжки в длину с места", category = ExerciseCategory.BROAD_JUMP, isFavorite = true, defaultUnit = ResultUnit.CM),
            Exercise(name = "Многоскоки (Hop-Step-Jump)", category = ExerciseCategory.JUMP, isFavorite = true, defaultUnit = ResultUnit.M),
            Exercise(name = "Прыжки через барьеры (76-84см)", category = ExerciseCategory.HURDLES, isFavorite = true, defaultUnit = ResultUnit.CM),

            // ОФП & Core
            Exercise(name = "Планка", category = ExerciseCategory.PLANK, isFavorite = true, defaultUnit = ResultUnit.SECONDS),
            Exercise(name = "Скручивания на пресс", category = ExerciseCategory.CORE, isFavorite = false, defaultUnit = ResultUnit.REPS),

            // Восстановительные & Заминка
            Exercise(name = "Растяжка мышц ног", category = ExerciseCategory.STRETCHING, isFavorite = false, defaultUnit = ResultUnit.SECONDS),
            Exercise(name = "Мобильность ТБС (Растяжка)", category = ExerciseCategory.MOBILITY, isFavorite = false, defaultUnit = ResultUnit.SECONDS)
        )

        exerciseList.forEach { exercise ->
            repository.insertExercise(exercise)
        }

        // 3. Создаём соревнования
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

        // 4. Создаём тренировки
        val trainings = listOf(
            TrainingSession(
                date = currentTime - (1 * 24 * 60 * 60 * 1000L),
                trainingType = TrainingType.STRENGTH,
                intensity = 8,
                durationMinutes = 90,
                notes = "Силовая тренировка ног"
            ),
            TrainingSession(
                date = currentTime - (3 * 24 * 60 * 60 * 1000L),
                trainingType = TrainingType.TECHNICAL,
                intensity = 7,
                durationMinutes = 60,
                notes = "Техника тройного прыжка"
            ),
            TrainingSession(
                date = currentTime - (5 * 24 * 60 * 60 * 1000L),
                trainingType = TrainingType.CONDITIONING,
                intensity = 6,
                durationMinutes = 45,
                notes = "ОФП"
            )
        )

        val trainingIds = trainings.map { repository.insertTrainingSession(it) }

        // 5. Добавляем результаты упражнений к тренировкам
        val exerciseResults = listOf(
            ExerciseResult(
                exerciseId = 1,
                trainingSessionId = trainingIds[0],
                date = currentTime - (1 * 24 * 60 * 60 * 1000L),
                result = 120.0,
                resultUnit = ResultUnit.KG,
                sets = 5,
                reps = 8,
                notes = "Хорошая техника"
            ),
            ExerciseResult(
                exerciseId = 2,
                trainingSessionId = trainingIds[0],
                date = currentTime - (1 * 24 * 60 * 60 * 1000L),
                result = 80.0,
                resultUnit = ResultUnit.KG,
                sets = 4,
                reps = 8
            )
        )

        exerciseResults.forEach { result ->
            repository.insertExerciseResult(result)
        }

        // 6. Создаём результаты тройного прыжка
        val baseTime = System.currentTimeMillis()

        val jumpResults = listOf(
            TripleJumpResult(
                competitionId = null,
                date = baseTime - (7 * 24 * 60 * 60 * 1000L),
                result = 14.5,
                weatherConditions = "Ясно, 20°C",
                notes = "Личный рекорд на тренировке"
            )
        )

        val jumpResultIds = jumpResults.map { repository.insertTripleJumpResult(it) }

        for (i in 1..6) {
            repository.insertJumpAttempt(
                JumpAttempt(
                    tripleJumpResultId = jumpResultIds[0],
                    attemptNumber = i,
                    attemptType = AttemptType.TRAINING,
                    distance = if (i == 3) 14.5 else 14.0 + (6 - i) * 0.1,
                    isFoul = false
                )
            )
        }
    }
}
