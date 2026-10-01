package com.example.diarytripeljump.data

import java.util.Calendar

class SeedDatabase(private val repository: TripleJumpRepository) {

    suspend fun seedInitialData() {
        // Проверяем, есть ли уже данные
        val exercises = repository.getAllExercises()
        // Если нужно проверить наличие данных, можно использовать first() на Flow

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

        // 2. Создаём упражнения
        val exerciseList = listOf(
            // Приседания
            Exercise(
                name = "Приседания со штангой",
                category = ExerciseCategory.SQUAT,
                isFavorite = true,
                defaultUnit = ResultUnit.KG
            ),
            Exercise(
                name = "Приседания в тренажёре Смита",
                category = ExerciseCategory.SQUAT,
                isFavorite = false,
                defaultUnit = ResultUnit.KG
            ),
            Exercise(
                name = "Гоблет-приседания",
                category = ExerciseCategory.SQUAT,
                isFavorite = false,
                defaultUnit = ResultUnit.KG
            ),

            // Выпады
            Exercise(
                name = "Выпады с гантелями",
                category = ExerciseCategory.LUNGE,
                isFavorite = true,
                defaultUnit = ResultUnit.KG
            ),
            Exercise(
                name = "Болгарские выпады",
                category = ExerciseCategory.LUNGE,
                isFavorite = false,
                defaultUnit = ResultUnit.KG
            ),
            Exercise(
                name = "Румынская тяга",
                category = ExerciseCategory.DEADLIFT,
                isFavorite = true,
                defaultUnit = ResultUnit.KG
            ),

            // Прыжки
            Exercise(
                name = "Прыжки на тумбу",
                category = ExerciseCategory.JUMP_BOX,
                isFavorite = true,
                defaultUnit = ResultUnit.CM
            ),
            Exercise(
                name = "Прыжки в длину с места",
                category = ExerciseCategory.BROAD_JUMP,
                isFavorite = true,
                defaultUnit = ResultUnit.CM
            ),
            Exercise(
                name = "Прыжки в высоту с места",
                category = ExerciseCategory.VERTICAL_JUMP,
                isFavorite = false,
                defaultUnit = ResultUnit.CM
            ),
            Exercise(
                name = "Прыжки с глубины",
                category = ExerciseCategory.DEPTH_JUMP,
                isFavorite = false,
                defaultUnit = ResultUnit.CM
            ),

            // Core
            Exercise(
                name = "Планка",
                category = ExerciseCategory.PLANK,
                isFavorite = true,
                defaultUnit = ResultUnit.SECONDS
            ),
            Exercise(
                name = "Скручивания",
                category = ExerciseCategory.CORE,
                isFavorite = false,
                defaultUnit = ResultUnit.REPS
            ),
            Exercise(
                name = "Подъём ног в висе",
                category = ExerciseCategory.CORE,
                isFavorite = false,
                defaultUnit = ResultUnit.REPS
            ),

            // Взрывная сила
            Exercise(
                name = "Рывок",
                category = ExerciseCategory.POWER_CLEAN,
                isFavorite = false,
                defaultUnit = ResultUnit.KG
            ),
            Exercise(
                name = "Толчок",
                category = ExerciseCategory.POWER_SNATCH,
                isFavorite = false,
                defaultUnit = ResultUnit.KG
            ),

            // Мобильность
            Exercise(
                name = "Растяжка мышц ног",
                category = ExerciseCategory.STRETCHING,
                isFavorite = false,
                defaultUnit = ResultUnit.SECONDS
            ),
            Exercise(
                name = "Мобильность тазобедренных суставов",
                category = ExerciseCategory.MOBILITY,
                isFavorite = false,
                defaultUnit = ResultUnit.SECONDS
            )
        )

        exerciseList.forEach { exercise ->
            repository.insertExercise(exercise)
        }

        // 3. Создаём соревнования
        val currentTime = System.currentTimeMillis()

        val competitions = listOf(
            Competition(
                name = "Чемпионат города",
                location = "Стадион",
                date = currentTime + (15 * 24 * 60 * 60 * 1000L), // +15 дней
                eventType = "Тройной прыжок",
                status = CompetitionStatus.UPCOMING,
                notes = "Главное соревнование сезона"
            ),
            Competition(
                name = "Кубок региона",
                location = "Спорткомплекс",
                date = currentTime + (30 * 24 * 60 * 60 * 1000L), // +30 дней
                eventType = "Тройной прыжок",
                status = CompetitionStatus.UPCOMING,
                notes = "Отборочный этап"
            ),
            Competition(
                name = "Открытый чемпионат",
                location = "Стадион",
                date = currentTime - (30 * 24 * 60 * 60 * 1000L), // -30 дней
                eventType = "Тройной прыжок",
                status = CompetitionStatus.COMPLETED,
                notes = "Результат: 14.2 м"
            )
        )

        competitions.forEach { competition ->
            repository.insertCompetition(competition)
        }

        // 4. Создаём тренировки
        val trainings = listOf(
            TrainingSession(
                date = currentTime - (1 * 24 * 60 * 60 * 1000L), // -1 день
                trainingType = TrainingType.STRENGTH,
                intensity = 8,
                durationMinutes = 90,
                notes = "Силовая тренировка ног"
            ),
            TrainingSession(
                date = currentTime - (3 * 24 * 60 * 60 * 1000L), // -3 дня
                trainingType = TrainingType.TECHNICAL,
                intensity = 7,
                durationMinutes = 60,
                notes = "Техника тройного прыжка"
            ),
            TrainingSession(
                date = currentTime - (5 * 24 * 60 * 60 * 1000L), // -5 дней
                trainingType = TrainingType.CONDITIONING,
                intensity = 6,
                durationMinutes = 45,
                notes = "ОФП"
            ),
            TrainingSession(
                date = currentTime - (7 * 24 * 60 * 60 * 1000L), // -7 дней
                trainingType = TrainingType.RECOVERY,
                intensity = 3,
                durationMinutes = 30,
                notes = "Восстановление"
            )
        )

        val trainingIds = trainings.map { repository.insertTrainingSession(it) }

        // 5. Добавляем результаты упражнений к тренировкам
        val exerciseResults = listOf(
            // Тренировка 1 (Силовая)
            ExerciseResult(
                exerciseId = 1, // Приседания со штангой
                trainingSessionId = trainingIds[0],
                date = currentTime - (1 * 24 * 60 * 60 * 1000L),
                result = 120.0,
                resultUnit = ResultUnit.KG,
                sets = 5,
                reps = 5,
                notes = "Хорошая техника"
            ),
            ExerciseResult(
                exerciseId = 4, // Выпады с гантелями
                trainingSessionId = trainingIds[0],
                date = currentTime - (1 * 24 * 60 * 60 * 1000L),
                result = 80.0,
                resultUnit = ResultUnit.KG,
                sets = 4,
                reps = 8,
                notes = null
            ),
            ExerciseResult(
                exerciseId = 6, // Румынская тяга
                trainingSessionId = trainingIds[0],
                date = currentTime - (1 * 24 * 60 * 60 * 1000L),
                result = 100.0,
                resultUnit = ResultUnit.KG,
                sets = 4,
                reps = 6,
                notes = null
            ),

            // Тренировка 2 (Техническая)
            ExerciseResult(
                exerciseId = 7, // Прыжки на тумбу
                trainingSessionId = trainingIds[1],
                date = currentTime - (3 * 24 * 60 * 60 * 1000L),
                result = 45.0,
                resultUnit = ResultUnit.CM,
                sets = 5,
                reps = 3,
                notes = "PB"
            ),
            ExerciseResult(
                exerciseId = 8, // Прыжки в длину с места
                trainingSessionId = trainingIds[1],
                date = currentTime - (3 * 24 * 60 * 60 * 1000L),
                result = 280.0,
                resultUnit = ResultUnit.CM,
                sets = 4,
                reps = 3,
                notes = null
            ),

            // Тренировка 3 (ОФП)
            ExerciseResult(
                exerciseId = 11, // Планка
                trainingSessionId = trainingIds[2],
                date = currentTime - (5 * 24 * 60 * 60 * 1000L),
                result = 120.0,
                resultUnit = ResultUnit.SECONDS,
                sets = 3,
                reps = 1,
                notes = null
            ),
            ExerciseResult(
                exerciseId = 12, // Скручивания
                trainingSessionId = trainingIds[2],
                date = currentTime - (5 * 24 * 60 * 60 * 1000L),
                result = 30.0,
                resultUnit = ResultUnit.REPS,
                sets = 4,
                reps = 15,
                notes = null
            )
        )

        exerciseResults.forEach { result ->
            repository.insertExerciseResult(result)
        }

        // 6. Создаём результаты тройного прыжка
        val baseTime = System.currentTimeMillis()

        val jumpResults = listOf(
            TripleJumpResult(
                competitionId = null, // Тренировка
                date = baseTime - (7 * 24 * 60 * 60 * 1000L),
                result = 14.5,
                weatherConditions = "Ясно, 20°C",
                notes = "Личный рекорд на тренировке"
            ),
            TripleJumpResult(
                competitionId = null, // Тренировка
                date = baseTime - (14 * 24 * 60 * 60 * 1000L),
                result = 14.2,
                weatherConditions = "Облачно, 18°C",
                notes = null
            ),
            TripleJumpResult(
                competitionId = null, // Тренировка
                date = baseTime - (21 * 24 * 60 * 60 * 1000L),
                result = 13.8,
                weatherConditions = "Ясно, 22°C",
                notes = null
            ),
            TripleJumpResult(
                competitionId = null, // Тренировка
                date = baseTime - (28 * 24 * 60 * 60 * 1000L),
                result = 13.5,
                weatherConditions = "Пасмурно, 17°C",
                notes = null
            ),
            TripleJumpResult(
                competitionId = null, // Тренировка
                date = baseTime - (35 * 24 * 60 * 60 * 1000L),
                result = 13.2,
                weatherConditions = "Ясно, 19°C",
                notes = null
            )
        )

        val jumpResultIds = jumpResults.map { repository.insertTripleJumpResult(it) }

        // 7. Добавляем попытки к результатам тройного прыжка
        val attempts = mutableListOf<JumpAttempt>()

        // Для первого результата (14.5 м) - тренировочные попытки
        for (i in 1..6) {
            attempts.add(
                JumpAttempt(
                    tripleJumpResultId = jumpResultIds[0],
                    attemptNumber = i,
                    attemptType = AttemptType.TRAINING,
                    distance = if (i == 3) 14.5 else if (i == 1) 14.2 else if (i == 2) 14.3 else 14.0 + (6 - i) * 0.1,
                    isFoul = false,
                    notes = if (i == 3) "Лучшая попытка" else null
                )
            )
        }

        // Для второго результата (14.2 м)
        for (i in 1..5) {
            attempts.add(
                JumpAttempt(
                    tripleJumpResultId = jumpResultIds[1],
                    attemptNumber = i,
                    attemptType = AttemptType.TRAINING,
                    distance = if (i == 2) 14.2 else 13.8 + (5 - i) * 0.1,
                    isFoul = i == 4,
                    notes = if (i == 4) "Заступ" else null
                )
            )
        }

        attempts.forEach { attempt ->
            repository.insertJumpAttempt(attempt)
        }
    }
}
