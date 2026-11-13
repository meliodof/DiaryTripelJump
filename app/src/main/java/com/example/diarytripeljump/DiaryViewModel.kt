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
    val repsPerSet: List<Int>? = null,
    /** Подход измеряется в секундах: [reps] и [repsPerSet] — секунды, результат один на подход. */
    val isTimed: Boolean = false,
    /** Заметка к упражнению в этой тренировке: самочувствие, техника, условия. */
    val notes: String? = null
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

    private val _customPalette = MutableStateFlow(preferences.customPalette)
    val customPalette: StateFlow<CustomPalette?> = _customPalette.asStateFlow()

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
    private val allRecords: StateFlow<List<RecordSummary>> = combine(rawTripleJumpResults, allExerciseResults, recordOverrides, allCompetitions) { tj, ex, ov, comps ->
        val today = startOfDay(System.currentTimeMillis())
        val starts = comps
            .filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && startOfDay(it.date) <= today }
            .mapNotNull { c -> bestOfCompetition(c)?.let { it to c.date } }
        RecordsEngine.build(tj, ex, ov, starts)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, emptyList())

    private val _hiddenRecords = MutableStateFlow(preferences.hiddenRecords)

    /** Рекорды без удалённых; удалённый появится снова, если его улучшить. */
    val records: StateFlow<List<RecordSummary>> = combine(allRecords, _hiddenRecords) { list, hidden ->
        list.filter { RecordInsights.isVisible(it, hidden) }
    }.stateIn(viewModelScope, AppSharing, emptyList())

    val hiddenRecordsCount: StateFlow<Int> = combine(allRecords, _hiddenRecords) { list, hidden ->
        list.count { !RecordInsights.isVisible(it, hidden) }
    }.stateIn(viewModelScope, AppSharing, 0)

    /** Рекорд из тренировок удаляется из списка; сами тренировки не меняются. */
    fun hideRecord(record: RecordSummary) {
        val changed = _hiddenRecords.value + (record.key to record.best)
        preferences.hiddenRecords = changed
        _hiddenRecords.value = changed
    }

    fun unhideRecords() {
        preferences.hiddenRecords = emptyMap()
        _hiddenRecords.value = emptyMap()
    }

    // --- Обучение на демо-дневнике ---
    val tutorialSeen: Boolean get() = preferences.tutorialSeen
    val tutorialActive: Boolean get() = preferences.tutorialActive
    val tutorialStep: Int get() = preferences.tutorialStep

    /** Начать обучение; true — сначала нужно открыть демо-дневник. */
    fun beginTutorial(isDemo: Boolean): Boolean {
        preferences.tutorialStep = 0
        preferences.tutorialReturnToOwn = !isDemo
        preferences.tutorialActive = true
        return !isDemo
    }

    fun saveTutorialStep(step: Int) {
        preferences.tutorialStep = step
    }

    /** Обучение пройдено или пропущено; true — нужно вернуться к своему дневнику. */
    fun finishTutorial(): Boolean {
        val back = preferences.tutorialReturnToOwn
        preferences.tutorialSeen = true
        preferences.tutorialActive = false
        preferences.tutorialReturnToOwn = false
        return back
    }

    fun skipTutorial() {
        preferences.tutorialSeen = true
    }

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

    /** Замеры веса и роста по датам. */
    val bodyMeasurements: StateFlow<List<BodyMeasurement>> = repository.getBodyMeasurements()
        .stateIn(viewModelScope, AppSharing, emptyList())

    // --- Советы по данным дневника (см. [AdviceEngine]) ---
    private val _dismissedAdvice = MutableStateFlow(preferences.dismissedAdvice)

    /** Скрытые с главной советы: в аналитике они остаются и их можно вернуть. */
    val dismissedAdvice: StateFlow<Set<String>> = _dismissedAdvice

    /** Все советы по данным дневника, важные первыми — для раздела аналитики. */
    val allAdvice: StateFlow<List<Advice>> =
        combine(allTrainingsWithExercises, allCompetitions, records, goals, bodyMeasurements) { t, c, r, g, b ->
            AdviceEngine.build(AdviceInput(t, c, r, g, b))
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, AppSharing, emptyList())

    /** Советы для главного экрана: без скрытых пользователем. */
    val advice: StateFlow<List<Advice>> = combine(allAdvice, _dismissedAdvice) { list, dismissed ->
        list.filter { it.id !in dismissed }
    }.stateIn(viewModelScope, AppSharing, emptyList())

    /** «Понятно»: совет скрывается с главной, пока ситуация не изменится (у новой ситуации другой id). */
    fun dismissAdvice(advice: Advice) {
        // хранятся только недавние: старые id уже не встретятся
        val changed = (_dismissedAdvice.value + advice.id).toList().takeLast(200).toSet()
        preferences.dismissedAdvice = changed
        _dismissedAdvice.value = changed
    }

    /** Вернуть скрытый совет на главную. */
    fun restoreAdvice(advice: Advice) {
        val changed = _dismissedAdvice.value - advice.id
        preferences.dismissedAdvice = changed
        _dismissedAdvice.value = changed
    }

    // --- Помощник «Спросить дневник» (см. [DiaryAssistant]) ---
    private val _assistantMessages = MutableStateFlow<List<AssistantMessage>>(emptyList())

    /** Переписка с помощником за время работы приложения (не сохраняется: вопросы могут касаться здоровья). */
    val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages.asStateFlow()

    fun askAssistant(question: String) {
        val q = question.trim().take(500)
        if (q.isEmpty()) return
        val id = System.nanoTime()
        _assistantMessages.value = (_assistantMessages.value + AssistantMessage(id, true, q)).takeLast(40)
        viewModelScope.launch(Dispatchers.Default) {
            val input = AssistantInput(
                records = records.value, insights = insights.value, advice = allAdvice.value,
                trainings = allTrainingsWithExercises.value, competitions = allCompetitions.value, goals = goals.value
            )
            val answer = DiaryAssistant.answer(q, input)
            _assistantMessages.value = (_assistantMessages.value + AssistantMessage(id + 1, false, answer.text, answer)).takeLast(40)
        }
    }

    fun clearAssistant() {
        _assistantMessages.value = emptyList()
    }

    /** Все показатели, которые можно вывести на график в аналитике. */
    val analyticsSeries: StateFlow<List<AnalyticsSeries>> =
        combine(allExerciseResults, rawTripleJumpResults, allCompetitions, bodyMeasurements, runUpHistory) { exercises, manual, comps, body, runUp ->
            val today = startOfDay(System.currentTimeMillis())
            // все попытки прошедших стартов, включая квалификацию
            val starts = comps
                .filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && startOfDay(it.date) <= today }
                .map { c -> SeriesSample(c.date, ChartData.attemptsOf(c.attemptsJson) + ChartData.attemptsOf(c.qualificationAttemptsJson)) }
                .filter { it.values.isNotEmpty() }
            ChartData.buildSeries(exercises, manual, starts, body, runUp)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, AppSharing, emptyList())

    // --- Прогноз (см. [Insights]) ---
    private val _pinnedForecasts = MutableStateFlow(preferences.pinnedForecasts)

    /** Показатели, по которым пользователь сам попросил прогноз: id показателя и через сколько недель (0 — к старту). */
    val pinnedForecasts: StateFlow<List<Pair<String, Int>>> = _pinnedForecasts

    fun addForecast(seriesId: String, weeks: Int) {
        val changed = (_pinnedForecasts.value.filterNot { it.first == seriesId } + (seriesId to weeks)).takeLast(10)
        preferences.pinnedForecasts = changed
        _pinnedForecasts.value = changed
    }

    fun removeForecast(seriesId: String) {
        val changed = _pinnedForecasts.value.filterNot { it.first == seriesId }
        preferences.pinnedForecasts = changed
        _pinnedForecasts.value = changed
    }

    /** Прогнозы к старту, срокам целей и по выбранным показателям, связи упражнений с тройным. */
    val insights: StateFlow<InsightsResult> =
        combine(
            combine(records, tripleJumpStartResults, allCompetitions, goals) { r, s, c, g -> listOf(r, s, c, g) },
            athleteProfile, analyticsSeries, _pinnedForecasts
        ) { base, profile, series, pinned ->
            @Suppress("UNCHECKED_CAST")
            val r = base[0] as List<RecordSummary>
            @Suppress("UNCHECKED_CAST")
            val starts = base[1] as List<StartResultUi>
            @Suppress("UNCHECKED_CAST")
            val comps = base[2] as List<Competition>
            @Suppress("UNCHECKED_CAST")
            val g = base[3] as List<ExerciseGoal>
            val byId = series.associateBy { it.id }
            Insights.build(
                r, starts.map { RecordPoint(it.competition.date, it.best, true) }, comps, g,
                birthDate = profile?.birthDate, profilePb = profile?.customPbMeters,
                custom = pinned.mapNotNull { (id, weeks) -> byId[id]?.let { it to weeks } }
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, AppSharing, InsightsResult(emptyList(), emptyList()))

    private val _visualization = MutableStateFlow(preferences.visualization)
    val visualization: StateFlow<VisualizationConfig> = _visualization.asStateFlow()

    fun saveVisualization(config: VisualizationConfig) {
        // один показатель на графике — один раз: повторный ключ в списке роняет экран
        val clean = config.copy(charts = config.charts.map { c -> c.copy(series = c.series.distinctBy { it.id }) })
        preferences.visualization = clean
        _visualization.value = clean
    }

    /** Изменение от текущих настроек, а не от их копии на экране: быстрые нажатия не теряются и не дублируются. */
    fun updateVisualization(change: (VisualizationConfig) -> VisualizationConfig) {
        saveVisualization(change(_visualization.value))
    }

    /** Новый замер; в профиль попадает самый поздний. */
    fun addBodyMeasurement(kind: BodyKind, value: Double, date: Long) {
        if (EntryRules.resultError(value, kind.resultUnit) != null) return
        viewModelScope.launch {
            repository.inTransaction {
                repository.insertBodyMeasurement(
                    BodyMeasurement(
                        date = date,
                        weightKg = value.takeIf { kind == BodyKind.WEIGHT },
                        heightCm = value.takeIf { kind == BodyKind.HEIGHT }
                    )
                )
                syncProfileBody()
            }
        }
    }

    fun deleteBodyMeasurement(measurement: BodyMeasurement) {
        viewModelScope.launch {
            repository.inTransaction {
                repository.deleteBodyMeasurement(measurement)
                syncProfileBody()
            }
        }
    }

    /** Вес и рост в профиле — последние замеры; без замеров остаются прежние значения. */
    private suspend fun syncProfileBody() {
        val all = repository.getBodyMeasurements().first()
        val current = repository.getProfile().first() ?: AthleteProfile()
        repository.insertProfile(
            current.copy(
                id = 1,
                weight = all.filter { it.weightKg != null }.maxByOrNull { it.date }?.weightKg ?: current.weight,
                height = all.filter { it.heightCm != null }.maxByOrNull { it.date }?.heightCm ?: current.height
            )
        )
    }

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

    /** Своя расцветка сохраняется; null возвращает стандартные цвета. */
    fun setCustomPalette(palette: CustomPalette?) {
        preferences.customPalette = palette
        _customPalette.value = palette
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
            // изменение в профиле тоже попадает в историю замеров
            val now = System.currentTimeMillis()
            if (weight != null && weight != current.weight) repository.insertBodyMeasurement(BodyMeasurement(date = now, weightKg = weight))
            if (height != null && height != current.height) repository.insertBodyMeasurement(BodyMeasurement(date = now, heightCm = height))
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
        if (EntryRules.resultError(input.result, input.unit) != null) return
        viewModelScope.launch {
            val hasInventory = !input.inventoryName.isNullOrBlank()
            val weather = if (input.isIndoor) null else input.weather?.takeIf { it.isNotBlank() }
            val notes = input.notes?.takeIf { it.isNotBlank() }
            val legSide = if (input.isSingleLeg) input.legSide else null
            val inventoryName = if (hasInventory) input.inventoryName else null
            val inventoryUnit = if (hasInventory) input.inventoryUnit else null
            val inventoryValue = input.storedInventoryValue()
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
    fun addInventory(name: String, unit: ResultUnit?) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val exists = repository.getAllInventory().first().any { it.name.equals(clean, ignoreCase = true) && it.unit == unit }
            if (!exists) repository.insertInventory(InventoryItem(name = clean, unit = unit))
        }
    }

    /** Меняет инвентарь и упражнения справочника с ним. Прошлые тренировки и рекорды не меняются. */
    fun updateInventory(item: InventoryItem, name: String, unit: ResultUnit?) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.inTransaction {
                repository.updateInventory(item.copy(name = clean, unit = unit))
                // в упражнении может быть несколько снарядов: меняется только этот
                // (91 см не становятся 91 кг: значение в другой единице вводится заново)
                repository.getAllExercises().first().forEach { ex ->
                    InventorySet.rename(
                        InventoryFields(ex.inventoryName, ex.inventoryUnit, ex.inventoryValue), item.name, item.unit, clean, unit
                    )?.let { f ->
                        repository.updateExercise(ex.copy(inventoryName = f.name, inventoryUnit = f.unit, inventoryValue = f.value))
                    }
                }
            }
        }
    }

    /** Из списка инвентарь исчезает, упражнения и результаты с ним сохраняются. */
    fun deleteInventory(item: InventoryItem) {
        viewModelScope.launch { repository.deleteInventory(item) }
    }

    val inventoryActions = InventoryActions(add = ::addInventory, update = ::updateInventory, delete = ::deleteInventory)

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
        /** Длительность от начала до окончания; null — время окончания не указано. */
        durationMinutes: Int?,
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
                        notes = notes.trim(),
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
        exerciseInputs: List<ExerciseResultInput>,
        /** Заметка к тренировке; null — не менять. */
        notes: String? = null,
        /** Длительность от начала до окончания; null — время окончания не указано. */
        durationMinutes: Int? = null
    ) {
        viewModelScope.launch {
            repository.inTransaction {
                // [date] — дата и время начала из окна тренировки
                repository.getAllTrainingsWithExercises().first().find { it.training.id == sessionId }?.training?.let { s ->
                    val newNotes = notes?.trim() ?: s.notes
                    if (date != s.date || newNotes != s.notes || durationMinutes != s.durationMinutes) {
                        repository.updateTrainingSession(s.copy(date = date, notes = newNotes, durationMinutes = durationMinutes))
                    }
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
                    repsPerSetJson = input.repsPerSet?.joinToString(","),
                    isTimed = input.isTimed,
                    notes = input.notes?.trim()?.takeIf { it.isNotEmpty() }
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
        if (EntryRules.resultError(input.result, input.unit) != null) return
        viewModelScope.launch {
            val hasInventory = !input.inventoryName.isNullOrBlank()
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
                    inventoryValue = input.storedInventoryValue(),
                    inSpikes = input.inSpikes
                )
            )
        }
    }

    /** Вес штанги равен самому результату — рекорд и вес снаряда не могут расходиться. */
    private fun NewRecordInput.storedInventoryValue(): Double? = when {
        inventoryName.isNullOrBlank() -> null
        RecordsEngine.isLoadedImplement(inventoryUnit, unit) -> result
        else -> inventoryValue
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