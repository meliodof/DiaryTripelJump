package com.example.diarytripeljump.data

import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Random

/** Что из демо-набора найдено в дневнике пользователя (остатки старой автозагрузки). */
data class DemoLeftovers(
    val trainings: Int,
    val competitions: Int,
    val periods: Int,
    val complexes: Int,
    val exercises: Int,
    val goals: Int,
    val profile: Boolean
) {
    val isEmpty: Boolean get() = trainings + competitions + periods + complexes + exercises + goals == 0 && !profile

    fun describe(): String = buildList {
        if (trainings > 0) add("тренировок: $trainings")
        if (competitions > 0) add("стартов: $competitions")
        if (periods > 0) add("периодов: $periods")
        if (complexes > 0) add("комплексов: $complexes")
        if (exercises > 0) add("упражнений в каталоге: $exercises")
        if (goals > 0) add("целей: $goals")
        if (profile) add("профиль «Алексей Морозов»")
    }.joinToString(", ")
}

/**
 * Демо-данные: профиль спортсмена, справочник упражнений, комплексы, тренировки,
 * старты, рекорды и подготовительные периоды за последние три месяца (считая от сегодняшнего дня).
 *
 * Заполняют только отдельную демо-базу ([TripleJumpDatabase.DEMO_DATABASE_NAME]). Старые версии приложения
 * заливали этот набор прямо в дневник пользователя — [scanLeftovers] и [removeLeftovers] находят и убирают его.
 */
class SeedDatabase(private val repository: TripleJumpRepository) {

    /** Заполняет демо-базу при первом открытии (и после очистки). */
    suspend fun seedIfEmpty() {
        val empty = repository.getAllTrainingsWithExercises().first().isEmpty() &&
            repository.getAllCompetitions().first().isEmpty()
        if (empty) {
            seedDemoData()
        } else {
            // демо-база, созданная прежней версией: профиль переименовываем, чтобы его не путали с настоящим
            val p = repository.getProfile().first()
            if (p != null && isOldDemoProfile(p)) repository.insertProfile(p.copy(firstName = DEMO_FIRST, lastName = DEMO_LAST, nickname = DEMO_NICK))
        }
    }

    // --- Поиск и удаление демо-записей в дневнике пользователя ---

    private fun isSeedTime(millis: Long, hour: Int): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c[Calendar.HOUR_OF_DAY] == hour && c[Calendar.MINUTE] == 0 && c[Calendar.SECOND] == 0 && c[Calendar.MILLISECOND] == 0
    }

    private fun isOldDemoProfile(p: AthleteProfile) = p.lastName == "Морозов" && p.nickname == "Лёха"

    /**
     * Запись считается демо, только если совпадают все признаки автозагрузки: точное время (10:00:00.000 для тренировок,
     * 12:00:00.000 для стартов), названия из демо-набора. Тренировку, в которую пользователь добавил своё упражнение, не трогаем.
     */
    private suspend fun findLeftovers(): Triple<List<TrainingWithExercises>, List<Competition>, DemoLeftovers> {
        val seedExercises = EXERCISES.map { it.name }.toSet()
        val seedSkips = SKIPS.values.toSet()
        val trainings = repository.getAllTrainingsWithExercises().first()
        val demoTrainings = trainings.filter { t ->
            isSeedTime(t.training.date, 10) && (t.training.notes ?: "").let { n -> n.isEmpty() || n.startsWith("Пропуск: ") } &&
                if (t.training.isSkipped) t.training.skipReason in seedSkips
                else t.exerciseResults.isNotEmpty() && t.exerciseResults.all { it.exerciseName in seedExercises }
        }
        val demoCompetitions = repository.getAllCompetitions().first().filter {
            it.name in SEED_MEETS && isSeedTime(it.date, 12) && it.eventTime == "11:00"
        }
        val periods = repository.getAllTrainingPeriods().first().filter { p -> SEED_PERIODS.any { it.first == p.name && it.second == p.colorHex } }
        val complexes = repository.getAllComplexes().first().filter { c -> COMPLEXES.any { it.first == c.complex.name } }

        // упражнения каталога убираем, только если после очистки на них не останется ни одного результата
        val demoIds = demoTrainings.map { it.training.id }.toSet()
        val stillUsed = trainings.filter { it.training.id !in demoIds }.flatMap { t -> t.exerciseResults.map { it.exerciseName } }.toSet()
        val exercises = repository.getAllExercises().first().filter { it.name in seedExercises && it.name !in stillUsed }
        val removedNames = exercises.map { it.name }.toSet()
        val goals = repository.getAllGoals().first().filter { g -> GOALS.any { it.first == g.name } && g.name in removedNames }
        val profile = repository.getProfile().first()?.let { isOldDemoProfile(it) } == true

        return Triple(
            demoTrainings, demoCompetitions,
            DemoLeftovers(demoTrainings.size, demoCompetitions.size, periods.size, complexes.size, exercises.size, goals.size, profile)
        )
    }

    suspend fun scanLeftovers(): DemoLeftovers = findLeftovers().third

    /** Удаляет найденные демо-записи одной транзакцией; аватар профиля сохраняется. */
    suspend fun removeLeftovers(): DemoLeftovers = repository.inTransaction {
        val (trainings, competitions, summary) = findLeftovers()
        trainings.forEach { repository.deleteTrainingSession(it.training) }
        competitions.forEach { repository.deleteCompetition(it) } // результаты стартов удаляются каскадом
        repository.getAllTrainingPeriods().first()
            .filter { p -> SEED_PERIODS.any { it.first == p.name && it.second == p.colorHex } }
            .forEach { repository.deleteTrainingPeriod(it) }
        repository.getAllComplexes().first()
            .filter { c -> COMPLEXES.any { it.first == c.complex.name } }
            .forEach { repository.deleteComplex(it.complex) }
        val stillUsed = repository.getAllTrainingsWithExercises().first().flatMap { t -> t.exerciseResults.map { it.exerciseName } }.toSet()
        val seedExercises = EXERCISES.map { it.name }.toSet()
        val removed = repository.getAllExercises().first().filter { it.name in seedExercises && it.name !in stillUsed }.map { it.name }.toSet()
        removed.forEach { repository.deleteExercisesByName(it) }
        repository.getAllGoals().first().filter { g -> GOALS.any { it.first == g.name } && g.name in removed }
            .forEach { repository.deleteGoal(it.recordKey) }
        repository.getProfile().first()?.takeIf { isOldDemoProfile(it) }?.let {
            repository.insertProfile(AthleteProfile(id = 1, avatarUriString = it.avatarUriString))
        }
        summary
    }

    private class ExDef(
        val name: String,
        val category: String,
        val unit: ResultUnit,
        val base: Double,
        val gain: Double,
        val sets: Int,
        val reps: Int,
        val legSide: String? = null,
        val invName: String? = null,
        val invUnit: ResultUnit? = null,
        val invValue: Double? = null
    )

    private companion object {
        const val DAY = 86_400_000L

        const val C_RUN = "Беговое упражнение"
        const val C_TECH = "Упражнение на технику тройного прыжка"
        const val C_STR = "Силовое упражнение"
        const val C_JUMP = "Прыжковое упражнение"
        const val C_GPP = "Упражнение ОФП"

        val EXERCISES = listOf(
            ExDef("Приседания со штангой", C_STR, ResultUnit.KG, 130.0, 20.0, 4, 5),
            ExDef("Тяга становая", C_STR, ResultUnit.KG, 150.0, 25.0, 3, 5),
            ExDef("Рывок штанги", C_STR, ResultUnit.KG, 70.0, 12.0, 4, 3),
            ExDef("Жим лёжа", C_STR, ResultUnit.KG, 90.0, 12.0, 4, 5),
            ExDef("Выпады со штангой", C_STR, ResultUnit.KG, 60.0, 15.0, 3, 4),
            ExDef("Бег 30 м", C_RUN, ResultUnit.SECONDS, 3.98, -0.16, 3, 3),
            ExDef("Бег 60 м с хода", C_RUN, ResultUnit.SECONDS, 6.62, -0.25, 3, 2),
            ExDef("Тройной прыжок с 6 шагов", C_TECH, ResultUnit.M, 13.10, 0.80, 4, 4),
            ExDef("Тройной прыжок с полного разбега", C_TECH, ResultUnit.M, 14.60, 0.65, 3, 4),
            ExDef("Многоскоки (5 прыжков)", C_JUMP, ResultUnit.M, 15.40, 1.20, 4, 4),
            ExDef("Прыжки на одной ноге", C_JUMP, ResultUnit.M, 11.00, 1.00, 3, 4, legSide = "LEFT"),
            ExDef("Прыжки через барьеры", C_JUMP, ResultUnit.CM, 80.0, 10.0, 4, 5, invName = "Барьер", invUnit = ResultUnit.CM, invValue = 91.0),
            ExDef("Выпрыгивания на тумбу", C_JUMP, ResultUnit.CM, 90.0, 12.0, 4, 4, invName = "Тумба", invUnit = ResultUnit.CM, invValue = 80.0),
            ExDef("Подтягивания", C_GPP, ResultUnit.REPS, 12.0, 4.0, 3, 3),
            ExDef("Бросок ядра назад", C_GPP, ResultUnit.M, 14.50, 1.70, 4, 4, invName = "Ядро", invUnit = ResultUnit.KG, invValue = 7.26)
        )

        val FAVORITES = setOf("Приседания со штангой", "Бег 30 м", "Прыжки через барьеры")

        val GOALS = listOf(
            "Приседания со штангой" to 160.0,
            "Бег 30 м" to 3.75
        )

        val COMPLEXES = listOf(
            Triple("Силовой блок ног", "База для силовых дней", listOf("Приседания со штангой", "Тяга становая", "Рывок штанги")),
            Triple("Прыжковая техника", "Прыжки и разбег для технических дней", listOf("Тройной прыжок с 6 шагов", "Многоскоки (5 прыжков)", "Прыжки через барьеры")),
            Triple("Скорость и ОФП", "Короткие ускорения и общая подготовка", listOf("Бег 30 м", "Бег 60 м с хода", "Подтягивания", "Бросок ядра назад"))
        )

        /** День недели → упражнения этого дня. */
        val WEEK_PLAN = mapOf(
            Calendar.MONDAY to listOf("Приседания со штангой", "Тяга становая", "Рывок штанги"),
            Calendar.TUESDAY to listOf("Тройной прыжок с 6 шагов", "Многоскоки (5 прыжков)", "Прыжки через барьеры"),
            Calendar.THURSDAY to listOf("Бег 30 м", "Бег 60 м с хода", "Подтягивания", "Бросок ядра назад"),
            Calendar.FRIDAY to listOf("Жим лёжа", "Выпады со штангой", "Выпрыгивания на тумбу", "Прыжки на одной ноге"),
            Calendar.SATURDAY to listOf("Тройной прыжок с полного разбега", "Многоскоки (5 прыжков)")
        )

        /** Номера тренировочных дней, которые «пропущены», и причины. */
        val SKIPS = mapOf(3 to "Болезнь", 4 to "Болезнь", 17 to "Работа", 26 to "Отдых", 38 to "Погода", 49 to "Отдых")

        val SEED_MEETS = setOf("Чемпионат Москвы", "Кубок России", "Первенство ЦФО", "Всероссийские соревнования")

        val SEED_PERIODS = listOf(
            "Общая подготовка" to "#2E7D6B",
            "Специальная подготовка" to "#2B5CA8",
            "Скоростно-силовой блок" to "#C79100",
            "Предсоревновательный" to "#B8471F"
        )

        // Демо-профиль заметно отличается от настоящего, чтобы режимы не путали
        const val DEMO_FIRST = "Демо"
        const val DEMO_LAST = "Спортсмен"
        const val DEMO_NICK = "demo"
    }

    private fun dayAt(offsetDays: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Добавляет демо-данные за последние 3 месяца. Повторный вызов не создаёт дубликатов. */
    suspend fun seedDemoData() {
        val rnd = Random(2026)
        val daysBack = 91

        repository.inTransaction {
            // --- Профиль спортсмена (чужой заполненный профиль не затираем) ---
            val profile = repository.getProfile().first()
            if (profile == null || profile.firstName.isNullOrBlank()) {
                repository.insertProfile(
                    AthleteProfile(
                        id = 1,
                        firstName = DEMO_FIRST,
                        lastName = DEMO_LAST,
                        nickname = DEMO_NICK,
                        greetingPreference = "NAME",
                        specialization = "Тройной прыжок",
                        birthDate = Calendar.getInstance().apply { clear(); set(2002, Calendar.MARCH, 14) }.timeInMillis,
                        height = 184.0,
                        weight = 76.0,
                        customPbMeters = 15.74,
                        avatarUriString = profile?.avatarUriString
                    )
                )
            }

            // --- Инвентарь ---
            val inv = repository.getAllInventory().first()
            listOf(
                "Барьер" to ResultUnit.CM, "Тумба" to ResultUnit.CM,
                "Штанга" to ResultUnit.KG, "Ядро" to ResultUnit.KG
            ).forEach { (n, u) ->
                if (inv.none { it.name.equals(n, true) && it.unit == u }) repository.insertInventory(InventoryItem(name = n, unit = u))
            }

            // --- Справочник упражнений ---
            val existing = repository.getAllExercises().first()
            val idByName = HashMap<String, Long>()
            existing.forEach { idByName[it.name.trim().lowercase()] = it.id }
            EXERCISES.forEach { d ->
                if (!idByName.containsKey(d.name.lowercase())) {
                    idByName[d.name.lowercase()] = repository.insertExercise(
                        Exercise(
                            name = d.name, categoryName = d.category, defaultUnit = d.unit,
                            isSingleLeg = d.legSide != null, legSide = d.legSide,
                            inventoryName = d.invName, inventoryUnit = d.invUnit, inventoryValue = d.invValue,
                            isFavorite = d.name in FAVORITES,
                            inSpikes = d.category == C_TECH
                        )
                    )
                }
            }

            // --- Цели (по одной на упражнение; существующие не трогаем) ---
            val haveGoals = repository.getAllGoals().first().map { it.recordKey }.toSet()
            GOALS.forEach { (exName, target) ->
                val d = EXERCISES.first { it.name == exName }
                val key = RecordsEngine.exerciseRecordKey(d.name, d.legSide != null, d.legSide, d.unit)
                if (key !in haveGoals) {
                    repository.upsertGoal(
                        ExerciseGoal(
                            recordKey = key,
                            name = d.name,
                            displayName = d.name + RecordsEngine.legSuffix(d.legSide != null, d.legSide),
                            unit = d.unit,
                            targetValue = target,
                            lowerIsBetter = RecordsEngine.isLowerBetter(d.name, d.unit)
                        )
                    )
                }
            }

            // --- Комплексы ---
            val haveComplexes = repository.getAllComplexes().first().map { it.complex.name.lowercase() }.toSet()
            COMPLEXES.forEach { (name, descr, items) ->
                if (name.lowercase() !in haveComplexes) {
                    val id = repository.insertComplex(ExerciseComplex(name = name, description = descr))
                    repository.insertComplexItems(
                        items.mapIndexedNotNull { i, n ->
                            idByName[n.lowercase()]?.let { ComplexExercise(complexId = id, exerciseId = it, position = i) }
                        }
                    )
                }
            }

            // --- Подготовительные периоды (даты начала разные; один период вложен в другой) ---
            val periods = repository.getAllTrainingPeriods().first()
            suspend fun addPeriod(name: String, from: Int, to: Int, color: String) {
                val start = startOfDay(dayAt(from, 0))
                if (periods.none { startOfDay(it.startDate) == start }) {
                    repository.insertTrainingPeriod(
                        TrainingPeriod(name = name, startDate = start, endDate = startOfDay(dayAt(to, 0)), colorHex = color)
                    )
                }
            }
            addPeriod("Общая подготовка", -91, -50, "#2E7D6B")
            addPeriod("Специальная подготовка", -49, -22, "#2B5CA8")
            addPeriod("Скоростно-силовой блок", -42, -29, "#C79100")
            addPeriod("Предсоревновательный", -21, 21, "#B8471F")

            // --- Тренировки: ~5 в неделю, результаты растут к концу трёх месяцев ---
            val trainings = repository.getAllTrainingsWithExercises().first()
            val busyDays = trainings.map { startOfDay(it.training.date) }.toSet()
            var trainingDayIndex = 0
            for (back in daysBack downTo 0) {
                val date = dayAt(-back, 10)
                val weekday = Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.DAY_OF_WEEK)
                val plan = WEEK_PLAN[weekday] ?: continue
                val idx = trainingDayIndex++
                if (startOfDay(date) in busyDays) continue

                val skipReason = SKIPS[idx]
                if (skipReason != null) {
                    repository.insertTrainingSession(
                        TrainingSession(
                            date = date, trainingType = TrainingType.RECOVERY, intensity = 1, durationMinutes = 0,
                            notes = "Пропуск: $skipReason", isSkipped = true, skipReason = skipReason
                        )
                    )
                    continue
                }

                val progress = (daysBack - back).toDouble() / daysBack
                val type = when (weekday) {
                    Calendar.MONDAY, Calendar.FRIDAY -> TrainingType.STRENGTH
                    Calendar.TUESDAY -> TrainingType.TECHNICAL
                    Calendar.THURSDAY -> TrainingType.CONDITIONING
                    else -> TrainingType.JUMP
                }
                val sessionId = repository.insertTrainingSession(
                    TrainingSession(
                        date = date, trainingType = type, intensity = 6 + rnd.nextInt(4),
                        durationMinutes = 70 + rnd.nextInt(5) * 10, notes = "", isSkipped = false
                    )
                )

                plan.forEach { exName ->
                    val d = EXERCISES.first { it.name == exName }
                    val exId = idByName[exName.lowercase()] ?: return@forEach
                    val lowerBetter = d.unit == ResultUnit.SECONDS
                    val step = when (d.unit) {
                        ResultUnit.KG -> 2.5
                        ResultUnit.CM -> 1.0
                        ResultUnit.REPS -> 1.0
                        else -> 0.01
                    }
                    // тренд + небольшой разброс; округление под шаг единицы измерения
                    val raw = d.base + d.gain * progress + (rnd.nextDouble() - 0.5) * Math.abs(d.gain) * 0.18
                    val best = Math.round(raw / step) * step
                    val results = (0 until d.reps).map { i ->
                        val drop = if (i == d.reps - 1 - rnd.nextInt(d.reps)) 0.0 else step * (1 + rnd.nextInt(3))
                        if (lowerBetter) best + drop else best - drop
                    }.toMutableList().also { list ->
                        // лучший результат серии всегда присутствует
                        list[rnd.nextInt(list.size)] = best
                    }
                    repository.insertExerciseResult(
                        ExerciseResult(
                            exerciseId = exId, exerciseName = d.name, trainingSessionId = sessionId, date = date,
                            result = if (lowerBetter) results.min() else results.max(),
                            resultUnit = d.unit, sets = d.sets, reps = d.reps,
                            setResultsJson = results.joinToString(","),
                            isSingleLeg = d.legSide != null, legSide = d.legSide,
                            inventoryName = d.invName, inventoryUnit = d.invUnit, inventoryValue = d.invValue,
                            // технические прыжки с разбега выполняются в шиповках
                            inSpikes = d.category == C_TECH
                        )
                    )
                }
            }

            // --- Старты и рекорды ---
            val comps = repository.getAllCompetitions().first()
            val results = repository.getAllResults().first()

            data class Meet(val name: String, val city: String, val offset: Int, val attempts: String?, val place: String?, val best: Double?, val level: String, val weather: String?)

            listOf(
                Meet("Чемпионат Москвы", "Москва", -77, "15.12, 15.31, X, 15.40, 15.22, X", "3 место", 15.40, "SB", "Тепло, +24°, ветер +0.8"),
                Meet("Кубок России", "Казань", -49, "15.38, 15.55, 15.47, X, 15.60, 15.52", "2 место", 15.60, "SB", "Облачно, +19°, ветер +1.1"),
                Meet("Первенство ЦФО", "Воронеж", -21, "15.50, X, 15.71, 15.66, 15.74, X", "1 место", 15.74, "PB", "Ясно, +17°, ветер +0.4"),
                Meet("Всероссийские соревнования", "Екатеринбург", 21, null, null, null, "PB", null)
            ).forEach { m ->
                val date = dayAt(m.offset, 12)
                if (comps.any { it.name == m.name && startOfDay(it.date) == startOfDay(date) }) return@forEach
                val addr = "Россия, ${m.city}"
                val compId = repository.insertCompetition(
                    Competition(
                        name = m.name, country = "Россия", city = m.city, location = addr, address = addr,
                        date = date, eventTime = "11:00",
                        attemptsJson = m.attempts, placement = m.place,
                        status = if (m.offset < 0) CompetitionStatus.COMPLETED else CompetitionStatus.UPCOMING
                    )
                )
                if (m.best != null && results.none { it.competitionId != null && it.date == date }) {
                    repository.insertTripleJumpResult(
                        TripleJumpResult(
                            competitionId = compId, eventName = "Тройной прыжок", date = date, result = m.best,
                            resultUnit = ResultUnit.M, isIndoor = false, recordLevel = m.level,
                            exerciseCategory = "Прыжковые", isCompetition = true, weatherConditions = m.weather
                        )
                    )
                }
            }
        }
    }
}
