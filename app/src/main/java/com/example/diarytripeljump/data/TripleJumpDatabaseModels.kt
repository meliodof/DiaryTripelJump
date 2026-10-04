package com.example.diarytripeljump.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "athlete_profile")
data class AthleteProfile(
    @PrimaryKey val id: Int = 1,
    val name: String? = null,
    val specialization: String? = "Тройной прыжок",
    val birthDate: Long? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val notes: String? = null
)

@Entity(tableName = "competitions")
data class Competition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String? = null,
    val date: Long,
    val eventType: String = "Тройной прыжок",
    val status: CompetitionStatus = CompetitionStatus.COMPLETED,
    val notes: String? = null
)

enum class CompetitionStatus { UPCOMING, COMPLETED, CANCELLED }

@Entity(
    tableName = "triple_jump_results",
    foreignKeys = [ForeignKey(
        entity = Competition::class,
        parentColumns = ["id"],
        childColumns = ["competitionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("competitionId")]
)
data class TripleJumpResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionId: Long?,
    val date: Long,
    val result: Double,
    val weatherConditions: String? = null,
    val notes: String? = null
)

@Entity(
    tableName = "jump_attempts",
    foreignKeys = [ForeignKey(
        entity = TripleJumpResult::class,
        parentColumns = ["id"],
        childColumns = ["tripleJumpResultId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripleJumpResultId")]
)
data class JumpAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripleJumpResultId: Long,
    val attemptNumber: Int,
    val attemptType: AttemptType,
    val distance: Double?,
    val isFoul: Boolean = false,
    val notes: String? = null
)

enum class AttemptType { QUALIFICATION, FINAL, TRAINING }

@Entity(tableName = "training_sessions")
data class TrainingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val trainingType: TrainingType,
    val intensity: Int? = null,
    val durationMinutes: Int? = null,
    val notes: String? = null,
    val isSkipped: Boolean = false,
    val skipReason: String? = null
)

enum class TrainingType {
    STRENGTH,
    TECHNICAL,
    JUMP,
    CONDITIONING,
    RECOVERY,
    TECHNICAL_STRENGTH,
    TECHNICAL_RECOVERY,
    STRENGTH_CONDITIONING,
    HYBRID
}

enum class ExerciseStage { WARMUP, MAIN, COOLDOWN }

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    val isFavorite: Boolean = false,
    val defaultUnit: ResultUnit
)

enum class ExerciseCategory {
    SQUAT, LUNGE, DEADLIFT,
    JUMP, JUMP_BOX, BROAD_JUMP, VERTICAL_JUMP, DEPTH_JUMP,
    CORE, PLANK,
    POWER_CLEAN, POWER_SNATCH,
    MOBILITY, STRETCHING,
    HURDLES,
    OTHER
}

@Entity(
    tableName = "exercise_results",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrainingSession::class,
            parentColumns = ["id"],
            childColumns = ["trainingSessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("exerciseId"), Index("trainingSessionId"), Index("date")]
)
data class ExerciseResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val trainingSessionId: Long?,
    val date: Long,
    val result: Double,
    val resultUnit: ResultUnit,
    val sets: Int? = null,
    val reps: Int? = null,
    val stage: ExerciseStage = ExerciseStage.MAIN,
    val hurdleHeightCm: Double? = null,
    val notes: String? = null
)

enum class ResultUnit { KG, CM, M, REPS, SECONDS }

@Entity(
    tableName = "training_jump_results",
    foreignKeys = [ForeignKey(
        entity = TrainingSession::class,
        parentColumns = ["id"],
        childColumns = ["trainingSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("trainingSessionId")]
)
data class TrainingJumpResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainingSessionId: Long,
    val result: Double? = null,
    val runUpSpeed: Double? = null,
    val hopPhase: Double? = null,
    val stepPhase: Double? = null,
    val jumpPhase: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: String? = null,
    val attemptNumber: Int? = null,
    val notes: String? = null
)

@Entity(
    tableName = "preparation_periods",
    foreignKeys = [ForeignKey(
        entity = Competition::class,
        parentColumns = ["id"],
        childColumns = ["competitionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("competitionId"), Index("startDate"), Index("endDate")]
)
data class PreparationPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionId: Long,
    val startDate: Long,
    val endDate: Long,
    val name: String? = null,
    val notes: String? = null
)

@Entity(
    tableName = "analytics",
    foreignKeys = [
        ForeignKey(
            entity = Competition::class,
            parentColumns = ["id"],
            childColumns = ["competitionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PreparationPeriod::class,
            parentColumns = ["id"],
            childColumns = ["preparationPeriodId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("competitionId"), Index("preparationPeriodId")]
)
data class Analytics(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionId: Long,
    val preparationPeriodId: Long,
    val createdAt: Long,
    val notes: String? = null
)

@Entity(
    tableName = "analytics_indicators",
    foreignKeys = [
        ForeignKey(
            entity = Analytics::class,
            parentColumns = ["id"],
            childColumns = ["analyticsId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("analyticsId"), Index("exerciseId")]
)
data class AnalyticsIndicator(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyticsId: Long,
    val exerciseId: Long? = null,
    val indicatorName: String,
    val unit: ResultUnit? = null
)

@Entity(
    tableName = "analytics_results",
    foreignKeys = [ForeignKey(
        entity = Analytics::class,
        parentColumns = ["id"],
        childColumns = ["analyticsId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("analyticsId")]
)
data class AnalyticsResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyticsId: Long,
    val indicatorName: String,
    val currentValue: Double? = null,
    val previousValue: Double? = null,
    val changePercent: Double? = null,
    val competitionResult: Double? = null,
    val previousCompetitionResult: Double? = null,
    val relationDescription: String,
    val confidence: Double? = null
)

@Entity(
    tableName = "correction_hints",
    foreignKeys = [ForeignKey(
        entity = Analytics::class,
        parentColumns = ["id"],
        childColumns = ["analyticsId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("analyticsId")]
)
data class CorrectionHint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyticsId: Long,
    val indicatorName: String,
    val hintText: String,
    val priority: Int = 0
)

@Entity(
    tableName = "control_indicators",
    foreignKeys = [ForeignKey(
        entity = CorrectionHint::class,
        parentColumns = ["id"],
        childColumns = ["hintId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("hintId")]
)
data class ControlIndicator(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hintId: Long,
    val indicatorName: String,
    val isSelected: Boolean = false,
    val comment: String? = null
)

data class SeasonStats(
    val year: Int,
    val totalCompetitions: Int,
    val totalTrainings: Int,
    val personalRecords: Int,
    val seasonalRecords: Int,
    val bestTripleJump: Double?,
    val averageTripleJump: Double?,
    val favoriteExercisesStats: String? = null
)
