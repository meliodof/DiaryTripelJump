package com.example.diarytripeljump.data

import kotlinx.coroutines.flow.Flow

class TripleJumpRepository(
    private val athleteProfileDao: AthleteProfileDao,
    private val competitionDao: CompetitionDao,
    private val tripleJumpResultDao: TripleJumpResultDao,
    private val jumpAttemptDao: JumpAttemptDao,
    private val trainingSessionDao: TrainingSessionDao,
    private val customExerciseCategoryDao: CustomExerciseCategoryDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseResultDao: ExerciseResultDao,
    private val trainingJumpResultDao: TrainingJumpResultDao,
    private val preparationPeriodDao: PreparationPeriodDao,
    private val analyticsDao: AnalyticsDao,
    private val analyticsIndicatorDao: AnalyticsIndicatorDao,
    private val analyticsResultDao: AnalyticsResultDao,
    private val correctionHintDao: CorrectionHintDao,
    private val controlIndicatorDao: ControlIndicatorDao
) {
    // Athlete Profile
    fun getProfile(): Flow<AthleteProfile?> = athleteProfileDao.getProfile()
    suspend fun insertProfile(profile: AthleteProfile) = athleteProfileDao.insert(profile)
    suspend fun updateProfile(profile: AthleteProfile) = athleteProfileDao.update(profile)

    // Competitions
    fun getUpcomingCompetitions(): Flow<List<Competition>> = competitionDao.getUpcomingCompetitions()
    fun getCompletedCompetitions(): Flow<List<Competition>> = competitionDao.getCompletedCompetitions()
    fun getAllCompetitions(): Flow<List<Competition>> = competitionDao.getAllCompetitions()
    suspend fun insertCompetition(competition: Competition): Long = competitionDao.insert(competition)
    suspend fun updateCompetition(competition: Competition) = competitionDao.update(competition)
    suspend fun deleteCompetition(competition: Competition) = competitionDao.delete(competition)

    // Triple Jump Results
    fun getRecentResults(limit: Int = 10): Flow<List<TripleJumpResult>> = tripleJumpResultDao.getRecentResults(limit)
    fun getResultsForCompetition(competitionId: Long): Flow<List<TripleJumpResult>> = tripleJumpResultDao.getResultsForCompetition(competitionId)
    fun getPersonalBest(): Flow<TripleJumpResult?> = tripleJumpResultDao.getPersonalBest()
    fun getResultWithAttempts(resultId: Long): Flow<TripleJumpResultWithAttempts?> = tripleJumpResultDao.getResultWithAttempts(resultId)
    suspend fun insertTripleJumpResult(result: TripleJumpResult): Long = tripleJumpResultDao.insert(result)
    suspend fun updateTripleJumpResult(result: TripleJumpResult) = tripleJumpResultDao.update(result)
    suspend fun deleteTripleJumpResult(result: TripleJumpResult) = tripleJumpResultDao.delete(result)

    // Jump Attempts
    fun getAttemptsForResult(resultId: Long): Flow<List<JumpAttempt>> = jumpAttemptDao.getAttemptsForResult(resultId)
    suspend fun insertJumpAttempt(attempt: JumpAttempt): Long = jumpAttemptDao.insert(attempt)
    suspend fun insertJumpAttempts(attempts: List<JumpAttempt>) = jumpAttemptDao.insertAll(attempts)
    suspend fun deleteJumpAttempt(attempt: JumpAttempt) = jumpAttemptDao.delete(attempt)

    // Training Sessions
    fun getAllTrainingsWithExercises(): Flow<List<TrainingWithExercises>> = trainingSessionDao.getAllTrainingsWithExercises()
    fun getTrainingWithExercises(sessionId: Long): Flow<TrainingWithExercises?> = trainingSessionDao.getTrainingWithExercises(sessionId)
    suspend fun getTrainingCountBySeason(seasonStart: Long, seasonEnd: Long): Int = trainingSessionDao.getTrainingCountBySeason(seasonStart, seasonEnd)
    suspend fun insertTrainingSession(session: TrainingSession): Long = trainingSessionDao.insert(session)
    suspend fun updateTrainingSession(session: TrainingSession) = trainingSessionDao.update(session)
    suspend fun deleteTrainingSession(session: TrainingSession) = trainingSessionDao.delete(session)

    // Custom Exercise Categories
    fun getAllCustomCategories(): Flow<List<CustomExerciseCategory>> = customExerciseCategoryDao.getAllCategories()
    suspend fun insertCustomCategory(category: CustomExerciseCategory): Long = customExerciseCategoryDao.insert(category)
    suspend fun deleteCustomCategory(category: CustomExerciseCategory) = customExerciseCategoryDao.delete(category)

    // Exercises
    fun getAllExercises(): Flow<List<Exercise>> = exerciseDao.getAllExercises()
    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insert(exercise)
    suspend fun updateExercise(exercise: Exercise) = exerciseDao.update(exercise)
    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    // Exercise Results
    fun getFavoriteExerciseResults(): Flow<List<ExerciseResultWithExercise>> = exerciseResultDao.getFavoriteExerciseResults()
    fun getResultsForExercise(exerciseId: Long): Flow<List<ExerciseResult>> = exerciseResultDao.getResultsForExercise(exerciseId)
    fun getResultsForTraining(sessionId: Long): Flow<List<ExerciseResult>> = exerciseResultDao.getResultsForTraining(sessionId)
    fun getPersonalBestForExercise(exerciseId: Long): Flow<ExerciseResult?> = exerciseResultDao.getPersonalBestForExercise(exerciseId)
    suspend fun insertExerciseResult(result: ExerciseResult): Long = exerciseResultDao.insert(result)
    suspend fun updateExerciseResult(result: ExerciseResult) = exerciseResultDao.update(result)
    suspend fun deleteExerciseResult(result: ExerciseResult) = exerciseResultDao.delete(result)

    // Training Jump Results
    fun getJumpResultsForTraining(sessionId: Long): Flow<List<TrainingJumpResult>> = trainingJumpResultDao.getJumpResultsForTraining(sessionId)
    suspend fun insertTrainingJumpResult(result: TrainingJumpResult): Long = trainingJumpResultDao.insert(result)
    suspend fun updateTrainingJumpResult(result: TrainingJumpResult) = trainingJumpResultDao.update(result)
    suspend fun deleteTrainingJumpResult(result: TrainingJumpResult) = trainingJumpResultDao.delete(result)

    // Preparation Periods
    fun getPeriodsForCompetition(competitionId: Long): Flow<List<PreparationPeriod>> = preparationPeriodDao.getPeriodsForCompetition(competitionId)
    suspend fun insertPreparationPeriod(period: PreparationPeriod): Long = preparationPeriodDao.insert(period)
    suspend fun updatePreparationPeriod(period: PreparationPeriod) = preparationPeriodDao.update(period)
    suspend fun deletePreparationPeriod(period: PreparationPeriod) = preparationPeriodDao.delete(period)

    // Analytics
    fun getAnalyticsForCompetition(competitionId: Long): Flow<List<Analytics>> = analyticsDao.getAnalyticsForCompetition(competitionId)
    fun getAnalyticsWithDetails(analyticsId: Long): Flow<AnalyticsWithDetails?> = analyticsDao.getAnalyticsWithDetails(analyticsId)
    suspend fun insertAnalytics(analytics: Analytics): Long = analyticsDao.insert(analytics)
    suspend fun updateAnalytics(analytics: Analytics) = analyticsDao.update(analytics)
    suspend fun deleteAnalytics(analytics: Analytics) = analyticsDao.delete(analytics)

    // Analytics Indicators
    fun getIndicatorsForAnalytics(analyticsId: Long): Flow<List<AnalyticsIndicator>> = analyticsIndicatorDao.getIndicatorsForAnalytics(analyticsId)
    suspend fun insertAnalyticsIndicator(indicator: AnalyticsIndicator): Long = analyticsIndicatorDao.insert(indicator)
    suspend fun updateAnalyticsIndicator(indicator: AnalyticsIndicator) = analyticsIndicatorDao.update(indicator)
    suspend fun deleteAnalyticsIndicator(indicator: AnalyticsIndicator) = analyticsIndicatorDao.delete(indicator)

    // Analytics Results
    fun getResultsForAnalytics(analyticsId: Long): Flow<List<AnalyticsResult>> = analyticsResultDao.getResultsForAnalytics(analyticsId)
    suspend fun insertAnalyticsResult(result: AnalyticsResult): Long = analyticsResultDao.insert(result)
    suspend fun updateAnalyticsResult(result: AnalyticsResult) = analyticsResultDao.update(result)
    suspend fun deleteAnalyticsResult(result: AnalyticsResult) = analyticsResultDao.delete(result)

    // Correction Hints
    fun getHintsForAnalytics(analyticsId: Long): Flow<List<CorrectionHint>> = correctionHintDao.getHintsForAnalytics(analyticsId)
    fun getHintWithControls(hintId: Long): Flow<CorrectionHintWithControls?> = correctionHintDao.getHintWithControls(hintId)
    suspend fun insertCorrectionHint(hint: CorrectionHint): Long = correctionHintDao.insert(hint)
    suspend fun updateCorrectionHint(hint: CorrectionHint) = correctionHintDao.update(hint)
    suspend fun deleteCorrectionHint(hint: CorrectionHint) = correctionHintDao.delete(hint)

    // Control Indicators
    fun getControlsForHint(hintId: Long): Flow<List<ControlIndicator>> = controlIndicatorDao.getControlsForHint(hintId)
    suspend fun insertControlIndicator(control: ControlIndicator): Long = controlIndicatorDao.insert(control)
    suspend fun updateControlIndicator(control: ControlIndicator) = controlIndicatorDao.update(control)
    suspend fun deleteControlIndicator(control: ControlIndicator) = controlIndicatorDao.delete(control)
}
