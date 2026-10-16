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
    val legSide: String? = null,
    val hasWeight: Boolean = true,
    val hasHurdles: Boolean = false,
    val hurdleHeightCm: Double? = null,
    val hurdleCount: Int? = null,
    val inventoryName: String? = null,
    val inventoryUnit: ResultUnit? = null,
    val inventoryValue: Double? = null,
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

    val rawTripleJumpResults: StateFlow<List<TripleJumpResult>> = repository.getAllResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allExerciseResults: StateFlow<List<ExerciseResult>> = repository.getAllExerciseResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryItems: StateFlow<List<InventoryItem>> = repository.getAllInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val recordOverrides: StateFlow<List<RecordOverride>> = repository.getAllRecordOverrides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trainingPeriods: StateFlow<List<TrainingPeriod>> = repository.getAllTrainingPeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPreparationPeriods: StateFlow<List<PreparationPeriod>> = repository.getAllPreparationPeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allComplexes: StateFlow<List<ExerciseComplex>> = repository.getAllComplexes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val records: StateFlow<List<RecordSummary>> = combine(rawTripleJumpResults, allExerciseResults, recordOverrides) { tj, ex, ov ->
        RecordsEngine.build(tj, ex, ov)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun loadData() {
        viewModelScope.launch {
            combine(allTrainingsWithExercises, allCompetitions, records) { trainings, comps, recs ->
                val year = Calendar.getInstance()[Calendar.YEAR]
                val activeTrainings = trainings.filter { !it.training.isSkipped }
                val completedComps = comps.filter { it.status == CompetitionStatus.COMPLETED || it.status == CompetitionStatus.ONGOING }

                val maxTjComp = comps.mapNotNull { c ->
                    c.attemptsJson?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.maxOrNull()
                }.maxOrNull() ?: 0.0

                val maxTjRaw = rawTripleJumpResults.value.filter { !it.isAntiRecord }.maxOfOrNull { it.result } ?: 0.0
                val bestJump = maxOf(maxTjComp, maxTjRaw)

                SeasonStats(
                    year = year,
                    totalCompetitions = completedComps.size,
                    totalTrainings = activeTrainings.size,
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

    // --- Exercise Complexes ---
    fun addExerciseComplex(name: String, exerciseNames: List<String>, notes: String? = null) {
        viewModelScope.launch {
            repository.insertComplex(
                ExerciseComplex(
                    name = name,
                    exercisesJson = exerciseNames.joinToString(","),
                    notes = notes
                )
            )
        }
    }

    fun deleteExerciseComplex(complex: ExerciseComplex) {
        viewModelScope.launch {
            repository.deleteComplex(complex)
        }
    }

    // --- Preparation Periods (With Validation against duplicate start dates!) ---
    suspend fun addPreparationPeriodValidated(name: String, startDate: Long, endDate: Long, colorHex: String = "#1E4D3B"): Boolean {
        val existingList = repository.getAllPreparationPeriods().first()

        val calNew = Calendar.getInstance().apply { timeInMillis = startDate }
        val duplicateStartDate = existingList.any { period ->
            val calExisting = Calendar.getInstance().apply { timeInMillis = period.startDate }
            calExisting.get(Calendar.YEAR) == calNew.get(Calendar.YEAR) &&
                    calExisting.get(Calendar.DAY_OF_YEAR) == calNew.get(Calendar.DAY_OF_YEAR)
        }

        if (duplicateStartDate) {
            return false // Duplicate start date blocked!
        }

        repository.insertPreparationPeriod(
            PreparationPeriod(
                name = name,
                startDate = startDate,
                endDate = endDate,
                colorHex = colorHex
            )
        )
        return true
    }

    fun addPreparationPeriod(name: String, startDate: Long, endDate: Long, colorHex: String = "#1E4D3B") {
        viewModelScope.launch {
            addPreparationPeriodValidated(name, startDate, endDate, colorHex)
        }
    }

    fun deletePreparationPeriod(period: PreparationPeriod) {
        viewModelScope.launch {
            repository.deletePreparationPeriod(period)
        }
    }

    // --- Training periods ---
    fun saveTrainingPeriod(id: Long?, name: String, start: Long, end: Long, colorHex: String) {
        viewModelScope.launch {
            val period = TrainingPeriod(id = id ?: 0, name = name.trim(), startDate = minOf(start, end), endDate = maxOf(start, end), colorHex = colorHex)
            repository.insertTrainingPeriod(period)
        }
    }

    fun deleteTrainingPeriod(period: TrainingPeriod) {
        viewModelScope.launch { repository.deleteTrainingPeriod(period) }
    }

    // --- Manual record edits ---
    fun updateRecord(record: RecordSummary, value: Double, date: Long, isIndoor: Boolean, weather: String?, notes: String?) {
        viewModelScope.launch {
            val id = record.deletableResultId
            if (id != null) {
                val row = repository.getAllResults().first().find { it.id == id } ?: return@launch
                repository.updateTripleJumpResult(
                    row.copy(
                        result = value,
                        date = date,
                        isIndoor = isIndoor,
                        weatherConditions = weather?.takeIf { it.isNotBlank() },
                        notes = notes?.takeIf { it.isNotBlank() }
                    )
                )
            } else {
                repository.upsertRecordOverride(
                    RecordOverride(
                        recordKey = record.key,
                        baseBest = record.autoBest,
                        value = value,
                        date = date,
                        isIndoor = isIndoor,
                        weather = weather?.takeIf { it.isNotBlank() },
                        notes = notes?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    fun resetRecordOverride(recordKey: String) {
        viewModelScope.launch { repository.deleteRecordOverride(recordKey) }
    }

    fun clearRecordOverride(recordKey: String) = resetRecordOverride(recordKey)

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

    // --- Profile ---
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

    // --- Exercises ---
    fun saveExerciseToDatabase(
        name: String,
        categoryName: String?,
        categoryColorHex: String = "#1E4D3B",
        defaultUnit: ResultUnit = ResultUnit.KG,
        isSingleLeg: Boolean = false,
        legSide: String? = null,
        inventoryName: String? = null,
        inventoryUnit: ResultUnit? = null,
        inventoryValue: Double? = null,
        equipmentName: String? = null
    ) {
        viewModelScope.launch {
            val existing = repository.getAllExercises().first().find { it.name.equals(name, ignoreCase = true) }
            if (existing == null) {
                repository.insertExercise(
                    Exercise(
                        name = name,
                        categoryName = categoryName,
                        categoryColorHex = categoryColorHex,
                        defaultUnit = defaultUnit,
                        isSingleLeg = isSingleLeg,
                        legSide = legSide,
                        inventoryName = inventoryName,
                        inventoryUnit = inventoryUnit,
                        inventoryValue = inventoryValue,
                        equipmentName = equipmentName
                    )
                )
            }
        }
    }

    fun updateExerciseMaster(exercise: Exercise) {
        viewModelScope.launch {
            repository.updateExercise(exercise)
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
                        legSide = if (input.isSingleLeg) input.legSide else null,
                        hasWeight = input.hasWeight,
                        hasHurdles = input.hasHurdles,
                        hurdleHeightCm = input.hurdleHeightCm,
                        hurdleCount = input.hurdleCount,
                        inventoryName = input.inventoryName?.takeIf { it.isNotBlank() },
                        inventoryUnit = if (input.inventoryName.isNullOrBlank()) null else input.inventoryUnit,
                        inventoryValue = if (input.inventoryName.isNullOrBlank()) null else input.inventoryValue,
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
                        legSide = if (input.isSingleLeg) input.legSide else null,
                        hasWeight = input.hasWeight,
                        hasHurdles = input.hasHurdles,
                        hurdleHeightCm = input.hurdleHeightCm,
                        hurdleCount = input.hurdleCount,
                        inventoryName = input.inventoryName?.takeIf { it.isNotBlank() },
                        inventoryUnit = if (input.inventoryName.isNullOrBlank()) null else input.inventoryUnit,
                        inventoryValue = if (input.inventoryName.isNullOrBlank()) null else input.inventoryValue,
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

    fun updateRecordResult(result: TripleJumpResult) {
        viewModelScope.launch {
            repository.updateTripleJumpResult(result)
        }
    }

    fun deleteTripleJumpResult(resultId: Long) {
        viewModelScope.launch {
            val list = repository.getAllResults().first()
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
