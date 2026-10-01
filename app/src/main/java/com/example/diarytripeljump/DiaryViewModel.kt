package com.example.diarytripeljump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diarytripeljump.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DiaryViewModel(
    private val repository: TripleJumpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Загружаем результаты тройного прыжка
            repository.getRecentResults(10).collect { results ->
                _uiState.update { it.copy(
                    tripleJumpResults = results.map { result ->
                        TripleJumpResultUi(
                            event = "Тройной прыжок",
                            result = String.format("%.1f м", result.result),
                            date = formatDate(result.date),
                            isPB = isPersonalBest(result.result)
                        )
                    }
                )}
            }
        }

        viewModelScope.launch {
            // Загружаем результаты избранных упражнений
            repository.getFavoriteExerciseResults().collect { results ->
                _uiState.update { it.copy(
                    favoriteExerciseResults = results.map { item ->
                        ExerciseResultUi(
                            exercise = item.exercise.name,
                            result = formatResult(item.exerciseResult.result, item.exerciseResult.resultUnit),
                            date = formatDate(item.exerciseResult.date),
                            isPB = false // TODO: добавить проверку PB
                        )
                    }
                )}
            }
        }

        viewModelScope.launch {
            // Загружаем предстоящие соревнования
            repository.getUpcomingCompetitions().collect { competitions ->
                _uiState.update { it.copy(
                    upcomingCompetitions = competitions
                )}
            }
        }

        viewModelScope.launch {
            // Загружаем статистику сезона
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val seasonStart = calendar.apply {
                set(year, Calendar.JANUARY, 1)
            }.timeInMillis
            val seasonEnd = calendar.apply {
                set(year, Calendar.DECEMBER, 31)
            }.timeInMillis

            val trainingCount = repository.getTrainingCountBySeason(seasonStart, seasonEnd)
            val competitionCount = repository.getAllCompetitions().first().size

            _uiState.update { it.copy(
                seasonStats = SeasonStats(
                    year = year,
                    totalCompetitions = competitionCount,
                    totalTrainings = trainingCount,
                    personalRecords = 5, // TODO: посчитать реальные ЛР
                    seasonalRecords = 8, // TODO: посчитать реальные СР
                    bestTripleJump = 14.5, // TODO: получить из БД
                    averageTripleJump = 13.8 // TODO: посчитать среднее
                )
            )}
        }
    }

    private fun formatDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = when (calendar.get(Calendar.MONTH)) {
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
    val tripleJumpResults: List<TripleJumpResultUi> = emptyList(),
    val favoriteExerciseResults: List<ExerciseResultUi> = emptyList(),
    val upcomingCompetitions: List<Competition> = emptyList(),
    val seasonStats: SeasonStats? = null
)

data class TripleJumpResultUi(
    val event: String,
    val result: String,
    val date: String,
    val isPB: Boolean
)

data class ExerciseResultUi(
    val exercise: String,
    val result: String,
    val date: String,
    val isPB: Boolean
)
