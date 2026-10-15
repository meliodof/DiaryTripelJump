package com.example.diarytripeljump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diarytripeljump.data.*
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
    val inventoryValue: Double? = null
)

class DiaryViewModel(
    private val repository: TripleJumpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    val athleteProfile: StateFlow<AthleteProfile?> = repository.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allCompetitions: StateFlow<List<Competition>> = repository.getAllCompetitions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrainingsWithExercises: StateFlow<List<TrainingWithExercises>> = repository.getAllTrainingsWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExercises: StateFlow<List<Exercise>> = repository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawTripleJumpResults: StateFlow<List<TripleJumpResult>> = repository.getAllResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allExerciseResults: StateFlow<List<ExerciseResult>> = repository.getAllExerciseResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Инвентарь пользователя (название + единица измерения: см или кг). */
    val inventoryItems: StateFlow<List<InventoryItem>> = repository.getAllInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Рекорды вычисляются из результатов тренировок и ручных записей — новый результат
     * автоматически создаёт или обновляет рекорд, а удаление тренировки откатывает его.
     */
    val records: StateFlow<List<RecordSummary>> = combine(rawTripleJumpResults, allExerciseResults) { tj, ex ->
        RecordsEngine.build(tj, ex)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Лента обновлений рекордов (новые сверху) для главного экрана. */
    val recordEvents: StateFlow<List<RecordEvent>> = records
        .map { RecordsEngine.events(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteExerciseResults: StateFlow<List<ExerciseResultWithExercise>> = repository.getFavoriteExerciseResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadData()
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
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
        customPbMeters: Double?
    ) {
        viewModelScope.launch {
            repository.insertProfile(
                AthleteProfile(
                    id = 1,
                    firstName = firstName,
                    lastName = lastName,
                    nickname = nickname,
                    avatarUriString = avatarUriString,
                    greetingPreference = greetingPreference,
                    birthDate = birthDate,
                    height = height,
                    weight = weight,
                    customPbMeters = customPbMeters
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
        viewModelScope.launch {
            repository.getRecentResults(10).collect { results ->
                _uiState.update { it.copy(
                    tripleJumpResults = results.map { result ->
                        TripleJumpResultUi(
                            id = result.id,
                            event = result.eventName,
                            result = formatResult(result.result, result.resultUnit),
                            date = formatDate(result.date),
                            isPB = result.recordLevel == "PB"
                        )
                    }
                )}
            }
        }

        viewModelScope.launch {
            repository.getFavoriteExerciseResults().collect { results ->
                _uiState.update { it.copy(
                    favoriteExerciseResults = results.map { item ->
                        ExerciseResultUi(
                            id = item.exerciseResult.id,
                            exercise = item.exercise.name,
                            result = formatResult(item.exerciseResult.result, item.exerciseResult.resultUnit),
                            date = formatDate(item.exerciseResult.date),
                            isPB = item.exerciseResult.result >= 100.0
                        )
                    }
                )}
            }
        }

        viewModelScope.launch {
            repository.getUpcomingCompetitions().collect { competitions ->
                _uiState.update { it.copy(
                    upcomingCompetitions = competitions
                )}
            }
        }

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
            }.collect { stats ->
                _uiState.update { it.copy(seasonStats = stats) }
            }
        }
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

    fun saveExerciseToDatabase(
        name: String,
        categoryName: String?,
        categoryColorHex: String = "#1E4D3B",
        defaultUnit: ResultUnit = ResultUnit.KG
    ) {
        viewModelScope.launch {
            val existing = repository.getAllExercises().first().find { it.name.equals(name, ignoreCase = true) }
            if (existing == null) {
                repository.insertExercise(
                    Exercise(
                        name = name,
                        categoryName = categoryName,
                        categoryColorHex = categoryColorHex,
                        defaultUnit = defaultUnit
                    )
                )
            }
        }
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
        eventType: String = "Тройной прыжок"
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
                    status = CompetitionStatus.UPCOMING
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

            exerciseInputs.forEach { input ->
                val existing = repository.getAllExercises().first().find { it.name.equals(input.exerciseName, ignoreCase = true) }
                val exId = existing?.id ?: repository.insertExercise(
                    Exercise(
                        name = input.exerciseName,
                        categoryName = "Пользовательские",
                        defaultUnit = input.unit
                    )
                )

                val setResJson = if (input.setResults.isNotEmpty()) input.setResults.joinToString(",") else null

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
                        setResultsJson = setResJson,
                        isSingleLeg = input.isSingleLeg,
                        legSide = if (input.isSingleLeg) input.legSide else null,
                        inventoryName = input.inventoryName?.takeIf { it.isNotBlank() },
                        inventoryUnit = if (input.inventoryName.isNullOrBlank()) null else input.inventoryUnit,
                        inventoryValue = if (input.inventoryName.isNullOrBlank()) null else input.inventoryValue
                    )
                )
            }

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

    fun updateTrainingSessionWithExercises(
        sessionId: Long,
        date: Long,
        exerciseInputs: List<ExerciseResultInput>
    ) {
        viewModelScope.launch {
            val oldResults = repository.getResultsForTraining(sessionId).first()
            oldResults.forEach { repository.deleteExerciseResult(it) }

            exerciseInputs.forEach { input ->
                val existing = repository.getAllExercises().first().find { it.name.equals(input.exerciseName, ignoreCase = true) }
                val exId = existing?.id ?: repository.insertExercise(
                    Exercise(
                        name = input.exerciseName,
                        categoryName = "Пользовательские",
                        defaultUnit = input.unit
                    )
                )

                val setResJson = if (input.setResults.isNotEmpty()) input.setResults.joinToString(",") else null

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
                        setResultsJson = setResJson,
                        isSingleLeg = input.isSingleLeg,
                        legSide = if (input.isSingleLeg) input.legSide else null,
                        inventoryName = input.inventoryName?.takeIf { it.isNotBlank() },
                        inventoryUnit = if (input.inventoryName.isNullOrBlank()) null else input.inventoryUnit,
                        inventoryValue = if (input.inventoryName.isNullOrBlank()) null else input.inventoryValue
                    )
                )
            }
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
        eventName: String,
        result: Double,
        unit: ResultUnit = ResultUnit.M,
        isIndoor: Boolean = false,
        isAntiRecord: Boolean = false,
        recordLevel: String = "PB",
        exerciseCategory: String = "Прыжковые",
        isCompetition: Boolean = true,
        weatherConditions: String? = null,
        notes: String? = null,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            repository.insertTripleJumpResult(
                TripleJumpResult(
                    eventName = eventName,
                    result = result,
                    resultUnit = unit,
                    isIndoor = isIndoor,
                    isAntiRecord = isAntiRecord,
                    recordLevel = recordLevel,
                    exerciseCategory = exerciseCategory,
                    isCompetition = isCompetition,
                    weatherConditions = if (isIndoor) null else weatherConditions,
                    notes = notes,
                    date = date
                )
            )
        }
    }

    fun deleteTripleJumpResult(resultId: Long) {
        viewModelScope.launch {
            val list = repository.getRecentResults(100).first()
            val target = list.find { it.id == resultId }
            if (target != null) {
                repository.deleteTripleJumpResult(target)
            }
        }
    }

    fun deleteExerciseResult(resultId: Long) {
        viewModelScope.launch {
            val list = repository.getFavoriteExerciseResults().first()
            val target = list.find { it.exerciseResult.id == resultId }
            if (target != null) {
                repository.deleteExerciseResult(target.exerciseResult)
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val day = calendar[Calendar.DAY_OF_MONTH]
        val month = when (calendar[Calendar.MONTH]) {
            Calendar.JANUARY -> "Янв"
            Calendar.FEBRUARY -> "Фев"
            Calendar.MARCH -> "Мар"
            Calendar.APRIL -> "Апр"
            Calendar.MAY -> "Май"
            Calendar.JUNE -> "Июн"
            Calendar.JULY -> "Июл"
            Calendar.AUGUST -> "Авг"
            Calendar.SEPTEMBER -> "Сен"
            Calendar.OCTOBER -> "Окт"
            Calendar.NOVEMBER -> "Ноя"
            Calendar.DECEMBER -> "Дек"
            else -> ""
        }
        return "$day $month"
    }

    private fun formatResult(value: Double, unit: ResultUnit): String {
        return when (unit) {
            ResultUnit.KG -> "${value.toInt()} кг"
            ResultUnit.CM -> "${value.toInt()} см"
            ResultUnit.M -> String.format(Locale.getDefault(), "%.2f м", value)
            ResultUnit.REPS -> "${value.toInt()} раз"
            ResultUnit.SECONDS -> "${value.toInt()} сек"
        }
    }
}

data class DiaryUiState(
    val isDarkTheme: Boolean = false,
    val tripleJumpResults: List<TripleJumpResultUi> = emptyList(),
    val favoriteExerciseResults: List<ExerciseResultUi> = emptyList(),
    val upcomingCompetitions: List<Competition> = emptyList(),
    val seasonStats: SeasonStats? = null
)

data class TripleJumpResultUi(
    val id: Long = 0,
    val event: String,
    val result: String,
    val date: String,
    val isPB: Boolean
)

data class ExerciseResultUi(
    val id: Long = 0,
    val exercise: String,
    val result: String,
    val date: String,
    val isPB: Boolean
)
