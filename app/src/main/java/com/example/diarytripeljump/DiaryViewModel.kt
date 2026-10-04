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
    val sets: Int? = null,
    val reps: Int? = null
)

class DiaryViewModel(
    private val repository: TripleJumpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    val allCompetitions: StateFlow<List<Competition>> = repository.getAllCompetitions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrainingsWithExercises: StateFlow<List<TrainingWithExercises>> = repository.getAllTrainingsWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExercises: StateFlow<List<Exercise>> = repository.getAllExercises()
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

    private fun loadData() {
        viewModelScope.launch {
            repository.getRecentResults(10).collect { results ->
                _uiState.update { it.copy(
                    tripleJumpResults = results.map { result ->
                        TripleJumpResultUi(
                            id = result.id,
                            event = "Тройной прыжок",
                            result = String.format(Locale.getDefault(), "%.2f м", result.result),
                            date = formatDate(result.date),
                            isPB = isPersonalBest(result.result)
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

            _uiState.update { it.copy(
                seasonStats = SeasonStats(
                    year = year,
                    totalCompetitions = competitionCount,
                    totalTrainings = trainingCount,
                    personalRecords = 5,
                    seasonalRecords = 8,
                    bestTripleJump = 14.5,
                    averageTripleJump = 13.8
                )
            )}
        }
    }

    // --- CRUD: Competitions ---
    fun addCompetition(name: String, location: String, date: Long, eventType: String) {
        viewModelScope.launch {
            repository.insertCompetition(
                Competition(
                    name = name,
                    location = location,
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
                repository.insertExerciseResult(
                    ExerciseResult(
                        exerciseId = input.exerciseId,
                        trainingSessionId = sessionId,
                        date = date,
                        result = input.result,
                        resultUnit = input.unit,
                        sets = input.sets,
                        reps = input.reps
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
    fun addTripleJumpResult(
        result: Double,
        date: Long,
        weatherConditions: String,
        notes: String,
        competitionId: Long? = null
    ) {
        viewModelScope.launch {
            repository.insertTripleJumpResult(
                TripleJumpResult(
                    competitionId = competitionId,
                    date = date,
                    result = result,
                    weatherConditions = weatherConditions,
                    notes = notes
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

    private suspend fun isPersonalBest(result: Double): Boolean {
        val best = repository.getPersonalBest().first()
        return best != null && result >= best.result
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
