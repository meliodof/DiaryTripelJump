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
    val isSingleLeg: Boolean = false,
    val hasWeight: Boolean = true,
    val hasHurdles: Boolean = false,
    val hurdleHeightCm: Double? = null,
    val hurdleCount: Int? = null,
    val setResults: List<Double> = emptyList()
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

    val allEquipment: StateFlow<List<EquipmentItem>> = repository.getAllEquipment()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawTripleJumpResults: StateFlow<List<TripleJumpResult>> = repository.getRecentResults(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteExerciseResults: StateFlow<List<ExerciseResultWithExercise>> = repository.getFavoriteExerciseResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadData()
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun addEquipmentItem(name: String, unit: ResultUnit, value: Double) {
        viewModelScope.launch {
            repository.insertEquipment(EquipmentItem(name = name, unit = unit, value = value))
        }
    }

    fun deleteEquipmentItem(item: EquipmentItem) {
        viewModelScope.launch {
            repository.deleteEquipment(item)
        }
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

        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val year = calendar[Calendar.YEAR]
            val seasonStart = calendar.apply { set(year, Calendar.JANUARY, 1) }.timeInMillis
            val seasonEnd = calendar.apply { set(year, Calendar.DECEMBER, 31) }.timeInMillis

            val trainingCount = repository.getTrainingCountBySeason(seasonStart, seasonEnd)
            val competitionCount = repository.getAllCompetitions().first().size
            val resultsList = repository.getRecentResults(100).first()

            val bestResultVal = resultsList.maxOfOrNull { it.result } ?: 0.0
            val avgResultVal = if (resultsList.isNotEmpty()) resultsList.map { it.result }.average() else 0.0
            val pbCount = resultsList.count { it.recordLevel == "PB" }
            val sbCount = resultsList.count { it.recordLevel == "SB" }

            _uiState.update { it.copy(
                seasonStats = SeasonStats(
                    year = year,
                    totalCompetitions = competitionCount,
                    totalTrainings = trainingCount,
                    personalRecords = pbCount,
                    seasonalRecords = sbCount,
                    bestTripleJump = bestResultVal,
                    averageTripleJump = avgResultVal
                )
            )}
        }
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

    fun deleteExerciseMaster(exercise: Exercise) {
        viewModelScope.launch {
            repository.deleteExercise(exercise)
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
                        isSingleLeg = input.isSingleLeg,
                        hasWeight = input.hasWeight,
                        hasHurdles = input.hasHurdles,
                        hurdleHeightCm = input.hurdleHeightCm,
                        hurdleCount = input.hurdleCount,
                        setResultsJson = setResJson
                    )
                )

                // Auto Interconnection: Check if this result is a new Record (PB / Best)
                if (input.result > 0) {
                    val exCat = when {
                        input.exerciseName.contains("бег", ignoreCase = true) -> "Беговые"
                        input.exerciseName.contains("силов", ignoreCase = true) || input.exerciseName.contains("присед", ignoreCase = true) -> "Силовые"
                        else -> "Прыжковые"
                    }
                    repository.insertTripleJumpResult(
                        TripleJumpResult(
                            eventName = input.exerciseName,
                            result = input.result,
                            resultUnit = input.unit,
                            date = date,
                            isIndoor = false,
                            isAntiRecord = false,
                            recordLevel = "PB",
                            exerciseCategory = exCat,
                            isCompetition = false,
                            notes = "Автоматический рекорд из тренировки"
                        )
                    )
                }
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
                        isSingleLeg = input.isSingleLeg,
                        hasWeight = input.hasWeight,
                        hasHurdles = input.hasHurdles,
                        hurdleHeightCm = input.hurdleHeightCm,
                        hurdleCount = input.hurdleCount,
                        setResultsJson = setResJson
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
