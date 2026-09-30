package com.example.diarytripeljump.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AthleteProfileDao {
    @Query("SELECT * FROM athlete_profile WHERE id = 1")
    fun getProfile(): Flow<AthleteProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AthleteProfile)

    @Update
    suspend fun update(profile: AthleteProfile)
}

@Dao
interface CompetitionDao {
    @Query("SELECT * FROM competitions WHERE status = 'UPCOMING' ORDER BY date ASC")
    fun getUpcomingCompetitions(): Flow<List<Competition>>

    @Query("SELECT * FROM competitions WHERE status = 'COMPLETED' ORDER BY date DESC")
    fun getCompletedCompetitions(): Flow<List<Competition>>

    @Query("SELECT * FROM competitions ORDER BY date DESC")
    fun getAllCompetitions(): Flow<List<Competition>>

    @Insert
    suspend fun insert(competition: Competition): Long

    @Update
    suspend fun update(competition: Competition)

    @Delete
    suspend fun delete(competition: Competition)
}

@Dao
interface TripleJumpResultDao {
    @Query("SELECT * FROM triple_jump_results ORDER BY date DESC LIMIT :limit")
    fun getRecentResults(limit: Int = 10): Flow<List<TripleJumpResult>>

    @Query("SELECT * FROM triple_jump_results WHERE competitionId = :competitionId")
    fun getResultsForCompetition(competitionId: Long): Flow<List<TripleJumpResult>>

    @Query("SELECT * FROM triple_jump_results WHERE result = (SELECT MAX(result) FROM triple_jump_results)")
    fun getPersonalBest(): Flow<TripleJumpResult?>

    @Query("SELECT MAX(result) FROM triple_jump_results")
    suspend fun getBestResult(): Double?

    @Transaction
    @Query("SELECT * FROM triple_jump_results WHERE id = :resultId")
    fun getResultWithAttempts(resultId: Long): Flow<TripleJumpResultWithAttempts?>

    @Insert
    suspend fun insert(result: TripleJumpResult): Long

    @Update
    suspend fun update(result: TripleJumpResult)

    @Delete
    suspend fun delete(result: TripleJumpResult)
}

@Dao
interface JumpAttemptDao {
    @Query("SELECT * FROM jump_attempts WHERE tripleJumpResultId = :resultId ORDER BY attemptNumber")
    fun getAttemptsForResult(resultId: Long): Flow<List<JumpAttempt>>

    @Insert
    suspend fun insert(attempt: JumpAttempt): Long

    @Insert
    suspend fun insertAll(attempts: List<JumpAttempt>)

    @Delete
    suspend fun delete(attempt: JumpAttempt)
}

@Dao
interface TrainingSessionDao {
    @Transaction
    @Query("SELECT * FROM training_sessions ORDER BY date DESC")
    fun getAllTrainingsWithExercises(): Flow<List<TrainingWithExercises>>

    @Transaction
    @Query("SELECT * FROM training_sessions WHERE id = :sessionId")
    fun getTrainingWithExercises(sessionId: Long): Flow<TrainingWithExercises?>

    @Query("SELECT COUNT(*) FROM training_sessions WHERE date >= :seasonStart AND date <= :seasonEnd")
    suspend fun getTrainingCountBySeason(seasonStart: Long, seasonEnd: Long): Int

    @Insert
    suspend fun insert(session: TrainingSession): Long

    @Update
    suspend fun update(session: TrainingSession)

    @Delete
    suspend fun delete(session: TrainingSession)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name")
    fun getFavoriteExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name")
    fun getExercisesByCategory(category: ExerciseCategory): Flow<List<Exercise>>

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface ExerciseResultDao {
    @Query("""
        SELECT exercise_results.*, exercises.name, exercises.category 
        FROM exercise_results 
        INNER JOIN exercises ON exercise_results.exerciseId = exercises.id
        WHERE exercises.isFavorite = 1
        ORDER BY date DESC
    """)
    fun getFavoriteExerciseResults(): Flow<List<ExerciseResultWithExercise>>

    @Query("SELECT * FROM exercise_results WHERE exerciseId = :exerciseId ORDER BY date DESC")
    fun getResultsForExercise(exerciseId: Long): Flow<List<ExerciseResult>>

    @Query("SELECT * FROM exercise_results WHERE trainingSessionId = :sessionId")
    fun getResultsForTraining(sessionId: Long): Flow<List<ExerciseResult>>

    @Query("""
        SELECT * FROM exercise_results 
        WHERE exerciseId = :exerciseId 
        AND result = (SELECT MAX(result) FROM exercise_results WHERE exerciseId = :exerciseId)
    """)
    fun getPersonalBestForExercise(exerciseId: Long): Flow<ExerciseResult?>

    @Query("""
        SELECT MAX(result) FROM exercise_results 
        WHERE exerciseId = :exerciseId 
        AND date >= :seasonStart 
        AND date <= :seasonEnd
    """)
    suspend fun getSeasonalBestForExercise(
        exerciseId: Long,
        seasonStart: Long,
        seasonEnd: Long
    ): Double?

    @Query("""
        SELECT AVG(result) FROM exercise_results 
        WHERE exerciseId = :exerciseId 
        AND date >= :seasonStart 
        AND date <= :seasonEnd
    """)
    suspend fun getSeasonalAverageForExercise(
        exerciseId: Long,
        seasonStart: Long,
        seasonEnd: Long
    ): Double?

    @Insert
    suspend fun insert(result: ExerciseResult): Long

    @Update
    suspend fun update(result: ExerciseResult)

    @Delete
    suspend fun delete(result: ExerciseResult)
}

@Dao
interface TrainingJumpResultDao {
    @Query("SELECT * FROM training_jump_results WHERE trainingSessionId = :sessionId")
    fun getJumpResultsForTraining(sessionId: Long): Flow<List<TrainingJumpResult>>

    @Insert
    suspend fun insert(result: TrainingJumpResult): Long

    @Update
    suspend fun update(result: TrainingJumpResult)

    @Delete
    suspend fun delete(result: TrainingJumpResult)
}

@Dao
interface PreparationPeriodDao {
    @Query("SELECT * FROM preparation_periods WHERE competitionId = :competitionId")
    fun getPeriodsForCompetition(competitionId: Long): Flow<List<PreparationPeriod>>

    @Insert
    suspend fun insert(period: PreparationPeriod): Long

    @Update
    suspend fun update(period: PreparationPeriod)

    @Delete
    suspend fun delete(period: PreparationPeriod)
}

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM analytics WHERE competitionId = :competitionId")
    fun getAnalyticsForCompetition(competitionId: Long): Flow<List<Analytics>>

    @Transaction
    @Query("SELECT * FROM analytics WHERE id = :analyticsId")
    fun getAnalyticsWithDetails(analyticsId: Long): Flow<AnalyticsWithDetails?>

    @Insert
    suspend fun insert(analytics: Analytics): Long

    @Update
    suspend fun update(analytics: Analytics)

    @Delete
    suspend fun delete(analytics: Analytics)
}

@Dao
interface AnalyticsIndicatorDao {
    @Query("SELECT * FROM analytics_indicators WHERE analyticsId = :analyticsId")
    fun getIndicatorsForAnalytics(analyticsId: Long): Flow<List<AnalyticsIndicator>>

    @Insert
    suspend fun insert(indicator: AnalyticsIndicator): Long

    @Update
    suspend fun update(indicator: AnalyticsIndicator)

    @Delete
    suspend fun delete(indicator: AnalyticsIndicator)
}

@Dao
interface AnalyticsResultDao {
    @Query("SELECT * FROM analytics_results WHERE analyticsId = :analyticsId")
    fun getResultsForAnalytics(analyticsId: Long): Flow<List<AnalyticsResult>>

    @Insert
    suspend fun insert(result: AnalyticsResult): Long

    @Update
    suspend fun update(result: AnalyticsResult)

    @Delete
    suspend fun delete(result: AnalyticsResult)
}

@Dao
interface CorrectionHintDao {
    @Query("SELECT * FROM correction_hints WHERE analyticsId = :analyticsId ORDER BY priority DESC")
    fun getHintsForAnalytics(analyticsId: Long): Flow<List<CorrectionHint>>

    @Transaction
    @Query("SELECT * FROM correction_hints WHERE id = :hintId")
    fun getHintWithControls(hintId: Long): Flow<CorrectionHintWithControls?>

    @Insert
    suspend fun insert(hint: CorrectionHint): Long

    @Update
    suspend fun update(hint: CorrectionHint)

    @Delete
    suspend fun delete(hint: CorrectionHint)
}

@Dao
interface ControlIndicatorDao {
    @Query("SELECT * FROM control_indicators WHERE hintId = :hintId")
    fun getControlsForHint(hintId: Long): Flow<List<ControlIndicator>>

    @Insert
    suspend fun insert(control: ControlIndicator): Long

    @Update
    suspend fun update(control: ControlIndicator)

    @Delete
    suspend fun delete(control: ControlIndicator)
}
