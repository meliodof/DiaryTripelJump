package com.example.diarytripeljump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diarytripeljump.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class ExerciseResultInput(
    val exerciseId: Long,
    val exerciseName: String,
    val result: Double,
    val unit: ResultUnit,
    val primaryValue: Int? = null,
    val secondaryValue: Int? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val setResults: List<Double> = emptyList(),
    val isSingleLeg: Boolean = false,
    val legSide: String? = null,
    val inventoryName: String? = null,
    val inventoryUnit: ResultUnit? = null,
    val inventoryValue: Double? = null,
    /** Выполнялось в шиповках — рекорд ведётся отдельно. */
    val inSpikes: Boolean = false,
    /** Упражнение по разбегу: заступы и перенос отметки вместо обычных результатов; null — обычное упражнение. */
    val runUp: List<RunUpAttempt>? = null,
    /** Положение свойств в названии, см. [TitleGrammar.compose]. */
    val titleLayout: String? = null,
    /** Повторения по подходам, если их было разное число; null — во всех подходах по [reps]. */
    val repsPerSet: List<Int>? = null
)

/** Результат старта в тройном прыжке для главного экрана. */
data class StartResultUi(
    val competition: Competition,
    val best: Double,
    val placement: String?,
    val attemptsText: String?,
    val isPersonalBest: Boolean
)

/** Квалификация старта: попытки, разбег в них и вышел ли спортсмен в финал. */
data class QualificationInput(
    val attemptsJson: String? = null,
    val runUpJson: String? = null,
    val qualified: Boolean = true
)

/** Данные для записи, добавляемой на вкладке «Рекорды». */
data class NewRecordInput(
    val eventName: String,
    val result: Double,
    val unit: ResultUnit,
    val isIndoor: Boolean,
    val recordLevel: String,
    val category: String,
    val isCompetition: Boolean,
    val weather: String?,
    val notes: String?,
    val isSingleLeg: Boolean = false,
    val legSide: String? = null,
    val inventoryName: String? = null,
    val inventoryUnit: ResultUnit? = null,
    val inventoryValue: Double? = null,
    val inSpikes: Boolean = false,
    /** Когда установлен рекорд. */
    val date: Long = System.currentTimeMillis()
)

private val NumberRegex = Regex("\\d+(?:[.,]\\d+)?")

/** Лучшая попытка из строки вида «15.12, 15.31, X, 15.40». */
private fun bestOfAttempts(text: String?): Double? =
    text?.let { NumberRegex.findAll(it).mapNotNull { m -> m.value.replace(',', '.').toDoubleOrNull() }.maxOrNull() }

/** Лучшая попытка старта с учётом квалификации: норма может быть выполнена и там. */
private fun bestOfCompetition(c: Competition): Double? =
    listOfNotNull(bestOfAttempts(c.attemptsJson), bestOfAttempts(c.qualificationAttemptsJson)).maxOrNull()

/** Комплекс вместе с развёрнутым списком упражнений (в порядке выполнения). */
data class ComplexUi(
    val complex: ExerciseComplex,
    val exercises: List<Exercise>
)

/**
 * Одна ViewModel обслуживает все вкладки, поэтому потоки держатся «горячими» всё время жизни экрана:
 * при переключении вкладок данные уже готовы — без повторных запросов к БД, пересчёта рекордов
 * и кадра с пустым списком (так было с WhileSubscribed(5000) при возврате на вкладку через 5+ секунд).
 */
private val AppSharing = SharingStarted.Eagerly

class DiaryViewModel(
    private val repository: TripleJumpRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(preferences.isDarkTheme)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    val athleteProfile: StateFlow<AthleteProfile?> = repository.getProfile()
        .stateIn(viewModelScope, AppSharing, null)

    val allCompetitions: StateFlow<List<Competition>> = repository.getAllCompetitions()
        .stateIn(viewModelScope, AppSharing, emptyList())

    val allTrainingsWithExercises: StateFlow<List<TrainingWithExercises>> = repository.getAllTrainingsWithExercises()
        .stateIn(viewModelScope, AppSharing, emptyList())

    val allExercises: StateFlow<List<Exercise>> = repository.getAllExercises()
        .stateIn(viewModelScope, AppSharing, emptyList())

    val rawTripleJumpResults: StateFlow<List<TripleJumpResult>> = repository.getAllResults()
        .stateIn(viewModelScope, AppSharing, emptyList())

    private val allExerciseResults: StateFlow<List<ExerciseResult>> = repository.getAllExerciseResults()
        .stateIn(viewModelScope, AppSharing, emptyList())

    /** Инвентарь пользователя (название + единица измерения: см или кг). */
    val inventoryItems: StateFlow<List<InventoryItem>> = repository.getAllInventory()
        .stateIn(viewModelScope, AppSharing, emptyList())

    /**
     * Рекорды вычисляются из результатов тренировок и ручных записей — новый результат
     * автоматически создаёт или обновляет рекорд, а удаление тренировки откатывает его.
     */
    private val recordOverrides: StateFlow<List<RecordOverride>> = repository.getAllRecordOverrides()
        .stateIn(viewModelScope, AppSharing, emptyList())

    /** Подготовительные периоды, отображаемые на календаре тренировок. */
    val trainingPeriods: StateFlow<List<TrainingPeriod>> = repository.getAllTrainingPeriods()
        .stateIn(viewModelScope, AppSharing, emptyList())

    /** Комплексы упражнений; удалённые упражнения из состава просто исчезают (каскад в БД). */
    val complexes: StateFlow<List<ComplexUi>> = combine(repository.getAllComplexes(), allExercises) { list, exercises ->
        val byId = exercises.associateBy { it.id }
        list.map { item ->
            ComplexUi(
                complex = item.complex,
                exercises = item.items.sortedBy { it.position }.mapNotNull { byId[it.exerciseId] }
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, emptyList())

    // Тяжёлые вычисления — в фоновом потоке, чтобы не задерживать отрисовку при переключении вкладок
    val records: StateFlow<List<RecordSummary>> = combine(rawTripleJumpResults, allExerciseResults, recordOverrides, allCompetitions) { tj, ex, ov, comps ->
        val today = startOfDay(System.currentTimeMillis())
        val starts = comps
            .filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && startOfDay(it.date) <= today }
            .mapNotNull { c -> bestOfCompetition(c)?.let { it to c.date } }
        RecordsEngine.build(tj, ex, ov, starts)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, emptyList())

    /** Цели по результатам; ключ совпадает с ключом рекорда. */
    val goals: StateFlow<List<ExerciseGoal>> = repository.getAllGoals()
        .stateIn(viewModelScope, AppSharing, emptyList())

    /** Разряд определяется сам по лучшим результатам в тройном прыжке на соревнованиях и по рекорду из профиля. */
    val autoRank: StateFlow<AutoRank?> = combine(athleteProfile, allCompetitions, rawTripleJumpResults) { profile, comps, results ->
        val today = startOfDay(System.currentTimeMillis())
        val samples = buildList<Pair<Double, Long>> {
            comps.filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && startOfDay(it.date) <= today }
                .forEach { c -> bestOfCompetition(c)?.let { add(it to c.date) } }
            results.filter {
                it.isCompetition && !it.isAntiRecord && it.resultUnit == ResultUnit.M && !it.isSingleLeg &&
                    it.eventName.contains("тройной", ignoreCase = true)
            }.forEach { add(it.result to it.date) }
            profile?.customPbMeters?.takeIf { it > 0 }?.let { add(it to System.currentTimeMillis()) }
        }
        SportRanks.best(samples, profile?.gender, profile?.birthDate)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, null)

    /** История размера разбега: ручные замеры и все переносы отметки из тренировок и стартов. */
    val runUpHistory: StateFlow<List<RunUpHistoryEntry>> =
        combine(repository.getRunUpMeasurements(), allExerciseResults, allCompetitions) { measurements, exercises, comps ->
            val moves = exercises.mapNotNull { r ->
                r.runUpJson?.let { RunUp.moveOf(r.date, RunUp.decode(it), "Тренировка") }
            } + comps.filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }.mapNotNull { c ->
                RunUp.moveOf(c.date, RunUp.decode(c.qualificationRunUpJson) + RunUp.decode(c.runUpJson), c.name)
            }
            RunUpLength.build(measurements, moves)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, emptyList())

    fun saveRunUpLength(lengthCm: Double?, lengthFeet: Double?) {
        if (lengthCm == null && lengthFeet == null) return
        viewModelScope.launch {
            repository.insertRunUpMeasurement(
                RunUpMeasurement(date = System.currentTimeMillis(), lengthCm = lengthCm, lengthFeet = lengthFeet)
            )
        }
    }

    /** Избранные упражнения каталога — именно они показываются на главном экране. */
    val favoriteExercises: StateFlow<List<Exercise>> = allExercises
        .map { list -> list.filter { it.isFavorite }.distinctBy { it.variantKey() }.sortedBy { it.title().lowercase() } }
        .stateIn(viewModelScope, AppSharing, emptyList())

    /** Результаты стартов в тройном прыжке (новые сверху) для главного экрана. */
    val tripleJumpStartResults: StateFlow<List<StartResultUi>> =
        combine(allCompetitions, rawTripleJumpResults) { comps, results ->
            val today = startOfDay(System.currentTimeMillis())
            val rows = comps
                .filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }
                .filter { startOfDay(it.date) <= today }
                .mapNotNull { c ->
                    val linked = results.filter { it.competitionId == c.id && !it.isAntiRecord && it.result > 0 }
                    val best = linked.maxOfOrNull { it.result } ?: bestOfCompetition(c) ?: return@mapNotNull null
                    StartResultUi(c, best, c.placement, c.attemptsJson, false)
                }
                .sortedByDescending { it.competition.date }
            val overall = rows.maxOfOrNull { it.best }
            rows.map { it.copy(isPersonalBest = overall != null && it.best >= overall) }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, emptyList())

    init {
        loadData()
    }

    // --- Остатки демо-данных в дневнике пользователя (старые версии заливали их при первом запуске) ---
    private val demoSeed = SeedDatabase(repository)
    private val _demoLeftovers = MutableStateFlow<DemoLeftovers?>(null)
    val demoLeftovers: StateFlow<DemoLeftovers?> = _demoLeftovers.asStateFlow()

    fun scanDemoLeftovers() {
        viewModelScope.launch { _demoLeftovers.value = demoSeed.scanLeftovers().takeIf { !it.isEmpty } }
    }

    fun removeDemoLeftovers(onDone: (DemoLeftovers) -> Unit = {}) {
        viewModelScope.launch {
            val removed = demoSeed.removeLeftovers()
            _demoLeftovers.value = null
            onDone(removed)
        }
    }

    /** Тема сохраняется и остаётся выбранной после перезапуска. */
    fun toggleTheme() {
        val dark = !_isDarkTheme.value
        preferences.isDarkTheme = dark
        _isDarkTheme.value = dark
    }

    fun updateAthleteProfile(
        firstName: String,
        lastName: String,
        nickname: String,
        avatarUriString: String?,
        greetingPreference: String,
        birthDate: Long?,
        height: Double?,
        weight: Double?,
        customPbMeters: Double?,
        gender: String?,
        leadingLeg: String?
    ) {
        viewModelScope.launch {
            val current = repository.getProfile().first() ?: AthleteProfile()
            repository.insertProfile(
                current.copy(
                    id = 1,
                    gender = gender,
                    firstName = firstName,
                    lastName = lastName,
                    nickname = nickname,
                    avatarUriString = avatarUriString ?: current.avatarUriString,
                    greetingPreference = greetingPreference,
                    birthDate = birthDate,
                    height = height,
                    weight = weight,
                    customPbMeters = customPbMeters,
                    leadingLeg = leadingLeg
                )
            )
        }
    }

    fun updateAthleteProfileAvatar(uriString: String) {
        viewModelScope.launch {
            val current = repository.getProfile().first() ?: AthleteProfile()
            repository.insertProfile(current.copy(avatarUriString = uriString))
        }
    }

    private fun loadData() {
        // Статистика сезона обновляется автоматически при любом изменении тренировок, стартов и рекордов
        viewModelScope.launch {
            combine(allTrainingsWithExercises, allCompetitions, records) { trainings, competitions, recs ->
                val cal = Calendar.getInstance()
                val year = cal[Calendar.YEAR]
                fun yearOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }[Calendar.YEAR]

                val trainingCount = trainings.count { !it.training.isSkipped && yearOf(it.training.date) == year }
                val competitionCount = competitions.count {
                    it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && yearOf(it.date) == year
                }
                val jumpRecords = recs.filter { !it.isAnti && it.unit == ResultUnit.M }
                val bestJump = (jumpRecords.filter { it.isCompetition }.ifEmpty { jumpRecords }).maxOfOrNull { it.best } ?: 0.0

                SeasonStats(
                    year = year,
                    totalCompetitions = competitionCount,
                    totalTrainings = trainingCount,
                    personalRecords = recs.count { !it.isAnti },
                    seasonalRecords = 0,
                    bestTripleJump = bestJump,
                    averageTripleJump = 0.0
                )
            }.flowOn(Dispatchers.Default).collect { stats ->
                _uiState.update { it.copy(seasonStats = stats) }
            }
        }
    }

    // --- Training periods ---
    /**
     * Сохраняет период. Возвращает в [onResult] false, если уже есть другой период с той же датой начала:
     * вложенность, общий конец или начало в день окончания другого периода допустимы.
     */
    fun saveTrainingPeriod(
        id: Long?, name: String, start: Long, end: Long, colorHex: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val from = startOfDay(minOf(start, end))
            val to = startOfDay(maxOf(start, end))
            val all = repository.getAllTrainingPeriods().first()
            if (PeriodRules.findStartConflict(all, from, id) != null) {
                onResult(false)
                return@launch
            }
            val period = TrainingPeriod(id = id ?: 0, name = name.trim(), startDate = from, endDate = to, colorHex = colorHex)
            if (id == null) repository.insertTrainingPeriod(period) else repository.updateTrainingPeriod(period)
            onResult(true)
        }
    }

    fun deleteTrainingPeriod(period: TrainingPeriod) {
        viewModelScope.launch { repository.deleteTrainingPeriod(period) }
    }

    // --- Manual record edits ---
    /**
     * Полная правка рекорда. Запись, внесённая вручную, меняется сама; рекорд из тренировок вычисляется,
     * поэтому для него хранится правка, которая действует, пока рекорд не обновится новым результатом.
     */
    fun updateRecord(record: RecordSummary, input: NewRecordInput) {
        viewModelScope.launch {
            val hasInventory = !input.inventoryName.isNullOrBlank()
            val weather = if (input.isIndoor) null else input.weather?.takeIf { it.isNotBlank() }
            val notes = input.notes?.takeIf { it.isNotBlank() }
            val legSide = if (input.isSingleLeg) input.legSide else null
            val inventoryName = if (hasInventory) input.inventoryName else null
            val inventoryUnit = if (hasInventory) input.inventoryUnit else null
            val inventoryValue = if (hasInventory) input.inventoryValue else null
            val id = record.deletableResultId
            if (id != null) {
                val row = repository.getAllResults().first().find { it.id == id } ?: return@launch
                repository.updateTripleJumpResult(
                    row.copy(
                        eventName = input.eventName.trim(),
                        result = input.result,
                        resultUnit = input.unit,
                        date = input.date,
                        isIndoor = input.isIndoor,
                        recordLevel = input.recordLevel,
                        exerciseCategory = input.category,
                        isCompetition = input.isCompetition,
                        weatherConditions = weather,
                        notes = notes,
                        isSingleLeg = input.isSingleLeg,
                        legSide = legSide,
                        inventoryName = inventoryName,
                        inventoryUnit = inventoryUnit,
                        inventoryValue = inventoryValue,
                        inSpikes = input.inSpikes
                    )
                )
            } else {
                repository.upsertRecordOverride(
                    RecordOverride(
                        recordKey = record.key,
                        baseBest = record.autoBest,
                        value = input.result,
                        date = input.date,
                        isIndoor = input.isIndoor,
                        weather = weather,
                        notes = notes,
                        eventName = input.eventName.trim(),
                        category = input.category,
                        unit = input.unit,
                        recordLevel = input.recordLevel,
                        isCompetition = input.isCompetition,
                        isSingleLeg = input.isSingleLeg,
                        legSide = legSide,
                        inSpikes = input.inSpikes,
                        inventoryName = inventoryName,
                        inventoryUnit = inventoryUnit,
                        inventoryValue = inventoryValue
                    )
                )
            }
        }
    }

    fun clearRecordOverride(key: String) {
        viewModelScope.launch { repository.deleteRecordOverride(key) }
    }

    // --- Goals ---
    fun saveGoal(
        recordKey: String, name: String, displayName: String, unit: ResultUnit,
        target: Double, lowerIsBetter: Boolean, deadline: Long?
    ) {
        if (target <= 0.0) return
        viewModelScope.launch {
            val old = repository.getAllGoals().first().firstOrNull { it.recordKey == recordKey }
            repository.upsertGoal(
                ExerciseGoal(
                    recordKey = recordKey, name = name, displayName = displayName, unit = unit,
                    targetValue = target, lowerIsBetter = lowerIsBetter, deadline = deadline,
                    createdAt = old?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteGoal(recordKey: String) {
        viewModelScope.launch { repository.deleteGoal(recordKey) }
    }

    // --- Favorites ---
    /** Избранное — для конкретного варианта упражнения (и его возможных дубликатов в справочнике). */
    fun setExerciseFavorite(exercise: Exercise, favorite: Boolean) {
        viewModelScope.launch {
            val key = exercise.variantKey()
            repository.getAllExercises().first().filter { it.variantKey() == key }
                .forEach { repository.updateExercise(it.copy(isFavorite = favorite)) }
        }
    }

    // --- Complexes ---
    fun saveComplex(id: Long?, name: String, description: String?, exerciseIds: List<Long>) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val complexId = if (id == null) {
                repository.insertComplex(ExerciseComplex(name = clean, description = description?.takeIf { it.isNotBlank() }))
            } else {
                val old = repository.getAllComplexes().first().firstOrNull { it.complex.id == id }?.complex ?: return@launch
                repository.updateComplex(old.copy(name = clean, description = description?.takeIf { it.isNotBlank() }))
                repository.deleteComplexItems(id)
                id
            }
            repository.insertComplexItems(
                exerciseIds.distinct().mapIndexed { i, exId -> ComplexExercise(complexId = complexId, exerciseId = exId, position = i) }
            )
        }
    }

    fun deleteComplex(complex: ExerciseComplex) {
        viewModelScope.launch { repository.deleteComplex(complex) }
    }

    /**
     * Удаляет вариант упражнения из справочника (и его дубликаты); другие варианты с тем же названием остаются.
     * Результаты в тренировках и рекорды сохраняются.
     */
    fun deleteExerciseFromCatalog(exercise: Exercise) {
        viewModelScope.launch {
            val key = exercise.variantKey()
            repository.getAllExercises().first().filter { it.variantKey() == key }.forEach { repository.deleteExercise(it) }
        }
    }

    /** Удаляет одно упражнение из сохранённой тренировки; рекорды пересчитываются автоматически. */
    fun deleteExerciseResultEntry(result: ExerciseResult) {
        viewModelScope.launch { repository.deleteExerciseResult(result) }
    }

    // --- Inventory ---
    fun addInventory(name: String, unit: ResultUnit) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val exists = repository.getAllInventory().first().any { it.name.equals(clean, ignoreCase = true) && it.unit == unit }
            if (!exists) repository.insertInventory(InventoryItem(name = clean, unit = unit))
        }
    }

    fun deleteInventory(item: InventoryItem) {
        viewModelScope.launch { repository.deleteInventory(item) }
    }

    /** Новое упражнение в справочнике, если такого варианта (название + свойства) ещё нет. */
    fun saveExerciseToDatabase(cfg: ExerciseConfig) {
        viewModelScope.launch {
            val key = cfg.variantKey()
            if (repository.getAllExercises().first().none { it.variantKey() == key }) {
                repository.insertExercise(applyConfig(Exercise(name = cfg.name), cfg))
            }
        }
    }

    /** Редактирование упражнения в справочнике: название, категория, единица, нога, шиповки, снаряд. */
    fun updateExercise(exercise: Exercise, cfg: ExerciseConfig) {
        viewModelScope.launch { repository.updateExercise(applyConfig(exercise, cfg)) }
    }

    private fun applyConfig(exercise: Exercise, cfg: ExerciseConfig): Exercise {
        val hasInventory = !cfg.inventoryName.isNullOrBlank()
        return exercise.copy(
            name = cfg.name.trim(),
            categoryName = cfg.category,
            defaultUnit = cfg.unit,
            isSingleLeg = cfg.isSingleLeg,
            legSide = if (cfg.isSingleLeg) cfg.legSide else null,
            inventoryName = if (hasInventory) cfg.inventoryName else null,
            inventoryUnit = if (hasInventory) cfg.inventoryUnit else null,
            // вес нагружаемого снаряда (штанги) не хранится — он равен результату
            inventoryValue = if (hasInventory && !cfg.isLoadedImplement) cfg.inventoryValue else null,
            inSpikes = cfg.inSpikes,
            titleLayout = cfg.titleLayout
        )
    }

    // --- CRUD: Competitions ---
    fun addCompetition(
        name: String,
        country: String? = "Россия",
        city: String? = "Москва",
        streetAndNumber: String? = null,
        location: String? = null,
        address: String? = null,
        eventTime: String? = null,
        hasQualification: Boolean = false,
        qualificationStandard: String? = null,
        didNotQualifyForFinal: Boolean = false,
        placement: String? = null,
        attemptsJson: String? = null,
        date: Long = System.currentTimeMillis(),
        eventType: String = "Тройной прыжок",
        runUpJson: String? = null,
        qualification: QualificationInput = QualificationInput()
    ) {
        viewModelScope.launch {
            val fullAddr = address ?: listOfNotNull(country, city, streetAndNumber).joinToString(", ")
            repository.insertCompetition(
                Competition(
                    name = name,
                    country = country,
                    city = city,
                    streetAndNumber = streetAndNumber,
                    location = fullAddr,
                    address = fullAddr,
                    eventTime = eventTime,
                    hasQualification = hasQualification,
                    qualificationStandard = qualificationStandard,
                    didNotQualifyForFinal = didNotQualifyForFinal,
                    placement = placement,
                    attemptsJson = attemptsJson,
                    date = date,
                    eventType = eventType,
                    status = CompetitionStatus.UPCOMING,
                    runUpJson = runUpJson,
                    qualificationAttemptsJson = qualification.attemptsJson,
                    qualificationRunUpJson = qualification.runUpJson,
                    qualifiedForFinal = qualification.qualified
                )
            )
        }
    }

    fun updateCompetition(competition: Competition) {
        viewModelScope.launch {
            repository.updateCompetition(competition)
        }
    }

    fun deleteCompetition(competition: Competition) {
        viewModelScope.launch {
            repository.deleteCompetition(competition)
        }
    }

    // --- CRUD: Training Sessions ---
    fun addTrainingSession(
        date: Long,
        trainingType: TrainingType,
        intensity: Int,
        durationMinutes: Int,
        notes: String,
        exerciseInputs: List<ExerciseResultInput>,
        hopPhase: Double? = null,
        stepPhase: Double? = null,
        jumpPhase: Double? = null,
        runUpSpeed: Double? = null
    ) {
        viewModelScope.launch {
            // Тренировка и её упражнения сохраняются целиком или не сохраняются вовсе
            repository.inTransaction {
                val sessionId = repository.insertTrainingSession(
                    TrainingSession(
                        date = date,
                        trainingType = trainingType,
                        intensity = intensity,
                        durationMinutes = durationMinutes,
                        notes = notes,
                        isSkipped = false
                    )
                )

                insertExerciseResults(sessionId, date, exerciseInputs)

                if (hopPhase != null || stepPhase != null || jumpPhase != null) {
                    val totalResult = (hopPhase ?: 0.0) + (stepPhase ?: 0.0) + (jumpPhase ?: 0.0)
                    repository.insertTrainingJumpResult(
                        TrainingJumpResult(
                            trainingSessionId = sessionId,
                            result = totalResult,
                            runUpSpeed = runUpSpeed,
                            hopPhase = hopPhase,
                            stepPhase = stepPhase,
                            jumpPhase = jumpPhase,
                            attemptNumber = 1
                        )
                    )
                }
            }
        }
    }

    /**
     * Перезаписывает упражнения тренировки. Удаление старых и вставка новых — одна транзакция:
     * раньше при сбое между шагами упражнения тренировки могли пропасть.
     */
    fun updateTrainingSessionWithExercises(
        sessionId: Long,
        date: Long,
        exerciseInputs: List<ExerciseResultInput>
    ) {
        viewModelScope.launch {
            repository.inTransaction {
                // [date] — дата и время из окна тренировки
                repository.getAllTrainingsWithExercises().first().find { it.training.id == sessionId }?.training?.let { s ->
                    if (date != s.date) repository.updateTrainingSession(s.copy(date = date))
                }
                val oldResults = repository.getResultsForTraining(sessionId).first()
                oldResults.forEach { repository.deleteExerciseResult(it) }
                insertExerciseResults(sessionId, date, exerciseInputs)
            }
        }
    }

    /**
     * Перенос всех записей дня (тренировок и пропусков) на другой день — например, перетаскиванием в календаре.
     * Время дня и результаты сохраняются, рекорды пересчитываются по новым датам.
     */
    fun moveTrainingDay(fromDay: Long, toDay: Long) {
        val from = startOfDay(fromDay)
        val to = startOfDay(toDay)
        if (from == to) return
        viewModelScope.launch {
            repository.inTransaction {
                repository.getAllTrainingsWithExercises().first()
                    .filter { startOfDay(it.training.date) == from }
                    .forEach { t ->
                        repository.updateTrainingSession(t.training.copy(date = keepTimeOfDay(t.training.date, to)))
                        t.exerciseResults.forEach { r -> repository.updateExerciseResult(r.copy(date = keepTimeOfDay(r.date, to))) }
                    }
            }
        }
    }

    /** Новая дата [day] с тем же временем суток, что у [original]. */
    private fun keepTimeOfDay(original: Long, day: Long): Long = startOfDay(day) + (original - startOfDay(original))

    /** Сохраняет упражнения тренировки; упражнения, которых нет в справочнике, добавляются в него. */
    private suspend fun insertExerciseResults(sessionId: Long, date: Long, inputs: List<ExerciseResultInput>) {
        val all = repository.getAllExercises().first()
        val byId = all.associateBy { it.id }
        val byVariant = all.associateByTo(HashMap()) { it.variantKey() }
        inputs.forEach { input ->
            val key = input.variantKey()
            // сначала то же упражнение справочника, если его вариант не менялся; иначе — по названию и свойствам
            val exId = byId[input.exerciseId]?.takeIf { it.variantKey() == key }?.id ?: byVariant[key]?.id ?: run {
                val created = Exercise(
                    name = input.exerciseName, categoryName = if (input.runUp != null) "Прыжковое упражнение" else "Пользовательские",
                    defaultUnit = input.unit,
                    isSingleLeg = input.isSingleLeg, legSide = input.legSide, inventoryName = input.inventoryName,
                    inventoryUnit = input.inventoryUnit, inventoryValue = input.inventoryValue, inSpikes = input.inSpikes,
                    titleLayout = input.titleLayout
                )
                val id = repository.insertExercise(created)
                byVariant[key] = created.copy(id = id)
                id
            }
            val hasInventory = !input.inventoryName.isNullOrBlank()
            // у штанги и других нагружаемых снарядов вес снаряда = результат
            val loaded = hasInventory && RecordsEngine.isLoadedImplement(input.inventoryUnit, input.unit)
            repository.insertExerciseResult(
                ExerciseResult(
                    exerciseId = exId,
                    exerciseName = input.exerciseName,
                    trainingSessionId = sessionId,
                    date = date,
                    result = input.result,
                    resultUnit = input.unit,
                    sets = input.sets,
                    reps = input.reps,
                    setResultsJson = input.setResults.takeIf { it.isNotEmpty() }?.joinToString(","),
                    isSingleLeg = input.isSingleLeg,
                    legSide = if (input.isSingleLeg) input.legSide else null,
                    inventoryName = if (hasInventory) input.inventoryName else null,
                    inventoryUnit = if (hasInventory) input.inventoryUnit else null,
                    inventoryValue = when {
                        !hasInventory -> null
                        loaded -> input.result.takeIf { it > 0 }
                        else -> input.inventoryValue
                    },
                    inSpikes = input.inSpikes,
                    runUpJson = input.runUp?.let { RunUp.encode(it) },
                    titleLayout = input.titleLayout,
                    repsPerSetJson = input.repsPerSet?.joinToString(",")
                )
            )
        }
    }

    fun addSkippedDaySession(
        date: Long,
        skipReason: String
    ) {
        viewModelScope.launch {
            repository.insertTrainingSession(
                TrainingSession(
                    date = date,
                    trainingType = TrainingType.RECOVERY,
                    intensity = 1,
                    durationMinutes = 0,
                    notes = "Пропуск: $skipReason",
                    isSkipped = true,
                    skipReason = skipReason
                )
            )
        }
    }

    fun deleteTrainingSession(session: TrainingSession) {
        viewModelScope.launch {
            repository.deleteTrainingSession(session)
        }
    }

    // --- CRUD: Triple Jump Results & Records ---
    fun addRecordResult(
        input: NewRecordInput,
        isAntiRecord: Boolean = false,
        date: Long = input.date
    ) {
        viewModelScope.launch {
            val hasInventory = !input.inventoryName.isNullOrBlank()
            val loaded = hasInventory && RecordsEngine.isLoadedImplement(input.inventoryUnit, input.unit)
            repository.insertTripleJumpResult(
                TripleJumpResult(
                    eventName = input.eventName.trim(),
                    result = input.result,
                    resultUnit = input.unit,
                    isIndoor = input.isIndoor,
                    isAntiRecord = isAntiRecord,
                    recordLevel = input.recordLevel,
                    exerciseCategory = input.category,
                    isCompetition = input.isCompetition,
                    weatherConditions = if (input.isIndoor) null else input.weather?.takeIf { it.isNotBlank() },
                    notes = input.notes?.takeIf { it.isNotBlank() },
                    date = date,
                    isSingleLeg = input.isSingleLeg,
                    legSide = if (input.isSingleLeg) input.legSide else null,
                    inventoryName = if (hasInventory) input.inventoryName else null,
                    inventoryUnit = if (hasInventory) input.inventoryUnit else null,
                    // вес штанги равен самому результату — рекорд и вес снаряда не могут расходиться
                    inventoryValue = when {
                        !hasInventory -> null
                        loaded -> input.result
                        else -> input.inventoryValue
                    },
                    inSpikes = input.inSpikes
                )
            )
        }
    }

    /** Удаляет ручную запись рекорда по id (раньше искалась только среди 100 последних и старые не удалялись). */
    fun deleteTripleJumpResult(resultId: Long) {
        viewModelScope.launch {
            val target = rawTripleJumpResults.value.find { it.id == resultId }
                ?: repository.getAllResults().first().find { it.id == resultId }
            if (target != null) repository.deleteTripleJumpResult(target)
        }
    }
}

data class DiaryUiState(
    val seasonStats: SeasonStats? = null
)