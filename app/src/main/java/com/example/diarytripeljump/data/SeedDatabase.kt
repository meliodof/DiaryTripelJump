package com.example.diarytripeljump.data

import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Random

/**
 * Версия набора демо-данных. Если демо-база заполнена прежней версией, она создаётся заново.
 * 2 — год данных под проверку прогноза, связей и советов (см. [DemoSignals]).
 */
const val DEMO_SEED_VERSION = 2

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
 * старты, рекорды, цели, замеры веса и подготовительные периоды за последний год (считая от сегодняшнего дня).
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

        /**
         * Цели со сроком (упражнение, значение, дней до срока): тяга по тренду успевает к сроку, хотя ещё не достигнута,
         * бег 30 м — не успевает. Прогноз покажет оба исхода.
         */
        val GOALS = listOf(
            Triple("Тяга становая", 182.0, 120),
            Triple("Бег 30 м", 3.78, 30)
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
        val SKIPS = mapOf(3 to "Болезнь", 4 to "Болезнь", 17 to "Работа", 26 to "Отдых", 38 to "Погода", 49 to "Отдых", 60 to "Учёба")

        val SEED_MEETS = setOf(
            "Чемпионат Москвы", "Кубок России", "Первенство ЦФО", "Всероссийские соревнования",
            "Зимнее первенство области", "Открытый чемпионат города", "Мемориал прыгунов"
        )

        /** Старты демо-года: сколько дней назад (минус — впереди) и где. */
        val MEETS = listOf(
            Triple("Зимнее первенство области", "Тула", 330),
            Triple("Открытый чемпионат города", "Москва", 260),
            Triple("Мемориал прыгунов", "Рязань", 190),
            Triple("Чемпионат Москвы", "Москва", 120),
            Triple("Кубок России", "Казань", 63),
            Triple("Первенство ЦФО", "Воронеж", 21),
            Triple("Всероссийские соревнования", "Екатеринбург", -21)
        )

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

    /** Неделя демо-года (0…[DemoSignals.WEEKS]) для дня, который был [back] дней назад. */
    private fun weekOf(back: Int): Int = ((365 - back) / 7).coerceIn(0, DemoSignals.WEEKS)

    private fun round(value: Double, step: Double) = Math.round(value / step) * step

    /**
     * Демо-дневник за год (считая от сегодняшнего дня), построенный так, чтобы сработали все модели и советы:
     * прогноз тройного к ближайшему старту и к срокам двух целей (одна достижима, другая нет), связи приседа и бега 30 м
     * с тройным ([DemoSignals]), резкий спад в беге 60 м, всплеск времени тренировок, частые заступы, жалобы в заметках,
     * пропуски и изменение веса. Повторный вызов на заполненной базе не создаёт дубликатов.
     */
    suspend fun seedDemoData() {
        val rnd = Random(2026)
        val daysBack = 365
        val meetValues = MEETS.filter { it.third > 0 }.associate { it.first to round(DemoSignals.meet(weekOf(it.third)), 0.01) }

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
                        weight = 74.2,
                        customPbMeters = meetValues.values.maxOrNull(),
                        avatarUriString = profile?.avatarUriString
                    )
                )
            }

            // --- Вес за год: за последний месяц −2,6 кг (больше 3 %) — совет о весе ---
            if (repository.getBodyMeasurements().first().isEmpty()) {
                listOf(330 to 78.5, 240 to 78.0, 150 to 77.4, 60 to 77.0, 35 to 76.8, 2 to 74.2).forEach { (back, kg) ->
                    repository.insertBodyMeasurement(BodyMeasurement(date = dayAt(-back, 8), heightCm = if (back == 330) 184.0 else null, weightKg = kg))
                }
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
            val runUpId = idByName[RunUp.NAME.lowercase()] ?: repository.insertExercise(
                Exercise(name = RunUp.NAME, categoryName = C_JUMP, defaultUnit = ResultUnit.CM)
            )

            // --- Цели со сроком ---
            val haveGoals = repository.getAllGoals().first().map { it.recordKey }.toSet()
            GOALS.forEach { (exName, target, days) ->
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
                            lowerIsBetter = RecordsEngine.isLowerBetter(d.name, d.unit),
                            deadline = startOfDay(dayAt(days, 0))
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

            // --- Подготовительные периоды года ---
            val periods = repository.getAllTrainingPeriods().first()
            suspend fun addPeriod(name: String, from: Int, to: Int, color: String) {
                val start = startOfDay(dayAt(from, 0))
                if (periods.none { startOfDay(it.startDate) == start }) {
                    repository.insertTrainingPeriod(
                        TrainingPeriod(name = name, startDate = start, endDate = startOfDay(dayAt(to, 0)), colorHex = color)
                    )
                }
            }
            addPeriod("Общая подготовка", -365, -200, "#2E7D6B")
            addPeriod("Специальная подготовка", -199, -90, "#2B5CA8")
            addPeriod("Скоростно-силовой блок", -150, -128, "#C79100")
            addPeriod("Предсоревновательный", -40, 21, "#B8471F")

            // --- Тренировки: ~5 в неделю весь год ---
            val trainings = repository.getAllTrainingsWithExercises().first()
            val busyDays = trainings.map { startOfDay(it.training.date) }.toSet()
            // дни по плану недели, от самого старого к сегодняшнему
            val planDays = (daysBack downTo 0).filter { back ->
                val weekday = Calendar.getInstance().apply { timeInMillis = dayAt(-back, 10) }.get(Calendar.DAY_OF_WEEK)
                WEEK_PLAN.containsKey(weekday)
            }
            val last = planDays.size - 1
            // пропуски: несколько за год и три за последний месяц — совет о пропусках
            val skips = mapOf(
                20 to "Болезнь", 21 to "Болезнь", 95 to "Работа", 150 to "Отдых", 210 to "Погода",
                last - 4 to "Болезнь", last - 8 to "Болезнь", last - 12 to "Учёба"
            )
            // жалобы в заметках двух из трёх последних тренировок — совет о заметках
            val notes = mapOf(
                last to "Тянет заднюю поверхность бедра после прыжков",
                last - 2 to "Болит голеностоп на отталкивании"
            )
            // свежий Бег 60 м заметно хуже нормы — совет «резкий спад»
            val lastThursday = planDays.lastOrNull { back ->
                Calendar.getInstance().apply { timeInMillis = dayAt(-back, 10) }.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY
            }

            planDays.forEachIndexed { idx, back ->
                val date = dayAt(-back, 10)
                if (startOfDay(date) in busyDays) return@forEachIndexed
                val weekday = Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.DAY_OF_WEEK)
                val plan = WEEK_PLAN.getValue(weekday)

                skips[idx]?.let { reason ->
                    repository.insertTrainingSession(
                        TrainingSession(
                            date = date, trainingType = TrainingType.RECOVERY, intensity = 1, durationMinutes = 0,
                            notes = "Пропуск: $reason", isSkipped = true, skipReason = reason
                        )
                    )
                    return@forEachIndexed
                }

                val week = weekOf(back)
                val progress = (daysBack - back).toDouble() / daysBack
                val type = when (weekday) {
                    Calendar.MONDAY, Calendar.FRIDAY -> TrainingType.STRENGTH
                    Calendar.TUESDAY -> TrainingType.TECHNICAL
                    Calendar.THURSDAY -> TrainingType.CONDITIONING
                    else -> TrainingType.JUMP
                }
                // последняя неделя заметно длиннее обычной — совет о нагрузке по времени
                val duration = if (back <= 6) 160 + rnd.nextInt(3) * 10 else 70 + rnd.nextInt(5) * 10
                val sessionId = repository.insertTrainingSession(
                    TrainingSession(
                        date = date, trainingType = type, intensity = 6 + rnd.nextInt(4),
                        durationMinutes = duration, notes = notes[idx] ?: "", isSkipped = false
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
                    // сигналы для моделей; остальные упражнения — тренд и независимый шум
                    val raw = when (d.name) {
                        "Приседания со штангой" -> DemoSignals.squat(week)
                        "Бег 30 м" -> DemoSignals.sprint30(week)
                        "Тройной прыжок с полного разбега" -> DemoSignals.jump(week)
                        "Бег 60 м с хода" -> d.base + d.gain * progress + rnd.nextGaussian() * 0.03 +
                            if (back == lastThursday) 0.45 else 0.0
                        else -> d.base + d.gain * progress + rnd.nextGaussian() * Math.abs(d.gain) * 0.08
                    }
                    val best = round(raw, step)
                    val results = (0 until d.reps).map {
                        val drop = step * (1 + rnd.nextInt(3))
                        if (lowerBetter) best + drop else best - drop
                    }.toMutableList().also { list -> list[rnd.nextInt(list.size)] = best }
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

                // разбег по субботам последних пяти недель: заступы в 4 из 6 попыток — совет о переносе отметки
                if (weekday == Calendar.SATURDAY && back <= 35) {
                    val attempts = listOf(0.5, 0.0, 1.0, 0.5, -0.5, 0.5).map { RunUpAttempt(boardFeet = it) }
                    repository.insertExerciseResult(
                        ExerciseResult(
                            exerciseId = runUpId, exerciseName = RunUp.NAME, trainingSessionId = sessionId, date = date,
                            result = 0.0, resultUnit = ResultUnit.CM, runUpJson = RunUp.encode(attempts)
                        )
                    )
                }
            }

            // --- Старты года: результаты идут за тренировочным трендом, впереди — ближайший старт ---
            val comps = repository.getAllCompetitions().first()
            val results = repository.getAllResults().first()
            var bestSoFar = 0.0
            MEETS.sortedByDescending { it.third }.forEach { (name, city, back) ->
                val date = dayAt(-back, 12)
                if (comps.any { it.name == name && startOfDay(it.date) == startOfDay(date) }) return@forEach
                val addr = "Россия, $city"
                val best = meetValues[name]
                val attempts = best?.let { b ->
                    val others = listOf(0.21, 0.09, -1.0, 0.0, 0.14, 0.05).map { d -> if (d < 0) "X" else String.format(java.util.Locale.US, "%.2f", b - d) }
                    others.joinToString(", ")
                }
                val compId = repository.insertCompetition(
                    Competition(
                        name = name, country = "Россия", city = city, location = addr, address = addr,
                        date = date, eventTime = "11:00",
                        attemptsJson = attempts, placement = best?.let { "${1 + rnd.nextInt(4)} место" },
                        status = if (back > 0) CompetitionStatus.COMPLETED else CompetitionStatus.UPCOMING
                    )
                )
                if (best != null && results.none { it.competitionId != null && it.date == date }) {
                    val level = if (best > bestSoFar) "PB" else "SB"
                    bestSoFar = maxOf(bestSoFar, best)
                    repository.insertTripleJumpResult(
                        TripleJumpResult(
                            competitionId = compId, eventName = "Тройной прыжок", date = date, result = best,
                            resultUnit = ResultUnit.M, isIndoor = back > 300, recordLevel = level,
                            exerciseCategory = "Прыжковые", isCompetition = true, weatherConditions = "Ветер +0.${rnd.nextInt(9)}"
                        )
                    )
                }
            }
        }
    }
}
